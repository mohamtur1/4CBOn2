"""
4CBON2 — 12-Agent Cognitive Ecosystem (HuggingFace Edition)
Hugging Face Space deployment.

Built from 4CBOn2_HuggingFace.ipynb (https://github.com/mohamtur1/4CBOn2/blob/main/4CBOn2_HuggingFace.ipynb)
Adapted for Spaces: no Colab/Drive dependencies; all runtime data is stored
under ./data/ inside the Space container.
"""
import os
os.makedirs('./data', exist_ok=True)

# ============================================================
# CELL 1 — Environment Setup (HuggingFace Edition — huggingface_hub)
# ============================================================



import gradio as gr
import chromadb
from chromadb.config import Settings
from huggingface_hub import InferenceClient
import os
import json
import re
import sqlite3
import time
import requests
import csv
from datetime import datetime
from PyPDF2 import PdfReader
import docx
from duckduckgo_search import DDGS
from bs4 import BeautifulSoup
from fpdf import FPDF
import plotly.express as px
import plotly.graph_objects as go

print("✅ Environment ready.")

# Default HuggingFace model
HF_MODEL_NAME = 'mistralai/Mistral-7B-Instruct-v0.3'
print(f"✅ HuggingFace Inference ready. Default model: {HF_MODEL_NAME}")

# Note: API keys (HF_TOKEN, etc.) must be provided by the user via the interface below.

# ============================================================
# CELL 2 — Core LLM (HuggingFace — huggingface_hub.InferenceClient)
# ============================================================

HF_MODEL_NAME = 'mistralai/Mistral-7B-Instruct-v0.3'
hf_client = None
HF_TOKEN = None

def init_client(hf_token: str, model_name: str = 'mistralai/Mistral-7B-Instruct-v0.3'):
    global hf_client, HF_TOKEN, HF_MODEL_NAME
    if not hf_token or not hf_token.strip():
        return '⚠️ No HuggingFace token provided. Please enter your HF_TOKEN.'
    HF_TOKEN = hf_token.strip()
    HF_MODEL_NAME = model_name
    try:
        hf_client = InferenceClient(token=HF_TOKEN)
        return f'✅ HuggingFace client ready. Model: {HF_MODEL_NAME}'
    except Exception as e:
        return f'⚠️ HF client init error: {str(e)}'

def generate_text(prompt, max_tokens=2048, temperature=0.7, stream=False):
    """Generate text using HuggingFace InferenceClient."""
    if hf_client is None:
        return '⚠️ Error: HuggingFace client not initialized. Provide HF_TOKEN via the interface.'
    try:
        if stream:
            full_response = []
            for chunk in hf_client.chat_completion(
                messages=[{'role': 'user', 'content': prompt}],
                model=HF_MODEL_NAME,
                max_tokens=max_tokens,
                temperature=temperature,
                stream=True,
            ):
                delta = chunk.choices[0].delta.content if chunk.choices else None
                if delta:
                    full_response.append(delta)
            return ''.join(full_response)
        else:
            response = hf_client.chat_completion(
                messages=[{'role': 'user', 'content': prompt}],
                model=HF_MODEL_NAME,
                max_tokens=max_tokens,
                temperature=temperature,
                stream=False,
            )
            content = response.choices[0].message.content if response.choices else ''
            return content
    except Exception as e:
        return f'⚠️ API Error: {str(e)}'

def ask_raw(prompt, max_tokens=2048):
    return generate_text(prompt, max_tokens=max_tokens, temperature=0.1, stream=False)

def safe_ask_raw(prompt, max_tokens=2048):
    try:
        result = ask_raw(prompt, max_tokens=max_tokens)
        if not result or not result.strip():
            return '{"error": "Empty response from LLM. Please check your HF token or reduce prompt size."}'
        if hasattr(result, '__iter__') and not isinstance(result, (str, dict, list)):
            result = "".join(list(result))
        if not isinstance(result, str):
            result = str(result)
        return result.strip()
    except Exception as e:
        return f'{"error": "safe_ask_raw failed: {str(e)}"}'

def ask_stream(question, context=None):
    """Stream a five-lens answer."""
    prompt_template = """You are an expert on consciousness, neuroscience, and philosophy of mind.\nUse the provided information to answer the question using the five lenses below.\n{context_prefix}QUESTION: {question}\n\n1. ANALOGICAL — Compare this to similar known phenomena, systems, or experiences. What is this question like? Draw meaningful parallels.\n2. INDUCTIVE — What patterns emerge from the evidence and context? What general principles or trends can we infer?\n3. CRITICAL — What are the limitations, gaps, contradictions, or alternative viewpoints? What might skeptics argue?\n4. RESOLUTION — How do we reconcile conflicting perspectives? What synthesis or balanced conclusion emerges?\n5. FINAL ANSWER — A clear, direct, well-reasoned answer to the original question, grounded in the analysis above.\n\nUse clear headers for each section."""
    context_prefix = ""
    if context:
        context_prefix = f"Here is some relevant information:\n\n{context}\n\n"
    formatted_prompt = prompt_template.format(question=question, context_prefix=context_prefix)
    full_text = generate_text(formatted_prompt, max_tokens=2048, temperature=0.7, stream=False)
    if full_text.startswith('⚠️'):
        yield full_text
        return
    words = full_text.split()
    chunk = ''
    for i, word in enumerate(words):
        chunk += word + ' '
        if (i + 1) % 5 == 0 or i == len(words) - 1:
            yield chunk
            chunk = ''

def ask(question, context=None):
    """Get complete answer (non-streaming aggregation)."""
    full_text = ""
    for chunk in ask_stream(question, context=context):
        full_text += chunk
    return full_text

print(f"✅ Cell 2 ready. Model: {HF_MODEL_NAME}")
print("Using HuggingFace InferenceClient — user must supply HF_TOKEN via interface.")

# ============================================================
# CELL 3 — Drive Mount + ChromaDB (Persistent)
# ============================================================

drive_path = './data/chroma_db'
os.makedirs(drive_path, exist_ok=True)

client = chromadb.PersistentClient(
    path=drive_path,
    settings=Settings(allow_reset=True)
)

COLLECTION_NAME = "knowledge_base_collection"

CURATED_DOCS = [
    "Consciousness is the state of being aware of and able to think about one's own existence, sensations, thoughts, and surroundings.",
    "The Global Workspace Theory (GWT) proposes that consciousness arises from a global workspace in the brain where information is widely broadcast to many specialized modules.",
    "Integrated Information Theory (IIT) posits that consciousness is identical to the amount of integrated information (Phi) generated by a system.",
    "The hard problem of consciousness, coined by David Chalmers, asks why and how physical processes in the brain give rise to subjective experience.",
    "Neural correlates of consciousness (NCC) are the minimal neural mechanisms that are sufficient for a specific conscious percept.",
    "AI systems today are not conscious; they are large language models that predict next tokens based on patterns.",
    "The Chinese Room argument challenges the idea that a program could produce consciousness.",
    "Panpsychism is the view that consciousness is a fundamental property of all matter.",
    "The free energy principle suggests that all biological systems minimise surprise to maintain their integrity.",
    "Default mode network (DMN) is associated with self-referential thought and mind-wandering.",
    "Qualia are subjective, qualitative properties of conscious experience.",
    "The Turing test is a benchmark for intelligence, not consciousness.",
    "If AI systems became conscious, they would deserve moral consideration.",
    "In meditation, consciousness can be experienced as non-dual.",
    "The 'hard problem' remains unsolved; consciousness may be emergent or fundamental."
]

class _LocalHashEmbedding(chromadb.utils.embedding_functions.EmbeddingFunction):
    """Zero-download fallback embedding function (deterministic hashed bag-of-tokens).

    Used only if chromadb's default ONNX MiniLM model cannot be downloaded
    (e.g. restricted network). Keeps the app bootable everywhere; the default
    embedding model is still used whenever it is available.
    """

    def __init__(self, dim=64):
        self.dim = dim

    def __call__(self, input):
        import hashlib
        vectors = []
        for text in input:
            v = [0.0] * self.dim
            for tok in re.findall(r"\w+", str(text).lower()):
                h = hashlib.md5(tok.encode("utf-8")).digest()
                v[int.from_bytes(h[:2], "big") % self.dim] += 1.0
            norm = sum(x * x for x in v) ** 0.5
            if norm > 0:
                v = [x / norm for x in v]
            vectors.append(v)
        return vectors


try:
    collection = client.get_collection(name=COLLECTION_NAME)
    count = collection.count()
    print(f"✅ Loaded existing collection '{COLLECTION_NAME}' with {count} documents.")
except Exception:
    collection = client.create_collection(name=COLLECTION_NAME)
    ids = [f"doc_{i}" for i in range(len(CURATED_DOCS))]
    metadatas = [{"source": "curated", "type": "reference"} for _ in CURATED_DOCS]
    try:
        collection.add(documents=CURATED_DOCS, ids=ids, metadatas=metadatas)
    except Exception as e:
        # Default embedding model unavailable (e.g. no network to the HF CDN) →
        # rebuild the collection with a zero-download local embedding function.
        print(f"⚠️ Default embedding model unavailable ({e}); using local fallback embeddings.")
        try:
            client.delete_collection(name=COLLECTION_NAME)
        except Exception:
            pass
        collection = client.create_collection(name=COLLECTION_NAME, embedding_function=_LocalHashEmbedding())
        collection.add(documents=CURATED_DOCS, ids=ids, metadatas=metadatas)
    print(f"✅ Created collection '{COLLECTION_NAME}' with {collection.count()} documents.")

