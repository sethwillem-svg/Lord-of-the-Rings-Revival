#!/usr/bin/env bash
# One-shot setup on a rented GPU machine (RunPod, Vast.ai, ...): Kimodo and/or HY-Motion, the generator and the viewer.
#
# On the machine (Ubuntu image with CUDA + PyTorch, e.g. RunPod "PyTorch 2.x"; expose HTTP port 8000):
#   git clone -b core/anim-viewer https://github.com/sethwillem-svg/Lord-of-the-Rings-Revival.git
#   cd Lord-of-the-Rings-Revival && bash tools/anim-viewer/cloud_setup.sh          # both models
#   bash tools/anim-viewer/cloud_setup.sh kimodo        # or only one: kimodo | hymotion
#
# Then (new shell, or `source ~/.bashrc`):
#   kimodo-up      start Kimodo (about 17 GB VRAM), then: gen --space http://localhost:7860 --prompts tools/anim-viewer/prompts/orc.txt --count 3
#   kimodo-down    stop it (free the GPU for HY-Motion)
#   gen-hy --prompts tools/anim-viewer/prompts/orc.txt --count 3      HY-Motion (about 26 GB VRAM)
# Clips land in <repo>/clips and show up in the viewer link printed at the end. On a 32 GB card run one model at a time.
#
# Needs a Hugging Face token (asked once). Kimodo's text encoder is built on Meta Llama 3 8B, a gated model:
# accept its licence at https://huggingface.co/meta-llama/Meta-Llama-3-8B-Instruct with the same account first.
# Disk: about 60 GB for both models (Llama 3 8B and Qwen3 8B text encoders are about 16 GB each).
set -euo pipefail
WHICH="${1:-both}"
case "$WHICH" in both|kimodo|hymotion) ;; *) echo "usage: cloud_setup.sh [both|kimodo|hymotion]"; exit 1;; esac
REPO="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/../.." && pwd)"
WORK="${CLOUD_WORK:-$( [ -d /workspace ] && echo /workspace || echo "$HOME" )}"
BIN="$WORK/bin"; mkdir -p "$BIN"
SUDO=""; [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null && SUDO="sudo"
say() { printf '\n== %s\n' "$*"; }

say "System packages"
$SUDO apt-get update -qq && $SUDO apt-get install -y -qq git git-lfs cmake python3-venv >/dev/null
git lfs install --skip-repo >/dev/null

say "Hugging Face login"
python3 -m pip install -q -U "huggingface_hub[cli]" gradio_client
if ! python3 -c "from huggingface_hub import get_token; import sys; sys.exit(0 if get_token() else 1)"; then
  read -r -s -p "Hugging Face access token (Read): " HFT; echo
  python3 -c "import sys; from huggingface_hub import login; login(token=sys.argv[1], add_to_git_credential=False)" "$HFT"
fi
hfdl() { if command -v hf >/dev/null; then hf download "$@"; else huggingface-cli download "$@"; fi; }

if [ "$WHICH" != hymotion ]; then
  say "Kimodo (Space app with Kimodo-SOMA-RP-v1.2)"
  [ -d "$WORK/kimodo-motion" ] || git clone -q https://huggingface.co/spaces/hugging-apps/kimodo-motion "$WORK/kimodo-motion"
  [ -d "$WORK/venv-kimodo" ] || python3 -m venv --system-site-packages "$WORK/venv-kimodo"
  "$WORK/venv-kimodo/bin/pip" install -q -r "$WORK/kimodo-motion/requirements.txt" "gradio[mcp]==6.30.0" spaces
  cat > "$BIN/kimodo-up" <<EOF
#!/usr/bin/env bash
# start the Kimodo app on localhost:7860 (first start downloads about 17 GB of weights)
cd "$WORK/kimodo-motion" && GRADIO_SERVER_PORT=7860 nohup "$WORK/venv-kimodo/bin/python" app.py > "$WORK/kimodo.log" 2>&1 &
echo "Kimodo starting (log: $WORK/kimodo.log) ..."
for _ in \$(seq 1 360); do
  grep -q "Running on" "$WORK/kimodo.log" 2>/dev/null && { echo "Kimodo ready: gen --space http://localhost:7860 ..."; exit 0; }
  pgrep -f "$WORK/venv-kimodo/bin/python app.py" >/dev/null || { echo "Kimodo stopped while starting:"; tail -30 "$WORK/kimodo.log"; exit 1; }
  sleep 5
done
echo "Still starting after 30 minutes; check $WORK/kimodo.log"
EOF
  cat > "$BIN/kimodo-down" <<EOF
#!/usr/bin/env bash
pkill -f "$WORK/venv-kimodo/bin/python app.py" && echo "Kimodo stopped." || echo "Kimodo was not running."
EOF
fi

if [ "$WHICH" != kimodo ]; then
  say "HY-Motion 1.0"
  [ -d "$WORK/HY-Motion-1.0" ] || git clone -q https://github.com/Tencent-Hunyuan/HY-Motion-1.0 "$WORK/HY-Motion-1.0"
  (cd "$WORK/HY-Motion-1.0" && git lfs pull)
  [ -d "$WORK/venv-hy" ] || python3 -m venv --system-site-packages "$WORK/venv-hy"
  "$WORK/venv-hy/bin/pip" install -q -r "$WORK/HY-Motion-1.0/requirements.txt"
  (cd "$WORK/HY-Motion-1.0" && hfdl tencent/HY-Motion-1.0 --include "HY-Motion-1.0/*" --local-dir ckpts/tencent)
  cat > "$BIN/gen-hy" <<EOF
#!/usr/bin/env bash
# gen with the local HY-Motion install (text encoders download on the first run)
exec python3 "$REPO/tools/anim-viewer/kimodo_batch.py" --backend hymotion --hy-repo "$WORK/HY-Motion-1.0" --hy-python "$WORK/venv-hy/bin/python" "\$@"
EOF
fi

chmod +x "$BIN"/* 2>/dev/null || true
grep -q "# lotr-anim-viewer-cloud" "$HOME/.bashrc" 2>/dev/null || cat >> "$HOME/.bashrc" <<EOF

# lotr-anim-viewer-cloud
export PATH="$BIN:\$PATH"
alias view="VIEW_BIND=0.0.0.0 VIEW_BROWSER=true $REPO/tools/anim-viewer/view.sh"
alias gen="python3 $REPO/tools/anim-viewer/kimodo_batch.py"
EOF
mkdir -p "$HOME/.config/lotr-anim-viewer" && touch "$HOME/.config/lotr-anim-viewer/no-commands"   # view.sh: don't ask

say "Viewer"
VIEW_BIND=0.0.0.0 VIEW_BROWSER=true "$REPO/tools/anim-viewer/view.sh" </dev/null
echo
echo "Done. Open a new shell (or: source ~/.bashrc), then:"
if [ "$WHICH" != hymotion ]; then echo "  kimodo-up && gen --space http://localhost:7860 --prompts tools/anim-viewer/prompts/orc.txt --count 3"; fi
if [ "$WHICH" != kimodo ]; then echo "  gen-hy --prompts tools/anim-viewer/prompts/orc.txt --count 3      (run kimodo-down first on a 32 GB card)"; fi
echo "  gen ... --dry-run     to preview a batch;  view stop   to stop the viewer server"
echo "Open the viewer link above in your own browser (RunPod: expose HTTP port 8000 on the pod). Export good takes from there."
echo "Stop the machine when you are done: you pay for every hour it runs."
