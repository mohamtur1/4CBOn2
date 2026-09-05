# @@SECTION:header@@
"""
4CBON2 — 12-Agent Cognitive Ecosystem (Gemini / Hugging Face Space Edition)
===========================================================================

A Hugging Face Space port of ``4CBOn2_Gemini2c.ipynb`` (the public "Gemini2
Frontier Research" edition), with the AI Rewriter cell replaced by the revised
16-layer pipeline in which **L3 runs before LP** and LP gates on the L3 rewrite
plan instead of the L2 evaluation text.

Differences from the Colab notebook
-----------------------------------
1. **LLM backend.** The notebook authenticates through ``google.colab.ai``
   (Colab's built-in OAuth, no API key). That only exists inside Colab, so this
   edition uses the **Google Generative AI API** (``google-genai``) with an API
   key the visitor pastes into the UI. Keys live in memory only.
2. **Per-request key isolation.** A Space is one shared process serving many
   visitors. The API key and model choice are bound to each request through
   ``contextvars`` (and propagated into the thread pools used by the scorer and
   the live-database search), so concurrent users never reuse one another's key
   or consume one another's quota.
3. **No Google Drive.** Space storage is ephemeral, so every
   ``/content/drive/MyDrive/...`` path becomes ``./data/...``. Data resets when
   the Space restarts; the curated research databases are reseeded on boot.
4. **Embedding fallback.** ChromaDB's default ONNX MiniLM model needs a download
   at boot. If that download is unavailable the app falls back to a
   zero-dependency local hashed embedding so it still starts.
5. **Public Rewriter gate.** The Rewriter tab is wired to
   ``run_public_rewriter`` (three free runs per session, then the Gumroad CTA),
   matching the public edition described in DEPLOYMENT.md.

Nothing in this file contains an API key or credential.
"""

import os

# ── Runtime data ─────────────────────────────────────────────
# Space storage is ephemeral; everything writable lives under DATA_DIR.
DATA_DIR = os.environ.get("FOURCBON2_DATA_DIR", "./data")
os.makedirs(DATA_DIR, exist_ok=True)


# @@SECTION:imports@@
# ============================================================
# CELL 1 — Environment Setup (Hugging Face Space Edition)
# ============================================================

import collections
import contextvars
import contextlib
import concurrent.futures
import csv
import hashlib
import json
import re
import sqlite3
import statistics
import threading
import time
import uuid
import xml.etree.ElementTree as ET
from datetime import datetime
from urllib.parse import quote_plus

import gradio as gr
import chromadb
import plotly.express as px
import plotly.graph_objects as go
import requests
from bs4 import BeautifulSoup
from chromadb.config import Settings
from duckduckgo_search import DDGS
from fpdf import FPDF
from fpdf.enums import XPos, YPos
from PyPDF2 import PdfReader

import docx

print("✅ Environment ready.")


# @@SECTION:llm@@
# ============================================================
# CELL 2 — Core LLM (Google Generative AI API — user-supplied key)
# ============================================================
#
# The Colab edition used google.colab.ai (OAuth, no key). A Space has no Colab
# OAuth, so visitors paste a Google AI Studio API key into the UI instead.
#
# Keys are held in memory only and scoped PER REQUEST via contextvars, because a
# Space serves many visitors from one shared process. Nothing is written to disk.
#
# ── WHY THE TOKEN BUDGET POLICY BELOW EXISTS ─────────────────
# In the Colab notebooks `generate_text(prompt, max_tokens=..., temperature=...)`
# accepted those two arguments and then DROPPED them — the single call site was
#     ai.generate_text(prompt=prompt, model_name=MODEL_NAME, stream=True)
# so there was no output cap at all. Every `max_tokens` in the pipeline (LP=5,
# scorer=10, L2=50) was decorative and never constrained the model.
#
# This API *does* honour the cap, and on Gemini 3 `max_output_tokens` is a
# COMBINED budget for thinking tokens + visible output. A cap of 5 is consumed
# entirely by internal reasoning and returns finish_reason=MAX_TOKENS with zero
# visible characters — which is exactly how LP started failing.
#
# So the notebook's numbers are treated as hints and floored, thinking is pinned
# low, and the cap is always set (omitting it can hang indefinitely).
# See build_src/WHY_COLAB_DIFFERED.md.

from google import genai
from google.genai import types as genai_types

# Default model. Override with the GEMINI_MODEL env var / Space secret, or pick
# a different model in the UI dropdown.
MODEL_NAME = os.environ.get("GEMINI_MODEL", "gemini-3.6-flash")
DEFAULT_MODEL_NAME = MODEL_NAME

MODEL_CHOICES = [
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite",
    "gemini-2.5-pro",
]
if MODEL_NAME not in MODEL_CHOICES:
    MODEL_CHOICES.insert(0, MODEL_NAME)

API_KEY_HELP = "Get a free key at https://aistudio.google.com/apikey"

# ── Thinking + token budget policy ──────────────────────────
# LOW measured ~1,377 thinking tokens vs ~15,726 at HIGH for identical output.
# Support varies by model (gemini-3.7-flash rejects "minimal"), so a rejected
# thinking_config is retried without it rather than failing the call.
THINKING_LEVEL = os.environ.get("GEMINI_THINKING_LEVEL", "LOW").strip().upper()
MIN_OUTPUT_TOKENS = int(os.environ.get("GEMINI_MIN_OUTPUT_TOKENS", "4096"))
MAX_OUTPUT_TOKENS_CEILING = int(os.environ.get("GEMINI_MAX_OUTPUT_TOKENS_CEILING", "16384"))
BUDGET_ESCALATION_FACTOR = 4

# Per-request binding. Threads do not inherit a parent context automatically, so
# every ThreadPoolExecutor submit below uses contextvars.copy_context().
_API_KEY_VAR = contextvars.ContextVar("gemini_api_key", default=None)
_MODEL_VAR = contextvars.ContextVar("gemini_model", default=None)


def _current_api_key():
    """API key for this request, falling back to the Space-level secret."""
    return (_API_KEY_VAR.get() or os.environ.get("GEMINI_API_KEY") or "").strip()


def current_model_name():
    """Model for this request, falling back to the module default."""
    return _MODEL_VAR.get() or MODEL_NAME


def init_client(api_key: str, model_name: str = None):
    """Bind an API key (and optional model) to the current request context.

    Signature-compatible with the notebook's ``init_client``: returns a status
    string that starts with ``✅`` on success so callers can branch on it.
    """
    key = (api_key or "").strip()
    if not key:
        key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        return (
            "⚠️ No Google API key provided. Paste your Gemini API key into the "
            f"**Google API Key** field ({API_KEY_HELP}), or set GEMINI_API_KEY as a Space secret."
        )
    _API_KEY_VAR.set(key)
    if model_name and str(model_name).strip():
        _MODEL_VAR.set(str(model_name).strip())
    return f"✅ Google Generative AI ready. Model: {current_model_name()}"


