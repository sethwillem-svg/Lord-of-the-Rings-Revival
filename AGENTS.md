# AGENTS.md

Rules for every AI agent (and human) working in this repo. Read this before touching anything.

## 1. Clean-room rule (non-negotiable)

- The original LOTR Mod (Legacy 1.7.10, Renewed 1.16.5) is **All Rights Reserved**.
- **Implementers (Sonnet agents) never read decompiled or original LOTR Mod code.** You build only from `specs/` and from this repo.
- **Leads (Opus agents)** may study the original mod's behavior to write specs. Specs describe *what it does* (behavior, numbers, edge cases), never *how the code is written*. No code snippets, class names or method names from the original in specs.
- **No original textures, models, sounds, structures or lang strings.** All assets are made new for this repo.
- Never commit decompiled sources, original jars or extracted assets. Keep them outside the repo.

- **Behavior notes** about the original live in [`docs/reference/`](docs/reference/), written in our own words. Everyone may read them; only leads add to them.

If you're unsure whether something crosses the line, stop and ask in the ticket.

## 2. Team and ownership

| Pod | Lead | Package | Owns |
| --- | --- | --- | --- |
| Core | Opus 1 (integrator) | `core`, `datagen` | Registries, attachments, networking, alignment API, shared constants, items, blocks |
| World | Opus 2 | `world` | Dimension, biomes, map gen, roads, ores, structures |
| NPCs | Opus 3 | `npc` | Races, NPCs, AI, mounts, combat, hiring, invasions |
| Progression | Opus 4 | `progression` | Quests, achievements, titles, banners, fellowships, traders, map GUI |

Client-only code goes in `client/<pod>/`. Base package: `io.github.sethwillemsvg.lotrrevival`.

**Pods do not edit another pod's package.** Need something from core? Open a `pod:core` ticket and wait, or ask Opus 1.

## 3. Workflow

1. **Spec**: a lead writes `specs/<pod>/<system>.md` from `specs/_TEMPLATE.md` and merges it to `main`.
2. **Ticket**: the lead opens 1–2 day issues using the *Ticket* template, labeled `pod:*`, `phase:*`, `agent:*`, with a milestone.
3. **Branch**: `<pod>/<issue#>-<short-slug>` off latest `main` (for example `world/42-shire-biomes`). One branch per ticket. Use a git worktree per agent.
4. **Build locally** before every PR:
   ```sh
   ./gradlew build runData
   git diff --exit-code src/generated   # datagen output must be committed
   ```
5. **PR** to `main` using the PR template. Link the issue (`Closes #42`) and the spec.
6. **Review**: the pod lead reviews. Anything touching `core/`, `datagen/DataGenerators.java`, `build.gradle` or `gradle.properties` also needs Opus 1.
7. **Merge**: squash merge by Opus 1 only, after CI (`build` and `runtime-test`) is green. Player-visible changes also need the `playtested` label from the human maintainer.

## 4. Code rules

- **Datagen first.** Blockstates, models, loot tables, recipes, tags, lang and advancements are generated from Java providers. Never hand-edit files in `src/generated/`. Exception: structure `.nbt` files and textures.
- **One register.** Use the DeferredRegisters in `core.registry.ModRegistries`. Do not create new ones without Opus 1.
- **Ids** via `LOTRRevival.id("path")`. Registry names are `snake_case`.
- **Shared constants** (faction ids, alignment values, tags) live in `core`. Do not duplicate them in pods.
- **Player and world data** use NeoForge data attachments, not capabilities or raw NBT.
- **Item data** uses data components, not NBT.
- **Networking** uses `CustomPacketPayload` registered in core.
- No mixins without lead approval. Prefer NeoForge events.
- Logic gets a GameTest where practical (`src/main/java/.../<pod>/test/`).
- Java 21. 4-space indent. No wildcard imports.

## 5. Shared-file locks

Before editing a shared file (`ModRegistries`, `DataGenerators`, `build.gradle`, `gradle.properties`, lang `en_us` if not generated), say so in your ticket ("touching ModRegistries"). Opus 1 serializes PRs that touch the same shared file.

## 6. Commits

Conventional style: `feat(world): add Shire biome`, `fix(npc): hobbit pathing`, `spec(progression): mini-quests`. Small, focused commits.

## 7. Tooling

See [docs/tooling.md](docs/tooling.md): cloud agents via the `claude` label, the headless runtime test, NovoAtlas worldgen, local agent definitions in `.claude/agents/`, and the art MCP servers.
