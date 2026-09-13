#!/usr/bin/env python3
"""End-to-end test of the Supabase Auth wiring on Vercel.

What is real here:
  * auth_core.py, api/auth.py and api/gate.py — the shipping code, imported and
    called through FastAPI's TestClient
  * HTTP. A local server on 127.0.0.1 implements the Supabase Auth endpoints
    the code calls, so requests really go over a socket
  * ES256 tokens and a JWKS, matching the project's actual signing scheme
    (ECC P-256, no shared secret). gate_core verifies them for real.
  * PKCE, including S256 challenge verification on the server side

What is a stub:
  * The Supabase *database*. Metering against real PostgreSQL is covered by
    test_gate.py (80 checks); here an in-memory stand-in answers the three
    entitlement queries so the auth flow can be tested on its own.

Usage: python3 test_auth.py   (needs: pip install fastapi httpx cryptography)
"""
import base64
import hashlib
import http.server
import json
import os
import sys
import threading
import re
import time
import urllib.error
import urllib.parse
import urllib.request

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
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec, utils
    from fastapi.testclient import TestClient
except ImportError as exc:
    print(f"SKIP — missing dependency: {exc}")
    print("       Nothing ran. Install: pip install fastapi httpx cryptography")
    sys.exit(0)

# ════════════════════════════════════════════════════════════
# A stand-in for Supabase Auth, over real HTTP
# ════════════════════════════════════════════════════════════
EC_KEY = ec.generate_private_key(ec.SECP256R1())
KID = "test-es256-key"
PASSWORD = "correct-horse-battery"
KNOWN_EMAIL = "user@example.com"
ADMIN_EMAIL = "mohamtur1@gmail.com"

REFRESH_TOKENS = {}     # refresh_token -> email
AUTH_CODES = {}         # code -> {"email", "challenge"}
SERVER_HITS = []


