# Progression data model

- **Pod:** progression
- **Lead:** Opus 4
- **Status:** draft
- **Depends on:** `specs/core/core-apis.md` (factions registry, alignment attachment and ranks, alignment change event, payload conventions, register homes), `specs/npc/npc-base.md` (quest-giver NPCs, NPC faction lookup; reference only)

## Summary

This spec fixes the data shapes every Progression system builds on: achievements (vanilla advancements in our own tabs), titles, per-player mini-quest state, and the data the Middle-earth menu/map screen reads. It decides what is stored where, what is synced to which client and when, and what survives death, dimension change, logout and restart. It does not define quest content, the menu's look, banners, fellowships or traders; those get their own specs and must use the shapes here.

Players care because this is what makes their journey persistent: the regions they have walked, the factions that honour them, the title shown above their head and the errands they are running for an NPC all have to be there tomorrow, after a death, on a server with friends.

## Player-facing behavior

1. **Achievements.** Pressing the advancements key (default `L`) shows our tabs next to vanilla's: one tab per region of Middle-earth plus a few category tabs (general, combat, crafting, travel). Entering a region, reaching an alignment rank, killing an enemy faction's NPCs, trading, crafting at a faction table and walking long distances unlock entries. An unlock shows the vanilla toast and, for most entries, a chat line visible to other players (vanilla `announce_to_chat`).
2. **Titles.** Some achievements and every alignment rank grant a title. A few titles are special (granted by an operator command or a server event, e.g. a playtester title). The player picks one title, or none, on the Titles page of the Middle-earth menu. The selected title appears:
   - as a chat prefix: `[Title] <Name> message`;
   - on the nameplate above the player's head and in the tab list.
   Alignment-rank titles are usable only while the player still holds that rank. If alignment drops below it, the title is unselected and the player gets a chat notice.
3. **Mini-quests.** An NPC quest-giver (NPC pod) offers a short task: bring 12 items, kill 8 enemies of a faction, deliver a thing to someone. The player accepts it, sees it in the quest log, does it, returns to the giver and gets the reward (alignment, coins, items). Quests can fail: the giver dies, the time limit runs out, or the player abandons it. Number of active quests is capped.
4. **Middle-earth menu.** One key (default `M`, rebindable) opens a tabbed screen: Map, Factions (alignment per faction, rank, progress to next rank), Titles, Quests (active and recently completed), and later Waypoints and Fellowships. Data is already on the client when it opens; no loading spinner for anything defined here.

## Rules and numbers

| Thing | Value | Notes |
| --- | --- | --- |
| Advancement tabs (initial) | 14 | 10 region + 4 category tabs; see "Tab layout" |
| Advancements target | ~1,000 by G4 | All datagen; zero hand-written JSON |
| Advancement id scheme | `lotrrevival:<tab>/<name>` | `snake_case`; tab root is `lotrrevival:<tab>/root` |
| Max active mini-quests per player | 5 | Server config `progression.maxActiveQuests`, 1-20 |
| Max active quests from one giver | 1 | Giver offers nothing else until it is resolved |
| Default quest time limit | none | Per-definition `time_limit_ticks`; 0 = no limit |
| Quest offer cooldown per giver per player | 6000 ticks (5 min) | After complete, fail or abandon |
| Completed-quest log kept per player | last 50 records | Older ones fold into counters only |
| Title id scheme | `lotrrevival:<source>/<name>` | e.g. `lotrrevival:rank/shire_friend`, `lotrrevival:achievement/long_walker` |
| Title text max length | 32 chars (rendered) | Enforced by datagen test |
| Chat prefix colour | title's `color`, default gray | From title definition |
| Travel distance check interval | every 20 ticks | Counts horizontal blocks; teleports > 64 blocks in one tick are ignored |
| Biome check interval | every 20 ticks | Fires `enter_biome` only on change |
| Login sync payload soft cap | 32 KiB per payload | Split into several payloads above that; vanilla hard cap is 1 MiB |
| Delta flush | end of server tick | Batches all changes for a player into one payload per type |

### Tab layout

Each tab is a root advancement with a background texture (new art) and an icon. Tabs are generated from one table in datagen, so adding a tab is one line.

