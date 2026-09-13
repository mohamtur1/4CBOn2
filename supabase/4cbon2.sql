-- 4CBON2 AI Rewriter memory and public access schema.
-- Run in the Supabase SQL editor. Keep the service-role key server-side only.

-- Beliefs table (for L8)
CREATE TABLE IF NOT EXISTS beliefs (
  id BIGSERIAL PRIMARY KEY,
  belief TEXT NOT NULL,
  score_before INTEGER,
  score_after INTEGER,
  run_number INTEGER,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Questions table (for L9)
CREATE TABLE IF NOT EXISTS questions (
  id BIGSERIAL PRIMARY KEY,
  run_id TEXT,
  question_text TEXT NOT NULL,
  question_level INTEGER,
  question_type TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Feedback table (for validated critiques)
CREATE TABLE IF NOT EXISTS feedback (
  id BIGSERIAL PRIMARY KEY,
  evidence TEXT NOT NULL,
  confidence INTEGER CHECK (confidence BETWEEN 1 AND 5),
  critique_type TEXT,
  suggested_correction TEXT,
  run_id TEXT,
  injected BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Run limits table (for public version's 3 free tests)
CREATE TABLE IF NOT EXISTS run_limits (
  id BIGSERIAL PRIMARY KEY,
  ip TEXT NOT NULL,
  run_date DATE NOT NULL,
  run_count INTEGER NOT NULL DEFAULT 0,
  UNIQUE(ip, run_date)
);

-- Subscriptions table (for Gumroad webhook)
CREATE TABLE IF NOT EXISTS subscriptions (
  id BIGSERIAL PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  subscription_id TEXT,
  product_name TEXT,
  status TEXT,
  sale_id TEXT,
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Row-level security: only the service role can write; public can read.
ALTER TABLE beliefs ENABLE ROW LEVEL SECURITY;
ALTER TABLE questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE run_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;

-- Policies are dropped before they are created so this file can be pasted into
-- the SQL editor more than once. Without the DROPs a re-run aborts halfway with
-- 'policy ... already exists', leaving the migration half-applied.
DROP POLICY IF EXISTS "Service role can insert beliefs" ON beliefs;
DROP POLICY IF EXISTS "Service role can insert questions" ON questions;
DROP POLICY IF EXISTS "Service role can insert feedback" ON feedback;
DROP POLICY IF EXISTS "Service role can insert run_limits" ON run_limits;
DROP POLICY IF EXISTS "Service role can insert subscriptions" ON subscriptions;
DROP POLICY IF EXISTS "Public can read beliefs" ON beliefs;
DROP POLICY IF EXISTS "Public can read questions" ON questions;
DROP POLICY IF EXISTS "Public can read feedback" ON feedback;
DROP POLICY IF EXISTS "Public can read run_limits" ON run_limits;
DROP POLICY IF EXISTS "Public can read subscriptions" ON subscriptions;

CREATE POLICY "Service role can insert beliefs" ON beliefs FOR INSERT WITH CHECK (auth.role() = 'service_role');
CREATE POLICY "Service role can insert questions" ON questions FOR INSERT WITH CHECK (auth.role() = 'service_role');
CREATE POLICY "Service role can insert feedback" ON feedback FOR INSERT WITH CHECK (auth.role() = 'service_role');
CREATE POLICY "Service role can insert run_limits" ON run_limits FOR INSERT WITH CHECK (auth.role() = 'service_role');
CREATE POLICY "Service role can insert subscriptions" ON subscriptions FOR INSERT WITH CHECK (auth.role() = 'service_role');

-- NOTE: run_limits and subscriptions are intentionally absent here. The
-- access-control block at the end of this file revokes anon read on both;
-- re-adding "Public can read" policies for them would reopen that hole.
CREATE POLICY "Public can read beliefs" ON beliefs FOR SELECT USING (true);
CREATE POLICY "Public can read questions" ON questions FOR SELECT USING (true);
CREATE POLICY "Public can read feedback" ON feedback FOR SELECT USING (true);


-- ═══════════════════════════════════════════════════════════
-- ▼ BEGIN supabase/access_control.sql (canonical, do not edit in place)
-- ═══════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════
-- 4CBON2 ACCESS CONTROL — accounts, shared metering, signed passes
-- ═══════════════════════════════════════════════════════════
-- This is the CANONICAL copy. It is embedded verbatim at the end of
--   supabase/4cbon2.sql   and   vercel/supabase.sql
-- `python3 test_sql_sync.py` fails the build if the three copies drift.
--
-- Supabase-only: references auth.role() and the anon / authenticated /
-- service_role roles that Supabase provisions. Idempotent — safe to paste
-- into the SQL editor more than once.
--
-- Rulings implemented here (Malik, 2026-09-11):
--   #1 login is mandatory; metering is per identity, not per IP
--   #3 anon read is revoked on subscriptions, run_limits, run_usage and
--      every table added below

-- ── 1. Shared metering ─────────────────────────────────────
-- ONE counter per identity per UTC day, shared across Ask a Question,
-- AI Rewriter and Agent Mode. `subject` is the verified account email.
--
-- `feature` deliberately lives on gate_passes (one row per run) rather
-- than here: a feature column on this table would make it one row per
-- feature and silently turn the shared pool into three separate pools.
create table if not exists run_usage (
  subject   text    not null,
  run_date  date    not null,
  run_count integer not null default 0,
  primary key (subject, run_date)
);

-- Atomic increment. A single statement, so two concurrent runs from the
-- same account cannot both read the same count. SECURITY DEFINER with a
-- pinned search_path: the caller has no direct write access to run_usage.
create or replace function bump_run_usage(p_subject text, p_day date)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  n integer;
begin
  insert into run_usage (subject, run_date, run_count)
  values (p_subject, p_day, 1)
  on conflict (subject, run_date)
  do update set run_count = run_usage.run_count + 1
  returning run_count into n;
  return n;
end;
$$;

-- Gate-side consume. The endpoint must both enforce the limit and move the
-- counter in ONE statement: reading the count and then incrementing it is
-- racy across two concurrent requests, and incrementing before checking
-- inflates the counter on denied attempts. The conditional DO UPDATE takes a
-- row lock, so exactly `p_limit` grants succeed per subject per day and the
-- counter records *granted* runs only.
--
-- Returns a composite: {p_allowed: bool, p_count: int}. p_count is the new
-- count when granted, or the current (unchanged) count when denied.
--
-- ADDED AFTER the schema was approved on 2026-09-11 — additive only: it does
-- not alter any approved table or the two approved functions.
create or replace function try_consume_run(
  p_subject text,
  p_day     date,
  p_limit   integer default 3,
  out p_allowed boolean,
  out p_count   integer
)
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into run_usage (subject, run_date, run_count)
  values (p_subject, p_day, 1)
  on conflict (subject, run_date)
  do update set run_count = run_usage.run_count + 1
   where run_usage.run_count < p_limit
  returning run_count into p_count;

  if p_count is null then
    -- The row exists but is already at or over the limit, so the UPDATE's
    -- WHERE excluded it and RETURNING produced nothing. Nothing was written.
    p_allowed := false;
    select coalesce(r.run_count, p_limit) into p_count
      from run_usage r
     where r.subject = p_subject
       and r.run_date = p_day;
  else
    p_allowed := true;
  end if;
end;
$$;

-- Legacy IP-keyed counter. vercel/app.py's increment_run_count() upserts
-- run_count = 1 on conflict, so the count never passed 1 and the "3 free
-- runs/day" check never fired. Same atomic fix, kept until that call site
-- is repointed in step 2.
create or replace function bump_run_limit(p_ip text, p_day date)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  n integer;
begin
  insert into run_limits (ip, run_date, run_count)
  values (p_ip, p_day, 1)
  on conflict (ip, run_date)
  do update set run_count = run_limits.run_count + 1
  returning run_count into n;
  return n;
end;
$$;

-- ── 2. Unlimited identities ────────────────────────────────
-- A table rather than only an env var, so admin access can change without
-- a redeploy. ADMIN_EMAIL in Vercel stays as a break-glass override.
create table if not exists admin_emails (
  email      text primary key,
  note       text,
  created_at timestamptz not null default now()
);

insert into admin_emails (email, note)
values ('mohamtur1@gmail.com', 'owner — unlimited runs')
on conflict (email) do nothing;

-- ── 3. Single-use signed passes ────────────────────────────
-- Vercel mints the pass; the Space presents it back to /api/consume.
-- jti is the replay guard — a consumed pass can never be spent twice.
create table if not exists gate_passes (
  jti             text primary key,
  subject         text not null,
  feature         text,
  issued_at       timestamptz not null default now(),
  expires_at      timestamptz not null,
  consumed_at     timestamptz,
  consumed_by_run text
);

create index if not exists gate_passes_subject_idx
  on gate_passes (subject, issued_at);

-- ── 4. Lock down reads (ruling #3) ─────────────────────────
alter table run_usage    enable row level security;
alter table admin_emails enable row level security;
alter table gate_passes  enable row level security;

-- The original schema granted anon SELECT on these two. With real accounts
-- and real subscriptions, a publicly readable subscriptions table is a
-- customer list. Drop the policies...
drop policy if exists "Public can read subscriptions" on subscriptions;
drop policy if exists "Public can read run_limits"    on run_limits;
drop policy if exists "Allow anonymous read"          on subscriptions;
drop policy if exists "Allow anonymous read"          on run_limits;

-- ...and revoke the implicit PUBLIC grant, so an anon query is refused
-- outright instead of silently returning zero rows.
revoke all on table subscriptions from public, anon, authenticated;
revoke all on table run_limits    from public, anon, authenticated;
revoke all on table run_usage     from public, anon, authenticated;
revoke all on table admin_emails  from public, anon, authenticated;
revoke all on table gate_passes   from public, anon, authenticated;

-- The server talks to Postgres as service_role, which Supabase grants
-- directly and which bypasses RLS.
grant all on table run_usage    to service_role;
grant all on table admin_emails to service_role;
grant all on table gate_passes  to service_role;
grant all on table subscriptions to service_role;
grant all on table run_limits    to service_role;

-- Only the service role may move a meter. (OUT parameters are not part of a
-- function's identity, hence the three-argument signature below.)
revoke execute on function bump_run_usage(text, date)        from public, anon, authenticated;
revoke execute on function bump_run_limit(text, date)        from public, anon, authenticated;
revoke execute on function try_consume_run(text, date, integer) from public, anon, authenticated;
grant  execute on function bump_run_usage(text, date)        to service_role;
grant  execute on function bump_run_limit(text, date)        to service_role;
grant  execute on function try_consume_run(text, date, integer) to service_role;

-- Explicit service-role policies. The service role bypasses RLS regardless;
-- these exist so the intent is auditable in the dashboard and survives a
-- future change to how the server connects.
drop policy if exists "Service role full access to run_usage"    on run_usage;
drop policy if exists "Service role full access to admin_emails" on admin_emails;
drop policy if exists "Service role full access to gate_passes"  on gate_passes;

create policy "Service role full access to run_usage"
  on run_usage for all
  using (auth.role() = 'service_role')
  with check (auth.role() = 'service_role');

create policy "Service role full access to admin_emails"
  on admin_emails for all
  using (auth.role() = 'service_role')
  with check (auth.role() = 'service_role');

create policy "Service role full access to gate_passes"
  on gate_passes for all
  using (auth.role() = 'service_role')
  with check (auth.role() = 'service_role');

-- ═══════════════════════════════════════════════════════════
-- GUMROAD PINGS — at-least-once, unordered, unsigned
-- ═══════════════════════════════════════════════════════════
-- Gumroad's own Ping documentation states four properties that this table
-- exists to survive:
--
--   1. Delivery is at-least-once. The same ping can arrive more than once,
--      so the primary key below is a real dedupe key, not a convenience.
--   2. Ordering is NOT guaranteed. A refund ping can arrive BEFORE the sale
--      ping it reverses.
--   3. A refund carries the SAME sale_id as the sale it reverses; the two are
--      distinguished only by resource_name. Deduping on sale_id alone would
--      silently swallow the refund.
--   4. Only 499/500/502/503/504 are retried, four times, then never again.
--      A dropped ping is lost for good, so this table is the audit trail that
--      lets us find the gap.
--
-- The consequence for design: subscription status must NEVER be derived from
-- the order pings arrived in. It is derived from the SET of pings recorded
-- here — "does a refund exist for this sale_id?" is order-independent, and a
-- late sale ping cannot undo a refund that was already applied.

create table if not exists gumroad_pings (
  sale_id          text        not null,
  resource_name    text        not null,              -- 'sale' | 'refund'
  received_at      timestamptz not null default now(),
  sale_timestamp   text,
  email            text,
  subscription_id  text,
  product_name     text,
  recurrence       text,
  refunded         text,
  is_test          boolean     not null default false,
  retry_count      integer,
  primary key (sale_id, resource_name)
);

comment on table gumroad_pings is
  'Raw Gumroad Ping log. Append-only; status is computed from this set, never from arrival order.';

-- Subscriptions gains the two columns reconciliation needs. Both are additive
-- and nullable/defaulted so an existing table is not rewritten and no row is
-- invalidated by the migration.
alter table subscriptions add column if not exists sale_timestamp text;
alter table subscriptions add column if not exists source text not null default 'ping';

comment on column subscriptions.source is
  '''ping'' = last set by a Gumroad Ping (unverified payload). ''api'' = confirmed by reading the sale back through the Gumroad API. Reconciliation overwrites ping with api.';

-- Ruling #3 applies here too: no anonymous access to a new table, in the same
-- migration that creates it. This one holds buyer email addresses.
alter table gumroad_pings enable row level security;
revoke all on table gumroad_pings from public, anon, authenticated;
grant  all on table gumroad_pings to service_role;

drop policy if exists "Service role full access to gumroad_pings" on gumroad_pings;
create policy "Service role full access to gumroad_pings"
  on gumroad_pings for all
  using (auth.role() = 'service_role')
  with check (auth.role() = 'service_role');

-- ▲ END supabase/access_control.sql
-- ═══════════════════════════════════════════════════════════