def b64e(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def jwks():
    numbers = EC_KEY.public_key().public_numbers()
    return {"keys": [{"kty": "EC", "crv": "P-256", "kid": KID, "alg": "ES256",
                      "use": "sig",
                      "x": b64e(numbers.x.to_bytes(32, "big")),
                      "y": b64e(numbers.y.to_bytes(32, "big"))}]}


def sign_access_token(email, exp_in=3600):
    """A real ES256 JWS, exactly as Supabase would issue one."""
    header = {"alg": "ES256", "typ": "JWT", "kid": KID}
    claims = {"sub": "uid-" + email, "email": email, "role": "authenticated",
              "exp": int(time.time()) + exp_in, "iat": int(time.time())}
    h = b64e(json.dumps(header, separators=(",", ":")).encode())
    c = b64e(json.dumps(claims, separators=(",", ":")).encode())
    der = EC_KEY.sign(f"{h}.{c}".encode(), ec.ECDSA(hashes.SHA256()))
    r, s = utils.decode_dss_signature(der)
    return f"{h}.{c}.{b64e(r.to_bytes(32, 'big') + s.to_bytes(32, 'big'))}"


def session_payload(email, exp_in=3600):
    refresh = "rt-" + hashlib.sha256(f"{email}{time.time()}".encode()).hexdigest()[:24]
    REFRESH_TOKENS[refresh] = email
    return {"access_token": sign_access_token(email, exp_in),
            "token_type": "bearer", "expires_in": exp_in,
            "refresh_token": refresh,
            "user": {"id": "uid-" + email, "email": email}}


def verify_locally(token):
    """Verify an ES256 token with the key we signed it with.

    Deliberately NOT gate_core.verify_supabase_jwt: that would fetch the JWKS
    over HTTP from this same server while it is busy handling the request, and
    a single-threaded server would deadlock against itself.
    """
    try:
        from cryptography.exceptions import InvalidSignature
        parts = token.split(".")
        if len(parts) != 3:
            return None
        header = json.loads(base64.urlsafe_b64decode(parts[0] + "=="))
        if header.get("alg") != "ES256":
            return None
        claims = json.loads(base64.urlsafe_b64decode(parts[1] + "=="))
        sig = base64.urlsafe_b64decode(parts[2] + "==")
        der = utils.encode_dss_signature(int.from_bytes(sig[:32], "big"),
                                         int.from_bytes(sig[32:], "big"))
        EC_KEY.public_key().verify(der, f"{parts[0]}.{parts[1]}".encode(),
                                   ec.ECDSA(hashes.SHA256()))
    except Exception:
        return None
    if claims.get("exp", 0) < time.time():
        return None
    return claims


class AuthHandler(http.server.BaseHTTPRequestHandler):
    def _send(self, code, payload=None, headers=None):
        body = json.dumps(payload).encode() if payload is not None else b""
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _body(self):
        length = int(self.headers.get("Content-Length") or 0)
        try:
            return json.loads(self.rfile.read(length) or b"{}")
        except ValueError:
            return {}

    def log_message(self, *args):
        pass

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        SERVER_HITS.append(parsed.path)
        if parsed.path == "/auth/v1/.well-known/jwks.json":
            return self._send(200, jwks())
        if parsed.path == "/auth/v1/user":
            auth = self.headers.get("Authorization", "")
            token = auth[7:] if auth.startswith("Bearer ") else ""
            claims = verify_locally(token)
            if claims is None:
                return self._send(401, {"msg": "invalid token"})
            return self._send(200, {"id": "uid-" + claims["email"], "email": claims["email"]})
        if parsed.path == "/auth/v1/authorize":
            q = urllib.parse.parse_qs(parsed.query)
            if q.get("provider", [""])[0] != "google":
                return self._send(400, {"msg": "unsupported provider"})
            code = "code-" + hashlib.sha256(str(time.time()).encode()).hexdigest()[:16]
            AUTH_CODES[code] = {"email": KNOWN_EMAIL,
                                "challenge": q.get("code_challenge", [""])[0]}
            target = q.get("redirect_to", [""])[0]
            if not target:
                return self._send(400, {"msg": "missing redirect_to"})
            return self._send(302, None, {"Location": f"{target}?code={code}"})
        return self._send(404, {"msg": "not found"})

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        SERVER_HITS.append(parsed.path)
        body = self._body()
        q = urllib.parse.parse_qs(parsed.query)
        grant = q.get("grant_type", [""])[0]

        if parsed.path == "/auth/v1/signup":
            email = (body.get("email") or "").strip().lower()
            if not email or "@" not in email:
                return self._send(422, {"msg": "invalid email"})
            if len(body.get("password") or "") < 8:
                return self._send(422, {"error": "password_should_be_different",
                                        "msg": "password is too short"})
            return self._send(200, session_payload(email))

        if parsed.path == "/auth/v1/token" and grant == "password":
            email = (body.get("email") or "").strip().lower()
            if email != KNOWN_EMAIL or body.get("password") != PASSWORD:
                return self._send(400, {"error": "invalid_grant",
                                        "error_description": "Invalid login credentials"})
            return self._send(200, session_payload(email))

        if parsed.path == "/auth/v1/token" and grant == "pkce":
            record = AUTH_CODES.pop(body.get("auth_code") or "", None)
            if not record:
                return self._send(400, {"error": "invalid_grant",
                                        "error_description": "invalid code"})
            verifier = body.get("code_verifier") or ""
            challenge = b64e(hashlib.sha256(verifier.encode()).digest())
            if challenge != record["challenge"]:
                return self._send(400, {"error": "invalid_grant",
                                        "error_description": "PKCE verification failed"})
            return self._send(200, session_payload(record["email"]))

        if parsed.path == "/auth/v1/token" and grant == "refresh_token":
            email = REFRESH_TOKENS.get(body.get("refresh_token") or "")
            if not email:
                return self._send(400, {"error": "invalid_grant",
                                        "error_description": "Invalid Refresh Token"})
            return self._send(200, session_payload(email))

        return self._send(404, {"msg": "not found"})


# Threaded: the app under test calls this server, and some of those
# calls overlap. A single-threaded server would serialise them.
httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), AuthHandler)
PORT = httpd.server_address[1]
threading.Thread(target=httpd.serve_forever, daemon=True).start()
BASE = f"http://127.0.0.1:{PORT}"