| Tab id | Covers |
| --- | --- |
| `general` | First entry to Middle-earth, the menu, first title, first quest |
| `shire` | The Shire and its surroundings |
| `eriador` | Bree-land, the Lone-lands, Eregion, northern ruins |
| `misty_mountains` | Misty Mountains, goblin holds, the high passes |
| `rhovanion` | Mirkwood, Dale, Erebor, the Iron Hills, Anduin vales |
| `lothlorien` | The Golden Wood and its borders |
| `rohan` | Rohan, Fangorn, Isengard |
| `gondor` | Gondor and its fiefs, Ithilien |
| `mordor` | Mordor and its approaches |
| `south_east` | Harad, Umbar, Rhun, Far Harad |
| `combat` | Faction-kill milestones, bosses, invasions |
| `crafting` | Faction crafting tables, forging, brewing |
| `trade` | Traders, coins, hiring milestones |
| `travel` | Distance milestones, roads, waypoints, fast travel |

Region membership of biomes comes from World pod biome tags (`#lotrrevival:region/<tab>`); progression only reads the tags.

### Achievement generation

- One `AdvancementSubProvider` per tab under `progression/datagen/advancement/`, registered through Core's `DataGenerators` (shared-file lock; ask Opus 1).
- Most entries are table-driven, not hand-coded. Generators take rows like `(tab, name, parent, icon, frame, trigger, title_grant?)`. Families that are pure data are generated in loops:
  - every biome tagged `#lotrrevival:visit_achievement` gets an `enter_biome` entry in its region tab;
  - every faction × each "milestone" rank (Core rank list) gets an `alignment_rank` entry;
  - every faction × kill tiers (1, 50, 250, 1000) gets a `kill_faction_npc` entry;
  - every faction crafting table gets a `craft_at_table` entry;
  - travel tiers (1k, 10k, 50k, 100k, 500k blocks).
- Lang keys: `advancements.lotrrevival.<tab>.<name>.title` and `.description`, generated by our lang sub-provider. All text is our own writing.
- Rewards: vanilla `rewards` (xp) is allowed. Title grants are not vanilla rewards; they are a datapack map (see Titles), so modpacks can retarget them without touching advancements.
- A datagen-time check fails the build if: an id breaks the naming scheme, a parent is missing, two entries share a lang key, or a referenced title id does not exist.

### Custom criteria triggers

All under `lotrrevival:` and registered in vanilla's trigger type registry. Each is a standard trigger whose instance has an optional `player` predicate plus the fields below. All fields are optional unless marked; omitted means "any".

| Trigger | Fired when | Conditions |
| --- | --- | --- |
| `enter_biome` | Player's biome changes (20-tick poll) | `biome` (id or `#tag`), `dimension` |
| `alignment_rank` | Core alignment change event, after the change | `faction` (required), `rank` (rank id, matches if current rank >= it), `min_alignment` (number) |
| `kill_faction_npc` | Player kills an NPC with a faction | `faction` or `faction_tag`, `entity` (vanilla entity predicate), `min_total` (cumulative kills of that faction, from the counter attachment) |
| `trade` | A trade with a faction trader completes | `faction`, `trader` (entity type or tag), `direction` (`buy`, `sell`, `any`), `item` (item predicate), `min_value` (coins) |
| `craft_at_table` | Player takes a result from a faction crafting table | `table` (block id or tag), `faction`, `item` (item predicate) |
| `travel_distance` | Every 20 ticks after the counter changes | `min_blocks` (required), `mode` (`any`, `foot`, `mounted`, `boat`), `dimension` |
| `complete_mini_quest` | A quest completes | `faction`, `quest` (definition id or tag), `min_total` |
| `earn_title` | A title is unlocked | `title` (id), `count` (min distinct titles owned) |

The `trade` and `craft_at_table` triggers are fired from code owned by the trader ticket (this pod) and the crafting table owner (core). Core fires `craft_at_table` through a Progression-owned static hook, or posts a NeoForge event we listen to; see Data and APIs.

Sample advancement (datagen output, shown as JSON for review):

```json
{
  "parent": "lotrrevival:combat/root",
  "display": {
    "icon": { "id": "minecraft:iron_sword" },
    "title": { "translate": "advancements.lotrrevival.combat.orc_bane_50.title" },
    "description": { "translate": "advancements.lotrrevival.combat.orc_bane_50.description" },
    "frame": "goal",
    "announce_to_chat": true
  },
  "criteria": {
    "kills": {
      "trigger": "lotrrevival:kill_faction_npc",
      "conditions": {
        "faction_tag": "#lotrrevival:orcish",
        "min_total": 50
      }
    }
  },
  "rewards": { "experience": 50 }
}
```

## Titles

**Definition** (datapack registry `lotrrevival:title`, file `data/<ns>/lotrrevival/title/<path>.json`):

