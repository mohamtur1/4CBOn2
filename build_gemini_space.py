#!/usr/bin/env python3
"""Build space_gemini/ (a Hugging Face Space) from 4CBOn2_Gemini2c.ipynb.

Mirrors the convention of build_hf.py, which generated the HuggingFace-edition
notebook. This script instead produces a deployable Gradio Space whose LLM layer
is the Google Generative AI API rather than Colab's built-in google.colab.ai.

The large data blobs (CURATED_DATABASES, QUESTION_BANK, every layer prompt, the
12 agent profiles, the scholarly search functions) are lifted verbatim from the
notebook so nothing is re-transcribed by hand. Only the parts that must change
for a Space are rewritten, and every rewrite is asserted so a silent miss fails
the build rather than shipping a broken app.

Usage:  python3 build_gemini_space.py
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
NOTEBOOK = os.path.join(ROOT, "4CBOn2_Gemini2c.ipynb")
HANDWRITTEN = os.path.join(ROOT, "build_src", "handwritten.py")
OUT_DIR = os.path.join(ROOT, "space_gemini")

REPLACEMENTS_APPLIED = []


def load_notebook_cells():
    with open(NOTEBOOK, encoding="utf-8") as handle:
        notebook = json.load(handle)
    return ["".join(cell.get("source", [])) for cell in notebook["cells"] if cell["cell_type"] == "code"]


def load_sections():
    with open(HANDWRITTEN, encoding="utf-8") as handle:
        raw = handle.read()
    sections = {}
    for block in raw.split("# @@SECTION:")[1:]:
        name, _, body = block.partition("@@\n")
        sections[name.strip()] = body.rstrip("\n")
    return sections


def sub_once(text, old, new, label):
    """Replace exactly one occurrence, or fail loudly."""
    if old not in text:
        raise SystemExit(f"❌ BUILD FAILED — pattern not found for [{label}]:\n{old[:200]}")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"❌ BUILD FAILED — [{label}] matched {count} times, expected 1.")
    REPLACEMENTS_APPLIED.append(label)
    return text.replace(old, new)


def sub_each(text, pairs, label):
    for old, new in pairs:
        text = sub_once(text, old, new, f"{label}: {old.splitlines()[0][:60]}")
    return text


# ════════════════════════════════════════════════════════════
# CELL 4 — agent profiles, tools, DB helpers  (Drive → DATA_DIR)
# ════════════════════════════════════════════════════════════
def transform_agents_cell(source):
    text = sub_each(source, [
        ('LOG_DIR = "/content/drive/MyDrive/4cbon2_logs"',
         '# Spaces have no Google Drive; tool logs live under DATA_DIR.\n'
         'LOG_DIR = os.path.join(DATA_DIR, "4cbon2_logs")'),
        ('def query_database(sql, db_path="/content/drive/MyDrive/4cbon2_data.db"):',
         'def query_database(sql, db_path=os.path.join(DATA_DIR, "4cbon2_data.db")):'),
        ('        path = f"/content/drive/MyDrive/4cbon2_notes/{filename}"',
         '        path = os.path.join(DATA_DIR, "4cbon2_notes", filename)'),
        ('        path = f"/content/drive/MyDrive/4cbon2_exports/{filename}"',
         '        path = os.path.join(DATA_DIR, "4cbon2_exports", filename)'),
        ('        path = f"/content/drive/MyDrive/4cbon2_reports/{filename}"',
         '        path = os.path.join(DATA_DIR, "4cbon2_reports", filename)'),
        ('AGENT_DB_PATH = "/content/drive/MyDrive/4cbon2_agents.db"',
         'AGENT_DB_PATH = os.path.join(DATA_DIR, "4cbon2_agents.db")'),
        # The model reads these tool descriptions, so they must not promise Drive.
        # fpdf2 >= 2.7.6 defaults multi_cell to new_x=XPos.RIGHT, so the cursor ends
        # up on the right margin and the SECOND line raises "Not enough horizontal
        # space to render a single character". Reset to the left margin each line.
        # Pre-existing bug: the same line is broken in space_demo/app.py today.
        ("            pdf.multi_cell(0, 8, line)",
         "            pdf.multi_cell(0, 8, line, new_x=XPos.LMARGIN, new_y=YPos.NEXT)"),
        ('"description": "Save a text note to Google Drive. Input: content string."',
         '"description": "Save a text note to the app data directory. Input: content string."'),
        ('"description": "Export data to a CSV file on Drive. Input: JSON list of objects."',
         '"description": "Export data to a CSV file in the app data directory. Input: JSON list of objects."'),
        ('"description": "Generate a PDF report from text content and save it to Drive. Input: text content string."',
         '"description": "Generate a PDF report from text content and save it to the app data directory. Input: text content string."'),
    ], "CELL4-paths")
    if "/content/drive" in text:
        raise SystemExit("❌ BUILD FAILED — CELL 4 still contains a /content/drive path.")
    return text


# ════════════════════════════════════════════════════════════
# CELL 5 — orchestrator  (Drive → DATA_DIR)
# ════════════════════════════════════════════════════════════
def transform_orchestrator_cell(source):
    text = sub_each(source, [
        ('AUDIT_LOG_PATH = "/content/drive/MyDrive/4cbon2_audit.jsonl"',
         'AUDIT_LOG_PATH = os.path.join(DATA_DIR, "4cbon2_audit.jsonl")'),
        ('TASK_MEMORY_PATH = "/content/drive/MyDrive/4cbon2_task_memory.db"',
         'TASK_MEMORY_PATH = os.path.join(DATA_DIR, "4cbon2_task_memory.db")'),
    ], "CELL5-paths")
    if "/content/drive" in text:
        raise SystemExit("❌ BUILD FAILED — CELL 5 still contains a /content/drive path.")

    # ── Resilience: deadline, heartbeat, and a live view for cancellation ──
    text = sub_once(
        text,
        "def run_orchestrator_stream(goal, model_name=None):\n"
        '    yield f"🚀 **Orchestrator started:** {goal}\\n\\n---\\n"\n'
        '    log_event("orchestrator_start", {"goal": goal})',

        "def run_orchestrator_stream(goal, model_name=None, deadline_seconds=None):\n"
        "    # A wall-clock deadline, because 12 subtasks x up to 3 tool iterations is\n"
        "    # 30+ sequential Gemini calls and a Space proxy will drop a run that takes\n"
        "    # too long. On expiry we stop and report what completed, rather than dying.\n"
        "    if deadline_seconds is None:\n"
        "        deadline_seconds = AGENT_DEADLINE_SECONDS\n"
        "    started_at = time.time()\n"
        "    deadline_hit = False\n"
        '    yield f"🚀 **Orchestrator started:** {goal}\\n\\n---\\n"\n'
        '    yield f"⏱️ Deadline {int(deadline_seconds)}s · thinking {THINKING_LEVEL or \'model default\'} · output floor {MIN_OUTPUT_TOKENS} tokens\\n\\n"\n'
        '    log_event("orchestrator_start", {"goal": goal, "deadline_seconds": deadline_seconds})',
        "CELL5-deadline-setup",
    )

    # Publish a live view so the UI can persist partial work if Gradio cancels us.
    # GeneratorExit derives from BaseException, so no `except Exception` here can
    # catch it — the caller handles that and needs access to this progress.
    text = sub_once(
        text,
        "    subtask_results = []\n"
        "    for i, item in enumerate(plan, 1):",

        "    subtask_results = []\n"
        "    # Same list object, so _ACTIVE_RUN always reflects live progress.\n"
        '    _ACTIVE_RUN["goal"] = goal\n'
        '    _ACTIVE_RUN["results"] = subtask_results\n'
        "    for i, item in enumerate(plan, 1):",
        "CELL5-active-run",
    )

    text = sub_once(
        text,
        '        subtask = item.get("subtask", f"Subtask {i}")',

        "        elapsed = time.time() - started_at\n"
        "        if deadline_seconds and elapsed > deadline_seconds:\n"
        "            deadline_hit = True\n"
        '            yield (f"\\n⏹️ **Deadline reached** ({int(deadline_seconds)}s) after "\n'
        '                   f"{len(subtask_results)} of {len(plan)} subtask(s).\\n"\n'
        '                   f"Synthesising a partial report from what completed.\\n\\n")\n'
        '            log_event("orchestrator_deadline", {"goal": goal, "elapsed_s": round(elapsed, 1),\n'
        '                                                "completed": len(subtask_results), "planned": len(plan)})\n'
        "            break\n"
        '        subtask = item.get("subtask", f"Subtask {i}")',
        "CELL5-deadline-check",
    )

    # Heartbeat while a specialist works, so the stream is never idle and a proxy
    # does not mistake a slow Gemini call for a dead connection.
    text = sub_once(
        text,
        '        yield f"⏳ Executing `{specialist}`...\\n"\n'
        "        result = execute_agent(\n"
        "            specialist,\n"
        '            f"Task: {subtask}\\n\\nInstructions: {instructions}\\n\\nContext: {context}"\n'
        "        )",

        '        yield f"⏳ Executing `{specialist}`...\\n"\n'
        "        result = None\n"
        "        for kind, payload in call_with_heartbeat(\n"
        "            execute_agent,\n"
        "            specialist,\n"
        '            f"Task: {subtask}\\n\\nInstructions: {instructions}\\n\\nContext: {context}",\n'
        "            label=specialist,\n"
        "        ):\n"
        '            if kind == "hb":\n'
        "                yield payload\n"
        "            else:\n"
        "                result = payload",
        "CELL5-heartbeat",
    )

    text = sub_once(
        text,
        '    yield f"\\n## 🧬 Final Answer\\n{final_answer}\\n\\n---\\n*Generated by 4CBON2 (Gemini Edition)*\\n"\n'
        "\n"
        '    log_event("orchestrator_complete", {"goal": goal, "steps": len(subtask_results)})',

        '    if deadline_hit:\n'
        '        yield ("\\n> ⚠️ **Partial run** — the deadline expired before every planned subtask "\n'
        '               f"ran. {len(subtask_results)} of {len(plan)} completed.\\n")\n'
        '    yield f"\\n## 🧬 Final Answer\\n{final_answer}\\n\\n---\\n*Generated by 4CBON2 (Gemini Edition)*\\n"\n'
        "\n"
        '    log_event("orchestrator_complete", {"goal": goal, "steps": len(subtask_results),\n'
        '                                        "deadline_hit": deadline_hit,\n'
        '                                        "elapsed_s": round(time.time() - started_at, 1)})\n'
        '    _ACTIVE_RUN["goal"] = None\n'
        '    _ACTIVE_RUN["results"] = []',
        "CELL5-completion",
    )
    return text


def transform_execute_agent_guards(source):
    """Give execute_agent the same error guard the Rewriter already has.

    execute_agent lives in CELL 5 (the orchestrator), not CELL 4.

    Without this, an '⚠️ API Error: ...' string satisfies the notebook's only
    check (`"could not generate" in ...`), gets accepted as the specialist's
    finding, and is silently propagated into the batch and final synthesis.
    """
    return sub_each(source, [
        ('    if not final_content or final_content.strip() == "" or "could not generate" in final_content.lower():',
         '    if is_llm_error(final_content) or "could not generate" in str(final_content or "").lower():'),
        ('        if not final_content or final_content.strip() == "":\n'
         '            final_content = f"⚠️ {agent_id} could not generate a response. Please provide more specific instructions or data."',
         '        if is_llm_error(final_content):\n'
         '            detail = str(final_content or "").strip()[:220]\n'
         '            final_content = (f"⚠️ {agent_id} could not generate a response. "\n'
         '                             f"Please provide more specific instructions or data. "\n'
         '                             f"Last error: {detail}")'),
    ], "CELL4-llm-error-guard")


# ════════════════════════════════════════════════════════════
# CELL 6 — RAG + live scholarly databases (context propagation)
# ════════════════════════════════════════════════════════════
def transform_rag_cell(source):
    return sub_once(
        source,
        '    with concurrent.futures.ThreadPoolExecutor(max_workers=min(8, len(names) + 1)) as pool:\n'
        '        future_map = {pool.submit(DATABASE_SEARCHERS[name], question, per_database): name for name in names}\n'
        '        future_map[pool.submit(search_official_web, question, domains, per_database)] = "Official web"',

        '    with concurrent.futures.ThreadPoolExecutor(max_workers=min(8, len(names) + 1)) as pool:\n'
        '        # Threads do not inherit the caller\'s contextvars, so each worker gets\n'
        '        # its own snapshot to keep this request\'s API key binding visible.\n'
        '        future_map = {pool.submit(contextvars.copy_context().run, DATABASE_SEARCHERS[name], question, per_database): name for name in names}\n'
        '        future_map[pool.submit(contextvars.copy_context().run, search_official_web, question, domains, per_database)] = "Official web"',
        "CELL6-contextvars",
    )


# ════════════════════════════════════════════════════════════
# CELL 7 — AI Rewriter: apply the revised pipeline (L3 before LP)
# ════════════════════════════════════════════════════════════
def transform_rewriter_cell(source):
    text = source

    # ── 1. Header comment: no longer the Colab OAuth path ──
    text = sub_once(
        text,
        "# This cell is intentionally self-contained after Cells 1–6. It uses the\n"
        "# authenticated google.colab.ai safe_ask_raw path; no provider API key is needed.",

        "# This cell is intentionally self-contained after Cells 1–6. It runs through the\n"
        "# same safe_ask_raw path as every other cell — the Google Generative AI API, with\n"
        "# the visitor's key bound to the current request context.",
        "RW-header",
    )

    # ── 2. RUNTIME_SPEC: pipeline order gains LP after L3 ──
    text = sub_once(
        text,
        "PIPELINE: L0 → P → W → LX → LA → LC → L1 → L2 → L3 → L4 → LR → L6 → L7 → L8 → L9 → L10",
        "PIPELINE: L0 → P → W → LX → LA → LC → L1 → L2 → L3 → LP → L4 → LR → L6 → L7 → L8 → L9 → L10",
        "RW-spec-order",
    )

    # ── 3. RUNTIME_SPEC: LP finally gets a layer definition ──
    text = sub_once(
        text,
        "L3 — REWRITE PLANNER: Plan the rewrite. Decide what stays, changes, gets added.\n"
        "L4 — FINALIZATION ENGINE:",

        "L3 — REWRITE PLANNER: Plan the rewrite. Decide what stays, changes, gets added.\n"
        "LP — POLICY TRANSLATION LAYER: Check whether the rewrite plan inverts the original claim. Halt if YES.\n"
        "L4 — FINALIZATION ENGINE:",
        "RW-spec-LP-definition",
    )

    # ── 4. LAYERS: L3 moves above LP ──
    text = sub_once(
        text,
        '    {"id": "LP", "name": "Policy Translation", "color": "#8b5cf6", "emoji": "⊛"},\n'
        '    {"id": "L3", "name": "Rewrite Planner", "color": "#7c3aed", "emoji": "◐"},',

        '    {"id": "L3", "name": "Rewrite Planner", "color": "#7c3aed", "emoji": "◐"},\n'
        '    {"id": "LP", "name": "Policy Translation", "color": "#8b5cf6", "emoji": "⊛"},',
        "RW-LAYERS-order",
    )

    # ── 5. PIPELINE_ORDER: the authoritative execution order ──
    text = sub_once(
        text,
        'PIPELINE_ORDER = ["L0", "P", "W", "LX", "LA", "LC", "L1", "L2", "LP", "L3", "L4", "LR", "L6", "L7", "L8", "L9", "L10"]',
        'PIPELINE_ORDER = ["L0", "P", "W", "LX", "LA", "LC", "L1", "L2", "L3", "LP", "L4", "LR", "L6", "L7", "L8", "L9", "L10"]',
        "RW-PIPELINE_ORDER",
    )

    # ── 6. QUESTION_BANK / Supabase comments ──
    text = sub_once(
        text,
        "# 100-QUESTION BANK — the external curriculum",
        "# 100-QUESTION BANK",
        "RW-bank-comment",
    )
    text = sub_once(
        text,
        "# Optional Supabase memory. The notebook remains fully usable before this is\n"
        "# configured; service-role credentials must only be set as Colab secrets/env vars.",
        "# Optional Supabase memory. The app remains fully usable before this is\n"
        "# configured; service-role credentials must only be set as Space secrets/env vars.",
        "RW-supabase-comment",
    )

    # ── 7. _llm_error: drop the redundant duplicate check ──
    text = sub_once(
        text,
        '    return (not value) or value.startswith("⚠️") or value.startswith(\'{"error"\') or value.startswith("{\\"error\\"")',
        '    return (not value) or value.startswith("⚠️") or value.startswith(\'{"error"\')',
        "RW-_llm_error",
    )

    # ── 8. THE LP FIX: gate on the L3 rewrite plan, not L2 scoring text ──
    text = sub_once(
        text,
        'def _lp(answer, l2):\n'
        "    return f'Claim: \"{answer[:200]}\"\\nProposal: \"{l2[:200]}\"\\n\\nDoes Proposal say the OPPOSITE of Claim? Answer with just one word: YES or NO'",

        "# ─── LP FIX ────────────────────────────────────────────────\n"
        "# LP now receives the L3 REWRITE PLAN (not L2 scoring text).\n"
        "# It checks whether the plan's proposed changes directly contradict\n"
        "# the original answer's core factual claim — not whether L2's evaluation\n"
        "# language sounds like an inversion.\n"
        "def _lp(answer, l3):\n"
        "    return (f'ORIGINAL ANSWER (first 200 chars):\\n\"{answer[:200]}\"\\n\\n'\n"
        "            f'REWRITE PLAN FROM L3 (first 200 chars):\\n\"{l3[:200]}\"\\n\\n'\n"
        "            f'Does the rewrite plan DIRECTLY CONTRADICT or REVERSE the core factual claim of the original?\\n'\n"
        "            f'Ignore: added caveats, restructuring, tone changes, safety additions, evidence qualifications.\\n'\n"
        "            f'Only answer YES if the plan explicitly states the OPPOSITE of the original core claim.\\n'\n"
        "            f'Answer with exactly one word: YES or NO')\n"
        "# ───────────────────────────────────────────────────────────",
        "RW-_lp-fix",
    )

    # ── 9. Stricter scorer prompt ──
    text = sub_once(
        text,
        '        prompt = f"Rate the quality of this AI-generated answer 0-100.\\nCriteria: Clarity (0-25), Structure (0-25), Depth (0-25), Correctness (0-25).\\nANSWER:\\n{text[:1200]}\\nReply with ONLY a single integer 0-100. Nothing else."',

        '        prompt = (f"Rate the quality of this AI-generated answer 0-100. Be strict.\\n"\n'
        '                  f"Criteria: Clarity (0-25), Structure (0-25), Depth (0-25), Correctness (0-25).\\n"\n'
        '                  f"Penalize heavily for: vague claims, missing citations, unqualified predictions, "\n'
        '                  f"no risk analysis, no definitions, anecdotal evidence, no concrete numbers.\\n"\n'
        '                  f"ANSWER:\\n{text[:1200]}\\n"\n'
        '                  f"Reply with ONLY a single integer 0-100. Nothing else.")',
        "RW-strict-scorer",
    )

    # ── 10. NO_REWRITE status wording ──
    text = sub_once(
        text,
        '            result["status"] = "HIGH QUALITY MODE: No improvement found. Original answer is stronger than any available rewrite. Your input is excellent."',
        '            result["status"] = ("HIGH QUALITY MODE: No improvement found. "\n'
        '                                "Original answer is stronger than any available rewrite.")',
        "RW-no_rewrite-status",
    )

    # ── 11. Execution order: L3 runs BEFORE LP, and LP inspects L3 ──
    text = sub_once(
        text,
        '        failed_layer = "LP"\n'
        '        result["outputs"]["LP"] = _run_layer("LP", LAYER_PROMPTS["LP"](answer, l2), max_tokens=5)\n'
        '        if result["outputs"]["LP"].strip().upper().startswith("YES"):\n'
        '            result["score_after"] = score_before\n'
        '            result["status"] = "LP HALT — proposed change inverts the original claim. Pipeline stopped to prevent structural inversion."\n'
        '            return result\n'
        '        failed_layer = "L3"\n'
        '        result["outputs"]["L3"] = _run_layer("L3", LAYER_PROMPTS["L3"](answer, l2, result["outputs"]["W"]))\n'
        '        failed_layer = "L4"',

        '        # ── L3 runs BEFORE LP ──────────────────────────────\n'
        '        failed_layer = "L3"\n'
        '        result["outputs"]["L3"] = _run_layer("L3", LAYER_PROMPTS["L3"](answer, l2, result["outputs"]["W"]))\n'
        '\n'
        '        # ── LP checks the L3 REWRITE PLAN, not L2 scoring text ──\n'
        '        failed_layer = "LP"\n'
        '        result["outputs"]["LP"] = _run_layer("LP", LAYER_PROMPTS["LP"](\n'
        '            answer, result["outputs"]["L3"]), max_tokens=5)\n'
        '        if result["outputs"]["LP"].strip().upper().startswith("YES"):\n'
        '            result["score_after"] = score_before\n'
        '            result["status"] = ("LP HALT — rewrite plan inverts the original claim. "\n'
        '                                "Pipeline stopped to prevent structural inversion.")\n'
        '            return result\n'
        '        # ───────────────────────────────────────────────────\n'
        '\n'
        '        failed_layer = "L4"',
        "RW-execution-order",
    )

    # ── 12. Scorer thread pool must see this request's API key ──
    text = sub_once(
        text,
        "    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:\n"
        "        futures = [pool.submit(_score_single, text, original_score) for _ in range(3)]",

        "    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:\n"
        "        # Each worker gets its own snapshot of this request's context, because\n"
        "        # threads do not inherit contextvars and _score_single needs the key.\n"
        "        futures = [pool.submit(contextvars.copy_context().run, _score_single, text, original_score)\n"
        "                   for _ in range(3)]",
        "RW-scorer-contextvars",
    )

    # ── Guards ──
    if "google.colab" in text:
        raise SystemExit("❌ BUILD FAILED — Rewriter still references google.colab.")
    order = re.search(r'PIPELINE_ORDER = \[(.*?)\]', text, re.S).group(1)
    if order.index('"L3"') > order.index('"LP"'):
        raise SystemExit("❌ BUILD FAILED — PIPELINE_ORDER still has LP before L3.")
    if 'def _lp(answer, l2)' in text:
        raise SystemExit("❌ BUILD FAILED — _lp still takes l2.")
    return text


def extract_ask_functions(cell_source):
    """Lift ask_stream/ask verbatim from the notebook's CELL 2.

    These carry the long frontier-research prompt (EVIDENCE RULES + the five
    answer headings). The surrounding Colab client is replaced, but the prompt
    itself must not be retyped by hand.
    """
    match = re.search(r"^def ask_stream\(.*?(?=^print\()", cell_source, re.S | re.M)
    if not match:
        raise SystemExit("❌ BUILD FAILED — could not locate ask_stream/ask in CELL 2.")
    blob = match.group(0).rstrip("\n")
    for required in ("def ask_stream(", "def ask(", "EVIDENCE RULES", "BEST CURRENT ANSWER"):
        if required not in blob:
            raise SystemExit(f"❌ BUILD FAILED — ask_stream/ask blob missing '{required}'.")
    if "google.colab" in blob or "ai.generate_text" in blob:
        raise SystemExit("❌ BUILD FAILED — ask_stream/ask blob still calls the Colab client.")
    REPLACEMENTS_APPLIED.append("ask_stream/ask lifted verbatim")
    return blob


def top_level_defs(source):
    return set(re.findall(r"^def (\w+)\(", source, re.M))


def extract_curated_databases(cell_source):
    """Pull the CURATED_DATABASES literal out of the notebook's CELL 3."""
    match = re.search(r"^CURATED_DATABASES = \{.*?^\}\n", cell_source, re.S | re.M)
    if not match:
        raise SystemExit("❌ BUILD FAILED — could not locate CURATED_DATABASES in CELL 3.")
    blob = match.group(0)
    for domain in ("ai", "mathematics", "science"):
        if f'"{domain}": [' not in blob:
            raise SystemExit(f"❌ BUILD FAILED — CURATED_DATABASES missing '{domain}'.")
    REPLACEMENTS_APPLIED.append("CURATED_DATABASES lifted verbatim")
    return blob.rstrip("\n")


