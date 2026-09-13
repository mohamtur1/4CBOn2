"""Gumroad Ping handling for 4CBON2.

Gumroad's own Ping documentation states four properties that shape this module.
Each one is handled explicitly, because the previous version of the webhook
handled none of them:

  1. **Delivery is at-least-once.** The same ping can arrive more than once, so
     every ping is recorded against a primary key of ``(sale_id,
     resource_name)`` and a repeat is recognised as a repeat.

  2. **Ordering is not guaranteed.** A refund ping can arrive *before* the sale
     ping it reverses. Status is therefore never derived from arrival order: it
     is derived from the *set* of pings recorded for an account. "Does a refund
     exist for this sale_id?" has the same answer no matter which ping landed
     first, so a late sale ping cannot silently undo a refund.

  3. **A refund carries the same `sale_id` as the sale it reverses.** The two
     are distinguished only by `resource_name`. Deduplicating on `sale_id`
     alone would swallow the refund, so the dedupe key includes it.

  4. **Only 499/500/502/503/504 are retried** (at 1 min, 3 min, 10 min, 1 hour,
     then never). Any other non-2xx, a timeout, or a connection error is
     dropped for good. A database failure must therefore surface as 503 —
     answering 200 to hide an error loses the notification permanently.

The payload is unsigned, so `?secret=` authenticates the *caller* but not the
*contents*. The ping is treated as a trigger: it grants access promptly because
a customer who just paid should not wait, while `tools/reconcile_subscribers.py`
reads sales back through the Gumroad API and is the authoritative record.
`subscriptions.source` records which of the two last wrote the row.
"""
from datetime import datetime, timezone
from typing import Any, Dict, Optional


class WebhookError(Exception):
    """The database could not be reached or refused the write.

    Callers must answer 503 so Gumroad retries. Answering 200 here would
    acknowledge a ping whose effects were never written, and Gumroad would
    never send it again.
    """


# Gumroad sends resource_name='sale' for a purchase and 'refund' when that same
# purchase is later refunded. Anything else is recorded but changes nothing.
SALE = "sale"
REFUND = "refund"


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_ping(form: Dict[str, Any]) -> Dict[str, Any]:
    """Normalise a form-encoded Ping body.

    Gumroad sends everything as a string, including the booleans, so `refunded`
    arrives as the text "true"/"false" and `test` the same way.
    """
    def text(key: str) -> str:
        value = form.get(key)
        return str(value).strip() if value is not None else ""

    def flag(key: str) -> bool:
        return text(key).strip().lower() in ("true", "1", "yes")

    sale_id = text("sale_id")
    resource = text("resource_name").lower() or (REFUND if flag("refunded") else SALE)
    retry_raw = text("retry_count")
    try:
        retry_count: Optional[int] = int(retry_raw) if retry_raw else None
    except ValueError:
        retry_count = None

    return {
        "sale_id": sale_id,
        "resource_name": resource,
        "sale_timestamp": text("sale_timestamp"),
        "email": text("email").lower(),
        "subscription_id": text("subscription_id"),
        "product_name": text("product_name"),
        "recurrence": text("recurrence"),
        "refunded": text("refunded"),
        "is_test": flag("test"),
        "retry_count": retry_count,
    }


def record_ping(sb, ping: Dict[str, Any]) -> bool:
    """Append a ping to the log. True if it is new, False if already seen.

    `ignore_duplicates` with the composite conflict target is the database-side
    half of the dedupe guarantee: two simultaneous retries of the same ping
    cannot both be recorded as new.
    """
    try:
        result = sb.table("gumroad_pings").upsert(
            {
                "sale_id": ping["sale_id"],
                "resource_name": ping["resource_name"],
                "sale_timestamp": ping["sale_timestamp"] or None,
                "email": ping["email"] or None,
                "subscription_id": ping["subscription_id"] or None,
                "product_name": ping["product_name"] or None,
                "recurrence": ping["recurrence"] or None,
                "refunded": ping["refunded"] or None,
                "is_test": ping["is_test"],
                "retry_count": ping["retry_count"],
            },
            on_conflict="sale_id,resource_name",
            ignore_duplicates=True,
        ).execute()
    except Exception as exc:
        raise WebhookError(f"could not record the ping: {type(exc).__name__}: {exc}") from exc
    return bool(result.data)


