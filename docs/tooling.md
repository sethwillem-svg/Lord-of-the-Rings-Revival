# Tooling

What speeds this project up, and how to set each piece up.

## 1. Cloud agents from GitHub issues (`.github/workflows/claude.yml`)

- Add the **`claude`** label to an issue: an agent implements it on a branch and opens a PR. `type:spec` issues run on Opus, everything else on Sonnet.
- Comment **`@claude ...`** on any issue or PR to get a response or a change.
- Runs on GitHub's runners, which can reach NeoForge's maven, so agents can build and run datagen.
- Public repos get about 20 concurrent free runners, so many labeled issues can be worked at once.

**One-time setup:**
1. Install the Claude GitHub app on this repo: <https://github.com/apps/claude>.
2. Add ONE repo secret (Settings → Secrets and variables → Actions):
   - `CLAUDE_CODE_OAUTH_TOKEN`: uses your Claude subscription. Generate it on a computer with `claude setup-token`.
   - or `ANTHROPIC_API_KEY`: from <https://console.anthropic.com>, billed per token.

## 2. Runtime test in CI (`runtime-test` job in `build.yml`)

[mc-runtime-test](https://github.com/headlesshq/mc-runtime-test) boots a real headless NeoForge 1.21.1 client with the built jar, joins a world, runs `/test runall`, and fails the PR on crashes or failed GameTests.

## 3. Worldgen from images: NovoAtlas

[NovoAtlas](https://github.com/TheDeathlyCow/novoatlas) (LGPL-3.0) builds terrain and biomes from a painted heightmap and biome map. It is **bundled in our jar** with ModDevGradle jar-in-jar (`jarJar` in `build.gradle`, version pinned by `novoatlas_file_id` in `gradle.properties`) and is a `required` dependency in `neoforge.mods.toml`. It is also on the runtime classpath for `runClient`, `runServer` and `runGameTestServer`. We do not compile against it: the World pod wires it through data (map_infos, density functions, biome source). Credit is in [NOTICE](../NOTICE). Verify the bundle with `unzip -l build/libs/*.jar | grep jarjar`.

The Middle-earth maps must be **our own drawings**. Don't trace Mevans' map or Tolkien's published maps.

## 4. Local Claude Code

- Agent definitions are in `.claude/agents/`: `lead-core`, `lead-world`, `lead-npc`, `lead-progression` (Opus), `implementer` (Sonnet, runs in its own worktree) and `artist`.
- Run parallel sessions with `claude --worktree <name>`, or dispatch background sessions with `claude agents`.
- Agent teams (experimental): set `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`. Teammates share one checkout, so give each a different pod package.
- `/batch` fans one large repetitive change (for example, 400 block variants) out to isolated subagents.

## 5. Art agents (local, optional)

- **Textures:** [mc-asset](https://github.com/smile-minecraft/mc-asset) is a CLI and MCP server where agents draw 16x16 textures as text grids, make material variants, seam-check tiles and validate the pack.
- **Models:** [blockbench-mcp](https://github.com/sosadly/blockbench-mcp) connects Claude Code to Blockbench desktop: build, texture, rig, animate, screenshot, and a human review step.
  ```sh
  git clone https://github.com/sosadly/blockbench-mcp && cd blockbench-mcp && npm install && npm run build
  # Blockbench: File > Plugins > Load Plugin from File > plugin/blockbench_mcp.js
  claude mcp add blockbench -- node /absolute/path/to/blockbench-mcp/dist/index.js
  ```

## 6. Cloud Claude sessions (claude.ai/code)

To let cloud sessions build the mod, allow these domains in claude.ai → Settings → Capabilities:

```
maven.neoforged.net
plugins.gradle.org
services.gradle.org
downloads.gradle.org
repo.maven.apache.org
repo1.maven.org
maven.parchmentmc.org
api.foojay.io
libraries.minecraft.net
launchermeta.mojang.com
piston-meta.mojang.com
piston-data.mojang.com
resources.download.minecraft.net
cursemaven.com
edge.forgecdn.net
mediafilez.forgecdn.net
```