@contextlib.contextmanager
def gemini_session(api_key, model_name=None):
    """Bind this visitor's key/model for the duration of a request."""
    key_token = _API_KEY_VAR.set((api_key or "").strip() or None)
    model_token = _MODEL_VAR.set(str(model_name).strip() if model_name and str(model_name).strip() else None)
    diag_token = _REQUEST_DIAG.set([])
    try:
        yield
    finally:
        _API_KEY_VAR.reset(key_token)
        _MODEL_VAR.reset(model_token)
        _REQUEST_DIAG.reset(diag_token)


# ════════════════════════════════════════════════════════════
# DIAGNOSTICS — every Gemini call records its finish reason and token split
# ════════════════════════════════════════════════════════════
DIAGNOSTICS = collections.deque(maxlen=400)
_DIAG_LOCK = threading.Lock()
# Per-request view, so a run's stats are not polluted by other visitors' calls.
_REQUEST_DIAG = contextvars.ContextVar("gemini_request_diag", default=None)


def _record_diagnostic(**fields):
    fields.setdefault("when", datetime.now().strftime("%H:%M:%S"))
    with _DIAG_LOCK:
        DIAGNOSTICS.append(fields)
        bucket = _REQUEST_DIAG.get()
        if bucket is not None:
            bucket.append(fields)


def request_diagnostics_summary():
    """One-line summary of the Gemini calls made by *this* request."""
    rows = _REQUEST_DIAG.get()
    if not rows:
        return "no Gemini calls recorded"
    out = sum(r.get("out_tokens") or 0 for r in rows)
    think = sum(r.get("think_tokens") or 0 for r in rows)
    failed = sum(1 for r in rows if r.get("status") in ("error", "empty"))
    retries = sum(1 for r in rows if r.get("status") == "retry")
    seconds = sum(r.get("latency_s") or 0 for r in rows)
    parts = [f"{len(rows)} Gemini call(s)", f"{out:,} output / {think:,} thinking tokens",
             f"{seconds:.0f}s"]
    if failed:
        parts.append(f"⚠️ {failed} failed")
    if retries:
        parts.append(f"{retries} retried")
    return " · ".join(parts)


def _infer_call_label(prompt):
    """Name a call from its prompt, so no call site needs changing."""
    text = prompt or ""
    match = re.search(r"YOU ARE NOW EXECUTING:\s*([A-Z0-9]+)", text)
    if match:
        return f"Rewriter:{match.group(1)}"
    if "Reply with ONLY a single integer" in text:
        return "Rewriter:SCORER"
    if "You are the Autonomous Orchestrator Agent" in text:
        return "Agent:PLANNER"
    if "Synthesise part" in text:
        return "Agent:BATCH-SYNTHESIS"
    if "Create the final strategic report" in text:
        return "Agent:FINAL-SYNTHESIS"
    if "respond with ONLY this JSON" in text:
        return "Agent:SPECIALIST"
    if "Provide a best-practice framework" in text:
        return "Agent:SPECIALIST-FALLBACK"
    if "4CBON2 Frontier Research Assistant" in text:
        return "RAG:ANSWER"
    return "LLM"


def budget_policy_summary():
    return (
        f"model default **`{DEFAULT_MODEL_NAME}`** · thinking level **{THINKING_LEVEL or 'model default'}** · "
        f"output floor **{MIN_OUTPUT_TOKENS}** tokens · ceiling **{MAX_OUTPUT_TOKENS_CEILING}** · "
        f"escalation **×{BUDGET_ESCALATION_FACTOR}** on MAX_TOKENS"
    )


def build_diagnostics_view(limit=80):
    """Return (summary_markdown, table_rows) for the Diagnostics tab."""
    with _DIAG_LOCK:
        rows = list(DIAGNOSTICS)
    if not rows:
        return ("No Gemini calls recorded yet in this Space session. Run a question, an agent "
                "goal or a Rewriter pipeline and refresh."), []

    recent = rows[-limit:][::-1]
    calls = len(rows)
    empties = sum(1 for r in rows if r.get("status") == "empty")
    errors = sum(1 for r in rows if r.get("status") == "error")
    out_tokens = sum(r.get("out_tokens") or 0 for r in rows)
    think_tokens = sum(r.get("think_tokens") or 0 for r in rows)
    latency = sum(r.get("latency_s") or 0 for r in rows)

    summary = (
        f"**{calls}** Gemini call(s) since boot · **{errors}** error(s) · **{empties}** empty "
        f"(MAX_TOKENS-style) · output tokens **{out_tokens:,}** · thinking tokens "
        f"**{think_tokens:,}** · total latency **{latency:.1f}s**\n\n"
        f"Policy: {budget_policy_summary()}"
    )
    if think_tokens and not out_tokens:
        summary += ("\n\n⚠️ **Thinking consumed the entire output budget.** Every call returned "
                    "zero visible tokens. Lower `GEMINI_THINKING_LEVEL` or raise "
                    "`GEMINI_MIN_OUTPUT_TOKENS`.")

    table = [[
        r.get("when", ""), r.get("label", ""), r.get("model", ""), r.get("status", ""),
        r.get("finish_reason", "") or "—",
        r.get("out_tokens") if r.get("out_tokens") is not None else "—",
        r.get("think_tokens") if r.get("think_tokens") is not None else "—",
        r.get("prompt_tokens") if r.get("prompt_tokens") is not None else "—",
        r.get("requested", ""), r.get("budget", ""),
        f"{r.get('latency_s', 0):.1f}", (r.get("detail") or "")[:110],
    ] for r in recent]
    return summary, table


DIAGNOSTIC_HEADERS = ["When", "Call", "Model", "Status", "Finish reason", "Out tok",
                      "Think tok", "Prompt tok", "Requested", "Budget", "Secs", "Detail"]


# ════════════════════════════════════════════════════════════
# ERROR INTERPRETATION
# ════════════════════════════════════════════════════════════
def is_llm_error(text):
    """True when a 'response' is really an error or empty placeholder.

    The Rewriter cell has its own ``_llm_error``; this is the same idea exposed
    to the orchestrator, so an API error can never be accepted as a specialist's
    finding and silently propagated into the final synthesis.
    """
    value = str(text or "").strip()
    if not value:
        return True
    return (value.startswith("⚠️") or value.startswith("❌")
            or value.startswith('{"error"') or value.startswith("{\\\"error\\\""))


def _is_thinking_config_rejected(message):
    lowered = str(message).lower()
    return ("thinking" in lowered and ("not supported" in lowered or "invalid" in lowered
                                       or "unsupported" in lowered or "unknown" in lowered))


