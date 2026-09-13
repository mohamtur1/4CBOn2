# OAuth + login setup — exact click-paths

For step 3 (auth wiring). Written so it can be executed without knowing the
Google Cloud or Supabase UI. Nothing here has been run — these are console
steps only Malik can do, so treat every "expected result" as something to
confirm, not as something already verified.

---

## ⚠️ One correction before you start

Your message said: *"Create the Google Cloud OAuth client with
`https://app.4cbon.com/auth/callback` as the authorized redirect URI."*

**With Supabase Auth handling the flow, that URI will not work.** Google never
redirects to your app. The sequence is:

```
browser → app.4cbon.com  →  Supabase /auth/v1/authorize?provider=google
        →  Google sign-in
        →  https://<project-ref>.supabase.co/auth/v1/callback   ← what Google redirects to
        →  Supabase mints the session
        →  back to app.4cbon.com
```

So the URI you register in Google Cloud is **Supabase's** callback, not your
domain's. Registering only `app.4cbon.com/auth/callback` produces
`redirect_uri_mismatch` and the login button appears broken.

Register **both** anyway — Supabase's is required, and yours costs nothing and
covers a future custom-domain or non-Supabase flow:

```
https://<project-ref>.supabase.co/auth/v1/callback
https://app.4cbon.com/auth/callback
```

You get `<project-ref>` from Supabase → **Project Settings → General →
Reference ID**, or straight from the project URL
(`https://<project-ref>.supabase.co`).

---

## Part A — Google Cloud Console

### A1. Create or pick a project
1. https://console.cloud.google.com
2. Sign in with the Google account that owns `mohamtur1@gmail.com`.
3. Top bar → project dropdown → **New Project**.
4. Name: `4CBON2`. Click **Create**. Wait for the notification, then select it
   in the same dropdown.

### A2. Configure the OAuth consent screen
This step is skipped by most guides and is why logins silently fail later.

1. Left menu → **APIs & Services → OAuth consent screen**.
   (Google has been renaming this to **Google Auth Platform → Overview**; if
   you land on a "Get started" page, click through it.)
2. **User type: External** → **Create**. Internal is only for Google Workspace
   domains and will refuse `@gmail.com` users.
3. Fill in:
   - App name: `4CBON2`
   - User support email: `mohamtur1@gmail.com`
   - Developer contact email: `mohamtur1@gmail.com`
   - Leave **Application home page** blank for now, or use `https://app.4cbon.com`.
4. **Save and Continue** through Scopes (none needed — Supabase asks for
   `openid email profile` itself) and through Test users.
5. **Audience / Test users**: while the app's publishing status is **Testing**,
   *only* users you list here can sign in. Click **Add users** and add
   `mohamtur1@gmail.com` plus anyone you want to trial it.
   To open it to everyone: **Audience → Publishing status → Publish app**.
   Note this is the single most common cause of "it works for me but not for
   the customer."

### A3. Create the OAuth client ID
1. Left menu → **APIs & Services → Credentials**.
2. **+ Create Credentials → OAuth client ID**.
3. **Application type: Web application**.
4. Name: `4CBON2 Supabase Auth`.
5. **Authorized JavaScript origins** — add:
   ```
   https://app.4cbon.com
   https://<project-ref>.supabase.co
   ```
6. **Authorized redirect URIs** — add both from the correction above:
   ```
   https://<project-ref>.supabase.co/auth/v1/callback
   https://app.4cbon.com/auth/callback
   ```
   Exact match, including `https://` and no trailing slash. A mismatch here is
   the other common cause of `redirect_uri_mismatch`.
7. **Create**. A dialog shows the **Client ID** and **Client secret**. Copy
   both now — the secret is shown in full only here (you can regenerate later,
   which invalidates the old one).

---

## Part B — Supabase

### B1. Turn on the Google provider
1. https://supabase.com/dashboard → select your project.
2. Left sidebar → **Authentication** → **Providers**
   (on some layouts: **Sign In / Up → Social providers**).
3. Find **Google** → toggle **Enable Sign in with Google**.
4. Paste the **Client ID** and **Client secret** from A3.
5. Leave **Authorized Client IDs** empty unless Google shows you more than one.
6. **Save**.

### B2. Turn on email/password
1. Same **Providers** list → **Email**.
2. **Enable Email provider**: on.
3. **Confirm email**: your choice. On = the user must click a link before the
   account is usable, which cuts fake signups. Off = they can run immediately.
   Recommendation: **on**, since the free tier is only 3 runs anyway and you
   want real addresses.
