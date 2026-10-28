# Legacy reference: mini-quests and bounties

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: how NPCs offer short quests, the quest kinds, limits, rewards, failure, the quest log, and the player-bounty system. Quest dialogue is not reproduced; only its structure is described.

## 1. Overview

- Mini-quests are short, randomly generated errands offered by ordinary faction NPCs (not traders). They are the main repeatable source of alignment outside combat.
- There is no quest board and no fixed quest list per NPC. Each NPC occasionally "rolls" a single offer from its faction's quest group, holds it for a while, and shows an indicator over its head.
- Quests have no time limit. They end by completion, by the giver dying (failure), or by the player abandoning them.

## 2. Quest groups

Quests are organised into 38 quest groups, roughly one per faction plus some special sub-groups. Each group has:

- a pool of quest templates (see section 3);
- optionally an achievement awarded on the first completion from that group (37 groups have one);
- optionally lore-book categories used for a bonus reward (35 groups);
- optionally an alignment override (the reward goes to a different faction than the giver's own) and a rule "no alignment reward if the player is an enemy of the reward faction".

Special sub-groups worth noting as design patterns:

| Pattern | Example | Behavior |
| --- | --- | --- |
| Spy for an enemy faction | Ruffian spies in Bree-land | Reward alignment goes to Isengard, not the giver's faction; only if the player is not an Isengard enemy. Offers pickpocket quests. |
| Thug for hire | Ruffian brutes | No alignment or coins (reward factor 0). On completion the giver joins the player as a hired warrior. |
| Recruitable warrior | Rohan shieldmaidens | Kill quests against Dunland/Isengard. No alignment/coins; the giver joins as a hired warrior; offered only at +150 alignment. Offer rate 5x the normal rate. |
| Relic reward | Arnor relic group (Rangers) | Kill quests; no alignment/coins; reward is one random piece of Arnorian gear (seven candidates). |
| Hunt renegades | Gondor | Kill a few renegades, high reward factor (8x). |
| Renegade giver | Gondor renegades | Offer rate 5x normal; requires +50 alignment. |

## 3. Quest kinds

Template counts across all groups (a template is one item/target with a count range and a reward factor):

| Kind | Templates | Selection weight |
| --- | --- | --- |
| Collect items | 834 | 10 |
| Kill (specific entity type) | 109 | 8 (shared with kill-faction) |
| Kill (any NPC of a faction) | 64 | 8 |
| Pickpocket | 2 | 6 |
| Bounty (kill a player) | 30 | 4 |
| Welcome / tutorial | 1 (scripted) | not random |
| Retrieve (kill X, it drops the item) | 0 used | unused in this build |

Generation picks a kind by weight among the kinds the group actually has, then a template uniformly within that kind. If generation fails (for example a bounty with no valid target) it retries up to 8 times, then gives up until the next roll.

### 3.1 Collect

- Objective: bring N of an item. N is rolled uniformly in the template's range when the offer is generated. Typical ranges: 1 to 3 for valuable items (tools, armour, pies), 4 to 15 for food, 10 to 40 for bulk goods (logs, pipeweed, bread).
- Hand-in: right-click the giver. Matching items are taken from the inventory, held slot first, then the rest in order, up to what is still owed. Partial hand-ins are kept; the quest completes the moment the total is reached.
- Matching ignores damage on damageable items. Drinks match regardless of strength/fullness. Items obtained by pickpocketing never count.
- Reward base: alignment = max(N x factor, 1); coins = round(alignment base x 2).
- Reward factors range from 0.25 (bulk) to 50 (mithril). Most are 0.5 to 8.

### 3.2 Kill (entity or faction)

- Objective: kill N of an entity type (subclasses count) or N NPCs of a faction. N is rolled at offer time, usually 10 to 30 or 10 to 40; elite targets 1 to 10 with higher factors.
- Progress counts every qualifying kill made while the quest is active, anywhere. Kills credited through the player's hired units also count.
- When the count is reached the player must return to the giver and right-click to complete.
- Reward base: alignment = N x factor; coins = round(N x 2 x factor). Factor defaults to 1.

### 3.3 Pickpocket

- Objective: steal from N NPCs of a named faction (2 to 6 or 2 to 8), then return the stolen items.
- Steal: sneak, empty hand, right-click a target NPC of that faction. Each NPC can be robbed once per quest. Fails immediately if the target is in combat or is looking at the player (vision cone about 130 degrees with line of sight).
- Outcome rolls: success 1 in 3. Noticed by the target 1 in 3 on success, 1 in 4 on failure. If noticed, the target attacks.
- Bystanders: unless the target noticed (and then with 50% chance anyway), friendly NPCs within 16 blocks (8 for civilians) who can see the player may notice. Chance = 0.5 + 0.5 x closeness (scaled from 4 blocks to max range), quartered for civilians. Each noticing NPC attacks.
- Any noticing applies a -1 alignment penalty with the robbed NPC's faction.
- The stolen item is tagged with the robbed NPC and the quest giver; only that giver accepts it.
- Reward base: like collect, but coins = alignment base x 5.

### 3.4 Bounty

See section 8.

### 3.5 Welcome (tutorial)

- Offered once by the wandering wizard NPC to new players. Scripted in 15 stages: receive the quest-log book, open it, open the map, cycle the alignment display (faction, then region), open the factions page, receive starter pouches, final talk.
- Several stages advance on UI events (opening the book, the map, the factions page, pressing the alignment keys). While a stage needs the map or factions page, opening the menu jumps straight there.
- No alignment or coins; awards its own achievement. Fails with a "moved on" state if the wizard leaves before it is finished. Only one such quest per player ever.

## 4. Offers: who, when, how often

| Rule | Value |
| --- | --- |
| Can offer | Adult NPCs of a faction that are not traders, trader escorts, hired units or drunkards |
| Player must be | Friendly to the NPC's faction (positive alignment, not hostile), NPC not fighting |
| Minimum alignment | 0 by default; a few NPC types use +50 or +150 |
| Chance to roll an offer | 1 in 20,000 per tick per idle NPC (about one offer per 17 minutes per NPC on average); 1 in 4,000 for two special NPC types |
| Offer lifetime | 24,000 ticks (20 minutes); the timer pauses while any player has the offer screen open |
| Offer sharing | One offer per NPC, visible to every eligible player; the first to accept takes it, then the NPC has none until it rolls again |
| Player-specific offers | Supported (an NPC can hold a private offer for one player), used for scripted cases |
| Indicator | A coloured marker above the NPC's head (colour = the faction's quest colour), drawn only within 12 blocks |
| Offer UI | Right-click opens a screen with the NPC, the opening quote and the objective; Accept or Decline |