def _friendly_api_error(exc):
    """Turn common Google API errors into something a visitor can act on."""
    text = str(exc)
    lowered = text.lower()
    if "api key not valid" in lowered or "api_key_invalid" in lowered or "invalid api key" in lowered:
        return f"that API key was rejected by Google. Check it at https://aistudio.google.com/apikey — {text}"
    if "permission_denied" in lowered or "does not have access" in lowered:
        return f"that key is not enabled for the Generative Language API — {text}"
    if "quota" in lowered or "rate limit" in lowered or "resource_exhausted" in lowered:
        return f"rate limit / quota exceeded for this key. Wait a moment or use another model — {text}"
    if "not found" in lowered and "model" in lowered:
        return (f"model '{current_model_name()}' was not available for this key. "
                f"Try another model in the dropdown — {text}")
    return text


def _unpack_response(response):
    """Pull (text, finish_reason, usage) out of a GenerateContentResponse."""
    finish = ""
    try:
        candidate = (getattr(response, "candidates", None) or [None])[0]
        if candidate is not None:
            reason = getattr(candidate, "finish_reason", None)
            finish = getattr(reason, "name", None) or (str(reason) if reason else "")
    except Exception:
        pass

    usage = {}
    metadata = getattr(response, "usage_metadata", None)
    if metadata is not None:
        usage = {
            "prompt_tokens": getattr(metadata, "prompt_token_count", None),
            "out_tokens": getattr(metadata, "candidates_token_count", None),
            "think_tokens": getattr(metadata, "thoughts_token_count", None),
            "total_tokens": getattr(metadata, "total_token_count", None),
        }

    try:
        text = response.text
    except Exception:
        text = None
    return (text or ""), finish, usage


def _empty_response_reason(response, finish, usage):
    """Explain a response with no visible text — usually thinking ate the budget."""
    think = usage.get("think_tokens")
    out = usage.get("out_tokens")
    if think and not out:
        return (f"model returned no visible text — all {think} output token(s) went to internal "
                f"thinking (finish reason: {finish or 'unknown'}). Raise GEMINI_MIN_OUTPUT_TOKENS "
                f"or lower GEMINI_THINKING_LEVEL.")
    try:
        feedback = getattr(response, "prompt_feedback", None)
        if feedback is not None and getattr(feedback, "block_reason", None):
            return f"prompt was blocked (block reason: {feedback.block_reason})."
    except Exception:
        pass
    if finish:
        return f"model returned no text (finish reason: {finish})."
    return "model returned no text. Try rephrasing, or shorten the input."


def _budget_ladder(requested):
    """(thinking_config, budget) attempts, cheapest first, escalating on failure."""
    base = max(int(requested or 0), MIN_OUTPUT_TOKENS)
    budgets = [base]
    escalated = min(base * BUDGET_ESCALATION_FACTOR, MAX_OUTPUT_TOKENS_CEILING)
    if escalated > base:
        budgets.append(escalated)

    thinking = None
    if THINKING_LEVEL and THINKING_LEVEL not in ("OFF", "NONE", "DISABLED", "DEFAULT"):
        try:
            thinking = genai_types.ThinkingConfig(thinking_level=THINKING_LEVEL)
        except Exception:
            thinking = None

    plan = [(thinking, budget) for budget in budgets]
    if thinking is not None:
        # Some models reject the parameter outright; give them a chance without it.
        plan += [(None, budget) for budget in budgets]
    return plan


def generate_text(prompt, max_tokens=2048, temperature=0.7, stream=False):
    """Generate text with the Google Generative AI API.

    Returns a plain string in both modes — ``ask_stream`` does its own
    word-by-word chunking for the UI, exactly as in the notebook. Any failure is
    returned as a ``⚠️``-prefixed string so ``is_llm_error`` can catch it.
    """
    api_key = _current_api_key()
    if not api_key:
        return ("⚠️ Error: no Google API key for this request. Paste your Gemini API key "
                f"into the **Google API Key** field ({API_KEY_HELP}).")

    model = current_model_name()
    prompt_text = str(prompt)
    label = _infer_call_label(prompt_text)
    last_detail = "no attempt completed"

    for thinking, budget in _budget_ladder(int(max_tokens)):
        kwargs = {"max_output_tokens": int(budget), "temperature": float(temperature)}
        if thinking is not None:
            kwargs["thinking_config"] = thinking
        started = time.time()
        try:
            # A fresh client per call keeps keys out of module state entirely.
            client = genai.Client(api_key=api_key)
            config = genai_types.GenerateContentConfig(**kwargs)
            if stream:
                parts, final_chunk = [], None
                for chunk in client.models.generate_content_stream(
                        model=model, contents=prompt_text, config=config):
                    final_chunk = chunk
                    piece = getattr(chunk, "text", None)
                    if piece:
                        parts.append(piece)
                text, finish, usage = _unpack_response(final_chunk) if final_chunk is not None else ("", "", {})
                if not text:
                    text = "".join(parts)
            else:
                response = client.models.generate_content(model=model, contents=prompt_text, config=config)
                text, finish, usage = _unpack_response(response)
        except Exception as exc:
            latency = time.time() - started
            message = str(exc)
            if thinking is not None and _is_thinking_config_rejected(message):
                last_detail = f"thinking_config rejected; retrying without it ({message[:90]})"
                _record_diagnostic(label=label, model=model, status="retry", finish_reason="",
                                   requested=int(max_tokens), budget=int(budget), latency_s=latency,
                                   out_tokens=None, think_tokens=None, prompt_tokens=None,
                                   detail=last_detail)
                continue
            _record_diagnostic(label=label, model=model, status="error", finish_reason="",
                               requested=int(max_tokens), budget=int(budget), latency_s=latency,
                               out_tokens=None, think_tokens=None, prompt_tokens=None,
                               detail=message[:160])
            return f"⚠️ API Error: {_friendly_api_error(exc)}"

        latency = time.time() - started
        if text.strip():
            _record_diagnostic(label=label, model=model, status="ok", finish_reason=finish,
                               requested=int(max_tokens), budget=int(budget), latency_s=latency,
                               detail="", **usage)
            return text

        # Empty: thinking almost certainly consumed the budget. Escalate.
        last_detail = _empty_response_reason(None, finish, usage)
        _record_diagnostic(label=label, model=model, status="empty", finish_reason=finish,
                           requested=int(max_tokens), budget=int(budget), latency_s=latency,
                           detail=last_detail[:160], **usage)

    return f"⚠️ API Error: {last_detail}"


def ask_raw(prompt, max_tokens=4096):
    return generate_text(prompt, max_tokens=max_tokens, temperature=0.1, stream=False)


