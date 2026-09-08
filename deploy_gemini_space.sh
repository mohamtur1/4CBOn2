#!/usr/bin/env bash
# ============================================================
# Deploy space_gemini/ to a Hugging Face Space (Gemini edition)
# ============================================================
# Requirements:
#   pip install "huggingface_hub>=0.23"
#   hf auth login          (or:  export HF_TOKEN=hf_...)
#
# Usage:
#   HF_USERNAME=<your-hf-username> ./deploy_gemini_space.sh [space_name]
#
# Example:
#   HF_USERNAME=mohamtur1 ./deploy_gemini_space.sh 4CBOn2-Gemini
#   -> https://huggingface.co/spaces/mohamtur1/4CBOn2-Gemini
# ============================================================
set -euo pipefail

SPACE_NAME="${1:-4CBOn2-Gemini}"
HF_USERNAME="${HF_USERNAME:-}"

if [ -z "$HF_USERNAME" ]; then
  echo "❌ Set HF_USERNAME (your Hugging Face username), e.g.:"
  echo "   HF_USERNAME=johndoe ./deploy_gemini_space.sh"
  exit 1
fi

REPO="${HF_USERNAME}/${SPACE_NAME}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FOLDER="${SCRIPT_DIR}/space_gemini"

if command -v hf >/dev/null 2>&1; then
  CLI="hf"
elif command -v huggingface-cli >/dev/null 2>&1; then
  CLI="huggingface-cli"
else
  echo "❌ Hugging Face CLI not found. Run: pip install \"huggingface_hub>=0.23\""
  exit 1
fi
echo "Using CLI: $CLI"

echo "🔎 Regenerating app.py from the notebook (guarded build)…"
python3 "${SCRIPT_DIR}/build_gemini_space.py"

echo "🚀 Creating Space if needed: ${REPO}"
"$CLI" repo create "$SPACE_NAME" --type space --sdk gradio -y || \
  echo "ℹ️  Space may already exist — continuing with upload."

echo "📤 Uploading ${FOLDER} → ${REPO}"
"$CLI" upload "$REPO" "$FOLDER" --repo-type=space \
  --commit-message="Deploy 4CBON2 (Gemini Frontier Research Edition) from space_gemini/"

echo ""
echo "✅ Deployed. Live at: https://huggingface.co/spaces/${REPO}"
echo "   First build takes a few minutes — watch it at:"
echo "   https://huggingface.co/spaces/${REPO}/settings"
echo ""
echo "💡 Reminder: visitors paste their own Google API key in the UI."
echo "   Optional Space secrets: GEMINI_API_KEY, GEMINI_MODEL,"
echo "   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY"
