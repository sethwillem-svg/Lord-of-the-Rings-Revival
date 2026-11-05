# Nemesis system and world events (Middle-earth add-on)

- **Project:** 3 of 4 for the add-on (after hiring and reputation, and the node map; before structures from licensed builds).
- **Status:** draft (2026-11-05), written by the lead; the maintainer reviews after.
- **Builds on:** the Middle-earth mod (NeoForge 1.21.1, branch `neoforge-fixes`) through its public classes only: `NpcEntity`, `NpcType`, `Faction` / `FactionLookup` (dispositions, rank pools, `initial_diplomacy`), the player's chosen faction (`StateSaverAndLoader` / `PlayerData`), `NpcEntityBuilder`, `ModDimensions.ME_WORLD_KEY`, and the `middle-earth:biome_event` / `middle-earth:structure_event` datapack registries (read only). Middle-earth is All Rights Reserved: the add-on depends on its jar and never copies its code, data, textures, models or lang.
- **Depends on (add-on specs):** `hiring-reputation.md` (reputation, `Reputation.onKill(killer, victim, multiplier)`, sworn enemies, the `lotr_revival:unit` attachment, unit levels), `combat-parry-warning.md` (parry and the attack warning marker, used by some traits), `node-map-travel.md` (map markers; being written in parallel), `structures.md` (event venues through structure tags; being written in parallel), `debug-logging.md` (loggers `nemesis` and `events`).
- **Ports:** `specs/archive/npc/nemesis.md`, this repo's own design for the old standalone mod. Its core (rise, ranks, traits, grudges, escape, cheat-death, ambush, two-sided world events, chunk tickets, a load governor) is kept. Its links to the dead standalone APIs (core-apis alignment and pledge, npc-base, `MapMarkerClient`, `StructureVenues`, `TerritoryControl`, pods) are replaced by Middle-earth's factions and ranks, the add-on's reputation, the node map and structure tags. Most event types, politics beyond challenges, war-camp blocks, territory control, hunt quests and achievements are moved to Out of scope.
- **Not from the original LOTR mod:** it had nothing like this. All numbers are ours and are config values.

## Summary

Captains remember you. A Middle-earth soldier or leader of a faction hostile to you that kills you, or kills one of your hired units, can rise as a **nemesis**: a named NPC with a title, a rank in its faction's war-host, strengths, one weakness, scars and a grudge against the players it has met. Orc captains rise against the Free Peoples; Gondor, Rohan, Elven and Dwarven captains rise against servants of the Shadow. Nemeses live on in saved data when no body is loaded, come back to ambush the players they hate, climb the ranks by challenging each other, and can cheat death and return scarred. Around the player, **world events** start every so often a short ride away, often at a structure: two warbands clash, a nemesis musters its band, raiders hit a settlement. They show on the node map; players can ride there and pick a side, and the outcome changes reputation and the nemesis ranks. Players care because Middle-earth stops being a field of interchangeable soldiers and starts having a war going on without them.

## Player-facing behaviour

1. **Rise.** A soldier or leader of a faction hostile to you kills you or one of your hired units. Chat, red: "*Gashnak Skull-Splitter* of Mordor has risen to **Marked**." For an evil player: "*Hirgon the Unbending* of Gondor has sworn to hunt you." It retreats in a puff of smoke.
2. **Nemeses screen.** A key (`N` by default) opens a list of each faction's war-host by rank: name, title, rank, scars, known strengths, known weakness, and whether it holds a grudge on you. Hosts of your own side are greyed.
3. **Ambush (rare).** Roughly once per 1.5 hours of play, a nemesis with a grudge on you finds you: it shouts a taunt naming you and attacks with its bodyguards. A boss bar in its rank colour shows its health. High ranks ride their faction's mount.
4. **The fight.** Nemeses have more health and damage by rank and carry traits, some of which change how parrying works against them (`combat-parry-warning.md`). Badly hurt, one may flee. Killed, it may cheat death and come back scarred. Kill it with its weakness, or after it has cheated death twice, and it dies for good.
5. **Rewards.** Killing a nemesis counts as a kill x25 for reputation (hiring's kill rule): a large gain with its enemies and a large loss with its friends.
6. **World events.** Every 10-20 minutes or so, something starts 64-256 blocks from you: an action-bar hint ("Steel rings out to the north-east") and a marker on the node map. Other players' events near you show too. Ride there and join a side, or ignore it and hear in a grey chat line how it ended.
7. **Sergeants.** One of your level-5 hired units that helps kill a nemesis becomes a **sergeant**: it gets a name, a title and one strength of its own.
8. **Politics.** Grey rumour lines to the players involved: "Ugrash the Grim challenged Gashnak Skull-Splitter and took his place as Captain of Mordor."

## Rules and numbers

All numbers are config values in `lotr_revival-common.toml` (sections `[nemesis]` and `[events]`) unless marked as data. **Design choice** marks a decision taken in this spec that the maintainer may change.

### Sides

- **Faction side:** Middle-earth's faction `disposition`. `GOOD` is good, `EVIL` is evil, `NEUTRAL` (brigand, wild_goblins) has no side and **never** has nemeses. Sub-factions (`longbeards.erebor`, `hobgoblin_tribes.gundabad`) have their own host.
- **Player side:** the disposition of the player's Middle-earth chosen faction. No chosen faction, or a NEUTRAL one: **unsided**.
- **Enemy check (symmetric):** an NPC of faction F is the player's enemy when F's diplomacy is HOSTILE to the player's chosen faction (Middle-earth's own `isHostileToward`), **or** the player's add-on reputation with F is below 0. So an unsided player who keeps killing Moria goblins makes Moria enemies.
- **Side switch** (Middle-earth faction change, detected by comparing on login and every 30 s): grudges held by nemeses of the player's new side go **dormant** (no ambushes, not chosen for events aimed at that player). Dormant grudges lose 1 point per in-game day and are removed at 0. Switching back wakes them. Records never move between factions.

### Who can rise

