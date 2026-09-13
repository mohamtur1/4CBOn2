"""
4CBON2 gate primitives — signed passes, JWT verification, entitlement.

Stdlib plus `cryptography`, and nothing else — no FastAPI, no Supabase — so the
cryptographic core can be exercised on its own and the serverless function that
serves /api/pass and /api/consume stays small and cold-starts fast
(vercel/app.py drags in google.generativeai; this must not). `cryptography` is
not a new dependency for the bundle: it already arrives transitively via
google-generativeai -> google-auth, which requires cryptography>=38.

Three credentials live here and must never be confused:

  * the JWKS public keys — verify a *user's* Supabase session token. Proves
    who the browser is. Published by Supabase Auth, safe to fetch and cache.
  * SUPABASE_JWT_SECRET — the legacy shared secret, still valid for tokens
    issued before the project switched to asymmetric keys.
  * GATE_SHARED_SECRET  — signs a *pass*. Held by Vercel and the HF Space only.
    Proves that Vercel, not the visitor, authorised this run.

A visitor who learns GATE_SHARED_SECRET can mint their own unlimited passes, so
it stays in Vercel env vars and the Space secret, never in the repo, never in a
log line, and never in a URL other than the pass itself.

Fail closed: every helper raises rather than returning a permissive default.
"""
import base64
import hashlib
import hmac
import json
import os
import time
import uuid
from datetime import date, datetime, timezone
from typing import Any, Dict, Optional, Tuple

# ── Policy ────────────────────────────────────────────────────
FREE_RUNS_PER_DAY = 3
PASS_TTL_SECONDS = 15 * 60          # short: the pass rides in a query string
MIN_SECRET_LENGTH = 32              # refuse to sign with a weak secret
ADMIN_EMAIL_ENV = "ADMIN_EMAIL"     # break-glass override, seeded in vercel.json


class GateError(Exception):
    """Base class. Callers turn any of these into a denial."""


class GateConfigError(GateError):
    """A required secret is missing or too weak. Nothing can be authorised."""


class PassError(GateError):
    """The presented pass is not usable: forged, expired, malformed, replayed."""


def _env(name: str, default: str = "") -> str:
    return (os.environ.get(name) or default).strip()


def _secret(name: str) -> str:
    value = _env(name)
    if not value:
        raise GateConfigError(f"{name} is not set — refusing to authorise anything")
    if len(value) < MIN_SECRET_LENGTH:
        raise GateConfigError(
            f"{name} is only {len(value)} chars; need at least {MIN_SECRET_LENGTH}. "
            f"Generate one with: python3 -c \"import secrets; print(secrets.token_urlsafe(48))\"")
    return value


def gate_secret() -> str:
    return _secret("GATE_SHARED_SECRET")


def jwt_secret() -> str:
    return _secret("SUPABASE_JWT_SECRET")


