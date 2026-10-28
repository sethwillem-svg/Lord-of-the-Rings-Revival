# Legacy reference: conquest and territory

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: everything Legacy did that touches land control: the conquest grid, how conquest is earned, spread, cleansed and lost, what it changes (NPC spawns, fast travel, the conquest map), and how it relates to areas of influence and invasions. Sources: playing the mod, the community wiki, and a study of the 36.15 jar's behavior. No code, internal names or text from the original appear here.

**Short version.** Legacy had no territory *ownership*. It had **conquest**: a per-faction number stored on a grid of 1,024 x 1,024-block squares. Pledged players raise their faction's number by killing its enemies; the number makes that faction's NPCs spawn more often (and unlocks extra NPC types) in the square and its neighbours. Nothing ever "changes hands": no borders, no structure capture, no effect on roads or invasion frequency. Everything beyond that in `specs/world/territory.md` is our own design.

## 1. The grid

| Thing | Legacy value |
| --- | --- |
| Square size | 8 x 8 biome-map pixels = **1,024 x 1,024 blocks** (64 x 64 chunks) |
| Alignment to the map | Squares are aligned to the biome map, not to block 0: the map's own origin pixel (810, 730) is the corner reference, so square edges fall on every 8th map pixel counted from the image's top-left corner |
| Grid extent | The whole biome map: 400 x 500 squares for a 3,200 x 4,000-pixel map. Positions outside the map are a dummy square that never stores anything |
| Stored per square | One strength number per playable faction (all 25 alignment factions), plus the world time of the square's last change |
| Range of a strength | 0 and up, no upper cap in normal play (the admin command accepts up to 100,000). Never negative |
| Storage | One small file per square that has any non-zero strength; squares that fall back to all-zero are deleted. Changed squares are saved by the server's periodic save |
| Where it works | Middle-earth dimension only. Disabled entirely by a server config switch ("enable conquest", default on) and in the "classic" (no-map) world type |

## 2. Earning conquest

### From kills

