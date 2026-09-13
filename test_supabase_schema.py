#!/usr/bin/env python3
"""Execute the Supabase schema against a real PostgreSQL and attack it.

This is not a lint. It boots an actual PostgreSQL server (via the `pgserver`
wheel, which ships the binaries), runs the migration files end to end, and then
verifies the properties the gate depends on:

  * the atomic RPC really increments (1,2,3,4 — not the old write-1 bug)
  * it stays atomic under 20 concurrent callers
  * anon cannot read subscriptions / run_limits / run_usage / admin_emails /
    gate_passes, and cannot execute either meter function
  * a role with no table grants at all can still increment, proving the
    SECURITY DEFINER boundary is what grants the write
  * the migration is idempotent (safe to paste twice)

Usage:
    python3 test_supabase_schema.py

If `pgserver` is not installed the suite prints SKIP and exits 0 — say so
loudly rather than claiming a pass. Install with:  pip install pgserver
"""
import concurrent.futures
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.abspath(__file__))
FAILURES = []
CHECKS = 0


def check(label, condition, detail=""):
    global CHECKS
    CHECKS += 1
    print(f"[{'PASS' if condition else 'FAIL'}] {label}" + (f" — {detail}" if detail else ""))
    if not condition:
        FAILURES.append(label)


# ════════════════════════════════════════════════════════════
# Boot a real PostgreSQL
# ════════════════════════════════════════════════════════════
try:
    import pgserver
except ImportError:
    print("SKIP — `pgserver` is not installed, so no PostgreSQL could be booted.")
    print("       Nothing in this suite ran. Install with: pip install pgserver")
    sys.exit(0)

PGDATA = tempfile.mkdtemp(prefix="4cbon2_pg_")
server = pgserver.get_server(PGDATA, cleanup_mode=None)
PGBIN = os.path.join(os.path.dirname(pgserver.__file__), "pginstall", "bin")
PSQL = os.path.join(PGBIN, "psql")
ENV = dict(os.environ, PGHOST=PGDATA, PGUSER="postgres", PGDATABASE="postgres")

if not os.path.exists(PSQL):
    print(f"SKIP — pgserver installed but no psql at {PSQL}. Nothing ran.")
    sys.exit(0)

version = subprocess.run([PSQL, "-Atc", "select version();"], env=ENV,
                         capture_output=True, text=True)
if version.returncode != 0:
    print(f"SKIP — PostgreSQL would not accept connections: {version.stderr.strip()}")
    sys.exit(0)
print(f"PostgreSQL under test: {version.stdout.strip().split(',')[0]}")
print("=" * 70)


def psql(sql, db="postgres", role=None, expect_fail=False):
    """Run SQL, optionally as another role. Returns (rc, out, err)."""
    args = [PSQL, "-v", "ON_ERROR_STOP=1", "-At", "-d", db]
    if role:
        # SET ROLE first, then the caller's statement, in one session.
        sql = f"set role {role};\n{sql}"
    args += ["-c", sql]
    r = subprocess.run(args, env=ENV, capture_output=True, text=True)
    return r.returncode, r.stdout.strip(), r.stderr.strip()


def psql_file(path, db):
    return subprocess.run([PSQL, "-v", "ON_ERROR_STOP=1", "-q", "-d", db, "-f", path],
                          env=ENV, capture_output=True, text=True)


