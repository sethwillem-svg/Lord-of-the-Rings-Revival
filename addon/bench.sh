#!/usr/bin/env bash
# Renderer benchmark, unattended: builds the add-on, starts Minecraft with Middle-earth + PulseLib, creates a flat test
# world, spawns NPCs, measures each renderer (theirs, vanilla, pulse), saves screenshots, quits and prints the results.
#
#   ./addon/bench.sh              200 NPCs: 30 s break, then a minute of vanilla and a minute of pulse under Spark (links printed)
#   ./addon/bench.sh 400          another count
#   ./addon/bench.sh 200 measure  the timed rounds instead (theirs, vanilla, pulse, then reversed; no Spark)
#   ./addon/bench.sh export [N]   record every Middle-earth NPC type (N of each, default 1) for the NPC clip viewer, then quit
#   ./addon/bench.sh play         just open the game (in the test world once it exists) and leave it to you: commands work
#   ME_JAR=/path/to.jar ./addon/bench.sh   if your Middle-earth jar is not in ~/middle-earth
#
# Results: addon/run/bench_results.txt   Screenshots: addon/run/screenshots/bench_*.png
# The test world (addon/run/saves/lotr_bench) is kept and reopened directly next time; delete it to start fresh.
# Uses the NVIDIA GPU when the laptop has one (GPU=intel to force the Intel one).
# Don't touch the game window while it runs (about 3 minutes after the game has loaded).
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
COUNT="${1:-200}"
RUN="${2:-profile}"   # profile (default): 30 s break, a minute of vanilla and a minute of pulse under Spark; measure: the timed rounds
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
[ "$COUNT" = "play" ] || [ "$COUNT" = "export" ] || echo "Benchmark with $COUNT NPCs. The game will open, run the test by itself and close."
chmod +x gradlew
rm -f run/bench_results.txt run/screenshots/bench_*.png
mkdir -p run/config
# NeoForge's early loading window can fail to hand over to Minecraft on hybrid Intel/NVIDIA laptops ("trouble handing off
# the window"): use Minecraft's own window from the start
if [ -f run/config/fml.toml ] && grep -q '^earlyWindowControl' run/config/fml.toml; then
  sed -i 's/^earlyWindowControl.*/earlyWindowControl = false/' run/config/fml.toml
else
  echo 'earlyWindowControl = false' >> run/config/fml.toml
fi
echo "Running... (first launch downloads Minecraft and takes a few minutes; full output in addon/run/bench_output.log)"
QUICK=()
[ -d run/saves/lotr_bench ] && QUICK=(-PquickPlay=lotr_bench)   # skip the menus and world generation after the first run
if [ "$COUNT" = "export" ]; then
  PER="${2:-1}"
  echo "Recording every Middle-earth NPC type ($PER of each) for the NPC viewer. The game opens, does it by itself and closes."
  rm -rf run/viewer_export
  ./gradlew runClient -PmeJar="$ME_JAR" -Pexport="$PER" "${QUICK[@]}" --console=plain > run/bench_output.log 2>&1 || true
  if [ -f run/viewer_export/index.json ]; then
    grep "lotr_revival export" run/logs/latest.log | tail -n 3 | sed 's/.*\[lotr_revival export\] /  /'
    echo "Done. Open the viewer and pick an NPC under Model:  ./tools/anim-viewer/view.sh npc"
  else
    echo "The export did not finish. Last lines of the game log:"; tail -n 30 run/logs/latest.log 2>/dev/null || true
  fi
  exit 0
fi
if [ "$COUNT" = "play" ]; then
  echo "Opening the game for you (no benchmark). Try: /lotrbench spawn 20 middle-earth:gondor.soldier ai   /lotrrender stance shield_brace"
  ./gradlew runClient -PmeJar="$ME_JAR" "${QUICK[@]}" --console=plain > run/bench_output.log 2>&1 || true
  exit 0
fi
PROFILE=(-Pprofile)
[ "$RUN" = "measure" ] && PROFILE=()
./gradlew runClient -PmeJar="$ME_JAR" -Pautotest="$COUNT" "${QUICK[@]}" "${PROFILE[@]}" --console=plain > run/bench_output.log 2>&1 || true
echo
if [ -f run/bench_results.txt ]; then
  cat run/bench_results.txt
  echo
  echo "Saved to $(pwd)/run/bench_results.txt (screenshots in run/screenshots/). Paste it back to Claude."
  # Spark profiles (uploaded by Spark): which renderer each link belongs to
  if grep -q "spark profile of" run/logs/latest.log 2>/dev/null; then
    echo; echo "Spark profiles (open in a browser; send these links to Claude):"
    LINKS=$(awk '/spark profile of .* start / {sub(/.*spark profile of /,""); m[++nm]=$1} match($0,/https?:\/\/spark\.lucko\.me\/[A-Za-z0-9]+/) && substr($0,RSTART,RLENGTH) !~ /\/docs$/ {l[++nl]=substr($0,RSTART,RLENGTH)} END {for (i=1;i<=nl;i++) print "  " (i<=nm ? m[i] : "profile " i) ": " l[i]}' run/logs/latest.log)
    if [ -n "$LINKS" ]; then echo "$LINKS"; else
      echo "  (no links found; what Spark said:)"
      grep -i "spark" run/logs/latest.log | grep -v "Loading\|spark 1.10\|Found mod\|mod file" | tail -n 25 | cut -c1-250
    fi
  fi
  # how each Middle-earth armour piece was recorded for PulseLib (for debugging the pulse renderer)
  grep -A6 "lotr_revival: recorded\|lotr_revival: could not record\|is not a HumanoidModel" run/logs/latest.log > run/armour_debug.txt 2>/dev/null || true
  if [ -s run/armour_debug.txt ]; then
    echo; echo "Armour recordings (also in $(pwd)/run/armour_debug.txt):"; head -n 40 run/armour_debug.txt | cut -c1-300
  fi
else
  echo "The benchmark did not finish. Last lines of the game log:"
  tail -n 30 run/logs/latest.log 2>/dev/null || true
fi
