# 4CBON2 — 16-Layer Cognitive Pipeline (Vercel Deployment)

Production-ready deployment of the 4CBON2 cognitive pipeline on Vercel, featuring:
- **16-layer cognitive architecture** ported from React/Claude to Python/Gemini
- **Zero-configuration** — clients bring their own Gemini API key
- **Supabase integration** for event logging, beliefs, questions, and feedback
- **Gumroad webhook** for subscription management
- **Landing page** with 3 free tests per day
- **Gradio interface** for interactive pipeline execution

## Architecture

```
/api/
  index.py              # FastAPI + Gradio app (main entry point)
  gumroad-webhook.py    # Gumroad subscription webhook handler
/app.py                 # Core 16-layer pipeline logic (Gemini API)
/public/
  index.html            # Landing page with pricing and features
/requirements.txt       # Python dependencies
/vercel.json           # Vercel deployment configuration
```

## 16-Layer Pipeline

The pipeline processes AI-generated answers through 16 cognitive layers:

1. **L0** — Interpretation Engine
2. **P** — Parsing Layer
3. **W** — World Model Layer
4. **LX** — Reality Adjudication
5. **LA** — Adversarial Countermodel
6. **LC** — Compression Integrity
7. **L1** — Hypothesis Engine
8. **L2** — Evaluation Layer
9. **LP** — Policy Translation
10. **L3** — Rewrite Planner
11. **L4** — Finalization Engine (★ Final Rewrite)
12. **LR** — Regret Layer
13. **L6** — Trace Memory
14. **L7** — Curriculum Generator
15. **L8** — Identity Model
16. **L9** — Socratic Integrity Engine
17. **L10** — Synthesis/Audit

Each layer has a specific cognitive role and produces measurable artifacts.

## Deployment Instructions

### Prerequisites

1. **Vercel Account** — Sign up at https://vercel.com
2. **Supabase Project** — Create at https://supabase.com
3. **Gumroad Product** — Already exists at https://4175358678144.gumroad.com/l/tbphpi
4. **Google AI Studio API Key** — For testing (clients will bring their own)

### Step 1: Set Up Supabase Tables

Run these SQL commands in your Supabase SQL Editor:

```sql
-- Event log (append-only, immutable runtime facts)
CREATE TABLE IF NOT EXISTS event_log (
  id BIGSERIAL PRIMARY KEY,
  event_type TEXT NOT NULL,
  details JSONB,
  run_id TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Beliefs (L8 self-beliefs, persistent across runs)
CREATE TABLE IF NOT EXISTS beliefs (
  id BIGSERIAL PRIMARY KEY,
  belief TEXT NOT NULL,
  score_before INTEGER,
  score_after INTEGER,
  run_number INTEGER,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Questions (L9 self-questions, injected into next run)
CREATE TABLE IF NOT EXISTS questions (
  id BIGSERIAL PRIMARY KEY,
  run_id TEXT,
  question_text TEXT NOT NULL,
  question_level INTEGER,
  question_type TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Feedback (human-submitted critiques)
CREATE TABLE IF NOT EXISTS feedback (
  id BIGSERIAL PRIMARY KEY,
  evidence TEXT NOT NULL,
  confidence INTEGER CHECK (confidence >= 1 AND confidence <= 5),
  critique_type TEXT CHECK (critique_type IN ('Factual', 'Stylistic', 'Uncertain')),
  suggested_correction TEXT,
  run_id TEXT,
  injected BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Run limits (3 free runs per day per IP)
CREATE TABLE IF NOT EXISTS run_limits (
  id BIGSERIAL PRIMARY KEY,
  ip TEXT NOT NULL,
  run_date DATE NOT NULL,
  run_count INTEGER DEFAULT 0,
  UNIQUE(ip, run_date)
);

-- Subscriptions (Gumroad webhook updates)
CREATE TABLE IF NOT EXISTS subscriptions (
  id BIGSERIAL PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  subscription_id TEXT,
  product_name TEXT,
  status TEXT,
  sale_id TEXT,
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Row-Level Security (RLS)
ALTER TABLE event_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE beliefs ENABLE ROW LEVEL SECURITY;
ALTER TABLE questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE run_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;

-- Policies (read-only for anon, full access for service_role)
CREATE POLICY "Allow anonymous read" ON event_log FOR SELECT USING (true);
CREATE POLICY "Allow anonymous read" ON beliefs FOR SELECT USING (true);
CREATE POLICY "Allow anonymous read" ON questions FOR SELECT USING (true);
CREATE POLICY "Allow anonymous read" ON feedback FOR SELECT USING (true);
CREATE POLICY "Allow anonymous read" ON run_limits FOR SELECT USING (true);
CREATE POLICY "Allow anonymous read" ON subscriptions FOR SELECT USING (true);
```

