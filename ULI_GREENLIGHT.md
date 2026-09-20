# ULI — Greenlight Packet v1

**Universal Learning Intelligence — decisions, and the build plan that follows from them**

Status: **awaiting greenlight from the author and from DeepSeek.** No code written.
Runtime target: **Google Colab**, using the built-in `google.colab.ai` API.
Deliverable of this packet: a set of *decisions* (not options) with stated costs and
named falsifiers, plus a milestone plan with gates. A greenlight means "build M0–M2
to these specs"; it does not mean "approve the whole vision."

How to review this: §11 is written for DeepSeek specifically — it lists the claims most
likely to be wrong and asks for attack. §10 pre-registers my own weakest decisions, so
a reviewer does not have to discover them.

---

## 0. Executive decisions

| # | Decision | Confidence | Falsifier |
|---|---|---|---|
| D1 | Mastery is classified by **what can verify it**, not by subject. Four classes: M-A formal, M-B evidential, M-C embodied/tacit, M-D local/contextual. v1 ships **M-A + M-B only**. | High | M-A/M-B split proves unstable for a real domain in M1 |
| D2 | Every domain is mastered **twice**: Track M (unconstrained, for truth) and Track H (human-constrained, for teaching). The artifact taught from is the Track H trace. | Medium | Track H traces are indistinguishable from ordinary lesson scripts in M2 |
| D3 | Knowledge governance is a **primary subsystem**: verification tiers, immutable versioned claims, quarantine, adversarial challenge, **errata propagation** to already-taught learners. | High | Errata events never fire in M5 because claims never change |
| D4 | **Verification independence**: teaching role ≠ assessing role ≠ adjudicating role, and T0 checks must be executable, not model-graded. | High | Independence costs so much latency in Colab that the loop dies |
| D5 | Cold start is bounded by **budget, not coverage**: ≤12 min / ≤15 items, provisional priors only, next item chosen by expected information gain about *the next action*. | Medium | 15 items cannot locate a learner well enough to name a next action |
| D6 | Hybrid multi-agent (DeepSeek's Option C) with **one persistent state: the Ledger**, an append-only event log. All modules are pure functions over it; learner model / competency state / mastery are *projections*. | High | Projection cost makes the loop perceptibly slow |
| D7 | The UI is the **Apprenticeship Surface**: two trajectories, divergence as the primary metaphor, mastery as evidence stack, learner model inspectable *and correctable*. Best practices are the floor, not the design. | High | Learner (author) cannot answer "what does it believe about me and why" in <30s using it |
| D8 | Colab's constraints are design rules: **no WebSockets**, checkpoint-resume mandatory, prompt-orchestrated tool protocol (no native function calling), lexical+structural retrieval (no embedding dependency), token budget per session. | High | Polling latency makes the conversation feel dead |
| D9 | v1 = **one domain, one learner, one loop, end to end.** Domain: **solar PV sizing**. Detector validation bench: **fractions** (pre-registered misconception). | High | Solar cannot produce a real T0 verifier |
| D10 | Self-improvement in v1 is **over artifacts, not parameters** — the agent gets better at modelling domains and choosing interventions, not at existing. The AGI claim gets a **number**: ADIR. | High | — (this is a scope boundary, not a prediction) |

---

## 1. The three questions DeepSeek said must be answered first

### D1 — Which masteries can the agent hold, and which delegate?

**Decision: mastery is classified by verifier, not by subject.** DeepSeek framed this as
"cognitive vs physical." That framing is close but wrong in a way that matters: what
determines whether the agent can hold mastery is not whether the domain is physical, it
is **whether a verifier exists that is independent of the agent.**

| Class | Verifier | What the agent can hold | Example |
|---|---|---|---|
| **M-A** formal | Executable check: solver, tests, unit balance, proof checker | Full mastery + **ground truth** | Solar load calc; code against tests; algebra |
| **M-B** evidential | Corroborated sources + adversarial challenge + explicit uncertainty | **A defensible position, never *the* answer** | History interpretation; threat models; business cases |
| **M-C** embodied | Human demonstrator / instrumented evidence (video, sensor, rubric) | The instructional model only. **Cannot hold mastery.** | Torque spec applied by hand; lab technique; pronunciation |
| **M-D** local | Source of record, time-stamped | Nothing. Structurally unknowable offline. | Which supplier in Freetown stocks a 3 kVA hybrid inverter today |

Three rulings fall out:

1. **v1 ships M-A and M-B.** M-A is what makes the whole architecture testable — without
   at least one class backed by an executable verifier, everything downstream reduces to
   a model grading itself. M-B is where most of education actually lives and is the harder
   case; we include it because shipping M-A alone would prove nothing about universality.
2. **M-C and M-D are designed-for, stubbed, and visible.** The UI must show a learner when a
   competency is delegated ("this one needs a human to judge it — here's what they'll look
   for"). Hiding delegation is how an edtech product lies.
3. **M-A competence does not imply M-C competence**, and the system must never let it. A
   learner who can size a system on paper is not certified to install it. This is stated in
   the architecture, not in a disclaimer.

**Cost:** four classes = four assessment pipelines, not one. v1 carries two, so the cost is
deferred rather than paid. **Falsifier:** if M-A/M-B turns out not to be a real distinction
in M1 (every M-B claim collapses into an M-A sub-question), the taxonomy earns nothing and
should be collapsed.

### D2 — How does the agent master a domain *copyably*?

**Decision: two tracks, and the teaching artifact comes only from Track H.**

- **Track M (machine mastery, unconstrained).** The agent ingests the domain corpus, solves,
  and is checked at the T0/T1 tier. It is allowed to be superhuman. Its output is
  **correctness ground truth**: the reference answers, the verified competency graph, the
  adversarial challenge results.
- **Track H (human-constrained mastery).** A *replay* of the same domain under declared
  bounds: one new concept per step, ≤25-minute sessions, no unstated tools, no lookups outside
  the declared corpus, every step logged with a reason. Its output is **the copyable
  trajectory** — and therefore the curriculum, the worked examples, and the assessment standard.

This resolves the objection DeepSeek raised (a superhuman path is not copyable) by not
pretending. **The Copyability Constraint** is checked mechanically:

> A Track H step is copyable only if **(a)** its resource use is within declared bounds,
> **(b)** every prerequisite it names is already established earlier in the trajectory, and
> **(c)** every error in the trajectory carries both a *detection cue* (how the agent knew it
> was wrong) and a *recovery action*. A concept whose smallest correct step violates (a) or (b)
> is flagged **uncompressible** and routed to chunking, human demonstration (M-C), or declared
> out of scope.

Condition **(c)** is the one that makes this apprenticeship rather than narration. An agent
that shows a corrected answer teaches the answer. An agent that shows *how it detected the
error* teaches the thing that transfers.

**The measurable output of Track H is a number: the uncompressible-concept count for a domain.**
A domain with many uncompressible concepts is one this architecture cannot yet teach, and the
system should say so rather than generate confident filler.

**Cost:** Track H is roughly 2× the generation cost of Track M and is the more expensive
artifact to build. **Falsifier:** if Track H traces, when read cold, are indistinguishable from
an ordinary well-written lesson script, then Track H is a fiction and D2 should be retracted —
we would be paying double for a narration style. This is my weakest decision (§10).

### D3 — How do we stop the agent's misconceptions from propagating?

DeepSeek is right that this becomes the load-bearing subsystem, and it is right that an agent
that is wrong and doesn't know it is worse than no agent. **Decision: four mechanisms, and the
fourth turns the problem into a product surface.**

1. **Verification tiers gate teachability.** T0 executable / T1 corroborated + challenged /
   T2H human-judged / T2L sourced-and-expiring. A claim is teachable only at or below the tier
   its verification supports. An unverified claim cannot reach instruction — not "is flagged,"
   *cannot reach*.
2. **Claims are immutable and versioned.** Every claim carries: source, authority class,
   retrieval date, expiry, confidence, verifier identity (model+version | human | executable
   test), and a `CONTESTED` field. Corrections never edit; they create a new version with a
   `SUPERSEDES` edge.
3. **Disagreement is stored, not averaged.** Every T1 claim gets a scheduled adversarial pass by
   a *different* role and, where available, a different model, whose instruction is to falsify.
   A claim that survives is `corroborated`. A claim that does not is `contested` — and a
   contested claim is teachable *only with the disagreement shown to the learner*. Averaging
   two model opinions into one confident sentence is precisely the failure mode to avoid.
4. **Errata propagation.** When a published claim is superseded, the system computes the
   downstream impact set (every competency artifact that depended on it), marks affected learner
   mastery states **stale** rather than wrong, and emits an errata event: *here is what you
   learned, here is what changed, here is the 2-minute re-check.* Governance becomes visible, and
   the system's honesty about its own corrections becomes part of what it teaches.

**Cost:** (4) requires dependency tracking on every artifact, which is real bookkeeping in the
ledger, plus a migration path when a heavily-depended-on claim changes. **Falsifier:** if in
practice claims never change, mechanism (4) is dead weight — but that would itself be evidence
the corpus is too narrow, and I would rather find that out in M5 than discover at scale that we
have no correction path at all.

---

## 2. Two decisions DeepSeek did not ask for, and the architecture needs

### D4 — Verification independence

The propagation worry in D3 has a deeper root: **an agent grading against a standard it produced
is not assessing, it is being self-consistent.** Rules:

- **T0 checks are code, never prose.** A solar load calculation is verified by recomputing it, not
  by asking a model whether it looks right. If a competency cannot be reduced to an executable
  check, it is not M-A, and it does not get T0 status (it becomes M-B, with correspondingly lower
  authority).
- **Three roles are distinct:** *teacher*, *assessor*, *adjudicator*. Different prompts, different
  context windows, and where the model roster allows, different models. The assessor never sees the
  teacher's reasoning chain before judging an attempt; the adjudicator only appears when teacher
  and assessor disagree.
- **The learner gets a second opinion by default on any failing assessment** — graded by a role that
  did not participate in teaching that concept that session.

**Cost:** roughly 2–3× the model calls per assessment round, and it is the first thing to become
slow on Colab's free tier. Mitigation: the adjudicator fires only on disagreement, and T0 checks
cost nothing. **Falsifier:** if latency makes the loop unusable, I would rather reduce the number
*of assessments* than drop independence — a fast system that lies about what the learner knows is
worthless.

### D5 — Cold start, bounded

Agent-first solves the *content* cold start — the reference trajectory exists before the learner
arrives. It does not solve the *learner* cold start. **Decision:**

- **Diagnostic budget: ≤12 minutes, ≤15 items.** Hard stop. After budget, the system must act.
- **Positional diagnosis.** Because the Track H trajectory exists, diagnosis asks *discriminating
  questions at the points where that trajectory forked*, not coverage questions. "Where are you
  relative to where the agent was at step 7?" is a sharper and cheaper question than "what do you
  know?"
- **Next item chosen by expected information gain about the next action**, not about the learner in
  general. Questions that would not change what happens next are not asked.
- **Diagnosis output is provisional.** It seeds priors. It never renders a verdict, and the UI must
  never present a diagnostic result as a fact about the learner.
- **Cold start ends when the system can name one next action with a stated reason.** That is the
  definition, and it is testable.

**Falsifier:** if 15 items cannot locate a learner well enough to name a next action, the budget was
wrong and diagnosis needs to become longitudinal — a different product shape, and worth knowing early.

---

## 3. The orchestrator: one state, called the Ledger

DeepSeek's strongest objection to the vision is that Option C names the modules and never says what
coordinates them. **Decision: the only shared mutable thing in the system is an append-only event log.**

- **The Ledger** is the source of truth: an append-only JSONL event stream, one file per learner,
  checkpointed to Google Drive.
- **Everything else is a projection.** The learner model, the competency graph state, mastery
  estimates, retention decay, the next-action queue — all are deterministic functions of the ledger,
  recomputable at any time.
- **Modules do not share state and do not negotiate.** Each is a pure function
  `(projection, task) → events`. Conflicts between modules are not resolved by argument; they are
  resolved by re-projecting and letting the newer evidence dominate. Modules cannot "disagree about
  state" because no module holds state.
- **Consistency across agents is therefore structural, not by protocol.** This is the answer to
  "state synchronization becomes difficult."

v1 module set is deliberately **six**, not ten: Orchestrator, Curriculum/Path, Teaching, Assessment,
Mastery, Governance. Retention/Memory is a projection, not a module. Research and Exam are deferred —
they are features of the same loop, not new loops.

**Why this matters for Colab specifically:** an append-only ledger in Drive is exactly what survives
a reclaimed runtime. Ephemerality stops being a risk and becomes a resume case. See D8.

**Cost:** projections must be cheap (they will be — the ledger for one learner is small) and the
event schema must be designed up front, which is the one thing in M0 that is expensive to change
later. **Falsifier:** projection cost becomes perceptible in the UI (>200 ms to re-render state).

---

## 4. D7 — The UI: the Apprenticeship Surface

Two things must both be true. **Best practices are the floor** — they are non-negotiable and they are
not interesting. **The design position is the ceiling**, and it is derived from the architecture, not
from an edtech pattern library.

### 4.1 The design position

**The UI is two trajectories, and its central job is to make the divergence between them legible.**

1. **The agent demonstrates visibly.** The primary content type is not "explanation," it is the agent
   *working the problem in front of the learner* — including uncertainty, dead ends, and error
   recovery. This is required by D2(c): the learner cannot copy what they cannot see, and tacit
   judgement must be externalised or imitation is impossible.
2. **The agent's trajectory is an artifact the learner can inspect.** How it learned this, what it got
   wrong first, what it had to revisit. The honest path is the copyable path, and it also normalises
   the learner's own struggle.
3. **Diagnosis is positional, not interrogative.** The screen does not say "let's assess you." It says
   "here is the route; here is where you probably are; I need three answers to move you." Every
   diagnostic question shows what it is discriminating between.
4. **Mastery is an evidence stack, never a percentage.** Show the individual pieces of evidence, their
   tier, their age, their verifier, and the retention curve. A progress bar is a lie about mastery and
   is banned outright.
5. **Assessment shows the standard first.** The learner sees the agent's Track H demonstration *before*
   attempting, then their own attempt, then the diff. Assessment is comparison against a demonstrated
   standard, not an abstract rubric.
6. **The learner model is inspectable and correctable.** Any screen can answer "what does it believe
   about me, and why?" in one interaction. A learner correction is first-class evidence written to the
   ledger — not a support ticket. **This is the single most important screen in the product.**
7. **Divergence is information, not failure.** When the learner takes a different route, the UI shows
   it and asks whether it matters — local constraints, goals, or context the agent could not know.
8. **Offline/interrupted is a designed state.** Colab's runtime will be reclaimed. That is not an error
   screen; it is a "your work is saved, here is what resumes" state. Copying continues.
9. **Imitation is a first-class mode with a step of its own:** watch → attempt → compare → correct →
   retry. Apprenticeship rendered as interface.
10. **Two products, not two tabs.** The teacher surface is a different application over the same ledger.
    It answers one question: *where are my learners diverging from the trajectory, and why?* The
    teacher is also the **T2-L / M-D knowledge source** — the person who supplies what the agent
    structurally cannot know (local prices, local practice, community norms, this classroom). That is a
    stronger role than "monitoring divergence."

### 4.2 The floor (non-negotiable, no credit for any of it)

Mobile-first (Africa context, §24 of the vision) · WCAG AA contrast · full keyboard navigation · visible
focus · reduced-motion respected · works at 360 px · ≥44 px touch targets · no layout shift while a model
responds · resumable in ≤3 taps · state surviving a refresh at every step · explicit save/sync indicator ·
plain-language error messages · no dark patterns.

Motion gets one deliberate exception: **the trajectory divergence animation is the only place time is
spent on motion**, because spatial continuity is what the learner is being asked to perceive. Everything
else moves instantly.

### 4.3 Banned outright

Progress rings as mastery · streaks · leaderboards · badges · confetti · a chat bubble as the primary
surface · "AI tutor" framing · percentage-complete · gamified anxiety · anything that converts a
provisional hypothesis about a learner into a label about a person.

### 4.4 Screen inventory (v1)

| Surface | Answers | Primary element |
|---|---|---|
| **Route** | Where does competence live, and where am I on it? | The two trajectories, side by side, navigable |
| **Beliefs** | What does it think about me, why, and how do I correct it? | Belief cards with evidence, tier, age, and a "that's wrong" control |
| **Next** | What is the one thing I should do now, and why? | Single action + stated reason + what it will change |
| **Watch** | Show me how this is done | Agent Track H trace, playable step by step, with uncertainty visible |
| **Attempt** | Let me try | Working surface + immediate diff against the demonstrated standard |
| **Evidence** | What have I actually proven? | Mastery evidence stack, retention curves, tier of each piece |
| **Errata** | Did anything I learned change? | Correction events, what changed, the re-check |

Seven surfaces. If a screen cannot say which of these it is, it does not ship.

---

## 5. D8 — Colab is not a deployment target, it is a constraint set

Colab was chosen deliberately: it is free, it is reachable by the learners in §24 of the vision, and its
built-in AI API means no key management. But its constraints are not annoyances to work around — several
of them align with the architecture, and the rest become design rules.

| Colab reality | Design rule derived from it |
|---|---|
| Free runtime reclaimed when idle; ~12 h ceiling | **Checkpoint-resume is mandatory, not a feature.** The ledger is written to Drive on every committed event. The notebook must be runnable top-to-bottom in ≤5 min and resumable from any checkpoint. |
| `google.colab.ai` = `ai.generate_text(prompt, model_name=..., stream=True)`; free tier = gemini-2.5-flash / flash-lite, monthly caps; Pro+ = wider roster | **Roles bind to a config-driven model roster** and degrade gracefully. A free user runs teacher/assessor on flash and the adjudicator on flash-lite; a Pro user gets stronger models on the adjudicator seat. Nothing may hard-code a model name. |
| No native tool-calling / function-calling API | **We define the action protocol**: every agent turn must emit a strict JSON action block, parsed and validated, with a bounded repair loop (max 2 retries) and a hard failure that is *visible in the ledger* rather than silently degraded. |
| No embedding API guaranteed | **Retrieval is lexical + structural over the competency graph**, not vector search. Prerequisites and concept edges do the work that similarity search would otherwise paper over. Honest bonus: graph-structural retrieval is the better fit for a prerequisite-structured curriculum anyway. |
| Port proxy does not reliably carry WebSockets (documented failures across projects, 2021–2024) | **No WebSockets.** Transport is request/response + a cursor-based poll on the ledger. Perceived streaming comes from the ledger growing, not from a socket. |
| Session/state is ephemeral; secrets are not appropriate in a notebook | **Nothing sensitive in the notebook. Learner data lives as JSONL in Drive. This is a demo-grade privacy posture and is explicitly NOT production-safe for real learners' data.** Stated here so it cannot be mistaken for an oversight. |
| Free-tier monthly caps are shared across all model calls | **Per-session token budget counter, visible in the UI.** Track M artifacts (the expensive, reusable ones) are computed once and cached in Drive. Only learner-time calls are variable cost. |

### 5.1 Notebook layout

```
uli_colab.ipynb
├── 00  bootstrap      installs, Drive mount, ledger load-or-create, health check
├── 01  contracts      event schema + action protocol + validators  (frozen at M0)
├── 02  projection     ledger → learner model / competency state / mastery
├── 03  roles          teacher | assessor | adjudicator adapters over google.colab.ai
├── 04  verifiers      T0 executable checks · T1 corroboration · T2 delegation stubs
├── 05  ingestion      source → concepts → prerequisites → competencies → coverage report
├── 06  track_m        machine mastery pass → verified competency graph (cached)
├── 07  track_h        human-constrained replay → copyable trace + constraint checker
├── 08  loop           diagnose → teach → attempt → assess → update → next
├── 09  governance     versioning, quarantine, adversarial pass, errata propagation
├── 10  server         FastAPI/uvicorn on a port + serve_kernel_port_as_iframe
├── 11  demo           the five acceptance tests, runnable end to end
```

UI is served as a static bundle by cell 10 and framed in the notebook. Development of the interface
happens outside the notebook (fast reload); the notebook only serves and wires it.

---

## 6. D9 — v1 scope: one domain, one learner, one loop

**Do not build a platform. Build one closed loop and prove it turned.**

**Domain: solar PV system sizing.** Chosen because it has a genuine executable verifier (energy balance,
voltage/current, autonomy, derating — a wrong answer is *wrong*, checkable in code), it is local to the
African context in §24, and it has an obvious M-C boundary (design is M-A; installation is M-C) which
forces the delegation interface to be real rather than theoretical.

**Detector validation bench: fractions / proportional reasoning.** Chosen because the misconception is
pre-registered and documented from the outside ("a larger denominator means a larger fraction"). That means
the misconception detector can be tested against a known answer key instead of being graded by the same
agent that wrote the lesson. This is the only place in v1 where we can validate the detector honestly, and
that is exactly why it is included.

### Acceptance tests — v1 is done when all five pass

| # | Test | Passes when |
|---|---|---|
| **T1** | Two-track mastery | Track M and Track H exist for 3 solar competencies; Track H passes the Copyability Constraint checker; every uncompressible concept is flagged, or the count is zero *and defendable*. |
| **T2** | The loop turns | A learner receives ≤12 min diagnosis → one next action with a stated reason → teach → attempt → assess → updated model, with the ledger showing the mastery estimate move **and why it moved**. |
| **T3** | Detector honesty | On the fractions bench, seeded with the known misconception, the detector names the misconception (not "incorrect") and the targeted intervention measurably reduces repeat-error rate. |
| **T4** | Kill-and-resume | Kill the Colab runtime mid-loop, restart, restore from Drive, and continue in the same state. No learner-visible loss. |
| **T5** | Governance fires | Inject a false claim → it is quarantined and never reaches instruction. Supersede a true claim → an errata event appears for a learner who learned the old version, with the re-check. |

**Explicitly out of scope for v1:** authentication, multi-tenancy, payments, teacher surface (designed,
stubbed only), M-C/M-D assessment pipelines, exam mode, research module, any deployment beyond a Colab
URL, and any claim of production readiness. Any of these appearing in v1 is scope failure.

---

## 7. D10 — The self-improvement boundary, and a number for the AGI claim

The vision says the architecture could take itself to AGI through recursive self-improvement. My ruling
is that this is **testable rather than asserted**, and the test has a name.

**In v1, self-improvement is over artifacts, not parameters.** The agent improves its domain models, its
Track H trajectories, its intervention choices and its error catalogue. It does not improve itself at the
weight level — in Colab, via `google.colab.ai`, it structurally cannot. Any claim beyond that is out of
scope for v1 and should be written down as such rather than implied.

**The AGI-relevant claim reduces to one measurable quantity.** Section 21 of the vision — source material
→ knowledge extraction → concept mapping → prerequisite mapping → competency mapping — is where the entire
"universal" claim lives, and it is doing enormous unexamined work. So measure it:

> **ADIR — Autonomous Domain Ingestion Rate.** Human-hours of structuring required per *verified*
> competency, and the verification-failure rate on T0 domains.

- ADIR **→ 0** with a failure rate under 5% on a domain the system has never seen: the universal claim is
  live, and the AGI question is legitimate.
- ADIR **stays high** or the failure rate does not fall: this is an extremely good adaptive tutor built on a
  hand-structured knowledge graph. That is a valuable product and it is **not** AGI, and the honest thing is
  to be able to tell the difference by measurement rather than by argument.

I am proposing we compute ADIR once, in M1, on a small unseen domain, and record the number in this repo —
even if it is embarrassing. A number that can embarrass you is the only kind worth having.

---

## 8. Where DeepSeek is right, and where I part company

**Right, and it changes the build:**

- Governance is load-bearing, not a support function. Accepted in full (D3).
- The three questions determine feasibility. Accepted, which is why this document is mostly their answers.
- "Best practices are the floor, not the ceiling" — correct, and the reason D7 is written as positions with a
  floor attached rather than a checklist.
- The agent's trajectory solves cold start. Accepted (D5) — with the correction that it solves the *content*
  cold start, not the learner's.
- The teacher's irreplaceable value is the context the agent could not have known. Accepted and extended (D7.10).

**Where I part company:**

1. **"Cognitive vs physical" is the wrong cut.** The right cut is *which independent verifier exists* (D1). A
   purely cognitive domain with no independent verifier (e.g. interpreting a culture's norms) is as unholdable
   as welding, and a physical domain with instrumented verification is more holdable than either. The cut
   determines what the system may assert, and getting it wrong is how the system ends up confident about things
   it cannot check.
2. **"The agent's mastery is ground truth" smuggles the problem back in.** Ground truth comes from *verifiers*.
   The agent's mastery is a *claim* that a verifier must accept. Once that is stated, D3 and D4 follow
   mechanically, and the propagation risk stops being a philosophical worry and becomes a test (T5).
3. **The agent's trajectory is not the standard for M-C and M-D domains.** DeepSeek's "assessment is comparison
   against the agent's demonstrated standard" is right for M-A/M-B and a category error for embodied and local
   competence, where the agent cannot hold the standard at all. The standard there is a human's.
4. **Divergence monitoring undersells the teacher.** The teacher is not only watching where learners leave the
   path; the teacher is the *legitimate source* for what the system cannot know. That is a structural role.

---

## 9. Milestones and greenlight gates

Each milestone ends in a gate. **A gate is passed by a demonstration, not by a report.** If a gate fails, the
plan pauses at that milestone rather than proceeding — the point of gates is to make failure cheap.

| M | Name | Delivers | Gate |
|---|---|---|---|
| **M0** | Spine | Ledger + event schema + action protocol + projections + UI shell + Colab serve + checkpoint-resume | Write and replay a hand-authored event stream; restart the runtime; identical projection. UI shows Route/Beliefs/Next with empty states. |
| **M1** | Truth | T0 verifiers; ingestion of solar corpus → competency graph; **Track M**; **ADIR measured** | A wrong sizing answer is rejected by code, not opinion. ADIR number recorded in the repo. |
| **M2** | Teachability | **Track H** replay + Copyability Constraint checker + uncompressible-concept report | A human reads a Track H trace *cold* and can answer: what did it get wrong first, how did it know, what would it do differently. If not — D2 fails its falsifier and we stop and retract. |
| **M3** | The loop | Diagnosis (≤12 min/15 items) → teach → attempt → assess → update → next; three distinct roles | Acceptance **T2**. Ledger shows why the mastery estimate moved. |
| **M4** | The surface | Full Apprenticeship Surface: all 7 screens, Watch/Attempt/Evidence/Errata built | The author can answer "what does it believe about me and why" in under 30 s, unaided, from any screen. |
| **M5** | Governance | Versioning, quarantine, adversarial pass, errata propagation, T3 fractions bench | Acceptance **T5**, then **T3**. Detector names the misconception, and it is the right one. |

**M0–M2 is the recommended first greenlight.** M3–M5 should be re-reviewed with M0–M2 evidence in hand,
because M2's outcome is the one that can invalidate D2 and therefore the shape of everything after it.

Time and cost: each milestone is a session-scale unit of work in a Colab notebook; the free-tier model caps
are the binding constraint, and Track M ingestion (M1) is the one operation that may need to be run in
chunks across sessions. Caching is designed in for exactly this reason.

---

## 10. Pre-registered weaknesses

Written down now so nobody has to discover them later and wonder whether they were noticed.

1. **D2 is the weakest decision.** The Copyability Constraint is mechanically checkable, but "copyable" is a
   claim about a *human*, and no checker we can write proves it. If Track H traces do not read differently from
   good lesson plans, D2 is expensive theatre. That is why M2's gate is a cold-read by a human, and why M3+
   is not greenlit yet.
2. **D5's numbers are guesses.** 12 minutes and 15 items are chosen to be *disprovable*, not because they are
   known-good. Expect them to move.
3. **D8's polling may feel dead.** Without WebSockets, a slow model call looks like a frozen screen. The
   mitigation (the ledger grows visibly, timers and state changes render immediately) is a design answer to an
   infrastructure problem, and it may not be enough.
4. **M-C/M-D are stubbed, so "universal" is not tested in v1.** v1 tests two classes out of four. That is a
   deliberate narrowing and it means the universality claim remains unproven at the end of M5.
5. **Colab is not a product.** A demo that only runs by pressing Run-All in one person's browser is not
   deployable, and none of these milestones change that. That is a later decision and it should not be smuggled
   into this one.
6. **The privacy posture is demo-grade.** Learner data in a Google Drive folder, in a notebook runtime, is not
   appropriate for real learners — especially minors. Nothing in v1 should be used with anyone but the author.

---

## 11. Review checklist for DeepSeek

Paste the following back with answers. I would rather be told these are wrong now than in M3.

**On D1 (mastery classes)**
1. Is "verifier availability" really a cleaner cut than "cognitive vs physical," or have I just renamed the same distinction?
2. Is there a domain where M-A and M-B both fail and the taxonomy breaks?

**On D2 (two-track, and the Copyability Constraint)**
3. Is the Copyability Constraint mechanically meaningful, or does it just describe good lesson design?
4. If Track H is ~2× the cost of Track M and its only payoff is a trace, is there a cheaper way to get the
   copyable trajectory — e.g. deriving it from Track M post hoc with a constraint checker?
5. Is "uncompressible-concept count" a real signal about a domain, or a measure of the agent's writing ability?

**On D3/D4 (governance; verification independence)**
6. Is role separation (teacher ≠ assessor ≠ adjudicator) sufficient to prevent self-consistency grading, given
   all three roles may run the same underlying model?
7. Does errata propagation create an unbounded notification debt — every correction fanning out to every affected
   learner forever? What is the right retention rule?
8. Is "contested claims are teachable with the disagreement shown" honest pedagogy or a way of dodging a decision?

**On D6 (the ledger)**
9. Is append-only + projection actually sufficient for cross-module consistency, or does it just move the
   conflict into projection time?
10. What breaks first — schema evolution, projection cost, or the ledger growing faster than it can be read?

**On D5 (cold start)**
11. Is positional diagnosis real, or does it collapse into a normal quiz once you try to implement it?

**On D7 (the UI)**
12. Which of the seven surfaces is unnecessary, and which is missing?
13. Is "mastery as evidence stack, never a percentage" actually usable, or does it push the learner's real
    question ("am I getting better?") onto the learner to answer by inference?
14. Does the learner-correction control ("you're wrong about me") create a path for a learner to talk the system
    out of accurate evidence?

**On D10**
15. Is ADIR the right metric, or is there a better single number for "can this actually teach an unseen domain"?
16. If ADIR is small and the T0 failure rate is low, does that in fact support the AGI claim — or does it just
    mean ingestion is easy for structured domains?

**Greenlight asks:**
- **Approve M0–M2** as specified, with M3–M5 held for review after the M2 gate.
- **Approve or overturn D1, D2, and D7**, since those three determine the shape of everything downstream.
- **Approve ADIR as a committed measurement** in M1, published in this repo regardless of outcome.

---

## Appendix A — Ledger event types (v1)

Frozen at M0; changing it later is the expensive thing.

`CLAIM_ASSERTED` · `CLAIM_VERIFIED` (tier, verifier) · `CLAIM_CONTESTED` · `CLAIM_SUPERSEDED` ·
`CLAIM_QUARANTINED` · `CONCEPT_DEFINED` · `PREREQUISITE_LINKED` · `COMPETENCY_DEFINED` ·
`TRACK_M_COMPLETED` · `TRACK_H_STEP` · `TRACK_H_CONSTRAINT_VIOLATION` · `CONCEPT_UNCOMPRESSIBLE` ·
`DIAGNOSTIC_ITEM_ASKED` · `DIAGNOSTIC_BUDGET_EXHAUSTED` · `BELIEF_UPDATED` (with cause) ·
`BELIEF_CORRECTED_BY_LEARNER` · `INTERVENTION_SELECTED` (with reason) · `ATTEMPT_SUBMITTED` ·
`ASSESSMENT_RECORDED` (tier, assessor role) · `ASSESSMENT_DISPUTED` · `ADJUDICATION_RECORDED` ·
`MASTERY_EVIDENCE_ADDED` · `MASTERY_DECAYED` · `MASTERY_MARKED_STALE` · `ERRATA_ISSUED` ·
`ERRATA_ACKNOWLEDGED` · `GOAL_SET` · `DELEGATION_REQUIRED` (M-C/M-D) · `ROLE_FAILURE` ·
`BUDGET_CONSUMED`

Two properties matter more than the list: **every belief-changing event carries a `cause` field** (so the
Beliefs screen can always answer "why"), and **every event carries its role and model identity** (so D4
independence is auditable after the fact, not just asserted).

## Appendix B — Domain selection, stated plainly

Solar PV sizing was chosen over three alternatives:

- **Programming** — the strongest T0 story (run the tests) and rejected for v1 only because its M-C boundary is
  weak; almost everything is machine-checkable, so the delegation interface would go untested.
- **Fractions** — the best detector-validation bench and a poor architecture showcase; kept as the T3 bench.
- **A history or business domain** — the purest M-B test and rejected because with no M-A anchor there is no
  ground truth against which to check the whole pipeline. It is the right M6 domain, after governance exists.

Solar is the only candidate that exercises M-A, M-B *and* the M-C boundary in one domain, in a context that
matches the vision's §24, with a verifier that is code.
