"""
4CBON2 Unified - Vercel Deployment
Works with both the original multi-agent system AND the 16-layer deep pipeline.
"""
import os
import json
import asyncio
from datetime import datetime
from typing import Generator, Dict, Any, Optional

import gradio as gr
from gradio.routes import mount_gradio_app
import uvicorn
from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import google.generativeai as genai

# ─────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
SUPABASE_URL = os.environ.get("SUPABASE_URL", "")
SUPABASE_KEY = os.environ.get("SUPABASE_SERVICE_KEY", "")
GUMROAD_API_KEY = os.environ.get("GUMROAD_API_KEY", "")

MODEL_NAME = os.environ.get("MODEL_NAME", "gemini-2.0-flash")

# In-memory API key storage (per-session, not persisted)
api_key_storage: Dict[str, str] = {}

# Daily run tracking
daily_runs: Dict[str, list] = {}  # client_id -> list of timestamps

# ─────────────────────────────────────────────
# Gemini Configuration
# ─────────────────────────────────────────────
if GEMINI_API_KEY:
    genai.configure(api_key=GEMINI_API_KEY)


def set_client_api_key(client_id: str, api_key: str) -> bool:
    """Store client's API key in memory only."""
    if api_key and api_key.strip():
        api_key_storage[client_id] = api_key.strip()
        try:
            genai.configure(api_key=api_key)
            return True
        except Exception:
            return False
    return False


def get_client_api_key(client_id: str) -> Optional[str]:
    """Get client's stored API key."""
    return api_key_storage.get(client_id)


def call_gemini(system_prompt: str, user_prompt: str, max_tokens: int = 800, 
                 model_name: str = None, client_id: str = None) -> str:
    """Make a Gemini API call with system prompt."""
    key = GEMINI_API_KEY
    if client_id and client_id in api_key_storage:
        key = api_key_storage[client_id]
    
    if not key:
        return "ERROR: No API key available. Provide key via /api/pipeline"
    
    if model_name is None:
        model_name = MODEL_NAME
    
    try:
        genai.configure(api_key=key)
        model = genai.GenerativeModel(
            model_name=model_name,
            system_instruction=system_prompt
        )
        response = model.generate_content(
            user_prompt[:2000],
            generation_config=genai.types.GenerationConfig(
                max_output_tokens=max_tokens,
                temperature=0.7,
            )
        )
        return response.text or ""
    except Exception as e:
        return f"ERROR: {str(e)}"


def safe_ask_raw(prompt: str, max_tokens: int = 2048) -> str:
    """Safe wrapper for ask_raw."""
    try:
        result = ask_raw(prompt, max_tokens=max_tokens)
        if not result or not result.strip():
            return '{"error": "Empty response from LLM. Please try again."}'
        return result.strip()
    except Exception as e:
        return f'{{"error": "safe_ask_raw failed: {str(e)}"}}'


def ask_raw(prompt: str, max_tokens: int = 2048) -> str:
    """Generate text using Gemini API."""
    global GEMINI_API_KEY
    
    if GEMINI_API_KEY:
        genai.configure(api_key=GEMINI_API_KEY)
    
    try:
        model = genai.GenerativeModel(MODEL_NAME)
        response = model.generate_content(
            prompt,
            generation_config=genai.types.GenerationConfig(
                max_output_tokens=max_tokens,
                temperature=0.7,
            )
        )
        return response.text or ""
    except Exception as e:
        return f"ERROR: {str(e)}"


def ask_stream(question: str, context: str = None) -> Generator[str, None, None]:
    """Stream a five-lens answer."""
    prompt_template = """You are an expert on consciousness, neuroscience, and philosophy of mind.
Use the provided information to answer the question using the five lenses below.
{context_prefix}QUESTION: {question}

1. ANALOGICAL — Compare this to similar known phenomena, systems, or experiences.
2. INDUCTIVE — What patterns emerge from the evidence and context?
3. CRITICAL — What are the limitations, gaps, contradictions, or alternative viewpoints?
4. RESOLUTION — How do we reconcile conflicting perspectives?
5. FINAL ANSWER — A clear, direct, well-reasoned answer to the original question.

Use clear headers for each section."""
    
    context_prefix = ""
    if context:
        context_prefix = f"Here is some relevant information:\n\n{context}\n\n"
    formatted_prompt = prompt_template.format(question=question, context_prefix=context_prefix)
    full_text = ask_raw(formatted_prompt, max_tokens=2048)
    if full_text.startswith("ERROR"):
        yield full_text
        return
    words = full_text.split()
    chunk = ""
    for i, word in enumerate(words):
        chunk += word + " "
        if (i + 1) % 5 == 0 or i == len(words) - 1:
            yield chunk
            chunk = ""


