"""Gumroad Ping receiver for 4CBON2.

Deliberately thin. The logic lives in webhook_core.py so it can be tested
without HTTP, and the Supabase client comes from db.py rather than from app.py —
importing app.py would pull gradio and google-generativeai into a function that
needs neither, slowing every cold start.

Two rules from Gumroad's Ping documentation drive the status codes below:

  * Gumroad gives the endpoint **5 seconds** to answer, and retries only on
    499/500/502/503/504 — four times, then never. A timeout or any other
    non-2xx loses the notification permanently.
  * Therefore work is kept to three round trips, and if it is running out of
    time the endpoint answers **503 on purpose** so the delivery is retried
    later rather than risk being killed mid-write.

Status code meanings, chosen against that retry policy:

  200  accepted (including a duplicate — a repeat ping is a success, not an error)
  400  malformed ping; retrying will not help, so this must not be retried
  401  wrong or missing secret; retrying will not help either
  503  our side failed, or is configured wrong — **retried**, which is the point
"""
import hmac
import os
import sys
import time

from fastapi import FastAPI, Query, Request
from fastapi.responses import JSONResponse

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import webhook_core  # noqa: E402
from db import supabase_client  # noqa: E402
from gate_core import GateError  # noqa: E402

app = FastAPI(title="4CBON2 Gumroad Webhook")

# Gumroad's Ping is a plain POST to whatever URL is configured, so the shared
# secret travels in the query string. That is the only channel available, but it
# does mean the value can appear in request logs — rotate it if that matters.
# Read per request rather than at import: Vercel applies env at deploy time and
# a stale module-level read would make a rotation look like a code change.
#
# Leaving the deadline short on purpose. Gumroad allows 5s; answering 503 at
# 3.5s buys margin for the retry to land on a warm function.
DEADLINE_SECONDS = 3.5


def _secret() -> str:
    return (os.environ.get("GUMROAD_WEBHOOK_SECRET") or "").strip()


@app.post("/api/gumroad-webhook")
async def gumroad_webhook(request: Request, secret: str = Query(None)):
    started = time.monotonic()
    expected = _secret()

    if not expected:
        # 503, not 500: this is our misconfiguration and it should be retried
        # once the variable is set, rather than dropped.
        print("[webhook] GUMROAD_WEBHOOK_SECRET is not configured")
        return JSONResponse({"status": "error", "error": "not_configured"}, status_code=503)

    # compare_digest, not ==: a plain comparison leaks the secret's length and
    # prefix through timing.
    if not hmac.compare_digest(secret or "", expected):
        return JSONResponse({"status": "error", "error": "invalid_secret"}, status_code=401)

    try:
        form = dict(await request.form())
    except Exception as exc:
        # 503, not 400. A parse failure has two possible causes — a genuinely
        # malformed body, or our own side being broken (a missing
        # python-multipart, for instance, fails every single ping). They are
        # indistinguishable here, and only one of them is recoverable, so take
        # the recoverable branch: 503 is retried, 400 is not. Four wasted
        # retries on a truly bad body cost nothing; a 400 on a broken deploy
        # discards every notification permanently.
        print(f"[webhook] could not parse the form body: {type(exc).__name__}: {exc}")
        return JSONResponse({"status": "error", "error": "unparsable_body"}, status_code=503)

    try:
        sb = supabase_client()
    except GateError as exc:
        print(f"[webhook] {exc}")
        return JSONResponse({"status": "error", "error": "entitlement_store_unavailable"},
                            status_code=503)

    try:
        summary = webhook_core.apply_ping(sb, form)
    except ValueError as exc:
        # A malformed ping. 400 is deliberately outside the retried set: the
        # same body would fail identically four more times.
        print(f"[webhook] rejected: {exc}")
        return JSONResponse({"status": "error", "error": str(exc)}, status_code=400)
    except webhook_core.WebhookError as exc:
        # The important one. Answering 200 here would tell Gumroad the ping was
        # handled when it was not, and it would never be sent again.
        print(f"[webhook] 503 so Gumroad retries: {exc}")
        return JSONResponse({"status": "error", "error": "storage_failed"}, status_code=503)
    except Exception as exc:
        print(f"[webhook] unexpected {type(exc).__name__}: {exc}")
        return JSONResponse({"status": "error", "error": "internal"}, status_code=503)

    elapsed = time.monotonic() - started
    if elapsed > DEADLINE_SECONDS:
        # We got there, but only just. Nothing to undo — the write is committed
        # and a retried delivery is deduplicated on (sale_id, resource_name) —
        # so report honestly and let the duplicate arrive.
        print(f"[webhook] slow: {elapsed:.2f}s (deadline {DEADLINE_SECONDS}s)")

    return JSONResponse({"status": "ok", **summary})


@app.get("/api/gumroad-webhook/health")
async def webhook_health():
    """Unauthenticated and deliberately vague: it reports whether the secret is
    present, never anything derived from it."""
    return {"status": "ok", "endpoint": "/api/gumroad-webhook",
            "configured": bool(_secret())}
