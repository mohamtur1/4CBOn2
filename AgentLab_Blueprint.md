# AgentLab — Colab Edition
## Autonomous research, agent building, and Kaggle competition workflows

**Technical blueprint · Version 1.0 · 21 October 2026**

**Status:** Proposed architecture, not a running application. No model, benchmark, or Kaggle submission has been executed as part of this blueprint. All budgets and acceptance thresholds below are design defaults, not measured performance or vendor price quotations.

**Product decision:** Build a single-user research workbench inside Google Colab. The user describes an objective; the system creates a bounded agent, performs research or machine-learning experiments, checks its work, and produces an auditable result. It can submit eligible, supported Kaggle entries when explicitly authorized. It can also propose and test improvements to its own workflows, but cannot change its safety rules or approve its own promotion.

---

# Part I — Explain it as if I were two

You have a little team of robot helpers.

You say, **“Please find this out,”** or **“Please try this puzzle.”**

One helper makes a plan. Other helpers do the work. A checker looks for mistakes.

They put their work in a box so they can find it again.

There are two big buttons:

- **Make a Helper:** Tell it what job to do.
- **Ask a Question:** Ask something and get an answer.

Kaggle is a place with puzzles. Your helpers can try the puzzles they know how to do. Before they send an answer, they check it and make sure you have said they may send it.

They can practice getting better. They cannot give themselves a gold star: the checker must test them.

Sometimes their computer goes to sleep. They save their work, and you can wake them up and continue.

**You say the job. The helpers do the little steps. You keep the important keys.**

---

# Part II — Explain it as an adult

## 1. What you are building

AgentLab is a small, software-controlled research organization, not a single chatbot with a long prompt. It has three connected capabilities:

1. **Research production:** Answer questions using retrievable evidence, citations, analysis, and review.
2. **Agent construction:** Turn a natural-language objective into a reusable, versioned agent specification with a plan, tools, permissions, budget, and success criteria.
3. **Measured improvement:** Compare candidate workflows against a preserved baseline using fixed evaluation procedures, then present a promotion or rejection report.

Kaggle participation is a specialized workflow within the Agent Builder. A competition agent inspects the competition, checks its rules and submission type, prepares a valid pipeline, runs permitted experiments, verifies the artifact, and submits only within its authorization.

**“Autonomous” means it chooses and carries out the intermediate steps within a pre-agreed boundary. It does not mean unlimited permissions, guaranteed correct answers, unlimited compute, or permission to bypass competition rules.**

## 2. Decisions already made

| Decision | Chosen design |
| --- | --- |
| Primary purpose | Both evidence-based research and system-level improvement |
| First environment | One interactive Google Colab notebook, used by one owner |
| Interface | Notebook-native tabs using ipywidgets; no public web tunnel by default |
| Main entry points | Agent Builder and Ask a Question |
| Autonomy | Autonomous planning, research, and bounded experiments; scoped submission authority |
| Deployment control | Human approval before promoting a new default agent version |
| Initial agents | Supervisor, Researcher, Analyst/Experiment Planner, and Reviewer |
| Initial model strategy | One hosted model provider; separate role contexts rather than four always-running models |
| Default model integration | Gemini API through a provider adapter; a stable eligible model is selected by capability and budget checks at setup |
| Search integration | A metered search adapter, initially Tavily; URL/document-only mode when no search key is configured |
| Storage | Local SQLite working database, immutable artifacts, and versioned project backups in Drive |
| First Kaggle support | Classic tabular prediction competitions with supported metrics and formats |
| Advanced competition support | Notebook/code, image, text, time-series, simulation, and hackathon adapters added only after their own acceptance tests |
| Foundation-model training | No foundation-model weight updates in version 1 |
| Arbitrary generated code | Proposed and quarantined, not automatically executed in the privileged Colab runtime |