def safe_ask_raw(prompt, max_tokens=4096):
    try:
        result = ask_raw(prompt, max_tokens=max_tokens)
        if not result or not result.strip():
            return '{"error": "Empty response from LLM. Please check your API key or reduce prompt size."}'
        if hasattr(result, "__iter__") and not isinstance(result, (str, dict, list)):
            result = "".join(list(result))
        if not isinstance(result, str):
            result = str(result)
        return result.strip()
    except Exception as e:
        return f'{{"error": "safe_ask_raw failed: {str(e)}"}}'


def check_api_key(api_key, model_name=None):
    """UI helper: verify a key with the cheapest possible call."""
    with gemini_session(api_key, model_name):
        if not _current_api_key():
            return ("⚠️ No Google API key provided. Paste your Gemini API key "
                    f"({API_KEY_HELP}), or set GEMINI_API_KEY as a Space secret.")
        probe = generate_text("Reply with the single word: ready", max_tokens=8, temperature=0.0)
        if is_llm_error(probe):
            return f"❌ {probe}"
        last = DIAGNOSTICS[-1] if DIAGNOSTICS else {}
        return (f"✅ Connected to **{current_model_name()}** in {last.get('latency_s', 0):.1f}s "
                f"({last.get('out_tokens') or 0} output / {last.get('think_tokens') or 0} thinking tokens). "
                f"This key will be used for the rest of this browser session.")



# @@SECTION:chroma_pre@@
# ============================================================
# CELL 3 — Strong AI, Mathematics & Science Knowledge Databases
# ============================================================
#
# Spaces have no Google Drive: the persistent Chroma store lives under DATA_DIR
# and is reseeded from CURATED_DATABASES on every boot.

drive_path = os.path.join(DATA_DIR, "chroma_db_gemini2_frontier_research")
os.makedirs(drive_path, exist_ok=True)

client = chromadb.PersistentClient(
    path=drive_path,
    settings=Settings(allow_reset=True)
)

# Uploaded documents remain in their own collection. The three domain collections
# below are seeded here and grow as live scholarly records are retrieved in Cell 6.
COLLECTION_NAME = "frontier_research_uploads"
DOMAIN_COLLECTIONS = {
    "ai": "strong_ai_database",
    "mathematics": "strong_mathematics_database",
    "science": "strong_science_database",
}


# @@SECTION:chroma_post@@
class _LocalHashEmbedding(chromadb.utils.embedding_functions.EmbeddingFunction):
    """Zero-download fallback embedding (deterministic hashed bag-of-tokens).

    ChromaDB's default embedder downloads an ONNX MiniLM model on first use. If
    that download is unavailable the Space would fail to boot, so we fall back to
    this. It is lexical rather than semantic — retrieval still works, it is just
    less forgiving about synonyms.
    """

    def __init__(self, dim=64):
        self.dim = dim

    def __call__(self, input):
        vectors = []
        for text in input:
            v = [0.0] * self.dim
            for tok in re.findall(r"\w+", str(text).lower()):
                h = hashlib.md5(tok.encode("utf-8")).digest()
                v[int.from_bytes(h[:2], "big") % self.dim] += 1.0
            norm = sum(x * x for x in v) ** 0.5
            if norm > 0:
                v = [x / norm for x in v]
            vectors.append(v)
        return vectors


def _resolve_embedding_function():
    """Pick one embedding function at boot and use it for every collection.

    Mixing embedders across collections would make query vectors incomparable
    with stored vectors, so the decision is made once, here.
    """
    try:
        from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2

        candidate = ONNXMiniLM_L6_V2()
        candidate(["warmup"])  # force the download now so failures surface at boot
        return candidate, "all-MiniLM-L6-v2 (ONNX, semantic)"
    except Exception as exc:
        return _LocalHashEmbedding(), f"local hashed fallback (lexical) — default model unavailable: {str(exc)[:120]}"


EMBEDDING_FN, EMBEDDING_LABEL = _resolve_embedding_function()
print(f"✅ Embeddings: {EMBEDDING_LABEL}")


def _get_or_create_collection(name):
    try:
        return client.get_collection(name=name, embedding_function=EMBEDDING_FN)
    except Exception:
        return client.create_collection(name=name, embedding_function=EMBEDDING_FN)


# Seed or update all domain databases deterministically, without duplicating rows.
domain_collections = {}
for domain, collection_name in DOMAIN_COLLECTIONS.items():
    col = _get_or_create_collection(collection_name)
    records = CURATED_DATABASES[domain]
    documents = [f"{r['title']}\n{r['text']}" for r in records]
    ids = [f"seed_{domain}_{i:03d}" for i in range(len(records))]
    metadatas = [{
        "source": "4CBON2 curated research index",
        "title": r["title"],
        "url": r["url"],
        "domain": domain,
        "type": "curated",
    } for r in records]
    col.upsert(documents=documents, ids=ids, metadatas=metadatas)
    domain_collections[domain] = col
    print(f"✅ {domain.title()} database ready: {col.count()} records")

collection = _get_or_create_collection(COLLECTION_NAME)
print(f"✅ Upload database ready: {collection.count()} records")
print("Collections available:", [c.name for c in client.list_collections()])


# @@SECTION:ui@@
# ============================================================
# CELL 8 — Master UI (Hugging Face Space Edition — Google Generative AI API)
# ============================================================


def _resolve_session(api_key, stored_key, model_name):
    """Prefer a freshly typed key; otherwise reuse this browser session's key."""
    key = (api_key or "").strip() or (stored_key or "").strip()
    return key, (model_name or DEFAULT_MODEL_NAME)


def _require_key(key):
    if key:
        return None
    return (
        "❌ A Google API key is required. Paste your Gemini API key into the "
        f"**Google API Key** field ({API_KEY_HELP})."
    )