def fresh_db(name):
    """A clean database with the roles and auth.role() Supabase provides."""
    psql(f'drop database if exists {name};')
    for role in ("anon", "authenticated", "service_role"):
        psql(f"do $$ begin if not exists (select 1 from pg_roles where rolname='{role}') "
             f"then create role {role} nologin; end if; end $$;")
    psql(f'create database {name};')
    psql("""
        create schema if not exists auth;
        create or replace function auth.role() returns text
        language sql stable as $$
          select coalesce(
                   nullif(current_setting('request.jwt.claims', true), '')::json ->> 'role',
                   'anon');
        $$;
        revoke all on schema auth from public;
        grant usage on schema auth to anon, authenticated, service_role;
        grant usage on schema public to anon, authenticated, service_role;
    """, db=name)
    # CRITICAL for the lockout tests to mean anything. Vanilla PostgreSQL
    # grants PUBLIC *nothing* on a new table — only the owner has rights — so
    # without this, anon would be refused on every table whether or not the
    # migration's REVOKEs exist, and those checks would pass vacuously.
    # Supabase runs exactly this in its initial migration, which is why RLS is
    # the real gate there. Replicating it makes the REVOKEs the thing under
    # test. Verified: has_table_privilege('public', <new table>, 'SELECT') is
    # 'f' here before this line and 't' for anon after it.
    psql("""
        alter default privileges in schema public
          grant all on tables to anon, authenticated, service_role;
        alter default privileges in schema public
          grant all on sequences to anon, authenticated, service_role;
    """, db=name)
    return name


# ════════════════════════════════════════════════════════════
# 1. Both migration files must run clean, twice
# ════════════════════════════════════════════════════════════
print("\nMIGRATION EXECUTION")
print("=" * 70)

for sql_file, dbname in (("supabase/4cbon2.sql", "db_supabase"),
                         ("vercel/supabase.sql", "db_vercel")):
    path = os.path.join(ROOT, sql_file)
    db = fresh_db(dbname)
    r1 = psql_file(path, db)
    check(f"{sql_file} applies cleanly", r1.returncode == 0, r1.stderr.strip()[:160])
    r2 = psql_file(path, db)
    check(f"{sql_file} is idempotent (second run clean)", r2.returncode == 0,
          r2.stderr.strip()[:160])

DB = "db_supabase"


def q(sql, db=DB, role=None):
    return psql(sql, db=db, role=role)


# ════════════════════════════════════════════════════════════
# 2. Objects exist
# ════════════════════════════════════════════════════════════
print("\nOBJECTS")
print("=" * 70)

for table in ("run_usage", "admin_emails", "gate_passes", "gumroad_pings"):
    rc, out, _ = q(f"select count(*) from pg_tables where tablename='{table}';")
    check(f"table {table} created", out == "1", out)

for fn in ("bump_run_usage", "bump_run_limit"):
    rc, out, _ = q(f"select count(*) from pg_proc where proname='{fn}';")
    check(f"function {fn} created", out == "1", out)

rc, out, _ = q("select note from admin_emails where email='mohamtur1@gmail.com';")
check("admin_emails seeded with mohamtur1@gmail.com", out.startswith("owner"), repr(out))

rc, out, _ = q("select count(*) from admin_emails;")
check("admin_emails has exactly one seeded row", out == "1", out)

rc, out, _ = q("select relrowsecurity from pg_class where relname='run_usage';")
check("run_usage has RLS enabled", out == "t", out)


# ════════════════════════════════════════════════════════════
# 3. The increment actually increments
# ════════════════════════════════════════════════════════════
print("\nATOMIC INCREMENT — the bug this migration fixes")
print("=" * 70)

SUBJECT = "user@example.com"
DAY = "2026-09-11"
q(f"delete from run_usage where subject='{SUBJECT}';")

seq = [q(f"select bump_run_usage('{SUBJECT}', date '{DAY}');")[1] for _ in range(4)]
check("bump_run_usage returns 1,2,3,4 in order", seq == ["1", "2", "3", "4"], str(seq))

rc, out, _ = q(f"select run_count from run_usage where subject='{SUBJECT}' and run_date='{DAY}';")
check("stored run_count is 4, not 1", out == "4", out)

rc, out, _ = q(f"select count(*) from run_usage where subject='{SUBJECT}';")
check("one row per (subject, run_date), not four", out == "1", out)

rc, out, _ = q(f"select bump_run_usage('other@example.com', date '{DAY}');")
check("a second subject starts at 1 (counters are independent)", out == "1", out)

