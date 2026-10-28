# NPC base entity and race framework

- **Pod:** npc
- **Lead:** Opus 3
- **Status:** draft
- **Depends on:** `specs/core/core-apis.md` (faction registry, faction relations, alignment attachment, alignment events, registers, datagen hooks), `specs/world/dimension-and-mapgen.md` (biome ids, structure markers, roads)
- **Sources:** behavior reference notes `docs/reference/legacy/npcs.md`, `docs/reference/legacy/hiring-units.md`, `docs/reference/legacy/combat-trade.md` (our own words about Legacy). Maintainer decision: match Legacy's hostility, friendly-fire, kill-value, healing, spawn-cap and Shire/Bree roster behavior.

## Summary

Every person in Middle-earth (hobbits, men, and later elves, dwarves and orcs) is built on one shared NPC base. The base gives each NPC a race (body, name style, base stats), exactly one faction, a generated name, equipment, a daily routine, speech, and a reaction to each player that comes from that player's alignment with the NPC's faction. Players care because the world feels inhabited and because their alignment visibly matters: a hobbit greets a friend of the Shire, a Bounder attacks a known enemy on sight, and the folk standing nearby turn on whoever kills one of their own. This spec also defines the hooks that trading and hiring plug into later, NPC spawn and despawn rules, and the first two races: hobbits (Shire, Bree) and men (Bree-land).

## Player-facing behavior

1. **Meeting an NPC.** NPCs wander near their home or spawn point. Within 8 blocks they sometimes turn to look at the player. Looking straight at one shows its nameplate: name plus role ("Tomlo Brandybuck, Farmer"), colored green (friendly), white (neutral) or red (hostile) for that viewer.
2. **Talking.** Right-click (or sneak-right-click on a trader) and the NPC says a line in chat to that player only: `<Tomlo Brandybuck> Fine weather for the barley, isn't it?`. The line depends on the player's standing, the time of day and the NPC's role. Example lines in this spec are ours; all speech is written new.
3. **Reactions by alignment.** A player below the faction's hostility threshold (default 0) is attacked on sight by guards and avoided by civilians, who answer only with hostile lines. At 0 or above a player can talk. Trading and hiring need the alignment set by the NPC's role: basic traders 0, smiths +50, the Shirriff +50, captains +100 to +400.
4. **Hitting a friendly NPC.** If a player's alignment with the NPC's faction is above 0, their hits do nothing unless the NPC is already fighting them. The first time this happens the player gets a one-time hint about the friendly-fire toggle, which lets them hit friendly NPCs on purpose. Hits never cost alignment. A hit that goes through angers only the NPC that was hit.
5. **Killing an NPC.** Killing costs alignment (more for guards, traders and captains). Every same-side NPC within 8 blocks that is not already busy attacks the killer.
6. **Faction warfare.** Guards attack NPCs of enemy factions and vanilla hostile monsters that come near. Civilians flee from both.
7. **Day and night.** By day civilians work and wander; at dusk structure-bound NPCs walk home and stay near the door; guards keep patrolling. Talking at night gives night lines ("Lock the gate, it's late.").
8. **Trading and hiring.** A trader role NPC opens a trade screen for a player who meets its `trade_min` (trade system: later spec). Until that spec ships, it says it has nothing to sell today. The Shirriff hires out Bounders at +50 once the hiring spec ships; hiring is defined only as an interface here. Hiring never gives alignment.

## Rules and numbers

All numbers are tuning placeholders unless marked fixed. Ticks: 20 per second. Numbers marked (L) are Legacy's and adopted by maintainer decision.

### Race and faction model

- A **race** is a body and culture: model, hitbox, base attributes, name bank, speech set, default temperament. Races are code-defined in the `npc` package (not a datapack registry), because each needs a model and renderer.
- A **faction** is a political allegiance owned by core (`lotrrevival:hobbit`, `lotrrevival:bree`, ...).
- A race can serve several factions (hobbits serve both `hobbit` and `bree`). **Each NPC has exactly one faction**, fixed by its entity type. It never changes at runtime and is not stored per entity (no editing via NBT).
- An **entity type** = race + faction + combat class (`civilian`, `guard` or `captain`). A civilian type has **roles** (farmer, villager, innkeeper, traveller) stored on the entity and synced to the client. Roles change equipment, routine, title, speech, the trader flag and the role's `trade_min`, not the AI class. Captains fight like guards and are unit traders (hiring).

