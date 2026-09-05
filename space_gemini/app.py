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


def ask_stream(question, context=None):
    """Stream a source-grounded, five-part frontier-research answer."""
    prompt_template = """You are the 4CBON2 Frontier Research Assistant: a rigorous, multidisciplinary research partner in artificial intelligence, mathematics, computer science, and the natural sciences.

The user may ask an ambitious open-ended question such as how to engineer an AGI-oriented agent, how one might attempt a Millennium Prize Problem, or how to investigate an unresolved scientific problem. Give the most useful answer that present evidence permits, but never imply that AGI has already been achieved or that an open theorem has been proved when it has not.

EVIDENCE RULES
- The RESEARCH CONTEXT contains retrieved records labelled [S1], [S2], and so on. Treat snippets as leads, not automatically as established truth.
- Cite a source as [S#] only when that exact source supports the claim. Never invent a citation, paper, theorem, result, experiment, URL, or database record.
- Distinguish established results, informed inference, and speculation. Mention conflicting evidence and missing data.
- Prefer primary literature, official problem statements, standards, and reproducible evidence. State when a claim needs expert or current-source verification.
- For a mathematical proof attempt: state definitions and assumptions, identify the exact new lemma needed, test edge cases and known obstructions, and label every unproved step. Do not present a sketch as a proof.
- For an AGI-oriented system: separate currently buildable components from AGI hypotheses; include architecture, data, tools, memory, planning, evaluation, security, alignment, and staged experiments.
- For scientific questions: propose falsifiable hypotheses, controls, measurements, uncertainty analysis, replication, and ethical/safety constraints where pertinent.

ANSWER FORMAT — use these five clear headings:
1. PROBLEM FORMULATION — Define the goal, scope, assumptions, success criteria, and whether it is open or unresolved.
2. EVIDENCE AND ANALOGIES — Synthesize the retrieved evidence and the most relevant parallels, with [S#] citations.
3. CANDIDATE APPROACH — Give a concrete architecture, proof strategy, model, experiment, or research program. Break it into executable stages.
4. CRITICAL TESTS — Identify failure modes, counterexamples, bottlenecks, safety issues, and decisive validation tests.
5. BEST CURRENT ANSWER — Answer directly; separate what can be done now from what remains unknown, and list the next three highest-value actions.

End with a short SOURCES USED section listing only the [S#] records actually cited. If the context is weak or unavailable, say so and provide a clearly labelled provisional answer instead of fabricating support.

{context_prefix}QUESTION: {question}
"""
    context_prefix = ""
    if context:
        context_prefix = f"RESEARCH CONTEXT:\n{context}\n\n"
    formatted_prompt = prompt_template.format(question=question, context_prefix=context_prefix)
    full_text = generate_text(formatted_prompt, max_tokens=4096, temperature=0.35, stream=False)
    if full_text.startswith("⚠️"):
        yield full_text
        return
    words = full_text.split()
    chunk = ""
    for i, word in enumerate(words):
        chunk += word + " "
        if (i + 1) % 5 == 0 or i == len(words) - 1:
            yield chunk
            chunk = ""

def ask(question, context=None):
    """Get a complete answer (non-streaming aggregation)."""
    return "".join(ask_stream(question, context=context))


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


CURATED_DATABASES = {
    "ai": [
        {
            "title": "AGI scope and claims",
            "text": "There is no universally accepted operational definition or demonstrated implementation of artificial general intelligence. An AGI-oriented engineering project should declare measurable capability, generalization, autonomy, resource, robustness, and safety criteria rather than treating AGI as a binary label.",
            "url": "https://www.nist.gov/artificial-intelligence"
        },
        {
            "title": "Agent architecture baseline",
            "text": "A currently buildable AI agent can combine a foundation model with task decomposition, a constrained tool interface, retrieval, working and episodic memory, planning, execution monitoring, reflection, and human approval gates. Every tool action should be typed, permissioned, logged, reversible where possible, and evaluated independently of fluent output.",
            "url": "https://arxiv.org/abs/2309.07864"
        },
        {
            "title": "World models and planning",
            "text": "General-purpose agents need models that predict consequences under interventions, not only next-token continuation. Candidate research directions include learned world models, model-based reinforcement learning, search, causal representation learning, hierarchical planning, and continual adaptation under distribution shift.",
            "url": "https://arxiv.org/list/cs.AI/recent"
        },
        {
            "title": "Memory and retrieval",
            "text": "Agent memory should separate immutable instructions, short-lived working state, episodic traces, and curated semantic knowledge. Retrieval quality must be measured with relevance, provenance, freshness, access-control, poisoning-resistance, and downstream task metrics.",
            "url": "https://arxiv.org/list/cs.CL/recent"
        },
        {
            "title": "Evaluation before autonomy",
            "text": "Evaluate an agent on held-out tasks, contamination-resistant tests, calibration, tool success, long-horizon reliability, adversarial robustness, cost, latency, and safe refusal. Capability benchmarks alone do not establish general intelligence or safe deployment.",
            "url": "https://crfm.stanford.edu/helm/"
        },
        {
            "title": "Risk management",
            "text": "A trustworthy AI development process maps risks, measures them, manages them, and governs the full lifecycle. High-impact autonomous actions require least privilege, sandboxing, rate limits, monitoring, incident response, and human accountability.",
            "url": "https://www.nist.gov/itl/ai-risk-management-framework"
        },
        {
            "title": "AI research literature database directory",
            "text": "Pertinent AI evidence sources include arXiv for preprints, OpenAlex and Crossref for scholarly metadata, Semantic Scholar for citation-linked discovery, Papers with Code for implementations and benchmarks, and official standards or benchmark sites for current protocols. Preprints should not be treated as peer-reviewed evidence.",
            "url": "https://openalex.org/"
        },
        {
            "title": "Reproducible AI experiments",
            "text": "A credible AI research result records datasets and licenses, train-validation-test splits, contamination checks, model and optimizer versions, seeds, compute, ablations, uncertainty intervals, negative results, and an executable evaluation harness.",
            "url": "https://paperswithcode.com/"
        },
        {
            "title": "Alignment as an empirical program",
            "text": "Alignment work includes specification, oversight, interpretability, robustness, scalable evaluation, red teaming, monitoring, and governance. A claim of alignment requires explicit threat models and evidence across anticipated and unanticipated operating conditions.",
            "url": "https://www.nist.gov/artificial-intelligence"
        },
        {
            "title": "AGI-oriented staged roadmap",
            "text": "A defensible roadmap starts with a narrow sandboxed agent, establishes a baseline, adds one capability at a time, runs adversarial and long-horizon evaluations, studies generalization and transfer, and expands permissions only when evidence satisfies predefined safety gates.",
            "url": "https://crfm.stanford.edu/helm/"
        },
    ],
    "mathematics": [
        {
            "title": "Clay Millennium Prize Problems — official source",
            "text": "The official Clay Mathematics Institute problem descriptions and rules are the authority for the Millennium Prize Problems. Before attempting a problem, retrieve the current official statement and status, define every term exactly, and identify which claimed step is not already known.",
            "url": "https://www.claymath.org/millennium-problems/"
        },
        {
            "title": "P versus NP",
            "text": "P versus NP asks whether every decision problem whose proposed solutions can be verified in polynomial time can also be solved in polynomial time. Any approach must respect relativization, natural-proofs, and algebrization barriers where applicable and must specify the computational model and uniformity assumptions.",
            "url": "https://www.claymath.org/millennium/p-vs-np/"
        },
        {
            "title": "Riemann Hypothesis",
            "text": "The Riemann Hypothesis asserts that every nontrivial zero of the Riemann zeta function has real part one half. Numerical verification of many zeros is evidence but not a proof; an attempt must bridge analytic continuation, the functional equation, and a valid argument covering all nontrivial zeros.",
            "url": "https://www.claymath.org/millennium/riemann-hypothesis/"
        },
        {
            "title": "Navier–Stokes existence and smoothness",
            "text": "The three-dimensional incompressible Navier–Stokes problem asks for a proof of global smooth solutions under the specified initial conditions or a valid breakdown example. Computation and turbulence intuition cannot replace the required global estimates and exact regularity argument.",
            "url": "https://www.claymath.org/millennium/navier-stokes-equation/"
        },
        {
            "title": "Yang–Mills existence and mass gap",
            "text": "The Yang–Mills problem requires a mathematically rigorous construction of quantum Yang–Mills theory on four-dimensional Euclidean space for a compact simple gauge group and proof of a positive mass gap. Perturbative or numerical physics evidence alone does not meet the statement.",
            "url": "https://www.claymath.org/millennium/yang-mills-the-maths-gap/"
        },
        {
            "title": "Hodge Conjecture",
            "text": "The Hodge Conjecture concerns whether certain rational cohomology classes of smooth projective complex varieties are rational linear combinations of classes of algebraic cycles. An attempt must preserve the exact rational, projective, and smooth hypotheses and distinguish known special cases.",
            "url": "https://www.claymath.org/millennium/hodge-conjecture/"
        },
        {
            "title": "Birch and Swinnerton-Dyer Conjecture",
            "text": "The Birch and Swinnerton-Dyer Conjecture relates the rank of an elliptic curve over the rationals to the order of vanishing of its L-function at one. Experimental agreement or finite computations do not establish the general statement.",
            "url": "https://www.claymath.org/millennium/birch-and-swinnerton-dyer-conjecture/"
        },
        {
            "title": "Poincaré Conjecture",
            "text": "The Poincaré Conjecture is the solved Millennium Prize Problem, resolved through Grigori Perelman's work on Ricci flow building on Richard Hamilton. It is useful as a model of deep proof verification, but it should not be presented as still open.",
            "url": "https://www.claymath.org/millennium/poincare-conjecture/"
        },
        {
            "title": "Proof-attempt protocol",
            "text": "A responsible open-problem attempt starts from the official statement, maps equivalent formulations and known partial results, selects the smallest plausible new lemma, proves it with all quantifiers explicit, actively searches for counterexamples, and obtains independent specialist review. A gap, numerical pattern, or unchecked symbolic derivation is not a proof.",
            "url": "https://www.claymath.org/millennium-problems/"
        },
        {
            "title": "Formal verification",
            "text": "Proof assistants can expose missing assumptions and mechanically check a formalized argument, but formalization does not make a false key lemma true. Lean's mathlib is a large community mathematical library useful for checking dependencies and machine-verifiable steps.",
            "url": "https://leanprover-community.github.io/mathlib4_docs/"
        },
        {
            "title": "Mathematics database directory",
            "text": "Useful mathematics sources include arXiv mathematics categories for preprints, OpenAlex and Crossref for discovery and DOI metadata, Semantic Scholar for citation exploration, zbMATH Open for mathematical indexing, OEIS for integer sequences, LMFDB for explicit number-theoretic objects, and MathSciNet where access is available.",
            "url": "https://zbmath.org/"
        },
        {
            "title": "Literature and priority check",
            "text": "Before claiming a new theorem, search multiple independent indexes, trace citations to primary papers, verify whether the result has an existing name or stronger form, and record exact bibliographic identifiers. Lack of a search result is not evidence of novelty.",
            "url": "https://www.crossref.org/"
        },
    ],
    "science": [
        {
            "title": "Scientific inference baseline",
            "text": "A scientific answer should distinguish observations, measurement models, causal hypotheses, predictions, and decisions. Good hypotheses are falsifiable, compared against alternatives, and evaluated with uncertainty rather than selected only because they fit existing data.",
            "url": "https://www.nist.gov/services-resources"
        },
        {
            "title": "Experimental design",
            "text": "A strong experiment pre-registers primary outcomes where practical, uses appropriate controls, randomization and blinding where applicable, justifies sample size, defines exclusion criteria in advance, and reports effect sizes and uncertainty intervals rather than relying only on thresholded significance.",
            "url": "https://www.ncbi.nlm.nih.gov/"
        },
        {
            "title": "Reproducibility",
            "text": "Reproducible science records raw-data provenance, calibration, protocols, software and environment versions, analysis code, sensitivity analyses, and negative results. Independent replication and convergent measurement are stronger than repeated analysis of one dataset.",
            "url": "https://www.nist.gov/"
        },
        {
            "title": "PubMed",
            "text": "PubMed is a primary discovery database for biomedical and life-science literature maintained by the US National Library of Medicine. Search results are bibliographic records; study design and full text must be assessed before using a result as evidence.",
            "url": "https://pubmed.ncbi.nlm.nih.gov/"
        },
        {
            "title": "Europe PMC",
            "text": "Europe PMC indexes life-science publications, preprints, grants, and links to openly available full text. Version and peer-review status should be checked because a preprint and its later journal article can differ.",
            "url": "https://europepmc.org/"
        },
        {
            "title": "Cross-disciplinary literature",
            "text": "OpenAlex, Crossref, and Semantic Scholar support broad scholarly discovery and citation tracing. Their metadata can be incomplete or duplicated, so decisive claims should be checked against the primary paper and publisher or repository record.",
            "url": "https://openalex.org/"
        },
        {
            "title": "Preprints",
            "text": "arXiv provides rapid access to physics, mathematics, computer science, quantitative biology, statistics, and related preprints. A preprint may be valuable and current but should not be described as peer reviewed unless a separate journal record confirms that status.",
            "url": "https://arxiv.org/"
        },
        {
            "title": "Domain-specific science database directory",
            "text": "Depending on the question, pertinent sources may include NASA ADS for astronomy and physics, NCBI databases for biology, Protein Data Bank for structures, UniProt for proteins, ClinicalTrials.gov for registered trials, GenBank for sequences, USGS for earth science, and NIST for standards and reference data.",
            "url": "https://www.ncbi.nlm.nih.gov/home/data/"
        },
        {
            "title": "Causal claims",
            "text": "Causal conclusions require a defensible identification strategy such as randomization, natural experiments, valid instruments, discontinuities, longitudinal controls, or an explicit causal model with sensitivity analysis. Correlation, prediction accuracy, and mechanistic plausibility alone are insufficient.",
            "url": "https://www.ncbi.nlm.nih.gov/"
        },
        {
            "title": "Safety and ethics",
            "text": "Research involving people, animals, pathogens, hazardous materials, ecosystems, or dual-use capabilities requires the applicable ethical review, biosafety, security, consent, privacy, and regulatory controls before execution. A literature-generated protocol is not a substitute for qualified oversight.",
            "url": "https://www.nih.gov/health-information/nih-clinical-research-trials-you/basics"
        },
        {
            "title": "Model validation",
            "text": "A scientific model should be checked for dimensional consistency, limiting cases, parameter identifiability, out-of-sample prediction, residual structure, robustness to plausible measurement error, and comparison with simpler baselines.",
            "url": "https://www.nist.gov/services-resources/software"
        },
    ],
}


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


