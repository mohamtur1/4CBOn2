---
title: 4CBON2 — 12-Agent Cognitive Ecosystem (HuggingFace Edition)
emoji: 🧠
colorFrom: indigo
colorTo: purple
sdk: gradio
sdk_version: 5.50.0
app_file: app.py
pinned: false
---

# 🚀 4CBON2 — 12-Agent Cognitive Ecosystem (HuggingFace Edition)

A Hugging Face Space deployment of the **4CBON2** multi-agent orchestration platform, powered by
[HuggingFace Inference](https://huggingface.co/docs/inference-providers/index) (`huggingface_hub.InferenceClient`).

This Space is built from the `4CBOn2_HuggingFace.ipynb` notebook:
👉 https://github.com/mohamtur1/4CBOn2/blob/main/4CBOn2_HuggingFace.ipynb

## ✨ Features

| Tab | What it does |
| --- | --- |
| 📁 **Upload Documents** | Index `.txt`, `.pdf`, and `.docx` files into the ChromaDB knowledge base |
| ❓ **Ask a Question** | 5-lens RAG answers grounded in the knowledge base |
| 🤖 **Agent Mode** | Multi-agent orchestration with 12 specialist agents (Sales Qualification, Legal Document Intelligence, Competitive Intelligence, Customer Engagement, Content Strategy, Marketing Automation, Evidence Management, Scheduling, Legal Intake, Scientific Research, …) |
| 🔨 **Builder** | Automated notebook builder: reads `4CBOn2_HuggingFace.ipynb`, proposes changes, validates syntax, runs a 5-lens verdict, and applies approved changes with backups |
| 📊 **Data Dashboard** | Plotly visualizations of task memory (response length, subtask distribution, goal keywords) |
| 📊 **Agent Status** | Live view of agent conversation histories |

## 🔑 Usage

1. **Get a HuggingFace token** — create one at https://huggingface.co/settings/tokens (an Inference / read token is enough for most providers).
2. **Paste your `HF_TOKEN`** into the *HuggingFace Token (HF_TOKEN)* field in the **Agent Mode** or **Ask a Question** tab.
3. **Enter a goal** (e.g. *"Analyze our competitor positioning and recommend a content strategy"*) and hit **Run Orchestrator**.

The orchestrator:
1. Generates an execution plan (up to 12 subtasks) using the `New Autonomous Agent` profile.
2. Delegates each subtask to the best-matching specialist agent.
3. Runs tools (`web_search`, `read_file`, `query_database`, `save_note`, `get_datetime`, `http_request`, `read_csv`, `write_csv`, `generate_pdf`, `scrape_webpage`) as needed.
4. Synthesizes batch summaries into a final strategic report.

## 🧠 Model

Default model: **`mistralai/Mistral-7B-Instruct-v0.3`** (used via `huggingface_hub.InferenceClient`).

> 💡 You can swap the model by editing `HF_MODEL_NAME` at the top of `app.py`.

## ⚙️ Options

- **Use Only HuggingFace Inference API** *(checked by default)* — ignores all optional API keys.
- **Enable Additional APIs** — reveals optional key fields (`CALENDAR_API_KEY`, `CRM_API_KEY`, `COMM_API_KEY`, `VISION_API_KEY`, `DOCUSIGN_API_KEY`, `SOCIAL_SCRAPER_API_KEY`, `SEO_API_KEY`, `S3_VAULT_KEY`, `PUBMED_API_KEY`) that are injected into the environment for tool use.

## 🗂 Runtime data

All runtime artifacts (ChromaDB, SQLite task memory, agent DB, audit logs, exports, backups) are stored under `./data/` inside the Space container. Note that Space storage is ephemeral — data resets on restart. The curated knowledge base is rebuilt automatically on boot.

## 🔗 Links

- Source notebook: https://github.com/mohamtur1/4CBOn2/blob/main/4CBOn2_HuggingFace.ipynb
- Repository: https://github.com/mohamtur1/4CBOn2