Interaction priority when right-clicking a quest-capable NPC:

1. If the player has an active quest from a different NPC that wants to use this NPC (pickpocketing), that runs first.
2. If the player has an active quest from this NPC: try to complete or hand in; otherwise the NPC says a progress line. The quest becomes the tracked quest.
3. Else if the NPC has an offer the player may accept: show the offer, unless the player already has 5 active quests for this faction, in which case the NPC says a "too many" line.
4. Else, if the NPC's faction has active bounties held by this player, 1 in 3 chance the NPC gives a hint about a bounty target (section 8).

## 5. Limits

| Limit | Value |
| --- | --- |
| Active quests per faction | 5 (failed quests do not count) |
| Active quests in total | No limit (5 per faction x every faction) |
| Active quests per giver per player | 1 |
| Several players with the same giver | Allowed; each holds their own quest |
| Cooldown after completing for a giver | None as such; the giver must roll a new offer |
| Time limit | None |

## 6. Rewards

All rewards are rolled at completion, not at offer time (only the target count is fixed at offer time).

| Reward | Rule |
| --- | --- |
| Alignment | Base x random(0.75 to 1.25), minimum 1. Given to the group's reward faction (normally the giver's). Goes through the normal alignment system, so allied factions may also gain. |
| Coins | Base x random(0.75 to 1.25), and 1 in 12 chance to multiply by 2 to 5. Minimum 1. Paid as coin items of the largest denominations, dropped by the giver. Bounty coins are not randomised. |
| Item table | If the template has a reward list, one random item from it. |
| Lore book | 1 in 10, if the group has lore categories. |
| Modifier template | 1 in 15, a random common one (for the reforge system). |
| Dwarf bonus | 1 in 10 a mithril-related book when the giver is a dwarf. |
| Pouch | 1 in 10, the dropped rewards come packed in a pouch instead of loose. |
| Hire | For hiring templates, the giver becomes the player's hired warrior. |
| Achievement | The group's first-completion achievement, if any. |
| Faction stats | Faction's "mini-quests completed" counter and the player's global completed count go up. |