```json
{
  "text": { "translate": "title.lotrrevival.rank.shire_friend" },
  "color": "#7FB24A",
  "source": { "type": "alignment_rank", "faction": "lotrrevival:hobbits", "rank": "lotrrevival:hobbits/friend" },
  "hidden_until_earned": false
}
```

`source.type` is one of:

- `advancement`: `{"advancement": "<id>"}`. Unlocked permanently when the advancement is done; revoked if the advancement is revoked by command.
- `alignment_rank`: `{"faction", "rank"}`. Not stored as unlocked; available while Core reports the player's rank >= `rank`.
- `special`: no condition. Granted and revoked only by command (`/lotrtitle grant|revoke <player> <title>`, permission level 2) or by code (fellowship founder, event winner).

**Per-player attachment** `lotrrevival:titles` (serialized, copied on death):

| Field | Type | Notes |
| --- | --- | --- |
| `unlocked` | set of title ids | `advancement` and `special` titles only |
| `selected` | optional title id | None = no title shown |
| `selected_since` | long (game time) | For UI sorting only |

On login and on every alignment change, the server re-checks `selected`: if it is a rank title the player no longer qualifies for, or a title id that no longer exists in the registry (datapack removed), it is cleared and the player gets a notice. Unknown ids in `unlocked` are kept on disk (a datapack may come back) but ignored.

**Display.** Chat prefix is added server-side when formatting chat. Nameplate and tab list use the player's display name, which every client computes locally, so each client must know other players' selected titles: the selected title (id only; text and colour come from the synced registry) is sent to the owner and to every player tracking them, and the display name is refreshed on change.

## Mini-quest data model

The content system (quest kinds, giver behaviour, dialogue, reward tables) is a later spec. This section fixes the storage and lifecycle only.

**Definition** (datapack registry `lotrrevival:mini_quest`). Objectives and rewards are type-dispatched codecs so later specs add types without changing the store.

```json
{
  "faction": "lotrrevival:hobbits",
  "givers": "#lotrrevival:quest_givers/hobbit",
  "weight": 10,
  "min_alignment": 0,
  "repeatable": true,
  "time_limit_ticks": 0,
  "objective": {
    "type": "lotrrevival:collect",
    "item": { "items": "minecraft:wheat" },
    "count": { "min": 8, "max": 16 }
  },
  "rewards": [
    { "type": "lotrrevival:alignment", "faction": "lotrrevival:hobbits", "amount": { "per_unit": 0.5, "min": 5 } },
    { "type": "lotrrevival:coins", "amount": { "per_unit": 2 } }
  ],
  "fail_on_giver_death": true
}
```

Rolled values (`count`, scaled rewards) are fixed when the quest is offered and stored in the instance, so a datapack change never alters a quest in progress.

**Per-player attachment** `lotrrevival:quests` (serialized, copied on death):

| Field | Type | Notes |
| --- | --- | --- |
| `active` | list of `QuestInstance`, max `maxActiveQuests` | Order = acceptance order |
| `log` | ring buffer of `QuestRecord`, max 50 | Completed, failed, abandoned |
| `completed_by_faction` | map faction id -> int | Never trimmed |
| `failed_total` | int | |
| `giver_cooldowns` | map giver UUID -> game time | Entries older than the cooldown are dropped on save |

`QuestInstance`: `uuid`, `definition` (id), `giver` (entity UUID), `giver_name` (component snapshot), `giver_faction`, `giver_pos` (last known `GlobalPos`), `accepted_at` (game time), `expires_at` (game time or -1), `rolled` (objective target values, rewards), `progress` (int or small map, objective-specific), `state` (`active`, `ready_to_turn_in`).

`QuestRecord`: `definition`, `giver_name`, `faction`, `outcome` (`completed`, `failed_giver_died`, `failed_expired`, `abandoned`, `failed_invalid`), `at` (game time).

**Lifecycle hooks** (Progression-owned, posted on the NeoForge game bus so other pods can react): `QuestOfferEvent` (cancelable; lets NPC pod veto or filter), `QuestAcceptedEvent`, `QuestProgressEvent`, `QuestCompletedEvent` (rewards are applied by Progression after listeners run; listeners may add rewards), `QuestFailedEvent` (with reason). Reward types call Core APIs only (alignment change, coin give).

**Failure and expiry.**

- Giver death: NPC pod tells us the UUID of a dying quest-giver (event); every online player with an instance from that giver fails it. Offline players fail it on their next login by checking a server-level "dead givers" set (`SavedData` on the overworld, entries kept 7 in-game days).
- Expiry: checked once per second for online players and on login for offline time.
- Unknown definition after a datapack change: instance fails with `failed_invalid`, no penalty.
- Abandon: from the Quests page; small alignment penalty is a content-spec decision, the hook just records `abandoned`.

