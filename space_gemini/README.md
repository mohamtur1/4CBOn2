---
title: 4CBON2 — Gemini Frontier Research Edition
emoji: 🧠
colorFrom: indigo
colorTo: purple
sdk: gradio
sdk_version: 6.26.0
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
| 🩺 **Diagnostics** | Every Gemini call: finish reason, output vs thinking tokens, budget, latency |
| ℹ️ **About** | Model, key handling, token policy and cost notes |

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
| `max_tokens` / `temperature` | accepted then **silently dropped** | honoured, with a floor + escalation ladder (see below) |
| Thinking | n/a on `gemini-2.5-flash` via Colab | pinned `LOW`, with a retry if the model rejects it |
| Failure visibility | errors returned as strings | finish reason + token split recorded in 🩺 Diagnostics |
| Long runs | none in Colab | deadline → labelled partial report; heartbeat; cancellation persisted |
| Gradio | n/a | 6.x (`gr.Dataframe(height=)` became `max_height=` in 6.0) |
| Storage | `/content/drive/MyDrive/...` | `./data/...` (ephemeral) |
| Launch | `demo.launch(share=True, inline=False)` | `demo.launch(server_name="0.0.0.0", server_port=7860)` |
| Rewriter gate | `run_rewriter` wired (unlimited) | `run_public_rewriter` (3 free runs + Gumroad CTA) |
| Embeddings | ONNX MiniLM downloaded | ONNX MiniLM, with a local hashed fallback if the download is unavailable |

Supabase memory stays optional and env-driven (`SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`).
Set them as Space secrets; never commit the service-role key.

## 🧮 Token budget policy — and why Colab never hit this

In the Colab notebooks, `generate_text(prompt, max_tokens=..., temperature=...)` accepted
those two arguments and then **silently dropped them**. Every call site was:

```python
ai.generate_text(prompt=prompt, model_name=MODEL_NAME, stream=True)
```

Verified across all three Colab editions — 4 call sites, none forwarding `max_tokens` or
`temperature`. So there was **no output cap at all**, and values like `max_tokens=5` on the
LP layer and `max_tokens=10` on the scorer were decorative.

The Google Generative AI API honours the cap. On Gemini 3, `max_output_tokens` is a
**combined** budget for thinking tokens *plus* visible output, and thinking is on by
default — so a cap of 5 is consumed entirely by internal reasoning and returns
`finish_reason=MAX_TOKENS` with zero visible characters. That is what broke LP.

| Guard | Behaviour |
| --- | --- |
| Output floor | `max(requested, GEMINI_MIN_OUTPUT_TOKENS)` — the notebook's numbers are treated as hints |
| Thinking | pinned to `GEMINI_THINKING_LEVEL` (default `LOW`; ~1,377 thinking tokens vs ~15,726 at `HIGH`) |
| Escalation | an empty `MAX_TOKENS` response retries at ×4 the budget, up to the ceiling |
| Model variance | if a model rejects `thinking_config`, the call retries without it |
| Hang prevention | the cap is **never** omitted — an uncapped Gemini 3 call can hang indefinitely |
| Error guard | `is_llm_error()` stops an `⚠️ API Error` string from being accepted as a specialist's finding and propagated into the synthesis |

`temperature` is now live too — a deliberate change, since the prompts demand exact formats
(JSON, one word, a bare integer).

### 🩺 Diagnosing a failure

Open the **Diagnostics** tab. `MAX_TOKENS` with `Out tok = 0` and a large `Think tok` means
thinking consumed the budget. The Rewriter status line and the Agent Mode log both end with
a per-request summary (`N Gemini call(s) · X output / Y thinking tokens · Zs`), scoped to
*your* request so other visitors' calls do not pollute it.

### ⚙️ Space secrets

| Secret | Default | Purpose |
| --- | --- | --- |
| `GEMINI_API_KEY` | — | Optional shared key. On a public Space this exposes your quota to every visitor |
| `GEMINI_MODEL` | `gemini-3.6-flash` | Default model |
| `GEMINI_THINKING_LEVEL` | `LOW` | `LOW` / `MEDIUM` / `HIGH`, or `DEFAULT` to send no thinking config |
| `GEMINI_MIN_OUTPUT_TOKENS` | `4096` | Output floor |
| `GEMINI_MAX_OUTPUT_TOKENS_CEILING` | `16384` | Escalation ceiling |
| `FOURCBON2_AGENT_DEADLINE` | `600` | Agent Mode wall-clock seconds before a labelled partial report |
| `FOURCBON2_HEARTBEAT` | `15` | Seconds between "still working…" heartbeats |
| `FOURCBON2_DATA_DIR` | `./data` | Where runtime data is written |
| `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | — | Optional Rewriter memory (L8 beliefs, L9 questions) |

### 🛡 Agent Mode resilience

A 12-subtask plan with up to 3 tool iterations is 30+ sequential Gemini calls, which can
outlive a proxy timeout. So:

* a **wall-clock deadline** stops the run and emits a clearly-labelled partial report from
  whatever completed, instead of dying;
* a **heartbeat** is emitted every `FOURCBON2_HEARTBEAT` seconds during a slow call, so the
  stream is never idle;
* when Gradio cancels the generator it raises `GeneratorExit`, which derives from
  `BaseException` — the notebook's `except Exception` never saw it, so runs died without a
  trace. That path now persists the partial specialist reports to task memory (recoverable
  from the Data Dashboard) and writes `orchestrator_interrupted` to the audit log.

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
agent profiles, scholarly searchers) **verbatim** from the notebook and applies 36 asserted
rewrites. Any pattern that fails to match aborts the build instead of silently shipping a
broken app, and a completeness guard fails the build if any notebook function goes missing
from the output.

Run the test suite (97 checks, no network needed — it mocks `genai.Client`):

```bash
python test_gemini_space.py
```

It reproduces the real production failures rather than just the happy path: an empty
`MAX_TOKENS` response, a model that rejects `thinking_config`, overlapping requests from two
different keys, the Agent Mode deadline, heartbeat emission, and `GeneratorExit`
cancellation persistence.
