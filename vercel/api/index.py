"""
4CBON2 Vercel Serverless Entry Point
FastAPI app with Gradio interface for the 16-layer pipeline
"""
import os
import json
import asyncio
from typing import Optional
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import HTMLResponse, JSONResponse, StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
import gradio as gr

from app import (
    execute_pipeline, LAYERS, load_beliefs, load_recent_questions,
    check_run_limit, log_event, get_supabase, score_with_gemini, call_gemini
)

app = FastAPI(title="4CBON2 — 16-Layer Cognitive Pipeline")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "https://4cbon.com",
        "https://www.4cbon.com",
        "https://4cbon.vercel.app",
        "http://localhost:3000",
        "http://localhost:7860",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ═══════════════════════════════════════════════════════════
# LANDING PAGE (served at root)
# ═══════════════════════════════════════════════════════════
@app.get("/", response_class=HTMLResponse)
async def landing_page():
    landing_path = os.path.join(os.path.dirname(__file__), "..", "public", "index.html")
    try:
        with open(landing_path, "r") as f:
            return HTMLResponse(content=f.read(), status_code=200)
    except FileNotFoundError:
        return HTMLResponse(content="<h1>4CBON2</h1><p>Landing page not found.</p>", status_code=200)


# ═══════════════════════════════════════════════════════════
# PIPELINE API ENDPOINT
# ═══════════════════════════════════════════════════════════
@app.post("/api/pipeline")
async def run_pipeline(request: Request):
    """Run the 16-layer pipeline. Streams events as SSE."""
    try:
        body = await request.json()
    except:
        raise HTTPException(status_code=400, detail="Invalid JSON")
    
    answer = body.get("answer", "").strip()
    api_key = body.get("api_key", "").strip()
    context = body.get("context", "").strip()
    model = body.get("model", "gemini-2.0-flash")
    
    if not answer:
        raise HTTPException(status_code=400, detail="No answer provided")
    if not api_key:
        raise HTTPException(status_code=400, detail="Gemini API key required")
    
    # Get client IP for rate limiting
    client_ip = request.headers.get("x-forwarded-for", "").split(",")[0].strip() or request.client.host or "unknown"
    
    async def event_stream():
        for event in execute_pipeline(answer, api_key, context, model, client_ip):
            yield f"data: {json.dumps(event)}\n\n"
    
    return StreamingResponse(event_stream(), media_type="text/event-stream")


# ═══════════════════════════════════════════════════════════
# SUPABASE OPERATIONS API
# ═══════════════════════════════════════════════════════════
@app.post("/api/supabase")
async def supabase_operations(request: Request):
    """Handle Supabase read/write operations."""
    try:
        body = await request.json()
    except:
        raise HTTPException(status_code=400, detail="Invalid JSON")
    
    action = body.get("_action", "")
    
    if action == "get_beliefs":
        beliefs = load_beliefs()
        return JSONResponse({"beliefs": [{"belief": b} for b in beliefs]})
    
    elif action == "get_recent_questions":
        questions = load_recent_questions()
        return JSONResponse({"questions": [{"question_text": q} for q in questions]})
    
    elif action == "get_validated_critiques":
        from app import load_validated_critiques
        critiques = load_validated_critiques()
        return JSONResponse({"critiques": critiques})
    
    elif action == "save_feedback":
        sb = get_supabase()
        if not sb:
            return JSONResponse({"error": "Supabase not configured"})
        try:
            sb.table("feedback").insert({
                "evidence": body.get("evidence", ""),
                "confidence": body.get("confidence", 3),
                "critique_type": body.get("critique_type", "Factual"),
                "suggested_correction": body.get("suggested_correction", ""),
                "run_id": body.get("run_id", ""),
                "injected": False,
                "created_at": __import__("datetime").datetime.utcnow().isoformat()
            }).execute()
            return JSONResponse({"status": "saved"})
        except Exception as e:
            return JSONResponse({"error": str(e)})
    
    elif action == "check_run_limit":
        client_ip = request.headers.get("x-forwarded-for", "").split(",")[0].strip() or request.client.host or "unknown"
        limit = check_run_limit(client_ip)
        return JSONResponse(limit)
    
    elif action == "log_event":
        log_event(
            body.get("event_type", ""),
            body.get("details", {}),
            body.get("run_id")
        )
        return JSONResponse({"status": "logged"})
    
    return JSONResponse({"error": f"Unknown action: {action}"})


