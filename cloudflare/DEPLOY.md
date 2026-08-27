# Deploy `4cbon-proxy` (Cloudflare dashboard)

This Worker proxies the custom domains to Hugging Face Spaces. **Do not change DNS records or Worker route bindings.**

| Host | Upstream |
| --- | --- |
| `4cbon.com` / `www.4cbon.com` | `https://mangathpup-4cbon2-static.static.hf.space` |
| `app.4cbon.com` | `https://mangathpup-4cbon2-app.hf.space` |

Source of truth: [`worker.js`](worker.js) (ES module).

---

## 1. Worker code

Open [`worker.js`](worker.js) and copy the **entire** file. It must start with the comment block and end with `};`.

It is an **ES module**. It uses `export default { async fetch(request) { ... } }`.

Do **not** wrap it in `addEventListener("fetch", ...)`. That is the legacy Service Worker format and Cloudflare will fail the deploy with a generic “Something went wrong”.

Do **not** use this (it is the old, broken version):

```javascript
const STATIC_SPACE = 'https://mangathpup-4cbon2-static.hf.space'; // WRONG host
headers: { ...request.headers, Host: ... }                       // causes HF 403
```

---

## 2. Dashboard steps

1. Sign in at [https://dash.cloudflare.com](https://dash.cloudflare.com).
2. Open **Workers & Pages**.
3. Click the existing Worker **`4cbon-proxy`** (do not create a new one).
4. Click **Edit code** (or the source editor).
5. Confirm the editor is in **Module** / ES module mode, not Service Worker.
   - If you see `addEventListener("fetch", ...)` as a template, switch the Worker type to **module** or delete that template first.
6. Select all existing code → delete it.
7. Paste the full contents of `cloudflare/worker.js`.
8. Click **Save and deploy**.
9. Wait until the UI shows the new version is live (usually a few seconds).
10. **Do not** edit **Triggers → Routes**. Leave:
    - `*4cbon.com/*`
    - `*app.4cbon.com/*`
11. **Do not** edit DNS.

If Save fails with “Something went wrong”:

- You pasted Service Worker syntax into a Module Worker, or the reverse.
- Paste `worker.js` unchanged (`export default { ... }`).
- Retry **Save and deploy**.

---

## 3. Testing

Hard-refresh (Ctrl+Shift+R / Cmd+Shift+R) so the old blank document is not cached.

| Check | How | Pass |
| --- | --- | --- |
| Health (apex) | Open `https://4cbon.com/health` | JSON `{"status":"ok",...}` |
| Health (app) | Open `https://app.4cbon.com/health` | JSON `{"status":"ok","host":"app.4cbon.com",...}` |
| Landing | Open `https://4cbon.com` | “4CBON2 – An Autonomous Agent” |
| www | Open `https://www.4cbon.com` | 301 to `https://4cbon.com` |
| App | Open `https://app.4cbon.com` | Gradio UI with title **4CBON2 — Public Edition**, Gemini key field, and tabs |
| Gradio root | Open `https://app.4cbon.com/config` and search for `"root"` | `"root": "https://app.4cbon.com"` — **not** `*.hf.space` |
| Embed | Open `https://app.4cbon.com/?embed=true` | Same Gradio UI, less HF chrome |
| Iframe | On `https://4cbon.com` scroll to **4CBON2 App / Live** | The Gradio app renders inside the iframe |
| Space still direct | `https://mangathpup-4cbon2-app.hf.space` | Unchanged, still works |

If `/config` still shows `"root": "https://mangathpup-4cbon2-app.hf.space"`, the new Worker source is not live yet. Re-open the editor and confirm the pasted file contains `rewritePublicUrls` and `PUBLIC.app`.

If `https://app.4cbon.com/health` 404s or returns HTML from Hugging Face, the `app.4cbon.com` route is not hitting this Worker. Check **Triggers → Routes** for `*app.4cbon.com/*` (add it only if it is actually missing).

---

## 4. Fallback prompt (for a Cloudflare agent)

Copy everything below the line and give it to an agent that **can** edit Worker `4cbon-proxy`.

-----

Update Cloudflare Worker `4cbon-proxy` only. Do not change DNS records. Do not change existing route bindings (`*4cbon.com/*` and `*app.4cbon.com/*`). Do not touch the Hugging Face Spaces.

Replace the Worker source with the ES module in this repo file:

`cloudflare/worker.js`

Requirements the code already implements:

1. ES module (`export default { async fetch(request) { ... } }`). Not `addEventListener`.
2. `4cbon.com` / `www.4cbon.com` → `https://mangathpup-4cbon2-static.static.hf.space` (the `.static.hf.space` host, never `mangathpup-4cbon2-static.hf.space`).
3. `app.4cbon.com` → `https://mangathpup-4cbon2-app.hf.space`.
4. `www.4cbon.com` 301s to `https://4cbon.com`.
5. `/health` returns JSON `{ status: "ok", time, host }` and is not proxied to Hugging Face.
6. Strip `Host`, `X-Forwarded-*`, `CF-*`, `CDN-Loop`, `True-Client-IP` before fetching Hugging Face (those headers cause `403 Forbidden: requests to …hf.space are not allowed`).
7. Rewrite HTML/JSON/JS so every `https://mangathpup-4cbon2-app.hf.space` becomes `https://app.4cbon.com` (Gradio `/config` `root` must be the public host).
8. Rewrite landing-page iframe `src` to `https://app.4cbon.com/?embed=true`.
9. Pass WebSocket upgrades through without buffering. Do not buffer `text/event-stream`.
10. Delete `X-Frame-Options`. Set `Content-Security-Policy: frame-ancestors 'self' https://4cbon.com https://www.4cbon.com https://app.4cbon.com`.
11. On Hugging Face 403 or fetch failure, return 503 text: `⚠️ 4CBON2 is temporarily unavailable. Please try again later.`

After deploy, verify:

- `https://app.4cbon.com/health` → JSON ok
- `https://app.4cbon.com/config` → `"root":"https://app.4cbon.com"`
- `https://app.4cbon.com` → Gradio “4CBON2 — Public Edition”
- `https://4cbon.com` still shows the landing page

-----
