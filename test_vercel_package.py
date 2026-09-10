"""
Regression test: Vercel serverless bundle size & importability.

Vercel's Python runtime bundles EVERY package installed from vercel/requirements.txt
into each serverless function, used or not. The 500 MB function-size cap is a hard
build failure. This test guards against re-inflating the bundle:

  1. No heavy "notebook/Colab-origin" packages back in vercel/requirements.txt
     (chromadb, torch, sentence-transformers, plotly, fpdf2, duckduckgo-search,
     PyPDF2, python-docx, beautifulsoup4, onnxruntime, kubernetes, ...).
  2. Every pinned dependency is actually imported by the vercel/ runtime code,
     or is an explicitly-documented transitive pin (with reason).
  3. The huggingface-hub <1.0 cold-start pin is present (gradio 4.44 imports
     HfFolder, removed in huggingface-hub 1.0; nothing else caps it since
     chromadb's tokenizers<1.0 constraint was removed).
  4. If fastapi + gradio are importable in the current interpreter, boot the
     real app (app.py + api/index.py + api/gumroad-webhook.py) and hit
     /api/health. Skipped gracefully when deps aren't installed.

Run:  python3 test_vercel_package.py
Exit: 0 all checks passed / 1 failure(s)
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
VERCEL_DIR = os.path.join(HERE, "vercel")
REQ = os.path.join(VERCEL_DIR, "requirements.txt")

FAILURES = []


def check(label, condition, detail=""):
    status = "PASS" if condition else "FAIL"
    print(f"[{status}] {label}" + (f" — {detail}" if detail and not condition else ""))
    if not condition:
        FAILURES.append(label)


# Packages that must NEVER return to the Vercel bundle (bundle-size killers).
# These belong to the HF Space / notebook stack (space_gemini/app.py uses them);
# nothing under vercel/ imports them.
BANNED = [
    "chromadb", "torch", "sentence-transformers", "sentence_transformers",
    "plotly", "fpdf2", "fpdf", "duckduckgo-search", "duckduckgo_search",
    "PyPDF2", "python-docx", "python_docx", "beautifulsoup4", "bs4",
    "onnxruntime", "kubernetes", "transformers", "scipy", "scikit-learn",
    "matplotlib-external", "pillow-heavy",
]

# requirement name -> top-level import name (None if not imported directly)
IMPORT_NAMES = {
    "fastapi": "fastapi",
    "gradio": "gradio",
    "google-generativeai": "google",
    "supabase": "supabase",
    "requests": "requests",
}
# Deliberate pins that vercel/ code does not import directly.
ALLOWED_TRANSITIVE_PINS = {
    "huggingface-hub": (
        "gradio 4.44 imports HfFolder (removed in huggingface-hub 1.0); "
        "chromadb's tokenizers<1.0 cap used to keep this below 1.0 and is gone"
    ),
    "uvicorn": (
        "required by gradio; used as the local dev server entrypoint, not "
        "imported by the serverless handlers"
    ),
    "python-multipart": (
        "form/multipart parsing backend required by FastAPI and gradio at runtime; "
        "never imported directly by vercel/ code"
    ),
    "pydantic": (
        "validation backend required by FastAPI at runtime; never imported "
        "directly by vercel/ code"
    ),
}


def parse_requirements(path):
    """Return list of (name, pinned_version) for non-comment requirement lines."""
    reqs = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            m = re.match(r"^([A-Za-z0-9._-]+?)\s*==\s*([A-Za-z0-9._!+]+)", line)
            if not m:
                reqs.append((line, None))
                continue
            reqs.append((m.group(1), m.group(2)))
    return reqs


def vercel_imports():
    """Set of top-level module names imported anywhere under vercel/."""
    names = set()
    for root, _dirs, files in os.walk(VERCEL_DIR):
        for fn in files:
            if not fn.endswith(".py"):
                continue
            with open(os.path.join(root, fn), encoding="utf-8") as f:
                src = f.read()
            for m in re.finditer(r"^\s*(?:import|from)\s+([A-Za-z_][\w.]*)", src, re.M):
                names.add(m.group(1).split(".")[0])
    return names


def main():
    check("vercel/requirements.txt exists", os.path.isfile(REQ))

    reqs = parse_requirements(REQ)
    check("requirements parse to pinned entries", bool(reqs) and all(v for _n, v in reqs),
          f"got {reqs}")

    names = [n.lower().replace("_", "-") for n, _v in reqs]
    # 1. banned packages
    for banned in BANNED:
        check(f"bundle does not contain {banned}", banned.lower() not in names)

    # 2. every direct requirement is imported by vercel/ code or a documented pin
    imports = vercel_imports()
    for name, _ver in reqs:
        key = name.lower().replace("_", "-")
        imp = IMPORT_NAMES.get(key)
        if imp is None:
            check(f"{name} is a documented transitive pin", key in ALLOWED_TRANSITIVE_PINS,
                  "not in ALLOWED_TRANSITIVE_PINS and not mapped to an import")
        else:
            check(f"{name} imported by vercel/ runtime code", imp in imports,
                  f"expected 'import {imp}' somewhere under vercel/")

    # 3. cold-start guard: huggingface-hub pinned below 1.0
    hub = {n.lower().replace("_", "-"): v for n, v in reqs}.get("huggingface-hub")
    check("huggingface-hub pinned below 1.0 (gradio 4.44 HfFolder cold-start guard)",
          hub is not None and hub.split(".")[0] in ("0",) and hub >= "0.19.3",
          f"huggingface-hub=={hub}")

    # 4. live boot smoke test (only when the deps are installed)
    try:
        import fastapi  # noqa: F401
        import gradio  # noqa: F401
        import google.generativeai  # noqa: F401
        import supabase  # noqa: F401
        have_deps = True
    except ImportError as e:
        have_deps = False
        print(f"[SKIP] live boot smoke test — deps not installed in this interpreter ({e})")

    if have_deps:
        sys.path.insert(0, VERCEL_DIR)
        try:
            import app as vercel_app
            check("vercel/app.py imports", len(vercel_app.LAYERS) >= 16)

            import importlib.util
            spec = importlib.util.spec_from_file_location(
                "vercel_index", os.path.join(VERCEL_DIR, "api", "index.py"))
            index = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(index)
            check("vercel/api/index.py imports (FastAPI + Gradio mount)", True)

            spec2 = importlib.util.spec_from_file_location(
                "vercel_gumroad", os.path.join(VERCEL_DIR, "api", "gumroad-webhook.py"))
            gw = importlib.util.module_from_spec(spec2)
            spec2.loader.exec_module(gw)
            check("vercel/api/gumroad-webhook.py imports", True)

            from fastapi.testclient import TestClient
            client = TestClient(index.app)
            r = client.get("/api/health")
            check("GET /api/health -> 200", r.status_code == 200, str(r.status_code))
            check("health reports 16-layer pipeline",
                  r.json().get("status") == "ok" and r.json().get("layers", 0) >= 16)
            r2 = client.post("/api/pipeline", json={})
            check("POST /api/pipeline rejects empty body with 400", r2.status_code == 400)
        except Exception as e:  # noqa: BLE001
            check("live boot smoke test", False, f"{type(e).__name__}: {e}")

    print("=" * 74)
    if FAILURES:
        print(f"RESULT: {len(FAILURES)} failure(s)")
        for f in FAILURES:
            print("  ✗", f)
        return 1
    print("RESULT: all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