# ============================================================
# CELL 4 — 12 Agent Profiles + Tool Registry + DB Helpers
# ============================================================

AGENT_PROFILES = {
    "Default General Assistant": {
        "system_prompt": "You are a helpful general assistant operating within the 4CBON2 architecture.",
        "required_api": None
    },
    "New Autonomous Agent": {
        "system_prompt": """You are the Autonomous Orchestrator Agent for the 4CBON2 ecosystem.
Your role is to:
1. Receive a complex goal from the user.
2. Break it down into 2-4 concrete subtasks.
3. For each subtask, select the most appropriate specialist agent from the list below.
4. Delegate the subtask to that specialist and collect their response.
5. Synthesise all specialist responses into a final, cohesive answer.

Available specialist agents and their expertise:
- Sales Qualification: Lead scoring, BANT criteria, pipeline readiness.
- Legal Document Intelligence: Clause analysis, regulatory compliance, liability extraction.
- Competitive Intelligence: Competitor tracking, market shifts, positioning analysis.
- Customer Engagement: Messaging, sentiment parsing, communication routing.
- Content Strategy: Editorial calendars, copy structuring, keyword architecture.
- Marketing Automation: Campaign triggers, conversion funnels, broadcast sequencing.
- Evidence Management: Data cross-referencing, source auditing, factual verification.
- Scheduling: Time-block coordination, calendar management, bottleneck resolution.
- Legal Intake: Client screening, conflict checks, disclosure structuring.
- Scientific Research: Literature synthesis, data parsing, hypothesis evaluation.
""",
        "required_api": None
    },
    "Sales Qualification": {
        "system_prompt": "You are a Sales Qualification agent. Focus on lead scoring, BANT criteria assessment, and pipeline readiness tracking.",
        "required_api": "CRM_API_KEY"
    },
    "Legal Document Intelligence": {
        "system_prompt": "You are a Legal Document Intelligence agent. Analyze clauses, verify regulatory compliance, and extract liability terms from legal documents.",
        "required_api": "DOCUSIGN_API_KEY"
    },
    "Competitive Intelligence": {
        "system_prompt": "You are a Competitive Intelligence agent. Scrape competitor updates, track market shifts, and analyze positioning strategies.",
        "required_api": "SEO_API_KEY"
    },
    "Customer Engagement": {
        "system_prompt": "You are a Customer Engagement agent. Craft personalized messaging, parse inbound sentiment, and handle communications routing.",
        "required_api": "COMM_API_KEY"
    },
    "Content Strategy": {
        "system_prompt": "You are a Content Strategy agent. Optimize editorial calendars, structure high-converting copy, and manage keyword architecture.",
        "required_api": "SEO_API_KEY"
    },
    "Marketing Automation": {
        "system_prompt": "You are a Marketing Automation agent. Orchestrate campaign triggers, analyze conversion funnels, and manage broadcast sequences.",
        "required_api": "SOCIAL_SCRAPER_API_KEY"
    },
    "Evidence Management": {
        "system_prompt": "You are an Evidence Management agent. Cross-reference empirical data, audit source trails, and verify factual consistency.",
        "required_api": "S3_VAULT_KEY"
    },
    "Scheduling": {
        "system_prompt": "You are a Scheduling agent. Coordinate time-blocks, handle calendar availability, and resolve logistical bottlenecks.",
        "required_api": "CALENDAR_API_KEY"
    },
    "Legal Intake": {
        "system_prompt": "You are a Legal Intake agent. Screen new client cases, check for conflicts of interest, and structure initial disclosures.",
        "required_api": "DOCUSIGN_API_KEY"
    },
    "Scientific Research": {
        "system_prompt": "You are a Scientific Research agent. Synthesize peer-reviewed literature, parse clinical or technical data, and evaluate hypotheses.",
        "required_api": "PUBMED_API_KEY"
    }
}

# Spaces have no Google Drive; tool logs live under DATA_DIR.
LOG_DIR = os.path.join(DATA_DIR, "4cbon2_logs")
os.makedirs(LOG_DIR, exist_ok=True)
LOG_FILE = os.path.join(LOG_DIR, f"tool_log_{datetime.now().strftime('%Y%m%d')}.jsonl")

def log_tool_call(tool_name, input_data, result):
    try:
        with open(LOG_FILE, "a") as f:
            f.write(json.dumps({
                "timestamp": datetime.now().isoformat(),
                "tool": tool_name,
                "input": str(input_data)[:500],
                "result_preview": str(result)[:500]
            }) + "\n")
    except Exception as e:
        print(f"⚠️ Log warning: {e}")

STOPWORDS = {
    "of", "the", "a", "an", "for", "to", "in", "on", "is", "are", "and", "or",
    "competitors", "competitor", "alternatives", "alternative", "best", "app",
    "apps", "software", "productivity", "who", "what", "current", "list",
    "similar", "tools", "top", "rated", "reviews", "review", "latest", "new"
}

def _is_relevant(query, text):
    words = re.findall(r"\w+", query)
    keywords = [w for w in words if w.lower() not in STOPWORDS and not (w.isdigit() and len(w) < 4)]
    if not keywords:
        return True
    text_lower = text.lower()
    for kw in keywords:
        if kw.lower() in text_lower:
            return True
    return False

def web_search(query):
    try:
        with DDGS() as ddgs:
            results = list(ddgs.text(query, max_results=5))
        if not results:
            return "No results found."
        formatted = []
        for r in results:
            title = r.get("title", "")
            body = r.get("body", "")
            href = r.get("href", "")
            formatted.append(f"{title}\n{body}\n{href}")
        combined = "\n\n".join(formatted)
        return combined if _is_relevant(query, combined) else "Results found but not highly relevant."
    except Exception as e:
        return f"Search error: {e}"

def read_file(file_path):
    try:
        if file_path.endswith(".txt"):
            with open(file_path, "r", errors="ignore") as f:
                return f.read()
        elif file_path.endswith(".pdf"):
            reader = PdfReader(file_path)
            texts = []
            for page in reader.pages:
                t = page.extract_text()
                if t:
                    texts.append(t)
            return "\n".join(texts)
        elif file_path.endswith(".docx"):
            doc = docx.Document(file_path)
            return "\n".join([para.text for para in doc.paragraphs])
        else:
            return "Unsupported file type. Use .txt, .pdf, or .docx"
    except Exception as e:
        return f"File read error: {e}"

def query_database(sql, db_path=os.path.join(DATA_DIR, "4cbon2_data.db")):
    try:
        cleaned = sql.strip().upper()
        if not cleaned.startswith("SELECT"):
            return "❌ Only SELECT queries are allowed for safety."
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        cursor.execute(sql)
        rows = cursor.fetchall()
        conn.close()
        return "\n".join([str(row) for row in rows]) if rows else "No results."
    except Exception as e:
        return f"Database error: {e}"

def save_note(content, filename=None):
    try:
        if filename is None:
            filename = f"note_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        path = os.path.join(DATA_DIR, "4cbon2_notes", filename)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            f.write(content)
        return f"Note saved to {path}"
    except Exception as e:
        return f"Save error: {e}"

def get_datetime():
    return datetime.now().strftime("Date: %Y-%m-%d | Time: %H:%M:%S")

def http_request(input_str):
    try:
        parsed = json.loads(input_str)
        url = parsed.get("url")
        if not url:
            return "Missing 'url' in input."
        fields = parsed.get("fields", [])
        response = requests.get(url, timeout=10, headers={"User-Agent": "Mozilla/5.0"})
        content_type = response.headers.get("Content-Type", "")
        if "application/json" in content_type:
            data = response.json()
        else:
            data = response.text
        if isinstance(data, dict) and len(json.dumps(data)) > 4000 and not fields:
            menu = {k: type(v).__name__ for k, v in data.items()}
            return f"Large response. Top-level keys: {json.dumps(menu, indent=2)}"
        if fields:
            result = {}
            for field in fields:
                parts = field.split(".")
                val = data
                for part in parts:
                    if isinstance(val, dict) and part in val:
                        val = val[part]
                    else:
                        val = None
                        break
                result[field] = val
            return json.dumps(result, indent=2)
        return json.dumps(data, indent=2)[:3000] if isinstance(data, (dict, list)) else str(data)[:3000]
    except Exception as e:
        return f"HTTP error: {e}"

def read_csv(file_path):
    try:
        with open(file_path, "r", newline="", errors="ignore") as f:
            rows = list(csv.reader(f))
        if not rows:
            return "CSV is empty."
        header = rows[0]
        preview = rows[1:6]
        return f"Columns: {', '.join(header)}\nRows: {len(rows)-1}\nPreview:\n" + "\n".join([str(r) for r in preview])
    except Exception as e:
        return f"CSV error: {e}"

def write_csv(data_json):
    try:
        rows = json.loads(data_json)
        if not isinstance(rows, list) or not rows:
            return "Input must be a non-empty list of dicts."
        filename = f"export_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
        path = os.path.join(DATA_DIR, "4cbon2_exports", filename)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        keys = list(rows[0].keys())
        with open(path, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=keys)
            writer.writeheader()
            writer.writerows(rows)
        return f"CSV saved to {path} ({len(rows)} rows)"
    except Exception as e:
        return f"CSV write error: {e}"

def generate_pdf(content):
    try:
        filename = f"report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.pdf"
        path = os.path.join(DATA_DIR, "4cbon2_reports", filename)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        pdf = FPDF()
        pdf.add_page()
        pdf.set_font("Helvetica", size=12)
        for line in content.split("\n"):
            pdf.multi_cell(0, 8, line, new_x=XPos.LMARGIN, new_y=YPos.NEXT)
        pdf.output(path)
        return f"PDF saved to {path}"
    except Exception as e:
        return f"PDF error: {e}"

def scrape_webpage(url):
    try:
        response = requests.get(url, timeout=10, headers={"User-Agent": "Mozilla/5.0"})
        soup = BeautifulSoup(response.text, "html.parser")
        for tag in soup(["script", "style", "nav", "footer", "header"]):
            tag.decompose()
        text = soup.get_text(separator="\n")
        lines = [line.strip() for line in text.split("\n") if line.strip()]
        return "\n".join(lines)[:3000] if lines else "No readable content."
    except Exception as e:
        return f"Scrape error: {e}"

TOOL_REGISTRY = {
    "web_search": {"function": web_search, "description": "Search the web for current information. Input: search query string.", "input": "query"},
    "read_file": {"function": read_file, "description": "Read contents of a .txt, .pdf, or .docx file. Input: file path string.", "input": "file_path"},
    "query_database": {"function": query_database, "description": "Run a SELECT SQL query against the local SQLite database. Input: SQL string.", "input": "sql"},
    "save_note": {"function": save_note, "description": "Save a text note to the app data directory. Input: content string.", "input": "content"},
    "get_datetime": {"function": get_datetime, "description": "Get the current date and time. No input required.", "input": None},
    "http_request": {"function": http_request, "description": "Fetch data from a URL. Input: JSON string like {'url': '...', 'fields': ['field1']}.", "input": "input_str"},
    "read_csv": {"function": read_csv, "description": "Read a CSV file and return columns, row count, and preview. Input: file path string.", "input": "file_path"},
    "write_csv": {"function": write_csv, "description": "Export data to a CSV file in the app data directory. Input: JSON list of objects.", "input": "data_json"},
    "generate_pdf": {"function": generate_pdf, "description": "Generate a PDF report from text content and save it to the app data directory. Input: text content string.", "input": "content"},
    "scrape_webpage": {"function": scrape_webpage, "description": "Fetch a webpage and extract its main readable text. Input: URL string.", "input": "url"}
}

def execute_tool(tool_name, tool_input=None):
    if tool_name not in TOOL_REGISTRY:
        return f"Unknown tool: {tool_name}"
    tool = TOOL_REGISTRY[tool_name]
    try:
        if tool["input"] is None:
            result = tool["function"]()
        else:
            result = tool["function"](tool_input)
    except Exception as e:
        result = f"Tool execution error: {e}"
    log_tool_call(tool_name, tool_input, result)
    return result

AGENT_DB_PATH = os.path.join(DATA_DIR, "4cbon2_agents.db")
os.makedirs(os.path.dirname(AGENT_DB_PATH), exist_ok=True)

