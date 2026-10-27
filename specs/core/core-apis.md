# Core APIs

- **Pod:** core
- **Lead:** Opus 1
- **Status:** draft
- **Depends on:** nothing (this spec unblocks every other pod)
- **Sources:** behavior reference `docs/reference/legacy/factions-alignment.md` (with `npcs.md`,
  `hiring-units.md`, `mini-quests.md`). Alignment and hostility rules follow the original mod's
  behavior by maintainer decision; numbers come from those notes, wording and design are ours.

## Summary

The shared foundation every pod builds on: how content is registered, the 25 factions as a
datapack registry with their relations, per-player alignment (the number that decides who attacks
you, who trades with you, who you can hire and which titles you hold), the events and API other pods
call to read and change alignment, network payload conventions, shared tags and constants, and the
`FactionMember` hook that NPCs implement. Players see this as: every faction remembers what you did
to it, killing a faction's enemies raises your standing, killing its people or its friends lowers
it (more sharply the more they trusted you), and ranks unlock as standing grows. Alignment comes
almost only from combat and quests; trading and hiring never change it. Numbers are defaults that
live in server config so they can be tuned without code changes.

## Player-facing behavior

1. A new player starts at **0** alignment with every faction except Utumno (locked, see below).
2. Killing an NPC of faction V lowers alignment with V and with every faction that is V's ally or
   friend, and raises it with every faction that is V's enemy or mortal enemy. Factions with no
   relation to V are untouched. The loss is multiplied when the player was trusted (see Kill rules).
   A short floating text (`+1.0 Gondor`, `-1.0 Mordor`, one line per faction changed) appears near
   the hotbar for 3 s.
3. Alignment also comes from mini-quests, admin commands and a few one-off events (marriage, cutting
   protected trees, and similar). **Trading and hiring never change alignment.** There is no passive
   gain over time.
4. When alignment crosses a rank threshold up or down, the player gets a chat message naming the
   faction and the new rank, plus a short sound on promotion.
5. NPCs of a faction are hostile to the player while the player's alignment with that faction is
   below the faction's hostility threshold (0 for most; 50 for the Woodland Realm). Some factions
   add a grey zone above the threshold where NPCs attack only by chance (Mordor orcs up to +100).
   There is no separate "friendly" band in core: whether an NPC trades, hires out or gives quests is
   decided per NPC role by the NPC pod (`trade_min`).
6. Holding positive alignment with two factions that are mortal enemies drains both, 5 points every
   50 s online (can be turned off in server config).
7. A player may pledge to one faction once they reach its pledge rank (100) and hold no positive
   alignment with any of its mortal enemies. Without a pledge, gains with a faction are halved once
   at or above its pledge threshold. While pledged, alignment with the pledge faction's mortal
   enemies cannot rise above 0. Killing one NPC of the pledged faction or its allies gives a warning;
   a second within one in-game day breaks the pledge. Breaking costs alignment and starts a cooldown.
8. Alignment and pledge survive death, logout, dimension change and server restart.
9. Ops can inspect and change alignment with `/lotr alignment ...` (see Commands).

## Rules and numbers

### Alignment values

| Thing | Value | Notes |
| --- | --- | --- |
| Storage type | `float`, rounded to 0.1 on every write | Display always shows one decimal with a sign |
| Range | -100,000.0 to +100,000.0 | Clamped on write |
| Starting value | 0.0 | Absent map entries read as 0.0 |
| Utumno | fixed -66,666.0 | Any faction with `fixed_alignment` ignores every change, including commands |
| Creative / spectator | never targeted, alignment still changes | |

### Hostility

| Thing | Value | Notes |
| --- | --- | --- |
| Hard hostility | alignment(F) < F.`hostility_threshold` | Default 0.0. `wood_elf` 50.0 (its first rank) |
| Grey zone | optional `suspicion` on the faction: `{ "ceiling": c, "factor": k }` | For threshold <= a < c, each NPC target check attacks with chance `min(1, 1 / (k * a))`; a <= 0 means chance 1 |
| `mordor` | `suspicion` ceiling 100, factor 5 | Troll NPC types use factor 10 via the `FactionMember` override (NPC pod) |
| At or above ceiling (or threshold if no grey zone) | never hostile on alignment grounds | NPC rules (provocation, witnesses, hired units) layer on top |
| Trade / hire / quest minimums | not core | Per NPC role `trade_min`, owned by NPC; typical 0, 50, 100 to 400 |

Core exposes the chance; the NPC pod's targeting rolls it at its own check rate.

### Rank ladders

Ranks are listed in each faction JSON, sorted by threshold. Rank ids are stable (titles, quests and
hiring refer to them); display names are lang. Rank ids are positional placeholders `r1`..`rN` until
the lore pass assigns our own descriptive ids per faction. Never reuse the original mod's invented
Elvish or Dwarvish rank titles.

Rank of a player = highest listed rank whose threshold <= alignment; else `neutral` if alignment >=
0, else `hostile` (both implicit, never listed). The **pledge rank** is the rank at threshold 100 in
every faction; ranks above it need a pledge for their titles and achievements (Progression).

