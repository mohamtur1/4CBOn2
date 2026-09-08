#!/usr/bin/env python3
"""End-to-end test of space_gemini/app.py against a mocked Google Generative AI API.

The sandbox cannot reach generativelanguage.googleapis.com, so this replaces
`genai.Client` with a fake that records every call and can be scripted to
reproduce the real failures seen in production:

  * MAX_TOKENS with zero visible text (thinking consumed the whole budget)
  * a model that rejects `thinking_config` outright

Everything else is real: generate_text()'s escalation ladder, the contextvars key
binding, RAG routing, the 12-agent orchestrator, all 17 Rewriter layers, the
deadline and heartbeat paths, and cancellation persistence.
"""
import contextvars
import os
import re
import sys
import threading
import time
import warnings

warnings.simplefilter("ignore")
os.environ["FOURCBON2_DATA_DIR"] = "./data_test"

APP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "space_gemini", "app.py")
CALLS = []
CALLS_LOCK = threading.Lock()
FAILURES = []

# Scriptable mock behaviour.
MOCK = {
    "min_budget_for_text": None,   # empty MAX_TOKENS whenever budget < this
    "reject_thinking": False,      # raise if thinking_config is present
    "sleep_per_call": 0.0,
    "always_empty": False,
}


def check(label, condition, detail=""):
    print(f"[{'PASS' if condition else 'FAIL'}] {label}" + (f" — {detail}" if detail else ""))
    if not condition:
        FAILURES.append(label)


def reset_mock():
    MOCK.update(min_budget_for_text=None, reject_thinking=False, sleep_per_call=0.0, always_empty=False)


# ════════════════════════════════════════════════════════════
# Fake google-genai client
# ════════════════════════════════════════════════════════════
class FakeUsage:
    def __init__(self, prompt, out, thoughts):
        self.prompt_token_count = prompt
        self.candidates_token_count = out
        self.thoughts_token_count = thoughts
        self.total_token_count = prompt + out + (thoughts or 0)


class FakeCandidate:
    def __init__(self, finish):
        self.finish_reason = finish