# The legacy IP-keyed RPC, same fix.
q("delete from run_limits where ip='9.9.9.9';")
seq = [q(f"select bump_run_limit('9.9.9.9', date '{DAY}');")[1] for _ in range(4)]
check("bump_run_limit returns 1,2,3,4 in order", seq == ["1", "2", "3", "4"], str(seq))


# ════════════════════════════════════════════════════════════
# 4. Atomicity under concurrency
# ════════════════════════════════════════════════════════════
print("\nCONCURRENCY")
print("=" * 70)

RACE = "race@example.com"
q(f"delete from run_usage where subject='{RACE}';")
N = 20


def one_bump(_):
    return q(f"select bump_run_usage('{RACE}', date '{DAY}');")[1]


with concurrent.futures.ThreadPoolExecutor(max_workers=N) as pool:
    results = list(pool.map(one_bump, range(N)))

rc, out, _ = q(f"select run_count from run_usage where subject='{RACE}' and run_date='{DAY}';")
check(f"{N} concurrent bumps land exactly {N} (no lost update)", out == str(N),
      f"run_count={out}, returned={sorted(set(results))}")
check(f"no two callers saw the same count", len(set(results)) == N,
      f"{len(set(results))} distinct of {N}")


# ════════════════════════════════════════════════════════════
# 5. anon is locked out (ruling #3)
# ════════════════════════════════════════════════════════════
print("\nANON LOCKOUT — ruling #3")
print("=" * 70)

# Positive control. If this fails, every "anon cannot read X" below is passing
# for the wrong reason — anon would simply have no grants at all, and the
# migration's REVOKEs would not be what's doing the work.
rc, out, err = q("select has_table_privilege('anon', 'beliefs', 'SELECT');")
check("HARNESS CONTROL: anon does hold table grants by default (as in Supabase)",
      out == "t", f"has_table_privilege={out} {err[:80]}")

rc, out, err = q("select count(*) from feedback;", role="anon")
check("HARNESS CONTROL: anon can read feedback (not revoked)", rc == 0,
      (err.splitlines() or [""])[0][:90])

for table in ("subscriptions", "run_limits", "run_usage", "admin_emails", "gate_passes",
              "gumroad_pings"):
    rc, out, err = q(f"select count(*) from {table};", role="anon")
    check(f"anon cannot read {table}", rc != 0, (err.splitlines() or [""])[0][:90])

for fn, args in (("bump_run_usage", f"'x@y.z', date '{DAY}'"),
                 ("bump_run_limit", f"'1.2.3.4', date '{DAY}'")):
    rc, out, err = q(f"select {fn}({args});", role="anon")
    check(f"anon cannot execute {fn}", rc != 0, (err.splitlines() or [""])[0][:90])

rc, out, err = q(f"insert into run_usage values ('anon@x.y', date '{DAY}', 1);", role="anon")
check("anon cannot write run_usage directly", rc != 0, (err.splitlines() or [""])[0][:90])

# The beliefs/questions "collective memory" was intentionally left public;
# assert that is still true so the change is deliberate, not accidental.
rc, out, err = q("select count(*) from beliefs;", role="anon")
check("beliefs is still anon-readable (deliberately left open)", rc == 0,
      (err.splitlines() or [""])[0][:90] if rc else f"count={out}")


# ════════════════════════════════════════════════════════════
# 6. The SECURITY DEFINER boundary is what grants the write
# ════════════════════════════════════════════════════════════
print("\nSECURITY DEFINER BOUNDARY")
print("=" * 70)

psql("do $$ begin if not exists (select 1 from pg_roles where rolname='probe') "
     "then create role probe nologin; end if; end $$;")
q("grant usage on schema public to probe;")
q(f"grant execute on function bump_run_usage(text, date) to probe;")

PROBE = "probe@example.com"
q(f"delete from run_usage where subject='{PROBE}';")
rc, out, err = q(f"select bump_run_usage('{PROBE}', date '{DAY}');", role="probe")
# `set role` emits its own "SET" line before the query result.
last = (out.splitlines() or [""])[-1].strip()
check("a role with NO table grants can still meter via the RPC", rc == 0 and last == "1",
      f"rc={rc} result={last!r} err={(err.splitlines() or [''])[0][:70]}")

