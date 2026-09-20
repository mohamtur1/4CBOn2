# ULI — New Session Bootstrap Prompt

*(Paste this entire file into a fresh Arena session. To start it: clone
`mohamtur1/Universal_Learner` and give the session that workspace.)*

---

You are starting work on the **Universal Learning Intelligence (ULI)** project, in this repository:
`mohamtur1/Universal_Learner`.

Work through **Phase 1** to completion before starting **Phase 2**. Do not skip Phase 1's
verification steps in order to get to building — the whole point of this prompt is that a previous
session's work survives the handover intact.

**Your session, in one line:** transplant the ULI artifacts into this repository as its first
commit, then build milestone **M0 (the Spine)** to its gate.

---

## PHASE 1 — TRANSPLANT (must complete; do not skip)

### Step 1.1 — Verify the repository before anything else

```bash
gh repo view mohamtur1/Universal_Learner --json name,isPrivate,isEmpty,defaultBranchRef
```

Three outcomes, three responses. **Report what you find to the user before continuing.**

| Result | What it means | What to do |
|---|---|---|
| Command succeeds, `isEmpty: false` | Ready | Continue to 1.2 |
| Command succeeds, `isEmpty: true` | Repo exists, no commits | Continue to 1.2, then see "If the repo is empty" below |
| Command fails / 404 | You cannot see it | **Stop.** Tell the user plainly and give them both fixes: (a) the repo does not exist yet — create it at `github.com/new`, owner `mohamtur1`, name **exactly** `Universal_Learner`, **Public**, tick *Add a README file* so `main` exists; or (b) it exists but is private and the GitHub app this session uses has not been granted access — GitHub → Settings → Applications → Installed GitHub Apps → the Arena app → Configure → add this repository. Then re-run this step. |

**Do not** guess alternative repository names, and **do not** create the repository yourself with
`gh repo create` unless the user explicitly asks you to in this session.

### Step 1.2 — Fetch the payload

Everything from the originating session lives in `mohamtur1/4CBOn2` (a different, unrelated
project) on branch `arena/01a0bcfe-4cbon2`, folder `uli_transplant/`.

```bash
rm -rf /tmp/uli-src /tmp/uli-payload
git clone --depth 1 --branch arena/01a0bcfe-4cbon2 \
  https://github.com/mohamtur1/4CBOn2.git /tmp/uli-src
cp -r /tmp/uli-src/uli_transplant /tmp/uli-payload
find /tmp/uli-payload -type f | sort
cat /tmp/uli-payload/PAYLOAD.md
```

Expected six files: `README.md`, `.gitignore`, `PAYLOAD.md`, `docs/ULI_WALKTHROUGH.md`,
`docs/ULI_GREENLIGHT.md`, `docs/SESSION_START.md`.

**If the clone fails**, fetch the same files directly:

```bash
BASE=https://raw.githubusercontent.com/mohamtur1/4CBOn2/arena/01a0bcfe-4cbon2/uli_transplant
mkdir -p /tmp/uli-payload/docs
curl -fsSL $BASE/README.md                   -o /tmp/uli-payload/README.md
curl -fsSL $BASE/.gitignore                  -o /tmp/uli-payload/.gitignore
curl -fsSL $BASE/PAYLOAD.md                  -o /tmp/uli-payload/PAYLOAD.md
curl -fsSL $BASE/docs/ULI_WALKTHROUGH.md     -o /tmp/uli-payload/docs/ULI_WALKTHROUGH.md
curl -fsSL $BASE/docs/ULI_GREENLIGHT.md      -o /tmp/uli-payload/docs/ULI_GREENLIGHT.md
curl -fsSL $BASE/docs/SESSION_START.md       -o /tmp/uli-payload/docs/SESSION_START.md
```

**If that also fails**, stop and ask the user to paste these five files from the old session:
`README.md`, `.gitignore`, `docs/ULI_WALKTHROUGH.md`, `docs/ULI_GREENLIGHT.md`,
`docs/SESSION_START.md`. Do not reconstruct them from memory or from this prompt — they contain a
real review record and a pre-registered list of the project's weakest claims, and a paraphrase
would destroy both.

### Step 1.3 — Verify you have the right revision, then place the files