# ════════════════════════════════════════════════════════════
# Import the endpoints with that server as "Supabase"
# ════════════════════════════════════════════════════════════
os.environ.update({
    "SUPABASE_URL": BASE,
    "SUPABASE_ANON_KEY": "anon-key-for-test",
    "SUPABASE_SERVICE_ROLE_KEY": "service-role-key",
    "SUPABASE_JWKS_URL": f"{BASE}/auth/v1/.well-known/jwks.json",
    "GATE_SHARED_SECRET": "test-gate-secret-0123456789abcdef0123456789abcdef",
    "PUBLIC_URL": "http://testserver",
    "ADMIN_EMAIL": ADMIN_EMAIL,
    "COOKIE_SECURE": "false",     # TestClient speaks http; see the Secure test below
    "SPACE_URL": "https://mohamtur1-4cbon2-gemini.hf.space",
})
os.environ.pop("SUPABASE_JWT_SECRET", None)   # the project has no shared secret

sys.path.insert(0, os.path.join(ROOT, "vercel"))
sys.path.insert(0, os.path.join(ROOT, "vercel", "api"))
import auth as auth_api       # noqa: E402
import gate as gate_api       # noqa: E402
import gate_core              # noqa: E402
import auth_core              # noqa: E402


class FakeSupabase:
    """In-memory stand-in for the data queries.

    Metering against real PostgreSQL is covered by test_gate.py; this exists so
    the auth flow can be exercised on its own. It implements only the shapes
    the endpoints use: select/insert/update with eq() and is_(), plus rpc().
    """

    def __init__(self):
        self.rows = {"admin_emails": [{"email": ADMIN_EMAIL}],
                     "subscriptions": [],
                     "run_usage": [],
                     "gate_passes": []}

    def table(self, name):
        rows = self.rows[name]

        class Result:
            def __init__(self, data):
                self.data = data

        class Q:
            def __init__(self):
                self.filters = []
                self.cols = "*"
                self.payload = None
                self.kind = "select"

            def select(self, cols="*"):
                self.kind, self.cols = "select", cols
                return self

            def insert(self, payload):
                self.kind, self.payload = "insert", payload
                return self

            def update(self, payload):
                self.kind, self.payload = "update", payload
                return self

            def eq(self, col, val):
                self.filters.append((col, ("eq", val)))
                return self

            def is_(self, col, val):
                self.filters.append((col, ("is", val)))
                return self

            def _matches(self, row):
                for col, (op, val) in self.filters:
                    if op == "eq" and row.get(col) != val:
                        return False
                    if op == "is" and str(val).lower() == "null" and row.get(col) is not None:
                        return False
                return True

            def execute(self):
                if self.kind == "insert":
                    rows.append(dict(self.payload))
                    return Result([dict(self.payload)])
                if self.kind == "update":
                    changed = [r for r in rows if self._matches(r)]
                    for r in changed:
                        r.update(self.payload)
                    return Result([dict(r) for r in changed])
                out = [r for r in rows if self._matches(r)]
                if self.cols == "*":
                    return Result([dict(r) for r in out])
                keys = [c.strip() for c in self.cols.split(",")]
                return Result([{k: r.get(k) for k in keys} for r in out])

        return Q()

    def rpc(self, fn, params):
        if fn != "try_consume_run":
            raise AssertionError(fn)
        subject, day, limit = params["p_subject"], params["p_day"], params["p_limit"]
        row = next((r for r in self.rows["run_usage"]
                    if r["subject"] == subject and r["run_date"] == day), None)
        if row is None:
            self.rows["run_usage"].append({"subject": subject, "run_date": day, "run_count": 1})
            data = {"p_allowed": True, "p_count": 1}
        elif row["run_count"] < limit:
            row["run_count"] += 1
            data = {"p_allowed": True, "p_count": row["run_count"]}
        else:
            data = {"p_allowed": False, "p_count": row["run_count"]}
        result = type("R", (), {"data": data})()
        # real gate.py chains .execute().data, so expose .execute(), not .data
        return type("Q", (), {"execute": staticmethod(lambda: result)})()


FAKE_DB = FakeSupabase()
auth_api.supabase_client = lambda: FAKE_DB
gate_api._supabase = lambda: FAKE_DB

auth_client = TestClient(auth_api.app)
gate_client = TestClient(gate_api.app)

print("=" * 74)
print(f"fake Supabase Auth listening on {BASE}")
print("=" * 74)

print("\nPKCE primitives")
verifier, challenge = auth_core.pkce_pair()
check("the S256 challenge is the SHA-256 of the verifier",
      challenge == b64e(hashlib.sha256(verifier.encode()).digest()))
check("the verifier is 64 chars of url-safe base64", len(verifier) == 64)
check("two calls produce different verifiers",
      auth_core.pkce_pair()[0] != auth_core.pkce_pair()[0])