| Ladder | Thresholds | Factions |
| --- | --- | --- |
| A (8 ranks, default) | 10, 50, **100**, 200, 500, 1000, 1500, 3000 | `blue_mountains`, `durins_folk`, `isengard`, `gondor`, `mordor`, `dorwinion`, `easterling`, `near_harad` |
| B (8) | 10, 50, **100**, 200, 500, 1000, 2000, 3000 | `high_elf`, `lothlorien` |
| C (7) | 10, 50, **100**, 200, 500, 1000, 2000 | `bree`, `dunedain_north`, `gundabad`, `angmar`, `dol_guldur`, `dale`, `dunland`, `half_troll` |
| D (7) | 10, **100**, 250, 500, 1000, 2000, 3000 | `hobbit` |
| E (7) | 50, **100**, 200, 500, 1000, 2000, 3000 | `wood_elf` |
| F (7) | 10, 50, **100**, 250, 500, 1000, 2000 | `rohan` |
| G (7) | 10, 50, **100**, 250, 500, 1000, 3000 | `far_harad` |
| H (7) | 10, 50, **100**, 200, 500, 1000, 3000 | `harad_jungle` |
| I (5) | 10, 50, **100**, 250, 500 | `fangorn` |

Bold is the pledge rank. A faction JSON that omits `ranks` gets ladder A.

### Kill rules

Victim faction V, base value `b` from the victim's `FactionMember.alignmentKillValue()`. Defaults the
NPC pod applies per role: civilian 1, guard / trader / smith 2, captain 5 (NPC specs may use other
values for elites, trolls and bosses). `m` is server config `alignment.kill_multiplier` (default 1.0).
`inf(X)` is the area-of-influence multiplier for faction X, a reserved hook that returns 1.0 until a
later World/Core spec defines zones.

| Target faction X | Change to alignment(X) | Config key, default |
| --- | --- | --- |
| X = V | -b·m·scale(X) | - |
| relation(V,X) = `ally` | -b·m·1.0·inf(X)·scale(X) | `alignment.ally_share` 1.0 |
| relation(V,X) = `friend` | -b·m·1.0·inf(X)·scale(X) | `alignment.friend_share` 1.0 |
| relation(V,X) = `neutral` | 0 | - |
| relation(V,X) = `enemy` | +b·m·1.0·inf(X), then gain modifiers | `alignment.enemy_share` 1.0 |
| relation(V,X) = `mortal_enemy` | +b·m·1.0·inf(X), then gain modifiers | `alignment.mortal_enemy_share` 1.0 |

- **Penalty scaling.** `scale(X)` = clamp(alignment(X) / 50, 1, 20) when alignment(X) > 0, else 1.
  Read before the change. Killing a value-1 NPC of a faction you hold +500 with costs 10; at +1,000
  or more it costs 20; below +50 it costs the base.
- **Civilian kills.** If the victim's `FactionMember.isCivilian()` is true, factions with
  `disapproves_civilian_kills` get no bonus. Penalties still apply.
- **Gain modifiers** (apply to every positive change from any source except `command`):
  1. **Unpledged halving.** If the player is not pledged to X and alignment(X) before the change is
     >= X's pledge threshold, the gain is halved. Factions without a pledge rank are never halved.
  2. **Mortal-enemy cap.** If the player is pledged to P and X is a `mortal_enemy` of P, the gain is
     clipped so the result is at most 0 (a gain while already above 0 becomes 0).
- **Who gets credit.** A kill counts when the killing damage source's entity is a player or a
  projectile owned by a player.
- **Hired-unit kills** (killer implements `AlignmentCreditor` naming an online player) give **no
  alignment** to anyone. Core still fires `FactionKillEvent` with the hired flag set when the owner is
  within 64 blocks, so a later conquest system can give its 25% share; kill counters are not
  incremented.
- **No change** if the victim's `grantsAlignmentOnKill(killer)` is false (summoned, converted, spawn
  egg in creative, or the NPC pod's self-defence rule: a hired unit ordered to attack the killer while
  the killer holds positive alignment with its faction), or if the victim has no faction.
- **PvP kills** never change alignment.
- Factions with `fixed_alignment` never receive changes.
- Kill alignment is applied first; pledge warning and break rules (below) run after.

### Other sources (defaults; the owning pod applies them through the API)

| Source id | Default | Owner |
| --- | --- | --- |
| `lotrrevival:kill` | table above | Core (automatic) |
| `lotrrevival:quest` | per-quest value x random 0.75..1.25, minimum 1 (Progression sets values) | Progression |
| `lotrrevival:event` | one-off events, `detail` names the event: NPC marriage +5 to the spouse's faction; cutting a Fangorn tree -1 `fangorn`; killing a Rohan horse -1 `rohan`; picking Dorwinion vineyard grapes without permission (below +2,000) -1 `dorwinion`; failed pickpocket -1 to the target's faction | Owning pod (World, NPC, Progression) |
| `lotrrevival:pledge_break` | see Pledge | Core (automatic) |
| `lotrrevival:drain` | see below | Core (automatic) |
| `lotrrevival:command` | any | Core |

Trading and hiring give **0** alignment and have no source id. Event penalties use the plain value
(no penalty scaling; that applies to kills only).

### Mortal-enemy drain

Every 1,000 ticks of a player's online time (`alignment.drain_interval_ticks`), every faction that is
in at least one `mortal_enemy` pair where the player holds > 0 with both sides loses
`alignment.drain_amount` (default 5.0), once per interval no matter how many pairs it is in. Never
drains below 0. Fixed and hidden factions are excluded. Toggle `alignment.mortal_enemy_drain`
(default true). The first drain ever shows a one-time explanatory chat message; each drain shows a
short client notice with the number of factions drained.