The welcome quest gets none of the random extras. Reward recap (coins, alignment, items, hired) is stored with the completed record and shown in the log.

## 7. Failure, abandoning and the quest log

- **Giver dies:** every player with an active quest from that NPC has it marked failed. The NPC itself keeps the list of players with its quests, so this works even for offline players. Failed quests remain in the active list, shown as "giver has died", until the player removes them. They do not count toward the per-faction limit.
- **Other failures:** bounty-specific (section 8) and the tutorial's "moved on".
- **Abandon:** from the quest log, with a confirmation. No alignment penalty, no cooldown.
- **Completed records:** kept forever until the player deletes them one by one (confirmation prompt). No automatic trimming.
- **Last known location:** while the giver is loaded, its position is recorded with each active quest; the player is updated when it moves more than 16 blocks. Used to show where the giver is.
- **Quest log UI:** lives in a craftable/given book item, not in the main menu. Two tabs: Active and Completed, with counts. Selecting a quest shows a diary-style page: the date given in the mod's in-world calendar, the biome where it was given, the giver's opening quote, any stage quotes, the objective, progress, and on completion the rewards. Buttons to track and to abandon/delete.
- **Tracking HUD:** exactly one quest can be tracked. It is shown in a small HUD box (icon, objective, short progress such as 3/10, or the failure reason), tinted with the quest colour; a client option places it left or right. Accepting or interacting about a quest auto-tracks it.
- **Persistence:** active and completed quests are saved in player data and survive death and relog.

## 8. Bounties

### 8.1 Becoming a target

- When a player (not in creative) kills an NPC of a faction inside that faction's area of influence (at least 50 blocks of reach is used), the faction records a kill against that player.
- Each kill record expires 3,456,000 ticks (48 hours of server time) after it was made.
- A player is a valid bounty target for a faction while they have at least 25 live kill records with it and have not been "bounty-killed" for that faction in the last 864,000 ticks (12 hours).
- Faction bounty records are saved per faction in the world save.

### 8.2 Getting a bounty quest

- Any group with a bounty template can roll one; config can disable bounty quests.
- The target is a random valid target for the giver's faction. Rewards are fixed at offer time: alignment = clamp(target's kill count, 1, 50); coins = clamp(kill count x 10 x random(0.75 to 1.25), 1, 1000).
- To accept: the player needs +100 alignment with the giver's faction, cannot be the target, and cannot already hold a bounty on the same target.
- On accept, the target receives a yellow chat notice (on their next tick, or next login) that this faction has placed a bounty on them. The hunter's identity is not revealed.

### 8.3 Resolution

