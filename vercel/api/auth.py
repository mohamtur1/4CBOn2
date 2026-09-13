"""
4CBON2 auth endpoints — sign-up, sign-in, Google OAuth, session.

A separate serverless function, like the gate. It talks to Supabase Auth and
nothing else, so it stays small and cold-starts fast.

The browser never sees a Supabase key and never holds a token in JavaScript:

  * every Supabase Auth call is made here, server-side, with the anon key held
    only in Vercel's environment;
  * the resulting session is written to an httpOnly, Secure, SameSite=Lax
    cookie, so page scripts cannot read it;
  * Google uses the PKCE flow, so Supabase redirects back with ?code=... rather
    than putting tokens in the URL fragment. No token reaches browser history
    or a referrer header.

Fail closed. Errors split three ways, because conflating them misleads:

  * bad credentials  -> 401 invalid_credentials
  * Supabase unreachable / 5xx, or a missing env var -> 503 auth_unavailable.
    Returning 401 here would blame the user's password for an outage.
  * anonymous caller -> 200 with authenticated:false from /api/auth/session,
    because that route is a question, not a gate.
"""
import os
import sys
from typing import Any, Dict, Optional

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, RedirectResponse

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import auth_core  # noqa: E402
from auth_core import (  # noqa: E402
    AuthConfigError, AuthError, AuthUpstreamError,
)
from gate_core import GateError, entitlement  # noqa: E402
from db import supabase_client  # noqa: E402

app = FastAPI(title="4CBON2 Auth")

SESSION_COOKIE = auth_core.SESSION_COOKIE
PKCE_COOKIE = auth_core.PKCE_COOKIE
SESSION_MAX_AGE = 7 * 24 * 3600      # the access token inside is refreshed well before this
PKCE_MAX_AGE = 10 * 60               # long enough to complete a Google round trip

# Secure cookies are not sent over plain http, which breaks local development.
# Default on; set COOKIE_SECURE=false for localhost only.
COOKIE_SECURE = (os.environ.get("COOKIE_SECURE", "true").strip().lower()
                 not in ("false", "0", "no"))


def _deny(status: int, reason: str, **extra) -> JSONResponse:
    # The reason is shown to a human; the detail goes to the function log only.
    detail = extra.pop("detail", None)
    if detail:
        print(f"[auth] deny {status}: {reason} — {detail}")
    return JSONResponse(status_code=status,
                        content={"authenticated": False, "error": reason, **extra})


def _cookie(response, name: str, value: str, max_age: int) -> None:
    response.set_cookie(key=name, value=value, max_age=max_age, path="/",
                        httponly=True, secure=COOKIE_SECURE, samesite="lax")


def _session_response(session: Dict[str, Any]) -> JSONResponse:
    response = JSONResponse({"authenticated": True, "email": session["email"]})
    _cookie(response, SESSION_COOKIE, auth_core.encode_session(session), SESSION_MAX_AGE)
    return response


def _read_session(request: Request) -> Optional[Dict[str, Any]]:
    raw = request.cookies.get(SESSION_COOKIE)
    if not raw:
        return None
    try:
        return auth_core.decode_session(raw)
    except AuthError:
        return None


async def _body(request: Request) -> Dict[str, Any]:
    try:
        payload = await request.json()
    except Exception:
        return {}
    return payload if isinstance(payload, dict) else {}


# ═══════════════════════════════════════════════════════════
# Email + password
# ═══════════════════════════════════════════════════════════
@app.post("/api/auth/signup")
async def signup(request: Request):
    body = await _body(request)
    try:
        session = auth_core.signup(str(body.get("email") or ""),
                                   str(body.get("password") or ""))
    except (AuthConfigError, AuthUpstreamError) as exc:
        # Misconfiguration or a Supabase outage. A 401 here would blame the
        # user's password for something that is not their doing.
        return _deny(503, "auth_unavailable", detail=str(exc))
    except AuthError as exc:
        return _deny(401, "signup_failed", detail=str(exc), message=str(exc))
    return _session_response(session)


@app.post("/api/auth/login")
async def login(request: Request):
    body = await _body(request)
    try:
        session = auth_core.login_password(str(body.get("email") or ""),
                                           str(body.get("password") or ""))
    except (AuthConfigError, AuthUpstreamError) as exc:
        # Misconfiguration or a Supabase outage. A 401 here would blame the
        # user's password for something that is not their doing.
        return _deny(503, "auth_unavailable", detail=str(exc))
    except AuthError as exc:
        # Same status and shape for a wrong password and an unknown address:
        # telling them apart is an account-enumeration oracle.
        return _deny(401, "invalid_credentials", detail=str(exc))
    return _session_response(session)


