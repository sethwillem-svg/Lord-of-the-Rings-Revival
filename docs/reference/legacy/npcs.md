# Legacy reference: NPCs

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: shared NPC behavior, then every NPC type grouped by faction. Ids in the tables are **our** proposed `snake_case` ids, not the original's registry names. Numbers are Legacy's defaults (1.7.10 attribute scale: 20 HP = 10 hearts, speed attribute 0.2 is slower than a vanilla zombie's 0.23). "Ticks" are 1/20 s.

## 1. Base stats

| Body | Max HP | Speed attr | Hitbox (w x h) | Notes |
| --- | --- | --- | --- | --- |
| Man (all human cultures) | 20 | 0.2 | 0.6 x 1.8 | Smiths/blacksmiths 25, captains/warlords/chieftains 25 |
| Hobbit | 16 | 0.2 | 0.45 x 1.2 | Bounders and the Shirriff 20 |
| Elf (all elf cultures) | 30 | 0.2 | man-sized | Rangedness: better accuracy (spread 0.5) |
| Dwarf | 26 | 0.2 | short | Commanders 30 |
| Orc (common) | 18 | 0.2 | 0.5 x 1.55 | Mordor and Dol Guldur orcs 20; Uruk-hai 26 (speed 0.22); Black Uruks 30; Gundabad Uruks 25; Utumno orcs 24 |
| Half-troll | 35 | 0.2 | large | Warriors speed 0.24; warlord 40; base melee 6 |
| Troll (stone/hill) | 60 | 0.2 | very large | Base melee 5; Mountain troll 70/7; Olog-hai 80/7; Mirk troll 70/6; Snow troll 40 |
| Warg | 20-32 random | 0.22 | wolf-like | Base melee 3-5 random; Utumno wargs 40 HP, 0.25 |
| Spider | 12 + 6 per size step | 0.35 minus 0.03 per size step | scales | Base melee 2 + size step |
| Ent / Huorn | 100 / 60 | 0.2 / 0.3 | tree | Melee 7 / 4 |
| Skeletal wraith (ruins, tombs) | 24 (some 30, marsh/barrow types 50) | 0.25 | man-sized | Hostile to everyone |
| Bosses | Balrog 300, Sauron 500, Mallorn ent 300 | | | Persistent, special AI |

- **Sense (follow) range** is 16 blocks by default; most elite warriors, archers and rangers use 24, wargs 32, bandits/wights/bosses 40. Riding a mount multiplies it by 1.5. NPCs spawned by an invasion get at least 40.
- **Unarmed melee** base is 2 damage. Not changed by race except where listed.
- **Two-headed trolls**: 1 in 10 stone trolls spawn with two heads: x1.5 max HP, +3 melee, x1.4 speed.
- **Ranged accuracy** is a per-type spread value (1.0 default, 0.75 for most human soldiers and dwarf/elf axe-throwers, 0.5 for elves, rangers, Black Uruks, Dale men, corsairs). Lower is tighter.

## 2. Melee and ranged attack rules

- **Melee damage** = 75% of the held weapon's damage value (see combat-trade.md for weapon values). If unarmed, the base 2. Then add the type's flat bonus (berserkers +2), +4 if the NPC is drunk, +0.5 per allied banner bearer within 16 blocks (max 5 bearers), plus enchantments.
- **Damage taken** near allied banner bearers is multiplied by (12 - n) / 12, n = bearers within 16 blocks of the same faction, max 5 (so at best x7/12).
- **Melee reach**: NPC's average hitbox edge + 1.0 x the weapon's reach factor (x1.5 when mounted). Attack interval: 20 ticks divided by the weapon's speed factor (daggers faster, polearms slower).
- **Mode switching**: an NPC with a target closer than (4 + width^2) blocks (x1.5 mounted) uses melee; beyond that and within 95% of its sense range it uses its ranged weapon if it has one. Each type keeps separate idle, melee, ranged, mounted-melee and mounted-idle items and swaps the held item on mode change.
- **Spear throwers**: a warrior holding a spear with a backup weapon throws the spear once at targets between 5 blocks and 75% of path range, then switches to the backup.
- **Ranged timing**: most archers fire every 30-50 ticks at up to 16 blocks; elite elves 25-35/40 ticks at 24 blocks; slingers and axe-throwers 20-40 ticks at 12 blocks. Arrow speed rises slightly with distance.
- **Poison**: orcs have a 1 in 15 chance per shot to fire a poisoned arrow; some daggers poison on hit (duration scales with difficulty).
- **Bombardiers** (orc and warg types) carry a placed explosive and run it to the target; nearby orcs flee from a lit bomb (12 blocks).
- **Combat stance**: an NPC counts as "in combat" while it has a target and for 40 ticks after; the client shows the weapon raised.

## 3. Hostility rules vs player alignment

Alignment is per player per faction (owned by core in our repo).

| Rule | Legacy behavior |
| --- | --- |
| Basic hostility | Target-seeking NPCs (warriors, archers, guards, wild beasts) attack players whose alignment with their faction is **below 0**. At 0 or above they ignore the player. |
| Mordor orcs | Probabilistic between 0 and +100: each check targets with chance 1 / (5 x alignment); never at +100 or more. |
| Trolls | Same as Mordor but 1 / (10 x alignment), safe at +100. |
| Woodland Realm elves | Probabilistic between 0 and the faction's first rank (+10): chance 1 / (20 x alignment). |
| Civilians | Never seek targets. They fight back (men) or panic (hobbits) only when hurt. |
| Hostile faction (wights, wraiths, bandits) | Attack everyone; do not attack members of their own kind. |
| Bandits | Only hired units target bandits; ordinary faction NPCs ignore them. |
| Creative players | Never targeted; a target that switches to creative is dropped. |
| Talk/trade gate | "Friendly" for speech means alignment >= 0 and the NPC is not currently targeting the player and was not recently hit by them. |
| Hobbit flight | Adult hobbits flee players at alignment -100 or worse within 8 blocks; hobbit children flee anyone with negative alignment (see uncertainty note at the end). |
| Weak-orc flight | Common orcs (not Mordor, not Uruks/Black Uruks/captains) run from players at -500 or worse within 8 blocks unless they or a nearby orc were attacked. |
| Hobbit children | Follow players at +200 or more. |

**Faction warfare**: an NPC also targets any living thing whose faction is an enemy (bad relation) of its own. Neutral factions are only targeted by a hired unit whose owner has negative alignment with that faction. NPCs of "good" factions never target **civilians** of enemy factions (the faction has a "disapproves of war crimes" flag); evil factions do target civilians. Relations come from the faction table (ally, friend, neutral, enemy, mortal enemy).

**Target priority**: candidates within sense range (vertical range capped at 8) are sorted by squared distance / 144, plus the square of the number of nearby NPCs (within 8 blocks) already attacking that candidate. This spreads attackers across several enemies instead of mobbing one. Retaliation against whoever hurt the NPC (or its hiring player) takes precedence over seeking. Hired units also attack whatever their owner attacks and whatever attacks their owner.

**When a target is acquired**: 1 in 3 chance the NPC speaks a hostile line to the player, but only if 5 or fewer NPCs within 16 blocks already target that player (no chat flood).

## 4. Friendly fire and kill penalties

- **Player hitting an NPC**: if the player's alignment with the NPC's faction (or the rider's faction, for mounts) is **above 0** and the NPC is not targeting that player, the hit is **cancelled entirely** (no damage). The player gets a one-time tutorial message explaining the friendly-fire option. A per-player toggle in the options screen allows hitting friendly NPCs deliberately.
- At exactly 0 or below, hits go through. There is no warning-hit or second-hit rule. Hits never change alignment.
- A player never damages their own hired units. Fellowships can block damage to members' hired units (and PvP between members) unless a siege is active.
- **Retaliation when hurt**: the hurt NPC targets the attacker (home range is ignored for that check). It does **not** call nearby allies; there is no general "call for help" on a hit. Exceptions: Bree ruffians alert other ruffians within a radius that grows by 4 blocks per hit (up to 24).
- **Killing an NPC**: the player loses alignment with the victim's faction (and with factions configured to take a penalty for that kill) equal to the type's kill value. If the player's alignment with the penalized faction is positive the loss is scaled by alignment / 50, clamped to x1..x20. Example: killing a hobbit (value 1) at +500 costs 10; at +1000 or more costs 20.
- **Kill values**: civilians 1, warriors/guards/militia/traders/smiths 2, captains and lords 5, trolls 3-4, ents 3, bosses 50. Ruffians and wraiths give 0.
- **Gains**: killing an enemy gives each "bonus" faction (enemies of the victim) the kill value, multiplied by that faction's area-of-influence factor at the kill position, halved if the player is above the pledge level (+100) but not pledged to that faction. Civilian kills give nothing to factions that disapprove of war crimes.
- **Witnesses**: after a player kill (not by a hired unit), every idle NPC within 8 blocks whose faction is allied with the victim targets the killer; up to 5 of them speak. Hired units of the killer, and hired units in general while the killer still has positive alignment, are excluded.
- **Self-defence**: killing someone else's hired unit that had been ordered to attack you, while you have positive alignment with its faction, costs no alignment.
- **Kills by hired units** earn the owner no alignment and no kill counts; they only add 25% conquest if the owner is within 64 blocks.
- Special penalties outside kills: killing a horse near Rohirrim (-1 Rohan and the riders attack), cutting Fangorn trees, pickpocketing, stealing grapes (-1 each).

## 5. Healing, despawn, persistence

- **Healing**: only traders and hired units regenerate: 1 HP every 40 ticks while they have no target; hired units near a banner bearer get an extra 1 HP every (240 - 40 x bearers) ticks. A hired unit's mount heals with it. Other NPCs never regenerate.
- **Despawn**: vanilla monster rules (immediate beyond 128 blocks from any player; random after 30 s beyond 32 blocks). Never despawn: structure-bound NPCs, characters and bosses, traders flagged to respawn, hired units, NPCs with an active quest for any player.
- **Trader respawn**: a structure trader that dies leaves an invisible respawn marker that recreates it (same trades and name) after 10-30 minutes.
- **Kidnap guard (config, default off)**: structure traders moved too far from their home are teleported back.
- **Home range**: structure NPCs wander within a home radius. If pushed beyond radius + 128 they forget their home; otherwise they path back at 1.3x speed when idle.
- **Burning**: all NPCs (except where disabled) panic-run at 1.5x when on fire.

## 6. Drops and XP

| Drop | Rule (looting level L) |
| --- | --- |
| Worn/held gear | Each piece has chance 1 / (20n - 4nL), n = number of equipped pieces. Dropped gear is pre-damaged to 50-75% of max durability. |
| Silver coins | Chance 1 / max(8 - 2L, 1); amount ~1-3 (heavy-tailed: 1 + round(((1+abs(gauss))^3) / 4)) times a random 1..L+1. |
| Rare loot | 1 in (50 - 5L): one item from a shared rare-drop table. |
| Modifier template | 1 in (60 - 5L): an upgrade template for the reforge system. |
| Pouch | 1 in 60 when killed by a player: all drops are packed into a pouch tinted with the faction color half the time. |
| Race extras | Orcs: rotten flesh, orc bones, 1/10 maggoty bread, rare orc drink or 1-2 steel ingots of their kind. Hobbits: hobbit bones, 1/8 study loot, 1/4 larder loot. Men: bones, culture loot (Bree house loot 1/6). Archers: 0-2 arrows or bolts. Bounders: pebbles. Ruffians: 2-4+ silver coins and sometimes a skull cup. |
| Gear from players | A hired unit drops any gear the player gave it, then its own gear by the normal chance. Hired units never drop coins, rare loot or pouches. |
| Ownership | Single-stack items dropped by an NPC remember the previous owner's name (shown in tooltip). |
| XP | 4-6 for most; hobbits 1-3, bounders 2-4. |

## 7. Names

- Every person-like NPC gets a generated name at spawn, persisted. Display format is "Name, the Type" through a translatable template; if the generated name equals the type name only one is shown.
- Name banks are per culture and gender, loaded from text files: hobbit (about 71 male and 51 female first names, 49 surnames), Bree (first names plus surnames), Gondor (45 male, 36 female), Rohan, Dale, Dorwinion, Dunland, Rhudaur hillmen, Rhun, Near Harad, Umbar, Gulf, Nomad, Morwaith, Taurethrim, dwarf (with patronymic children), Sindarin and Quenya (elves), orc (prefix + suffix syllables), troll, ent (prefix + suffix). Settlement, tavern and sign names have their own banks.
- Hobbit and Bree spouses share surnames on marriage; children derive names from parents.
- Vanilla name tags are rejected by NPCs; only tamed rideable beasts (wargs, spiders) can be renamed.
- One in 10,000 traders spawns with a joke name and 100x prices (easter egg).

## 8. Speech

- Speech lives in per-culture, per-role text banks, each a list of lines. Lines can contain placeholders for the player's name, the NPC's location name and a quest objective. Drunk NPCs garble their lines.
- Delivery: a chat line to the one player concerned, prefixed with the NPC's name. No broadcast.
- **Categories** (counts of bank files in Legacy): friendly (181), hostile (128), neutral (91), hired (47); plus drunkard, child, departure (travelling traders), skirmish (orcs fighting orcs), tame/ride lines for mounts, holiday banks, and mini-quest banks (start, progress, complete, too many quests, bounty help).
- **Selection**: hostile if the player is not friendly; else hired if this player hired it; else role-friendly (traders: "friendly" if the player can trade, otherwise "neutral" for those below the trade level). Hobbits: drunkard bank when drunk; children use child banks.
- **Triggers**:
  - Right-click when idle and not in combat. Per-NPC cooldown of 40 ticks between lines.
  - Acquiring a new player target (1 in 3, crowd-limited as above).
  - Witnessing an ally's killer (up to 5 speakers within 8 blocks).
  - Hired unit: after killing an enemy (1 in 3, owner within 16 blocks), and idle chatter (1 in 4000 per tick while following, owner within 16).
  - Traders: unprompted advertisement to players within 10 blocks when no trade for 600 ticks; each player 1 in 3; then wait 5-20 s.
  - Holidays: 1 in 8 chance to replace the line with a seasonal one on the relevant dates.

## 9. Day/night and idle behavior

- **Dark spawning**: orcs, trolls, wargs, spiders, half-trolls, wights and the Balrog need low light to spawn (sky light check and block light at most a random 0-7), except in biomes flagged "hostiles spawn by day" (Mordor, Angmar etc.) and when spawning as conquest reinforcements.
- **Sunlight**: common orcs in direct daylight get a slowness effect (refreshed every second). Stone trolls seek shade, flee the sun, and turn to stone after 300 ticks of direct sun (hired owners are told). Olog-hai and Mirk trolls are immune.
- **No daily schedule** for good NPCs: there is no work/home routine. Idle tasks are wander, look at players (8 blocks) and other NPCs (5 blocks), open doors, eat and drink culture food every few minutes, hobbits and Bree folk smoke pipes, hobbits marry and raise children.
- **Orc skirmishes**: orcs occasionally start brawls with other orcs (base chance 1 in 20,000 per check, 10x more likely per skirmishing orc nearby, min 1 in 40); lasts 160 ticks. Config toggle.
- **Lunar eclipse** nights multiply invasion chances by 5 (see combat-trade.md).

## 10. Spawning

- **Wild spawner** (its own system, not vanilla categories). Every tick (or every N ticks with config compensation), for each player the 15 x 15 chunk area around them is eligible. Global cap: 100 NPCs per 196 eligible chunks in Middle-earth (500 in Utumno). Persistent, hired and trader-respawn NPCs do not count; some dense biomes count each NPC more than once.
- **Multiplayer damping**: a per-type damping factor reduces the per-player cap as player count rises (configurable, never below half).
- Per chunk: a random point, 3 group attempts within 5 blocks; group size from the spawn entry; never within 24 blocks of a player or of world spawn; ground must be a monster-spawnable top surface.
- **Biome lists**: each biome has weighted faction lists. Lists with weight 0 only spawn when that faction has conquered the area (conquest system). Some lists have an extra 1-in-N chance (Bree men and guards in Bree-land use 1 in 500, so Bree folk come almost entirely from structures).
- **Banner protection** blocks hostile spawns; **structure respawners** block enemy-faction spawns around them.
- **Structure respawners**: invisible markers in villages and forts. Every 3600 ticks, if no player within 24 blocks and fewer than the cap (default 4) of its types within 8 blocks, spawn one within 4 blocks, optionally mounted, with a home range.
- **Travelling traders** (merchants of Dale, Lindon dwarves, Iron Hills, Lothlorien, Rivendell, Dorwinion, Near Harad, Nomads, the scrap trader) appear near players every 0.8-10 hours (then 1 in 1000 per tick), stay a while, then announce departure and leave.
- **Hobbits** only spawn on the biome's top block above sea level.

## 11. Factions and NPC types

Columns: our id, role, HP (blank = race default from section 1), loadout summary, spawn context. "W" = wild biome lists, "S" = structures, "I" = invasions, "T" = travelling, "H" = hireable (see hiring-units.md). Market trader rows list several ids that differ only in stock.

### Hobbits (faction: hobbit)

| Id | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- |
| `hobbit` | civilian; may be child, drunk, married | 16 | idle food/pipe | W (weight 40, groups 1-4), S |
| `hobbit_bounder` | warrior (militia) | 20 | iron or bronze dagger, sling; colored hat; 1/3 ride a Shire pony | W (weight 1, 1-3), I, H |
| `hobbit_shirriff` | captain (unit trader for bounders, conquest horn) | 20 | as bounder, distinct hat | S |
| `hobbit_farmer` | trader (farm produce) and farmhand hirer | 16 | iron hoe | S |
| `hobbit_farmhand` | farmhand unit | 16 | hoe | H |
| `hobbit_bartender` | trader (food, drink, pipeweed, furniture) | 16 | | S |
| `hobbit_orcharder` | trader (fruit) | 16 | axe | S (orchards) |

### Bree-land (faction: bree)

| Id | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- |
| `bree_man` | civilian (fights back when hurt) | 20 | random dagger/axe or nothing | W (1/500), S |
| `bree_hobbit` | civilian (flees like hobbits) | 16 | | W (1/500), S |
| `bree_guard` | warrior, male only | 20 | iron sword (2/3) or pike, dyed leather | W (1/500), S, I, H |
| `bree_captain` | captain (unit trader, horn) | 25 | | S |
| `bree_banner_bearer` | banner bearer | 20 | banner | I, H |
| `bree_blacksmith` | smith trader | 25 | smith hammer | S |
| `bree_innkeeper`, `bree_hobbit_innkeeper` | innkeeper traders | 20/16 | | S (inns) |
| `bree_baker`, `bree_brewer`, `bree_butcher`, `bree_florist`, `bree_lumberman`, `bree_mason` | market traders (man) | 20 | trade tools | S (market) |
| `bree_hobbit_baker`, `bree_hobbit_brewer`, `bree_hobbit_butcher`, `bree_hobbit_florist` | market traders (hobbit) | 16 | | S |
| `bree_farmer`, `bree_farmhand` | farmer trader / farmhand unit | 20 | hoe | S, H |

### Ruffians (faction: ruffian, an Isengard-aligned spawn list)

| Id | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- |
| `ruffian_spy` | thief (steals one item, then flees) | 20 | sword/dagger | W (Shire, Bree-land) |
| `ruffian_brute` | brawler; scares hobbits and Bree men | 30 | sword/club | W |

Ruffians target players only rarely (only those with nausea), avoid rangers and Bree guards, and call other ruffians when hit. Kill value 0.

### Rangers of the North (faction: ranger_north)

| Id | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- |
| `dunedain` | civilian | 20 | | S |
| `ranger_north` | warrior/archer; 1/20 mounted | 25 (range 24) | dagger, ranger bow, ranger armor | W, S, I, H |
| `ranger_north_captain` / `ranger_north_banner_bearer` | captain / banner | 25 | sword / banner | S / I, H |
| `dunedain_blacksmith` | smith | 25 | | S |

### Dwarves (Durin's Folk and Blue Mountains: `durins_folk`, `blue_mountains`)

| Id | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- |
| `dwarf`, `blue_dwarf` | civilian | 26 | dwarven dagger | W, S |
| `dwarf_warrior`, `blue_dwarf_warrior` | warrior | 26 | axe, hammer, mattock, pike, spear or sword; full dwarven plate | W, S, I, H |
| `dwarf_axe_thrower`, `blue_dwarf_axe_thrower` | thrown-axe ranged | 26 | throwing axes | W, I, H |
| `dwarf_banner_bearer`, `blue_dwarf_banner_bearer` | banner | 26 | silver plate | I, H |
| `dwarf_commander`, `blue_dwarf_commander` | captain | 30 | hammer, gold plate | S |
| `dwarf_miner`, `blue_dwarf_miner` | trader (ores, gems) | 26 | pickaxe | S (mines) |
| `dwarf_smith`, `blue_mountains_smith` | smith | 26 | | S |
| `iron_hills_merchant`, `blue_dwarf_merchant` | travelling traders | 26 | | T |
| `wicked_dwarf` | outcast trader for Mordor | 26 | dwarven armor | W (rare) |

### Elves

| Id | Faction | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- | --- |
| `galadhrim_elf` | lothlorien | civilian archer | 30 | dagger, mallorn bow | W, S |
| `galadhrim_warden` | lothlorien | scout (hithlain cloak gear) | 30 | dagger, bow | W, H |
| `galadhrim_warrior` | lothlorien | warrior; 1/4 mounted | 30 | sword/spear/polearm/long spear + elven bow | W, S, I, H |
| `galadhrim_lord`, `galadhrim_banner_bearer`, `galadhrim_smith`, `galadhrim_trader` | lothlorien | captain / banner / smith / travelling trader | 30 | | S / I,H / S / T |
| `high_elf`, `high_elf_warrior`, `high_elf_lord`, `high_elf_banner_bearer`, `high_elf_smith` | high_elf (Lindon) | civilian / warrior (1/4 mounted) / captain / banner / smith | 30 | high-elven gear and bows | W, S, I, H |
| `rivendell_elf`, `rivendell_warrior`, `rivendell_lord`, `rivendell_banner_bearer`, `rivendell_smith`, `rivendell_trader` | high_elf (Rivendell) | same set | 30 | Rivendell gear | W, S, I, H, T |
| `wood_elf`, `wood_elf_scout`, `wood_elf_warrior`, `wood_elf_captain`, `wood_elf_banner_bearer`, `wood_elf_smith` | wood_elf | archer civilian / scout / warrior (1/4 on elk) / captain / banner / smith | 30 | Mirkwood bow, wood-elven gear | W, S, I, H |
| `dorwinion_elf`, `dorwinion_elf_warrior`, `dorwinion_elf_archer`, `dorwinion_elf_captain`, `dorwinion_elf_banner_bearer`, `dorwinion_elf_vintner`, `dorwinion_merchant_elf` | dorwinion | civilian / warrior / archer / captain / banner / wine trader / travelling | 30 | Bladorthin spears, Dorwinion elf bows | S, I, H, T |

### Men of the North and Rhovanion

| Id | Faction | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- | --- |
| `dale_man`, `dale_levyman`, `dale_soldier`, `dale_archer` | dale | civilian / militia / soldier (1/8 mounted) / archer | 20 | gambeson; sword/axe/pike/spear; Dale bow | W, S, I, H |
| `dale_captain`, `dale_banner_bearer`, `esgaroth_banner_bearer` | dale | captain / banners | 25/20 | | S / I, H |
| `dale_blacksmith`, `dale_baker`, `dale_merchant` | dale | smith / baker / travelling | 25/20 | | S / T |
| `dorwinion_man`, `dorwinion_guard`, `dorwinion_crossbower`, `dorwinion_captain`, `dorwinion_banner_bearer` | dorwinion | civilian / spear guard / crossbow / captain / banner | 20 (captain 25) | iron spear, iron crossbow, Dorwinion armor | S, I, H |
| `dorwinion_vinehand`, `dorwinion_vinekeeper`, `dorwinion_merchant_man` | dorwinion | farmhand / vineyard trader-hirer / travelling | 20 | hoe | S, H, T |
| `easterling`, `easterling_levyman`, `easterling_warrior`, `easterling_archer`, `easterling_gold_warrior`, `easterling_fire_thrower` | rhudel | civilian / militia / warrior (1/6 mounted) / archer / elite (25 HP) / fire-pot thrower | 20 | Rhun axes, polearms, pikes, bows, gold armor | W, S, I, H |
| `easterling_warlord`, `easterling_banner_bearer`, `easterling_blacksmith`, `easterling_bartender`, `easterling_farmer`, `easterling_farmhand` | rhudel | captain / banner / smith / bartender / farm trader-hirer / farmhand | 25/20 | | S, H |
| `easterling_baker`, `_brewer`, `_butcher`, `_fishmonger`, `_goldsmith`, `_hunter`, `_lumberman`, `_mason` | rhudel | market traders | 20 | | S (towns) |

### Rohan (faction: rohan)

| Id | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- |
| `rohan_man` | civilian | 20 | | W, S |
| `rohirrim_warrior` | cavalry warrior, 1/3 mounted | 20 | sword/axe/spear; lance when mounted; Rohan armor | W, S, I, H |
| `rohirrim_archer` | horse archer | 20 | Rohan bow | W, I, H |
| `rohan_shieldmaiden` | female warrior (never mounted) | 20 | Rohan helmet | S |
| `rohirrim_marshal`, `rohan_banner_bearer` | captain / banner | 25/20 | marshal armor | S / I, H |
| `rohan_blacksmith`, `rohan_meadhost`, `rohan_stablemaster`, `rohan_farmer`, `rohan_farmhand` | smith / mead-hall / horse trader / farm | 25/20 | | S, H |
| `rohan_baker`, `_brewer`, `_builder`, `_butcher`, `_fishmonger`, `_lumberman`, `_orcharder` | market traders | 20 | | S |

### Gondor and its fiefs (faction: gondor)

| Id | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- |
| `gondor_man` | civilian | 20 | | W, S |
| `gondor_levyman` | militia | 20 | gambeson, helmet | W, S, I, H |
| `gondor_soldier` | warrior, 1/6 mounted | 20 | sword/hammer/pike/spear, lance mounted | W, S, I, H |
| `gondor_archer` | archer | 20 | Gondor bow | W, S, I, H |
| `gondor_tower_guard` | elite (pledge) | 24, speed 0.24 | spear, winged helm | S (Minas Tirith), H |
| `gondor_captain`, `gondor_banner_bearer` | captain / banner | 25/20 | | S / I, H |
| `ranger_ithilien`, `ranger_ithilien_captain`, `ranger_ithilien_banner_bearer` | ranger archers | 25 (range 24) | Gondor bow, sword | W, S, I, H |
| `dol_amroth_soldier`, `dol_amroth_archer`, `swan_knight`, `dol_amroth_captain`, `dol_amroth_banner_bearer` | fief troops; swan knights 30 HP, 1/4 mounted with lance | 20/30 | Dol Amroth gear | S, I, H |
| `lossarnach_axeman`, `lossarnach_captain`, `lossarnach_banner_bearer` | battleaxe + throwing axe | 20/25 | | S, I, H |
| `pelargir_marine`, `pelargir_captain`, `pelargir_banner_bearer`, `lebennin_levyman`, `lebennin_captain`, `lebennin_banner_bearer` | marines (sword/trident) and levies | 20/25 | | S, I, H |
| `pinnath_gelin_soldier`, `_captain`, `_banner_bearer`; `blackroot_soldier`, `blackroot_archer`, `_captain`, `_banner_bearer`; `lamedon_hillman`, `lamedon_soldier`, `lamedon_archer`, `_captain`, `_banner_bearer` | fief troops | 20/25 | fief armor | S, I, H |
| `gondor_blacksmith`, `gondor_bartender`, `gondor_farmer`, `gondor_farmhand` | smith / bartender / farm | 25/20 | | S, H |
| `gondor_baker`, `_brewer`, `_butcher`, `_fishmonger`, `_florist`, `_greengrocer`, `_lumberman`, `_mason` | market traders | 20 | | S |

### Dunland (faction: dunland)

`dunlending` (civilian with crude weapons, 20), `dunlending_warrior` (20), `dunlending_archer`, `dunlending_axe_thrower`, `dunlending_berserker` (30 HP, +2 melee, battleaxe, range 24, pledge), `dunlending_warlord` (25, captain), `dunlending_banner_bearer`, `dunlending_bartender`. Context: W, S, I, H.

### Isengard (faction: isengard)

| Id | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- |
| `isengard_snaga`, `isengard_snaga_archer` | weak orcs | 18 | | W, I, H |
| `uruk_hai` | warrior | 26, speed 0.22 | Uruk blades, pike, Uruk armor | W, S, I, H |
| `uruk_hai_crossbower` | crossbow | 26 | Uruk crossbow | I, H |
| `uruk_hai_berserker` | berserker (pledge) | 30, +2 melee | scimitar, fur | I, H |
| `uruk_hai_sapper` | demolition (explosive charge) | 26 | | I, H |
| `uruk_hai_mercenary_captain`, `uruk_hai_banner_bearer`, `uruk_hai_trader` | captain / banner / trader | 30/26 | skull staff | S / I,H / S |
| `uruk_warg`, `uruk_warg_bombardier` | wargs / bomb-carrying warg | 20-32 | | W, I, H |

### Mordor (faction: mordor)

| Id | Role | HP | Loadout | Context |
| --- | --- | --- | --- | --- |
| `mordor_orc` | warrior | 20 | orc weapons, orc armor | W (groups 4-6), S, I, H |
| `mordor_orc_archer`, `mordor_orc_bombardier` | archer / bomber | 20 | orc bow | W, I, H |
| `black_uruk`, `black_uruk_archer`, `black_uruk_captain`, `black_uruk_banner_bearer` | elite (pledge) | 30 (range 24) | Black Uruk gear | S, I, H |
| `mordor_orc_mercenary_captain`, `mordor_banner_bearer`, `minas_morgul_banner_bearer` | captain / banners | 25/20 | skull staff | S / I, H |
| `mordor_orc_slaver` | slave-farmhand trader (Nurn) | 20 | | S |
| `mordor_orc_spider_keeper`, `nan_ungol_banner_bearer`, `mordor_spider` | spider-rider unit trader / banner / rideable spider | 25/20/12+ | | S, I, H |
| `mordor_orc_trader` | trader | 20 | poisoned dagger, fur | S |
| `mordor_warg`, `mordor_warg_bombardier` | wargs | 20-32 | | W, I, H |
| `olog_hai` | troll (sun-immune, pledge) | 80, melee 7 | | I, H |
| `nurn_slave` | farmhand (hired from slaver) | 20 | hoe | H |

### Angmar, Gundabad, Dol Guldur

| Id | Faction | Role | HP | Context |
| --- | --- | --- | --- | --- |
| `angmar_orc`, `angmar_orc_archer`, `angmar_orc_bombardier`, `angmar_orc_mercenary_captain`, `angmar_banner_bearer`, `angmar_orc_trader` | angmar | orc set | 18 (captain 25) | W, S, I, H |
| `angmar_warg`, `angmar_warg_bombardier`, `troll`, `snow_troll`, `mountain_troll` (throws rocks, 5 dmg) | angmar | beasts | 20-32 / 60 / 40 / 70 | W, I, H |
| `angmar_hillman`, `angmar_hillman_warrior`, `angmar_hillman_axe_thrower`, `angmar_hillman_chieftain`, `angmar_hillman_banner_bearer` | angmar | Rhudaur hillmen | 20 (chieftain 25) | W, S, I, H |
| `mountain_troll_chieftain` | angmar | boss (x2 HP, x1.5 speed when enraged) | 50 base | S (boss) |
| `gundabad_orc`, `gundabad_orc_archer`, `gundabad_uruk`, `gundabad_uruk_archer`, `gundabad_orc_mercenary_captain`, `gundabad_banner_bearer`, `gundabad_orc_trader`, `gundabad_warg` | gundabad | orc set; Uruks 25 HP range 24 | 18/25 | W (raids across the north), S, I, H |
| `dol_guldur_orc`, `dol_guldur_orc_archer`, `dol_guldur_orc_chieftain`, `dol_guldur_banner_bearer`, `dol_guldur_orc_trader`, `mirkwood_spider`, `mirk_troll` | dol_guldur | orc set, spiders, sun-immune troll | 20 / 12+ / 70 | W, S, I, H |

### Near Harad (faction: near_harad, several cultures)

Coast Southrons, Harnedor, Umbar, Corsairs, Nomads and the Gulf of Harad all share faction `near_harad`.

| Culture | Ids (civilian, warrior, archer, captain, banner, smith, other) | Notes |
| --- | --- | --- |
| Coast | `near_haradrim`, `near_haradrim_warrior`, `near_haradrim_archer`, `near_haradrim_warlord`, `near_harad_banner_bearer`, `near_harad_blacksmith`, `southron_champion` (always mounted, 25 HP, pledge), `near_harad_merchant` (T) | Market: `southron_baker`, `_bartender`, `_brewer`, `_butcher`, `_farmer`, `_fishmonger`, `_florist`, `_goldsmith`, `_lumberman`, `_mason`, `_miner`, `_trader` |
| Harnedor | `harnedhrim`, `harnedor_warrior` (1/8 mounted), `harnedor_archer`, `harnedor_warlord`, `harnedor_banner_bearer`, `harnedor_blacksmith`, `harnedor_farmer`, `harnedor_farmhand` | Market set as above with `harnedor_` prefix (plus hunter, miner) |
| Umbar | `umbarian`, `umbar_warrior` (1/6 mounted), `umbar_archer`, `umbar_captain`, `umbar_banner_bearer`, `umbar_blacksmith`, `umbar_farmer`, `gondor_renegade` (deserter, pledge-free) | Market set with `umbar_` prefix |
| Corsairs | `corsair` (spear + bow), `corsair_captain`, `corsair_slaver` (sells `harad_slave` farmhands) | Coastal camps and ships |
| Nomads | `nomad`, `nomad_warrior` (1/8 on camel), `nomad_archer`, `nomad_chieftain`, `nomad_banner_bearer`, `nomad_armourer`, `nomad_brewer`, `nomad_mason`, `nomad_miner`, `nomad_trader`, `nomad_merchant` (T) | Desert tents |
| Gulf | `gulf_haradrim`, `gulf_harad_warrior` (1/10 mounted), `gulf_harad_archer`, `gulf_harad_warlord`, `gulf_harad_banner_bearer`, `gulf_blacksmith`, plus market set with `gulf_` prefix | |
| Mercenary | `moredain_mercenary` | Hires itself (see hiring-units.md) |

All humans 20 HP, captains and smiths 25. All hireable via their warlord except civilians.

### Far Harad

| Id | Faction | Role | HP |
| --- | --- | --- | --- |
| `moredain`, `moredain_warrior` (1/10 on zebra), `moredain_chieftain`, `moredain_banner_bearer`, `moredain_huntsman`, `moredain_hutmaker`, `moredain_village_trader` | morwaith | lion-hunter savanna culture | 20 (chieftain 25) |
| `tauredain`, `tauredain_warrior`, `tauredain_blowgunner` (range 24), `tauredain_chieftain`, `tauredain_banner_bearer`, `tauredain_shaman`, `tauredain_smith`, `tauredain_farmer`, `tauredain_farmhand` | taurethrim | jungle culture | 20 (chieftain/smith 25) |
| `half_troll`, `half_troll_warrior` (1/12 on rhino), `half_troll_warlord`, `half_troll_banner_bearer`, `half_troll_scavenger` | half_troll | brutes, range 24 | 35 (warlord 40) |

### Fangorn, hostile and special

- `ent` (fangorn, 100 HP, 7 melee), `huorn` (60 HP, hides as a tree), `dark_huorn` (dark_huorn faction, Old Forest), `mallorn_ent` (boss, 300 HP).
- Hostile: `bandit`, `bandit_harad` (steal from players, 1-4 per group, biome chance), `barrow_wight` (50 HP, 6 melee, Barrow-downs), `marsh_wraith` (Dead Marshes, 50), `skeletal_wraith` variants in Gondor ruins, Rohan barrows and Harad/Taurethrim pyramids (24-30).
- Utumno (own dimension): `utumno_orc`, `utumno_orc_archer` (24), `utumno_warg` + fire/ice/obsidian variants (40), `utumno_ice_spider`, `utumno_troll` (60, 7), `utumno_snow_troll`, `tormented_elf`, `balrog` (300, 10 melee, fire).
- Characters: `gandalf`, `saruman`, `gollum` (follows a player, fishes), `sauron` (boss, 500 HP). Characters are persistent.
- `scrap_trader` (unaligned travelling junk dealer).

## Differences from our draft spec

Compared with `specs/npc/npc-base.md` (draft, G2).

| Topic | Legacy | Our draft | Recommendation |
| --- | --- | --- | --- |
| `hostile_below` | 0 for everyone; Mordor orcs, trolls and Woodland elves add a probabilistic grey zone (0..+100 or 0..+10) | 0, with "some factions above 0" | Keep 0 default; model grey zones as an optional per-faction "suspicion ceiling" with chance falling off by alignment, not as a hard higher threshold. |
| `friendly_at` | No separate friendly band: >= 0 can talk and use basic traders; specialists gate at +50..+400 | +10 band; neutral can't trade | Drop the separate +10 band or set it to 0; gate trade/hire per role with explicit `trade_min` like Legacy (basic 0, smiths 50, captains 100-400). |
| Health | Hobbit 16, man 20, bounder 20, Bree guard 20 (no bonus), captains/smiths 25 | Hobbit 16, man 20, guards +4 | Keep; use 25 for captains and smiths; Bree guard +0 or +4 is a free choice. |
| Speed | 0.2 attribute with 1.3-1.5x attack multipliers | 0.24 / 0.23 | Fine to stay faster; vanilla movement in 1.21 feels sluggish at 0.2. |
| Hobbit hitbox | 0.45 x 1.2 | 0.45 x 1.25 | Either; keep ours. |
| Regen | Only traders and hired units, 1 HP / 40 ticks out of combat | All NPCs, 1 HP / 100 ticks after 200 ticks | Keep ours but consider limiting to non-hostile-band NPCs so players can't be out-healed by orc packs. |
| Spawn caps | Global 100 per 196-chunk player area, no local cap, groups 1-6, 24 blocks from players | Global 40, local 8 per 64 blocks | Keep our lower caps for 1.21 performance; add Legacy's multiplayer damping idea. |
| Bree wild spawns | Bree people almost only from structures (1/500 list chance) | `bree_man` 8, `bree_hobbit` 4, `bree_guard` 2 wild | Cut Bree wild weights sharply until Bree village structures exist, then make them structure-only. |
| Friendly fire | Hits from players with alignment > 0 are fully blocked (toggle to allow); no warning hit; hits never cost alignment | First hit warns, second within 10 s provokes | Prefer Legacy's block + per-player toggle: simpler, no griefing, no warning state machine. |
| Call for help | Only on a kill: allies within 8 blocks attack the killer; a hit only angers the victim | Allies within 16 alerted on provocation | Narrow ours to "on kill or on hostile-band attacker", radius 8-12. |
| Kill values | 1 civilian, 2 guard/trader/smith, 5 captain; loss scaled by alignment/50 (x1..x20) | 1 / 2 / 3 | Use 1 / 2 / 2 / 5 and ask core to adopt the alignment-scaled penalty. |
| Hobbit/Bree NPC list | Shire: hobbit, bounder (guard), shirriff (captain), farmer, farmhand, bartender, orcharder. Bree: man, hobbit, guard, captain, banner bearer, blacksmith, innkeepers, market traders, farmer, farmhand, plus ruffians | `hobbit`, `hobbit_shirriff` (guard), `bree_hobbit`, `bree_man`, `bree_guard` | Rename our guard to `hobbit_bounder` and reserve `hobbit_shirriff` for the captain/hirer; add `bree_captain` and ruffians in a later ticket. |
| Hobbit civilians | Flee orcs/wargs/trolls/spiders within 12, players at -100 or worse, panic when hurt | Flee hostile-band players within 8 | Keep ours; consider Legacy's -100 bar so slightly negative players aren't shunned. |
| Day/night | No routine for good NPCs; orcs slowed in sun, trolls petrify | Full dusk/home routine | Keep ours as new content, but scope it later; it is not parity-critical. |
| Equipment drops | ~1/(20n) per piece, damaged | 0% | Allow a small chance once faction gear exists. |
| Coin drops | 1 in 8 chance, ~1-3 coins | 0-2 coins | Adopt Legacy's rarer but chunkier drop. |
| Despawn | Vanilla monster rules plus persistence for structure, hired, quest, respawning traders | Same | Matches. |
| Structure respawn | Every 3600 ticks, cap ~4, no player within 24; traders 10-30 min after death | Check every 600 ticks, 1 day after death | Shorten our 1-day delay for traders to 10-30 min. |
| Speech cooldown | 40 ticks; plus target-acquire, witness, hired and advertisement lines | 60 ticks, talk only | Keep 60; add target-acquire lines (crowd-limited) and trader adverts later. |

### Uncertainty

- The adult-hobbit -100 flee bar and child-flee rule come from the alignment table and the hobbit flee AI; the decompiled flee filter appears logically inverted (likely a decompiler or refactor artifact), so the stated rule is our best reading of intent.
- Common orcs in sunlight receive a zero-strength resistance effect alongside slowness; in 1.7.10 that effect gives no protection, so we list only the slowness.
