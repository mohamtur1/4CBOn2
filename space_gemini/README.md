---
title: 4CBON2 — Gemini Frontier Research Edition
emoji: 🧠
colorFrom: indigo
colorTo: purple
sdk: gradio
sdk_version: 5.50.0
app_file: app.py
pinned: false
license: mit
---

# 🚀 4CBON2 — 12-Agent Cognitive Ecosystem (Gemini Frontier Research Edition)

A Hugging Face Space port of [`4CBOn2_Gemini2c.ipynb`](https://github.com/mohamtur1/4CBOn2/blob/main/4CBOn2_Gemini2c.ipynb)
— the public **Gemini2 Frontier Research** edition — running on the
**Google Generative AI API** instead of Colab's built-in `google.colab.ai`.

## ✨ Tabs

| Tab | What it does |
| --- | --- |
| 📁 **Upload Documents** | Index `.txt`, `.pdf`, `.docx` into the ChromaDB research store |
| ❓ **Ask a Question** | Source-grounded research answers: local AI/Maths/Science vector DBs + live arXiv, OpenAlex, Semantic Scholar, Crossref, PubMed, Europe PMC, OEIS and official NIST/Clay/NIH/NASA results, cited as `[S1]…[Sn]` |
| 🤖 **Agent Mode** | Multi-agent orchestration across 12 specialists, 10 tools, streaming log |
| 🧠 **AI Rewriter** | The 16-layer cognitive pipeline (see below), **3 free runs per session** |
| ✉️ **Request Custom Agent** | Prepares an email draft to `mohamtur1@gmail.com` |
| 📊 **Data Dashboard** | Plotly views over task memory |
| 📊 **Agent Status** | Live agent conversation histories |
| ℹ️ **About** | Model, key handling and cost notes |

## 🔑 Bring your own Google API key

1. Create a free key at **[Google AI Studio](https://aistudio.google.com/apikey)**.
2. Paste it into the **Google API Key** field on any tab. It is remembered for that browser session (`gr.State`), so you only type it once.
3. Pick a model — default **`gemini-3.6-flash`**.
4. Click **🔌 Test Connection** to verify before running anything expensive.

Your key is **never written to disk or logged**. Each request binds the key through
`contextvars`, and every `ThreadPoolExecutor` submit copies that context — so concurrent
visitors to this public Space cannot use your key or your quota.

> Alternatively set `GEMINI_API_KEY` as a **Space secret** to run without pasting a key.
> On a *public* Space that exposes your key and quota to every visitor, so prefer the UI field.

### ⚠️ Cost awareness

| Action | Approx. Gemini calls |
| --- | --- |
| Ask a Question | 1 |
| AI Rewriter (one run) | ~20 — 17 layers + a 3-call median score before and after |
| Agent Mode (one run) | 15–50 depending on the plan |

## 🧠 AI Rewriter pipeline order

```
L0 → P → W → LX → LA → LC → L1 → L2 → L3 → LP → L4 → LR → L6 → L7 → L8 → L9 → L10
```

**L3 (Rewrite Planner) runs before LP (Policy Translation)**, and LP gates on the L3
rewrite plan rather than the L2 evaluation text. LP was previously pattern-matching L2's
critique language, which could trigger a false `LP HALT`; it now compares the original
answer's core factual claim against the proposed plan, ignoring added caveats,
restructuring, tone changes, safety additions and evidence qualifications.

The UI layer heading is generated from `PIPELINE_ORDER`, so it cannot drift out of sync
with the boxes beneath it.

## 🔁 Differences from the Colab notebook

| Concern | Colab notebook | This Space |
| --- | --- | --- |
| LLM | `google.colab.ai` (OAuth, no key) | `google-genai` with a user-supplied key |
| Model | first of `ai.list_models()` | `gemini-3.6-flash` (UI-selectable) |
| Key scope | single user | per-request `contextvars` |
| Storage | `/content/drive/MyDrive/...` | `./data/...` (ephemeral) |
| Launch | `demo.launch(share=True, inline=False)` | `demo.launch(server_name="0.0.0.0", server_port=7860)` |
| Rewriter gate | `run_rewriter` wired (unlimited) | `run_public_rewriter` (3 free runs + Gumroad CTA) |
| Embeddings | ONNX MiniLM downloaded | ONNX MiniLM, with a local hashed fallback if the download is unavailable |

Supabase memory stays optional and env-driven (`SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`).
Set them as Space secrets; never commit the service-role key.

## 🗂 Ephemeral storage

Everything writable lives under `./data/` — ChromaDB, tool logs, notes, CSV/PDF exports,
`4cbon2_agents.db`, `4cbon2_audit.jsonl`, `4cbon2_task_memory.db`. **Space storage resets
on restart or sleep.** The curated AI / Mathematics / Science research indexes are reseeded
deterministically on every boot, so retrieval quality is unaffected.

## 🚀 Deploy

```bash
HF_USERNAME=<your-hf-username> ./deploy_gemini_space.sh [space-name]
```

Or by hand:

```bash
hf repo create <space-name> --type space --sdk gradio -y
hf upload <your-hf-username>/<space-name> space_gemini/ --repo-type=space
```

## 🔧 Local development

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python app.py            # http://127.0.0.1:7860
```

Regenerate `app.py` from the notebook after editing it:

```bash
python build_gemini_space.py
```

The build lifts every large blob (`CURATED_DATABASES`, `QUESTION_BANK`, all layer prompts,
agent profiles, scholarly searchers) **verbatim** from the notebook and applies 26 asserted
rewrites. Any pattern that fails to match aborts the build instead of silently shipping a
broken app.
