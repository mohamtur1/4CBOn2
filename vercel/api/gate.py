"""
4CBON2 gate — /api/pass and /api/consume.

A separate serverless function on purpose. vercel/api/index.py imports app.py,
which imports google.generativeai and gradio; the gate needs none of that and
must cold-start fast, because the HF Space calls /api/consume at the top of
every Ask / Rewriter / Agent Mode run and a slow answer there reads as a hang.

Two endpoints:

  POST /api/pass     browser → Vercel. Proves who the visitor is (Supabase
                     session JWT), resolves entitlement, mints a single-use
                     pass and records its jti.
  POST /api/consume  HF Space → Vercel. Proves Vercel authorised this run
                     (pass signature), spends the jti exactly once, and moves
                     the shared counter atomically.

Fail closed everywhere: an unhandled error becomes a denial, never an allow.
"""
import os
import sys
import time
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import auth_core  # noqa: E402
from auth_core import AuthError  # noqa: E402
from gate_core import (  # noqa: E402
    FREE_RUNS_PER_DAY,
    PASS_TTL_SECONDS,
    GateError,
    PassError,
    bearer,
    entitlement,
    gate_secret,
    mint_pass,
    utc_today,
    verify_pass,
    verify_supabase_jwt,
)

app = FastAPI(title="4CBON2 Gate")

VALID_FEATURES = {"ask", "rewriter", "agent"}
SPACE_URL = os.environ.get("SPACE_URL", "").rstrip("/")
# Secure cookies are dropped by browsers over plain http, which breaks local
# development. Default on; set COOKIE_SECURE=false for localhost only.
COOKIE_SECURE = (os.environ.get("COOKIE_SECURE", "true").strip().lower()
                 not in ("false", "0", "no"))


from db import supabase_client as _supabase  # noqa: E402


def _deny(status: int, reason: str, **extra) -> JSONResponse:
    # Never echo the exception text to the client: it can contain a SQL error
    # quoting a row, or a secret name. It goes to the function log instead.
    print(f"[gate] deny {status}: {reason} {extra}")
    return JSONResponse(status_code=status,
                        content={"allowed": False, "error": reason, **extra})


def _client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for", "")
    return forwarded.split(",")[0].strip() or (request.client.host if request.client else "unknown")


# ═══════════════════════════════════════════════════════════
# POST /api/pass — mint a single-use pass for a signed-in user
# ═══════════════════════════════════════════════════════════
@app.post("/api/pass")
async def issue_pass(request: Request):
    """Mint a pass for the signed-in visitor.

    Identity comes from an Authorization header (programmatic callers) or from
    the httpOnly session cookie set by /api/auth/* (the landing page). The
    cookie path transparently refreshes an expired access token, so a visitor
    who left the tab open for two hours is not signed out mid-session.
    """
    token = bearer(request.headers.get("authorization"))
    from_cookie = False
    session = None
    if not token:
        raw = request.cookies.get(auth_core.SESSION_COOKIE)
        if raw:
            try:
                session = auth_core.decode_session(raw)
                token = session.get("access_token", "")
                from_cookie = True
            except AuthError:
                session = None
    if not token:
        return _deny(401, "login_required",
                     hint="Sign in at app.4cbon.com, or send "
                          "Authorization: Bearer <Supabase access token>.")

    refreshed = None
    try:
        claims = verify_supabase_jwt(token)
    except GateError as exc:
        # One refresh attempt, and only for a cookie session: a caller passing
        # an explicit header is responsible for its own token lifecycle.
        if not (from_cookie and session and session.get("refresh_token")):
            return _deny(401, "invalid_session", detail=str(exc))
        try:
            refreshed = auth_core.refresh(session["refresh_token"])
            claims = verify_supabase_jwt(refreshed["access_token"])
        except (AuthError, GateError) as retry_exc:
            response = _deny(401, "session_expired", detail=str(retry_exc))
            response.delete_cookie(auth_core.SESSION_COOKIE, path="/")
            return response

    email = (claims.get("email") or "").strip().lower()
    try:
        sb = _supabase()
    except GateError as exc:
        return _deny(503, "gate_unavailable", detail=str(exc))

    try:
        rights = entitlement(email, sb)
    except GateError as exc:
        # entitlement() only raises when it could not reach the database — the
        # "no runs left" case returns normally with remaining == 0 and becomes
        # a 402 below. An outage is our fault, so it is a 503, not a 403: a
        # 403 here would show a paying subscriber the paywall during a Supabase
        # blip. Still fail closed — allowed is False either way.
        return _deny(503, "entitlement_unavailable", detail=str(exc))

    if not rights["unlimited"] and rights["remaining"] <= 0:
        return _deny(402, "daily_limit_reached", used=rights["used"],
                     limit=FREE_RUNS_PER_DAY,
                     upgrade=os.environ.get("GUMROAD_URL",
                                            "https://4175358678144.gumroad.com/l/tbphpi"))

    # Mint, then record the jti. If the record fails the pass must not be
    # handed out — /api/consume would have no row to check for replay.
    try:
        pass_token, payload = mint_pass(email, None if rights["unlimited"] else rights["remaining"])
    except GateError as exc:
        return _deny(503, "gate_unavailable", detail=str(exc))

    try:
        sb.table("gate_passes").insert({
            "jti": payload["jti"],
            "subject": email,
            "expires_at": datetime.fromtimestamp(payload["exp"], timezone.utc).isoformat(),
        }).execute()
    except Exception as exc:
        print(f"[gate] could not record pass jti: {exc}")
        return _deny(503, "gate_unavailable", detail="pass could not be recorded")

    body: Dict[str, Any] = {
        "allowed": True,
        "unlimited": rights["unlimited"],
        "remaining": rights["remaining"],
        "expires_in": PASS_TTL_SECONDS,
        "pass": pass_token,
    }
    if SPACE_URL:
        body["url"] = f"{SPACE_URL}/?pass={pass_token}"
    response = JSONResponse(body)
    if refreshed is not None:
        # Hand the renewed session back so the next call does not have to
        # refresh again. httpOnly and Secure, same as /api/auth/* writes it.
        response.set_cookie(key=auth_core.SESSION_COOKIE,
                            value=auth_core.encode_session(refreshed),
                            max_age=7 * 24 * 3600, path="/", httponly=True,
                            secure=COOKIE_SECURE, samesite="lax")
    return response