# ═══════════════════════════════════════════════════════════
# GRADIO INTERFACE (mounted at /app)
# ═══════════════════════════════════════════════════════════
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "mohamtur1@gmail.com")

def create_gradio_app():
    """Create the Gradio interface for the pipeline."""
    
    with gr.Blocks(
        title="4CBON2 — 16-Layer Cognitive Pipeline",
        theme=gr.themes.Base(
            primary_hue="orange",
            secondary_hue="blue",
        ),
        css="""
        .layer-card { border-left: 3px solid #ff6b35; padding: 12px; margin: 8px 0; background: #06060f; border-radius: 8px; }
        .score-bar { height: 6px; border-radius: 3px; transition: width 0.8s ease; }
        .pipeline-bar { display: flex; gap: 4px; flex-wrap: wrap; margin: 16px 0; }
        .layer-badge { font-family: monospace; font-size: 9px; padding: 4px 8px; border-radius: 4px; }
        footer { display: none !important; }
        .gradio-container { max-width: 720px !important; margin: 0 auto !important; }
        """
    ) as demo:
        gr.Markdown("# 🧠 4CBON2 — 16-Layer Cognitive Pipeline")
        gr.Markdown("*Paste any AI answer. The pipeline runs 16 cognitive layers to measurably improve it.*")
        
        with gr.Row():
            api_key = gr.Textbox(
                label="🔑 Gemini API Key",
                placeholder="Enter your Gemini API key (used in-memory only, never stored)",
                type="password",
                info="Get a free key at https://aistudio.google.com/apikey"
            )
            model_select = gr.Dropdown(
                choices=["gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-pro"],
                value="gemini-2.0-flash",
                label="Model"
            )
        
        answer_input = gr.Textbox(
            label="Paste AI Answer",
            placeholder="Paste any AI-generated answer here...",
            lines=6
        )
        
        context_input = gr.Textbox(
            label="Context (optional)",
            placeholder="What should this answer achieve? (leave blank to auto-detect)",
            lines=2
        )
        
        with gr.Row():
            run_btn = gr.Button("▶ RUN PIPELINE", variant="primary", scale=2)
            stop_btn = gr.Button("✕ STOP", variant="stop", scale=1, visible=False)
        
        # Score display
        score_display = gr.Markdown(visible=False)
        
        # Pipeline status
        pipeline_status = gr.Markdown(visible=False)
        
        # Layer outputs
        layer_outputs = {}
        for layer in LAYERS:
            with gr.Accordion(
                f"{layer['emoji']} {layer['id']} — {layer['name']}",
                open=layer.get("final", False),
                visible=False
            ) as acc:
                layer_outputs[layer["id"]] = {
                    "accordion": acc,
                    "output": gr.Textbox(
                        label=f"{layer['id']} Output",
                        lines=8 if layer.get("final") else 5,
                        interactive=False,
                        show_copy_button=True
                    )
                }
        
        # Status
        status_box = gr.Textbox(label="Status", interactive=False, visible=False)
        
        # Email for admin check
        email_input = gr.Textbox(
            label="Email (for admin access)",
            placeholder="Enter your email",
            visible=False
        )
        
        def run_pipeline_gradio(answer, api_key_val, context, model):
            """Run pipeline and yield updates."""
            if not answer or not answer.strip():
                yield {status_box: gr.update(value="❌ Please paste an AI answer.", visible=True)}
                return
            if not api_key_val or not api_key_val.strip():
                yield {status_box: gr.update(value="❌ Gemini API key required.", visible=True)}
                return
            
            yield {
                run_btn: gr.update(visible=False),
                stop_btn: gr.update(visible=True),
                status_box: gr.update(value="🚀 Pipeline running...", visible=True),
                score_display: gr.update(visible=True),
                pipeline_status: gr.update(visible=True, value=""),
            }
            
            # Make all layer accordions visible
            updates = {}
            for lid, ldata in layer_outputs.items():
                updates[ldata["accordion"]] = gr.update(visible=True)
                updates[ldata["output"]] = gr.update(value="")
            yield updates
            
            import requests as req
            # Call our own API endpoint
            try:
                # Use the pipeline generator directly
                for event in execute_pipeline(answer, api_key_val, context, model, "gradio_user"):
                    if event["type"] == "score_before":
                        yield {score_display: gr.update(value=f"**Score:** {event['score']}/100 → ...")}
                    elif event["type"] == "score_after":
                        # Get the before score from the display
                        yield {score_display: gr.update(value=f"**Score:** → {event['score']}/100")}
                    elif event["type"] == "layer_start":
                        layer_id = event["layer"]
                        if layer_id in layer_outputs:
                            yield {pipeline_status: gr.update(value=f"⟳ Running {layer_id}...")}
                    elif event["type"] == "layer_complete":
                        layer_id = event["layer"]
                        if layer_id in layer_outputs:
                            yield {layer_outputs[layer_id]["output"]: gr.update(value=event["output"])}
                    elif event["type"] == "halt":
                        yield {
                            status_box: gr.update(value=f"⚠️ {event['reason']}"),
                            run_btn: gr.update(visible=True),
                            stop_btn: gr.update(visible=False),
                        }
                    elif event["type"] == "error":
                        if event["message"] == "DAILY_LIMIT_REACHED":
                            yield {
                                status_box: gr.update(value="🔒 You've used your 3 free runs today. [Upgrade to Pro →](https://4175358678144.gumroad.com/l/tbphpi)"),
                                run_btn: gr.update(visible=True),
                                stop_btn: gr.update(visible=False),
                            }
                        else:
                            yield {status_box: gr.update(value=f"❌ {event['message']}")}
                    elif event["type"] == "complete":
                        s0 = event.get("score_before", "?")
                        s1 = event.get("score_after", "?")
                        delta = (s1 - s0) if isinstance(s0, int) and isinstance(s1, int) else "?"
                        yield {
                            score_display: gr.update(value=f"**Score:** {s0}/100 → {s1}/100 ({'+' if isinstance(delta, int) and delta > 0 else ''}{delta})"),
                            status_box: gr.update(value=f"✅ Pipeline complete! Run ID: {event.get('run_id', 'N/A')}"),
                            run_btn: gr.update(visible=True),
                            stop_btn: gr.update(visible=False),
                        }
                    elif event["type"] == "memory":
                        yield {pipeline_status: gr.update(value=event["status"])}
            except Exception as e:
                yield {
                    status_box: gr.update(value=f"❌ Error: {str(e)}"),
                    run_btn: gr.update(visible=True),
                    stop_btn: gr.update(visible=False),
                }
        
        run_btn.click(
            fn=run_pipeline_gradio,
            inputs=[answer_input, api_key, context_input, model_select],
            outputs=[run_btn, stop_btn, status_box, score_display, pipeline_status] + 
                    [ldata["accordion"] for ldata in layer_outputs.values()] +
                    [ldata["output"] for ldata in layer_outputs.values()]
        )
    
    return demo

# Mount Gradio app
gradio_app = create_gradio_app()
app = gr.mount_gradio_app(app, gradio_app, path="/app")


# ═══════════════════════════════════════════════════════════
# HEALTH CHECK
# ═══════════════════════════════════════════════════════════
@app.get("/api/health")
async def health_check():
    return {"status": "ok", "service": "4CBON2", "layers": len(LAYERS)}