# ═══════════════════════════════════════════════════════════
# Google OAuth (PKCE)
# ═══════════════════════════════════════════════════════════
@app.get("/api/auth/google")
async def google_start():
    try:
        verifier, challenge = auth_core.pkce_pair()
        url = auth_core.google_authorize_url(challenge)
    except AuthError as exc:
        # AuthConfigError subclasses AuthError, so a missing SUPABASE_URL or
        # anon key lands here too — correctly, as a 503.
        return _deny(503, "auth_unavailable", detail=str(exc))
    response = RedirectResponse(url, status_code=302)
    # The verifier must survive the round trip to Google and back, and must not
    # be readable by page scripts.
    _cookie(response, PKCE_COOKIE, verifier, PKCE_MAX_AGE)
    return response


@app.get("/api/auth/callback")
async def google_callback(request: Request, code: str = "", error: str = ""):
    if error:
        return _deny(401, "oauth_denied", message=error)
    if not code:
        return _deny(401, "oauth_incomplete",
                     message="Google did not return an authorisation code")
    verifier = request.cookies.get(PKCE_COOKIE) or ""
    try:
        session = auth_core.exchange_code(code, verifier)
    except (AuthConfigError, AuthUpstreamError) as exc:
        return _deny(503, "auth_unavailable", detail=str(exc))
    except AuthError as exc:
        return _deny(401, "oauth_exchange_failed", detail=str(exc))
    response = RedirectResponse("/", status_code=302)
    _cookie(response, SESSION_COOKIE, auth_core.encode_session(session), SESSION_MAX_AGE)
    response.delete_cookie(PKCE_COOKIE, path="/")
    return response


# ═══════════════════════════════════════════════════════════
# Session
# ═══════════════════════════════════════════════════════════
@app.post("/api/auth/refresh")
async def refresh(request: Request):
    session = _read_session(request)
    if not session:
        return _deny(401, "not_signed_in")
    try:
        renewed = auth_core.refresh(session.get("refresh_token", ""))
    except (AuthConfigError, AuthUpstreamError) as exc:
        # Supabase is down. Do NOT clear the cookie: a transient outage would
        # sign every user out at once and they would all have to log in again.
        return _deny(503, "auth_unavailable", detail=str(exc))
    except AuthError as exc:
        response = _deny(401, "session_expired", detail=str(exc))
        response.delete_cookie(SESSION_COOKIE, path="/")
        return response
    return _session_response(renewed)


@app.post("/api/auth/logout")
async def logout():
    response = JSONResponse({"authenticated": False})
    response.delete_cookie(SESSION_COOKIE, path="/")
    return response


@app.get("/api/auth/session")
async def session_status(request: Request):
    """Who is signed in, and what they may do today.

    The landing page calls this on load to decide whether to show a sign-in
    form or a run counter. It needs no credentials of its own beyond the
    cookie, and an anonymous visitor gets a normal 200 with
    authenticated: false rather than a 401 — this endpoint is a question, not
    a gate.
    """
    session = _read_session(request)
    if not session:
        return JSONResponse({"authenticated": False})

    access_token = session.get("access_token", "")
    if auth_core.needs_refresh(session):
        try:
            session = auth_core.refresh(session.get("refresh_token", ""))
        except (AuthConfigError, AuthUpstreamError) as exc:
            return _deny(503, "auth_unavailable", detail=str(exc))
        except AuthError as exc:
            response = _deny(401, "session_expired", detail=str(exc))
            response.delete_cookie(SESSION_COOKIE, path="/")
            return response
        access_token = session["access_token"]
    else:
        # The cookie was written when the token was minted. If Supabase has
        # since revoked it, find out here rather than at /api/pass.
        try:
            auth_core.get_user(access_token)
        except (AuthConfigError, AuthUpstreamError) as exc:
            return _deny(503, "auth_unavailable", detail=str(exc))
        except AuthError as exc:
            response = _deny(401, "session_expired", detail=str(exc))
            response.delete_cookie(SESSION_COOKIE, path="/")
            return response

    try:
        rights = entitlement(session["email"], supabase_client())
    except GateError as exc:
        # The identity is proven but the meter is unreachable. Report the
        # sign-in and omit entitlement rather than failing the whole call.
        return JSONResponse({"authenticated": True, "email": session["email"],
                             "entitlement": None, "entitlement_error": "unavailable"})

    response = JSONResponse({"authenticated": True, "email": session["email"],
                             "unlimited": rights["unlimited"],
                             "remaining": rights["remaining"],
                             "used": rights["used"]})
    if auth_core.needs_refresh({"expires_at": session.get("expires_at")}):
        _cookie(response, SESSION_COOKIE, auth_core.encode_session(session), SESSION_MAX_AGE)
    return response


@app.get("/api/auth/health")
async def health():
    configured = {}
    for name in ("SUPABASE_URL", "SUPABASE_ANON_KEY", "SUPABASE_SERVICE_ROLE_KEY",
                 "PUBLIC_URL"):
        configured[name] = bool((os.environ.get(name) or "").strip())
    configured["cookie_secure"] = COOKIE_SECURE
    return {"status": "ok", "configured": configured}
