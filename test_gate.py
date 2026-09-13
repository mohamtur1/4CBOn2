#!/usr/bin/env python3
"""End-to-end test of the gate: /api/pass and /api/consume.

What is real here:
  * gate_core.py and vercel/api/gate.py — the actual shipping code, imported
    and called, not reimplemented
  * PostgreSQL 16 booted locally, with the real migration applied, so
    run_usage / gate_passes / admin_emails / try_consume_run are the real
    tables and the real function
  * FastAPI + TestClient, so the HTTP layer, status codes and JSON bodies are
    the real ones

What is a test double:
  * The PostgREST transport. `supabase.create_client` is replaced by a client
    whose query builder emits SQL and runs it through psql against that same
    real PostgreSQL. So only the HTTP-to-PostgREST hop is faked; the SQL, the
    tables, the atomic RPC and the replay guard all really execute.
  * To stop that double from quietly drifting from the real library's API,
    the first section asserts the method names and signatures against the
    installed `supabase` / `postgrest` packages.

Usage: python3 test_gate.py     (needs: pip install fastapi httpx supabase)
"""
import base64
import concurrent.futures
import hashlib
import hmac as hmac_mod
import importlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
FAILURES = []
CHECKS = 0


def check(label, condition, detail=""):
    global CHECKS
    CHECKS += 1
    print(f"[{'PASS' if condition else 'FAIL'}] {label}" + (f" — {detail}" if detail else ""))
    if not condition:
        FAILURES.append(label)


try:
    import pgserver
    from fastapi.testclient import TestClient
    import supabase as supabase_pkg
except ImportError as exc:
    print(f"SKIP — missing dependency: {exc}")
    print("       Nothing ran. Install: pip install pgserver fastapi httpx supabase")
    sys.exit(0)

# ════════════════════════════════════════════════════════════
# 0. The double must match the real library's API
# ════════════════════════════════════════════════════════════
print("=" * 74)
print("API SURFACE — the test double must not drift from supabase-py")
print("=" * 74)

import inspect  # noqa: E402

check("supabase exposes create_client", hasattr(supabase_pkg, "create_client"))
check("supabase.Client has .table()", hasattr(supabase_pkg.Client, "table"))
check("supabase.Client has .rpc()", hasattr(supabase_pkg.Client, "rpc"))

_probe = __import__("postgrest").SyncPostgrestClient("http://127.0.0.1:1", headers={"apikey": "x"})
_tbl = _probe.from_("run_usage")
check("table builder supports select/insert/update",
      all(hasattr(_tbl, m) for m in ("select", "insert", "update")))
_sel = _tbl.select("run_count").eq("subject", "x")
check("select().eq() supports .is_() and .execute()",
      hasattr(_sel, "is_") and hasattr(_sel, "execute"))
_upd = _tbl.update({"run_count": 1}).eq("subject", "x")
check("update().eq() supports .is_()", hasattr(_upd, "is_"))
check("is_() signature is (column, value)",
      list(inspect.signature(_sel.is_).parameters) == ["column", "value"])
from postgrest.base_request_builder import APIResponse  # noqa: E402
check(".execute() result exposes .data", "data" in APIResponse.model_fields,
      str(list(APIResponse.model_fields)))
check("...and .data is typed as a LIST, so gate.py must unwrap it",
      "List" in str(APIResponse.model_fields["data"]), str(APIResponse.model_fields["data"]))

# ════════════════════════════════════════════════════════════
# 1. Real PostgreSQL with the real migration
# ════════════════════════════════════════════════════════════
PGDATA = tempfile.mkdtemp(prefix="4cbon2_gate_")
server = pgserver.get_server(PGDATA, cleanup_mode=None)
PGBIN = os.path.join(os.path.dirname(pgserver.__file__), "pginstall", "bin")
PSQL = os.path.join(PGBIN, "psql")
ENV = dict(os.environ, PGHOST=PGDATA, PGUSER="postgres", PGDATABASE="postgres")
DB = "db_gate"


