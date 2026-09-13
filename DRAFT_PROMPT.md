# DRAFT PROMPT v3 — Vercel as auth + paywall gate, HF Space as compute backend

> **Status: APPROVED and under implementation.** Copy everything below the line into a new
> agent turn.
>
> **v3 changes — Malik's rulings, 2026-09-11, all four closed:**
> 1. **Login is mandatory** for Ask a Question, AI Rewriter and Agent Mode. Landing page,
>    About and Diagnostics stay open. The "3 anonymous runs by IP" alternative is **not**
>    to be built — it reopens the VPN/NAT weakness this project exists to close.
> 2. **Fail closed** when Supabase or the Space↔Vercel call is unreachable.
>    `FOURCBON2_GATE_FAIL_OPEN` defaults to `false` and stays that way unless Malik says
>    otherwise explicitly.
> 3. **Revoke anon read** on `subscriptions`, `run_limits`, `run_usage` and every new table,
>    in the same migration — not a follow-up.
> 4. **Run history is a named follow-up project**, explicitly out of scope here, recorded in
>    the handover so it is neither lost nor silently assumed to already exist.
>
> **v2 changes:** decision #1 answered — all three features share one 3-run/day pool. Login
> layer added (Google OAuth + email/password via Supabase Auth), unlimited runs for
> `mohamtur1@gmail.com`. Verified inventory of what the Space sends to Supabase added.
>
> **Implementation status: step 1 (SQL) complete and tested — see the last section.**


---

## Your task

Build a **gate**, not a port. `app.4cbon.com` (Vercel) becomes the front door that
**authenticates the visitor** and decides *how many runs they have left*; the existing
Hugging Face Space (`space_gemini/`) keeps doing the actual compute, unchanged in behaviour.

Do **not** move `space_gemini/app.py` onto Vercel. Do **not** add heavy packages to
`vercel/requirements.txt`. Do **not** change DNS.

Work only on branch `arena/01a08fea-4cbon2`.

---

## Context you must not re-derive — these are already measured

Verified in this repo on 2026-09-11. Take them as given.

**Why porting is off the table (measured, not estimated).**
`space_gemini/requirements.txt` installed into a clean venv:

```
117 wheels, 179 MB compressed
installed site-packages: 784,705,933 bytes (784.7 MB apparent / 748 MiB allocated)
largest: kubernetes 84M · gradio 82M · pandas 76M · onnxruntime 67M · plotly 60M
         · chromadb_rust_bindings 57M · numpy 45M (+28M numpy.libs)
resolved: gradio 6.27.0, chromadb 1.5.9, pydantic 2.12.3, google-genai 2.8.0,
          onnxruntime 1.30.0, numpy 2.4.6, pandas 3.0.5, plotly 7.0.0, fpdf2 2.8.8
```

`vercel/requirements.txt`'s header records two real Vercel builds: exact 589.9 MB →
Vercel-reported 566 MB, and exact 400.3 MB → ~421 MB. Those imply ratios of 0.959 and
1.049, so no single multiplier is defensible — which is why the figures below went wrong
twice before being measured properly.

**Measured with `tools/measure_bundle.py`, which excludes `__pycache__`:**

```
space_gemini/requirements.txt installed in a clean venv:
  778.1 MB on disk = 184.3 MB bytecode + 22.7 MB tests + 571.2 MB shipped
  -> ~601 MB in Vercel's accounting, OVER the 500 MB cap by ~101 MB
```

Two earlier revisions of this document gave ~753 MB and then ~823 MB. Both counted compiled
bytecode, which a Vercel function never ships, and both were wrong by 150–220 MB. The
conclusion never changed — it is over the cap — but the overshoot is ~101 MB, not ~323 MB.
The file being "only 177 KB" (actual: 181,583 bytes) is still irrelevant: Vercel bundles the
*dependency tree*. Closing a ~101 MB gap is arithmetically conceivable, but the only items
big enough to close it are ChromaDB and its ONNX/Kubernetes tree (~190 MB) and plotly
(~60 MB) — i.e. the research vector store and the dashboard. Trimming to fit means deleting
the app.

**The existing run-limit enforcement is broken.** `vercel/app.py:351` `increment_run_count()`
writes `run_count: 1` on conflict — it **does not increment**. No Postgres trigger or function
in `supabase/4cbon2.sql` or `vercel/supabase.sql` increments either (checked). So
`run_count` can never exceed 1 and `check_run_limit()` (`vercel/app.py:336`) returns
`allowed: True` forever. The "3 free runs/day" has never worked. It also **fails open**: on a
Supabase error or missing config it returns `{"allowed": True, ...}`.

