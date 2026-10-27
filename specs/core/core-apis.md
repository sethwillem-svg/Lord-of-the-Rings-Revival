# Core APIs

- **Pod:** core
- **Lead:** Opus 1
- **Status:** draft
- **Depends on:** nothing (this spec unblocks every other pod)

## Summary

The shared foundation every pod builds on: how content is registered, the 25 factions as a
datapack registry with their relations, per-player alignment (the number that decides who attacks
you, who trades with you, who you can hire and which titles you hold), the events and API other pods
call to read and change alignment, network payload conventions, shared tags and constants, and the
`FactionMember` hook that NPCs implement. Players see this as: every faction remembers what you did
to it, killing a faction's enemies raises your standing, killing its people or its allies lowers it,
and ranks unlock as standing grows. Everything here is our own design; numbers are placeholder
defaults that live in server config so they can be tuned without code changes.

## Player-facing behavior

1. A new player starts at **0** alignment with every faction except Utumno (locked, see below).
2. Killing an NPC of faction V lowers alignment with V, lowers it by a smaller amount with V's
   allies and friends, and raises it with V's enemies and mortal enemies. A short floating text
   (`+1.0 Gondor`, `-1.0 Mordor`, one line per faction changed) appears near the hotbar for 3 s.
3. Completing a faction quest or trading with its traders raises alignment with that faction.
4. When alignment crosses a rank threshold up or down, the player gets a chat message naming the
   faction and the new rank, plus a short sound on promotion.
5. NPCs of a faction attack the player on sight while the player's alignment with that faction is
   below the faction's hostility threshold (0 for most; 50 for the Woodland Realm). At or above it
   they are passive and may trade, hire or give quests (rules for those belong to other specs).
6. Holding positive alignment with two factions that are mortal enemies slowly drains both (can be
   turned off in server config).
7. A player may pledge to one faction once they reach its pledge rank and hold no positive
   alignment with any of its mortal enemies. Killing NPCs of the pledged faction or its allies, or
   dropping below the pledge rank, breaks the pledge. Pledge benefits are specified by Progression.
8. Alignment and pledge survive death, logout, dimension change and server restart.
9. Ops can inspect and change alignment with `/lotr alignment ...` (see Commands).

## Rules and numbers

### Alignment values

| Thing | Value | Notes |
| --- | --- | --- |
| Storage type | `float`, rounded to 0.1 on every write | Display always shows one decimal |
| Range | -100,000.0 to +100,000.0 | Clamped on write |
| Starting value | 0.0 | Absent map entries read as 0.0 |
| Utumno | fixed -66,666.0 | Any faction with `fixed_alignment` ignores every change, including commands |
| Hostility | NPC of F is hostile to player if alignment(F) < F.`hostility_threshold` | Default threshold 0.0 |
| Creative / spectator | never targeted, alignment still changes | |

### Rank ladder (default, per faction)

Ranks are listed in each faction JSON. If a faction omits `ranks`, this default applies. Rank ids
are stable (titles, quests and hiring refer to them); display names are lang.

| Rank id | Threshold (>=) | Notes |
| --- | --- | --- |
| `hostile` | below hostility threshold | Implicit, not listed in JSON |
| `neutral` | hostility threshold | Implicit, not listed in JSON |
| `r1` .. `r8` | 10, 50, 100, 200, 500, 1000, 1500, 3000 | Default ladder; factions rename ids in their JSON |
| Pledge rank | the rank named by `pledge_rank`, default the 100 rank | |

Rank of a player = highest listed rank whose threshold <= alignment; else `neutral` or `hostile`.

### Kill rules

Victim faction V, base value `b` from the victim's `FactionMember.alignmentKillValue()` (default
1.0; NPC specs set e.g. 2.0 for soldiers, 5.0 to 25.0 for captains and lords). `m` is server config
`alignment.kill_multiplier` (default 1.0).

| Target faction X | Change to alignment(X) | Config key, default |
| --- | --- | --- |
| X = V | -b·m | - |
| relation(V,X) = `ally` | -b·m·0.5 | `alignment.ally_share` 0.5 |
| relation(V,X) = `friend` | -b·m·0.25 | `alignment.friend_share` 0.25 |
| relation(V,X) = `neutral` | 0 | - |
| relation(V,X) = `enemy` | +b·m·1.0 | `alignment.enemy_share` 1.0 |
| relation(V,X) = `mortal_enemy` | +b·m·1.0 | `alignment.mortal_enemy_share` 1.0 |

