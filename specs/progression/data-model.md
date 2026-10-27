# Progression data model

- **Pod:** progression
- **Lead:** Opus 4
- **Status:** draft (revised to match Legacy behavior per maintainer decision, 2026-10-27)
- **Depends on:** `specs/core/core-apis.md` (factions registry, alignment attachment, ranks and pledges, alignment change event, payload conventions, register homes), `specs/npc/npc-base.md` (quest-giver NPCs, NPC faction lookup, hired-unit kill credit; reference only)
- **Sources:** behavior notes in `docs/reference/legacy/achievements-titles.md`, `docs/reference/legacy/mini-quests.md` and `docs/reference/legacy/banners-fellowships-menu.md` (our own words about Legacy 36.15). Numbers below come from those notes; all names, text and code are our own.

## Summary

This spec fixes the data shapes every Progression system builds on: achievements (stored as vanilla advancements, shown in our own menu page), the achievement count that other systems spend, titles, per-player mini-quest state (with the bounty shape reserved), and the data the Middle-earth menu reads. It decides what is stored where, what is synced to which client and when, and what survives death, dimension change, logout and restart. Quest content, the menu's look, banners, fellowships, shields, waypoints and traders get their own specs and must use the shapes here.

Players care because this is what makes their journey persistent: the lands they have seen, the factions that honour them, the title in front of their name and the errands they are running for an NPC all have to be there tomorrow, after a death, on a server with friends.

## Player-facing behavior

1. **Middle-earth menu.** One key (default `L`, rebindable, in our key category) opens the menu when no other screen is open. It reopens the last page used until the player goes back to the hub. Pages: Achievements, Map, Factions, Fellowships, Titles, Shields, Options, plus a small Quests summary (counts and the tracked quest). Data is already on the client when it opens; no loading spinner for anything defined here.
2. **Achievements.** On the Achievements page, one of 14 tabs at a time, header "earned / total" for the whole set and per tab. Earned entries are bright, unearned greyed. Entries are unlocked by entering a biome, reaching a faction rank, the first kill of a faction's NPC or a notable creature, the first trade with a kind of trader, wearing a full faction armour set, first use of a faction crafting table, the first completed quest of a quest group, first hire of a unit type, and one-off feats. Explorer milestones count distinct biomes visited (10, 20, 30, 40, 50).
   - Many entries are alignment-gated ("ally of" or "enemy of" one or more factions). An entry the player is not currently eligible for is hidden and excluded from every count. Eligibility is re-checked live: if alignment flips, an entry earned earlier while ineligible appears as earned and starts counting, and an eligible entry can drop out again.
   - On earning an eligible entry the player gets a toast and the whole server gets a chat line (player name and the achievement name in brackets, hover for details). Ineligible earns are recorded silently. A server config list suppresses the broadcast for chosen entries (default: the "killed a hobbit" entry, so new evil players are not hunted for it).
   - No XP or item rewards. The value of achievements is their **count**: it raises the custom-waypoint limit (5 + floor(n / 5)), the number of fellowships a player may lead (1 + floor(n / 20)) and unlocks shields at 25, 50, 100 and 200.
3. **Titles.** On the Titles page the player picks one title (or none), then one of the 16 vanilla chat colours (default white). The title shows as a coloured bracketed prefix in chat, `[Title] <Name> message`, and next to the name in fellowship member lists. It is not shown on nameplates or in the tab list. If the player stops qualifying (alignment drops, pledge broken, achievement no longer counted), the selection is cleared with a chat notice naming the lost title. A server config can disable titles.
4. **Mini-quests.** Ordinary faction NPCs occasionally hold an offer, marked above their head. Right-clicking shows the offer; the first eligible player to accept takes it. Quests are tracked in a **quest book** item (Active and Completed tabs, diary-style detail page, Track and Abandon or Delete buttons) and one quest at a time in a **HUD tracker**. Quests have no time limit. They end by completion (return to the giver), by the giver dying (failed), or by abandoning (free, with a confirmation). Rewards are alignment and coins with some randomness, sometimes extras.

## Rules and numbers