def _select_pings(sb, email: str):
    try:
        return (sb.table("gumroad_pings")
                .select("sale_id,resource_name,sale_timestamp,received_at,email")
                .eq("email", email)
                .execute().data) or []
    except Exception as exc:
        raise WebhookError(f"could not read gumroad_pings: {type(exc).__name__}: {exc}") from exc


def resolve_status(pings) -> Dict[str, Any]:
    """Decide an account's status from the set of pings, ignoring arrival order.

    The newest sale_id wins, and that sale is refunded if *any* refund ping
    exists for it. Because this reads the whole set rather than the last event,
    a refund that arrived before its sale still produces 'cancelled', and a
    sale ping that arrives afterwards cannot flip it back to 'active'.
    """
    if not pings:
        return {"status": None, "sale_id": None, "sale_timestamp": None, "email": None}

    by_sale: Dict[str, Dict[str, Any]] = {}
    for row in pings:
        sale_id = row.get("sale_id")
        if not sale_id:
            continue
        entry = by_sale.setdefault(sale_id, {"sale_id": sale_id, "refunded": False,
                                             "sale_timestamp": "", "received_at": "",
                                             "email": None})
        if row.get("resource_name") == REFUND:
            entry["refunded"] = True
        stamp = row.get("sale_timestamp") or ""
        if stamp > entry["sale_timestamp"]:
            entry["sale_timestamp"] = stamp
        seen = row.get("received_at") or ""
        if seen > entry["received_at"]:
            entry["received_at"] = seen
        entry["email"] = entry["email"] or row.get("email")

    if not by_sale:
        return {"status": None, "sale_id": None, "sale_timestamp": None, "email": None}

    newest = max(by_sale.values(),
                 key=lambda e: (e["sale_timestamp"], e["received_at"], e["sale_id"]))
    return {
        "status": "cancelled" if newest["refunded"] else "active",
        "sale_id": newest["sale_id"],
        "sale_timestamp": newest["sale_timestamp"] or None,
        "email": newest["email"],
    }


def write_subscription(sb, email: str, decision: Dict[str, Any], source: str = "ping") -> None:
    try:
        sb.table("subscriptions").upsert({
            "email": email,
            "subscription_id": None,
            "product_name": None,
            "status": decision["status"],
            "sale_id": decision["sale_id"],
            "sale_timestamp": decision["sale_timestamp"],
            "source": source,
            "updated_at": _now(),
        }, on_conflict="email").execute()
    except Exception as exc:
        raise WebhookError(f"could not update subscriptions: {type(exc).__name__}: {exc}") from exc


def apply_ping(sb, form: Dict[str, Any]) -> Dict[str, Any]:
    """Record a ping and recompute the buyer's entitlement from the full set.

    Returns a summary for the response body and the logs.
    """
    ping = parse_ping(form)
    if not ping["sale_id"]:
        # Nothing to dedupe against and nothing to reconcile later. A malformed
        # ping will not improve on retry, so the caller answers 400, which
        # Gumroad deliberately does not retry.
        raise ValueError("ping is missing sale_id")

    is_new = record_ping(sb, ping)
    email = ping["email"]
    if not email:
        # No address to attach the entitlement to. Recorded for audit, so a
        # later ping or reconciliation can still pick it up.
        return {"recorded": is_new, "duplicate": not is_new, "applied": False,
                "reason": "no email on the ping"}

    decision = resolve_status(_select_pings(sb, email))
    if decision["status"] is None:
        return {"recorded": is_new, "duplicate": not is_new, "applied": False,
                "reason": "no sale_id could be resolved"}

    write_subscription(sb, email, decision, source="ping")
    return {
        "recorded": is_new,
        "duplicate": not is_new,
        "applied": True,
        "email": email,
        "status": decision["status"],
        "sale_id": decision["sale_id"],
        "is_test": ping["is_test"],
        "resource_name": ping["resource_name"],
    }