print("\n" + "=" * 74)
print("Email + password")
print("=" * 74)
r = auth_client.post("/api/auth/signup", json={"email": "New@Example.com",
                                               "password": "a-good-password"})
check("signup succeeds", r.status_code == 200 and r.json()["authenticated"], r.text[:80])
check("signup returns the lowercased email", r.json()["email"] == "new@example.com",
      r.json().get("email"))
cookie = r.cookies.get(auth_core.SESSION_COOKIE)
check("signup sets the session cookie", bool(cookie))
raw = r.headers.get("set-cookie", "")
check("the cookie is HttpOnly", "httponly" in raw.lower(), raw[:100])
check("the cookie is SameSite=Lax", "samesite=lax" in raw.lower(), raw[:100])

r = auth_client.post("/api/auth/signup", json={"email": "x@y.z", "password": "short"})
check("a short password is refused", r.status_code == 401, f"{r.status_code} {r.text[:60]}")
r = auth_client.post("/api/auth/signup", json={"email": "not-an-email",
                                               "password": "a-good-password"})
check("an invalid email is refused", r.status_code == 401, f"{r.status_code}")

r = auth_client.post("/api/auth/login", json={"email": KNOWN_EMAIL, "password": PASSWORD})
check("login with the right password succeeds",
      r.status_code == 200 and r.json()["email"] == KNOWN_EMAIL, r.text[:80])

wrong = auth_client.post("/api/auth/login", json={"email": KNOWN_EMAIL, "password": "nope"})
unknown = auth_client.post("/api/auth/login", json={"email": "ghost@example.com",
                                                    "password": "whatever123"})
check("a wrong password is refused", wrong.status_code == 401, f"{wrong.status_code}")
check("wrong password and unknown account give the SAME error (no enumeration)",
      wrong.status_code == unknown.status_code
      and wrong.json()["error"] == unknown.json()["error"],
      f"{wrong.json().get('error')} vs {unknown.json().get('error')}")

print("\n" + "=" * 74)
print("Session status")
print("=" * 74)
r = TestClient(auth_api.app).get("/api/auth/session")
check("an anonymous visitor gets 200 authenticated:false, not a 401",
      r.status_code == 200 and r.json()["authenticated"] is False, f"{r.status_code} {r.text[:60]}")

signed_in = TestClient(auth_api.app)
signed_in.post("/api/auth/login", json={"email": KNOWN_EMAIL, "password": PASSWORD})
r = signed_in.get("/api/auth/session")
check("a signed-in visitor sees their email",
      r.status_code == 200 and r.json()["email"] == KNOWN_EMAIL, r.text[:90])
check("...and their remaining runs", r.json().get("remaining") == 3, str(r.json()))

admin_client = TestClient(auth_api.app)
admin_client.post("/api/auth/signup", json={"email": ADMIN_EMAIL, "password": "a-good-password"})
r = admin_client.get("/api/auth/session")
check("the admin email is reported unlimited", r.json().get("unlimited") is True, r.text[:90])

print("\n" + "=" * 74)
print("Google OAuth via PKCE — real HTTP round trip")
print("=" * 74)
oauth = TestClient(auth_api.app)
r = oauth.get("/api/auth/google", follow_redirects=False)
check("/api/auth/google redirects", r.status_code == 302, str(r.status_code))
location = r.headers.get("location", "")
check("...to Supabase's /auth/v1/authorize", "/auth/v1/authorize" in location, location[:70])
for expected in ("provider=google", "flow_type=pkce", "code_challenge=",
                 "code_challenge_method=S256", "redirect_to="):
    check(f"...the authorize URL carries {expected}", expected in location, location[:90])
pkce_cookie = r.cookies.get(auth_core.PKCE_COOKIE)
check("...and sets the PKCE verifier cookie", bool(pkce_cookie))
check("the PKCE cookie is httpOnly", "httponly" in r.headers.get("set-cookie", "").lower())

# Walk the redirect the browser would follow: Supabase bounces back with ?code=
bounced = TestClient(auth_api.app)
r = bounced.get("/api/auth/callback?code=not-a-real-code")
check("a callback with a bogus code is refused",
      r.status_code == 401 and r.json()["error"] == "oauth_exchange_failed",
      f"{r.status_code} {r.text[:70]}")