| Rule | Value |
| --- | --- |
| Who | Only a player **pledged** to a faction. Unpledged players never generate conquest, whatever their alignment |
| Which kills | Killing an NPC whose faction is one the pledge faction gains alignment from (its enemies). Conquest is earned even where the kill earns no alignment (outside the faction's area of influence) |
| Amount | The victim's kill value (civilian 1, warrior 2, captain 5, bosses more) x world conquest rate x the biome's conquest gain rate at the player's position |
| World conquest rate | Default 1.0, set per world by an op command (0 to 100), saved with the world |
| Biome gain rate | 1.0 normally. 0.2 in hard-to-take homelands (the Shire, the elf realms, Rivendell, Lothlórien, Fangorn, the Pelennor, Umbar and similar: 14 biomes), 0.5 in 11 biomes (Angmar, Mordor, Lindon, Dunland, Pelargir and others), 0.75 in 3 (Ettenmoors, Dorwinion, Rhûdel) |
| Hired units | A kill by the player's hired unit gives 25% of the conquest (and no alignment) |
| Civilians | Factions that disapprove of civilian kills earn nothing from them, so no conquest either |
| Per-player opt-out | A player option turns off conquest from their own kills |
| Square used | The square the **killer** stands in, not the victim |

### Radial spread

Each conquest award is spread over every square whose centre is within 3.5 squares of the killer's square (37 squares in a rough disc). A square at distance d (in squares, centre to centre) receives `amount x (1 - d / 3.5)`: 100% in the centre, about 71% next door, 43% two squares out, 14% three squares out.

### Cleansing comes first

In each square of the spread, if the **victim's** faction has strength there, the award reduces that faction's strength instead (by the same amount) and the killer's faction gains nothing in that square. Only where the enemy has no foothold does the pledge faction's strength rise. So taking land back from an occupier is a two-step job: wipe their number out, then build yours.

### From invasions

When an invasion is beaten, the contributors who are pledged to an enemy of the invading faction share **50 conquest** split evenly between them, applied exactly like a kill award (radial spread, cleansing the invader first, gain rates applied).

### Feedback

- Each kill's floating bonus shows the conquest gained (or cleansed) in the centre square next to the alignment gain.
- Crossing a multiple of 50 (or dropping to 0) in a square sends a notice to players in that same square within 200 blocks of the killer who are pledged to that faction (or, for cleansing, pledged to one of its enemies).
- A per-faction statistic counts total conquest earned.

## 3. Losing conquest

| Rule | Value |
| --- | --- |
| Grace | A square that has not changed for **1 hour of world time** (72,000 ticks) starts to decay |
| Decay | After the grace hour, every faction's strength in the square drops by **1 per further hour**. Linear, all factions alike |
| Reset of the clock | Any change to any faction in the square restarts the grace hour for the whole square (decay so far is locked in at that moment) |
| Cleansing | Enemy kills nearby (section 2) |
| Admin | Commands can set, add, spread or clear |

In practice decay is very slow: a square at 300 needs about 300 idle hours to empty. Land stays conquered for a long time once a player has worked on it.

## 4. Effects

### NPC spawning (the main effect)

- Each biome's spawn list is a set of faction groups with a base weight. Some groups have base weight **0**: they never spawn until their faction has conquest in the area. This is how orcs come to the Shire or Rangers to Bree-land.
- A group's effective weight is `base + round(0.2 x conquest x sensitivity)`. Conquest here is the faction's strength in the square, plus **one third** of each ally's strength (only allies that have no spawn group of their own in that biome).
- Sensitivity is 1.0 by default. Many home-faction groups have sensitivity 0 (their weight never grows), and a few have 2.0.
- Within a group, individual spawn lists can need a minimum conquest. Typical steps: 25 (Isengard snaga and Uruk-hai in the Shire), 50 (elite soldiers, black uruks, wargs), 100 (spiders, Harad and Easterling allies), 150 (olog-hai), 500 (Gondor's main levy in Ithilien).
- Example: the Shire's hobbit group has weight 100. Uruk-hai with 100 Isengard conquest get weight 20, so about 1 spawn in 6 is an invader.
- NPCs that spawn because of conquest (the extra weight above base) ignore the darkness rule (orcs spawn in daylight; trolls only if sun-proof) and ignore the "near an enemy fort" block, but still respect banner protection.
- Conquest never removes anyone from a list; the home faction is only diluted.

### Fast travel

An enemy faction's fixed waypoint can be used by a player whose pledge faction holds **500 or more** strength in that waypoint's square, if the pledge faction's conquest is effective there (section 5). The map shows such waypoints as "unlocked by conquest".

### Achievements and stats

- One achievement for viewing your own faction's conquest map.
- Conquest earned per faction is a player statistic.

### Not affected

Legacy conquest has **no** effect on invasion frequency, road danger, structures, banners or ownership. Invasion chances come only from the biome's list (see `combat-trade.md` section 7). Structure garrisons (respawners) are permanent.

## 5. The conquest map

- Opened by using a command-table block (crafted war table), not from the normal map screen, and only when conquest is enabled.
- Shows a sepia map with one coloured square per grid square for **one faction at a time**, chosen with a scroll bar; a button cycles the faction list by map region.
- Colour intensity is relative: the strongest square **currently in view** is fully opaque, the rest scale down (with a 10% minimum for any non-zero square). A colour key shows the scale.
- Square style tells whether conquest does anything there: **solid** if the faction has spawn groups in the square's biomes ("effective"), **striped** if only an ally does ("ally boost"), **grey** if neither ("no effect"). Hovering shows the number and this note.
- Who can view what: you must be pledged. You can view your own faction, its allies and its enemies. Viewing any other faction needs the rank two steps above your pledge rank.
- Data is requested per faction when opened and is a snapshot; it does not update live.

## 6. Admin

| Command | Effect |
| --- | --- |
| set / add `<faction> <amount> [x z]` | One square |
| radial `<faction> <amount> [x z]` | Spread like a kill; a negative amount cleanses |
| clear `[x z]` | Zero all factions in a square |
| rate `[value]` | Read or set the world conquest rate (0-100) |

## 7. Related systems (not conquest)

- **Areas of influence** (`factions-alignment.md` section 6): fixed circles per faction that scale *alignment* gains from kills. They never move and are unrelated to conquest; conquest is earned outside them.
- **Invasions** (`combat-trade.md` section 7): biome-listed, alignment-gated, and blocked by enemy banners and fort respawners. Their only conquest link is the 50-point defeat reward.
- **Structure respawners** (`structures.md`): forts and camps keep their garrison forever. Conquest spawns ignore the "near enemy respawner" block, but respawners themselves never change faction.

## 8. Gaps worth noting for our design

- No ownership, so nothing for a player to *see* change except spawn mix. Players on the wiki describe conquest as invisible unless you open the table.
- Very slow decay plus no cap means a busy server's land ends up permanently saturated.
- Fixed square grid ignores geography (a square can straddle a river or a mountain range).
- No limit on how fast one player can push conquest, and no protection for factions with no active players.