### G2 entity types

| Entity id | Race | Faction | Class | Max HP | Roles | Spawns | Hireable later |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `lotrrevival:hobbit` | hobbit | hobbit | civilian | 16 (L) | villager, farmer, innkeeper, traveller | wild, structures | no |
| `lotrrevival:hobbit_bounder` | hobbit | hobbit | guard | 20 (L) | - | wild (rare), structures | yes, from the Shirriff at +50 |
| `lotrrevival:hobbit_shirriff` | hobbit | hobbit | captain | 20 (L) | unit trader (Bounders) | structures only | no (hires out Bounders) |
| `lotrrevival:bree_hobbit` | hobbit | bree | civilian | 16 (L) | villager, farmer, innkeeper | structures; wild 1/500 | no |
| `lotrrevival:bree_man` | man | bree | civilian | 20 (L) | villager, farmer, innkeeper, traveller | structures; wild 1/500 | no |
| `lotrrevival:bree_guard` | man | bree | guard | 20 (L) | - | structures; wild 1/500 | yes, from `bree_captain` (later ticket) |

- `hobbit_shirriff` is the Shire's captain, not its guard. The Shire's guard is `hobbit_bounder`.
- Later tickets (not G2): `bree_captain` (25 HP, unit trader for Bree guards at +100), `bree_blacksmith` (25 HP smith), Bree market traders, and Bree-land ruffians. Smiths and captains of all races use 25 HP (L) unless their race is tougher.

Role weights for wild civilian spawns: villager 60, farmer 30, traveller 10. Innkeeper never spawns wild; only structures assign it.

### Race base stats

| Stat | Hobbit | Man | Notes |
| --- | --- | --- | --- |
| Max health | 16 | 20 | Per-type values in the entity table override this (Bounder, Shirriff 20; captains and smiths 25) |
| Hitbox (w x h) | 0.45 x 1.25 | 0.6 x 1.8 | Eye height 1.05 / 1.62 |
| Movement speed attribute | 0.24 | 0.23 | Vanilla zombie is 0.23. Deliberately faster than Legacy's 0.2, which feels sluggish in 1.21 |
| Wander speed multiplier | 0.8 | 0.8 | |
| Flee speed multiplier | 1.5 | 1.3 | |
| Unarmed attack damage | 2 (L) | 2 (L) | Used only when nothing is held |
| Follow / sense range | 16 (L) | 16 (L) | Blocks; vertical capped at 8. Riding a mount x1.5 (later) |
| Knockback resistance | 0 | 0 | Guards and captains 0.1 |
| Base armor | 0 | 0 | Worn armor counts normally |
| Temperament (civilian) | flee | fight back, flee below 30% HP | Guards and captains always fight |
| XP on player kill | 1-3 (L) | 1-3 | Bounders 2-4 (L); other guards and captains 3-5 |

### Shared behavior

| Thing | Value | Notes |
| --- | --- | --- |
| Look at player | within 8 blocks, 2% chance per tick to start, hold 2-4 s | Also turns to face a player who is talking to it |
| Idle wander | every 120 ticks avg, up to 10 blocks | Clamped to home radius when it has a home |
| Swim | yes; avoids water when pathing if possible | |
| Doors | opens and closes wooden doors and fence gates | Never iron doors |
| Healing (L) | **only trader roles (goods or units) and hired units**: 1 HP every 40 ticks while they have no target | All other NPCs never regenerate. Banner-bearer bonus healing is for later |
| Nameplate | shown on crosshair within 8 blocks, color by viewer's standing | Not always-on, so villages aren't a wall of text |
| Picks up items | no | Avoids griefing and despawn exploits |
| Fall damage, fire, drowning | vanilla | On fire: panic-run at 1.5x (L) |
| Melee damage (L) | **75% of the held weapon's attack damage**; unarmed 2 | Plus enchantments. Weapon values come from the combat spec / core items |
| Melee attack cooldown | 20 ticks | Weapon speed factors arrive with the combat spec |
| Ranged attacks | none at G2 | Bounder slings come with the combat spec |

Later (not G2, noted for the combat spec): banner bearers within 16 blocks give allies +0.5 melee damage each and multiply damage taken by (12 - n) / 12, max 5 bearers (L).

### Names