# A genuine round trip: start, follow to authorize, land back on the callback.
flow = TestClient(auth_api.app)
start = flow.get("/api/auth/google", follow_redirects=False)
to_supabase = start.headers["location"]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """Stop at the 302. Following it would take us to redirect_to, which is
    http://testserver/... - a hostname only TestClient can resolve."""
    def redirect_request(self, *a, **k):
        return None


opener = urllib.request.build_opener(NoRedirect)
code = None
try:
    opener.open(to_supabase)
except urllib.error.HTTPError as e:
    loc = e.headers.get("Location", "")
    code = urllib.parse.parse_qs(urllib.parse.urlparse(loc).query).get("code", [None])[0]
check("the fake IdP issued an authorisation code", bool(code), str(code)[:30])
r = flow.get(f"/api/auth/callback?code={code}", follow_redirects=False)
check("the callback exchanges the code and redirects to /",
      r.status_code == 302 and r.headers.get("location") == "/",
      f"{r.status_code} loc={r.headers.get('location')} {r.text[:60]}")
check("...and signs the user in",
      bool(r.cookies.get(auth_core.SESSION_COOKIE))
      or flow.cookies.get(auth_core.SESSION_COOKIE) is not None)
r = flow.get("/api/auth/session")
check("after the OAuth flow the session resolves to the Google account",
      r.status_code == 200 and r.json().get("email") == KNOWN_EMAIL, r.text[:90])

r = auth_client.get("/api/auth/callback")
check("a callback with no code is refused",
      r.status_code == 401 and r.json()["error"] == "oauth_incomplete", f"{r.status_code}")
r = auth_client.get("/api/auth/callback?error=access_denied")
check("a callback carrying an OAuth error is refused",
      r.status_code == 401 and r.json()["error"] == "oauth_denied", f"{r.status_code}")

print("\n" + "=" * 74)
print("Refresh and logout")
print("=" * 74)
r = signed_in.post("/api/auth/refresh")
check("refresh renews the session", r.status_code == 200 and r.json()["authenticated"], r.text[:80])
check("refresh issues a new access token",
      r.cookies.get(auth_core.SESSION_COOKIE) != cookie)

anon = TestClient(auth_api.app)
r = anon.post("/api/auth/refresh")
check("refresh with no session is refused", r.status_code == 401, f"{r.status_code}")

before = signed_in.cookies.get(auth_core.SESSION_COOKIE)
r = signed_in.post("/api/auth/logout")
check("logout clears the cookie",
      r.status_code == 200 and signed_in.cookies.get(auth_core.SESSION_COOKIE) in (None, ""),
      r.headers.get("set-cookie", "")[:70])
r = signed_in.get("/api/auth/session")
check("...and the visitor is anonymous again", r.json()["authenticated"] is False)

print("\n" + "=" * 74)
print("Auth -> gate handoff: cookie to signed pass")
print("=" * 74)
user = TestClient(auth_api.app)
user.post("/api/auth/login", json={"email": KNOWN_EMAIL, "password": PASSWORD})
session_cookie = user.cookies.get(auth_core.SESSION_COOKIE)
check("the browser holds a session cookie", bool(session_cookie))

gated = TestClient(gate_api.app, cookies={auth_core.SESSION_COOKIE: session_cookie})
r = gated.post("/api/pass")
check("/api/pass accepts the session cookie with no Authorization header",
      r.status_code == 200 and r.json()["allowed"], f"{r.status_code} {r.text[:90]}")
check("...and mints a real pass", len(r.json().get("pass", "").split(".")) == 2)
check("...with 3 runs remaining", r.json().get("remaining") == 3, str(r.json().get("remaining")))
check("...and the Space URL", r.json().get("url", "").startswith(
      "https://mohamtur1-4cbon2-gemini.hf.space/?pass="))

spent = gated.post("/api/consume", json={"pass": r.json()["pass"], "feature": "rewriter"})
check("that pass spends successfully", spent.status_code == 200 and spent.json()["allowed"],
      f"{spent.status_code} {spent.text[:80]}")

r = TestClient(gate_api.app).post("/api/pass")
check("/api/pass with neither header nor cookie is still refused",
      r.status_code == 401 and r.json()["error"] == "login_required", f"{r.status_code}")

