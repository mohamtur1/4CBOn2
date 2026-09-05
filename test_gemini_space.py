#!/usr/bin/env python3
"""End-to-end test of space_gemini/app.py against a mocked Google Generative AI API.

The sandbox cannot reach generativelanguage.googleapis.com, so this replaces
`genai.Client` with a fake that records every call. That still exercises the real
generate_text() code path, the contextvars key binding, the RAG routing, the
12-agent orchestrator and all 17 Rewriter layers — everything except the HTTP call.
"""
import os
import re
import sys
import threading
import warnings

warnings.simplefilter("ignore")
os.environ["FOURCBON2_DATA_DIR"] = "./data_test"

APP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "space_gemini", "app.py")
CALLS = []
CALLS_LOCK = threading.Lock()
FAILURES = []


def check(label, condition, detail=""):
    status = "PASS" if condition else "FAIL"
    print(f"[{status}] {label}" + (f" — {detail}" if detail else ""))
    if not condition:
        FAILURES.append(label)


# ════════════════════════════════════════════════════════════
# Fake google-genai client
# ════════════════════════════════════════════════════════════
class FakeResponse:
    def __init__(self, text):
        self.text = text
        self.candidates = []
        self.prompt_feedback = None


class FakeModels:
    def generate_content(self, *, model, contents, config=None):
        prompt = str(contents)
        with CALLS_LOCK:
            CALLS.append({
                "key": _CURRENT_KEY.get(),
                "model": model,
                "max_tokens": getattr(config, "max_output_tokens", None),
                "temperature": getattr(config, "temperature", None),
                "prompt_head": prompt[:90].replace("\n", " "),
                "prompt": prompt,
                "prompt_len": len(prompt),
            })
        return FakeResponse(_fake_reply(prompt))

    def generate_content_stream(self, *, model, contents, config=None):
        yield self.generate_content(model=model, contents=contents, config=config)


class FakeClient:
    def __init__(self, api_key=None):
        # Record the key this client was constructed with, per thread.
        _CURRENT_KEY_LOCAL.key = api_key
        _CURRENT_KEY.set(api_key)
        self.models = FakeModels()


_CURRENT_KEY_LOCAL = threading.local()
import contextvars
_CURRENT_KEY = contextvars.ContextVar("fake_key", default=None)


def _fake_reply(prompt):
    if "Reply with ONLY a single integer 0-100" in prompt:
        return "45"                      # < 68 → STANDARD operating mode
    if "Answer with exactly one word: YES or NO" in prompt:
        return "NO"                      # LP does not halt
    if "You are L4 — Finalization Engine" in prompt:
        return ("Improved answer. " * 60)  # > 500 chars → passes the truncation guard
    if "You are L9 — Socratic Integrity Engine" in prompt:
        return ("Q: Did L3 hand LP a plan it could actually judge?\n"
                "Q: Was the LP verdict sensitive to the rewrite plan wording?\n"
                "Q: Did the pipeline serve the author's goal?")
    if "You are L2 — Evaluation Layer" in prompt:
        return "PROCEED — H1 adds precision without audience overshoot."
    if "Output as JSON array" in prompt:
        return ('[{"subtask": "Analyse competitor positioning", "specialist": "Competitive Intelligence",'
                ' "instructions": "Map the top three competitors."},'
                ' {"subtask": "Review contract liability", "specialist": "Legal Document Intelligence",'
                ' "instructions": "Summarise indemnity clauses."}]')
    if "respond with ONLY this JSON" in prompt:
        return '{"action": "final_answer", "content": "Specialist finding: no material issues identified."}'
    return "Mocked Gemini analysis for this layer."


# ════════════════════════════════════════════════════════════
# Boot the app with the fake client injected
# ════════════════════════════════════════════════════════════
source = open(APP, encoding="utf-8").read()
LAUNCH = 'demo.launch(server_name="0.0.0.0", server_port=int(os.environ.get("PORT", 7860)))'
assert LAUNCH in source, "launch line not found"
source = source.replace(LAUNCH, "")          # do not bind a port during tests
source = source.replace("demo.queue()", "")

namespace = {"__name__": "app_under_test", "__file__": os.path.abspath(APP)}
exec(compile(source, APP, "exec"), namespace)