class FakeResponse:
    def __init__(self, text, finish="STOP", prompt=900, out=None, thoughts=0):
        self._text = text
        self.candidates = [FakeCandidate(finish)]
        self.prompt_feedback = None
        self.usage_metadata = FakeUsage(prompt, out if out is not None else max(1, len(text) // 4), thoughts)

    @property
    def text(self):
        if not self._text:
            raise ValueError("Response has no text.")
        return self._text


class FakeModels:
    def generate_content(self, *, model, contents, config=None):
        prompt = str(contents)
        budget = getattr(config, "max_output_tokens", None)
        thinking = getattr(config, "thinking_config", None)
        if MOCK["sleep_per_call"]:
            time.sleep(MOCK["sleep_per_call"])

        rejected = MOCK["reject_thinking"] and thinking is not None
        starved = (MOCK["always_empty"]
                   or (MOCK["min_budget_for_text"] is not None and (budget or 0) < MOCK["min_budget_for_text"]))
        # Record BEFORE raising, so a rejected attempt is still visible to assertions.
        with CALLS_LOCK:
            CALLS.append({
                "key": _CURRENT_KEY.get(), "model": model, "budget": budget,
                "temperature": getattr(config, "temperature", None),
                "thinking_level": getattr(thinking, "thinking_level", None) if thinking else None,
                "prompt": prompt, "prompt_head": prompt[:90].replace("\n", " "),
                "starved": starved, "rejected": rejected,
            })
        if rejected:
            raise ValueError("thinking_level is not supported by this model")
        if starved:
            return FakeResponse("", finish="MAX_TOKENS", out=0, thoughts=budget or 0)
        return FakeResponse(_fake_reply(prompt))

    def generate_content_stream(self, *, model, contents, config=None):
        yield self.generate_content(model=model, contents=contents, config=config)


class FakeClient:
    def __init__(self, api_key=None):
        _CURRENT_KEY.set(api_key)
        self.models = FakeModels()


_CURRENT_KEY = contextvars.ContextVar("fake_key", default=None)


def _fake_reply(prompt):
    if "Reply with ONLY a single integer 0-100" in prompt:
        return "45"                      # < 68 → STANDARD operating mode
    if "Answer with exactly one word: YES or NO" in prompt:
        return "NO"                      # LP does not halt
    if "You are L4 — Finalization Engine" in prompt:
        return "Improved answer. " * 60   # > 500 chars → passes the truncation guard
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
assert LAUNCH in source, "launch line not found — UI section changed?"
source = source.replace(LAUNCH, "").replace("demo.queue()", "")

namespace = {"__name__": "app_under_test", "__file__": os.path.abspath(APP)}
exec(compile(source, APP, "exec"), namespace)

import google.genai
google.genai.Client = FakeClient
namespace["genai"].Client = FakeClient

print("\n" + "=" * 74)
print("END-TO-END TESTS")
print("=" * 74)

# ── 1. Key isolation ────────────────────────────────────────
check("init_client rejects an empty key", namespace["init_client"]("", None).startswith("⚠️"))
with namespace["gemini_session"]("KEY_A", "gemini-3.6-flash"):
    check("key bound inside session", namespace["_current_api_key"]() == "KEY_A")
    check("model bound inside session", namespace["current_model_name"]() == "gemini-3.6-flash")
check("key cleared after session", namespace["_current_api_key"]() == "")

seen, barrier = {}, threading.Barrier(2)


def worker(name, key):
    with namespace["gemini_session"](key, "gemini-3.6-flash"):
        barrier.wait(timeout=10)
        namespace["generate_text"]("hello", max_tokens=8)
        seen[name] = _CURRENT_KEY.get()


threads = [threading.Thread(target=worker, args=(n, k)) for n, k in (("a", "KEY_A"), ("b", "KEY_B"))]
for t in threads:
    t.start()
for t in threads:
    t.join(timeout=30)
check("concurrent sessions keep separate keys", seen.get("a") == "KEY_A" and seen.get("b") == "KEY_B", str(seen))

# ── 2. THE BUG: tiny caps must be floored, and thinking pinned ──
CALLS.clear()
with namespace["gemini_session"]("KEY_BUDGET", "gemini-3.6-flash"):
    out = namespace["generate_text"]("Answer with exactly one word: YES or NO", max_tokens=5)
sent_budget = CALLS[0]["budget"]
floor = namespace["MIN_OUTPUT_TOKENS"]
check("LP-style max_tokens=5 is floored, not sent as 5", sent_budget >= floor and sent_budget != 5,
      f"sent {sent_budget}, floor {floor}")
check("thinking_level is pinned on the request", CALLS[0]["thinking_level"] == namespace["THINKING_LEVEL"],
      str(CALLS[0]["thinking_level"]))
check("a floored tiny call returns real text", out.strip() == "NO", repr(out))
check("temperature is honoured (was dropped in Colab)", CALLS[0]["temperature"] is not None)

# ── 3. MAX_TOKENS escalation ladder ────────────────────────
reset_mock()
MOCK["min_budget_for_text"] = 9000       # starve anything below the escalated budget
CALLS.clear()
with namespace["gemini_session"]("KEY_ESC", "gemini-3.6-flash"):
    out = namespace["generate_text"]("Say something.", max_tokens=5)
budgets = [c["budget"] for c in CALLS]
check("starved call escalates the budget and retries", len(CALLS) >= 2 and budgets[-1] > budgets[0], str(budgets))
check("escalation eventually returns text", bool(out.strip()) and not out.startswith("⚠️"), repr(out[:60]))

reset_mock()
MOCK["always_empty"] = True
CALLS.clear()
with namespace["gemini_session"]("KEY_ESC2", "gemini-3.6-flash"):
    out = namespace["generate_text"]("Say something.", max_tokens=5)
check("exhausting the ladder surfaces a diagnosable ⚠️ error",
      out.startswith("⚠️") and "thinking" in out.lower(), repr(out[:110]))
check("the exhausted error names the thinking-token cause", "MAX_TOKENS" in out or "thinking" in out.lower())

# ── 4. thinking_config rejected by the model ───────────────
reset_mock()
MOCK["reject_thinking"] = True
CALLS.clear()
with namespace["gemini_session"]("KEY_NOTHINK", "gemini-3.6-flash"):
    out = namespace["generate_text"]("Say something.", max_tokens=5)
levels = [c["thinking_level"] for c in CALLS]
check("a rejected thinking_config is retried without it", None in levels and levels[0] is not None, str(levels))
check("the rejected attempt was actually sent first", CALLS[0]["rejected"] is True)
check("the call still succeeds after dropping thinking_config",
      bool(out.strip()) and not out.startswith("⚠️"), repr(out[:60]))
_retry_diags = [r for r in namespace["DIAGNOSTICS"] if r.get("status") == "retry"]
check("the rejection is recorded as a retry in diagnostics",
      any("thinking_config rejected" in (r.get("detail") or "") for r in _retry_diags),
      str(_retry_diags[-1].get("detail"))[:80] if _retry_diags else "none")
reset_mock()

# ── 5. is_llm_error guard ──────────────────────────────────
check("is_llm_error flags an API error string",
      namespace["is_llm_error"]("⚠️ API Error: model returned no text (finish reason: MAX_TOKENS)."))
check("is_llm_error flags empty", namespace["is_llm_error"]("   "))
check("is_llm_error passes real content", not namespace["is_llm_error"]("Specialist finding: all good."))

reset_mock()
MOCK["always_empty"] = True
with namespace["gemini_session"]("KEY_AGENT_ERR", "gemini-3.6-flash"):
    agent_out = namespace["execute_agent"]("Scientific Research", "Assess this claim.")
check("execute_agent never returns a raw API error as a specialist finding",
      "finish reason: MAX_TOKENS" not in agent_out or "could not generate a response" in agent_out,
      agent_out[:100])
reset_mock()

# ── 6. Diagnostics ─────────────────────────────────────────
CALLS.clear()
with namespace["gemini_session"]("KEY_DIAG", "gemini-3.6-flash"):
    namespace["generate_text"]("hello", max_tokens=5)
    line = namespace["request_diagnostics_summary"]()
check("per-request diagnostics summary counts this call only", line.startswith("1 Gemini call"), line)
summary, rows = namespace["build_diagnostics_view"]()
check("diagnostics table exposes the token split",
      rows and "Think tok" in namespace["DIAGNOSTIC_HEADERS"] and rows[0][6] != "",
      str(rows[0][:8]) if rows else "no rows")
check("diagnostics summary reports the active policy", "thinking level" in summary.lower(), summary[:90])
outside = namespace["request_diagnostics_summary"]()
check("summary is empty outside a session (no cross-request bleed)", outside == "no Gemini calls recorded", outside)

# ── 7. Scorer thread pool inherits the key ─────────────────
CALLS.clear()
with namespace["gemini_session"]("KEY_SCORER", "gemini-3.6-flash"):
    score = namespace["score_with_claude"]("Some answer to score.")
check("score_with_claude made 3 parallel calls", len(CALLS) == 3, f"{len(CALLS)}")
check("all 3 scorer workers saw the session key", {c["key"] for c in CALLS} == {"KEY_SCORER"})
check("scorer returns an int", isinstance(score, int), str(score))
check("scorer calls are floored too (was max_tokens=10)",
      all(c["budget"] >= namespace["MIN_OUTPUT_TOKENS"] for c in CALLS), str({c["budget"] for c in CALLS}))

# ── 8. RAG routing ─────────────────────────────────────────
for question, expect in [("How do I build an AGI-oriented agent with neural planning?", "ai"),
                         ("Is there a proof of the Riemann Hypothesis via the zeta function?", "mathematics"),
                         ("What clinical experiment could test this biology hypothesis?", "science")]:
    domains = namespace["infer_research_domains"](question)
    check(f"routes to {expect}", expect in domains, str(domains))

with namespace["gemini_session"]("KEY_RAG", "gemini-3.6-flash"):
    answer, report = namespace["handle_ask_question"](
        namespace["COLLECTION_NAME"], "How do I build an AGI-oriented agent?",
        use_live_databases=False, return_report=True)
check("RAG produced an answer", bool(answer) and not answer.startswith("❌"), answer[:70])
check("RAG produced a retrieval report", "Domains:" in report, report[:100])

# ── 9. Orchestrator ────────────────────────────────────────
with namespace["gemini_session"]("KEY_ORCH", "gemini-3.6-flash"):
    out = "".join(namespace["run_orchestrator_stream"](
        "Analyse competitor positioning and contract liability", deadline_seconds=300))
check("orchestrator emitted a multi-agent report", "# 🧠 Multi-Agent Report" in out, f"{len(out)} chars")
check("orchestrator delegated to Competitive Intelligence", "Competitive Intelligence" in out)
check("orchestrator reached final synthesis", "## 🧬 Final Answer" in out)
check("orchestrator announces the deadline/policy up front", "Deadline" in out and "output floor" in out)
check("no partial-run warning when inside the deadline", "Partial run" not in out)

# ── 10. Deadline produces a labelled PARTIAL report ────────
reset_mock()
MOCK["sleep_per_call"] = 0.35
with namespace["gemini_session"]("KEY_DL", "gemini-3.6-flash"):
    partial = "".join(namespace["run_orchestrator_stream"]("Analyse competitor positioning and liability",
                                                           deadline_seconds=0.6))
check("deadline tripped and said so", "Deadline reached" in partial, "")
check("deadline run is labelled as partial", "Partial run" in partial)
check("deadline run still produced a usable report", "# 🧠 Multi-Agent Report" in partial)
check("deadline run completed fewer subtasks than planned",
      re.search(r"(\d+) of (\d+) completed", partial) is not None
      and int(re.search(r"(\d+) of (\d+) completed", partial).group(1))
      < int(re.search(r"(\d+) of (\d+) completed", partial).group(2)),
      (re.search(r"\d+ of \d+ completed", partial) or [None]) and
      (re.search(r"\d+ of \d+ completed", partial).group(0) if re.search(r"\d+ of \d+ completed", partial) else "n/a"))
reset_mock()

# ── 11. Heartbeat keeps the stream alive ───────────────────
events = list(namespace["call_with_heartbeat"](lambda: (time.sleep(0.5), "done")[1],
                                                label="slow-agent", interval=0.15))
heartbeats = [p for k, p in events if k == "hb"]
results = [p for k, p in events if k == "result"]
check("heartbeat emits progress while a call is slow", len(heartbeats) >= 2, f"{len(heartbeats)} beats")
check("heartbeat still returns the real result", results == ["done"], str(results))
check("heartbeat messages are labelled", all("slow-agent" in h for h in heartbeats), heartbeats[0][:60])


def boom():
    raise RuntimeError("specialist exploded")


try:
    list(namespace["call_with_heartbeat"](boom, label="x", interval=0.1))
    check("heartbeat propagates worker exceptions", False, "no exception raised")
except RuntimeError as exc:
    check("heartbeat propagates worker exceptions", "exploded" in str(exc))

# ── 12. Cancellation persists partial work ─────────────────
before = namespace["load_task_memory_data"]()[0] or []
gen = namespace["run_agent"]("Analyse competitor positioning and contract liability",
                             "KEY_CANCEL", "", "gemini-3.6-flash", True, False)
advanced = 0
try:
    for _ in range(200):
        log, _upd = next(gen)
        advanced += 1
        if "done." in log and "Step 1/2" in log:
            break
    gen.close()                      # raises GeneratorExit inside the generator
    cancelled = True
except StopIteration:
    cancelled = False
check("generator was driven to a completed subtask before cancelling", advanced > 3, f"{advanced} yields")
after = namespace["load_task_memory_data"]()[0] or []
check("cancelling a run persists the partial report to task memory", len(after) > len(before),
      f"{len(before)} -> {len(after)}")
if len(after) > len(before):
    newest = after[0]
    check("the persisted record is labelled as cancelled", "cancelled" in newest["final_answer"].lower(),
          newest["final_answer"][:80])
    check("the persisted record keeps the specialist work", "Step 1" in newest["subtasks"], newest["subtasks"][:80])
audit = open(os.path.join(namespace["DATA_DIR"], "4cbon2_audit.jsonl"), encoding="utf-8").read()
check("the interruption was written to the audit log", "orchestrator_interrupted" in audit)

# ── 13. Rewriter: full 17-layer pipeline ───────────────────
order = namespace["PIPELINE_ORDER"]
check("PIPELINE_ORDER runs L3 before LP", order.index("L3") < order.index("LP"), " → ".join(order))
check("PIPELINE_ORDER has 17 artifacts", len(order) == 17, str(len(order)))
check("_lp takes the L3 plan", list(namespace["_lp"].__code__.co_varnames[:2]) == ["answer", "l3"])

CALLS.clear()
with namespace["gemini_session"]("KEY_RW", "gemini-3.6-flash"):
    ui = namespace["run_public_rewriter"]("An AI answer that needs improvement.", "Make it clearer.", [], 0)
status = ui[2]
layer_outputs = dict(zip(order, ui[3:3 + len(order)]))
check("rewriter completed the pipeline", "Pipeline complete" in status, status[:110])
check("every layer produced output", all(layer_outputs[k].strip() for k in order),
      str([k for k in order if not layer_outputs[k].strip()]))
check("NO layer call was starved by a tiny cap", not any(c["starved"] for c in CALLS),
      str([c["budget"] for c in CALLS][:6]))
check("the old LP cap of 5 is gone", 5 not in [c["budget"] for c in CALLS])
check("the old scorer cap of 10 is gone", 10 not in [c["budget"] for c in CALLS])

# The notebook's tiny caps were NOT typos — they were sized to each layer's
# visible output (LP is a YES/NO halt gate, the scorer emits a bare integer).
# The port must REINTERPRET them as hints, not delete them: the call sites keep
# expressing each layer's output contract, and the units translation happens
# once, in the LLM adapter. These two assertions pin that invariant from both
# sides so a future edit can neither drop the hints nor let them reach the wire.
check("the notebook's LP hint of 5 survives at the call site",
      re.search(r'LAYER_PROMPTS\["LP"\][\s\S]{0,120}?max_tokens=5\)', source) is not None)
check("the notebook's scorer hint of 10 survives at the call site",
      "safe_ask_raw(prompt, max_tokens=10)" in source)
check("the notebook's L2 HIGH_QUALITY hint of 50 survives at the call site",
      'max_tokens=50 if operating_mode == "HIGH_QUALITY"' in source)
_lp_call = next((c for c in CALLS if "EXECUTING: LP" in c["prompt"]), None)
check("LP's hint of 5 reaches the wire floored, not raw",
      _lp_call is not None and _lp_call["budget"] >= namespace["MIN_OUTPUT_TOKENS"],
      f"budget={_lp_call['budget'] if _lp_call else 'no LP call recorded'}")

_LAYER_RE = re.compile(r"YOU ARE NOW EXECUTING: (\S+)")
layer_calls = [(c, _LAYER_RE.search(c["prompt"]).group(1)) for c in CALLS if _LAYER_RE.search(c["prompt"])]
executed = [name for _, name in layer_calls]
check("layers executed in PIPELINE_ORDER", executed == order, " ".join(executed))
l3_i, lp_i = executed.index("L3"), executed.index("LP")
check("L3 was called before LP", l3_i < lp_i, f"L3@{l3_i} LP@{lp_i}")
lp_prompt = next(c["prompt"] for c, name in layer_calls if name == "LP")
check("LP received the L3 REWRITE PLAN (not L2 text)",
      "REWRITE PLAN FROM L3" in lp_prompt and "ORIGINAL ANSWER (first 200 chars)" in lp_prompt)
check("LP no longer compares a 'Proposal' from L2", "Proposal:" not in lp_prompt)
check("all rewriter calls used the session key", {c["key"] for c in CALLS} == {"KEY_RW"})
check("score_before is a real score, not the silent 50 fallback", ui[0] == 45, str(ui[0]))
check("L9 produced 3 questions", len(ui[-2]) == 3)
check("run counter incremented to 1", ui[-1] == 1, str(ui[-1]))

# ── 14. The 3-free-run public gate ─────────────────────────
with namespace["gemini_session"]("KEY_RW", "gemini-3.6-flash"):
    blocked = namespace["run_public_rewriter"]("Another answer.", "", [], 3)
check("4th run is blocked by the paywall", "3 free runs" in blocked[2], blocked[2][:100])
check("paywall surfaces the Gumroad URL", namespace["GUMROAD_URL"] in blocked[2])

# ── 15. Tools write under DATA_DIR ─────────────────────────
note = namespace["execute_tool"]("save_note", "hello from a test")
check("save_note writes under the data dir", "data_test" in note and "/content/drive" not in note, note)
pdf = namespace["execute_tool"]("generate_pdf", "Report line one\nReport line two\nLine three")
check("generate_pdf writes under the data dir", "data_test" in pdf and "/content/drive" not in pdf, pdf)
check("generate_pdf renders >1 line (fpdf2 new_x fix)", "PDF saved" in pdf, pdf)
csv_out = namespace["execute_tool"]("write_csv", '[{"a": 1, "b": 2}]')
check("write_csv writes under the data dir", "data_test" in csv_out and "/content/drive" not in csv_out)
check("get_datetime tool works", "Date:" in namespace["execute_tool"]("get_datetime"))
check("query_database blocks non-SELECT",
      namespace["execute_tool"]("query_database", "DROP TABLE agents").startswith("❌"))

# ── 16. Dashboard + report ─────────────────────────────────
figs, err = namespace["create_plotly_dashboard"]()
check("dashboard renders figures", err is None and figs and len(figs) >= 2, f"err={err} figs={len(figs or [])}")
report = namespace["copy_all_rewriter_report"](45, 62, "ok", *["text"] * len(order))
check("Copy All report labels layers in pipeline order", report.index("\nL3") < report.index("\nLP"))

# ── 17. UI callback arity ──────────────────────────────────
agent_chunks = list(namespace["run_agent"](
    "Analyse competitor positioning", "KEY_UI", "", "gemini-3.6-flash", True, False))
check("run_agent is a generator of 2-tuples (log, key_update)",
      agent_chunks and all(isinstance(c, tuple) and len(c) == 2 for c in agent_chunks), f"{len(agent_chunks)} yields")
check("run_agent log accumulates progressively",
      len(agent_chunks) > 3 and len(agent_chunks[-1][0]) > len(agent_chunks[0][0]),
      f"first={len(agent_chunks[0][0])} last={len(agent_chunks[-1][0])}")
check("run_agent ends with a diagnostics summary", "Gemini call(s)" in agent_chunks[-1][0])

missing_agent = list(namespace["run_agent"]("goal", "", "", "gemini-3.6-flash", True, False))
check("run_agent refuses without a key (still a 2-tuple)",
      len(missing_agent) == 1 and len(missing_agent[0]) == 2 and "❌" in missing_agent[0][0])

ask_result = namespace["ask_five_lens"]("What is an agent?", False, "KEY_UI", "", "gemini-3.6-flash")
check("ask_five_lens returns 3 values", len(ask_result) == 3, str(len(ask_result)))
check_result = namespace["check_key_and_remember"]("KEY_UI", "", "gemini-3.6-flash")
check("check_key_and_remember returns 2 values", len(check_result) == 2)

rw_ui = namespace["run_public_rewriter_ui"]("An answer.", "", [], 0, "KEY_UI", "", "gemini-3.6-flash")
expected_rw = 3 + len(order) + 4
check(f"run_public_rewriter_ui returns {expected_rw} values", len(rw_ui) == expected_rw, str(len(rw_ui)))
check("rewriter UI status carries the diagnostics summary", "Gemini call(s)" in rw_ui[2], rw_ui[2][-90:])
rw_blocked = namespace["run_public_rewriter_ui"]("An answer.", "", [], 3, "KEY_UI", "", "gemini-3.6-flash")
check("blocked run returns the same arity", len(rw_blocked) == expected_rw)
rw_nokey = namespace["run_public_rewriter_ui"]("An answer.", "", [], 0, "", "", "gemini-3.6-flash")
check("no-key run returns the same arity with an error", len(rw_nokey) == expected_rw and "❌" in rw_nokey[2])

check("load_dashboard returns 4 values", len(namespace["load_dashboard"]()) == 4)
check("refresh_diagnostics returns 2 values", len(namespace["refresh_diagnostics"]()) == 2)
check("clear_diagnostics empties the log", namespace["clear_diagnostics"]()[1] == [])
check("request_custom_agent validates email",
      namespace["request_custom_agent"]("bad", "desc", "Simple", "").startswith("❌"))
check("request_custom_agent builds a mailto draft",
      "mailto:mohamtur1@gmail.com" in namespace["request_custom_agent"]("a@b.com", "desc", "Simple", ""))
hidden = namespace["update_keys_visibility"](True, True)
shown = namespace["update_keys_visibility"](False, True)
check("update_keys_visibility hides keys in Gemini-only mode", getattr(hidden, "visible", None) is False)
check("update_keys_visibility reveals keys when enabled", getattr(shown, "visible", None) is True)

# ── 18. No secrets in the generated source ─────────────────
app_text = open(APP, encoding="utf-8").read()
check("no hardcoded API key literal", not re.search(r"AIza[0-9A-Za-z_\-]{10,}", app_text))
check("no Supabase service-role key literal", "eyJ" not in app_text)

import gradio
print(f"\n(ran under gradio {gradio.__version__})")
print("=" * 74)
print(f"RESULT: {len(FAILURES)} failure(s)" if FAILURES else "RESULT: all checks passed")
for f in FAILURES:
    print("  ✗", f)
print("=" * 74)
sys.exit(1 if FAILURES else 0)