- A kill counts when the killing damage source's entity is a player, a projectile owned by a
  player, or an entity implementing `AlignmentCreditor` that names an online player (hired units);
  the latter are scaled by `alignment.hired_kill_share` (default 0.5).
- No change if the victim's `grantsAlignmentOnKill()` is false (summoned, already-converted,
  spawned by a spawn egg in creative), or if the victim has no faction.
- PvP kills never change alignment.
- Factions with `fixed_alignment` never receive changes.

### Other sources (defaults; the owning pod applies them through the API)

| Source id | Default | Owner |
| --- | --- | --- |
| `lotrrevival:kill` | table above | Core (automatic) |
| `lotrrevival:trade` | +0.1 per 10 coin value, cap +5.0 per trader faction per in-game day | Progression |
| `lotrrevival:quest` | +10.0 per mini-quest (quest spec may set 5 to 50) | Progression |
| `lotrrevival:hire` | 0 (hiring costs coins, not alignment) | NPC |
| `lotrrevival:drain` | see below | Core (automatic) |
| `lotrrevival:command` | any | Core |

### Mortal-enemy drain

Every 6,000 ticks of a player's online time (`alignment.drain_interval_ticks`), for each pair of
factions that are `mortal_enemy` and where the player holds > 0 with both, each of the two loses
`max(1.0, 1% of its current value)`. Never drains below 0. Toggle `alignment.mortal_enemy_drain`
(default true). Chat warning the first time per session a drain applies.

### Pledge

| Thing | Value |
| --- | --- |
| Requirement | alignment >= pledge rank threshold, and alignment <= 0 with every mortal enemy |
| Auto-break | player kills an NPC of the pledged faction or its `ally`; or alignment drops below pledge threshold |
| Voluntary break | allowed any time (UI belongs to Progression) |
| Cooldown after any break | 24,000 ticks of game time before a new pledge (`alignment.pledge_cooldown_ticks`) |
| Break penalty | none to alignment in this spec; Progression may add one via the API |

## Factions

25 factions in four regions. Ids are `lotrrevival:<id>`. Display names and rank names are a
lore-pass task and are not fixed here. Ids are ours; where the original mod used invented names we
use descriptive ones instead.

| Region | Faction ids |
| --- | --- |
| `westlands` | `hobbit`, `bree`, `dunedain_north`, `blue_mountains`, `high_elf`, `gundabad`, `angmar`, `wood_elf`, `dol_guldur`, `dale`, `durins_folk`, `lothlorien`, `dunland`, `isengard`, `fangorn`, `rohan`, `gondor`, `mordor` |
| `rhun` | `dorwinion`, `easterling` |
| `harad` | `near_harad`, `far_harad`, `harad_jungle`, `half_troll` |
| `deeps` | `utumno` (hidden from map and alignment lists, `fixed_alignment` -66,666) |

### Relations

Five levels, symmetric: `ally`, `friend`, `neutral`, `enemy`, `mortal_enemy`.

| Level | NPCs fight each other | Kill spread | Other effect |
| --- | --- | --- | --- |
| `ally` | never | penalty 0.5 | killing one breaks a pledge to the other |
| `friend` | never | penalty 0.25 | |
| `neutral` | no (only after friendly fire) | none | |
| `enemy` | on sight | bonus 1.0 | |
| `mortal_enemy` | on sight | bonus 1.0 | blocks pledge, causes drain |

The datagen provider expands these rules into explicit `relations` in every faction file (runtime
only reads explicit entries; a missing pair is `neutral`):

- **Blocs.** Free peoples: `hobbit`, `bree`, `dunedain_north`, `blue_mountains`, `high_elf`,
  `wood_elf`, `dale`, `durins_folk`, `lothlorien`, `fangorn`, `rohan`, `gondor`, `dorwinion`.
  Shadow: `gundabad`, `angmar`, `dol_guldur`, `dunland`, `isengard`, `mordor`, `easterling`,
  `near_harad`, `far_harad`, `half_troll`. Unaligned: `harad_jungle`, `utumno`.
