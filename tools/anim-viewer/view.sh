#!/usr/bin/env bash
# Open the mocap rig viewer watching <repo>/clips (any browser).
#
#   animview                      start (or reuse) the server and open the viewer with mordor_orc
#   animview gundabad_olog_hai    same, with another unit from src/main/art/blockbench (name without .bbmodel)
#   animview npc                  open the Middle-earth NPC clip viewer (npc.html)
#   animview stop                 stop the server
#   animview setup                (re)add the `animview` and `animgen` commands to your shell
#
# First run from the repo (./tools/anim-viewer/view.sh) offers to add the `animview` and `animgen` commands for you.
# (Not `view`/`gen`: `view` is vim's read-only mode on most Linux systems.)
# Port: VIEW_PORT (default 8000). VIEW_BIND=0.0.0.0 serves other machines too (cloud GPU). Browser: VIEW_BROWSER (default: your default browser via xdg-open or open).
set -euo pipefail
REPO="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/../.." && pwd)"
PORT="${VIEW_PORT:-8000}"
BIND="${VIEW_BIND:-127.0.0.1}"   # 0.0.0.0 to reach the viewer from another machine (cloud GPU)

RC="$HOME/.bashrc"; [ "${SHELL##*/}" = "zsh" ] && RC="$HOME/.zshrc"
MARK="$HOME/.config/lotr-anim-viewer/no-commands"
setup_commands() {   # add `animview` and `animgen` aliases to the shell config, once
  if grep -q '^# lotr-anim-viewer$' "$RC" 2>/dev/null; then   # replace the old `view`/`gen` aliases (view clashes with vim)
    sed -i.bak '/^# lotr-anim-viewer$/,/^alias gen=/d' "$RC"
    add_aliases; echo "Renamed the commands to animview and animgen (view is vim's). Run: source $RC"; return 0
  fi
  grep -q "alias animview=" "$RC" 2>/dev/null && { [ "${1:-}" = force ] && echo "Commands are already in $RC."; return 0; }
  if [ "${1:-}" != force ]; then
    [ -t 0 ] && [ ! -f "$MARK" ] || return 0
    read -r -p "Add the 'animview' and 'animgen' commands to $RC so they work from anywhere? [Y/n] " answer
    case "$answer" in [nN]*) mkdir -p "$(dirname "$MARK")"; touch "$MARK"; return 0;; esac
  fi
  add_aliases
  echo "Added. New terminals have them; in this one run: source $RC"
}
add_aliases() {
  printf '\n# lotr-anim-viewer commands\nalias animview="%s"\nalias animgen="python3 %s"\n' \
    "$REPO/tools/anim-viewer/view.sh" "$REPO/tools/anim-viewer/kimodo_batch.py" >> "$RC"
}
if [ "${1:-}" = "setup" ]; then setup_commands force; exit 0; fi
setup_commands

if [ "${1:-}" = "stop" ]; then
  pids=$(pgrep -f "(http.server|serve.py) $PORT --bind .* --directory $REPO" || true)
  [ -n "$pids" ] && kill $pids && echo "Viewer server stopped." || echo "No viewer server running on port $PORT."
  exit 0
fi

NPC_VIEW=""
if [ "${1:-}" = "npc" ]; then NPC_VIEW=1; set -- mordor_orc; fi   # animview npc: the Middle-earth NPC clip viewer
UNIT="${1:-mordor_orc}"
MODEL=$(cd "$REPO" && find src/main/art/blockbench -name "$UNIT.bbmodel" -not -path '*/lod/*' | head -1)
if [ -z "$MODEL" ]; then
  echo "No model named $UNIT in src/main/art/blockbench. Examples:"
  (cd "$REPO" && find src/main/art/blockbench -name '*.bbmodel' -not -path '*/lod/*' | sed 's#.*/##; s/\.bbmodel$//; s/^/  /' | sort | head -12)
  exit 1
fi

mkdir -p "$REPO/clips"
[ -f "$REPO/clips/.gitignore" ] || printf '*\n' > "$REPO/clips/.gitignore"   # keeps generated clips out of git

# Something already on the port must be our no-cache server for this repo. An older view.sh started a plain http.server
# (the browser caches it), possibly for another copy of the repo: then the viewer shows old files. Replace it.
if (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
  if ! curl -sf "http://127.0.0.1:$PORT/tools/anim-viewer/serve.py" 2>/dev/null | cmp -s - "$REPO/tools/anim-viewer/serve.py" \
     || ! curl -sfI "http://127.0.0.1:$PORT/tools/anim-viewer/npc.html" 2>/dev/null | grep -qi '^cache-control: no-store'; then
    old=$(pgrep -f "([h]ttp.server|[s]erve.py) $PORT" || true)   # [x]: never match this script
    if [ -n "$old" ]; then
      echo "Replacing an old viewer server on port $PORT."; kill $old; sleep 0.5
    else
      echo "Port $PORT is used by another program. Run with another port: VIEW_PORT=8010 $0 $*"; exit 1
    fi
  fi
fi
if ! (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
  nohup python3 "$REPO/tools/anim-viewer/serve.py" "$PORT" --bind "$BIND" --directory "$REPO" >/dev/null 2>&1 &
  for _ in 1 2 3 4 5 6 7 8 9 10; do (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null && break; sleep 0.3; done
fi

URL="http://localhost:$PORT/tools/anim-viewer/?watch=/clips/&model=/$MODEL"
[ -n "$NPC_VIEW" ] && URL="http://localhost:$PORT/tools/anim-viewer/npc.html"
echo "Viewer: $URL"
[ -n "${RUNPOD_POD_ID:-}" ] && echo "From your own browser: https://$RUNPOD_POD_ID-$PORT.proxy.runpod.net/tools/anim-viewer/?watch=/clips/&model=/$MODEL"
echo "Clips folder: $REPO/clips   (generate with: animgen \"A person ...\")"
OPEN="${VIEW_BROWSER:-$(command -v xdg-open || command -v open || true)}"
if [ -n "$OPEN" ]; then "$OPEN" "$URL" >/dev/null 2>&1 & else echo "Open that address in your browser."; fi
