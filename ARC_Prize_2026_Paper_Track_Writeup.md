## Abstract

ARC-AGI-3 shifts fluid-intelligence evaluation from static grid transduction to stateful interactive worlds where mechanics, goals, hazards, and action semantics must be inferred online under strict efficiency and compute limits. Across a five-submission campaign on `arc-prize-2026-arc-agi-3`—spanning symbolic DSL starters (`0.06`–`0.07`), our zero-LLM `4CBOn2` State-Graph Frontier Explorer (`0.10`, Version ID `364758641`), and open-weight multimodal Python-REPL agents atop Tufa Labs' *Duck* harness and Daniel Franzen's Milestone 2 patch (`25.83`, Version ID `354898739`)—we analyze why interactive generalization stalls and how execution-bounded governance prevents silent regression. While HUD-masked state-graph search raises zero-LLM accuracy by `+67%` (`0.06` to `0.10`), multimodal REPL world-modeling delivers a **`258x` leap (`0.10` to `25.83`)**. Yet diagnostics across a 10-game validation slice (`42.84` mean score) expose a second bottleneck: games whose rules crystallize early solve completely (`sb26`: `8/8` levels, `100.00` score, `44.8k` tokens), whereas 90% of incomplete games (`3/7` to `5/9` levels) stop not from action exhaustion (`53`–`239` actions), but from **deliberation-token starvation (`78k`–`101k` tokens)** driven by open-loop belief drift and cyclic retries. Through `4CBOn2`, we decouple **stochastic hypothesis generation** from **deterministic transition enforcement**, paired with baseline-locked promotion gates that prevented deploying a regressed variant (`25.15`, Version `358771962`) over our `25.83` champion.

## 1. Theoretical Motivation: Dual-Budget POMDPs (*Theory*)

An ARC-AGI-3 run spans $M = 110$ unseen games (55 public, 55 private), each with $L \in [6, 10]$ levels on $64 \times 64$, 16-color grids using discrete moves (`ACTION1`–`ACTION5`, `RESET`) and coordinate clicks (`ACTION6`). Later levels carry triangularly increasing weight ($\ell / \frac{L(L+1)}{2}$), discounted by quadratic human-relative efficiency $E = \left(\frac{h}{h + a}\right)^2$. Agents face a coupled **dual-budget POMDP**:

1. **Physical Action Cost ($a$):** Every redundant probe, cycle, or permadeath reset permanently shrinks $E$.
2. **Token & Runtime Cost ($T, t$):** Under Kaggle's offline single-GPU budget, tokens spent re-parsing grids or deliberating in circles starve solvable downstream levels.

Pure graph search (`0.10`) plateaus after shallow levels because object priors (keys, switches, teleporters, timers) demand semantic abstraction rather than blind state enumeration. Conversely, unconstrained VLM loops (`25.83`) stall on deeper levels via **open-loop belief drift**: batching 15+ moves without mid-batch checks, mistaking HUD timer bars for causal board changes, or burning `>95k` tokens deliberating without falsifying probes (`53` actions across `96,609` tokens on `r11l`).

**Core Principle:** Reliable adaptation in unseen interactive worlds requires decoupling **untrusted world-model proposal** (multimodal reasoning + persistent Python synthesis) from **deterministic runtime invariants** (interior state-hash guards, permadeath ledgers, KV-cache hysteresis, and continuation-value scheduling).

## 2. System Architecture & Provenance (*Completeness*)

### 2.1 Open-Source Attribution
We clearly separate upstream foundations from our governance architecture and empirical analysis:
* **Base Harness:** Tufa Labs (*The Duck*, Jeroen Cottaar, Milestone 1 winner), providing the multi-turn multimodal Python-REPL loop.
* **Milestone 2 Patch & Serving:** Daniel Franzen (`da-fr/arc-agi-3-solution`), contributing context-drain hysteresis, frame-diff visuals, retained Python helpers, state/death guard primitives, Kaplan-Meier scheduling, and the Pennyroyal SGLang v2.5.3 launcher (crediting John Pezzulli, Mamy Ratsimbazafy, and Gabriel Olympie) serving `Intel/Qwen3.8-Flash-Next-W4A16-AutoRound` with `albucino` `FP8PLE` multi-token prediction (MTP) drafting on one NVIDIA RTX PRO 6000 (`96 GB` VRAM).
* **Our Contributions:** (1) Controlled cross-paradigm comparison from symbolic DSL synthesis (`0.06`) and zero-LLM state-graph search (`0.10`) to multimodal REPL execution (`25.83`); (2) per-game token-vs-action bottleneck diagnosis; and (3) `4CBOn2` bounded-autonomy governance pairing an Android control plane with baseline-locked `Kaggle6` promotion gates and guarded REPL execution.

### 2.2 Multimodal Serving & Sawtooth KV-Cache Hysteresis
`Qwen3.8-Flash-Next` (`W4A16` + 3-step `FP8` MTP draft, `0.96` static VRAM share, `fp8` KV cache) serves `10` active streams (`120` coroutines across `110` games) at `139,264` SGLang context (`116k` input + `12k` max output, `T=0.7`, `top_p=0.95`, `top_k=20`). To preserve RadixAttention prefix hits, context pruning follows a **sawtooth hysteresis curve**: history grows untouched to `116k` tokens, then drains in one batch to `58k`, keeping the cached prefix identical for the next `58k` tokens.

### 2.3 Change-Salient Perception & Retained Python Skills
Static `64x64` frames hide subtle 2-pixel transitions and multi-frame hazards. Grids are upscaled `10x` alongside side-by-side frame-diff renders, game-over diff frames, and multi-subframe animations. Game-scoped persistent Python functions let BFS pathfinders and object parsers written on Level 1 transfer into Levels 2–10 without re-synthesis.

