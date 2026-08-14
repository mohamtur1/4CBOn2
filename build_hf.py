#!/usr/bin/env python3
"""Build 4CBOn2_HuggingFace.ipynb from 4CBOn2_Gemini.ipynb with targeted replacements."""
import json

with open('4CBOn2_Gemini.ipynb', 'r', encoding='utf-8') as f:
    nb = json.load(f)

# Helper to apply replacements on source arrays
def apply_replacements(cell_index, replacements_dict):
    cell = nb['cells'][cell_index]
    sources = cell.get('source', [])
    new_sources = []
    for line in sources:
        new_line = line
        for old, new in replacements_dict.items():
            new_line = new_line.replace(old, new)
        new_sources.append(new_line)
    cell['source'] = new_sources

# ============================================================
# GLOBAL REPLACEMENTS ACROSS ALL CELLS
# ============================================================
for cell in nb['cells']:
    sources = cell.get('source', [])
    for idx, line in enumerate(sources):
        # File references
        line = line.replace('4CBOn2_Gemini.ipynb', '4CBOn2_HuggingFace.ipynb')
        # Edition references
        line = line.replace('Gemini Edition', 'HuggingFace Edition')
        # Google AI references
        line = line.replace('google.colab.ai', 'huggingface_hub')
        line = line.replace('google.colab.ai ready', 'HuggingFace Inference ready')
        line = line.replace('Google Gemini', 'HuggingFace Inference')
        line = line.replace('google/gemini-2.5-flash', 'mistralai/Mistral-7B-Instruct-v0.3')
        sources[idx] = line

# ============================================================
# CELL 0 (Environment Setup) — full rewrite of imports/setup
# ============================================================
cell0_sources = [
    "# ============================================================\n",
    "# CELL 1 — Environment Setup (HuggingFace Edition — huggingface_hub)\n",
    "# ============================================================\n",
    "\n",
    "!pip install -q gradio chromadb PyPDF2 python-docx duckduckgo-search beautifulsoup4 fpdf2 sentence-transformers plotly huggingface_hub\n",
    "\n",
    "import gradio as gr\n",
    "import chromadb\n",
    "from chromadb.config import Settings\n",
    "from google.colab import drive\n",
    "from huggingface_hub import InferenceClient\n",
    "import os\n",
    "import torch\n",
    "import json\n",
    "import re\n",
    "import sqlite3\n",
    "import time\n",
    "import requests\n",
    "import csv\n",
    "from datetime import datetime\n",
    "from PyPDF2 import PdfReader\n",
    "import docx\n",
    "from duckduckgo_search import DDGS\n",
    "from bs4 import BeautifulSoup\n",
    "from fpdf import FPDF\n",
    "import plotly.express as px\n",
    "import plotly.graph_objects as go\n",
    "\n",
    'print("✅ Environment ready.")\n',
    'print("PyTorch version:", torch.__version__)\n',
    'print("GPU available:", torch.cuda.is_available())\n',
    "\n",
    "# Default HuggingFace model\n",
    "HF_MODEL_NAME = 'mistralai/Mistral-7B-Instruct-v0.3'\n",
    "print(f\"\\n✅ HuggingFace Inference ready. Default model: {HF_MODEL_NAME}\")\n",
    "\n",
    "# Note: API keys (HF_TOKEN, etc.) must be provided by the user via the interface below.\n"
]
nb['cells'][0]['source'] = cell0_sources

