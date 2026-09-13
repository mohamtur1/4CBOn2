# 4CBON2 — Handover

Everything needed to deploy and operate the run-limit system, in the order it
has to happen. Written so it can be followed without reading the conversation
that produced it.

Companion documents:

- **`OAUTH_SETUP.md`** — exact Google Cloud and Supabase click-paths, the full
  env-var tables, and the verification commands. This file sequences the work;
  that one details each step.
- **`DRAFT_PROMPT.md`** — the approved specification and the rulings behind it.

---

## 1. What was built, in one paragraph

The Space had no authentication at all and its only limit was a per-browser
counter that reset on every reload. Now: the HF Space stays the compute
backend, and Vercel is a thin gatekeeper that authenticates the visitor,
checks Supabase, mints a short-lived signed pass, and receives the Gumroad
webhook. The Space spends passes; it never decides entitlement and holds no
signing key. Login is mandatory for all three AI features, which draw from one
shared pool of **3 runs per day per account**. Landing, About and Diagnostics
stay open. There is no anonymous IP-metered fallback, by ruling — it would
reopen the VPN/NAT weakness this exists to close.

---

## 2. Ruling #4 — run history is an explicit, named follow-up

**Per-account run history is deliberately NOT in this project.** It is a named
follow-up, out of scope here, and this section exists so that decision is not
mistaken for an oversight later.

What was built instead, and why it is not the same thing:

| Table | Holds | Is it history? |
| --- | --- | --- |
| `run_usage` | one row per account per day: `subject`, `run_date`, `run_count` | No. A single counter, overwritten in place. It cannot tell you *when* within the day a run happened, or which feature used it. |
| `gate_passes` | `jti`, `subject`, `feature`, `issued_at`, `consumed_at` | Partially, and accidentally. It is a replay-protection log, not an audit trail. It has no retention policy and no index for reporting. |

`run_usage` has **no `feature` column on purpose** — adding one would have split
the shared 3-run pool into three separate pools, which is the opposite of what
was asked for. That decision is why per-feature history cannot be reconstructed
from it, and why the follow-up needs its own table.

### What the follow-up would need

A separate append-only table (working name `run_history`) written at the same
point `/api/consume` spends a pass, carrying at minimum: timestamp, subject,
feature, the pass `jti`, whether it was allowed, and the denial code when it
was not. Roughly:

```sql
create table if not exists run_history (
  id          bigserial primary key,
  subject     text        not null,
  feature     text        not null,
  jti         text,
  allowed     boolean     not null,
  code        text,
  created_at  timestamptz not null default now()
);
create index on run_history (subject, created_at desc);
-- anon read revoked, same migration, per ruling #3
```

Two things to settle before building it:

1. **Retention.** Unbounded growth of a table with one row per run per user.
2. **Privacy.** It ties a run to an identifiable account and a timestamp. That
   is personal data in a way the current counter is not.

Neither was in scope, and neither should be decided by whoever picks this up
without asking.

---

## 3. What you need to do manually, in order

Do these in this order. Each step's verification is what tells you the next one
is worth starting.

### Step 1 — Supabase: run the migration

Paste **`supabase/4cbon2.sql`** into Supabase → SQL Editor and run it.
(`vercel/supabase.sql` contains the identical canonical block; `test_sql_sync.py`
fails if the three copies drift, so either works.)

It creates `run_usage`, `admin_emails`, `gate_passes`, `gumroad_pings`, the
functions `bump_run_usage`, `bump_run_limit`, `try_consume_run`, adds
`sale_timestamp` and `source` to `subscriptions`, seeds `mohamtur1@gmail.com`
into `admin_emails`, and revokes anonymous access on all of it.

The file is safe to paste more than once — policies are dropped before they are
created, and the column additions use `if not exists`.

**Verify:**

```sql
select count(*) from run_usage;          -- 0, but no permission error
select count(*) from gumroad_pings;      -- 0
select email, note from admin_emails;    -- mohamtur1@gmail.com
select p_allowed, p_count from try_consume_run('probe@example.com', current_date, 3);
```

The last one should return `t | 1`. Clean it up with
`delete from run_usage where subject = 'probe@example.com';`.

### Step 2 — Google Cloud OAuth client

Follow **`OAUTH_SETUP.md` Parts A and B**. You create the OAuth client and
enable the Supabase Google provider.

**Register both callback URLs** — this was a correction that matters.
Registering only the Supabase one breaks login:

```
https://olnnmmuvpehjqkvrhypn.supabase.co/auth/v1/callback
https://app.4cbon.com/auth/callback
```

Both must also be in Supabase → Authentication → URL Configuration →
**Redirect URLs**.

**Verify** — the JWKS is already confirmed, so this is a live check of the
provider, not of signing:

```bash
curl -s https://olnnmmuvpehjqkvrhypn.supabase.co/auth/v1/.well-known/jwks.json
```