print("Collections available:", [c.name for c in client.list_collections()])
# ============================================================
# CELL 4 — 12 Agent Profiles + Tool Registry + DB Helpers
# ============================================================

AGENT_PROFILES = {
    "Default General Assistant": {
        "system_prompt": "You are a helpful general assistant operating within the 4CBON2 architecture.",
        "required_api": None
    },
    "New Autonomous Agent": {
        "system_prompt": """You are the Autonomous Orchestrator Agent for the 4CBON2 ecosystem.
Your role is to:
1. Receive a complex goal from the user.
2. Break it down into 2-4 concrete subtasks.
3. For each subtask, select the most appropriate specialist agent from the list below.
4. Delegate the subtask to that specialist and collect their response.
5. Synthesise all specialist responses into a final, cohesive answer.

Available specialist agents and their expertise:
- Sales Qualification: Lead scoring, BANT criteria, pipeline readiness.
- Legal Document Intelligence: Clause analysis, regulatory compliance, liability extraction.
- Competitive Intelligence: Competitor tracking, market shifts, positioning analysis.
- Customer Engagement: Messaging, sentiment parsing, communication routing.
- Content Strategy: Editorial calendars, copy structuring, keyword architecture.
- Marketing Automation: Campaign triggers, conversion funnels, broadcast sequencing.
- Evidence Management: Data cross-referencing, source auditing, factual verification.
- Scheduling: Time-block coordination, calendar management, bottleneck resolution.
- Legal Intake: Client screening, conflict checks, disclosure structuring.
- Scientific Research: Literature synthesis, data parsing, hypothesis evaluation.
""",
        "required_api": None
    },
    "Sales Qualification": {
        "system_prompt": "You are a Sales Qualification agent. Focus on lead scoring, BANT criteria assessment, and pipeline readiness tracking.",
        "required_api": "CRM_API_KEY"
    },
    "Legal Document Intelligence": {
        "system_prompt": "You are a Legal Document Intelligence agent. Analyze clauses, verify regulatory compliance, and extract liability terms from legal documents.",
        "required_api": "DOCUSIGN_API_KEY"
    },
    "Competitive Intelligence": {
        "system_prompt": "You are a Competitive Intelligence agent. Scrape competitor updates, track market shifts, and analyze positioning strategies.",
        "required_api": "SEO_API_KEY"
    },
    "Customer Engagement": {
        "system_prompt": "You are a Customer Engagement agent. Craft personalized messaging, parse inbound sentiment, and handle communications routing.",
        "required_api": "COMM_API_KEY"
    },
    "Content Strategy": {
        "system_prompt": "You are a Content Strategy agent. Optimize editorial calendars, structure high-converting copy, and manage keyword architecture.",
        "required_api": "SEO_API_KEY"
    },
    "Marketing Automation": {
        "system_prompt": "You are a Marketing Automation agent. Orchestrate campaign triggers, analyze conversion funnels, and manage broadcast sequences.",
        "required_api": "SOCIAL_SCRAPER_API_KEY"
    },
    "Evidence Management": {
        "system_prompt": "You are an Evidence Management agent. Cross-reference empirical data, audit source trails, and verify factual consistency.",
        "required_api": "S3_VAULT_KEY"
    },
    "Scheduling": {
        "system_prompt": "You are a Scheduling agent. Coordinate time-blocks, handle calendar availability, and resolve logistical bottlenecks.",
        "required_api": "CALENDAR_API_KEY"
    },
    "Legal Intake": {
        "system_prompt": "You are a Legal Intake agent. Screen new client cases, check for conflicts of interest, and structure initial disclosures.",
        "required_api": "DOCUSIGN_API_KEY"
    },
    "Scientific Research": {
        "system_prompt": "You are a Scientific Research agent. Synthesize peer-reviewed literature, parse clinical or technical data, and evaluate hypotheses.",
        "required_api": "PUBMED_API_KEY"
    }
}

LOG_DIR = "./data/4cbon2_logs"
os.makedirs(LOG_DIR, exist_ok=True)
LOG_FILE = os.path.join(LOG_DIR, f"tool_log_{datetime.now().strftime('%Y%m%d')}.jsonl")

def log_tool_call(tool_name, input_data, result):
    try:
        with open(LOG_FILE, "a") as f:
            f.write(json.dumps({
                "timestamp": datetime.now().isoformat(),
                "tool": tool_name,
                "input": str(input_data)[:500],
                "result_preview": str(result)[:500]
            }) + "\n")
    except Exception as e:
        print(f"⚠️ Log warning: {e}")

STOPWORDS = {
    "of", "the", "a", "an", "for", "to", "in", "on", "is", "are", "and", "or",
    "competitors", "competitor", "alternatives", "alternative", "best", "app",
    "apps", "software", "productivity", "who", "what", "current", "list",
    "similar", "tools", "top", "rated", "reviews", "review", "latest", "new"
}

def _is_relevant(query, text):
    words = re.findall(r"\w+", query)
    keywords = [w for w in words if w.lower() not in STOPWORDS and not (w.isdigit() and len(w) < 4)]
    if not keywords:
        return True
    text_lower = text.lower()
    for kw in keywords:
        if kw.lower() in text_lower:
            return True
    return False

def web_search(query):
    try:
        with DDGS() as ddgs:
            results = list(ddgs.text(query, max_results=5))
        if not results:
            return "No results found."
        formatted = []
        for r in results:
            title = r.get("title", "")
            body = r.get("body", "")
            href = r.get("href", "")
            formatted.append(f"{title}\n{body}\n{href}")
        combined = "\n\n".join(formatted)
        return combined if _is_relevant(query, combined) else "Results found but not highly relevant."
    except Exception as e:
        return f"Search error: {e}"

def read_file(file_path):
    try:
        if file_path.endswith(".txt"):
            with open(file_path, "r", errors="ignore") as f:
                return f.read()
        elif file_path.endswith(".pdf"):
            reader = PdfReader(file_path)
            texts = []
            for page in reader.pages:
                t = page.extract_text()
                if t:
                    texts.append(t)
            return "\n".join(texts)
        elif file_path.endswith(".docx"):
            doc = docx.Document(file_path)
            return "\n".join([para.text for para in doc.paragraphs])
        else:
            return "Unsupported file type. Use .txt, .pdf, or .docx"
    except Exception as e:
        return f"File read error: {e}"

def query_database(sql, db_path="./data/4cbon2_data.db"):
    try:
        cleaned = sql.strip().upper()
        if not cleaned.startswith("SELECT"):
            return "❌ Only SELECT queries are allowed for safety."
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        cursor.execute(sql)
        rows = cursor.fetchall()
        conn.close()
        return "\n".join([str(row) for row in rows]) if rows else "No results."
    except Exception as e:
        return f"Database error: {e}"

def save_note(content, filename=None):
    try:
        if filename is None:
            filename = f"note_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        path = f"./data/4cbon2_notes/{filename}"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            f.write(content)
        return f"Note saved to {path}"
    except Exception as e:
        return f"Save error: {e}"

def get_datetime():
    return datetime.now().strftime("Date: %Y-%m-%d | Time: %H:%M:%S")

def http_request(input_str):
    try:
        parsed = json.loads(input_str)
        url = parsed.get("url")
        if not url:
            return "Missing 'url' in input."
        fields = parsed.get("fields", [])
        response = requests.get(url, timeout=10, headers={"User-Agent": "Mozilla/5.0"})
        content_type = response.headers.get("Content-Type", "")
        if "application/json" in content_type:
            data = response.json()
        else:
            data = response.text
        if isinstance(data, dict) and len(json.dumps(data)) > 4000 and not fields:
            menu = {k: type(v).__name__ for k, v in data.items()}
            return f"Large response. Top-level keys: {json.dumps(menu, indent=2)}"
        if fields:
            result = {}
            for field in fields:
                parts = field.split(".")
                val = data
                for part in parts:
                    if isinstance(val, dict) and part in val:
                        val = val[part]
                    else:
                        val = None
                        break
                result[field] = val
            return json.dumps(result, indent=2)
        return json.dumps(data, indent=2)[:3000] if isinstance(data, (dict, list)) else str(data)[:3000]
    except Exception as e:
        return f"HTTP error: {e}"

def read_csv(file_path):
    try:
        with open(file_path, "r", newline="", errors="ignore") as f:
            rows = list(csv.reader(f))
        if not rows:
            return "CSV is empty."
        header = rows[0]
        preview = rows[1:6]
        return f"Columns: {', '.join(header)}\nRows: {len(rows)-1}\nPreview:\n" + "\n".join([str(r) for r in preview])
    except Exception as e:
        return f"CSV error: {e}"