**The Space's own limit is not a limit.** `run_public_rewriter` (`space_gemini/app.py:2697`)
takes `free_run_count` from `public_free_run_state = gr.State(0)` (`app.py:3084`) — browser
session state. Reload → 3 more runs. `DEPLOYMENT.md` already calls it "a client session
guard, not a security boundary."

**`app.py` is generated — never hand-edit it.** `build_gemini_space.py` assembles
`space_gemini/app.py` from `4CBOn2_Gemini2c.ipynb` (8 cells, asserted) plus
`build_src/handwritten.py` (7 `@@SECTION:name@@` blocks: header, imports, llm, chroma_pre,
chroma_post, ui, orch_helpers). `deploy_gemini_space.sh` runs the build before every upload,
so a hand edit to `app.py` is destroyed on the next deploy. **Gate code goes in
`build_src/handwritten.py`**, wired into `main()`'s join list. The completeness guard fails
the build if a notebook top-level def goes missing — don't break it.

**The Space URL is public.** A redirect or iframe from Vercel enforces nothing: anyone with
the `*.hf.space` URL skips Vercel entirely. **Enforcement must live inside the Space, keyed
to a credential only Vercel can mint.**

---

## What the Space sends to Supabase today — verified inventory

Malik asked specifically. This is the complete answer, traced through every writer.

There is exactly **one** Supabase writer in the whole Space: `_supabase_insert(table, payload)`
at `space_gemini/app.py:2543`, a bare `requests.post` to `{SUPABASE_URL}/rest/v1/{table}`
using the service-role key, silently no-oping when `SUPABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY`
are unset. It has exactly **one** caller: `save_rewriter_memory` (`app.py:2557`), reached only
from `_execute_rewriter` (`app.py:2676`).

| Tab | Goes to Supabase? | What happens to the data instead |
| --- | --- | --- |
| 📁 Upload Documents | **No** | ChromaDB in `./data` (local, ephemeral) |
| ❓ Ask a Question | **No** | Nothing persisted. `ask_five_lens` (`app.py:2938`) returns the answer and discards it |
| 🤖 Agent Mode | **No** | Local SQLite only: `4cbon2_task_memory.db` (`task_memory`: goal, subtasks, final_answer, timestamp — written by `run_orchestrator_stream`, `app.py:1725`, and `persist_partial_run`, `app.py:1311`), `4cbon2_agents.db` (`agents`), `4cbon2_audit.jsonl` |
| 🧠 **AI Rewriter** | **Yes — the only one** | `beliefs`: `belief` (L8 text, truncated to 200 chars), `score_before`, `score_after`, `run_number`. `questions`: `run_id`, `question_text`, `question_level` (1–3), `question_type` (observation/reasoning/alignment) |
| 📊 Data Dashboard | **No** | Reads the *local* `task_memory` table, `LIMIT 20` (`app.py:2153`) |
| 🩺 Diagnostics | **No** | `collections.deque(maxlen=400)` in process memory (`app.py:210`). Never persisted. Lost on every restart/sleep |

Two consequences that matter for this work:

1. **Nothing is attributable to a user.** Neither table has an email, IP or user id column.
   `beliefs.run_number` is `len(_REWRITER_BELIEFS)` — a **module-level list** (`app.py:2351`)
   shared by every visitor and reset on restart. It is not a counter and cannot be used for
   metering. Adding login means adding a `subject`/`user_id` column here if you want per-user
   history; today the "collective memory" is anonymous and global by design.
2. **All four other tabs are amnesiac.** Agent Mode output, the Dashboard and Diagnostics
   vanish when the Space sleeps. If Malik expects "log in and see my past runs," that history
   does not currently exist anywhere — it would have to be created, and that is a separate
   feature from the gate. Say so; don't imply it comes free with login.

---

## Identity and login (new in v2)

**Auth lives on Vercel, never inside Gradio.** Supabase Auth (hosted) provides both providers
Malik asked for — Google OAuth and email/password — and Vercel already holds the
service-role key. Doing OAuth inside a Gradio app on a Space would mean either shipping the
anon key to the browser from the Space or hand-rolling a redirect flow in Gradio. Don't.

Flow:

1. `app.4cbon.com` → Supabase Auth: **Continue with Google** (OAuth) or **email + password**
   (sign-up / sign-in). Supabase stores and hashes credentials in `auth.users`. **We never
   store a password in any of our tables, and never log one.**
2. Vercel verifies the session, resolves the identity to an email, then checks entitlement.
3. Vercel mints a signed pass carrying the **verified email** and hands the browser the Space
   URL. The Space never sees a password or an OAuth token.

**Entitlement rules (decided):**

