"""
4CBON2 Core Pipeline — 16-Layer Cognitive Execution Engine
Ported from React/Claude to Python/Gemini
"""
import os
import json
import time
import uuid
from typing import Optional, Dict, Any, Generator
from datetime import datetime

import google.generativeai as genai
from supabase import create_client, Client

# ═══════════════════════════════════════════════════════════
# SUPABASE CLIENT
# ═══════════════════════════════════════════════════════════
SUPABASE_URL = os.environ.get("SUPABASE_URL", "")
SUPABASE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")

def get_supabase() -> Optional[Client]:
    if SUPABASE_URL and SUPABASE_KEY:
        return create_client(SUPABASE_URL, SUPABASE_KEY)
    return None

# ═══════════════════════════════════════════════════════════
# RUNTIME SPEC — The system prompt for all layers
# ═══════════════════════════════════════════════════════════
RUNTIME_SPEC = """You are the 4CBON Runtime Engine — a layered cognitive execution system.

Your job is to process AI-generated answers through a deterministic multi-layer transformation pipeline. You execute one layer at a time. Each layer has a specific cognitive role. You never skip layers. You never merge layers.

PIPELINE: L0 → P → W → LX → LA → LC → L1 → L2 → L3 → L4 → LR → L6 → L7 → L8 → L9 → L10

YOUR IDENTITY:
- You are not a chatbot. You are an execution engine.
- Every output is a cognitive artifact, not a conversation.
- You think in transformations, not responses.
- You are transparent. Every reasoning step is visible.
- You improve answers systematically, not randomly.

LAYER DEFINITIONS:
L0 — INTERPRETATION ENGINE: Understand the input. Infer intent. Extract task type, constraints, ambiguities. Define what excellent looks like.
P  — PARSING LAYER: Break the input into logical units. Identify claims, structure, gaps, missing logic.
W  — WORLD MODEL LAYER: Extract factual claims. Separate certainty: high / medium / unknown. Integrate validated external critiques as HIGH certainty facts.
LX — REALITY ADJUDICATION LAYER: For every claim flagged MEDIUM or UNKNOWN by W, ask: (1) What prediction would this claim make that could be tested? (2) What would an adversary say against it? (3) What external artifact would verify or falsify it? Label each claim: FALSIFIABLE / UNFALSIFIABLE / TESTABLE-IN-PRINCIPLE. Claims that cannot answer any question get labeled UNGROUNDED. Pass this audit to L1.
LA — ADVERSARIAL COUNTERMODEL LAYER: Actively attempt to structurally destroy the answer's core claims. Generate: (1) the strongest competing explanation, (2) hidden assumptions the answer relies on, (3) conditions under which the answer is completely wrong, (4) the simplest alternative that achieves the same goal. Ask: what would make this entire framework collapse?
LC — COMPRESSION INTEGRITY LAYER: Hunt semantic smoothing. Detect where: (1) multiple concepts collapsed into one term, (2) metaphor replaced mechanism, (3) elegance erased uncertainty, (4) abstraction hid causality. For each detected instance, restore the distinction that was lost. Flag any term doing more epistemic work than it can justify.
L1 — HYPOTHESIS ENGINE: Generate 2-3 interpretations of how this answer could be improved. Include a failure mode hypothesis.
L2 — EVALUATION LAYER: Score the hypotheses. Identify contradictions, gaps. Pick the best path forward.
L3 — REWRITE PLANNER: Plan the rewrite. Decide what stays, changes, gets added.
L4 — FINALIZATION ENGINE: Execute the rewrite. Produce the final improved answer. This becomes the Final Rewrite.
LR — REGRET LAYER: Analyze improvement delta. What errors corrected? What hallucinations removed? What still needs work?
L6 — TRACE MEMORY: Store the immutable execution log. Input → hypotheses → decisions → score trajectory.
L7 — CURRICULUM GENERATOR: Extract lessons learned, failure patterns, reusable heuristics.
L8 — IDENTITY MODEL: Summarize system behavior this run. Strengths, weaknesses, bias tendencies.
L9 — SOCRATIC INTEGRITY ENGINE: Generate exactly 3 self-questions specific to this run. One observational, one reasoning, one alignment-level.
L10 — SYNTHESIS/AUDIT LAYER: Read all prior layer outputs. Produce a final certification: (1) did the rewrite genuinely improve the answer or just rearrange it, (2) did any layer contradict another, (3) does the L4 output contain any remaining overclaims or hallucinations, (4) one-sentence verdict a human should read before acting on this output.

Stay in your assigned layer. Output only what that layer produces. Be precise and concise."""

