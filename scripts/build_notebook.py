#!/usr/bin/env python3
"""Builds the canonical 4CBOn2_Kaggle6.ipynb notebook (Divisions 28-31, extended in v0.3 Division 51)."""
import json
from pathlib import Path


def md_cell(source_text: str) -> dict:
    lines = [line + "\n" for line in source_text.strip("\n").split("\n")]
    if lines:
        lines[-1] = lines[-1].rstrip("\n")
    return {
        "cell_type": "markdown",
        "metadata": {},
        "source": lines,
    }


def code_cell(source_text: str) -> dict:
    lines = [line + "\n" for line in source_text.strip("\n").split("\n")]
    if lines:
        lines[-1] = lines[-1].rstrip("\n")
    return {
        "cell_type": "code",
        "execution_count": None,
        "metadata": {"trusted": True},
        "outputs": [],
        "source": lines,
    }


CELL_1_HEADER_COMMENT = """# ==============================================================================
# 4CBOn2 — Kaggle6 Unified Agent Core (4CBOn2_Kaggle6.ipynb)
# Canonical repository target: mohamtur1/4CBOn2 -> 4CBOn2_Kaggle6.ipynb
#
# Architecture & Role Boundary:
# - Colab Controller (COLAB_CONTROLLER): Uses built-in google.colab.ai for
#   bounded planning, evidence synthesis, and job bundle preparation.
# - Kaggle Runner (KAGGLE_RUNNER): Executes revalidated 4cbon2.job_bundle.v1
#   bundles, tabular validation pipelines, and ARC Prize 2026 (ARC-AGI-3)
#   offline game-playing submissions (/kaggle/working/submission.parquet).
# - All cells are valid Python 3 so both 'File -> Import Notebook' AND direct
#   copy-paste into a Kaggle/Colab code cell work without SyntaxError.
# =============================================================================="""

CELL_2_RUNTIME = '''import hashlib
import importlib
import json
import os
import re
import sqlite3
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Optional, Set, Tuple

BUNDLE_SCHEMA_V1 = "4cbon2.job_bundle.v1"
REQUIRED_AI_PROVIDER = "google.colab.ai"

ROLE_ANDROID = "ANDROID_COMPANION"
ROLE_COLAB = "COLAB_CONTROLLER"
ROLE_KAGGLE = "KAGGLE_RUNNER"
ROLE_LOCAL = "LOCAL_SANDBOX"


@dataclass(frozen=True)
class RuntimeDetectionResult:
    role: str
    is_colab: bool
    is_kaggle: bool
    colab_ai_module_importable: bool
    working_dir: str


def detect_runtime_environment(
    env: Optional[Dict[str, str]] = None,
    module_probe: Optional[Callable[[str], bool]] = None,
) -> RuntimeDetectionResult:
    """Division 29: Detects Colab Controller vs Kaggle Runner vs Local Sandbox honestly."""
    environ = env if env is not None else dict(os.environ)

    def _default_probe(mod_name: str) -> bool:
        try:
            importlib.import_module(mod_name)
            return True
        except Exception:
            return False

    probe = module_probe or _default_probe
    is_kaggle = bool(environ.get("KAGGLE_KERNEL_RUN_TYPE")) or os.path.isdir("/kaggle/working")
    is_colab = (not is_kaggle) and (
        bool(environ.get("COLAB_RELEASE_TAG"))
        or bool(environ.get("COLAB_GPU"))
        or probe("google.colab")
    )
    colab_ai_ok = is_colab and probe("google.colab.ai")

    if is_colab:
        role = ROLE_COLAB
        work_dir = environ.get("FOURCBON2_WORKDIR", "/content/4cbon2_workspace")
    elif is_kaggle:
        role = ROLE_KAGGLE
        work_dir = environ.get("FOURCBON2_WORKDIR", "/kaggle/working/4cbon2_workspace")
    else:
        role = ROLE_LOCAL
        work_dir = environ.get("FOURCBON2_WORKDIR", "./4cbon2_workspace")

    return RuntimeDetectionResult(
        role=role,
        is_colab=is_colab,
        is_kaggle=is_kaggle,
        colab_ai_module_importable=colab_ai_ok,
        working_dir=work_dir,
    )


CAPABILITY_DEFINITIONS: List[Tuple[str, str, bool]] = [
    ("Task Automation", "Core workflow engine & bounded queue", False),
    ("Web Browsing", "Approved public HTTPS reader & source adapters", False),
    ("Planning and Reasoning", "Supervisor + google.colab.ai (Colab only)", True),
    ("Tool Integration", "Typed tool registry & permission broker", False),
    ("File Management", "Scoped workspace file import/export with SHA-256", False),
    ("Code Execution", "Reviewed tabular/analysis templates only", False),
    ("Data Analysis", "Deterministic CSV/JSON & tabular validation", False),
    ("Multi-Step Execution", "Bounded task loop with step/retry caps", False),
    ("Memory and Context", "SQLite working/knowledge/procedural/experimental memory", False),
    ("Decision-Making", "Policy-checked action selection", False),
    ("Self-Correction", "Max 2 safe retries + 1 bounded replan within budget", False),
    ("Monitoring", "Real action timeline & outcome states", False),
    ("Communication", "Local/notebook reports; outbound gated", False),
    ("Human Oversight", "Scoped approval grants & Stop All cancellation", False),
    ("Continuous Operation", "Active-session queue without anti-idle hacks", False),
]


def build_capability_panel(
    runtime: RuntimeDetectionResult,
    colab_ai_verified: bool = False,
    external_auth_configured: bool = False,
) -> List[Dict[str, str]]:
    """Returns truthful availability across all 15 unified architecture capabilities."""
    panel: List[Dict[str, str]] = []
    for name, module_desc, requires_colab_ai in CAPABILITY_DEFINITIONS:
        if requires_colab_ai:
            if runtime.role == ROLE_COLAB and colab_ai_verified:
                state = "Available"
            elif runtime.role == ROLE_COLAB:
                state = "Needs authorization"
            else:
                state = "Unavailable in this runtime"
        elif name in ("Web Browsing", "Communication") and not external_auth_configured:
            state = "Available (read/draft only; external actions need authorization)"
        else:
            state = "Available"
        panel.append({"capability": name, "module": module_desc, "status": state})
    return panel
'''