def code_only_view(source):
    """Source with docstrings and comments removed, so guards test code not prose.

    The module docstring deliberately mentions ``google.colab.ai`` and
    ``/content/drive`` while explaining what changed; those must not trip a guard
    that exists to catch leftover Colab code. String literals are preserved,
    because real paths live in string literals.
    """
    import ast
    import io
    import tokenize

    tree = ast.parse(source)
    docstring_lines = set()
    for node in ast.walk(tree):
        if isinstance(node, (ast.Module, ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            body = getattr(node, "body", None)
            if body and isinstance(body[0], ast.Expr) and isinstance(body[0].value, ast.Constant) \
                    and isinstance(body[0].value.value, str):
                doc = body[0]
                docstring_lines.update(range(doc.lineno, (doc.end_lineno or doc.lineno) + 1))

    comment_lines = set()
    for token in tokenize.generate_tokens(io.StringIO(source).readline):
        if token.type == tokenize.COMMENT:
            comment_lines.add(token.start[0])

    kept = []
    for number, line in enumerate(source.splitlines(), start=1):
        if number in docstring_lines or number in comment_lines:
            continue
        # Drop trailing comments on code lines (best effort; ignores '#' in strings).
        if "#" in line:
            prefix = line.split("#", 1)[0]
            if prefix.count('"') % 2 == 0 and prefix.count("'") % 2 == 0:
                line = prefix
        kept.append(line)
    return "\n".join(kept)


def main():
    cells = load_notebook_cells()
    if len(cells) != 8:
        raise SystemExit(f"❌ BUILD FAILED — expected 8 code cells, found {len(cells)}.")
    sections = load_sections()

    app = "\n\n\n".join([
        sections["header"],
        sections["imports"],
        sections["llm"],
        extract_ask_functions(cells[1]),
        sections["chroma_pre"],
        extract_curated_databases(cells[2]),
        sections["chroma_post"],
        transform_agents_cell(cells[3]),
        sections["orch_helpers"],
        transform_execute_agent_guards(transform_orchestrator_cell(cells[4])),
        transform_rag_cell(cells[5]),
        transform_rewriter_cell(cells[6]),
        sections["ui"],
    ]) + "\n"

    # ── Completeness guard ───────────────────────────────────
    # Every top-level function the notebook defines must survive the port. A
    # missing one is a NameError at runtime, deep inside a Gradio callback — this
    # catches it at build time instead.
    notebook_defs = set()
    for cell in cells[1:7]:
        notebook_defs |= top_level_defs(cell)
    generated_defs = top_level_defs(app)
    # Renamed on purpose: the UI callback gains the API-key parameters.
    intentionally_changed = set()
    missing = sorted(notebook_defs - generated_defs - intentionally_changed)
    if missing:
        raise SystemExit(f"❌ BUILD FAILED — {len(missing)} notebook function(s) missing from app.py:\n   "
                         + ", ".join(missing))
    REPLACEMENTS_APPLIED.append(f"all {len(notebook_defs)} notebook functions present")

    # Guard against Colab/Space-hostile CODE. Prose mentions of google.colab.ai in
    # the module docstring are intentional (they explain what changed), so these
    # patterns target imports and call sites rather than the bare string.
    banned_code = (
        "from google.colab",
        "import google.colab",
        "ai.list_models(",
        "drive.mount",
        "/content/drive/MyDrive/",
        "share=True",
        "InferenceClient",
        "huggingface_hub",
        "import torch",
    )
    code_view = code_only_view(app)
    for banned in banned_code:
        if banned in code_view:
            offending = next((l.strip() for l in code_view.splitlines() if banned in l), "")
            raise SystemExit(f"❌ BUILD FAILED — generated code still contains '{banned}':\n   {offending[:200]}")

    # The LLM layer must really be the Google Generative AI API.
    for required in ("from google import genai", "genai.Client(api_key=", "gemini-3.6-flash"):
        if required not in code_view:
            raise SystemExit(f"❌ BUILD FAILED — generated code is missing '{required}'.")

    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, "app.py"), "w", encoding="utf-8") as handle:
        handle.write(app)

    compile(app, "app.py", "exec")

    print(f"✅ Wrote {os.path.relpath(os.path.join(OUT_DIR, 'app.py'), ROOT)} "
          f"({len(app.splitlines())} lines, {len(app):,} chars)")
    print(f"✅ Syntax compiles. {len(REPLACEMENTS_APPLIED)} guarded replacements applied.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