# ── base64url ─────────────────────────────────────────────────
def _b64e(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _b64d(text: str) -> bytes:
    # Restore padding. A malformed token must fail as PassError, never leak a
    # binascii traceback that a caller might mistake for "not a pass, allow it".
    padding = "=" * (-len(text) % 4)
    try:
        return base64.urlsafe_b64decode(text + padding)
    except Exception as exc:
        raise PassError(f"malformed base64url segment: {exc}") from exc


# ── Signed passes ─────────────────────────────────────────────
def mint_pass(subject: str,
              runs: Optional[int],
              secret: Optional[str] = None,
              ttl: int = PASS_TTL_SECONDS,
              jti: Optional[str] = None,
              now: Optional[float] = None) -> Tuple[str, Dict[str, Any]]:
    """Mint a pass for `subject`.

    `runs` is None for unlimited identities (admin, active subscriber) and an
    int of remaining runs otherwise. Returns (token, payload) — the caller
    records the payload's jti in gate_passes before handing the token out, or
    the replay guard has nothing to check against.
    """
    if not subject or not str(subject).strip():
        raise GateError("cannot mint a pass without a subject")
    key = (secret if secret is not None else gate_secret()).encode("utf-8")
    issued = int(now if now is not None else time.time())
    payload = {
        "sub": str(subject).strip().lower(),
        "runs": runs,
        "iat": issued,
        "exp": issued + int(ttl),
        "jti": jti or uuid.uuid4().hex,
    }
    body = _b64e(json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8"))
    sig = _b64e(hmac.new(key, body.encode("ascii"), hashlib.sha256).digest())
    return f"{body}.{sig}", payload


def verify_pass(token: str,
                secret: Optional[str] = None,
                now: Optional[float] = None) -> Dict[str, Any]:
    """Verify signature and expiry. Raises PassError on any problem."""
    if not token or not isinstance(token, str):
        raise PassError("no pass presented")
    parts = token.strip().split(".")
    if len(parts) != 2:
        raise PassError("pass is not in body.signature form")
    body, sig = parts
    key = (secret if secret is not None else gate_secret()).encode("utf-8")
    expected = _b64e(hmac.new(key, body.encode("ascii"), hashlib.sha256).digest())
    # compare_digest on the encoded text: constant time, and safe against the
    # length-leak that a plain == would give.
    if not hmac.compare_digest(expected, sig):
        raise PassError("signature does not match")
    try:
        payload = json.loads(_b64d(body).decode("utf-8"))
    except Exception as exc:
        raise PassError(f"pass payload is not valid JSON: {exc}") from exc
    if not isinstance(payload, dict):
        raise PassError("pass payload is not an object")
    for field in ("sub", "exp", "jti"):
        if field not in payload:
            raise PassError(f"pass is missing '{field}'")
    reference = now if now is not None else time.time()
    if not isinstance(payload["exp"], (int, float)) or reference >= payload["exp"]:
        raise PassError("pass has expired")
    return payload


# ── Supabase session JWTs ─────────────────────────────────────
# Supabase has migrated this project from a shared HS256 secret to asymmetric
# signing keys (ECC P-256 => ES256), published as a JWKS. Verification
# therefore has two paths, and the security of both rests on one rule:
#
#   THE ALGORITHM DECIDES WHERE THE KEY COMES FROM, NEVER THE OTHER WAY ROUND.
#
# An asymmetric key is only ever used to verify an asymmetric signature, and
# the shared secret is only ever used for HMAC. That is what defeats the
# classic alg-confusion attack, where an attacker relabels an asymmetric token
# as HS256 and hopes the server HMACs it with the public key it already has.
# Here there is no code path in which a JWKS key can reach the HMAC branch.

_JWKS_ALGS = {"ES256", "RS256"}
_JWKS_CACHE: Dict[str, Any] = {"url": None, "keys": {}, "fetched_at": 0.0}
JWKS_TTL_SECONDS = 3600


def jwks_url() -> str:
    explicit = _env("SUPABASE_JWKS_URL")
    if explicit:
        return explicit
    base = _env("SUPABASE_URL").rstrip("/")
    if not base:
        raise GateConfigError("SUPABASE_JWKS_URL / SUPABASE_URL is not set")
    return f"{base}/auth/v1/.well-known/jwks.json"


def _http_get_json(url: str, timeout: int = 10) -> Dict[str, Any]:
    import urllib.request
    req = urllib.request.Request(url, headers={"Accept": "application/json",
                                               "User-Agent": "4cbon2-gate/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _b64u_int(value: str) -> int:
    return int.from_bytes(_b64d(value), "big")


def _jwk_to_public_key(jwk: Dict[str, Any]):
    """Build a verification key from a JWK. P-256 (ES256) and RSA (RS256)."""
    from cryptography.hazmat.primitives.asymmetric.rsa import RSAPublicNumbers
    from cryptography.hazmat.primitives.asymmetric.ec import (
        SECP256R1, EllipticCurvePublicNumbers)

    kty = jwk.get("kty")
    if kty == "EC":
        if jwk.get("crv") != "P-256":
            raise GateError(f"unsupported JWKS curve {jwk.get('crv')!r}; expected P-256")
        numbers = EllipticCurvePublicNumbers(_b64u_int(jwk["x"]), _b64u_int(jwk["y"]),
                                             SECP256R1())
        return numbers.public_key()
    if kty == "RSA":
        return RSAPublicNumbers(_b64u_int(jwk["e"]), _b64u_int(jwk["n"])).public_key()
    raise GateError(f"unsupported JWKS key type {kty!r}")


def load_jwks(force: bool = False, loader=None) -> Dict[str, Dict[str, Any]]:
    """Fetch and cache the JWKS, keyed by kid.

    `loader` is injectable so the fetch can be tested without a network. On a
    fetch failure this raises: a stale key set is not silently reused, because
    the ruling is to fail closed. The 1h TTL means a JWKS outage only affects
    cold starts, not warm ones.
    """
    url = jwks_url()
    fresh = (_JWKS_CACHE["url"] == url
             and _JWKS_CACHE["keys"]
             and time.time() - _JWKS_CACHE["fetched_at"] < JWKS_TTL_SECONDS)
    if fresh and not force:
        return _JWKS_CACHE["keys"]
    get = loader or _http_get_json
    try:
        document = get(url)
        keys = {k["kid"]: k for k in document.get("keys", []) if k.get("kid")}
    except GateError:
        raise
    except Exception as exc:
        raise GateError(f"could not fetch the JWKS from {url}: {type(exc).__name__}") from exc
    if not keys:
        raise GateError(f"the JWKS at {url} contained no usable keys")
    _JWKS_CACHE.update({"url": url, "keys": keys, "fetched_at": time.time()})
    return keys


def reset_jwks_cache() -> None:
    _JWKS_CACHE.update({"url": None, "keys": {}, "fetched_at": 0.0})


def _verify_signature(alg: str, header: Dict[str, Any], signing_input: bytes,
                      signature: bytes, loader=None) -> None:
    """Verify, or raise. Never returns a truthy/falsy hint the caller could
    misread — an unverified token is always an exception."""
    if alg == "HS256":
        # HMAC branch. The only key this branch can ever see is the configured
        # shared secret, so a public key cannot be tricked into being one.
        expected = _b64e(hmac.new(jwt_secret().encode("utf-8"), signing_input,
                                  hashlib.sha256).digest())
        if not hmac.compare_digest(expected, _b64e(signature)):
            raise GateError("session token signature does not match")
        return

    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import ec, padding, utils

    kid = header.get("kid")
    if not kid:
        raise GateError("an asymmetric session token must carry a kid")
    keys = load_jwks(loader=loader)
    jwk = keys.get(kid)
    if jwk is None:
        # A key may have rotated in since the cache was filled. Refresh once.
        keys = load_jwks(force=True, loader=loader)
        jwk = keys.get(kid)
    if jwk is None:
        raise GateError(f"no JWKS key matches kid {kid!r}")

    # Cross-check the claimed algorithm against the key the kid resolves to.
    # Without this, a token claiming RS256 but pointing at an EC key reaches
    # ECPublicKey.verify() with the wrong arity and raises TypeError — an
    # unhandled 500 instead of a clean denial.
    expected_kty = "EC" if alg == "ES256" else "RSA"
    if jwk.get("kty") != expected_kty:
        raise GateError(f"token alg {alg} does not match the JWKS key type "
                        f"{jwk.get('kty')!r} for kid {kid!r}")
    if jwk.get("alg") and jwk["alg"] != alg:
        raise GateError(f"token alg {alg} does not match the JWKS key's declared "
                        f"alg {jwk['alg']!r}")

    public_key = _jwk_to_public_key(jwk)
    try:
        if alg == "ES256":
            # A JWS ES256 signature is raw r||s, not the DER encoding that
            # cryptography expects.
            if len(signature) != 64:
                raise GateError("ES256 signature is not 64 bytes")
            der = utils.encode_dss_signature(int.from_bytes(signature[:32], "big"),
                                             int.from_bytes(signature[32:], "big"))
            public_key.verify(der, signing_input, ec.ECDSA(hashes.SHA256()))
        else:  # RS256
            public_key.verify(signature, signing_input, padding.PKCS1v15(),
                              hashes.SHA256())
    except GateError:
        raise
    except InvalidSignature as exc:
        raise GateError("session token signature does not match") from exc
    except Exception as exc:
        # A malformed key or signature must never escape as a 500. Anything
        # cryptography cannot make sense of is, by definition, not a valid token.
        raise GateError(f"signature could not be verified: {type(exc).__name__}") from exc


def verify_supabase_jwt(token: str,
                        secret: Optional[str] = None,
                        now: Optional[float] = None,
                        loader=None) -> Dict[str, Any]:
    """Verify a Supabase Auth access token and return its claims.

    Accepts ES256/RS256 against the project's JWKS (the current scheme) and
    HS256 against SUPABASE_JWT_SECRET (the legacy key, which Supabase keeps
    valid for tokens issued before the switch until they expire). Anything
    else — `none`, an unexpected alg, an unknown kid — is rejected.
    """
    if not token or not isinstance(token, str):
        raise GateError("no session token presented")
    parts = token.strip().split(".")
    if len(parts) != 3:
        raise GateError("session token is not a JWS compact serialisation")
    header_b64, claims_b64, sig_b64 = parts
    try:
        header = json.loads(_b64d(header_b64).decode("utf-8"))
    except Exception as exc:
        raise GateError(f"session token header is not valid JSON: {exc}") from exc
    alg = header.get("alg")
    if alg not in _JWKS_ALGS | {"HS256"}:
        raise GateError(f"unsupported token alg {alg!r}; accepted: ES256, RS256, HS256")
    signature = _b64d(sig_b64)
    signing_input = f"{header_b64}.{claims_b64}".encode("ascii")

    if secret is not None and alg == "HS256":
        # Test/explicit-secret path; same branch, key supplied directly.
        expected = _b64e(hmac.new(secret.encode("utf-8"), signing_input,
                                  hashlib.sha256).digest())
        if not hmac.compare_digest(expected, _b64e(signature)):
            raise GateError("session token signature does not match")
    else:
        _verify_signature(alg, header, signing_input, signature, loader=loader)

    try:
        claims = json.loads(_b64d(claims_b64).decode("utf-8"))
    except Exception as exc:
        raise GateError(f"session token claims are not valid JSON: {exc}") from exc
    if not isinstance(claims, dict):
        raise GateError("session token claims are not an object")
    reference = now if now is not None else time.time()
    exp = claims.get("exp")
    if not isinstance(exp, (int, float)) or reference >= exp:
        raise GateError("session token has expired")
    email = (claims.get("email") or "").strip().lower()
    if not email:
        raise GateError("session token carries no email")
    return claims


def bearer(header_value: Optional[str]) -> str:
    """Pull the token out of an Authorization header. Empty string if absent."""
    if not header_value:
        return ""
    scheme, _, token = header_value.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        return ""
    return token.strip()


# ── Entitlement ───────────────────────────────────────────────
def utc_today() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def is_admin(email: str, sb) -> bool:
    """Owner/admin => unlimited. Table first, env override as break-glass."""
    email = (email or "").strip().lower()
    if not email:
        return False
    override = _env(ADMIN_EMAIL_ENV).lower()
    if override and email == override:
        return True
    try:
        rows = sb.table("admin_emails").select("email").eq("email", email).execute().data
    except Exception as exc:
        # Fail closed: if we cannot check, they are not an admin.
        raise GateError(f"could not check admin_emails: {exc}") from exc
    return bool(rows)


def is_active_subscriber(email: str, sb) -> bool:
    email = (email or "").strip().lower()
    if not email:
        return False
    try:
        rows = (sb.table("subscriptions").select("status")
                .eq("email", email).eq("status", "active").execute().data)
    except Exception as exc:
        raise GateError(f"could not check subscriptions: {exc}") from exc
    return bool(rows)


def runs_used(subject: str, sb, day: Optional[str] = None) -> int:
    day = day or utc_today()
    try:
        rows = (sb.table("run_usage").select("run_count")
                .eq("subject", subject).eq("run_date", day).execute().data)
    except Exception as exc:
        raise GateError(f"could not read run_usage: {exc}") from exc
    if not rows:
        return 0
    try:
        return max(0, int(rows[0].get("run_count", 0)))
    except (TypeError, ValueError) as exc:
        raise GateError(f"run_usage.run_count is not an integer: {exc}") from exc


def entitlement(email: str, sb, day: Optional[str] = None) -> Dict[str, Any]:
    """Resolve what `email` may do today. Raises GateError on any DB failure."""
    email = (email or "").strip().lower()
    if not email:
        raise GateError("no identity to check")
    if is_admin(email, sb) or is_active_subscriber(email, sb):
        return {"email": email, "unlimited": True, "used": 0,
                "remaining": None, "reason": "admin or active subscriber"}
    used = runs_used(email, sb, day)
    remaining = max(0, FREE_RUNS_PER_DAY - used)
    return {"email": email, "unlimited": False, "used": used,
            "remaining": remaining, "reason": "free tier"}
