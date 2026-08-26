# 4CBON2 Cloudflare Worker

Paste [`worker.js`](worker.js) into the **existing** Worker in the Cloudflare dashboard.

Do **not** change DNS records or Worker route bindings.

## What was wrong

The Spaces are already **Public** and **Running**. Visibility was not the 403 cause.

| Check | Result |
| --- | --- |
| `mangathpup/4cbon2-static` visibility | Public (`private: false`) |
| `mangathpup/4cbon2-app` visibility | Public (`private: false`) |
| Static runtime | `RUNNING` |
| App runtime | `RUNNING` on `cpu-basic` |
| Official static host (HF API `host`) | `https://mangathpup-4cbon2-static.static.hf.space` |
| Official app host | `https://mangathpup-4cbon2-app.hf.space` |

Hugging Face static Spaces live on `*.static.hf.space`, not `*.hf.space`.

| URL | Observed |
| --- | --- |
| `https://mangathpup-4cbon2-static.hf.space` | Hugging Face **404** |
| `https://mangathpup-4cbon2-static.static.hf.space` | Landing page **200** |
| `https://mangathpup-4cbon2-app.hf.space` | Gradio app **200** |
| `https://mangathpup-4cbon2-app.hf.space?embed=True` | Gradio app **200** |
| `https://app.4cbon.com` | Gradio app **200** |
| `https://4cbon.com` | Landing page **200** (verified after Worker redeploy) |
| `https://www.4cbon.com` | Redirects to `https://4cbon.com` |
| `https://4cbon.com/health` | `{"status":"ok",...}` **200** |

The `403 Forbidden: requests to …hf.space are not allowed` message is Hugging Face rejecting a proxied request whose `Host` / `X-Forwarded-Host` is the custom domain (`4cbon.com`), or whose target is the unused `*.hf.space` alias for a static Space.

This Worker:

1. Proxies the apex site to `mangathpup-4cbon2-static.static.hf.space`.
2. Proxies `app.4cbon.com` to `mangathpup-4cbon2-app.hf.space`.
3. Strips `Host`, `X-Forwarded-Host`, and Cloudflare forwarding headers so HF does not treat the request as an unregistered custom domain.
4. Returns a 503 fallback if Hugging Face still replies 403.
5. Rewrites Gradio `/config` `root` from the HF host to `https://app.4cbon.com` so the iframe talks to the Worker, not `*.hf.space`.
6. Rewrites the landing-page iframe to `https://app.4cbon.com/?embed=true` and allows framing from `4cbon.com`.

Do **not** deploy the “sample” Worker that targets `mangathpup-4cbon2-static.hf.space` or that forwards `request.headers` unchanged. That combination is what produced the original 404/403.

## How to apply (Cloudflare dashboard)

1. Open the Worker that already serves `4cbon.com` / `app.4cbon.com`.
2. Replace its source with `cloudflare/worker.js`.
3. Save and deploy.
4. Leave DNS and routes untouched.
5. Hard-refresh `https://4cbon.com` and `https://app.4cbon.com`.

## Hugging Face settings

Space **Settings** pages require a logged-in owner session. This environment cannot open:

- https://huggingface.co/spaces/mangathpup/4cbon2-static/settings
- https://huggingface.co/spaces/mangathpup/4cbon2-app/settings

No visibility change is needed. The public API already reports both Spaces as public and not gated.

If you still want to double-check in the UI:

1. Open each Settings page while logged in as `mangathpup`.
2. Confirm **Visibility** is **Public**.
3. Confirm the static Space SDK is **Static** and the app Space SDK is **Gradio**.
