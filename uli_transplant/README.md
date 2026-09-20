# Universal Learning Intelligence

An intelligence architected around **learning itself** — not a chatbot, not an LMS, not a
course platform, not a question bank.

**Status: M0 (the spine) in progress. Nothing here is usable yet, and nothing here should be
pointed at a real learner.** See *Honest limits* below.

---

## The idea in one paragraph

The unit of this system is not content. It is a learner's **changing competence**. Instead of
asking *"which lesson is next?"* it asks *"what can this person actually do, what is stopping the
next thing, and what is the smallest legitimate intervention that moves it?"* The engine is
universal; the subject knowledge is modular. Mathematics, cybersecurity, solar engineering and a
subject nobody has loaded yet are all the same shape to the engine and different shapes to the
knowledge layer.

## The four rules everything else exists to serve

1. **Truth comes from a verifier, not from the agent.** An agent grading itself against a standard
   it produced is being self-consistent, not correct. A claim is truth only once something outside
   the agent accepts it — an executable check, corroborated sources under adversarial challenge, or
   a human. A claim's verifier tier is the ceiling on what the system may assert, and an unverified
   claim is *unreachable* by instruction, not merely flagged.
2. **The learner copies a path a human could walk.** The agent masters a domain twice: once freely,
   to establish what is true (**Track M**), then again under human limits — bounded sessions, one
   new idea at a time, no hidden tools — to establish what is copyable (**Track H**). Only Track H
   is ever shown to a learner. Every error in it must carry *how the agent knew it was wrong* and
   *what it did about it*; an agent that shows a corrected answer teaches the answer, an agent that
   shows the detection teaches what transfers.
3. **If it cannot teach something, it says so.** Re-chunk → re-scope (labelled as reduced) →
   delegate to a human → quarantine as `NOT_TEACHABLE`. The last rung is a visible refusal, never
   confident filler. And Track M cannot override a Track H refusal: M rules on truth, H rules on
   teachability, and a conflict between them is surfaced as an architectural failure rather than
   resolved by priority.
4. **Never turn a hypothesis about a person into a label about a person.** The model of a learner is
   provisional, inspectable, and correctable by the learner. "You are bad at mathematics" is
   forbidden at the architecture level. "Your errors cluster in proportional reasoning; here is the
   evidence" is permitted — and ships with a control to disagree.

## Where this is going

Twelve stages, each ending at a gate that is passed by a **demonstration**, never by a report.

| Stage | Name | Its one job |
|---|---|---|
| M0 | Spine | Ledger, event schema, action protocol, projections, a UI shell that survives a dead runtime |
| M1 | Truth | Executable verifiers; ingest one domain; the machine masters it; measure the ingestion |
| M2 | Teachability | The human-constrained replay, the constraint checker, the refusal ladder |
| M3 | The Loop | Diagnosis on a budget; teach → attempt → assess → update → next; three independent roles |
| M4 | The Surface | All eight screens, the Trend Line, the learner-correction path, resume UX |
| M5 | Governance | Tiers, versioning, quarantine, adversarial pass, errata propagation |
| M6 | Second Domain | A domain of a different kind; prove the engine is not shaped like the first one |
| M7 | Teacher Surface | A second product over the same ledger; teachers supply what the agent cannot know |
| M8 | Delegation | The embodied/tacit pipeline as a workflow, not a disclaimer |
| M9 | Many Learners | Sharding, retention, cost model, and a privacy posture fit for a real human |
| M10 | Off the Notebook | A real server; offline and low-bandwidth packaging; the trajectory as a carried artifact |
| M11 | The Verdict | Measure universality across unseen domains and publish the number, including the bad news |

**Greenlit: M0, M1, M2.** M3–M11 are held for review after M2's gate, which is a human reading a
Track H trace cold. If that gate fails, the two-track premise is retracted and everything after it
changes shape — which is exactly why it is not greenlit yet.

## Documents

| File | What it is |
|---|---|
| `docs/ULI_WALKTHROUGH.md` | **The master document.** The whole project, the rulings R1–R17, the build contract, the acceptance tests, and a glossary in plain words. Authoritative. |
| `docs/ULI_GREENLIGHT.md` | The decisions packet as *submitted for review* — a frozen snapshot of what was judged (D1–D10). Historical. |
| `docs/SESSION_START.md` | Onboarding for any future working session. Read before touching code. |

If the walkthrough and the greenlight packet disagree, **the walkthrough wins** — it carries the rulings.

## How it runs

Google Colab, using the built-in `google.colab.ai` library (no API key, no setup). Two of Colab's
constraints shaped the design rather than being worked around: the port proxy does not reliably
carry WebSockets, so transport is request/response plus a ledger cursor; and there is no native
tool-calling, so every agent turn emits a strict JSON action block with a visible failure path.
The notebook is a runtime — the system lives in the `uli/` package and must be importable and
testable without opening Colab.

## Honest limits

- **Demo-grade privacy until M9.** Learner data in a notebook runtime backed by a Drive folder is
  not acceptable for real learners, especially minors. Until M9, the only user of this system is
  its author. That is a rule, not a caveat.
- **Two mastery classes of four are built.** Formal (executable checks) and evidential
  (corroborated + adversarially challenged). Embodied/tacit and local knowledge are designed for,
  stubbed, and made visible as delegations. "Universal" is a claim about the architecture, not
  about coverage the system has earned.
- **Role independence is weak on a free tier.** Teacher, assessor and adjudicator may share model
  weights. The limitation is measured (cross-role disagreement rate) rather than hidden.
- **The two-track premise is the bet this project would lose.** It has a scheduled falsification at
  M2's gate.
- **Colab is not a product.** M10 exists because none of M0–M9 changes that.

## Working on this

Sessions are one milestone at a time, ending at a gate. Read `docs/SESSION_START.md`, then
`docs/ULI_WALKTHROUGH.md` Part 8 for the stage you are on. Decisions are changed by adding a ruling
with its cost and its falsifier — never by silently deviating.

## License

Not yet chosen. Ask before assuming.
