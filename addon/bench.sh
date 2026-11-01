#!/usr/bin/env bash
# Renderer benchmark, unattended: builds the add-on, starts Minecraft with Middle-earth + PulseLib, creates a flat test
# world, spawns NPCs, measures each renderer (theirs, vanilla, pulse), saves screenshots, quits and prints the results.
#
#   ./addon/bench.sh            200 NPCs
#   ./addon/bench.sh 400        another count
#   ME_JAR=/path/to.jar ./addon/bench.sh   if your Middle-earth jar is not in ~/middle-earth
#
# Results: addon/run/bench_results.txt   Screenshots: addon/run/screenshots/bench_*.png
# Uses the NVIDIA GPU when the laptop has one (GPU=intel to force the Intel one).
# Don't touch the game window while it runs (about 2-3 minutes after the game has loaded).
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
COUNT="${1:-200}"
ME_JAR="${ME_JAR:-$(ls "$HOME"/middle-earth/middle-earth/build/libs/*.jar 2>/dev/null | grep -v -- '-sources' | head -1 || true)}"
if [ -z "$ME_JAR" ] || [ ! -f "$ME_JAR" ]; then
  echo "No Middle-earth jar found. Build it first:"
  echo "  cd ~/middle-earth && git checkout neoforge-fixes && ./gradlew :middle-earth:build -x test"
  echo "or point to it: ME_JAR=/path/to/middle-earth.jar $0"
  exit 1
fi
echo "Middle-earth jar: $ME_JAR"
# Laptops with Intel + NVIDIA graphics: run the game on the NVIDIA GPU (GPU=intel ./addon/bench.sh to test the Intel one)
if [ "${GPU:-auto}" != "intel" ] && command -v nvidia-smi >/dev/null 2>&1 && nvidia-smi -L >/dev/null 2>&1; then
  export __NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia __VK_LAYER_NV_optimus=NVIDIA_only
  echo "GPU: NVIDIA ($(nvidia-smi -L | head -1 | sed 's/ (UUID.*//'))"
elif [ "${GPU:-auto}" = "nvidia" ]; then
  echo "GPU=nvidia was asked for, but nvidia-smi does not work: is the NVIDIA driver installed?"; exit 1
fi
echo "Benchmark with $COUNT NPCs. The game will open, run the test by itself and close."
chmod +x gradlew
rm -f run/bench_results.txt run/screenshots/bench_*.png
mkdir -p run
echo "Running... (first launch downloads Minecraft and takes a few minutes; full output in addon/run/bench_output.log)"
./gradlew runClient -PmeJar="$ME_JAR" -Pautotest="$COUNT" --console=plain > run/bench_output.log 2>&1 || true
echo
if [ -f run/bench_results.txt ]; then
  cat run/bench_results.txt
  echo
  echo "Saved to $(pwd)/run/bench_results.txt (screenshots in run/screenshots/). Paste it back to Claude."
else
  echo "The benchmark did not finish. Last lines of the game log:"
  tail -n 30 run/logs/latest.log 2>/dev/null || true
fi
