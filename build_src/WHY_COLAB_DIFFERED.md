# Why the Colab edition never hit MAX_TOKENS
# (findings that drive the token-budget policy in space_gemini/app.py)

Two things were true in the Colab notebooks. Only their **combination** is what
broke this port, and the first of them was mis-attributed in an earlier draft of
this note: the parameters were not dropped by Colab's API, they were dropped by
the notebook's own wrapper, one level up.

## 1. The caps were never forwarded — by the notebook, not by Colab

Cell 2 of every Colab edition defines a wrapper with a generic LLM signature:

    def generate_text(prompt, max_tokens=4096, temperature=0.4, stream=False):
        """Generate text using google.colab.ai (OAuth; no API key required)."""
        try:
            full_response = []
            for chunk in ai.generate_text(prompt=prompt, model_name=MODEL_NAME, stream=True):
                if chunk is not None:
                    full_response.append(chunk)
            return "".join(full_response)
        except Exception as e:
            return f"⚠️ API Error: {str(e)}"

Three of the four parameters never reach the model:

| Parameter | Accepted by wrapper | Forwarded to `ai.generate_text` |
| --- | --- | --- |
| `prompt` | yes | **yes** |
| `max_tokens` | yes (default 4096) | **no** — dropped |
| `temperature` | yes (default 0.4) | **no** — dropped |
| `stream` | yes (default False) | **no** — hardcoded to `True` |

`stream=False` is inert too: every caller that asked for non-streaming got a
stream anyway, which was harmless only because the wrapper re-joins the chunks
before returning. The signature advertised a conventional LLM client; the body
wired through `prompt` alone.

This is the whole call surface — 4 sites across the 3 Colab editions, all
identical, none forwarding either parameter:

    4CBOn2_Gemini.ipynb    2 call sites  passes max_tokens? False  passes temperature? False
    4CBOn2_Gemini2b.ipynb  1 call site   passes max_tokens? False  passes temperature? False
    4CBOn2_Gemini2c.ipynb  1 call site   passes max_tokens? False  passes temperature? False

## 2. The numbers were nonetheless correct

It is tempting to read `max_tokens=5` as a typo. It is not. Each tiny cap was
sized to the **visible output** that layer actually emits:

| Layer | Cap | What it returns | Why the cap is right |
| --- | --- | --- | --- |
| LP | 5 | `YES` / `NO` | the caller only tests `startswith("YES")` to halt the pipeline |
| scorer | 10 | a bare integer | prompt ends "Reply with ONLY a single integer 0-100. Nothing else." |
| L2 (HIGH_QUALITY) | 50 | a short verdict | `PRESERVE` / `ESCALATE` / a one-line rationale |

The notebook was internally consistent: it specified terse output contracts in
the prompts and then sized the budget to match. For a model with no reasoning
phase, that is correct engineering.

## 3. So the mismatch is one of units, not of intent

The Google Generative AI API *does* honour the cap. On Gemini 3,
`max_output_tokens` is a **combined** budget for thinking tokens plus visible
output, and thinking is on by default. A field the notebook used to mean
"visible characters I expect" now has to also fit the reasoning that precedes
them. A cap of 5 is therefore consumed entirely by internal reasoning and
returns `finish_reason=MAX_TOKENS` with zero visible characters.

Corroborating reports:

    googleapis/python-genai#2062 — max_output_tokens=1024 -> ~906 thinking tokens
      -> truncated -> MAX_TOKENS. Also: omitting max_output_tokens entirely can
      hang 20+ minutes.
    valentinfrlch/ha-llmvision#609 — gemini-3-flash-preview with
      maxOutputTokens:50 -> "content": {}, finishReason: MAX_TOKENS,
      thoughtsTokenCount: 47.

Colab additionally ran `available_models[0]` = `google/gemini-2.5-flash`, not
Gemini 3, so it had less thinking overhead to absorb in the first place.

The failures are therefore a **porting artifact**: correct, deliberate numbers
written for visible-output semantics, carried faithfully into an API where the
same field means visible output *plus* reasoning.

## Policy adopted

1. The notebook's `max_tokens` values are **reinterpreted, not deleted**. They
   remain visible-output hints and are floored at `MIN_OUTPUT_TOKENS` to leave
   room for reasoning. Deleting them would throw away a correct expression of
   each layer's output contract.
2. `thinking_level=LOW` (measured ~1,377 thinking tokens vs ~15,726 at HIGH for
   identical output) to keep latency and cost sane across 20–50 call runs. Not
   `MINIMAL` — some Gemini 3 models reject it outright.
3. The cap is **always** set, never omitted, to avoid the indefinite hang.
4. On an empty `MAX_TOKENS` response, escalate the budget ×4 and retry; if the
   model rejects `thinking_config`, retry without it.
5. `temperature` is now live. That is a deliberate behaviour change, and a
   beneficial one: the prompts demand exact formats (JSON, one word, a bare
   integer), so the low temperatures the notebook asked for but never got are
   exactly what these layers want.
6. `stream` is now live as well — the UI genuinely streams, which the Colab
   wrapper only pretended to support.