def run_agent(goal, api_key, stored_key, model_name, use_gemini_only, enable_additional, *api_keys):
    """Run the orchestrator with optional API key injection controlled by checkboxes.

    Yields ``(accumulated_log, key_update)`` tuples. Two details differ from the
    notebook on purpose:

    * Gradio *replaces* a Textbox on every yield rather than appending, so the
      notebook's incremental ``yield chunk`` left only the final chunk visible.
      Accumulating gives the intended progressive execution log.
    * The second output persists the key into session state so the visitor types
      it once and every tab reuses it.
    """
    key, model = _resolve_session(api_key, stored_key, model_name)
    missing = _require_key(key)
    if missing:
        yield missing, gr.update()
        return

    log = ""

    def emit(chunk):
        nonlocal log
        log += chunk
        return log, gr.update(value=key)

    def release_optional_keys():
        for name in OPTIONAL_KEY_NAMES:
            os.environ.pop(name, None)

    with gemini_session(key, model):
        try:
            yield emit(f"✅ Using Google Generative AI ({current_model_name()}) — key supplied in the UI, memory only.\n\n")

            # Checkbox logic for additional APIs
            if use_gemini_only:
                yield emit("🔒 **Gemini Only mode** — all additional API keys ignored.\n\n")
            elif enable_additional:
                yield emit("🔓 **Additional APIs enabled** — injecting optional API keys.\n\n")
                for name, val in zip(OPTIONAL_KEY_NAMES, api_keys):
                    if val and val.strip():
                        os.environ[name] = val.strip()
            else:
                yield emit("🔒 **Additional APIs disabled** — using Gemini only.\n\n")

            try:
                for chunk in run_orchestrator_stream(goal):
                    yield emit(chunk)
                yield emit(f"\n📈 _{request_diagnostics_summary()}_\n")
            except GeneratorExit:
                # Gradio cancelled the generator: the browser disconnected or a
                # proxy timed out. Yielding here is illegal, so the only thing we
                # can do — and the thing that was missing — is persist the work.
                saved = persist_partial_run(
                    "Run cancelled: the browser disconnected or the request timed out")
                print(f"⚠️ Agent Mode cancelled; recovered {saved} partial specialist report(s).")
                raise
            except Exception as e:
                yield emit(f"❌ Orchestrator error: {str(e)}\n\n📈 _{request_diagnostics_summary()}_\n")
            except BaseException as e:
                saved = persist_partial_run(f"Run interrupted by {type(e).__name__}")
                print(f"⚠️ Agent Mode interrupted by {type(e).__name__}; recovered {saved} report(s).")
                raise
        finally:
            release_optional_keys()


def check_key_and_remember(api_key, stored_key, model_name):
    """Test Connection: verify the key, then remember it for this session."""
    key, model = _resolve_session(api_key, stored_key, model_name)
    return check_api_key(key, model), gr.update(value=key)


# The layer heading is generated from PIPELINE_ORDER so it can never drift out of
# sync with the boxes below it (the revised pipeline runs L3 before LP).
PIPELINE_ORDER_LABEL = " → ".join(PIPELINE_ORDER)

# Optional third-party keys injected into the environment for tool use. Defined
# once so the setup and teardown paths cannot drift apart.
OPTIONAL_KEY_NAMES = ["CALENDAR_API_KEY", "CRM_API_KEY", "COMM_API_KEY", "VISION_API_KEY",
                      "DOCUSIGN_API_KEY", "SOCIAL_SCRAPER_API_KEY", "SEO_API_KEY",
                      "S3_VAULT_KEY", "PUBMED_API_KEY"]