| Thing | Value | Notes |
| --- | --- | --- |
| Menu key | `L` | Keybind in our category; see open question 7 on the vanilla clash |
| Achievement tabs | 14 | 10 region + 4 category; see "Tab layout" |
| Achievements target | ~670 at parity | All datagen; zero hand-written JSON. More is stretch |
| Achievement id scheme | `lotrrevival:<tab>/<name>` | `snake_case`; same id for the backing advancement and the display entry |
| Explorer milestones | 10, 20, 30, 40, 50 distinct biomes | Distinct biome ids ever stood in, Middle-earth only |
| Ally / enemy gate | ally: alignment >= 0 with at least one listed faction; enemy: alignment <= 0 with at least one | Evaluated live for display and counting |
| Custom waypoints allowed | 5 + floor(n / 5) | n = `achievementCount`; World/waypoint spec enforces |
| Fellowships a player may lead | 1 + floor(n / 20) | Fellowship spec enforces |
| Achievement shields | 25, 50, 100, 200 | Four tiers; shield spec enforces |
| Title id scheme | `lotrrevival:<source>/<name>` | e.g. `lotrrevival:rank/hobbits_friend` |
| Title count target | ~527 | 31 starter, 12 special, 175 faction, 25 multi-faction, 1 any-faction, 96 achievement, ~187 rank (incl. 11 feminine variants) |
| Title text max length | 32 chars (rendered) | Enforced by datagen test |
| Title colours | 16 vanilla chat colours | Player choice, default white |
| Active quests per faction | 5 | Config `progression.maxActiveQuestsPerFaction`, 1-20. Failed quests not counted |
| Active quests in total | 20 | Config `progression.maxActiveQuestsTotal`, 5-100. Failed quests not counted |
| Active quests per giver per player | 1 | |
| Quest time limit | none | No field; quests never expire |
| Offer roll chance | 1 in 20,000 per tick per idle eligible NPC | ~one offer per 17 min per NPC; groups may multiply (e.g. x5) |
| Offer lifetime | 24,000 ticks (20 min) | Timer pauses while any player has that offer's screen open |
| Offer indicator range | 12 blocks | Coloured marker, colour from the quest group |
| Kind weights at offer roll | collect 10, kill 8, pickpocket 6, bounty 4 | Among kinds the group has; template uniform within kind; up to 8 retries |
| Alignment reward | base x uniform(0.75, 1.25), min 1 | Rolled at completion |
| Coin reward | base x uniform(0.75, 1.25); 1 in 12 chance x uniform-int(2, 5); min 1 | Rolled at completion; bounty coins fixed at offer, no variance |
| Abandon | free | No alignment loss, no cooldown |
| Completed-quest log | last 50 records + counters | Player can delete records one by one |
| Giver last-position update | when it moves > 16 blocks | While loaded |
| Biome check interval | every 20 ticks | Fires `enter_biome` only on change |
| Login sync payload soft cap | 32 KiB per payload | Split above that; vanilla hard cap is 1 MiB |
| Delta flush | end of server tick | One payload per dirty type per player |

### Server and client config

Server: `progression.miniQuestsEnabled` (true), `progression.bountiesEnabled` (true), `progression.maxActiveQuestsPerFaction` (5), `progression.maxActiveQuestsTotal` (20), `progression.titlesEnabled` (true), `progression.strictFactionTitles` (false), `progression.achievementBroadcastDenylist` (list of achievement ids). Client: `progression.questTrackerSide` (`left` or `right`).

## Achievements

### Storage and display split

Each achievement has two halves with the same id, both generated together:

- a **backing advancement** with criteria only and **no `display` block**. Vanilla handles criteria, persistence, `/advancement` commands and per-player progress; because it has no display, it never appears in the vanilla advancement screen, never toasts and never announces;
- a **display entry** in the synced datapack registry `lotrrevival:achievement` that holds everything the menu needs.

We do the toast and broadcast ourselves, after the eligibility check. This is what makes live gating possible: the vanilla screen cannot hide an earned entry per player, our page can.

**Display entry** (`data/<ns>/lotrrevival/achievement/<tab>/<name>.json`):

```json
{
  "tab": "rhovanion",
  "icon": { "id": "minecraft:bow" },
  "title": { "translate": "achievement.lotrrevival.rhovanion.first_spider.title" },
  "description": { "translate": "achievement.lotrrevival.rhovanion.first_spider.description" },
  "sort_group": "other",
  "requires": [ { "relation": "ally", "factions": ["lotrrevival:wood_elves", "lotrrevival:dale"] } ],
  "counted": true
}
```