def init_agent_db():
    conn = sqlite3.connect(AGENT_DB_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS agents (
            agent_id TEXT PRIMARY KEY,
            system_prompt TEXT,
            conversation_history TEXT,
            tools TEXT,
            created_at TEXT,
            updated_at TEXT
        )
    ''')
    conn.commit()
    conn.close()

def load_agent(agent_id):
    conn = sqlite3.connect(AGENT_DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        "SELECT agent_id, system_prompt, conversation_history, tools FROM agents WHERE agent_id = ?",
        (agent_id,)
    )
    row = cursor.fetchone()
    conn.close()
    if row:
        return {
            "agent_id": row[0],
            "system_prompt": row[1],
            "conversation_history": json.loads(row[2]) if row[2] else [],
            "tools": json.loads(row[3]) if row[3] else []
        }
    return None

def save_agent(agent_id, system_prompt, conversation_history, tools=None):
    if tools is None:
        tools = []
    conn = sqlite3.connect(AGENT_DB_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        INSERT OR REPLACE INTO agents (agent_id, system_prompt, conversation_history, tools, updated_at)
        VALUES (?, ?, ?, ?, ?)
    ''', (
        agent_id,
        system_prompt,
        json.dumps(conversation_history),
        json.dumps(tools),
        datetime.now().isoformat()
    ))
    conn.commit()
    conn.close()

def update_agent_conversation(agent_id, new_messages):
    agent = load_agent(agent_id)
    if agent is None:
        if agent_id in AGENT_PROFILES:
            agent = {
                "agent_id": agent_id,
                "system_prompt": AGENT_PROFILES[agent_id]["system_prompt"],
                "conversation_history": [],
                "tools": []
            }
        else:
            raise ValueError(f"Agent '{agent_id}' not found")
    agent["conversation_history"].extend(new_messages)
    save_agent(agent["agent_id"], agent["system_prompt"], agent["conversation_history"], agent["tools"])

def clear_agent_history(agent_id):
    agent = load_agent(agent_id)
    if agent:
        save_agent(agent_id, agent["system_prompt"], [], agent["tools"])