def pg(sql, db=DB):
    r = subprocess.run([PSQL, "-At", "-d", db, "-c", sql], env=ENV,
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError((r.stderr.strip().splitlines() or ["psql failed"])[0])
    return r.stdout.strip()


pg("drop database if exists db_gate;", db="postgres")
for role in ("anon", "authenticated", "service_role"):
    pg(f"do $$ begin if not exists (select 1 from pg_roles where rolname='{role}') "
       f"then create role {role} nologin; end if; end $$;", db="postgres")
pg("create database db_gate;", db="postgres")
pg("""create schema if not exists auth;
      create or replace function auth.role() returns text language sql stable
      as $$ select 'service_role' $$;
      grant usage on schema auth to anon, authenticated, service_role;
      grant usage on schema public to anon, authenticated, service_role;
      alter default privileges in schema public
        grant all on tables to anon, authenticated, service_role;""")
r = subprocess.run([PSQL, "-v", "ON_ERROR_STOP=1", "-q", "-d", DB,
                    "-f", os.path.join(ROOT, "supabase", "4cbon2.sql")],
                   env=ENV, capture_output=True, text=True)
check("migration applies to the gate test database", r.returncode == 0,
      (r.stderr.strip().splitlines() or [""])[-1][:120])
print(f"\nPostgreSQL: {pg('select version();', db='postgres').split(',')[0]}")


# ════════════════════════════════════════════════════════════
# 2. The PostgREST double
# ════════════════════════════════════════════════════════════
def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


class Result:
    def __init__(self, data):
        self.data = data


class Query:
    def __init__(self, table, kind, cols="*", payload=None):
        self.table, self.kind, self.cols, self.payload = table, kind, cols, payload
        self.where = []

    def eq(self, column, value):
        self.where.append(f'"{column}" = {lit(value)}')
        return self

    def is_(self, column, value):
        token = str(value).lower()
        if token == "null":
            self.where.append(f'"{column}" is null')
        elif token in ("true", "false"):
            self.where.append(f'"{column}" is {token}')
        else:
            raise ValueError(f"double only supports is_ null/true/false, got {value!r}")
        return self

    def _where_sql(self):
        return (" where " + " and ".join(self.where)) if self.where else ""

    def execute(self):
        if self.kind == "select":
            sql = (f"select coalesce(json_agg(row_to_json(t)), '[]'::json) "
                   f"from (select {self.cols} from \"{self.table}\"{self._where_sql()}) t")
        elif self.kind == "insert":
            cols = ", ".join(f'"{c}"' for c in self.payload)
            vals = ", ".join(lit(v) for v in self.payload.values())
            sql = (f'with ins as (insert into "{self.table}" ({cols}) values ({vals}) '
                   f"returning *) select coalesce(json_agg(row_to_json(ins)), '[]'::json) from ins")
        elif self.kind == "update":
            assigns = ", ".join(f'"{c}" = {lit(v)}' for c, v in self.payload.items())
            sql = (f'with upd as (update "{self.table}" set {assigns}{self._where_sql()} '
                   f"returning *) select coalesce(json_agg(row_to_json(upd)), '[]'::json) from upd")
        else:
            raise ValueError(self.kind)
        return Result(json.loads(pg(sql)))


class Table:
    def __init__(self, name):
        self.name = name

    def select(self, cols="*"):
        return Query(self.name, "select", cols=cols)

    def insert(self, payload):
        return Query(self.name, "insert", payload=payload)

    def update(self, payload):
        return Query(self.name, "update", payload=payload)


# Some PostgREST deployments wrap a function's composite return in a list,
# which is what APIResponse.data is actually typed as. Flip this to prove the
# endpoint unwraps both shapes.
RPC_WRAP_LIST = False


class RPC:
    def __init__(self, fn, params):
        self.fn, self.params = fn, params

    def execute(self):
        args = ", ".join(f"{k} => {lit(v)}" for k, v in self.params.items())
        data = json.loads(pg(f"select to_json(f) from {self.fn}({args}) f"))
        return Result([data] if RPC_WRAP_LIST else data)


class FakeClient:
    def table(self, name):
        return Table(name)

    def from_(self, name):
        return Table(name)

    def rpc(self, fn, params=None):
        return RPC(fn, params or {})


USE_FAKE = True
_real_create_client = supabase_pkg.create_client


def create_client(url, key):
    return FakeClient() if USE_FAKE else _real_create_client(url, key)


supabase_pkg.create_client = create_client

# ════════════════════════════════════════════════════════════
# 3. Import the real gate with secrets configured
# ════════════════════════════════════════════════════════════
GATE_SECRET = "test-gate-secret-0123456789abcdef0123456789abcdef"
JWT_SECRET = "test-jwt-secret-0123456789abcdef0123456789abcdef"
os.environ.update({
    "SUPABASE_URL": "https://example.supabase.co",
    "SUPABASE_SERVICE_ROLE_KEY": "service-role-key",
    "GATE_SHARED_SECRET": GATE_SECRET,
    "SUPABASE_JWT_SECRET": JWT_SECRET,
    "ADMIN_EMAIL": "mohamtur1@gmail.com",
    "SPACE_URL": "https://mohamtur1-4cbon2-gemini.hf.space",
    "GUMROAD_URL": "https://4175358678144.gumroad.com/l/tbphpi",
})
sys.path.insert(0, os.path.join(ROOT, "vercel"))
sys.path.insert(0, os.path.join(ROOT, "vercel", "api"))
gate = importlib.import_module("gate")
gate_core = importlib.import_module("gate_core")
client = TestClient(gate.app)


def jwt_for(email, secret=JWT_SECRET, exp_in=3600, alg="HS256", sign=True, email_key="email"):
    def b64(raw):
        return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()
    header = {"alg": alg, "typ": "JWT"}
    claims = {"sub": "user-id", "exp": int(time.time()) + exp_in}
    if email is not None:
        claims[email_key] = email
    h, c = b64(json.dumps(header).encode()), b64(json.dumps(claims).encode())
    signing_input = f"{h}.{c}".encode()
    if alg == "none" or not sign:
        return f"{h}.{c}."
    sig = b64(hmac_mod.new(secret.encode(), signing_input, hashlib.sha256).digest())
    return f"{h}.{c}.{sig}"


def reset(email):
    pg(f"delete from run_usage where subject='{email}';")
    pg(f"delete from gate_passes where subject='{email}';")


def mint(email):
    r = client.post("/api/pass", headers={"Authorization": f"Bearer {jwt_for(email)}"})
    return r


print("\n" + "=" * 74)
print("gate_core — signatures, in isolation")
print("=" * 74)

token, payload = gate_core.mint_pass("User@Example.com", 3, secret=GATE_SECRET)
back = gate_core.verify_pass(token, secret=GATE_SECRET)
check("mint -> verify round-trips", back["jti"] == payload["jti"], back["jti"][:12])
check("subject is lowercased on mint", back["sub"] == "user@example.com", back["sub"])
check("ttl is 15 minutes", payload["exp"] - payload["iat"] == 900,
      str(payload["exp"] - payload["iat"]))

body, sig = token.split(".")
forged = base64.urlsafe_b64encode(
    json.dumps({"sub": "user@example.com", "runs": 999, "exp": payload["exp"],
                "jti": payload["jti"]}, separators=(",", ":"), sort_keys=True).encode()
).rstrip(b"=").decode()
try:
    gate_core.verify_pass(f"{forged}.{sig}", secret=GATE_SECRET)
    check("editing the payload breaks the signature", False, "accepted a tampered pass")
except gate_core.PassError as e:
    check("editing the payload breaks the signature", True, str(e)[:50])

try:
    gate_core.verify_pass(token, secret="a-completely-different-secret-value-0000")
    check("a pass signed with another secret is rejected", False)
except gate_core.PassError:
    check("a pass signed with another secret is rejected", True)

expired, _ = gate_core.mint_pass("user@example.com", 3, secret=GATE_SECRET, ttl=-5)
try:
    gate_core.verify_pass(expired, secret=GATE_SECRET)
    check("an expired pass is rejected", False)
except gate_core.PassError as e:
    check("an expired pass is rejected", True, str(e)[:40])

for bad in ("", "nodothere", "a.b.c", "!!!.%%%", token + "x"):
    try:
        gate_core.verify_pass(bad, secret=GATE_SECRET)
        check(f"malformed pass rejected: {bad!r}", False)
    except gate_core.PassError:
        check(f"malformed pass rejected: {bad!r}", True)

saved = os.environ.pop("GATE_SHARED_SECRET")
try:
    gate_core.mint_pass("a@b.c", 3)
    check("missing GATE_SHARED_SECRET refuses to sign", False)
except gate_core.GateConfigError as e:
    check("missing GATE_SHARED_SECRET refuses to sign", True, str(e)[:44])
os.environ["GATE_SHARED_SECRET"] = "tooshort"
try:
    gate_core.mint_pass("a@b.c", 3)
    check("a weak GATE_SHARED_SECRET refuses to sign", False)
except gate_core.GateConfigError as e:
    check("a weak GATE_SHARED_SECRET refuses to sign", True, str(e)[:44])
os.environ["GATE_SHARED_SECRET"] = saved

print("\nJWT verification")
claims = gate_core.verify_supabase_jwt(jwt_for("Owner@Example.com"), secret=JWT_SECRET)
check("a valid HS256 session token is accepted", claims["email"] == "Owner@Example.com")
try:
    gate_core.verify_supabase_jwt(jwt_for("a@b.c", alg="none"), secret=JWT_SECRET)
    check('alg=none is REJECTED (no signature bypass)', False)
except gate_core.GateError as e:
    check('alg=none is REJECTED (no signature bypass)', True, str(e)[:52])
try:
    gate_core.verify_supabase_jwt(jwt_for("a@b.c", secret="wrong-secret-0123456789abcdef0123"),
                                  secret=JWT_SECRET)
    check("a token signed with the wrong secret is rejected", False)
except gate_core.GateError:
    check("a token signed with the wrong secret is rejected", True)
try:
    gate_core.verify_supabase_jwt(jwt_for("a@b.c", exp_in=-10), secret=JWT_SECRET)
    check("an expired session token is rejected", False)
except gate_core.GateError:
    check("an expired session token is rejected", True)
try:
    gate_core.verify_supabase_jwt(jwt_for(None), secret=JWT_SECRET)
    check("a token with no email is rejected", False)
except gate_core.GateError:
    check("a token with no email is rejected", True)

# ════════════════════════════════════════════════════════════
print("\n" + "=" * 74)
print("/api/gate/health")
print("=" * 74)
r = client.get("/api/gate/health")
check("health returns 200", r.status_code == 200, str(r.status_code))
h = r.json()
check("health reports free_runs_per_day = 3", h.get("free_runs_per_day") == 3, str(h))
check("health reports all secrets configured",
      all(h["configured"].values()), str(h.get("configured")))

print("\n" + "=" * 74)
print("/api/pass — identity required")
print("=" * 74)
r = client.post("/api/pass")
check("no Authorization header -> 401 login_required",
      r.status_code == 401 and r.json()["error"] == "login_required", f"{r.status_code} {r.text[:70]}")
r = client.post("/api/pass", headers={"Authorization": "Bearer garbage.token.here"})
check("a bad session token -> 401 invalid_session",
      r.status_code == 401 and r.json()["error"] == "invalid_session", f"{r.status_code} {r.text[:60]}")
r = client.post("/api/pass", headers={"Authorization": f"Bearer {jwt_for('a@b.c', alg='none')}"})
check("an alg=none token -> 401 (not a pass)", r.status_code == 401, f"{r.status_code}")

print("\n" + "=" * 74)
print("Happy path against real PostgreSQL")
print("=" * 74)
USER = "free@example.com"
reset(USER)
r = mint(USER)
check("first /api/pass is allowed", r.status_code == 200 and r.json()["allowed"], r.text[:90])
body = r.json()
check("3 runs remaining on a fresh account", body["remaining"] == 3, str(body.get("remaining")))
check("unlimited is False for a free account", body["unlimited"] is False)
check("the Space URL carries the pass", body.get("url", "").startswith(
    "https://mohamtur1-4cbon2-gemini.hf.space/?pass="), body.get("url", "")[:60])
passes = pg(f"select count(*) from gate_passes where subject='{USER}';")
check("the jti was recorded in gate_passes", passes == "1", passes)

p1 = body["pass"]
r = client.post("/api/consume", json={"pass": p1, "feature": "ask", "run_id": "run-1"})
check("consume of a valid pass is allowed", r.status_code == 200 and r.json()["allowed"],
      f"{r.status_code} {r.text[:80]}")
check("remaining drops to 2", r.json()["remaining"] == 2, str(r.json()))
count = pg(f"select run_count from run_usage where subject='{USER}';")
check("run_usage really incremented in PostgreSQL", count == "1", count)

r = client.post("/api/consume", json={"pass": p1, "feature": "ask", "run_id": "run-2"})
check("replaying the same pass -> 409 pass_already_used",
      r.status_code == 409 and r.json()["error"] == "pass_already_used", f"{r.status_code} {r.text[:70]}")
count = pg(f"select run_count from run_usage where subject='{USER}';")
check("the replay did not move the counter", count == "1", count)

forged_body, forged_sig = p1.split(".")
r = client.post("/api/consume",
                json={"pass": f"{forged_body}.{forged_sig[:-2]}xx", "feature": "ask"})
check("a tampered pass -> 403 invalid_pass",
      r.status_code == 403 and r.json()["error"] == "invalid_pass", f"{r.status_code} {r.text[:70]}")

r = client.post("/api/consume", json={"pass": mint(USER).json()["pass"], "feature": "bogus"})
check("an unknown feature -> 400 unknown_feature",
      r.status_code == 400 and r.json()["error"] == "unknown_feature", f"{r.status_code} {r.text[:70]}")

print("\n" + "=" * 74)
print("The shared pool — one counter across all three features")
print("=" * 74)
reset(USER)
seen = []
for feature in ("ask", "rewriter", "agent"):
    token = mint(USER).json()["pass"]
    resp = client.post("/api/consume", json={"pass": token, "feature": feature,
                                             "run_id": f"run-{feature}"})
    seen.append((feature, resp.status_code, resp.json().get("allowed")))
check("1 ask + 1 rewriter + 1 agent are all allowed",
      all(s[2] for s in seen), str(seen))
count = pg(f"select run_count from run_usage where subject='{USER}';")
check("the three features summed into ONE counter = 3", count == "3", count)

fourth = mint(USER)
check("the 4th /api/pass is refused with 402 daily_limit_reached",
      fourth.status_code == 402 and fourth.json()["error"] == "daily_limit_reached",
      f"{fourth.status_code} {fourth.text[:70]}")
check("the 402 carries the Gumroad upgrade link",
      "gumroad.com" in fourth.json().get("upgrade", ""), fourth.json().get("upgrade", ""))

# Even if a client somehow holds a 4th valid pass, consume must refuse it.
reset(USER)
tokens = [mint(USER).json()["pass"] for _ in range(5)]
verdicts = [client.post("/api/consume", json={"pass": t, "feature": "ask"}).status_code
            for t in tokens]
check("5 pre-minted passes -> exactly 3 granted, then 402",
      verdicts[:3] == [200, 200, 200] and verdicts[3:] == [402, 402], str(verdicts))
count = pg(f"select run_count from run_usage where subject='{USER}';")
check("...and the counter stayed at 3, not 5", count == "3", count)

print("\n" + "=" * 74)
print("Concurrent consumes from one account")
print("=" * 74)
reset(USER)
tokens = [mint(USER).json()["pass"] for _ in range(6)]
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    codes = list(pool.map(
        lambda t: client.post("/api/consume", json={"pass": t, "feature": "agent"}).status_code,
        tokens))
granted = codes.count(200)
count = pg(f"select run_count from run_usage where subject='{USER}';")
check("6 concurrent consumes -> exactly 3 granted", granted == 3, f"codes={sorted(codes)}")
check("...counter is exactly 3", count == "3", count)

print("\n" + "=" * 74)
print("A list-wrapped RPC response must still grant the run")
print("=" * 74)
reset(USER)
RPC_WRAP_LIST = True
try:
    token = mint(USER).json()["pass"]
    r = client.post("/api/consume", json={"pass": token, "feature": "ask"})
    check("consume succeeds when .data is a one-element list",
          r.status_code == 200 and r.json()["allowed"], f"{r.status_code} {r.text[:80]}")
    check("...and remaining is computed from the unwrapped count",
          r.json().get("remaining") == 2, str(r.json()))
finally:
    RPC_WRAP_LIST = False

print("\n" + "=" * 74)
print("Unlimited identities")
print("=" * 74)
ADMIN = "mohamtur1@gmail.com"
reset(ADMIN)
r = mint(ADMIN)
check("admin /api/pass is allowed", r.status_code == 200 and r.json()["allowed"], r.text[:80])
check("admin is unlimited", r.json()["unlimited"] is True, str(r.json().get("unlimited")))
check("admin remaining is null", r.json()["remaining"] is None)
admin_pass = r.json()["pass"]
r = client.post("/api/consume", json={"pass": admin_pass, "feature": "rewriter"})
check("admin consume is allowed", r.status_code == 200 and r.json()["allowed"], r.text[:80])
count = pg(f"select count(*) from run_usage where subject='{ADMIN}';")
check("admin consume does NOT create a run_usage row", count == "0", count)
r = client.post("/api/consume", json={"pass": admin_pass, "feature": "rewriter"})
check("an admin pass is still single-use (replay -> 409)", r.status_code == 409,
      f"{r.status_code} {r.text[:60]}")

SUB = "paid@example.com"
reset(SUB)
pg(f"delete from subscriptions where email='{SUB}';")
pg(f"insert into subscriptions (email, status) values ('{SUB}', 'active');")
r = mint(SUB)
check("an active subscriber is unlimited",
      r.status_code == 200 and r.json()["unlimited"] is True, r.text[:80])
pg(f"update subscriptions set status='cancelled' where email='{SUB}';")
r = mint(SUB)
check("a cancelled subscriber drops back to the free tier",
      r.status_code == 200 and r.json()["unlimited"] is False and r.json()["remaining"] == 3,
      r.text[:90])
pg(f"delete from subscriptions where email='{SUB}';")

print("\n" + "=" * 74)
print("Fail closed")
print("=" * 74)
saved_secret = os.environ.pop("GATE_SHARED_SECRET")
r = mint("free@example.com")
check("missing GATE_SHARED_SECRET -> 503, not a pass",
      r.status_code == 503 and not r.json().get("allowed"), f"{r.status_code} {r.text[:70]}")
os.environ["GATE_SHARED_SECRET"] = saved_secret

# A key that is not even JWT-shaped. supabase-py rejects it inside
# create_client, which is exactly the path that used to escape as a 500.
USE_FAKE = False
saved_url = os.environ["SUPABASE_URL"]
saved_key = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
os.environ["SUPABASE_URL"] = "http://127.0.0.1:9"
os.environ["SUPABASE_SERVICE_ROLE_KEY"] = "not-a-jwt"
try:
    r = mint("free@example.com")
    check("a malformed service key -> 503, not an unhandled 500",
          r.status_code == 503 and not r.json().get("allowed"), f"{r.status_code} {r.text[:70]}")
except Exception as exc:
    check("a malformed service key -> 503, not an unhandled 500", False,
          f"escaped as {type(exc).__name__}")

# A well-formed key but a database that is not listening.
FAKE_JWT_KEY = ("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                "eyJyb2xlIjoic2VydmljZV9yb2xlIn0."
                "0000000000000000000000000000000000000000000")
os.environ["SUPABASE_SERVICE_ROLE_KEY"] = FAKE_JWT_KEY
try:
    r = mint("free@example.com")
    check("unreachable Supabase -> 503 gate_unavailable (fail closed)",
          r.status_code == 503 and not r.json().get("allowed"), f"{r.status_code} {r.text[:70]}")
except Exception as exc:
    check("unreachable Supabase -> 503 gate_unavailable (fail closed)", False,
          f"escaped as {type(exc).__name__}")
finally:
    os.environ["SUPABASE_URL"] = saved_url
    os.environ["SUPABASE_SERVICE_ROLE_KEY"] = saved_key
    USE_FAKE = True

# /api/consume must fail closed too: mint while healthy, then cut the database.
reset(USER)
healthy_pass = mint(USER).json()["pass"]
USE_FAKE = False
os.environ["SUPABASE_URL"] = "http://127.0.0.1:9"
os.environ["SUPABASE_SERVICE_ROLE_KEY"] = FAKE_JWT_KEY
try:
    r = client.post("/api/consume", json={"pass": healthy_pass, "feature": "ask"})
    check("consume with an unreachable database -> 503, not allowed",
          r.status_code == 503 and not r.json().get("allowed"), f"{r.status_code} {r.text[:70]}")
except Exception as exc:
    check("consume with an unreachable database -> 503, not allowed", False,
          f"escaped as {type(exc).__name__}")
finally:
    os.environ["SUPABASE_URL"] = saved_url
    os.environ["SUPABASE_SERVICE_ROLE_KEY"] = saved_key
    USE_FAKE = True
check("...and the pass was NOT spent, so the run can be retried",
      pg(f"select consumed_at is null from gate_passes where jti='"
         f"{healthy_pass.split('.')[0] and json.loads(base64.urlsafe_b64decode(healthy_pass.split('.')[0] + '==').decode())['jti']}';") == "t")

r = client.post("/api/consume", json={"pass": "not.a.real.pass", "feature": "ask"})
check("a garbage pass -> 403, never allowed", r.status_code == 403 and not r.json()["allowed"])

print("\n" + "=" * 74)
print("vercel/app.py — the repointed increment_run_count (the live bug)")
print("=" * 74)

legacy = importlib.import_module("app")   # vercel/app.py, needs google.generativeai
check("vercel/app.py imports", hasattr(legacy, "increment_run_count"))

IP = "203.0.113.7"
TODAY = __import__("datetime").datetime.utcnow().strftime("%Y-%m-%d")
pg(f"delete from run_limits where ip='{IP}';")

seq = [legacy.increment_run_count(IP) for _ in range(4)]
check("increment_run_count now returns 1,2,3,4 (was writing 1 every time)",
      seq == [1, 2, 3, 4], str(seq))
stored = pg(f"select run_count from run_limits where ip='{IP}' and run_date='{TODAY}';")
check("run_limits really holds 4, not 1", stored == "4", stored)

check("check_run_limit denies once the count reaches 3",
      legacy.check_run_limit(IP)["allowed"] is False, str(legacy.check_run_limit(IP)))
pg(f"update run_limits set run_count=1 where ip='{IP}';")
lim = legacy.check_run_limit(IP)
check("check_run_limit allows at count 1 and reports 2 remaining",
      lim["allowed"] is True and lim["remaining"] == 2, str(lim))
check("...and reports itself as enforced", lim.get("enforced") is True)

# Fail closed: configured, but the database is gone.
USE_FAKE = False
os.environ["SUPABASE_URL"] = "http://127.0.0.1:9"
os.environ["SUPABASE_SERVICE_ROLE_KEY"] = FAKE_JWT_KEY
importlib.reload(legacy)
try:
    lim = legacy.check_run_limit(IP)
    check("check_run_limit FAILS CLOSED when the database is unreachable",
          lim["allowed"] is False and lim.get("error") == "meter_unavailable", str(lim))
    check("increment_run_count returns None rather than raising",
          legacy.increment_run_count(IP) is None)
finally:
    os.environ["SUPABASE_URL"] = saved_url
    os.environ["SUPABASE_SERVICE_ROLE_KEY"] = saved_key
    USE_FAKE = True
    importlib.reload(legacy)

# The one deliberate fail-open: no metering configured at all.
os.environ["SUPABASE_URL"] = ""
importlib.reload(legacy)
try:
    lim = legacy.check_run_limit(IP)
    check("with no Supabase configured it allows but flags enforced=False",
          lim["allowed"] is True and lim.get("enforced") is False, str(lim))
finally:
    os.environ["SUPABASE_URL"] = saved_url
    importlib.reload(legacy)

print("\n" + "=" * 74)
print("Open tabs need no login")
print("=" * 74)
r = client.get("/api/gate/health")
check("health is reachable with no credentials at all", r.status_code == 200)

print("\n" + "=" * 74)
print(f"ran {CHECKS} checks")
print(f"RESULT: {len(FAILURES)} failure(s)" if FAILURES else "RESULT: all checks passed")
for f in FAILURES:
    print("  ✗", f)
print("=" * 74)

server.cleanup()
shutil.rmtree(PGDATA, ignore_errors=True)
sys.exit(1 if FAILURES else 0)