```bash
wc -l /tmp/uli-payload/docs/ULI_WALKTHROUGH.md      # expect ~876 lines
grep -c "R17" /tmp/uli-payload/docs/ULI_WALKTHROUGH.md   # expect > 0  (rulings present)
grep -c "ADIR-S" /tmp/uli-payload/docs/ULI_WALKTHROUGH.md # expect > 0  (M11 metrics present)
```

If either grep returns 0, you have the wrong revision. Stop and say so.

```bash
cp    /tmp/uli-payload/README.md      README.md
cp    /tmp/uli-payload/.gitignore     .gitignore
mkdir -p docs
cp    /tmp/uli-payload/docs/*.md      docs/
ls -la . docs
```

**If GitHub auto-created a README** when the repo was made: replace it. Do not merge the two.

**If the repo is empty** (no `main` yet): make the transplant your first commit on your session
branch and push it. If the host will not let you open a pull request against a branch that does not
exist yet, stop and ask the user how they want the first commit to land rather than pushing to
`main` on your own initiative.

### Step 1.4 — Record that the transplant happened

`docs/ULI_WALKTHROUGH.md` Part 10 describes the repository situation as unresolved, because at the
time of writing the target returned 404. **It is now resolved.** Amend Part 10.1 to record: the
transplant date, the source (`4CBOn2`, branch `arena/01a0bcfe-4cbon2`, folder `uli_transplant/`),
the commit SHA of the first commit in this repository, and that this repository is now the home and
the living copy while the `4CBOn2` copies are frozen history. Keep it factual and short — two or
three sentences. Do not otherwise rewrite the walkthrough; amendments go through rulings (§7 below).

### Step 1.5 — Commit and open the PR

Commit to **your session branch** and push there. Open a pull request against `main`. Do not push
to any other branch. Then report Phase 1 complete with the files placed and the PR link.

---

## PHASE 2 — BUILD M0 (THE SPINE)

Only after Phase 1 is complete and the user says continue. **M0 only.** M1 and M2 are greenlit but
are *not* this session's work; M3–M11 are not greenlit at all.

### 2.1 — Read before you write code

Read, in this order: `docs/SESSION_START.md` (short), then `docs/ULI_WALKTHROUGH.md` — at minimum
**Part 7** (rulings R1–R17), **Part 8 → M0**, **Part 9** (Colab operations), **Appendix A** (event
types), **Part 10.2** (target layout), **Part 12** (known weaknesses).

Then, before writing any code, tell the user in your own words: (a) the four rules in §3 of this
prompt, (b) what M0's gate is, and (c) the one thing you are deliberately not building. If you
cannot do all three, you have not read enough.

### 2.2 — What M0 delivers

- **`uli/events.py`** — the event envelope `{v, seq, ts, learner, actor:{role, model}, type,
  payload, cause}`; the event types exactly as listed in walkthrough **Appendix A** — do not invent
  or rename types; validators. Two properties are load-bearing and must be enforced in code:
  every belief-changing event carries `cause`, and every event carries `actor`. *Frozen at M0 —
  changing it later is the expensive thing this milestone exists to prevent.*
- **`uli/ledger.py`** — append, read, checkpoint to Drive, load-or-create on boot, single writer,
  monotonic per-learner sequence, orchestrator-assigned timestamps, and **upcasters** so an older
  event read by a newer reader is transformed explicitly rather than guessed.
- **`uli/projections.py`** — learner model, competency state, mastery, retention, next-action
  queue. Pure functions over the ledger, recomputable from empty. No mutable state anywhere else.
- **`uli/protocol.py`** — the strict JSON action block: parse, validate, a bounded repair loop
  (two attempts), then a hard failure recorded as `ROLE_FAILURE`. **No silent degradation, ever.**
- **`uli/roles.py`** — teacher / assessor / adjudicator over `google.colab.ai`. Model names come
  from a config-driven roster; **nothing may hard-code a model name**. Include the per-session
  token budget counter.
- **`uli/server.py`** — serves the static UI and a JSON API with a **ledger cursor** (no
  WebSockets — the Colab port proxy does not reliably carry them).
- **`ui/`** — the shell for **Route**, **Beliefs** and **Next**, rendered against real projections
  with honest empty states. Static files, no build step. Mobile-first, 360 px, AA contrast,
  keyboard navigable.
- **`notebooks/uli_colab.ipynb`** — bootstrap cell: install, mount Drive, load-or-create the
  ledger, print a health line (resuming at event *N*, budget remaining, model roster), start the
  server, frame it with `serve_kernel_port_as_iframe`. Runnable top to bottom in under five
  minutes, resumable from any checkpoint.