CELL_3_BUNDLE = '''FORBIDDEN_SECRET_PATTERNS = [
    re.compile(r"\\bgithub_pat_[A-Za-z0-9_]{12,}\\b"),
    re.compile(r"\\bgh[pousr]_[A-Za-z0-9]{16,}\\b"),
    re.compile(r"\\bKGAT_[A-Za-z0-9_\\-]{10,}\\b"),
    re.compile(r"(?i)Bearer\\s+[A-Za-z0-9._\\-]{12,}"),
    re.compile(r"COLAB_KERNEL_PROXY"),
    re.compile(r"COLAB_TENSORBOARD"),
]

FORBIDDEN_KEY_SUBSTRINGS = ("secret", "password", "token", "colab_proxy", "api_key")


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def redact_secrets(text: str) -> str:
    out = text
    for pat in FORBIDDEN_SECRET_PATTERNS:
        out = pat.sub("[REDACTED_SECRET]", out)
    return out


def contains_forbidden_secret(text: str) -> bool:
    return any(pat.search(text) is not None for pat in FORBIDDEN_SECRET_PATTERNS)


def canonical_bundle_payload_json(manifest_fields: Dict[str, Any]) -> str:
    """Exact field order matching Android SharedJobBundleManifest.canonicalPayloadJson."""
    ordered = {
        "schemaVersion": manifest_fields["schemaVersion"],
        "bundleId": manifest_fields["bundleId"],
        "jobId": manifest_fields["jobId"],
        "title": manifest_fields["title"],
        "goal": manifest_fields["goal"],
        "profile": manifest_fields["profile"],
        "sourceRole": manifest_fields["sourceRole"],
        "targetRole": manifest_fields["targetRole"],
        "maxSteps": int(manifest_fields["maxSteps"]),
        "maxModelCalls": int(manifest_fields["maxModelCalls"]),
        "maxRuntimeMinutes": int(manifest_fields["maxRuntimeMinutes"]),
        "requestedActions": list(manifest_fields.get("requestedActions", [])),
        "artifacts": [
            {
                "artifactId": a["artifactId"],
                "fileName": a["fileName"],
                "mimeType": a["mimeType"],
                "sizeBytes": int(a["sizeBytes"]),
                "sha256Hex": str(a["sha256Hex"]).lower(),
            }
            for a in manifest_fields.get("artifacts", [])
        ],
        "createdAtEpochMs": int(manifest_fields["createdAtEpochMs"]),
    }
    return json.dumps(ordered, separators=(",", ":"), ensure_ascii=False)


def create_shared_job_bundle(
    bundle_id: str,
    job_id: str,
    title: str,
    goal: str,
    profile: str,
    source_role: str = ROLE_COLAB,
    target_role: str = ROLE_KAGGLE,
    max_steps: int = 10,
    max_model_calls: int = 8,
    max_runtime_minutes: int = 15,
    requested_actions: Optional[List[str]] = None,
    artifacts: Optional[List[Dict[str, Any]]] = None,
    created_at_epoch_ms: Optional[int] = None,
) -> Dict[str, Any]:
    """Division 28: Creates a credential-free, SHA-256 integrity-checked job bundle."""
    if contains_forbidden_secret(title) or contains_forbidden_secret(goal):
        raise ValueError("Refusing to create bundle containing credential or proxy-token markers.")
    if not (1 <= max_steps <= 100 and 0 <= max_model_calls <= 24 and 1 <= max_runtime_minutes <= 60):
        raise ValueError("Bundle execution caps are outside permitted bounds.")

    ts = int(created_at_epoch_ms if created_at_epoch_ms is not None else time.time() * 1000)
    clean_artifacts = []
    for art in (artifacts or []):
        fname = str(art["fileName"])
        if not fname or ".." in fname or "/" in fname or "\\\\" in fname:
            raise ValueError(f"Unsafe artifact filename: {fname}")
        digest = str(art["sha256Hex"]).lower()
        if not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError("Invalid artifact SHA-256 hex.")
        clean_artifacts.append(
            {
                "artifactId": str(art["artifactId"]),
                "fileName": fname,
                "mimeType": str(art.get("mimeType", "application/octet-stream")),
                "sizeBytes": int(art["sizeBytes"]),
                "sha256Hex": digest,
            }
        )

    fields = {
        "schemaVersion": BUNDLE_SCHEMA_V1,
        "bundleId": bundle_id,
        "jobId": job_id,
        "title": title.strip(),
        "goal": goal.strip(),
        "profile": profile,
        "sourceRole": source_role,
        "targetRole": target_role,
        "maxSteps": max_steps,
        "maxModelCalls": max_model_calls,
        "maxRuntimeMinutes": max_runtime_minutes,
        "requestedActions": list(requested_actions or []),
        "artifacts": clean_artifacts,
        "createdAtEpochMs": ts,
    }
    canonical = canonical_bundle_payload_json(fields)
    fields["manifestSha256"] = sha256_hex(canonical.encode("utf-8"))
    return fields


def import_and_revalidate_bundle(
    raw_json: str,
    locally_allowed_actions: Optional[Set[str]] = None,
    supplied_artifact_bytes: Optional[Dict[str, bytes]] = None,
) -> Dict[str, Any]:
    """
    Division 28: Validates bundle integrity and re-checks local policy.
    A manifest hash is integrity, NOT authority: embedded 'approved: true' flags are stripped/ignored.
    """
    if not raw_json or len(raw_json) > 262_144:
        raise ValueError("Bundle JSON is empty or exceeds size cap.")
    if contains_forbidden_secret(raw_json):
        raise ValueError("Bundle rejected: contains forbidden credential or Colab proxy marker.")

    parsed = json.loads(raw_json)
    if not isinstance(parsed, dict):
        raise ValueError("Bundle root must be a JSON object.")

    bad_keys = [
        k for k in parsed.keys()
        if any(sub in k.lower() for sub in FORBIDDEN_KEY_SUBSTRINGS)
    ]
    if bad_keys:
        raise ValueError(f"Bundle contains prohibited secret key(s): {bad_keys}")

    if parsed.get("schemaVersion") != BUNDLE_SCHEMA_V1:
        raise ValueError(f"Unsupported schemaVersion: {parsed.get('schemaVersion')}")

    max_steps = int(parsed.get("maxSteps", 0))
    max_calls = int(parsed.get("maxModelCalls", -1))
    max_mins = int(parsed.get("maxRuntimeMinutes", 0))
    if not (1 <= max_steps <= 100 and 0 <= max_calls <= 24 and 1 <= max_mins <= 60):
        raise ValueError("Bundle resource caps out of range.")

    expected_canonical = canonical_bundle_payload_json(parsed)
    actual_sha = sha256_hex(expected_canonical.encode("utf-8"))
    if actual_sha != str(parsed.get("manifestSha256", "")).lower():
        raise ValueError("Bundle manifestSha256 mismatch (tampered or corrupted).")

    artifact_map = supplied_artifact_bytes or {}
    for art in parsed.get("artifacts", []):
        fname = art["fileName"]
        if fname in artifact_map:
            if sha256_hex(artifact_map[fname]) != str(art["sha256Hex"]).lower():
                raise ValueError(f"Artifact '{fname}' failed SHA-256 check.")

    allowed = set(locally_allowed_actions or set())
    requested = list(parsed.get("requestedActions", []))
    permitted = [a for a in requested if a in allowed]
    denied = [a for a in requested if a not in allowed]

    return {
        "manifest": {
            k: parsed[k]
            for k in (
                "schemaVersion", "bundleId", "jobId", "title", "goal", "profile",
                "sourceRole", "targetRole", "maxSteps", "maxModelCalls",
                "maxRuntimeMinutes", "requestedActions", "artifacts",
                "createdAtEpochMs", "manifestSha256",
            )
        },
        "locallyPermittedActions": permitted,
        "deniedRequestedActions": denied,
        "requiresFreshOwnerApproval": True,
    }
'''

CELL_4_COLAB_AI = '''PREFERRED_MODEL_SUBSTRINGS = ("gemini-2.5", "gemini-2.0", "gemini")


class ColabAiUnavailableError(RuntimeError):
    """Raised honestly when google.colab.ai is unavailable in the active runtime."""


class ColabBuiltInAiAdapter:
    """
    Division 30: Bounded adapter around `from google.colab import ai`.
    - Never substitutes another LLM provider or external API key.
    - Never exports Colab internal model-proxy environment variables or tokens.
    - Enforces model-call budgets, prompt/response bounds, and max 2 JSON-schema repair retries.
    """

    def __init__(
        self,
        runtime: RuntimeDetectionResult,
        max_model_calls: int = 8,
        max_prompt_chars: int = 6_000,
        max_response_chars: int = 8_000,
        injected_ai_module: Any = None,
    ) -> None:
        self.runtime = runtime
        self.max_model_calls = max(1, min(max_model_calls, 24))
        self.max_prompt_chars = max_prompt_chars
        self.max_response_chars = max_response_chars
        self.calls_used = 0
        self.selected_model_id: Optional[str] = None
        self._injected_ai = injected_ai_module

    def _load_colab_ai(self) -> Any:
        if self._injected_ai is not None:
            if self.runtime.role != ROLE_COLAB:
                raise ColabAiUnavailableError(
                    f"google.colab.ai is disabled in role '{self.runtime.role}'; hand off to COLAB_CONTROLLER."
                )
            return self._injected_ai

        if self.runtime.role != ROLE_COLAB:
            raise ColabAiUnavailableError(
                f"Live google.colab.ai is unavailable in '{self.runtime.role}' (no silent provider fallback)."
            )
        try:
            from google.colab import ai as colab_ai  # type: ignore
            return colab_ai
        except Exception as exc:
            raise ColabAiUnavailableError(
                "Could not import `from google.colab import ai` in this runtime."
            ) from exc

    def probe_and_select_model(self) -> str:
        ai_mod = self._load_colab_ai()
        models = list(ai_mod.list_models())
        if not models:
            raise ColabAiUnavailableError("google.colab.ai returned no available models for this account.")

        chosen = None
        for pref in PREFERRED_MODEL_SUBSTRINGS:
            for candidate in models:
                if pref in str(candidate).lower():
                    chosen = str(candidate)
                    break
            if chosen:
                break
        self.selected_model_id = chosen or str(models[0])
        return self.selected_model_id

    def generate_bounded_text(self, prompt: str) -> str:
        if self.calls_used >= self.max_model_calls:
            raise RuntimeError(f"Model-call budget exhausted ({self.max_model_calls}).")
        clean_prompt = redact_secrets(prompt.strip())
        if not clean_prompt:
            raise ValueError("Prompt must not be empty.")
        if len(clean_prompt) > self.max_prompt_chars:
            clean_prompt = clean_prompt[: self.max_prompt_chars]

        ai_mod = self._load_colab_ai()
        if self.selected_model_id is None:
            self.probe_and_select_model()

        self.calls_used += 1
        try:
            raw_resp = ai_mod.generate_text(clean_prompt, model_name=self.selected_model_id)
        except TypeError:
            raw_resp = ai_mod.generate_text(clean_prompt)

        sanitized = redact_secrets(str(raw_resp or ""))[: self.max_response_chars]
        return sanitized

    def propose_structured_action(
        self,
        goal: str,
        allowed_tools: Set[str],
    ) -> Dict[str, Any]:
        """
        Requests a strict JSON action object (`{"tool": ..., "args": {...}, "rationale": ...}`)
        with at most 2 format-repair attempts inside the same call budget. Never calls eval/exec.
        """
        base_prompt = (
            "Return ONLY a JSON object with keys 'tool', 'args', and 'rationale'. "
            f"Allowed tools: {sorted(allowed_tools)}. Goal: {goal}"
        )
        last_error = "Unparsed response"
        for attempt in range(3):  # initial + at most 2 repairs
            prompt = (
                base_prompt
                if attempt == 0
                else f"{base_prompt}\\nPrevious output failed schema validation ({last_error}). Output strict JSON only."
            )
            raw = self.generate_bounded_text(prompt)
            try:
                parsed = json.loads(raw)
                if not isinstance(parsed, dict):
                    raise ValueError("Action must be a JSON object.")
                tool = str(parsed.get("tool", ""))
                args = parsed.get("args", {})
                if tool not in allowed_tools:
                    raise ValueError(f"Rejected unknown or unpermitted tool '{tool}'.")
                if not isinstance(args, dict):
                    raise ValueError("'args' must be a JSON object.")
                return {
                    "tool": tool,
                    "args": args,
                    "rationale": redact_secrets(str(parsed.get("rationale", "")))[:300],
                }
            except Exception as exc:
                last_error = str(exc)
        raise ValueError(f"Model output failed strict action schema after repair budget: {last_error}")

    def export_probe_receipt(self, response_text: str, recorded_at_ms: Optional[int] = None) -> Dict[str, Any]:
        """Produces a proxy-secret-free receipt compatible with Android ColabBuiltInAiBridge."""
        if self.runtime.role != ROLE_COLAB or not self.selected_model_id:
            raise ColabAiUnavailableError("Cannot issue verified Colab AI receipt without active COLAB_CONTROLLER probe.")
        bounded = redact_secrets(response_text)[: self.max_response_chars]
        canonical = (
            f"{REQUIRED_AI_PROVIDER}|{self.selected_model_id}|{ROLE_COLAB}|"
            f"{self.calls_used}|{self.max_model_calls}|{bounded}"
        )
        digest = sha256_hex(canonical.encode("utf-8"))
        return {
            "provider": REQUIRED_AI_PROVIDER,
            "selectedModelId": self.selected_model_id,
            "runtimeRole": ROLE_COLAB,
            "modelCallsUsed": self.calls_used,
            "maxModelCalls": self.max_model_calls,
            "boundedResponseText": bounded,
            "receiptSha256": digest,
            "recordedAtEpochMs": int(recorded_at_ms if recorded_at_ms is not None else time.time() * 1000),
        }
'''

