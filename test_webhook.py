#!/usr/bin/env python3
"""Tests for the Gumroad Ping path: webhook_core + api/gumroad-webhook.py.

The cases below are not generic coverage — each one corresponds to a property
Gumroad's own Ping documentation states, or to a defect found in the previous
implementation. The two that matter most:

  * a refund must set status='cancelled'. The old code computed status by
    asking whether the string "subscription_cancelled" appeared in the repr of
    the whole payload, which is never true for a real Gumroad ping, so a refund
    wrote status='active' and kept a refunded customer on unlimited forever.

  * a refund that arrives BEFORE its sale must still result in 'cancelled'.
    Status is derived from the set of recorded pings, not from arrival order.

The database is an in-memory fake that mirrors the real semantics the code
depends on: a composite primary key that makes upsert-with-ignore_duplicates
return no rows on a repeat, and an upsert keyed on email.

Usage: /tmp/gateenv/bin/python test_webhook.py
"""
import importlib.util
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ROOT, "vercel"))
sys.path.insert(0, os.path.join(ROOT, "vercel", "api"))

os.environ.setdefault("SUPABASE_URL", "https://example.supabase.co")
os.environ.setdefault("SUPABASE_SERVICE_ROLE_KEY", "service-role-key-for-tests")
os.environ["GUMROAD_WEBHOOK_SECRET"] = "correct-horse-battery-staple"
os.environ["COOKIE_SECURE"] = "false"

import webhook_core  # noqa: E402
from webhook_core import apply_ping, parse_ping, resolve_status  # noqa: E402

spec = importlib.util.spec_from_file_location(
    "wh", os.path.join(ROOT, "vercel", "api", "gumroad-webhook.py"))
wh = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wh)

from fastapi.testclient import TestClient  # noqa: E402

CHECKS = 0
FAILURES = []


def check(label, condition, detail=""):
    global CHECKS
    CHECKS += 1
    print(f"[{'PASS' if condition else 'FAIL'}] {label}" + (f" — {detail}" if detail else ""))
    if not condition:
        FAILURES.append(label)


class FakeSupabase:
    """Mirrors only the semantics this code relies on."""

    def __init__(self):
        self.pings = []
        self.subs = {}
        # gate_core.entitlement() also reads admin_emails and run_usage. They
        # must be distinct, empty tables: falling back to `subs` for any
        # unknown table would make every buyer look like an admin and mask
        # exactly the refund behaviour under test.
        self.others = {"admin_emails": [], "run_usage": []}

    def table(self, name):
        outer = self

        class Result:
            def __init__(self, data):
                self.data = data

        class Q:
            def __init__(self):
                self.filters = []
                self.cols = "*"

            def select(self, cols="*"):
                self.cols = cols
                return self

            def eq(self, col, val):
                self.filters.append((col, val))
                return self

            def _rows(self):
                if name == "gumroad_pings":
                    return outer.pings
                if name == "subscriptions":
                    return list(outer.subs.values())
                return outer.others[name]      # KeyError on an unmodelled table

            def upsert(self, payload, on_conflict="", ignore_duplicates=False):
                """Returns a chainable builder, like postgrest does — the real
                API is .upsert(...).execute(), not .upsert(...)."""
                rows = self._rows()
                keys = [k.strip() for k in on_conflict.split(",") if k.strip()]
                data = None
                for row in rows:
                    if all(row.get(k) == payload.get(k) for k in keys):
                        if ignore_duplicates:
                            data = []                  # ON CONFLICT DO NOTHING
                        else:
                            row.update(payload)
                            data = [dict(row)]
                        break
                if data is None:
                    rows.append(dict(payload))
                    if name == "subscriptions":
                        outer.subs[payload["email"]] = dict(payload)
                    data = [dict(payload)]
                result = Result(data)
                return type("Builder", (), {"execute": staticmethod(lambda: result)})()

            def execute(self):
                out = [dict(r) for r in self._rows()]
                for col, val in self.filters:
                    out = [r for r in out if r.get(col) == val]
                if self.cols == "*":
                    return Result(out)
                keys = [c.strip() for c in self.cols.split(",")]
                return Result([{k: r.get(k) for k in keys} for r in out])

        return Q()


