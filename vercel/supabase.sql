-- Run in Supabase SQL editor. event_log is intentionally not altered.
-- All INSERT/UPDATE/DELETE policies are absent: only the service-role key can write.
create table if not exists beliefs (
  id bigserial primary key, belief text not null, score_before integer,
  score_after integer, run_number integer, created_at timestamptz default now()
);
create table if not exists questions (
  id bigserial primary key, run_id text, question_text text not null,
  question_level integer, question_type text, created_at timestamptz default now()
);
create table if not exists feedback (
  id bigserial primary key, evidence text not null,
  confidence integer check (confidence between 1 and 5),
  critique_type text, suggested_correction text, run_id text,
  injected boolean default false, created_at timestamptz default now()
);
create table if not exists run_limits (
  id bigserial primary key, ip text not null, run_date date not null,
  run_count integer not null default 0, unique(ip, run_date)
);
create table if not exists subscriptions (
  id bigserial primary key, email text unique not null, subscription_id text,
  product_name text, status text, sale_id text, updated_at timestamptz default now()
);

-- RLS means the public/anon role cannot read or write user data.
-- The server uses SUPABASE_SERVICE_ROLE_KEY, which bypasses RLS.
alter table beliefs enable row level security;
alter table questions enable row level security;
alter table feedback enable row level security;
alter table run_limits enable row level security;
alter table subscriptions enable row level security;