Expect one key with `kid` `7bac5d81-9339-48e0-bc53-6c582eba28d1`,
`kty: EC`, `crv: P-256`, `alg: ES256`. **Confirmed 2026-09-12**, so this is a
regression check, not a discovery.

### Step 3 — Generate the two secrets

```bash
python3 -c "import secrets; print(secrets.token_urlsafe(48))"
```

Run it **twice**. One value for `GATE_SHARED_SECRET`, a **different** one for
`GUMROAD_WEBHOOK_SECRET`. `GATE_SHARED_SECRET` must be at least 32 characters —
the gate refuses to sign with anything shorter.

> **Do not** put `GATE_SHARED_SECRET` on the HF Space. The Space does not verify
> pass signatures; it calls `/api/consume`. A Space that could verify passes
> could forge them.

### Step 4 — Vercel environment variables

Vercel → Project → Settings → Environment Variables, applied to Production.
**Redeploy afterwards** — Vercel does not pick up new variables on a running
deployment.

| Variable | Value | Notes |
| --- | --- | --- |
| `SUPABASE_URL` | `https://olnnmmuvpehjqkvrhypn.supabase.co` | |
| `SUPABASE_SERVICE_ROLE_KEY` | service_role key | secret |
| `SUPABASE_ANON_KEY` | anon key | not secret, but never sent to a browser |
| `SUPABASE_JWT_SECRET` | *(leave unset)* | set only to cover a transition window |
| `SUPABASE_JWKS_URL` | *(leave unset)* | default path is correct |
| `GATE_SHARED_SECRET` | from step 3 | secret, ≥32 chars |
| `SPACE_URL` | `https://mangathpup-4cbon2-app.hf.space` | no trailing slash |
| `GUMROAD_URL` | `https://4175358678144.gumroad.com/l/tbphpi` | already seeded |
| `GUMROAD_WEBHOOK_SECRET` | from step 3 | secret |
| `ADMIN_EMAIL` | `mohamtur1@gmail.com` | already seeded |
| `PUBLIC_URL` | `https://app.4cbon.com` | must match step 2 exactly |
| `COOKIE_SECURE` | `true` | `false` only for localhost testing |

**Verify** — these need no credentials and isolate layers one at a time:

```bash
# 1. Gate is up and sees its config. All fields should be true.
curl -s https://app.4cbon.com/api/gate/health | python3 -m json.tool

# 2. Auth function is up and sees its config.
curl -s https://app.4cbon.com/api/auth/health | python3 -m json.tool

# 3. No credentials -> 401 login_required. Proves the route reaches the gate
#    function rather than the Gradio catch-all.
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://app.4cbon.com/api/pass

# 4. A junk token -> 401 invalid_session, not 500.
curl -s -X POST https://app.4cbon.com/api/pass -H 'Authorization: Bearer not.a.token'
```

Then a real session, the way the browser does it:

```bash
curl -s -c /tmp/jar -X POST https://app.4cbon.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}'

curl -s -b /tmp/jar -X POST https://app.4cbon.com/api/pass | python3 -m json.tool
# Expect: allowed true, remaining 3, a pass, and a url pointing at the Space.
```

The session is an **httpOnly cookie**, so it is not in Local Storage and
JavaScript cannot read it. That is deliberate; it is also why the devtools
shortcut is `Application → Cookies → cbon_session`.

### Step 5 — HF Space secrets, then redeploy the Space

Space → Settings → Variables and secrets.

| Variable | Value | Notes |
| --- | --- | --- |
| `FOURCBON2_GATE_URL` | `https://app.4cbon.com` | no trailing slash. **Without it every run is blocked.** |
| `FOURCBON2_GATE_FAIL_OPEN` | *(leave unset)* | defaults to `false`. Setting `true` allows uncounted runs during an outage — deliberately, never by accident |
| `FOURCBON2_GATE_TIMEOUT` | *(optional)* | seconds; default 8 |
| `FOURCBON2_HOME_URL` | *(optional)* | default `https://app.4cbon.com` |

`FOURCBON2_DATA_DIR`, `FOURCBON2_AGENT_DEADLINE` and `FOURCBON2_HEARTBEAT`
already exist and are unrelated to the gate.

**The Space must be redeployed.** `space_gemini/app.py` was regenerated by
`build_gemini_space.py`; the gate does not exist until the new file is pushed to
Hugging Face.

```bash
python3 build_gemini_space.py     # regenerates space_gemini/app.py, asserts every rewrite
```

**Restart the Space** after setting the secrets — Gradio reads the environment
at boot, not per request.

**Verify:** open `app.4cbon.com`, sign in, run once. A blocked run says why:

