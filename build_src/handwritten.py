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
    try:
        yield
    finally:
        _API_KEY_VAR.reset(key_token)
        _MODEL_VAR.reset(model_token)


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
        return (
            f"model '{current_model_name()}' was not available for this key. "
            f"Try another model in the dropdown — {text}"
        )
    return text


def _empty_response_reason(response):
    """Explain a response with no text (usually a safety block)."""
    try:
        candidate = (getattr(response, "candidates", None) or [None])[0]
        if candidate is not None:
            reason = getattr(getattr(candidate, "finish_reason", None), "name", None)
            if reason:
                return f"model returned no text (finish reason: {reason})."
        feedback = getattr(response, "prompt_feedback", None)
        if feedback is not None and getattr(feedback, "block_reason", None):
            return f"prompt was blocked (block reason: {feedback.block_reason})."
    except Exception:
        pass
    return "model returned no text. Try rephrasing, or shorten the input."


def generate_text(prompt, max_tokens=2048, temperature=0.7, stream=False):
    """Generate text with the Google Generative AI API.

    Returns a plain string in both modes — ``ask_stream`` does its own
    word-by-word chunking for the UI, exactly as in the notebook.
    """
    api_key = _current_api_key()
    if not api_key:
        return (
            "⚠️ Error: no Google API key for this request. Paste your Gemini API key "
            f"into the **Google API Key** field ({API_KEY_HELP})."
        )
    model = current_model_name()
    try:
        # A fresh client per call keeps keys out of module state entirely.
        client = genai.Client(api_key=api_key)
        config = genai_types.GenerateContentConfig(
            max_output_tokens=int(max_tokens),
            temperature=float(temperature),
        )
        if stream:
            parts = []
            for chunk in client.models.generate_content_stream(
                model=model, contents=str(prompt), config=config
            ):
                text = getattr(chunk, "text", None)
                if text:
                    parts.append(text)
            return "".join(parts)

        response = client.models.generate_content(model=model, contents=str(prompt), config=config)
        try:
            text = response.text
        except Exception:
            text = None
        if text is None:
            return f"⚠️ API Error: {_empty_response_reason(response)}"
        return text
    except Exception as exc:
        return f"⚠️ API Error: {_friendly_api_error(exc)}"


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
            return (
                "⚠️ No Google API key provided. Paste your Gemini API key "
                f"({API_KEY_HELP}), or set GEMINI_API_KEY as a Space secret."
            )
        probe = generate_text("Reply with the single word: ready", max_tokens=8, temperature=0.0)
        if str(probe).startswith("⚠️"):
            return f"❌ {probe}"
        return (
            f"✅ Connected to **{current_model_name()}**. "
            f"This key will be used for the rest of this browser session."
        )


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

    with gemini_session(key, model):
        yield emit(f"✅ Using Google Generative AI ({current_model_name()}) — key supplied in the UI, memory only.\n\n")

        # Checkbox logic for additional APIs
        if use_gemini_only:
            yield emit("🔒 **Gemini Only mode** — all additional API keys ignored.\n\n")
        elif enable_additional:
            yield emit("🔓 **Additional APIs enabled** — injecting optional API keys.\n\n")
            key_names = ["CALENDAR_API_KEY", "CRM_API_KEY", "COMM_API_KEY", "VISION_API_KEY",
                         "DOCUSIGN_API_KEY", "SOCIAL_SCRAPER_API_KEY", "SEO_API_KEY", "S3_VAULT_KEY", "PUBMED_API_KEY"]
            for name, val in zip(key_names, api_keys):
                if val and val.strip():
                    os.environ[name] = val.strip()
        else:
            yield emit("🔒 **Additional APIs disabled** — using Gemini only.\n\n")

        try:
            for chunk in run_orchestrator_stream(goal):
                yield emit(chunk)
        except Exception as e:
            yield emit(f"❌ Orchestrator error: {str(e)}")
        finally:
            key_names = ["CALENDAR_API_KEY", "CRM_API_KEY", "COMM_API_KEY", "VISION_API_KEY",
                         "DOCUSIGN_API_KEY", "SOCIAL_SCRAPER_API_KEY", "SEO_API_KEY", "S3_VAULT_KEY", "PUBMED_API_KEY"]
            for name in key_names:
                os.environ.pop(name, None)


def check_key_and_remember(api_key, stored_key, model_name):
    """Test Connection: verify the key, then remember it for this session."""
    key, model = _resolve_session(api_key, stored_key, model_name)
    return check_api_key(key, model), gr.update(value=key)


# The layer heading is generated from PIPELINE_ORDER so it can never drift out of
# sync with the boxes below it (the revised pipeline runs L3 before LP).
PIPELINE_ORDER_LABEL = " → ".join(PIPELINE_ORDER)

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
                # outputs = [score_before, score_after, status, *17 layers, audit, l9, count]
                return list(outputs) + [gr.update(value=key)]

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
            `gemini-3.6-flash` is billed per token on your key.

            ### 🗂 Ephemeral storage
            Uploaded documents, cached live records, agent history and task memory live under `{DATA_DIR}`
            and **reset whenever the Space restarts or sleeps**. The curated AI / Mathematics / Science
            research indexes are reseeded automatically on every boot.
            """)

demo.queue()
demo.launch(server_name="0.0.0.0", server_port=int(os.environ.get("PORT", 7860)))