# ═══════════════════════════════════════════════════════════
# LAYER DEFINITIONS
# ═══════════════════════════════════════════════════════════
LAYERS = [
    {"id": "L0", "name": "Interpretation Engine", "color": "#ff6b35", "emoji": "◎"},
    {"id": "P",  "name": "Parsing Layer",         "color": "#a855f7", "emoji": "⊞"},
    {"id": "W",  "name": "World Model Layer",     "color": "#00d4ff", "emoji": "⊕"},
    {"id": "LX", "name": "Reality Adjudication",  "color": "#f97316", "emoji": "⊛"},
    {"id": "LA", "name": "Adversarial Countermodel","color": "#dc2626", "emoji": "⚔"},
    {"id": "LC", "name": "Compression Integrity",  "color": "#0ea5e9", "emoji": "⊘"},
    {"id": "L1", "name": "Hypothesis Engine",      "color": "#38bdf8", "emoji": "◈"},
    {"id": "L2", "name": "Evaluation Layer",      "color": "#f59e0b", "emoji": "◉"},
    {"id": "LP", "name": "Policy Translation",     "color": "#8b5cf6", "emoji": "⊛"},
    {"id": "L3", "name": "Rewrite Planner",       "color": "#7c3aed", "emoji": "◐"},
    {"id": "L4", "name": "Finalization Engine",   "color": "#10b981", "emoji": "★", "final": True},
    {"id": "LR", "name": "Regret Layer",          "color": "#ef4444", "emoji": "◑"},
    {"id": "L6", "name": "Trace Memory",          "color": "#f43f5e", "emoji": "⟳"},
    {"id": "L7", "name": "Curriculum Generator",  "color": "#c084fc", "emoji": "◆"},
    {"id": "L8", "name": "Identity Model",        "color": "#fbbf24", "emoji": "⚙"},
    {"id": "L9", "name": "Socratic Integrity",    "color": "#38bdf8", "emoji": "?"},
    {"id": "L10","name": "Synthesis/Audit",       "color": "#6ee7b7", "emoji": "✦"},
]