### Step 2: Get Supabase Credentials

1. Go to your Supabase project → Settings → API
2. Copy:
   - **Project URL** (e.g., `https://xxxxx.supabase.co`)
   - **anon/public key** (starts with `eyJ...`)
   - **service_role key** (starts with `eyJ...`, keep secret!)

### Step 3: Generate Gumroad Webhook Secret

Create a random secret for webhook verification:

```bash
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
```

Save this value — you'll need it for both Vercel env vars and Gumroad webhook URL.

### Step 4: Deploy to Vercel

#### Option A: Deploy via Vercel CLI (Recommended)

```bash
# Install Vercel CLI
npm i -g vercel

# Login to Vercel
vercel login

# Navigate to project
cd /path/to/4cbon2-vercel

# Deploy (first time — creates new project)
vercel

# Set environment variables
vercel env add SUPABASE_URL production
# Paste: https://xxxxx.supabase.co

vercel env add SUPABASE_SERVICE_ROLE_KEY production
# Paste: your service_role key

vercel env add GUMROAD_WEBHOOK_SECRET production
# Paste: the random secret from Step 3

vercel env add ADMIN_EMAIL production
# Paste: mohamtur1@gmail.com

# Deploy to production
vercel --prod
```

#### Option B: Deploy via Vercel Dashboard

1. Go to https://vercel.com/new
2. Import the Git repository (or upload the project folder)
3. Configure environment variables:
   - `SUPABASE_URL` — Your Supabase project URL
   - `SUPABASE_SERVICE_ROLE_KEY` — Your Supabase service role key
   - `GUMROAD_WEBHOOK_SECRET` — Random secret from Step 3
   - `ADMIN_EMAIL` — `mohamtur1@gmail.com`
4. Click "Deploy"

### Step 5: Configure Gumroad Webhook

1. Go to your Gumroad product settings: https://gumroad.com/products/tbphpi/edit
2. Scroll to "Webhooks" section
3. Add webhook URL:
   ```
   https://your-project.vercel.app/api/gumroad-webhook?secret=YOUR_SECRET_HERE
   ```
   (Replace `YOUR_SECRET_HERE` with the secret from Step 3)
4. Save changes

### Step 6: Test the Deployment