| Thing | Value | Notes |
| --- | --- | --- |
| Factions | every faction with disposition GOOD or EVIL that has a LEADER rank pool | so new Middle-earth factions join automatically |
| Turning off | config `disabled_factions` (list of faction ids), empty by default | records of a disabled faction stay but never manifest |
| NPC rank | SOLDIER, KNIGHT, VETERAN or LEADER | rank = the highest rank pool of its faction that lists the NPC's type; CIVILIAN and MILITIA never |
| Type opt-out | datapack tag `lotr_revival:never_nemesis` (NPC type ids) | empty by default; for unique NPCs added later |
| Must be | a Middle-earth `NpcEntity`, in an `allowed_dimensions` dimension (default `ModDimensions.ME_WORLD_KEY`), not a hired unit (no `lotr_revival:unit`), not bound to a structure manager (no host position), no custom name | |
| Never by | creative or spectator players, fake players, Peaceful difficulty | no rise, renown or grudge |

### Renown and grudges (kills only)

A wild eligible NPC with no record gets one when the promotion roll passes; an existing nemesis only gains renown.

| Trigger | Renown | Promotion roll (new NPC) | Grudge |
| --- | --- | --- | --- |
| Kills a player whose enemy it is (melee or its projectile) | +10 | 35%, +20% if LEADER rank | that player +3 |
| Kills a hired unit of a player whose enemy it is | +2, +1 per unit level above 1 | 10%, +3% per unit level above 1 | the owner +1 |
| Kills its ambush target again | +5 on top | n/a | +3 |
| Escapes | +4 | n/a | the attacker +1 |
| Cheats death | +6 | n/a | the killer +2 |
| Wins a challenge | +8 | n/a | none |
| Wins a world event | +3 | n/a | none |
| Any player damages it | none | n/a | that player +1 (once per manifestation) |

- Surviving a fight does not promote (kept from the old spec).
- Grudge scores are capped at 10; each record keeps its top 8 grudge holders.
- Renown is capped at 200.

### Ranks

Rank follows renown, limited by seats per faction. A nemesis with enough renown for a full rank waits one rank below as a **challenger**.

| Rank | Renown | Seats per faction | Health | Melee damage | Armour | Knockback res. | Strengths | Bodyguards | Boss bar |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Marked | 0 | 12 | x1.25 | x1.10 | +2 | +0.1 | 1 | 0 | white |
| Captain | 15 | 8 | x1.6 | x1.20 | +4 | +0.2 | 1 | 2 | green |
| Commander | 35 | 5 | x2.2 | x1.35 | +6 | +0.3 | 2 | 3 | yellow |
| Warlord | 70 | 3 | x3.0 | x1.50 | +8 | +0.4 | 3 | 4 | purple |
| Overlord | 120 | 1 | x4.0 | x1.65 | +10 | +0.5 | 4 | 6 | red |

- Multipliers apply on top of the Middle-earth type's own attributes through attribute modifiers with fixed ids per attribute (`lotr_revival:nemesis_rank`), replaced on rank change, never stacked. Movement speed +3% per rank above Marked.
- **Gear (design choice):** the body wears what the NPC wore when it rose (saved in the record). From Commander up, it is re-rolled once from the faction's LEADER rank gear (Middle-earth's `Faction.getRandomGear(level, LEADER, race)`), then kept.
- **Bodyguards:** NPCs of its faction's SOLDIER and KNIGHT rank pools, spawned with it, following it, removed when it retreats. Never nemeses themselves while guarding (no roll).
- **Mount:** Warlord and Overlord ride their type's mount if the Middle-earth type has mount data, otherwise a mount of a LEADER type of the faction that has one, otherwise none.
- **Combat (`combat-parry-warning.md`):** every nemesis body gets that spec's nemesis heavy-swing chance (30%, set through `AttackTiming.heavyChanceOverride`) and stagger x0.5; traits below change them.
- Every nemesis has exactly **one weakness**. A Commander or higher loses it with 25% chance per won challenge, and gets a new one rolled at the next rank (design choice: nobody is unbeatable for long).

### Traits

Datapack `data/<namespace>/nemesis_traits/<id>.json`, reloadable: `kind` (`strength`, `weakness`, `scar`), `effect` (one of a fixed set in code), effect numbers, `name` lang key, optional `sides` (`good`, `evil`), `races` (Middle-earth race ids) and `factions`. Data chooses and tunes; code has the effects. Starting set (our own names and numbers):

| Strength | Effect |
| --- | --- |
| Iron-hide | -40% projectile damage |
| Bloodthirsty | heals 20% of the melee damage it deals (evil only) |
| Armour-breaker | its melee ignores 25% of the target's armour |
| War-horn | once per manifestation, at 50% health or less, calls 3 soldiers of its faction |
| Unshakeable | knockback immune; cannot be slowed |
| Parry master | NPC parry chance (`combat-parry-warning.md`, `NpcParry`) +20%, above that spec's 40% maximum up to 60%; parry cooldown 20 ticks instead of 40 |
| Crushing blow | heavy-swing chance 50% instead of the nemesis 30% (`AttackTiming.heavyChanceOverride`); heavy swings are unblockable and show the red warning marker, as that spec defines |
| Oath-bound / Hate-fuelled | +25% damage against players it holds a grudge on (name by side) |

| Weakness | Effect | Dies for good when killed by |
| --- | --- | --- |
| Fears fire | x1.5 fire damage; flees 5 s when set alight | fire |
| Arrow-shy | x1.4 projectile damage | a projectile |
| Open guard | x1.5 damage from behind (attacker more than 90 degrees off its facing) | a hit from behind |
| Telegraphs | never makes heavy swings (override 0%); stagger x2 instead of the nemesis x0.5 | a hit landed with the counter bonus after a perfect parry |
| Sun-sick | -25% damage in direct daylight (orc, uruk, goblin and snaga races only) | any hit while it is sun-sick |

- **Scars** come from cheat-death: -50% damage of the type that "killed" it (fire, projectile, explosion, fall, drowning, melee), plus a scar title.
- **Intel:** strengths show on the Nemeses screen once any player has fought it. Its weakness shows to a player after that player hits its weak spot once. Hidden traits show "?".
- Unknown trait ids in a record are dropped on load with one WARN.

### Names