CELL_5_AGENT_CORE = '''class UnifiedAgentExecutionCore:
    """
    Division 31: Shared Agent Execution Core implementing:
    Goal -> Capability/Policy Check -> Bounded Plan -> Tool Broker -> Observe -> Verify -> Memory.
    """

    def __init__(
        self,
        runtime: RuntimeDetectionResult,
        sqlite_path: str = ":memory:",
        ai_adapter: Optional[ColabBuiltInAiAdapter] = None,
    ) -> None:
        self.runtime = runtime
        self.ai_adapter = ai_adapter
        self.stopped = False
        self.conn = sqlite3.connect(sqlite_path)
        self._init_memory_tables()
        self.tools: Dict[str, Callable[[Dict[str, Any]], Dict[str, Any]]] = {
            "summarize_csv": self._tool_summarize_csv,
            "lookup_memory": self._tool_lookup_memory,
        }

    def _init_memory_tables(self) -> None:
        cur = self.conn.cursor()
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS scoped_memory (
                memory_type TEXT NOT NULL,
                scope_id TEXT NOT NULL,
                key TEXT NOT NULL,
                value_json TEXT NOT NULL,
                sha256_hex TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (memory_type, scope_id, key)
            )
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS timeline_events (
                seq INTEGER PRIMARY KEY AUTOINCREMENT,
                stage TEXT NOT NULL,
                outcome TEXT NOT NULL,
                summary TEXT NOT NULL
            )
            """
        )
        self.conn.commit()

    def stop_all(self) -> None:
        self.stopped = True
        self._record_event("STOP", "VERIFIED", "Owner triggered Stop All; halting new actions.")

    def save_memory(self, memory_type: str, scope_id: str, key: str, payload: Dict[str, Any]) -> str:
        if memory_type not in ("working", "knowledge", "procedural", "experimental"):
            raise ValueError(f"Unsupported memory_type: {memory_type}")
        encoded = redact_secrets(json.dumps(payload, separators=(",", ":"), sort_keys=True))
        digest = sha256_hex(encoded.encode("utf-8"))
        self.conn.execute(
            """
            INSERT OR REPLACE INTO scoped_memory (memory_type, scope_id, key, value_json, sha256_hex, updated_at_ms)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (memory_type, scope_id, key, encoded, digest, int(time.time() * 1000)),
        )
        self.conn.commit()
        return digest

    def read_memory(self, memory_type: str, scope_id: str) -> List[Dict[str, Any]]:
        cur = self.conn.execute(
            "SELECT key, value_json, sha256_hex FROM scoped_memory WHERE memory_type = ? AND scope_id = ?",
            (memory_type, scope_id),
        )
        return [
            {"key": row[0], "value": json.loads(row[1]), "sha256Hex": row[2]}
            for row in cur.fetchall()
        ]

    def timeline(self) -> List[Dict[str, str]]:
        cur = self.conn.execute("SELECT stage, outcome, summary FROM timeline_events ORDER BY seq ASC")
        return [{"stage": r[0], "outcome": r[1], "summary": r[2]} for r in cur.fetchall()]

    def request_live_ai_plan(self, goal: str, allowed_tools: Set[str]) -> Dict[str, Any]:
        """Uses google.colab.ai in COLAB_CONTROLLER; returns honest handoff in KAGGLE_RUNNER."""
        if self.runtime.role != ROLE_COLAB or self.ai_adapter is None:
            self._record_event(
                "PLAN",
                "CONTROLLER_HANDOFF_REQUIRED",
                f"Live Colab AI unavailable in {self.runtime.role}; return bundle to Colab Controller.",
            )
            return {"status": "CONTROLLER_HANDOFF_REQUIRED", "role": self.runtime.role}

        action = self.ai_adapter.propose_structured_action(goal, allowed_tools)
        self._record_event("PLAN", "VERIFIED", f"Selected permitted tool '{action['tool']}'.")
        return {"status": "PLANNED", "action": action}

    def run_bounded_workflow(
        self,
        scope_id: str,
        goal: str,
        planned_steps: List[Dict[str, Any]],
        permitted_tools: Set[str],
        max_steps: int = 6,
    ) -> Dict[str, Any]:
        """Executes a bounded, verified workflow with max 2 safe retries per transient error."""
        self._record_event("PLAN", "ATTEMPTED", f"Starting bounded goal: {goal}")
        results: List[Dict[str, Any]] = []
        executed = 0

        for step in planned_steps:
            if self.stopped:
                return {"status": "STOPPED", "stepsExecuted": executed, "results": results}
            if executed >= max_steps:
                self._record_event("CHECK", "BLOCKED", f"Reached max_steps cap ({max_steps}).")
                return {"status": "CAPPED", "stepsExecuted": executed, "results": results}

            tool_name = str(step.get("tool", ""))
            args = step.get("args", {})
            if tool_name not in permitted_tools or tool_name not in self.tools:
                msg = f"Rejected unpermitted or unknown tool '{tool_name}'."
                self._record_event("GATE", "BLOCKED", msg)
                return {"status": "BLOCKED", "reason": msg, "stepsExecuted": executed}

            executed += 1
            attempt = 0
            while True:
                attempt += 1
                try:
                    obs = self.tools[tool_name](args)
                    if not obs.get("verified", False):
                        raise ValueError(obs.get("error", "Verification check failed."))
                    results.append(obs)
                    self.save_memory("working", scope_id, f"step_{executed}", obs)
                    self._record_event("CHECK", "VERIFIED", f"Verified tool '{tool_name}'.")
                    break
                except Exception as exc:
                    if attempt <= 2 and step.get("retryable", False):
                        continue
                    err_msg = f"Tool '{tool_name}' failed: {exc}"
                    self._record_event("ACTION", "FAILED", err_msg)
                    return {"status": "FAILED", "reason": err_msg, "stepsExecuted": executed}

        return {"status": "COMPLETED_VERIFIED", "stepsExecuted": executed, "results": results}

    def _tool_summarize_csv(self, args: Dict[str, Any]) -> Dict[str, Any]:
        raw_csv = str(args.get("csv", "")).strip()
        lines = [ln.strip() for ln in raw_csv.splitlines() if ln.strip()]
        if len(lines) < 2:
            return {"verified": False, "error": "CSV requires header and >= 1 data row."}
        headers = [h.strip() for h in lines[0].split(",")]
        rows = [[c.strip() for c in ln.split(",")] for ln in lines[1:]]
        if any(len(r) != len(headers) for r in rows):
            return {"verified": False, "error": "Ragged CSV row detected."}
        return {"verified": True, "rowCount": len(rows), "columns": headers}

    def _tool_lookup_memory(self, args: Dict[str, Any]) -> Dict[str, Any]:
        scope_id = str(args.get("scope_id", "default"))
        entries = self.read_memory("knowledge", scope_id)
        return {"verified": True, "count": len(entries), "entries": entries}

    def _record_event(self, stage: str, outcome: str, summary: str) -> None:
        self.conn.execute(
            "INSERT INTO timeline_events (stage, outcome, summary) VALUES (?, ?, ?)",
            (stage, outcome, redact_secrets(summary)),
        )
        self.conn.commit()
'''

