"""
4CBON2 Supabase Auth client — the only code that talks to Supabase Auth.

Deliberately a thin HTTP client over the Supabase Auth REST API rather than the
`gotrue` SDK, for three reasons:

  1. The anon key never reaches the browser. Every call is made server-side
     from the Vercel function, so client JavaScript holds no Supabase key at
     all.
  2. Sessions live in an httpOnly cookie set by Vercel, not in localStorage,
     so a cross-site scripting bug on the landing page cannot lift a token.
  3. It is testable. `test_auth.py` runs these functions against a real HTTP
     server on 127.0.0.1 that implements the same endpoints, including PKCE
     verification, so the flow is executed rather than mocked.

Endpoints used (all under {SUPABASE_URL}/auth/v1):
    POST /signup                        email + password registration
    POST /token?grant_type=password     email + password sign-in
    GET  /authorize?provider=google     start Google OAuth (PKCE)
    POST /token?grant_type=pkce         exchange the callback code
    POST /token?grant_type=refresh_token
    GET  /user                          resolve the access token to a profile

Fail closed: anything unexpected raises AuthError. No function here returns a
permissive default.
"""
import base64
import hashlib
import json
import os
import secrets
import time
from typing import Any, Dict, Optional, Tuple

import requests

AUTH_TIMEOUT_SECONDS = 10
REFRESH_MARGIN_SECONDS = 60     # refresh a little before the token actually dies

# Cookie names. Defined here, not in api/auth.py, because api/gate.py has to
# read the same session cookie and must not import an endpoint module to do it.
SESSION_COOKIE = "cbon_session"
PKCE_COOKIE = "cbon_pkce"


class AuthError(Exception):
    """Any failure to authenticate. Callers turn this into a 401."""


class AuthUpstreamError(AuthError):
    """Supabase Auth could not be reached, or answered with a server error.

    Callers must answer 503, not 401. Reporting a Supabase outage as
    "invalid credentials" tells the user their password is wrong and sends
    them to reset one that was never the problem.
    """


class AuthConfigError(AuthError):
    """The deployment itself is misconfigured. Callers must answer 503, not
    401 — telling a user their credentials are wrong when the real problem is
    a missing environment variable sends them chasing a phantom."""


def _cfg(name: str) -> str:
    value = (os.environ.get(name) or "").strip()
    if not value:
        raise AuthConfigError(f"{name} is not configured")
    return value


def supabase_url() -> str:
    return _cfg("SUPABASE_URL").rstrip("/")


def anon_key() -> str:
    return _cfg("SUPABASE_ANON_KEY")


def public_url() -> str:
    """Where the browser is. Supabase will only redirect back to a URL on the
    project's Redirect URLs list, so this must match what is configured there."""
    return (os.environ.get("PUBLIC_URL") or "https://app.4cbon.com").rstrip("/")


def _auth_url(path: str) -> str:
    return f"{supabase_url()}/auth/v1{path}"


def _headers(access_token: Optional[str] = None) -> Dict[str, str]:
    headers = {"apikey": anon_key(), "Content-Type": "application/json"}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers


def _request(method: str, path: str, *, params: Optional[Dict[str, Any]] = None,
             body: Optional[Dict[str, Any]] = None,
             access_token: Optional[str] = None) -> Dict[str, Any]:
    try:
        response = requests.request(method, _auth_url(path), params=params,
                                    json=body, headers=_headers(access_token),
                                    timeout=AUTH_TIMEOUT_SECONDS)
    except requests.RequestException as exc:
        raise AuthUpstreamError(
            f"could not reach Supabase Auth: {type(exc).__name__}") from exc

    if response.status_code >= 500:
        # An outage at Supabase is not the user's fault.
        raise AuthUpstreamError(f"Supabase Auth returned {response.status_code}")
    if response.status_code >= 400:
        detail = _error_message(response)
        # 4xx is the user's problem (wrong password, email taken), so the
        # message is passed through for display. 5xx above is not.
        raise AuthError(detail or f"Supabase Auth rejected the request ({response.status_code})")
    try:
        return response.json()
    except ValueError as exc:
        raise AuthError("Supabase Auth returned a non-JSON response") from exc


def _error_message(response) -> str:
    try:
        payload = response.json()
    except ValueError:
        return ""
    if isinstance(payload, dict):
        for key in ("error_description", "msg", "message", "error"):
            value = payload.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
    return ""