### Pledge

| Thing | Value |
| --- | --- |
| Requirement | alignment >= the faction's pledge threshold (100); alignment <= 0 with every `mortal_enemy` of it (`enemy` does not block); no active cooldown; not already pledged. Factions with `fixed_alignment` or `hidden` cannot be pledged |
| Voluntary break | allowed any time (UI belongs to Progression) |
| Auto-break | after any alignment change that leaves the requirement unmet (mainly dropping below 100) |
| Kill warning | player directly kills an NPC of the pledged faction or of one of its `ally` factions (`friend` does not count): first kill gives a chat warning and starts a 24,000-tick counter of online time (`alignment.pledge_warning_ticks`); a second such kill while it runs breaks the pledge (reason `killed_ally`) |
| Break penalty | alignment with the broken faction drops to the threshold of the rank **two below** the current rank, but never below half the pledge threshold (50); if already at or below that value, no change. Below the first rank the target is 50. Applied as source `pledge_break` |
| Cooldown | 36,000 ticks + 180,000 ticks x clamp((alignment before penalty - pledge threshold) / 5,000, 0, 1), rounded down. Counts down only while online. The broken faction is remembered for display. Config `alignment.pledge_cooldown_base_ticks`, `alignment.pledge_cooldown_extra_ticks`, `alignment.pledge_cooldown_scale` |
| End of cooldown | chat notice; any faction may be pledged, including the same one |
| Command break | `/lotr pledge clear` breaks with reason `command`: no penalty, no cooldown |

Example: Gondor +600 (rank at 500). Two below is the 100 rank, so alignment becomes 100 (-500).
Cooldown = 36,000 + 500/5,000 x 180,000 = 54,000 ticks (45 min online).

Pledge benefits beyond these rules (hire prices, titles, conquest) are specified by other pods.

## Factions

25 factions in four regions. Ids are `lotrrevival:<id>`. Display names and rank names are a
lore-pass task and are not fixed here. Ids are ours; where the original mod used invented names we
use descriptive ones instead, and display names avoid those invented names too.

| Region | Faction ids |
| --- | --- |
| `westlands` | `hobbit`, `bree`, `dunedain_north`, `blue_mountains`, `high_elf`, `gundabad`, `angmar`, `wood_elf`, `dol_guldur`, `dale`, `durins_folk`, `lothlorien`, `dunland`, `isengard`, `fangorn`, `rohan`, `gondor`, `mordor` |
| `rhun` | `dorwinion`, `easterling` |
| `harad` | `near_harad`, `far_harad`, `harad_jungle`, `half_troll` |
| `deeps` | `utumno` (hidden from map and alignment lists, `fixed_alignment` -66,666) |

### Faction kinds and flags

| Faction | Kinds (`#lotrrevival:kind/<k>`) | `disapproves_civilian_kills` |
| --- | --- | --- |
| `hobbit` | free, men | yes |
| `bree` | free, men | yes |
| `dunedain_north` | free, men | yes |
| `blue_mountains` | free, dwarf | yes |
| `high_elf` | free, elf | yes |
| `gundabad` | orc | no |
| `angmar` | orc, troll | no |
| `wood_elf` | free, elf | yes |
| `dol_guldur` | orc | no |
| `dale` | free, men | yes |
| `durins_folk` | free, dwarf | yes |
| `lothlorien` | free, elf | yes |
| `dunland` | men | no |
| `isengard` | orc | no |
| `fangorn` | free, tree | yes |
| `rohan` | free, men | yes |
| `gondor` | free, men | yes |
| `mordor` | orc | no |
| `dorwinion` | free, men, elf | yes |
| `easterling` | men | yes |
| `near_harad` | men | yes |
| `far_harad` | men | no |
| `harad_jungle` | free, men | no |
| `half_troll` | men, troll | no |
| `utumno` | orc | no |

`hobbit` and `fangorn` are isolationist (matters only for the later area-of-influence spec: no
falloff band). Kinds are for grouping, e.g. hire units that need a pledge to any elf or dwarf faction.

### Relations

Five levels, symmetric: `ally`, `friend`, `neutral`, `enemy`, `mortal_enemy`. "Good" = ally or
friend, "bad" = enemy or mortal enemy. A faction is `ally` to itself. **Any pair not listed below is
`neutral`**; the table is deliberately sparse so kills stay local (killing a Gondor soldier does not
touch Lothlorien).

