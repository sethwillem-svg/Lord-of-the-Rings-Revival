# Playtesting guide

Welcome, and thanks for testing. You do not need to write code. You need Java, a clone of this repo and about 30 minutes for the first build.

## 1. One-time setup

1. Install **JDK 21** (for example Temurin 21) and Git.
2. Clone and switch to the test branch:
   ```sh
   git clone https://github.com/sethwillem-svg/Lord-of-the-Rings-Revival.git
   cd Lord-of-the-Rings-Revival
   git checkout integrate/wave-g5
   ```
3. Launch a dev client (the first run downloads Minecraft and builds the mod, which can take 20 to 30 minutes):
   ```sh
   ./gradlew runClient
   ```
   On Windows use `gradlew.bat runClient`. Later runs are much faster.
4. To get newer changes later: `git pull`, then `./gradlew runClient` again.

## 2. What to test

Work through the checklist for the current wave, ticking boxes as you go:

- [`wave-g5.md`](https://github.com/sethwillem-svg/Lord-of-the-Rings-Revival/blob/integrate/wave-g5/docs/playtest/wave-g5.md) (on the test branch): gear, bounties, waypoints and fast travel, bridges, portal.
- `world-scale.md` (added when the world-scale PR is approved): compares Half and Quarter map sizes.

Create a **new** creative or survival world for each checklist. Handy commands are listed at the top of each checklist (`/lotrrevival middleearth`, `/lotr waypoint unlock @s all`, and so on).

## 3. Reporting what you find

Open an issue with the **Bug** template and include:

- The checklist item and what you expected.
- What happened instead (a screenshot or short clip helps a lot).
- `logs/latest.log` from the `run/` folder. Crashes also write to `run/crash-reports/`.
- Your OS and whether you used any extra mods.

Things that feel off but are not broken (too slow, too ugly, confusing) are just as useful. Say so in the issue.

## 4. Ground rules

- Test only. Do not merge pull requests; the maintainer approves each one after playtesting.
- Do not copy anything from the original LOTR Mod into issues or pull requests (code, textures, text). Describing how it behaved is fine. See [AGENTS.md](../../AGENTS.md).
- Questions go in the issue or pull request you are testing.
