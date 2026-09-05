# Why the Colab edition never hit MAX_TOKENS
# (findings that drive the token-budget policy in space_gemini/app.py)

`google.colab.ai.generate_text()` was called like this in **all three** Colab
notebooks (Gemini, Gemini2b, Gemini2c) — 4 call sites, all identical:

    for chunk in ai.generate_text(prompt=prompt, model_name=MODEL_NAME, stream=True):

`max_tokens` and `temperature` were accepted by the wrapper's signature and then
**silently dropped** — they were never forwarded to the model. Verified by
grepping every `ai.generate_text(...)` call site in the repo:

    4CBOn2_Gemini.ipynb    2 call sites  passes max_tokens? False  passes temperature? False
    4CBOn2_Gemini2b.ipynb  1 call site   passes max_tokens? False  passes temperature? False
    4CBOn2_Gemini2c.ipynb  1 call site   passes max_tokens? False  passes temperature? False

Consequences in Colab:

* There was **no output cap at all**, so thinking tokens had unlimited room and
  the model always finished with visible text.
* `max_tokens=5` on LP, `max_tokens=10` on the scorer, `max_tokens=50` on L2 in
  HIGH_QUALITY mode were **decorative**. They never constrained anything, so the
  pipeline's exact-format contracts ("one word: YES or NO", "ONLY a single
  integer") were always satisfiable.
* `temperature=0.1` / `0.35` were also decorative; Colab used its own default.
* The model was `available_models[0]` = `google/gemini-2.5-flash`, not Gemini 3.

So the failures are a **porting artifact**, not a pre-existing notebook bug:
those inert numbers were carried faithfully into the Google Generative AI API,
which *does* honour `max_output_tokens`. On Gemini 3 — where thinking is on by
default and `max_output_tokens` is a COMBINED budget for thinking + visible
output — a cap of 5 is entirely consumed by internal reasoning, returning
`finish_reason=MAX_TOKENS` with zero visible characters.

Corroborating reports:
  googleapis/python-genai#2062 — max_output_tokens=1024 -> ~906 thinking tokens
    -> truncated -> MAX_TOKENS. Also: omitting max_output_tokens entirely can
    hang 20+ minutes.
  valentinfrlch/ha-llmvision#609 — gemini-3-flash-preview with
    maxOutputTokens:50 -> "content": {}, finishReason: MAX_TOKENS,
    thoughtsTokenCount: 47.

Policy adopted, given the above:

1. The notebook's `max_tokens` values are treated as **hints, not caps**. They
   are floored at MIN_OUTPUT_TOKENS, because the numbers were never real
   constraints in the edition this pipeline was designed against.
2. `thinking_level=LOW` (measured ~1,377 thinking tokens vs ~15,726 at HIGH for
   identical output) to keep latency and cost sane across 20-50 call runs.
3. The cap is **always** set — never omitted — to avoid the indefinite hang.
4. On an empty MAX_TOKENS response, escalate the budget and retry; if the model
   rejects `thinking_config` (support varies: gemini-3.7-flash's card says
   `minimal` errors), retry without it.
5. `temperature` is now live. That is a deliberate behaviour change: the prompts
   demand exact formats (JSON, one word, a bare integer), so the low
   temperatures the notebook asked for but never got are a net win.
