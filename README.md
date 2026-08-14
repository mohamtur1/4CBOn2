# 4CBON2 — Unified Cognitive Platform

> **One codebase** that works in both **Colab** (development) and **Vercel** (production).

---

## What's Included

| Feature | Description |
|---------|-------------|
| **12 Specialist Agents** | Sales, Legal, Marketing, Research, and more |
| **16-Layer Deep Pipeline** | L0 → P → W → LX → LA → LC → L1 → L2 → L3 → L4 → LR → L6 → L7 → L8 → L9 → L10 |
| **RAG Knowledge Base** | Upload documents, ask questions with 5-lens framework |
| **Agent Builder** | AI proposes changes, you approve, it builds |
| **Data Dashboard** | Visualize task history with Plotly charts |
| **Gumroad Integration** | License key verification |
| **Supabase Logging** | Track pipeline runs, usage analytics |

---

## Quick Start

### Option 1: Google Colab (Recommended for Development)

1. Open `4CBON2_Unified.ipynb` in Google Colab
2. Run Cell 1 to install dependencies
3. Run Cell 2 — if using Colab Mode, no API key needed!
4. Mount Google Drive (Cell 3) for persistence
5. Run through all remaining cells
6. The Gradio interface will launch with a public link

### Option 2: Local Development

```bash
# Clone the repository
git clone https://github.com/mohamtur1/4CBON2.git
cd 4CBON2

# Install dependencies
pip install gradio chromadb PyPDF2 python-docx duckduckgo-search \
  beautifulsoup4 fpdf2 sentence-transformers plotly google-generativeai supabase

# Set your API key
export GEMINI_API_KEY="your-key-here"

# Run the app
python app.py
```

### Option 3: Vercel Deployment

1. Push to GitHub
2. Connect to Vercel
3. Set environment variables:
   - `GEMINI_API_KEY` (optional — clients can bring their own)
   - `SUPABASE_URL`
   - `SUPABASE_SERVICE_KEY`
   - `GUMROAD_API_KEY`
4. Deploy!

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     UNIFIED CODEBASE                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   COLAB     │  │   VERCEL    │  │     API ENDPOINTS   │  │
│  │  Interface  │  │   FastAPI   │  │  /api/pipeline      │  │
│  │  Gradio UI  │  │  + Gradio   │  │  /api/supabase      │  │
│  └─────────────┘  └─────────────┘  │  /api/gumroad-webhook│  │
│                                     └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    SHARED CORE LOGIC                         │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐  │
│  │ 12 Agents    │ │ 16-Layer     │ │ RAG + ChromaDB     │  │
│  │ Profiles     │ │ Pipeline     │ │ Knowledge Base     │  │
│  └──────────────┘ └──────────────┘ └────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    DATA PERSISTENCE                          │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐  │
│  │ Google Drive  │ │ Supabase     │ │ Local/SQLite       │  │
│  │ (Colab)       │ │ (Production) │ │ (Agent DB)         │  │
│  └──────────────┘ └──────────────┘ └────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## The 16-Layer Pipeline

| Layer | Name | Purpose |
|-------|------|---------|
| **L0** | Interpretation Engine | Understand intent, extract task type |
| **P** | Parsing Layer | Break into logical units, identify gaps |
| **W** | World Model Layer | Extract claims, label certainty |
| **LX** | Reality Adjudication | Apply falsification tests |
| **LA** | Adversarial Countermodel | Attack the answer |
| **LC** | Compression Integrity | Hunt semantic smoothing |
| **L1** | Hypothesis Engine | Generate improvement paths |
| **L2** | Evaluation Layer | Score and select best path |
| **LP** | Policy Translation | Check for contradictions |
| **L3** | Rewrite Planner | Plan precise changes |
| **L4** | Finalization Engine | Execute the rewrite |
| **LR** | Regret Layer | Analyze improvement delta |
| **L6** | Trace Memory | Store execution log |
| **L7** | Curriculum Generator | Extract lessons |
| **L8** | Identity Model | Summarize system behavior |
| **L9** | Socratic Integrity | Generate self-questions |
| **L10** | Synthesis/Audit | Final certification |

---

## Files

| File | Purpose |
|------|---------|
| `4CBON2_Unified.ipynb` | Complete Colab notebook with all features |
| `vercel/app.py` | Vercel/FastAPI application |
| `vercel/requirements.txt` | Python dependencies |
| `vercel/supabase_schema.sql` | Database schema |
| `vercel/public/index.html` | Landing page with 3 free tests |

---

## Supabase Setup

1. Create a new Supabase project
2. Run `vercel/supabase_schema.sql` in the SQL Editor
3. Copy the URL and service key to Vercel environment variables

---

## Gumroad Integration

1. Get your Gumroad API key
2. Set `GUMROAD_API_KEY` in Vercel
3. The `/api/gumroad-webhook` endpoint handles license verification

---

## Privacy

- **API keys stay in memory only** — never stored to disk
- **No user data persistence** on the server
- Clients bring their own keys
- Supabase stores only anonymized usage metrics

---

## License

MIT License — see LICENSE file

---

## Support

- Open an issue on GitHub
- Join the community Discord
- Read the documentation

---

*Built with ❤️ by the 4CBON2 team*