4. **Save**.

### B3. Set the redirect targets
1. **Authentication → URL Configuration**.
2. **Site URL**: `https://app.4cbon.com`
3. **Redirect URLs** → add:
   ```
   https://app.4cbon.com/**
   http://localhost:3000/**
   ```
   Supabase only redirects a finished login to a URL on this list. Missing
   this drops the user on a Supabase-hosted page after sign-in.

### B4. Copy the credentials the gate needs
1. **Project Settings → API**.
2. Copy:
   - **Project URL** → Vercel `SUPABASE_URL`
   - **service_role** key (secret) → Vercel `SUPABASE_SERVICE_ROLE_KEY`
   - **anon / publishable** key → used by the browser for sign-in only. It is
     safe in client code; RLS is what protects the data.
3. **Do not** put the service_role key anywhere except Vercel env vars and the
   Space secret.

### B5. JWT signing scheme — RESOLVED: asymmetric, JWKS path implemented

Confirmed from your console on 2026-09-11:

| Key | Type | Status |
| --- | --- | --- |
| `7bac5d81-9339-48e0-bc53-6c582eba28d1` | **ECC (P-256)** → ES256 | **Current** |
| `4f52f33b-e918-4296-8905-cbe9ecf2caef` | Legacy HS256 (shared secret) | Valid only for tokens issued **before** the switch, until they expire |

So there is no shared secret to verify new tokens against. **The JWKS path is
now implemented** — `gate_core.py` verifies ES256 (and RS256) against the
published JWKS, matching on `kid`, cached for one hour, and refreshes once on
an unknown `kid` so a key rotation does not lock anyone out. Covered by
`test_jwt_jwks.py` (33 checks).

**Your question about the legacy HS256 key: no special handling is needed, and
here is the reasoning rather than just the answer.**

Supabase access tokens are short-lived — one hour by default. The legacy key is
valid only for tokens issued before the switch, so its useful window closed at
most one hour after you flipped it. Since your console already shows ECC as
current, that window has passed for anyone who has signed in since.

I still implemented HS256 support anyway, because it costs nothing and removes
a failure mode: if `SUPABASE_JWT_SECRET` is set, legacy tokens verify; if it is
absent, HS256 is refused outright. It is **not** a weakened path — the algorithm
decides where the key comes from, never the reverse, so there is no code path
in which a JWKS public key can be used as an HMAC secret. That specific attack
(alg relabelled to HS256, signed with the public key, which is public
information) is tested and rejected.

**Recommendation: leave `SUPABASE_JWT_SECRET` unset in Vercel.** With no secret
configured, only JWKS-verified ES256 tokens are accepted, which is the strictest
configuration and the one that matches where the project actually is. Set it
only if you hit a transition-window 401 for a user who signed in just before the
switch — and clear it again once their token has rolled over.

### B6. The JWKS URL

Derived automatically from `SUPABASE_URL` as:

```
https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
```

Nothing to configure. Override with `SUPABASE_JWKS_URL` only if Supabase moves
it.

**CONFIRMED 2026-09-12** against the live project
(`olnnmmuvpehjqkvrhypn.supabase.co`). The response is:

```json
{"keys":[{"alg":"ES256","crv":"P-256","ext":true,"key_ops":["verify"],
  "kid":"7bac5d81-9339-48e0-bc53-6c582eba28d1","kty":"EC","use":"sig",
  "x":"dqSAZhszxM3hbzi-F-iGpM1B9FK5WjUCs-nlscxdYWg",
  "y":"O_Y0soTa_n-Eskak7kEnJUAPiqS8G9_Yvfm_08I1D80"}]}
```

`kid`, `kty: EC`, `crv: P-256` and `alg: ES256` all match the values the
verification path was built and tested against, so no code change is needed and
`SUPABASE_JWKS_URL` can be left unset.

If Supabase ever rotates the key, the gate refreshes its cache once on an
unknown `kid` and picks the new one up without a redeploy.

---

### B7. Space framing headers — CONFIRMED, iframe is viable

Checked 2026-09-12 against `https://mangathpup-4cbon2-app.hf.space`. The
response carries **no `X-Frame-Options`** and **no `Content-Security-Policy`
with `frame-ancestors`**, so the Space can be framed by `app.4cbon.com`.