def sale(sale_id="S1", email="buyer@example.com", **kw):
    base = {"sale_id": sale_id, "resource_name": "sale", "email": email,
            "subscription_id": "sub_1", "product_name": "4CBON Pro",
            "sale_timestamp": "2026-06-02T10:00:00Z", "recurrence": "monthly"}
    base.update(kw)
    return base


def refund(sale_id="S1", email="buyer@example.com", **kw):
    base = {"sale_id": sale_id, "resource_name": "refund", "email": email,
            "subscription_id": "sub_1", "product_name": "4CBON Pro",
            "sale_timestamp": "2026-06-02T10:00:00Z", "refunded": "true"}
    base.update(kw)
    return base


print("=" * 74)
print("parse_ping — Gumroad sends every value as a string")
print("=" * 74)
p = parse_ping(refund())
check("refunded arrives as text and is stored as text", p["refunded"] == "true", repr(p["refunded"]))
check("resource_name is read from the payload", p["resource_name"] == "refund", p["resource_name"])
check("email is lowercased", parse_ping(sale(email="Buyer@Example.COM"))["email"] == "buyer@example.com")
check("a ping with no resource_name but refunded=true is treated as a refund",
      parse_ping({"sale_id": "S1", "email": "a@b.c", "refunded": "true"})["resource_name"] == "refund")
check("a ping with neither is treated as a sale",
      parse_ping({"sale_id": "S1", "email": "a@b.c"})["resource_name"] == "sale")
check("test='true' parses as a boolean", parse_ping(sale(test="true"))["is_test"] is True)
check("test absent parses as False", parse_ping(sale())["is_test"] is False)
check("retry_count is an integer when present", parse_ping(sale(retry_count="2"))["retry_count"] == 2)
check("retry_count is None when absent", parse_ping(sale())["retry_count"] is None)

print("\n" + "=" * 74)
print("resolve_status — order-independent by construction")
print("=" * 74)
db = FakeSupabase()
check("no pings at all resolves to no status", resolve_status(db.pings)["status"] is None)

apply_ping(db, sale())
check("a sale ping grants 'active'", db.subs["buyer@example.com"]["status"] == "active")

apply_ping(db, refund())
check("a refund revokes it — the defect this rewrite exists to fix",
      db.subs["buyer@example.com"]["status"] == "cancelled",
      db.subs["buyer@example.com"]["status"])

# The same sale_id carries both pings; only resource_name tells them apart.
check("both pings are recorded under one sale_id",
      len(db.pings) == 2 and {r["resource_name"] for r in db.pings} == {"sale", "refund"},
      str([(r["sale_id"], r["resource_name"]) for r in db.pings]))

print("\n" + "=" * 74)
print("At-least-once delivery — deduplicate on (sale_id, resource_name)")
print("=" * 74)
db2 = FakeSupabase()
first = apply_ping(db2, sale(sale_id="S9"))
second = apply_ping(db2, sale(sale_id="S9"))
check("the first delivery is recorded as new", first["recorded"] is True)
check("a repeat delivery is recognised as a duplicate", second["duplicate"] is True)
check("...and is not stored twice", len(db2.pings) == 1, f"{len(db2.pings)} rows")
check("the entitlement is unchanged by the repeat",
      db2.subs["buyer@example.com"]["status"] == "active")

print("\n" + "=" * 74)
print("Ordering — a refund can arrive before the sale it reverses")
print("=" * 74)
db3 = FakeSupabase()
apply_ping(db3, refund(sale_id="S5"))
check("a refund with no sale yet still resolves to 'cancelled'",
      db3.subs["buyer@example.com"]["status"] == "cancelled",
      db3.subs["buyer@example.com"]["status"])
apply_ping(db3, sale(sale_id="S5"))
check("the sale arriving afterwards does NOT flip it back to active",
      db3.subs["buyer@example.com"]["status"] == "cancelled",
      db3.subs["buyer@example.com"]["status"])

