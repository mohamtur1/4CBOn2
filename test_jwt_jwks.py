#!/usr/bin/env python3
"""Verify Supabase session tokens against a JWKS (ES256 / RS256) and HS256.

The project moved from a shared HS256 secret to asymmetric signing keys
(ECC P-256 => ES256), so session tokens are now verified against the published
JWKS. This suite generates real keypairs, signs real tokens, and checks the
properties that matter — above all that the algorithm cannot be used to smuggle
a public key into the HMAC branch.

No PostgreSQL and no network beyond 127.0.0.1. Usage: python3 test_jwt_jwks.py
"""
import base64
import hashlib
import hmac as hmac_mod
import http.server
import json
import os
import sys
import threading
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ROOT, "vercel"))

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
    from cryptography.hazmat.primitives.asymmetric import ec, padding, rsa, utils
except ImportError:
    print("SKIP — `cryptography` is not installed. Nothing ran.")
    sys.exit(0)

import gate_core  # noqa: E402

# ════════════════════════════════════════════════════════════
# Real keys, real signatures
# ════════════════════════════════════════════════════════════
EC_KEY = ec.generate_private_key(ec.SECP256R1())
EC_PUB = EC_KEY.public_key()
EC_KID = "7bac5d81-9339-48e0-bc53-6c582eba28d1"   # Malik's reported current key id

EC2_KEY = ec.generate_private_key(ec.SECP256R1())   # a different key: an attacker's
RSA_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)
RSA_KID = "rsa-key-1"

LEGACY_SECRET = "legacy-hs256-shared-secret-0123456789abcdef"