## Middle-earth menu: data needs and sync

| Data | Owner | How it reaches the client |
| --- | --- | --- |
| Faction list, names, colours, rank thresholds | Core (datapack registry) | Synced datapack registry |
| Alignment per faction for the player | Core attachment | Core's alignment sync payloads |
| Current rank per faction | Core (derived) | Computed on client from the above, via a Core helper |
| Titles registry | Progression | Synced datapack registry |
| Own `titles` attachment | Progression | `titles_full` on login, `titles_delta` after |
| Others' selected titles | Progression | `title_selected` to trackers |
| Own `quests` attachment (active + log) | Progression | `quests_full` on login, `quests_delta` after |
| Advancement progress | Vanilla | Vanilla advancement packets |
| Map image, region outlines | World | Client resources and World spec |
| Waypoints, fast travel, fellowships | World / Progression later specs | Not in this spec; menu reserves tabs |

**Sync strategy.**

- Login (`PlayerLoggedInEvent`, after Core's alignment sync): send `titles_full` and `quests_full`. Both are well under 32 KiB in normal play (50 log records, 5 active quests, a few hundred title ids); if a serializer ever exceeds 32 KiB it splits by list and the client merges until a `last` flag.
- Changes mark a dirty flag per player and type; at the end of the server tick one `*_delta` payload per dirty type goes out (add/remove ids, changed quest instances by uuid, appended log records).
- Respawn and dimension change: re-send `*_full` (cheap, avoids edge cases with client player re-creation).
- Trackers: on `StartTracking` for a player, send that player's `title_selected` to the new tracker.
- Payload names follow Core's convention; proposed: `lotrrevival:progression/titles_full`, `.../titles_delta`, `.../title_selected`, `.../quests_full`, `.../quests_delta`, and client-to-server `.../select_title`, `.../abandon_quest`. Client-to-server payloads are validated server-side (title owned or qualified, quest uuid exists).
- Vanilla advancement sync already limits what is sent to visible entries, so ~1,000 advancements are not a login concern; we keep most entries non-hidden but parented in chains so the tree only shows a couple of levels ahead.

## Edge cases

- **Multiplayer:** each player has their own titles and quests; quest-givers are shared, so two players can each hold one quest from the same giver. Quest kill credit goes to the player who lands the kill (fellowship sharing is the fellowship spec). Selected titles are visible to all. A player cannot select a title another player "owns"; titles are not exclusive.
- **Death:** `titles` and `quests` attachments copy on death. Active quests keep running; items already handed in are not lost. Kill/travel counters copy too.
- **Dimension change:** the player entity and attachments carry over; client gets a fresh full sync. Travel distance does not count the teleport. `enter_biome` fires on arrival.
- **Logout:** attachments save with the player file. Quest timers use game time, so time keeps running while offline only if the server keeps running; expiry and dead-giver checks run on login.
- **Server restart:** attachments load with player data; `SavedData` (dead givers) loads with the overworld. Datapack registries reload; stale ids handled as above.
- **`/advancement revoke`:** revokes advancement titles derived from that advancement; if selected, cleared. Counters are not reset (they are stats, not advancements).
- **Giver chunk unloaded:** quest stays active; `giver_pos` lets the menu point the player back.
- **Giver despawns without dying** (shouldn't happen for persistent NPCs): treated as death after 7 days unseen; NPC pod should make givers persistent.
- **Datapack removes a faction:** quests from that faction fail `failed_invalid`; rank titles for it become unavailable.
- **Interaction with other systems:** hired units, invasions, banners, fellowships and traders fire our triggers or quest progress through the events above; they do not write the attachments directly.

## Data and APIs

**From Core (`specs/core/core-apis.md`) — the exact list Progression needs:**

1. Faction datapack registry key and a helper to resolve `lotrrevival:<faction>` ids, plus faction tags (e.g. `#lotrrevival:orcish`).
2. Rank model per faction: ordered rank ids, threshold values, a comparable rank, and a pure helper `rank(faction, alignment)` usable on both sides.
3. Alignment attachment read API (server and client): get alignment for a faction, for all factions.
4. Alignment change event on the game bus, posted after the change, carrying player, faction, old and new value, old and new rank, and cause.
5. Alignment write API with a cause (quest reward, achievement) so Progression never touches the attachment directly.
6. Client-side alignment sync (full on login, deltas), finished before our login sync runs, or an ordering hook/event we can wait on.
7. Entity faction lookup: given an entity, its faction id (NPC pod implements, Core owns the interface).
8. Register homes in `ModRegistries` for: `ATTACHMENT_TYPES`, `TRIGGER_TYPES` (vanilla criteria), and a place to declare our datapack registries (`lotrrevival:title`, `lotrrevival:mini_quest`, plus objective/reward type registries `lotrrevival:quest_objective_type`, `lotrrevival:quest_reward_type`), or permission for Progression to add them there.
9. Payload registration convention and helper (namespaced path `lotrrevival:<pod>/<name>`, version string) so our seven payloads register in the core registrar.
10. Coin/currency API: give and take coins by value (for quest and trade rewards).
11. Faction crafting table: an event or hook fired when a player takes a crafted result (for `craft_at_table`).
12. Shared constants for keybind category and the Middle-earth menu key id, if Core keeps those.

**From NPC pod (`specs/npc/npc-base.md`, reference only):** quest-giver capability on NPCs (entity tag or flag), persistent givers, a death event with giver UUID, the interaction entry point that calls our `QuestOfferEvent`.

**From World pod:** region biome tags `#lotrrevival:region/<tab>` and `#lotrrevival:visit_achievement`.

**Progression defines:** attachments `lotrrevival:titles`, `lotrrevival:quests`, `lotrrevival:progress_counters` (`kills_by_faction`, `distance_by_mode`, `trades_total`, `last_biome`); triggers listed above; datapack registries `title`, `mini_quest`; quest events; payloads listed above; `SavedData` `lotrrevival_dead_givers`; commands `/lotrtitle`, `/lotrquest` (op: list, give, complete, clear).

## Out of scope

- Quest content: objective and reward type catalogue beyond `collect`, `alignment`, `coins`; giver dialogue; reward balancing (separate `mini-quests.md` spec).
- Map rendering, waypoints, fast travel, fellowships, banners, traders (own specs).
- Hiring and invasion achievements' trigger sources (NPC pod fires; we only define triggers).
- Writing the ~1,000 achievement entries; this spec only sets the generator and naming.
- Long-term quest chains / the larger "lore quest" content.

## Acceptance tests

- [ ] GameTest: killing a mock faction NPC 50 times grants an advancement using `kill_faction_npc` with `min_total: 50`.
- [ ] GameTest: changing alignment via Core API across a rank threshold fires `alignment_rank`; dropping below clears a selected rank title.
- [ ] GameTest: `titles` and `quests` attachments survive a simulated death (clone) unchanged.
- [ ] GameTest: accepting a 6th quest with `maxActiveQuests = 5` is refused.
- [ ] GameTest: killing the giver fails its quest with `failed_giver_died`; expiry fails with `failed_expired`.
- [ ] GameTest: a quest instance whose definition is removed fails `failed_invalid` on next tick without crashing.
- [ ] Datagen check: all advancement ids match `<tab>/<name>`, no missing parents, every title reference resolves.
- [ ] Unit test: `titles_full` and `quests_full` serialized size with 50 log records and 5 quests is under 32 KiB.
- [ ] Playtest: two players on a dedicated server see each other's selected title on nameplates and in chat; title survives relog and server restart.
- [ ] Playtest: menu Factions, Titles and Quests pages show correct data immediately after login and after changing dimension.

## Open questions

1. **Max active quests:** 5 is our choice; is a lower cap (3) better for pacing? Should it scale with total completed quests?
2. **Vanilla `minecraft:location` vs our `enter_biome`:** vanilla can match biomes every second already. We prefer ours (fires once per change, matches tags cleanly), but it is extra code. Confirm.
3. **Counters as vanilla custom stats** (visible in the Statistics screen) or our own `progress_counters` attachment? Stats are free to sync and display but need a `CUSTOM_STAT` register per faction, which is dynamic (datapack factions). Leaning attachment.
4. **Rank titles when alignment drops:** clear selection (proposed) or keep showing greyed-out? Original-like behavior suggests clearing.
5. **Chat prefix vs other chat mods:** prefix via display-name only (works everywhere) or decorate the message too?
6. **Alignment sync ordering:** does Core expose a "player synced" event, or do we just send after a one-tick delay?
7. **Does Core own coins and the faction crafting tables,** or does Progression (traders) own coins? Affects item 10 above.
8. **Advancement tab count:** NeoForge pages tabs automatically; 14 of ours plus vanilla is fine, but should region tabs be merged further (e.g. `south_east` split into `harad` and `rhun`)?
9. **Offline time:** should quest time limits pause while the player is offline? Proposed: no (game time), but most limits will be 0 anyway.

## Tickets

Filled in by the lead once the spec is approved.