db4 = FakeSupabase()
apply_ping(db4, sale(sale_id="S6", sale_timestamp="2026-06-02T10:00:00Z"))
apply_ping(db4, refund(sale_id="S6", sale_timestamp="2026-06-02T10:00:00Z"))
apply_ping(db4, sale(sale_id="S7", sale_timestamp="2026-07-02T10:00:00Z"))
check("a later renewal re-grants access",
      db4.subs["buyer@example.com"]["status"] == "active"
      and db4.subs["buyer@example.com"]["sale_id"] == "S7",
      str(db4.subs["buyer@example.com"]["sale_id"]))
apply_ping(db4, refund(sale_id="S6", sale_timestamp="2026-06-02T10:00:00Z", email="buyer@example.com"))
check("a re-delivered refund for the OLD sale does not revoke the renewal",
      db4.subs["buyer@example.com"]["status"] == "active",
      db4.subs["buyer@example.com"]["status"])

print("\n" + "=" * 74)
print("Malformed pings")
print("=" * 74)
db5 = FakeSupabase()
try:
    apply_ping(db5, {"email": "nobody@example.com"})
    check("a ping with no sale_id is refused", False, "no error raised")
except ValueError as exc:
    check("a ping with no sale_id is refused", "sale_id" in str(exc), str(exc))
check("...and nothing was written", not db5.pings and not db5.subs)

db6 = FakeSupabase()
result = apply_ping(db6, sale(sale_id="S8", email=""))
check("a ping with no email is recorded for audit but applied to nobody",
      result["applied"] is False and len(db6.pings) == 1, str(result))

print("\n" + "=" * 74)
print("Endpoint — status codes chosen against Gumroad's retry policy")
print("=" * 74)
good = TestClient(wh.app)


def with_db(db):
    """Point the endpoint at a fake instead of a real Supabase project."""
    wh.supabase_client = lambda: db


with_db(FakeSupabase())
r = good.post("/api/gumroad-webhook?secret=correct-horse-battery-staple",
              data=sale(sale_id="E1"))
check("a valid ping is acknowledged 200", r.status_code == 200, f"{r.status_code} {r.text[:80]}")
check("...and reports the granted status", r.json().get("status") == "active", r.text[:80])

r = good.post("/api/gumroad-webhook?secret=wrong", data=sale(sale_id="E2"))
check("a wrong secret is 401 — and 401 is deliberately NOT retried",
      r.status_code == 401, f"{r.status_code}")

r = good.post("/api/gumroad-webhook", data=sale(sale_id="E3"))
check("a missing secret is 401", r.status_code == 401, f"{r.status_code}")

r = good.post("/api/gumroad-webhook?secret=correct-horse-battery-staple",
              data={"email": "x@example.com"})
check("a malformed ping is 400 — retrying would not help", r.status_code == 400, f"{r.status_code}")


class ExplodingSupabase(FakeSupabase):
    def table(self, name):
        raise RuntimeError("connection reset by peer")


with_db(ExplodingSupabase())
r = good.post("/api/gumroad-webhook?secret=correct-horse-battery-staple",
              data=sale(sale_id="E4"))
check("a database failure is 503 so Gumroad RETRIES it",
      r.status_code == 503, f"{r.status_code} {r.text[:60]}")
check("...specifically 503, not 200 — answering 200 would lose the ping forever",
      r.status_code != 200, f"{r.status_code}")

# A server-side parse failure is indistinguishable from a bad body here, and
# only one of the two is recoverable — so it must take the retried branch.
from starlette.requests import Request as _SR
_original_form = _SR.form


async def _broken_form(self):
    raise RuntimeError("The `python-multipart` library must be installed")


_SR.form = _broken_form
try:
    r = good.post("/api/gumroad-webhook?secret=correct-horse-battery-staple",
                  data=sale(sale_id="E9"))
    check("a server-side parse failure is 503, so a broken deploy does not "
          "silently discard every ping", r.status_code == 503, f"{r.status_code} {r.text[:60]}")
finally:
    _SR.form = _original_form

saved = os.environ.pop("GUMROAD_WEBHOOK_SECRET")
try:
    r = good.post("/api/gumroad-webhook?secret=anything", data=sale(sale_id="E5"))
    check("an unconfigured secret is 503, not 500 or 401", r.status_code == 503, f"{r.status_code}")
finally:
    os.environ["GUMROAD_WEBHOOK_SECRET"] = saved