CELL_6_PRIVACY_INJECTION = '''INJECTION_PATTERNS = [
    ("instruction_override", re.compile(r"(?i)\\b(ignore|disregard|override)\\s+(all\\s+)?(previous|prior|system|safety)\\s+(instructions|rules|scope)")),
    ("authority_escalation", re.compile(r"(?i)\\b(grant\\s+permission|enable\\s+all\\s+apps|approved\\s*:\\s*true|bypass\\s+approval)")),
    ("exfiltration_directive", re.compile(r"(?i)\\b(send|upload|forward|post|email)\\s+.*\\b(token|password|pat|credential|keystore|otp|secret)s?\\b")),
]

SENSITIVE_QUERY_RE = re.compile(r"(?i)[?&](token|secret|password|api_key|pat|key|otp|auth)=")


def inspect_untrusted_content(source_label: str, raw_text: str) -> Dict[str, Any]:
    """Division 35: Wraps untrusted web/file/message input in an inert data boundary and flags injection patterns."""
    redacted = redact_secrets(raw_text)
    signals = [name for name, pat in INJECTION_PATTERNS if pat.search(raw_text)]
    body = redacted.replace("</untrusted_data>", "[escaped_tag]")[:16_000]
    safe_src = source_label.replace('"', "_")
    envelope = f'<untrusted_data source="{safe_src}" authority="none">\\n{body}\\n</untrusted_data>'
    return {
        "sourceLabel": source_label,
        "sanitizedEnvelope": envelope,
        "injectionSignals": signals,
        "redactedSecrets": redacted != raw_text,
    }


def verify_outbound_transfer(
    destination: str,
    allowed_destinations: Set[str],
    payload: str,
    allow_cloud_transfer: bool = False,
) -> Dict[str, Any]:
    """Blocks unauthorized destinations, secret query leaks, and unconsented cloud data sharing."""
    dest = destination.strip()
    if not dest or not any(dest.lower() == a.lower() or (a.startswith("https://") and dest.lower().startswith(a.lower())) for a in allowed_destinations):
        return {"permitted": False, "reason": f"Destination '{dest}' is outside approved scope."}
    if contains_forbidden_secret(dest) or SENSITIVE_QUERY_RE.search(dest):
        return {"permitted": False, "reason": "Blocked secret/token leakage in destination URL."}
    if not allow_cloud_transfer:
        return {"permitted": False, "reason": "Data-sharing scope prohibits outbound cloud transfer."}
    if contains_forbidden_secret(payload):
        return {"permitted": False, "reason": "Blocked raw credential or proxy token in payload."}
    return {"permitted": True, "sanitizedPayload": redact_secrets(payload)}
'''

CELL_7_KAGGLE_PIPELINE_AND_GATE = '''import math
import random


def prepare_tabular_baseline(
    competition_slug: str,
    train_csv: str,
    test_csv: str,
    id_col: str = "id",
    target_col: str = "target",
    folds: int = 3,
    l2_reg: float = 1.0,
    seed: int = 42,
) -> Dict[str, Any]:
    """
    Division 37: Trusted, deterministic prepare-only tabular baseline pipeline.
    Validates schema, runs seeded k-fold regularized linear baseline, checks output artifact,
    and performs zero network calls or live submissions.
    """
    def _parse(csv_text: str) -> Tuple[List[str], List[List[str]]]:
        lines = [ln.strip() for ln in csv_text.strip().splitlines() if ln.strip()]
        if len(lines) < 2:
            raise ValueError("CSV requires header and >= 1 row.")
        headers = [h.strip() for h in lines[0].split(",")]
        if any(not h for h in headers) or len(set(headers)) != len(headers):
            raise ValueError("Invalid or duplicate CSV headers.")
        rows = [[c.strip() for c in ln.split(",")] for ln in lines[1:]]
        if any(len(r) != len(headers) for r in rows):
            raise ValueError("Ragged CSV row count.")
        return headers, rows

    tr_h, tr_rows = _parse(train_csv)
    te_h, te_rows = _parse(test_csv)
    if id_col not in tr_h or target_col not in tr_h:
        raise ValueError(f"Train CSV missing '{id_col}' or '{target_col}'.")
    features = [c for c in tr_h if c not in (id_col, target_col)]
    if not features:
        raise ValueError("No feature columns in Train CSV.")
    if set(te_h) != set([id_col] + features) or len(te_h) != len(features) + 1:
        raise ValueError("Test CSV columns do not match expected feature schema.")
    if len(tr_rows) < folds * 2:
        raise ValueError("Insufficient training rows for k-fold CV.")

    id_idx_te = te_h.index(id_col)
    test_ids = [r[id_idx_te] for r in te_rows]
    if any(not tid for tid in test_ids) or len(set(test_ids)) != len(test_ids):
        raise ValueError("Blank or duplicate IDs in Test CSV.")

    y_idx = tr_h.index(target_col)
    y_all = [float(r[y_idx]) for r in tr_rows]
    tr_f_idx = [tr_h.index(f) for f in features]
    te_f_idx = [te_h.index(f) for f in features]

    medians = []
    for fi in tr_f_idx:
        vals = sorted(float(r[fi]) for r in tr_rows if r[fi] != "")
        medians.append(vals[len(vals) // 2] if vals else 0.0)

    x_train = [
        [float(r[tr_f_idx[j]]) if r[tr_f_idx[j]] != "" else medians[j] for j in range(len(features))]
        for r in tr_rows
    ]
    x_test = [
        [float(r[te_f_idx[j]]) if r[te_f_idx[j]] != "" else medians[j] for j in range(len(features))]
        for r in te_rows
    ]

    def _fit(xs: List[List[float]], ys: List[float]):
        n, d = len(xs), len(xs[0])
        y_mean = sum(ys) / n
        means = [sum(row[j] for row in xs) / n for j in range(d)]
        scales = [
            max(1e-9, math.sqrt(sum((row[j] - means[j]) ** 2 for row in xs) / n))
            for j in range(d)
        ]
        w = [0.0] * d
        lr = 0.08
        for _ in range(160):
            grad = [0.0] * d
            for i in range(n):
                pred = y_mean + sum(w[j] * ((xs[i][j] - means[j]) / scales[j]) for j in range(d))
                err = pred - ys[i]
                for j in range(d):
                    grad[j] += err * ((xs[i][j] - means[j]) / scales[j])
            for j in range(d):
                w[j] -= lr * ((grad[j] / n) + l2_reg * w[j])
        return y_mean, w, means, scales

    def _pred(model, row: List[float]) -> float:
        y_mean, w, means, scales = model
        return y_mean + sum(w[j] * ((row[j] - means[j]) / scales[j]) for j in range(len(w)))

    rng = random.Random(seed)
    indices = list(range(len(tr_rows)))
    rng.shuffle(indices)

    fold_scores: List[float] = []
    for fold in range(folds):
        val_i = [indices[p] for p in range(len(indices)) if p % folds == fold]
        tr_i = [indices[p] for p in range(len(indices)) if p % folds != fold]
        m = _fit([x_train[i] for i in tr_i], [y_all[i] for i in tr_i])
        mse = sum((_pred(m, x_train[i]) - y_all[i]) ** 2 for i in val_i) / len(val_i)
        fold_scores.append(math.sqrt(mse))

    full_m = _fit(x_train, y_all)
    preds = [_pred(full_m, row) for row in x_test]
    if any(not math.isfinite(p) for p in preds):
        raise ValueError("Non-finite prediction produced.")

    csv_out = f"{id_col},{target_col}\\n" + "\\n".join(
        f"{tid},{p:.6f}" for tid, p in zip(test_ids, preds)
    ) + "\\n"
    sha = sha256_hex(csv_out.encode("utf-8"))
    return {
        "competitionSlug": competition_slug,
        "rowCount": len(test_ids),
        "foldRmse": fold_scores,
        "meanCvRmse": sum(fold_scores) / len(fold_scores),
        "submissionCsv": csv_out,
        "artifactSha256": sha,
        "externalSubmissionPerformed": False,
    }


class SubmissionGateController:
    """
    Division 38: Enforces per-run & rolling-24h caps, durable intent logging before network call,
    separate live-submission authorization, and uncertain-acknowledgment retry locks.
    """

    def __init__(self, max_per_run: int = 1, max_per_24h: int = 1) -> None:
        self.max_per_run = max_per_run
        self.max_per_24h = max_per_24h
        self.ledger: List[Dict[str, Any]] = []

    def submit_gated(
        self,
        run_id: str,
        prepared_artifact: Dict[str, Any],
        live_authorized: bool,
        owner_approved_sha256: Optional[str],
        transport_fn: Callable[[str, str], Dict[str, Any]],
        now_ms: int = 1700000000000,
    ) -> Dict[str, Any]:
        slug = prepared_artifact["competitionSlug"]
        sha = prepared_artifact["artifactSha256"]

        if not live_authorized:
            rec = {"runId": run_id, "slug": slug, "artifactSha256": sha, "state": "ARTIFACT_PREPARED_LOCAL_ONLY", "ts": now_ms}
            self.ledger.append(rec)
            return rec

        if any(r["slug"] == slug and r["state"] == "UNCERTAIN_ACKNOWLEDGMENT" for r in self.ledger):
            return {"runId": run_id, "slug": slug, "artifactSha256": sha, "state": "REJECTED_BY_GATE_OR_CAP", "reason": "Unresolved uncertain acknowledgment."}

        counted = ("LIVE_SUBMISSION_ACKNOWLEDGED", "UNCERTAIN_ACKNOWLEDGMENT")
        if sum(1 for r in self.ledger if r["runId"] == run_id and r["state"] in counted) >= self.max_per_run:
            return {"runId": run_id, "slug": slug, "artifactSha256": sha, "state": "REJECTED_BY_GATE_OR_CAP", "reason": "Per-run cap reached."}

        if sum(1 for r in self.ledger if r["ts"] >= now_ms - 86_400_000 and r["state"] in counted) >= self.max_per_24h:
            return {"runId": run_id, "slug": slug, "artifactSha256": sha, "state": "REJECTED_BY_GATE_OR_CAP", "reason": "24h cap reached."}

        if owner_approved_sha256 != sha:
            return {"runId": run_id, "slug": slug, "artifactSha256": sha, "state": "REJECTED_BY_GATE_OR_CAP", "reason": "Artifact SHA-256 not approved by owner."}

        intent = {"runId": run_id, "slug": slug, "artifactSha256": sha, "state": "INTENT_RECORDED", "ts": now_ms}
        self.ledger.append(intent)
        try:
            resp = transport_fn(slug, prepared_artifact["submissionCsv"])
            if resp.get("acknowledged"):
                intent["state"] = "LIVE_SUBMISSION_ACKNOWLEDGED"
                intent["receiptRef"] = resp.get("ref")
            else:
                intent["state"] = "REJECTED_BY_SERVICE"
        except TimeoutError:
            intent["state"] = "UNCERTAIN_ACKNOWLEDGMENT"
        return dict(intent)
'''

