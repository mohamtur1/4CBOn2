#!/usr/bin/env python3
"""Guard against the three copies of the access-control SQL drifting apart.

supabase/access_control.sql is canonical. It is embedded verbatim at the end of
supabase/4cbon2.sql and vercel/supabase.sql so either file can be pasted into
the Supabase SQL editor on its own. Duplication like that silently rots, so
this fails if the embedded copies stop matching the canonical one.

Usage: python3 test_sql_sync.py
"""
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
CANONICAL = os.path.join(ROOT, "supabase", "access_control.sql")
TARGETS = [
    os.path.join(ROOT, "supabase", "4cbon2.sql"),
    os.path.join(ROOT, "vercel", "supabase.sql"),
]

FAILURES = []


def check(label, condition, detail=""):
    print(f"[{'PASS' if condition else 'FAIL'}] {label}" + (f" — {detail}" if detail else ""))
    if not condition:
        FAILURES.append(label)


canonical = open(CANONICAL, encoding="utf-8").read()
check("canonical file exists and is non-trivial", len(canonical) > 1000,
      f"{len(canonical)} bytes")

# The statements the gate cannot work without. If one is edited out of the
# canonical file, embedding it verbatim everywhere is no help.
for required in ("create table if not exists run_usage",
                 "create or replace function bump_run_usage",
                 "create or replace function bump_run_limit",
                 "create or replace function try_consume_run",
                 "create table if not exists admin_emails",
                 "create table if not exists gate_passes",
                 "mohamtur1@gmail.com",
                 "revoke all on table subscriptions from public, anon, authenticated",
                 "revoke execute on function bump_run_usage"):
    check(f"canonical contains: {required[:52]}", required in canonical)

for path in TARGETS:
    rel = os.path.relpath(path, ROOT)
    body = open(path, encoding="utf-8").read()
    check(f"{rel} embeds the canonical block verbatim", canonical in body,
          "" if canonical in body else "drift detected — re-embed from "
                                        "supabase/access_control.sql")
    check(f"{rel} embeds it exactly once",
          body.count("BEGIN supabase/access_control.sql") == 1,
          f"found {body.count('BEGIN supabase/access_control.sql')}")

# The two target files must not disagree about the pre-existing tables the
# migration alters, or the same paste behaves differently per file.
supa = open(TARGETS[0], encoding="utf-8").read()
verc = open(TARGETS[1], encoding="utf-8").read()
for table in ("subscriptions", "run_limits"):
    check(f"both files define {table}",
          f"create table if not exists {table}" in supa.lower()
          and f"create table if not exists {table}" in verc.lower())

print()
print(f"RESULT: {len(FAILURES)} failure(s)" if FAILURES else "RESULT: all checks passed")
for f in FAILURES:
    print("  ✗", f)
sys.exit(1 if FAILURES else 0)