This settles the step-5 decision in favour of an iframe over a full-page
redirect: the visitor stays on our origin, the session cookie is first-party,
and the hand-off is a `?pass=` on the frame source rather than a navigation.

Two caveats that a header check cannot settle:

* Hugging Face can add framing headers at the proxy layer at any time. Step 5
  should degrade gracefully to a redirect if the frame fails to load rather
  than assume this stays true.
* Cold start was measured informally at ~6 s first load and ~3 s warm — not a
  deep-sleep wake. A frame that sits blank for six seconds needs a loading
  state, and a real cold boot may be slower than this.

---

## Part C — Vercel environment variables

**Project → Settings → Environment Variables**, applied to Production
(and Preview if you test there). Redeploy after adding them; Vercel does not
pick up new env vars on a running deployment.

| Variable | Value | Notes |
| --- | --- | --- |
| `SUPABASE_URL` | Project URL from B4 | |
| `SUPABASE_SERVICE_ROLE_KEY` | service_role key | secret |
| `SUPABASE_ANON_KEY` | anon key from B4 | **not** secret, but never sent to the browser — see Part E |
| `PUBLIC_URL` | `https://app.4cbon.com` | no trailing slash; must match B3 exactly |
| `COOKIE_SECURE` | `true` | set `false` **only** to test on `http://localhost` |
| `SUPABASE_JWT_SECRET` | legacy JWT secret | **leave unset** — see B5. Set only to cover the transition window |
| `SUPABASE_JWKS_URL` | *(optional)* | only if Supabase's JWKS is not at the default path in B6 |
| `GATE_SHARED_SECRET` | see below | secret, **≥ 32 chars** — the gate refuses to sign with anything shorter |
| `SPACE_URL` | `https://<user>-<space>.hf.space` | no trailing slash |
| `GUMROAD_URL` | `https://4175358678144.gumroad.com/l/tbphpi` | |
| `ADMIN_EMAIL` | `mohamtur1@gmail.com` | break-glass admin override |
| `GUMROAD_WEBHOOK_SECRET` | random string | secret |

Generate the two secrets with:

```bash
python3 -c "import secrets; print(secrets.token_urlsafe(48))"
```

Run it twice — one value for `GATE_SHARED_SECRET`, a **different** one for
`GUMROAD_WEBHOOK_SECRET`.

**Correction to an earlier note:** `GATE_SHARED_SECRET` must **not** be added to
the HF Space. The Space does not verify pass signatures — it spends passes by
calling `POST /api/consume`, and the gate verifies them. Only Vercel holds the
signing key, which is the whole point: a Space that could verify passes could
also forge them, and the gatekeeper would be decorative. See Part G for what
the Space actually needs.

---

## Part D — Verify, in order

Run these after the deploy. Each one isolates a different layer, so the first
failure tells you which part is wrong.

```bash
# 1. The gate is up and sees its config. All four must be true.
curl -s https://app.4cbon.com/api/gate/health | python3 -m json.tool

# 2. No token -> 401 login_required. Proves the route reaches the gate
#    function and not the Gradio catch-all.
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://app.4cbon.com/api/pass

# 3. A junk token -> 401 invalid_session, not 500.
curl -s -X POST https://app.4cbon.com/api/pass \
  -H 'Authorization: Bearer not.a.token'

# 4. A real session. Sign in at app.4cbon.com (Google or email+password).
#    The session lives in an httpOnly cookie, so it is NOT in Local Storage and
#    JavaScript cannot read it. Grab it from devtools -> Application -> Cookies
#    -> app.4cbon.com -> cbon_session, then:
curl -s -X POST https://app.4cbon.com/api/pass \
  -H "Cookie: cbon_session=$CBON_SESSION" | python3 -m json.tool
#    Expect: allowed true, remaining 3, a pass, and a url pointing at the Space.
#
#    Or, without touching devtools at all — the browser flow the page uses:
curl -s -c /tmp/jar -X POST https://app.4cbon.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}'
curl -s -b /tmp/jar -X POST https://app.4cbon.com/api/pass | python3 -m json.tool
#    /api/pass reads the cookie itself; an expired access token inside it is
#    refreshed once, transparently, and the renewed cookie comes back.

# 5. Spend that pass and watch the counter move.
curl -s -X POST https://app.4cbon.com/api/consume \
  -H 'Content-Type: application/json' \
  -d '{"pass":"<paste>","feature":"ask","run_id":"manual-1"}'

# 6. Replay it. Expect 409 pass_already_used.
#    (repeat the command above unchanged)
```