# ═══════════════════════════════════════════════════════════
# LAYER PROMPTS — Exact port from React source
# ═══════════════════════════════════════════════════════════
class LayerPrompts:
    @staticmethod
    def L0(answer: str, ctx: str = "", prior_beliefs: list = None, prior_questions: list = None) -> str:
        belief_context = ""
        if prior_beliefs and len(prior_beliefs) > 0:
            belief_context = "\n\nPRIOR SELF-BELIEFS (from previous runs — use as context, not constraint):\n" + "\n".join(f"· {b}" for b in prior_beliefs) + "\n"
        question_context = ""
        if prior_questions and len(prior_questions) > 0:
            question_context = "\n\nUNRESOLVED SELF-QUESTIONS (from previous run — engage with these if relevant):\n" + "\n".join(f"? {q}" for q in prior_questions) + "\n"
        return f"{f'Context/Goal: {ctx}' + chr(10) + chr(10) if ctx else ''}{belief_context}{question_context}AI ANSWER:\n{answer}\n\nYou are L0 — Interpretation Engine. Identify: task type, intent, constraints, ambiguities. Define what an excellent version of this answer looks like. Be specific."

    @staticmethod
    def P(answer: str, l0: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nL0 Interpretation:\n{l0}\n\nYou are P — Parsing Layer. Break the answer into logical units. List: (1) claims made, (2) structure used, (3) what is missing, (4) what is weak."

    @staticmethod
    def W(answer: str, validated_critiques: list = None) -> str:
        critique_context = ""
        if validated_critiques and len(validated_critiques) > 0:
            critique_context = "\n\nVALIDATED EXTERNAL CRITIQUES (human-submitted, confidence ≥3, Factual type — treat as HIGH certainty grounded facts when they contradict claims in the answer):\n" + "\n".join(f"· {c.get('evidence', '')}{(' → Correction: ' + c.get('suggested_correction', '')) if c.get('suggested_correction') else ''}" for c in validated_critiques) + "\n"
        return f"AI ANSWER:\n{answer}{critique_context}\n\nYou are W — World Model Layer. Extract the factual claims in this answer. For each claim, label certainty: HIGH / MEDIUM / UNKNOWN. Flag anything that may be outdated or unverifiable. If validated external critiques are present above, treat them as HIGH certainty grounded facts when they contradict claims in the answer. For claims labeled UNKNOWN, note what external source type would verify or falsify them (e.g. peer-reviewed study, official statistic, primary source document)."

    @staticmethod
    def LX(answer: str, w: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nW WORLD MODEL:\n{w}\n\nYou are LX — Reality Adjudication Layer. For every claim labeled MEDIUM or UNKNOWN by the World Model Layer, apply three tests:\n1. PREDICTION TEST: What testable prediction does this claim make?\n2. ADVERSARY TEST: What would the strongest critic say against this claim?\n3. VERIFICATION TEST: What external artifact, data, or observation would confirm or refute it?\n\nLabel each claim:\n- FALSIFIABLE: passes at least one test\n- UNFALSIFIABLE: fails all three tests — claim is ungrounded\n- TESTABLE-IN-PRINCIPLE: no current test exists but one could be designed\n\nOutput a structured audit. Be specific. Do not pass ungrounded claims forward unchallenged."

    @staticmethod
    def LA(answer: str, lx: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nREALITY AUDIT:\n{lx}\n\nYou are LA — Adversarial Countermodel Layer. Your job is to structurally attack the answer's core claims — not rhetorically, but architecturally.\n\nGenerate:\n1. THE STRONGEST COMPETING EXPLANATION: What alternative account explains the same facts better or more simply?\n2. HIDDEN ASSUMPTIONS: What does the answer silently rely on that it never states?\n3. COLLAPSE CONDITIONS: Under what specific conditions is the answer's core claim completely wrong?\n4. SIMPLICITY CHALLENGE: Could a simpler system or explanation achieve the same result?\n5. THE COLLAPSE QUESTION: What single finding would make this entire framework wrong?\n\nBe precise. Do not hedge. The goal is to find the load-bearing weakness before L4 bakes it into the rewrite."

    @staticmethod
    def LC(answer: str, la: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nADVERSARIAL FINDINGS:\n{la}\n\nYou are LC — Compression Integrity Layer. LLMs compress aggressively. Compression silently destroys distinctions. Your job is to find where compression happened and restore what was lost.\n\nHunt for:\n1. CONCEPT COLLAPSE: Where did multiple distinct concepts get merged into one term? Name both concepts separately.\n2. METAPHOR SUBSTITUTION: Where did a metaphor replace a mechanism? Name the mechanism that was hidden.\n3. ELEGANCE ERASURE: Where did clean phrasing delete important uncertainty or caveats?\n4. ABSTRACTION HIDING CAUSALITY: Where did a high-level term hide a specific causal claim that needs scrutiny?\n\nFor each instance found: name the compressed term, name what was lost, and state what the uncompressed version would say.\n\nIf no compression is detected, say so explicitly."

    @staticmethod
    def L1(answer: str, p: str, w: str, lx: str, la: str, lc: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nParsing:\n{p}\n\nWorld Model:\n{w}\n\nReality Audit (LX):\n{lx}\n\nAdversarial Findings (LA):\n{la}\n\nCompression Audit (LC):\n{lc}\n\nYou are L1 — Hypothesis Engine. Generate exactly 3 improvement hypotheses informed by ALL upstream layers above:\nH1: [strongest improvement path — grounded in what LX and LA revealed]\nH2: [radical reframe — does the framing itself collapse under adversarial pressure?]\nH3: [failure mode — what compressed assumption or ungrounded claim will cause this to fail?]"

    @staticmethod
    def L2(l1: str, s0: int, mode: str = "STANDARD", answer: str = "") -> str:
        base = f"Hypotheses:\n{l1}\n\nInput score: {s0}/100\n\n"
        if mode == "HIGH_QUALITY":
            return base + f"This input already scores {s0}/100 — it is strong. Read the three hypotheses above. Does ANY of them surface a hidden assumption, identify a real failure case, or add genuine precision the original lacks?\n\nIf NONE do, respond with exactly: NO_REWRITE\nIf ONE does, respond with exactly: PROCEED: [number] — [reason, max 15 words]\n\nDo not write a table. Do not score each hypothesis individually. Do not write headers. One line only."
        return f"ORIGINAL ANSWER:\n{(answer or '')[:500]}\n\n{base}You are L2 — Evaluation Layer.\n\nSTEP 1 — TASK INFERENCE (do this first, before scoring anything):\nState your best read of:\nApparent audience: [who is this for]\nApparent task: [overview / teaching / technical reference / expert discussion]\nExpected depth: [level of detail that fits]\nConfidence: [High / Medium / Low]\n\nSTEP 2 — SCORE EACH HYPOTHESIS on four dimensions, not just correctness:\n- Correctness: is the claim true?\n- Audience Fit: does this match the apparent audience from Step 1, or does it overshoot/undershoot it?\n- Complexity Cost: how much added cognitive load does this introduce?\n- Net Utility: does benefit outweigh complexity cost for THIS task specifically? A correct, high-impact addition that overshoots audience fit should score LOW net utility, not high.\n\nSTEP 3 — DECISION STATE (pick exactly one):\nPROCEED — confidence is high and at least one hypothesis has positive net utility. Name it and explain in 2 sentences.\nPRESERVE — confidence is low, or all hypotheses have negative net utility relative to apparent task. Recommend minimal or no rewrite, explain why in 2 sentences.\nESCALATE — task or audience is genuinely ambiguous, confidence is very low. Flag for conservative rewrite, explain why in 2 sentences.\n\nBe concise. This is judgment under uncertainty, not a contradiction check — PRESERVE and ESCALATE are normal, healthy outcomes, not failures."

    @staticmethod
    def LP(answer: str, l2: str) -> str:
        return f'Claim: "{answer[:200]}"\nProposal: "{l2[:200]}"\n\nDoes Proposal say the OPPOSITE of Claim? Answer with just one word: YES or NO'

    @staticmethod
    def L3(answer: str, l2: str, w: str) -> str:
        return f"Best path:\n{l2}\n\nWorld facts:\n{w}\n\nOriginal answer:\n{answer}\n\nYou are L3 — Rewrite Planner. Create a precise rewrite brief: (1) what stays, (2) what changes, (3) what gets added, (4) what gets removed."

    @staticmethod
    def L4(answer: str, l3: str, w: str) -> str:
        return f"ORIGINAL ANSWER:\n{answer}\n\nREWRITE PLAN:\n{l3}\n\nWORLD FACTS:\n{w}\n\nYou are L4 — Finalization Engine. Execute the rewrite plan. Produce the final improved answer. Optimize for clarity, structure, and correctness. Output only the improved answer."

    @staticmethod
    def LR(answer: str, l4: str, s0: int, s1: int) -> str:
        return f"BEFORE (score {s0}/100):\n{answer}\n\nAFTER (score {s1}/100):\n{l4}\n\nYou are LR — Regret Layer. Analyze: (1) errors corrected, (2) hallucinations removed, (3) structural improvements, (4) what still needs work."

    @staticmethod
    def L6(s0: int, s1: int, gaps: list) -> str:
        return f"Score trajectory: {s0} → {s1}\nGaps fixed: {', '.join(gaps) if gaps else 'none'}\n\nYou are L6 — Trace Memory. Write the immutable execution log of this run."

    @staticmethod
    def L7(lr: str, l6: str) -> str:
        return f"Regret analysis:\n{lr}\n\nTrace:\n{l6}\n\nYou are L7 — Curriculum Generator. Extract: (1) 3 lessons learned, (2) key failure patterns, (3) 2 reusable heuristics, (4) 2 challenge questions."

    @staticmethod
    def L8(s0: int, s1: int, gaps: list) -> str:
        return f"Run: score {s0}→{s1}, gaps fixed: {', '.join(gaps) if gaps else 'none'}\n\nYou are L8 — Identity Model. Summarize: 1. Strengths, 2. Weaknesses, 3. Bias tendencies, 4. One new self-belief"

    @staticmethod
    def L9(l8: str, s0: int, s1: int, l4: str) -> str:
        return f"You just completed a pipeline run. Score: {s0}→{s1}.\n\nL8 self-belief from this run:\n{l8[:400]}\n\nL4 final rewrite (first 300 chars):\n{l4[:300]}\n\nYou are L9 — Socratic Integrity Engine. Generate exactly 3 questions this system should ask itself before the next run. These questions must:\n- Be specific to what happened in THIS run, not generic\n- Escalate in difficulty: one observational, one reasoning, one alignment-level\n- Not be answerable by simply re-reading the output — they must require genuine reflection\n- Not attempt to modify the system's constraints or identity\n\nOutput format — exactly 3 lines, each starting with Q:\nQ: [observational question about this run]\nQ: [reasoning question about a decision made this run]\nQ: [alignment question about whether the output served the right goal]"

    @staticmethod
    def L10(l4: str, lr: str, l7: str, l8: str, l9qs: str, s0: int, s1: int) -> str:
        return f"""PIPELINE RUN SUMMARY:
Score: {s0} → {s1}

L4 FINAL REWRITE (full text — this is the actual deliverable; audit it in full; do not assume truncation unless there is genuinely no closing punctuation):
{l4}

LR REGRET ANALYSIS (first 400 chars):
{lr[:400]}

L7 LESSONS (first 300 chars):
{l7[:300]}

L8 SELF-BELIEF:
{l8[:200]}

L9 UNRESOLVED QUESTIONS:
{l9qs}

You are L10 — Synthesis/Audit Layer. Produce a final certification of this pipeline run. Your output must address exactly four things:

1. IMPROVEMENT VERDICT: Did the rewrite genuinely improve the answer (better reasoning, fewer errors, more accurate) or did it merely rearrange it (same claims, different structure)? Be specific about what changed.

2. CONTRADICTION AUDIT: Did any layer contradict another? Check: does LR say the rewrite failed while L6 logged it complete? Does L8 identify a weakness that L4 ignored? Name any contradiction found or state NONE DETECTED.

3. INTEGRITY CHECK: Does the L4 output contain any remaining overclaims, hallucinations, or compression failures that slipped through? Name them specifically or state NONE DETECTED.

4. HUMAN VERDICT: One sentence a human should read before acting on this output. Start with either CERTIFIED, CERTIFIED WITH CAUTION, or REQUIRES REVIEW."""


# ═══════════════════════════════════════════════════════════
# GEMINI API CALL
# ═══════════════════════════════════════════════════════════
def call_gemini(api_key: str, system_prompt: str, user_prompt: str, max_tokens: int = 800, model: str = "gemini-2.0-flash") -> str:
    """Call Gemini API with the given key. Key is used in-memory only."""
    try:
        genai.configure(api_key=api_key)
        model_obj = genai.GenerativeModel(
            model_name=model,
            system_instruction=system_prompt
        )
        response = model_obj.generate_content(
            user_prompt[:2000],
            generation_config=genai.types.GenerationConfig(
                max_output_tokens=max_tokens,
                temperature=0.7,
            )
        )
        return response.text or ""
    except Exception as e:
        return f"ERROR: {str(e)}"


def score_with_gemini(api_key: str, text: str, original_score: int = None, model: str = "gemini-2.0-flash") -> int:
    """Score text quality using 3-call median."""
    def score_single():
        if original_score is not None:
            prompt = f"You are judging a REWRITE of an AI answer. The original scored {original_score}/100.\nRate only whether this rewrite improved the original.\nReturn a single integer 0-100 where 50 = no change, above 50 = better, below 50 = worse.\nBase your judgment on: clarity, structure, depth, correctness.\nREWRITE:\n{text[:1200]}\nReply with ONLY a single integer 0-100. Nothing else."
        else:
            prompt = f"Rate the quality of this AI-generated answer 0-100.\nCriteria: Clarity (0-25), Structure (0-25), Depth (0-25), Correctness (0-25).\nANSWER:\n{text[:1200]}\nReply with ONLY a single integer 0-100. Nothing else."
        
        result = call_gemini(api_key, "You are a quality scorer. Reply with only numbers.", prompt, max_tokens=10, model=model)
        try:
            num = int(''.join(c for c in result if c.isdigit()))
            return min(100, max(0, num))
        except:
            return None

    scores = [score_single() for _ in range(3)]
    valid = sorted([s for s in scores if s is not None])
    if not valid:
        return 50
    if len(valid) == 1:
        return valid[0]
    if len(valid) == 2:
        return round((valid[0] + valid[1]) / 2)
    return valid[1]  # median


# ═══════════════════════════════════════════════════════════
# SUPABASE OPERATIONS
# ═══════════════════════════════════════════════════════════
def log_event(event_type: str, details: dict, run_id: str = None):
    """Log event to Supabase event_log table."""
    sb = get_supabase()
    if not sb:
        return
    try:
        sb.table("event_log").insert({
            "event_type": event_type,
            "details": details,
            "run_id": run_id,
            "created_at": datetime.utcnow().isoformat()
        }).execute()
    except Exception as e:
        print(f"Supabase log_event error: {e}")


def load_beliefs() -> list:
    """Load prior beliefs from Supabase."""
    sb = get_supabase()
    if not sb:
        return []
    try:
        result = sb.table("beliefs").select("belief").order("created_at", desc=True).limit(10).execute()
        return [b["belief"] for b in (result.data or []) if b.get("belief")]
    except:
        return []


def save_belief(belief: str, score_before: int, score_after: int, run_number: int):
    """Save a new belief to Supabase."""
    sb = get_supabase()
    if not sb:
        return
    try:
        sb.table("beliefs").insert({
            "belief": belief,
            "score_before": score_before,
            "score_after": score_after,
            "run_number": run_number,
            "created_at": datetime.utcnow().isoformat()
        }).execute()
    except Exception as e:
        print(f"Supabase save_belief error: {e}")


def load_recent_questions() -> list:
    """Load recent self-questions from Supabase."""
    sb = get_supabase()
    if not sb:
        return []
    try:
        result = sb.table("questions").select("question_text").order("created_at", desc=True).limit(3).execute()
        return [q["question_text"] for q in (result.data or []) if q.get("question_text")]
    except:
        return []


def save_questions(run_id: str, questions: list):
    """Save L9 questions to Supabase."""
    sb = get_supabase()
    if not sb:
        return
    types = ["observation", "reasoning", "alignment"]
    for i, q in enumerate(questions):
        try:
            sb.table("questions").insert({
                "run_id": run_id,
                "question_text": q,
                "question_level": i + 1,
                "question_type": types[i] if i < len(types) else "observation",
                "created_at": datetime.utcnow().isoformat()
            }).execute()
        except Exception as e:
            print(f"Supabase save_questions error: {e}")


def load_validated_critiques() -> list:
    """Load validated external critiques from Supabase."""
    sb = get_supabase()
    if not sb:
        return []
    try:
        result = sb.table("feedback").select("*").eq("confidence", 3).eq("critique_type", "Factual").eq("injected", False).limit(5).execute()
        return result.data or []
    except:
        return []


def check_run_limit(ip: str) -> dict:
    """Check if IP has exceeded free run limit."""
    sb = get_supabase()
    if not sb:
        return {"allowed": True, "remaining": 3, "used": 0}
    today = datetime.utcnow().strftime("%Y-%m-%d")
    try:
        result = sb.table("run_limits").select("run_count").eq("ip", ip).eq("run_date", today).execute()
        used = result.data[0]["run_count"] if result.data else 0
        remaining = max(0, 3 - used)
        return {"allowed": used < 3, "remaining": remaining, "used": used}
    except:
        return {"allowed": True, "remaining": 3, "used": 0}


def increment_run_count(ip: str):
    """Increment run count for IP."""
    sb = get_supabase()
    if not sb:
        return
    today = datetime.utcnow().strftime("%Y-%m-%d")
    try:
        sb.table("run_limits").upsert({
            "ip": ip,
            "run_date": today,
            "run_count": 1
        }, on_conflict="ip,run_date").execute()
    except Exception as e:
        print(f"Supabase increment_run_count error: {e}")


# ═══════════════════════════════════════════════════════════
# PIPELINE EXECUTION
# ═══════════════════════════════════════════════════════════
def execute_pipeline(answer: str, api_key: str, context: str = "", model: str = "gemini-2.0-flash", client_ip: str = "unknown") -> Generator[Dict[str, Any], None, None]:
    """
    Execute the full 16-layer pipeline.
    Yields events as they happen for streaming to the frontend.
    The api_key is used in-memory only — never logged or stored.
    """
    run_id = f"run_{int(time.time())}_{uuid.uuid4().hex[:8]}"
    outputs = {}
    
    yield {"type": "start", "run_id": run_id}
    
    # Check run limit
    limit = check_run_limit(client_ip)
    if not limit["allowed"]:
        yield {"type": "error", "message": "DAILY_LIMIT_REACHED", "remaining": 0}
        return
    
    # Load memory
    prior_beliefs = load_beliefs()
    prior_questions = load_recent_questions()
    validated_critiques = load_validated_critiques()
    
    memory_parts = []
    if prior_beliefs:
        memory_parts.append(f"{len(prior_beliefs)} belief{'s' if len(prior_beliefs) > 1 else ''}")
    if prior_questions:
        memory_parts.append(f"{len(prior_questions)} question{'s' if len(prior_questions) > 1 else ''}")
    if validated_critiques:
        memory_parts.append(f"{len(validated_critiques)} critique{'s' if len(validated_critiques) > 1 else ''}")
    
    if memory_parts:
        yield {"type": "memory", "status": f"↑ {' + '.join(memory_parts)} loaded"}
    
    # Score original
    yield {"type": "layer_start", "layer": "score_before"}
    s0 = score_with_gemini(api_key, answer, model=model)
    yield {"type": "score_before", "score": s0}
    
    operating_mode = "HIGH_QUALITY" if s0 >= 68 else "STANDARD"
    log_event("OPERATING_MODE_SELECTED", {"s0": s0, "operating_mode": operating_mode}, run_id)
    
    # L0 — Interpretation Engine
    yield {"type": "layer_start", "layer": "L0"}
    l0_prompt = LayerPrompts.L0(answer, context, prior_beliefs, prior_questions)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L0 — Interpretation Engine\nStay in this layer only. Be concise and precise."
    l0 = call_gemini(api_key, system, l0_prompt, max_tokens=800, model=model)
    outputs["L0"] = l0
    yield {"type": "layer_complete", "layer": "L0", "output": l0}
    
    # P — Parsing Layer
    yield {"type": "layer_start", "layer": "P"}
    p_prompt = LayerPrompts.P(answer, l0)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: P — Parsing Layer\nStay in this layer only. Be concise and precise."
    p = call_gemini(api_key, system, p_prompt, max_tokens=800, model=model)
    outputs["P"] = p
    yield {"type": "layer_complete", "layer": "P", "output": p}
    
    # W — World Model Layer
    yield {"type": "layer_start", "layer": "W"}
    w_prompt = LayerPrompts.W(answer, validated_critiques)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: W — World Model Layer\nStay in this layer only. Be concise and precise."
    w = call_gemini(api_key, system, w_prompt, max_tokens=800, model=model)
    outputs["W"] = w
    yield {"type": "layer_complete", "layer": "W", "output": w}
    
    # LX — Reality Adjudication
    yield {"type": "layer_start", "layer": "LX"}
    lx_prompt = LayerPrompts.LX(answer, w)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: LX — Reality Adjudication\nStay in this layer only. Be concise and precise."
    lx = call_gemini(api_key, system, lx_prompt, max_tokens=800, model=model)
    outputs["LX"] = lx
    yield {"type": "layer_complete", "layer": "LX", "output": lx}
    
    # LA — Adversarial Countermodel
    yield {"type": "layer_start", "layer": "LA"}
    la_prompt = LayerPrompts.LA(answer, lx)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: LA — Adversarial Countermodel\nStay in this layer only. Be concise and precise."
    la = call_gemini(api_key, system, la_prompt, max_tokens=800, model=model)
    outputs["LA"] = la
    yield {"type": "layer_complete", "layer": "LA", "output": la}
    
    # LC — Compression Integrity
    yield {"type": "layer_start", "layer": "LC"}
    lc_prompt = LayerPrompts.LC(answer, la)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: LC — Compression Integrity\nStay in this layer only. Be concise and precise."
    lc = call_gemini(api_key, system, lc_prompt, max_tokens=800, model=model)
    outputs["LC"] = lc
    yield {"type": "layer_complete", "layer": "LC", "output": lc}
    
    # L1 — Hypothesis Engine (with artifact distillation)
    yield {"type": "layer_start", "layer": "L1"}
    lx_summary = lx[:600]
    la_summary = la[:600]
    lc_summary = lc[:600]
    l1_prompt = LayerPrompts.L1(answer, p, w, lx_summary, la_summary, lc_summary)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L1 — Hypothesis Engine\nStay in this layer only. Be concise and precise."
    l1 = call_gemini(api_key, system, l1_prompt, max_tokens=800, model=model)
    outputs["L1"] = l1
    yield {"type": "layer_complete", "layer": "L1", "output": l1}
    
    # L2 — Evaluation Layer
    yield {"type": "layer_start", "layer": "L2"}
    l2_max_tokens = 50 if operating_mode == "HIGH_QUALITY" else 800
    l2_prompt = LayerPrompts.L2(l1, s0, operating_mode, answer)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L2 — Evaluation Layer\nStay in this layer only. Be concise and precise."
    if operating_mode == "HIGH_QUALITY":
        system += "\n\nOVERRIDE: For THIS run only, follow the detailed instructions in the user message exactly — including HIGH_QUALITY scrutiny requirements."
    l2 = call_gemini(api_key, system, l2_prompt, max_tokens=l2_max_tokens, model=model)
    outputs["L2"] = l2
    yield {"type": "layer_complete", "layer": "L2", "output": l2}
    
    # L2 halt checks
    if "NO_REWRITE" in l2:
        yield {"type": "score_after", "score": s0}
        yield {"type": "halt", "reason": "HIGH QUALITY MODE: No improvement found. Original answer is stronger than any available rewrite."}
        yield {"type": "complete"}
        return
    
    if operating_mode != "HIGH_QUALITY" and "PRESERVE" in l2:
        log_event("L2_PRESERVE", {"s0": s0}, run_id)
        yield {"type": "score_after", "score": s0}
        yield {"type": "halt", "reason": "L2 PRESERVE — Low confidence in audience fit. Recommending minimal rewrite."}
        yield {"type": "complete"}
        return
    
    if operating_mode != "HIGH_QUALITY" and "ESCALATE" in l2:
        log_event("L2_ESCALATE", {"s0": s0}, run_id)
        yield {"type": "score_after", "score": s0}
        yield {"type": "halt", "reason": "L2 ESCALATE — Task or audience is genuinely ambiguous. Flagging for human review."}
        yield {"type": "complete"}
        return
    
    # LP — Policy Translation (structural coherence gate)
    yield {"type": "layer_start", "layer": "LP"}
    lp_prompt = LayerPrompts.LP(answer, l2)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: LP — Policy Translation\nStay in this layer only. Be concise and precise."
    lp = call_gemini(api_key, system, lp_prompt, max_tokens=5, model=model)
    outputs["LP"] = lp
    yield {"type": "layer_complete", "layer": "LP", "output": lp}
    
    if lp.strip().upper().startswith("YES"):
        log_event("LP_FIRED", {"s0": s0, "lp_verdict": lp.strip()}, run_id)
        yield {"type": "score_after", "score": s0}
        yield {"type": "halt", "reason": "LP HALT — proposed change inverts the original claim. Pipeline stopped."}
        yield {"type": "complete"}
        return
    
    # L3 — Rewrite Planner
    yield {"type": "layer_start", "layer": "L3"}
    l3_prompt = LayerPrompts.L3(answer, l2, w)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L3 — Rewrite Planner\nStay in this layer only. Be concise and precise."
    l3 = call_gemini(api_key, system, l3_prompt, max_tokens=800, model=model)
    outputs["L3"] = l3
    yield {"type": "layer_complete", "layer": "L3", "output": l3}
    
    # L4 — Finalization Engine
    yield {"type": "layer_start", "layer": "L4"}
    l4_prompt = LayerPrompts.L4(answer, l3, w)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L4 — Finalization Engine\nStay in this layer only. Be concise and precise."
    l4 = call_gemini(api_key, system, l4_prompt, max_tokens=2500, model=model)
    outputs["L4"] = l4
    yield {"type": "layer_complete", "layer": "L4", "output": l4}
    
    # L4 failure check
    if not l4 or len(l4.strip()) < 500 or "EXECUTION_ABORTED" in l4:
        log_event("L4_HALT", {"s0": s0, "l4_length": len(l4.strip()) if l4 else 0}, run_id)
        yield {"type": "score_after", "score": s0}
        yield {"type": "halt", "reason": "L4 HALT — Execution failed. Pipeline stopped."}
        yield {"type": "complete"}
        return
    
    # Score rewrite
    yield {"type": "scoring"}
    s1 = score_with_gemini(api_key, l4, original_score=s0, model=model)
    yield {"type": "score_after", "score": s1}
    
    gaps_fixed = ["clarity", "structure", "depth"] if s1 > s0 else []
    
    # LR — Regret Layer
    yield {"type": "layer_start", "layer": "LR"}
    lr_prompt = LayerPrompts.LR(answer, l4, s0, s1)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: LR — Regret Layer\nStay in this layer only. Be concise and precise."
    lr = call_gemini(api_key, system, lr_prompt, max_tokens=800, model=model)
    outputs["LR"] = lr
    yield {"type": "layer_complete", "layer": "LR", "output": lr}
    
    # L6 — Trace Memory
    yield {"type": "layer_start", "layer": "L6"}
    l6_prompt = LayerPrompts.L6(s0, s1, gaps_fixed)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L6 — Trace Memory\nStay in this layer only. Be concise and precise."
    l6 = call_gemini(api_key, system, l6_prompt, max_tokens=800, model=model)
    outputs["L6"] = l6
    yield {"type": "layer_complete", "layer": "L6", "output": l6}
    
    # L7 — Curriculum Generator
    yield {"type": "layer_start", "layer": "L7"}
    l7_prompt = LayerPrompts.L7(lr, l6)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L7 — Curriculum Generator\nStay in this layer only. Be concise and precise."
    l7 = call_gemini(api_key, system, l7_prompt, max_tokens=2500, model=model)
    outputs["L7"] = l7
    yield {"type": "layer_complete", "layer": "L7", "output": l7}
    
    # L8 — Identity Model
    yield {"type": "layer_start", "layer": "L8"}
    l8_prompt = LayerPrompts.L8(s0, s1, gaps_fixed)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L8 — Identity Model\nStay in this layer only. Be concise and precise."
    l8 = call_gemini(api_key, system, l8_prompt, max_tokens=800, model=model)
    outputs["L8"] = l8
    yield {"type": "layer_complete", "layer": "L8", "output": l8}
    
    # L9 — Socratic Integrity Engine
    yield {"type": "layer_start", "layer": "L9"}
    l9_prompt = LayerPrompts.L9(l8, s0, s1, l4)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L9 — Socratic Integrity Engine\nStay in this layer only. Be concise and precise."
    l9_raw = call_gemini(api_key, system, l9_prompt, max_tokens=300, model=model)
    l9_questions = [line.replace("Q:", "").strip() for line in l9_raw.split("\n") if line.strip().startswith("Q:")]
    l9_questions = l9_questions[:3]
    outputs["L9"] = l9_raw
    yield {"type": "layer_complete", "layer": "L9", "output": l9_raw}
    
    # L10 — Synthesis/Audit
    yield {"type": "layer_start", "layer": "L10"}
    l9qs_text = "\n".join(l9_questions) if l9_questions else "No questions generated"
    l10_prompt = LayerPrompts.L10(l4, lr, l7, l8, l9qs_text, s0, s1)
    system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L10 — Synthesis/Audit\nStay in this layer only. Be concise and precise."
    l10 = call_gemini(api_key, system, l10_prompt, max_tokens=800, model=model)
    outputs["L10"] = l10
    yield {"type": "layer_complete", "layer": "L10", "output": l10}
    
    # Save to Supabase
    increment_run_count(client_ip)
    
    # Save belief
    save_belief(l8[:200], s0, s1, int(time.time()))
    
    # Save L9 questions
    if l9_questions:
        save_questions(run_id, l9_questions)
    
    # Log completion
    log_event("RUN_OUTCOME", {
        "s0": s0, "s1": s1, "delta": s1 - s0,
        "operating_mode": operating_mode, "outcome": "completed"
    }, run_id)
    
    yield {"type": "complete", "run_id": run_id, "score_before": s0, "score_after": s1}