import google.genai
google.genai.Client = FakeClient             # patch the SDK class the app calls
namespace["genai"].Client = FakeClient

print("\n" + "=" * 70)
print("END-TO-END TESTS")
print("=" * 70)

# ── 1. Key isolation ────────────────────────────────────────
check("init_client rejects an empty key",
      namespace["init_client"]("", None).startswith("⚠️"))

with namespace["gemini_session"]("KEY_A", "gemini-3.6-flash"):
    check("key bound inside session", namespace["_current_api_key"]() == "KEY_A")
    check("model bound inside session", namespace["current_model_name"]() == "gemini-3.6-flash")
check("key cleared after session", namespace["_current_api_key"]() == "")

seen = {}
barrier = threading.Barrier(2)


def worker(name, key):
    with namespace["gemini_session"](key, "gemini-3.6-flash"):
        barrier.wait(timeout=10)                       # force genuine overlap
        namespace["generate_text"]("hello", max_tokens=8)
        seen[name] = _CURRENT_KEY.get()


threads = [threading.Thread(target=worker, args=(n, k))
           for n, k in (("a", "KEY_A"), ("b", "KEY_B"))]
for t in threads:
    t.start()
for t in threads:
    t.join(timeout=30)
check("concurrent sessions keep separate keys",
      seen.get("a") == "KEY_A" and seen.get("b") == "KEY_B", str(seen))

# ── 2. Scorer thread pool inherits the key ──────────────────
CALLS.clear()
with namespace["gemini_session"]("KEY_SCORER", "gemini-3.6-flash"):
    score = namespace["score_with_claude"]("Some answer to score.")
keys_used = {c["key"] for c in CALLS}
check("score_with_claude made 3 parallel calls", len(CALLS) == 3, f"{len(CALLS)} calls")
check("all 3 scorer workers saw the session key", keys_used == {"KEY_SCORER"}, str(keys_used))
check("scorer returns an int", isinstance(score, int), str(score))

# ── 3. RAG routing ─────────────────────────────────────────
for question, expect in [
    ("How do I build an AGI-oriented agent with neural planning?", "ai"),
    ("Is there a proof of the Riemann Hypothesis via the zeta function?", "mathematics"),
    ("What clinical experiment could test this biology hypothesis?", "science"),
]:
    domains = namespace["infer_research_domains"](question)
    check(f"routes to {expect}", expect in domains, str(domains))

with namespace["gemini_session"]("KEY_RAG", "gemini-3.6-flash"):
    answer, report = namespace["handle_ask_question"](
        namespace["COLLECTION_NAME"],
        "How do I build an AGI-oriented agent?",
        use_live_databases=False,          # sandbox has no outbound scholarly access
        return_report=True,
    )
check("RAG produced an answer", bool(answer) and not answer.startswith("❌"), answer[:80])
check("RAG produced a retrieval report", "Domains:" in report, report[:110])

# ── 4. Orchestrator ────────────────────────────────────────
with namespace["gemini_session"]("KEY_ORCH", "gemini-3.6-flash"):
    out = "".join(namespace["run_orchestrator_stream"]("Analyse competitor positioning and contract liability"))
check("orchestrator emitted a multi-agent report", "# 🧠 Multi-Agent Report" in out, f"{len(out)} chars")
check("orchestrator delegated to Competitive Intelligence", "Competitive Intelligence" in out)
check("orchestrator reached final synthesis", "## 🧬 Final Answer" in out)

# ── 5. Rewriter: full 17-layer pipeline ────────────────────
order = namespace["PIPELINE_ORDER"]
check("PIPELINE_ORDER runs L3 before LP", order.index("L3") < order.index("LP"), " → ".join(order))
check("PIPELINE_ORDER has 17 artifacts", len(order) == 17, str(len(order)))
check("_lp takes the L3 plan",
      list(namespace["_lp"].__code__.co_varnames[:2]) == ["answer", "l3"],
      str(list(namespace["_lp"].__code__.co_varnames[:2])))

CALLS.clear()
with namespace["gemini_session"]("KEY_RW", "gemini-3.6-flash"):
    ui = namespace["run_public_rewriter"]("An AI answer that needs improvement.", "Make it clearer.", [], 0)

