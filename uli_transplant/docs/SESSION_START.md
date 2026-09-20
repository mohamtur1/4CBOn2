# SESSION START — read this before touching anything

Onboarding contract for any working session on this repository. Short by design; the depth is in
`docs/ULI_WALKTHROUGH.md`.

---

## 1. What this project is

An intelligence architected around learning itself. The unit is a learner's **changing competence**,
not content. The engine is universal; subject knowledge is modular. It is not a chatbot, an LMS, a
course platform, or a question bank — and if a design decision starts pushing it toward one of
those, that decision is wrong.

## 2. Where things live

| Path | What |
|---|---|
| `docs/ULI_WALKTHROUGH.md` | **The master document and the build contract.** Part 7 = rulings R1–R17. Part 8 = the stage you are building, with its gate. Part 12 = known weaknesses. Appendix A = event types. Appendix C = glossary in plain words. |
| `docs/ULI_GREENLIGHT.md` | The decisions as reviewed (D1–D10). Historical snapshot. Where the two disagree, **the walkthrough wins.** |
| `docs/ARCHITECTURE.md` | System diagram and the invariants as code-level rules. *(Created during M0.)* |
| `uli/` | The system. Importable package — nothing lives only in the notebook. |
| `notebooks/uli_colab.ipynb` | Colab runtime. Imports `uli/`, serves the UI, checkpoints to Drive. Not the product. |
| `ui/` | The Apprenticeship Surface. Static files, **no build step** (deliberate — see walkthrough Part 10.2). |
| `tests/` | The acceptance tests, runnable outside Colab. |
| `data/` | Gitignored. Ledgers, caches, artifacts. Never committed. |

## 3. The invariants — violating one of these is a bug, not a preference

1. **Truth comes from a verifier, not the agent.** A claim's verifier tier is the ceiling on what
   may be asserted. Unverified claims are unreachable by instruction.
2. **Two independent tracks.** Track M rules on truth; Track H rules on teachability. Only Track H
   is shown to a learner. Every Track H error carries a **detection cue** and a **recovery action**.
3. **Track M cannot override a Track H refusal.** Conflict is surfaced as an architectural failure.
4. **The ladder on the way out:** re-chunk → re-scope (labelled) → delegate → `NOT_TEACHABLE`.
   Never filler.
5. **No label about a person.** Provisional, inspectable, correctable beliefs only.
6. **The ledger is the only mutable thing.** Everything else is a projection, recomputable from
   empty. Single writer. Append-only.
7. **T0 checks are code, never prose.**
8. **No silent degradation.** Protocol violations, budget exhaustion and role failures are visible
   in the ledger and in the UI.

## 4. Standing constraints

- **Colab:** no WebSockets (transport is request/response + a ledger cursor) · no native tool
  calling (strict JSON action blocks with a bounded repair loop) · no embedding dependency
  (lexical + graph-structural retrieval) · model names come from a config roster, **never
  hard-coded** · checkpoint-resume is mandatory, because the runtime will be reclaimed mid-work.
- **Privacy:** demo-grade until M9. **The only user of this system is its author.** No real
  learner's data, ever, before M9 — especially not a minor's.
- **Secrets:** none are needed. `google.colab.ai` requires no key. Never ask the user for a
  credential, token or password.
- **No build step for the UI.** It ships as static files, cacheable for offline use.
- **Nothing lives only in the notebook.**

## 5. Current state

- **Greenlit: M0, M1, M2.**
- **Not greenlit: M3–M11.** M2's gate is a human reading a Track H trace cold, and it can
  invalidate the two-track premise — so nothing downstream is approved until it is passed.
- Build one stage at a time, and remember that **a gate is passed by a demonstration, not by a
  report.** Show the output; do not claim the result.

## 6. How a session runs

1. Read this file, then walkthrough Part 8 for your stage.
2. Work on your session branch. Push there and open a pull request against `main`. Do not push to
   other branches.
3. End with a status report: **what is done · what the gate actually showed · what is next · what
   blocked · what you are uncertain about.** Bad news first.

## 7. The one rule about changing decisions

A decision is changed by **adding a ruling** to walkthrough Part 7 — numbered, with its **cost** and
its **falsifier** — never by silently deviating. If a ruling turns out to be wrong, say so and
retract it explicitly; a retracted ruling is a result, not an embarrassment. This project
pre-registers its weakest claims on purpose (Part 12), and the only way that stays honest is if
failures get written down as loudly as successes.
