#!/usr/bin/env bash
# ============================================================
# Deploy space_demo/ to a Hugging Face Space
# ============================================================
# Requirements:
#   - huggingface_hub installed:  pip install "huggingface_hub>=0.23,<1.0"
#   - Authenticated with Hugging Face:
#         huggingface-cli login        (interactive, stores your token)
#     or  export HF_TOKEN=hf_...       (read-only/inference tokens also work)
#
# Usage:
#   HF_USERNAME=<your-hf-username> ./deploy_space.sh [space_name]
#
# Example:
#   HF_USERNAME=mohamtur1 ./deploy_space.sh 4CBOn2
#   -> https://huggingface.co/spaces/mohamtur1/4CBOn2
#
# Note: this sandbox cannot reach huggingface.co, so run this script from
# your own machine / any environment with HF access.
# ============================================================
set -euo pipefail

SPACE_NAME="${1:-4CBOn2}"
HF_USERNAME="${HF_USERNAME:-}"

if [ -z "$HF_USERNAME" ]; then
  echo "❌ Set HF_USERNAME (your Hugging Face username), e.g.:"
  echo "   HF_USERNAME=johndoe ./deploy_space.sh"
  exit 1
fi

REPO="${HF_USERNAME}/${SPACE_NAME}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FOLDER="${SCRIPT_DIR}/space_demo"

# Prefer `hf` (huggingface_hub>=1.0) or fall back to `huggingface-cli`
if command -v hf >/dev/null 2>&1; then
  CLI="hf"
elif command -v huggingface-cli >/dev/null 2>&1; then
  CLI="huggingface-cli"
else
  echo "❌ Hugging Face CLI not found. Run: pip install \"huggingface_hub>=0.23,<1.0\""
  exit 1
fi
echo "Using CLI: $CLI"

echo "🚀 Creating Space if needed: ${REPO}"
"$CLI" repo create "$SPACE_NAME" --type space --sdk gradio -y || \
  echo "ℹ️  Space may already exist — continuing with upload."

echo "📤 Uploading ${FOLDER} → ${REPO}"
"$CLI" upload "$REPO" "$FOLDER" --repo-type=space \
  --commit-message="Deploy 4CBON2 (HuggingFace Edition) from space_demo/"

echo ""
echo "✅ Deployed. Live at: https://huggingface.co/spaces/${REPO}"
echo "   (First build takes a few minutes — watch it at: https://huggingface.co/spaces/${REPO}/settings)"