status = ui[2]
layer_outputs = dict(zip(order, ui[3:3 + len(order)]))
check("rewriter completed the pipeline", "Pipeline complete" in status, status[:120])
check("every layer produced output", all(layer_outputs[k].strip() for k in order),
      str([k for k in order if not layer_outputs[k].strip()]))
# _run_layer stamps every prompt with "YOU ARE NOW EXECUTING: <id> — <name>".
# Filter to those, because CALLS also holds the scorer calls (which have no marker).
_LAYER_RE = re.compile(r"YOU ARE NOW EXECUTING: (\S+)")
layer_calls = [(c, _LAYER_RE.search(c["prompt"]).group(1)) for c in CALLS if _LAYER_RE.search(c["prompt"])]
executed = [name for _, name in layer_calls]
check("layers executed in PIPELINE_ORDER", executed == order, " ".join(executed))

l3_index = executed.index("L3") if "L3" in executed else None
lp_index = executed.index("LP") if "LP" in executed else None
check("L3 was called before LP",
      l3_index is not None and lp_index is not None and l3_index < lp_index,
      f"L3@{l3_index} LP@{lp_index}")

lp_prompt = next(c["prompt"] for c, name in layer_calls if name == "LP")
check("LP received the L3 REWRITE PLAN (not L2 text)",
      "REWRITE PLAN FROM L3" in lp_prompt and "ORIGINAL ANSWER (first 200 chars)" in lp_prompt,
      lp_prompt[-320:].replace("\n", " ")[:150])
check("LP no longer compares a 'Proposal' from L2", "Proposal:" not in lp_prompt)
check("all rewriter calls used the session key",
      {c["key"] for c in CALLS} == {"KEY_RW"}, str({c["key"] for c in CALLS}))
check("L9 produced 3 questions", len(ui[-2]) == 3, str(ui[-2])[:100])
check("run counter incremented to 1", ui[-1] == 1, str(ui[-1]))

# ── 6. The 3-free-run public gate ──────────────────────────
with namespace["gemini_session"]("KEY_RW", "gemini-3.6-flash"):
    blocked = namespace["run_public_rewriter"]("Another answer.", "", [], 3)
check("4th run is blocked by the paywall", "3 free runs" in blocked[2], blocked[2][:120])
check("paywall surfaces the Gumroad URL",
      namespace["GUMROAD_URL"] in blocked[2], namespace["GUMROAD_URL"])

# ── 7. Tools write under DATA_DIR, never /content/drive ────
note = namespace["execute_tool"]("save_note", "hello from a test")
check("save_note writes under the data dir", "data_test" in note and "/content/drive" not in note, note)
pdf = namespace["execute_tool"]("generate_pdf", "Report line one\nReport line two")
check("generate_pdf writes under the data dir", "data_test" in pdf and "/content/drive" not in pdf, pdf)
csv_out = namespace["execute_tool"]("write_csv", '[{"a": 1, "b": 2}]')
check("write_csv writes under the data dir", "data_test" in csv_out and "/content/drive" not in csv_out, csv_out)
check("get_datetime tool works", "Date:" in namespace["execute_tool"]("get_datetime"))
check("query_database blocks non-SELECT",
      namespace["execute_tool"]("query_database", "DROP TABLE agents").startswith("❌"))

# ── 8. Dashboard ───────────────────────────────────────────
figs, err = namespace["create_plotly_dashboard"]()
check("dashboard renders figures", err is None and figs and len(figs) >= 2, f"err={err} figs={len(figs or [])}")

# ── 9. Copy All report ─────────────────────────────────────
report = namespace["copy_all_rewriter_report"](45, 62, "ok", *["text"] * len(order))
check("Copy All report labels layers in pipeline order",
      report.index("\nL3") < report.index("\nLP"), "L3 before LP in report")

# ── 10. UI callback arity must match declared outputs ──────
# Gradio only discovers a mismatch when a visitor clicks, so check it here.
# (Handlers nested inside `with gr.Blocks()` are still module-level: `with`
# creates no new scope in Python.)
with namespace["gemini_session"]("KEY_UI", "gemini-3.6-flash"):
    pass

