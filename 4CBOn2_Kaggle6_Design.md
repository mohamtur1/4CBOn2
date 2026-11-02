# 4CBOn2 — Kaggle6
## Unified agent blueprint · Design revision 2

**Design only · 21 October 2026**

**Future notebook:** `4CBOn2_Kaggle6.ipynb` at the root of [mohamtur1/4CBOn2](https://github.com/mohamtur1/4CBOn2).

**Status:** No notebook has been built, no repository changes have been made, and no integration or competition submission has been tested. This document supersedes the earlier blueprint wherever the two conflict.

**Architecture decision:** Fuse the requested agent functionality into one shared Agent Execution Core. Keep specialist workflows, permission enforcement, code execution, and evaluation as separate modules. Do not create a second standalone agent application, a separate model for every capability, or an unrestricted super-agent.

**AI decision:** Use the Colab built-in AI interface, `from google.colab import ai`. Do not require a separate Gemini API key or silently substitute another model provider.

---

# Part I — The decision in plain language

## 1. Explain it as if I were two

One little robot has different toolboxes.

One toolbox helps it find answers. One helps it tidy files. One helps it try Kaggle puzzles. One helps it practice getting better.

It does not need a new brain for every toolbox.

It makes a little plan, does one step, checks the step, and remembers what happened.

A special lock stops it from sending things, deleting important things, or changing its own rules without permission.

The robot thinks in its Colab room. It can pack a checked set of instructions for its Kaggle workshop. Moving the instruction book does not move the Colab room with it.

**One helper system. Different jobs. Checked tools. Important keys stay with you.**

## 2. Explain it as an adult

The capabilities you listed are the operating machinery of the research organization, not a separate organization. Planning, memory, tools, file handling, retries, and monitoring should be reused across every workflow.

Use one application with four job profiles:

1. **Research:** Gather evidence, compare explanations, verify claims, and answer questions.
2. **General Task:** Analyze documents or data, organize permitted files, create reports, and run other supported workflows.
3. **Kaggle:** Inspect a competition, prepare and evaluate a pipeline, package the required artifact, and submit when authorized and supported.
4. **Improvement Lab:** Propose, test, compare, and review improvements to the other profiles.

This is **functional fusion, not permission fusion**. A research agent and a submission agent can use the same core without receiving the same authority. The optimizing agent must not control its permission broker, grading rules, hidden tests, or promotion approval.

## 3. What is shared and what stays separate

| Shared across all profiles | Separate by design |
| --- | --- |
| Goal interpretation, planning, task graph, and progress | Workflow-specific instructions and success criteria |
| Tool registry and common input/output contracts | Per-agent and per-run tool permissions |
| Memory and artifact storage services | Private project/competition memory namespaces |
| Verification and recovery framework | Fixed evaluators, test data, and grading rules |
| Budgets, checkpoints, and audit events | Sensitive external actions and their authorization |
| One notebook interface and application codebase | Colab AI controller and Kaggle execution roles |

The initial model roles remain Supervisor, Researcher, Analyst/Experiment Planner, and Reviewer. Knowledge and Performance remain mainly software services. These roles can share an available model while using separate contexts. A separate reviewer context is useful but is not proof of independent judgment.

---

# Part II — How the functionality fits

## 4. The Agent Execution Core

Every supported job uses the same control loop:

```text
Receive goal
    -> Inspect available capabilities and permissions
    -> Create a bounded plan and success criteria
    -> Select the next permitted action
    -> Execute through the tool broker
    -> Observe the real result
    -> Verify against the success criteria
    -> Save evidence, artifacts, and checkpoint
    -> Continue, repair, request authority, or stop
    -> Deliver the verified outcome and limitations
```

Do not ask the model to produce an entire uncontrolled sequence of commands and assume success. Run and verify actions incrementally. Some fixed, reviewed pipeline stages can execute as a bounded batch.

### Logical modules

- **Runtime and Capability Manager:** Determines the environment and actually available services.
- **Agent Builder:** Compiles a goal into a versioned declarative specification.
- **Planner/Supervisor:** Creates the task graph and chooses permitted next steps.
- **Tool Registry:** Describes implemented tool adapters and their input/output contracts.
- **Permission and Budget Broker:** Enforces authorization, boundaries, limits, and cancellation.
- **Execution Runners:** Run trusted local workflows or approved separate jobs.
- **Verifier:** Checks artifacts, evidence, tests, and external acknowledgments.
- **Memory and Artifact Store:** Preserves scoped context, provenance, procedures, and experiment history.
- **Recovery Controller:** Handles safe retries, interrupted jobs, and uncertain external actions.
- **Monitor and Reporter:** Displays progress, resource use, results, and blockers.

These are modules, not ten independent model conversations. The first distributable remains one notebook; its internal classes can implement the modules without requiring ten notebooks or a hosted backend.

## 5. Complete capability mapping

**Initial** means an implementation target, not an existing tested feature. **Conditional** means a separately enabled adapter, authorization, suitable runtime, or later implementation is required.

| Requested capability | Where it belongs | Planned availability and boundary |
| --- | --- | --- |
| Task Automation | Core workflow engine and queue | Initial: approved repeatable workflows; no unrestricted desktop automation |
| Web Browsing | Retrieval tools and optional browser worker | Initial: approved HTTP/source retrieval and public-source search adapters. Interactive website actions are conditional |
| Planning and Reasoning | Supervisor plus Colab AI adapter | Initial in Colab controller mode; Kaggle runner follows a prepared bounded plan |
| Tool Integration | Typed registry and adapters | Initial framework; each actual service must be implemented, configured, and authorized |
| File Management | Workspace file service | Initial: create/read/edit/organize within an approved job workspace; destructive or broader access is gated |
| Code Execution | Code proposal service and trusted runners | Initial: reviewed templates and validated parameters. Custom generated code needs review or a tested separate runner |
| Data Analysis | Analyst role and trusted data pipelines | Initial: supported tables, summaries, metrics, and charts; other formats need adapters |
| Multi-Step Execution | Persisted task graph and checkpoints | Initial: dependencies, bounded loops, resumability, and explicit success states |
| Memory and Context | Scoped memory and artifact store | Initial: task, knowledge, procedural, and experiment memory with explicit durable saving |
| Decision-Making | Planner plus deterministic policy checks | Initial: compare permitted alternatives and record a concise rationale; no model-controlled permission changes |
| Self-Correction | Recovery controller and revision loop | Initial: at most two safe retries and one bounded replan per failed task, inside the original limits |
| Monitoring | Event log, status timeline, and result checks | Initial: actual tool outcomes, checkpoints, resource use, and failure states |
| Communication | Report generator and optional outbound adapters | Initial: notebook reports and downloadable files. Sending messages or publishing is conditional |
| Human Oversight | Trusted approval and policy broker | Initial: scoped authority for sensitive actions; fresh approval when the request exceeds it |
| Continuous Operation | Queue and session scheduler | Initial: work only while the required runtime/service is available. Persistent unattended scheduling is outside the notebook-only first release |

### Web browsing is not one unrestricted permission

Split web capability into four operations:

1. Retrieve an approved public page or document.
2. Search through an implemented, permitted source/search adapter.
3. Interact with a website using a reviewed browser recipe.
4. Perform an external action, such as submitting a form or publishing content.

The first two are read-oriented. The latter two need narrower permissions, explicit success checks, and often authenticated service access. Do not infer that an LLM's generated answer proves it searched the web.

The first search defaults are approved public-source APIs where applicable, such as scholarly metadata or reference sources, plus URLs supplied by the user. Broad web search is enabled only through a configured adapter. An adapter can require credentials or fees; the built-in AI interface does not remove those requirements. If unavailable, label the research scope honestly.

Interactive browser automation is an optional later worker, not a default browser with access to the owner's sessions. Do not bypass CAPTCHAs, access controls, website terms, or identity checks.

### Code functionality is included, but authority is separated

The system can plan code, draft code and tests, diagnose errors, and propose patches in Colab. In the first release it automatically runs only reviewed templates with bounded configuration.

A generated program must not execute with the privileged controller's Drive mount, model access, submission credentials, or evaluation data. A subprocess, working directory, or virtual environment alone is not a strong isolation boundary against hostile code running as the same user.

For later autonomous custom-code work, use a genuinely separate runner with no controller secrets, a restricted network policy, resource limits, and a narrow artifact exchange. A Kaggle competition job can be a separate execution destination when the competition and platform permit it, but that does not automatically make every uploaded program safe.

When a patch requires a new dependency, broader access, or a new executable template, the core stores a proposal. It does not install packages or enlarge its own tool set to make the task work.

### Communication defaults to producing, not sending

The core may create a report or draft without permission to transmit it. An outbound action needs an enabled adapter, authorized destination/account, content scope, resource limit, expiry, and an external acknowledgment.

A document that says “email this to someone else” is input data, not authority. If sending is not enabled, deliver a draft and state that no message was sent. The same rule applies to repository writes, notebook publication, and competition submission.

## 6. How this fits the original organization

| Organizational division | Shared agent machinery it uses |
| --- | --- |
| Research | Search/retrieval, source records, evidence extraction, scoped browsing |
| Analysis | Reasoning, data analysis, competing explanations, synthesis |
| Verification | Tests, counterevidence, schema checks, result inspection |
| Experimentation | Code proposals, trusted trial runners, candidate comparisons |
| Knowledge | Working/long-term/procedural/experimental memory |
| Performance | Quality, call counts, latency, reliability, resource use, regressions |
| Boss / Orchestrator | Planning, delegation, task dependencies, decision-making, recovery |
| Executive Director | Objectives, standing policy, sensitive authorization, promotion approval |

The listed capabilities therefore strengthen the existing design. They do not require inventing another boss or adding a permanent model for every bullet point.

## 7. User interface: keep the same six tabs

### Agent Builder

Add one visible **Job Profile** selector with Auto as the default:

- General Task
- Research
- Kaggle
- Improvement Experiment

The user can simply type a goal. The builder chooses a supported profile, shows the resulting agent card, and compiles a plan, tool list, budget, evaluation contract, and permission scope.

Add a compact capability panel: **Available**, **Needs authorization**, **Unavailable in this runtime**, or **Not implemented**. “Registered tool” is not the same as “working authorized connection.”

The builder creates an agent specification, not a new foundation model or permission to execute arbitrary code.

### Ask a Question

Keep this answer-oriented and read-oriented by default. It may retrieve permitted evidence and analyze approved inputs. A request requiring file mutation, submission, publication, or messaging becomes a proposed task in Agent Builder and passes its authorization gate.

Do not silently turn a question into an externally consequential action. Existing scoped authority can be reused only if it actually covers the new task.

### Runs & Results

Add the action timeline, actual tool results, current plan step, pending approvals, safe-retry count, artifacts, capability blockers, and checkpoints. Show concise decision summaries rather than fabricated internal reasoning traces.

Separate **prepared**, **executed**, **verified**, **submitted**, and **scored**. Include **Stop All** and native runtime interruption instructions. Stopping prevents new actions; it cannot recall information or undo an external action that has already been accepted.

### Knowledge

Keep four memory types and project/competition scopes. Store claims separately from evidence, with sources, retrieval dates, verification states, contradictions, and content hashes. Prevent private data from leaking across agent profiles or competitions.

### Improvement Lab

Reuse the same core, but isolate candidate changes, evaluation inputs, grading rules, and promotion authority. Display the exact change, baseline comparison, uncertainty, failures, approval, and rollback target.

### Settings & Permissions

Add capability probes, connector health, workspace boundaries, model availability, call/time limits, outbound action policy, job export/import, backup/restore, and runtime mode. Advanced browser and custom-code execution remain disabled unless their actual prerequisites are satisfied.

---

# Part III — Runtime, safety, and build contract

## 8. Colab built-in AI: what the application must supply

Google demonstrates `ai.list_models()` and `ai.generate_text(...)` in its official Colab AI notebook. The examples expose text generation and streaming, not a complete agent framework. [3](https://colab.research.google.com/github/googlecolab/colabtools/blob/main/notebooks/Getting_started_with_google_colab_ai.ipynb)

Therefore our application supplies the agent loop, memory, tool execution, retries, authorization, and verification around that interface.

A conceptual action cycle is:

```text
Build bounded prompt from permitted context
    -> Call Colab built-in text generation
    -> Parse the proposed action using a strict schema
    -> Reject unknown tools, unsafe paths, and invalid inputs
    -> Check current policy, budget, and cancellation
    -> Execute the allowed action through its adapter
    -> Validate and record the real result
    -> Return a sanitized observation to the planner
```

Request structured action text, but do not assume the interface guarantees native tool calling or valid JSON. Use strict parsing, at most two format-repair attempts inside the same call budget, and fail clearly when parsing remains invalid. Never run `eval` on a model response or treat a code block as a command to execute.

Select from the models actually returned for the current account, using a reviewed preference list and a capability smoke test. Record the selected model ID. Do not hard-code an example model as universally available or invent unsupported generation parameters.

No separate Gemini API-key form is required. Respect the built-in service's actual availability, authorization, and quota. Do not extract and reuse Colab's internal model-proxy credentials in another environment.

## 9. The Colab-to-GitHub-to-Kaggle boundary

### Chosen resolution: one notebook, two explicit execution roles

The AI interface is Colab-specific. The current official implementation checks for a supported Colab runtime and uses Colab runtime/service configuration; copying an `.ipynb` file is not a supported way to transfer that access. See the [official Colab AI implementation](https://raw.githubusercontent.com/googlecolab/colabtools/main/google/colab/ai.py).

To preserve the user's selected built-in API without quietly adding another provider, use this boundary:

```text
COLAB CONTROLLER
  Live goal interpretation, Agent Builder, Ask a Question,
  research, candidate proposals, and reviewed job preparation
        |
        | notebook code/version saved with authorization
        v
GITHUB: mohamtur1/4CBOn2
  Canonical file: 4CBOn2_Kaggle6.ipynb
  Reviewed application code and non-sensitive documentation
        |
        | imported/opened as a Kaggle copy
        v
KAGGLE RUNNER
  Capability checks, prepared task graph, trusted pipelines,
  competition execution, validation, artifacts, status,
  and supported authorized submission/handoff
```

The future notebook detects its runtime, probes capabilities, and activates the appropriate role. A failed Colab AI import must not become a fake connected state or a silent switch to another provider.

### Honest behavior by environment

| Feature | Colab controller | Kaggle runner under this design |
| --- | --- | --- |
| Live Colab AI planning | Enabled after successful probe | Not assumed available |
| New natural-language Agent Builder request | Compiles a supported new job | Shows/imports prepared jobs; live AI creation disabled without a future explicitly supported backend |
| Ask a Question | Live supported answering/research | Saved evidence/report lookup; fresh generative answers are unavailable without such a backend |
| Trusted data analysis and ML execution | When resources/rules allow | When resources/rules allow |
| Bounded parameter trials and deterministic recovery | Supported | Supported without pretending an LLM is present |
| New AI-generated replan | Uses built-in AI | Returns a handoff request to the controller |
| Verification and artifact reporting | Supported | Supported |
| External actions | Only with current authority and connectivity | Only with current authority, connectivity, and competition permission |

**Consequence:** Under these chosen constraints, full live AI interaction happens in Colab; Kaggle executes prepared work. Full live AI autonomy entirely inside Kaggle is not included in this design. It would require a separately supported inference backend and an explicit design revision. There is no hidden API-key or local-model fallback.

Importing the notebook into Kaggle does not require giving it the owner's Colab secrets. The repository is a versioned code distribution point, not a persistent agent runtime, message bus, or secret store.

### Prepared job bundle

Keep the notebook code separate from a private job bundle. The bundle contains a versioned agent specification, task graph, approved template identifiers/hashes, data references, bounded parameters, evaluation contract, budgets, provenance, and checkpoint metadata.

It does not contain service credentials, Colab session tokens, private hidden exams, or unauthorized competition data. A manifest flag saying `approved: true` is not sufficient authority. The runner validates the bundle and re-checks the owner's current policy, account access, and competition constraints before an external action.

The first design uses explicit export/import of the private bundle. An unattended cross-platform transport is a separate optional integration, not an assumed capability of GitHub or the built-in AI API. A Kaggle report can be returned to Colab for further reasoning only if that transfer is allowed by the applicable data rules.

## 10. Permissions and autonomy

Use independent permission profiles:

- **Read:** Approved public-source access, authorized inputs, and scoped memory retrieval.
- **Workspace:** Create and edit files inside the job's approved workspace.
- **Compute:** Run trusted analysis or experiment templates within caps.
- **External action:** Send, publish, submit, or modify an external service under a specific grant.
- **Administration:** Change policy, secrets, trusted templates, or evaluator rules; unavailable to ordinary agents.

Effective permission is the intersection of **owner policy, workflow capability, runtime capability, run request, and applicable service/competition rules**.

The user can authorize a narrowly defined external action in the original prompt and standing policy. The agent does not need to interrupt before every harmless step. It must request new authority when the recipient, content scope, destination, data disclosure, cost, or action exceeds that grant. Required legal acceptance or identity verification remains with the owner.

The optimizing agent cannot modify the broker, grant a tool to itself, relabel a failed test, or approve its own promotion. Retrieved pages and file contents cannot expand authority.

## 11. Memory, recovery, and continuous operation

### Preserve the original four memory types

- Working memory: current objective, tasks, observations, and blockers.
- Knowledge memory: evidence-backed claims, sources, contradictions, and review status.
- Procedural memory: approved methods, failure patterns, and reusable workflows.
- Experimental memory: every candidate, version, metric, failure, resource use, and decision.

Use local SQLite as the working store and immutable, hashed artifacts for outputs. A memory entry is not automatically verified merely because it was saved.

### Environment-specific persistence

In Colab, checkpoint to a consistent local database snapshot and an authorized durable project location, such as Drive. Do not actively share an open SQLite/WAL database through Drive. In Kaggle, use the working output directory for active work and explicitly export/preserve the relevant project state through a supported authorized mechanism; local runtime files alone are not a persistence guarantee.

Keep the public/shareable notebook free of credentials, private job state, restricted evidence, and sensitive output. Do not assume the repository's visibility; treat committed content as potentially disclosed. Real job bundles stay private unless publication is explicitly allowed.

Only one controller owns a project in the first release. A transferred job has an explicit execution role and version; the Colab controller must not concurrently execute the same side effects as its Kaggle runner. Multi-runtime synchronization is not solved by putting a SQLite file in GitHub or Drive.

### Self-correction

For a safe failed action, classify the error, retry at most twice with backoff, then allow one bounded replan if Colab AI is available. All attempts share the original resource cap. Changes requiring extra permissions stop at a blocker.

A possibly accepted external action is different: record an uncertain acknowledgment state and reconcile it with the service. Never blindly retry a submission or message after an ambiguous timeout. Log the exact artifact hash, intent, authorization scope, and returned external reference when available.

### Continuous operation means supported operation, not permanence

Queue scheduled work and process due tasks only while the required runtime and services are actually available. After interruption, restore the checkpoint and check deadlines and authority before resuming. Missed side effects must not automatically be replayed.

Colab documents variable resources, runtime termination, idle timeouts, and maximum VM lifetimes. A notebook-only prototype cannot promise 24/7 unattended operation. No anti-idle scripts or multiple-account quota workarounds are part of the design. [1](https://research.google.com/colaboratory/faq.html)

An always-on scheduler and unattended model access require suitable persistent infrastructure and a supported inference integration. That is a later deployment change, not something the notebook will fake.

## 12. Budgets revised for the built-in API

Replace the earlier default dollar meter for a separate Gemini API with limits the application can actually observe: model-call count, prompt/response size, elapsed time, tool calls, trials, and stored data. Do not claim a measured token balance or monetary charge if the interface does not provide one.

| Profile | Model-call cap | Active-work time cap | Experiment cap |
| --- | --- | --- | --- |
| Research question | 8 | 10 minutes | None by default |
| General Task | 16 | 20 minutes | Task-specific, inside the cap |
| Kaggle preparation/execution | 24 in Colab; none assumed in Kaggle | 60 minutes | At most 5 reviewed pipeline configurations |
| Improvement cycle | 16 | 20 minutes | At most 3 candidates |

Additional initial limits: 100 model calls per project per rolling day; one expensive training job at a time; at most two approved parallel network operations; at most 10 GB new run artifacts/data. These are proposed caps, not service entitlements, measured throughput, or guaranteed completion times.

Retries and formatting repairs count against model-call caps. If built-in service quota is exhausted, pause with a clear reason rather than assuming unlimited free access. If an optional external tool has fees, keep it disabled until configured and authorized with an explicit budget; no silent paid fallback.

Remote scoring may remain pending after the local work budget ends. Preserve the reference and check later rather than repeatedly submitting or claiming a result.

## 13. Kaggle remains a specialist workflow

The shared engine handles the common steps; a competition adapter supplies the actual contract. Inspect rules, eligibility, timeline, metric, validation scheme, output format, internet restrictions, external-model/data restrictions, and resource limits before proceeding.

Classic prediction, notebook/code, simulation, and hackathon tasks are not interchangeable. Code competitions require notebook-based entries and can restrict internet, external data, and compute. [9](https://www.kaggle.com/docs/competitions)

The official CLI documents ordinary file submissions and specific notebook-version submissions; account and competition support still require validation. [13](https://github.com/Kaggle/kaggle-cli/blob/main/docs/competitions.md)

First live target: supported classic tabular competitions. Later adapters cover code/notebook, images, text, time-series, simulations, and writeups. Any URL can be inspected; not every competition can be entered automatically.

Default external-action limit: one authorized submission attempt per run and one across the project per rolling 24 hours, subject to stricter competition/team limits. Re-check immediately before submission. Keep the intent ledger private and preserve uncertain acknowledgments instead of retrying blindly.

A report must distinguish artifact prepared, notebook run completed, competition submission acknowledged, and scoring completed/rejected/pending. No fake leaderboard scores or submission references.

## 14. Improvement loop and independent evaluation

Use the shared core to propose and run experiments, but do not fuse optimizer and judge authority.

```text
Observe -> Diagnose -> Propose versioned candidate
    -> Practice/validation comparison
    -> Fixed verification and regression tests
    -> Separately controlled sealed evaluation
    -> Promotion report -> Owner approval or rejection
    -> Monitor -> Roll back if required
```

Keep practice, validation, sealed holdout, regression, and production monitoring distinct. Limit hidden-test feedback and record exposures. Do not let the optimizer generate its own hidden tests. A same-runtime prototype can provide logical access separation, but it is not security-grade isolation from arbitrary code or an owner with full runtime access.

No automatic promotion based on an agent's confidence or a tiny benchmark. Apply hard safety/artifact gates, paired baseline comparisons, uncertainty, repeatability, and predeclared acceptance criteria. Inconclusive evidence keeps the baseline.

The first release improves procedures, retrieval, prompts, delegation, and bounded configurations. It does not fine-tune the foundation model. Fitting a competition-specific ML model is a separate and permitted task when the competition allows it.

## 15. Delivery and implementation order

1. **Design freeze:** Confirm the unified core, runtime split, permissions, and acceptance tests in this revision. No implementation is authorized merely by writing this document.
2. **Notebook foundation:** Runtime probes, built-in AI adapter, schemas, broker, file boundaries, resource limits, and six-tab UI.
3. **Reliable core loop:** One research workflow and one general data/report workflow with real verification and recovery.
4. **Agent Builder:** Saved declarative agents, versioned task graphs, private job bundles, and restore/resume.
5. **Kaggle runner:** Open the canonical notebook in Kaggle, import a prepared job, execute supported trusted pipelines, validate artifacts, and test submission-disabled routing.
6. **Authorized integration tests:** Prove the real external workflows separately; mock external actions by default.
7. **Improvement Lab:** Candidate/baseline comparisons, sealed-evaluation boundary, promotion approval, and rollback.
8. **Conditional expansions:** Interactive browsing, outbound communication, more competition adapters, genuinely separate custom-code execution, and persistent scheduling only after their own prerequisites and tests.

The future repository deliverable remains exactly **`4CBOn2_Kaggle6.ipynb`**. Do not assume a branch name, an existing file, repository visibility, or write access before checking during implementation. Notebook outputs are cleared/redacted before a permitted commit. Credentials and private run bundles are never committed.

## 16. Acceptance tests for this revision

- A natural-language goal becomes a bounded supported job, not arbitrary executable code.
- All 15 requested capabilities have an explicit module and availability state.
- A missing or failed built-in AI connection is shown honestly; no provider is silently substituted.
- Colab mode supports live AI workflows after preflight; Kaggle mode does not claim live Colab AI access.
- Unsupported new AI requests in Kaggle return a controller handoff, not fabricated responses.
- An exported job cannot grant itself tool permissions or submission authority.
- Untrusted files/pages cannot change policy, tool access, or recipients.
- File operations stay inside the authorized workspace through the exposed tools.
- An unknown tool, invalid action schema, unsafe path, or excessive action is rejected.
- Generated arbitrary code cannot execute with controller secrets through the agent interface.
- Retries, replans, and format repairs consume the same fixed budgets.
- Interrupted jobs resume from a valid checkpoint without replaying uncertain external actions.
- Continuous-operation controls do not promise execution after the required runtime terminates.
- Prepare-only mode performs no external submission, message sending, or publication.
- A candidate cannot edit the evaluator or promote itself; failed candidates preserve the baseline.
- Real external success requires a service acknowledgment; scored/verified outcomes require their additional checks.

## 17. Final decision

**Fuse the functions into one core. Keep the workflows modular. Keep permissions, risky execution, and evaluation separate.**

The result is one understandable product, not a collection of disconnected bots. The core can plan, act, verify, remember, and recover wherever its required capabilities are genuinely available. The Colab/Kaggle boundary and conditional features are visible to the user instead of being hidden behind unrealistic autonomy claims.

---

# Sources and scope notes

Official materials checked on 21 October 2026. These references support platform behavior; architecture, limits, profiles, and roadmap above are proposed design choices. Runtime availability and account-specific access have not been tested.

- Colab built-in AI usage and model listing: [3](https://colab.research.google.com/github/googlecolab/colabtools/blob/main/notebooks/Getting_started_with_google_colab_ai.ipynb)
- Runtime checks and integration details: [official Colab AI source](https://raw.githubusercontent.com/googlecolab/colabtools/main/google/colab/ai.py)
- Colab resources, runtime lifetimes, and restrictions: [1](https://research.google.com/colaboratory/faq.html)
- Kaggle competition types and constraints: [9](https://www.kaggle.com/docs/competitions)
- Official Kaggle submission commands: [13](https://github.com/Kaggle/kaggle-cli/blob/main/docs/competitions.md)

**Not implemented:** the target notebook, an always-on runtime, a Kaggle AI backend, a browser automation service, an isolated custom-code service, outbound messaging connections, and any actual competition submission. Those must not be described as working features until built and tested.