print("\n" + "=" * 74)
print("An expired access token refreshes transparently")
print("=" * 74)
expired_session = auth_core.encode_session({
    "access_token": sign_access_token(KNOWN_EMAIL, exp_in=-300),
    "refresh_token": [t for t, e in REFRESH_TOKENS.items() if e == KNOWN_EMAIL][0],
    "expires_at": int(time.time()) - 300, "email": KNOWN_EMAIL})
stale = TestClient(gate_api.app, cookies={auth_core.SESSION_COOKIE: expired_session})
r = stale.post("/api/pass")
check("an expired token does NOT sign the visitor out",
      r.status_code == 200 and r.json()["allowed"], f"{r.status_code} {r.text[:90]}")
check("...and the renewed session cookie is handed back",
      bool(r.cookies.get(auth_core.SESSION_COOKIE)), r.headers.get("set-cookie", "")[:60])

dead = auth_core.encode_session({"access_token": sign_access_token(KNOWN_EMAIL, exp_in=-300),
                                 "refresh_token": "rt-does-not-exist",
                                 "expires_at": int(time.time()) - 300,
                                 "email": KNOWN_EMAIL})
r = TestClient(gate_api.app, cookies={auth_core.SESSION_COOKIE: dead}).post("/api/pass")
check("an expired token with a dead refresh token IS refused",
      r.status_code == 401 and r.json()["error"] == "session_expired", f"{r.status_code} {r.text[:70]}")

r = TestClient(gate_api.app, cookies={auth_core.SESSION_COOKIE: "garbage!!!"}).post("/api/pass")
check("an unreadable session cookie is refused, not crashed on",
      r.status_code == 401, f"{r.status_code} {r.text[:70]}")

print("\n" + "=" * 74)
print("Fail closed")
print("=" * 74)
saved = os.environ.pop("SUPABASE_ANON_KEY")
try:
    r = TestClient(auth_api.app).post("/api/auth/login",
                                      json={"email": KNOWN_EMAIL, "password": PASSWORD})
    check("a missing anon key -> 503, not a misleading 401",
          r.status_code == 503 and r.json()["error"] == "auth_unavailable",
          f"{r.status_code} {r.text[:70]}")
finally:
    os.environ["SUPABASE_ANON_KEY"] = saved

print("\n" + "=" * 74)
print("Supabase key naming (classic and renamed)")
print("=" * 74)
# Supabase renamed its keys (anon -> publishable, service_role -> secret). A
# deployment configured under either name must work identically; when both are
# set the classic name wins, so an in-flight migration never changes behaviour.
check("anon_key() reads the classic name when it is the only one set",
      auth_core.anon_key() == "anon-key-for-test")

saved = os.environ.pop("SUPABASE_ANON_KEY")
os.environ["SUPABASE_PUBLISHABLE_KEY"] = "sb_publishable_test000"
try:
    check("anon_key() falls back to SUPABASE_PUBLISHABLE_KEY when the classic name is unset",
          auth_core.anon_key() == "sb_publishable_test000")

    # Not just the helper: the whole sign-in flow must work through the new name,
    # because that is what a fresh deployment will have configured.
    r = TestClient(auth_api.app).post("/api/auth/login",
                                      json={"email": KNOWN_EMAIL, "password": PASSWORD})
    check("sign-in still succeeds when only the renamed key is configured",
          r.status_code == 200 and r.json().get("authenticated") is True,
          f"{r.status_code} {r.text[:70]}")
finally:
    os.environ["SUPABASE_ANON_KEY"] = saved
    os.environ.pop("SUPABASE_PUBLISHABLE_KEY", None)

os.environ["SUPABASE_PUBLISHABLE_KEY"] = "sb_publishable_other"
try:
    check("anon_key() prefers SUPABASE_ANON_KEY when both names are set",
          auth_core.anon_key() == "anon-key-for-test")
finally:
    os.environ.pop("SUPABASE_PUBLISHABLE_KEY", None)

saved = os.environ.pop("SUPABASE_ANON_KEY")
os.environ["SUPABASE_PUBLISHABLE_KEY"] = "   "
try:
    try:
        auth_core.anon_key()
        check("a whitespace-only renamed key is treated as unset, not as a key",
              False)
    except auth_core.AuthConfigError:
        check("a whitespace-only renamed key is treated as unset, not as a key",
              True)
finally:
    os.environ["SUPABASE_ANON_KEY"] = saved
    os.environ.pop("SUPABASE_PUBLISHABLE_KEY", None)