agent_chunks = list(namespace["run_agent"](
    "Analyse competitor positioning", "KEY_UI", "", "gemini-3.6-flash", True, False))
check("run_agent is a generator of 2-tuples (log, key_update)",
      agent_chunks and all(isinstance(c, tuple) and len(c) == 2 for c in agent_chunks),
      f"{len(agent_chunks)} yields")
check("run_agent log accumulates progressively",
      len(agent_chunks) > 3 and len(agent_chunks[-1][0]) > len(agent_chunks[0][0]),
      f"first={len(agent_chunks[0][0])} last={len(agent_chunks[-1][0])} chars")
check("run_agent log contains the full report",
      "# 🧠 Multi-Agent Report" in agent_chunks[-1][0])

missing_agent = list(namespace["run_agent"]("goal", "", "", "gemini-3.6-flash", True, False))
check("run_agent refuses without a key (still a 2-tuple)",
      len(missing_agent) == 1 and isinstance(missing_agent[0], tuple) and len(missing_agent[0]) == 2
      and "❌" in missing_agent[0][0], str(missing_agent[0][0])[:70])

with namespace["gemini_session"]("KEY_UI", "gemini-3.6-flash"):
    ask_result = namespace["ask_five_lens"]("What is an agent?", False, "KEY_UI", "", "gemini-3.6-flash")
check("ask_five_lens returns 3 values (answer, status, key)", len(ask_result) == 3, str(len(ask_result)))

check_result = namespace["check_key_and_remember"]("KEY_UI", "", "gemini-3.6-flash")
check("check_key_and_remember returns 2 values", len(check_result) == 2, str(len(check_result)))

rw_ui = namespace["run_public_rewriter_ui"]("An answer.", "", [], 0, "KEY_UI", "", "gemini-3.6-flash")
expected_rw = 3 + len(order) + 4     # scores+status, 17 layer boxes, audit+l9+counter+key
check(f"run_public_rewriter_ui returns {expected_rw} values", len(rw_ui) == expected_rw, str(len(rw_ui)))

rw_blocked = namespace["run_public_rewriter_ui"]("An answer.", "", [], 3, "KEY_UI", "", "gemini-3.6-flash")
check("blocked run returns the same arity", len(rw_blocked) == expected_rw, str(len(rw_blocked)))
check("blocked run surfaces the CTA", "3 free runs" in rw_blocked[2], str(rw_blocked[2])[:70])

rw_nokey = namespace["run_public_rewriter_ui"]("An answer.", "", [], 0, "", "", "gemini-3.6-flash")
check("no-key run returns the same arity with an error", len(rw_nokey) == expected_rw and "❌" in rw_nokey[2],
      str(rw_nokey[2])[:70])

dash = namespace["load_dashboard"]()
check("load_dashboard returns 4 values", len(dash) == 4, str(len(dash)))
check("request_custom_agent validates email",
      namespace["request_custom_agent"]("bad", "desc", "Simple", "").startswith("❌"))
check("request_custom_agent builds a mailto draft",
      "mailto:mohamtur1@gmail.com" in namespace["request_custom_agent"]("a@b.com", "desc", "Simple", ""))
hidden = namespace["update_keys_visibility"](True, True)     # Gemini-only ⇒ hide
shown = namespace["update_keys_visibility"](False, True)     # additional enabled ⇒ show
check("update_keys_visibility hides keys in Gemini-only mode", getattr(hidden, "visible", None) is False)
check("update_keys_visibility reveals keys when enabled", getattr(shown, "visible", None) is True)

# ── 11. No secrets in the generated source ─────────────────
app_text = open(APP, encoding="utf-8").read()
check("no hardcoded API key literal", not re.search(r"AIza[0-9A-Za-z_\-]{10,}", app_text))
check("no Supabase service-role key literal", "eyJ" not in app_text)

print("\n" + "=" * 70)
print(f"RESULT: {len(FAILURES)} failure(s)" if FAILURES else "RESULT: all checks passed")
for f in FAILURES:
    print("  ✗", f)
print("=" * 70)
sys.exit(1 if FAILURES else 0)