- *"This Space is not connected to its run gate"* → `FOURCBON2_GATE_URL` is unset **on the Space**.
- *"The run gate is unreachable"* → the Space reached Vercel but Vercel could not reach Supabase. Check the **Vercel** env, not the Space.
- *"Sign in to run this"* → no pass reached the Space.
- *"Daily limit reached"* → working as intended.

### Step 6 — Backfill the paying subscriber

The Gumroad Ping was never configured, so `subscriptions` is empty even though
4CBON Pro has one real subscriber who has renewed monthly since June. Their next
renewal is 3 October, so **without this step the only paying customer lands on
the free tier the day enforcement ships.**

Create an API application first: Gumroad → Settings → Advanced → Applications →
Create application.

```bash
export GUMROAD_ACCESS_TOKEN=...
export SUPABASE_URL=https://olnnmmuvpehjqkvrhypn.supabase.co
export SUPABASE_SERVICE_ROLE_KEY=...

python3 tools/reconcile_subscribers.py            # prints the diff, writes nothing
python3 tools/reconcile_subscribers.py --apply    # writes it, source='api'
```

**Read the dry-run diff before applying.** `fetch_sales()` has never reached the
live API, so the first real run is its first test.

**Verify:**

```sql
select email, status, source, sale_id, updated_at from subscriptions;
```

The subscriber should show `status='active'` and `source='api'`.

### Step 7 — Gumroad Ping URL

**Last**, once everything above is live, so it is only ever set once.

Gumroad → Settings → Advanced → **Ping → Ping endpoint**:

```
https://app.4cbon.com/api/gumroad-webhook?secret=<GUMROAD_WEBHOOK_SECRET>
```

The secret travels in the query string because that is the only channel a Ping
offers, so it can appear in request logs. Treat it as rotatable. Your
`seller_id` is `eMG0EQSBBmGIfAVlmsTVhA==`; the handler ignores it, but it is a
useful cross-check that a ping really came from this account.

**Verify** — a test purchase of your own product sends `test=true`, which the
handler records and applies like any other ping, marked `is_test=true`:

```sql
select sale_id, resource_name, email, is_test, received_at
from gumroad_pings order by received_at desc limit 10;
```

Then run `tools/reconcile_subscribers.py` again — it is the authority and will
correct anything a test left behind.

### Step 8 — End-to-end, as a real visitor

1. Sign up at `app.4cbon.com` with a throwaway address.
2. Run Ask once, the Rewriter once, Agent Mode once — all three should succeed.
3. A fourth run, on **any** feature, should be blocked with "Daily limit
   reached". That is the shared pool working.
4. Sign in as `mohamtur1@gmail.com` and confirm unlimited runs, and that **no
   row** appears in `run_usage` for it:

```sql
select subject, run_date, run_count from run_usage order by run_date desc limit 5;
select jti, subject, feature, consumed_at from gate_passes order by issued_at desc limit 5;
```

---

## 4. Ongoing operation

**Reconciliation is the source of truth, not the webhook.** Gumroad's own
documentation says a Ping payload is unsigned and that a delivery is dropped for
good once its four retries run out. The ping grants access promptly; the API
read-back corrects the record.

Run this after every deploy, then on a schedule — daily is enough:

```bash
python3 tools/reconcile_subscribers.py --apply
```

A paying customer who shows up on the free tier is almost always a dropped ping,
and this fixes it without anyone needing to understand why.

**Bundle size.** Vercel bundles every installed package into each function
against a 500 MB cap. After changing anything in `vercel/requirements.txt`:

```bash
python3 -m venv /tmp/m && /tmp/m/bin/pip install -r vercel/requirements.txt
python3 tools/measure_bundle.py /tmp/m/lib/python3.11/site-packages
```

Current: **401.8 MB shipped, ~422.6 MB in Vercel's accounting, ~77 MB margin.**

Do not use `du -sh` for this. A fresh venv is ~540 MB on disk, but ~112 MB is
`__pycache__` and ~26 MB is `tests/`, neither of which ships. Counting them
overstates the bundle by ~35% and produces a false "over budget" alarm — that
false alarm already happened once during this project.

---

## 5. Reference

### Endpoints

| Method + path | Purpose |
| --- | --- |
| `POST /api/pass` | mint a pass for the signed-in visitor |
| `POST /api/consume` | spend a pass and move the counter |
| `GET /api/gate/health` | gate config, no credentials needed |
| `POST /api/auth/{signup,login,refresh,logout}` | session lifecycle |
| `GET /api/auth/google` | 302 to Supabase with a fresh PKCE pair |
| `GET /api/auth/callback` | Supabase's `redirect_to` target |
| `GET /api/auth/session` | who is signed in, plus `remaining`/`unlimited` |
| `GET /api/auth/health` | auth config, no credentials needed |
| `POST /api/gumroad-webhook?secret=…` | Gumroad Ping receiver |