# ═══════════════════════════════════════════════════════════
# POST /api/consume — spend a pass and move the counter
# ═══════════════════════════════════════════════════════════
@app.post("/api/consume")
async def consume(request: Request):
    try:
        payload_in = await request.json()
    except Exception:
        return _deny(400, "invalid_json")
    if not isinstance(payload_in, dict):
        return _deny(400, "invalid_json")

    pass_token = (payload_in.get("pass") or "").strip()
    feature = str(payload_in.get("feature") or "").strip().lower()
    run_id = str(payload_in.get("run_id") or "").strip() or None

    if feature and feature not in VALID_FEATURES:
        return _deny(400, "unknown_feature", valid=sorted(VALID_FEATURES))

    # 1. Signature + expiry. Fail closed on any malformation.
    try:
        claims = verify_pass(pass_token)
    except GateError as exc:
        return _deny(403, "invalid_pass", detail=str(exc))

    subject = claims["sub"]
    jti = claims["jti"]

    try:
        sb = _supabase()
    except GateError as exc:
        return _deny(503, "gate_unavailable", detail=str(exc))

    # 2. Spend the jti exactly once. The conditional update is the replay
    #    guard: two requests racing on the same pass both match the row, but
    #    only one sees consumed_at IS NULL after the other commits, so the
    #    loser updates zero rows. An unknown jti also updates zero rows, which
    #    is the correct answer — a pass Vercel never issued is not spendable.
    try:
        updated = (sb.table("gate_passes")
                   .update({"consumed_at": datetime.now(timezone.utc).isoformat(),
                            "consumed_by_run": run_id,
                            "feature": feature or None})
                   .eq("jti", jti)
                   .is_("consumed_at", "null")
                   .execute())
    except Exception as exc:
        print(f"[gate] replay check failed for jti={jti}: {exc}")
        return _deny(503, "gate_unavailable", detail="replay check failed")

    if not (updated.data or []):
        return _deny(409, "pass_already_used", jti=jti)

    # 3. Unlimited identities stop here: the pass was still spent, so a leaked
    #    admin pass cannot be replayed, but no counter moves.
    if claims.get("runs") is None:
        return JSONResponse({"allowed": True, "unlimited": True, "remaining": None,
                             "feature": feature, "run_id": run_id})

    # 4. Enforce and increment in one atomic statement.
    try:
        result = sb.rpc("try_consume_run", {
            "p_subject": subject,
            "p_day": utc_today(),
            "p_limit": FREE_RUNS_PER_DAY,
        }).execute()
    except Exception as exc:
        print(f"[gate] try_consume_run failed for {subject}: {exc}")
        return _deny(503, "gate_unavailable", detail="meter unavailable")

    # PostgREST returns a bare object for a function with OUT parameters, but
    # supabase-py types .data as List[_ReturnT] and some deployments hand back
    # a one-element list. Accept both rather than silently denying a run that
    # the database actually granted.
    raw = result.data
    if isinstance(raw, list):
        raw = raw[0] if raw else {}
    data = raw if isinstance(raw, dict) else {}
    allowed = bool(data.get("p_allowed"))
    count = data.get("p_count")
    remaining = None
    if isinstance(count, int):
        remaining = max(0, FREE_RUNS_PER_DAY - count)

    if not allowed:
        return _deny(402, "daily_limit_reached", used=count, limit=FREE_RUNS_PER_DAY,
                     upgrade=os.environ.get("GUMROAD_URL",
                                            "https://4175358678144.gumroad.com/l/tbphpi"))

    return JSONResponse({"allowed": True, "unlimited": False, "remaining": remaining,
                         "used": count, "feature": feature, "run_id": run_id})


# ═══════════════════════════════════════════════════════════
# Health — must not require a secret, so it can be polled by the Space
# to tell "gate down" apart from "gate refusing me".
# ═══════════════════════════════════════════════════════════
@app.get("/api/gate/health")
async def health():
    configured = {"supabase": bool(os.environ.get("SUPABASE_URL", "").strip()),
                  "service_key": bool(os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()),
                  "space_url": bool(SPACE_URL)}
    try:
        gate_secret()
        configured["gate_secret"] = True
    except GateError:
        configured["gate_secret"] = False
    return {"status": "ok", "free_runs_per_day": FREE_RUNS_PER_DAY,
            "pass_ttl_seconds": PASS_TTL_SECONDS, "configured": configured}