Then in Supabase → **SQL Editor**:

```sql
select subject, run_date, run_count from run_usage order by run_date desc limit 5;
select jti, subject, feature, consumed_at from gate_passes order by issued_at desc limit 5;
select * from admin_emails;
```

Signing in as `mohamtur1@gmail.com` should produce `unlimited: true` and **no**
row in `run_usage` at all.

---

## Part E — The auth endpoints (`vercel/api/auth.py`)

Sign-in runs entirely on Vercel. The Space never sees a password or an OAuth
token; it only ever receives a signed pass carrying the verified email.

| Method + path | What it does |
| --- | --- |
| `POST /api/auth/signup` | `{email, password}` → creates the account, signs in, sets the cookie |
| `POST /api/auth/login` | `{email, password}` → signs in, sets the cookie |
| `GET /api/auth/google` | 302 to Supabase's `/auth/v1/authorize` with a fresh PKCE pair |
| `GET /api/auth/callback` | Supabase's `redirect_to` target; exchanges `?code=` for a session |
| `POST /api/auth/refresh` | renews the access token from the refresh token |
| `POST /api/auth/logout` | clears the cookie |
| `GET /api/auth/session` | who is signed in, plus `remaining`/`unlimited` |
| `GET /api/auth/health` | which env vars this deployment can see (no values) |

Four properties worth knowing before you debug anything:

1. **The anon key never reaches the browser.** Every Supabase Auth call is made
   server-side by the function. There is no `@supabase/supabase-js` on the page
   and no key in `index.html` — `test_auth.py` asserts both.
2. **The session is an httpOnly, Secure, SameSite=Lax cookie** (`cbon_session`),
   not Local Storage. Page scripts cannot read it, which is why `/api/pass`
   accepts the cookie directly. This is what makes the devtools step in D4
   necessary.
3. **Google uses `flow_type=pkce`.** Supabase redirects back with `?code=`,
   never with tokens in the URL fragment, so nothing sensitive lands in browser
   history or a `Referer` header.
4. **Errors are split three ways, deliberately.**

| Situation | Status | Why |
| --- | --- | --- |
| wrong password / unknown address | `401 invalid_credentials` | identical for both — telling them apart is an account-enumeration oracle |
| Supabase unreachable, or a 5xx from it | `503 auth_unavailable` | **not** 401: blaming the user's password for an outage sends them to reset a password that was never wrong, and the cookie is left alone so nobody is signed out |
| env var missing | `503 auth_unavailable` | a deploy mistake must not read as bad credentials |
| anonymous visitor on `/api/auth/session` | `200 authenticated:false` | that route is a question, not a gate |

`COOKIE_SECURE=true` is the default. Browsers refuse `Secure` cookies over plain
http, so local testing needs `COOKIE_SECURE=false` — on production it must stay
`true`.

---

## Part F — Gumroad Ping, and the one paying customer

**Status confirmed 2026-09-12:** the Ping endpoint field in Gumroad → Settings →
Advanced was **empty**. The webhook has therefore never been called, not even
once, and nothing needs migrating. It is a clean slate — fill it in only after
the new endpoint is deployed with a real `GUMROAD_WEBHOOK_SECRET`, so it is set
once, to the final URL.

### F1. What to enter

**Gumroad → Settings → Advanced → Ping → Ping endpoint:**

```
https://app.4cbon.com/api/gumroad-webhook?secret=<GUMROAD_WEBHOOK_SECRET>
```

The secret travels in the query string because that is the only channel a Ping
offers. It can therefore appear in request logs, so treat it as rotatable.
Your `seller_id` is `eMG0EQSBBmGIfAVlmsTVhA==`; the handler ignores it, but it
is a useful cross-check that a ping really came from this account.

### F2. The problem the Ping alone does not solve

4CBON Pro has **one real active subscriber** — $9/month, renewed 2 June,
2 July, 3 August and 3 September. Because the webhook was never configured, the
database has never been told they exist, and `subscriptions` is empty.

Their next renewal is 3 October. Waiting for the webhook would put the
project's only paying customer on the free tier the day enforcement goes live,
and they would hit the three-run wall with no explanation.

So after the first deploy, backfill from Gumroad's own API rather than waiting
for the next ping:

