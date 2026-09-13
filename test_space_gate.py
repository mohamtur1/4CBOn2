#!/usr/bin/env python3
"""Tests for the Space-side run gate (build_src/handwritten.py @@SECTION:gate@@).

The gate functions under test are not re-implemented here — they are loaded
with the build's own `load_sections()` and exec'd, so what runs is byte-for-byte
what gets spliced into space_gemini/app.py. A fake gate answers over real HTTP
on 127.0.0.1, so timeouts, non-JSON bodies and non-200 statuses are exercised
for real rather than stubbed.

The single most important property is checked structurally against the
*generated* file: in `run_agent`, the gate must be consulted before the first
`yield`. Gradio renders whatever a generator yields, so a gate that runs later
hands over the start of an unpaid run.

Usage: /tmp/gateenv/bin/python test_space_gate.py
"""
import http.server
import importlib.util
import json
import os
import sys
import threading

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ROOT)

CHECKS = 0
FAILURES = []


def check(label, condition, detail=""):
    global CHECKS
    CHECKS += 1
    print(f"[{'PASS' if condition else 'FAIL'}] {label}" + (f" — {detail}" if detail else ""))
    if not condition:
        FAILURES.append(label)


# ════════════════════════════════════════════════════════════
# Fake gate over real HTTP
# ════════════════════════════════════════════════════════════
RESPONSE = {"status": 200, "body": {"allowed": True}}
SEEN = []


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length).decode("utf-8") if length else ""
        try:
            SEEN.append(json.loads(raw))
        except ValueError:
            SEEN.append({"_raw": raw})
        status = RESPONSE["status"]
        body = RESPONSE["body"]
        if isinstance(body, str):
            payload = body.encode("utf-8")
            ctype = "text/plain"
        else:
            payload = json.dumps(body).encode("utf-8")
            ctype = "application/json"
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
BASE = f"http://127.0.0.1:{httpd.server_address[1]}"
threading.Thread(target=httpd.serve_forever, daemon=True).start()


# ════════════════════════════════════════════════════════════
# Load the section the build actually splices in
# ════════════════════════════════════════════════════════════
spec = importlib.util.spec_from_file_location(
    "builder", os.path.join(ROOT, "build_gemini_space.py"))
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)

sections = builder.load_sections()
check("the build defines a gate section", "gate" in sections,
      ", ".join(sorted(sections)))


class _Request:
    def __init__(self, params):
        self.query_params = params


class _GrStub:
    Request = _Request

    @staticmethod
    def update(**kw):
        return ("__gr_update__", kw)


class _RequestsStub:
    """Delegates to the real requests library, so timeouts and encodings are
    genuine; only the target is redirected to the local fake."""
    post = staticmethod(__import__("requests").post)


def load_gate(env):
    """Exec the shipped section with a chosen environment."""
    saved = {k: os.environ.get(k) for k in
             ("FOURCBON2_GATE_URL", "FOURCBON2_GATE_FAIL_OPEN", "FOURCBON2_GATE_TIMEOUT",
              "FOURCBON2_HOME_URL")}
    for k in saved:
        os.environ.pop(k, None)
    os.environ.update(env)
    ns = {"os": os, "requests": _RequestsStub, "gr": _GrStub, "print": print}
    exec(compile(sections["gate"], "gate_section", "exec"), ns)
    for k, v in saved.items():
        if v is None:
            os.environ.pop(k, None)
        else:
            os.environ[k] = v
    return ns


print("=" * 74)
print("The gate section, exec'd as the build splices it")
print("=" * 74)

gate = load_gate({"FOURCBON2_GATE_URL": BASE, "FOURCBON2_GATE_TIMEOUT": "5"})
check("a real pass is accepted", gate["gate_check"]("good.pass.token", "ask")[0] is True)
check("...and the feature name reaches the gate", SEEN and SEEN[-1]["feature"] == "ask",
      str(SEEN[-1] if SEEN else None))
check("...and so does the pass itself", SEEN[-1]["pass"] == "good.pass.token")

print("\n" + "=" * 74)
print("Fail closed — the default, and the whole point")
print("=" * 74)

gate_nc = load_gate({"FOURCBON2_GATE_TIMEOUT": "5"})
check("FOURCBON2_GATE_FAIL_OPEN defaults to false", gate_nc["GATE_FAIL_OPEN"] is False)
ok, msg = gate_nc["gate_check"]("good.pass.token", "agent")
check("with no FOURCBON2_GATE_URL a run is blocked, not allowed", ok is False)
check("...and the message points the visitor at the site", "app.4cbon.com" in msg, msg[:70])

ok, msg = gate["gate_check"]("", "agent")
check("an empty pass is blocked", ok is False and "Sign in" in msg, msg[:60])

gate_dead = load_gate({"FOURCBON2_GATE_URL": "http://127.0.0.1:9",
                       "FOURCBON2_GATE_TIMEOUT": "2"})
