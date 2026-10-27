# Legacy reference: factions and alignment

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: factions, relations, alignment sources and spread, ranks, pledging, areas of influence, conquest, drain, commands. Ids below use our snake_case style and our draft ids where they exist (`dunedain_north`, `easterling`, `far_harad`, `harad_jungle`), not Legacy's internal names.

## 1. Factions

25 player-facing factions plus a few internal pseudo-factions. Regions are the three map groups Legacy uses for its faction screen, plus Utumno which lives in its own dimension.

| # | Our id | Legacy display name | Region | Kind | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 | `hobbit` | Hobbits | West | free, men-like | Isolationist (see 6); disapproves of civilian kills |
| 2 | `bree` | Bree-land | West | free, men | |
| 3 | `dunedain_north` | Dúnedain of the North | West | free, men | |
| 4 | `blue_mountains` | Blue Mountains | West | free, dwarf | |
| 5 | `high_elf` | High Elves | West | free, elf | |
| 6 | `gundabad` | Gundabad | West | orc | |
| 7 | `angmar` | Angmar | West | orc, troll | |
| 8 | `wood_elf` | Woodland Realm | West | free, elf | NPCs attack below first rank (50), not below 0 |
| 9 | `dol_guldur` | Dol Guldur | West | orc | |
| 10 | `dale` | Dale | West | free, men | |
| 11 | `durins_folk` | Durin's Folk | West | free, dwarf | |
| 12 | `lothlorien` | Lothlórien | West | free, elf | |
| 13 | `dunland` | Dunland | West | men | |
| 14 | `isengard` | Isengard | West | orc | |
| 15 | `fangorn` | Fangorn | West | free, tree | Isolationist |
| 16 | `rohan` | Rohan | West | free, men | |
| 17 | `gondor` | Gondor | West | free, men | Largest area of influence in the West |
| 18 | `mordor` | Mordor | West | orc | |
| 19 | `dorwinion` | Dorwinion | East | free, men, elf | |
| 20 | `easterling` | Rhúdel (mod-coined name for the Easterlings) | East | men | |
| 21 | `near_harad` | Near Harad | South | men | |
| 22 | `far_harad` | Morwaith (mod-coined) | South | men | |
| 23 | `harad_jungle` | Taurethrim (mod-coined) | South | free, men | |
| 24 | `half_troll` | Half-trolls | South | men, troll | |
| 25 | `utumno` | Utumno | Utumno dimension | orc | Fixed alignment -66,666; not playable; mortal enemy of every playable faction |

Internal pseudo-factions (not shown to players, cannot be pledged, alignment never changes):

| Id | Fixed alignment | Role |
| --- | --- | --- |
| `dark_huorn` | -1 | Hostile trees; mortal enemy of `hobbit`, `bree`, `dunedain_north` |
| `ruffian` | 0 | Bree-land troublemakers |
| `hostile` | n/a | Treated as mortal enemy of everything (generic monsters) |
| `unaligned` | n/a | Treated as neutral to everything (animals etc.) |

The "kind" tags (free, men, elf, dwarf, orc, troll, tree) are used for grouping, e.g. some hired units require a pledge to *any* elf faction or *any* dwarf faction.

"Disapproves of civilian kills": these factions give no kill bonus when the player kills an enemy *civilian* (farmer, trader, etc.) and their own NPCs never target civilians. Factions that disapprove: `hobbit`, `bree`, `dunedain_north`, `blue_mountains`, `high_elf`, `wood_elf`, `dale`, `durins_folk`, `lothlorien`, `fangorn`, `rohan`, `gondor`, `dorwinion`, `easterling`, `near_harad`. Factions that do not mind: `gundabad`, `angmar`, `dol_guldur`, `dunland`, `isengard`, `mordor`, `far_harad`, `harad_jungle`, `half_troll`, `utumno`.

## 2. Relations

Five levels: ally, friend, neutral, enemy, mortal enemy. Relations are symmetric. Any pair not listed is **neutral** (Legacy is much sparser than a full bloc-vs-bloc matrix). A faction is always ally to itself.