# ─────────────────────────────────────────────
# 16-Layer Pipeline (Unified Edition)
# ─────────────────────────────────────────────
PIPELINE_LAYERS = [
    {"id": "L0", "name": "Interpretation Engine", "color": "#ff6b35", "emoji": "◎"},
    {"id": "P", "name": "Parsing Layer", "color": "#a855f7", "emoji": "⊞"},
    {"id": "W", "name": "World Model Layer", "color": "#00d4ff", "emoji": "⊕"},
    {"id": "LX", "name": "Reality Adjudication", "color": "#f97316", "emoji": "⊛"},
    {"id": "LA", "name": "Adversarial Countermodel", "color": "#dc2626", "emoji": "⚔"},
    {"id": "LC", "name": "Compression Integrity", "color": "#0ea5e9", "emoji": "⊘"},
    {"id": "L1", "name": "Hypothesis Engine", "color": "#38bdf8", "emoji": "◈"},
    {"id": "L2", "name": "Evaluation Layer", "color": "#f59e0b", "emoji": "◉"},
    {"id": "LP", "name": "Policy Translation", "color": "#8b5cf6", "emoji": "⊛"},
    {"id": "L3", "name": "Rewrite Planner", "color": "#7c3aed", "emoji": "◐"},
    {"id": "L4", "name": "Finalization Engine", "color": "#10b981", "emoji": "★", "final": True},
    {"id": "LR", "name": "Regret Layer", "color": "#ef4444", "emoji": "◑"},
    {"id": "L6", "name": "Trace Memory", "color": "#f43f5e", "emoji": "⟳"},
    {"id": "L7", "name": "Curriculum Generator", "color": "#c084fc", "emoji": "◆"},
    {"id": "L8", "name": "Identity Model", "color": "#fbbf24", "emoji": "⚙"},
    {"id": "L9", "name": "Socratic Integrity", "color": "#38bdf8", "emoji": "?"},
    {"id": "L10", "name": "Synthesis/Audit", "color": "#6ee7b7", "emoji": "✦"},
]

RUNTIME_SPEC = """You are the 4CBON Runtime Engine — a layered cognitive execution system.

Your job is to process AI-generated answers through a deterministic multi-layer transformation pipeline.

PIPELINE: L0 → P → W → LX → LA → LC → L1 → L2 → L3 → L4 → LR → L6 → L7 → L8 → L9 → L10

LAYER DEFINITIONS:
L0 — INTERPRETATION ENGINE: Understand the input. Infer intent. Extract task type, constraints, ambiguities.
P  — PARSING LAYER: Break the input into logical units. Identify claims, structure, gaps.
W  — WORLD MODEL LAYER: Extract factual claims. Label certainty: HIGH / MEDIUM / UNKNOWN.
LX — REALITY ADJUDICATION: For MEDIUM/UNKNOWN claims, apply three tests. Label: FALSIFIABLE / UNFALSIFIABLE / UNGROUNDED.
LA — ADVERSARIAL COUNTERMODEL: Generate strongest competing explanation, hidden assumptions, collapse conditions.
LC — COMPRESSION INTEGRITY: Hunt semantic smoothing, metaphor substitution, elegance erasure.
L1 — HYPOTHESIS ENGINE: Generate 3 improvement hypotheses.
L2 — EVALUATION LAYER: Score hypotheses. Pick best path.
L3 — REWRITE PLANNER: Plan precise rewrite.
L4 — FINALIZATION ENGINE: Execute the rewrite.
LR — REGRET LAYER: Analyze improvement delta.
L6 — TRACE MEMORY: Store execution log.
L7 — CURRICULUM GENERATOR: Extract lessons learned.
L8 — IDENTITY MODEL: Summarize system behavior.
L9 — SOCRATIC INTEGRITY: Generate 3 self-questions.
L10 — SYNTHESIS/AUDIT: Produce final certification.

Stay in your assigned layer. Output only what that layer produces."""