- **`docs/ARCHITECTURE.md`** — the system diagram and the invariants from §3 expressed as
  code-level rules.

### 2.3 — The gate (a demonstration, not a report)

1. **Replay identity.** Hand-author an event stream, project it, restart the runtime, re-project —
   identical state. Show the two outputs side by side.
2. **Kill and resume.** Run the notebook top to bottom twice; the second run must *resume*, not
   duplicate. Show the health line proving where it resumed.

Then update the walkthrough: mark M0's gate status in Part 8 with the date and what was shown.
**Do not claim a gate.** Show the output.

---

## 3. The non-negotiables (if you read nothing else)

1. **Truth comes from a verifier, not from the agent.** An agent grading itself against a standard
   it produced is being self-consistent, not correct. A claim's verifier tier is the ceiling on what
   the system may assert, and an unverified claim is *unreachable* by instruction — not merely
   flagged.
2. **Two independent tracks.** *Track M* masters a domain freely to establish what is true. *Track
   H* replays it under human limits to establish what is copyable — and only Track H is ever shown
   to a learner. Every error in it must carry **how the agent knew it was wrong** and **what it did
   about it**. Track M cannot override a Track H refusal.
3. **If it cannot teach something, it says so.** Re-chunk → re-scope (labelled as reduced) →
   delegate to a human → quarantine as `NOT_TEACHABLE`. Never filler.
4. **Never turn a hypothesis about a person into a label about a person.** Beliefs are provisional,
   carry a `cause`, are visible to the learner, and are correctable by the learner.

Plus, operationally: **the ledger is the only mutable thing** (everything else is a projection);
**T0 checks are code, never prose**; **no silent degradation**; and **model names come from config**.

## 4. Constraints you must not break

- **Privacy: demo-grade until M9.** The only user of this system is its author. No real learner's
  data, ever, especially not a minor's. This is a rule, not a caveat, and it does not relax because
  something would be nicer to demo.
- **No secrets.** `google.colab.ai` needs no key. Never ask the user for a credential, token or
  password; never paste one into a notebook.
- **Colab realities are design rules**, not obstacles to route around: no WebSockets · no native
  tool calling · no embedding dependency · runtime reclaimed when idle, so checkpoint-resume is
  mandatory.
- **Do not port code from `4CBOn2`.** That is a different project — an auth/paywall/run-limit system
  (`vercel/`, `supabase/`, `space_gemini/`). Nothing there belongs here. The only thing crossing
  over is the `uli_transplant/` payload.
- **Nothing lives only in the notebook.** If the `uli/` package cannot be imported and tested
  without opening Colab, M0 is not done.
- **Never commit** `data/`, ledgers, caches, or artifacts.
- **Do not add** CI, a LICENSE, dependencies beyond necessity, or a UI build step without asking.

## 5. Working with this user

- He is not a programmer and drives AI to build. **Plain language, one decision at a time**, with
  the trade-off stated. Never a wall of jargon without it.
- **Bad news first.** If a gate fails, say so immediately and plainly. The project's entire method
  depends on failures being written down as loudly as successes — the walkthrough pre-registers its
  own weakest claims on purpose (Part 12).
- **Evidence, not assurance.** "The test passed" is worth nothing; the output is worth everything.
- **One question at a time** when a decision is genuinely his (naming, scope, credentials, anything
  touching his accounts). Decide the technical questions yourself.

## 6. Changing a decision

A decision is changed by **adding a ruling** — numbered `R18`, `R19`, … in walkthrough Part 7, with
its **cost** and its **falsifier** — never by silently deviating. If a ruling turns out to be wrong,
say so and retract it explicitly. A retracted ruling is a result, not an embarrassment.

## 7. How to end the session

Push your branch, make sure the PR is open, and end with this report — sections in this order:

```
PHASE 1:   files placed (list) · PR link · anything that did not match this prompt
PHASE 2:   what is built · what the gate actually showed (paste the output)
NEXT:      what M1 needs, and anything you would need a decision on before starting it
BLOCKED:   anything that stopped you, with the exact error
UNSURE:    what you are least confident about in your own work
```

If you get through Phase 1 and M0's gate, **stop there.** Do not start M1 in this session — the
next session starts clean with this repository as its home, reading `docs/SESSION_START.md`.