| Identity | Runs |
| --- | --- |
| `mohamtur1@gmail.com` (owner/admin) | **Unlimited**, always |
| Active subscriber (`subscriptions.status = 'active'`) | Unlimited |
| Any other logged-in user | **3 per day, shared across all three features** |

Admin should be a table (`admin_emails`), not just an env var, so it can change without a
redeploy — but seed it with `mohamtur1@gmail.com` and keep an `ADMIN_EMAIL` env override as
a break-glass.

**Shared counter across all three features (Malik's decision, v2).** One pool per identity
per UTC day. Ask a Question, AI Rewriter and Agent Mode all draw from it. Not per-feature
counters. 1 Ask + 1 Rewriter + 1 Agent = 3 used; the 4th of *any* kind is denied with the
Gumroad CTA at `space_gemini/app.py:2695`.

Because identity is now an email rather than an IP, meter on a `subject` column, not
`run_limits.ip`. See §1 below.

---

## Unknowns you must verify before writing code

This sandbox has no general outbound HTTPS (confirmed: `curl https://example.com` → `000`,
`https://huggingface.co` → `000`; PyPI works, the open web does not). Run these and **paste
real output**. Do not guess, and do not paste a fabricated transcript.

1. **What `app.4cbon.com` points at today.** From here it resolves only to Cloudflare anycast
   (`104.21.46.208`, `2606:4700:3031::6815:2ed0`) — which is what *both* Vercel and
   `hf.space` sit behind, so it proves nothing. `curl -sI https://app.4cbon.com | head -20`
   and identify the target from the headers. The claim that DNS "already points directly at
   the HF Space" is **unverified**.
2. **The Space's live slug and framing headers.**
   `curl -sI https://<user>-<space>.hf.space | grep -iE 'x-frame-options|content-security-policy|location'`.
   If `X-Frame-Options: DENY/SAMEORIGIN` or a CSP `frame-ancestors` is present, iframe is dead
   → use redirect.
3. **Cold-start / sleep behaviour** on the Space's current hardware tier, and wake latency.
   A paying visitor must not be sent to a black box for a minute.
4. **Gumroad webhook end-to-end.** `vercel/api/gumroad-webhook.py` takes the secret as a
   `?secret=` query param and upserts `subscriptions` on `on_conflict="email"` — untested.
   Drive a test sale and a cancellation.
5. **Google OAuth wiring (new).** A Google Cloud OAuth client with
   `https://app.4cbon.com/auth/callback` (plus the Supabase callback URL) as an authorised
   redirect URI, and the Supabase Google provider enabled. This is Malik's console work —
   emit exact click-paths, don't assume it exists.

---

## Product decisions — all closed

1. ~~Which tabs consume a paid run?~~ **All three — Ask a Question, AI Rewriter, Agent
   Mode — draw from one shared 3/day pool.**
2. ~~Is login mandatory?~~ **Yes.** All three features require a session; landing page,
   About and Diagnostics stay open. The anonymous IP-metered alternative is **not** built.
3. ~~Fail open or fail closed?~~ **Fail closed**, `FOURCBON2_GATE_FAIL_OPEN=false`, on all
   three gated tabs. Stays false unless Malik says otherwise.
4. **Iframe or redirect** — the only open item, decided by Unknowns §2 once the framing
   headers are read.
5. ~~Tighten the public-read RLS policies?~~ **Yes.** Anon read revoked on `subscriptions`,
   `run_limits`, `run_usage`, `admin_emails` and `gate_passes`, in the same migration.
6. **Run history** — a named follow-up project, out of scope here.

**One deviation from Malik's step-1 wording, flagged rather than silent:** he specified
`run_usage(subject, run_date, run_count, feature)`. A `feature` column on that table would
make it one row per feature and quietly convert the shared pool into three separate pools —
the opposite of ruling #1. So `run_usage` is `(subject, run_date, run_count)` with a primary
key on `(subject, run_date)`, and `feature` lives on `gate_passes`, one row per run. Same
audit breakdown, pool stays single. Say the word if a denormalised per-feature table is
wanted as well.

---

## What to build

### 1. Supabase — atomic counting, subject-keyed (additive only)

Add to `supabase/4cbon2.sql` **and** `vercel/supabase.sql`, kept in sync:

```sql
create table if not exists run_usage (
  subject text not null,            -- user email, or 'ip:<addr>' if anonymous is allowed
  run_date date not null,
  run_count integer not null default 0,
  primary key (subject, run_date)
);

create or replace function bump_run_usage(p_subject text, p_day date)
returns integer language plpgsql security definer as $$
declare n integer;
begin
  insert into run_usage (subject, run_date, run_count)
  values (p_subject, p_day, 1)
  on conflict (subject, run_date) do update
    set run_count = run_usage.run_count + 1
  returning run_count into n;
  return n;
end $$;
revoke execute on function bump_run_usage(text, date) from public, anon, authenticated;

create table if not exists admin_emails (
  email text primary key, note text, created_at timestamptz default now()
);
insert into admin_emails (email, note) values ('mohamtur1@gmail.com', 'owner')
  on conflict do nothing;

create table if not exists gate_passes (
  jti text primary key, subject text not null,
  issued_at timestamptz default now(), expires_at timestamptz not null,
  consumed_at timestamptz, consumed_by_run text, feature text
);
```

One statement, race-free, callable via `sb.rpc("bump_run_usage", {...})`. This replaces the
broken `increment_run_count()`. Keep `run_limits` in place for the legacy `vercel/app.py`
path, or migrate it — but do not leave two counters that disagree.

The `feature` column on `gate_passes` exists only for audit ("which tab used the run");
**the count itself is feature-agnostic**, which is exactly what a shared pool needs.

### 2. Vercel — auth, mint, consume (`vercel/api/`)

- `POST /api/pass` — verify the Supabase session, resolve the email, then:
  admin or active subscriber → `runs: null` (unlimited); otherwise read `run_usage` for
  `(email, today)` and mint only if `run_count < 3`. Also `HEAD` the Space to detect a
  sleeping backend and return `{"url": "...?pass=...", "waking": bool, "remaining": n}`.
- `POST /api/consume` — called **by the Space**, not the browser. Verifies HMAC + expiry,
  marks the `jti` consumed (reject replay), records `feature`, calls `bump_run_usage` via
  RPC, returns `{"allowed": bool, "remaining": n, "unlimited": bool}`. **Single source of
  truth for the count.** For unlimited identities it must still validate the pass but skip
  the increment.

Pass format: `base64url(payload).base64url(hmac_sha256(secret, payload))`, payload
`{"sub": <email>, "runs": n|null, "exp": <now+900>, "jti": <uuid4>}`. Secret
`GATE_SHARED_SECRET` (32+ random bytes) in **Vercel env vars and the Space secret only** —
never in the repo. 15-minute TTL, single use.

Stdlib `hmac`/`hashlib`/`base64`/`json` plus already-present `requests` and `supabase`.
**No new packages** — ~79 MB of headroom and a standing rule in the requirements header.

### 3. Space — enforce on all three entry points

Verified structure you must respect (these are the exact shapes today):

| Entry point | Where | Shape | Gate mechanics |
| --- | --- | --- | --- |
| `ask_five_lens` | `handwritten.py:848` / `app.py:2938` | **closure nested at indent 12 inside the UI builder**, blocking `return` | Gate must be code *inside* `@@SECTION:ui@@`, not a module-level decorator |
| `run_public_rewriter_ui` | `handwritten.py:1010` / `app.py:3100` | **closure at indent 12**, blocking `return` | Same |
| `run_agent` (UI) | `handwritten.py:715` / `app.py:2805` | **module-level generator** — `yield` at 2809, 2817, 2825, 2836; drives `_agent_stream` (`app.py:2749`) → `run_orchestrator_stream` (`app.py:1605`) | Consume **before the first yield**, exactly once per click |

Two traps, both real in this codebase:

- **Duplicate definition.** `def run_agent` exists twice at module level: `app.py:1751`
  (notebook version, `run_agent(goal, system_override=None)`, blocking) and `app.py:2805`
  (UI version). The later shadows the former, so **1751 is dead code**. Wrap 2805 — the one
  that actually runs — and note the shadow in the build so nobody "fixes" it later.
- **Generator double-charge.** Gradio resumes generators with a fresh `Context` (already
  covered by existing tests in `test_gemini_space.py`). If the consume call sits after the
  first `yield`, or is keyed per-iteration, one Agent Mode run can burn several runs from the
  pool. Consume once, before streaming starts, and make it idempotent per click.

In `build_src/handwritten.py`, add `@@SECTION:gate@@` and wire it into `main()`'s join list:

- Read `pass` from the query string at load, verify HMAC + expiry locally, store the subject.
- Before each of the three runs, `POST /api/consume` with `feature` ∈
  `{ask, rewriter, agent}`. Deny on any failure when `FOURCBON2_GATE_FAIL_OPEN` is false,
  reusing `GUMROAD_URL` (`app.py:2695`).
- `gr.State(0)` stops being the authority — keep it only as a UI hint, or remove it.
- New env vars, documented in `space_gemini/README.md`'s secrets table: `GATE_SHARED_SECRET`,
  `GATE_VERCEL_URL`, `FOURCBON2_GATE_FAIL_OPEN`.
- Residual risk to record: the pass rides in the query string, so HF access logs see it.
  Mitigated by 15-min TTL + single use. If Malik wants it out of logs, that needs the
  reverse-proxy variant — say so, don't silently accept it.

### 4. Tests — the repo's own runners, extended

`python test_gemini_space.py` (111 `check()` call sites today, mocked `genai.Client`, no
network). Add:

- forged HMAC rejected · expired pass rejected · replayed `jti` rejected
- **4th run denied on each of the three features independently** (Ask, Rewriter, Agent)
- **shared pool sums across a mix: 1 Ask + 1 Rewriter + 1 Agent = 3 used; a 4th of any
  kind denied**
- **one Agent Mode run consumes exactly one run** even when the generator is resumed with a
  fresh `Context`
- admin email (`mohamtur1@gmail.com`) is unlimited and does not decrement the pool
- active subscriber is unlimited
- missing `GATE_SHARED_SECRET` → fail closed · `/api/consume` unreachable → fail closed
- no pass at all → all three gated tabs refuse, ungated tabs still work

Then: `python build_gemini_space.py` (guarded build + completeness guard must pass),
`python test_vercel_package.py` (must pass; proves no heavy package crept in), and
`python3 build_gemini_space.py && git diff --stat space_gemini/app.py` to confirm the
generated file only gained the gate section.

### 5. Docs

Update `DEPLOYMENT.md`, `vercel/README.md`, `space_gemini/README.md`: new env vars, the auth
+ pass flow, the atomic-increment fix, the shared-pool rule, and an explicit "**the old IP
counter never incremented; here is what changed**" note. Also state plainly what login does
**not** give you (see the inventory: no persisted history for Ask/Agent/Dashboard/Diagnostics).
Keep the rule that no DNS change happens without Malik's explicit approval.

---

## Explicitly out of scope

- Porting `space_gemini/app.py` to Vercel (measured: ~753 MB vs a 500 MB cap).
- Any change to `vercel/requirements.txt` beyond what §2 needs (nothing).
- Persisting Agent Mode / Dashboard / Diagnostics history. Separate feature; propose it, don't
  fold it in.
- Any DNS, Vercel-project, Supabase-project, Google Cloud or Gumroad console change. Emit
  exact click-paths, SQL and webhook URLs as instructions for Malik.
- Touching `4CBOn2_Gemini2c.ipynb`. It is build input; if the gate needs to change it, stop
  and ask.

## Reverse-proxy variant — only if framing fails

If the Space cannot be framed and Malik wants the URL hidden entirely, Vercel would proxy all
Space traffic. Flag the risks before agreeing: Gradio 6 uses websockets and SSE for the Agent
Mode streaming log, Vercel Python functions are short-lived and poor proxies for long-lived
streaming, and `vercel/README.md` itself notes the default 10s function timeout. **Spike one
route before committing.** The signed-pass design works with or without framing — build it
first.

## Definition of done

1. A forged pass is rejected by the Space (test output pasted).
2. The 4th run in a day is denied on **whichever** of the three features it is attempted on,
   and after a page reload / new browser (test output pasted, with the reason it is no longer
   `gr.State`).
3. A mixed sequence (Ask → Rewriter → Agent) totals 3 and the 4th is refused (test output).
4. One Agent Mode run consumes exactly one run under generator resumption (test output).
5. `mohamtur1@gmail.com` runs unlimited and the pool does not move (test output).
6. `run_usage.run_count` actually reaches 4 for one subject (test output from the RPC).
7. `python test_gemini_space.py` → `RESULT: all checks passed`; `python
   test_vercel_package.py` passes; `python build_gemini_space.py` completes.
8. The five live checks in **Unknowns** are run by someone with network access and their raw
   output is pasted — or explicitly marked "not run, here's why".
9. A handover note: what Malik must click in Google Cloud, Supabase, Vercel, Gumroad and the
   Space settings, in order, plus rollback.

---

## Implementation status

### Step 1 — SQL migration: DONE, tested against real PostgreSQL

Files:

| File | Change |
| --- | --- |
| `supabase/access_control.sql` | **New. Canonical** copy of the access-control block |
| `supabase/4cbon2.sql` | Canonical block embedded verbatim; pre-existing `CREATE POLICY` statements made idempotent |
| `vercel/supabase.sql` | Canonical block embedded verbatim |
| `vercel/README.md` | Removed the "Allow anonymous read" policies for `run_limits` / `subscriptions` — that README block instructed you to re-open the hole ruling #3 closes |
| `test_sql_sync.py` | **New.** Fails if the canonical block drifts from either embedded copy |
| `test_supabase_schema.py` | **New.** Boots a real PostgreSQL (via the `pgserver` wheel) and attacks the schema |

Shipped objects: `run_usage(subject, run_date, run_count)` PK `(subject, run_date)` ·
`bump_run_usage(text, date)` and `bump_run_limit(text, date)` — both atomic, `SECURITY
DEFINER`, `set search_path = public` · `admin_emails` seeded with `mohamtur1@gmail.com` ·
`gate_passes(jti PK, subject, feature, issued_at, expires_at, consumed_at, consumed_by_run)`
with an index on `(subject, issued_at)` · RLS enabled on all three new tables · anon read
revoked on `subscriptions`, `run_limits`, `run_usage`, `admin_emails`, `gate_passes` ·
execute revoked on both meter functions from `public`, `anon`, `authenticated`.

`bump_run_limit` is included now, ahead of need, so step 2 can repoint the broken
`increment_run_count()` in `vercel/app.py:351` with a one-line change.

**Verified — `python3 test_sql_sync.py` → `RESULT: all checks passed` (15 checks).**

**Verified — `test_supabase_schema.py` → `ran 39 checks against real PostgreSQL`,
`RESULT: all checks passed`, on PostgreSQL 16.2.** Highlights:

- both migration files apply cleanly **and are idempotent** (second paste clean)
- `bump_run_usage` returns 1, 2, 3, 4; stored `run_count` is 4, **not 1** — the live bug
- one row per `(subject, run_date)`, not four; second subject starts at 1 independently
- **20 concurrent bumps land exactly 20, 20 distinct return values, no lost update**
- anon denied on all five tables and both functions (`permission denied for table …`)
- a role with **no** table grants can still meter through the RPC but cannot read the table
  — proving `SECURITY DEFINER` is what grants the write
- duplicate `jti` rejected by the primary key — the replay guard

**Two bugs the test caught that the first draft of the migration had:**

1. `supabase/4cbon2.sql` was **not idempotent** — its pre-existing `CREATE POLICY`
   statements had no `DROP POLICY IF EXISTS`, so a second paste aborted at line 63 with
   `policy "Service role can insert beliefs" already exists`, leaving the migration
   half-applied. Pre-existing, not introduced here, but it is in the file being shipped, so
   it is fixed.
2. **The anon-lockout tests were passing for the wrong reason.** Vanilla PostgreSQL grants
   PUBLIC nothing on a new table (measured: `has_table_privilege('public', <new table>,
   'SELECT')` → `f`), so anon was refused everywhere whether or not the migration's
   `REVOKE`s existed. Supabase runs `ALTER DEFAULT PRIVILEGES … GRANT ALL ON TABLES TO anon,
   authenticated, service_role`, which is why RLS is the real gate there. The harness now
   replicates that, and two positive-control checks assert anon *does* hold grants and *can*
   read `feedback`, so the lockout results are attributable to the `REVOKE`s.

Not run, and why: nothing in this step touches Python runtime code, so
`test_gemini_space.py` and `test_vercel_package.py` have no changed path to execute. They
are required again at steps 2 and 5.

### Step 2 — /api/pass and /api/consume: DONE, tested against real PostgreSQL

| File | Change |
| --- | --- |
| `vercel/gate_core.py` | **New.** Stdlib-only: HMAC pass mint/verify, HS256 Supabase JWT verify, entitlement. No FastAPI, no Supabase, so the crypto is testable alone and the function cold-starts fast |
| `vercel/api/gate.py` | **New.** FastAPI app: `POST /api/pass`, `POST /api/consume`, `GET /api/gate/health` |
| `vercel/vercel.json` | Gate registered as its own function + routes ahead of the `/api/(.*)` catch-all; env block gains `SUPABASE_JWT_SECRET`, `GATE_SHARED_SECRET`, `SPACE_URL`, `GUMROAD_URL` |
| `vercel/app.py` | `increment_run_count()` repointed onto the `bump_run_limit` RPC; `check_run_limit()` now fails closed |
| `supabase/access_control.sql` | Adds `try_consume_run` (see below); re-embedded into both SQL files |
| `test_gate.py` | **New.** 80 checks |
| `OAUTH_SETUP.md` | **New.** Google Cloud + Supabase click-paths for step 3 |

**Two additions beyond the approved schema, flagged:**

1. **`try_consume_run(subject, day, limit)` returning `(p_allowed, p_count)`.**
   The endpoint has to enforce the limit and move the counter in one statement.
   Read-then-increment is racy across concurrent requests; increment-then-check
   inflates the counter on denied attempts. The conditional
   `ON CONFLICT DO UPDATE … WHERE run_count < limit` takes a row lock, so
   exactly three grants succeed per subject per day and `run_count` records
   *granted* runs only. Additive — no approved table or function was altered.
   `bump_run_usage` is kept for admin/backfill use.
2. **The `feature` column stayed on `gate_passes`**, per Malik's confirmation.

**Verified — `test_gate.py` → `ran 80 checks`, `RESULT: all checks passed`, on
PostgreSQL 16.2.** The real `gate_core.py` and `vercel/api/gate.py` are imported
and called through FastAPI's TestClient; the tables, `try_consume_run` and the
replay guard are the real ones. Only the PostgREST transport is a double, and
the suite first asserts that double's method names and signatures against the
installed `supabase`/`postgrest` packages so it cannot silently drift.

Highlights:

- 1 Ask + 1 Rewriter + 1 Agent → one counter = 3; the 4th `/api/pass` → `402`
  with the Gumroad link
- 5 pre-minted passes → exactly 3 granted, counter stays 3, **not** 5
- 6 concurrent consumes → exactly 3 granted
- pass replay → `409`, and the counter does not move
- tampered pass, wrong secret, expired pass, malformed pass → all rejected
- `"alg": "none"` JWT rejected — no signature bypass
- `mohamtur1@gmail.com` → `unlimited: true`, and **no** `run_usage` row is
  created; an admin pass is still single-use
- active subscriber unlimited; cancelling the subscription drops them to the
  free tier on the next `/api/pass`
- `increment_run_count()` now returns 1, 2, 3, 4 and `run_limits` really holds
  4 — the live bug, fixed and proven
- `check_run_limit()` fails closed on an unreachable database, and the one
  deliberate fail-open (no Supabase configured at all) reports `enforced: false`

**Three defects the tests caught in code I had just written:**

1. `_supabase()` let `create_client`'s `SupabaseException` escape on a malformed
   key. Only `GateError` was caught, so production would have returned an
   unhandled 500 with a stack trace instead of a clean fail-closed 503.
2. A Supabase outage returned **403** from `/api/pass`. Enforcement was correct
   (`allowed: false`), but 403 reads as "you are not entitled" — a paying
   subscriber would have been shown the paywall during a database blip. Now 503
   `entitlement_unavailable`, with the genuine out-of-runs case still 402.
3. `APIResponse.data` is typed `List[...]`, so an RPC result can arrive
   list-wrapped. `gate.py` read it as a bare dict and would have denied a run
   the database actually granted. Now unwraps both shapes, with a test that
   forces the list shape.

`test_vercel_package.py` → `RESULT: all checks passed`: the gate added **no new
dependency** (it walks every `.py` under `vercel/`, so `gate.py` and
`gate_core.py` were scanned). Its live-boot smoke test still SKIPs — that needs
`gradio`, which is not installed in the test interpreter here.

Not run: nothing exercised the deployed Vercel function or a real Supabase
project. `OAUTH_SETUP.md` Part D has the curl sequence for that, and B5 flags a
blocking unknown — **if the project uses asymmetric JWT signing keys there is no
shared secret to verify against and step 3 needs a JWKS path first.**

### Step 2b — JWKS verification: DONE, tested

Malik confirmed the project is on **asymmetric signing keys** — current key
`7bac5d81-9339-48e0-bc53-6c582eba28d1`, ECC P-256 (ES256); the legacy HS256 key
`4f52f33b-e918-4296-8905-cbe9ecf2caef` remains valid only for tokens issued
before the switch.

| File | Change |
| --- | --- |
| `vercel/gate_core.py` | `verify_supabase_jwt` now verifies ES256/RS256 against the JWKS by `kid`, with a 1h cache and one refresh on an unknown kid. HS256 retained for the transition window, gated on `SUPABASE_JWT_SECRET` being set |
| `vercel/requirements.txt` | `cryptography==50.0.1` declared explicitly (now a direct import) |
| `test_vercel_package.py` | `cryptography` added to `IMPORT_NAMES` |
| `test_jwt_jwks.py` | **New.** 33 checks |
| `OAUTH_SETUP.md` | §B5 resolved, §B6 added (JWKS URL + how to confirm the `kid` matches) |

**Cost to the bundle: zero.** `cryptography` already arrives transitively via
`google-generativeai -> google-auth` (which requires `cryptography>=38`).
Verified by installing `vercel/requirements.txt` into a clean venv — it is
present at 16 MB with or without the new line. It is declared anyway, following
the same rule the file already documents for `huggingface-hub`: a direct import
must not depend on a transitive resolution a future release could drop.

**The legacy HS256 key needs no special handling.** Supabase access tokens live
about an hour, so the legacy key's useful window closed within an hour of the
switch. HS256 support is implemented regardless (it costs nothing and removes a
failure mode) but the recommendation is to leave `SUPABASE_JWT_SECRET` unset so
only JWKS-verified ES256 tokens are accepted. Security rests on one rule: **the
algorithm decides where the key comes from, never the reverse** — there is no
path in which a JWKS public key can reach the HMAC branch.

**Verified — `test_jwt_jwks.py` → `ran 33 checks`, `RESULT: all checks
passed`.** Real P-256 and RSA keypairs, real signatures, and a real HTTP fetch
from a local server (3 tokens → 1 request, proving the cache). Covers: a token
signed by a *different* P-256 key rejected; the email claim edited without
re-signing rejected; unknown `kid` rejected after exactly one refresh; a newly
rotated-in key accepted after a refresh; **alg relabelled to HS256 and signed
with the public key rejected**; `alg="none"`, `HS512`, `ES512`, `PS256`, empty
and null all rejected; unreachable/empty/usable-keyless JWKS all deny.

**One defect the tests caught in the new code:** a token claiming `RS256` but
pointing at an EC `kid` reached `ECPublicKey.verify()` with the wrong arity and
raised `TypeError` — which would have escaped the endpoint as an unhandled 500
rather than a clean denial. The algorithm is now cross-checked against the key
type (and against the JWK's own declared `alg`), and any unexpected
`cryptography` exception becomes a `GateError`.

### Bundle audit — and a false alarm I raised and then withdrew

I reported that `vercel/requirements.txt` had drifted +143.7 MB in 24 hours and was ~71 MB
over the 500 MB cap. **That was wrong, and the deploy was never at risk.**

The measurement counted 111.8 MB of `__pycache__` bytecode and 26.0 MB of `tests/`
directories. Neither is shipped in a Vercel function. Measured correctly:

```
unpinned floating resolve:  539.5 MB on disk -> 401.8 MB shipped -> ~423 MB Vercel
pinned (this change):       539.5 MB on disk -> 401.8 MB shipped -> ~423 MB Vercel
cap 500 MB  ->  margin 77.4 MB, comfortably under
```

~423 MB against the header's recorded ~421 MB confirms both the method and that **there was
no drift at all**. `tools/measure_bundle.py` now exists so the measurement cannot be done by
hand again, and it asserts its own arithmetic (shipped + bytecode + tests = total).

What *was* real, and is the reason to keep the pins: I checked what the seven floaters would
have resolved to on 2026-09-10 against PyPI's release dates, and today's resolve is **older**
for most of them (numpy 2.4.6 vs 2.5.3 available, pandas 2.3.3 vs 3.0.5 available, pillow
10.4.0 vs 12.3.0 available). gradio 4.44 pins them down — `pandas<3.0`, `pillow<11.0`,
`numpy<3.0`, `matplotlib~=3.0`. So:

- there was no size growth to reverse;
- **pinning "to the 2026-09-10 versions" as literally requested would have broken the
  build.** The newest releases available that day (pandas 3.0.5, pillow 12.3.0) violate
  gradio 4.44's constraints and the resolve would fail outright. The pins below are the
  newest versions that *satisfy* gradio, which is what actually resolved.

| File | Change |
| --- | --- |
| `vercel/requirements.txt` | `numpy==2.4.6`, `pandas==2.3.3`, `matplotlib==3.11.2`, `fonttools==4.65.0`, `contourpy==1.3.3`, `kiwisolver==1.5.1`, `pillow==10.4.0` |
| `tools/measure_bundle.py` | **New.** Measures shipped size the way Vercel sees it; exits 1 over budget |
| `test_vercel_package.py` | The seven pins registered in `ALLOWED_TRANSITIVE_PINS` with reasons |

The pins buy **determinism, not size** — the resolve already picked exactly these versions,
so the measurement is identical before and after. Their value is that a rebuild next month
cannot silently move the bundle.

**Verified — `test_vercel_package.py` on the pinned env → `RESULT: all checks passed`,
including the live boot smoke test that had been skipping for lack of dependencies:**
`vercel/app.py` imports, `vercel/api/index.py` imports with the Gradio mount,
`vercel/api/gumroad-webhook.py` imports, `GET /api/health` → 200, `POST /api/pipeline`
rejects an empty body with 400. So gradio 4.44 boots on these pins, confirmed by running it
rather than by reading version numbers.

### Steps 3–6 — not started

Step 3 (Supabase Auth wiring) is unblocked: §B5 is answered and the JWKS path
is in place.