- Within a bloc: `ally`. Across Free peoples and Shadow: `enemy`.
- Within Free peoples, `friend` instead of `ally`: `fangorn` with everyone; `dorwinion` with everyone
  except `dale`; `wood_elf` with `durins_folk` and `blue_mountains`.
- Within Shadow, `friend` instead of `ally`: `isengard`–`mordor`; each of `easterling`,
  `near_harad`, `far_harad`, `half_troll` with each of `gundabad`, `angmar`, `dol_guldur`,
  `dunland`, `isengard`.
- `mortal_enemy` pairs: `gondor`–`mordor`, `rohan`–`isengard`, `rohan`–`dunland`,
  `wood_elf`–`dol_guldur`, `lothlorien`–`dol_guldur`, `durins_folk`–`gundabad`,
  `dunedain_north`–`angmar`, `high_elf`–`angmar`, `gondor`–`near_harad`, `dale`–`easterling`.
- `harad_jungle`: `enemy` with `near_harad`, `far_harad`, `half_troll`; `neutral` with all others.
- `utumno`: `mortal_enemy` with all 24 others (excluded from drain and pledge checks since its
  alignment is fixed and never positive).
- `wood_elf` has `hostility_threshold` 50.0.

Faction tags (registry tags on `lotrrevival:faction`): `#lotrrevival:free_peoples`,
`#lotrrevival:shadow`, `#lotrrevival:region/<region>`, `#lotrrevival:hidden`.

## Edge cases

- **Multiplayer:** alignment is per player and server-authoritative. Each client receives only its
  own full alignment map. Other players' pledged faction (not their values) is sent to players
  tracking them, for nameplates and fellowships. Two kills in the same tick each apply fully.
- **Death:** alignment and pledge copied to the new player entity (`copyOnDeath`). No death penalty.
- **Logout / restart:** stored on the player via the attachment codec. Full resync on login.
- **Dimension change:** the attachment travels with the player; core resends the full snapshot on
  `PlayerChangedDimensionEvent` and `PlayerRespawnEvent` because the client player object is rebuilt.
- **Datapack removes a faction:** saved entries keyed by unknown ids are kept verbatim (keys are
  stored as plain ids, not resolved holders) and ignored until the faction returns. API calls with
  an unknown faction return 0.0 / no-op and log one warning per id per session. A pledge to a missing
  faction reads as no pledge but is not erased.
- **Asymmetric relations in a datapack:** relation(a,b) uses a's entry if present, else b's; if both
  are present and differ, the more hostile one wins and a warning is logged once at datapack load.
- **Rank thresholds out of order in JSON:** sorted by threshold at load; duplicate rank ids reject the
  file (vanilla datapack error, faction missing).
- **Rounding:** each per-faction delta is rounded to 0.1 before being applied; a delta that rounds to
  0.0 is skipped and fires no event.
- **Clamp reached:** value stays at the bound, `Changed` still fires with the actual (clamped) delta.
- **Interaction with other systems:** NPC targeting calls `FactionRelations.isHostile`; Progression
  reads ranks for titles, hiring prices and quest gating; World reads faction keys for structure and
  spawn tables. None of them write the attachment directly.

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
generated by a core datagen provider. Sample `data/lotrrevival/lotrrevival/faction/gondor.json`:

```json
{
  "region": "westlands",
  "color": "#3D6FB5",
  "sort_order": 170,
  "hidden": false,
  "hostility_threshold": 0.0,
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
    "lotrrevival:rohan": "ally",
    "lotrrevival:fangorn": "friend",
    "lotrrevival:isengard": "enemy",
    "lotrrevival:mordor": "mortal_enemy",
    "lotrrevival:near_harad": "mortal_enemy"
  }
}
```

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `region` | `westlands` / `rhun` / `harad` / `deeps` | required | Enum in core |
| `color` | `#RRGGBB` | required | Map, popups, banners |
| `sort_order` | int | 0 | Ordering in lists |
| `hidden` | bool | false | Excluded from map/alignment UI and `/lotr faction list` without `all` |
| `hostility_threshold` | float | 0.0 | |
| `fixed_alignment` | float | absent | If present, value is locked |
| `pledge_rank` | rank id | the 100 rank | Absent with `fixed_alignment` means not pledgeable |
| `ranks` | list of `{id, threshold}` | default ladder | ids `[a-z0-9_]+` |
| `relations` | map faction id -> level | `{}` | Missing pairs are `neutral` |

