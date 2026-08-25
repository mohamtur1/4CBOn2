#!/usr/bin/env python3
"""
Deploy the public 4CBON2 build to Hugging Face Spaces.

Creates two Spaces and pushes the matching files from this repo:

  1. 4cbon2-static  (Static Space  -> static_space/  : landing page)
  2. 4cbon2-app      (Gradio Space  -> space_demo/    : the public app)

Usage:
    export HF_TOKEN=hf_...           # your Hugging Face access token
    python3 deploy_hf_spaces.py

Optional:
    python3 deploy_hf_spaces.py --username <hf-username>   # skip auto-detection

The HF username is auto-detected from the token (via whoami) when not supplied.
No secrets are written to any repo — the token is read from the environment only.

Requirements: huggingface_hub>=0.23,<1.0  (auto-installed if missing).
"""
import os
import sys
import subprocess

REPO_ROOT = os.path.dirname(os.path.abspath(__file__))

SPACES = [
    {
        "name": "4cbon2-static",
        "sdk": "static",
        "folder": os.path.join(REPO_ROOT, "static_space"),
        "msg": "Deploy 4CBON2 public landing (static)",
    },
    {
        "name": "4cbon2-app",
        "sdk": "gradio",
        "folder": os.path.join(REPO_ROOT, "space_demo"),
        "msg": "Deploy 4CBON2 public app (gradio)",
    },
]


def ensure_hf_hub() -> None:
    try:
        import huggingface_hub  # noqa: F401
    except ImportError:
        print("Installing huggingface_hub ...")
        subprocess.check_call(
            [sys.executable, "-m", "pip", "install", "-q", "huggingface_hub>=0.23,<1.0"]
        )


def main() -> int:
    from huggingface_hub import HfApi
    from huggingface_hub.utils import RepositoryNotFoundError

    token = os.environ.get("HF_TOKEN", "").strip()
    if not token:
        print("❌ HF_TOKEN is not set.")
        print("   export HF_TOKEN=hf_...   # create at https://huggingface.co/settings/tokens")
        return 1

    api = HfApi(token=token)

    # --- Determine username -------------------------------------------------
    username = None
    if "--username" in sys.argv:
        i = sys.argv.index("--username")
        if i + 1 < len(sys.argv):
            username = sys.argv[i + 1].strip()
    if not username:
        try:
            username = api.whoami()["name"]
        except Exception as e:
            print(f"❌ Could not authenticate token: {e}")
            print("   Make sure HF_TOKEN is a valid write-capable token.")
            return 1
    print(f"✅ Authenticated as: {username}")

    # --- Create + upload each Space -----------------------------------------
    results = []
    for spec in SPACES:
        repo_id = f"{username}/{spec['name']}"
        try:
            api.create_repo(
                repo_id=repo_id,
                repo_type="space",
                space_sdk=spec["sdk"],
                exist_ok=True,
            )
            print(f"✅ Space ready: {repo_id}")
        except Exception as e:
            print(f"⚠️ Could not create {repo_id} (may already exist): {e}")

        folder = spec["folder"]
        if not os.path.isdir(folder):
            print(f"❌ Folder not found: {folder}")
            continue
        print(f"📤 Uploading {os.path.basename(folder)} -> {repo_id}")
        api.upload_folder(
            repo_id=repo_id,
            folder_path=folder,
            repo_type="space",
            commit_message=spec["msg"],
        )
        url = f"https://huggingface.co/spaces/{repo_id}"
        results.append(url)
        print(f"✅ Uploaded: {url}")

    print("\n" + "=" * 60)
    print("DEPLOYED SPACES")
    for u in results:
        print("  " + u)
    print("=" * 60)
    print("First build can take a few minutes:")
    print("  - Watch builds: https://huggingface.co/spaces/{0}/settings".format(
        f"{username}/4cbon2-app"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