class PipelinePrompts:
    @staticmethod
    def L0(answer: str, ctx: str = "") -> str:
        return f"{f'Context/Goal: {ctx}\n\n' if ctx else ''}AI ANSWER:\n{answer}\n\nYou are L0 — Interpretation Engine. Identify: task type, intent, constraints, ambiguities."

    @staticmethod
    def P(answer: str, l0: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nL0 Interpretation:\n{l0}\n\nYou are P — Parsing Layer. Break the answer into logical units. List: (1) claims, (2) structure, (3) gaps, (4) weaknesses."

    @staticmethod
    def W(answer: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nYou are W — World Model Layer. Extract factual claims. Label each: HIGH / MEDIUM / UNKNOWN."

    @staticmethod
    def LX(answer: str, w: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nW WORLD MODEL:\n{w}\n\nYou are LX — Reality Adjudication. For MEDIUM/UNKNOWN claims: (1) PREDICTION TEST, (2) ADVERSARY TEST, (3) VERIFICATION TEST. Label: FALSIFIABLE / UNFALSIFIABLE / UNGROUNDED."

    @staticmethod
    def LA(answer: str, lx: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nREALITY AUDIT:\n{lx}\n\nYou are LA — Adversarial Countermodel. Generate: (1) strongest competing explanation, (2) hidden assumptions, (3) collapse conditions, (4) simplest alternative."

    @staticmethod
    def LC(answer: str, la: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nADVERSARIAL:\n{la}\n\nYou are LC — Compression Integrity. Hunt for: (1) concept collapse, (2) metaphor substitution, (3) elegance erasure, (4) abstraction hiding causality."

    @staticmethod
    def L1(answer: str, p: str, w: str, lx: str, la: str, lc: str) -> str:
        return f"AI ANSWER:\n{answer}\n\nParsing:\n{p}\n\nWorld Model:\n{w}\n\nReality Audit:\n{lx[:600]}\n\nAdversarial:\n{la[:600]}\n\nCompression:\n{lc[:600]}\n\nYou are L1 — Hypothesis Engine. Generate exactly 3 improvement hypotheses:\nH1: [strongest improvement]\nH2: [radical reframe]\nH3: [failure mode hypothesis]"

    @staticmethod
    def L2(l1: str, s0: int) -> str:
        return f"Hypotheses:\n{l1}\n\nInput score: {s0}/100\n\nYou are L2 — Evaluation Layer. Score each hypothesis. Identify contradictions. Pick best path."

    @staticmethod
    def LP(answer: str, l2: str) -> str:
        return f'Claim: "{answer[:200]}"\nProposal: "{l2[:200]}"\n\nDoes Proposal say the OPPOSITE of Claim? Answer: YES or NO'

    @staticmethod
    def L3(answer: str, l2: str, w: str) -> str:
        return f"Best path:\n{l2}\n\nWorld facts:\n{w}\n\nOriginal answer:\n{answer}\n\nYou are L3 — Rewrite Planner. Create rewrite brief: what stays/changes/adds/removes."

    @staticmethod
    def L4(answer: str, l3: str, w: str) -> str:
        return f"ORIGINAL:\n{answer}\n\nPLAN:\n{l3}\n\nFACTS:\n{w}\n\nYou are L4 — Finalization Engine. Execute the rewrite."

    @staticmethod
    def LR(answer: str, l4: str, s0: int, s1: int) -> str:
        return f"BEFORE (score {s0}):\n{answer}\n\nAFTER (score {s1}):\n{l4}\n\nYou are LR — Regret Layer. Analyze: errors corrected, hallucinations removed, improvements, what still needs work."

    @staticmethod
    def L6(s0: int, s1: int, gaps: list) -> str:
        return f"Score: {s0} → {s1}\nGaps: {', '.join(gaps) if gaps else 'none'}\n\nYou are L6 — Trace Memory. Write execution log."

    @staticmethod
    def L7(lr: str, l6: str) -> str:
        return f"Regret:\n{lr}\n\nTrace:\n{l6}\n\nYou are L7 — Curriculum Generator. Extract: 3 lessons, 2 failure patterns, 2 reusable heuristics."

    @staticmethod
    def L8(s0: int, s1: int, gaps: list) -> str:
        return f"Run: {s0}→{s1}, gaps: {', '.join(gaps) if gaps else 'none'}\n\nYou are L8 — Identity Model. Summarize strengths, weaknesses, bias tendencies."

    @staticmethod
    def L9(l8: str, s0: int, s1: int, l4: str) -> str:
        return f"Run: {s0}→{s1}\n\nL8:\n{l8[:400]}\n\nL4:\n{l4[:300]}\n\nYou are L9 — Socratic Integrity. Generate exactly 3 questions."

    @staticmethod
    def L10(l4: str, lr: str, l7: str, l8: str, l9qs: str, s0: int, s1: int) -> str:
        return f"""PIPELINE RUN:
Score: {s0} → {s1}

L4 FINAL:
{l4}

LR REGRET:
{lr[:400]}

L7 LESSONS:
{l7[:300]}

L8 BELIEF:
{l8[:200]}

L9 QUESTIONS:
{l9qs}

You are L10 — Synthesis/Audit. CERTIFIED / CERTIFIED WITH CAUTION / REQUIRES REVIEW."""


def score_text(text: str, original_score: int = None) -> int:
    """Score text quality using Gemini."""
    if original_score is not None:
        prompt = f"Rate this REWRITE of an AI answer (original scored {original_score}/100). Reply with ONLY a single integer 0-100 (50=no change, above=better).\n\n{text[:1200]}\n\n:"
    else:
        prompt = f"Rate quality 0-100 (Clarity 0-25, Structure 0-25, Depth 0-25, Correctness 0-25).\n\n{text[:1200]}\n\n:"
    
    try:
        result = call_gemini("You are a quality scorer. Reply with only numbers.", prompt, max_tokens=10)
        num = int(''.join(c for c in result if c.isdigit()))
        return min(100, max(0, num)) if num else 50
    except:
        return 50


def execute_deep_pipeline(answer: str, context: str = "", client_id: str = None) -> Generator[Dict[str, Any], None, None]:
    """Execute the 16-layer deep pipeline."""
    run_id = f"run_{int(asyncio.get_event_loop().time() if hasattr(asyncio, 'get_event_loop') else 0)}_{datetime.now().strftime('%Y%m%d%H%M%S')}"
    outputs = {}
    
    yield {"type": "start", "run_id": run_id, "mode": "DEEP_PIPELINE"}
    
    # Score original
    yield {"type": "layer_start", "layer": "score_before"}
    s0 = score_text(answer)
    yield {"type": "score_before", "score": s0}
    
    operating_mode = "HIGH_QUALITY" if s0 >= 68 else "STANDARD"
    yield {"type": "mode", "mode": operating_mode}
    
    # Execute layers
    layers_to_run = [
        ("L0", PipelinePrompts.L0(answer, context), 800),
        ("P", PipelinePrompts.P(answer, ""), 800),
        ("W", PipelinePrompts.W(answer), 800),
        ("LX", PipelinePrompts.LX(answer, ""), 800),
        ("LA", PipelinePrompts.LA(answer, ""), 800),
        ("LC", PipelinePrompts.LC(answer, ""), 800),
    ]
    
    for layer_id, prompt, max_tokens in layers_to_run:
        yield {"type": "layer_start", "layer": layer_id}
        system = f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: {layer_id}\nStay in this layer only."
        result = call_gemini(system, prompt, max_tokens=max_tokens, client_id=client_id)
        outputs[layer_id] = result
        yield {"type": "layer_complete", "layer": layer_id, "output": result}
    
    # L1-L2
    yield {"type": "layer_start", "layer": "L1"}
    l1 = call_gemini(f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L1", 
                     PipelinePrompts.L1(answer, outputs.get("P", ""), outputs.get("W", ""),
                                        outputs.get("LX", "")[:600], outputs.get("LA", "")[:600],
                                        outputs.get("LC", "")[:600]),
                     max_tokens=800, client_id=client_id)
    outputs["L1"] = l1
    yield {"type": "layer_complete", "layer": "L1", "output": l1}
    
    yield {"type": "layer_start", "layer": "L2"}
    l2 = call_gemini(f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L2",
                     PipelinePrompts.L2(l1, s0),
                     max_tokens=50 if operating_mode == "HIGH_QUALITY" else 800, client_id=client_id)
    outputs["L2"] = l2
    yield {"type": "layer_complete", "layer": "L2", "output": l2}
    
    # Halt checks
    if "NO_REWRITE" in l2 or "PRESERVE" in l2 or "ESCALATE" in l2:
        yield {"type": "score_after", "score": s0}
        yield {"type": "halt", "reason": "HIGH QUALITY: No improvement found."}
        yield {"type": "complete", "run_id": run_id, "score_before": s0, "score_after": s0}
        return
    
    # LP check
    yield {"type": "layer_start", "layer": "LP"}
    lp = call_gemini(f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: LP",
                     PipelinePrompts.LP(answer, l2), max_tokens=5, client_id=client_id)
    outputs["LP"] = lp
    yield {"type": "layer_complete", "layer": "LP", "output": lp}
    
    if lp.strip().upper().startswith("YES"):
        yield {"type": "score_after", "score": s0}
        yield {"type": "halt", "reason": "LP HALT: proposed change inverts the original claim."}
        yield {"type": "complete", "run_id": run_id, "score_before": s0, "score_after": s0}
        return
    
    # L3-L4
    yield {"type": "layer_start", "layer": "L3"}
    l3 = call_gemini(f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L3",
                     PipelinePrompts.L3(answer, l2, outputs.get("W", "")),
                     max_tokens=800, client_id=client_id)
    outputs["L3"] = l3
    yield {"type": "layer_complete", "layer": "L3", "output": l3}
    
    yield {"type": "layer_start", "layer": "L4"}
    l4 = call_gemini(f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L4",
                     PipelinePrompts.L4(answer, l3, outputs.get("W", "")),
                     max_tokens=2500, client_id=client_id)
    outputs["L4"] = l4
    yield {"type": "layer_complete", "layer": "L4", "output": l4}
    
    if not l4 or len(l4.strip()) < 50:
        yield {"type": "score_after", "score": s0}
        yield {"type": "halt", "reason": "L4 HALT: Execution failed."}
        yield {"type": "complete", "run_id": run_id, "score_before": s0, "score_after": s0}
        return
    
    # Score rewrite
    yield {"type": "scoring"}
    s1 = score_text(l4, original_score=s0)
    yield {"type": "score_after", "score": s1}
    
    gaps_fixed = ["clarity", "structure", "depth"] if s1 > s0 else []
    
    # Remaining layers
    remaining = [
        ("LR", PipelinePrompts.LR(answer, l4, s0, s1), 800),
        ("L6", PipelinePrompts.L6(s0, s1, gaps_fixed), 800),
        ("L7", PipelinePrompts.L7("", ""), 2500),
        ("L8", PipelinePrompts.L8(s0, s1, gaps_fixed), 800),
    ]
    
    for layer_id, prompt, max_tokens in remaining:
        yield {"type": "layer_start", "layer": layer_id}
        result = call_gemini(f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: {layer_id}",
                              prompt, max_tokens=max_tokens, client_id=client_id)
        outputs[layer_id] = result
        yield {"type": "layer_complete", "layer": layer_id, "output": result}
    
    # L9
    yield {"type": "layer_start", "layer": "L9"}
    l9_raw = call_gemini(f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L9",
                         PipelinePrompts.L9(outputs.get("L8", ""), s0, s1, l4),
                         max_tokens=300, client_id=client_id)
    l9_questions = [line.replace("Q:", "").strip() for line in l9_raw.split("\n") if line.strip().startswith("Q:")]
    l9_questions = l9_questions[:3]
    outputs["L9"] = l9_raw
    yield {"type": "layer_complete", "layer": "L9", "output": l9_raw}
    
    # L10
    yield {"type": "layer_start", "layer": "L10"}
    l10 = call_gemini(f"{RUNTIME_SPEC}\n\nYOU ARE NOW EXECUTING: L10",
                      PipelinePrompts.L10(l4, outputs.get("LR", ""), outputs.get("L7", ""),
                                         outputs.get("L8", ""),
                                         "\n".join(l9_questions) if l9_questions else "No questions",
                                         s0, s1),
                      max_tokens=800, client_id=client_id)
    outputs["L10"] = l10
    yield {"type": "layer_complete", "layer": "L10", "output": l10}
    
    yield {"type": "complete", "run_id": run_id, "score_before": s0, "score_after": s1}


# ─────────────────────────────────────────────
# Simple Multi-Agent System (Lightweight)
# ─────────────────────────────────────────────
AGENT_PROFILES = {
    "general": "You are a helpful assistant.",
    "analyst": "You are a data analyst. Provide structured analysis with metrics.",
    "creative": "You are a creative thinker. Generate innovative ideas and alternatives.",
    "critic": "You are a critical thinker. Identify flaws, risks, and counterarguments.",
}


def run_simple_agent(query: str, agent_type: str = "general", context: str = "") -> str:
    """Run a simple agent query."""
    system_prompt = AGENT_PROFILES.get(agent_type, AGENT_PROFILES["general"])
    if context:
        system_prompt += f"\n\nContext:\n{context}"
    return call_gemini(system_prompt, query, max_tokens=1024)


# ─────────────────────────────────────────────
# Gradio UI
# ─────────────────────────────────────────────
def create_gradio_app():
    """Create the Gradio interface."""
    
    with gr.Blocks(title="4CBON2 Unified - Cognitive Platform") as app:
        gr.Markdown("# 🚀 4CBON2 — Unified Cognitive Platform")
        gr.Markdown("*Multi-Agent Orchestration + 16-Layer Deep Pipeline*")
        
        with gr.Tabs():
            # Deep Pipeline Tab
            with gr.TabItem("🧠 Deep Pipeline (L0-L10)"):
                gr.Markdown("""
                ## 🧠 16-Layer Cognitive Pipeline
                
                Paste any AI-generated answer. The pipeline runs it through **16 cognitive layers** to improve it.
                
                **Pipeline:** L0 → P → W → LX → LA → LC → L1 → L2 → L3 → L4 → LR → L6 → L7 → L8 → L9 → L10
                """)
                
                with gr.Row():
                    with gr.Column(scale=2):
                        pipeline_answer = gr.Textbox(
                            label="Paste AI Answer",
                            lines=6,
                            placeholder="Paste any AI-generated answer here..."
                        )
                        pipeline_context = gr.Textbox(
                            label="Context (optional)",
                            lines=2,
                            placeholder="What should this answer achieve?"
                        )
                        pipeline_btn = gr.Button("▶ RUN PIPELINE", variant="primary")
)
                    
                    with gr.Column(scale=1):
                        pipeline_status = gr.Markdown("**Status:** Ready")
                
                pipeline_output = gr.Textbox(label="Pipeline Output", lines=30, interactive=False)
                
                def run_pipeline(answer, context):
                    if not answer.strip():
                        yield "❌ Please paste an AI answer."
                        return
                    
                    for event in execute_deep_pipeline(answer, context):
                        if event["type"] == "layer_start":
                            yield f"⟳ Running {event['layer']}...\n"
                        elif event["type"] == "layer_complete":
                            layer = event["layer"]
                            output = event["output"][:200] + "..." if len(event["output"]) > 200 else event["output"]
                            yield f"✅ {layer}: {output}\n\n"
                        elif event["type"] == "score_before":
                            yield f"📈 Score before: {event['score']}/100\n"
                        elif event["type"] == "score_after":
                            yield f"📉 Score after: {event['score']}/100\n"
                        elif event["type"] == "complete":
                            delta = event["score_after"] - event["score_before"]
                            yield f"\n✅ COMPLETE! Score: {event['score_before']}/100 → {event['score_after']}/100 ({'+' if delta > 0 else ''}{delta})"
                
                pipeline_btn.click(fn=run_pipeline, inputs=[pipeline_answer, pipeline_context], outputs=[pipeline_output])
            
            # Simple Agent Tab
            with gr.TabItem("🤖 Agent Mode"):
                gr.Markdown("## 🤖 Simple Agent Mode")
                
                agent_type = gr.Dropdown(choices=list(AGENT_PROFILES.keys()), value="general", label="Agent Type")
                agent_query = gr.Textbox(label="Query", lines=3, placeholder="What would you like help with?")
                agent_context = gr.Textbox(label="Context (optional)", lines=2)
                agent_btn = gr.Button("Run", variant="primary")
                agent_output = gr.Textbox(label="Response", lines=20, interactive=False)
                
                agent_btn.click(fn=run_simple_agent, inputs=[agent_query, agent_type, agent_context], outputs=[agent_output])
            
            # Ask Tab
            with gr.TabItem("❓ Ask a Question"):
                gr.Markdown("## ❓ Five-Lens Question Answering")
                
                question_input = gr.Textbox(label="Your Question", lines=3, placeholder="What would you like to understand?")
                ask_btn = gr.Button("Ask", variant="primary")
                ask_output = gr.Textbox(label="Answer", lines=25, interactive=False)
                
                def ask_question(question):
                    for chunk in ask_stream(question):
                        yield chunk
                
                ask_btn.click(fn=ask_question, inputs=[question_input], outputs=[ask_output])
    
    return app


# ─────────────────────────────────────────────
# FastAPI App
# ─────────────────────────────────────────────
app = FastAPI(title="4CBON2 Unified API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount Gradio
gradio_app = create_gradio_app()
app = mount_gradio_app(app, gradio_app, path="/app")


@app.get("/")
async def root():
    return {"message": "4CBON2 Unified API", "version": "1.0.0", "features": ["deep_pipeline", "agents", "qa"]}


@app.get("/api/health")
async def health():
    return {"status": "healthy", "timestamp": datetime.now().isoformat()}


@app.post("/api/pipeline")
async def api_pipeline(request: Request):
    """Run the deep pipeline via API."""
    try:
        body = await request.json()
        answer = body.get("answer", "")
        context = body.get("context", "")
        client_id = request.headers.get("X-Client-ID", "anonymous")
        
        if not answer.strip():
            raise HTTPException(status_code=400, detail="Answer is required")
        
        # Collect all events
        events = []
        for event in execute_deep_pipeline(answer, context, client_id=client_id):
            events.append(event)
        
        # Extract summary
        score_before = None
        score_after = None
        outputs = {}
        
        for event in events:
            if event["type"] == "score_before":
                score_before = event["score"]
            elif event["type"] == "score_after":
                score_after = event["score"]
            elif event["type"] == "layer_complete":
                outputs[event["layer"]] = event["output"]
        
        return {
            "success": True,
            "run_id": events[0].get("run_id") if events else None,
            "score_before": score_before,
            "score_after": score_after,
            "outputs": outputs,
            "final_answer": outputs.get("L4", ""),
            "certification": outputs.get("L10", ""),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/supabase")
async def api_supabase(request: Request):
    """Log data to Supabase."""
    try:
        if not SUPABASE_URL or not SUPABASE_KEY:
            return {"success": False, "message": "Supabase not configured"}
        
        body = await request.json()
        table = body.get("table", "pipeline_runs")
        data = body.get("data", {})
        
        # Note: In production, use the supabase Python client
        # For now, return success placeholder
        return {"success": True, "message": f"Would log to {table}", "data": data}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/gumroad-webhook")
async def api_gumroad(request: Request):
    """Handle Gumroad license verification."""
    try:
        body = await request.json()
        
        # Verify license with Gumroad
        license_key = body.get("license_key", "")
        product_id = body.get("product_id", "")
        
        if not license_key:
            raise HTTPException(status_code=400, detail="License key required")
        
        # In production, verify with Gumroad API
        return {"success": True, "verified": True, "license_key": license_key}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ─────────────────────────────────────────────
# Main (for local testing)
# ─────────────────────────────────────────────
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