1. Visit your Vercel URL (e.g., `https://4cbon2.vercel.app`)
2. You should see the landing page
3. Click "Try free (3 runs/day)" → redirects to `/app`
4. Enter your Gemini API key (get one at https://aistudio.google.com/apikey)
5. Paste an AI-generated answer
6. Click "▶ RUN PIPELINE"
7. Watch all 16 layers execute in sequence

### Step 7: Update DNS (After Testing)

Once you've verified the new deployment works:

1. Go to your domain registrar (where `4cbon.com` is registered)
2. Update DNS to point to the new Vercel project
3. Or use Vercel's domain management: https://vercel.com/docs/concepts/projects/domains

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `SUPABASE_URL` | Supabase project URL | Yes |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase service role key (secret!) | Yes |
| `GUMROAD_WEBHOOK_SECRET` | Random secret for webhook verification | Yes |
| `ADMIN_EMAIL` | Admin email for Agent Builder access | No (defaults to `mohamtur1@gmail.com`) |

## API Endpoints

### `POST /api/pipeline`
Run the 16-layer pipeline. Streams events as Server-Sent Events (SSE).

**Request:**
```json
{
  "answer": "Paste AI-generated answer here...",
  "api_key": "Your Gemini API key",
  "context": "Optional context/goal",
  "model": "gemini-2.0-flash"
}
```

**Response:** SSE stream with events:
- `{"type": "start", "run_id": "..."}`
- `{"type": "layer_start", "layer": "L0"}`
- `{"type": "layer_complete", "layer": "L0", "output": "..."}`
- `{"type": "score_before", "score": 62}`
- `{"type": "score_after", "score": 72}`
- `{"type": "complete", "run_id": "...", "score_before": 62, "score_after": 72}`

### `POST /api/supabase`
Supabase operations (beliefs, questions, feedback, run limits).

**Request:**
```json
{
  "_action": "get_beliefs" | "get_recent_questions" | "save_feedback" | "check_run_limit" | "log_event",
  ...
}
```

### `POST /api/gumroad-webhook?secret=YOUR_SECRET`
Gumroad subscription webhook handler.

**Events handled:**
- `sale` — New subscription
- `subscription_updated` — Plan change
- `subscription_cancelled` — Cancellation
- `subscription_restarted` — Reactivation

### `GET /app`
Gradio interface for interactive pipeline execution.

### `GET /`
Landing page with pricing and features.

## Security Notes

1. **Gemini API keys are never stored** — used in-memory only, never logged or persisted
2. **Supabase service_role key** — keep secret, only use in Vercel env vars
3. **Gumroad webhook secret** — verify on every webhook request
4. **Row-Level Security (RLS)** — enabled on all Supabase tables
5. **Rate limiting** — 3 free runs per day per IP (tracked server-side)

## Features

### Landing Page
- Beautiful dark theme with gradient accents
- Pricing section (Free vs Pro)
- Feature highlights
- 16-layer pipeline visualization
- Cross-session memory explanation
- Use cases section
- Gumroad upgrade links

### Gradio App
- Gemini API key input (in-memory only)
- Model selection (gemini-2.0-flash, gemini-2.5-flash, gemini-2.5-pro)
- Answer input with context field
- Real-time layer execution display
- Score trajectory visualization
- Layer output accordions with copy buttons
- Status messages and error handling

### Pipeline Features
- **Scoring** — 3-call median scoring for reliability
- **Memory** — Beliefs and questions persist across runs
- **Feedback** — Human-submitted critiques improve future runs
- **Rate limiting** — 3 free runs/day per IP
- **Error handling** — Graceful degradation on API failures
- **Logging** — All events logged to Supabase

## Local Development

```bash
# Install dependencies
pip install -r requirements.txt

# Set environment variables
export SUPABASE_URL="https://xxxxx.supabase.co"
export SUPABASE_SERVICE_ROLE_KEY="your-key"
export GUMROAD_WEBHOOK_SECRET="your-secret"

# Run locally
uvicorn api.index:app --reload --port 8000

# Visit http://localhost:8000
```

## Troubleshooting

### "Module not found" errors
```bash
pip install -r requirements.txt --upgrade
```

### Supabase connection errors
- Verify `SUPABASE_URL` is correct (includes `https://`)
- Verify `SUPABASE_SERVICE_ROLE_KEY` is the service role key (not anon key)
- Check RLS policies are enabled

### Gumroad webhook not working
- Verify webhook URL includes `?secret=YOUR_SECRET`
- Check Vercel logs: `vercel logs your-deployment-url`
- Verify `GUMROAD_WEBHOOK_SECRET` matches in both Vercel and Gumroad

### Pipeline hangs or times out
- Gemini API has rate limits — check your quota
- Vercel serverless functions have 10s timeout by default
- Consider upgrading to Vercel Pro for longer timeouts

## License

Proprietary — 4CBON2 © 2026

## Support

For issues or questions:
- Email: mohamtur1@gmail.com
- Gumroad: https://4175358678144.gumroad.com/l/tbphpi

---

**Built with:**
- FastAPI + Gradio (Python backend)
- Google Gemini API (LLM inference)
- Supabase (database + auth)
- Vercel (serverless hosting)
- Gumroad (subscriptions)