### Denial codes

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `unknown_feature`, `invalid_json` | the request was malformed |
| 401 | `login_required`, `invalid_session`, `session_expired`, `invalid_credentials`, `not_signed_in`, `signup_failed`, `oauth_denied`, `oauth_incomplete`, `oauth_exchange_failed` | identity problem |
| 402 | `daily_limit_reached` | the shared pool is empty; carries `upgrade` |
| 403 | `invalid_pass` | the signature did not verify |
| 409 | `pass_already_used` | replay |
| 503 | `gate_unavailable`, `entitlement_unavailable`, `auth_unavailable` | **our** fault, not the visitor's |

The 401/503 split is deliberate. Reporting an outage as bad credentials sends
the user to reset a password that was never wrong, and clearing the session
cookie would sign everyone out at once.

### Constants

`FREE_RUNS_PER_DAY = 3` · pass TTL 900 s · JWKS cache 3600 s ·
valid features `ask`, `rewriter`, `agent` · admin override env `ADMIN_EMAIL`.

### Tests

The `/tmp` venvs are wiped between sessions, so rebuild first:

```bash
python3 -m venv /tmp/gateenv && /tmp/gateenv/bin/pip install \
  "fastapi==0.115.0" httpx requests cryptography "pydantic==2.9.2" \
  "supabase==2.7.4" pgserver "python-multipart==0.0.9" \
  "google-generativeai==0.8.3" packaging
```

| Suite | Checks | What it covers |
| --- | --- | --- |
| `test_sql_sync.py` | — | the three SQL copies have not drifted |
| `test_jwt_jwks.py` | 33 | JWT verification, the algorithm-confusion attacks |
| `test_gate.py` | 80 | metering, against real PostgreSQL |
| `test_auth.py` | 72 | auth flows, against a fake IdP signing ES256 over real HTTP |
| `test_webhook.py` | 47 | Gumroad Ping semantics |
| `test_space_gate.py` | 46 | the Space-side gate, exec'd from the build's own sections |
| `test_supabase_schema.py` | 62 | the migration, against real PostgreSQL 16.2 |
| `test_vercel_package.py` | — | bundle rules and a live boot |

Run every one of them before merging. A `[SKIP]` banner means the venv is
missing a dependency, not that the code passed.

---

## 6. What is not verified

Stated plainly, because each of these is a real risk rather than a formality.

- **The Gradio app has never booted with the gate.** It needs the Space's heavy
  dependency tree plus network access. The gate functions are executed for real
  in `test_space_gate.py` and the wiring is AST-verified on the generated file,
  but no browser has rendered a blocked run.
- **The postMessage pass hand-off has not run end to end.** Both halves are
  covered individually; the actual iframe↔parent exchange has not happened. The
  symptom if it is broken is specific: the first run works, and every later run
  says "pass already spent".
- **`reconcile_subscribers.py` has never reached the live Gumroad API.**
  `decide()` is unit-tested; `fetch_sales()` pagination is not.
- **No Gumroad ping has ever been received**, so the webhook has never met a
  real request. The field set comes from Gumroad's documentation, not a capture.
- **The landing page has never been loaded in a browser.** Its JavaScript is
  syntax-checked and its endpoint list is cross-checked against the live route
  table, so a renamed route fails the tests — but click behaviour is untested.
- **Framing headers are a point-in-time observation.** Hugging Face sent no
  `X-Frame-Options` and no CSP `frame-ancestors` on 2026-09-12, which is what
  makes the iframe viable. Those are set at the proxy layer and can appear at
  any time; the redirect fallback is kept for that reason.
- **Cold start** was measured informally at ~6 s first load, ~3 s warm — not a
  deep-sleep wake. A real cold boot may be slower.

---

## 7. Standing constraints

These were decided deliberately and should not be undone casually.

- Gate code goes in `build_src/handwritten.py`, **never** hand-edited into the
  generated `space_gemini/app.py`.
- `FOURCBON2_GATE_FAIL_OPEN` defaults to `false`. Fail closed.
- Login is mandatory for all three features. **Do not** add an anonymous
  IP-metered fallback — it reopens the VPN/NAT weakness.
- All three features share one pool. Do not add a `feature` column to
  `run_usage`.
- Anon read stays revoked on `subscriptions`, `run_limits`, `run_usage` and
  every new table, in the same migration that creates it.
- Never expose the admin notebook or the service-role key.
- Never add `chromadb`, `torch`, `sentence-transformers`, `plotly`, `fpdf2`,
  `duckduckgo-search`, `PyPDF2`, `python-docx` or `beautifulsoup4` to
  `vercel/requirements.txt`. Keep `huggingface-hub` pinned below 1.0.
- After any `vercel/` dependency change, run `test_vercel_package.py` and
  re-measure with `tools/measure_bundle.py`.
- Any DNS change is approved personally, not by an agent.
