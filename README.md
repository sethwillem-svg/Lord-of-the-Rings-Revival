# The Lord of the Rings: Revival

An open-source Middle-earth mod for **NeoForge 1.21.1**, built from scratch. The goal is feature parity with the classic LOTR Mod (Legacy, 1.7.10) on modern Minecraft.

> **Unofficial fan project.** Not affiliated with, endorsed by, or connected to the Tolkien Estate, Middle-earth Enterprises, or the authors of the original LOTR Mod. Non-commercial.

## Status

Pre-alpha. Nothing playable yet. See [the plan](docs/plan.md) and the [milestones](https://github.com/sethwillem-svg/Lord-of-the-Rings-Revival/milestones).

| Gate | Target | What it means |
| --- | --- | --- |
| G1 | Week 3 | Core APIs frozen |
| G2 | Week 8 | Playable early game (Shire to Bree) |
| G3 | Week 16 | All biomes, races and factions |
| G4 | Week 24 | Parity beta |

## Building

Requires JDK 21.

```sh
./gradlew build          # compile and package
./gradlew runData        # regenerate src/generated/resources
./gradlew runClient      # launch a dev client
./gradlew runGameTestServer
```

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). AI agents must read [AGENTS.md](AGENTS.md) first.

**No code or assets from the original LOTR Mod (Legacy or Renewed) may enter this repo.** They are All Rights Reserved. The original mod is used only as a behavior reference, through written specs in [`specs/`](specs/).

## License

- **Code:** [GNU LGPL v3.0](COPYING.LESSER) (which builds on the [GPL v3.0](LICENSE)).
- **Assets** (textures, models, sounds, structures, lang text): [CC BY-NC-SA 4.0](ASSETS_LICENSE).
- Tolkien's names, places and characters are not covered by either license and belong to their respective owners.
