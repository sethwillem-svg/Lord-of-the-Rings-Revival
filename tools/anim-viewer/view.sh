#!/usr/bin/env bash
# Open the mocap rig viewer watching <repo>/clips (any browser).
#
#   view                      start (or reuse) the server and open the viewer with mordor_orc
#   view gundabad_olog_hai    same, with another unit from src/main/art/blockbench (name without .bbmodel)
#   view stop                 stop the server
#
# Port: VIEW_PORT (default 8000). Browser: VIEW_BROWSER (default: xdg-open, so your default browser).
set -euo pipefail
REPO="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/../.." && pwd)"
PORT="${VIEW_PORT:-8000}"

if [ "${1:-}" = "stop" ]; then
  pids=$(pgrep -f "http.server $PORT --bind 127.0.0.1 --directory $REPO" || true)
  [ -n "$pids" ] && kill $pids && echo "Viewer server stopped." || echo "No viewer server running on port $PORT."
  exit 0
fi

UNIT="${1:-mordor_orc}"
MODEL=$(cd "$REPO" && find src/main/art/blockbench -name "$UNIT.bbmodel" -not -path '*/lod/*' | head -1)
if [ -z "$MODEL" ]; then
  echo "No model named $UNIT in src/main/art/blockbench. Examples:"
  (cd "$REPO" && find src/main/art/blockbench -name '*.bbmodel' -not -path '*/lod/*' -printf '  %f\n' | sed 's/\.bbmodel$//' | sort | head -12)
  exit 1
fi

mkdir -p "$REPO/clips"
[ -f "$REPO/clips/.gitignore" ] || printf '*\n' > "$REPO/clips/.gitignore"   # keeps generated clips out of git

if ! (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
  nohup python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$REPO" >/dev/null 2>&1 &
  for _ in 1 2 3 4 5 6 7 8 9 10; do (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null && break; sleep 0.3; done
fi

URL="http://localhost:$PORT/tools/anim-viewer/?watch=/clips/&model=/$MODEL"
echo "Viewer: $URL"
echo "Clips folder: $REPO/clips   (generate with: gen \"A person ...\")"
${VIEW_BROWSER:-xdg-open} "$URL" >/dev/null 2>&1 &