CELL_8_IMPROVEMENT_LAB = '''class NotebookImprovementLab:
    """
    Division 39: Minimal Improvement Lab with preserved versions, fixed sealed evaluator,
    prohibition on candidate self-promotion, owner-gated promotion, and rollback.
    """

    FIXED_SEED = 20261031
    FIXED_FOLDS = 3

    def __init__(self, min_rmse_gain: float = 1e-4) -> None:
        self.min_rmse_gain = min_rmse_gain
        self.versions: Dict[str, Dict[str, Any]] = {}
        self.reports: Dict[str, Dict[str, Any]] = {}
        self.active_version_id: Optional[str] = None
        self.rollback_version_id: Optional[str] = None

    def register_version(self, version_id: str, l2_reg: float, parent_id: Optional[str] = None) -> Dict[str, Any]:
        ver = {"versionId": version_id, "parentId": parent_id, "l2Reg": float(l2_reg)}
        self.versions[version_id] = ver
        if self.active_version_id is None:
            self.active_version_id = version_id
        return ver

    def compare_candidate(
        self,
        report_id: str,
        baseline_id: str,
        candidate_id: str,
        sealed_train_csv: str,
        sealed_test_csv: str,
    ) -> Dict[str, Any]:
        base = self.versions[baseline_id]
        cand = self.versions[candidate_id]
        b_res = prepare_tabular_baseline("lab", sealed_train_csv, sealed_test_csv, folds=self.FIXED_FOLDS, l2_reg=base["l2Reg"], seed=self.FIXED_SEED)
        c_res = prepare_tabular_baseline("lab", sealed_train_csv, sealed_test_csv, folds=self.FIXED_FOLDS, l2_reg=cand["l2Reg"], seed=self.FIXED_SEED)
        gain = b_res["meanCvRmse"] - c_res["meanCvRmse"]
        improved_folds = sum(1 for bf, cf in zip(b_res["foldRmse"], c_res["foldRmse"]) if cf < bf)
        passes = gain >= self.min_rmse_gain and improved_folds >= 2
        rep = {
            "reportId": report_id,
            "baselineId": baseline_id,
            "candidateId": candidate_id,
            "baselineRmse": b_res["meanCvRmse"],
            "candidateRmse": c_res["meanCvRmse"],
            "rmseGain": gain,
            "passesEvaluator": passes,
            "state": "PENDING_OWNER_APPROVAL" if passes else "REJECTED_KEEP_BASELINE",
        }
        self.reports[report_id] = rep
        return rep

    def promote_candidate(self, report_id: str, approved_by_owner: bool) -> Dict[str, Any]:
        if not approved_by_owner:
            raise PermissionError("Candidate cannot promote itself; explicit owner approval required.")
        rep = self.reports[report_id]
        if not rep["passesEvaluator"]:
            return rep
        self.rollback_version_id = self.active_version_id
        self.active_version_id = rep["candidateId"]
        rep["state"] = "PROMOTED_BY_OWNER"
        return rep

    def rollback(self) -> Optional[str]:
        if self.rollback_version_id is not None:
            self.active_version_id = self.rollback_version_id
        return self.active_version_id
'''