```bash
export GUMROAD_ACCESS_TOKEN=...        # Settings -> Advanced -> Applications
export SUPABASE_URL=https://olnnmmuvpehjqkvrhypn.supabase.co
export SUPABASE_SERVICE_ROLE_KEY=...

python3 tools/reconcile_subscribers.py            # prints the diff, writes nothing
python3 tools/reconcile_subscribers.py --apply    # writes it, source='api'
```

That same tool is the ongoing authority. Gumroad's own documentation says a
Ping payload is unsigned and that a delivery is dropped for good once its four
retries run out, so the ping is treated as a trigger that grants access
promptly, and this read-back is what corrects the record. Run it after every
deploy and then on a schedule — daily is enough.

`subscriptions.source` records which path last wrote a row: `'ping'` for an
unsigned Ping, `'api'` for a confirmed read-back.

### F3. Testing it without a real charge

Gumroad sends `test=true` when the seller buys their own product. The handler
records and applies those like any other ping, and marks `is_test=true` in
`gumroad_pings`, so the end-to-end path can be exercised without a real
payment. A reconciliation run afterwards corrects anything a test left behind.

---

## Part G — HF Space secrets (step 5)

**Space → Settings → Variables and secrets.** The Space spends passes; it never
decides entitlement and holds no signing key.

| Variable | Value | Notes |
| --- | --- | --- |
| `FOURCBON2_GATE_URL` | `https://app.4cbon.com` | no trailing slash. **Without it every run is blocked** — the gate fails closed |
| `FOURCBON2_GATE_FAIL_OPEN` | *(leave unset)* | defaults to `false`. Setting it `true` allows runs while the gate is down — uncounted, so only for a declared outage |
| `FOURCBON2_GATE_TIMEOUT` | *(optional)* | seconds to wait for `/api/consume`; default 8 |
| `FOURCBON2_HOME_URL` | *(optional)* | where "sign in" links point; default `https://app.4cbon.com` |

Restart the Space after adding them — Gradio reads the environment at boot.

To check the wiring without a browser, from any machine:

```bash
# Should be refused: no pass, and the gate is reachable.
curl -s https://<user>-<space>.hf.space/api/consume-probe 2>/dev/null   # not a real route

# The real check is behavioural: open app.4cbon.com, sign in, run once, and
# watch the gate's own log line appear in the Vercel function logs:
#   [gate] ask: denied ...        (or a 200 with no log line)
```

If a run is blocked with **"This Space is not connected to its run gate"**,
`FOURCBON2_GATE_URL` is unset on the Space. If it is blocked with **"The run
gate is unreachable"**, the Space reached Vercel but Vercel could not reach
Supabase — check `SUPABASE_URL` and the service-role key on Vercel, not the
Space.

---

## Failure → cause

| Symptom | Almost always |
| --- | --- |
| `redirect_uri_mismatch` | The Supabase callback URL is not in Google's authorized redirect URIs, or has a trailing slash |
| `access_blocked:unregistered` | Wrong OAuth client ID, or the consent screen project isn't the one the client lives in |
| Works for you, `access_denied` for everyone else | Consent screen is still in **Testing** and they are not a test user (A2.5) |
| `401 invalid_session` on a real token | `SUPABASE_JWT_SECRET` is wrong, or the project is on asymmetric signing (B5) |
| `503 gate_unavailable` | `GATE_SHARED_SECRET` missing or under 32 chars, or Supabase is unreachable from the function |
| `402` immediately for a new user | A `run_usage` row already exists for that email from testing — `delete from run_usage where subject = 'you@example.com';` |
| Login lands on a Supabase page | The return URL is not in **Redirect URLs** (B3.3) |
| `503 auth_unavailable` at sign-in | Supabase Auth is unreachable or an env var is missing — **not** a wrong password |
| A paying customer is on the free tier | `subscriptions` has no `active` row for them; run `tools/reconcile_subscribers.py` (F2) |
| A refunded customer still has unlimited | A ping was dropped after its four retries; run `tools/reconcile_subscribers.py` |
| Gumroad shows failed Ping deliveries | Check the secret in the URL, then `select * from gumroad_pings order by received_at desc limit 10;` |
| "This Space is not connected to its run gate" | `FOURCBON2_GATE_URL` is unset on the **Space** (Part G) |
| "The run gate is unreachable" on a run | Vercel could not reach Supabase — check the Vercel env, not the Space |
| First run works, every later run says "pass already spent" | The postMessage pass hand-off is not firing — check the browser console on app.4cbon.com |
| The app frame stays blank | The Space has started sending framing headers; use "Open in a new tab" |