saved = os.environ.pop("SUPABASE_ANON_KEY")
os.environ["SUPABASE_PUBLISHABLE_KEY"] = "  sb_publishable_trimmed  "
try:
    check("the renamed key value is stripped before use",
          auth_core.anon_key() == "sb_publishable_trimmed")
finally:
    os.environ["SUPABASE_ANON_KEY"] = saved
    os.environ.pop("SUPABASE_PUBLISHABLE_KEY", None)

check("service_role_key() reads the classic name when it is the only one set",
      auth_core.service_role_key() == "service-role-key")

saved = os.environ.pop("SUPABASE_SERVICE_ROLE_KEY")
os.environ["SUPABASE_SECRET_KEY"] = "sb_secret_test000"
try:
    check("service_role_key() falls back to SUPABASE_SECRET_KEY when the classic name is unset",
          auth_core.service_role_key() == "sb_secret_test000")

    # The gate's health probe must agree with the code path that actually
    # builds the client — a probe that only knows the classic name would report
    # "service_key: false" on a deployment that is fully configured.
    r = TestClient(gate_api.app).get("/api/gate/health")
    check("the gate's health endpoint sees the renamed secret key as configured",
          r.status_code == 200 and r.json()["configured"]["service_key"] is True,
          str(r.json().get("configured")))
finally:
    os.environ["SUPABASE_SERVICE_ROLE_KEY"] = saved
    os.environ.pop("SUPABASE_SECRET_KEY", None)

os.environ["SUPABASE_SECRET_KEY"] = "sb_secret_other"
try:
    check("service_role_key() prefers SUPABASE_SERVICE_ROLE_KEY when both names are set",
          auth_core.service_role_key() == "service-role-key")
finally:
    os.environ.pop("SUPABASE_SECRET_KEY", None)

saved = os.environ.pop("SUPABASE_SERVICE_ROLE_KEY")
os.environ.pop("SUPABASE_SECRET_KEY", None)
try:
    try:
        auth_core.service_role_key()
        check("a missing service key is a configuration error, not a silent default",
              False)
    except auth_core.AuthConfigError as exc:
        check("a missing service key is a configuration error, not a silent default",
              "SUPABASE_SERVICE_ROLE_KEY" in str(exc) and "SUPABASE_SECRET_KEY" in str(exc),
              str(exc))
finally:
    os.environ["SUPABASE_SERVICE_ROLE_KEY"] = saved

# A signed-in visitor, established while Supabase is still healthy, so the
# outage tests below observe a real session rather than an anonymous one.
outage = TestClient(auth_api.app)
outage.post("/api/auth/login", json={"email": KNOWN_EMAIL, "password": PASSWORD})
check("control: the outage client is signed in before the outage",
      outage.get("/api/auth/session").json().get("authenticated") is True,
      outage.get("/api/auth/session").text[:70])

os.environ["SUPABASE_URL"] = "http://127.0.0.1:9"
try:
    r = TestClient(auth_api.app).post("/api/auth/login",
                                      json={"email": KNOWN_EMAIL, "password": PASSWORD})
    check("an unreachable Supabase -> 503", r.status_code == 503, f"{r.status_code} {r.text[:70]}")

    # Two things must both hold during the outage: the status is 503 (not 401),
    # and the cookie survives. Clearing it here would sign every user out at
    # once whenever Supabase blips.
    r = outage.get("/api/auth/session")
    check("during the outage /session reports 503, not 401",
          r.status_code == 503 and r.json().get("error") == "auth_unavailable",
          f"{r.status_code} {r.text[:70]}")
    check("...and does NOT delete the session cookie",
          not any(auth_core.SESSION_COOKIE in h and "Max-Age=0" in h
                  for h in r.headers.get_list("set-cookie")),
          str(r.headers.get_list("set-cookie"))[:90])

    r = outage.post("/api/auth/refresh")
    check("during the outage /refresh reports 503 and keeps the cookie",
          r.status_code == 503 and not any(
              auth_core.SESSION_COOKIE in h and "Max-Age=0" in h
              for h in r.headers.get_list("set-cookie")),
          f"{r.status_code} {str(r.headers.get_list('set-cookie'))[:90]}")
finally:
    os.environ["SUPABASE_URL"] = BASE

# The outage is over and nobody was signed out: the same cookie still works.
r = outage.get("/api/auth/session")
check("after the outage the same session is still valid (nobody was signed out)",
      r.status_code == 200 and r.json().get("authenticated") is True,
      f"{r.status_code} {r.text[:70]}")

