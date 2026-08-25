# Deploy 4CBON2 to Hugging Face Spaces

Public deployment of the 4CBON2 ecosystem on Hugging Face Spaces,
fronted by a Cloudflare Worker on your own domain.

## Spaces

| Space            | Type    | Folder          | Contents                                            |
| ---------------- | ------- | --------------- | --------------------------------------------------- |
| `4cbon2-static`  | static  | `static_space/` | Public landing page (`index.html` + `README.md`)    |
| `4cbon2-app`     | gradio  | `space_demo/`   | Public app (`app.py`, `requirements.txt`, README)   |

## 1. Create + push the Spaces

Run from any machine with Hugging Face access (not from this sandbox — see note):

```bash
export HF_TOKEN=hf_...        # your Hugging Face access token
python3 deploy_hf_spaces.py
```

The script auto-detects your HF username from the token, creates both Spaces,
and uploads the matching folders. It prints the final URLs, e.g.:

```
https://huggingface.co/spaces/<your-user>/4cbon2-static
https://huggingface.co/spaces/<your-user>/4cbon2-app
```

The username shown by `whoami` for this token is where the Spaces are created;
if it is `mohamtur1`, the URLs are:

```
https://huggingface.co/spaces/mohamtur1/4cbon2-static   (landing page)
https://huggingface.co/spaces/mohamtur1/4cbon2-app      (public app)
```

First build takes a few minutes. Watch it at
`https://huggingface.co/spaces/<user>/4cbon2-app/settings`.

## 2. Put your domain in front of them (Cloudflare)

See [`cloudflare/SETUP.md`](cloudflare/SETUP.md) and [`cloudflare/worker.js`](cloudflare/worker.js).

- `app.4cbon.com` → Gradio app Space
- `4cbon.com`     → static landing Space

## 3. Testing checklist

1. Landing page loads at the static Space URL.
2. The app loads at the Gradio Space URL (Agent Mode / Ask a Question / Upload tabs).
3. In the app, provide an `HF_TOKEN` in the interface and confirm the model replies.
4. After adding Cloudflare: both `4cbon.com` and `app.4cbon.com` load and the
   Gradio queue (streaming) works through the Worker.

## Notes

- No secrets are stored in the repo. The token lives only in your environment
  when you run the deploy script. The app uses a token entered in the UI.
- This sandbox's network blocks `huggingface.co` (its egress firewall), so the
  Spaces push must be run from a machine/environment with HF access. Everything
  needed is committed to this repo.