### 2.4 Expected-Continuation-Value Scheduling
Stream admission ranks games by marginal expected score rate:
$$V(s) = \frac{A(\ell, a, L) + \tau(t)B(\ell, L)}{\rho}\left[w_a 0.5^{(a/\sigma_a)^2} + (1 - w_a) 0.5^{(T/(\sigma_T \rho))^2}\right]$$
where $A(\ell, a, L)$ is triangularly normalized immediate reward, $B(\ell, L)$ is remaining-level continuation value faded by $\tau(t)$ over the final 20% of runtime, and $\rho \in [0.5, 2.0]$ tracks per-game log-EWMA token pace.

### 2.5 `4CBOn2`: State-Graph Priors & Promotion Discipline
Our `4CBOn2` runtime includes a zero-LLM **State-Graph Frontier Explorer** (`0.10` LB) that hashes `64x64` grids, masks static HUD borders via pixel-change detection to isolate dynamic `ACTION6(x, y)` click targets, prunes verified no-ops per state hash, and runs Dijkstra backtracking across unexplored `(state, action)` frontiers. Its **Improvement Lab** enforces a strict invariant: no candidate replaces the locked baseline without outperforming it under explicit human approval.

## 3. Empirical Results & Bottleneck Anatomy (*Accuracy & Progress*)

### 3.1 Kaggle Public Leaderboard Progression (`arc-prize-2026-arc-agi-3`)

| Run | Paradigm & Mechanism | Version ID | Public LB | Governance Decision |
| :--- | :--- | :---: | :---: | :--- |
| **v1** | Symbolic perception + DSL starter | `353491457` | `0.06` | Initial baseline |
| **v2** | Refined heuristic/DSL starter | `354120266` | `0.07` | Promoted (`+0.01`) |
| **Frontier** | `4CBOn2` Zero-LLM Frontier Explorer | `364758641` | **`0.10`** | Best zero-LLM prior (`+67%` vs v1) |
| **v3** | Multimodal REPL (`Qwen3.8-Flash-Next` + Duck/Franzen) | `354898739` | **`25.83`** | **Champion (`258x` vs Frontier)** |
| **v4** | Modified `franzen-base v4` configuration | `358771962` | `25.15` | **Rejected (`-0.68` regression)** |

### 3.2 Ten-Game Validation Slice (`42.84` Mean Score)

| Game | State | Score | Levels | Actions | Tokens | Limiting Bottleneck |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| `sb26` | **Won** | **100.00** | `8/8` | 153 | 44,838 | Early rule induction + skill reuse |
| `r11l` | Gave up | 47.62 | `4/6` | **53** | **96,609** | `1,822` tokens/action (deliberation loop) |
| `ft09` | Gave up | 47.62 | `4/6` | **68** | **98,032** | `1,442` tokens/action on Level 5 twist |
| `tr87` | Gave up | 47.62 | `4/6` | 239 | 78,167 | High Level 5 action search cost |
| `sc25` | Gave up | 45.47 | `4/6` | 180 | 93,286 | Token cap reached mid-level |
| `lp85` | Gave up | 41.67 | `5/8` | 91 | 100,886 | 8-level horizon vs `100k` cap |
| `tu93` | Gave up | 32.48 | `5/9` | 178 | 93,055 | 9-level horizon; mid-game rule twist |
| `ar25` | Gave up | 27.78 | `4/8` | 132 | 80,119 | Unverified multi-step batch drift |
| `vc33` | Gave up | 21.43 | `3/7` | 76 | 92,783 | `1,221` tokens/action without progress |
| `re86` | Gave up | 16.67 | `3/8` | 154 | 89,775 | Repeated hazard resets on Level 4 |

**Diagnostic Takeaway:** Nine of ten games solved 3–5 levels zero-shot, confirming open-weight multimodal REPL models induce novel mechanics online. Stalls occur at Levels 4–6 when rule twists break early hypotheses and the model burns `80k`–`101k` tokens (`>1,400` tokens/action in `r11l` and `ft09`) deliberating instead of executing cheap Python probes.

## 4. Guarded Execution & Universality (*Novelty & Universality*)

### 4.1 Deterministic Runtime Transition Guards
Targeting the `r11l`/`ft09`/`re86` bottlenecks motivates wrapping REPL execution (`Level >= 2`) in orthogonal invariants preserving RadixAttention prefix caching and `2,048`-token yield continuity:
1. **In-Snippet State-Cycle Latch:** Masking HUD borders when hashing board state to halt runaway `while` loops inside one Python call once `(interior_hash, action)` repeats—saving 20–30 life-bar moves without falsely blocking animation cues.
2. **Cross-Reset Permadeath Ledger:** Indexing `(interior_hash, fatal_action)` pairs across resets so lethal moves stay guarded (`1`-slot overridable) even after `116k` to `58k` context drains.
3. **Closed-Loop Verification & Tool-First Probing:** Enforcing predicted-vs-actual state assertions inside batched scripts, favoring zero-action Python probes over long guessing monologues, and activating Kaplan-Meier token-pace scheduling.

### 4.2 Generalization & Limitations
Decoupling **probabilistic policy synthesis** from **deterministic invariants and baseline-locked promotion (`4CBOn2`)** transfers directly to software agents, robotics, and automated science—where actions carry irreversible cost and unvalidated edits cause silent regression (`25.83` to `25.15`). Two limits remain: model weights stay frozen at test time, and iterative improvement requires GPU compute rather than idle phone execution. Pairing neuro-symbolic skill synthesis with execution-bounded governance offers a reproducible path toward human-level interactive generalization.