r = TestClient(auth_api.app).get("/api/auth/health")
check("/api/auth/health reports its configuration",
      r.status_code == 200 and r.json()["configured"]["SUPABASE_ANON_KEY"] is True,
      str(r.json().get("configured")))

print("\n" + "=" * 74)
print("Cookie flags in production mode")
print("=" * 74)
os.environ["COOKIE_SECURE"] = "true"
import importlib
importlib.reload(auth_api)
secure_client = TestClient(auth_api.app)
r = secure_client.post("/api/auth/signup", json={"email": "secure@example.com",
                                                 "password": "a-good-password"})
header = r.headers.get("set-cookie", "")
check("with COOKIE_SECURE=true the cookie carries the Secure flag",
      "secure" in header.lower() and "httponly" in header.lower(), header[:110])
os.environ["COOKIE_SECURE"] = "false"

print("\n" + "=" * 74)
print("Landing page wiring (vercel/public/index.html)")
print("=" * 74)
PAGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "vercel", "public", "index.html")
html = open(PAGE, encoding="utf-8").read()

routes = {getattr(r, "path", None) for r in auth_api.app.routes}
routes |= {getattr(r, "path", None) for r in gate_api.app.routes}
routes.discard(None)

# The endpoints the page calls must exist. A renamed route would otherwise
# only surface as a 404 in someone's browser, after deploy.
called = re.findall(r'"(/api/[a-z0-9/_-]+)"', html)
check("the page calls at least one endpoint", bool(called), str(called))
missing = sorted({c for c in called if c not in routes})
check("every endpoint the page calls is a real registered route",
      not missing, f"missing: {missing}")

# The whole point of the server-side design: nothing secret reaches the page.
LEAK_MARKERS = (
    "SUPABASE_SERVICE_ROLE",
    "service_role",
    "eyJhbGciOiJIUzI1NiI",          # base64 prefix of a HS256 JWT header
    "supabaseKey",
    "createClient(",
    "@supabase/supabase-js",
)
leaks = [w for w in LEAK_MARKERS if w in html]
check("no Supabase key or client-side Supabase SDK in the landing page",
      not leaks, f"found: {leaks}")

# Every entry point into the app must be gated by the script.
ctas = re.findall(r'href="/app"', html)
check("the page still offers the free entry points", len(ctas) >= 2, f"{len(ctas)} found")
check("all of them are intercepted and routed through /api/pass",
      'querySelectorAll(\'a[href="/app"]\')' in html, "no interceptor found")

# The session cookie is httpOnly, so the page cannot read it and must ask.
check("the page asks the server who is signed in rather than reading a token",
      '"/api/auth/session"' in html and "localStorage" not in html, "")
check("the page never puts an access token in JavaScript",
      "access_token" not in html, "access_token appears in the page")

print("\n" + "=" * 74)
print("Framed app hand-off (step 5)")
print("=" * 74)
# The Space holds no session and cannot mint a pass, so the page must serve
# fresh ones on demand. If this wiring is missing the first run works and every
# run after it fails with "pass already spent" — easy to miss in a demo.
check("the page frames the Space rather than navigating away",
      'id="app-frame"' in html and "openFrame(" in html)
check("it listens for the iframe asking for a fresh pass",
      '"cbon:need-pass"' in html)
check("...and answers by minting one and posting it back",
      '"cbon:pass"' in html and "postMessage" in html)
check("the redirect fallback is present, since framing headers can appear later",
      "Open in a new tab" in html and 'id="app-pop"' in html)
check("the iframe is not left holding the app after closing",
      'src = "about:blank"' in html)
check("the counter is refreshed on close, because runs were spent",
      html.count("refreshSession()") >= 3, f"{html.count('refreshSession()')} call sites")
# A pass rides in a query string; a referrer would leak it to third parties.
check("the iframe does not leak the pass via Referer",
      'referrerpolicy="no-referrer-when-downgrade"' in html)

print("\n" + "=" * 74)
print(f"ran {CHECKS} checks")
print(f"RESULT: {len(FAILURES)} failure(s)" if FAILURES else "RESULT: all checks passed")
for f in FAILURES:
    print("  ✗", f)
print("=" * 74)

httpd.shutdown()
sys.exit(1 if FAILURES else 0)