"Good" relations = ally or friend. "Bad" relations = enemy or mortal enemy.

| Faction | Ally | Friend | Enemy | Mortal enemy |
| --- | --- | --- | --- | --- |
| `hobbit` | bree, dunedain_north, blue_mountains, dale, durins_folk | high_elf, wood_elf, lothlorien, rohan, gondor | - | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `bree` | hobbit | dunedain_north, blue_mountains, high_elf, wood_elf, dale, durins_folk, lothlorien | - | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `dunedain_north` | hobbit, high_elf, lothlorien, gondor | bree, wood_elf, rohan | dunland, easterling, near_harad, far_harad | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `blue_mountains` | hobbit, durins_folk | bree | - | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `high_elf` | dunedain_north, lothlorien, fangorn | hobbit, bree, wood_elf, gondor | easterling, near_harad | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `gundabad` | - | angmar, dol_guldur, mordor | - | hobbit, bree, dunedain_north, blue_mountains, high_elf, wood_elf, dale, durins_folk, lothlorien, fangorn, rohan, gondor, dorwinion |
| `angmar` | dol_guldur, mordor | gundabad | - | same 13 as gundabad |
| `wood_elf` | lothlorien, fangorn, dorwinion | hobbit, bree, dunedain_north, high_elf | easterling, near_harad | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `dol_guldur` | angmar, mordor | gundabad | - | same 13 as gundabad |
| `dale` | hobbit, durins_folk | bree, rohan, gondor | easterling | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `durins_folk` | hobbit, blue_mountains, dale | bree, dunland | - | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `lothlorien` | dunedain_north, high_elf, wood_elf, fangorn | hobbit, bree | easterling, near_harad | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `dunland` | - | durins_folk, isengard | dunedain_north, gondor | rohan |
| `isengard` | - | dunland, half_troll | - | same 13 as gundabad |
| `fangorn` | high_elf, wood_elf, lothlorien, harad_jungle | - | - | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `rohan` | gondor | hobbit, dunedain_north, dale | near_harad, far_harad | gundabad, angmar, dol_guldur, dunland, isengard, mordor, easterling, half_troll |
| `gondor` | dunedain_north, rohan | hobbit, high_elf, dale | dunland, far_harad | gundabad, angmar, dol_guldur, isengard, mordor, easterling, near_harad, half_troll |
| `mordor` | angmar, dol_guldur, half_troll | gundabad, easterling, near_harad, far_harad | - | the 13 above plus harad_jungle |
| `dorwinion` | wood_elf | - | - | gundabad, angmar, dol_guldur, isengard, mordor, half_troll |
| `easterling` | - | mordor | dunedain_north, high_elf, wood_elf, dale, lothlorien | rohan, gondor |
| `near_harad` | - | mordor, far_harad, half_troll | dunedain_north, high_elf, wood_elf, lothlorien, rohan, harad_jungle | gondor |
| `far_harad` | - | mordor, near_harad | dunedain_north, rohan, gondor | harad_jungle |
| `harad_jungle` | fangorn | - | near_harad | mordor, far_harad, half_troll |
| `half_troll` | mordor | isengard, near_harad | - | the 13 above plus harad_jungle |

Plus: every playable faction is mortal enemy of `utumno`.

Notable consequences:
- Many "obvious" free-peoples pairs are **neutral**: e.g. Gondor and Lothlórien, Rohan and the elves, Dorwinion and Dale. Killing a Gondor soldier does not affect Lothlórien.
- Dunland is friend to Durin's Folk; Fangorn is ally to the jungle faction.
- Evil factions among themselves are mostly friend, not ally (Gundabad has no allies at all).
- Relations can be overridden per world by ops (`/facRelations`); overrides are saved with the world and synced to clients.

NPC targeting: two faction NPCs fight if their relation is bad. A neutral NPC attacks another neutral-faction NPC only if it is a hired unit whose owner has negative alignment with the target's faction.

## 3. Alignment basics