- **Data (ours, original words):** `data/lotr_revival/nemesis_names/<race path>.json` (`{ "first": [ ... ] }`, per Middle-earth race id path: `orc`, `uruk`, `goblin`, `snaga`, `human`, `elf`, `dwarf`; `any` as fallback) and `data/lotr_revival/nemesis_names/titles.json` (`[{ "text": "Skull-Splitter", "side": "evil|good|any", "traits": ["bloodthirsty"] }]`). Titles that match one of its traits are preferred.
- **Never** a canon unique name, and never strings from Middle-earth's or the original mod's lang files.
- Display `<First> <Title>`, rank line "Captain of Mordor" (faction name from Middle-earth's faction lang). Unique per faction (up to 10 re-rolls, then a numeral is added).
- Cheat-death replaces the title with the scar title ("the Burnt", "the Arrow-Ridden").
- The body's custom name is set to the display name; players cannot rename it (a name tag is refused and not used up).

### Bodies and persistence

- **Record** in `SavedData` `lotr_revival_nemeses` (overworld data storage), keyed by a nemesis id (UUID): faction, NPC type, race, name parts, renown, rank, traits, weakness, scars, intel (who knows what), cheat-death count, grudges (player UUID -> score, dormant flag), gear snapshot, status, manifestation number, body UUID, last seen dimension, position and game time, creation time, last time a non-dormant grudge holder was online.
- **Status:** `AT_LARGE` (no body), `MANIFEST` (one body exists), `DEAD` (kept 7 in-game days for the screen, then removed).
- **Body:** an ordinary Middle-earth `NpcEntity` of the record's type (built with `NpcEntityBuilder`), with the entity attachment `lotr_revival:nemesis_link` (nemesis id, manifestation number). At most one body per record. The manifestation number goes up on every spawn; a body that loads with an older number is removed silently.
- **Retreat:** bodies never despawn naturally. Outside a world event, when no player is within 128 blocks for 60 s or its chunk unloads, the body retreats (removed, record `AT_LARGE`). A freshly risen NPC retreats 3 s after its kill.
- **Server cap:** at most `max_manifest` bodies at once (default 8), world events included.

### Escape and cheat-death

- **Escape:** once per manifestation, when a hit would leave it at 20% health or less: Marked 50%, Captain 40%, Commander 30%, Warlord 20%, Overlord 10%, +20% if it holds a grudge of 5 or more on the attacker. Smoke, a taunt, body removed, `AT_LARGE`, renown +4.
- **Cheat-death:** when the body dies: 30% the first time, 15% the second, never after two. The body dies normally (Middle-earth loot rules, kill credit, reputation). The record stays `AT_LARGE`, renown +6, gains the scar for the damage type and its title, grudge on the killer +2.

### Dying for good

The record becomes `DEAD` when:

1. it dies and cheat-death fails, or it already cheated death twice;
2. it dies to its weakness (table above);
3. it loses a challenge;
4. it **fades**: no non-dormant grudge holder has been online for 30 real days (`fade_days`), or it is the lowest-renown Marked of a faction over its seat count;
5. an operator kills it by command.

There is no hiring of nemeses (kept from the old spec).

### Reputation

- Killing a nemesis body (death or cheat-death, not escape) calls `Reputation.onKill(killer, victim, 25)` (`nemesis_reputation_multiplier`, default 25 for every rank; hiring's rule then gives the gains and losses, and a hired unit's kill credits its owner at 50%).
- World event win bonuses (Participation) go through the reputation project's add entry point (see Open questions).

### Sergeants (deferred here from hiring)

- When a nemesis body dies or cheats death and a hired unit of level 5 dealt damage to it in its last 30 s, the unit with the most damage among them becomes a **sergeant** (if it is not already one, and its owner has fewer than `max_sergeants`, default 3).
- A sergeant gets a name and title from our banks (its own race and side), one strength rolled from the strengths allowed for its side and race, and its name shown above it. Traits work the same as on nemeses. No rank multipliers, no weakness, no boss bar.
- Stored in the entity attachment `lotr_revival:sergeant` (name parts, trait id), owned by the nemesis package. Hiring code is not changed: the unit's owner and level are read from the hiring project's `lotr_revival:unit` attachment.
- **Design choice:** a sergeant that dies loses the title (its fallen record is hiring's and does not carry our attachment); the owner gets "Sergeant <name> has fallen." See Open questions.

### Politics (off-screen only)

Every 30 minutes of server time, for each enabled faction with at least 2 `AT_LARGE` nemeses, 60% chance of one:

| Event | Weight | Rule |
| --- | --- | --- |
| Challenge | 3 | A challenger fights the lowest-renown holder of the rank above. Power = renown + 1d30 each; the winner takes the rank and +8 renown; the loser is `DEAD`. |
| Ascension | 1 | A random nemesis gets +6 renown. |

Rumour lines go only to the grudge holders of either party who are online.

### Ambush rules (rare; own schedule)

| Thing | Value |
| --- | --- |
| Check | every 30 s per online player, staggered by player |
| Eligible player | survival or adventure, not Peaceful, in an allowed dimension, online at least 15 min this session, more than 128 blocks from their respawn point and from world spawn, not riding fast travel (`node-map-travel.md`), governor not Red |
| Candidates | `AT_LARGE` records with a non-dormant grudge of 2 or more on the player, of a faction the player is an enemy of |
| Cooldown | 75 min of online time after an ambush against them (60 min if they won) |
| Chance after cooldown | 1% x (1 + 0.25 x highest grudge, at most 3) per check: roughly one ambush per 85-110 min of eligible play |
| Spawn spot | surface 16-28 blocks away, out of the player's line of sight if possible, not water; skip the check if no spot in 16 tries |
| Leave | no grudge holder within 64 blocks for 30 s: retreats, no renown |

### World events

A world event is a short, bounded scene staged near a player. It holds its own chunk tickets so it plays out whether or not anyone watches, uses a structure as its stage when one is near, shows on the node map, and ends in an outcome that changes reputation and nemesis records.

#### Two sides

- Every event has a **lead side** (the NPCs whose objective it is) and an **opposing side** (who try to stop them), each one or more factions.
- **Players pick a side by what they do.** A player whose chosen faction is not HOSTILE to a side's faction and whose reputation with it is 0 or more is that side's **ally**: its NPCs do not target them (normal Middle-earth diplomacy already gives this for most cases; the event adds nothing else). Anyone who damages an event NPC becomes a participant of the other side.
- **Same scene, two names.** Marker titles and hints follow the viewer: an orc raid on a Dale settlement is "Raid on <venue>" to an evil player and "Defend <venue>" to a good one.
- **Side balance when staging:** for a player of side S, 60% of events have S leading (things to help), 40% have S's enemies leading (things to stop). Unsided players: 50/50.

#### Event types (first set)

| Type | Lead | Opposition | Participants (lead / opposing) | Lead wins when | Opposition wins when | Win bonus (reputation) |
| --- | --- | --- | --- | --- | --- | --- |
| **Ambush** | a nemesis with a grudge (Ambush rules) | the target player and anyone helping | nemesis + bodyguards / players | target dies, or flees more than 64 blocks | nemesis dies or escapes | lead: renown +5; opposition: the nemesis kill only |
| **Warband clash** | a band of faction A | a band of faction B, HOSTILE to A | leader + 4-6 / leader + 4-6 | last side standing | last side standing | +15 with each winning faction |
| **Muster** | a nemesis gathers its band at a venue of its faction (or open ground) | none unless players attack | nemesis + bodyguards + 2-4 | undisturbed for the duration: renown +3, +1 bodyguard at its next manifestation | the nemesis dies or escapes | opposition: +20 with each faction HOSTILE to the nemesis's faction (shared rule below) |
| **Raid / defence** | raiders of faction A attack a `village` venue owned by faction B (B HOSTILE to A) | the settlement's resident NPCs plus 2-4 spawned guards of B | leader + 5-7 / residents + guards | half of the defenders dead, or 5 min survived with the leader alive | all raiders dead or fled | lead: +15 with A; defence: +20 with B |

- The lead of a clash or raid is a nemesis of A when an `AT_LARGE` one is free and the cap allows (records with a grudge on the staged-for player first, then highest renown), otherwise an NPC of A's LEADER rank pool (which never rises from an event alone).
- Spawned NPCs count for normal kill reputation. Participants carry the entity attachment `lotr_revival:world_event` (event id, side, role) and are removed 30 s after the event ends (immediately if unloaded).

#### Picking factions and NPC types (reuses Middle-earth's biome events)

Middle-earth's `biome_event` registry maps each biome to a weighted list of the NPC types that spawn there (and `structure_event` does the same for some structures). The add-on **reads** these tables; it never changes or replaces them.

- **Factions present at a site:** the factions of the NPC types listed in the site's `structure_event` entry (if the site is in such a structure), else its `biome_event` entry, else the default entry. Only GOOD and EVIL factions count.
- **Lead faction:** a present faction of the side chosen by the side balance. If none is present for that side, use the other side; if no faction is present at all, the site is rejected.
- **Opposing faction:** a present faction HOSTILE to the lead; else the lead's **sworn enemy** from hiring's `sworn_enemies` data; else any HOSTILE faction of the other side with a LEADER rank pool. For a raid it is the venue's owner.
- **NPC types:** for each faction, its rank pools (leader from LEADER, others from SOLDIER and KNIGHT). When the faction's types appear in the site's biome table, their table weights weight the pick, so a band looks like the NPCs that live there. The tables' own spawn conditions (light, height, same-type limits) are ignored for event spawns; the event's own caps apply.
- **Design choice:** the event hint uses our own lang keys, not the tables' broadcast keys.

#### Venues (structure tags from `structures.md`)

- Events prefer to happen in structures. `structures.md` defines the venue structure tags and their owning faction; this spec uses the kinds `outpost`, `camp`, `ruin`, `bridge` and `village` (tag names as `structures.md` defines them, expected `#lotr_revival:event_venues/<kind>`). The `village` tag may also list Middle-earth's own settlement structures by id.
- **Lookup:** structure starts in **already loaded** chunks inside the distance band only; the lookup never loads or generates chunks. At most one lookup per roll, at most 64 chunk checks, results cached per 512-block region for 5 min.

| Type | Preferred venues | Fallback |
| --- | --- | --- |
| Ambush | none: happens where the player is | n/a |
| Warband clash | `bridge`, `ruin`, `outpost` of either faction | open ground |
| Muster | `camp` or `outpost` of the nemesis's faction, `ruin` | open ground |
| Raid / defence | `village` owned by a faction (required) | none: the type is not picked |

- **Using a venue:** the event centre is the structure's centre; participants spawn on open floor inside its bounds (two air blocks over a solid block, not water). If fewer than half of the participants can be placed, the venue is skipped and gets the venue cooldown.
- **Venue cooldown:** one event per venue per 30 min.

#### Scheduling

| Thing | Value | Notes |
| --- | --- | --- |
| Roll | every 30 s per online player, staggered | ambushes use their own schedule |
| Eligible player | survival or adventure, not Peaceful, in an allowed dimension, online at least 3 min this session, no event staged for them right now | |
| Chance per roll | 5% | with the cooldown, about one event every 10-20 min per player |
| Per-player cooldown | 5 min after the last event staged for them ended | |
| Distance band | 64-256 blocks from the player | |
| Site checks | surface, not water; more than 128 blocks from world spawn and from any player's respawn point; not within 96 blocks of another active event; for open ground, height varies by 3 or less over 9x9 blocks | |
| Type weights | warband clash 3, muster 2, raid / defence 2 | after the side balance; types whose venue or factions are missing are filtered out; up to 4 redraws |
| Hint | action-bar line with direction and rough distance to the player it was staged for; node-map marker for everyone in range | |

#### Load governor (design choice: simplified)

| State | Enter when | Effect |
| --- | --- | --- |
| Green | average tick time over the last 100 ticks below 35 ms | normal |
| Yellow | 35-45 ms, or chunk budget at 75% or more | event chance halved; no raids; at most 4 chunks per new event |
| Red | above 45 ms, or chunk budget full | no new events and no ambushes (skipped rolls start no cooldown); after 30 s in Red, active events with no player within 64 blocks resolve off-screen, oldest first |

- A state is held at least 10 s; dropping a level needs the average 5 ms below that level's entry value.
- The tick-time source is injectable for tests.

#### Chunk loading

Our own NeoForge ticket controller `lotr_revival:world_events` (registered in `RegisterTicketControllersEvent`), forced ticking tickets owned by the event id.

| Limit | Value |
| --- | --- |
| Concurrent events, server | 2 + 1 per 3 eligible online players, at most 8 |
| Events staged for one player | 1 at a time, plus at most one ambush |
| Forced chunks per event | 9 (3x3 around the centre), 4 in Yellow |
| Chunk budget, total | 48 |
| Entities per event | 16; total across events 120 |
| Duration | at most 8 min (ambush 5) |
| Release | on end; when the staged-for player logs out, leaves the dimension or is more than 384 blocks away for 60 s (handover to another participant within 128 blocks first, else off-screen resolution); Red shedding; server stop |
| On server start | the controller's validation callback drops every leftover ticket; events never resume |

Ambushes use no forced chunks (they happen 16-28 blocks from the player).

#### Participation and credit

- **Owner:** the player the event was staged for (hint, cooldown, release and handover rules).
- **Participant:** any player who, within the event radius + 32 blocks while it is active, damages an event NPC or is damaged by one. Their side is the side whose enemies they damaged most.
- **Win bonus:** the winning side's bonus goes once to each participant of that side who dealt at least 20% of all player damage to the losing side, or who was within 32 blocks of the event centre at the end. Hired units' damage counts for their owner.
- Players on opposite sides at one event fight each other only under the server's PvP setting; events never grant renown, grudges or reputation for player kills.

#### Outcomes

- **On screen:** ends when a side wins, the time runs out (lead loses, except Muster where the lead wins), or one side is all dead or fled. Survivors walk off and are removed 30 s later.
- **Off-screen** (released early): power per side = sum over its NPCs (militia or soldier 1, knight or veteran 2, leader 3, nemesis 3 + rank index) + 1d10; the losing side's nemeses die with normal cheat-death rules; no win bonus for anyone.
- **Into the records:** the winning side's nemeses +3 renown; deaths by the normal rules.
- **Rumour:** one grey chat line to players within 384 blocks and to online grudge holders of any nemesis in it.
- **Game events (NeoForge bus):** `WorldEventStartedEvent` (cancellable), `WorldEventEndedEvent` (type, outcome, venue, participants and sides).

#### Map markers

- Active events plus the last 10 ended ones (kept one in-game day) are known to the server; the ended list is saved with the event SavedData.
- S2C `WorldEventsPayload`: every event within 1024 blocks of the player in their dimension, plus any event with a nemesis holding a grudge on them, at most 16, nearest first. Sent on login, dimension change, and when an event in that set starts, changes state or ends. Entry: event id, type id, centre x/z, radius, venue kind or none, state (`active`, `ended`), outcome, title (lang key and arguments, chosen for this viewer's side), faction colours, start and end game time, whether it is staged for this player.
- The client keeps these in `EventMarkerCache`; the node map draws them through the marker hook defined in `node-map-travel.md` (icon per type, faction colour, tooltip with title and time left; own events highlighted; ended events faded). Events are not fast-travel targets.

### Config

`[nemesis]`: `enabled`, `disabled_factions` (empty), `allowed_dimensions` (`middle-earth:middle_earth`), promotion chances, renown values, rank thresholds, seats and stat multipliers, `max_manifest` (8), retreat distance (128) and delay (60 s), escape and cheat-death chances, `nemesis_reputation_multiplier` (25), `fade_days` (30), politics period (30 min) and weights, ambush check (30 s), chance (1%), cooldowns (75 / 60 min), safe radius (128), `max_sergeants` (3), screen key.
`[events]`: `enabled`, roll period (30 s), chance (5%), cooldown (5 min), band (64, 256), type weights, side balance (0.6), caps (`max_events_base` 2, `players_per_extra_event` 3, `max_events` 8), `max_chunks_per_event` (9), `chunk_budget` (48), entity caps (16 / 120), durations, release distance (384), spawn exclusion radius (128), event spacing (96), venue cooldown (30 min), lookup budget (64) and cache time (5 min), win bonuses, governor thresholds (35 / 45 ms, 5 ms hysteresis, 10 s hold, 30 s shed delay).
Config lists are built with `Arrays.asList`.

### Performance limits

- No per-tick scans: event and ambush rolls every 30 s per player (staggered), politics every 30 min, body checks every 2 s on existing bodies only (at most `max_manifest`), governor reads the tick average once per second.
- Records: at most 29 seats per faction plus challengers and `DEAD` records; the SavedData is written only when dirty.
- Venue lookups: loaded chunks only, one per roll, 64 chunk checks, cached.
- Sync: Nemeses screen snapshot on request (at most once per 2 s per player, at most 60 cards); `WorldEventsPayload` at most 16 entries, sent on change only.

## Edge cases

- **Multiplayer:** one shared war-host per faction, grudges per player. A good and an evil player on one server see the same hosts; each is hunted by the other side's. Two grudge holders together: the nemesis targets the higher grudge; both see the boss bar.
- **Other players' events:** visible within 1024 blocks and open to anyone. A player can own one event and take part in others.
- **Owner logs out during an event:** handover to a participant within 128 blocks, else off-screen resolution and release.
- **Death in an ambush:** counts as a kill (renown, grudge); the body retreats 5 s later.
- **Hired units:** valid targets for nemeses and event NPCs; their kills of a nemesis credit their owner by hiring's 50% rule; their deaths can promote (rules above). Units that follow their owner to an event are participants on the owner's side.
- **Disguise (hiring):** a disguised player is not seen as an enemy by the disguised faction's NPCs, so they cannot cause a rise from that faction while disguised. A nemesis holding a grudge on a player sees through the disguise at 16 blocks (it ends as if a leader discovered it).
- **Killed by other NPCs, beasts or the environment:** cheat-death and dying-for-good rules apply; rumour to grudge holders. No reputation.
- **Side switch mid-ambush:** the grudge goes dormant, but the current body fights on until it retreats or dies.
- **Server stop during an event:** tickets released; participants saved in chunks are removed on load (their event id is unknown); nemeses in it count as `AT_LARGE`.
- **Stale or duplicate bodies:** a body with an older manifestation number, or of a `DEAD` record, is removed on load without drops.
- **Datapack removes a faction or NPC type, or config disables a faction:** its records stay, never manifest, are not chosen for events, and fade on schedule. An event whose faction disappears mid-run resolves off-screen.
- **Middle-earth biome table missing for a biome:** the default entry is used; if it lists no GOOD or EVIL faction, the site is rejected.
- **Venue griefed by players:** still returned by the lookup; the placement rule above skips it if there is no room.
- **Peaceful:** no rises, ambushes or events; bodies retreat; running events end off-screen.
- **Fast travel (`node-map-travel.md`):** a player travelling is not eligible for rolls; a staged event whose owner travels more than 384 blocks is handed over or resolved off-screen.
- **Dimension change:** bodies and events exist only in `allowed_dimensions`; NPCs never change dimension.

## Data and APIs

- **SavedData:** `lotr_revival_nemeses` (records, politics timer, per-player ambush cooldown and online-time counters) and `lotr_revival_world_events` (ended-events list, per-player event cooldowns, venue cooldowns). Both in overworld data storage.
- **Entity attachments:** `lotr_revival:nemesis_link` (nemesis id, manifestation number; saved; nemesis id synced for the client name plate), `lotr_revival:world_event` (event id, side, role; saved), `lotr_revival:sergeant` (name parts, trait id; saved, synced).
- **Player attachment:** `lotr_revival:nemesis_player` (last seen Middle-earth faction for side-switch detection, side-switch time; saved).
- **Datapack:** `data/<ns>/nemesis_traits/*.json`, `data/lotr_revival/nemesis_names/*.json`, tag `data/lotr_revival/tags/middle-earth/npc_type/never_nemesis.json`, our lang lines (`lotr_revival.nemesis.*`, `lotr_revival.event.*`). Event type ids are fixed in code for this first set (`lotr_revival:ambush`, `warband_clash`, `muster`, `raid`).
- **Read from other add-on systems:** hiring's `Reputation.onKill`, the reputation add entry point, `sworn_enemies` data, the `lotr_revival:unit` attachment (owner, level); `structures.md` venue tags; `node-map-travel.md` marker hook and the "is travelling" query; `combat-parry-warning.md` hooks for the parry traits.
- **Middle-earth used (read only):** `NpcEntity` (type id, faction id, structure-manager host position, equipment), `NpcEntityBuilder`, `NpcType` (race, mount data), `Faction` / `FactionLookup` (disposition, rank pools via `getAllNpcDatas`, `isHostileToward`, `getRandomGear`, banner colour for markers), `PlayerData` chosen faction, `DynamicRegistriesME.BIOME_EVENT` and `STRUCTURE_EVENT` with `BiomeEventData`, `ModDimensions.ME_WORLD_KEY`.
- **Read API for other systems:** `NemesisApi` (`find(id)`, `bodyOf(entity)`, `atLarge(faction)`, `grudges(player)`, `side(player)`, `isEnemy(npcFaction, player)`).
- **Game events (NeoForge bus):** `NemesisRisenEvent`, `NemesisRankChangedEvent`, `NemesisDefeatedEvent` (`ESCAPED`, `CHEATED_DEATH`, `DEAD`; cause; killer), `NemesisAmbushEvent` (cancellable), `WorldEventStartedEvent` (cancellable), `WorldEventEndedEvent`.
- **Packets (CustomPacketPayload):** C2S `NemesisRosterRequest`; S2C `NemesisRosterPayload` (cards with name, title, rank, faction, scars, revealed traits, grudge on the viewer); S2C `WorldEventsPayload`. Boss bars use vanilla `ServerBossEvent`.
- **Commands** (op level 2): `/lotr_revival nemesis list [faction] | info <id> | spawn <faction> [rank] | renown <id> <amount> | kill <id> | grudge <id> <player> <amount>` and `/lotr_revival event list | start <type> [player] | end <id>`.

## Logging

Loggers `LotrLog.get("nemesis")` and `LotrLog.get("events")` (`debug-logging.md`). Ids: nemesis id short form, entity UUID short form, faction id, event id, venue structure id; positions `x,y,z`; game tick.

| Level | Logger | When |
| --- | --- | --- |
| INFO | nemesis | on server start: records loaded per status and faction; trait and name files loaded (count, ids); config values in use |
| INFO | events | on server start: tickets dropped by validation; config values in use; every governor state change |
| WARN | nemesis | unknown trait id dropped; record of an unknown faction or type; a name bank empty for a race (fallback used); `max_manifest` reached |
| WARN | events | venue tag missing or empty (fallback to open ground); no faction present at a site; chunk budget or entity cap reached; a handover or release that found no player |
| ERROR | both | an exception caught in a tick handler, listener or SavedData load, with the stack trace |
| DBG | nemesis | each kill considered for a rise: `[DBG nemesis] rise roll: npc=3fa2c1 type=middle-earth:mordor.warrior rank=SOLDIER victim=player:9b1e enemy=true chance=0.35 roll=0.21 -> risen id=7c4d name="Gashnak Skull-Splitter"`; renown and rank changes (from, to, reason); grudge changes; manifest and retreat (reason); escape and cheat-death rolls (chance, roll, result); dying for good (rule number); politics outcomes; ambush checks that pass eligibility (chance, roll); sergeant promotions |
| DBG | events | each roll for a player (eligible or why not, chance, roll); type, side and faction picks with the site's present factions; venue lookup (chunks checked, kind found or fallback); placement counts; state changes; participants joining with their side; outcome (on or off screen, power rolls); win bonus payouts; ticket add and release (chunks, budget used) |

- Summary line every 600 ticks while debug is on: `nemesis` (records per status, bodies, rises, deaths, ambush checks and ambushes), `events` (rolls, staged, ended by outcome, active, chunks used, governor state).
- No per-tick lines; body checks and participant tracking are counted into the summary.

## Out of scope

- Hiring or dominating nemeses; promotion from surviving a fight (kept from the old spec).
- Politics beyond challenge and ascension: cross-faction feuds, alliances, betrayal, staged feud duels.
- Event types of the old spec not in the first set: beast hunt, captain hunting, prisoner rescue, ranger hunt, border patrol, caravan escort, border crossing, mine defence, lighting the beacon, and war camps (no event places blocks in this spec).
- Territory control and land protection (Middle-earth has no such system yet).
- Invasion leaders, hunt quests, achievements and titles.
- Nemesis-specific models, textures or animations beyond scale, gear, mount and the boss bar.
- Sergeant ranks above one, sergeants surviving death, sergeant auras (formation code is not touched).
- A data-driven event type registry (types are fixed in code for now).
- Events in other dimensions; player-vs-player nemeses.

## Acceptance tests

All game tests use `@GameTest(template = "empty")`, live in `nemesis/test/` and `events/test/`, run headless in CI, and log START/PASS/FAIL through the `test` logger. Tests override `allowed_dimensions` to the test level and use an injectable random source (`NemesisRandom.forTest(seed)` or forced roll results). Mock players come from `makeMockServerPlayerInLevel` and are removed in a `finally`; a test helper sets their Middle-earth chosen faction and add-on reputation.

- [ ] GameTest `sides`: a Gondor player is good, a Mordor player evil, a brigand or no-faction player unsided; a Mordor warrior is an enemy of the Gondor player, not of the Mordor player, and becomes an enemy of an unsided player once their Mordor reputation is below 0.
- [ ] GameTest `eligibility`: a Mordor warrior (SOLDIER) and captain (LEADER) can rise; a snaga (CIVILIAN), militia, a hired unit, a structure-bound NPC, a named NPC, a brigand and a `never_nemesis` type cannot.
- [ ] GameTest `rise_on_player_kill`: with a forced roll, a Mordor warrior killing a mock Gondor player creates a Marked record of Mordor with grudge 3, a name from our banks, one strength and one weakness, and the body retreats within 3 s; with the roll forced to fail, nothing is created; in creative, nothing.
- [ ] GameTest `rise_on_unit_kill`: a nemesis-eligible NPC killing a level-3 hired unit uses chance 16% and gives the owner grudge 1.
- [ ] GameTest `ranks_and_seats`: renown thresholds map to ranks; a ninth Captain-worthy record stays Marked as a challenger; a challenge with a fixed seed promotes the winner, marks the loser `DEAD` and swaps rank modifiers.
- [ ] GameTest `rank_modifiers`: a Commander body has x2.2 max health and x1.35 attack damage over its type's base; changing rank twice leaves exactly one modifier per attribute.
- [ ] GameTest `body_rules`: no player within 128 blocks for 60 s retreats the body (`AT_LARGE`); a second spawn bumps the manifestation number; a body loaded with the old number is removed; never two bodies for one record.
- [ ] GameTest `escape_cheat_death`: with fixed rolls, a hit to 15% health escapes a Marked nemesis (+4 renown); a death with a passing roll keeps it `AT_LARGE` with a scar and scar title; a third death, or a death by its weakness, makes it `DEAD`.
- [ ] GameTest `reputation_hook`: killing a nemesis body calls `Reputation.onKill` with multiplier 25 exactly once; an escape calls nothing.
- [ ] GameTest `traits`: Iron-hide cuts projectile damage by 40%; Arrow-shy raises it by 40%; Open guard applies only from behind; an unknown trait id in a saved record is dropped with a WARN.
- [ ] GameTest `side_switch`: changing a player's Middle-earth faction to the other side makes the new side's grudges dormant; dormant grudges decay 1 per in-game day; switching back wakes them.
- [ ] GameTest `ambush_eligibility`: Peaceful, creative, under 15 min online, inside the safe radius, inside the cooldown, the cap reached or a Red governor each block an ambush; otherwise a forced roll spawns the nemesis 16-28 blocks away with its bodyguards and a boss bar.
- [ ] GameTest `sergeant`: a level-5 hired unit that damaged a nemesis in its last 30 s gets the sergeant attachment with a name and one strength when the nemesis dies; a level-4 unit does not; the owner's cap of 3 holds.
- [ ] GameTest `persistence`: records, grudges, intel, cooldowns and the ended-events list survive a SavedData save and load round trip.
- [ ] GameTest `site_factions`: with a stub biome table listing Mordor and Gondor types, a clash picks one as lead and the other as opposition; with only Mordor listed it picks Mordor's sworn enemy (Gondor) from hiring's data; with no GOOD or EVIL faction the site is rejected.
- [ ] GameTest `scheduler`: rolls respect the band, the spawn and respawn exclusion, the 96-block spacing, the per-player cooldown and the player-scaled server cap; side balance over 1,000 seeded rolls is 60/40 within 3 points.
- [ ] GameTest `governor`: injected tick times move Green -> Yellow -> Red and back with the 10 s hold and 5 ms hysteresis; Red sheds an event with no player near and releases its chunks.
- [ ] GameTest `tickets`: an event forces at most 9 chunks (4 in Yellow), the total never exceeds 48, and tickets are released on end, on owner logout and on shedding.
- [ ] GameTest `venues`: with a structure placed in a loaded chunk and a test venue tag, a muster uses it and spawns participants inside its bounds; with no venue it falls back to open ground; a raid without a `village` venue is never picked; the venue cooldown blocks a second event.
- [ ] GameTest `event_outcomes`: a scripted warband clash (one side killed by the test) ends with the right winner, pays +15 to a participant over 20% damage and nothing to one under it without being near the centre; an off-screen resolution with a fixed seed gives the expected winner and nemesis deaths.
- [ ] GameTest `events_payload`: a player gets events within 1024 blocks (including another player's) and grudge events further away, at most 16, nearest first, with the title key for their side and the own flag set only on their event.
- [ ] Playtest: die to Mordor orcs until one rises, see it on the Nemeses screen, get ambushed, scar it, kill it for good with its weakness; get a Gondor captain risen against an orc-aligned player; see events every 10-20 min, ride to one at a structure and swing it; watch a friend's event on the node map; get a sergeant.

## Tickets

Each 0.5-2 days. Paths are under `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/`. The nemesis tickets come first; events start after ticket N1 and N2. Nothing here edits hiring or formation code; reads go through hiring's public API and attachments.

**Nemesis (`nemesis/`)**

| # | Files | Scope | Size |
| --- | --- | --- | --- |
| N1 | `nemesis/NemesisRecord.java`, `nemesis/NemesisSavedData.java`, `nemesis/NemesisLink.java`, `nemesis/NemesisConfig.java`, `nemesis/NemesisRandom.java`, `nemesis/test/NemesisTestPlayers.java` | record and codec, SavedData, link attachment, config section, injectable random, test helper for mock players with a faction and reputation; `persistence` test | 1.5 d |
| N2 | `nemesis/Sides.java`, `nemesis/MeRanks.java`, `nemesis/NemesisPlayerData.java` | faction side from disposition, player side, enemy check (diplomacy or reputation), rank from rank pools, eligibility, side-switch detection, dormant grudges and decay; `sides`, `eligibility`, `side_switch` tests | 1 d |
| N3 | `nemesis/NemesisTraits.java`, `nemesis/NemesisNames.java`, data under `resources/data/lotr_revival/nemesis_traits/` and `nemesis_names/`, lang | trait and name loaders, starting trait set, our own name banks and titles | 1 d |
| N4 | `nemesis/TraitEffects.java` | effect code for every starting strength, weakness and scar; parry traits through `combat-parry-warning.md` hooks (stub until that lands); `traits` test | 2 d |
| N5 | `nemesis/Promotion.java`, `nemesis/Renown.java` | kill listener for player and unit kills, rolls, renown, grudges, seats and challengers, rise chat lines, `NemesisRisenEvent`, `NemesisRankChangedEvent`; `rise_on_player_kill`, `rise_on_unit_kill`, `ranks_and_seats` (rank part) tests | 1.5 d |
| N6 | `nemesis/Manifestation.java`, `nemesis/NemesisBossBar.java` | spawning bodies with `NpcEntityBuilder`, rank modifiers, gear snapshot and leader gear, bodyguards, mounts, custom name, retreat and stale-body rules, `max_manifest`, name-tag refusal; `rank_modifiers`, `body_rules` tests | 2 d |
| N7 | `nemesis/DeathRules.java` | escape, cheat-death, scars, weakness deaths, dying for good, `Reputation.onKill(..., 25)`, `NemesisDefeatedEvent`; `escape_cheat_death`, `reputation_hook` tests | 1.5 d |
| N8 | `nemesis/Ambush.java` | ambush schedule, eligibility, cooldowns, spawn spot, taunt, leave rule, `NemesisAmbushEvent`; registers as the `ambush` event type with E1 when present; `ambush_eligibility` test | 1.5 d |
| N9 | `nemesis/Politics.java` | challenge and ascension, fading, `DEAD` purge after 7 days, rumour lines; `ranks_and_seats` (challenge part) | 1 d |
| N10 | `nemesis/Sergeants.java`, `nemesis/SergeantData.java` | damage tracking by level-5 units, promotion, attachment, name plate, owner cap, fallen message; `sergeant` test | 1 d |
| N11 | `nemesis/NemesisApi.java`, `nemesis/NemesisCommands.java` | read API and op commands | 0.5 d |
| N12 | `nemesis/network/NemesisRosterPayloads.java`, `nemesis/client/NemesisScreen.java`, `nemesis/client/NemesisKeys.java` | roster request and payload (rate-limited), list screen by faction and rank, key binding | 1.5 d |

**World events (`events/`)**

| # | Files | Scope | Size |
| --- | --- | --- | --- |
| E1 | `events/WorldEvent.java`, `events/WorldEventType.java`, `events/WorldEventManager.java`, `events/WorldEventSavedData.java`, `events/EventParticipant.java` | event state machine, type interface, participant attachment, end and cleanup, rumour lines, started and ended game events, SavedData | 2 d |
| E2 | `events/EventScheduler.java`, `events/LoadGovernor.java` | per-player rolls, eligibility, band and site checks, side balance, type weights, caps and cooldowns; governor with injectable tick source; `scheduler`, `governor` tests | 1.5 d |
| E3 | `events/EventChunkTickets.java` | ticket controller, per-event and total budgets, release rules, start-up validation; `tickets` test | 1 d |
| E4 | `events/SiteRoster.java` | factions present from Middle-earth's biome and structure event tables, lead and opposing pick with sworn-enemy fallback, weighted NPC type pick from rank pools; `site_factions` test | 1 d |
| E5 | `events/VenueFinder.java` | venue lookup over loaded chunks by structure tag, region cache, open-ground fallback, floor placement, venue cooldown; `venues` test (with a test tag) | 1.5 d |
| E6 | `events/Participation.java`, `events/OffscreenResolver.java` | participant tracking and sides, win bonus through reputation, handover, off-screen power rolls; `event_outcomes` test | 1.5 d |
| E7 | `events/types/WarbandClashEvent.java`, `events/types/MusterEvent.java` | the two types with their win rules and records updates | 1.5 d |
| E8 | `events/types/RaidEvent.java` | raid and defence with venue residents joining the defence | 1.5 d |
| E9 | `events/network/WorldEventsPayload.java`, `events/client/EventMarkerCache.java` | per-player payload selection and sending, per-viewer titles, client cache, node-map marker hook (stub until `node-map-travel.md` lands); `events_payload` test | 1 d |
| E10 | `events/EventCommands.java` | `/lotr_revival event` with `list`, `start` and `end` | 0.5 d |

## Open questions

1. **Reputation add entry point.** Hiring's spec names only `Reputation.onKill`. World event bonuses need a plain `Reputation.add(player, faction, amount, source)`. May the hiring project add it (a one-method ticket in the reputation package), or should events pay through `onKill` with a synthetic victim?
2. **Nemesis reputation multiplier:** keep x25 for every rank as hiring's spec says, or scale it by rank (for example Marked x15 up to Overlord x40)?
3. **Sergeants and death.** A fallen unit's record is hiring's and does not carry our attachment, so a sergeant loses the title when it dies (current design). Should hiring's fallen record carry an extra-data compound so a revived sergeant keeps its name and trait?
4. **Names.** Middle-earth NPCs have no personal names, so nemeses and sergeants use our own name banks. Who writes the banks (the maintainer, or a lead with a style guide), and how many first names per race are enough (suggested 40)?
5. **Event frequency:** one event every 10-20 min per player and one ambush per 1.5 h of play. Right pace for your server, or slower?
6. **Muster with no players:** an undisturbed muster grows the nemesis. Should musters only be staged for players whose side is the muster's enemy (so there is something to stop), or for anyone?
7. **Command root:** `/lotr_revival` is assumed; is there a shared add-on command root the other specs use?
8. **Governor thresholds:** 35 ms (back off) and 45 ms (stop) average tick time. Fine for your host?