- `sort_group`: `special` (rank entries, first entry, explorer milestones), `biome`, `other`. Page order: `special` by id, then `biome` alphabetically by title, then `other` alphabetically.
- `requires`: optional list; all conditions must hold. Each is `relation` (`ally` or `enemy`) plus `factions` (any one listed faction satisfies it).
- Rank entries carry `"rank": {"faction", "rank"}` instead of `requires`. Their eligibility: alignment with that faction >= 0, and, for ranks above the faction's pledge rank, an active pledge to it. Their title text is the rank name from Core (feminine variant when the player prefers it).
- `counted`: false for anything outside Middle-earth (reserved for future dimensions). Defaults true.

**Earned and eligible.** "Earned" = the backing advancement is done. "Eligible" = the `requires` / rank check passes now. An entry is shown and counted only if eligible. Shown-as-earned only if earned and eligible.

**Award flow.** A trigger completes the advancement (vanilla). On `AdvancementEvent.EarnEvent` for our namespace we check eligibility: if eligible, send the toast payload to the player and broadcast the chat line (unless the id is in the denylist); if not, nothing visible happens. When an entry later becomes eligible through an alignment or pledge change, it appears as earned with no toast and no broadcast.

### Achievement count (shared currency)

`ProgressionApi.achievementCount(Player)`: number of entries that are earned, eligible and `counted`. Works on server (reads advancements and Core alignment) and on client (reads our synced earned set and Core's synced alignment). Cached per player; recomputed on earn, on revoke and on every Core alignment or pledge change event. When the value changes we post `AchievementCountChangedEvent(player, old, new)` on the game bus.

Consumers: custom waypoints (World), fellowships, shields. They call the helper and listen to the event; they never read advancements directly. See open question 4 on where the interface lives.

### Tab layout

Tabs are our own menu tabs, generated from one table. Legacy's region categories are merged into 14. Faction-specific entries, including rank entries, go in the faction's region tab; cross-faction entries go in a category tab.

| Tab id | Covers |
| --- | --- |
| `general` | First entry to Middle-earth, explorer milestones, cross-faction one-offs, quest and bounty milestones, banners |
| `shire` | The Shire and its surroundings |
| `eriador` | Bree-land, Lone-lands, Eregion, Enedwaith, Dunland, Angmar, Blue Mountains, Lindon, Forodwaith |
| `misty_mountains` | Misty Mountains, goblin holds, Gundabad, the high passes |
| `rhovanion` | Mirkwood, Dale, Erebor, Iron Hills, Dorwinion, Anduin vales |
| `lothlorien` | The Golden Wood and its borders |
| `rohan` | Rohan, Fangorn, Isengard |
| `gondor` | Gondor and its fiefs, Ithilien, the Wetwang |
| `mordor` | Mordor and its approaches |
| `south_east` | Near Harad, Umbar, Far Harad, the southern jungle, Rhun, the Red Mountains |
| `combat` | Cross-faction kills, bosses, invasions, notable creatures |
| `crafting` | Cross-faction forging, brewing, cooking, smelting |
| `trade` | Cross-faction trading, coins, hiring |
| `travel` | Roads, waypoints, fast travel, oceans |

Region membership of biomes comes from World pod biome tags (`#lotrrevival:region/<tab>`); progression only reads the tags.

### Achievement generation

- One sub-provider per tab under `progression/datagen/achievement/`, emitting both the backing advancement and the display entry. Registered through Core's `DataGenerators` (shared-file lock; ask Opus 1).
- Table-driven. Rows: `(tab, name, icon, sort_group, trigger, requires?)`. Families generated in loops:
  - every biome tagged `#lotrrevival:visit_achievement` gets one `enter_biome` entry in its region tab (at most one per biome, ~100);
  - every faction x every rank (Core rank list) gets one rank entry (~176);
  - every faction gets one first-kill entry (`kill_faction_npc`), gated `enemy` of that faction;
  - every faction crafting table gets a first-use entry, every faction armour set a full-set entry, every quest group a first-completion entry;
  - explorer milestones 10/20/30/40/50 (`distinct_biomes`).
- Lang keys: `achievement.lotrrevival.<tab>.<name>.title` and `.description`, from our lang sub-provider. All text is our own writing; never reuse Legacy names or descriptions.
- A datagen check fails the build if: an id breaks the scheme, a display entry has no backing advancement or vice versa, two entries share a lang key, a `requires` faction or rank does not exist, or a title references a missing achievement.

### Custom criteria triggers

All under `lotrrevival:`, registered in vanilla's trigger type registry. Each instance has an optional `player` predicate plus the fields below; omitted means "any".

| Trigger | Fired when | Conditions |
| --- | --- | --- |
| `enter_biome` | Player's biome changes (20-tick poll) | `biome` (id or `#tag`), `dimension` |
| `distinct_biomes` | The player's visited-biome set grows | `min_count` (required) |
| `alignment_rank` | Core alignment change event, after the change | `faction` (required), `rank` (matches if current rank >= it) |
| `kill_faction_npc` | Player, or a unit hired by the player, kills an NPC with a faction | `faction` or `faction_tag`, `entity` (entity predicate), `by_hired` (bool, default any) |
| `trade` | A trade with a faction trader completes | `faction`, `trader` (entity type or tag), `direction` (`buy`, `sell`, `any`), `item` |
| `craft_at_table` | Player takes a result from a faction crafting table | `table` (block id or tag), `faction`, `item` |
| `wear_armor_set` | Player's equipment changes and all four armour slots match | `set` (item tag of the four pieces) |
| `hire_unit` | Player hires a unit (fired by NPC pod) | `entity` (type or tag), `faction` |
| `complete_mini_quest` | A quest completes | `faction`, `group`, `quest` (definition id or tag), `min_total` |
| `earn_title` | A title becomes usable for the first time | `title`, `count` (min distinct titles usable) |

Achievements are first-time only; there are no kill or distance tiers. The `trade` and `craft_at_table` triggers are fired from the trader code (this pod) and the crafting-table owner (Core, through a hook or event; see Data and APIs).

Sample backing advancement (datagen output):

```json
{
  "criteria": {
    "kill": {
      "trigger": "lotrrevival:kill_faction_npc",
      "conditions": { "faction": "lotrrevival:hobbits" }
    }
  }
}
```

## Titles

**Definition** (synced datapack registry `lotrrevival:title`, file `data/<ns>/lotrrevival/title/<source>/<name>.json`):

```json
{
  "text": { "translate": "title.lotrrevival.rank.hobbits_friend" },
  "text_feminine": { "translate": "title.lotrrevival.rank.hobbits_friend.feminine" },
  "source": { "type": "alignment_rank", "faction": "lotrrevival:hobbits", "rank": "lotrrevival:hobbits/friend" },
  "hidden_until_usable": false
}
```

`text_feminine` is optional (gendered ranks only). There is no colour in the definition; the player picks it.

`source.type` is one of:

| Type | Fields | Usable when | Legacy-sized count |
| --- | --- | --- | --- |
| `starter` | none | Always | 31 |
| `special` | none | Granted by `/lotrtitle grant` (permission 2) or code; hidden unless owned. Covers playtester, contest and similar titles | 12 |
| `alignment` | `factions` (list), `min` (number or `"pledge"`) | Alignment >= `min` with any one listed faction. `"pledge"` = that faction's pledge-rank threshold. With `strictFactionTitles`, also needs an active pledge to that faction | 175 single-faction at `"pledge"`, 25 multi-faction at +100, 1 any-faction at +10,000 |
| `achievement` | `achievement` | The achievement is earned and eligible (counted) | 96 |
| `alignment_rank` | `faction`, `rank` | Rank >= `rank`; if `rank` is above the faction's pledge rank, an active pledge too. With `strictFactionTitles`, the pledge rank itself also needs a pledge | ~187 incl. feminine |

Faction titles use place and people names from the books, picked by us; do not copy Legacy's lists or wording.

**Per-player attachment** `lotrrevival:titles` (serialized, copied on death):

| Field | Type | Notes |
| --- | --- | --- |
| `granted` | set of title ids | `special` titles only; all other sources are derived live |
| `selected` | optional title id | None = no title |
| `color` | chat colour name | One of the 16; default `white` |
| `feminine_ranks` | bool | Use `text_feminine` where present; also used for rank achievement names |
| `ever_usable` | set of title ids | For `earn_title` (fires once per title) |

**Validation.** Client-to-server `select_title` carries title id and colour; the server checks the title is usable and the colour is one of the 16. The server re-checks `selected` on login, on every Core alignment or pledge change, on achievement earn/revoke and on datapack reload. If no longer usable, or the id no longer exists, it is cleared and the player gets a chat notice naming it. Unknown ids in `granted` stay on disk (a datapack may come back) but are ignored.

**Display.** The chat prefix is added server-side when the chat message is decorated, using the title text and chosen colour. Fellowship member lists get titles from a server-side cache `SavedData` `lotrrevival_title_cache` (player UUID -> selected title id, colour, feminine flag), updated on change, so offline members still show their title. Nothing is sent to trackers; nameplates and the tab list are untouched.

## Mini-quest data model

Quest content (templates, dialogue, quest group list, balancing, pickpocket rules, extras) is the later `mini-quests.md` spec. This section fixes storage, lifecycle and reward shape.

**Quest group** (datapack registry `lotrrevival:quest_group`): `faction`, `reward_faction` (default `faction`), `skip_alignment_if_enemy` (bool), `min_alignment` (default 0), `offer_rate_multiplier` (default 1), `color` (indicator and tracker tint), `first_completion_achievement` (optional id), `lore_categories` (reserved). About 38 groups expected.

**Quest template** (datapack registry `lotrrevival:mini_quest`). One template = one target with a count range and a reward factor. Objectives and rewards are type-dispatched codecs.

```json
{
  "group": "lotrrevival:hobbits",
  "objective": {
    "type": "lotrrevival:collect",
    "item": { "items": "minecraft:wheat" },
    "count": { "min": 10, "max": 40 }
  },
  "reward_factor": 0.25,
  "extra_rewards": []
}
```

Objective types, first wave: `collect`, `kill_entity`, `kill_faction`. Reserved now (shape only, later specs): `pickpocket`, `bounty`, `scripted` (tutorial). Reward types: `alignment`, `coins`, plus reserved `item_table`, `hire_giver`, `lore_book`. No `deliver` objective.

**Base values** (fixed at offer from the rolled count N):

| Kind | Alignment base | Coin base |
| --- | --- | --- |
| collect | max(N x factor, 1) | round(alignment base x 2) |
| kill_entity, kill_faction | N x factor | round(N x 2 x factor) |
| pickpocket (reserved) | as collect | alignment base x 5 |
| bounty (reserved) | clamp(target kills, 1, 50) | clamp(kills x 10 x uniform(0.75, 1.25), 1, 1000), final at offer |

Variance, the 1-in-12 coin bonus and any extras are rolled at completion. Alignment goes to the group's `reward_faction` through Core's alignment write API with cause `quest` (skipped if `skip_alignment_if_enemy` and the player is an enemy of it). Coins are paid through Core's coin API as coin items of the largest denominations, dropped at the giver.

**Offers on the giver** (entity attachment `lotrrevival:quest_giver`, serialized with the entity):

| Field | Type | Notes |
| --- | --- | --- |
| `offer` | optional `QuestOffer` | `template`, rolled `count`, base values, `ticks_left` (from 24,000), `for_player` (optional UUID, scripted offers) |
| `holders` | set of player UUIDs | Players with an active quest from this giver |

Each tick, for an entity in `#lotrrevival:quest_givers` with no offer, NPC pod's `canOfferQuests(entity)` true (adult, not a trader, escort, hired unit or drunk, not in combat) and minis enabled, roll 1 in (20,000 / `offer_rate_multiplier`). On success generate from the group (kind by weight, template uniform, 8 retries). `ticks_left` counts down except while a player has the offer screen open; at 0 the offer is dropped. The client draws the marker for givers within 12 blocks whose offer it may accept (see payload `quest_offer_marker`).

**Interaction priority** on right-clicking a giver: (1) a pickpocket quest from another giver that targets this NPC (reserved); (2) the player's own active quest from this giver: hand in or complete, else a progress line, and it becomes tracked; (3) an offer the player may accept: open the offer screen, unless the player is at the per-faction or total cap, in which case a "too many" line; (4) reserved: bounty hint, 1 in 3.

Accept requires: alignment with the group's faction >= `min_alignment` and the faction not hostile, giver not in combat, below both caps, no active quest from this giver. Accepting removes the offer (first come, first served) and auto-tracks the quest.

**Per-player attachment** `lotrrevival:quests` (serialized, copied on death):

| Field | Type | Notes |
| --- | --- | --- |
| `active` | list of `QuestInstance` | Acceptance order. Includes failed ones until removed. Non-failed entries limited by both caps; at most 20 failed entries, oldest moved to `log` beyond that |
| `tracked` | optional quest uuid | Exactly one or none |
| `log` | ring buffer of `QuestRecord`, max 50 | Completed, failed-and-removed, abandoned. Player may delete entries |
| `completed_by_faction` | map faction id -> int | Never trimmed; Factions page statistic |
| `completed_total`, `failed_total` | int | |
| `bounties_completed` | int | Reserved: every 5th gives a trophy and achievement |

`QuestInstance`: `uuid`, `template`, `group`, `giver` (UUID), `giver_name` (component snapshot), `giver_faction`, `giver_pos` (last known `GlobalPos`), `given_at` (game time), `given_biome`, `count`, `alignment_base`, `coin_base`, `progress` (int or small objective map), `state` (`active`, `ready_to_turn_in`, `failed`), `fail_reason` (`giver_died`, `invalid`, reserved `claimed`, `killed_by_target`, `moved_on`), `objective_data` (reserved: robbed NPC UUIDs, bounty target UUID and name).

`QuestRecord`: `template`, `giver_name`, `faction`, `outcome` (`completed`, `failed`, `abandoned`), `fail_reason`, `at`, `rewards` (alignment, coins, item stacks, hired flag).

**Progress rules.** Collect: hand-in takes matching items held slot first, then inventory order, up to what is owed; partial hand-ins are kept; completes on reaching the count. Damage on damageable items and drink strength are ignored; items flagged as stolen never count. Kill: every qualifying kill while active counts, anywhere, including kills by the player's hired units; then return to the giver.

**Lifecycle events** (Progression-owned, game bus): `QuestOfferEvent` (cancelable), `QuestAcceptedEvent`, `QuestProgressEvent`, `QuestCompletedEvent` (listeners may add rewards; Progression applies them after), `QuestFailedEvent` (with reason). Reward types call Core APIs only.

**Failure.**

- Giver death: NPC pod posts a death event with the giver UUID; we fail every online holder's quest from it (`holders` list) and add the UUID to `SavedData` `lotrrevival_dead_givers` (entries kept 7 in-game days) for offline holders, checked on login. The failed quest stays in `active`, shown as failed with its reason, until the player removes it; it does not count toward caps. The player gets a one-time chat notice.
- Unknown template after a datapack change: fails with `invalid`, no penalty.
- Abandon: book or HUD button, with confirmation. No penalty, no cooldown; recorded as `abandoned`.
- Giver last position: while loaded, if it moved more than 16 blocks since recorded, update `giver_pos` for online holders.

**Bounties (reserved shape, separate spec).** `SavedData` `lotrrevival_faction_bounties`: per faction, per player, kill timestamps (each expires after 3,456,000 ticks / 48 h) and last bounty-killed time (12 h immunity, 864,000 ticks). A player is a valid target with >= 25 live kills and no immunity. Accepting needs +100 with the giver's faction, not being the target, and no other bounty on the same target. Resolution swings alignment by 1x or 2x the bounty value, floored at +100; full rules go in `specs/progression/bounties.md`.

**Quest book and HUD.** The quest book item (`lotrrevival:quest_book`) opens the log screen on use; it is given on first entry to Middle-earth and is craftable (recipe in the content spec). The HUD tracker draws the tracked quest: icon, objective, short progress (e.g. `3/10`) or failure reason, tinted with the group colour, on the side from client config. The menu's Quests summary shows counts and the tracked quest only.

## Middle-earth menu: data needs and sync

| Data | Owner | How it reaches the client |
| --- | --- | --- |
| Factions, names, colours, ranks, pledge rank | Core (datapack registry) | Synced datapack registry |
| Alignment per faction, pledge | Core attachment | Core's sync payloads |
| Achievement display entries, titles, quest groups and templates | Progression | Synced datapack registries |
| Own earned achievement ids | Progression (from vanilla advancements) | `achievements_full` on login, `achievements_delta` on earn/revoke |
| Toast for an eligible earn | Progression | `achievement_toast` |
| Own `titles` attachment | Progression | `titles_full` on login, `titles_delta` after |
| Own `quests` attachment | Progression | `quests_full` on login, `quests_delta` after |
| Quest offer markers | Progression | `quest_offer_marker` to trackers of the giver: entity id, group, or cleared |
| Map, waypoints, fast travel | World / later specs | Not in this spec |
| Fellowships, shields | Later progression specs | Read `achievementCount` and the title cache |

**Sync strategy.**

- Login (after Core's alignment sync): send `achievements_full`, `titles_full`, `quests_full`. Typical sizes: ~670 ids at worst, 20 active quests, 50 log records; split by list above 32 KiB with a `last` flag.
- Changes mark a dirty flag per player and type; one `*_delta` per dirty type at end of tick.
- Respawn and dimension change: re-send the three `*_full` payloads.
- Offer markers: sent on `StartTracking` of a giver with an offer, and to all trackers when an offer appears or is taken or expires. The client hides markers further than 12 blocks or for groups whose `min_alignment` its own alignment fails.
- Payload names (Core convention): `lotrrevival:progression/achievements_full`, `.../achievements_delta`, `.../achievement_toast`, `.../titles_full`, `.../titles_delta`, `.../quests_full`, `.../quests_delta`, `.../quest_offer_marker`; client-to-server `.../select_title`, `.../accept_quest`, `.../decline_quest`, `.../abandon_quest`, `.../remove_quest`, `.../delete_quest_record`, `.../track_quest`, `.../offer_screen_open` (pauses lifetime). All client-to-server payloads are validated server-side.
- Vanilla advancement packets carry nothing of ours (no display blocks), so achievement count adds no vanilla login cost.

## Edge cases

- **Multiplayer:** titles and quests are per player. Offers are shared; the first to accept wins, and a second player with the offer screen open sees it close with a "taken" line. Several players may each hold a quest from the same giver. Titles are not exclusive.
- **Alignment flip:** count, visible entries, usable titles and HUD all change live. A selected title can be lost by a single kill that drops alignment below a threshold.
- **Death:** `titles` and `quests` copy on death; active quests keep running; handed-in items are kept. Visited-biome set and counters copy too.
- **Dimension change:** attachments carry over; client gets a fresh full sync. `enter_biome` fires on arrival. Biomes outside Middle-earth do not count toward explorer milestones.
- **Logout:** attachments save with the player file. Dead-giver check runs on login. Quests have no timers, so offline time does not matter. Offer lifetimes on unloaded givers do not tick.
- **Server restart:** attachments load with player data; `SavedData` (dead givers, bounties, title cache) loads with the overworld. Datapack registries reload; stale ids handled as above.
- **`/advancement grant|revoke`:** works on backing advancements; count, achievement titles and selection update; revoke clears a selected achievement title. Counters are not reset.
- **Giver chunk unloaded:** quest stays active; `giver_pos` shows the last known place.
- **Giver despawns without dying:** treated as death after 7 days unseen; NPC pod should make givers persistent.
- **Datapack removes a faction:** its quests fail `invalid`; its rank and faction titles and gated entries become unusable or hidden.
- **Titles disabled by config:** no prefix, page hidden, selection kept on disk.
- **Other systems** fire our triggers or quest progress through events; they never write our attachments.

## Data and APIs

**From Core (`specs/core/core-apis.md`):**

1. Faction registry key, id helper and faction tags (e.g. `#lotrrevival:orcish`).
2. Rank model per faction: ordered rank ids, thresholds, the pledge rank, gendered rank names, and a pure `rank(faction, alignment)` usable on both sides.
3. Alignment read API (server and client) for one or all factions.
4. Pledge read API (server and client): current pledge faction or none, plus a pledge change event.
5. Alignment change event (after the change): player, faction, old/new value, old/new rank, cause.
6. Alignment write API with a cause (`quest`, `kill`). **Agreed rules: quests and kills give alignment; trading and hiring do not.**
7. Client alignment and pledge sync finished before our login sync, or an event we can wait on.
8. Entity faction lookup (NPC pod implements, Core owns the interface).
9. Register homes in `ModRegistries` for `ATTACHMENT_TYPES` (player: `titles`, `quests`, `progress_counters`; entity: `quest_giver`), `TRIGGER_TYPES`, our datapack registries (`achievement`, `title`, `quest_group`, `mini_quest`, plus `quest_objective_type` and `quest_reward_type`), and `ITEMS` for `quest_book` (shared-file lock).
10. Payload registration convention so our payloads register in the core registrar.
11. Coin API: give coins by value as largest denominations, optionally dropped at a position.
12. Faction crafting table hook or event when a player takes a result.
13. Keybind category constant; our `L` menu key and the vanilla clash (open question 7).
14. Possibly hosting the `achievementCount` interface (open question 4).

**From NPC pod (reference):** quest-giver tag `#lotrrevival:quest_givers`, `canOfferQuests(entity)`, persistent givers, a giver death event with UUID, the interaction entry point that calls our priority chain, a kill event that attributes hired-unit kills to the owner, and a `hire_unit` trigger fire.

**From World pod:** biome tags `#lotrrevival:region/<tab>` and `#lotrrevival:visit_achievement`; waypoint limit reads `achievementCount`.

**Progression defines:** `ProgressionApi.achievementCount(Player)` and `AchievementCountChangedEvent`; attachments `titles`, `quests`, `progress_counters` (`kills_by_faction`, `trades_total`, `visited_biomes` set, `last_biome`), entity attachment `quest_giver`; triggers above; datapack registries above; quest events; payloads above; `SavedData` `lotrrevival_dead_givers`, `lotrrevival_title_cache`, `lotrrevival_faction_bounties` (reserved); item `quest_book`; commands `/lotrtitle` (grant, revoke, list), `/lotrquest` (op: list, give, complete, clear).

## Out of scope

- Quest content: template and group catalogue, dialogue, extras (lore books, modifiers, pouches), pickpocket rules, tutorial script (`mini-quests.md`).
- Bounty resolution rules and announcements (`bounties.md`).
- Map rendering, waypoints, fast travel, fellowships, shields, banners, traders, menu art (own specs).
- Writing the ~670 achievements and ~527 titles; this spec sets generators, sources and naming.
- Long-term quest chains and lore quests.

## Acceptance tests

- [ ] GameTest: first kill of a mock faction NPC by a player who is an enemy of it grants the entry; count goes up by 1.
- [ ] GameTest: same kill by a player with alignment +10 records the advancement, no toast, count unchanged; dropping alignment to 0 makes it count without a toast.
- [ ] GameTest: a kill by the player's hired unit counts for a `kill_faction` quest.
- [ ] GameTest: visiting 10 distinct biomes grants the first explorer milestone; re-entering a visited biome does not count.
- [ ] GameTest: `achievementCount` changes fire `AchievementCountChangedEvent` with correct old and new values.
- [ ] GameTest: crossing a rank threshold fires `alignment_rank`; a rank title above the pledge rank is unusable without a pledge; dropping below clears the selection with a notice.
- [ ] GameTest: selecting a title with a colour outside the 16 is rejected.
- [ ] GameTest: `titles` and `quests` survive a simulated death unchanged.
- [ ] GameTest: a 6th quest for one faction is refused; a 21st quest across factions is refused; a failed quest frees its slot.
- [ ] GameTest: killing the giver fails its quest with `giver_died`; the quest stays listed until removed.
- [ ] GameTest: with a seeded RNG, coin and alignment rewards fall within base x [0.75, 1.25] and the bonus applies at the expected rate over 10,000 rolls (about 1 in 12).
- [ ] GameTest: an offer with `ticks_left` 1 expires next tick unless the offer screen is open.
- [ ] GameTest: a template removed by datapack fails the instance `invalid` without crashing.
- [ ] Datagen check: ids match `<tab>/<name>`, every display entry has a backing advancement and vice versa, every title and `requires` reference resolves, no backing advancement has a `display` block.
- [ ] Unit test: `achievements_full` (670 ids), `titles_full` and `quests_full` (20 active, 50 log) are each under 32 KiB.
- [ ] Playtest: `L` opens the menu; two players on a dedicated server see each other's title and colour in chat and in the fellowship list, not on nameplates; it survives relog and restart.
- [ ] Playtest: quest book and HUD tracker show the right quest right after login and after a dimension change.

## Open questions

1. **Vanilla `minecraft:location` vs our `enter_biome`:** vanilla can match biomes every second; ours fires once per change, matches tags cleanly and feeds the visited-biome set. Leaning ours.
2. **Counters as vanilla custom stats** or our `progress_counters` attachment? Stats need a register entry per datapack faction. Leaning attachment.
3. **Chat prefix vs other chat mods:** decorate the chat message server-side (proposed) or use the player display name, which would also leak into nameplates?
4. **Home of `achievementCount`:** a Progression `api` subpackage other pods call, or a Core-owned interface Progression implements so World and NPC never depend on our package? Opus 1 to decide.
5. **Advancements without display:** confirm with Opus 1 that backing advancements plus our own registry and page is acceptable (it is the only way to hide and re-show earned entries live).
6. **Alignment sync ordering:** does Core expose a "player synced" event, or do we send after a one-tick delay?
7. **Menu key `L` clashes with vanilla Advancements (also `L`).** Our achievements no longer need the vanilla screen. Ship with the clash shown in Controls, or have Core move vanilla's default once on first launch?
8. **Coins and crafting tables:** does Core own coins and faction crafting tables, or does Progression (traders) own coins? Affects Core items 11-12.
9. **Completed log:** Legacy keeps completed quests forever with manual delete; we keep 50 plus counters to bound sync size. Acceptable?

## Tickets

Filled in by the lead once the spec is approved.