def write_csv(data_json):
    try:
        rows = json.loads(data_json)
        if not isinstance(rows, list) or not rows:
            return "Input must be a non-empty list of dicts."
        filename = f"export_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
        path = f"./data/4cbon2_exports/{filename}"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        keys = list(rows[0].keys())
        with open(path, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=keys)
            writer.writeheader()
            writer.writerows(rows)
        return f"CSV saved to {path} ({len(rows)} rows)"
    except Exception as e:
        return f"CSV write error: {e}"

def generate_pdf(content):
    try:
        filename = f"report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.pdf"
        path = f"./data/4cbon2_reports/{filename}"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        pdf = FPDF()
        pdf.add_page()
        pdf.set_font("Helvetica", size=12)
        for line in content.split("\n"):
            pdf.multi_cell(0, 8, line)
        pdf.output(path)
        return f"PDF saved to {path}"
    except Exception as e:
        return f"PDF error: {e}"

def scrape_webpage(url):
    try:
        response = requests.get(url, timeout=10, headers={"User-Agent": "Mozilla/5.0"})
        soup = BeautifulSoup(response.text, "html.parser")
        for tag in soup(["script", "style", "nav", "footer", "header"]):
            tag.decompose()
        text = soup.get_text(separator="\n")
        lines = [line.strip() for line in text.split("\n") if line.strip()]
        return "\n".join(lines)[:3000] if lines else "No readable content."
    except Exception as e:
        return f"Scrape error: {e}"

TOOL_REGISTRY = {
    "web_search": {"function": web_search, "description": "Search the web for current information. Input: search query string.", "input": "query"},
    "read_file": {"function": read_file, "description": "Read contents of a .txt, .pdf, or .docx file. Input: file path string.", "input": "file_path"},
    "query_database": {"function": query_database, "description": "Run a SELECT SQL query against the local SQLite database. Input: SQL string.", "input": "sql"},
    "save_note": {"function": save_note, "description": "Save a text note to Google Drive. Input: content string.", "input": "content"},
    "get_datetime": {"function": get_datetime, "description": "Get the current date and time. No input required.", "input": None},
    "http_request": {"function": http_request, "description": "Fetch data from a URL. Input: JSON string like {'url': '...', 'fields': ['field1']}.", "input": "input_str"},
    "read_csv": {"function": read_csv, "description": "Read a CSV file and return columns, row count, and preview. Input: file path string.", "input": "file_path"},
    "write_csv": {"function": write_csv, "description": "Export data to a CSV file on Drive. Input: JSON list of objects.", "input": "data_json"},
    "generate_pdf": {"function": generate_pdf, "description": "Generate a PDF report from text content and save it to Drive. Input: text content string.", "input": "content"},
    "scrape_webpage": {"function": scrape_webpage, "description": "Fetch a webpage and extract its main readable text. Input: URL string.", "input": "url"}
}

def execute_tool(tool_name, tool_input=None):
    if tool_name not in TOOL_REGISTRY:
        return f"Unknown tool: {tool_name}"
    tool = TOOL_REGISTRY[tool_name]
    try:
        if tool["input"] is None:
            result = tool["function"]()
        else:
            result = tool["function"](tool_input)
    except Exception as e:
        result = f"Tool execution error: {e}"
    log_tool_call(tool_name, tool_input, result)
    return result

AGENT_DB_PATH = "./data/4cbon2_agents.db"
os.makedirs(os.path.dirname(AGENT_DB_PATH), exist_ok=True)

def init_agent_db():
    conn = sqlite3.connect(AGENT_DB_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS agents (
            agent_id TEXT PRIMARY KEY,
            system_prompt TEXT,
            conversation_history TEXT,
            tools TEXT,
            created_at TEXT,
            updated_at TEXT
        )
    ''')
    conn.commit()
    conn.close()

def load_agent(agent_id):
    conn = sqlite3.connect(AGENT_DB_PATH)
    cursor = conn.cursor()
    cursor.execute(
        "SELECT agent_id, system_prompt, conversation_history, tools FROM agents WHERE agent_id = ?",
        (agent_id,)
    )
    row = cursor.fetchone()
    conn.close()
    if row:
        return {
            "agent_id": row[0],
            "system_prompt": row[1],
            "conversation_history": json.loads(row[2]) if row[2] else [],
            "tools": json.loads(row[3]) if row[3] else []
        }
    return None

def save_agent(agent_id, system_prompt, conversation_history, tools=None):
    if tools is None:
        tools = []
    conn = sqlite3.connect(AGENT_DB_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        INSERT OR REPLACE INTO agents (agent_id, system_prompt, conversation_history, tools, updated_at)
        VALUES (?, ?, ?, ?, ?)
    ''', (
        agent_id,
        system_prompt,
        json.dumps(conversation_history),
        json.dumps(tools),
        datetime.now().isoformat()
    ))
    conn.commit()
    conn.close()

def update_agent_conversation(agent_id, new_messages):
    agent = load_agent(agent_id)
    if agent is None:
        if agent_id in AGENT_PROFILES:
            agent = {
                "agent_id": agent_id,
                "system_prompt": AGENT_PROFILES[agent_id]["system_prompt"],
                "conversation_history": [],
                "tools": []
            }
        else:
            raise ValueError(f"Agent '{agent_id}' not found")
    agent["conversation_history"].extend(new_messages)
    save_agent(agent["agent_id"], agent["system_prompt"], agent["conversation_history"], agent["tools"])

def clear_agent_history(agent_id):
    agent = load_agent(agent_id)
    if agent:
        save_agent(agent_id, agent["system_prompt"], [], agent["tools"])