- Each race has name banks per gender: first names and family names. Hobbits: `First Family` ("Hamfast Proudfoot"). Men of Bree: `First Family` with a Bree-style surname ("Barliman Heathertoe" style; banks are our own lists of Tolkien-flavored names, Tolkien names allowed).
- Gender is picked 50/50 at spawn and only selects the name bank and voice lines. One model per race at G2.
- Banks are datapack JSON owned by npc: `data/lotrrevival/lotrrevival/npc_names/<race>_<gender>_first.json` and `<race>_family.json` (lists of strings). Reloadable.
- The name is rolled once at spawn, persisted on the entity, and never re-rolled. A vanilla name tag overrides the displayed name and sets persistence.
- Title shown after the name: the role or class lang key (`Farmer`, `Bounder`, `Shirriff`, `Bree Guard`). Villager role has no title.

### Speech

- Trigger: right-click (main hand, empty or any item that has no use on entities). Cooldown 60 ticks per player per NPC; spam gives nothing.
- Delivery: system chat message to the interacting player only, formatted `<Name> line`. No broadcast.
- Categories: `friendly`, `neutral`, `hostile`, `night`, `flee`, `witness` (seeing an ally killed), `trader_none` (no wares yet), plus role-specific variants (`farmer_friendly`, `innkeeper_friendly`, `shirriff_friendly`, ...).
- Selection order: hostile (player is hostile to this NPC, or the NPC is targeting them) > night (30% chance) > role-specific > `friendly` if `a > 0`, else `neutral`. A trader whose `trade_min` the player does not meet uses `neutral`. Random line within the category.
- Witness lines: when allies turn on a killer (see Kills), up to 5 of them speak a `witness` line to the killer.
- Ambient lines: none at G2 (chat spam). Speech bubbles are an open question.
- Lines are lang keys `npc.lotrrevival.<race>.<category>.<n>` produced by npc pod datagen; 4 or more lines per category per race. All text written new for this repo.

### Hostility and access

`a` = the player's alignment with the NPC's faction (one faction only; allies and enemies affect alignment changes, which core handles, not hostility).

- **Hostile** if `a < hostility_threshold` (L). `hostility_threshold` is a per-faction field in core's faction JSON, default 0. There is no separate friendly band.
- **Grey zone** (L): a faction may set core's optional grey-zone field (an upper alignment bound and a chance divisor `k`). For `0 < a < bound`, each target check targets the player with chance `min(1, 1 / (k x a))`; at or above the bound, never. Legacy values for later factions: Mordor orcs bound +100, k 5; trolls bound +100, k 10; Woodland Realm bound +10, k 20. G2 factions (`hobbit`, `bree`) set none.
- **Not hostile** otherwise. A not-hostile player is ignored by guards and can talk.

| Action | Requirement |
| --- | --- |
| Guards target the player | hostile (or grey-zone roll succeeds), or the player hit this NPC (retaliation) |
| Civilians flee the player | hostile, within 8 blocks; or the player hit this hobbit civilian |
| Talk (non-hostile lines) | not hostile and the NPC is not targeting the player |
| Trade | not hostile, not targeted, and `a >= trade_min` of the role |
| Hire / open unit screen | not hostile, not targeted, and `a >= hire_min` of the unit or unit trader |
| Friendly-fire protection | `a > 0` (see Friendly fire) |

**Per-role minimums (L), owned by npc** as data in `data/<ns>/lotrrevival/npc_roles/<type>.json` (`trade_min`, `hire_min`):

| Role | `trade_min` | `hire_min` |
| --- | --- | --- |
| Market traders, innkeepers/bartenders, farmers | 0 | farmers hire farmhands at 0 (later) |
| Smiths, stablemasters | +50 | - |
| `hobbit_shirriff` | +50 | +50 (Bounder); Bounder with pony +100 (later) |
| Captains (later) | per faction, +100 to +400 | per unit: list base + unit extra (hiring spec) |
| `bree_captain` (later) | +100 | +100 (Bree guard) |

Other trader levels (elf travelling traders +75, dwarf miners and smiths, elf smiths and orc traders +100, Black Uruk captain +400) are filled in when those types land.

- Standing is re-evaluated for the current target and for nearby players every 20 ticks, reading the server-side alignment attachment.
- Nameplate color, computed client-side from the viewer's synced alignment and the synced faction fields: red if `a < hostility_threshold`; white if `a <= 0` or inside a grey zone; green otherwise.
- Creative and spectator players are never targeted and always treated as not hostile for talk; a target that switches to creative is dropped (L).
- On Peaceful difficulty NPCs still spawn and talk but never target players.

