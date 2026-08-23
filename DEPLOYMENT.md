# 4CBON2 replacement deployment

This change set is a **separate preview build**. It does not modify the existing production site at `4cbon.com`, the existing Vercel app, or the blueprint notebook. Malik must review and approve the preview before any DNS change.

## Deliverables

| File | Purpose |
| --- | --- |
| `4CBOn2_Gemini2b.ipynb` | Private/admin Colab notebook. Keeps the six blueprint tabs, adds the AI Rewriter, and keeps the Builder fully available. |
| `4CBOn2_Gemini2c.ipynb` | Public Colab notebook. Replaces Builder with Request Custom Agent, adds a three-run session limit, and retains the other tabs plus AI Rewriter. |
| `docs/index.html` | GitHub Pages landing page. It contains no API keys or internal credentials. |
| `supabase/4cbon2.sql` | Supabase schema and the requested RLS policies. |

The six original blueprint cells are copied unchanged into both new notebooks. The requested blueprint lives on the source branch as `4CBOn2_Gemini2.ipynb`; the new rewriter is a separate cell, so the blueprint is not edited.

## 1. Test the notebooks in Colab

1. Open the admin notebook from the branch/PR preview:
   `https://colab.research.google.com/github/mohamtur1/4CBOn2/blob/arena/01a02b8e-4cbon2/4CBOn2_Gemini2b.ipynb`
2. Run Cells 1–8 in order (Cell 9 is an optional download helper). Cell 1 installs dependencies and authenticates through `google.colab.ai`; no model API key is requested.
3. Confirm the original tabs work: Upload Documents, Ask a Question, Agent Mode, Builder, Data Dashboard, and Agent Status.
4. In **AI Rewriter**, paste a short, clearly written answer, optionally add a goal, click **Run Pipeline**, and verify the ordered artifacts: L0, P, W, LX, LA, LC, L1, L2, LP, L3, L4, LR, L6, L7, L8, L9, and L10. `Score Before`, `Score After`, `Final L10 Audit`, and **Copy All** should populate.
5. Run the same input a second time and confirm the previous L9 questions appear in the L0 prompt context. If Supabase variables are configured, confirm L8 beliefs and L9 questions are inserted.
6. Open the public notebook, run the same checks, and confirm there is no Builder tab or Builder function. Submit the Request Custom Agent form and confirm it creates a pre-filled email draft to `mohamtur1@gmail.com`.
7. Click Run Pipeline four times in a single public notebook session. The first three are allowed; the fourth shows:
   `You've used your 3 free runs. Subscribe to continue: https://4175358678144.gumroad.com/l/tbphpi`

The public notebook uses `gr.State` for the free-run counter, as permitted by the specification. It is a client session guard, not a security boundary. For an enforceable lifetime or account-level limit, move the check to a server endpoint backed by `run_limits` before public launch.

## 2. Configure optional Supabase memory

1. Create a Supabase project and run [`supabase/4cbon2.sql`](supabase/4cbon2.sql) in the SQL Editor.
2. Set `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` as private Colab environment variables/secrets. Never place the service-role key in the notebook UI, landing page, Git history, or browser JavaScript.
3. The rewriter cell writes L8 records to `beliefs` and L9 records to `questions` when both values are present. Without them, the notebook remains functional and uses local in-session memory.
4. The supplied policies intentionally permit public reads because that is the approved schema. Review whether beliefs, questions, feedback, run limits, and subscription metadata should be public before enabling an anonymous production client; a privacy-preserving launch should restrict these tables to authenticated users or a server-side reporting role.

## 3. Gumroad subscription webhook

The public CTA is the existing product URL:
`https://4175358678144.gumroad.com/l/tbphpi`

The subscription table is ready for the existing webhook integration. If the webhook is enabled, keep its Supabase service-role credential and signing secret in the deployment provider's encrypted environment variables. Verify a test sale and cancellation in a staging Supabase project before writing to production.

## 4. Publish the landing page on GitHub Pages

1. Merge the reviewed files into the repository's intended deployment branch only after approval. This session is working on `arena/01a02b8e-4cbon2`; it does not change `main`.
2. In GitHub, open **Settings → Pages**.
3. Under **Build and deployment**, choose **Deploy from a branch**, select the approved branch, and select `/docs` as the folder.
4. Save and wait for the Pages URL to become available. Test the page at the generated `github.io` URL first.
5. Verify both Colab links, Gumroad links, mailto contact links, mobile layout, and accessibility (keyboard focus, readable contrast, and a narrow viewport).
6. Keep the existing `4cbon.com` DNS records untouched during preview.

The landing page points to the requested public notebook on `main`:
`https://colab.research.google.com/github/mohamtur1/4CBOn2/blob/main/4CBOn2_Gemini2c.ipynb`

That link should be changed only if the approved public notebook is hosted at another stable ref.

## 5. Preview and DNS switch procedure

1. Malik reviews the two notebooks in Colab and the GitHub Pages preview.
2. Run the complete acceptance checklist above, including a failed-layer test, L9 next-run injection, Builder access in admin, Builder removal in public, mailto generation, and the free-run limit.
3. Confirm no production code or DNS was changed and that no credentials appear in the repository.
4. Only after Malik personally approves, point `4cbon.com` to the GitHub Pages custom-domain target in GitHub Pages settings. Follow GitHub's current HTTPS/custom-domain instructions and allow DNS propagation.
5. Keep the old production deployment available during propagation and record a rollback plan: restore the previous DNS records if the preview fails.
6. After cutover, re-test the landing page and both outbound purchase/Colab links. Do not expose the admin notebook link on the public landing page.

## Architecture notes

- `4CBOn2` is the ecosystem; **Autonomous Agent** remains the general-purpose orchestrator over the blueprint's specialists; **AI Rewriter** is a separate subsystem in the new tab.
- Each rewriter layer is called through `safe_ask_raw`, with `RUNTIME_SPEC` included as the execution-system prompt. The three-call median scorer mirrors the React source while using `google.colab.ai`.
- L9 is displayed as a generated artifact between L8 and L10. Its three questions are saved in a local variable/session state and injected into the next L0 run; optional Supabase inserts provide persistence.
- Pipeline failures stop the run and preserve already-produced artifacts rather than asking later layers to evaluate broken output.
