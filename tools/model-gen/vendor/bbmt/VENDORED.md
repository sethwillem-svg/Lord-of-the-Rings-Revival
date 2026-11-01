# Vendored: Blockbench Models Toolkit

- Source: https://github.com/dlkhwshabwlfdl-web/Blockbench-Models-Toolkit-
- Commit: 749793d8660aa035ab48b22b607fd78d8f0729f1 (2026-09-28)
- Copied: 2026-11-01. Licence: MIT (`LICENSE`).
- Contents: the compiled `dist/` (built here with `tsc` from that commit), `LICENSE`, `package.json`.

Reviewed before copying:
- `package.json` has no install or lifecycle scripts; the one runtime dependency is `pngjs`.
- The source makes no network requests and starts no processes (no `fetch`, `http`, `child_process`).
- `npm run build`, its 35 smoke checks (`scripts/smoke.mjs`) and its 121 unit tests passed.

Update by re-reviewing a newer commit the same way and re-copying `dist/`. Never install it from npm or git
at build time.