rc, out, err = q(f"select count(*) from run_usage;", role="probe")
check("...but that role still cannot read the table directly", rc != 0,
      (err.splitlines() or [""])[0][:90])


# ════════════════════════════════════════════════════════════
# 7. Pass replay guard
# ════════════════════════════════════════════════════════════
print("\nSIGNED-PASS REPLAY GUARD")
print("=" * 70)

psql("delete from gate_passes;", db=DB)
rc, _, err = q("insert into gate_passes (jti, subject, feature, expires_at) "
               "values ('jti-1', 'user@example.com', 'rewriter', now() + interval '15 minutes');")
check("a pass can be recorded", rc == 0, err[:90])
rc, _, err = q("insert into gate_passes (jti, subject, feature, expires_at) "
               "values ('jti-1', 'user@example.com', 'agent', now() + interval '15 minutes');")
check("the same jti cannot be spent twice (primary key)", rc != 0,
      (err.splitlines() or [""])[0][:90])


# ════════════════════════════════════════════════════════════
# 7b. try_consume_run — enforce and increment in one statement
# ════════════════════════════════════════════════════════════
print("\ntry_consume_run — the gate's consume path")
print("=" * 70)

rc, out, err = q("select count(*) from pg_proc where proname='try_consume_run';")
check("function try_consume_run created", out == "1", out)

TC = "tc@example.com"
q(f"delete from run_usage where subject='{TC}';")

verdicts = []
for _ in range(5):
    rc, out, err = q(f"select * from try_consume_run('{TC}', date '{DAY}', 3);")
    verdicts.append(out)

check("first three consumes are allowed", all("t" in v for v in verdicts[:3]), str(verdicts[:3]))
check("4th and 5th consumes are denied", all(v.startswith("f") for v in verdicts[3:]),
      str(verdicts[3:]))

rc, out, _ = q(f"select run_count from run_usage where subject='{TC}' and run_date='{DAY}';")
check("denied attempts do NOT inflate the counter (still 3, not 5)", out == "3", out)

rc, out, err = q("select * from try_consume_run('brand-new@example.com', date '" + DAY + "', 3);")
check("a fresh subject is allowed at count 1", out.startswith("t") and out.endswith("1"),
      f"{out} {err[:60]}")

# The whole point of doing it in one statement: 10 concurrent consumers, limit 3.
RACE2 = "race2@example.com"
q(f"delete from run_usage where subject='{RACE2}';")
with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
    outs = list(pool.map(
        lambda _: q(f"select * from try_consume_run('{RACE2}', date '{DAY}', 3);")[1], range(10)))

granted = sum(1 for o in outs if o.startswith("t"))
rc, out, _ = q(f"select run_count from run_usage where subject='{RACE2}' and run_date='{DAY}';")
check("10 concurrent consumers, limit 3 -> exactly 3 granted", granted == 3,
      f"granted={granted} results={sorted(outs)}")
check("...and the stored count is exactly 3", out == "3", out)

rc, out, err = q(f"select * from try_consume_run('{TC}', date '{DAY}', 3);", role="anon")
check("anon cannot execute try_consume_run", rc != 0, (err.splitlines() or [""])[0][:90])


# ════════════════════════════════════════════════════════════
# 7b. gumroad_pings — the dedupe key the webhook depends on
# ════════════════════════════════════════════════════════════
print("\nGUMROAD PINGS")
print("=" * 70)

rc, out, _ = q("select count(*) from pg_constraint c "
               "join pg_class t on t.oid=c.conrelid "
               "where t.relname='gumroad_pings' and c.contype='p';")
check("gumroad_pings has a primary key", out == "1", out)