| Thing | Legacy behavior |
| --- | --- |
| Storage | One decimal number per player per playable faction; missing = 0 |
| Display | One decimal place with a sign (e.g. +12.5) |
| Range | No clamp during normal play. Commands accept -10,000 to +10,000 only |
| NPC hostility | NPCs attack a player whose alignment with their faction is below 0 (Woodland Realm: below 50, its first rank) |
| Fixed factions | Alignment reads the fixed value and never changes |
| Sync | Every change is broadcast to all players in the world (other players' alignment is visible unless the player hides it) |
| Per-faction stats | Counters kept per faction: NPCs of it killed, its enemies killed, trades, hires, mini-quests done, conquest earned, conquest-horn taken |

## 4. Alignment sources

### 4.1 Kills (the main source)

Every faction NPC type has a kill value. Typical values:

| NPC role | Value |
| --- | --- |
| Civilian / basic member (hobbit, Bree-lander, basic orc, Dale man) | 1 |
| Warrior, guard, smith, trader, farmer, bartender, warg, uruk | 2 |
| Elite warrior (e.g. Dorwinion elf warrior), troll, snow troll, ent | 3 |
| Mountain troll, olog-hai, mirk-troll | 4 |
| Captain, lord, warlord, chieftain, commander, marshal | 5 |
| Huorn | 2 |
| Boss-tier (mountain troll chieftain, mallorn ent) | 50 |

When a player kills an NPC of faction V with value `b`:

| Faction affected | Change |
| --- | --- |
| Every faction with a **bad** relation to V (enemy or mortal enemy) | **+b**, times the area-of-influence multiplier for that faction (section 6), then halved if the player is at or above that faction's pledge threshold and not pledged to it, then capped (section 7.3) |
| V itself | **-b**, always full strength |
| Every faction with a **good** relation to V (ally or friend) | **-b** times the area-of-influence multiplier for that faction |

- Enemy and mortal enemy give the same bonus (factor 1.0). Ally and friend give the same penalty (factor 1.0, not reduced).
- **Penalty scaling:** if the player's current alignment with a penalized faction is positive, the penalty is multiplied by `alignment / 50`, clamped to 1..20. So killing a value-1 NPC of a faction you hold +1,000 with costs 20; at +500 costs 10; below +50 costs the base.
- **Civilian kills:** factions that disapprove of civilian kills (section 1) get no bonus from them. Penalties still apply.
- Some NPCs carry a forced list of bonus factions that always get the full bonus regardless of area of influence (e.g. special enemies placed in a faction's land).
- **Hired-unit kills:** a kill by a player's hired unit counts only if the owner is within 64 blocks. It gives **no alignment bonus or penalty at all**; it only feeds conquest at 25% (section 8) and kill counters are not incremented.
- **Self-defence:** killing a hired unit that was commanded to attack you, while you have positive alignment with its faction, gives no alignment change.
- PvP kills do not change alignment directly (bounties excepted).
- A floating "+2.0 Mordor / -2.0 Gondor" style indicator appears at the kill position.

### 4.2 Other sources

| Source | Change | Notes |
| --- | --- | --- |
| Mini-quest completion | Per-quest value times random 0.75..1.25, minimum 1 | Kill quests: targets x quest factor. Collect quests: item count x quest factor (min 1). Welcome quests: 0. Same halving rule as kills when above pledge threshold and unpledged. Some quest groups give nothing while alignment is negative |
| NPC marriage | +5 to the spouse's faction | |
| Cutting a Fangorn tree | -1 Fangorn | |
| Killing a Rohan horse | -1 Rohan | |
| Picking grapes in a Dorwinion vineyard without permission | -1 Dorwinion | Vineyards are free to use at 2,000 Dorwinion |
| Failed pickpocket mini-quest | -1 to the target's faction | |
| Breaking a pledge | see 7.2 | |
| **Trading** | **0** | Only increments the trade counter |
| **Hiring** | **0** | Only increments the hire counter; costs coins |

No passive gain over time.

### 4.3 Alignment gates (examples of use)

Common thresholds that consume alignment without spending it: crafting tables and portals usable at +1; traders and captains typically need 0, 50, 100, 150, 200, 250, 300 or 400 depending on the NPC (e.g. smiths 50 to 100, captains 100 to 300, Black Uruk captain 400); riding wargs or spiders 50; mountain/Mordor trolls trust at 100; conquest horn 1,500; hobbit marriage 100, dwarf marriage 200; huorn summoning 500.

Hire price formula (NPC pod reference): base cost doubled if not pledged to the unit's faction; then a discount of up to 50% that grows with alignment surplus above the unit's requirement (full discount at +1,500 surplus when pledged, +2,000 when not). Minimum 1 coin. Some units are pledge-exclusive.

## 5. Ranks

A player's rank is the highest rank whose threshold is at or below their alignment. Below the first rank: "neutral" at 0 or more, "enemy" below 0. Each rank grants an achievement and an unlockable title. Ranks marked (g) have a separate feminine display name. The **pledge rank** (bold) is the rank needed to pledge; ranks above it require being pledged to earn their achievement and title.

Display names are mostly plain English or Tolkien terms; several elf and dwarf ranks use mod-authored Elvish/Khuzdul titles, which we must not reuse. Ids below are descriptive.

| Faction | Rank thresholds (id) |
| --- | --- |
| `hobbit` | 10 guest, **100 friend**, 250 hayward, 500 bounder, 1000 shirriff, 2000 chief_shirriff, 3000 thain |
| `bree` | 10 guest, 50 friend, **100 townsman**, 200 trustee, 500 champion, 1000 captain, 2000 master |
| `dunedain_north` | 10 friend, 50 warden, **100 ranger**, 200 soldier, 500 knight, 1000 champion, 2000 captain |
| `blue_mountains` | 10 guest, 50 friend, **100 warden**, 200 axebearer, 500 champion, 1000 captain, 1500 noble, 3000 lord (g) |
| `high_elf` | 10 guest, 50 friend, **100 warrior**, 200 herald, 500 captain, 1000 noble, 2000 commander, 3000 lord (g) |
| `gundabad` | 10 thrall, 50 snaga, **100 raider**, 200 ravager, 500 scourge, 1000 warlord, 2000 chieftain |
| `angmar` | 10 thrall, 50 servant, **100 kinsman**, 200 warrior, 500 champion, 1000 warlord, 2000 chieftain |
| `wood_elf` | 50 guest, **100 friend**, 200 marchwarden, 500 herald, 1000 captain, 2000 noble, 3000 lord (g) |
| `dol_guldur` | 10 thrall, 50 servant, **100 brigand**, 200 torchbearer, 500 despoiler, 1000 captain, 2000 lieutenant |
| `dale` | 10 guest, 50 friend, **100 soldier**, 200 herald, 500 captain, 1000 marshal, 2000 lord (g) |
| `durins_folk` | 10 guest, 50 friend, **100 oathfriend**, 200 axebearer, 500 champion, 1000 commander, 1500 lord (g), 3000 king (g) |
| `lothlorien` | 10 guest, 50 friend, **100 warden**, 200 warrior, 500 herald (g), 1000 captain, 2000 noble, 3000 lord (g) |
| `dunland` | 10 guest, 50 kinsman, **100 warrior**, 200 club_bearer, 500 avenger, 1000 warlord, 2000 chieftain |
| `isengard` | 10 thrall, 50 snaga, **100 soldier**, 200 tree_feller, 500 berserker, 1000 corporal, 1500 hand, 3000 captain |
| `fangorn` | 10 newcomer, 50 friend, **100 treeherd**, 250 master, 500 elder |
| `rohan` | 10 guest, 50 footman, **100 at_arms**, 250 rider, 500 esquire, 1000 captain, 2000 marshal |
| `gondor` | 10 guest, 50 friend, **100 at_arms**, 200 soldier, 500 knight, 1000 champion, 1500 captain, 3000 lord (g) |
| `mordor` | 10 thrall, 50 snaga, **100 brigand**, 200 slave_driver, 500 despoiler, 1000 captain, 1500 lieutenant, 3000 commander |
| `dorwinion` | 10 guest, 50 vinehand, **100 wine_merchant**, 200 vintner_guard, 500 captain, 1000 master, 1500 chief_taster, 3000 lord (g) |
| `easterling` | 10 bondsman, 50 levyman, **100 clansman**, 200 warrior, 500 champion, 1000 golden_warrior, 1500 warlord, 3000 chieftain |
| `near_harad` | 10 guest, 50 friend, **100 kinsman**, 200 warrior, 500 champion, 1000 serpent_guard, 1500 warlord, 3000 prince (g) |
| `far_harad` | 10 guest, 50 friend, **100 kinsman**, 250 hunter, 500 warrior, 1000 chief, 3000 great_chief |
| `harad_jungle` | 10 guest, 50 friend, **100 forest_man**, 200 warrior, 500 champion, 1000 warlord, 3000 chieftain |
| `half_troll` | 10 guest, 50 scavenger, **100 troll_kin**, 200 warrior, 500 raider, 1000 warlord, 2000 chieftain |

Pattern: 7 or 8 ranks, first at 10 (Woodland Realm 50), pledge always at 100, top at 2,000 or 3,000 (Fangorn tops out at 500). Middle steps are 50/100/200/500/1000 with 250 or 1500 substitutions.

Rank changes give a chat notice and unlock the rank achievement and title (pledge-gated above the pledge rank). A server option makes titles at the pledge rank also require pledging.

## 6. Areas of influence (control zones)

Each faction has a set of circular zones on the map around its landmarks (Gondor: around Minas Tirith, Dol Amroth's region, the Green Hills, Poros and Harad crossings, Umbar; Mordor: one huge zone centred on Barad-dûr; Hobbits: several small zones across the Shire and Bree; and so on, 1 to 10 zones per faction). Radii range from 10 to 500 map pixels; one map pixel is 128 blocks.

Effect on kill alignment for a bonus or ally-penalty faction F:

| Player position | Multiplier for F |
| --- | --- |
| Inside one of F's zones, or within 24 blocks of a living NPC of F | 1.0 |
| Outside, within 50 map px (6,400 blocks) of F's nearest zone edge | Linear falloff from 1.0 at the edge to 0.0 at 50 px |
| Beyond that, or in another dimension | 0 (no change) |
| Isolationist factions (`hobbit`, `fangorn`) | No falloff band: 1.0 inside, 0 outside |

- The killed NPC's own faction always takes the full penalty wherever the kill happens.
- Zones can be switched off per world by ops; then every location counts as inside. The "classic" world type also disables them.
- Kills inside a faction's zones (plus 50 px margin) are recorded for bounties: enemy factions later offer bounty mini-quests on players with many recent kills. Kill records last 3,456,000 ticks (48 in-game days); a player whose bounty was claimed is protected for 864,000 ticks (12 days).

## 7. Pledging

### 7.1 Making a pledge

| Requirement | Value |
| --- | --- |
| Alignment | At or above the faction's pledge rank threshold (100 for all factions) |
| Mortal enemies | No positive alignment with any mortal enemy of that faction (enemy-only relations do not block) |
| Cooldown | No active pledge-break cooldown |
| Where | From the faction screen, anywhere; one pledge at a time |

Pledging plays a sound, grants a "pledge" achievement and re-checks rank achievements (unlocking pledge-gated ones already earned by alignment).

### 7.2 Breaking a pledge

Ways to lose it:
- Voluntary unpledge from the faction screen.
- Automatic, whenever any alignment change leaves the pledge requirement unmet (mainly: dropping below the pledge threshold).
- Killing NPCs of the pledged faction or its **allies** (friends do not count): the first kill gives a warning and starts a 24,000-tick (one in-game day) counter; a second such kill while the counter is running breaks the pledge. The counter only counts down while online.

On any break:

| Effect | Value |
| --- | --- |
| Alignment penalty | Alignment drops to the threshold of the rank **two below** the current rank, but never below half the pledge threshold (50); no change if already lower. Shown as a normal alignment change |
| Cooldown | 36,000 ticks (30 min) plus up to 180,000 ticks (150 min) scaled by (alignment above the pledge threshold) / 5,000, capped at 1. Max 3 h at +5,100 or more |
| Cooldown clock | Counts down only while the player is online; the broken faction is remembered and shown during the cooldown |
| End of cooldown | Chat notice; any faction may then be pledged, including the same one |

Example: Gondor at +600 (knight). Break: rank two below knight is at_arms (100), so alignment becomes 100 (penalty -500). Cooldown = 36,000 + (500/5,000) x 180,000 = 54,000 ticks (45 min).

### 7.3 Effects of being pledged

| Effect | Value |
| --- | --- |
| Gain rate above pledge threshold | Unpledged players get **half** of every positive gain for a faction once at or above its pledge threshold. Pledged players get full gains for their pledged faction |
| Mortal-enemy cap | While pledged, alignment with the pledge faction's mortal enemies cannot rise above 0 (gains are clipped) |
| Ranks above pledge rank | Their achievements and titles require the pledge |
| Hiring | Full price only when pledged (unpledged pays double); some units require the pledge, or a pledge to any elf (non-man) or any dwarf faction |
| Conquest | Only a pledged player generates conquest, for the pledged faction; can view conquest of own faction, allies and enemies (others need the rank two above the pledge rank) |
| Invasions | Defeating an invasion of an enemy faction gives conquest to the pledge faction |
| Fast travel (unsure) | Some waypoint behavior depends on the pledge faction holding 500+ conquest strength in that zone |

## 8. Conquest

| Thing | Value |
| --- | --- |
| Grid | Map split into cells of 8x8 map px (1,024 x 1,024 blocks); each cell stores a strength per faction |
| Gain | On killing an NPC whose faction is an enemy of the player's pledge faction: kill value x world conquest rate (default 1.0) x biome gain rate (default 1.0); hired-unit kills 25% |
| Spread | Applied to all cells within 3.5 cells of the player's cell, scaled linearly from 100% at the centre to 0% at 3.5 cells |
| Cleansing | In a cell where the victim's faction has strength, the amount reduces that faction's strength instead of adding to yours |
| Effectiveness | Full in cells whose biomes spawn the pledge faction's NPCs; reduced "ally boost" where only an ally spawns; none elsewhere |
| Decay | A cell untouched for 1 hour (72,000 ticks of world time) loses 1 strength per further hour, for all factions in it |
| Toggles | Server config to disable conquest; per-player option to stop kills generating conquest; disabled in the classic world type and outside Middle-earth |
| Conquest horn | Needs 1,500 alignment |

## 9. Alignment drain

| Thing | Value |
| --- | --- |
| Trigger | Player holds positive alignment with two playable factions that are **mortal enemies** of each other |
| Rate | Every 1,000 player ticks online (50 s), each faction involved in at least one such pair loses 5 (not below 0); a faction in several pairs still loses 5 once |
| Notice | A one-time explanatory message plus a per-drain client notice with the number of factions drained |
| Toggle | Server config, default on |
| Migration | Old saves holding positive alignment with mortal enemies were offered a one-time choice of which alignments to zero |

## 10. Commands (all op-level)

| Command | Effect |
| --- | --- |
| `/alignment set <faction or all> <value> [player]` | Set alignment (-10,000..10,000) |
| `/alignment add <faction or all> <amount> [player]` | Add; refuses results outside -10,000..10,000 |
| `/alignmentsee <player>` | Open another player's faction screen read-only |
| `/facRelations set <f1> <f2> <relation>` / `/facRelations reset` | Override or reset relations world-wide |
| `/pledgeCooldown <ticks> [player]` | Set the pledge-break cooldown (0..10,000,000) |
| `/conquest set/add/radial <faction> <amount> [x z]`, `/conquest clear [x z]`, `/conquest rate [value]` | Edit conquest |
| `/alignmentZones enable/disable` | Toggle areas of influence |

Players can also hide their alignment from others (faction screen option).

## Differences from our draft spec

Compared with `specs/core/core-apis.md` (core-1 draft).

| # | Topic | Legacy | Our draft | Recommendation |
| --- | --- | --- | --- | --- |
| 1 | Relation matrix | Sparse: about 155 explicit pairs, many free-peoples pairs neutral; evil factions mostly friend, Gundabad has no allies | Dense bloc rules: everyone in a bloc ally, cross-bloc enemy | **Decide.** Legacy's sparsity keeps kills local; our blocs make every kill ripple across 12 factions. Lean toward Legacy-like sparsity, expressed through our own datagen rules |
| 2 | Mortal enemy set | Very broad: every free faction vs every orc faction, plus a few men pairs | 10 hand-picked pairs | **Decide.** This drives pledge blocking and drain. Legacy's breadth makes "one side only" the norm; ours allows dual-alignment play |
| 3 | Ally/friend penalty share | Full penalty (1.0) to both, scaled by area of influence | 0.5 ally, 0.25 friend, everywhere | Keep ours (simpler, no zones yet); revisit if we add areas of influence |
| 4 | Penalty scaling with standing | Penalty x clamp(alignment/50, 1, 20) when alignment is positive | None | **Match Legacy** (or similar): it is what makes betrayal at high rank hurt |
| 5 | Areas of influence | Bonus to distant factions fades to 0 outside their zones | None; bonuses apply everywhere | Decide; needs World pod waypoints. Recommend deferring, with a hook in `applyKill` for a multiplier |
| 6 | Halved gains above pledge threshold when unpledged | Yes, all positive gains | No | **Match Legacy**: core incentive to pledge |
| 7 | Mortal-enemy cap while pledged | Gains with mortal enemies clipped at 0 | Not specified | **Match Legacy**: prevents pledge then grind enemies |
| 8 | Trade alignment | 0 | +0.1 per 10 coins, cap 5/day | Keep ours or drop; decide with Progression. Legacy relies on kills and quests only |
| 9 | Mini-quest reward | Per-quest value, random x0.75..1.25 | Flat +10 (5..50) | Keep ours as default; adopt the random spread |
| 10 | Hired-unit kills | No alignment at all; conquest only at 25%, owner within 64 blocks | 0.5 share | Decide; Legacy avoids farming alignment with armies. Lean toward Legacy (0) or a small share |
| 11 | Civilian kills | Factions disapproving of civilian kills give no bonus | Not modeled | Match Legacy later (needs a civilian flag on `FactionMember`); add the flag now |
| 12 | Drain | -5 every 1,000 ticks per involved faction | max(1, 1%) every 6,000 ticks | Keep ours (gentler); Legacy drains 360/hour which forces a choice quickly. Decide tuning |
| 13 | Pledge break by kills | Warn on first kill of pledged/allied NPC, break on second within 24,000 ticks | Break on first kill | **Match Legacy** (one-kill grace avoids accidental breaks) |
| 14 | Pledge break penalty | Drop to the rank two below (min 50) | None | **Match Legacy**: we left it to Progression; move into core |
| 15 | Pledge cooldown | 30 min to 3 h of online time, scaled by alignment | Flat 24,000 ticks of game time | **Match Legacy** shape (online time, scaled), our own constants |
| 16 | Alignment range | Unclamped in play, commands +-10,000 | Clamped +-100,000 | Keep ours |
| 17 | Rank ladders | Per-faction ids and thresholds, 5 to 8 ranks, pledge always at 100 | Default ladder 10..3000 with r1..r8 | Keep our default ladder; use faction-specific ids from our lore pass, not Legacy's mod-coined titles |
| 18 | Woodland Realm hostility | Below 50 | Below 50 | Same |
| 19 | Fixed factions | Utumno -66,666, plus internal hostile/unaligned/dark-huorn/ruffian | Utumno -66,666 | Same; add `hostile`/`unaligned` pseudo-relations for non-faction mobs |
| 20 | Faction set | 25, with easterling, far_harad, harad_jungle as single factions | 25, same split | Same; avoid Legacy's coined names (Rhúdel, Morwaith, Taurethrim) in display names |
| 21 | Conquest | Full system (grid, spread, decay) | Not in core spec | Out of scope for now; note for World/Progression |
| 22 | Commands | `/alignment set/add`, `/facRelations`, `/pledgeCooldown`, etc. | `/lotr alignment ...` | Keep ours (single root); add `relations` and `pledge cooldown` subcommands |
