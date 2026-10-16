# 4CBON2 Cloudflare Worker (`4cbon-proxy`)

Paste [`worker.js`](worker.js) into the existing Worker. Full dashboard, test, and fallback-prompt steps: [`DEPLOY.md`](DEPLOY.md).

Do **not** change DNS records or Worker route bindings.

## Live diagnosis

The Hugging Face Space is healthy. `app.4cbon.com` reaches the Worker (`/health` returns JSON). The blank Gradio page is caused by `/config` advertising:

```json
"root": "https://mangathpup-4cbon2-app.hf.space"
```

The Worker rewrites that `root` to `https://app.4cbon.com`, strips headers that trigger HF 403s, allows framing from `4cbon.com`, and points the landing iframe at `?embed=true`.