CELL_9_ARC_AGI_3_AND_ENTRYPOINT = '''import shutil
import subprocess
import sys
import urllib.request
from collections import Counter
from pathlib import Path


def extract_foreground_targets(grid_2d: List[List[int]], max_targets: int = 24) -> List[Tuple[int, int]]:
    """
    Finds distinct foreground object centroids/pixels (x, y) on a 2D ARC-AGI-3 grid
    by ignoring the dominant background color.
    """
    if not grid_2d or not grid_2d[0]:
        return [(32, 32)]
    h = len(grid_2d)
    w = len(grid_2d[0])
    counts: Counter = Counter()
    for row in grid_2d:
        counts.update(row)
    bg_color = counts.most_common(1)[0][0]

    by_color: Dict[int, List[Tuple[int, int]]] = {}
    for y in range(h):
        row = grid_2d[y]
        for x in range(w):
            c = row[x]
            if c != bg_color:
                by_color.setdefault(c, []).append((x, y))

    targets: List[Tuple[int, int]] = []
    # Add per-color centroids + representative pixels first
    for color, pts in sorted(by_color.items(), key=lambda kv: len(kv[1])):
        cx = sum(p[0] for p in pts) // len(pts)
        cy = sum(p[1] for p in pts) // len(pts)
        # Snap to nearest actual pixel of that color so clicks hit real sprites
        nearest = min(pts, key=lambda p: (p[0] - cx) ** 2 + (p[1] - cy) ** 2)
        if nearest not in targets:
            targets.append(nearest)
        mid = pts[len(pts) // 2]
        if mid not in targets:
            targets.append(mid)
        if len(targets) >= max_targets:
            break
    return targets or [(w // 2, h // 2)]


class FrontierExplorerPolicy:
    """
    4CBOn2 Bounded State-Graph Explorer policy for ARC Prize 2026 (ARC-AGI-3).
    - Hashes 2D frames to prune no-op (dead wall) actions per board state.
    - Respects `available_actions` so turns are never wasted on disabled controls.
    - Targets foreground object coordinates for ACTION6 instead of random background pixels.
    """

    def __init__(self, seed: int = 20261031) -> None:
        self.rng = random.Random(seed)
        self.dead_actions: Dict[str, Set[str]] = {}
        self.tried_actions: Dict[str, Set[str]] = {}
        self.clicked_coords: Dict[str, Set[Tuple[int, int]]] = {}
        self.prev_hash: Optional[str] = None
        self.prev_action_key: Optional[str] = None
        self.prev_levels: int = 0

    @staticmethod
    def hash_grid(grid_2d: List[List[int]]) -> str:
        raw = json.dumps(grid_2d, separators=(",", ":")).encode("utf-8")
        return hashlib.sha1(raw).hexdigest()[:16]

    def select_action_spec(
        self,
        grid_2d: List[List[int]],
        available_action_ids: List[int],
        levels_completed: int = 0,
    ) -> Tuple[int, Optional[Tuple[int, int]]]:
        if levels_completed > self.prev_levels:
            self.prev_levels = levels_completed
            self.prev_hash = None
            self.prev_action_key = None

        cur_hash = self.hash_grid(grid_2d) if grid_2d else "empty"
        if self.prev_hash is not None and self.prev_action_key is not None:
            if cur_hash == self.prev_hash:
                self.dead_actions.setdefault(self.prev_hash, set()).add(self.prev_action_key)

        valid_ids = [aid for aid in available_action_ids if aid != 0] or [1, 2, 3, 4, 5, 6]
        dead = self.dead_actions.get(cur_hash, set())
        tried = self.tried_actions.setdefault(cur_hash, set())

        candidates: List[Tuple[str, int, Optional[Tuple[int, int]]]] = []
        for aid in valid_ids:
            if aid == 6:
                pts = extract_foreground_targets(grid_2d)
                used_pts = self.clicked_coords.setdefault(cur_hash, set())
                untried_pts = [p for p in pts if p not in used_pts]
                chosen_pt = untried_pts[0] if untried_pts else (self.rng.randint(0, 63), self.rng.randint(0, 63))
                key = f"6:{chosen_pt[0]},{chosen_pt[1]}"
                candidates.append((key, 6, chosen_pt))
            else:
                candidates.append((str(aid), aid, None))

        non_dead = [c for c in candidates if c[0] not in dead] or candidates
        untried = [c for c in non_dead if c[0] not in tried]
        pool = untried if untried else non_dead
        chosen_key, chosen_id, chosen_xy = self.rng.choice(pool)

        tried.add(chosen_key)
        if chosen_id == 6 and chosen_xy is not None:
            self.clicked_coords.setdefault(cur_hash, set()).add(chosen_xy)
        self.prev_hash = cur_hash
        self.prev_action_key = chosen_key
        return chosen_id, chosen_xy


ARC3_MY_AGENT_SOURCE = """from __future__ import annotations
import hashlib
import json
import random
import time
from collections import Counter
from typing import Any, Dict, List, Optional, Set, Tuple

from arcengine import FrameData, GameAction, GameState
from agents.agent import Agent


def _foreground_targets(grid_2d: List[List[int]], max_targets: int = 24) -> List[Tuple[int, int]]:
    if not grid_2d or not grid_2d[0]:
        return [(32, 32)]
    h, w = len(grid_2d), len(grid_2d[0])
    counts: Counter = Counter()
    for row in grid_2d:
        counts.update(row)
    bg = counts.most_common(1)[0][0]
    by_color: Dict[int, List[Tuple[int, int]]] = {}
    for y in range(h):
        row = grid_2d[y]
        for x in range(w):
            c = row[x]
            if c != bg:
                by_color.setdefault(c, []).append((x, y))
    out: List[Tuple[int, int]] = []
    for _, pts in sorted(by_color.items(), key=lambda kv: len(kv[1])):
        cx = sum(p[0] for p in pts) // len(pts)
        cy = sum(p[1] for p in pts) // len(pts)
        nearest = min(pts, key=lambda p: (p[0] - cx) ** 2 + (p[1] - cy) ** 2)
        if nearest not in out:
            out.append(nearest)
        mid = pts[len(pts) // 2]
        if mid not in out:
            out.append(mid)
        if len(out) >= max_targets:
            break
    return out or [(w // 2, h // 2)]


class MyAgent(Agent):
    # 4CBOn2 Bounded State-Graph Frontier Explorer for ARC Prize 2026 (ARC-AGI-3)

    MAX_ACTIONS = 200

    def __init__(self, *args: Any, **kwargs: Any) -> None:
        super().__init__(*args, **kwargs)
        seed = (int(time.time() * 1_000_000) ^ (hash(self.game_id) & 0xFFFFFFFF)) & 0xFFFFFFFF
        self.rng = random.Random(seed)
        self.dead_actions: Dict[str, Set[str]] = {}
        self.tried_actions: Dict[str, Set[str]] = {}
        self.clicked_coords: Dict[str, Set[Tuple[int, int]]] = {}
        self.prev_hash: Optional[str] = None
        self.prev_key: Optional[str] = None
        self.prev_levels: int = 0

    @property
    def name(self) -> str:
        return f"{super().name}.{self.MAX_ACTIONS}"

    def is_done(self, frames: list[FrameData], latest_frame: FrameData) -> bool:
        return latest_frame.state is GameState.WIN

    def choose_action(self, frames: list[FrameData], latest_frame: FrameData) -> GameAction:
        if latest_frame.state in (GameState.NOT_PLAYED, GameState.GAME_OVER):
            self.prev_hash = None
            self.prev_key = None
            return GameAction.RESET

        try:
            levels = int(getattr(latest_frame, "levels_completed", 0) or 0)
            if levels > self.prev_levels:
                self.prev_levels = levels
                self.prev_hash = None
                self.prev_key = None

            raw_frames = getattr(latest_frame, "frame", None) or []
            grid_2d = raw_frames[-1] if raw_frames else []
            cur_hash = hashlib.sha1(
                json.dumps(grid_2d, separators=(",", ":")).encode("utf-8")
            ).hexdigest()[:16] if grid_2d else "empty"

            if self.prev_hash is not None and self.prev_key is not None and cur_hash == self.prev_hash:
                self.dead_actions.setdefault(self.prev_hash, set()).add(self.prev_key)

            avail_raw = getattr(latest_frame, "available_actions", None) or []
            all_non_reset = [a for a in GameAction if a is not GameAction.RESET]
            legal_actions = [
                a for a in all_non_reset
                if (a in avail_raw) or (a.value in avail_raw) or (a.name in avail_raw)
            ] or all_non_reset

            dead = self.dead_actions.get(cur_hash, set())
            tried = self.tried_actions.setdefault(cur_hash, set())

            candidates = []
            for act in legal_actions:
                if act.is_complex():
                    pts = _foreground_targets(grid_2d)
                    used = self.clicked_coords.setdefault(cur_hash, set())
                    untried_pts = [p for p in pts if p not in used]
                    xy = untried_pts[0] if untried_pts else (self.rng.randint(0, 63), self.rng.randint(0, 63))
                    candidates.append((f"{act.name}:{xy[0]},{xy[1]}", act, xy))
                else:
                    candidates.append((act.name, act, None))

            non_dead = [c for c in candidates if c[0] not in dead] or candidates
            untried = [c for c in non_dead if c[0] not in tried]
            pool = untried if untried else non_dead
            key, action, xy = self.rng.choice(pool)

            tried.add(key)
            self.prev_hash = cur_hash
            self.prev_key = key

            if action.is_complex():
                cx, cy = xy if xy is not None else (32, 32)
                self.clicked_coords.setdefault(cur_hash, set()).add((cx, cy))
                action.set_data({"x": int(cx), "y": int(cy)})
                action.reasoning = {"policy": "4cbon2_frontier_explorer", "target": [int(cx), int(cy)]}
            else:
                action.reasoning = {"policy": "4cbon2_frontier_explorer", "action": action.name}
            return action
        except Exception:
            fallback = self.rng.choice([a for a in GameAction if a is not GameAction.RESET])
            if fallback.is_complex():
                fallback.set_data({"x": self.rng.randint(0, 63), "y": self.rng.randint(0, 63)})
            return fallback
"""


def find_arc_agi_3_competition_dir(search_roots: Optional[List[str]] = None) -> Optional[Path]:
    candidates = search_roots or [
        "/kaggle/input/competitions/arc-prize-2026-arc-agi-3",
        "/kaggle/input/arc-prize-2026-arc-agi-3",
    ]
    for p in candidates:
        path = Path(p)
        if path.is_dir() and ((path / "ARC-AGI-3-Agents").is_dir() or (path / "arc_agi_3_wheels").is_dir()):
            return path
    # Fallback: scan /kaggle/input up to depth 3 for any directory containing ARC-AGI-3-Agents
    kaggle_in = Path("/kaggle/input")
    if search_roots is None and kaggle_in.is_dir():
        for root, dirs, _ in os.walk(kaggle_in):
            rel_depth = len(Path(root).relative_to(kaggle_in).parts)
            if "ARC-AGI-3-Agents" in dirs or "arc_agi_3_wheels" in dirs:
                return Path(root)
            if rel_depth >= 3:
                dirs[:] = []
    return None


def write_arc3_commit_parquet(output_parquet_path: Path) -> str:
    """Writes the required schema-valid dummy submission.parquet during Kaggle Save & Run All."""
    output_parquet_path.parent.mkdir(parents=True, exist_ok=True)
    import pandas as pd
    df = pd.DataFrame(
        data=[["1_0", "1", True, 1]],
        columns=["row_id", "game_id", "end_of_game", "score"],
    )
    df.to_parquet(output_parquet_path, index=False)
    return sha256_hex(output_parquet_path.read_bytes())


def run_arc_agi_3_kaggle_flow(
    comp_dir: Path,
    working_dir: Path = Path("/kaggle/working"),
    tmp_dir: Path = Path("/tmp"),
    is_competition_rerun: Optional[bool] = None,
) -> Dict[str, Any]:
    """
    Executes the official two-phase ARC Prize 2026 - ARC-AGI-3 Kaggle submission workflow:
      - Phase A (Save & Run All / Commit): installs offline wheels, stages /tmp/my_agent.py,
        and emits /kaggle/working/submission.parquet so Kaggle enables 'Submit to Competition'.
      - Phase B (Competition Rerun): waits for http://gateway:8001/api/games, registers MyAgent
        into ARC-AGI-3-Agents, and plays all hidden games to emit evaluated submission.parquet.
    """
    rerun = bool(os.getenv("KAGGLE_IS_COMPETITION_RERUN")) if is_competition_rerun is None else is_competition_rerun
    wheels_dir = comp_dir / "arc_agi_3_wheels"
    if wheels_dir.is_dir():
        subprocess.run(
            [
                sys.executable, "-m", "pip", "install",
                "--no-index", "--find-links", str(wheels_dir),
                "arc-agi", "python-dotenv",
            ],
            check=True,
        )

    tmp_dir.mkdir(parents=True, exist_ok=True)
    staged_agent = tmp_dir / "my_agent.py"
    staged_agent.write_text(ARC3_MY_AGENT_SOURCE, encoding="utf-8")

    if rerun:
        # Wait up to 600s for the Kaggle evaluation gateway sidecar
        deadline = time.time() + 600
        while True:
            try:
                with urllib.request.urlopen("http://gateway:8001/api/games", timeout=5) as resp:
                    if resp.status == 200:
                        break
            except Exception:
                if time.time() >= deadline:
                    raise TimeoutError("Timed out waiting for http://gateway:8001/api/games")
                time.sleep(3)

        src_fw = comp_dir / "ARC-AGI-3-Agents"
        dst_fw = working_dir / "ARC-AGI-3-Agents"
        if dst_fw.exists():
            shutil.rmtree(dst_fw)
        shutil.copytree(src_fw, dst_fw)
        shutil.copyfile(staged_agent, dst_fw / "agents" / "templates" / "my_agent.py")

        init_code = (
            "from typing import Type\\n"
            "from dotenv import load_dotenv\\n"
            "from .agent import Agent, Playback\\n"
            "from .swarm import Swarm\\n"
            "from .templates.random_agent import Random\\n"
            "from .templates.my_agent import MyAgent\\n\\n"
            "load_dotenv()\\n\\n"
            "AVAILABLE_AGENTS: dict[str, Type[Agent]] = {\\n"
            "    'random': Random,\\n"
            "    'myagent': MyAgent,\\n"
            "}\\n"
        )
        (dst_fw / "agents" / "__init__.py").write_text(init_code, encoding="utf-8")

        env_body = (
            "SCHEME=http\\n"
            "HOST=gateway\\n"
            "PORT=8001\\n"
            "ARC_API_KEY=test-key-123\\n"
            "ARC_BASE_URL=http://gateway:8001/\\n"
            "OPERATION_MODE=online\\n"
            "ENVIRONMENTS_DIR=\\n"
            f"RECORDINGS_DIR={working_dir / 'server_recording'}\\n"
        )
        (dst_fw / ".env").write_text(env_body, encoding="utf-8")

        run_env = dict(os.environ)
        run_env["MPLBACKEND"] = "agg"
        subprocess.run([sys.executable, "main.py", "--agent", "myagent"], cwd=str(dst_fw), env=run_env, check=True)
        return {"mode": "COMPETITION_RERUN", "agentStaged": str(staged_agent)}

    out_parquet = working_dir / "submission.parquet"
    # Pre-flight smoke check in /tmp (keeps /kaggle/working clean with ONLY submission.parquet)
    src_fw = comp_dir / "ARC-AGI-3-Agents"
    if src_fw.is_dir():
        verify_fw = tmp_dir / "arc3_verify_fw"
        if verify_fw.exists():
            shutil.rmtree(verify_fw)
        shutil.copytree(src_fw, verify_fw)
        shutil.copyfile(staged_agent, verify_fw / "agents" / "templates" / "my_agent.py")
        slim_init = (
            "from typing import Type\\n"
            "from dotenv import load_dotenv\\n"
            "from .agent import Agent, Playback\\n"
            "from .swarm import Swarm\\n"
            "from .templates.random_agent import Random\\n"
            "from .templates.my_agent import MyAgent\\n\\n"
            "load_dotenv()\\n\\n"
            "AVAILABLE_AGENTS: dict[str, Type[Agent]] = {'random': Random, 'myagent': MyAgent}\\n"
        )
        (verify_fw / "agents" / "__init__.py").write_text(slim_init, encoding="utf-8")
        subprocess.run(
            [sys.executable, "-c", "from agents import AVAILABLE_AGENTS; assert 'myagent' in AVAILABLE_AGENTS"],
            cwd=str(verify_fw),
            check=True,
        )

    sha = write_arc3_commit_parquet(out_parquet)
    return {
        "mode": "COMMIT_SAVE_RUN",
        "submissionParquet": str(out_parquet),
        "parquetSha256": sha,
        "agentStaged": str(staged_agent),
        "agentImportVerified": src_fw.is_dir(),
    }


def run_notebook_entrypoint() -> Dict[str, Any]:
    rt = detect_runtime_environment()
    comp_dir = find_arc_agi_3_competition_dir()

    # Self-test the ARC-AGI-3 Frontier Explorer policy on a synthetic 8x8 frame
    demo_grid = [[0] * 8 for _ in range(8)]
    demo_grid[2][5] = 3  # Foreground object at (x=5, y=2)
    policy = FrontierExplorerPolicy(seed=42)
    act_id, xy = policy.select_action_spec(demo_grid, available_action_ids=[6], levels_completed=0)

    status: Dict[str, Any] = {
        "runtimeRole": rt.role,
        "colabAiImportable": rt.colab_ai_module_importable,
        "arcAgi3DatasetMounted": comp_dir is not None,
        "frontierSelfTest": {"chosenActionId": act_id, "chosenTargetXY": xy},
    }

    print("=== 4CBOn2_Kaggle6 Live Status ===")
    print(f"Runtime Role            : {rt.role}")
    print(f"google.colab.ai Ready   : {rt.colab_ai_module_importable}")
    print(f"ARC-AGI-3 Mount Found   : {comp_dir if comp_dir else 'No (running offline self-test)'}")
    print(f"ARC-AGI-3 Explorer Test : action=ACTION{act_id}, target_xy={xy} (expected foreground (5, 2))")

    if comp_dir is not None:
        arc_res = run_arc_agi_3_kaggle_flow(comp_dir=comp_dir)
        status["arcAgi3Result"] = arc_res
        print(f"ARC-AGI-3 Flow Result   : {json.dumps(arc_res)}")
        if arc_res["mode"] == "COMMIT_SAVE_RUN":
            print("READY: Once Save & Run All completes, click 'Submit to Competition' and select submission.parquet!")
    elif rt.is_kaggle and Path("/kaggle/working").is_dir():
        # Even if attached before mounting dataset, emit clear guidance
        print("TIP: Attach the 'ARC Prize 2026 - ARC-AGI-3' competition data in Kaggle Inputs (+ Add Input) and turn Internet OFF.")

    return status


NOTEBOOK_LAST_STATUS = run_notebook_entrypoint()
'''