| Level | NPCs fight each other | Kill spread | Other effect |
| --- | --- | --- | --- |
| `ally` | never | full penalty | killing one counts toward breaking a pledge to the other |
| `friend` | never | full penalty | |
| `neutral` | no (only a hired unit whose owner has negative alignment with the target's faction) | none | |
| `enemy` | on sight | full bonus | |
| `mortal_enemy` | on sight | full bonus | blocks pledge, caps gains while pledged, causes drain |

The core datagen provider holds this table as data and writes each pair into both factions'
`relations` (runtime only reads explicit entries). 152 pairs plus 24 Utumno pairs.

**Ally (22 pairs)**

| Faction | Allies |
| --- | --- |
| `hobbit` | `bree`, `dunedain_north`, `blue_mountains`, `dale`, `durins_folk` |
| `dunedain_north` | `high_elf`, `lothlorien`, `gondor` |
| `blue_mountains` | `durins_folk` |
| `high_elf` | `lothlorien`, `fangorn` |
| `angmar` | `dol_guldur`, `mordor` |
| `wood_elf` | `lothlorien`, `fangorn`, `dorwinion` |
| `dol_guldur` | `mordor` |
| `dale` | `durins_folk` |
| `lothlorien` | `fangorn` |
| `fangorn` | `harad_jungle` |
| `rohan` | `gondor` |
| `mordor` | `half_troll` |

**Friend (29 pairs)**

| Faction | Friends |
| --- | --- |
| `hobbit` | `high_elf`, `wood_elf`, `lothlorien`, `rohan`, `gondor` |
| `bree` | `dunedain_north`, `blue_mountains`, `high_elf`, `wood_elf`, `dale`, `durins_folk`, `lothlorien` |
| `dunedain_north` | `wood_elf`, `rohan` |
| `high_elf` | `wood_elf`, `gondor` |
| `gundabad` | `angmar`, `dol_guldur`, `mordor` |
| `dale` | `rohan`, `gondor` |
| `durins_folk` | `dunland` |
| `dunland` | `isengard` |
| `isengard` | `half_troll` |
| `mordor` | `easterling`, `near_harad`, `far_harad` |
| `near_harad` | `far_harad`, `half_troll` |

**Enemy (16 pairs)**

| Faction | Enemies |
| --- | --- |
| `dunedain_north` | `dunland`, `easterling`, `near_harad`, `far_harad` |
| `high_elf` | `easterling`, `near_harad` |
| `wood_elf` | `easterling`, `near_harad` |
| `dale` | `easterling` |
| `lothlorien` | `easterling`, `near_harad` |
| `dunland` | `gondor` |
| `rohan` | `near_harad`, `far_harad` |
| `gondor` | `far_harad` |
| `near_harad` | `harad_jungle` |

**Mortal enemy (85 pairs + Utumno)**

- Every one of the 13 free factions `hobbit`, `bree`, `dunedain_north`, `blue_mountains`,
  `high_elf`, `wood_elf`, `dale`, `durins_folk`, `lothlorien`, `fangorn`, `rohan`, `gondor`,
  `dorwinion` with every one of the 6 orc-side factions `gundabad`, `angmar`, `dol_guldur`,
  `isengard`, `mordor`, `half_troll` (78 pairs).
- Plus 7 pairs: `dunland`–`rohan`, `rohan`–`easterling`, `gondor`–`easterling`,
  `gondor`–`near_harad`, `mordor`–`harad_jungle`, `far_harad`–`harad_jungle`,
  `harad_jungle`–`half_troll`.
- `utumno` with all 24 others (excluded from drain and pledge checks since its alignment is fixed
  and never positive).

Notable consequences: Gundabad has no allies; evil factions are mostly friends, not allies; Dunland
is a friend of Durin's Folk; Fangorn is an ally of the jungle faction.

Faction tags (registry tags on `lotrrevival:faction`): `#lotrrevival:kind/<kind>` (free, men, elf,
dwarf, orc, troll, tree), `#lotrrevival:region/<region>`, `#lotrrevival:hidden`,
`#lotrrevival:isolationist`.

## Edge cases

- **Multiplayer:** alignment is per player and server-authoritative. Each client receives only its
  own full alignment map. Other players' pledged faction (not their values) is sent to players
  tracking them, for nameplates and fellowships. Two kills in the same tick each apply fully.
- **Death:** alignment, pledge, cooldown and warning counter copied to the new player entity
  (`copyOnDeath`). No death penalty.
- **Logout / restart:** stored on the player via the attachment codec. Full resync on login. Online
  timers (drain, pledge cooldown, pledge warning) pause while offline.
- **Dimension change:** the attachment travels with the player; core resends the full snapshot on
  `PlayerChangedDimensionEvent` and `PlayerRespawnEvent` because the client player object is rebuilt.
- **Datapack removes a faction:** saved entries keyed by unknown ids are kept verbatim (keys are
  stored as plain ids, not resolved holders) and ignored until the faction returns. API calls with
  an unknown faction return 0.0 / no-op and log one warning per id per session. A pledge to a missing
  faction reads as no pledge but is not erased.
- **Asymmetric relations in a datapack:** relation(a,b) uses a's entry if present, else b's; if both
  are present and differ, the more hostile one wins and a warning is logged once at datapack load.
- **Rank thresholds out of order in JSON:** sorted by threshold at load; duplicate rank ids reject the
  file (vanilla datapack error, faction missing). A faction with no rank at 100 uses the lowest rank
  at or above 100 as its pledge rank.
- **Rounding:** each per-faction delta is rounded to 0.1 after all multipliers and before being
  applied; a delta that rounds to 0.0 is skipped and fires no event.
- **Clamp reached:** value stays at the bound, `Changed` still fires with the actual (clamped) delta.
- **Pledge auto-break by a command:** a command that sets a mortal enemy above 0 or the pledge
  faction below 100 auto-breaks with reason `below_rank` (penalty and cooldown apply), matching "any
  change that leaves the requirement unmet".
- **Kill that both warns and drops below 100:** kill alignment applies first, so the auto-break
  (`below_rank`) wins and no warning is shown.
- **Interaction with other systems:** NPC targeting calls `FactionRelations.isHostile` and
  `hostilityChance`; Progression reads ranks for titles, hiring prices and quest gating; World reads
  faction keys for structure and spawn tables. None of them write the attachment directly.

## Data and APIs

### 1. Registration layout

- All `DeferredRegister`s live in `core.registry.ModRegistries` (existing: `BLOCKS`, `ITEMS`,
  `ENTITY_TYPES`, `CREATIVE_TABS`). G1 adds: `BLOCK_ENTITY_TYPES`, `MENU_TYPES`, `SOUND_EVENTS`,
  `PARTICLE_TYPES`, `DATA_COMPONENTS`, `ATTACHMENT_TYPES`, `MOB_EFFECTS`, `ARMOR_MATERIALS`,
  `RECIPE_TYPES`, `RECIPE_SERIALIZERS`, `FEATURES`, `STRUCTURE_TYPES`, `STRUCTURE_PIECES`,
  `CHUNK_GENERATORS`, `BIOME_SOURCES`, `POI_TYPES`, `MEMORY_MODULE_TYPES`, `SENSOR_TYPES`,
  `TRIGGER_TYPES`, `LOOT_CONDITION_TYPES`, `LOOT_FUNCTION_TYPES`, `COMMAND_ARGUMENT_TYPES`. Anything
  else: `pod:core` issue.
- Datapack registries are declared in `core.registry.ModDataRegistries` (keys + codecs, registered in
  `DataPackRegistryEvent.NewRegistry` with a network codec so clients receive them). G1 ships only
  `lotrrevival:faction`. Reserved for later, created on request: `lotrrevival:waypoint` (World),
  `lotrrevival:npc_spawn_list` (NPC), `lotrrevival:invasion` (NPC), `lotrrevival:mini_quest`
  (Progression), `lotrrevival:title` (Progression).
- Each pod owns one entry class `<pod>.<Pod>Mod` (`world.WorldMod`, `npc.NpcMod`,
  `progression.ProgressionMod`, `core.CoreMod`) with `public static void init(IEventBus modBus)`.
  `LOTRRevival`'s constructor calls the four in that order after `ModRegistries.register`. Core
  creates the three non-core stubs once in G1 (empty bodies); afterwards each pod owns its file.
  Pods put entries in their own holder classes (for example `world.block.WorldBlocks`) using the
  shared registers, and touch those classes from `init` so they class-load before registration.
- Ids: `LOTRRevival.id("snake_case")`. Items and blocks: `<material>_<thing>` (`mithril_ingot`,
  `gondor_brick_stairs`). Entities: `<faction>_<role>` (`gondor_soldier`).

### 2. Faction datapack registry

Registry key `lotrrevival:faction`. Files at `data/<namespace>/lotrrevival/faction/<id>.json`,
generated by a core datagen provider from the tables in this spec. Sample
`data/lotrrevival/lotrrevival/faction/gondor.json`:

```json
{
  "region": "westlands",
  "color": "#3D6FB5",
  "sort_order": 170,
  "hidden": false,
  "hostility_threshold": 0.0,
  "disapproves_civilian_kills": true,
  "pledge_rank": "r3",
  "ranks": [
    { "id": "r1", "threshold": 10.0 },
    { "id": "r2", "threshold": 50.0 },
    { "id": "r3", "threshold": 100.0 },
    { "id": "r4", "threshold": 200.0 },
    { "id": "r5", "threshold": 500.0 },
    { "id": "r6", "threshold": 1000.0 },
    { "id": "r7", "threshold": 1500.0 },
    { "id": "r8", "threshold": 3000.0 }
  ],
  "relations": {
    "lotrrevival:dunedain_north": "ally",
    "lotrrevival:rohan": "ally",
    "lotrrevival:hobbit": "friend",
    "lotrrevival:high_elf": "friend",
    "lotrrevival:dale": "friend",
    "lotrrevival:dunland": "enemy",
    "lotrrevival:far_harad": "enemy",
    "lotrrevival:gundabad": "mortal_enemy",
    "lotrrevival:angmar": "mortal_enemy",
    "lotrrevival:dol_guldur": "mortal_enemy",
    "lotrrevival:isengard": "mortal_enemy",
    "lotrrevival:mordor": "mortal_enemy",
    "lotrrevival:easterling": "mortal_enemy",
    "lotrrevival:near_harad": "mortal_enemy",
    "lotrrevival:half_troll": "mortal_enemy",
    "lotrrevival:utumno": "mortal_enemy"
  }
}
```

`mordor.json` additionally has `"suspicion": { "ceiling": 100.0, "factor": 5.0 }`; `wood_elf.json`
has `"hostility_threshold": 50.0`.

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `region` | `westlands` / `rhun` / `harad` / `deeps` | required | Enum in core |
| `color` | `#RRGGBB` | required | Map, popups, banners |
| `sort_order` | int | 0 | Ordering in lists |
| `hidden` | bool | false | Excluded from map/alignment UI and `/lotr faction list` without `all` |
| `hostility_threshold` | float | 0.0 | |
| `suspicion` | `{ceiling: float, factor: float}` | absent | Grey zone, see Hostility. `ceiling` > threshold, `factor` > 0 |
| `disapproves_civilian_kills` | bool | false | No kill bonus for civilian victims |
| `fixed_alignment` | float | absent | If present, value is locked |
| `pledge_rank` | rank id | the 100 rank | Absent with `fixed_alignment` means not pledgeable |
| `ranks` | list of `{id, threshold}` | ladder A | ids `[a-z0-9_]+` |
| `relations` | map faction id -> level | `{}` | Missing pairs are `neutral` |

Lang keys (datagen): `faction.lotrrevival.<id>`, `faction.lotrrevival.<id>.rank.<rank_id>`, optional
feminine variant `faction.lotrrevival.<id>.rank.<rank_id>.f`, `region.lotrrevival.<region>`.
Constants: `core.faction.Factions.GONDOR` etc., one `ResourceKey<Faction>` per id above.

### 3. Alignment attachment

`lotrrevival:alignment`, `AttachmentType<AlignmentData>` on players only, serialized by codec,
`copyOnDeath`. Saved shape:

```json
{
  "values": { "lotrrevival:gondor": 152.5, "lotrrevival:mordor": -40.0 },
  "pledge": "lotrrevival:gondor",
  "pledge_warning_ticks": 0,
  "pledge_cooldown_ticks": 0,
  "pledge_cooldown_faction": "lotrrevival:rohan",
  "drain_timer": 320,
  "drain_notice_shown": true
}
```

Timer fields are remaining ticks of online time. Zero values and empty timers are not stored. Pods
never touch the attachment directly; they use the API.

### 4. Alignment API (server side, `core.alignment.Alignment`)

```java
public final class Alignment {
    static float get(Player player, ResourceKey<Faction> faction);           // both sides; client reads synced copy
    static FactionRank rank(Player player, ResourceKey<Faction> faction);
    static boolean meetsRank(Player player, ResourceKey<Faction> faction, String rankId);
    static float add(ServerPlayer player, ResourceKey<Faction> faction, float delta, AlignmentSource source); // gain modifiers applied; returns applied delta
    static void set(ServerPlayer player, ResourceKey<Faction> faction, float value, AlignmentSource source);
    static Map<ResourceKey<Faction>, Float> applyKill(ServerPlayer player, ResourceKey<Faction> victim, float baseValue, boolean civilian, AlignmentSource source); // spread rules
    static Optional<ResourceKey<Faction>> pledge(Player player);
    static PledgeResult tryPledge(ServerPlayer player, ResourceKey<Faction> faction);
    static void breakPledge(ServerPlayer player, PledgeBreakReason reason);
    static void setInfluenceProvider(InfluenceProvider provider);            // reserved; default returns 1.0
}

public record AlignmentSource(ResourceLocation type, Optional<ResourceLocation> detail, Optional<Vec3> pos) {}

@FunctionalInterface
public interface InfluenceProvider {          // reserved for the area-of-influence spec
    float multiplier(ServerPlayer player, ResourceKey<Faction> faction); // 0..1
}
```

`core.faction.FactionRelations`:

```java
static FactionRelation relation(RegistryAccess access, ResourceKey<Faction> a, ResourceKey<Faction> b); // a == b -> ALLY
static Optional<ResourceKey<Faction>> factionOf(Entity entity);  // FactionMember, else entity-type tag fallback
static boolean isHostile(LivingEntity attacker, LivingEntity target); // deterministic part of AI targeting
static float hostilityChance(LivingEntity npc, Player player);   // 1 below threshold, grey-zone chance, else 0
```

`isHostile`: two faction entities -> relation is `enemy` or `mortal_enemy`; faction entity vs player
-> alignment < hostility threshold and player not creative/spectator; `utumno` vs anything -> true;
otherwise false. `hostilityChance` uses the NPC's `FactionMember.suspicion()` override if present,
else the faction's `suspicion`. NPC specs layer hired-unit, provocation and witness rules on top.

### 5. Events (NeoForge game bus, server side only)

| Event | Cancellable | Fields | Typical listener |
| --- | --- | --- | --- |
| `AlignmentEvent.Change` | yes, delta mutable | player, faction, delta (after gain modifiers), source | Special-case adjustments |
| `AlignmentEvent.Changed` | no | player, faction, old, new, source | Achievements |
| `AlignmentEvent.RankChanged` | no | player, faction, oldRank, newRank | Titles |
| `FactionKillEvent` | yes, base value mutable | player, victim entity, victim faction, base value, civilian flag, credited-via-hire flag | Conquest (later), achievements, kill counters |
| `PledgeEvent.Pledge` | yes | player, faction | |
| `PledgeEvent.Warned` | no | player, faction, victim entity | |
| `PledgeEvent.Break` | no | player, faction, reason (`voluntary`, `killed_ally`, `below_rank`, `command`), penalty, cooldown ticks | |

`set` from commands fires `Changed` and `RankChanged` but not `Change`. Hired-unit kills fire
`FactionKillEvent` (hire flag true) but apply no alignment.

### 6. NPC hooks (`core.faction`)

```java
public interface FactionMember {
    ResourceKey<Faction> faction();
    default float alignmentKillValue() { return 1.0f; }      // civilian 1, guard/trader/smith 2, captain 5
    default boolean isCivilian() { return false; }
    default boolean grantsAlignmentOnKill(ServerPlayer killer) { return true; } // self-defence etc.
    default Optional<Suspicion> suspicion() { return Optional.empty(); }       // per-type grey-zone override
}

public record Suspicion(float ceiling, float factor) {}

public interface AlignmentCreditor {          // hired units, tamed mounts that fight
    Optional<UUID> creditedPlayer();
}
```

Entity types that cannot implement `FactionMember` (vanilla, other mods) get a faction from the
entity-type tag `#lotrrevival:faction/<id>` with kill value 1.0, not civilian. An entity type in
more than one such tag logs a warning and uses the first by faction `sort_order`. Core listens to
`LivingDeathEvent` (lowest priority, skipped if canceled) and runs the kill and pledge-warning rules;
NPC code never applies kill alignment itself.

**Note for the NPC pod:** trade, hire and quest minimums are per NPC role (`trade_min`) and owned by
NPC; core only answers hostility. Kill values per role, `isCivilian`, the troll `suspicion`
override (factor 10, ceiling 100) and the self-defence rule are set in NPC specs.

### 7. Networking conventions

- Payloads are Java records implementing `CustomPacketPayload`, named `<Thing>Payload`, in
  `<pod>.network`. Id: `lotrrevival:<pod>/<snake_name>` (for example `lotrrevival:core/alignment_sync`).
- Each pod registers its payloads from its `<Pod>Mod.init` via a listener on
  `RegisterPayloadHandlersEvent`, using `CoreNetwork.registrar(event)` which fixes the protocol
  version string (`"1"`; bumped by Opus 1 on any breaking wire change).
- `StreamCodec`s only, no NBT on the wire. Strings capped (256 chars), collections capped (1,024).
- Handlers run on the main thread (default). Client-to-server handlers validate everything: sender
  alive, in reach (<= 8 blocks of the target entity or block), permission, rate limit (20 per second
  per player per payload, excess dropped and logged at debug).
- Core payloads:

| Id | Direction | Content | Sent when |
| --- | --- | --- | --- |
| `core/alignment_sync` | to client | full value map, pledge, cooldown remaining and faction | login, respawn, dimension change, `/lotr alignment reset` |
| `core/alignment_change` | to client | faction, new value, delta, source type, optional pos | after each applied change |
| `core/pledge_sync` | to trackers and self | player UUID, optional faction | pledge or break; start tracking |
| `core/drain_notice` | to client | number of factions drained | each drain |

Client side lives in `client.core.ClientAlignment` (read-only mirror with `get`, `rank`, `pledge`).

### 8. Tags and shared constants

Format `lotrrevival:<group>/<name>` for families, plain `lotrrevival:<name>` for singletons. Use
`c:` common tags for materials (`c:ingots/mithril`, `c:ores/mithril`, `c:gems/...`). Constants in
`core.tag.ModTags` (nested `Blocks`, `Items`, `EntityTypes`, `Biomes`, `Factions`).

| Registry | Tags core defines in G1 | Owner of members |
| --- | --- | --- |
| entity_type | `faction/<faction_id>` (25), `orcs`, `elves`, `dwarves`, `hobbits`, `men`, `trolls`, `ents`, `mounts` | NPC |
| item | `weapons/daggers`, `weapons/spears`, `weapons/pikes`, `weapons/battleaxes`, `weapons/hammers`, `weapons/throwing`, `armor/<faction_id>`, `poisonable`, `coins` | Core content, NPC |
| block | `crafting_tables`, `ores/<material>` mirrors of `c:` tags, `road_surfaces` | Core content, World |
| biome | `is_middle_earth`, `region/<region>`, `faction_home/<faction_id>` | World |
| faction | `kind/<kind>`, `region/<region>`, `hidden`, `isolationist` | Core |

Other constants in core: dimension key `lotrrevival:middle_earth` (`core.ModDimensions`; World owns
the JSON), alignment source ids above, config spec `core.config.CoreConfig` (file
`lotrrevival-server.toml`, section `[alignment]` with every config key in this spec).

### 9. Commands

Root `/lotr`. Pods add their own literals under it in `RegisterCommandsEvent` (Brigadier merges);
core reserves `alignment`, `faction`, `pledge`. Faction arguments use registry-key suggestions.

| Command | Permission | Effect |
| --- | --- | --- |
| `/lotr alignment get [player] [faction]` | 0 for self, 2 for others | Prints value and rank, or all non-zero values |
| `/lotr alignment set <players> <faction> <value>` | 2 | Sets (clamped, rounded) |
| `/lotr alignment add <players> <faction> <delta> [spread]` | 2 | Adds; with `spread`, runs the kill rules with `delta` as base value |
| `/lotr alignment reset <players> [faction]` | 2 | Zeroes one or all |
| `/lotr pledge set <player> <faction> [force]` | 2 | Pledges; `force` skips requirements and cooldown |
| `/lotr pledge clear <player>` | 2 | Breaks with reason `command` (no penalty, no cooldown) |
| `/lotr pledge cooldown <player> <ticks>` | 2 | Sets remaining cooldown, 0..10,000,000 |
| `/lotr faction list [all]` | 0 | Lists factions by region |
| `/lotr faction relation <a> <b>` | 0 | Prints relation |

Command feedback uses `commands.lotrrevival.<path>` lang keys.

## Out of scope

- **Areas of influence** (per-faction map zones that fade distant kill bonuses to 0) and
  **conquest** (per-cell faction strength fed by pledged kills, hired kills at 25%): a later
  World/Core spec. This spec only reserves `InfluenceProvider` and the hire flag on
  `FactionKillEvent`.
- Bounties (kill records, bounty mini-quests): later Progression spec.
- Pledge benefits beyond the rules above (hire prices, pledge-gated titles and units).
- Alignment UI screen and the per-faction map overlay (separate `specs/core/alignment-ui.md` and
  Progression map GUI); this spec only requires the floating change text, rank chat message, pledge
  warning and drain notice.
- Faction display names, rank ids and names, colours finalization (lore pass).
- Hiring, trading prices, quest content, invasion rules, NPC trade/hire minimums (NPC, Progression).
- Per-world relation overrides by ops.
- Fellowships sharing anything alignment-related.

## Acceptance tests

- [ ] GameTest: all 25 faction files load; relations symmetric; 152 non-Utumno pairs; `utumno` fixed
      at -66,666; `gondor`–`lothlorien` is `neutral`.
- [ ] GameTest: player at 0 everywhere kills a non-civilian `gondor` NPC with b=1.0: gondor,
      dunedain_north, rohan, hobbit, high_elf, dale each -1.0; lothlorien 0.0; dunland, far_harad,
      gundabad, angmar, dol_guldur, isengard, mordor, easterling, near_harad, half_troll each +1.0;
      harad_jungle 0.0; utumno unchanged.
- [ ] GameTest: same kill with `isCivilian` true: easterling and near_harad 0.0 (disapprove),
      mordor +1.0.
- [ ] GameTest: penalty scaling: gondor at +500 costs 10.0 for b=1; at +1,200 costs 20.0; rohan at
      +30 costs 1.0.
- [ ] GameTest: unpledged player at mordor +150 kills a b=2 gondor NPC: mordor +1.0. Pledged to
      mordor: +2.0.
- [ ] GameTest: pledged to gondor with mordor at -0.5, a +2.0 mordor gain leaves mordor at 0.0.
- [ ] GameTest: hired-unit kill changes no alignment and fires `FactionKillEvent` with the hire
      flag; PvP kill changes nothing.
- [ ] GameTest: rank and `RankChanged` fire when crossing 10.0 up and down; wood_elf has no rank at
      10.
- [ ] GameTest: `hostilityChance` for mordor: 1.0 at -1; 0.1 at +2; 0.0 at +100; wood_elf hostile at
      +49.9, not at +50.
- [ ] GameTest: `Change` cancellation leaves value untouched; delta rounding to 0.0 fires nothing.
- [ ] GameTest: alignment, pledge and timers survive death (respawn copy) and End-exit clone.
- [ ] GameTest: pledge blocked while positive with a mortal enemy, not by a plain enemy.
- [ ] GameTest: pledged to gondor at +600: first rohan kill warns and keeps the pledge; a second
      within 24,000 online ticks breaks it (`killed_ally`); after the counter expires a kill only
      warns again.
- [ ] GameTest: voluntary break at gondor +600: gondor becomes 100.0, cooldown 54,000 ticks; at +95
      (auto-break) gondor becomes 50.0; at +40 unchanged.
- [ ] GameTest: drain removes 5.0 from each involved faction (once per faction) after 1,000 ticks;
      never below 0; disabled by config.
- [ ] GameTest: unknown faction id in saved data round-trips unchanged.
- [ ] Playtest: two players on a dedicated server see only their own values; popups, rank messages,
      pledge warning and drain notice appear; values persist across relog and Nether trip.
- [ ] Playtest: `/lotr alignment` and `/lotr pledge` commands work with tab completion.

## Open questions

1. **Range.** Is ±100,000 right, or should we cap lower (±10,000, the original's command range) to
   keep numbers readable?
2. **Woodland Realm hostility.** The reference notes disagree: `factions-alignment.md` says hard
   hostility below +50; `npcs.md` describes a chance-based grey zone above 0 (factor 20). This spec
   uses the hard threshold 50. Switch to threshold 0 plus `suspicion {ceiling 50, factor 20}`?
3. **Region granularity.** `westlands` holds 18 factions. Does the map GUI need sub-regions
   (`eriador`, `rhovanion`, `south`) as a second field?
4. **Payload registration.** AGENTS.md says payloads are "registered in core"; this spec has pods
   register through core's helper from their own `init`. Update AGENTS.md wording if approved.
5. **Pod entry stubs.** Core creates `WorldMod`, `NpcMod`, `ProgressionMod` stubs once in G1. Leads
   to confirm this one-time cross-package edit.
6. **Other players' alignment.** The original shows every player's values to everyone, with a
   per-player hide option. We send only the pledge. Do nameplates or fellowships need values?
7. **Pseudo-factions.** The original has hidden factions for generic hostile monsters, unaligned
   animals, hostile trees (mortal enemy of `hobbit`, `bree`, `dunedain_north`) and Bree ruffians.
   Add them as hidden faction files now, or let the NPC pod request them via `pod:core`?
8. **Command breaks.** Should `/lotr pledge clear` apply the break penalty and cooldown like any
   other break (the original does), with a `nopenalty` flag instead?
9. **Lossoth.** Some versions list a 26th faction (Lossoth, Forodwaith). Out of the 25-faction
   baseline; add later as `lossoth` in `westlands` if wanted.

## Tickets

Filled in by the lead once the spec is approved.