| Event | Effect |
| --- | --- |
| Hunter kills target | Quest objective done (return to giver to claim). Target is marked bounty-killed for the faction (12 hours immunity, other hunters' quests on this target fail as "claimed"). Target loses 2 x the bounty's alignment value with their pledged faction, or else their highest faction above +100, never dropping below +100. Target gets two chat lines; everyone else gets a server-wide announcement. |
| Target kills hunter | Hunter's quest fails. Target gains the bounty's alignment value with their pledged or highest faction (any positive). Hunter loses 2 x the value with the giver's faction if above +100, floored at +100. Target earns an achievement; server-wide announcement. |
| Another hunter claims first | Quest fails as "claimed". |
| Completion | Normal mini-quest completion with the fixed rewards. Every 5th completed bounty also adds a bounty trophy item and an achievement; the first completion gives another achievement. |

### 8.4 Hints

When the hunter talks to an NPC of a faction that has an active bounty in their log, there is a 1 in 3 chance the NPC gives a hint: either the target's last known biome or last known waypoint region (chosen at random), if known.

## 9. Config surface

- Allow mini-quests (on/off).
- Allow bounty quests (on/off).
- HUD tracker on the right side (client).

## 10. Admin notes

- No dedicated quest command in this build; quests are inspected only via player data.

## Differences from our draft spec

Compared with `specs/progression/data-model.md` (draft, 2026-10).

| Topic | Legacy | Our draft | Recommendation |
| --- | --- | --- | --- |
| Active cap | 5 per faction, no global cap | 5 total (config 1 to 20) | Use a per-faction cap of 5 plus a generous global cap (for example 20); a global 5 will feel tight for multi-faction players. |
| Per giver | 1 active per giver per player | 1 | Matches; keep. |
| Offer model | Random roll per NPC (1/20,000 per tick), one shared offer held 20 minutes, first to accept takes it | Giver offers on interaction; per-giver cooldown 5 minutes | Keep on-demand offers (simpler, more legible) but keep a per-giver cooldown; consider making offers per player rather than shared. |
| Time limits | None anywhere | Optional `time_limit_ticks` (default 0) | Keep the field, default 0; no Legacy content needs it. |
| Completed log | Unbounded, manual delete | Ring buffer of 50 plus counters | Keep 50 plus counters; add manual delete to match player habits. |
| Failed quests | Stay in the active list until removed; not counted for the cap | Move to log with a reason | Ours is cleaner; keep, but show a one-time chat notice so players learn why. |
| Abandon penalty | None | Left to content spec | Recommend no penalty, as in Legacy. |
| Kill credit | Hired-unit kills count | Player-landed kills only | Count hired-unit kills; hiring is a core loop and players expect it. |
| Objective kinds | Collect, kill entity, kill faction, pickpocket, bounty, scripted tutorial; no delivery | collect first; delivery mentioned in prose | Ship collect, kill_entity, kill_faction first; pickpocket and bounty as follow-ups; drop "deliver" from the player-facing text unless we design it. |
| Rewards rolled | Target fixed at offer; alignment and coins rolled at completion with +/-25% and a 1-in-12 coin jackpot | All rolled at offer | Keep ours (deterministic, testable); emulate the jackpot as an offer-time roll. |
| Extra rewards | Lore book 1/10, modifier template 1/15, pouch 1/10, hire, item table | Alignment and coins | Add `item_table` and `hire_giver` reward types to the type registry from day one. |
| Reward faction | Group can redirect alignment to another faction, with "not if enemy" rule | Per-reward `faction` | Ours covers it; add the "skip if enemy" flag. |
| Dead-giver handling | NPC keeps a list of players with its quests; marks them on death | Server `SavedData` of dead givers, 7 days | Either works; ours does not need NPC-side storage, keep it. |
| Quest log location | A book item plus a HUD tracker; not in the menu | Quests page in the Middle-earth menu | Keep the menu page; add the single tracked-quest HUD, which players rely on. |
| Bounties | Full PvP bounty system (25 kills, 48 h records, 12 h immunity, +/-2x alignment swings) | Not covered | Out of the data model; write a separate bounty spec later, behind a server config defaulting to on. |