### Friendly fire and retaliation (L)

- **Protection:** if the player's alignment with the NPC's faction is above 0 and the NPC is not currently targeting that player, damage from that player (melee, sweep, or the player's projectile) is cancelled entirely. No warning hit, no second-hit rule.
- **Hint:** the first cancelled hit sends that player a one-time chat hint explaining the toggle. Shown once per player, ever.
- **Toggle:** a per-player `allow_friendly_fire` flag (default off) turns protection off for that player. Stored in an npc-owned player attachment with the hint flag. At G2 it is set by command (see Open questions); the options screen comes later.
- At `a <= 0` (or with the toggle on) hits go through.
- **Hits never change alignment.**
- **Retaliation:** an NPC that takes damage from a player or mob targets the attacker (home range ignored for this). Guards, captains and man civilians fight; hobbit civilians flee for 200 ticks. **A plain hit angers only the NPC that was hit**; it does not call allies.
- **Retaliation ends** when the attacker dies, logs out, changes dimension, switches to creative/spectator, or stays beyond sense range for 200 ticks.
- A player never damages their own hired units (later).
- Hits cancelled by another system (for example fellowship PvP prevention) never provoke retaliation; the reaction runs only on damage that was actually applied.
- Accidental NPC-on-NPC hits between same or allied factions (sweeps, misplaced swings) do not cause retaliation.

### Kills

- **Allies turn on the killer (L):** when a player kills an NPC, every NPC within 8 blocks whose faction is the victim's or allied with it, and that has no current target, targets the killer as retaliation. Hobbit civilians flee instead. Up to 5 of them speak a `witness` line. The killer's own hired units are excluded (later). Kills by a hired unit do not trigger this.
- **Alignment:** killing an NPC calls core's kill-alignment API with the NPC's faction and the type's `kill_value`. Core applies the loss (scaled by the player's positive alignment, Legacy: alignment / 50 clamped to x1..x20), gains for enemy factions and area-of-influence rules.

| Type class / role | `kill_value` (L) |
| --- | --- |
| Civilian | 1 |
| Guard (`hobbit_bounder`, `bree_guard`) | 2 |
| Trader or smith role (innkeeper, farmer-trader, smith) | 2 |
| Captain (`hobbit_shirriff`, later captains) | 5 |

A civilian with a trader role (innkeeper) counts as a trader (2). Killing an NPC of an enemy faction gains alignment through the same core call (the NPC pod reports the kill; core decides gain or loss per faction).

### NPC vs NPC faction warfare

- Guards and captains target, within sense range and line of sight: NPCs whose faction is an **enemy** of theirs (core relation), any vanilla `minecraft:monster` category mob except creepers (guards avoid creepers by keeping 4 blocks away and letting them pass), and hostile players.
- Target priority: whoever attacked me (or the ally I am avenging) > hostile player > enemy NPC > monster. Nearest wins ties.
- Civilians never start fights. They flee from enemy NPCs, monsters within 8 blocks, and hostile players.
- NPCs never target NPCs of the same, allied or friend factions, and never target neutral factions unless attacked.
- At G2 `hobbit` and `bree` are allies, so in practice guards fight monsters and hostile players. Faction warfare becomes visible when enemy factions land at G3.

### Day/night routine

| Time (day ticks) | Structure-bound civilians | Wild civilians | Guards | Innkeeper / Shirriff |
| --- | --- | --- | --- | --- |
| 0-11999 (day) | wander within home radius (default 16) | wander freely, travellers walk toward a random point 32-64 blocks away | patrol within home radius (24) or freely | stays within 6 of home |
| 12000-12999 (dusk) | path to home point | keep wandering | patrol | stays within 6 |
| 13000-23999 (night) | stay within 4 of home, indoors if home is indoors | wander within 8 of current spot | patrol | stays within 6 |

- Farmers in daytime prefer standing near farmland within home radius (cosmetic; no crop harvesting at G2).
- If home cannot be reached in 600 ticks the NPC stops trying until the next dusk.

### Equipment

