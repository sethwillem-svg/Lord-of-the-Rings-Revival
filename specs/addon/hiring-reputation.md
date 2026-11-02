# Hiring, reputation and disguise (Middle-earth add-on)

- **Project:** 1 of 4 for the add-on (then: node map with waypoints and fast travel, nemesis system and world events, structures from licensed builds)
- **Status:** in design (decisions below agreed with the maintainer on 2026-11-02; sections still open are marked)
- **Builds on:** the Middle-earth mod (NeoForge 1.21.1, branch `neoforge-fixes`) and its Seven Stars API, used through their public classes only; both are All Rights Reserved, nothing is copied.
- **Supersedes for the add-on:** `specs/npc/hiring-trading.md` (written for the old standalone mod). Traders and coins of our own are out: Middle-earth plans trading, and has its own silver coins.

## Decisions so far

### Reputation
- A score per player per Middle-earth faction, stored on the player, saved, synced to the client.
- Start: the faction chosen in Middle-earth's onboarding +100 (first hiring tier); factions HOSTILE to it -100; all others 0.
- Tiers (config): below 0 Hostile (recruiters refuse), 0-99 Neutral (no hiring), 100 Trusted, 300 Respected, 700 Honoured, 1500 Champion, 3000 Hero.
- Army cap from the best tier over all factions: 4 / 8 / 14 / 22 / 30; maximum 30 by default (config).
- In the first version reputation drives hiring and recruiter willingness only; who attacks whom stays Middle-earth's diplomacy (except disguise, below).
- Shown on a Reputation screen (key) and by `/reputation`.

### Kill rule
Killing an NPC of faction X (Middle-earth affinities: ALLY, FRIENDLY, HOSTILE):

| Who | Change |
| --- | --- |
| X | -2 |
| each faction ALLY or FRIENDLY with X | -1 |
| X's sworn enemy (table below) | +2 |
| each other faction HOSTILE to X | +1 |

Multipliers: captain and leader NPC types x10, nemeses x25 (nemesis project). Kills by your hired units count at 50%.
Example, Gondor player kills a Moria goblin: Moria -2, Moria's friends -1, Erebor +2 (Moria's sworn enemy), Gondor and the other good factions +1.

### Sworn enemies
Middle-earth has no "hates most" level, so the add-on keeps this table as datapack data (`data/lotr_revival/sworn_enemies/*.json`, one file per faction, overridable):

| Faction | Sworn enemy |
| --- | --- |
| gondor | mordor |
| mordor | gondor |
| rohan | isengard |
| isengard | rohan |
| longbeards.erebor (and longbeards) | hobgoblin_tribes.gundabad |
| hobgoblin_tribes.gundabad (and hobgoblin_tribes) | longbeards.erebor |
| moria | longbeards.erebor |
| lothlorien | moria |
| woodland_realm | goblin_town |
| goblin_town | woodland_realm |
| dale | hobgoblin_tribes.gundabad |
| shire | mordor |
| brigand, wild_goblins | none |

### Recruiting
- Recruiters are Middle-earth's existing leader and captain NPC types (for example `gondor.leader`, `mordor.captain`, `isengard.leader`, `rohan.eorling_marshal`, `lothlorien.lord`, `woodland_realm.commander`, `longbeards.erebor.leader`, `hobgoblin_tribes.gundabad.leader`, `brigand.chieftain`, `dale.sergeant`).
- A recruiter offers its faction's NPC types; better units need higher tiers.
- Price: Middle-earth silver coins plus a minimum reputation tier.
- A hire spawns a Middle-earth NPC of that type and attaches our data (owner, squad, orders, level, kept gear).

### Units
- Approach 1: hired units stay Middle-earth NPCs; add-on behaviour is added to their AI as top-priority goals, built on Seven Stars API brain tasks where they fit (defend home, stroll around home, move to remembered position).
- Squads: each recruiter purchase is a squad.
- Controls in the Conqueror's Blade style: number keys select a squad (or all), a command wheel (follow, hold here at the looked-at block, charge the looked-at target, formation, dismiss), quick keys for follow / hold / charge; without orders units follow and defend you.
- Formations: line, shield wall, column, wedge, loose.
- You can equip units through their menu; the gear stays with them.
- Travel: units on "follow" teleport with you (fast travel, dimension change) or catch up when far behind; units holding a position stay.
- Death: a fallen unit can be revived at a recruiter of its faction for half its hire price, keeping its gear and progress.
- Units stay in the world when you log out and resume when you return.

### Disguise (open: details being designed)
- Wearing a full set (all four pieces) of an enemy faction's armour disguises you to that faction: its NPCs do not attack you on sight in its territory.
- No disguise while you have hired units with you.
- Which faction an armour piece belongs to comes from Middle-earth's NPC type gear: a piece belongs to the factions whose NPC types wear it.

## Open sections
- Disguise: how it breaks.
- Unit progression (modest levels, no grind).
- Formations: spacing, how each behaves while following and holding.
- Technical design: data attachments, packets, goals and mixins, Seven Stars API use, persistence.
- Testing.