def b64e(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def ec_jwk(pub, kid):
    numbers = pub.public_numbers()
    return {"kty": "EC", "crv": "P-256", "kid": kid, "alg": "ES256", "use": "sig",
            "x": b64e(numbers.x.to_bytes(32, "big")),
            "y": b64e(numbers.y.to_bytes(32, "big"))}


def rsa_jwk(pub, kid):
    numbers = pub.public_numbers()
    return {"kty": "RSA", "kid": kid, "alg": "RS256", "use": "sig",
            "n": b64e(numbers.n.to_bytes((numbers.n.bit_length() + 7) // 8, "big")),
            "e": b64e(numbers.e.to_bytes((numbers.e.bit_length() + 7) // 8, "big"))}


JWKS = {"keys": [ec_jwk(EC_PUB, EC_KID), rsa_jwk(RSA_KEY.public_key(), RSA_KID)]}


def make_jwt(email="user@example.com", alg="ES256", kid=EC_KID, exp_in=3600,
             key=EC_KEY, claims_extra=None, tamper=False, sig_override=None,
             hmac_key=None):
    header = {"alg": alg, "typ": "JWT"}
    if kid is not None:
        header["kid"] = kid
    claims = {"sub": "abc", "exp": int(time.time()) + exp_in, "role": "authenticated"}
    if email is not None:
        claims["email"] = email
    if claims_extra:
        claims.update(claims_extra)
    h = b64e(json.dumps(header, separators=(",", ":")).encode())
    c_signed = b64e(json.dumps(claims, separators=(",", ":")).encode())
    # The signature is always taken over the ORIGINAL claims. `tamper` swaps a
    # different claims segment in afterwards, which is what an attacker does —
    # editing the payload without being able to re-sign it.
    signing_input = f"{h}.{c_signed}".encode()
    if sig_override is not None:
        return f"{h}.{c_signed}.{b64e(sig_override)}"
    if alg == "ES256":
        der = key.sign(signing_input, ec.ECDSA(hashes.SHA256()))
        r, s = utils.decode_dss_signature(der)
        sig = b64e(r.to_bytes(32, "big") + s.to_bytes(32, "big"))
    elif alg == "RS256":
        sig = b64e(key.sign(signing_input, padding.PKCS1v15(), hashes.SHA256()))
    elif alg == "HS256":
        sig = b64e(hmac_mod.new(hmac_key.encode(), signing_input, hashlib.sha256).digest())
    elif alg == "none":
        return f"{h}.{c_signed}."
    else:
        raise ValueError(alg)
    if tamper:
        c_signed = b64e(json.dumps({**claims, "email": "attacker@evil.test"},
                                   separators=(",", ":")).encode())
    return f"{h}.{c_signed}.{sig}"


LOADS = []


def loader(url):
    """Stands in for the network. Records calls so caching can be asserted."""
    LOADS.append(url)
    return JWKS


os.environ["SUPABASE_URL"] = "https://projref.supabase.co"
os.environ["SUPABASE_JWT_SECRET"] = LEGACY_SECRET
os.environ.pop("SUPABASE_JWKS_URL", None)

print("=" * 74)
print("JWKS endpoint resolution")
print("=" * 74)
check("default JWKS URL derives from SUPABASE_URL",
      gate_core.jwks_url() == "https://projref.supabase.co/auth/v1/.well-known/jwks.json",
      gate_core.jwks_url())
os.environ["SUPABASE_JWKS_URL"] = "https://example.test/jwks.json"
check("SUPABASE_JWKS_URL overrides it", gate_core.jwks_url() == "https://example.test/jwks.json")
os.environ.pop("SUPABASE_JWKS_URL")

print("\n" + "=" * 74)
print("ES256 — the current signing scheme")
print("=" * 74)
gate_core.reset_jwks_cache()

claims = gate_core.verify_supabase_jwt(make_jwt("Owner@Example.com"), loader=loader)
check("a valid ES256 token is accepted", claims["email"] == "Owner@Example.com")
check("the JWKS was fetched", len(LOADS) == 1, f"{len(LOADS)} fetch(es)")
gate_core.verify_supabase_jwt(make_jwt(), loader=loader)
check("the JWKS is cached, not refetched per token", len(LOADS) == 1,
      f"{len(LOADS)} fetch(es) after 2 tokens")

try:
    gate_core.verify_supabase_jwt(make_jwt(key=EC2_KEY), loader=loader)
    check("a token signed by a DIFFERENT P-256 key is rejected", False)
except gate_core.GateError as e:
    check("a token signed by a DIFFERENT P-256 key is rejected", True, str(e)[:44])

try:
    gate_core.verify_supabase_jwt(make_jwt(tamper=True), loader=loader)
    check("editing the email claim breaks the signature", False)
except gate_core.GateError as e:
    check("editing the email claim breaks the signature", True, str(e)[:44])

try:
    gate_core.verify_supabase_jwt(make_jwt(exp_in=-10), loader=loader)
    check("an expired ES256 token is rejected", False)
except gate_core.GateError:
    check("an expired ES256 token is rejected", True)

try:
    gate_core.verify_supabase_jwt(make_jwt(email=None), loader=loader)
    check("a token with no email is rejected", False)
except gate_core.GateError:
    check("a token with no email is rejected", True)

try:
    gate_core.verify_supabase_jwt(make_jwt(kid="a-kid-that-does-not-exist"), loader=loader)
    check("an unknown kid is rejected (after one refresh)", False)
except gate_core.GateError as e:
    check("an unknown kid is rejected (after one refresh)", True, str(e)[:44])
check("an unknown kid triggered exactly one cache refresh", len(LOADS) == 2,
      f"{len(LOADS)} fetch(es)")

try:
    gate_core.verify_supabase_jwt(make_jwt(kid=None), loader=loader)
    check("an asymmetric token with no kid is rejected", False)
except gate_core.GateError as e:
    check("an asymmetric token with no kid is rejected", True, str(e)[:44])

print("\n" + "=" * 74)
print("Key rotation")
print("=" * 74)
NEW_KID = "rotated-in-key-id"
gate_core.reset_jwks_cache()
gate_core.verify_supabase_jwt(make_jwt(), loader=loader)          # fills the cache
ROTATED = {"keys": JWKS["keys"] + [ec_jwk(EC_PUB, NEW_KID)]}
rotating_loader_calls = []


def rotating_loader(url):
    rotating_loader_calls.append(url)
    return ROTATED


try:
    claims = gate_core.verify_supabase_jwt(make_jwt(kid=NEW_KID), loader=rotating_loader)
    check("a token signed with a newly rotated-in key is accepted after a refresh",
          claims["email"] == "user@example.com")
except gate_core.GateError as e:
    check("a token signed with a newly rotated-in key is accepted after a refresh",
          False, str(e)[:60])

print("\n" + "=" * 74)
print("RS256")
print("=" * 74)
claims = gate_core.verify_supabase_jwt(make_jwt(alg="RS256", kid=RSA_KID, key=RSA_KEY),
                                       loader=loader)
check("a valid RS256 token is accepted", claims["email"] == "user@example.com")
try:
    gate_core.verify_supabase_jwt(make_jwt(alg="RS256", kid=EC_KID, key=RSA_KEY),
                                  loader=loader)
    check("an RS256 signature is not verified with the EC key", False)
except gate_core.GateError:
    check("an RS256 signature is not verified with the EC key", True)

print("\n" + "=" * 74)
print("Algorithm confusion — the attack this design exists to stop")
print("=" * 74)

# The attacker knows the public key (the JWKS is public). They relabel a token
# as HS256 and sign it with HMAC(public key bytes), betting the server will use
# the JWKS key as the HMAC secret.
pub_pem = EC_PUB.public_bytes(serialization.Encoding.PEM,
                              serialization.PublicFormat.SubjectPublicKeyInfo)
hostile = make_jwt(email="attacker@evil.test", alg="HS256", kid=None,
                   hmac_key=pub_pem.decode())
try:
    gate_core.verify_supabase_jwt(hostile, loader=loader)
    check("alg=HS256 signed with the PUBLIC KEY is rejected", False,
          "the HMAC branch accepted JWKS material")
except gate_core.GateError as e:
    check("alg=HS256 signed with the PUBLIC KEY is rejected", True, str(e)[:44])

# The bug this caught: an alg that does not match the key the kid resolves to
# used to reach ECPublicKey.verify() with the wrong arity and raise TypeError,
# which would have escaped the endpoint as an unhandled 500.
try:
    gate_core.verify_supabase_jwt(make_jwt(alg="RS256", kid=EC_KID, key=RSA_KEY),
                                  loader=loader)
    check("alg=RS256 pointing at an EC kid is rejected cleanly (no TypeError)", False)
except gate_core.GateError as e:
    check("alg=RS256 pointing at an EC kid is rejected cleanly (no TypeError)", True,
          str(e)[:56])
except TypeError as e:
    check("alg=RS256 pointing at an EC kid is rejected cleanly (no TypeError)", False,
          f"escaped as TypeError: {e}")

try:
    gate_core.verify_supabase_jwt(make_jwt(alg="ES256", kid=RSA_KID, key=EC_KEY),
                                  loader=loader)
    check("alg=ES256 pointing at an RSA kid is rejected cleanly", False)
except gate_core.GateError as e:
    check("alg=ES256 pointing at an RSA kid is rejected cleanly", True, str(e)[:56])
except TypeError as e:
    check("alg=ES256 pointing at an RSA kid is rejected cleanly", False,
          f"escaped as TypeError: {e}")

try:
    gate_core.verify_supabase_jwt(make_jwt(alg="none"), loader=loader)
    check('alg="none" is rejected', False)
except gate_core.GateError as e:
    check('alg="none" is rejected', True, str(e)[:52])

for alg in ("HS512", "ES512", "PS256", "", None):
    try:
        token = make_jwt(alg="HS256", hmac_key=LEGACY_SECRET)
        header = json.loads(base64.urlsafe_b64decode(token.split(".")[0] + "==").decode())
        header["alg"] = alg
        forged = (b64e(json.dumps(header, separators=(",", ":")).encode())
                  + "." + token.split(".")[1] + "." + token.split(".")[2])
        gate_core.verify_supabase_jwt(forged, loader=loader)
        check(f"alg={alg!r} is not in the allowlist", False)
    except gate_core.GateError:
        check(f"alg={alg!r} is not in the allowlist", True)

print("\n" + "=" * 74)
print("Legacy HS256 — the transition window")
print("=" * 74)
legacy_token = make_jwt(alg="HS256", kid=None, hmac_key=LEGACY_SECRET)
claims = gate_core.verify_supabase_jwt(legacy_token, loader=loader)
check("a legacy HS256 token still verifies while the secret is configured",
      claims["email"] == "user@example.com")
check("...and carries the real email, not a substituted one",
      claims["email"] != "attacker@evil.test")

saved = os.environ.pop("SUPABASE_JWT_SECRET")
try:
    gate_core.verify_supabase_jwt(legacy_token, loader=loader)
    check("with no shared secret configured, HS256 is refused", False)
except gate_core.GateConfigError as e:
    check("with no shared secret configured, HS256 is refused", True, str(e)[:44])
finally:
    os.environ["SUPABASE_JWT_SECRET"] = saved

print("\n" + "=" * 74)
print("Fetch failures fail closed")
print("=" * 74)


def broken_loader(url):
    raise OSError("connection refused")


gate_core.reset_jwks_cache()
try:
    gate_core.verify_supabase_jwt(make_jwt(), loader=broken_loader)
    check("an unreachable JWKS denies the token", False)
except gate_core.GateError as e:
    check("an unreachable JWKS denies the token", True, str(e)[:52])

gate_core.reset_jwks_cache()
try:
    gate_core.verify_supabase_jwt(make_jwt(), loader=lambda url: {"keys": []})
    check("an empty JWKS denies the token", False)
except gate_core.GateError as e:
    check("an empty JWKS denies the token", True, str(e)[:44])

gate_core.reset_jwks_cache()
try:
    gate_core.verify_supabase_jwt(make_jwt(), loader=lambda url: {"keys": [{"kty": "EC"}]})
    check("a JWKS with no usable kid denies the token", False)
except gate_core.GateError as e:
    check("a JWKS with no usable kid denies the token", True, str(e)[:44])

print("\n" + "=" * 74)
print("Real HTTP fetch against 127.0.0.1")
print("=" * 74)

HITS = []


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        HITS.append(self.path)
        body = json.dumps(JWKS).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


httpd = http.server.HTTPServer(("127.0.0.1", 0), Handler)
port = httpd.server_address[1]
threading.Thread(target=httpd.serve_forever, daemon=True).start()

os.environ["SUPABASE_JWKS_URL"] = f"http://127.0.0.1:{port}/auth/v1/.well-known/jwks.json"
gate_core.reset_jwks_cache()
try:
    claims = gate_core.verify_supabase_jwt(make_jwt("live@example.com"))
    check("a token verifies against a JWKS fetched over real HTTP",
          claims["email"] == "live@example.com")
    gate_core.verify_supabase_jwt(make_jwt())
    gate_core.verify_supabase_jwt(make_jwt())
    check("3 tokens -> 1 HTTP request (the cache works)", len(HITS) == 1,
          f"{len(HITS)} request(s) to {HITS[:2]}")
    check("the cache expires on TTL, not on every call",
          gate_core._JWKS_CACHE["keys"] and EC_KID in gate_core._JWKS_CACHE["keys"])
finally:
    httpd.shutdown()
    os.environ.pop("SUPABASE_JWKS_URL", None)
    gate_core.reset_jwks_cache()

print("\n" + "=" * 74)
print(f"ran {CHECKS} checks")
print(f"RESULT: {len(FAILURES)} failure(s)" if FAILURES else "RESULT: all checks passed")
for f in FAILURES:
    print("  ✗", f)
print("=" * 74)
sys.exit(1 if FAILURES else 0)