r = good.get("/api/gumroad-webhook/health")
check("health needs no secret", r.status_code == 200, f"{r.status_code}")
check("health reports configuration without leaking the secret",
      r.json().get("configured") is True and saved not in r.text, r.text[:80])

print("\n" + "=" * 74)
print("End to end: a refund actually costs the customer their entitlement")
print("=" * 74)
import gate_core  # noqa: E402

db7 = FakeSupabase()
with_db(db7)
good.post("/api/gumroad-webhook?secret=correct-horse-battery-staple", data=sale(sale_id="E6"))
check("after a sale ping is_active_subscriber() is True",
      gate_core.is_active_subscriber("buyer@example.com", db7) is True)
check("...so entitlement() is unlimited",
      gate_core.entitlement("buyer@example.com", db7)["unlimited"] is True)

good.post("/api/gumroad-webhook?secret=correct-horse-battery-staple", data=refund(sale_id="E6"))
check("after a refund ping is_active_subscriber() is False",
      gate_core.is_active_subscriber("buyer@example.com", db7) is False)
rights = gate_core.entitlement("buyer@example.com", db7)
check("...so they are back on the free tier with 3 runs",
      rights["unlimited"] is False and rights["remaining"] == 3, str(rights))

print("\n" + "=" * 74)
print("reconcile_subscribers.decide() — the API read-back is the authority")
print("=" * 74)
spec2 = importlib.util.spec_from_file_location(
    "rec", os.path.join(ROOT, "tools", "reconcile_subscribers.py"))
rec = importlib.util.module_from_spec(spec2)
spec2.loader.exec_module(rec)

live = {"id": "S1", "email": "payer@example.com", "subscription_id": "sub_1",
        "product_name": "4CBON Pro", "sale_timestamp": "2026-09-03T10:00:00Z",
        "refunded": False, "subscription_cancelled_at": None}
out = rec.decide([live])
check("an active subscription resolves to 'active' — the real paying customer's shape",
      out["payer@example.com"]["status"] == "active", str(out.get("payer@example.com")))

out = rec.decide([dict(live, subscription_cancelled_at="2026-09-05T00:00:00Z")])
check("a cancelled subscription resolves to 'cancelled'",
      out["payer@example.com"]["status"] == "cancelled")

out = rec.decide([dict(live, refunded=True)])
check("a refunded sale resolves to 'cancelled'",
      out["payer@example.com"]["status"] == "cancelled")

out = rec.decide([dict(live, subscription_id=None)])
check("a one-off purchase of a monthly product does NOT grant unlimited",
      out["payer@example.com"]["status"] == "cancelled",
      "a one-off is not an open-ended entitlement")

out = rec.decide([dict(live, email="Payer@Example.COM")])
check("emails are lowercased, so they match the account identity",
      "payer@example.com" in out)

out = rec.decide([
    dict(live, id="S1", sale_timestamp="2026-09-03T10:00:00Z",
         subscription_cancelled_at="2026-09-05T00:00:00Z"),
    dict(live, id="S2", sale_timestamp="2026-10-03T10:00:00Z", subscription_cancelled_at=None),
])
check("a later renewal wins over an older cancellation",
      out["payer@example.com"]["status"] == "active", out["payer@example.com"]["status"])

out = rec.decide([
    dict(live, id="S1", sale_timestamp="2026-10-03T10:00:00Z", subscription_cancelled_at=None),
    dict(live, id="S2", sale_timestamp="2026-10-03T10:00:00Z",
         subscription_cancelled_at="2026-10-04T00:00:00Z"),
])
check("on an exact timestamp tie the active sale wins (the safer error)",
      out["payer@example.com"]["status"] == "active", out["payer@example.com"]["status"])

out = rec.decide([{"id": "S1", "email": "", "subscription_id": "sub_1"}])
check("a sale with no email is skipped rather than crashing", out == {}, str(out))

print("\n" + "=" * 74)
print(f"ran {CHECKS} checks")
print(f"RESULT: {len(FAILURES)} failure(s)" if FAILURES else "RESULT: all checks passed")
for f in FAILURES:
    print("  ✗", f)
print("=" * 74)
sys.exit(1 if FAILURES else 0)
