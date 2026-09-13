#!/usr/bin/env python3
"""Reconcile `subscriptions` against Gumroad's own API.

Why this exists at all
----------------------
The Gumroad Ping endpoint was never configured, so no ping has ever arrived and
the `subscriptions` table is empty — even though 4CBON Pro has a real paying
customer who has renewed monthly since June. That customer's next renewal is
weeks away, so relying on the webhook alone would put the project's only paying
customer on the free tier the moment enforcement went live, and they would hit
the three-run wall without ever being told why.

This tool closes that gap by reading the sales back through the Gumroad API and
writing the answer directly. It is also the authoritative record going forward:
Gumroad's own documentation says a Ping payload is unsigned and that a delivery
can be dropped once the four retries run out, so the ping is a trigger and this
is the source of truth. Run it after deploy, then on a schedule.

Usage
-----
    export GUMROAD_ACCESS_TOKEN=...          # Gumroad -> Settings -> Advanced -> Applications
    export SUPABASE_URL=...
    export SUPABASE_SERVICE_ROLE_KEY=...
    python3 tools/reconcile_subscribers.py                 # dry run, prints the diff
    python3 tools/reconcile_subscribers.py --apply         # write it
    python3 tools/reconcile_subscribers.py --apply --product-permalink <permalink>

The decision logic is in `decide()` and is deliberately free of network and
database calls so it can be unit-tested; the HTTP and Supabase work is bolted
around it.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from typing import Any, Dict, List

API_ROOT = "https://api.gumroad.com"
API_BASE = f"{API_ROOT}/v2"
TIMEOUT = 20
# Ten sales per page, so this is ~5,000 sales. A hard stop beats an
# infinite loop if the cursor ever fails to terminate.
MAX_PAGES = 500


class ReconcileError(Exception):
    pass


# ── the decision, in one testable function ────────────────────────
# /v2/sales returns `created_at`; the Ping and license-verify payloads use
# `sale_timestamp`. Accept either rather than guessing which endpoint we are
# reading — reading the wrong one silently makes every sale tie on "".
TIMESTAMP_FIELDS = ("created_at", "sale_timestamp")

# Three separate ways a subscription stops, and all three mean "not paying".
# Checking only the first was a real defect: a membership that ended on its own,
# or whose card failed to renew, would have been granted unlimited runs.
ENDED_FIELDS = ("subscription_cancelled_at",   # cancelled by customer or creator
                "subscription_ended_at",       # fixed-length membership ran out
                "subscription_failed_at")      # renewal failed, usually a card error


def _timestamp(sale: Dict[str, Any]) -> str:
    for field in TIMESTAMP_FIELDS:
        value = sale.get(field)
        if value:
            return str(value)
    return ""


def decide(sales: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    """Turn a list of Gumroad sales into {email: entitlement}.

    An email is an active subscriber when it has a sale that is all of:

      * a subscription (carries a `subscription_id`) — a one-off purchase of a
        monthly product is not an open-ended entitlement;
      * not refunded and not charged back;
      * not disputed, unless the dispute was won;
      * not ended by any of the three subscription-end timestamps.

    Everything else resolves to `cancelled`, which `is_active_subscriber()`
    treats as the free tier. An email is written once, keyed on the newest
    qualifying sale, so repeated runs are stable.
    """
    out: Dict[str, Dict[str, Any]] = {}
    for sale in sales:
        email = str(sale.get("email") or "").strip().lower()
        if not email:
            continue

        is_subscription = bool(sale.get("subscription_id"))
        refunded = _truthy(sale.get("refunded"))
        # Both spellings, deliberately. Gumroad's own docs and client libraries
        # do not agree on whether this field is "chargedback" or "chargebacked",
        # and the two differ by a single letter. Guessing wrong silently
        # disables a control that decides whether someone pays — so accept
        # either rather than pick one.
        chargebacked = _truthy(sale.get("chargedback")) or _truthy(sale.get("chargebacked"))
        disputed = _truthy(sale.get("disputed")) and not _truthy(sale.get("dispute_won"))
        ended = any(sale.get(f) for f in ENDED_FIELDS)

        active = bool(is_subscription and not refunded and not chargebacked
                      and not disputed and not ended)
        stamp = _timestamp(sale)
        status = "active" if active else "cancelled"

        current = out.get(email)
        # A later sale wins. On an exact tie prefer the active one: downgrading
        # a paying customer is the worse of the two errors.
        if current is not None:
            if stamp < current["sale_timestamp"]:
                continue
            if stamp == current["sale_timestamp"] and current["status"] == "active":
                continue

        out[email] = {
            "email": email,
            "status": status,
            "sale_id": str(sale.get("id") or sale.get("sale_id") or ""),
            "subscription_id": str(sale.get("subscription_id") or "") or None,
            "product_name": sale.get("product_name"),
            "sale_timestamp": stamp or None,
        }
    return out


def _truthy(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in ("true", "1", "yes")


# ── Gumroad API ───────────────────────────────────────────────────
def fetch_sales(access_token: str, product_permalink: str = "") -> List[Dict[str, Any]]:
    """Read every sale through the API.

    Gumroad paginates `/v2/sales` with a cursor: the response carries
    `next_page_url` (a path like `/v2/sales?page_key=...`) which has to be
    followed, and results come back ten at a time. The older `page=N` parameter
    is deprecated. An earlier version of this function incremented `page` and
    looked for a `next_url` key that does not exist, so it always stopped after
    the first ten sales — invisible on a small account and silently truncating
    on a large one.
    """
    params = {"access_token": access_token}
    if product_permalink:
        params["product_permalink"] = product_permalink
    url = f"{API_BASE}/sales?{urllib.parse.urlencode(params)}"

    sales: List[Dict[str, Any]] = []
    pages = 0
    while url:
        pages += 1
        if pages > MAX_PAGES:
            raise ReconcileError(
                f"stopped after {MAX_PAGES} pages; the cursor never ended. "
                "Nothing was written — this is a bug, not a data condition.")

        request = urllib.request.Request(url, headers={"Accept": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise ReconcileError(f"Gumroad API returned {exc.code}") from exc
        except urllib.error.URLError as exc:
            raise ReconcileError(f"could not reach the Gumroad API: {exc.reason}") from exc

        if not payload.get("success", True):
            raise ReconcileError(f"Gumroad API refused the request: {payload.get('message')}")

        batch = payload.get("sales") or []
        sales.extend(batch)

        # next_page_url is a path, not an absolute URL.
        nxt = payload.get("next_page_url") or payload.get("next_url") or ""
        if not batch or not nxt:
            break
        url = nxt if nxt.startswith("http") else f"{API_ROOT}{nxt}"
    return sales


# ── Supabase ──────────────────────────────────────────────────────
def load_current(sb) -> Dict[str, str]:
    try:
        rows = sb.table("subscriptions").select("email,status").execute().data or []
    except Exception as exc:
        raise ReconcileError(f"could not read subscriptions: {type(exc).__name__}: {exc}") from exc
    return {str(r.get("email") or "").lower(): str(r.get("status") or "") for r in rows}


def write(sb, decision: Dict[str, Any], sale: Dict[str, Any]) -> None:
    try:
        sb.table("subscriptions").upsert({
            "email": decision["email"],
            "subscription_id": sale.get("subscription_id"),
            "product_name": sale.get("product_name"),
            "status": sale["status"],
            "sale_id": sale["sale_id"],
            "sale_timestamp": sale["sale_timestamp"],
            # 'api' marks the row as confirmed against Gumroad rather than
            # taken from an unsigned ping.
            "source": "api",
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }, on_conflict="email").execute()
    except Exception as exc:
        raise ReconcileError(f"could not write {decision}: {type(exc).__name__}: {exc}") from exc


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--apply", action="store_true",
                        help="write the result; without this the tool only prints the diff")
    parser.add_argument("--product-permalink", default=os.environ.get("GUMROAD_PRODUCT_PERMALINK", ""),
                        help="limit to one product (defaults to every sale on the account)")
    args = parser.parse_args()

    token = (os.environ.get("GUMROAD_ACCESS_TOKEN") or "").strip()
    if not token:
        print("GUMROAD_ACCESS_TOKEN is not set — nothing to reconcile against.")
        return 2

    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "vercel"))
    from db import supabase_client  # noqa: E402
    from gate_core import GateError  # noqa: E402

    try:
        sales = fetch_sales(token, args.product_permalink)
    except ReconcileError as exc:
        print(f"FAILED: {exc}")
        return 1

    truth = decide(sales)
    print(f"read {len(sales)} sale(s) from Gumroad -> {len(truth)} distinct email(s)")

    try:
        sb = supabase_client()
        current = load_current(sb)
    except (ReconcileError, GateError) as exc:
        print(f"FAILED: {exc}")
        return 1

    changes = []
    for email, sale in sorted(truth.items()):
        if current.get(email) != sale["status"]:
            changes.append((email, current.get(email, "(absent)"), sale["status"]))

    if not changes:
        print("nothing to change — the table already agrees with Gumroad.")
        return 0

    print(f"{len(changes)} row(s) differ:")
    for email, was, now in changes:
        print(f"  {email}: {was} -> {now}")

    if not args.apply:
        print("\ndry run only. Re-run with --apply to write these.")
        return 0

    for email, _was, _now in changes:
        write(sb, {"email": email}, truth[email])
    print(f"\nwrote {len(changes)} row(s) with source='api'.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
