"""
Gumroad Webhook Handler for 4CBON2
Handles subscription events: sale, subscription_updated, subscription_cancelled, subscription_restarted
"""
import os
import json
import hashlib
from datetime import datetime
from fastapi import FastAPI, Request, HTTPException, Query
from fastapi.responses import JSONResponse

app = FastAPI()

GUMROAD_WEBHOOK_SECRET = os.environ.get("GUMROAD_WEBHOOK_SECRET", "")

# Import Supabase client from main app
import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from app import get_supabase, log_event


@app.post("/api/gumroad-webhook")
async def gumroad_webhook(request: Request, secret: str = Query(None)):
    """Handle Gumroad webhook events."""
    
    # Verify secret
    if not GUMROAD_WEBHOOK_SECRET:
        raise HTTPException(status_code=500, detail="Webhook secret not configured")
    
    if secret != GUMROAD_WEBHOOK_SECRET:
        raise HTTPException(status_code=401, detail="Invalid webhook secret")
    
    # Parse form data (Gumroad sends form-encoded data)
    try:
        form = await request.form()
        data = dict(form)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to parse webhook data: {e}")
    
    # Gumroad sends form-encoded fields. Depending on the webhook version,
    # the event is either explicit or represented by a subscription_status.
    event_type = (data.get("event_type") or data.get("resource_type") or "").strip().lower()
    if event_type not in {"sale", "subscription_updated", "subscription_cancelled", "subscription_restarted"}:
        status_hint = (data.get("subscription_status") or "").lower()
        event_type = {
            "cancelled": "subscription_cancelled",
            "canceled": "subscription_cancelled",
            "restarted": "subscription_restarted",
            "active": "subscription_updated",
        }.get(status_hint, "sale" if data.get("sale_id") else "unknown")
    buyer_email = data.get("email", "").strip()
    subscription_id = data.get("subscription_id", "")
    product_name = data.get("product_name", "")
    sale_id = data.get("sale_id", "")
    
    # Log the event
    log_event("GUMROAD_WEBHOOK", {
        "event_type": event_type,
        "buyer_email": buyer_email,
        "subscription_id": subscription_id,
        "product_name": product_name,
        "sale_id": sale_id,
        "timestamp": datetime.utcnow().isoformat()
    })
    
    # Update Supabase
    sb = get_supabase()
    if sb and buyer_email:
        try:
            # Determine subscription status
            if "subscription_cancelled" in str(data):
                status = "cancelled"
            elif "subscription_restarted" in str(data):
                status = "active"
            elif "subscription_updated" in str(data):
                status = "active"
            elif sale_id:
                status = "active"  # New sale
            else:
                status = "unknown"
            
            # Upsert subscription record
            sb.table("subscriptions").upsert({
                "email": buyer_email,
                "subscription_id": subscription_id,
                "product_name": product_name,
                "status": status,
                "sale_id": sale_id,
                "updated_at": datetime.utcnow().isoformat()
            }, on_conflict="email").execute()
            
        except Exception as e:
            # Table might not exist yet — that's ok
            print(f"Supabase subscription update error: {e}")
    
    return JSONResponse({"status": "ok", "event": event_type})


@app.get("/api/gumroad-webhook/health")
async def webhook_health():
    return {"status": "ok", "endpoint": "/api/gumroad-webhook"}