# ============================================================
# CELL 1 (Core LLM) — rewrite with HF InferenceClient
# ============================================================
cell1_sources = [
    "# ============================================================\n",
    "# CELL 2 — Core LLM (HuggingFace — huggingface_hub.InferenceClient)\n",
    "# ============================================================\n",
    "\n",
    "HF_MODEL_NAME = 'mistralai/Mistral-7B-Instruct-v0.3'\n",
    "hf_client = None\n",
    "HF_TOKEN = None\n",
    "\n",
    "def init_client(hf_token: str, model_name: str = 'mistralai/Mistral-7B-Instruct-v0.3'):\n",
    "    global hf_client, HF_TOKEN, HF_MODEL_NAME\n",
    "    if not hf_token or not hf_token.strip():\n",
    "        return '⚠️ No HuggingFace token provided. Please enter your HF_TOKEN.'\n",
    "    HF_TOKEN = hf_token.strip()\n",
    "    HF_MODEL_NAME = model_name\n",
    "    try:\n",
    "        hf_client = InferenceClient(token=HF_TOKEN)\n",
    "        return f'✅ HuggingFace client ready. Model: {HF_MODEL_NAME}'\n",
    "    except Exception as e:\n",
    "        return f'⚠️ HF client init error: {str(e)}'\n",
    "\n",
    "def generate_text(prompt, max_tokens=2048, temperature=0.7, stream=False):\n",
    '    \"\"\"Generate text using HuggingFace InferenceClient.\"\"\"\n',
    "    if hf_client is None:\n",
    "        return '⚠️ Error: HuggingFace client not initialized. Provide HF_TOKEN via the interface.'\n",
    "    try:\n",
    "        if stream:\n",
    "            full_response = []\n",
    "            for chunk in hf_client.chat_completion(\n",
    "                messages=[{'role': 'user', 'content': prompt}],\n",
    "                model=HF_MODEL_NAME,\n",
    "                max_tokens=max_tokens,\n",
    "                temperature=temperature,\n",
    "                stream=True,\n",
    "            ):\n",
    "                delta = chunk.choices[0].delta.content if chunk.choices else None\n",
    "                if delta:\n",
    "                    full_response.append(delta)\n",
    "            return ''.join(full_response)\n",
    "        else:\n",
    "            response = hf_client.chat_completion(\n",
    "                messages=[{'role': 'user', 'content': prompt}],\n",
    "                model=HF_MODEL_NAME,\n",
    "                max_tokens=max_tokens,\n",
    "                temperature=temperature,\n",
    "                stream=False,\n",
    "            )\n",
    "            content = response.choices[0].message.content if response.choices else ''\n",
    "            return content\n",
    "    except Exception as e:\n",
    "        return f'⚠️ API Error: {str(e)}'\n",
    "\n",
    "def ask_raw(prompt, max_tokens=2048):\n",
    "    return generate_text(prompt, max_tokens=max_tokens, temperature=0.1, stream=False)\n",
    "\n",
    "def safe_ask_raw(prompt, max_tokens=2048):\n",
    "    try:\n",
    "        result = ask_raw(prompt, max_tokens=max_tokens)\n",
    "        if not result or not result.strip():\n",
    '            return \'{"error": "Empty response from LLM. Please check your HF token or reduce prompt size."}\'\n',
    "        if hasattr(result, '__iter__') and not isinstance(result, (str, dict, list)):\n",
    '            result = "".join(list(result))\n',
    "        if not isinstance(result, str):\n",
    "            result = str(result)\n",
    "        return result.strip()\n",
    "    except Exception as e:\n",
    '        return f\'{"error": "safe_ask_raw failed: {str(e)}"}\'\n',
    "\n",
    "def ask_stream(question, context=None):\n",
    '    \"\"\"Stream a five-lens answer.\"\"\"\n',
    '    prompt_template = \"\"\"You are an expert on consciousness, neuroscience, and philosophy of mind.\\n',
    "Use the provided information to answer the question using the five lenses below.\\n",
    "{context_prefix}QUESTION: {question}\\n",
    "\\n",
    "1. ANALOGICAL — Compare this to similar known phenomena, systems, or experiences. What is this question like? Draw meaningful parallels.\\n",
    "2. INDUCTIVE — What patterns emerge from the evidence and context? What general principles or trends can we infer?\\n",
    "3. CRITICAL — What are the limitations, gaps, contradictions, or alternative viewpoints? What might skeptics argue?\\n",
    "4. RESOLUTION — How do we reconcile conflicting perspectives? What synthesis or balanced conclusion emerges?\\n",
    "5. FINAL ANSWER — A clear, direct, well-reasoned answer to the original question, grounded in the analysis above.\\n",
    "\\n",
    "Use clear headers for each section.\"\"\"\n",
    '    context_prefix = ""\n',
    "    if context:\n",
    '        context_prefix = f"Here is some relevant information:\\n\\n{context}\\n\\n"\n',
    "    formatted_prompt = prompt_template.format(question=question, context_prefix=context_prefix)\n",
    "    full_text = generate_text(formatted_prompt, max_tokens=2048, temperature=0.7, stream=False)\n",
    "    if full_text.startswith('⚠️'):\n",
    "        yield full_text\n",
    "        return\n",
    "    words = full_text.split()\n",
    "    chunk = ''\n",
    "    for i, word in enumerate(words):\n",
    "        chunk += word + ' '\n",
    "        if (i + 1) % 5 == 0 or i == len(words) - 1:\n",
    "            yield chunk\n",
    "            chunk = ''\n",
    "\n",
    "def ask(question, context=None):\n",
    '    \"\"\"Get complete answer (non-streaming aggregation).\"\"\"\n',
    '    full_text = ""\n',
    "    for chunk in ask_stream(question, context=context):\n",
    "        full_text += chunk\n",
    "    return full_text\n",
    "\n",
    'print(f"✅ Cell 2 ready. Model: {HF_MODEL_NAME}")\n',
    'print("Using HuggingFace InferenceClient — user must supply HF_TOKEN via interface.")\n',
]
nb['cells'][1]['source'] = cell1_sources

# ============================================================
# CELL 2 (Drive + ChromaDB) — change path reference
# ============================================================
for cell in nb['cells']:
    sources = cell.get('source', [])
    for idx, line in enumerate(sources):
        if 'chroma_db_gemini' in line:
            sources[idx] = line.replace('chroma_db_gemini', 'chroma_db_huggingface')

# ============================================================
# CELL 6 (UI) — major replacements for HF token and references
# ============================================================
ui_cell = nb['cells'][6]
ui_sources = ui_cell.get('source', [])
new_ui_sources = []