def _session(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Normalise a Supabase auth response into the shape the rest of the app uses.

    Raises if the response has no access token — a signup with email
    confirmation enabled returns a user but no session, and that must not be
    mistaken for a successful sign-in.
    """
    access_token = payload.get("access_token")
    if not access_token:
        raise AuthError(payload.get("msg")
                        or "no session was issued — check whether email confirmation is enabled")
    user = payload.get("user") or {}
    email = (user.get("email") or payload.get("email") or "").strip().lower()
    if not email:
        raise AuthError("the session carries no email")
    try:
        expires_in = int(payload.get("expires_in") or 3600)
    except (TypeError, ValueError):
        expires_in = 3600
    return {
        "access_token": access_token,
        "refresh_token": payload.get("refresh_token") or "",
        "expires_at": int(time.time()) + expires_in,
        "email": email,
        "user_id": user.get("id") or payload.get("id") or "",
    }


# ═══════════════════════════════════════════════════════════
# Email + password
# ═══════════════════════════════════════════════════════════
def signup(email: str, password: str) -> Dict[str, Any]:
    email = (email or "").strip().lower()
    if not email or "@" not in email:
        raise AuthError("a valid email address is required")
    if not password or len(password) < 8:
        raise AuthError("the password must be at least 8 characters")
    return _session(_request("POST", "/signup", body={"email": email, "password": password}))


def login_password(email: str, password: str) -> Dict[str, Any]:
    email = (email or "").strip().lower()
    if not email or not password:
        raise AuthError("email and password are required")
    return _session(_request("POST", "/token", params={"grant_type": "password"},
                             body={"email": email, "password": password}))


# ═══════════════════════════════════════════════════════════
# Google OAuth via PKCE
# ═══════════════════════════════════════════════════════════
def pkce_pair() -> Tuple[str, str]:
    """RFC 7636 S256. The verifier stays in an httpOnly cookie; only the
    challenge is sent to Google/Supabase."""
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(48)).rstrip(b"=").decode()
    challenge = base64.urlsafe_b64encode(
        hashlib.sha256(verifier.encode("ascii")).digest()).rstrip(b"=").decode()
    return verifier, challenge


def callback_url() -> str:
    return f"{public_url()}/api/auth/callback"


def google_authorize_url(code_challenge: str) -> str:
    """Where to send the browser to start a Google sign-in.

    `flow_type=pkce` is explicit: it makes Supabase redirect back with
    ?code=... rather than putting tokens in the URL fragment, so no token ever
    lands in browser history or a referrer header.
    """
    params = {
        "provider": "google",
        "redirect_to": callback_url(),
        "flow_type": "pkce",
        "code_challenge": code_challenge,
        "code_challenge_method": "S256",
    }
    query = "&".join(f"{k}={requests.utils.quote(str(v), safe='')}" for k, v in params.items())
    return f"{_auth_url('/authorize')}?{query}"


def exchange_code(code: str, code_verifier: str) -> Dict[str, Any]:
    if not code or not code_verifier:
        raise AuthError("the OAuth callback is missing its code or verifier")
    return _session(_request("POST", "/token", params={"grant_type": "pkce"},
                             body={"auth_code": code, "code_verifier": code_verifier}))


# ═══════════════════════════════════════════════════════════
# Session maintenance
# ═══════════════════════════════════════════════════════════
def refresh(refresh_token: str) -> Dict[str, Any]:
    if not refresh_token:
        raise AuthError("no refresh token")
    return _session(_request("POST", "/token", params={"grant_type": "refresh_token"},
                             body={"refresh_token": refresh_token}))


def get_user(access_token: str) -> Dict[str, Any]:
    payload = _request("GET", "/user", access_token=access_token)
    email = (payload.get("email") or "").strip().lower()
    if not email:
        raise AuthError("the access token resolved to no email")
    return {"email": email, "user_id": payload.get("id") or ""}


def needs_refresh(session: Dict[str, Any], now: Optional[float] = None) -> bool:
    """True when the access token is missing, malformed, or about to expire."""
    expires_at = session.get("expires_at")
    if not isinstance(expires_at, (int, float)):
        return True
    return (now if now is not None else time.time()) >= expires_at - REFRESH_MARGIN_SECONDS


def encode_session(session: Dict[str, Any]) -> str:
    """Cookie value. Not encrypted — it is httpOnly and Secure, and the tokens
    inside are already signed by Supabase. Encryption here would add a second
    secret to manage for no real gain."""
    return base64.urlsafe_b64encode(
        json.dumps(session, separators=(",", ":")).encode("utf-8")).decode("ascii")


def decode_session(value: str) -> Dict[str, Any]:
    if not value:
        raise AuthError("no session cookie")
    try:
        session = json.loads(base64.urlsafe_b64decode(value + "=" * (-len(value) % 4)).decode("utf-8"))
    except Exception as exc:
        raise AuthError("the session cookie is not readable") from exc
    if not isinstance(session, dict) or not session.get("access_token"):
        raise AuthError("the session cookie carries no access token")
    return session