Lang keys (datagen): `faction.lotrrevival.<id>`, `faction.lotrrevival.<id>.rank.<rank_id>`,
`region.lotrrevival.<region>`. Constants: `core.faction.Factions.GONDOR` etc., one
`ResourceKey<Faction>` per id above.

### 3. Alignment attachment

`lotrrevival:alignment`, `AttachmentType<AlignmentData>` on players only, serialized by codec,
`copyOnDeath`. Saved shape:

```json
{
  "values": { "lotrrevival:gondor": 152.5, "lotrrevival:mordor": -40.0 },
  "pledge": "lotrrevival:gondor",
  "pledge_cooldown_until": 1284000,
  "drain_timer": 3120
}
```

Zero values are not stored. Pods never touch the attachment directly; they use the API.

### 4. Alignment API (server side, `core.alignment.Alignment`)

```java
public final class Alignment {
    static float get(Player player, ResourceKey<Faction> faction);           // both sides; client reads synced copy
    static FactionRank rank(Player player, ResourceKey<Faction> faction);
    static boolean meetsRank(Player player, ResourceKey<Faction> faction, String rankId);
    static float add(ServerPlayer player, ResourceKey<Faction> faction, float delta, AlignmentSource source); // returns applied delta
    static void set(ServerPlayer player, ResourceKey<Faction> faction, float value, AlignmentSource source);
    static Map<ResourceKey<Faction>, Float> applyKill(ServerPlayer player, ResourceKey<Faction> victim, float baseValue, AlignmentSource source); // spread rules
    static Optional<ResourceKey<Faction>> pledge(Player player);
    static PledgeResult tryPledge(ServerPlayer player, ResourceKey<Faction> faction);
    static void breakPledge(ServerPlayer player, PledgeBreakReason reason);
}

public record AlignmentSource(ResourceLocation type, Optional<ResourceLocation> detail, Optional<Vec3> pos) {}
```

`core.faction.FactionRelations`:

```java
static FactionRelation relation(RegistryAccess access, ResourceKey<Faction> a, ResourceKey<Faction> b); // a == b -> ALLY
static Optional<ResourceKey<Faction>> factionOf(Entity entity);  // FactionMember, else entity-type tag fallback
static boolean isHostile(LivingEntity attacker, LivingEntity target); // AI targeting entry point
```

`isHostile`: two faction entities -> relation is `enemy` or `mortal_enemy`; faction entity vs player
-> alignment < hostility threshold and player not creative/spectator; `utumno` vs anything -> true;
otherwise false. NPC specs layer hired-unit and provocation rules on top.

### 5. Events (NeoForge game bus, server side only)

| Event | Cancellable | Fields | Typical listener |
| --- | --- | --- | --- |
| `AlignmentEvent.Change` | yes, delta mutable | player, faction, delta, source | Progression pledge bonuses |
| `AlignmentEvent.Changed` | no | player, faction, old, new, source | Achievements |
| `AlignmentEvent.RankChanged` | no | player, faction, oldRank, newRank | Titles |
| `FactionKillEvent` | yes, base value mutable | player, victim entity, victim faction, base value, credited-via-hire flag | Conquest, achievements |
| `PledgeEvent.Pledge` | yes | player, faction | |
| `PledgeEvent.Break` | no | player, faction, reason (`voluntary`, `killed_ally`, `below_rank`, `command`) | |

`set` from commands fires `Changed` and `RankChanged` but not `Change`.

### 6. NPC hooks (`core.faction`)

```java
public interface FactionMember {
    ResourceKey<Faction> faction();
    default float alignmentKillValue() { return 1.0f; }
    default boolean grantsAlignmentOnKill() { return true; }
}

public interface AlignmentCreditor {          // hired units, tamed mounts that fight
    Optional<UUID> creditedPlayer();
}
```