ok, msg = gate_dead["gate_check"]("good.pass.token", "ask")
check("an unreachable gate blocks the run", ok is False, msg[:60])
check("...and says nothing was charged", "Nothing was charged" in msg, msg[:80])

gate_open = load_gate({"FOURCBON2_GATE_URL": "http://127.0.0.1:9",
                       "FOURCBON2_GATE_TIMEOUT": "2",
                       "FOURCBON2_GATE_FAIL_OPEN": "true"})
check("FOURCBON2_GATE_FAIL_OPEN=true is the only way to allow through an outage",
      gate_open["gate_check"]("good.pass.token", "ask")[0] is True)

print("\n" + "=" * 74)
print("Only an explicit allow is an allow")
print("=" * 74)

for label, resp in [
    ("a 200 with allowed:false", {"status": 200, "body": {"allowed": False, "error": "denied"}}),
    ("a 200 with no allowed key at all", {"status": 200, "body": {"hello": "world"}}),
    ("a 200 whose body is not JSON", {"status": 200, "body": "<html>not json</html>"}),
    ("a 500", {"status": 500, "body": {"allowed": True}}),
    ("a 403", {"status": 403, "body": {"allowed": True}}),
]:
    RESPONSE["status"] = resp["status"]
    RESPONSE["body"] = resp["body"]
    ok, msg = gate["gate_check"]("good.pass.token", "rewriter")
    check(f"{label} denies the run", ok is False, msg[:60])

RESPONSE["status"] = 200
RESPONSE["body"] = {"allowed": True}
check("...and an explicit allowed:true still passes",
      gate["gate_check"]("good.pass.token", "rewriter")[0] is True)

print("\n" + "=" * 74)
print("Visitor-facing messages")
print("=" * 74)

RESPONSE["status"] = 402
RESPONSE["body"] = {"allowed": False, "error": "daily_limit_reached", "used": 3, "limit": 3,
                    "upgrade": "https://4175358678144.gumroad.com/l/tbphpi"}
ok, msg = gate["gate_check"]("good.pass.token", "ask")
check("402 blocks the run", ok is False)
check("...and names the shared daily limit", "shared across" in msg, msg[:80])
check("...and carries the upgrade link", "gumroad.com/l/tbphpi" in msg)
check("...and reports how many were used", "3 of 3" in msg, msg[:80])

RESPONSE["status"] = 401
RESPONSE["body"] = {"allowed": False, "error": "login_required"}
ok, msg = gate["gate_check"]("good.pass.token", "ask")
check("401 asks the visitor to sign in", ok is False and "Sign in" in msg, msg[:60])

RESPONSE["status"] = 409
RESPONSE["body"] = {"allowed": False, "error": "pass_already_used"}
ok, msg = gate["gate_check"]("good.pass.token", "ask")
check("409 explains the pass was already spent", ok is False and "already spent" in msg,
      msg[:60])

RESPONSE["status"] = 503
RESPONSE["body"] = {"allowed": False, "error": "entitlement_unavailable"}
ok, msg = gate["gate_check"]("good.pass.token", "ask")
check("503 is an outage message, not a limit message",
      ok is False and "unreachable" in msg, msg[:60])

RESPONSE["status"] = 200
RESPONSE["body"] = {"allowed": True}

print("\n" + "=" * 74)
print("Pass capture from the iframe URL")
print("=" * 74)
check("gate_capture_pass reads ?pass=",
      gate["gate_capture_pass"](_Request({"pass": "abc.def.ghi"})) == "abc.def.ghi")
check("...and returns empty when there is none",
      gate["gate_capture_pass"](_Request({})) == "")


class _BrokenRequest:
    @property
    def query_params(self):
        raise RuntimeError("no request context")


check("...and does not raise when there is no request context",
      gate["gate_capture_pass"](_BrokenRequest()) == "")

print("\n" + "=" * 74)
print("Structural checks on the GENERATED space_gemini/app.py")
print("=" * 74)
app_src = open(os.path.join(ROOT, "space_gemini", "app.py"), encoding="utf-8").read()
app_lines = app_src.splitlines()

# Find the UI run_agent — the generator Gradio actually calls — not the dead
# module-level one that is shadowed by it.
agent_starts = [i for i, l in enumerate(app_lines)
                if l.startswith("def run_agent(goal, api_key, stored_key")]
check("the UI run_agent exists in the generated file", len(agent_starts) == 1,
      f"{len(agent_starts)} matches")