| Type / role | Main hand | Ranged / other | Armor |
| --- | --- | --- | --- |
| Hobbit villager | none | | none |
| Hobbit / Bree farmer | hoe (placeholder `minecraft:wooden_hoe`) | | none |
| Innkeeper | none (later: mug) | | none |
| Traveller | none (later: walking staff) | | leather boots |
| Hobbit Bounder | dagger, iron or bronze (placeholder `minecraft:iron_sword` until core daggers exist) | sling (held when the combat spec adds ranged mode); 1 in 3 rides a Shire pony once the mount spec exists | colored hat (placeholder leather cap) |
| Hobbit Shirriff | as Bounder | sling (later) | distinct hat (placeholder leather cap, different dye) |
| Bree guard | 2 in 3 sword (placeholder `minecraft:iron_sword`), 1 in 3 pike (later; sword until core pikes exist) | | dyed leather chest and helmet |

- Equipment drop chance: 0% at G2 (no free gear farming).
- Placeholders are swapped for core items (daggers, slings, pikes, Bree armor) when core registers them. The loadout table is data in the npc pod (`npc_loadouts/`) so the swap is one change. Until ranged mode and mounts exist, Bounders fight in melee on foot.

### Spawning

**Wild spawns** use an npc pod spawner, not vanilla mob categories (so NPCs don't eat the monster or creature caps and can apply faction rules). Caps and damping follow Legacy (L).

| Thing | Value |
| --- | --- |
| Eligible area | for each player in `lotrrevival:middle_earth`, the 15 x 15 chunk square centred on them; overlapping squares count once |
| Pass interval | every tick (L); config `spawn_interval` N runs it every N ticks and multiplies attempts by N |
| Global cap (L) | 100 NPCs per 196 eligible chunks, scaled by the damping multiplier |
| Multiplayer damping (L) | multiplier `m = max(0.5, 1 / (1 + d x (P - 1)))`, `P` = players in the dimension, `d` default 0.1 (config, per-type overrides later). Never below half |
| Counted | wild NPCs only; structure-bound, hired and trader-respawn NPCs do not count |
| Per chunk attempt | random point in the chunk; 3 group attempts within 5 blocks of it; group size from the spawn entry |
| Surface | monster-spawnable top surface (grass, dirt, path, podzol, sand); sky visible; not in water or lava. Hobbits only on the biome's top block above sea level (L) |
| Light | block light + sky light >= 8 at spawn spot |
| Time | civilians and travellers: day only (0-11999). Guards: any time |
| Distance | >= 24 blocks from every player and from world spawn (L) |
| Inside structures | wild spawner skips columns inside a structure bounding box |
| Banner protection | skips a spot where `core.api.LandProtection.blocksFactionSpawn(level, pos, npcFaction)` is true (an enemy-faction banner covers it) |

Spawn tables are npc pod datapack JSON keyed by biome id: `data/lotrrevival/lotrrevival/npc_spawns/<biome>.json`, each entry `{type, weight, group_min, group_max, roles?, chance?}`. `chance` N (default 1) is an extra 1-in-N roll after the entry is picked (L). G2 tables:

| Biome | Entries (weight, group, chance) |
| --- | --- |
| `lotrrevival:shire` | `hobbit` 40, 1-4; `hobbit_bounder` 1, 1-3 |
| `lotrrevival:bree_land` | `bree_man` 8, 1-3, 1/500; `bree_hobbit` 4, 1-3, 1/500; `bree_guard` 2, 1-2, 1/500 |

Bree folk therefore come almost entirely from Bree structures. `hobbit_shirriff` never spawns wild. Biomes without a table spawn no NPCs. Travellers spawn only within 8 blocks of a block in tag `lotrrevival:road` if the world pod provides it; otherwise anywhere in the biome.

**Structure-bound spawns.** Structures from the world pod place NPC spawn markers (convention: a structure block in DATA mode with metadata `lotrrevival:npc <type> [role=<role>] [count=<n>] [home=<radius>]`). When the structure generates, the npc pod turns each marker into a **spawn anchor** stored in the chunk's attachment and spawns `count` NPCs (default 1) with that home point and radius (default 16).

| Thing | Value |
| --- | --- |
| Anchor check | every 600 ticks while the chunk is loaded |
| Respawn | if fewer than `count` living NPCs belong to the anchor, spawn one |
| Respawn conditions | no player within 16 blocks and none with line of sight; 24000 ticks (1 day) since that NPC died |
| Light/time | ignored for anchors |
| Persistence | anchor NPCs never despawn |

### Despawning

- Wild NPCs follow vanilla monster rules: instant despawn when no player within 128 blocks; after 600 ticks with no player within 32 blocks, 1/800 chance per tick.
- Never despawn: structure-bound NPCs, name-tagged NPCs, NPCs that are hired (later), NPCs with a trade in progress, NPCs currently targeting a player.

### Death drops

| Type | Loot (loot table, datagen) |
| --- | --- |
| Hobbit civilians | 0-1 bread (placeholder food), looting +1 |
| Man civilians | 0-1 bread, looting +1 |
| Guards and captains | 0-1 bread |
| Any, killed by player | 0-2 `lotrrevival:silver_coin` once core registers it (currency placeholder) |

Equipment does not drop. XP only when killed by a player.

### Trading hook

- Trader roles at G2: innkeeper (`trade_min` 0) and the Shirriff's unit screen (`hire_min` +50). Later specs add more.
- Interaction order on right-click: if trader and the player is not hostile, not targeted and `a >= trade_min`: post `NpcInteractEvent(kind = TRADE)` (unit traders: `kind = HIRE`); if nothing handles it, say a `trader_none` line. Otherwise talk.
- Sneak-right-click always talks, so players can hear trader lines.
- Currency: `lotrrevival:silver_coin` (core item). The trade spec owns prices, stock and the screen.

### Hiring hook (interface only)

A hireable NPC exposes: hire requirement (`hire_min` alignment, coin cost), an owner (player UUID or none), a command state (follow, guard position, none), and a flag for whether it currently counts as hired. The base only guarantees: hired NPCs never despawn, never target their owner (even if the owner becomes hostile), are never damaged by their owner, heal as above, still pick other targets by their own faction's rules, and carry owner and state through save/load. **Hiring gives no alignment**, and kills made by hired units earn the owner no alignment (L). Unit lists, costs, desertion and commands belong to the hiring spec. G2 data: the Shirriff offers Bounders at +50.

## Edge cases

- **Multiplayer:** hostility is per player. One NPC can ignore A and target B. Speech goes only to the interacting player. Retaliation is per player. Nameplate color is computed client-side from the viewer's own synced alignment.
- **Standing change mid-fight:** if a target that is not a retaliation target stops being hostile (quest reward, command), the NPC drops it at the next 20-tick check.
- **Friendly player hit while being targeted:** protection does not apply (the NPC is already targeting them), so a friendly player can defend themselves against an NPC that is retaliating.
- **Player at exactly 0 hits an NPC:** damage applies and the NPC retaliates; no alignment change.
- **Kill with the toggle on:** still costs alignment and still turns allies within 8 blocks on the killer.
- **Player death, logout, dimension change:** clears retaliation against that player. NPCs don't follow through portals.
- **NPC through portal:** NPCs can't use portals (avoids faction NPCs leaking into the Overworld).
- **Unloaded chunk with anchor:** no respawn while unloaded; on load the next 600-tick check catches up with at most one spawn per anchor per check.
- **Killing an NPC with no player involved** (lava, monster, another NPC): no alignment change and no ally reaction against a player. Kills by a player's tamed wolf or projectile count as the player's kill.
- **Killing a hostile NPC as a friendly player** (rogue member of your own faction attacking you): still costs alignment; there are no "rogue" members at G2.
- **Spawn cap with many players:** the damping multiplier never drops below 0.5, so a full server still sees half density per area.
- **Name banks missing or empty after a bad datapack:** fall back to race-name-only ("Hobbit") and log one warning.
- **Spawn table refers to an unknown entity id:** skip entry, log one warning.
- **Commands:** `/summon lotrrevival:bree_man` gives a wild NPC with random role (not innkeeper) and a fresh name. Spawn eggs behave the same. A summoned Shirriff has no home anchor and wanders freely.
- **Interaction with other systems:** alignment (core) is read, never written except via the kill API; trading and hiring (progression and npc later) plug in through the event and hireable interface; structures (world) place markers only.

## Data and APIs

**Needed from core (`specs/core/core-apis.md`):**

1. Faction datapack registry key and lookup by id; shared constants for `lotrrevival:hobbit` and `lotrrevival:bree`.
2. Faction relation query between two factions (at least ally, friend, neutral, enemy) with helpers "is allied or friend" and "is enemy".
3. Per-faction `hostility_threshold` (default 0) in core's faction JSON, owned by core, plus core's optional grey-zone field (upper bound and chance divisor). Both synced to clients for nameplate colors. There is no `friendly_at`.
4. Server-side read of a player's alignment with a faction from the alignment attachment.
5. Client-side read of the local player's alignments (synced), for nameplate colors.
6. A kill-alignment API: "player P killed a member of faction F worth V", where core applies the alignment-scaled loss (Legacy: x alignment/50, clamped x1..x20), gains for enemy factions, ally/enemy ripple and area-of-influence rules, and fires the alignment change event.
7. The alignment change event (npc uses it in GameTests and to drop targets early; optional at runtime).
8. Rank lookup by alignment value (optional, for speech flavor later).
9. Access through `ModRegistries` to register entity types, attributes, data attachment types (chunk attachment for spawn anchors, player attachment for friendly-fire prefs), and the npc reload listeners (name banks, spawn tables, loadouts, role minimums).
10. Spawn egg items for the 6 entity types (core owns items) or permission for npc to register them via `ModRegistries.ITEMS`.
11. Item `lotrrevival:silver_coin` (currency placeholder) and later faction weapons (daggers, slings, pikes) and armor.
12. Entity type tag keys in core: `lotrrevival:npcs`, `lotrrevival:hobbits`, `lotrrevival:men`.
13. A datagen hook so npc providers (lang, loot tables, entity tags, spawn egg models) run from `DataGenerators`.
14. The dimension key `lotrrevival:middle_earth`.
15. A shared command root (or permission for npc to add a subcommand) for the friendly-fire toggle.

**Needed from world (`specs/world/dimension-and-mapgen.md`):** biome ids `lotrrevival:shire` and `lotrrevival:bree_land`; the structure marker convention above (or an agreed alternative); Bree village structures with markers (Bree folk rely on them); optional block tag `lotrrevival:road`.

**Owned by npc (this spec):**

- Entity types and attributes listed above; renderers and models under `client/npc/`.
- Synced entity fields: role, gender, name (string), name-roll seed. Persisted: those plus home pos/radius, anchor id, hire data (later).
- Datapack folders: `npc_names/`, `npc_spawns/`, `npc_loadouts/`, `npc_roles/` (per type/role `trade_min`, `hire_min`, `kill_value`) under `data/<ns>/lotrrevival/`.
- Chunk attachment `lotrrevival:npc_spawn_anchors` (list of anchor pos, type, role, count, home radius, NPC UUIDs, last-death times).
- Player attachment `lotrrevival:npc_player_prefs` (`allow_friendly_fire`, `friendly_fire_hint_shown`), persisted across death.
- `NpcInteractEvent` on the NeoForge event bus (kinds: TALK, TRADE, HIRE), cancelable, posted by the npc pod for other pods to handle.
- Hireable interface (fields above).
- No packets at G2 (speech and the hint use chat; nameplate colors use core's synced data).

## Out of scope

- Trade screen, prices, stock, currency exchange (progression trader spec).
- Hiring screens, unit lists and costs, commands, desertion, unit leveling, warhorns (npc hiring spec).
- Mounts (Shire ponies), ranged combat (slings), weapon stat tables, banner bearers (npc combat spec).
- Invasions, mini-quests, Red Book (later specs). Future invasion and NPC block-breaking systems must consult `core.api.LandProtection.blocksInvasion` and `blocksNpcGriefing` before acting on a position.
- Hobbit children, marriage, NPC farming or crop harvesting, NPC sleeping in beds.
- Races other than hobbits and men; Bree captain, smiths and market traders; ruffians, Rangers of the North and bandits.
- Speech bubbles above heads and voiced audio; target-acquire lines and trader adverts.

## Acceptance tests

- [ ] GameTest: a player with `hobbit` alignment -5 near a `hobbit_bounder` is targeted within 40 ticks; with alignment 0 it is not targeted after 100 ticks.
- [ ] GameTest: a hobbit civilian near a player at -5 alignment moves away from the player (distance increases by at least 3 blocks within 60 ticks).
- [ ] GameTest: two players, A at +20 and B at -20 `bree`; a `bree_guard` targets B and never A.
- [ ] GameTest: a player at +20 `bree` hits a `bree_man`: no damage taken, no target set, hint sent once (a second hit sends no second hint).
- [ ] GameTest: same player with `allow_friendly_fire` on hits a `bree_man`: damage applies, the `bree_man` targets the player, a `bree_guard` 6 blocks away does not target the player, and the player's alignment is unchanged.
- [ ] GameTest: a player at 0 `bree` hits a `bree_man`: damage applies and it targets the player.
- [ ] GameTest: retaliation clears after the player stays beyond sense range for 200 ticks.
- [ ] GameTest: a player kills a `bree_man`; an idle `bree_guard` 6 blocks away targets the player, one 12 blocks away does not; a `hobbit` civilian 6 blocks away flees.
- [ ] GameTest: kills report to core's kill-alignment API once with faction and value: `hobbit` 1, `hobbit_bounder` 2, innkeeper 2, `hobbit_shirriff` 5 (verify via the alignment change event).
- [ ] GameTest: a `bree_guard` with an iron sword (6 damage) deals 4.5 to an unarmored target; an unarmed NPC deals 2.
- [ ] GameTest: a damaged innkeeper with no target heals 1 HP after 40 ticks; a damaged `bree_guard` does not heal after 400 ticks.
- [ ] GameTest: right-click on the Shirriff by a player at +50 `hobbit` posts `NpcInteractEvent(HIRE)`; at +49 posts TALK.
- [ ] GameTest: right-click on an innkeeper by a player at 0 posts `NpcInteractEvent(TRADE)`; at -1 posts TALK with a hostile line.
- [ ] GameTest: a `bree_guard` attacks a zombie within 16 blocks; a `bree_man` civilian flees from it.
- [ ] GameTest: a `hobbit_bounder` and a `bree_guard` (allied factions) placed together do not target each other for 200 ticks.
- [ ] GameTest: the wild spawner never exceeds 100 counted NPCs in one player's 196-chunk area, and with 6 players the per-area cap is `100 x max(0.5, 1/1.5)` = 66 (rounded down).
- [ ] GameTest: a structure anchor with `count=2` respawns a killed NPC after the 1-day timer when no player is near, and not before.
- [ ] GameTest: a wild NPC with no player within 128 blocks is removed; an anchor NPC is not.
- [ ] GameTest: name persists across save/load (write NBT, reload, compare name and role).
- [ ] Playtest: walk through the Shire and Bree-land by day and night; NPC density feels lived-in but not crowded (target 3-8 NPCs in view in a village area); Bree-land outside villages is nearly empty of Bree folk.
- [ ] Playtest: names and lines read well and nothing repeats annoyingly in a 10-minute visit.

## Open questions

1. Speech: chat only, or also a short overhead bubble (needs a client packet)? Default chat only.
2. Spawn eggs: core-owned items or npc-registered via `ModRegistries.ITEMS`?
3. Structure marker format: DATA structure blocks (proposed) vs a dedicated invisible marker block from core. Needs world and core sign-off.
4. Equipment drop chance: keep 0%, or adopt Legacy's small per-piece chance (about 1 in 20n, pre-damaged) once faction gear exists.
5. One model per race with gender only in names, or separate female models at G3?
6. Friendly-fire toggle at G2: command under a core-owned root (proposed) or wait for progression's options screen?
7. Structure trader respawn: keep our 1-day timer or adopt Legacy's 10-30 minutes for traders?
8. Grey-zone field shape: core names and owns it; npc needs an upper bound and a chance divisor per faction, and may want per-type overrides (Legacy's trolls differ from Mordor orcs in the same faction).

## Tickets

G2, in dependency order:

1. #36 npc: NPC base entity, race framework and hobbit civilian with spawn egg (sonnet-6)
2. #37 npc: Bounder, Shirriff and Bree types, man race, loadouts and death drops (sonnet-6; blocked by #36)
3. #38 npc: name banks, nameplates, speech and interaction hooks (sonnet-7; blocked by #36)
4. #39 npc: home points, day/night routine and spawn anchors (sonnet-8; blocked by #36)
5. #40 npc: hostility targeting, faction warfare, flee, melee and healing (sonnet-6; blocked by #36, #37)
6. #41 npc: wild NPC spawner, spawn tables and despawning (sonnet-8; blocked by #37)
7. #42 npc: friendly fire, retaliation and kill witnesses (sonnet-7; blocked by #38, #40)
8. #43 npc: G2 later items blocked on world, core and progression (sonnet-8; blocked by #41, #39, #35)

Requested from core: #35 core: silver_coin item and lotrrevival:npcs entity tag key.