Entity types that cannot implement `FactionMember` (vanilla, other mods) get a faction from the
entity-type tag `#lotrrevival:faction/<id>` with kill value 1.0. An entity type in more than one such
tag logs a warning and uses the first by faction `sort_order`. Core listens to `LivingDeathEvent`
(lowest priority, skipped if canceled) and runs the kill rules; NPC code never applies kill
alignment itself.

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
| `core/alignment_sync` | to client | full value map, pledge, cooldown end | login, respawn, dimension change, `/lotr alignment reset` |
| `core/alignment_change` | to client | faction, new value, delta, source type, optional pos | after each applied change |
| `core/pledge_sync` | to trackers and self | player UUID, optional faction | pledge or break; start tracking |

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
| faction | `free_peoples`, `shadow`, `region/<region>`, `hidden` | Core |

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
| `/lotr pledge set <player> <faction> [force]` | 2 | Pledges; `force` skips requirements |
| `/lotr pledge clear <player>` | 2 | Breaks with reason `command` |
| `/lotr faction list [all]` | 0 | Lists factions by region |
| `/lotr faction relation <a> <b>` | 0 | Prints relation |

Command feedback uses `commands.lotrrevival.<path>` lang keys.

## Out of scope

- Pledge benefits, conquest and map control zones (Progression).
- Alignment UI screen and the per-faction map overlay (separate `specs/core/alignment-ui.md` and
  Progression map GUI); this spec only requires the floating change text and rank chat message.
- Faction display names, rank names, colours finalization (lore pass).
- Hiring, trading prices, quest content, invasion rules (NPC, Progression).
- Fellowships sharing anything alignment-related.

## Acceptance tests

- [ ] GameTest: all 25 faction files load; relations symmetric; `utumno` fixed at -66,666.
- [ ] GameTest: killing a `gondor` NPC with b=1.0 gives gondor -1.0, rohan -0.5, fangorn -0.25,
      mordor +1.0, harad_jungle 0.0.
- [ ] GameTest: hired-unit kill credits owner at 0.5x; PvP kill changes nothing.
- [ ] GameTest: rank and `RankChanged` fire when crossing 10.0 up and down.
- [ ] GameTest: `Change` cancellation leaves value untouched; delta rounding to 0.0 fires nothing.
- [ ] GameTest: alignment survives death (respawn copy) and End-exit clone.
- [ ] GameTest: pledge blocked while positive with a mortal enemy; auto-breaks on killing an ally.
- [ ] GameTest: drain reduces both mortal-enemy values after one interval; disabled by config.
- [ ] GameTest: unknown faction id in saved data round-trips unchanged.
- [ ] Playtest: two players on a dedicated server see only their own values; popups and rank
      messages appear; values persist across relog and Nether trip.
- [ ] Playtest: `/lotr alignment` commands work with tab completion.

## Open questions

1. **Range.** Is ±100,000 right, or should we cap lower (±10,000) to keep numbers readable?
2. **Hired-unit kill share.** 0.5x is a guess; full credit makes hired armies the best farm.
3. **Enemy bonus to many factions.** One Gondor kill raises all ten Shadow factions. Should the
   bonus go only to factions where the player already has >= 0, or only to the victim's mortal
   enemies plus the pledged faction?
4. **Drain tuning.** 1% every 5 minutes online; should it be faster, or only while pledged?
5. **Region granularity.** `westlands` holds 18 factions. Does the map GUI need sub-regions
   (`eriador`, `rhovanion`, `south`) as a second field?
6. **Payload registration.** AGENTS.md says payloads are "registered in core"; this spec has pods
   register through core's helper from their own `init`. Update AGENTS.md wording if approved.
7. **Pod entry stubs.** Core creates `WorldMod`, `NpcMod`, `ProgressionMod` stubs once in G1. Leads
   to confirm this one-time cross-package edit.
8. **Other players' alignment.** Do nameplates need alignment values (not just pledge) of others?
9. **Lossoth.** Some versions list a 26th faction (Lossoth, Forodwaith). Out of the 25-faction
   baseline; add later as `lossoth` in `westlands` if wanted.

## Tickets

Filled in by the lead once the spec is approved.
