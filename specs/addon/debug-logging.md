# Debug logging and CI (Middle-earth add-on)

- **Status:** draft (2026-11-05), written by the lead; the maintainer reviews after.
- **Applies to:** every add-on system. Each spec has a **Logging** section that lists its events, using the rules below.

## Summary

Every system logs what it decides, under its own name, so a CI run can be read system by system. Debug lines are off in normal play and on in CI. GitHub Actions builds the Middle-earth jar, builds the add-on, runs the game tests on a headless server, then splits the log into one file per system and uploads them.

## Rules

### Loggers

- One logger per system, named `lotr_revival.<system>`. System names: `core`, `combat`, `hiring`, `formation`, `reputation`, `disguise`, `nodemap`, `travel`, `nemesis`, `events`, `structures`, `anim`, `render`, `test`.
- Code gets it from one helper: `LotrLog.get("<system>")` (package `io.github.sethwillemsvg.lotrrevival.core.log`). No other `LogUtils.getLogger()` in new code.
- `LotrLog.debug("<system>")` says whether debug lines are on for that system.

### Turning debug on

| Switch | Effect |
| --- | --- |
| JVM property `lotr_revival.debug=all` | debug on for every system |
| `lotr_revival.debug=combat,nemesis` | debug on for the listed systems |
| config `debug.systems` (common config, list) | same, from the config file; the property wins if both are set |
| nothing | debug off; only INFO and above |

- Debug lines are written at **INFO** with the prefix `[DBG <system>]`, so they reach `latest.log` without a Log4j config. They are guarded by `LotrLog.debug(...)` so they cost nothing when off.
- Normal lines use the prefix `[<system>]`.

### What every system logs

| Level | When |
| --- | --- |
| INFO (always) | setup: registered content, loaded data files (count and ids), config values in use |
| WARN (always) | bad data skipped (file and reason), a fallback taken, a limit reached |
| ERROR (always) | an exception caught at a system boundary, with the stack trace |
| DBG | every decision: inputs, the rule applied, the result. Example: `[DBG combat] parry ok: defender=uuid weapon=sword window=4/6 ticks attacker=uuid dmg 7.0->0.0` |

- Lines carry ids (entity UUID short form, faction id, structure id, waypoint id), positions as `x,y,z`, and the game tick.
- A system logs a **summary line** once per 600 ticks while debug is on (counts of what it did in that window), so long runs stay readable.
- No log line per tick per entity. Per-tick events are counted and go in the summary.

### Game tests

- Every game test logs `[test] START <name>` and `[test] PASS|FAIL <name>: <reason>` through the `test` logger.
- Tests live in `<system>/test/` and use `@GameTest(template = "empty")` (the template is `addon/src/main/resources/data/lotr_revival/structure/empty.nbt`).

## CI (GitHub Actions)

Workflow `.github/workflows/build.yml`, on push and pull request, and by hand (`workflow_dispatch` with a `debug` input, default `all`).

1. Check out the repo. Set up Java 21 (Temurin) and Gradle caching.
2. **Middle-earth jar:** clone `Jukoz/middle-earth`, branch `neoforge-fixes`, at depth 1 outside the workspace; cache the jar keyed by the branch head commit; build `:middle-earth:jar` on a cache miss. The jar is never uploaded or committed (All Rights Reserved).
3. `./gradlew build -PmeJar=<jar>` in `addon/`. Compiler output is saved as `compile.log`.
4. `./gradlew runGameTestServer -PmeJar=<jar> -Pdebug=<input>` in `addon/`, with a time limit of 20 minutes.
5. **Split logs:** a script (`.github/scripts/split-logs.sh`) reads `run/gametest/logs/latest.log` and writes `logs/<system>.log` for each system (lines with `[<system>]` or `[DBG <system>]`), `logs/errors.log` (WARN, ERROR and stack traces), and `logs/summary.md` (per system: lines, warnings, errors; per test: pass or fail). The summary is also written to the job summary.
6. Upload `logs/`, `compile.log` and the full `latest.log` as the artifact `logs-<run number>`, kept 14 days, whether the run passed or failed.
7. The job fails if compiling fails, a required game test fails, or the server crashes.

## Out of scope

- Remote log collection, metrics, a client-side test run in CI (needs a display).

## Acceptance tests

- [ ] With `-Pdebug=combat`, `latest.log` has `[DBG combat]` lines and no `[DBG nemesis]` lines.
- [ ] A CI run uploads one log file per system that logged, plus `errors.log` and `summary.md`.