rc, out, _ = q("select string_agg(a.attname, ',' order by x.ord) "
               "from pg_constraint c "
               "join pg_class t on t.oid=c.conrelid "
               "join unnest(c.conkey) with ordinality x(attnum, ord) "
               "  on x.attnum = any (c.conkey) "
               "join pg_attribute a on a.attrelid=t.oid and a.attnum=x.attnum "
               "where t.relname='gumroad_pings' and c.contype='p';")
# (sale_id, resource_name) together. sale_id alone would swallow a refund,
# because a refund carries the same sale_id as the sale it reverses.
check("its primary key is (sale_id, resource_name), not sale_id alone",
      out in ("sale_id,resource_name", "resource_name,sale_id"), out)

psql("delete from gumroad_pings;", db=DB)
q("insert into gumroad_pings (sale_id, resource_name, email) "
  "values ('S1', 'sale', 'buyer@example.com');")
rc, out, err = q("insert into gumroad_pings (sale_id, resource_name, email) "
                 "values ('S1', 'refund', 'buyer@example.com');")
check("a refund sharing the sale's sale_id is stored as its own row", rc == 0,
      (err.splitlines() or [""])[0][:90])
rc, out, err = q("insert into gumroad_pings (sale_id, resource_name, email) "
                 "values ('S1', 'sale', 'buyer@example.com');")
check("a re-delivered identical ping is rejected by the primary key", rc != 0,
      (err.splitlines() or [""])[0][:90])
rc, out, _ = q("select count(*) from gumroad_pings;")
check("so at-least-once delivery cannot inflate the log", out == "2", out)

for col in ("sale_timestamp", "source"):
    rc, out, _ = q(f"select count(*) from information_schema.columns "
                   f"where table_name='subscriptions' and column_name='{col}';")
    check(f"subscriptions gained the {col} column", out == "1", out)

rc, out, _ = q("select column_default from information_schema.columns "
               "where table_name='subscriptions' and column_name='source';")
check("subscriptions.source defaults to 'ping'", "ping" in (out or ""), repr(out))

rc, out, err = q("insert into subscriptions (email) values ('backfill@example.com');")
check("the added columns are defaulted, so existing inserts still work", rc == 0,
      (err.splitlines() or [""])[0][:90])

rc, out, _ = q("select source from subscriptions where email='backfill@example.com';")
check("...and a row written without source is marked 'ping'", out == "ping", repr(out))
q("delete from subscriptions where email='backfill@example.com';")

rc, out, _ = q("select relrowsecurity from pg_class where relname='gumroad_pings';")
check("gumroad_pings has RLS enabled", out == "t", out)


# ════════════════════════════════════════════════════════════
# 8. The vercel/ file gets the same objects
# ════════════════════════════════════════════════════════════
print("\nvercel/supabase.sql PARITY")
print("=" * 70)

for table in ("run_usage", "admin_emails", "gate_passes", "gumroad_pings"):
    rc, out, _ = q(f"select count(*) from pg_tables where tablename='{table}';", db="db_vercel")
    check(f"db_vercel has {table}", out == "1", out)

seq = [q(f"select bump_run_usage('v@example.com', date '{DAY}');", db="db_vercel")[1]
       for _ in range(3)]
check("db_vercel bump_run_usage returns 1,2,3", seq == ["1", "2", "3"], str(seq))

rc, out, _ = q("select count(*) from pg_proc where proname='try_consume_run';", db="db_vercel")
check("db_vercel has try_consume_run", out == "1", out)

rc, out, _ = q("select note from admin_emails where email='mohamtur1@gmail.com';", db="db_vercel")
check("db_vercel admin_emails seeded", out.startswith("owner"), repr(out))


# ════════════════════════════════════════════════════════════
print("\n" + "=" * 70)
print(f"ran {CHECKS} checks against real PostgreSQL")
print(f"RESULT: {len(FAILURES)} failure(s)" if FAILURES else "RESULT: all checks passed")
for f in FAILURES:
    print("  ✗", f)
print("=" * 70)

server.cleanup()
shutil.rmtree(PGDATA, ignore_errors=True)
sys.exit(1 if FAILURES else 0)