with gr.Blocks(title="4CBON2 — Gemini Frontier Research Edition") as demo:
    # Shared per-browser-session store for the API key and model choice, so a
    # visitor types their key once and every tab reuses it. Held in Gradio
    # session state (browser side), never on disk.
    session_key = gr.State("")

    gr.Markdown("# 🚀 4CBON2 — 12-Agent Cognitive Ecosystem (Gemini Frontier Research Edition)")
    gr.Markdown(
        "*Powered by the **Google Generative AI API** with source-grounded AI, mathematics and science "
        "research databases.*"
    )
    gr.Markdown(
        f"> 🔑 **Bring your own key.** Paste a Google AI Studio API key once in any tab "
        f"({API_KEY_HELP}). It is kept in this browser session's memory only — never stored on the "
        f"server or written to disk, and isolated from other visitors. "
        f"Default model: **`{DEFAULT_MODEL_NAME}`**. Runtime data is ephemeral and resets on Space restart."
    )

    with gr.Tabs():
        # ── Upload Tab ──
        with gr.TabItem("📁 Upload Documents"):
            gr.Markdown("Upload .txt, .pdf, or .docx files to the knowledge base.")
            file_input = gr.File(label="Upload file", file_types=[".txt", ".pdf", ".docx"])
            upload_output = gr.Textbox(label="Status", interactive=False)
            upload_btn = gr.Button("Process & Index", variant="primary")
            upload_btn.click(fn=process_document, inputs=[file_input], outputs=[upload_output])

        # ── Ask a Question Tab ──
        with gr.TabItem("❓ Ask a Question"):
            gr.Markdown(
                "### Frontier Research Question\n"
                "Ask an ambitious AI, mathematics, or science question. The system automatically routes it "
                "to the strong local domain databases and can retrieve current records from pertinent scholarly databases."
            )
            with gr.Row():
                ask_api_key = gr.Textbox(
                    label="Google API Key",
                    placeholder="AIza...",
                    type="password",
                    scale=3,
                    info=f"Required. {API_KEY_HELP}",
                )
                ask_model = gr.Dropdown(
                    choices=MODEL_CHOICES,
                    value=DEFAULT_MODEL_NAME,
                    label="Gemini Model",
                    scale=2,
                )
            ask_check_btn = gr.Button("🔌 Test Connection")
            question_box = gr.Textbox(
                label="Your Question",
                lines=4,
                placeholder="How do I build an AGI-oriented agent? Or: develop a rigorous research approach to one Millennium Prize Problem.",
            )
            gr.Examples(
                examples=[
                    ["How do I build an AGI-oriented agent, and how should I evaluate it safely?"],
                    ["Attempt a rigorous research approach to one of the Millennium Prize Problems."],
                    ["What experiment could distinguish the leading explanations for an unresolved scientific question?"],
                ],
                inputs=[question_box],
            )
            use_live_databases = gr.Checkbox(
                value=True,
                label="Search live scholarly and official databases",
                info="Queries pertinent sources such as arXiv, OpenAlex, Semantic Scholar, Crossref, PubMed, Europe PMC, OEIS, and official sites. Individual sources can be temporarily unavailable.",
            )
            with gr.Accordion("Available research databases", open=False):
                gr.Markdown(
                    "**Persistent local vector databases:** Strong AI, Strong Mathematics, Strong Science, plus uploaded documents. "
                    "Live results are cached into the pertinent local database for later questions.\n\n"
                    "**Live discovery:** arXiv · OpenAlex · Semantic Scholar · Crossref · PubMed · Europe PMC · OEIS · official NIST/Clay/NIH/NASA web results. "
                    "Retrieval supplies evidence; it does not by itself validate a proof or scientific claim."
                )
            ask_output = gr.Textbox(label="Source-grounded Answer", lines=26, interactive=False)
            ask_status = gr.Textbox(label="Retrieval Report", lines=4, interactive=False)
            ask_btn = gr.Button("Research & Answer", variant="primary")

            def ask_five_lens(question, use_live, api_key, stored, model_name):
                key, model = _resolve_session(api_key, stored, model_name)
                missing = _require_key(key)
                if missing:
                    return missing, "❌ No API key", gr.update()
                if not question or not question.strip():
                    return "❌ Enter a question.", "❌ No question", gr.update(value=key)
                try:
                    with gemini_session(key, model):
                        answer, report = handle_ask_question(
                            COLLECTION_NAME,
                            question,
                            use_live_databases=bool(use_live),
                            return_report=True,
                        )
                    return answer, f"✅ Done | {report}", gr.update(value=key)
                except Exception as e:
                    return f"❌ Error: {str(e)}", "❌ Failed", gr.update(value=key)

            ask_btn.click(
                fn=ask_five_lens,
                inputs=[question_box, use_live_databases, ask_api_key, session_key, ask_model],
                outputs=[ask_output, ask_status, session_key],
            )
            ask_check_btn.click(
                fn=check_key_and_remember,
                inputs=[ask_api_key, session_key, ask_model],
                outputs=[ask_status, session_key],
            )

        # ── Agent Mode Tab ──
        with gr.TabItem("🤖 Agent Mode"):
            gr.Markdown("Multi-Agent Orchestration with 12 specialists powered by Google Gemini.")
            with gr.Row():
                with gr.Column(scale=2):
                    profile_selector = gr.Dropdown(choices=list(AGENT_PROFILES.keys()), value="New Autonomous Agent", label="Agent Profile")
                    with gr.Row():
                        agent_api_key = gr.Textbox(
                            label="Google API Key",
                            placeholder="AIza...",
                            type="password",
                            scale=3,
                            info=f"Required. {API_KEY_HELP}",
                        )
                        agent_model = gr.Dropdown(
                            choices=MODEL_CHOICES,
                            value=DEFAULT_MODEL_NAME,
                            label="Gemini Model",
                            scale=2,
                        )
                    agent_goal = gr.Textbox(label="Goal / Instructions", lines=3, placeholder="e.g. Analyze our competitor positioning and recommend a content strategy...")

                    # Checkboxes
                    chk_gemini_only = gr.Checkbox(
                        label="Use Only Gemini API",
                        value=True,
                        info="When checked, only Gemini is used. All other API keys are ignored."
                    )
                    chk_enable_additional = gr.Checkbox(
                        label="Enable Additional APIs",
                        value=False,
                        info="When checked (and Gemini-only is OFF), optional API key fields become available."
                    )

                    agent_btn = gr.Button("Run Orchestrator", variant="primary")

                with gr.Column(scale=1):
                    additional_keys_accordion = gr.Accordion("🔑 Optional API Keys", open=False, visible=False)
                    with additional_keys_accordion:
                        t_cal = gr.Textbox(label="Calendar", type="password")
                        t_crm = gr.Textbox(label="CRM", type="password")
                        t_comm = gr.Textbox(label="Comm", type="password")
                        t_vision = gr.Textbox(label="Vision/OCR", type="password")
                        t_ds = gr.Textbox(label="DocuSign", type="password")
                        t_social = gr.Textbox(label="Social", type="password")
                        t_seo = gr.Textbox(label="SEO", type="password")
                        t_s3 = gr.Textbox(label="S3/Vault", type="password")
                        t_pubmed = gr.Textbox(label="PubMed", type="password")

            # Visibility logic
            def update_keys_visibility(gemini_only, enable_additional):
                show = (not gemini_only) and enable_additional
                return gr.Accordion(visible=show, open=show)

            chk_gemini_only.change(
                fn=update_keys_visibility,
                inputs=[chk_gemini_only, chk_enable_additional],
                outputs=[additional_keys_accordion]
            )
            chk_enable_additional.change(
                fn=update_keys_visibility,
                inputs=[chk_gemini_only, chk_enable_additional],
                outputs=[additional_keys_accordion]
            )

            agent_output = gr.Textbox(label="Execution Log & Output", lines=25, interactive=False)
            agent_btn.click(
                fn=run_agent,
                inputs=[
                    agent_goal,
                    agent_api_key, session_key, agent_model,
                    chk_gemini_only, chk_enable_additional,
                    t_cal, t_crm, t_comm, t_vision, t_ds, t_social, t_seo, t_s3, t_pubmed
                ],
                outputs=[agent_output, session_key]
            )

        # ============================================================
        # AI REWRITER TAB — ordered artifact display
        # ============================================================
        with gr.TabItem("🧠 AI Rewriter (16-Layer Pipeline)"):
            gr.Markdown("""
            ## AI Rewriter — 16-Layer Pipeline
            Paste an AI-generated answer and optionally describe its goal. The runtime executes each layer in order,
            scores the answer before and after, preserves the three L9 Socratic questions for the next run, and includes three free runs per session.
            """)
            with gr.Row():
                rewriter_api_key = gr.Textbox(
                    label="Google API Key",
                    placeholder="AIza...",
                    type="password",
                    scale=3,
                    info=f"Required — one run makes ~20 Gemini calls. {API_KEY_HELP}",
                )
                rewriter_model = gr.Dropdown(
                    choices=MODEL_CHOICES,
                    value=DEFAULT_MODEL_NAME,
                    label="Gemini Model",
                    scale=2,
                )
            rewriter_answer = gr.Textbox(
                label="AI-generated answer",
                lines=8,
                placeholder="Paste the answer you want to inspect and improve..."
            )
            rewriter_context = gr.Textbox(
                label="Context / goal (optional)",
                lines=3,
                placeholder="What should the answer achieve, and who will use it?"
            )
            with gr.Row():
                rewriter_score_before = gr.Number(label="Score Before", precision=0, interactive=False)
                rewriter_score_after = gr.Number(label="Score After", precision=0, interactive=False)
            rewriter_status = gr.Markdown("Ready. No pipeline run yet.")
            rewriter_run_btn = gr.Button("▶ Run Pipeline", variant="primary")
            rewriter_l9_state = gr.State([])
            public_free_run_state = gr.State(0)

            gr.Markdown(f"### Layer outputs — {PIPELINE_ORDER_LABEL}")
            rewriter_layer_boxes = []
            for _layer in PIPELINE_ORDER:
                _meta = LAYER_METADATA.get(_layer, {"name": "Socratic Integrity Engine"})
                _box = gr.Textbox(
                    label=f"{_layer} — {_meta['name']}",
                    lines=3 if _layer not in ("L4", "L10") else 8,
                    interactive=False
                )
                rewriter_layer_boxes.append(_box)
            rewriter_audit = gr.Textbox(label="Final L10 Audit", lines=10, interactive=False)
            rewriter_copy_btn = gr.Button("⊡ Copy All", variant="secondary")
            rewriter_report = gr.Textbox(label="Copy All report", lines=12, interactive=False)

            def run_public_rewriter_ui(answer, context, prior_questions, free_runs, api_key, stored, model_name):
                """Public gate: three free runs per browser session, then the CTA."""
                key, model = _resolve_session(api_key, stored, model_name)
                missing = _require_key(key)
                if missing:
                    blocked = _empty_pipeline_result(missing)
                    return _result_to_ui(blocked)[:-1] + [list(prior_questions or []), int(free_runs or 0), gr.update()]
                with gemini_session(key, model):
                    outputs = run_public_rewriter(answer, context, prior_questions, free_runs)
                    # Must be read inside the session: the per-request bucket is
                    # torn down on exit.
                    summary_line = request_diagnostics_summary()
                # outputs = [score_before, score_after, status, *17 layers, audit, l9, count]
                outputs = list(outputs)
                outputs[2] = f"{outputs[2]}\n\n📈 _{summary_line}_"
                return outputs + [gr.update(value=key)]

            _rewriter_outputs = (
                [rewriter_score_before, rewriter_score_after, rewriter_status]
                + rewriter_layer_boxes
                + [rewriter_audit, rewriter_l9_state, public_free_run_state, session_key]
            )
            rewriter_run_btn.click(
                fn=run_public_rewriter_ui,
                inputs=[rewriter_answer, rewriter_context, rewriter_l9_state, public_free_run_state,
                        rewriter_api_key, session_key, rewriter_model],
                outputs=_rewriter_outputs,
            )
            rewriter_copy_btn.click(
                fn=copy_all_rewriter_report,
                inputs=[rewriter_score_before, rewriter_score_after, rewriter_status] + rewriter_layer_boxes,
                outputs=[rewriter_report],
            )

        # ── Request Custom Agent Tab (public version) ──
        with gr.TabItem("✉️ Request Custom Agent"):
            gr.Markdown("""
            ## Request a Custom Agent
            Tell the 4CBON2 team what you need. Requests may be free or paid depending on complexity.
            Submitting prepares an email to **mohamtur1@gmail.com**; review the draft and press Send in your email client.
            """)
            request_email = gr.Textbox(label="Your email (required)", placeholder="you@example.com")
            request_description = gr.Textbox(
                label="Agent description (required)", lines=6,
                placeholder="What should the agent do? Include inputs, outputs, tools, and an example workflow."
            )
            request_complexity = gr.Dropdown(
                choices=["Simple", "Moderate", "Complex"], value="Simple", label="Complexity level"
            )
            request_notes = gr.Textbox(label="Additional notes (optional)", lines=3)
            request_submit = gr.Button("Prepare Email Request", variant="primary")
            request_status = gr.Markdown()

            def request_custom_agent(email, description, complexity, notes):
                from urllib.parse import quote
                email = (email or "").strip()
                description = (description or "").strip()
                if not email or "@" not in email or "." not in email.rsplit("@", 1)[-1]:
                    return "❌ Please enter a valid email address."
                if not description:
                    return "❌ Please describe the custom agent you want."
                subject = quote(f"4CBON2 Custom Agent Request — {complexity or 'Simple'}")
                body = quote(
                    f"Requester email: {email}\n\nComplexity: {complexity or 'Simple'}\n\n"
                    f"Agent description:\n{description}\n\nAdditional notes:\n{(notes or '').strip() or '(none)'}"
                )
                return f"✅ Request prepared. [Open your email draft](mailto:mohamtur1@gmail.com?subject={subject}&body={body})"

            request_submit.click(
                fn=request_custom_agent,
                inputs=[request_email, request_description, request_complexity, request_notes],
                outputs=[request_status],
            )

        # ── Data Dashboard Tab ──
        with gr.TabItem("📊 Data Dashboard"):
            gr.Markdown("""
            ## Task Memory Visualization

            Visualize your agent task history with interactive Plotly charts.

            **Metrics:**
            - Task response length over time
            - Subtask distribution
            - Common words in task goals
            """)

            dashboard_btn = gr.Button("🔄 Load Dashboard", variant="primary")
            dashboard_output = gr.Textbox(label="Status", interactive=False)

            with gr.Row():
                dashboard_plot1 = gr.Plot(label="Task Timeline")
                dashboard_plot2 = gr.Plot(label="Subtask Distribution")

            with gr.Row():
                dashboard_plot3 = gr.Plot(label="Goal Word Frequency")

            def load_dashboard():
                try:
                    figures, error = create_plotly_dashboard()
                    if error:
                        return f"❌ {error}", None, None, None

                    if not figures:
                        return "❌ No figures generated", None, None, None

                    # Return up to 3 figures
                    fig1 = figures[0] if len(figures) > 0 else None
                    fig2 = figures[1] if len(figures) > 1 else None
                    fig3 = figures[2] if len(figures) > 2 else None

                    return f"✅ Loaded {len(figures)} visualization(s)", fig1, fig2, fig3
                except Exception as e:
                    return f"❌ Error: {str(e)}", None, None, None

            dashboard_btn.click(
                fn=load_dashboard,
                inputs=[],
                outputs=[dashboard_output, dashboard_plot1, dashboard_plot2, dashboard_plot3]
            )

        # ── Agent Status Tab ──
        with gr.TabItem("📊 Agent Status"):
            gr.Markdown("View all agent conversation histories.")
            refresh_btn = gr.Button("Refresh")
            agent_status_display = gr.Markdown("Click refresh to load.")

            def get_agent_status():
                output = "## 📊 Agent Status\n\n"
                for agent_id in get_all_agents():
                    agent = load_agent(agent_id)
                    history_len = len(agent.get("conversation_history", []))
                    output += f"- **{agent_id}**: {history_len} messages\n"
                return output

            refresh_btn.click(fn=get_agent_status, outputs=[agent_status_display])
            demo.load(fn=get_agent_status, outputs=[agent_status_display])

        # ── Diagnostics Tab ──
        with gr.TabItem("🩺 Diagnostics"):
            gr.Markdown(f"""
            ## Gemini Call Diagnostics

            Every Gemini call this Space process has made, newest first. Use this when a run
            fails or returns nothing — the **Finish reason** and the **Out tok / Think tok**
            split say immediately whether thinking ate the output budget.

            `MAX_TOKENS` with `Out tok = 0` and a large `Think tok` means the cap was too
            small for the model's internal reasoning.

            **Active policy:** {budget_policy_summary()}
            """)
            diag_btn = gr.Button("🔄 Refresh", variant="primary")
            diag_clear_btn = gr.Button("🗑 Clear log", variant="secondary")
            diag_summary = gr.Markdown("No Gemini calls recorded yet.")
            diag_table = gr.Dataframe(
                headers=DIAGNOSTIC_HEADERS,
                type="array",
                label="Recent Gemini calls (newest first)",
                interactive=False,
                wrap=True,
                max_height=420,
            )

            def refresh_diagnostics():
                summary, rows = build_diagnostics_view()
                return summary, rows

            def clear_diagnostics():
                with _DIAG_LOCK:
                    DIAGNOSTICS.clear()
                return "Cleared. No Gemini calls recorded yet.", []

            diag_btn.click(fn=refresh_diagnostics, inputs=[], outputs=[diag_summary, diag_table])
            diag_clear_btn.click(fn=clear_diagnostics, inputs=[], outputs=[diag_summary, diag_table])

        # ── About Tab ──
        with gr.TabItem("ℹ️ About"):
            gr.Markdown(f"""
            ## About this Space

            **4CBON2** is a 12-agent cognitive ecosystem with source-grounded research retrieval and a
            16-layer answer-rewriting pipeline.

            | Component | Value |
            | --- | --- |
            | LLM | Google Generative AI API (`google-genai`) |
            | Default model | `{DEFAULT_MODEL_NAME}` |
            | Pipeline order | `{PIPELINE_ORDER_LABEL}` |
            | Vector store | ChromaDB — `{EMBEDDING_LABEL}` |
            | Runtime data | `{DATA_DIR}` (ephemeral) |
            | Source notebook | [`4CBOn2_Gemini2c.ipynb`](https://github.com/mohamtur1/4CBOn2/blob/main/4CBOn2_Gemini2c.ipynb) |

            ### 🔑 Your API key
            * Create one free at [Google AI Studio](https://aistudio.google.com/apikey).
            * It is held **in this browser session's memory only** — never written to disk, never logged.
            * Each request is isolated via `contextvars`, so concurrent visitors cannot use your key or
              your quota.
            * Optionally set `GEMINI_API_KEY` as a **Space secret** to run without pasting a key. Be aware
              that on a public Space this makes *your* key and quota available to every visitor.

            ### ⚠️ Cost awareness
            One **AI Rewriter** run makes roughly 20 Gemini calls (17 layers plus a 3-call median score
            before and after). One **Agent Mode** run can make 15–50 calls depending on the plan.
            `gemini-3.6-flash` is billed per token on your key — and on Gemini 3 **thinking tokens are
            billed as output tokens**.

            ### 🧮 Token budget policy
            {budget_policy_summary()}

            In the original Colab notebooks `max_tokens` was accepted by the wrapper and then **silently
            dropped** — the only call site was `ai.generate_text(prompt=..., model_name=..., stream=True)`.
            There was never any output cap, so values like `max_tokens=5` on the LP layer were decorative.

            This API honours the cap, and on Gemini 3 `max_output_tokens` is a **combined** budget for
            thinking + visible output. A cap of 5 is consumed entirely by internal reasoning and returns
            `finish_reason=MAX_TOKENS` with zero visible characters.

            So the notebook's numbers are treated as **hints**: floored at `{MIN_OUTPUT_TOKENS}`,
            escalating x{BUDGET_ESCALATION_FACTOR} to at most `{MAX_OUTPUT_TOKENS_CEILING}` when a call
            comes back empty, with thinking pinned to `{THINKING_LEVEL}`. The cap is never omitted,
            because an uncapped Gemini 3 call can hang indefinitely.

            Tune via Space secrets: `GEMINI_THINKING_LEVEL`, `GEMINI_MIN_OUTPUT_TOKENS`,
            `GEMINI_MAX_OUTPUT_TOKENS_CEILING`, `FOURCBON2_AGENT_DEADLINE`
            (default `{AGENT_DEADLINE_SECONDS}`s), `FOURCBON2_HEARTBEAT`.

            After any failure, open the **🩺 Diagnostics** tab — it shows the finish reason and the
            output/thinking token split for every call.

            ### 🗂 Ephemeral storage
            Uploaded documents, cached live records, agent history and task memory live under `{DATA_DIR}`
            and **reset whenever the Space restarts or sleeps**. The curated AI / Mathematics / Science
            research indexes are reseeded automatically on every boot.
            """)