CELL_10_INTRO_MARKDOWN = (
    "### v0.3 · Daily Lab handoff\n"
    "\n"
    "Emits `4cbon2.run_telemetry.v1` after every run and applies an owner-approved "
    "`4cbon2.candidate_diff.v1` back onto the runtime config. The Android companion ingests the "
    "telemetry, diagnoses it against the locked champion, and returns exactly one bounded change "
    "for you to approve — the notebook never widens its own authority."
)

CELL_10_RUN_TELEMETRY_AND_CANDIDATE_DIFF = r'''# ==============================================================================
# Division 51 (v0.3) — Run telemetry emission & bounded candidate-diff application
#
# Closes the notebook half of the v0.3 daily loop. The Android companion reasons about
# `4cbon2.run_telemetry.v1` records (Divisions 43-48); this cell is what produces them from a real
# Kaggle/Colab run, and what applies an owner-approved `4cbon2.candidate_diff.v1` back onto the
# runtime configuration.
#
# Two rules carried over from the app:
#   1. Never invent evidence. A telemetry record missing score / actions / tokens is refused.
#   2. Never widen authority in the notebook. A candidate diff may write exactly one allowlisted
#      key, inside owner-declared bounds; evaluator, submission and credential settings are
#      unreachable from here.
# ==============================================================================

RUN_TELEMETRY_SCHEMA_V1 = "4cbon2.run_telemetry.v1"
CANDIDATE_DIFF_SCHEMA_V1 = "4cbon2.candidate_diff.v1"

BENCHMARK_KINDS = ("ARC_AGI_INTERACTIVE", "TABULAR_CSV_CV", "SCALAR_METRIC")
TELEMETRY_SOURCES = ("DIAGNOSTICS_JSON", "KAGGLE_KERNEL_STATUS", "COLAB_RECEIPT", "MANUAL_ENTRY")

_REDACTED = "[redacted]"
_SECRET_KEY_HINTS = (
    "token", "secret", "password", "passwd", "authorization", "auth", "apikey", "api_key",
    "credential", "private_key", "pat", "cookie", "session_key",
)
_SECRET_VALUE_PATTERNS = (
    re.compile(r"(?i)bearer\s+[A-Za-z0-9._~+/-]{8,}"),
    re.compile(r"(?i)\bKGAT_[A-Za-z0-9]{8,}"),
    re.compile(r"(?i)\bgh[pousr]_[A-Za-z0-9]{16,}"),
    re.compile(r"(?i)\bgithub_pat_[A-Za-z0-9_]{16,}"),
    re.compile(r"(?i)\b[a-z0-9_]*(token|secret|password|api_?key)\b\s*[=:]\s*\S+"),
)

# Owner-declared bounds for candidate patches; mirrors BoundedPatchPolicy on Android.
PATCH_MAX_KEYS = 1
PATCH_RANGES = {
    "reasoning_token_budget": (128.0, 4000.0),
    "max_actions_per_level": (1.0, 200.0),
    "exploration_temperature": (0.0, 1.0),
    "exploration_frontier_weight": (0.0, 1.0),
    "dead_action_prune_threshold": (0.0, 10.0),
    "max_wall_clock_ms": (60000.0, 21600000.0),
    "max_actions": (1.0, 500.0),
    "max_total_tokens": (1.0, 150000.0),
}
PATCH_REVERT_KEY = "revert_to_version"
_PROTECTED_PATCH_PREFIXES = ("evaluator", "submission", "credential", "secret", "promotion", "champion")


def _redact_value(value: str) -> str:
    text = str(value)
    for pattern in _SECRET_VALUE_PATTERNS:
        if pattern.search(text):
            return _REDACTED
    return text


def sanitize_diagnostics(mapping: Optional[Dict[str, Any]]) -> Tuple[Dict[str, str], bool]:
    """Drops credential-shaped keys and masks credential-shaped values. Returns (safe, changed)."""
    changed = False
    safe: Dict[str, str] = {}
    for key, value in (mapping or {}).items():
        if any(hint in str(key).lower() for hint in _SECRET_KEY_HINTS):
            changed = True
            continue
        redacted = _redact_value(value)
        if redacted != str(value):
            changed = True
        safe[str(key)[:120]] = redacted[:400]
    return safe, changed


def build_run_telemetry(
    competition_slug: str,
    benchmark_kind: str,
    run_label: str,
    version_ref: str,
    metric_name: str,
    primary_metric: Optional[float],
    higher_is_better: bool,
    levels_completed: int,
    total_actions: int,
    tokens_used: int,
    wall_clock_ms: int = 0,
    source: str = "DIAGNOSTICS_JSON",
    source_ref: Optional[str] = None,
    diagnostics: Optional[Dict[str, Any]] = None,
    started_at_epoch_ms: int = 0,
    completed_at_epoch_ms: int = 0,
    telemetry_id: Optional[str] = None,
) -> Dict[str, Any]:
    """Builds a validated, secret-free `4cbon2.run_telemetry.v1` record.

    Raises ValueError instead of guessing: a run without a score, action count or token count is not
    evidence, and the Android diagnoser must never receive invented numbers.
    """
    if not competition_slug:
        raise ValueError("competition_slug is required")
    if benchmark_kind not in BENCHMARK_KINDS:
        raise ValueError(f"benchmark_kind must be one of {BENCHMARK_KINDS}")
    if source not in TELEMETRY_SOURCES:
        raise ValueError(f"source must be one of {TELEMETRY_SOURCES}")
    if primary_metric is None:
        raise ValueError("primary_metric is required")
    primary = float(primary_metric)
    if primary != primary or primary in (float("inf"), float("-inf")):
        raise ValueError("primary_metric must be finite")
    if int(total_actions) <= 0:
        raise ValueError("total_actions must be greater than zero")
    if int(tokens_used) < 0 or int(levels_completed) < 0 or int(wall_clock_ms) < 0:
        raise ValueError("counters must not be negative")

    safe_diagnostics, redacted = sanitize_diagnostics(diagnostics)
    if redacted:
        safe_diagnostics["redactions_applied"] = "true"

    record = {
        "contractVersion": RUN_TELEMETRY_SCHEMA_V1,
        "telemetryId": telemetry_id or f"tel_{int(time.time() * 1000)}_{int(total_actions)}",
        "competitionSlug": competition_slug,
        "benchmarkKind": benchmark_kind,
        "runLabel": run_label,
        "versionRef": str(version_ref),
        "metricName": metric_name,
        "primaryMetric": primary,
        "higherIsBetter": bool(higher_is_better),
        "levelsCompleted": int(levels_completed),
        "totalActions": int(total_actions),
        "tokensUsed": int(tokens_used),
        "wallClockMs": int(wall_clock_ms),
        "source": source,
        "sourceRef": source_ref,
        "startedAtEpochMs": int(started_at_epoch_ms),
        "completedAtEpochMs": int(completed_at_epoch_ms) or int(time.time() * 1000),
        "recordedAtEpochMs": int(time.time() * 1000),
        "redactionApplied": True,
        "diagnostics": safe_diagnostics,
    }
    ok, problems = validate_run_telemetry(record)
    if not ok:
        raise ValueError("invalid telemetry: " + "; ".join(problems))
    return record


def validate_run_telemetry(payload: Dict[str, Any]) -> Tuple[bool, List[str]]:
    """Structural check of a telemetry record. Mirrors the Android ingest rejections."""
    problems: List[str] = []
    if not isinstance(payload, dict):
        return False, ["telemetry must be an object"]
    if payload.get("contractVersion") != RUN_TELEMETRY_SCHEMA_V1:
        problems.append("unsupported contractVersion")
    for field_name in ("telemetryId", "competitionSlug", "runLabel", "versionRef", "metricName"):
        if not isinstance(payload.get(field_name), str) or not payload.get(field_name):
            problems.append(f"{field_name} must be a non-empty string")
    if payload.get("benchmarkKind") not in BENCHMARK_KINDS:
        problems.append("benchmarkKind is not recognised")
    metric = payload.get("primaryMetric")
    if not isinstance(metric, (int, float)) or isinstance(metric, bool):
        problems.append("primaryMetric must be numeric")
    for field_name in ("levelsCompleted", "totalActions", "tokensUsed", "wallClockMs"):
        value = payload.get(field_name)
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            problems.append(f"{field_name} must be a non-negative integer")
    if payload.get("totalActions") == 0:
        problems.append("totalActions must be greater than zero")
    if payload.get("redactionApplied") is not True:
        problems.append("record must assert redactionApplied")
    raw_json = json.dumps(payload, sort_keys=True)
    if any(pattern.search(raw_json) for pattern in _SECRET_VALUE_PATTERNS):
        problems.append("record still contains credential-shaped material")
    return (not problems), problems


def emit_run_telemetry(output_path: Path, payload: Dict[str, Any]) -> Path:
    """Atomically writes a validated telemetry record for the Android companion to ingest."""
    ok, problems = validate_run_telemetry(payload)
    if not ok:
        raise ValueError("refusing to emit invalid telemetry: " + "; ".join(problems))
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = output_path.with_suffix(output_path.suffix + ".tmp")
    tmp_path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
    tmp_path.replace(output_path)
    return output_path


def load_candidate_diff(path_or_payload: Any) -> Dict[str, Any]:
    if isinstance(path_or_payload, dict):
        return path_or_payload
    return json.loads(Path(path_or_payload).read_text(encoding="utf-8"))


def validate_candidate_diff(diff: Dict[str, Any], evaluator_id: Optional[str] = None) -> List[str]:
    """Returns every reason the diff may not be applied. Empty list means it is safe to apply."""
    violations: List[str] = []
    if not isinstance(diff, dict):
        return ["diff must be an object"]
    if diff.get("contractVersion") != CANDIDATE_DIFF_SCHEMA_V1:
        violations.append("unsupported diff contractVersion")
    if evaluator_id is not None:
        violations.append("the notebook may not install or replace an evaluator")
    patch = diff.get("patch")
    if not isinstance(patch, dict) or not patch:
        violations.append("patch must be a non-empty object")
        return violations
    if len(patch) > PATCH_MAX_KEYS:
        violations.append(f"patch changes {len(patch)} keys; at most {PATCH_MAX_KEYS} allowed")
    for key, raw_value in patch.items():
        key_str = str(key)
        if any(key_str.startswith(prefix) or f"_{prefix}" in key_str for prefix in _PROTECTED_PATCH_PREFIXES):
            violations.append(f"key '{key_str}' targets a protected policy area")
            continue
        if key_str == PATCH_REVERT_KEY:
            if not str(raw_value):
                violations.append("revert target is blank")
            continue
        if key_str not in PATCH_RANGES:
            violations.append(f"key '{key_str}' is not allowlisted")
            continue
        try:
            numeric = float(raw_value)
        except (TypeError, ValueError):
            violations.append(f"key '{key_str}' needs a number")
            continue
        low, high = PATCH_RANGES[key_str]
        if not (low <= numeric <= high):
            violations.append(f"key '{key_str}'={numeric} is outside {low}..{high}")
    return violations


def apply_candidate_diff(
    config: Dict[str, Any],
    diff: Dict[str, Any],
    evaluator_id: Optional[str] = None,
) -> Dict[str, Any]:
    """Applies one owner-approved bounded change onto a runtime config, as a new dict."""
    violations = validate_candidate_diff(diff, evaluator_id=evaluator_id)
    if violations:
        raise ValueError("candidate diff refused: " + "; ".join(violations))
    updated = dict(config)
    for key, raw_value in diff["patch"].items():
        if str(key) == PATCH_REVERT_KEY:
            target = str(raw_value)
            champion_config = diff.get("championConfig") or {}
            if not isinstance(champion_config, dict) or not champion_config:
                raise ValueError("revert diff has no championConfig to restore")
            updated.update(champion_config)
            updated["restored_version"] = target
            continue
        updated[str(key)] = float(raw_value) if "." in str(raw_value) else int(float(raw_value))
    updated["applied_diff_id"] = diff.get("diffId")
    return updated
'''