The interface choice is deliberate: Colab prioritizes notebook interaction, imposes variable runtime/resource limits, and restricts bypassing the notebook UI with a separate web UI on its free managed runtimes. This is an interactive prototype, not a 24/7 hosted service. [1](https://research.google.com/colaboratory/faq.html)

## 3. How you will use it

### First setup

1. Open the notebook, save your own copy, and run its setup cells.
2. Grant only the notebook access it needs. Store provider and Kaggle credentials in Colab Secrets, not in prompts or saved notebook cells. The official Gemini Colab quickstart demonstrates the Secrets pattern. [19](https://colab.research.google.com/github/google-gemini/cookbook/blob/main/quickstarts/Get_started.ipynb)
3. Connect a project backup location. Review the standing permission policy and spending caps.
4. Run the connection checks and a submission-disabled demonstration.
5. Enable live Kaggle submission only if you want it; otherwise every competition agent stops at a verified, downloadable artifact.

### Normal use

- To get an answer, open **Ask a Question**, type the question, and click **Research**.
- To create a reusable worker, open **Agent Builder**, describe the outcome, and click **Build & Run**.
- To compete, include a Kaggle URL and the instruction to prepare or submit an entry.
- Watch **Runs & Results** for the plan, current step, evidence, experiments, costs, artifacts, and blockers.
- Open **Improvement Lab** to inspect a proposed change and approve or reject promotion.
- After a runtime reset, run setup again and click **Restore Project / Resume**. Work resumes from the last valid checkpoint, not necessarily the exact instruction that was interrupted.

The system makes ordinary technical decisions itself. The owner still has to provide credentials, give required consents, accept applicable competition agreements, and authorize actions outside an existing policy. These are obligations, not design clarifications.

---

# Part III — Build specification

## 4. User interface

Use six top-level tabs. Keep advanced controls inside expandable panels. ipywidgets supplies notebook controls including text areas, buttons, progress indicators, and tab panels. [6](https://ipywidgets.readthedocs.io/en/stable/)

| Tab | Main controls | Main output |
| --- | --- | --- |
| **Agent Builder** | Goal, attachments/competition URL, template, budget, Build & Run, Save Agent | Validated agent card, workflow, permissions, success criteria |
| **Ask a Question** | Question, optional files/URLs, Quick or Research mode, Research button | Answer, citations, uncertainties, supporting evidence, follow-up options |
| **Runs & Results** | Run selector, Pause, Resume, Stop, artifact download | Timeline, task status, model comparisons, costs, submission status |
| **Knowledge** | Search, source filters, project scope, verification filter | Claims, evidence, contradictions, provenance, reusable procedures |
| **Improvement Lab** | Propose/Test, candidate comparison, Approve Promotion, Reject, Roll Back | Version diff, benchmark report, regression results, decision history |
| **Settings & Permissions** | Connection checks, caps, authorized tools, submission toggle, backup/restore | Connection health, effective policy, project state |

### Global status strip

Show runtime connection, active run, spend used/reserved, remaining time, last durable checkpoint, live-submission mode, and **Stop All**. Red means blocked or failed, amber means waiting/uncertain, and green means a verified completed step. Never show green merely because an LLM claims success.

**Stop All** prevents new brokered actions and cancels local managed work where possible. It cannot undo an already accepted submission, recall disclosed information, or guarantee cancellation of a remote job. Include Colab's native interrupt control as a fallback.

### Agent Builder wireframe

```text
AGENT BUILDER
What should this agent accomplish?
[ Describe the goal, or paste a Kaggle competition URL ... ]

Template: [Auto-detect]   Time: [60 min]   API cap: [$5]
Mode:     [Prepare only / Submit if explicitly authorized]

[Build & Run]   [Save Agent]   [Advanced]

AGENT CARD
Goal | Plan | Allowed tools | Budget | Evaluation | Blockers

PROGRESS
Inspect -> Plan -> Baseline -> Improve -> Verify -> Submit -> Report
```

A prompt can narrow permissions but cannot enlarge the standing policy. If a prompt asks for a prohibited action, the builder records the limitation and either runs the safe subset or returns a specific blocker.

### Ask a Question behavior

Research mode is the default. Quick mode is available for explanations that do not require live evidence. Research mode searches approved sources, extracts evidence, separates claims from quotations, drafts an answer, and runs a review. Display sources and retrieval dates. Do not fabricate citations or label self-reported confidence as a calibrated probability.

Follow-up questions inherit the relevant project context, not all historical documents. A **Turn into Agent** button converts a question into a saved agent specification. It does not silently authorize publishing, competition entry, or spending beyond the question budget.

## 5. Organization and responsibilities

```text
USER / EXECUTIVE DIRECTOR
       |
       v
SUPERVISOR / RESEARCH ORCHESTRATOR
       |
       +--> Research: retrieve sources and evidence
       +--> Analysis: compare explanations and synthesize
       +--> Experimentation: propose bounded trials
       +--> Verification: inspect claims, tests, and artifacts
       |
       v
TRUSTED SERVICES
Policy broker | Queue | Memory | Evaluation | Budgets | Versions
       |
       v
Permitted external services, data stores, and competition runtimes
```

### Four initial model roles

- **Supervisor:** Converts the goal into a task graph, chooses the template, delegates, merges results, and proposes next steps. It does not control the policy broker or grading rules.
- **Researcher:** Retrieves sources, records evidence, and identifies missing information. Retrieved content is data, not instructions.
- **Analyst / Experiment Planner:** Produces competing explanations or structured experiment proposals. For Kaggle, it chooses among trusted pipeline options and bounded parameters.
- **Reviewer:** Receives the task, artifact, evidence, and evaluation rubric in a fresh context. It attempts to identify errors and unsupported conclusions.

Knowledge management and performance measurement are primarily software services, not additional chatty agents. Use one role invocation at a time by default, with at most two approved I/O operations in parallel. This keeps costs and debugging manageable.

Separate contexts reduce direct influence but do not make a shared underlying model genuinely independent or infallible. Deterministic checks and external measurements take precedence over model opinions. A different evaluator model can be added later if it measurably helps.

## 6. System architecture and technology

### Control path

```text
Notebook UI
    -> Request validation + policy compilation
    -> Versioned agent specification
    -> Persisted task graph
    -> Supervisor + bounded worker invocations
    -> Tool broker / trusted pipeline runner
    -> Deterministic checks + reviewer
    -> Artifact, report, or authorized submission
    -> Memory + checkpoint + audit events
```

### Chosen implementation

| Layer | Version-1 choice |
| --- | --- |
| UI | ipywidgets + IPython display, native notebook output |
| Orchestration | Plain Python, explicit state machine, asyncio for permitted I/O |
| Data contracts | Pydantic models; reject unknown or disallowed fields |
| LLM access | Gemini adapter first; provider-neutral interface for later substitution |
| Retrieval | Search adapter + HTTP client + document parsers with size/time limits |
| Task queue | SQLite task table, dependencies, leases, attempts, and checkpoints |
| Knowledge retrieval | SQLite full-text search initially; vector index only after evidence that it improves retrieval |
| Tabular ML | pandas, NumPy, scikit-learn; optional reviewed boosting libraries |
| Notebook generation | nbformat with reviewed templates |
| Kaggle access | Official Kaggle CLI through a typed wrapper; no shell-string interpolation |
| Testing | pytest, deterministic fixtures, recorded/mocked external responses |
| Configuration | Versioned YAML/JSON, reviewed templates, dependency lock file |
| Persistence | Local working files plus versioned Drive snapshot bundles |

The current Kaggle CLI documentation describes installation, API-token/OAuth authentication, and a Python 3.11+ requirement. The setup cell must check the actual Colab Python version and the installed CLI capabilities before enabling live integration. Pin a version only after the build has passed its integration tests; do not silently upgrade dependencies during a run. [3](https://github.com/Kaggle/kaggle-cli/blob/main/docs/README.md)

### Notebook layout

1. **Read Me / safety summary:** What runs, what is sent to services, and what requires consent.
2. **Install and preflight:** Checked dependencies, runtime capabilities, available space, and connection health.
3. **Secrets and project restore:** Read authorized secrets, restore a verified checkpoint, and display effective permissions.
4. **Launch UI:** Render the six tabs and connect the controller.
5. **Run controller / recovery:** Execute bounded interactive work and expose recovery controls.
6. **Developer diagnostics:** Tests and sanitized diagnostics, hidden behind an advanced section.

Keep active work tied to real interactive notebook execution; do not add anti-idle scripts or pretend a background thread makes Colab permanent. The UI/controller implementation must pass a responsiveness test in Colab, including progress updates, cancellation, and recovery.

### Colab limitations to design around

Colab virtual machines are temporary, have enforced lifetimes, and offer variable compute availability. The notebook file being saved does not preserve the VM's complete execution state. [1](https://research.google.com/colaboratory/faq.html)

Therefore:

- No 24/7 service promise and no deadline guarantee across disconnections.
- Checkpoint at completed task boundaries and before/after external side effects.
- Preserve model artifacts and trial state where the training library supports safe resumption.
- Reconcile the state of remote Kaggle work after reconnecting.
- Provide an exportable project bundle.
- Move the same control plane to persistent infrastructure later if unattended operation becomes a requirement.

## 7. Agent construction contract

The builder creates a **declarative agent specification**, not an unrestricted new program. A saved agent consists of its goal, role prompts, trusted template, permitted tool profile, workflow, evaluation contract, budget, and version.

### Example prompt

> Create an agent for https://www.kaggle.com/competitions/titanic. Compare up to five leakage-safe tabular pipelines within 60 minutes and $5 of API spending. Use no external datasets. If my account has accepted the rules and this run is authorized, submit one verified entry. Otherwise prepare the files and explain the blocker.

### Example specification

```yaml
schema_version: 1
name: Titanic Competition Agent
template_id: kaggle.tabular.v1
competition_slug: titanic
goal: Produce one valid, locally evaluated competition entry.
workflow:
  - inspect
  - plan
  - baseline
  - improve
  - verify
  - submit_if_authorized
  - report
budget:
  wall_minutes: 60
  api_usd: 5
  max_trials: 5
  max_retries_per_task: 2
  max_submission_attempts: 1
policy:
  external_datasets: forbidden
  raw_data_to_hosted_llm: forbidden
  arbitrary_python_execution: forbidden
  submission: explicitly_requested_and_policy_allowed
  baseline_promotion: human_approval
success:
  - valid_split_and_metric
  - reproducible_pipeline
  - valid_submission_artifact
  - no_critical_policy_failure
```

This is a proposed configuration schema, not an assertion that the integration has been implemented. Concrete target columns, metrics, IDs, file formats, and split policies must come from inspected competition materials, not guesses derived from the example.

The compiler calculates:

**Effective permission = owner policy ∩ agent template capability ∩ run request ∩ competition rules.**

Policy grants live outside the editable agent specification. Changing a role prompt cannot grant a new tool. Every run pins the agent version, template hash, policy version, model ID, dataset fingerprint, and dependency-lock hash.

## 8. Kaggle: one prompt, multiple correct routes

### The honest interpretation of “any competition”

**Any competition URL can be accepted for inspection. Universal unattended participation cannot be guaranteed.** Competition rules, eligibility, submission formats, resources, deadlines, and supported interfaces differ. Kaggle officially distinguishes classic prediction, code, simulation, and hackathon workflows. [9](https://www.kaggle.com/docs/competitions)

The product must return one of:

- **Supported:** Can run end to end under the current policy.
- **Supported with handoff:** Can prepare the work, but a required platform or consent step remains.
- **Blocked:** Eligibility, rules, budget, deadline, or authorization prevents the action.
- **Adapter required:** This competition needs a workflow that is not yet implemented.

Never display “submitted” for a file that has only been generated or for a notebook that has only been pushed.

### Capability matrix

| Competition family | Planned route | Coverage commitment |
| --- | --- | --- |
| Classic tabular prediction | Train locally, validate the required artifact, upload through the supported API/CLI | First end-to-end live target; supported metrics/formats only |
| Code / notebook | Generate a private Kaggle Notebook, execute on Kaggle, submit a specific notebook version/output | Add after dedicated integration tests; authorized manual handoff if unavailable |
| Image, text, or time-series prediction | Dedicated data loaders, validation schemes, models, and resource checks | Later adapters; not a tabular template with a renamed label |
| Simulation / game agent | Competition environment, executable-agent contract, episode testing, required packaging | Competition-specific adapter required |
| Hackathon / writeup | Assemble the required project, report, links, and submission checklist | Draft/prepare first; publishing requires the correct supported route and authority |
| Invite-only, closed, ineligible, or unsupported | Explain the exact constraint and preserve useful preparatory work | No bypass, false entry, or false success |

Code competitions require notebook-based entries rather than an ordinary local prediction upload. Hackathons and simulations have materially different deliverables. Competition-specific requirements remain authoritative. [9](https://www.kaggle.com/docs/competitions)

### Kaggle run, step by step

1. **Identify:** Parse a Kaggle URL/slug; restrict retrieval to approved Kaggle endpoints and validated source links.
2. **Inspect:** Record description, evaluation metric/direction, timeline, data files, submission mechanism, resource constraints, eligibility, external-data/model restrictions, and team/daily limits. Store source URL, retrieval time, and a rules snapshot hash. Mark missing critical information as unknown, not permitted.
3. **Confirm access:** Authenticate, verify account access, and check that rules have been accepted. Kaggle requires rule acceptance before downloading competition data or submitting. The owner handles any required agreement or identity step in Kaggle's UI; the agent never impersonates consent. [9](https://www.kaggle.com/docs/competitions)
4. **Check feasibility:** Select an available adapter. Estimate data size, memory, model/API spend, compute time, and deadline margin. Stop early if the run cannot reasonably fit its caps.
5. **Acquire data:** Download only authorized files. Validate archives and checksums. Avoid path traversal and decompression bombs. Keep competition data private and namespaced.
6. **Plan validation:** Choose the competition metric and an appropriate split: stratified classification, random IID, group-aware, temporal, or another justified scheme. Fit preprocessing only on each training fold. Freeze the plan before comparing candidates.
7. **Build a baseline:** Execute a trusted pipeline and confirm it beats or matches a meaningful reference when applicable. Save configuration, metric, seeds, and artifacts.
8. **Run bounded experiments:** Compare at most five candidate configurations initially. Prioritize local validation, not repeated public-leaderboard probing.
9. **Choose and verify:** Select by the frozen local criterion; audit leakage, schema, runtime, reproducibility, and rule compliance. Preserve the best verified version.
10. **Package correctly:** Generate the exact file, notebook, executable agent, or writeup bundle the competition requires.
11. **Gate submission:** Re-check authorization, current rules/deadline, budget, quota, artifact hash, and submission type immediately before the external action.
12. **Submit or hand off:** Send one authorized supported entry, or produce a precise ready-to-submit handoff.
13. **Reconcile:** Record the returned submission reference, poll permitted status endpoints with backoff, and show pending/scored/rejected accurately. Preserve failures and the exact submitted artifact.

Kaggle warns that public and private leaderboard scores differ and that chasing the public leaderboard can overfit it. Kaggle's hidden test labels are not available to this lab and must not be confused with a locally reserved holdout. [9](https://www.kaggle.com/docs/competitions)

### Submission routes and evidence

The official CLI documents both ordinary file submission and submission of a specific kernel version using `-k` and `-v`. Kernel push uploads notebook/code and can run it on Kaggle. Support must still be checked against the installed version, account permissions, and the target competition. [27](https://github.com/Kaggle/kaggle-cli/blob/main/docs/competitions.md) [20](https://github.com/Kaggle/kaggle-cli/blob/main/docs/kernels.md)

The trusted adapter, not the model, constructs argument arrays for these command shapes:

```text
Classic:
  kaggle competitions submit COMPETITION
    -f VERIFIED_FILE -m AUDIT_MESSAGE

Notebook/code:
  kaggle kernels push -p REVIEWED_NOTEBOOK_FOLDER
  [wait for the exact version to finish successfully]
  kaggle competitions submit COMPETITION
    -f REQUIRED_OUTPUT -k OWNER/NOTEBOOK -v VERSION
    -m AUDIT_MESSAGE
```

These are documented command patterns, not commands run for this blueprint. Do not blindly use CSV submission for a code-only competition. Respect per-competition internet, model, data, and runtime constraints; do not assume internet must always be on or always off. If the supported submission route fails because of permission or platform restrictions, do not bypass it: prepare a handoff with the notebook URL, exact version, output, error, and next permitted action.

**Submission success levels:**

1. Artifact prepared.
2. Notebook execution completed, if required.
3. Platform acknowledged the competition submission and returned its reference.
4. Submission scored, rejected, or still pending.

Only level 3 means submitted; it does not mean scored successfully or eligible to win. The CLI exposes submission references and status queries. [27](https://github.com/Kaggle/kaggle-cli/blob/main/docs/competitions.md)

### Submission validation

For file-based prediction adapters, verify the required columns and their order, identifier alignment, row count where applicable, data types, finite values, permitted value ranges, and any competition-specific structural rules. Not every competition is CSV-based, not every task has one row per test example, and probabilities must not be inferred merely from the presence of numeric output.

Re-run the final trusted pipeline from a clean run directory. Confirm that the artifact being uploaded has the same hash as the reviewed artifact. Pin the exact notebook version for code submissions. Do not invent a leaderboard score or a successful test result.

### Prevent duplicate and excessive submissions

- Default internal cap: one submission attempt per run and one across this project per rolling 24 hours, subject to the stricter competition/team limit.
- Track submissions across all agents in the project. This does not control a teammate acting elsewhere; Kaggle remains the final authority.
- Allocate a durable submission intent before network I/O, keyed by account/team, competition, artifact hash or notebook version, and authorization scope.
- Use a unique run marker in the submission description where permitted.
- If an upload times out after a possible acceptance, mark **ACK_UNKNOWN**. Reconcile platform history before considering any retry. If it remains ambiguous, stop for review instead of submitting again.
- Poll with exponential backoff. Respect rate limits and avoid busy loops. Kaggle documents dynamic rate limiting. [12](https://www.kaggle.com/docs/api)

The local intent ledger prevents the lab from blindly repeating actions; it cannot provide an atomic exactly-once guarantee across an external API and a suddenly terminated Colab VM.

## 9. Autonomy, authority, and code execution

### Chosen policy

**Autonomous execution inside a narrow envelope; human-controlled expansion of that envelope.**

| Action | Default authority |
| --- | --- |
| Search approved sources, analyze evidence, draft answers | Autonomous within the run budget |
| Build an agent from reviewed templates | Autonomous within existing permissions |
| Fit and compare trusted competition pipelines | Autonomous within compute/data rules |
| Propose instruction, retrieval, or parameter changes | Autonomous |
| Evaluate a candidate on permitted development/validation tasks | Autonomous within experiment caps |
| Submit one eligible supported entry | Only if live mode is enabled and the run explicitly requests submission |
| Promote a candidate as the new default | Owner approval after a comparison report |
| Accept legal agreements, verify identity, spend above caps, publish unrelated work | Owner action or separate scoped authorization |
| Change the safety broker, grading logic, permission policy, or secrets | Never editable by the optimizing agents |
| Execute arbitrary generated Python in the privileged Colab process | Disabled |

“Submit this competition entry” is scoped authority for that entry under the standing limits; it is not permission to enter other competitions, change a team, accept new terms, or keep submitting indefinitely.

### The important sandbox distinction

A Python subprocess, virtual environment, directory restriction, or reviewer prompt is **not a strong security sandbox** against hostile Python running as the same Colab user. Code in that trust boundary may be able to access mounted Drive files or authorized secrets. Colab itself warns that mounting Drive lets notebook code access Drive files. [1](https://research.google.com/colaboratory/faq.html)

Therefore the first version automatically executes **reviewed code templates with validated bounded parameters**, not model-authored arbitrary programs. The model cannot supply executable expressions, arbitrary imports, shell commands, install commands, or unrestricted file paths through its configuration.

Custom code is stored as a proposal for review. For later autonomous custom-code experiments, use a genuinely separate execution environment without the owner's Drive mount, control-plane credentials, or hidden exams. Kaggle Notebooks can provide a separate competition execution route when permitted, but jobs must receive no privileged secrets and follow the competition's network/data constraints. Do not describe either route as universally secure without testing its actual isolation.

In the prototype, evaluator data is logically hidden from agent-facing tools. That is not tamper-proof isolation from a person or process with full runtime access. Strong hidden-exam protection requires an evaluator outside the optimizer's execution boundary.

## 10. Self-improvement without self-deception

There are two separate loops:

- **Competition-model improvement:** Fit task-specific ML model parameters and compare pipelines on leakage-safe validation data.
- **Agent-system improvement:** Improve instructions, retrieval, delegation, tool selection, and bounded configuration. The underlying foundation LLM is not being fine-tuned in version 1.

### The boss-worker improvement loop

```text
Observe a failure or measurable inefficiency
    -> Diagnose using permitted practice data
    -> Propose ONE versioned change
    -> Run a bounded candidate experiment
    -> Compare with the preserved baseline
    -> Run fixed verification and regression gates
    -> Nominate a finalist for sealed evaluation
    -> Produce a promotion/rejection report
    -> Human approval, or keep the baseline
    -> Monitor future work and roll back if necessary
```

A failed candidate returns to diagnosis without overwriting the working version. In-flight runs keep their pinned version even if a newer version is promoted.

### Evaluation separation

| Set | Available to optimizer? | Purpose |
| --- | --- | --- |
| Practice/development | Yes | Diagnose and fix known weaknesses |
| Validation | Scores and permitted diagnostics | Choose candidate configurations |
| Sealed holdout | No items, labels, or item-level feedback | Evaluate a nominated finalist |
| Regression | Fixed service-managed suite | Preserve already-supported behavior |
| Production monitoring | Sanitized results and incidents | Detect failure to transfer to real tasks |

Use a small starter suite of 100 cases: 30 practice, 20 validation, 20 sealed, and 30 regression cases. Include source grounding, contradictions, retrieval, policy enforcement, schema checks, mocked submission failures, and recovery. This is a **starter smoke suite**, not proof of broad research competence or statistical safety. Grow and audit it before trusting automatic promotions.

Limit sealed tests to one finalist per improvement cycle. Return aggregate gate results, not answer keys. Record every exposure. After three unsuccessful sealed evaluations against a suite, freeze further optimization against that suite until a separately prepared replacement is available. Do not let the optimizer generate or edit its own hidden exams.

A shared-model reviewer provides useful criticism, not objective ground truth. Prefer exact metric implementations, executable tests, source checks, and a separately controlled evaluator.

### Promotion decision

Hard gates must pass first: no critical policy failure, valid artifacts, reproducibility checks, and no defined critical regressions. Then compare quality, cost, and latency on identical tasks and comparable resource settings.

Illustrative release targets:

- At least 10% lower measured cost or latency while meeting a predeclared quality non-inferiority criterion; **or**
- A meaningful quality gain without exceeding the approved cost/latency envelope.

Set the non-inferiority margin before evaluating candidates. Report paired comparisons, task-level uncertainty, repeatability, and failed cases. Use repeated runs or multiple seeds where appropriate. If the data is too small to support the claimed improvement, label it inconclusive and retain the baseline. Do not treat “no observed failure” as proof that no degradation exists.

The owner approves an immutable candidate version. Promotion updates an active-version pointer for future runs. Rollback restores the prior pointer and records the incident. Neither promotion nor rollback can undo external actions already taken.

## 11. Knowledge and evidence model

Keep four forms of memory distinct:

- **Working:** Current objective, task graph, evidence in progress, blockers, and next actions.
- **Long-term:** Verified claims and supporting sources, scoped by project/domain/competition.
- **Procedural:** Reviewed workflows, failure patterns, tool instructions, and reusable lessons.
- **Experimental:** Every candidate change, environment, metric, budget, error, and decision.

### Claim record

Each important stored claim has: claim text; project scope; supporting and contradicting evidence IDs; source URL/document ID; source publication date when known; retrieval time; source snapshot/content hash; verification state; qualitative confidence with rationale; reviewer/version; and expiry/recheck rule where relevant.

Verification states are **unverified**, **supported**, **disputed**, **outdated**, or **rejected**. A model's confident wording does not establish support. Store the evidence passage separately from the claim so the relationship can be inspected.

Search only authorized namespaces. Competition data, private artifacts, and restricted findings must not be silently reused in another competition or included in a general training corpus. Procedural lessons can be reused only after removing restricted details and checking applicable rules.

## 12. Data model, versions, and recovery

### Minimum SQLite entities

| Entity | Essential content |
| --- | --- |
| agents / agent_versions | Name, declarative spec, parent version, hashes, active pointer |
| runs | Goal, agent/policy/model versions, state, outcome, budget, timestamps |
| tasks | Dependencies, tool/role, state, attempts, lease, inputs/outputs |
| events | Ordered state transitions, tool metadata, errors, sanitized decisions |
| sources / evidence | Provenance, retrieval time, snapshots/hashes, evidence passages |
| claims / claim_evidence | Claim state, support/contradiction links, review metadata |
| memory_entries | Scope, type, content references, permissions, expiry |
| experiments / evaluations | Candidate/baseline IDs, dataset/test fingerprints, metrics, uncertainty |
| competition_profiles | Submission type, metric, rules snapshot, access/limit checks |
| approvals / submission_intents | Scope, artifact/version hashes, permission decision, external references |
| submissions | Intent ID, acknowledged reference, exact artifact/version, scoring state |
| artifacts / budget_ledger | Paths, hashes, classification, resource reservation and actual use |

### States

A run moves through `DRAFT -> PREFLIGHT -> QUEUED -> RUNNING -> VERIFYING -> COMPLETED`, with explicit alternatives: `WAITING_FOR_USER`, `PAUSED`, `BLOCKED`, `FAILED`, and `CANCELLED`.

Track external submission state separately: `NOT_REQUESTED`, `READY_TO_SUBMIT`, `AWAITING_AUTH`, `UPLOAD_IN_PROGRESS`, `ACK_UNKNOWN`, `SUBMITTED_PENDING_SCORE`, `SCORED`, or `REJECTED`.

A completed report can have the outcome **handoff required**; it must not be labeled **competition submitted**. A rejected submission preserves its rejection reason rather than being treated as a successful scored entry.

### Storage strategy

Work on the database and datasets under the local Colab filesystem. Do not use an actively written SQLite/WAL database on mounted Drive as if it were a reliable multi-user database.

At a checkpoint:

1. Commit local state.
2. Use SQLite's backup mechanism to create a consistent local snapshot.
3. Bundle the snapshot, manifest, configuration, and new immutable artifacts.
4. Copy to a new Drive generation; verify checksums before publishing its completed manifest.
5. Retain previous valid generations. Restore the newest fully verified generation, not simply the newest timestamp.

Drive operations can be slow or fail, and the Colab FAQ recommends avoiding excessive small-file operations. A partial upload must not replace the last valid backup. [1](https://research.google.com/colaboratory/faq.html)

Only one active runtime may own a project in version 1. A Drive file is not a robust distributed lock. Multi-user or multi-runtime operation requires a transactional shared store and a different deployment design.

On restart, recover expired task leases, validate artifacts, and distinguish retriable local work from uncertain external side effects. Never automatically replay a submission, publication, or other non-idempotent action.

## 13. Tool broker, budgets, and security

### Brokered operations

Expose typed, narrow operations such as `search_sources`, `fetch_allowed_source`, `store_evidence`, `run_trusted_pipeline`, `score_predictions`, `validate_submission`, `push_reviewed_notebook`, `submit_verified_entry`, and `query_submission_status`.

The broker checks effective permissions, identifiers, input schemas, path boundaries, cancellation state, and budget before execution. It constructs CLI argument lists without `shell=True`. No generic terminal tool, arbitrary SQL tool, unrestricted filesystem reader, or unrestricted Python executor is exposed to model roles.

### Default budgets

| Limit | Research question | Kaggle agent | Improvement cycle |
| --- | --- | --- | --- |
| Wall-clock target/cap | 10 minutes | 60 minutes | 20 minutes |
| Metered API spending cap | USD 2 | USD 5 | USD 2 |
| Experiment candidates | Not applicable | At most 5 | At most 3 |
| Recovery retries per safe task | 2 | 2 | 2 |
| Competition submissions | 0 | At most 1 if authorized | 0 |

Additional defaults: USD 10 session-wide API cap; one active expensive training job; at most two parallel approved network operations; 10 GB new run-data cap; at most 60 minutes of requested GPU work per session. Detect actual resource availability and shorten/refuse work that does not fit. These are caps, not promises that the work will finish or that a free GPU is available.

Reserve estimated API spend before concurrent calls and reconcile actual usage afterward. Apply bounded output-token settings. Include search/provider charges where known. Unknown pricing or usage should not be silently treated as free. Provider billing may be delayed, so a client-side meter is not an absolute provider-side spending guarantee; use provider account limits when available.

### Security requirements

- Keep credentials in Colab Secrets and the trusted integration layer; never in prompts, evidence, exported notebooks, or routine logs. Kaggle supports API tokens, including `KAGGLE_API_TOKEN`; select the supported method during preflight. [3](https://github.com/Kaggle/kaggle-cli/blob/main/docs/README.md)
- Never claim that scrubbing an environment alone isolates arbitrary code from the owner account.
- Treat retrieved pages, uploaded documents, dataset cells, and competition notebooks as untrusted content. They cannot grant tools or rewrite system policy.
- Escape rendered HTML. Do not execute scripts from source documents or load untrusted pickles.
- Validate URLs against an allowlist and block private/metadata IP destinations, redirects to prohibited destinations, and oversized responses.
- Validate paths, archives, dependencies, and output formats; use reviewed dependency locks.
- Default to no raw competition rows sent to a hosted LLM. Even schemas or aggregates may be restricted: external transfer requires an applicable permission/rules check. If prohibited, keep those computations local and use only permitted summaries or stop the hosted-model stage.
- Make notebook/artifact visibility private by default. Publication requires the correct explicit authority.
- Record sanitized audit events and content hashes. Application-level append-only conventions and hash chains are useful for diagnosis, but are not tamper-proof against the owner or a compromised runtime.
- Never circumvent eligibility, submission quotas, access controls, licenses, identity checks, or platform anti-abuse limits.

## 14. Scorecard and output contracts

### Research deliverable

A research answer contains: a direct answer; supported claims and citations; material counterevidence; uncertainty and limitations; a concise method summary; and links to saved evidence where allowed.

Measure claim correctness using labeled audits, citation support, source quality, coverage against a task rubric, reproducibility, tool success, latency, cost, and policy compliance. Separate deterministic results, human judgments, and LLM-judge scores in the report.

### Kaggle deliverable

A competition report contains: the competition snapshot; applicable constraints; validation method and metric; compared configurations; seeds and dependency versions; selected pipeline; data and artifact hashes; final verification results; submission authority and reference if acknowledged; scoring status; remaining limitations; and exact reproduction steps.

Do not compare unrelated competition metrics as if they were the same number. Report local validation and public leaderboard scores separately. Do not imply that the final private score is known before Kaggle reveals it.

### Improvement deliverable

A promotion report contains: baseline/candidate IDs; exact change; fixed evaluation method; item/task counts; quality/cost/latency differences and uncertainty; safety/regression results; sealed-test exposure count; failed cases; recommendation; approval; and rollback target.

**Safety, required rule compliance, and minimum artifact validity are gates. They cannot be bought off by a faster average score.**

## 15. Project structure

```text
agentlab/
  AgentLab_Colab.ipynb
  requirements.lock
  config/
    defaults.yaml
    policy.yaml
  agentlab/
    ui.py
    schemas.py
    builder.py
    orchestrator.py
    queue.py
    broker.py
    budgets.py
    storage.py
    memory.py
    evaluation.py
    versions.py
    providers/
      gemini.py
      search.py
    workflows/
      research.py
      kaggle_classic.py
      kaggle_notebook.py
    adapters/
      kaggle_cli.py
    trusted_templates/
      tabular_pipeline.py
      notebook_template.ipynb
  tests/
    unit/
    workflow/
    security/
    recovery/
    integration_opt_in/
```

Mutable project state is stored separately from application code. Sealed evaluation items are not shipped in an agent-readable workspace or generic retrieval index. A future separately controlled evaluator has its own deployment and access boundary.

## 16. Implementation sequence

### Stage A — Reliable question answering

Build the UI skeleton, project store, provider adapter, permissions, and one evidence-based research workflow. Add the reviewer and a deterministic citation/provenance check. Demonstrate an honest insufficient-evidence answer as well as a successful answer.

**Exit:** The Ask tab produces traceable results; secrets are not logged; budgets and Stop work.

### Stage B — Declarative Agent Builder

Add schema validation, trusted templates, saved versions, task graphs, retries, and restore/resume. Support creating a research agent and a submission-disabled competition plan from natural language.

**Exit:** A prompt creates a reusable bounded agent without expanding permissions or executing generated code.

### Stage C — First live Kaggle workflow

Add classic tabular data inspection, leakage-safe validation, a few reviewed pipelines, bounded tuning, format checks, and the typed Kaggle adapter. Run recorded/mock tests before one explicitly authorized live integration test on an eligible supported competition.

**Exit:** Demonstrate a valid artifact and a real acknowledged submission reference when live mode is enabled; otherwise an accurately labeled prepare-only result. A leaderboard medal or strong score is not the exit criterion.

### Stage D — Improvement Lab

Add baseline/candidate experiments, fixed practice/validation/regression suites, version diffs, promotion approval, and rollback. Keep sealed evaluation logically separate in the prototype; use a separately controlled evaluator before claiming strong exam isolation or enabling later unattended promotion.

**Exit:** An improvement can be accepted or rejected based on a reproducible report, and a failed candidate cannot overwrite the baseline.

### Stage E — Coverage expansion

Implement code/notebook routing and then domain adapters one at a time. Each must pass its own packaging, rule, recovery, and live-integration checks. Add isolated custom-code execution only with an adequately tested separate runner.

**Exit:** Publish a tested capability matrix. “Any URL can be inspected” remains distinct from “every competition can be entered autonomously.”

## 17. Acceptance tests before calling it autonomous

| Test | Required behavior |
| --- | --- |
| Natural-language builder | Produces a schema-valid agent card, workflow, evaluation plan, and effective permissions |
| Permission escalation attempt | Prompt or retrieved content cannot enable a forbidden tool or increase caps |
| Grounded research | Supports material external factual claims with inspectable sources; flags missing evidence |
| Missing credentials | Clear setup blocker; no invented connection or fake live result |
| Prepare-only mode | Produces artifacts without any submission call |
| Competition-type routing | Code-only, simulation, and writeup tasks are not treated as generic CSV uploads |
| Invalid artifact | Wrong schema, duplicate/misaligned IDs, invalid values, or violated structural rules block submission |
| Leakage fixture | Detects planted leakage and refuses the invalid comparison |
| Ambiguous upload acknowledgment | Enters ACK_UNKNOWN and reconciles; does not blindly resubmit |
| Quota/deadline/eligibility blocker | Stops or hands off honestly; does not use another account or bypass a limit |
| Concurrent budget calls | Reservations prevent the broker knowingly allocating more than its cap |
| Runtime interruption | Restores a valid checkpoint, checks remote state, and does not replay side effects |
| Candidate failure | Keeps the previous active version and records the rejection |
| Sealed-test protection | Agent-facing tools cannot retrieve exam items/labels; prototype isolation limitations are documented |
| Stop control | Prevents new brokered actions and stops local work at the next enforceable cancellation boundary |
| Live submission evidence | Shows an actual platform reference and separate pending/scored/rejected status, or states that no submission occurred |

Run external-action tests with mocks by default. Real submissions and publication tests require an explicit opt-in flag, a named authorized account/competition, and known remaining limits.

## 18. Final scope statement

The first working release is **a Colab-native Agent Builder plus Ask a Question interface, with evidence storage, bounded research, trusted tabular experiments, honest Kaggle routing, and human-approved workflow improvement**.

It is not an unrestricted self-rewriting AI, a production security boundary, a guaranteed competition winner, or an always-on research company. Its strength is that it can act independently on the small steps while preserving the evidence, costs, limits, and tests needed to judge whether it actually did useful work.

---

# Sources and verification notes

Official documentation checked for this design on 21 October 2026. Interfaces and limits may change; the future implementation must perform runtime capability checks. No account-specific permission, availability, eligibility, submission, or benchmark result was tested here.

- **Colab runtime, UI, Drive, and resource constraints:** [1](https://research.google.com/colaboratory/faq.html)
- **Kaggle competition types, entry requirements, and leaderboard cautions:** [9](https://www.kaggle.com/docs/competitions)
- **Kaggle API authentication and dynamic rate limits:** [12](https://www.kaggle.com/docs/api)
- **Official Kaggle CLI setup and token authentication:** [3](https://github.com/Kaggle/kaggle-cli/blob/main/docs/README.md)
- **Official Kaggle CLI file/kernel submission and status commands:** [27](https://github.com/Kaggle/kaggle-cli/blob/main/docs/competitions.md)
- **Official Kaggle CLI notebook push/run commands:** [20](https://github.com/Kaggle/kaggle-cli/blob/main/docs/kernels.md)
- **Notebook-native controls and tabs:** [6](https://ipywidgets.readthedocs.io/en/stable/)
- **Official Gemini Colab example using Secrets:** [19](https://colab.research.google.com/github/google-gemini/cookbook/blob/main/quickstarts/Get_started.ipynb)
