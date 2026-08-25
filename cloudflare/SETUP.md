# Cloudflare Worker + DNS — 4CBON2 on Hugging Face Spaces

This guide puts your own domain in front of the two public Hugging Face Spaces:

| URL              | Backend Space                           | What it serves            |
| ---------------- | --------------------------------------- | ------------------------- |
| `https://app.4cbon.com` | `mohamtur1/4cbon2-app` (Gradio)   | The public **app**        |
| `https://4cbon.com`     | `mohamtur1/4cbon2-static` (Static) | The public **landing page** |

The Worker in `worker.js` proxies both Spaces and rewrites `Host` / `Set-Cookie`
/ `Location` headers so the apps work on your domain. Gradio WebSockets
(`/queue/join`) are proxied too.

---

## 1. Prerequisites

- The two Spaces are live (see `../deploy_hf_spaces.py`).
- A Cloudflare account with `4cbon.com` added as a zone
  (or you create the zone for the first time).
- The Cloudflare CLI (`wrangler`) or the Cloudflare dashboard.
  Install wrangler: `npm i -g wrangler` and run `wrangler login`.

> **Update the origins** in `worker.js` (or set them as Worker bindings) to the
> real `*.hf.space` URLs for **your** account. The defaults assume username
> `mohamtur1`. If the token's username differs, run the deploy script and copy
> the actual URLs it prints.

---

## 2. Deploy the Worker

### Option A — Wrangler (CLI)

1. Create `wrangler.toml` in this folder:

```toml
name = "4cbon2-spaces"
main = "worker.js"
compatibility_date = "2026-08-25"

# Optional bindings — uncomment and adjust to override the defaults in worker.js:
# [vars]
# ROUTE_DOMAIN = "4cbon.com"
# HF_APP_ORIGIN = "https://mohamtur1-4cbon2-app.hf.space"
# HF_STATIC_ORIGIN = "https://mohamtur1-4cbon2-static.hf.space"
```

2. Deploy:

```bash
cd cloudflare
wrangler deploy
```

### Option B — Dashboard

1. Cloudflare dashboard → **Workers & Pages** → **Create** → **Worker**.
2. Replace the generated code with the contents of `worker.js`.
3. **Save and Deploy**.

---

## 3. Connect your domain (DNS)

Recommended: use **Worker custom domains** (Cloudflare creates the DNS + SSL for you).

1. Open your deployed Worker → **Settings** → **Domains & Routes** → **Add Custom Domain**.
2. Add **`4cbon.com`** (serves the landing page).
3. Add **`app.4cbon.com`** (serves the app).
4. Cloudflare automatically:
   - creates the DNS `A` records,
   - issues / applies an SSL cert,
   - adds the route so both hosts hit the Worker.

Manual alternative (if you prefer explicit DNS):

1. **DNS** tab → **Add record** for `app`:
   - Type `A`, Name `app`, content `192.0.2.1` (placeholder),
     Proxy status **Proxied (orange cloud)**. (Workers ignore the IP when a route matches.)
2. Add a route in **Workers → your worker → Settings → Routes**:
   - Pattern `app.4cbon.com/*` → the worker.
   - Pattern `4cbon.com/*` → the worker (landing page).
3. If the zone is brand new, set Cloudflare's nameservers at your domain registrar
   (Settings → Domains shows the two nameservers Cloudflare assigned, e.g.
   `ada.ns.cloudflare.com`). Update the `NS` records at your registrar and wait
   for propagation (can take minutes to 24h).

---

## 4. Verify

- `https://4cbon.com` → the static landing page.
- `https://app.4cbon.com` → the Gradio app UI.
- In the app, type in the **Ask a Question** / **Agent Mode** tabs and confirm
  the streaming output and the request queue work (WebSocket path).

---

## 5. Notes & caveats

- **Keep the `hf.space` URLs working too** while you test — they are the origin
  and are the source of truth.
- **Don't hardcode secrets in the Worker.** The app reads the user's own
  `HF_TOKEN` from the UI; nothing secret needs to be in `worker.js` or
  `wrangler.toml`.
- If you later rename the Spaces/account, update `HF_APP_ORIGIN` and
  `HF_STATIC_ORIGIN` (bindings) — no code change needed.
- The proxy passes through the public internet to `*.hf.space`; latency is
  acceptable for a public demo. For production at scale, consider pinning
  requests or moving the backend behind a fixed origin.

## 6. Rollback

- To stop routing through the Worker, set the DNS records to **DNS only (grey
  cloud)** / remove the routes, or delete the custom domains. The Spaces
  themselves are unaffected and remain reachable at their `hf.space` URLs.