if agent_starts:
    start = agent_starts[0]
    body = []
    for line in app_lines[start + 1:]:
        if line and not line[0].isspace() and not line.startswith(")"):
            break
        body.append(line)
    # Parse it properly. Line scanning is not enough here: the docstring's
    # second line literally begins with the word "yield.", so any textual
    # search finds prose before it finds code.
    import ast

    tree = ast.parse(app_src)

    def walk_own_body(node):
        """Descend, but do not enter nested function definitions."""
        for child in ast.iter_child_nodes(node):
            if isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                continue
            yield child
            yield from walk_own_body(child)

    ui_agent = next(
        (n for n in ast.walk(tree)
         if isinstance(n, ast.FunctionDef) and n.name == "run_agent"
         and {a.arg for a in n.args.args} >= {"gate_pass", "stored_key"}), None)
    check("the gated run_agent parses as a function definition", ui_agent is not None)

    if ui_agent is not None:
        own = list(walk_own_body(ui_agent))
        gate_calls = [n for n in own
                      if isinstance(n, ast.Call) and getattr(n.func, "id", "") == "gate_check"]
        yields = [n for n in own if isinstance(n, (ast.Yield, ast.YieldFrom))]
        check("run_agent consults the gate", len(gate_calls) == 1,
              f"{len(gate_calls)} gate_check call(s)")
        check("run_agent yields somewhere (it is a generator)", bool(yields),
              f"{len(yields)} yield(s)")
        if gate_calls and yields:
            g, y = gate_calls[0].lineno, min(n.lineno for n in yields)
            check("...and the gate runs BEFORE the first yield", g < y,
                  f"gate_check on line {g}, first yield on line {y}")
        # The denial path must return, not fall through into the pipeline.
        guarded = any(isinstance(n, ast.If) and
                      any(isinstance(c, ast.Return) for c in ast.walk(n))
                      for n in own if isinstance(n, ast.If) and n.lineno < (
                          min(x.lineno for x in yields) if yields else 10 ** 9))
        check("...and a denial returns instead of falling through", guarded)

    # gate_check must precede any real work in the other two callbacks too.
    for name in ("ask_five_lens", "run_public_rewriter_ui"):
        fn = next((n for n in ast.walk(tree)
                   if isinstance(n, ast.FunctionDef) and n.name == name), None)
        if fn is None:
            check(f"{name} exists", False)
            continue
        own = list(walk_own_body(fn))
        gate_calls = [n for n in own
                      if isinstance(n, ast.Call) and getattr(n.func, "id", "") == "gate_check"]
        calls = [n for n in own if isinstance(n, ast.Call)
                 and getattr(n.func, "id", "") in ("_resolve_session", "run_public_rewriter",
                                                   "handle_ask_question")]
        check(f"{name} gates before doing any work",
              bool(gate_calls) and (not calls or gate_calls[0].lineno < min(c.lineno for c in calls)),
              f"gate {gate_calls[0].lineno if gate_calls else None}, "
              f"first work {min((c.lineno for c in calls), default=None)}")
        check(f"{name} takes the pass as a parameter",
              "gate_pass" in {a.arg for a in fn.args.args},
              str([a.arg for a in fn.args.args]))

# The dead run_agent must stay untouched; gating a shadowed function would be
# theatre, and gating it wrongly could change agent behaviour.
dead = [i for i, l in enumerate(app_lines) if l.startswith("def run_agent(goal, system_override")]
check("the shadowed run_agent is still present and ungated",
      len(dead) == 1 and "gate_check" not in "\n".join(app_lines[dead[0]:dead[0] + 12]),
      f"{len(dead)} matches")

# Each gated callback must receive the pass textbox, in the right position.
check("the hidden pass textbox exists with the id the bridge JS writes to",
      'elem_id="cbon-pass-box"' in app_src)
check("the Blocks mounts the postMessage bridge", "js=GATE_BRIDGE_JS" in app_src)
check("demo.load seeds the pass from ?pass=", "fn=gate_capture_pass" in app_src)

inputs_blk = app_src[app_src.find("fn=run_agent"):app_src.find("fn=run_agent") + 900]
check("run_agent's inputs pass gate_pass_box before the tool keys",
      inputs_blk.find("gate_pass_box") > inputs_blk.find("chk_enable_additional")
      and inputs_blk.find("gate_pass_box") < inputs_blk.find("t_cal"),
      "ordering matters: it maps positionally onto the signature before *api_keys")

sig = app_lines[agent_starts[0]] + " " + app_lines[agent_starts[0] + 1] if agent_starts else ""
check("run_agent's signature takes gate_pass before *api_keys",
      "gate_pass, *api_keys" in sig, sig.strip()[:110])

check("the per-browser free-run counter no longer decides access",
      "Public gate: three free runs per browser session" not in app_src)

print("\n" + "=" * 74)
print("The build still guards its own output")
print("=" * 74)
compile(app_src, "app.py", "exec")
check("the generated app.py compiles", True)
check("the gate section is defined before the UI that calls it",
      app_src.find("# @@SECTION") >= 0 or app_src.find("def gate_check") < app_src.find("def run_agent(goal, api_key"))

print("\n" + "=" * 74)
print(f"ran {CHECKS} checks")
print(f"RESULT: {len(FAILURES)} failure(s)" if FAILURES else "RESULT: all checks passed")
for f in FAILURES:
    print("  ✗", f)
print("=" * 74)

httpd.shutdown()
sys.exit(1 if FAILURES else 0)
