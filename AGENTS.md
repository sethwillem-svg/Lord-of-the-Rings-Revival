# AGENTS.md

> **Add-on (since 2026-11-01).** This repo builds an add-on for the Middle-earth mod (Jukoz, NeoForge 1.21.1, branch
> `neoforge-fixes`). The from-scratch mod, pods and ticket workflow are retired; their specs are in `specs/archive/` for
> reference only. Never build from an archived spec.

Rules for every AI agent (and human) working in this repo. Read this before touching anything.

## 1. Clean-room rule (non-negotiable)

- The original LOTR Mod (Legacy 1.7.10, Renewed 1.16.5) is **All Rights Reserved**.
- **Implementers (Sonnet agents) never read decompiled or original LOTR Mod code.** You build only from `specs/` and from this repo.
- **Leads (Opus agents)** may study the original mod's behavior to write specs. Specs describe *what it does* (behavior, numbers, edge cases), never *how the code is written*. No code snippets, class names or method names from the original in specs.
- **No original textures, models, sounds, structures or lang strings.** All assets are made new for this repo.
- Never commit decompiled sources, original jars or extracted assets. Keep them outside the repo.

- **Middle-earth mod** is All Rights Reserved too: never copy its code, textures, models, structures or lang files here. Its public classes (and the Seven Stars API) are integration points: call them, never copy them. The add-on depends on its jar; it does not contain it.

If you're unsure whether something crosses the line, stop and ask.

## 2. Layout

| Path | What |
| --- | --- |
| `addon/` | The add-on (`lotr_revival`), package `io.github.sethwillemsvg.lotrrevival`, one package per system: `combat`, `hiring`, `nodemap`, `nemesis`, `events`, `structures`; client code in `client/<system>/`; shared helpers in `core/` |
| `specs/addon/` | Current specs. `specs/archive/` holds the retired standalone-mod specs |
| `tools/anim-viewer/`, `tools/anim/` | Animation pipeline |
| `src/main/art/blockbench/` | Orc, olog and troll models |
| `.github/workflows/build.yml` | CI: builds Middle-earth, builds the add-on, runs game tests, uploads one log per system |

## 3. Workflow

1. **Spec:** `specs/addon/<system>.md` from `specs/_TEMPLATE.md`. Specs list design choices and open questions; the maintainer reviews them.
2. **Branch:** one per piece of work. Agents working in parallel use separate git worktrees and stay inside their system's package.
3. **Build:** CI is the build of record (cloud sessions often cannot reach Mojang's or NeoForged's servers). Locally:
   ```sh
   cd addon && ./gradlew build runGameTestServer -PmeJar=<middle-earth jar> -PmeApiJars=<sevenstars-api jar> -Pdebug=all
   ```
4. **Debug:** read the CI artifact `logs-<run>`: `summary.md`, one `<system>.log` per system, `errors.log`. See `specs/addon/debug-logging.md`.
5. **Merge:** to `main` after CI is green; player-visible changes also need a playtest by the maintainer.

## 4. Code rules

- Log through `core.log.LotrLog` with your system's name; debug lines through `LotrLog.dbg` (see the logging spec).
- Integrate with Middle-earth through events, its public API and the Seven Stars API. A mixin only when nothing else works, and say why in the class comment.
- Player and world data use NeoForge data attachments or `SavedData`; item data uses data components; packets use `CustomPacketPayload`.
- Numbers from specs are config values (`lotr_revival-common.toml`) unless the spec says data.
- Logic gets a GameTest in `<system>/test/` with `@GameTest(template = "empty")`. Mock players from `makeMockServerPlayerInLevel` share one name and stay in the player list: always remove them in a `finally`.
- Java 21. 4-space indent. No wildcard imports.

## 5. Commits

Conventional style: `feat(combat): parry window`, `fix(nodemap): travel cost`, `spec(addon): structures`. Small, focused commits.