# We will do line-by-line targeted replacements for the UI cell
for line in ui_sources:
    # Basic replacements
    line = line.replace('4CBOn2_Gemini.ipynb', '4CBOn2_HuggingFace.ipynb')
    line = line.replace('Gemini Edition', 'HuggingFace Edition')
    line = line.replace('google.colab.ai', 'huggingface_hub')
    line = line.replace('Google Gemini', 'HuggingFace Inference')
    line = line.replace('google/gemini-2.5-flash', 'mistralai/Mistral-7B-Instruct-v0.3')

    # Replace references to colab_ai with HF references
    line = line.replace('from google.colab import ai as colab_ai', 'from huggingface_hub import InferenceClient')
    line = line.replace('COLAB_AI_AVAILABLE = True', 'HF_AI_AVAILABLE = True')
    line = line.replace('COLAB_AI_AVAILABLE = False', 'HF_AI_AVAILABLE = False')
    line = line.replace('COLAB_AI_AVAILABLE', 'HF_AI_AVAILABLE')
    line = line.replace('colab_ai', 'hf_client_ref')
    line = line.replace('google.colab.ai not available', 'HuggingFace token not set')

    # Replace Gemini-only checkbox labels
    line = line.replace('Use Only Gemini API', 'Use Only HuggingFace Inference API')
    line = line.replace('Gemini Only mode', 'HuggingFace Only mode')
    line = line.replace('Using Gemini', 'Using HuggingFace')
    line = line.replace('Using Gemini only', 'Using HuggingFace only')
    line = line.replace('only Gemini', 'only HuggingFace')
    line = line.replace('all additional API', 'all additional APIs')

    # Replace API key references where appropriate (keep optional keys, but make main one HF)
    line = line.replace('OpenCode API Key', 'HuggingFace Token (HF_TOKEN)')
    line = line.replace('OpenCode API Key (Required)', 'HuggingFace Token (HF_TOKEN, Required)')
    line = line.replace('OpenCode Key Required', 'HF Token Required')
    line = line.replace('sk-', 'hf_')

    # Replace function references inside UI that call init_client / generate_text properly
    # We need to update `run_agent_with_keys` logic to call init_client with HF_TOKEN
    # Since the original function name may be different, let's adjust the function name in the source
    line = line.replace('run_agent_with_keys', 'run_agent_with_keys')
    line = line.replace('opencode_key_input', 'hf_token_input')
    line = line.replace('opencode_key', 'hf_token')
    line = line.replace('chk_opencode_only', 'chk_hf_only')
    line = line.replace('chk_enable_additional', 'chk_enable_additional')
    line = line.replace('agent_model', 'agent_model')
    line = line.replace('use_opencode_only', 'use_hf_only')

    new_ui_sources.append(line)

ui_cell['source'] = new_ui_sources

# ============================================================
# ADDITIONAL REPLACEMENTS ACROSS ALL CELLS FOR UI FUNCTION NAMES
# ============================================================
# The UI references the orchestrator function names. Let's scan all cells for function references
# and update them if needed. Since we kept the same `run_agent` / `run_orchestrator` names,
# this should work, but we must ensure the button click inputs reference the correct variables.

# In the original Gemini notebook, the agent button click uses:
# fn=run_agent, inputs=[agent_goal, chk_gemini_only, chk_enable_additional, ... api keys ...]
# We renamed variables but the code that builds the Gradio interface also needs to reference
# the renamed checkbox/input variables correctly.

# Let's inspect the UI cell for the click definition and fix variable names.
ui_cell_text = ''.join(ui_cell.get('source', []))

# We need to replace the click handler definition in the source array properly.
# Since we did string replacements, `chk_opencode_only` became `chk_hf_only`, etc.
# However, the original source may have had these exact names already changed
# in the previous step. We need to verify the click definition uses the right variable names.

# Let's inspect the relevant section around agent_btn.click
for idx, line in enumerate(ui_cell.get('source', [])):
    if 'agent_btn.click(' in line:
        print(f"Found agent_btn.click at line {idx}: {line[:120]}")
    if 'chk_hf_only' in line:
        print(f"Found chk_hf_only at line {idx}: {line[:120]}")
    if 'hf_token_input' in line:
        print(f"Found hf_token_input at line {idx}: {line[:120]}")

# ============================================================
# CELL 7 (Download) — change file name
# ============================================================
cell7_sources = [
    "# ============================================================\n",
    "# CELL 8 — Download This Notebook\n",
    "# ============================================================\n",
    "from google.colab import files\n",
    "files.download('4CBOn2_HuggingFace.ipynb')\n"
]
nb['cells'][7]['source'] = cell7_sources

# ============================================================
# FINAL CLEANUP: Update metadata and save
# ============================================================
nb['metadata']['colab']['provenance'] = []
# Update any remaining references in outputs / other fields (optional)

with open('4CBOn2_HuggingFace.ipynb', 'w', encoding='utf-8') as f:
    json.dump(nb, f, indent=1, ensure_ascii=False)

print("Created 4CBOn2_HuggingFace.ipynb")
