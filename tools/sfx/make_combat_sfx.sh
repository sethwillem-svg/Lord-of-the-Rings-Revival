#!/usr/bin/env bash
# Synthesizes the combat sounds of specs/addon/combat-parry-warning.md from scratch with ffmpeg (no samples, no
# recordings): sines, seeded noise and envelopes only, so every sound is made for this repo.
#   tools/sfx/make_combat_sfx.sh [out_dir]
# Output: mono 44.1 kHz Ogg Vorbis, default addon/src/main/resources/assets/lotr_revival/sounds/combat/.
# The noise is a hash of the sample index (no random source), so a rerun gives the same sound.
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="${1:-addon/src/main/resources/assets/lotr_revival/sounds/combat}"
mkdir -p "$OUT"
SR=44100

# make <name> <seconds> <aevalsrc expression of t> [filter chain]
make() {
  local name="$1" dur="$2" expr="$3" filt="${4:-anull}"
  local fade
  fade=$(awk "BEGIN{print $dur-0.03}")
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "aevalsrc=exprs='$expr':s=$SR:d=$dur" \
    -af "$filt,alimiter=limit=0.89:level=false,afade=t=out:st=$fade:d=0.03" \
    -ac 1 -ar $SR -c:a libvorbis -q:a 5 -map_metadata -1 -fflags +bitexact -flags:a +bitexact "$OUT/$name.ogg"
  echo "$OUT/$name.ogg"
}

# Seeded white noise in [-1, 1]: a hash of the sample index.
N='(2*mod(abs(sin(floor(t*44100)*12.9898+78.233))*43758.5453\,1)-1)'

# Perfect parry: a bright metallic clang. Inharmonic partials (struck-bar ratios) with partial-dependent decays, a slowly
# wobbling upper partial, and a short noise click for the impact.
make parry_perfect 1.2 \
  "0.30*sin(2*PI*1180*t)*exp(-3.2*t) + 0.24*sin(2*PI*3257*t)*exp(-5*t) + 0.16*sin(2*PI*6372*t)*exp(-8*t) + 0.12*sin(2*PI*10537*t)*exp(-12*t) + 0.10*sin(2*PI*2310*t+3*sin(2*PI*7*t))*exp(-4*t) + 0.5*$N*exp(-90*t)" \
  "highpass=f=300,aecho=0.8:0.5:37:0.25"

# Glance: a short scraping ring as the blade slides off: band-passed noise with a buzz plus a falling partial.
make parry_glance 0.45 \
  "0.6*$N*exp(-9*t)*(0.6+0.4*sin(2*PI*55*t)) + 0.22*sin(2*PI*(1650-900*t)*t)*exp(-10*t)" \
  "bandpass=f=2600:t=h:w=1800,highpass=f=500"

# Shield block: a heavy wooden thud. A low sine whose pitch drops fast, with low-passed noise for the knock.
make shield_block 0.5 \
  "0.9*sin(2*PI*(95+140*exp(-30*t))*t)*exp(-11*t) + 0.45*$N*exp(-40*t)" \
  "lowpass=f=1400"

# Stagger: a whoosh of air (noise swelling and fading) with a soft low thump at the stumble. No voice.
make stagger 0.7 \
  "0.55*$N*pow(sin(PI*min(t/0.5\,1))\,2) + gt(t\,0.32)*0.8*sin(2*PI*(60+70*exp(-25*(t-0.32)))*(t-0.32))*exp(-14*(t-0.32))" \
  "bandpass=f=700:t=h:w=900,lowpass=f=2500"

# Heavy warning: a low horn-like swell with a slow tremolo, read as danger without being a voice or an alarm.
make heavy_warn 0.9 \
  "(0.45*sin(2*PI*98*t) + 0.25*sin(2*PI*147*t) + 0.12*sin(2*PI*196*t) + 0.06*sin(2*PI*294*t)) * min(t/0.12\,1) * exp(-1.6*t) * (0.75+0.25*sin(2*PI*6*t))" \
  "lowpass=f=1200"

# Counter ready: a short bright two-note chime (a fifth apart, the second a beat later).
make counter_ready 0.8 \
  "0.32*sin(2*PI*1320*t)*exp(-6*t) + 0.10*sin(2*PI*2640*t)*exp(-9*t) + gt(t\,0.09)*(0.32*sin(2*PI*1980*(t-0.09))*exp(-6*(t-0.09)) + 0.08*sin(2*PI*3960*(t-0.09))*exp(-10*(t-0.09)))" \
  "highpass=f=400"