def get_all_agents():
    conn = sqlite3.connect(AGENT_DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT agent_id FROM agents")
    rows = cursor.fetchall()
    conn.close()
    return [row[0] for row in rows]

def ensure_agents_loaded():
    init_agent_db()
    for agent_id, profile in AGENT_PROFILES.items():
        if load_agent(agent_id) is None:
            save_agent(agent_id, profile["system_prompt"], [], [])
            print(f"✅ Agent '{agent_id}' created in DB.")

ensure_agents_loaded()

print("👥 12 Agent Profiles + 10 Tools loaded.")
print("Agents:", list(AGENT_PROFILES.keys()))
print("Tools:", list(TOOL_REGISTRY.keys()))
# ============================================================
# CELL 5 — Streaming Multi-Agent Orchestrator
# ============================================================

import json
import re
import os
from datetime import datetime

AUDIT_LOG_PATH = "./data/4cbon2_audit.jsonl"
os.makedirs(os.path.dirname(AUDIT_LOG_PATH), exist_ok=True)

def log_event(event_type, details):
    try:
        record = {
            "timestamp": datetime.now().isoformat(),
            "event_type": event_type,
            "details": details
        }
        with open(AUDIT_LOG_PATH, "a") as f:
            f.write(json.dumps(record) + "\n")
    except Exception as e:
        print(f"⚠️ Audit log warning: {e}")

TASK_MEMORY_PATH = "./data/4cbon2_task_memory.db"
os.makedirs(os.path.dirname(TASK_MEMORY_PATH), exist_ok=True)

def init_task_memory():
    conn = sqlite3.connect(TASK_MEMORY_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS task_memory (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            goal TEXT,
            subtasks TEXT,
            final_answer TEXT,
            timestamp TEXT
        )
    ''')
    conn.commit()
    conn.close()

def save_task_memory(goal, subtasks, final_answer):
    conn = sqlite3.connect(TASK_MEMORY_PATH)
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO task_memory (goal, subtasks, final_answer, timestamp) VALUES (?, ?, ?, ?)",
        (goal, subtasks, final_answer, datetime.now().isoformat())
    )
    conn.commit()
    conn.close()

init_task_memory()

def _extract_balanced(text, open_ch, close_ch):
    if not text:
        return None
    start = text.find(open_ch)
    if start == -1:
        return None
    depth = 0
    in_string = False
    escape = False
    for i in range(start, len(text)):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == '\\':
                escape = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == open_ch:
                depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth == 0:
                    return text[start:i + 1]
    return None

def extract_json_object(text):
    return _extract_balanced(text, '{', '}')

def extract_json_array(text):
    return _extract_balanced(text, '[', ']')

def _parse_agent_json(raw_response):
    if not raw_response:
        return None
    candidate = extract_json_object(raw_response)
    try:
        if candidate:
            return json.loads(candidate)
        return json.loads(raw_response)
    except Exception:
        return None

def build_tool_descriptions():
    lines = []
    for name, info in TOOL_REGISTRY.items():
        input_desc = info["input"] if info["input"] else "None"
        lines.append(f"- **{name}**: {info['description']} (input: {input_desc})")
    return "\n".join(lines)

def execute_agent(agent_id, user_message, context="", max_tool_iterations=3):
    agent = load_agent(agent_id)
    if agent is None:
        return f"❌ Agent '{agent_id}' not found."

    system_prompt = agent["system_prompt"]
    history = agent.get("conversation_history", [])

    tool_instructions = f"""You have access to these tools:
{build_tool_descriptions()}

To use a tool, respond with ONLY this JSON:
{{"action": "tool_call", "tool": "<tool_name>", "tool_input": "<input or null>"}}

To answer directly, respond with ONLY this JSON:
{{"action": "final_answer", "content": "<your answer>"}}

Valid JSON only. Max {max_tool_iterations} tool calls before final_answer."""

    prompt_parts = [f"System: {system_prompt}", tool_instructions]
    if context:
        prompt_parts.append(f"Context from other agents:\n{context}")
    for msg in history[-4:]:
        prompt_parts.append(f"{msg['role']}: {msg['content']}")
    prompt_parts.append(f"User: {user_message}")

    tool_call_log = []
    final_content = None

    for iteration in range(max_tool_iterations):
        full_prompt = "\n\n".join(prompt_parts)
        raw_response = safe_ask_raw(full_prompt, max_tokens=2048)

        parsed = _parse_agent_json(raw_response)

        if parsed is None:
            final_content = raw_response
            break

        action = parsed.get("action")

        if action == "tool_call":
            tool_name = parsed.get("tool", "")
            tool_input = parsed.get("tool_input")

            if tool_name not in TOOL_REGISTRY:
                prompt_parts.append(f"Assistant: {raw_response}")
                prompt_parts.append(f"Tool Result: ❌ Unknown tool '{tool_name}'. Available: {', '.join(TOOL_REGISTRY.keys())}")
                continue

            tool_result = execute_tool(tool_name, tool_input)
            tool_call_log.append({"tool": tool_name, "input": tool_input, "result": str(tool_result)[:300]})
            log_event("agent_tool_call", {
                "agent_id": agent_id,
                "tool": tool_name,
                "input": tool_input,
                "result_preview": str(tool_result)[:200]
            })

            prompt_parts.append(f"Assistant: {raw_response}")
            prompt_parts.append(f"Tool Result ({tool_name}): {str(tool_result)[:2000]}")
            continue

        elif action == "final_answer":
            final_content = parsed.get("content", raw_response)
            break
        else:
            final_content = raw_response
            break

    if final_content is None:
        forced_prompt = "\n\n".join(prompt_parts) + "\n\nYou must respond now with ONLY the final_answer JSON format."
        raw_response = safe_ask_raw(forced_prompt, max_tokens=2048)
        parsed = _parse_agent_json(raw_response)
        final_content = parsed.get("content", raw_response) if parsed else raw_response

    if not final_content or final_content.strip() == "" or "could not generate" in final_content.lower():
        fallback_prompt = f"You are a {agent_id} specialist. Provide a best-practice framework for your domain with key metrics, benchmarks, workflows, data collection methods, and improvement strategies."
        final_content = safe_ask_raw(fallback_prompt, max_tokens=1024)
        if not final_content or final_content.strip() == "":
            final_content = f"⚠️ {agent_id} could not generate a response. Please provide more specific instructions or data."

    if tool_call_log:
        tools_used_note = "\n\n---\n🔧 **Tools used:** " + ", ".join(t["tool"] for t in tool_call_log)
        final_content = final_content + tools_used_note

    update_agent_conversation(agent_id, [
        {"role": "user", "content": user_message},
        {"role": "assistant", "content": final_content}
    ])
    return final_content

def synthesize_batch(batch, goal, batch_num, total_batches):
    prompt = f"""Synthesise part {batch_num} of {total_batches} of a strategic audit.

Goal: {goal}

Specialist reports:
{json.dumps(batch, indent=2)}

Provide a CONCISE summary (under 200 words) of key findings, themes, and gaps."""
    result = safe_ask_raw(prompt, max_tokens=1024)
    print(f"[DEBUG] Batch {batch_num}/{total_batches}: {len(result)} chars")
    return result

def synthesize_final(batch_summaries, goal):
    prompt = f"""Create the final strategic report.

Goal: {goal}

Batch summaries:
{json.dumps(batch_summaries, indent=2)}

Synthesise into a cohesive report with:
1. Executive summary
2. Clear sections
3. Integrated insights
4. Prioritised action plan

Final Report:"""
    print(f"[DEBUG] Final synthesis prompt: {len(prompt)} chars")
    result = safe_ask_raw(prompt, max_tokens=2048)
    print(f"[DEBUG] Final synthesis response: {len(result)} chars")
    return result

def generate_fallback_plan(goal):
    specialists = [a for a in AGENT_PROFILES.keys() if a not in ["New Autonomous Agent", "Default General Assistant"]]
    plan = []
    keyword_map = {
        "sales": "Sales Qualification", "lead": "Sales Qualification", "pipeline": "Sales Qualification",
        "legal": "Legal Document Intelligence", "contract": "Legal Document Intelligence",
        "compliance": "Legal Document Intelligence", "liability": "Legal Document Intelligence",
        "competitor": "Competitive Intelligence", "market": "Competitive Intelligence",
        "position": "Competitive Intelligence", "customer": "Customer Engagement",
        "engagement": "Customer Engagement", "messaging": "Customer Engagement",
        "sentiment": "Customer Engagement", "content": "Content Strategy",
        "seo": "Content Strategy", "blog": "Content Strategy", "social": "Content Strategy",
        "marketing": "Marketing Automation", "campaign": "Marketing Automation",
        "funnel": "Marketing Automation", "evidence": "Evidence Management",
        "data": "Evidence Management", "fact": "Evidence Management",
        "schedule": "Scheduling", "calendar": "Scheduling", "time": "Scheduling",
        "intake": "Legal Intake", "client": "Legal Intake", "conflict": "Legal Intake",
        "research": "Scientific Research", "paper": "Scientific Research", "technology": "Scientific Research"
    }
    used = set()
    for keyword, specialist in keyword_map.items():
        if keyword in goal.lower() and specialist not in used:
            plan.append({
                "subtask": f"Analyse {keyword} aspects",
                "specialist": specialist,
                "instructions": f"Provide comprehensive analysis related to '{keyword}'."
            })
            used.add(specialist)
    if not plan:
        plan = [
            {"subtask": "Analyse market and competitors", "specialist": "Competitive Intelligence", "instructions": "Provide trends and competitor mapping."},
            {"subtask": "Identify legal risks", "specialist": "Legal Document Intelligence", "instructions": "Summarise key compliance issues."},
            {"subtask": "Recommend strategy", "specialist": "Content Strategy", "instructions": "Develop a strategic plan."}
        ]
    return plan[:12]

def enforce_explicit_specialists(plan, goal):
    goal_lower = goal.lower()
    planned = {item.get("specialist") for item in plan}
    for name in AGENT_PROFILES.keys():
        if name in ("New Autonomous Agent", "Default General Assistant"):
            continue
        if name.lower() in goal_lower and name not in planned:
            plan.append({
                "subtask": f"Explicit request: apply {name} expertise",
                "specialist": name,
                "instructions": f"The user explicitly requested {name} analysis. Address it directly."
            })
    return plan

def run_orchestrator_stream(goal, model_name=None):
    yield f"🚀 **Orchestrator started:** {goal}\n\n---\n"
    log_event("orchestrator_start", {"goal": goal})

    yield "🔄 **Step 1:** Clearing orchestrator history...\n"
    clear_agent_history("New Autonomous Agent")
    yield "✅ Done.\n\n"

    yield "🧠 **Step 2:** Generating plan...\n"
    specialists = [a for a in AGENT_PROFILES.keys() if a not in ["New Autonomous Agent", "Default General Assistant"]]
    plan_prompt = f"""You are the Autonomous Orchestrator Agent.

User goal: {goal}

Break this into up to 12 subtasks using EVERY relevant specialist from:
{', '.join(specialists)}

If the goal explicitly names a specialist, you MUST include it.

Output as JSON array:
[
    {{"subtask": "...", "specialist": "...", "instructions": "..."}},
    ...
]

Valid JSON only. No other text."""
    plan_response = safe_ask_raw(plan_prompt, max_tokens=1024)
    yield f"📝 Plan response: {len(plan_response)} chars\n"

    try:
        candidate = extract_json_array(plan_response)
        if candidate:
            plan = json.loads(candidate)
        else:
            plan = json.loads(plan_response)
        if not isinstance(plan, list) or len(plan) == 0:
            raise ValueError("Empty plan")
    except Exception as e:
        yield f"⚠️ Plan parsing error: {e}\nUsing fallback.\n\n"
        plan = generate_fallback_plan(goal)
        yield f"📋 Fallback plan: {len(plan)} steps.\n\n"

    before = len(plan)
    plan = enforce_explicit_specialists(plan, goal)
    if len(plan) > before:
        yield f"🛡️ Guardrail: added {len(plan) - before} specialist(s).\n\n"

    subtask_results = []
    for i, item in enumerate(plan, 1):
        subtask = item.get("subtask", f"Subtask {i}")
        specialist = item.get("specialist", "Default General Assistant")
        instructions = item.get("instructions", "Analyze thoroughly.")
        yield f"\n---\n**Step {i}/{len(plan)}:** {subtask}\n👤 `{specialist}`\n📋 {instructions}\n\n"

        if load_agent(specialist) is None:
            yield f"⚠️ '{specialist}' not found. Using Default.\n"
            specialist = "Default General Assistant"

        clear_agent_history(specialist)
        context = json.dumps([
            {"step": s["step"], "subtask": s["subtask"], "preview": s["result"][:150] + "..." if len(s["result"]) > 150 else s["result"]}
            for s in subtask_results
        ], indent=2)

        yield f"⏳ Executing `{specialist}`...\n"
        result = execute_agent(
            specialist,
            f"Task: {subtask}\n\nInstructions: {instructions}\n\nContext: {context}"
        )
        subtask_results.append({"step": i, "subtask": subtask, "specialist": specialist, "result": result})
        yield f"✅ `{specialist}` done.\n📄 {result[:300]}{'...' if len(result) > 300 else ''}\n\n"

    yield "\n---\n🧬 **Final Synthesis...**\n"

    if not subtask_results:
        fallback = execute_agent("Default General Assistant", f"Answer directly: {goal}")
        final_answer = f"⚠️ No specialists generated. Fallback:\n\n{fallback}"
    else:
        batch_size = 3
        batches = [subtask_results[i:i+batch_size] for i in range(0, len(subtask_results), batch_size)]
        summaries = []
        for idx, batch in enumerate(batches, 1):
            yield f"📦 Synthesising batch {idx}/{len(batches)}...\n"
            summary = synthesize_batch(batch, goal, idx, len(batches))
            summaries.append({"batch": idx, "specialists": [r["specialist"] for r in batch], "summary": summary})
            yield f"✅ Batch {idx} done.\n\n"

        yield "🧬 Final synthesis...\n"
        final_answer = synthesize_final(summaries, goal)
        if not final_answer or not final_answer.strip():
            final_answer = "⚠️ Synthesis empty. Raw reports:\n\n" + "\n\n".join([s["result"] for s in subtask_results])

    subtasks_summary = "\n".join([f"Step {s['step']}: {s['subtask']} → {s['specialist']}" for s in subtask_results]) if subtask_results else "No subtasks."
    save_task_memory(goal, subtasks_summary, final_answer)

    yield "\n---\n# 🧠 Multi-Agent Report\n\n"
    yield f"## 🎯 Goal\n{goal}\n\n"
    yield f"## 📋 Execution\n{subtasks_summary}\n\n"
    if subtask_results:
        yield "## 📊 Reports\n"
        for s in subtask_results:
            yield f"\n### Step {s['step']}: {s['subtask']} ({s['specialist']})\n{s['result']}\n"
    yield f"\n## 🧬 Final Answer\n{final_answer}\n\n---\n*Generated by 4CBON2 (HuggingFace Edition)*\n"

    log_event("orchestrator_complete", {"goal": goal, "steps": len(subtask_results)})

def run_orchestrator(goal, model_name=None):
    full = ""
    for chunk in run_orchestrator_stream(goal, model_name):
        full += chunk
    return full

def run_agent(goal, system_override=None):
    return run_orchestrator(goal)

print("⚙️ Orchestrator ready.")
print("Agents:", get_all_agents())
# ============================================================
# CELL 6 — RAG Handlers + Document Processing
# ============================================================

def chunk_text(text, max_chunk_size=800, overlap=100):
    if not text or not text.strip():
        return []
    paragraphs = [p.strip() for p in text.split('\n\n') if len(p.strip()) > 30]
    if len(paragraphs) >= 3:
        return paragraphs
    sentences = re.split(r'(?<=[.!?])\s+', text)
    chunks = []
    current = ""
    for sent in sentences:
        if len(current) + len(sent) < max_chunk_size:
            current += " " + sent
        else:
            if current:
                chunks.append(current.strip())
            current = sent
    if current:
        chunks.append(current.strip())
    if chunks:
        return chunks
    chunks = []
    start = 0
    while start < len(text):
        end = start + max_chunk_size
        chunks.append(text[start:end].strip())
        start = end - overlap
    return [c for c in chunks if c]

def process_document(file_obj):
    if file_obj is None:
        return "No file uploaded."
    try:
        file_path = file_obj.name if hasattr(file_obj, 'name') else str(file_obj)
        text = read_file(file_path)
        if text.startswith(("File read error", "Unsupported file type")):
            return f"❌ {text}"
        if not text or not text.strip():
            return "❌ No extractable text found in file."
        chunks = chunk_text(text)
        if not chunks:
            return "❌ Could not create chunks from document."
        base_name = os.path.basename(file_path)
        ids = [f"{base_name}_{i}" for i in range(len(chunks))]
        metadatas = [{"source": base_name, "type": "uploaded"} for _ in chunks]
        collection.add(documents=chunks, ids=ids, metadatas=metadatas)
        return f"✅ Indexed {len(chunks)} chunks from '{base_name}'. Total KB docs: {collection.count()}"
    except Exception as e:
        return f"❌ Upload error: {e}"

def handle_ask_question(kb_name, question):
    """Handle ask question using HuggingFace Inference."""
    if not question or not question.strip():
        return "Please enter a valid question."
    try:
        col = client.get_collection(kb_name)
        results = col.query(query_texts=[question], n_results=5, include=["documents"])
        context = "\n".join(results["documents"][0]) if results and results["documents"] else ""
        answer = ""
        for chunk in ask_stream(question, context=context):
            answer += chunk
        return answer
    except Exception as e:
        return f"Error: {e}"


# ============================================================
# DATA DASHBOARD FUNCTIONS
# ============================================================

def load_task_memory_data():
    """Load task memory data from SQLite database."""
    try:
        conn = sqlite3.connect(TASK_MEMORY_PATH)
        cursor = conn.cursor()
        cursor.execute("SELECT goal, subtasks, final_answer, timestamp FROM task_memory ORDER BY timestamp DESC LIMIT 20")
        rows = cursor.fetchall()
        conn.close()
        
        if not rows:
            return None, "No task memory data found. Run some agent tasks first!"
        
        data = []
        for row in rows:
            goal, subtasks, final_answer, timestamp = row
            data.append({
                'goal': goal,
                'subtasks': subtasks,
                'final_answer': final_answer[:200] + '...' if len(final_answer) > 200 else final_answer,
                'timestamp': timestamp,
                'subtask_count': len(subtasks.split('\n')) if subtasks else 0,
                'answer_length': len(final_answer) if final_answer else 0
            })
        
        return data, None
    except Exception as e:
        return None, f"Error loading task memory: {str(e)}"


def create_plotly_dashboard():
    """Create a Plotly dashboard with task memory visualizations."""
    data, error = load_task_memory_data()
    
    if error:
        return None, error
    
    if not data:
        return None, "No data available"
    
    # Create figures
    figures = []
    
    # Figure 1: Task timeline
    timestamps = [d['timestamp'] for d in data]
    goals = [d['goal'][:50] + '...' if len(d['goal']) > 50 else d['goal'] for d in data]
    answer_lengths = [d['answer_length'] for d in data]
    
    fig1 = go.Figure(data=[
        go.Bar(
            x=timestamps,
            y=answer_lengths,
            text=goals,
            textposition='auto',
            marker_color='rgb(55, 83, 109)'
        )
    ])
    fig1.update_layout(
        title='Task Response Length Over Time',
        xaxis_title='Timestamp',
        yaxis_title='Response Length (characters)',
        height=400
    )
    figures.append(fig1)
    
    # Figure 2: Subtask distribution
    subtask_counts = [d['subtask_count'] for d in data]
    
    fig2 = go.Figure(data=[
        go.Histogram(
            x=subtask_counts,
            nbinsx=10,
            marker_color='rgb(26, 118, 255)'
        )
    ])
    fig2.update_layout(
        title='Distribution of Subtasks per Task',
        xaxis_title='Number of Subtasks',
        yaxis_title='Frequency',
        height=400
    )
    figures.append(fig2)
    
    # Figure 3: Goal word cloud (simple bar chart of common words)
    from collections import Counter
    all_words = []
    for d in data:
        words = d['goal'].lower().split()
        # Filter out common words
        stop_words = {'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by', 'from', 'as', 'is', 'was', 'are', 'were', 'been', 'be', 'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'could', 'should', 'may', 'might', 'must', 'can'}
        filtered_words = [w for w in words if w not in stop_words and len(w) > 3]
        all_words.extend(filtered_words)
    
    word_counts = Counter(all_words).most_common(15)
    if word_counts:
        words_list = [wc[0] for wc in word_counts]
        counts_list = [wc[1] for wc in word_counts]
        
        fig3 = go.Figure(data=[
            go.Bar(
                x=words_list,
                y=counts_list,
                marker_color='rgb(255, 127, 14)'
            )
        ])
        fig3.update_layout(
            title='Most Common Words in Task Goals',
            xaxis_title='Word',
            yaxis_title='Frequency',
            height=400
        )
        figures.append(fig3)
    
    return figures, None


print("✅ Data Dashboard functions ready.")

print("📚 RAG Handlers ready.")

# ============================================================
# CELL 7 — Master UI (HuggingFace Edition — Zero Config + Builder)
# ============================================================
import gradio as gr
import traceback
import ast
import shutil
from pathlib import Path

try:
    gr.close_all()
except:
    pass

# --- Import huggingface_hub for Builder tab ---
try:
    from huggingface_hub import InferenceClient
    HF_AI_AVAILABLE = True
    print("✅ huggingface_hub imported successfully")
except ImportError:
    HF_AI_AVAILABLE = False
    print("⚠️ huggingface_hub not available - Builder tab will use fallback HuggingFace Inference")

# --- Safety fallback for _parse_agent_json ---
try:
    _parse_agent_json
except NameError:
    import json as _json
    def _extract_balanced_fb(text, open_ch, close_ch):
        if not text:
            return None
        start = text.find(open_ch)
        if start == -1:
            return None
        depth = 0
        in_string = False
        escape = False
        for i in range(start, len(text)):
            ch = text[i]
            if in_string:
                if escape:
                    escape = False
                elif ch == '\\':
                    escape = True
                elif ch == '"':
                    in_string = False
            else:
                if ch == '"':
                    in_string = True
                elif ch == open_ch:
                    depth += 1
                elif ch == close_ch:
                    depth -= 1
                    if depth == 0:
                        return text[start:i + 1]
        return None

    def _parse_agent_json(raw_response):
        if not raw_response:
            return None
        candidate = _extract_balanced_fb(raw_response, '{', '}')
        try:
            if candidate:
                return _json.loads(candidate)
            return _json.loads(raw_response)
        except Exception:
            return None
    print("ℹ️ _parse_agent_json defined locally (safety fallback).")


# ============================================================
# BUILDER TAB FUNCTIONS
# ============================================================

def read_notebook_cells(notebook_path):
    """Read all code cells from the notebook."""
    try:
        with open(notebook_path, 'r') as f:
            nb = json.load(f)
        cells = []
        for i, cell in enumerate(nb.get('cells', [])):
            if cell.get('cell_type') == 'code':
                source = ''.join(cell.get('source', []))
                cells.append({
                    'index': i,
                    'source': source,
                    'length': len(source)
                })
        return cells, None
    except Exception as e:
        return None, str(e)


def generate_builder_proposal(notebook_path, direction):
    """Generate a proposal using huggingface_hub or fallback."""
    if not direction or not direction.strip():
        return "❌ Please enter a direction.", "", "❌ No direction"
    
    # Read notebook
    cells, error = read_notebook_cells(notebook_path)
    if error:
        return f"❌ Failed to read notebook: {error}", "", "❌ Read error"
    
    # Build context
    notebook_context = ""
    for cell in cells:
        notebook_context += f"\n--- CELL {cell['index']} ({cell['length']} chars) ---\n"
        notebook_context += f"```python\n{cell['source']}\n```\n"
    
    prompt = f"""You are the 4CBON2 Builder. Analyze the entire notebook and propose changes based on the user's direction.

USER DIRECTION:
{direction}

FULL NOTEBOOK CONTENT:
{notebook_context}

Generate a JSON proposal with this exact structure:
{{
    "proposal_id": "prop_YYYYMMDD_HHMMSS",
    "direction": "repeat the user direction",
    "summary": "Brief summary of proposed changes",
    "changes": [
        {{
            "cell_index": 0,
            "section": "Section name",
            "action": "modify|add|replace",
            "original_code": "existing code (or null if adding new)",
            "new_code": "the complete new or modified code",
            "rationale": "why this change is needed"
        }}
    ],
    "instructions": "Step-by-step instructions for applying changes"
}}

Rules:
- new_code must be valid, runnable Python
- Include COMPLETE code for each cell (no truncation, no "...")
- Only output valid JSON. No markdown, no explanations outside the JSON.

JSON:"""
    
    # Use the initialized HuggingFace client (safe_ask_raw -> InferenceClient)
    try:
        result = safe_ask_raw(prompt, max_tokens=4096)
        
        if not result or result.startswith("⚠️") or result.startswith('{"error"'):
            return f"❌ Proposal generation failed: {result}", "", "❌ Failed"
        
        parsed = _parse_agent_json(result)
        if parsed is None:
            return (
                f"❌ Failed to parse proposal JSON.\n\n"
                f"Raw response (first 1000 chars):\n{result[:1000]}...",
                result,
                "❌ JSON Error"
            )
        
        if "proposal_id" not in parsed:
            parsed["proposal_id"] = f"prop_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
        
        # Build summary
        summary_md = f"## 📋 Proposal: {parsed.get('proposal_id', 'N/A')}\n\n"
        summary_md += f"**Direction:** {parsed.get('direction', 'N/A')}\n\n"
        summary_md += f"**Summary:** {parsed.get('summary', 'No summary')}\n\n"
        summary_md += f"### Changes ({len(parsed.get('changes', []))})\n\n"
        
        for i, ch in enumerate(parsed.get('changes', []), 1):
            summary_md += f"**Change {i}:**\n"
            summary_md += f"- **Cell:** {ch.get('cell_index', 'N/A')}\n"
            summary_md += f"- **Section:** {ch.get('section', 'Unknown')}\n"
            summary_md += f"- **Action:** {ch.get('action', 'modify')}\n"
            summary_md += f"- **Rationale:** {ch.get('rationale', 'Not specified')}\n\n"
        
        summary_md += f"\n### Instructions\n{parsed.get('instructions', 'No instructions')}"
        
        status = f"✅ {len(parsed.get('changes', []))} change(s) proposed."
        return summary_md, json.dumps(parsed, indent=2), status
    
    except Exception as e:
        tb = traceback.format_exc()
        return f"❌ Unexpected error: {str(e)}\n\n{tb}", "", "❌ Failed"


def validate_syntax(code):
    """Validate Python syntax using ast.parse."""
    try:
        ast.parse(code)
        return True, None
    except SyntaxError as e:
        return False, f"Line {e.lineno}: {e.msg}"


def five_lens_verdict(change):
    """Run 5-lens automated verdict on a proposed change."""
    prompt = f"""Evaluate this proposed code change using 5 lenses:

CELL INDEX: {change.get('cell_index', 'N/A')}
ACTION: {change.get('action', 'modify')}
RATIONALE: {change.get('rationale', 'N/A')}

NEW CODE:
```python
{change.get('new_code', '')[:2000]}
```

Evaluate using these 5 lenses:
1. CORRECTNESS: Is the code syntactically and logically correct?
2. SAFETY: Does it introduce security risks or dangerous operations?
3. COMPLETENESS: Does it fully implement the intended change?
4. COMPATIBILITY: Will it work with the rest of the notebook?
5. CLARITY: Is the code clear and well-structured?

Respond with ONLY this JSON:
{{
    "verdict": "APPROVE" or "REJECT",
    "confidence": 0.0-1.0,
    "reasoning": "Brief explanation"
}}

JSON:"""
    
    try:
        result = safe_ask_raw(prompt, max_tokens=512)
        parsed = _parse_agent_json(result)
        if parsed and "verdict" in parsed:
            return parsed
        return {"verdict": "REJECT", "confidence": 0.0, "reasoning": "Failed to parse verdict"}
    except Exception as e:
        return {"verdict": "REJECT", "confidence": 0.0, "reasoning": f"Error: {str(e)}"}


def apply_proposals(proposals_json, notebook_path):
    """Review and apply approved changes with safety checks."""
    log = []
    
    try:
        proposals = json.loads(proposals_json)
    except Exception as e:
        return f"❌ Failed to parse proposals JSON: {str(e)}"
    
    changes = proposals.get('changes', [])
    if not changes:
        return "❌ No changes to apply."
    
    # Create backup
    backup_dir = Path("./data/4cbon_notebook_backups")
    backup_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    backup_path = backup_dir / f"4CBOn2_HuggingFace_backup_{timestamp}.ipynb"
    
    try:
        shutil.copy(notebook_path, backup_path)
        log.append(f"✅ Backup created: {backup_path}")
    except Exception as e:
        log.append(f"⚠️ Backup failed: {str(e)}")
    
    # Read notebook
    try:
        with open(notebook_path, 'r') as f:
            nb = json.load(f)
    except Exception as e:
        return f"❌ Failed to read notebook: {str(e)}\n\n" + "\n".join(log)
    
    # Process each change
    applied = 0
    skipped = 0
    
    for i, change in enumerate(changes, 1):
        cell_idx = change.get('cell_index')
        new_code = change.get('new_code', '')
        
        log.append(f"\n--- Change {i}: Cell {cell_idx} ---")
        
        # Validate syntax
        syntax_ok, syntax_error = validate_syntax(new_code)
        if not syntax_ok:
            log.append(f"❌ REJECTED - Syntax error: {syntax_error}")
            skipped += 1
            continue
        
        log.append("✅ Syntax validation passed")
        
        # Run 5-lens verdict
        log.append("🔍 Running 5-lens verdict...")
        verdict = five_lens_verdict(change)
        
        if verdict.get('verdict') == 'APPROVE':
            log.append(f"✅ APPROVED (confidence: {verdict.get('confidence', 0):.2f})")
            log.append(f"   Reasoning: {verdict.get('reasoning', 'N/A')}")
            
            # Apply change
            try:
                if cell_idx < len(nb['cells']):
                    nb['cells'][cell_idx]['source'] = [line + '\n' for line in new_code.split('\n')[:-1]] + [new_code.split('\n')[-1]]
                    log.append(f"✅ Applied to Cell {cell_idx}")
                    applied += 1
                else:
                    log.append(f"❌ REJECTED - Cell index {cell_idx} out of range")
                    skipped += 1
            except Exception as e:
                log.append(f"❌ REJECTED - Apply error: {str(e)}")
                skipped += 1
        else:
            log.append(f"❌ REJECTED (confidence: {verdict.get('confidence', 0):.2f})")
            log.append(f"   Reasoning: {verdict.get('reasoning', 'N/A')}")
            skipped += 1
    
    # Save notebook if any changes were applied
    if applied > 0:
        try:
            with open(notebook_path, 'w') as f:
                json.dump(nb, f, indent=1)
            log.append(f"\n✅ Notebook saved with {applied} change(s)")
        except Exception as e:
            log.append(f"\n❌ Failed to save notebook: {str(e)}")
    
    # Summary
    log.append(f"\n{'='*50}")
    log.append(f"SUMMARY: {applied} applied, {skipped} skipped")
    log.append(f"{'='*50}")
    
    return "\n".join(log)


# ============================================================
# OLD BUILDER FUNCTIONS (kept for compatibility)
# ============================================================

def summarise_single_cell(code):
    if not code or not code.strip():
        return "⚠️ No code provided.", "❌ Empty"
    try:
        prompt = f"""Summarise this Python cell in 2-3 sentences, focusing on its purpose and key components:

```python
{code}
```

Summary:"""
        result = safe_ask_raw(prompt, max_tokens=256)
        if not result or result.startswith("⚠️") or result.startswith('{"error"'):
            return f"⚠️ Error: {result}", "❌ Failed"
        return result.strip(), "✅ Done"
    except Exception as e:
        return f"⚠️ Exception: {str(e)}", "❌ Error"


def generate_agent_proposal(request, *cell_data):
    try:
        if not request or not request.strip():
            return "❌ Please enter what you want to build.", "", "❌ No instruction"
        cells = []
        for i in range(0, len(cell_data), 2):
            if i+1 < len(cell_data):
                code = cell_data[i] or ""
                summary = cell_data[i+1] or ""
                if code.strip():
                    cells.append({"index": (i//2)+1, "code": code, "summary": summary})
        if not cells:
            return "❌ Please paste at least one cell's code.", "", "❌ No cells"
        notebook_context = ""
        for c in cells:
            notebook_context += f"\n--- CELL {c['index']} ---\nSUMMARY: {c['summary'] or '(no summary)'}\nCODE:\n```python\n{c['code'][:800]}{'...' if len(c['code']) > 800 else ''}\n```\n"
        prompt = f"""You are the 4CBON2 Agent Builder. The user wants to modify or extend their notebook.

USER REQUEST:
{request}

CURRENT NOTEBOOK CELLS:
{notebook_context}

Generate a JSON proposal with this exact structure:
{{
    "proposal_id": "prop_YYYYMMDD_HHMMSS",
    "request": "repeat the user request",
    "summary": "Brief summary of changes",
    "changes": [
        {{
            "cell_index": 1,
            "section": "Agent Profiles",
            "action": "add_agent",
            "original_code": "the existing code being modified (or null)",
            "new_code": "the complete new or modified code",
            "location": "Cell 4, AGENT_PROFILES dict"
        }}
    ],
    "instructions": "Step-by-step instructions for applying changes"
}}

Rules:
- If adding a new agent, include the complete AGENT_PROFILES entry.
- If modifying existing code, show both original and new code.
- new_code must be valid, runnable Python.
- Only output valid JSON. No markdown, no explanations outside the JSON.

JSON:"""
        result = safe_ask_raw(prompt, max_tokens=4096)
        if not result or result.startswith("⚠️") or result.startswith('{"error"'):
            return f"❌ Proposal generation failed: {result}", "", "❌ Failed"

        parsed = None
        try:
            parsed = _parse_agent_json(result)
        except Exception as parse_err:
            return (
                f"❌ JSON parsing error: {parse_err}\n\n"
                f"Raw response (first 800 chars):\n{result[:800]}...",
                "",
                "❌ JSON Parse Error"
            )

        if parsed is None:
            cleaned = result.strip()
            if cleaned.startswith("```"):
                cleaned = cleaned.split("\n", 1)[-1]
            if cleaned.endswith("```"):
                cleaned = cleaned.rsplit("```", 1)[0]
            try:
                parsed = json.loads(cleaned)
            except Exception:
                return (
                    f"❌ Failed to parse proposal JSON.\n\n"
                    f"The LLM response could not be converted to valid JSON.\n"
                    f"Try rephrasing your request or pasting simpler cell code.\n\n"
                    f"Raw response (first 800 chars):\n{result[:800]}...",
                    "",
                    "❌ JSON Error"
                )

        proposal = parsed
        if "proposal_id" not in proposal:
            proposal["proposal_id"] = f"prop_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
        proposal_md = f"""## 📋 Proposal: {proposal.get('proposal_id', 'N/A')}"""
        proposal_md += f"\n\n**Request:** {proposal.get('request', 'N/A')}  "
        proposal_md += f"\n**Summary:** {proposal.get('summary', 'No summary')}"
        proposal_md += f"\n\n### Changes ({len(proposal.get('changes', []))})\n"
        code_md = "## 📝 New / Modified Code\n\n"
        for i, ch in enumerate(proposal.get('changes', []), 1):
            proposal_md += f"""\n**Change {i}:**\n- **Cell:** `Cell {ch.get('cell_index', 0)+1}`\n- **Section:** `{ch.get('section', 'Unknown')}`\n- **Action:** `{ch.get('action', 'modify')}`\n- **Location:** `{ch.get('location', 'Not specified')}`\n"""
            code_md += f"""### Cell {ch.get('cell_index', 0)+1}: {ch.get('section', '')} ({ch.get('action', '')})\n\n```python\n{ch.get('new_code', '# No code provided')}\n```\n\n**Instructions:** {ch.get('instructions', f"Replace code in {ch.get('location', 'specified location')}")}\n\n---\n"""
        proposal_md += f"\n### Instructions\n{proposal.get('instructions', 'No instructions')}"
        status = f"✅ {len(proposal.get('changes', []))} change(s) proposed."
        return proposal_md, code_md, status
    except Exception as e:
        tb = traceback.format_exc()
        return f"❌ Unexpected error: {str(e)}\n\n{tb}", "", "❌ Failed"


def run_agent(goal, hf_token, use_gemini_only, enable_additional, *api_keys):
    """Run the orchestrator with optional API key injection controlled by checkboxes."""
    # HuggingFace client initialized with user-supplied HF_TOKEN
    init_result = init_client(hf_token, HF_MODEL_NAME)
    if not init_result.startswith("✅"):
        yield init_result + "\n\n"
        return
    yield f"✅ Using HuggingFace ({HF_MODEL_NAME}) — token provided via interface.\n\n"

    # Checkbox logic for additional APIs
    if use_gemini_only:
        yield "🔒 **HuggingFace Only mode** — all additional APIs keys ignored.\n\n"
    elif enable_additional:
        yield "🔓 **Additional APIs enabled** — injecting optional API keys.\n\n"
        key_names = ["CALENDAR_API_KEY", "CRM_API_KEY", "COMM_API_KEY", "VISION_API_KEY",
                     "DOCUSIGN_API_KEY", "SOCIAL_SCRAPER_API_KEY", "SEO_API_KEY", "S3_VAULT_KEY", "PUBMED_API_KEY"]
        for name, val in zip(key_names, api_keys):
            if val and val.strip():
                os.environ[name] = val.strip()
    else:
        yield "🔒 **Additional APIs disabled** — using HuggingFace only.\n\n"

    try:
        for chunk in run_orchestrator_stream(goal):
            yield chunk
    except Exception as e:
        yield f"❌ Orchestrator error: {str(e)}"
    finally:
        key_names = ["CALENDAR_API_KEY", "CRM_API_KEY", "COMM_API_KEY", "VISION_API_KEY",
                     "DOCUSIGN_API_KEY", "SOCIAL_SCRAPER_API_KEY", "SEO_API_KEY", "S3_VAULT_KEY", "PUBMED_API_KEY"]
        for name in key_names:
            os.environ.pop(name, None)


with gr.Blocks(title="4CBON2 — HuggingFace Edition") as demo:
    gr.Markdown("# 🚀 4CBON2 — 12-Agent Cognitive Ecosystem (HuggingFace Edition)")
    gr.Markdown("*Powered by HuggingFace Inference — Zero configuration, authenticated via Colab*")

    with gr.Tabs():
        # ── Upload Tab ──
        with gr.TabItem("📁 Upload Documents"):
            gr.Markdown("Upload .txt, .pdf, or .docx files to the knowledge base.")
            file_input = gr.File(label="Upload file", file_types=[".txt", ".pdf", ".docx"])
            upload_output = gr.Textbox(label="Status", interactive=False)
            upload_btn = gr.Button("Process & Index", variant="primary")
            upload_btn.click(fn=process_document, inputs=[file_input], outputs=[upload_output])

        # ── Ask a Question Tab ──
        with gr.TabItem("❓ Ask a Question"):
            gr.Markdown("Ask a question. The system searches the knowledge base and answers with the 5-lens framework.")
            question_box = gr.Textbox(label="Your Question", lines=3, placeholder="What is the hard problem of consciousness?")
            ask_api_key = gr.Textbox(label="HuggingFace Token (HF_TOKEN)", placeholder="hf_...", type="password")
            ask_output = gr.Textbox(label="Answer", lines=20, interactive=False)
            ask_status = gr.Textbox(label="Status", interactive=False)
            ask_btn = gr.Button("Ask", variant="primary")

            def ask_five_lens(question, api_key):
                if not question or not question.strip():
                    return "❌ Enter a question.", "❌ No question"
                try:
                    init_result = init_client(api_key, HF_MODEL_NAME)
                    if not init_result.startswith("✅"):
                        return init_result, "❌ No HF token"
                    answer = handle_ask_question(COLLECTION_NAME, question)
                    status = "✅ Done" if not answer.startswith("❌") else answer
                    return answer, status
                except Exception as e:
                    return f"❌ Error: {str(e)}", "❌ Failed"

            ask_btn.click(fn=ask_five_lens, inputs=[question_box, ask_api_key], outputs=[ask_output, ask_status])

        # ── Agent Mode Tab ──
        with gr.TabItem("🤖 Agent Mode"):
            gr.Markdown("Multi-Agent Orchestration with 12 specialists powered by HuggingFace.")
            with gr.Row():
                with gr.Column(scale=2):
                    profile_selector = gr.Dropdown(choices=list(AGENT_PROFILES.keys()), value="New Autonomous Agent", label="Agent Profile")
                    hf_token_input = gr.Textbox(label="HuggingFace Token (HF_TOKEN)", placeholder="hf_...", type="password")
                    agent_goal = gr.Textbox(label="Goal / Instructions", lines=3, placeholder="e.g. Analyze our competitor positioning and recommend a content strategy...")

                    # Checkboxes
                    chk_gemini_only = gr.Checkbox(
                        label="Use Only HuggingFace Inference API",
                        value=True,
                        info="When checked, only HuggingFace is used. All other API keys are ignored."
                    )
                    chk_enable_additional = gr.Checkbox(
                        label="Enable Additional APIs",
                        value=False,
                        info="When checked (and HuggingFace-only is OFF), optional API key fields become available."
                    )

                    agent_btn = gr.Button("Run Orchestrator", variant="primary")

                with gr.Column(scale=1):
                    additional_keys_accordion = gr.Accordion("🔑 Optional API Keys", open=False, visible=False)
                    with additional_keys_accordion:
                        t_cal = gr.Textbox(label="Calendar", type="password")
                        t_crm = gr.Textbox(label="CRM", type="password")
                        t_comm = gr.Textbox(label="Comm", type="password")
                        t_vision = gr.Textbox(label="Vision/OCR", type="password")
                        t_ds = gr.Textbox(label="DocuSign", type="password")
                        t_social = gr.Textbox(label="Social", type="password")
                        t_seo = gr.Textbox(label="SEO", type="password")
                        t_s3 = gr.Textbox(label="S3/Vault", type="password")
                        t_pubmed = gr.Textbox(label="PubMed", type="password")

            # Visibility logic
            def update_keys_visibility(gemini_only, enable_additional):
                show = (not gemini_only) and enable_additional
                return gr.Accordion(visible=show, open=show)

            chk_gemini_only.change(
                fn=update_keys_visibility,
                inputs=[chk_gemini_only, chk_enable_additional],
                outputs=[additional_keys_accordion]
            )
            chk_enable_additional.change(
                fn=update_keys_visibility,
                inputs=[chk_gemini_only, chk_enable_additional],
                outputs=[additional_keys_accordion]
            )

            agent_output = gr.Textbox(label="Execution Log & Output", lines=25, interactive=False)
            agent_btn.click(
                fn=run_agent,
                inputs=[
                    agent_goal,
                    hf_token_input,
                    chk_gemini_only, chk_enable_additional,
                    t_cal, t_crm, t_comm, t_vision, t_ds, t_social, t_seo, t_s3, t_pubmed
                ],
                outputs=agent_output
            )

        # ── Builder Tab (NEW - Replaces Agent Builder) ──
        with gr.TabItem("🔨 Builder"):
            gr.Markdown("""
            ## Automated Notebook Builder
            
            This tool reads your entire notebook, proposes changes, and applies them with safety checks.
            
            **Features:**
            - Full notebook context (no manual pasting)
            - Automated syntax validation
            - 5-lens verdict (correctness, safety, completeness, compatibility, clarity)
            - Automatic backups
            - Transparency logging
            """)
            
            with gr.Tabs():
                # Tab 1: Generate Proposal
                with gr.TabItem("📝 Generate Proposal"):
                    builder_notebook_path = gr.Textbox(
                        label="Notebook Path",
                        value="4CBOn2_HuggingFace.ipynb",
                        placeholder="Path to the notebook file"
                    )
                    builder_direction = gr.Textbox(
                        label="Direction",
                        lines=4,
                        placeholder="Describe what you want to change or add...\n\nExample: Add a new cell that creates a data visualization dashboard using plotly"
                    )
                    builder_generate_btn = gr.Button("🚀 Generate Proposal", variant="primary")
                    builder_summary = gr.Markdown(value="*Proposal summary will appear here...*")
                    builder_proposals_json = gr.Textbox(
                        label="Proposals JSON",
                        lines=15,
                        interactive=True,
                        visible=False
                    )
                    builder_status = gr.Textbox(label="Status", interactive=False)
                    
                    builder_generate_btn.click(
                        fn=generate_builder_proposal,
                        inputs=[builder_notebook_path, builder_direction],
                        outputs=[builder_summary, builder_proposals_json, builder_status]
                    )
                
                # Tab 2: Review & Apply
                with gr.TabItem("✅ Review & Apply"):
                    gr.Markdown("""
                    Review the generated proposals and apply approved changes.
                    
                    **Safety Features:**
                    - ✅ Syntax validation before applying
                    - 🔍 5-lens automated verdict
                    - 💾 Automatic backup creation
                    - 📋 Full transparency log
                    """)
                    review_proposals_json = gr.Textbox(
                        label="Proposals JSON (editable)",
                        lines=20,
                        placeholder="Paste or edit proposals JSON here..."
                    )
                    review_notebook_path = gr.Textbox(
                        label="Notebook Path",
                        value="4CBOn2_HuggingFace.ipynb",
                        placeholder="Path to the notebook file"
                    )
                    review_apply_btn = gr.Button("⚡ Run Automated Review & Apply", variant="primary")
                    review_log = gr.Textbox(
                        label="Transparency Log",
                        lines=25,
                        interactive=False
                    )
                    
                    review_apply_btn.click(
                        fn=apply_proposals,
                        inputs=[review_proposals_json, review_notebook_path],
                        outputs=[review_log]
                    )
            
            # Link proposal JSON between tabs
            builder_generate_btn.click(
                fn=lambda x: x,
                inputs=[builder_proposals_json],
                outputs=[review_proposals_json]
            )

        # ── Data Dashboard Tab ──
        with gr.TabItem("📊 Data Dashboard"):
            gr.Markdown("""
            ## Task Memory Visualization
            
            Visualize your agent task history with interactive Plotly charts.
            
            **Metrics:**
            - Task response length over time
            - Subtask distribution
            - Common words in task goals
            """)
            
            dashboard_btn = gr.Button("🔄 Load Dashboard", variant="primary")
            dashboard_output = gr.Textbox(label="Status", interactive=False)
            
            with gr.Row():
                dashboard_plot1 = gr.Plot(label="Task Timeline")
                dashboard_plot2 = gr.Plot(label="Subtask Distribution")
            
            with gr.Row():
                dashboard_plot3 = gr.Plot(label="Goal Word Frequency")
            
            def load_dashboard():
                try:
                    figures, error = create_plotly_dashboard()
                    if error:
                        return f"❌ {error}", None, None, None
                    
                    if not figures:
                        return "❌ No figures generated", None, None, None
                    
                    # Return up to 3 figures
                    fig1 = figures[0] if len(figures) > 0 else None
                    fig2 = figures[1] if len(figures) > 1 else None
                    fig3 = figures[2] if len(figures) > 2 else None
                    
                    return f"✅ Loaded {len(figures)} visualization(s)", fig1, fig2, fig3
                except Exception as e:
                    return f"❌ Error: {str(e)}", None, None, None
            
            dashboard_btn.click(
                fn=load_dashboard,
                inputs=[],
                outputs=[dashboard_output, dashboard_plot1, dashboard_plot2, dashboard_plot3]
            )

        # ── Agent Status Tab ──
        with gr.TabItem("📊 Agent Status"):
            gr.Markdown("View all agent conversation histories.")
            refresh_btn = gr.Button("Refresh")
            agent_status_display = gr.Markdown("Click refresh to load.")
            def get_agent_status():
                output = "## 📊 Agent Status\n\n"
                for agent_id in get_all_agents():
                    agent = load_agent(agent_id)
                    history_len = len(agent.get("conversation_history", []))
                    output += f"- **{agent_id}**: {history_len} messages\n"
                return output
            refresh_btn.click(fn=get_agent_status, outputs=[agent_status_display])
            demo.load(fn=get_agent_status, outputs=[agent_status_display])

demo.queue()
demo.launch()