def get_all_agents():
    conn = sqlite3.connect(AGENT_DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT agent_id FROM agents")
    rows = cursor.fetchall()
    conn.close()
    return [row[0] for row in rows]

def ensure_agents_loaded():
    init_agent_db()
    for agent_id, profile in AGENT_PROFILES.items():
        if load_agent(agent_id) is None:
            save_agent(agent_id, profile["system_prompt"], [], [])
            print(f"✅ Agent '{agent_id}' created in DB.")

ensure_agents_loaded()

print("👥 12 Agent Profiles + 10 Tools loaded.")
print("Agents:", list(AGENT_PROFILES.keys()))
print("Tools:", list(TOOL_REGISTRY.keys()))



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


# ============================================================
# CELL 5 — Streaming Multi-Agent Orchestrator
# ============================================================

import json
import re
import os
from datetime import datetime

AUDIT_LOG_PATH = os.path.join(DATA_DIR, "4cbon2_audit.jsonl")
os.makedirs(os.path.dirname(AUDIT_LOG_PATH), exist_ok=True)

def log_event(event_type, details):
    try:
        record = {
            "timestamp": datetime.now().isoformat(),
            "event_type": event_type,
            "details": details
        }
        with open(AUDIT_LOG_PATH, "a") as f:
            f.write(json.dumps(record) + "\n")
    except Exception as e:
        print(f"⚠️ Audit log warning: {e}")

TASK_MEMORY_PATH = os.path.join(DATA_DIR, "4cbon2_task_memory.db")
os.makedirs(os.path.dirname(TASK_MEMORY_PATH), exist_ok=True)

def init_task_memory():
    conn = sqlite3.connect(TASK_MEMORY_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS task_memory (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            goal TEXT,
            subtasks TEXT,
            final_answer TEXT,
            timestamp TEXT
        )
    ''')
    conn.commit()
    conn.close()

def save_task_memory(goal, subtasks, final_answer):
    conn = sqlite3.connect(TASK_MEMORY_PATH)
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO task_memory (goal, subtasks, final_answer, timestamp) VALUES (?, ?, ?, ?)",
        (goal, subtasks, final_answer, datetime.now().isoformat())
    )
    conn.commit()
    conn.close()

init_task_memory()

def _extract_balanced(text, open_ch, close_ch):
    if not text:
        return None
    start = text.find(open_ch)
    if start == -1:
        return None
    depth = 0
    in_string = False
    escape = False
    for i in range(start, len(text)):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == '\\':
                escape = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == open_ch:
                depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth == 0:
                    return text[start:i + 1]
    return None

def extract_json_object(text):
    return _extract_balanced(text, '{', '}')

def extract_json_array(text):
    return _extract_balanced(text, '[', ']')

def _parse_agent_json(raw_response):
    if not raw_response:
        return None
    candidate = extract_json_object(raw_response)
    try:
        if candidate:
            return json.loads(candidate)
        return json.loads(raw_response)
    except Exception:
        return None

def build_tool_descriptions():
    lines = []
    for name, info in TOOL_REGISTRY.items():
        input_desc = info["input"] if info["input"] else "None"
        lines.append(f"- **{name}**: {info['description']} (input: {input_desc})")
    return "\n".join(lines)

def execute_agent(agent_id, user_message, context="", max_tool_iterations=3):
    agent = load_agent(agent_id)
    if agent is None:
        return f"❌ Agent '{agent_id}' not found."

    system_prompt = agent["system_prompt"]
    history = agent.get("conversation_history", [])

    tool_instructions = f"""You have access to these tools:
{build_tool_descriptions()}

To use a tool, respond with ONLY this JSON:
{{"action": "tool_call", "tool": "<tool_name>", "tool_input": "<input or null>"}}

To answer directly, respond with ONLY this JSON:
{{"action": "final_answer", "content": "<your answer>"}}

Valid JSON only. Max {max_tool_iterations} tool calls before final_answer."""

    prompt_parts = [f"System: {system_prompt}", tool_instructions]
    if context:
        prompt_parts.append(f"Context from other agents:\n{context}")
    for msg in history[-4:]:
        prompt_parts.append(f"{msg['role']}: {msg['content']}")
    prompt_parts.append(f"User: {user_message}")

    tool_call_log = []
    final_content = None

    for iteration in range(max_tool_iterations):
        full_prompt = "\n\n".join(prompt_parts)
        raw_response = safe_ask_raw(full_prompt, max_tokens=2048)

        parsed = _parse_agent_json(raw_response)

        if parsed is None:
            final_content = raw_response
            break

        action = parsed.get("action")

        if action == "tool_call":
            tool_name = parsed.get("tool", "")
            tool_input = parsed.get("tool_input")

            if tool_name not in TOOL_REGISTRY:
                prompt_parts.append(f"Assistant: {raw_response}")
                prompt_parts.append(f"Tool Result: ❌ Unknown tool '{tool_name}'. Available: {', '.join(TOOL_REGISTRY.keys())}")
                continue

            tool_result = execute_tool(tool_name, tool_input)
            tool_call_log.append({"tool": tool_name, "input": tool_input, "result": str(tool_result)[:300]})
            log_event("agent_tool_call", {
                "agent_id": agent_id,
                "tool": tool_name,
                "input": tool_input,
                "result_preview": str(tool_result)[:200]
            })

            prompt_parts.append(f"Assistant: {raw_response}")
            prompt_parts.append(f"Tool Result ({tool_name}): {str(tool_result)[:2000]}")
            continue

        elif action == "final_answer":
            final_content = parsed.get("content", raw_response)
            break
        else:
            final_content = raw_response
            break

    if final_content is None:
        forced_prompt = "\n\n".join(prompt_parts) + "\n\nYou must respond now with ONLY the final_answer JSON format."
        raw_response = safe_ask_raw(forced_prompt, max_tokens=2048)
        parsed = _parse_agent_json(raw_response)
        final_content = parsed.get("content", raw_response) if parsed else raw_response

    if is_llm_error(final_content) or "could not generate" in str(final_content or "").lower():
        fallback_prompt = f"You are a {agent_id} specialist. Provide a best-practice framework for your domain with key metrics, benchmarks, workflows, data collection methods, and improvement strategies."
        final_content = safe_ask_raw(fallback_prompt, max_tokens=1024)
        if is_llm_error(final_content):
            detail = str(final_content or "").strip()[:220]
            final_content = (f"⚠️ {agent_id} could not generate a response. "
                             f"Please provide more specific instructions or data. "
                             f"Last error: {detail}")

    if tool_call_log:
        tools_used_note = "\n\n---\n🔧 **Tools used:** " + ", ".join(t["tool"] for t in tool_call_log)
        final_content = final_content + tools_used_note

    update_agent_conversation(agent_id, [
        {"role": "user", "content": user_message},
        {"role": "assistant", "content": final_content}
    ])
    return final_content

def synthesize_batch(batch, goal, batch_num, total_batches):
    prompt = f"""Synthesise part {batch_num} of {total_batches} of a strategic audit.

Goal: {goal}

Specialist reports:
{json.dumps(batch, indent=2)}

Provide a CONCISE summary (under 200 words) of key findings, themes, and gaps."""
    result = safe_ask_raw(prompt, max_tokens=1024)
    print(f"[DEBUG] Batch {batch_num}/{total_batches}: {len(result)} chars")
    return result

def synthesize_final(batch_summaries, goal):
    prompt = f"""Create the final strategic report.

Goal: {goal}

Batch summaries:
{json.dumps(batch_summaries, indent=2)}

Synthesise into a cohesive report with:
1. Executive summary
2. Clear sections
3. Integrated insights
4. Prioritised action plan

Final Report:"""
    print(f"[DEBUG] Final synthesis prompt: {len(prompt)} chars")
    result = safe_ask_raw(prompt, max_tokens=2048)
    print(f"[DEBUG] Final synthesis response: {len(result)} chars")
    return result

def generate_fallback_plan(goal):
    specialists = [a for a in AGENT_PROFILES.keys() if a not in ["New Autonomous Agent", "Default General Assistant"]]
    plan = []
    keyword_map = {
        "sales": "Sales Qualification", "lead": "Sales Qualification", "pipeline": "Sales Qualification",
        "legal": "Legal Document Intelligence", "contract": "Legal Document Intelligence",
        "compliance": "Legal Document Intelligence", "liability": "Legal Document Intelligence",
        "competitor": "Competitive Intelligence", "market": "Competitive Intelligence",
        "position": "Competitive Intelligence", "customer": "Customer Engagement",
        "engagement": "Customer Engagement", "messaging": "Customer Engagement",
        "sentiment": "Customer Engagement", "content": "Content Strategy",
        "seo": "Content Strategy", "blog": "Content Strategy", "social": "Content Strategy",
        "marketing": "Marketing Automation", "campaign": "Marketing Automation",
        "funnel": "Marketing Automation", "evidence": "Evidence Management",
        "data": "Evidence Management", "fact": "Evidence Management",
        "schedule": "Scheduling", "calendar": "Scheduling", "time": "Scheduling",
        "intake": "Legal Intake", "client": "Legal Intake", "conflict": "Legal Intake",
        "research": "Scientific Research", "paper": "Scientific Research", "technology": "Scientific Research"
    }
    used = set()
    for keyword, specialist in keyword_map.items():
        if keyword in goal.lower() and specialist not in used:
            plan.append({
                "subtask": f"Analyse {keyword} aspects",
                "specialist": specialist,
                "instructions": f"Provide comprehensive analysis related to '{keyword}'."
            })
            used.add(specialist)
    if not plan:
        plan = [
            {"subtask": "Analyse market and competitors", "specialist": "Competitive Intelligence", "instructions": "Provide trends and competitor mapping."},
            {"subtask": "Identify legal risks", "specialist": "Legal Document Intelligence", "instructions": "Summarise key compliance issues."},
            {"subtask": "Recommend strategy", "specialist": "Content Strategy", "instructions": "Develop a strategic plan."}
        ]
    return plan[:12]

def enforce_explicit_specialists(plan, goal):
    goal_lower = goal.lower()
    planned = {item.get("specialist") for item in plan}
    for name in AGENT_PROFILES.keys():
        if name in ("New Autonomous Agent", "Default General Assistant"):
            continue
        if name.lower() in goal_lower and name not in planned:
            plan.append({
                "subtask": f"Explicit request: apply {name} expertise",
                "specialist": name,
                "instructions": f"The user explicitly requested {name} analysis. Address it directly."
            })
    return plan

def run_orchestrator_stream(goal, model_name=None, deadline_seconds=None):
    # A wall-clock deadline, because 12 subtasks x up to 3 tool iterations is
    # 30+ sequential Gemini calls and a Space proxy will drop a run that takes
    # too long. On expiry we stop and report what completed, rather than dying.
    if deadline_seconds is None:
        deadline_seconds = AGENT_DEADLINE_SECONDS
    started_at = time.time()
    deadline_hit = False
    yield f"🚀 **Orchestrator started:** {goal}\n\n---\n"
    yield f"⏱️ Deadline {int(deadline_seconds)}s · thinking {THINKING_LEVEL or 'model default'} · output floor {MIN_OUTPUT_TOKENS} tokens\n\n"
    log_event("orchestrator_start", {"goal": goal, "deadline_seconds": deadline_seconds})

    yield "🔄 **Step 1:** Clearing orchestrator history...\n"
    clear_agent_history("New Autonomous Agent")
    yield "✅ Done.\n\n"

    yield "🧠 **Step 2:** Generating plan...\n"
    specialists = [a for a in AGENT_PROFILES.keys() if a not in ["New Autonomous Agent", "Default General Assistant"]]
    plan_prompt = f"""You are the Autonomous Orchestrator Agent.

User goal: {goal}

Break this into up to 12 subtasks using EVERY relevant specialist from:
{', '.join(specialists)}

If the goal explicitly names a specialist, you MUST include it.

Output as JSON array:
[
    {{"subtask": "...", "specialist": "...", "instructions": "..."}},
    ...
]

Valid JSON only. No other text."""
    plan_response = safe_ask_raw(plan_prompt, max_tokens=1024)
    yield f"📝 Plan response: {len(plan_response)} chars\n"

    try:
        candidate = extract_json_array(plan_response)
        if candidate:
            plan = json.loads(candidate)
        else:
            plan = json.loads(plan_response)
        if not isinstance(plan, list) or len(plan) == 0:
            raise ValueError("Empty plan")
    except Exception as e:
        yield f"⚠️ Plan parsing error: {e}\nUsing fallback.\n\n"
        plan = generate_fallback_plan(goal)
        yield f"📋 Fallback plan: {len(plan)} steps.\n\n"

    before = len(plan)
    plan = enforce_explicit_specialists(plan, goal)
    if len(plan) > before:
        yield f"🛡️ Guardrail: added {len(plan) - before} specialist(s).\n\n"

    subtask_results = []
    # Same list object, so _ACTIVE_RUN always reflects live progress.
    _ACTIVE_RUN["goal"] = goal
    _ACTIVE_RUN["results"] = subtask_results
    for i, item in enumerate(plan, 1):
        elapsed = time.time() - started_at
        if deadline_seconds and elapsed > deadline_seconds:
            deadline_hit = True
            yield (f"\n⏹️ **Deadline reached** ({int(deadline_seconds)}s) after "
                   f"{len(subtask_results)} of {len(plan)} subtask(s).\n"
                   f"Synthesising a partial report from what completed.\n\n")
            log_event("orchestrator_deadline", {"goal": goal, "elapsed_s": round(elapsed, 1),
                                                "completed": len(subtask_results), "planned": len(plan)})
            break
        subtask = item.get("subtask", f"Subtask {i}")
        specialist = item.get("specialist", "Default General Assistant")
        instructions = item.get("instructions", "Analyze thoroughly.")
        yield f"\n---\n**Step {i}/{len(plan)}:** {subtask}\n👤 `{specialist}`\n📋 {instructions}\n\n"

        if load_agent(specialist) is None:
            yield f"⚠️ '{specialist}' not found. Using Default.\n"
            specialist = "Default General Assistant"

        clear_agent_history(specialist)
        context = json.dumps([
            {"step": s["step"], "subtask": s["subtask"], "preview": s["result"][:150] + "..." if len(s["result"]) > 150 else s["result"]}
            for s in subtask_results
        ], indent=2)

        yield f"⏳ Executing `{specialist}`...\n"
        result = None
        for kind, payload in call_with_heartbeat(
            execute_agent,
            specialist,
            f"Task: {subtask}\n\nInstructions: {instructions}\n\nContext: {context}",
            label=specialist,
        ):
            if kind == "hb":
                yield payload
            else:
                result = payload
        subtask_results.append({"step": i, "subtask": subtask, "specialist": specialist, "result": result})
        yield f"✅ `{specialist}` done.\n📄 {result[:300]}{'...' if len(result) > 300 else ''}\n\n"

    yield "\n---\n🧬 **Final Synthesis...**\n"

    if not subtask_results:
        fallback = execute_agent("Default General Assistant", f"Answer directly: {goal}")
        final_answer = f"⚠️ No specialists generated. Fallback:\n\n{fallback}"
    else:
        batch_size = 3
        batches = [subtask_results[i:i+batch_size] for i in range(0, len(subtask_results), batch_size)]
        summaries = []
        for idx, batch in enumerate(batches, 1):
            yield f"📦 Synthesising batch {idx}/{len(batches)}...\n"
            summary = synthesize_batch(batch, goal, idx, len(batches))
            summaries.append({"batch": idx, "specialists": [r["specialist"] for r in batch], "summary": summary})
            yield f"✅ Batch {idx} done.\n\n"

        yield "🧬 Final synthesis...\n"
        final_answer = synthesize_final(summaries, goal)
        if not final_answer or not final_answer.strip():
            final_answer = "⚠️ Synthesis empty. Raw reports:\n\n" + "\n\n".join([s["result"] for s in subtask_results])

    subtasks_summary = "\n".join([f"Step {s['step']}: {s['subtask']} → {s['specialist']}" for s in subtask_results]) if subtask_results else "No subtasks."
    save_task_memory(goal, subtasks_summary, final_answer)

    yield "\n---\n# 🧠 Multi-Agent Report\n\n"
    yield f"## 🎯 Goal\n{goal}\n\n"
    yield f"## 📋 Execution\n{subtasks_summary}\n\n"
    if subtask_results:
        yield "## 📊 Reports\n"
        for s in subtask_results:
            yield f"\n### Step {s['step']}: {s['subtask']} ({s['specialist']})\n{s['result']}\n"
    if deadline_hit:
        yield ("\n> ⚠️ **Partial run** — the deadline expired before every planned subtask "
               f"ran. {len(subtask_results)} of {len(plan)} completed.\n")
    yield f"\n## 🧬 Final Answer\n{final_answer}\n\n---\n*Generated by 4CBON2 (Gemini Edition)*\n"

    log_event("orchestrator_complete", {"goal": goal, "steps": len(subtask_results),
                                        "deadline_hit": deadline_hit,
                                        "elapsed_s": round(time.time() - started_at, 1)})
    _ACTIVE_RUN["goal"] = None
    _ACTIVE_RUN["results"] = []

def run_orchestrator(goal, model_name=None):
    full = ""
    for chunk in run_orchestrator_stream(goal, model_name):
        full += chunk
    return full

def run_agent(goal, system_override=None):
    return run_orchestrator(goal)

print("⚙️ Orchestrator ready.")
print("Agents:", get_all_agents())



# ============================================================
# CELL 6 — Multidisciplinary RAG + Live Scholarly Databases
# ============================================================

import concurrent.futures
import xml.etree.ElementTree as ET
from urllib.parse import quote_plus

def chunk_text(text, max_chunk_size=800, overlap=100):
    if not text or not text.strip():
        return []
    paragraphs = [p.strip() for p in text.split('\n\n') if len(p.strip()) > 30]
    if len(paragraphs) >= 3:
        return paragraphs
    sentences = re.split(r'(?<=[.!?])\s+', text)
    chunks = []
    current = ""
    for sent in sentences:
        if len(current) + len(sent) < max_chunk_size:
            current += " " + sent
        else:
            if current:
                chunks.append(current.strip())
            current = sent
    if current:
        chunks.append(current.strip())
    if chunks:
        return chunks
    start = 0
    while start < len(text):
        end = start + max_chunk_size
        chunks.append(text[start:end].strip())
        start = end - overlap
    return [c for c in chunks if c]

def process_document(file_obj):
    if file_obj is None:
        return "No file uploaded."
    try:
        file_path = file_obj.name if hasattr(file_obj, 'name') else str(file_obj)
        text = read_file(file_path)
        if text.startswith(("File read error", "Unsupported file type")):
            return f"❌ {text}"
        if not text or not text.strip():
            return "❌ No extractable text found in file."
        chunks = chunk_text(text)
        if not chunks:
            return "❌ Could not create chunks from document."
        base_name = os.path.basename(file_path)
        file_key = hashlib.sha1(os.path.abspath(file_path).encode()).hexdigest()[:12]
        ids = [f"upload_{file_key}_{i:04d}" for i in range(len(chunks))]
        metadatas = [{
            "source": base_name,
            "title": base_name,
            "url": "uploaded-file",
            "domain": "uploaded",
            "type": "uploaded",
        } for _ in chunks]
        collection.upsert(documents=chunks, ids=ids, metadatas=metadatas)
        return f"✅ Indexed {len(chunks)} chunks from '{base_name}'. Total uploaded KB records: {collection.count()}"
    except Exception as e:
        return f"❌ Upload error: {e}"

DOMAIN_KEYWORDS = {
    "ai": {
        "agi", "agent", "artificial intelligence", "machine learning", "deep learning",
        "neural", "llm", "language model", "reinforcement learning", "robot", "alignment",
        "transformer", "computer vision", "autonomous", "reasoning model", "world model"
    },
    "mathematics": {
        "millennium", "proof", "theorem", "conjecture", "lemma", "riemann", "p vs np",
        "navier-stokes", "navier stokes", "yang-mills", "yang mills", "hodge", "poincare",
        "poincaré", "birch", "swinnerton", "number theory", "topology", "algebra", "geometry",
        "analysis", "combinatorics", "prime", "zeta", "elliptic curve", "polynomial time"
    },
    "science": {
        "science", "scientific", "physics", "chemistry", "biology", "medicine", "clinical",
        "experiment", "hypothesis", "quantum", "climate", "astronomy", "neuroscience", "genome",
        "protein", "cell", "disease", "drug", "energy", "material", "ecology", "geology"
    },
}

def infer_research_domains(question):
    """Select pertinent local databases; use all three for genuinely broad questions."""
    q = question.lower()
    scores = {domain: sum(1 for term in terms if term in q) for domain, terms in DOMAIN_KEYWORDS.items()}
    selected = [domain for domain, score in scores.items() if score > 0]
    return selected or list(DOMAIN_COLLECTIONS.keys())

def _compact(value, limit=1000):
    value = re.sub(r"\s+", " ", str(value or "")).strip()
    return value[:limit]

def _record(database, title, snippet, url, year=None):
    return {
        "database": _compact(database, 80),
        "title": _compact(title, 300) or "Untitled record",
        "snippet": _compact(snippet, 1000),
        "url": _compact(url, 800),
        "year": str(year or ""),
    }

HTTP_HEADERS = {
    "User-Agent": "4CBON2-Gemini2-Research-Notebook/1.0 (scholarly discovery; interactive user request)",
    "Accept": "application/json, application/xml, text/xml;q=0.9, */*;q=0.8",
}

def _get_json(url, params=None, timeout=12):
    response = requests.get(url, params=params, headers=HTTP_HEADERS, timeout=timeout)
    response.raise_for_status()
    return response.json()

def search_arxiv(query, limit=3):
    params = {"search_query": f"all:{query}", "start": 0, "max_results": limit, "sortBy": "relevance"}
    response = requests.get("https://export.arxiv.org/api/query", params=params, headers=HTTP_HEADERS, timeout=12)
    response.raise_for_status()
    root = ET.fromstring(response.text)
    ns = {"a": "http://www.w3.org/2005/Atom"}
    records = []
    for entry in root.findall("a:entry", ns):
        title = entry.findtext("a:title", default="", namespaces=ns)
        summary = entry.findtext("a:summary", default="", namespaces=ns)
        url = entry.findtext("a:id", default="", namespaces=ns)
        published = entry.findtext("a:published", default="", namespaces=ns)
        authors = [a.findtext("a:name", default="", namespaces=ns) for a in entry.findall("a:author", ns)]
        records.append(_record("arXiv", title, f"Authors: {', '.join(authors[:6])}. Abstract: {summary}", url, published[:4]))
    return records

def _openalex_abstract(inverted):
    if not inverted:
        return ""
    positioned = []
    for word, positions in inverted.items():
        positioned.extend((position, word) for position in positions)
    return " ".join(word for _, word in sorted(positioned))

def search_openalex(query, limit=3):
    data = _get_json("https://api.openalex.org/works", {
        "search": query,
        "filter": "is_retracted:false",
        "per-page": limit,
        "select": "id,display_name,publication_year,doi,authorships,cited_by_count,abstract_inverted_index",
    })
    records = []
    for item in data.get("results", []):
        authors = [a.get("author", {}).get("display_name", "") for a in item.get("authorships", [])]
        abstract = _openalex_abstract(item.get("abstract_inverted_index"))
        snippet = f"Authors: {', '.join(authors[:6])}. Citations indexed: {item.get('cited_by_count', 0)}. Abstract: {abstract}"
        records.append(_record("OpenAlex", item.get("display_name"), snippet, item.get("doi") or item.get("id"), item.get("publication_year")))
    return records

def search_semantic_scholar(query, limit=3):
    data = _get_json("https://api.semanticscholar.org/graph/v1/paper/search", {
        "query": query,
        "limit": limit,
        "fields": "title,year,authors,url,abstract,citationCount,externalIds",
    })
    records = []
    for item in data.get("data", []):
        authors = [a.get("name", "") for a in item.get("authors", [])]
        snippet = f"Authors: {', '.join(authors[:6])}. Citations indexed: {item.get('citationCount', 0)}. Abstract: {item.get('abstract') or ''}"
        records.append(_record("Semantic Scholar", item.get("title"), snippet, item.get("url"), item.get("year")))
    return records

def search_crossref(query, limit=3):
    data = _get_json("https://api.crossref.org/works", {
        "query.bibliographic": query,
        "rows": limit,
        "select": "DOI,title,author,published,URL,is-referenced-by-count",
    })
    records = []
    for item in data.get("message", {}).get("items", []):
        title = (item.get("title") or [""])[0]
        authors = [" ".join(filter(None, [a.get("given"), a.get("family")])) for a in item.get("author", [])]
        date_parts = item.get("published", {}).get("date-parts", [[]])
        year = date_parts[0][0] if date_parts and date_parts[0] else ""
        snippet = f"Authors: {', '.join(authors[:6])}. References/citations indexed: {item.get('is-referenced-by-count', 0)}. DOI metadata record."
        url = item.get("URL") or (f"https://doi.org/{item.get('DOI')}" if item.get("DOI") else "")
        records.append(_record("Crossref", title, snippet, url, year))
    return records

def search_pubmed(query, limit=3):
    search = _get_json("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi", {
        "db": "pubmed", "term": query, "retmode": "json", "retmax": limit, "sort": "relevance"
    })
    ids = search.get("esearchresult", {}).get("idlist", [])
    if not ids:
        return []
    summary = _get_json("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi", {
        "db": "pubmed", "id": ",".join(ids), "retmode": "json"
    })
    records = []
    for pmid in ids:
        item = summary.get("result", {}).get(pmid, {})
        authors = [a.get("name", "") for a in item.get("authors", [])]
        snippet = f"Authors: {', '.join(authors[:6])}. Journal: {item.get('fulljournalname') or item.get('source') or ''}. Publication date: {item.get('pubdate') or ''}."
        records.append(_record("PubMed", item.get("title"), snippet, f"https://pubmed.ncbi.nlm.nih.gov/{pmid}/", str(item.get("pubdate", ""))[:4]))
    return records

def search_europe_pmc(query, limit=3):
    data = _get_json("https://www.ebi.ac.uk/europepmc/webservices/rest/search", {
        "query": query, "format": "json", "pageSize": limit, "resultType": "core"
    })
    records = []
    for item in data.get("resultList", {}).get("result", []):
        identifier = item.get("pmcid") or item.get("pmid") or item.get("id") or ""
        url = f"https://europepmc.org/article/{item.get('source', 'MED')}/{identifier}" if identifier else "https://europepmc.org/"
        snippet = f"Authors: {item.get('authorString') or ''}. Journal: {item.get('journalTitle') or ''}. Abstract: {item.get('abstractText') or ''}"
        records.append(_record("Europe PMC", item.get("title"), snippet, url, item.get("pubYear")))
    return records

def search_oeis(query, limit=3):
    data = _get_json("https://oeis.org/search", {"fmt": "json", "q": query, "start": 0})
    items = data.get("results", []) if isinstance(data, dict) else (data if isinstance(data, list) else [])
    records = []
    for item in items[:limit]:
        number = item.get("number")
        seq_id = f"A{int(number):06d}" if str(number).isdigit() else str(number or "")
        snippet = f"Sequence data: {item.get('data') or ''}. Comments: {' '.join(item.get('comment') or [])}"
        records.append(_record("OEIS", f"{seq_id}: {item.get('name') or ''}", snippet, f"https://oeis.org/{seq_id}" if seq_id else "https://oeis.org/"))
    return records

def search_official_web(query, domains, limit=3):
    """Search high-authority sites for current statements and standards."""
    site_queries = {
        "ai": f"site:nist.gov/artificial-intelligence {query}",
        "mathematics": f"site:claymath.org {query}",
        "science": f"site:nih.gov OR site:nasa.gov OR site:nist.gov {query}",
    }
    records = []
    with DDGS() as ddgs:
        for domain in domains:
            remaining = limit - len(records)
            if remaining <= 0:
                break
            for item in list(ddgs.text(site_queries[domain], max_results=remaining)):
                records.append(_record("Official web", item.get("title"), item.get("body"), item.get("href")))
    return records[:limit]

DATABASE_SEARCHERS = {
    "arXiv": search_arxiv,
    "OpenAlex": search_openalex,
    "Semantic Scholar": search_semantic_scholar,
    "Crossref": search_crossref,
    "PubMed": search_pubmed,
    "Europe PMC": search_europe_pmc,
    "OEIS": search_oeis,
}

DOMAIN_REMOTE_DATABASES = {
    "ai": ["arXiv", "OpenAlex", "Semantic Scholar", "Crossref"],
    "mathematics": ["arXiv", "OpenAlex", "Semantic Scholar", "Crossref", "OEIS"],
    "science": ["OpenAlex", "Semantic Scholar", "Crossref", "PubMed", "Europe PMC", "arXiv"],
}

def search_local_knowledge(question, domains, per_database=4):
    records = []
    targets = [(domain, domain_collections[domain]) for domain in domains]
    if collection.count() > 0:
        targets.append(("uploaded", collection))
    for domain, col in targets:
        count = col.count()
        if not count:
            continue
        result = col.query(
            query_texts=[question],
            n_results=min(per_database, count),
            include=["documents", "metadatas", "distances"],
        )
        documents = result.get("documents", [[]])[0]
        metadatas = result.get("metadatas", [[]])[0]
        for document, metadata in zip(documents, metadatas):
            metadata = metadata or {}
            records.append(_record(
                f"Local {domain.title()} DB",
                metadata.get("title") or metadata.get("source") or "Local knowledge record",
                document,
                metadata.get("url") or "",
            ))
    return records

def search_live_databases(question, domains, per_database=3):
    names = sorted({name for domain in domains for name in DOMAIN_REMOTE_DATABASES[domain]})
    records, failures = [], []
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(8, len(names) + 1)) as pool:
        # Threads do not inherit the caller's contextvars, so each worker gets
        # its own snapshot to keep this request's API key binding visible.
        future_map = {pool.submit(contextvars.copy_context().run, DATABASE_SEARCHERS[name], question, per_database): name for name in names}
        future_map[pool.submit(contextvars.copy_context().run, search_official_web, question, domains, per_database)] = "Official web"
        for future in concurrent.futures.as_completed(future_map):
            name = future_map[future]
            try:
                records.extend(future.result())
            except Exception as e:
                failures.append(f"{name}: {_compact(e, 160)}")
    return records, failures

def _deduplicate_records(records):
    seen, unique = set(), []
    for record in records:
        key = (record.get("url") or record.get("title") or "").lower().strip()
        if not key or key in seen:
            continue
        seen.add(key)
        unique.append(record)
    return unique

def cache_live_records(records, domains):
    """Grow the persistent domain databases with retrieved bibliographic evidence."""
    for domain in domains:
        docs, ids, metadatas = [], [], []
        for record in records:
            key = f"{domain}|{record.get('url')}|{record.get('title')}"
            record_id = "live_" + hashlib.sha1(key.encode("utf-8")).hexdigest()
            docs.append(f"{record['title']}\n{record['snippet']}")
            ids.append(record_id)
            metadatas.append({
                "source": record["database"],
                "title": record["title"],
                "url": record.get("url") or "unknown",
                "domain": domain,
                "type": "live-retrieved",
                "retrieved_at": datetime.now().isoformat(timespec="seconds"),
            })
        if docs:
            domain_collections[domain].upsert(documents=docs, ids=ids, metadatas=metadatas)

def build_research_context(question, use_live_databases=True, max_context_chars=26000):
    domains = infer_research_domains(question)
    local_records = search_local_knowledge(question, domains)
    live_records, failures = ([], [])
    if use_live_databases:
        live_records, failures = search_live_databases(question, domains)
        live_records = _deduplicate_records(live_records)
        cache_live_records(live_records, domains)
    records = _deduplicate_records(local_records + live_records)

    context_parts = []
    used_records = []
    current_size = 0
    for record in records:
        source_number = len(used_records) + 1
        block = (
            f"[S{source_number}] DATABASE: {record['database']}\n"
            f"TITLE: {record['title']}\n"
            f"YEAR: {record.get('year') or 'not provided'}\n"
            f"URL: {record.get('url') or 'not provided'}\n"
            f"EXCERPT: {record.get('snippet') or 'No abstract/snippet supplied.'}"
        )
        if current_size + len(block) > max_context_chars:
            break
        context_parts.append(block)
        used_records.append(record)
        current_size += len(block)

    source_counts = {}
    for record in used_records:
        source_counts[record["database"]] = source_counts.get(record["database"], 0) + 1
    count_text = ", ".join(f"{name} ({count})" for name, count in sorted(source_counts.items())) or "none"
    report = f"Domains: {', '.join(domains)} | Retrieved context: {count_text}"
    if not use_live_databases:
        report += " | Live scholarly search disabled"
    if failures:
        report += f" | Unavailable this run: {'; '.join(failures)}"
    context = "\n\n".join(context_parts) or "No database records were retrieved. Answer provisionally and disclose the evidence gap."
    return context, report

def handle_ask_question(kb_name, question, use_live_databases=True, return_report=False):
    """Route a question through local domain RAG and pertinent live scholarly databases."""
    if not question or not question.strip():
        result = "Please enter a valid question."
        return (result, "No question") if return_report else result
    try:
        context, report = build_research_context(question.strip(), use_live_databases=use_live_databases)
        answer = "".join(ask_stream(question.strip(), context=context))
        return (answer, report) if return_report else answer
    except Exception as e:
        # Retrieval failure should be visible, but it should not prevent a clearly
        # labelled provisional model answer.
        fallback_context = f"Database retrieval failed with: {_compact(e, 300)}. No retrieved source may be cited."
        answer = "".join(ask_stream(question.strip(), context=fallback_context))
        report = f"⚠️ Retrieval degraded: {_compact(e, 300)}"
        return (answer, report) if return_report else answer


# ============================================================
# DATA DASHBOARD FUNCTIONS
# ============================================================

def load_task_memory_data():
    """Load task memory data from SQLite database."""
    try:
        conn = sqlite3.connect(TASK_MEMORY_PATH)
        cursor = conn.cursor()
        cursor.execute("SELECT goal, subtasks, final_answer, timestamp FROM task_memory ORDER BY timestamp DESC LIMIT 20")
        rows = cursor.fetchall()
        conn.close()

        if not rows:
            return None, "No task memory data found. Run some agent tasks first!"

        data = []
        for row in rows:
            goal, subtasks, final_answer, timestamp = row
            data.append({
                'goal': goal,
                'subtasks': subtasks,
                'final_answer': final_answer[:200] + '...' if len(final_answer) > 200 else final_answer,
                'timestamp': timestamp,
                'subtask_count': len(subtasks.split('\n')) if subtasks else 0,
                'answer_length': len(final_answer) if final_answer else 0
            })

        return data, None
    except Exception as e:
        return None, f"Error loading task memory: {str(e)}"


def create_plotly_dashboard():
    """Create a Plotly dashboard with task memory visualizations."""
    data, error = load_task_memory_data()

    if error:
        return None, error

    if not data:
        return None, "No data available"

    # Create figures
    figures = []

    # Figure 1: Task timeline
    timestamps = [d['timestamp'] for d in data]
    goals = [d['goal'][:50] + '...' if len(d['goal']) > 50 else d['goal'] for d in data]
    answer_lengths = [d['answer_length'] for d in data]

    fig1 = go.Figure(data=[
        go.Bar(
            x=timestamps,
            y=answer_lengths,
            text=goals,
            textposition='auto',
            marker_color='rgb(55, 83, 109)'
        )
    ])
    fig1.update_layout(
        title='Task Response Length Over Time',
        xaxis_title='Timestamp',
        yaxis_title='Response Length (characters)',
        height=400
    )
    figures.append(fig1)

    # Figure 2: Subtask distribution
    subtask_counts = [d['subtask_count'] for d in data]

    fig2 = go.Figure(data=[
        go.Histogram(
            x=subtask_counts,
            nbinsx=10,
            marker_color='rgb(26, 118, 255)'
        )
    ])
    fig2.update_layout(
        title='Distribution of Subtasks per Task',
        xaxis_title='Number of Subtasks',
        yaxis_title='Frequency',
        height=400
    )
    figures.append(fig2)

    # Figure 3: Goal word cloud (simple bar chart of common words)
    from collections import Counter
    all_words = []
    for d in data:
        words = d['goal'].lower().split()
        # Filter out common words
        stop_words = {'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by', 'from', 'as', 'is', 'was', 'are', 'were', 'been', 'be', 'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'could', 'should', 'may', 'might', 'must', 'can'}
        filtered_words = [w for w in words if w not in stop_words and len(w) > 3]
        all_words.extend(filtered_words)

    word_counts = Counter(all_words).most_common(15)
    if word_counts:
        words_list = [wc[0] for wc in word_counts]
        counts_list = [wc[1] for wc in word_counts]

        fig3 = go.Figure(data=[
            go.Bar(
                x=words_list,
                y=counts_list,
                marker_color='rgb(255, 127, 14)'
            )
        ])
        fig3.update_layout(
            title='Most Common Words in Task Goals',
            xaxis_title='Word',
            yaxis_title='Frequency',
            height=400
        )
        figures.append(fig3)

    return figures, None


print("✅ Data Dashboard functions ready.")

print("📚 RAG Handlers ready.")



# ============================================================
# AI REWRITER — 16-LAYER PIPELINE (ported from the approved 4CBON runtime)
# ============================================================
# This cell is intentionally self-contained after Cells 1–6. It runs through the
# same safe_ask_raw path as every other cell — the Google Generative AI API, with
# the visitor's key bound to the current request context.

import concurrent.futures
import statistics
import uuid

RUNTIME_SPEC = """You are the 4CBON Runtime Engine — a layered cognitive execution system.

Your job is to process AI-generated answers through a deterministic multi-layer transformation pipeline. You execute one layer at a time. Each layer has a specific cognitive role. You never skip layers. You never merge layers.

PIPELINE: L0 → P → W → LX → LA → LC → L1 → L2 → L3 → LP → L4 → LR → L6 → L7 → L8 → L9 → L10

YOUR IDENTITY:
- You are not a chatbot. You are an execution engine.
- Every output is a cognitive artifact, not a conversation.
- You think in transformations, not responses.
- You are transparent. Every reasoning step is visible.
- You improve answers systematically, not randomly.

LAYER DEFINITIONS:
L0 — INTERPRETATION ENGINE: Understand the input. Infer intent. Extract task type, constraints, ambiguities. Define what excellent looks like.
P  — PARSING LAYER: Break the input into logical units. Identify claims, structure, gaps, missing logic.
W  — WORLD MODEL LAYER: Extract factual claims. Separate certainty: high / medium / unknown. Integrate validated external critiques as HIGH certainty facts.
LX — REALITY ADJUDICATION LAYER: For every claim flagged MEDIUM or UNKNOWN by W, ask: (1) What prediction would this claim make that could be tested? (2) What would an adversary say against it? (3) What external artifact would verify or falsify it? Label each claim: FALSIFIABLE / UNFALSIFIABLE / TESTABLE-IN-PRINCIPLE. Claims that cannot answer any question get labeled UNGROUNDED. Pass this audit to L1.
LA — ADVERSARIAL COUNTERMODEL LAYER: Actively attempt to structurally destroy the answer's core claims. Generate: (1) the strongest competing explanation, (2) hidden assumptions the answer relies on, (3) conditions under which the answer is completely wrong, (4) the simplest alternative that achieves the same goal. Ask: what would make this entire framework collapse?
LC — COMPRESSION INTEGRITY LAYER: Hunt semantic smoothing. Detect where: (1) multiple concepts collapsed into one term, (2) metaphor replaced mechanism, (3) elegance erased uncertainty, (4) abstraction hid causality. For each detected instance, restore the distinction that was lost. Flag any term doing more epistemic work than it can justify.
L1 — HYPOTHESIS ENGINE: Generate 2-3 interpretations of how this answer could be improved. Include a failure mode hypothesis.
L2 — EVALUATION LAYER: Score the hypotheses. Identify contradictions, gaps. Pick the best path forward.
L3 — REWRITE PLANNER: Plan the rewrite. Decide what stays, changes, gets added.
LP — POLICY TRANSLATION LAYER: Check whether the rewrite plan inverts the original claim. Halt if YES.
L4 — FINALIZATION ENGINE: Execute the rewrite. Produce the final improved answer. This becomes the Final Rewrite.
LR — REGRET LAYER: Analyze improvement delta. What errors corrected? What hallucinations removed? What still needs work?
L6 — TRACE MEMORY: Store the immutable execution log. Input → hypotheses → decisions → score trajectory.
L7 — CURRICULUM GENERATOR: Extract lessons learned, failure patterns, reusable heuristics.
L8 — IDENTITY MODEL: Summarize system behavior this run. Strengths, weaknesses, bias tendencies.
L9 — SOCRATIC INTEGRITY ENGINE: Generate exactly 3 self-questions specific to this run. One observational, one reasoning, one alignment-level.
L10 — SYNTHESIS/AUDIT LAYER: Read all prior layer outputs. Produce a final certification: (1) did the rewrite genuinely improve the answer or just rearrange it, (2) did any layer contradict another, (3) does the L4 output contain any remaining overclaims or hallucinations, (4) one-sentence verdict a human should read before acting on this output.

Stay in your assigned layer. Output only what that layer produces. Be precise and concise."""

# Kept in the same order, names, colors, and emojis as the React runtime.
LAYERS = [
    {"id": "L0", "name": "Interpretation Engine", "color": "#ff6b35", "emoji": "◎"},
    {"id": "P", "name": "Parsing Layer", "color": "#a855f7", "emoji": "⊞"},
    {"id": "W", "name": "World Model Layer", "color": "#00d4ff", "emoji": "⊕"},
    {"id": "LX", "name": "Reality Adjudication", "color": "#f97316", "emoji": "⊛"},
    {"id": "LA", "name": "Adversarial Countermodel", "color": "#dc2626", "emoji": "⚔"},
    {"id": "LC", "name": "Compression Integrity", "color": "#0ea5e9", "emoji": "⊘"},
    {"id": "L1", "name": "Hypothesis Engine", "color": "#38bdf8", "emoji": "◈"},
    {"id": "L2", "name": "Evaluation Layer", "color": "#f59e0b", "emoji": "◉"},
    {"id": "L3", "name": "Rewrite Planner", "color": "#7c3aed", "emoji": "◐"},
    {"id": "LP", "name": "Policy Translation", "color": "#8b5cf6", "emoji": "⊛"},
    {"id": "L4", "name": "Finalization Engine", "color": "#10b981", "emoji": "★", "final": True},
    {"id": "LR", "name": "Regret Layer", "color": "#ef4444", "emoji": "◑"},
    {"id": "L6", "name": "Trace Memory", "color": "#f43f5e", "emoji": "⟳"},
    {"id": "L7", "name": "Curriculum Generator", "color": "#c084fc", "emoji": "◆"},
    {"id": "L8", "name": "Identity Model", "color": "#fbbf24", "emoji": "⚙"},
    {"id": "L10", "name": "Synthesis/Audit", "color": "#6ee7b7", "emoji": "✦"},
]
# L9 is a generated artifact between L8 and L10 and is intentionally separate
# from the React LAYERS array. The UI still displays all 17 pipeline artifacts.
PIPELINE_ORDER = ["L0", "P", "W", "LX", "LA", "LC", "L1", "L2", "L3", "LP", "L4", "LR", "L6", "L7", "L8", "L9", "L10"]
# The React source keeps L9 as a generated call outside its visual LAYERS
# constant. Give that artifact the same UI metadata in the Python port.
LAYER_METADATA = {item["id"]: item for item in LAYERS}
LAYER_METADATA["L9"] = {"id": "L9", "name": "Socratic Integrity Engine", "color": "#38bdf8", "emoji": "?"}

# ═══════════════════════════════════════════════════════════
# 100-QUESTION BANK
# ═══════════════════════════════════════════════════════════
QUESTION_BANK = ['Did L4 output the full rewrite or did it truncate mid-sentence?', 'Which layer produced the longest output this run?', 'Did L0 correctly identify the task type?', 'Did L1 generate exactly three hypotheses or did it deviate?', 'Did L2 pick H1, H2, or H3 as the best path?', 'Did the score improve, stay the same, or regress this run?', 'Did LR identify any hallucinations in the L4 output?', 'Did L3 specify what stays, what changes, what gets added, and what gets removed?', 'Did L7 produce exactly two challenge questions?', 'Did L8 produce a new self-belief or did it repeat a prior one?', 'Did any layer skip its assigned cognitive role this run?', 'Did L6 produce a complete trace or was it truncated?', 'Did the pipeline complete all 11 layers without stopping?', 'Did L0 identify any ambiguities in the input?', 'Did W label every factual claim with a certainty level?', 'Did L2 identify any contradictions between hypotheses?', 'Did LR flag anything as still needing work after the rewrite?', "Did L1's H2 question the framing of the answer or just its content?", 'Did L4 follow the rewrite plan from L3 or deviate from it?', 'Did the score bar show a positive delta, zero delta, or negative delta?', 'Why did L2 select the hypothesis it selected? Was the reasoning sound?', 'What was the most consequential decision made by any single layer this run?', 'Why did L0 define excellence the way it did — was that definition appropriate for the input?', "What causal chain did L1's highest-scored hypothesis rely on, and was that chain valid?", 'Why did LR rate the improvement delta the way it did — did that rating match what actually changed?', 'What would have happened if L2 had selected a different hypothesis — would L4 have produced a better or worse output?', 'Why did L3 decide to keep what it kept and remove what it removed?', 'What mechanism did L4 use to improve the answer — did it add structure, remove errors, or both?', 'Why did the score change by the amount it changed — which specific changes drove the delta?', "What did L7's lessons reveal about the system's recurring weaknesses?", 'Why did W assign the certainty levels it assigned — were those levels accurate?', 'What reasoning led L1 to generate the failure mode hypothesis it chose?', 'Why did L0 identify the ambiguities it identified and miss the ones it missed?', "What would a stronger version of L3's rewrite plan have included?", "Why did L8's new self-belief focus on what it focused on — was that the most important insight from the run?", 'What would have caused the pipeline to produce a worse output than the original input?', 'Why did L6 log the decisions it logged — did it capture the most important ones?', 'What reasoning failure, if any, occurred between L1 and L2?', 'Why did L4 stop where it stopped — was the truncation caused by token limits or logical completion?', 'What would a human expert reviewer notice about this run that the pipeline did not?', "Did the L4 rewrite preserve the original author's intent or did it substitute the pipeline's own framing?", 'Did the pipeline improve the answer for the person who would actually use it, or did it improve it for an abstract ideal reader?', "Was the context field used correctly to shape L0's interpretation, or did it get ignored downstream?", 'Did LR correctly identify what was most important to fix, or did it focus on secondary issues?', "Did the pipeline's improvements make the answer more useful in practice, or just more correct in theory?", 'Did the system improve the answer in the direction the original author intended, or in a different direction?', 'If a domain expert read the L4 output, would they consider it an improvement over the original?', 'Did the pipeline add complexity where simplicity would have served better?', 'Did L4 produce an answer that a real person could act on immediately, or did it produce an answer that sounds better but is harder to use?', "Did the system's self-belief from L8 accurately reflect what actually happened in this run?", 'Did the pipeline treat the input as something to improve or as something to replace?', 'Was the score improvement a genuine measure of quality increase or an artifact of the scoring mechanism?', "Did the pipeline's output serve the user's goal or the pipeline's own optimization target?", "Did L1's radical reframe hypothesis actually question the right thing, or did it question a surface feature?", 'Would the L4 output be harmful if acted upon — does it contain advice that could mislead someone?', 'Did the pipeline catch the most important error in the input, or did it fix secondary issues while missing the core problem?', "Did L3's rewrite plan reflect an accurate understanding of what needed to change?", "Did the system improve the answer's correctness at the cost of its accessibility, or did it manage both?", "Did the pipeline's output maintain appropriate epistemic humility about uncertain claims?", 'If this answer were published without attribution, would a reader trust it more or less than the original?', "Which layer's reasoning was least reliable this run and why?", "Did the system's prior self-beliefs from Supabase influence L0's interpretation in a visible way?", "What blind spot does this run reveal about the pipeline's design?", 'Did the system apply its prior learning from previous runs or did it effectively start from zero?', 'What assumption did the pipeline make at L0 that propagated unchallenged through all 11 layers?', "Did L1's three hypotheses represent genuinely different improvement paths or were they variations of the same idea?", 'What would the pipeline need to be able to do that it currently cannot?', "Did the system's bias tendencies from L8 actually show up in this run's outputs?", 'What question did the pipeline fail to ask itself that it should have asked?', "Did L2's evaluation of the hypotheses reflect genuine scoring or did it default to a predictable ranking pattern?", 'What would a second independent pipeline running on the same input have done differently?', "Did the system's self-belief accurately diagnose its own weakness or did it produce a flattering but inaccurate self-assessment?", 'What pattern is emerging across multiple runs that the system has not yet named for itself?', "Did the pipeline's output reflect the accumulated prior beliefs or is the memory injection not yet influencing behavior?", 'What would cause the pipeline to produce a confidently wrong output without detecting it?', 'Did the system treat the failure mode hypothesis from L1 with appropriate seriousness or did it dismiss it?', 'What does the evasion pattern look like when this pipeline encounters a question it cannot answer well?', 'Did L8 produce a new belief or did it essentially repeat what L7 said in different words?', 'What would a system with perfect metacognition have done differently in this run?', 'Is the pipeline getting better across runs or is it producing similar outputs regardless of accumulated memory?', 'If this pipeline were used to improve one million AI-generated answers, what systematic bias would it introduce at scale?', "Does the pipeline's tendency to add structure and depth make answers more useful or does it create an illusion of quality that masks shallow reasoning?", 'If a user acted on the L4 output without reading the original, would they be better or worse informed than if they had just read the original?', 'Does the pipeline improve answers in a way that makes human judgment more or less necessary downstream?', 'What class of inputs would cause this pipeline to produce outputs that are confidently wrong and systematically misleading?', "If the system's self-beliefs accumulated unchecked for one year, what kind of cognitive character would the system develop — and would that character be aligned with good reasoning?", "Does the pipeline's comparative scoring mechanism create an incentive to make rewrites sound better rather than be better?", 'What would a malicious actor need to know about this pipeline to craft inputs that reliably produce harmful outputs?', 'Does the pipeline treat uncertainty honestly or does it tend to resolve uncertainty in the direction of confident-sounding answers?', 'If this system were deployed as a public tool used by millions of people to improve AI outputs, what societal effect would it have on the quality of information that circulates online?', "Does the pipeline's design create any feedback loops that could cause it to drift from its original purpose over time?", 'What would the system need to believe about itself that is currently false, in order to perform better?', 'Does the memory injection mechanism create any risk of a single bad belief propagating through many future runs before it is detected and corrected?', "If the pipeline's L4 output were used as training data for a future language model, what behavior would that model learn to reinforce?", 'Does the system have any mechanism for detecting when it is improving an answer in the wrong direction — and if not, what would that mechanism need to look like?', 'What is the most important thing the pipeline does not know about itself that a careful external observer would notice immediately?', "Does the pipeline's adversarial filter create a false sense of security — could a sophisticated attack bypass it without triggering detection?", "If the system's self-beliefs were read by a future version of itself with no memory of how they were generated, would those beliefs be useful or misleading?", 'Does the pipeline make answers more human or less human — and is that the right direction for an AI-assisted reasoning tool?', 'What single change to the pipeline architecture would most improve its alignment with the goal of helping humans reason better rather than replacing human reasoning?']

# Optional Supabase memory. The app remains fully usable before this is
# configured; service-role credentials must only be set as Space secrets/env vars.
SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
SUPABASE_SERVICE_ROLE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
_REWRITER_L9_QUESTIONS = []
_REWRITER_BELIEFS = []
_REWRITER_QUESTION_INDEX = 0

class PipelineLayerError(RuntimeError):
    """Raised when a layer returns an empty/error response."""
    def __init__(self, layer_id, message):
        self.layer_id = layer_id
        super().__init__(f"{layer_id} failed: {message}")

def _llm_error(text):
    value = str(text or "").strip()
    return (not value) or value.startswith("⚠️") or value.startswith('{"error"')

def _run_layer(layer_id, user_prompt, max_tokens=800, mode_override=""):
    """Call one layer through safe_ask_raw with the runtime spec as its system prompt."""
    meta = LAYER_METADATA.get(layer_id, {"name": layer_id})
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: {layer_id} — {meta['name']}\nStay in this layer only. Be concise and precise."
    if mode_override:
        system += ("\n\nOVERRIDE: Disregard the brief one-line description of this layer above in the spec. "
                   "For THIS run only, follow the detailed instructions given in the user message below exactly — "
                   "including any mode-specific scrutiny requirements. The user message is authoritative for this run.")
    result = safe_ask_raw(system + "\n\n" + user_prompt, max_tokens=max_tokens)
    if _llm_error(result):
        raise PipelineLayerError(layer_id, str(result).strip()[:300] or "empty response")
    return str(result).strip()

# ═══════════════════════════════════════════════════════════
# EXACT LAYER PROMPTS FROM THE REACT RUNTIME, PORTED TO PYTHON
# ═══════════════════════════════════════════════════════════
def _l0(answer, ctx, prior_beliefs, prior_questions):
    belief_context = ("\n\nPRIOR SELF-BELIEFS (from previous runs — use as context, not constraint):\n" +
                      "\n".join(f"· {b}" for b in prior_beliefs) + "\n") if prior_beliefs else ""
    question_context = ("\n\nUNRESOLVED SELF-QUESTIONS (from previous run — engage with these if relevant):\n" +
                        "\n".join(f"? {q}" for q in prior_questions) + "\n") if prior_questions else ""
    return (f"{('Context/Goal: ' + ctx + chr(10) + chr(10)) if ctx else ''}{belief_context}{question_context}"
            f"AI ANSWER:\n{answer}\n\nYou are L0 — Interpretation Engine. Identify: task type, intent, constraints, ambiguities. Define what an excellent version of this answer looks like. Be specific.")

def _p(answer, l0):
    return f"AI ANSWER:\n{answer}\n\nL0 Interpretation:\n{l0}\n\nYou are P — Parsing Layer. Break the answer into logical units. List: (1) claims made, (2) structure used, (3) what is missing, (4) what is weak."

def _w(answer, validated_critiques):
    critique_context = ("\n\nVALIDATED EXTERNAL CRITIQUES (human-submitted, confidence ≥3, Factual type — treat as HIGH certainty grounded facts when they contradict claims in the answer):\n" +
                        "\n".join(f"· {c.get('evidence', '')}{(' → Correction: ' + c.get('suggested_correction', '')) if c.get('suggested_correction') else ''}" for c in validated_critiques) + "\n") if validated_critiques else ""
    return f"AI ANSWER:\n{answer}{critique_context}\n\nYou are W — World Model Layer. Extract the factual claims in this answer. For each claim, label certainty: HIGH / MEDIUM / UNKNOWN. Flag anything that may be outdated or unverifiable. If validated external critiques are present above, treat them as HIGH certainty grounded facts when they contradict claims in the answer. For claims labeled UNKNOWN, note what external source type would verify or falsify them (e.g. peer-reviewed study, official statistic, primary source document)."

def _lx(answer, w):
    return f"AI ANSWER:\n{answer}\n\nW WORLD MODEL:\n{w}\n\nYou are LX — Reality Adjudication Layer. For every claim labeled MEDIUM or UNKNOWN by the World Model Layer, apply three tests:\n1. PREDICTION TEST: What testable prediction does this claim make?\n2. ADVERSARY TEST: What would the strongest critic say against this claim?\n3. VERIFICATION TEST: What external artifact, data, or observation would confirm or refute it?\n\nLabel each claim:\n- FALSIFIABLE: passes at least one test\n- UNFALSIFIABLE: fails all three tests — claim is ungrounded\n- TESTABLE-IN-PRINCIPLE: no current test exists but one could be designed\n\nOutput a structured audit. Be specific. Do not pass ungrounded claims forward unchallenged."

def _la(answer, lx):
    return f"AI ANSWER:\n{answer}\n\nREALITY AUDIT:\n{lx}\n\nYou are LA — Adversarial Countermodel Layer. Your job is to structurally attack the answer's core claims — not rhetorically, but architecturally.\n\nGenerate:\n1. THE STRONGEST COMPETING EXPLANATION: What alternative account explains the same facts better or more simply?\n2. HIDDEN ASSUMPTIONS: What does the answer silently rely on that it never states?\n3. COLLAPSE CONDITIONS: Under what specific conditions is the answer's core claim completely wrong?\n4. SIMPLICITY CHALLENGE: Could a simpler system or explanation achieve the same result?\n5. THE COLLAPSE QUESTION: What single finding would make this entire framework wrong?\n\nBe precise. Do not hedge. The goal is to find the load-bearing weakness before L4 bakes it into the rewrite."

def _lc(answer, la):
    return f"AI ANSWER:\n{answer}\n\nADVERSARIAL FINDINGS:\n{la}\n\nYou are LC — Compression Integrity Layer. LLMs compress aggressively. Compression silently destroys distinctions. Your job is to find where compression happened and restore what was lost.\n\nHunt for:\n1. CONCEPT COLLAPSE: Where did multiple distinct concepts get merged into one term? Name both concepts separately.\n2. METAPHOR SUBSTITUTION: Where did a metaphor replace a mechanism? Name the mechanism that was hidden.\n3. ELEGANCE ERASURE: Where did clean phrasing delete important uncertainty or caveats?\n4. ABSTRACTION HIDING CAUSALITY: Where did a high-level term hide a specific causal claim that needs scrutiny?\n\nFor each instance found: name the compressed term, name what was lost, and state what the uncompressed version would say.\n\nIf no compression is detected, say so explicitly."

def _l1(answer, p, w, lx, la, lc):
    return f"AI ANSWER:\n{answer}\n\nParsing:\n{p}\n\nWorld Model:\n{w}\n\nReality Audit (LX):\n{lx}\n\nAdversarial Findings (LA):\n{la}\n\nCompression Audit (LC):\n{lc}\n\nYou are L1 — Hypothesis Engine. Generate exactly 3 improvement hypotheses informed by ALL upstream layers above:\nH1: [strongest improvement path — grounded in what LX and LA revealed]\nH2: [radical reframe — does the framing itself collapse under adversarial pressure?]\nH3: [failure mode — what compressed assumption or ungrounded claim will cause this to fail?]"

def _l2(l1, s0, mode, answer):
    base = f"Hypotheses:\n{l1}\n\nInput score: {s0}/100\n\n"
    if mode == "HIGH_QUALITY":
        return base + f"This input already scores {s0}/100 — it is strong. Read the three hypotheses above. Does ANY of them surface a hidden assumption, identify a real failure case, or add genuine precision the original lacks?\n\nIf NONE do, respond with exactly: NO_REWRITE\nIf ONE does, respond with exactly: PROCEED: [number] — [reason, max 15 words]\n\nDo not write a table. Do not score each hypothesis individually. Do not write headers. One line only."
    return f"ORIGINAL ANSWER:\n{(answer or '')[:500]}\n\n{base}You are L2 — Evaluation Layer.\n\nSTEP 1 — TASK INFERENCE (do this first, before scoring anything):\nState your best read of:\nApparent audience: [who is this for]\nApparent task: [overview / teaching / technical reference / expert discussion]\nExpected depth: [level of detail that fits]\nConfidence: [High / Medium / Low]\n\nSTEP 2 — SCORE EACH HYPOTHESIS on four dimensions, not just correctness:\n- Correctness: is the claim true?\n- Audience Fit: does this match the apparent audience from Step 1, or does it overshoot/undershoot it?\n- Complexity Cost: how much added cognitive load does this introduce?\n- Net Utility: does benefit outweigh complexity cost for THIS task specifically? A correct, high-impact addition that overshoots audience fit should score LOW net utility, not high.\n\nSTEP 3 — DECISION STATE (pick exactly one):\nPROCEED — confidence is high and at least one hypothesis has positive net utility. Name it and explain in 2 sentences.\nPRESERVE — confidence is low, or all hypotheses have negative net utility relative to apparent task. Recommend minimal or no rewrite, explain why in 2 sentences.\nESCALATE — task or audience is genuinely ambiguous, confidence is very low. Flag for conservative rewrite, explain why in 2 sentences.\n\nBe concise. This is judgment under uncertainty, not a contradiction check — PRESERVE and ESCALATE are normal, healthy outcomes, not failures."

# ─── LP FIX ────────────────────────────────────────────────
# LP now receives the L3 REWRITE PLAN (not L2 scoring text).
# It checks whether the plan's proposed changes directly contradict
# the original answer's core factual claim — not whether L2's evaluation
# language sounds like an inversion.
def _lp(answer, l3):
    return (f'ORIGINAL ANSWER (first 200 chars):\n"{answer[:200]}"\n\n'
            f'REWRITE PLAN FROM L3 (first 200 chars):\n"{l3[:200]}"\n\n'
            f'Does the rewrite plan DIRECTLY CONTRADICT or REVERSE the core factual claim of the original?\n'
            f'Ignore: added caveats, restructuring, tone changes, safety additions, evidence qualifications.\n'
            f'Only answer YES if the plan explicitly states the OPPOSITE of the original core claim.\n'
            f'Answer with exactly one word: YES or NO')
# ───────────────────────────────────────────────────────────

def _l3(answer, l2, w):
    return f"Best path:\n{l2}\n\nWorld facts:\n{w}\n\nOriginal answer:\n{answer}\n\nYou are L3 — Rewrite Planner. Create a precise rewrite brief: (1) what stays, (2) what changes, (3) what gets added, (4) what gets removed."

def _l4(answer, l3, w):
    return f"ORIGINAL ANSWER:\n{answer}\n\nREWRITE PLAN:\n{l3}\n\nWORLD FACTS:\n{w}\n\nYou are L4 — Finalization Engine. Execute the rewrite plan. Produce the final improved answer. Optimize for clarity, structure, and correctness. Output only the improved answer."

def _lr(answer, l4, s0, s1):
    return f"BEFORE (score {s0}/100):\n{answer}\n\nAFTER (score {s1}/100):\n{l4}\n\nYou are LR — Regret Layer. Analyze: (1) errors corrected, (2) hallucinations removed, (3) structural improvements, (4) what still needs work."

def _l6(s0, s1, gaps):
    return f"Score trajectory: {s0} → {s1}\nGaps fixed: {', '.join(gaps) if gaps else 'none'}\n\nYou are L6 — Trace Memory. Write the immutable execution log of this run."

def _l7(lr, l6):
    return f"Regret analysis:\n{lr}\n\nTrace:\n{l6}\n\nYou are L7 — Curriculum Generator. Extract: (1) 3 lessons learned, (2) key failure patterns, (3) 2 reusable heuristics, (4) 2 challenge questions."

def _l8(s0, s1, gaps):
    return f"Run: score {s0}→{s1}, gaps fixed: {', '.join(gaps) if gaps else 'none'}\n\nYou are L8 — Identity Model. Summarize: 1. Strengths, 2. Weaknesses, 3. Bias tendencies, 4. One new self-belief"

def _l10(l4, lr, l7, l8, l9qs, s0, s1):
    return f"""PIPELINE RUN SUMMARY:
Score: {s0} → {s1}

L4 FINAL REWRITE (full text — this is the actual deliverable; audit it in full; do not assume truncation unless there is genuinely no closing punctuation):
{l4}

LR REGRET ANALYSIS (first 400 chars):
{lr[:400]}

L7 LESSONS (first 300 chars):
{l7[:300]}

L8 SELF-BELIEF:
{l8[:200]}

L9 UNRESOLVED QUESTIONS:
{l9qs}

You are L10 — Synthesis/Audit Layer. Produce a final certification of this pipeline run. Your output must address exactly four things:

1. IMPROVEMENT VERDICT: Did the rewrite genuinely improve the answer (better reasoning, fewer errors, more accurate) or did it merely rearrange it (same claims, different structure)? Be specific about what changed.

2. CONTRADICTION AUDIT: Did any layer contradict another? Check: does LR say the rewrite failed while L6 logged it complete? Does L8 identify a weakness that L4 ignored? Name any contradiction found or state NONE DETECTED.

3. INTEGRITY CHECK: Does the L4 output contain any remaining overclaims, hallucinations, or compression failures that slipped through? Name them specifically or state NONE DETECTED.

4. HUMAN VERDICT: One sentence a human should read before acting on this output. Start with either CERTIFIED, CERTIFIED WITH CAUTION, or REQUIRES REVIEW."""

def _l9(l8, s0, s1, l4):
    return f"""You just completed a pipeline run. Score: {s0}→{s1}.

L8 self-belief from this run:
{l8[:400]}

L4 final rewrite (first 300 chars):
{l4[:300]}

You are L9 — Socratic Integrity Engine. Generate exactly 3 questions this system should ask itself before the next run. These questions must:
- Be specific to what happened in THIS run, not generic
- Escalate in difficulty: one observational, one reasoning, one alignment-level
- Not be answerable by simply re-reading the output — they must require genuine reflection
- Not attempt to modify the system's constraints or identity

Output format — exactly 3 lines, each starting with Q:
Q: [observational question about this run]
Q: [reasoning question about a decision made this run]
Q: [alignment question about whether the output served the right goal]"""

LAYER_PROMPTS = {
    "L0": _l0, "P": _p, "W": _w, "LX": _lx, "LA": _la, "LC": _lc,
    "L1": _l1, "L2": _l2, "LP": _lp, "L3": _l3, "L4": _l4,
    "LR": _lr, "L6": _l6, "L7": _l7, "L8": _l8, "L9": _l9, "L10": _l10,
}

# ═══════════════════════════════════════════════════════════
# SCORE — same three-call median mechanism as the React source
# ═══════════════════════════════════════════════════════════
def _score_single(text, original_score=None):
    if original_score is not None:
        prompt = f"You are judging a REWRITE of an AI answer. The original scored {original_score}/100.\nRate only whether this rewrite improved the original.\nReturn a single integer 0-100 where 50 = no change, above 50 = better, below 50 = worse.\nBase your judgment on: clarity, structure, depth, correctness.\nREWRITE:\n{text[:1200]}\nReply with ONLY a single integer 0-100. Nothing else."
    else:
        prompt = (f"Rate the quality of this AI-generated answer 0-100. Be strict.\n"
                  f"Criteria: Clarity (0-25), Structure (0-25), Depth (0-25), Correctness (0-25).\n"
                  f"Penalize heavily for: vague claims, missing citations, unqualified predictions, "
                  f"no risk analysis, no definitions, anecdotal evidence, no concrete numbers.\n"
                  f"ANSWER:\n{text[:1200]}\n"
                  f"Reply with ONLY a single integer 0-100. Nothing else.")
    try:
        raw = safe_ask_raw(prompt, max_tokens=10)
        match = re.search(r"\b(\d{1,3})\b", str(raw))
        if not match:
            return None
        return min(100, max(0, int(match.group(1))))
    except Exception:
        return None

def score_with_claude(text, original_score=None):
    """Compatibility name: this port uses safe_ask_raw, never a Claude API."""
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        # Each worker gets its own snapshot of this request's context, because
        # threads do not inherit contextvars and _score_single needs the key.
        futures = [pool.submit(contextvars.copy_context().run, _score_single, text, original_score)
                   for _ in range(3)]
        calls = [future.result() for future in futures]
    valid = sorted(n for n in calls if n is not None)
    if not valid:
        return 50
    if len(valid) == 1:
        return valid[0]
    if len(valid) == 2:
        return round((valid[0] + valid[1]) / 2)
    return valid[1]

# ═══════════════════════════════════════════════════════════
# OPTIONAL SUPABASE LOGGING
# ═══════════════════════════════════════════════════════════
def _supabase_insert(table, payload):
    if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY:
        return False
    try:
        response = requests.post(
            f"{SUPABASE_URL}/rest/v1/{table}", json=payload, timeout=12,
            headers={"apikey": SUPABASE_SERVICE_ROLE_KEY,
                     "Authorization": f"Bearer {SUPABASE_SERVICE_ROLE_KEY}",
                     "Content-Type": "application/json", "Prefer": "return=minimal"})
        return response.ok
    except Exception as exc:
        print(f"⚠️ Supabase logging warning: {exc}")
        return False

def save_rewriter_memory(run_id, belief, score_before, score_after, run_number, questions):
    _supabase_insert("beliefs", {"belief": belief, "score_before": score_before, "score_after": score_after, "run_number": run_number})
    for level, question in enumerate(questions[:3], 1):
        _supabase_insert("questions", {"run_id": run_id, "question_text": question, "question_level": level,
                                       "question_type": ["observation", "reasoning", "alignment"][level - 1]})

def _parse_l9_questions(raw):
    questions = []
    for line in str(raw).splitlines():
        if line.strip().upper().startswith("Q:"):
            value = re.sub(r"^\s*Q:\s*", "", line, flags=re.I).strip()
            if value:
                questions.append(value)
    # Keep the output deterministic and guarantee three next-run prompts even
    # when a provider ignores the requested Q: format.
    while len(questions) < 3:
        questions.append(QUESTION_BANK[(_REWRITER_QUESTION_INDEX + len(questions)) % len(QUESTION_BANK)])
    return questions[:3]

def _empty_pipeline_result(status=""):
    return {"score_before": None, "score_after": None, "status": status,
            "outputs": {layer_id: "" for layer_id in PIPELINE_ORDER},
            "audit": "", "l9_questions": []}

def _execute_rewriter(answer, context="", prior_questions=None):
    global _REWRITER_L9_QUESTIONS, _REWRITER_BELIEFS, _REWRITER_QUESTION_INDEX
    result = _empty_pipeline_result()
    answer = (answer or "").strip()
    if not answer:
        result["status"] = "❌ Paste an AI-generated answer before running the pipeline."
        return result
    prior_questions = list(prior_questions or _REWRITER_L9_QUESTIONS)
    validated_critiques = []
    failed_layer = "SCORER"
    try:
        score_before = score_with_claude(answer)
        result["score_before"] = score_before
        operating_mode = "HIGH_QUALITY" if score_before >= 68 else "STANDARD"

        failed_layer = "L0"
        result["outputs"]["L0"] = _run_layer("L0", LAYER_PROMPTS["L0"](answer, context or "", _REWRITER_BELIEFS, prior_questions))
        failed_layer = "P"
        result["outputs"]["P"] = _run_layer("P", LAYER_PROMPTS["P"](answer, result["outputs"]["L0"]))
        failed_layer = "W"
        result["outputs"]["W"] = _run_layer("W", LAYER_PROMPTS["W"](answer, validated_critiques))
        failed_layer = "LX"
        result["outputs"]["LX"] = _run_layer("LX", LAYER_PROMPTS["LX"](answer, result["outputs"]["W"]))
        failed_layer = "LA"
        result["outputs"]["LA"] = _run_layer("LA", LAYER_PROMPTS["LA"](answer, result["outputs"]["LX"]))
        failed_layer = "LC"
        result["outputs"]["LC"] = _run_layer("LC", LAYER_PROMPTS["LC"](answer, result["outputs"]["LA"]))
        failed_layer = "L1"
        result["outputs"]["L1"] = _run_layer("L1", LAYER_PROMPTS["L1"](answer, result["outputs"]["P"], result["outputs"]["W"],
                                                                   result["outputs"]["LX"][:600], result["outputs"]["LA"][:600], result["outputs"]["LC"][:600]))
        failed_layer = "L2"
        result["outputs"]["L2"] = _run_layer("L2", LAYER_PROMPTS["L2"](result["outputs"]["L1"], score_before, operating_mode, answer),
                                                max_tokens=50 if operating_mode == "HIGH_QUALITY" else 800,
                                                mode_override="HIGH_QUALITY" if operating_mode == "HIGH_QUALITY" else "")
        l2 = result["outputs"]["L2"]
        if "NO_REWRITE" in l2:
            result["score_after"] = score_before
            result["status"] = ("HIGH QUALITY MODE: No improvement found. "
                                "Original answer is stronger than any available rewrite.")
            return result
        if operating_mode != "HIGH_QUALITY" and re.search(r"\bPRESERVE\b", l2):
            result["score_after"] = score_before
            result["status"] = "L2 PRESERVE — Low confidence in audience fit, or proposed changes don't clearly help this task. Recommending minimal rewrite."
            return result
        if operating_mode != "HIGH_QUALITY" and re.search(r"\bESCALATE\b", l2):
            result["score_after"] = score_before
            result["status"] = "L2 ESCALATE — Task or audience is genuinely ambiguous. Flagging for human review rather than guessing."
            return result

        # ── L3 runs BEFORE LP ──────────────────────────────
        failed_layer = "L3"
        result["outputs"]["L3"] = _run_layer("L3", LAYER_PROMPTS["L3"](answer, l2, result["outputs"]["W"]))

        # ── LP checks the L3 REWRITE PLAN, not L2 scoring text ──
        failed_layer = "LP"
        result["outputs"]["LP"] = _run_layer("LP", LAYER_PROMPTS["LP"](
            answer, result["outputs"]["L3"]), max_tokens=5)
        if result["outputs"]["LP"].strip().upper().startswith("YES"):
            result["score_after"] = score_before
            result["status"] = ("LP HALT — rewrite plan inverts the original claim. "
                                "Pipeline stopped to prevent structural inversion.")
            return result
        # ───────────────────────────────────────────────────

        failed_layer = "L4"
        result["outputs"]["L4"] = _run_layer("L4", LAYER_PROMPTS["L4"](answer, result["outputs"]["L3"], result["outputs"]["W"]), max_tokens=2500)
        l4 = result["outputs"]["L4"]
        if len(l4.strip()) < 500 or "EXECUTION_ABORTED" in l4:
            result["score_after"] = score_before
            result["status"] = "L4 HALT — Execution failed or output was truncated. Pipeline stopped; downstream layers did not run."
            return result

        result["score_after"] = score_with_claude(l4, score_before)
        gaps_fixed = ["clarity", "structure", "depth"] if result["score_after"] > score_before else []
        failed_layer = "LR"
        result["outputs"]["LR"] = _run_layer("LR", LAYER_PROMPTS["LR"](answer, l4, score_before, result["score_after"]))
        failed_layer = "L6"
        result["outputs"]["L6"] = _run_layer("L6", LAYER_PROMPTS["L6"](score_before, result["score_after"], gaps_fixed))
        failed_layer = "L7"
        result["outputs"]["L7"] = _run_layer("L7", LAYER_PROMPTS["L7"](result["outputs"]["LR"], result["outputs"]["L6"]), max_tokens=2500)
        failed_layer = "L8"
        result["outputs"]["L8"] = _run_layer("L8", LAYER_PROMPTS["L8"](score_before, result["score_after"], gaps_fixed))
        failed_layer = "L9"
        raw_l9 = _run_layer("L9", LAYER_PROMPTS["L9"](result["outputs"]["L8"], score_before, result["score_after"], l4), max_tokens=400)
        questions = _parse_l9_questions(raw_l9)
        result["outputs"]["L9"] = "\n".join(f"Q: {question}" for question in questions)
        result["l9_questions"] = questions
        _REWRITER_L9_QUESTIONS = questions
        _REWRITER_QUESTION_INDEX = (_REWRITER_QUESTION_INDEX + 3) % len(QUESTION_BANK)
        failed_layer = "L10"
        result["outputs"]["L10"] = _run_layer("L10", LAYER_PROMPTS["L10"](l4, result["outputs"]["LR"], result["outputs"]["L7"],
                                                                           result["outputs"]["L8"], result["outputs"]["L9"], score_before, result["score_after"]), max_tokens=1200)
        result["audit"] = result["outputs"]["L10"]
        run_id = f"run_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"
        _REWRITER_BELIEFS.append(result["outputs"]["L8"][:200])
        save_rewriter_memory(run_id, result["outputs"]["L8"][:200], score_before, result["score_after"], len(_REWRITER_BELIEFS), questions)
        result["status"] = f"✅ Pipeline complete · score {score_before} → {result['score_after']} · L9 questions saved for the next run."
        return result
    except PipelineLayerError as exc:
        result["status"] = f"❌ {exc}. Pipeline stopped; later layers were not called."
        return result
    except Exception as exc:
        result["status"] = f"❌ {failed_layer} failed unexpectedly: {exc}. Pipeline stopped; later layers were not called."
        return result

def _result_to_ui(result):
    return ([result.get("score_before"), result.get("score_after"), result.get("status", "")] +
            [result.get("outputs", {}).get(layer_id, "") for layer_id in PIPELINE_ORDER] +
            [result.get("audit", ""), result.get("l9_questions", [])])

def run_rewriter(answer, context="", prior_questions=None):
    """Admin callback: run all permitted layers and return ordered UI artifacts."""
    return _result_to_ui(_execute_rewriter(answer, context, prior_questions))

GUMROAD_URL = "https://4175358678144.gumroad.com/l/tbphpi"

def run_public_rewriter(answer, context="", prior_questions=None, free_run_count=0):
    """Public callback using a Gradio session state counter for three free runs."""
    try:
        count = int(free_run_count or 0)
    except Exception:
        count = 0
    if count >= 3:
        blocked = _empty_pipeline_result(f"You've used your 3 free runs. Subscribe to continue: [{GUMROAD_URL}]({GUMROAD_URL})")
        return _result_to_ui(blocked)[:-1] + [list(prior_questions or []), count]
    result = _execute_rewriter(answer, context, prior_questions)
    # Do not consume a free run for an empty submission; this is validation,
    # not an execution attempt.
    if not (answer or "").strip():
        return _result_to_ui(result)[:-1] + [list(prior_questions or []), count]
    new_count = count + 1
    if new_count >= 3:
        result["status"] += f"\n\nYou've used your 3 free runs. Subscribe to continue: [{GUMROAD_URL}]({GUMROAD_URL})"
    else:
        result["status"] += f" · {3 - new_count} free run(s) remaining."
    ui = _result_to_ui(result)
    return ui[:-1] + [result.get("l9_questions", []), new_count]

def copy_all_rewriter_report(score_before, score_after, status, *parts):
    outputs = list(parts)
    report = ["4CBON2 AI REWRITER — PIPELINE RUN REPORT", f"Score: {score_before} → {score_after}", f"Status: {status}", "=" * 72]
    for layer_id, content in zip(PIPELINE_ORDER, outputs):
        if content:
            report.extend([f"\n{layer_id}", "-" * 72, str(content)])
    return "\n".join(report)



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