def main() -> None:
    nb = {
        "cells": [
            code_cell(CELL_1_HEADER_COMMENT + "\n\n" + CELL_2_RUNTIME),
            code_cell(CELL_3_BUNDLE),
            code_cell(CELL_4_COLAB_AI),
            code_cell(CELL_5_AGENT_CORE),
            code_cell(CELL_6_PRIVACY_INJECTION),
            code_cell(CELL_7_KAGGLE_PIPELINE_AND_GATE),
            code_cell(CELL_8_IMPROVEMENT_LAB),
            code_cell(CELL_9_ARC_AGI_3_AND_ENTRYPOINT),
            md_cell(CELL_10_INTRO_MARKDOWN),
            code_cell(CELL_10_RUN_TELEMETRY_AND_CANDIDATE_DIFF),
        ],
        "metadata": {
            "kernelspec": {
                "display_name": "Python 3",
                "language": "python",
                "name": "python3",
            },
            "language_info": {
                "name": "python",
                "version": "3.12",
            },
            "kaggle": {
                "accelerator": "nvidiaTeslaT4",
                "isInternetEnabled": False,
                "isGpuEnabled": True,
                "language": "python",
                "sourceType": "notebook",
            },
        },
        "nbformat": 4,
        "nbformat_minor": 5,
    }
    out_path = Path(__file__).resolve().parents[1] / "4CBOn2_Kaggle6.ipynb"
    out_path.write_text(json.dumps(nb, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {out_path} ({out_path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