demo.queue()
demo.launch(server_name="0.0.0.0", server_port=int(os.environ.get("PORT", 7860)))


# @@SECTION:orch_helpers@@
# ============================================================
# CELL 5a — Orchestrator resilience helpers
# ============================================================
#
# Agent Mode is the fragile part of this app: a 12-subtask plan with up to 3 tool
# iterations each is 30+ sequential Gemini calls. On a Space that can outlive a
# proxy timeout, and when Gradio cancels the generator it raises GeneratorExit —
# which derives from BaseException, so the notebook's `except Exception` never
# sees it and the run died without recording anything.

AGENT_DEADLINE_SECONDS = int(os.environ.get("FOURCBON2_AGENT_DEADLINE", "600"))
HEARTBEAT_INTERVAL_SECONDS = int(os.environ.get("FOURCBON2_HEARTBEAT", "15"))

# Live view of the in-flight run, so a cancelled run can still be persisted.
_ACTIVE_RUN = {"goal": None, "results": []}


def call_with_heartbeat(fn, *args, label="the model", interval=None, **kwargs):
    """Run ``fn`` in a worker thread, yielding heartbeats until it returns.

    Yields ``("hb", text)`` tuples while waiting and a final ``("result", value)``.
    The worker gets a copy of this request's context so it can still see the
    visitor's API key.
    """
    interval = interval or HEARTBEAT_INTERVAL_SECONDS
    box = {}
    ctx = contextvars.copy_context()

    def runner():
        try:
            box["value"] = ctx.run(fn, *args, **kwargs)
        except BaseException as exc:          # re-raised in the caller's thread
            box["error"] = exc

    worker = threading.Thread(target=runner, daemon=True, name=f"4cbon2-{label}")
    worker.start()
    waited = 0
    while worker.is_alive():
        worker.join(interval)
        if worker.is_alive():
            waited += interval
            yield ("hb", f"⏱️ `{label}` still working… {waited}s elapsed\n")
    if "error" in box:
        raise box["error"]
    yield ("result", box.get("value"))


def persist_partial_run(reason):
    """Save whatever a cancelled/interrupted run produced, so the work is not lost.

    Called from the UI's GeneratorExit handler: by then the generator can no
    longer yield to the browser, but it can still write to task memory, which the
    Data Dashboard reads.
    """
    goal = _ACTIVE_RUN.get("goal")
    results = list(_ACTIVE_RUN.get("results") or [])
    try:
        log_event("orchestrator_interrupted", {"goal": goal, "reason": reason,
                                               "completed": len(results)})
    except Exception:
        pass
    if not goal or not results:
        return 0
    try:
        summary = "\n".join(f"Step {s['step']}: {s['subtask']} → {s['specialist']}" for s in results)
        body = "\n\n".join(
            f"### Step {s['step']}: {s['subtask']} ({s['specialist']})\n{s['result']}" for s in results)
        save_task_memory(goal, summary,
                         f"⚠️ {reason}. Synthesis never ran; these are the raw specialist "
                         f"reports recovered from the interrupted session.\n\n{body}")
    except Exception as exc:
        print(f"⚠️ Could not persist partial run: {exc}")
        return 0
    return len(results)
