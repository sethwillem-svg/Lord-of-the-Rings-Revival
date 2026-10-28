# Nemesis system and world events

- **Pod:** npc
- **Lead:** Opus 3
- **Status:** draft (maintainer answers of 2026-10-28 applied)
- **Schedule:** after G3 (needs every faction's NPC types, mounts, faction captain loadouts and the map GUI). Invasion leaders, hunt quests and settlement raids land with or after G4.
- **Depends on:** `specs/npc/npc-base.md` (`NpcEntity`, classes, names, speech, kills, despawn, loadouts), `specs/npc/hiring-trading.md` (hired units, unit levels), `specs/core/core-apis.md` (alignment, pledge, kill path, `FactionRelations`, faction kinds, `LandProtection`, `FellowshipQuery`, influence), `specs/progression/quests-fellowships-banners.md` (kill quests, banners, achievements, titles, menu hub), progression map page (#88) and map GUI, `specs/world/*` (settlement locations, for raids). Invasion spec (S8, not written yet).
- **Beyond parity:** Legacy LOTR had no nemesis system and no world events of this kind. Nothing here comes from it; this is our own design. All numbers are ours and tunable through config.
- **Why not the Nemesis System (PAL) mod:** we read it (MIT) as a feature checklist only; it cannot promote our NPCs, has no API and needs Architectury and owo-lib, so a native system fits our factions better.

## Summary

Captains remember you. A captain of a faction you are at war with that kills you, or butchers your hired company, can become a **nemesis**: a named NPC with a title, a rank in its faction's war-host, strengths, a weakness, scars and a grudge against the players it has met. Orc captains rise against the Free Peoples' champions; Gondor, Rohan, Elven and Dwarven captains rise against the servants of the Shadow. Nemeses persist in saved data when no copy of them is loaded, feud and ally along faction lines, lead invasions, carry bounties posted by enemy factions, and act in the world through **world events**: things that happen a short ride from the player and show on the map, such as an ambush, a warband clash, a captain hunting beasts, a raid on a village or a prisoner waiting for rescue. Players can ride to an event and interfere; what happens there changes alignment and the hierarchy. Players care because Middle-earth stops being a field of interchangeable soldiers and starts having a war going on without them.

## Player-facing behavior

1. **Rise.** A captain or soldier of a faction you are an enemy of kills you or one of your hired units. A red chat line: "*Gashnak Skull-Splitter* of Mordor has risen to **Marked**." For an evil player: "*Hirgon the Unbending* of Gondor has sworn to hunt you." It retreats (vanishes).
2. **Hierarchy.** The Middle-earth menu (L) gains a **Nemeses** page: each enabled faction's war-host as a pyramid (Overlord on top), each card with name, title, rank, scars, known strengths and weakness, grudges, rivals and allies. Your grudges are highlighted; hosts of your own side are shown greyed.
3. **World events.** Now and then something starts 64-256 blocks from you: smoke and a chat hint ("Steel rings out to the north-east"), and a marker on the map. You can ride there and join in, or ignore it and hear how it ended.
4. **Ambush** is one kind of event, aimed at you: a nemesis that holds a grudge finds you, speaks a taunt naming you, and attacks with its bodyguards, a rank-coloured boss bar and, at high ranks, a mount.
5. **The fight.** Nemeses hit harder and have more health by rank and carry traits. Badly hurt, one may flee; killed, it may cheat death and return scarred. Exploit its weakness, or kill it after it has cheated death twice, and it dies for good.
6. **Rewards.** Killing a nemesis pays (or costs) alignment scaled by rank, counts for achievements and titles, and completes hunt quests that enemy factions post on it.
7. **Politics.** Rumours: a Captain challenged a Warlord and took his place; two Commanders swore alliance; a Mordor Warlord slew an Isengard rival.
8. **War.** Invasions may be led by a nemesis, preferably one that hates a player nearby.

## Rules and numbers

### Sides and who can become a nemesis

**Faction side.** A faction is on the **good** side if it has kind `free` (`#lotrrevival:kind/free`), otherwise **evil**. Factions with no NPC host (`hostile`, `unaligned`, `hostile_trees`, `utumno`, `ruffians`) never have nemeses.

**Player side**, recomputed whenever alignment or pledge changes (cached per player, not per tick):

1. Pledged: the pledged faction's side.
2. Not pledged: the faction with the largest absolute alignment. If that value is >= +100, its side; if <= -100, the opposite side; otherwise the player is **unsided**.
3. Ties: good wins (fewer surprise orc hunts for new players).

**Eligibility (symmetric).** An eligible NPC can rise against a player when the NPC's faction is enabled and the player is its **enemy**: the player's side is the opposite of the faction's side, or the player's alignment with that faction is below 0. So an unsided player can still make enemies of the faction they have been killing.

| Thing | Value | Notes |
| --- | --- | --- |
| Faction tag | `#lotrrevival:nemesis_factions` (npc pod writes it) lists every faction that *can* have nemeses | |
| Default on, evil | `mordor`, `isengard`, `angmar`, `dol_guldur`, `gundabad` | The five orc-led hosts. |
| Default on, good | `gondor`, `rohan`, `dunedain_north` (Rangers), `high_elf`, `wood_elf`, `lothlorien`, `durins_folk`, `blue_mountains`, `dale` | Factions with soldiers and captains. |
| In the tag, default off | `dunland`, `near_harad`, `far_harad`, `easterling`, `half_troll` | Config list `disabled_factions` holds these by default. Open question. |
| Not in the tag | `hobbit`, `bree`, `dorwinion`, `fangorn`, `harad_jungle` | No war-host to speak of. |
| Type opt-out | entity type tag `#lotrrevival:never_nemesis` | Trolls, ents, unique NPCs. |
| Classes | GUARD and CAPTAIN | Civilians never. |
| Must be | wild (not hired, not structure-bound quest/trader NPC), in Middle-earth, not name-tagged | |

**Switching sides.** When a player's side changes:

- Grudges held against them by nemeses now on **their own** side go **dormant**: those nemeses no longer ambush them, are not picked for events aimed at them, and do not count for their hunt quests. Dormant grudges lose 1 point per in-game day and are deleted at 0.
- Switching back within 7 in-game days wakes the remaining dormant grudges.
- Hunt quests whose target is now on their own side fail ("you no longer serve those who posted this").
- Nemeses of the new opposite side can rise against them from the next kill on. Existing records are never moved between factions.

### What promotes them (kills only)

A wild eligible NPC with no record gets one when the promotion roll passes; a nemesis just adds renown.

| Trigger | Renown | Promotion roll (new NPC) | Grudge |
| --- | --- | --- | --- |
| Kills a player who is its enemy (direct, or its projectile) | +10 | 35%, +20% if CAPTAIN class | that player +3 |
| Kills a hired unit of a player who is its enemy | +2, +1 per 5 unit levels (max +6) | 10%, +3% per 5 unit levels (max 30%) | owner +1 |
| Ambush kills its target again | +5 on top of the kill | n/a | +3 (revenge) |
| Escapes | +4 | n/a | attacker +1 |
| Cheats death | +6 | n/a | killer +2 |
| Wins a challenge or feud (politics or event) | +8 | n/a | none |
| Wins a world event (see Outcomes) | +3, +1 per enemy NPC it killed (max +5) | n/a | none |

Surviving a fight does **not** promote (maintainer decision). Players in creative or spectator, FakePlayers and Peaceful worlds never cause promotions or grudges.

### Ranks

Rank comes from renown; each faction's host has limited seats. A nemesis with enough renown for a full rank stays below as a **challenger**.

| Rank | Renown | Seats per faction | Health | Melee damage | Armor | Knockback res. | Strengths | Bodyguards | Extras |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Marked | 0 | 12 | x1.25 | x1.10 | +2 | +0.1 | 1 | 0 | white boss bar |
| Captain | 15 | 8 | x1.6 | x1.20 | +4 | +0.2 | 1 | 2 | captain loadout |
| Commander | 35 | 5 | x2.2 | x1.35 | +6 | +0.3 | 2 | 3 | yellow bar |
| Warlord | 70 | 3 | x3.0 | x1.50 | +8 | +0.4 | 3 | 4 | rides faction mount, purple bar |
| Overlord | 120 | 1 | x4.0 | x1.65 | +10 | +0.5 | 4 | 6 | red bar, war-banner bearer when banners exist |

- Multipliers apply to the NPC's race base values through attribute modifiers with fixed ids (replaced on rank change, never stacked). Movement speed +3% per rank above Marked (max +12%).
- Every nemesis has exactly **one weakness**. A Commander or higher loses it with 25% chance per won challenge ("overcame his fear of fire").
- Gear from npc loadout data: Marked uses the type's normal loadout; Captain+ the faction captain loadout; Warlord+ the loadout's best tier in every slot.
- Renown cap 200. Thresholds and seats are config.

### Traits

Datapack `data/<ns>/lotrrevival/nemesis_traits/<id>.json` (reloadable): kind (`strength`, `weakness`, `scar`), effect id, numbers, lang key, allowed factions/races/sides. Effects are a fixed set in code; data chooses and tunes them. Starting set:

| Strength | Effect |
| --- | --- |
| Iron-hide | -40% projectile damage |
| Fire-blooded | immune to fire and lava (evil only) |
| Bloodthirsty | heals 20% of melee damage dealt (evil only) |
| Armour-breaker | melee ignores 25% of target armor |
| War-horn | once per fight at <= 50% health, calls 3 soldiers of its type |
| Poisoned blade | melee poisons 4 s (evil only) |
| Unshakeable | knockback immune, cannot be slowed |
| Fleet | +15% speed, leaps at targets 4-10 blocks away (cooldown 8 s) |
| Hate-fueled / Oath-bound | +25% damage against a player it holds a grudge against (name per side) |
| Keen-eyed | sees through invisibility, +50% detection range (good only) |
| Shield-wall | blocks the first hit every 6 s if facing the attacker (good only) |

| Weakness | Effect |
| --- | --- |
| Fears fire | x1.5 fire damage; flees 5 s when set alight |
| Sun-sick | -25% damage in direct daylight (orcs only) |
| Open guard | x1.5 damage taken from behind |
| Arrow-shy | x1.4 projectile damage |
| Beast-fear | flees 5 s from wolves, horses and wargs not its own |
| Slow wits | does nothing for the first 2 s of every fight |
| Proud | refuses to flee and never escapes; x1.25 damage taken while below 30% health (good only) |

Scars come from cheat-death: -50% damage of the type that "killed" it, plus a scar title.

**Intel:** strengths show on the hierarchy page once any player has fought it; the weakness shows to a player after they hit its weak spot, after a hunt-quest reveal, or after a rescue event (the freed captive tells you). Hidden traits show "?".

### Names and titles

- First name from the race name bank (`npc_names/<race>_male_first.json`).
- Title from a new bank `npc_names/nemesis_titles.json`: entries `{ "text": "...", "side": "evil|good|any", "tags": [...] }`, our own words ("Skull-Splitter", "the Unbending", "Orc-bane"); trait tags prefer matching titles. No canon unique names.
- Cheat-death replaces the title with the scar title ("the Burnt", "the Arrow-Ridden", "the Half-Drowned").
- Display `<First> <Title>`, rank line "Captain of Mordor". Unique per faction (up to 10 re-rolls). Translatable.

### Persistence

- **Record** in `SavedData` `lotrrevival_nemeses` (overworld storage), keyed by nemesis id: faction, entity type, name parts, renown, rank, traits, weakness, scars, revealed-to sets, cheat-death count, grudges (player UUID -> score and dormant flag, top 8 kept), kills of players and units, allies, rival, status, manifestation number and entity UUID, last seen dimension/pos/time, created time.
- **Status:** `AT_LARGE` (no body), `MANIFEST` (one body exists), `DEAD` (kept 7 in-game days for the menu and quests, then purged).
- **Body:** an ordinary `NpcEntity` of the record's type with data attachment `lotrrevival:nemesis_link` (nemesis id + manifestation number). At most one body per record. The number increments per spawn; a body loaded with a stale number is discarded.
- **Unload/despawn:** bodies never despawn naturally. Outside a world event, when the chunk unloads or no player is within 128 blocks for 60 s, the body retreats (removed, `AT_LARGE`). Inside an event the event's chunk tickets keep it loaded until the event ends.
- A freshly promoted NPC retreats (smoke) after its kill.

### World events

A world event is a short, bounded scene staged near a player, run on force-loaded chunks so it plays out whether or not the player watches it closely, shown on the map, and resolved into nemesis records and alignment.

#### Event types

| Type | What happens | Needs | Win / lose for the nemesis side |
| --- | --- | --- | --- |
| **Ambush** | A grudge-holding nemesis and bodyguards attack the player (rules below). | grudge >= 2 | Kills or drives off the player / dies or flees |
| **Beast hunt** | A nemesis and 2-3 followers fight wild beasts (wolves, bears, spiders, wargs for good captains) spawned for the event. | biome with suitable beasts | Kills the beasts / dies |
| **Warband clash** | A nemesis warband fights an enemy faction's warband: another nemesis (relation `ENEMY`/`MORTAL_ENEMY`) or an ordinary captain with 4-6 soldiers. | border land: both factions have influence > 0 nearby, or either does and the other is a mortal enemy | Last side standing |
| **Feud duel** | Two nemeses settle a politics challenge in person, ringed by their bodyguards, who do not interfere. | a pending challenge between `AT_LARGE` nemeses of one faction | Duel winner takes the rank; loser `DEAD` (or cheats death) |
| **War camp** | A nemesis musters 4-8 soldiers around a campfire; nobody attacks unless provoked. | wild land in its faction's territory, flat 7x7 | Undisturbed for the event duration: renown +3, next appearance with +2 bodyguards / camp broken: nemesis flees or fights |
| **Village raid** | A nemesis warband attacks a world-generated settlement of an enemy faction; the settlement's guards defend. Raiders never break or place blocks. | settlement within the band (world pod lookup), not protected by a banner that keeps the raider faction out | Kills half the defenders or survives 5 min / dies or flees |
| **Captain hunting** | A nemesis and 2 trackers chase a lone enemy-faction NPC (a Ranger, a scout, an orc runner) across the area. | open land | Catches and kills the quarry / quarry escapes the area or nemesis dies |
| **Rescue** | A nemesis's warband holds a captive NPC of the player's side (bound, cannot fight). | the captive's faction is the player's side or a faction they have > 0 with | Captive is executed at the timer end / player kills the guards and right-clicks the captive |

- Every type except ambush and rescue can involve nemeses of the player's own side; events are about the war, not only about the player.
- Event participants carry attachment `lotrrevival:world_event` (event id, role). Participants that are not nemeses are ordinary NPCs and despawn with the event.
- Spawned beasts and soldiers count for normal alignment rules when the player kills them.

#### Scheduling

| Thing | Value | Notes |
| --- | --- | --- |
| Roll | every 60 s per online player, staggered | Ambush keeps its own schedule (below). |
| Eligible player | survival/adventure, not Peaceful, in Middle-earth, online >= 5 min this session, not in an active event, not within 64 blocks of an active invasion | |
| Chance per roll | 3% | ~1 event per 30 min of eligible play |
| Per-player cooldown | 12 min after the last event *staged for* that player ended | |
| Distance band | 64-256 blocks from the player, horizontal | Visible or reachable, not on top of them. |
| Site checks | Middle-earth; loaded or loadable surface; not water; > 128 blocks from world spawn, from the Middle-earth arrival point and from any player's respawn point; not in protected land (`LandProtection.blocksFactionSpawn` for any participating faction); not within 96 blocks of another event | |
| Type choice | weights: beast hunt 3, warband clash 3, war camp 2, captain hunting 2, village raid 2, rescue 1, feud duel 2 (only if a challenge is pending); then filtered by site needs and territory; a type with no valid site is dropped and another drawn (max 4 tries) | |
| Territory | each participating faction must have influence > 0 at the site (core influence; always true until areas of influence land), or the biome is tagged for that faction | |
| Nemesis choice | prefer `AT_LARGE` records with a grudge on the player, then records of factions near the site, highest renown first; if none fit, the event uses an ordinary captain (who never becomes a nemesis from the event alone) | |
| Hint | on start: a chat line with direction and rough distance, and the map marker | |

#### Chunk loading

Our own NeoForge `TicketController` `lotrrevival:world_events` (registered in `RegisterTicketControllersEvent`), forced tickets owned by the event id.

| Limit | Value |
| --- | --- |
| Max concurrent events, server | 6 (config) |
| Max events staged for one player | 1 at a time (plus their ambush) |
| Max forced chunks per event | 9 (3x3 around the site), ticking |
| Max forced chunks total | 54 |
| Max duration | 10 min (ambush 5, war camp 8, rescue 6); then it resolves |
| Auto-release | on end; when the player it was staged for logs out, leaves Middle-earth, or is > 384 blocks away for 60 s (event resolves off-screen first); on server stop |
| Load validation | on server start the controller's validation callback drops every leftover ticket of ours; events are never resumed |
| Never | near spawn/respawn points (128 blocks) or inside protected land; no event type in this spec targets protected land. A future siege type would be the only exception and needs its own spec. |

Ambushes happen 16-28 blocks from the player, so they use no forced chunks.

#### Outcomes

- **On-screen:** the event ends when a side wins, the timer runs out, or every participant on one side is dead or fled. Survivors walk away and are removed 30 s later (or immediately if unloaded).
- **Off-screen** (released early): power roll per side = sum of participant ranks (ordinary soldier 1, captain 2, nemesis 3 + rank index) + 1d10; higher wins, losers' nemeses are killed with normal cheat-death rules.
- **Into the records:** win +3 renown (+1 per enemy NPC killed, max +5); death by normal rules; feud duel decides the challenge; war camp bonus bodyguards on next appearance; renown from killing beasts is capped at the win bonus.
- **Alignment:** player kills follow the normal kill path (and the nemesis rank multiplier). Bonus on a player-decided win: +5 with each faction on the side the player helped (+10 for rescue, with the captive's faction), applied once per event.
- **Rumour:** one grey chat line to players within 384 blocks and to grudge holders of any nemesis involved ("The Warg-riders of Gashnak were scattered near the Dead Marshes").
- **Events:** `WorldEventStartedEvent` (cancellable), `WorldEventEndedEvent` (type, outcome, participants, helping players).

#### Map markers and sync

- Server keeps the active events and the last 20 ended ones (for one in-game day) in memory, with the ended list saved in `SavedData` `lotrrevival_world_events`.
- S2C `WorldEventsPayload`: the full list of events within 1024 blocks of the player in their dimension plus any event involving a nemesis with a grudge on them, max 16. Sent on login, dimension change, and when an event in that set starts, changes state or ends. Each entry: event id, type id, centre x/z, radius (blocks), state (`active`, `ended`), outcome (when ended), title as lang key + args (nemesis names, faction names), faction colour(s), start and end game time.
- Client: npc fills a new core client holder `core.api.MapMarkerClient` (same pattern as `WaypointClient`: backend registered by npc, read by progression; needs a `pod:core` ticket). **Progression's map page draws the markers** (icon per type, faction colour, tooltip with title and time left; ended events fade).

### Ambush rules (an event type with its own schedule)

| Thing | Value |
| --- | --- |
| Check | every 20 s per online player, staggered |
| Eligible player | as for events, plus > 128 blocks from their respawn point and not in protected land for the nemesis's faction |
| Candidates | `AT_LARGE` records holding a non-dormant grudge >= 2 against the player, whose faction the player is currently an enemy of. Hunt-quest targets count as grudge 2 for the holder. |
| Chance | 1.5% x (1 + 0.25 x highest grudge, max 3) x 2 if inside the nemesis faction's region |
| Cooldown | 25 min of online time after an ambush against them (20 min if they won) |
| Server cap | at most `max_manifest` nemesis bodies (default 8, events included) |
| Spawn spot | surface 16-28 blocks away, out of line of sight if possible, not water or protected land; skip if none in 16 tries |
| Allies | an allied `AT_LARGE` nemesis with a grudge on the same player joins at 40% |
| Leave | no grudge holder within 64 blocks for 30 s: retreats, no renown |

### Escape and cheat-death

- **Escape:** once per manifestation, when a hit would leave it at <= 20% health: Marked 50%, Captain 40%, Commander 30%, Warlord 20%, Overlord 10%, +20% if it holds grudge 5+ on the attacker. Not with Proud or during Slow wits. Smoke, taunt, body removed, `AT_LARGE`, renown +4.
- **Cheat-death:** when the body dies, 30% first time, 15% second, never after two, never when it dies for good. The body dies normally (loot, alignment, events); record stays `AT_LARGE`, renown +6, gains the scar for the damage type (fire, projectile, explosion, magic, fall, drowning, melee "Cleaved") and its title, grudge on the killer +2.

### How nemeses die for good

The record goes `DEAD` when:

1. it dies and the cheat-death roll fails, or it already cheated death twice;
2. it dies to its weakness's damage type (fire for Fears fire, projectile for Arrow-shy, from behind for Open guard, while Sun-sick in daylight);
3. it loses a challenge, feud or feud duel;
4. it is killed in an off-screen event resolution and fails cheat-death;
5. **fades:** no non-dormant grudge holder has logged in for 30 real days (config), or it is the lowest-renown Marked when its faction is over the seat count;
6. an admin kills it.

There is no hiring of nemeses (maintainer decision).

### Politics (feuds and alliances)

Every 30 min of server time, for each enabled faction with >= 2 `AT_LARGE` nemeses, 60% chance of one event:

| Event | Weight | Rule |
| --- | --- | --- |
| Challenge | 4 | A challenger fights the lowest-renown holder of the rank above. If a player is eligible for a world event near either, it is staged as a **feud duel** event; otherwise resolved off-screen: power = renown + 1d30, winner takes the rank, loser `DEAD`. |
| Cross-faction feud | 3 | With a nemesis of a faction whose relation is `ENEMY`/`MORTAL_ENEMY` (either side, good vs evil or rival evils). Prefer staging as a **warband clash** event; else off-screen power roll, loser `DEAD`, winner +8. |
| Alliance | 2 | Two of the same faction or `ALLY`/`FRIEND` factions with no rival link become allies (max 1 ally each). |
| Betrayal | 1 | Allied pair: 50% the stronger slays the other (+8), else the alliance breaks. |
| Ascension | 2 | Random nemesis +6 renown. |

Rumours go only to grudge holders of either party and players pledged to either faction.

### Rank-scaled alignment

Killing a nemesis body multiplies its type's `kill_value` before core's kill-alignment call: Marked x2, Captain x3, Commander x4, Warlord x6, Overlord x10. Core turns it into gain (enemy factions) or loss (its own and allied factions), so the same rule rewards a Ranger killing an orc Warlord and an orc-pledged player killing a Gondor Warlord. Cheat-death deaths count; escapes do not.

### Invasion leaders (with the invasion spec)

When an invasion of faction F starts, if an `AT_LARGE` F nemesis of Captain+ exists and the cap allows: 50% (75% if a player in range has a non-dormant grudge with it) that the highest-ranked such nemesis (grudge holders preferred) leads it. It spawns with its bodyguards in the first wave, boss bar "leading the invasion". Its death or escape routs the current wave (invasion spec defines routing). Banners that keep F's invasions out keep it out too. Works for good-faction invasions of evil lands the same way.

### Hunt quests (progression)

- New kill-quest kind `nemesis_hunt`, offered by NPCs of factions that are `ENEMY`/`MORTAL_ENEMY` of the nemesis's faction, needs +50 alignment with the giver. Target: a named `AT_LARGE` nemesis of that enemy faction, preferring ones with a grudge on the player, then highest rank.
- Accepting gives the holder a grudge 2 with the target and a region hint; 1 in 3 giver talks reveal its weakness.
- Completes when the target becomes `DEAD` by the holder, their hired unit, or a fellowship member within 32 blocks. Cheat-death does not complete it ("he lives yet"). Fails if it dies by others or politics, or if the holder switches sides.
- Reward: template alignment and coins x the rank multiplier. Weight 3.

### Achievements and titles (progression)

From our events; suggested: first nemesis risen against you; slay a nemesis; Vengeance (slay the nemesis that last killed you); slay a Warlord; slay an Overlord; make a nemesis cheat death; turn the tide of a world event; free a captive; slay 10 nemeses (title "Captain-bane"). Names and art are progression's.

### Speech

New `NpcSpeech` categories per side/race: `nemesis_rise`, `nemesis_ambush`, `nemesis_ambush_revenge`, `nemesis_ambush_scarred`, `nemesis_escape`, `nemesis_cheat_death`, `nemesis_death`, `event_hint_<type>`, `event_rumour_<type>`, `captive_thanks`. Placeholders: player, nemesis, scar, biome, direction. Chat to the relevant players only.

### Config (`lotrrevival-npc-server.toml`)

`[nemesis]`: `enabled`, `disabled_factions` (dunland, near_harad, far_harad, easterling, half_troll), promotion chances, renown values, rank thresholds and seats, `max_manifest` (8), ambush period/chance/cooldown/safe radius, politics period and weights, cheat-death chances, rank alignment multipliers, fade days, `allowed_dimensions` (middle_earth), side threshold (100), dormant grudge decay.
`[world_events]`: `enabled`, roll period (60 s), chance (3%), cooldown (12 min), distance band (64, 256), type weights, `max_events` (6), `max_chunks_per_event` (9), `max_duration` per type, release distance (384), spawn/respawn exclusion radius (128), alignment bonuses.
Lists built with `Arrays.asList`.

### Performance limits

- Records: seats cap each faction at 29; 14 enabled factions ~400 records; one SavedData written only when dirty.
- No per-tick scans: event rolls every 60 s and ambush checks every 20 s per player, politics every 30 min, body checks every 2 s only on existing bodies (<= `max_manifest`).
- Forced chunks hard-capped at 54; events and their participants hard-capped (max 16 entities per event).
- Sync: hierarchy page requests a snapshot on open (C2S `NemesisRosterRequest`, 1 per 2 s per player), S2C `NemesisRosterPayload` with at most 60 cards; world events payload max 16 entries, sent on change only.

## Edge cases

- **Multiplayer: shared hierarchy per faction, per-player grudges (decision).** One captain is the same nemesis to everyone; grudges are per player. Fits shared world NPCs, invasions, bounties, feuds and fellowships. A good player and an evil player on one server see the same hosts; each is hunted by the other side's.
- **Two players near one event:** it was staged for one player, but anyone can join. Release distance and logout rules follow the player it was staged for; if they leave, it hands over to another eligible player within 128 blocks instead of resolving, if there is one.
- **Two grudge holders together:** the nemesis targets the higher grudge; both get the boss bar.
- **Player logs out in an event:** see auto-release; event resolves off-screen, tickets released.
- **Server stop during an event:** tickets released; participants saved in chunks are discarded on load (event attachment with an unknown event id); nemeses in it count as `AT_LARGE`.
- **Player death in an ambush:** counts as a kill; the body retreats 5 s later.
- **Hired units:** valid targets of nemeses and events; their kills of a nemesis count as the owner's for quests and achievements, not for alignment (normal hired-kill rules).
- **Killed by other NPCs, beasts or the environment:** cheat-death and die-for-good rules apply; rumour to grudge holders.
- **Side switch mid-ambush:** the grudge goes dormant but the current body fights on until it retreats or dies.
- **Unsided player killed by an orc:** promotes if their Mordor alignment is below 0 (they are its enemy); otherwise no promotion.
- **Name tag:** refused on a nemesis body and on event participants; the tag is not used up.
- **Dimension change:** bodies and events exist only in `allowed_dimensions`; NPCs never change dimension.
- **Datapack removes a faction from the tag or config disables it:** records stay, never manifest, feud or appear in events, and fade on schedule. Unknown trait ids are dropped on load with one warning.
- **Peaceful:** no promotions, ambushes or events; existing bodies retreat, running events end off-screen.
- **Banners placed during an event:** the event continues (protection blocks spawning, not pursuit), but no new participants spawn inside.

## Data and APIs

- **NPC pod (new):** `npc/nemesis/` (records, SavedData `lotrrevival_nemeses`, attachment `lotrrevival:nemesis_link`, side calculation, traits, promotion, politics, ambush) and `npc/worldevent/` (scheduler, `TicketController` `lotrrevival:world_events`, event types, attachment `lotrrevival:world_event`, SavedData `lotrrevival_world_events`). Commands `/lotr nemesis list|info|spawn|renown|kill|purge` and `/lotr worldevent list|start <type>|end <id>` (op level 2).
- **Datapack (npc datagen):** `#lotrrevival:nemesis_factions`, `#lotrrevival:never_nemesis`, `nemesis_traits/*.json`, `npc_names/nemesis_titles.json`, speech lines, lang, biome tags for beast hunts if needed.
- **Events (NeoForge game bus):** `NemesisRisenEvent`, `NemesisRankChangedEvent`, `NemesisDefeatedEvent` (outcome `CHEATED_DEATH` / `DEAD`, cause, killer, killer is grudge holder), `NemesisAmbushEvent` (cancellable), `WorldEventStartedEvent` (cancellable), `WorldEventEndedEvent`.
- **Read API:** `NemesisApi` (`find`, `atLarge(faction)`, `grudges(player)`, `side(player)`). If progression must not import npc, a `core.api.NemesisQuery` holder like `LandProtection` (`pod:core` ticket).
- **Payloads:** `NemesisRosterRequest` (C2S), `NemesisRosterPayload` (S2C), `WorldEventsPayload` (S2C).
- **Client:** `client/npc/nemesis/NemesisHierarchyScreen`; npc registers a `core.api.MapMarkerClient` backend. Menu hub page and map markers are drawn by progression (`pod:progression` tickets).
- **Core APIs used:** alignment and pledge reads, kill path, faction kinds and `FactionRelations`, `Alignment.influence`, `LandProtection.blocksFactionSpawn`, `FellowshipQuery`, `ModDimensions.MIDDLE_EARTH`. New: `MapMarkerClient` (core).
- **World pod:** a settlement lookup (nearest generated settlement of a faction within a radius, with its centre and bounds) for village raids (`pod:world` ticket if it does not exist by then).
- **Small NPC-pod edits:** `NpcEntity.alignmentKillValue()` rank multiplier, despawn exclusion for bodies and event participants, `NpcLoadoutData` rank tiers.

## Out of scope

- Hiring or dominating nemeses (maintainer decision).
- Promotion from surviving a fight (maintainer decision).
- Sieges of protected land, events in other dimensions, events that place or break blocks.
- Player-vs-player nemeses.
- Captured letters and other intel items (hook left in Intel).
- Nemesis-specific models or textures beyond scale and gear.
- Integration with the PAL Nemesis System mod.

## Acceptance tests

- [ ] GameTest: side calculation (pledge first, largest absolute alignment, threshold, tie), and enemy check for good and evil factions.
- [ ] GameTest: an eligible Mordor guard kills a good mock player with a forced roll → record, Marked, grudge 3, body retreated; a Gondor captain kills an evil player → record on Gondor's host; a hired unit's killer promotes at the unit-level chance; no promotion when the player is not its enemy.
- [ ] GameTest: side switch makes grudges dormant, decays them, wakes them within 7 days, fails the hunt quest.
- [ ] GameTest: renown, seats and challengers; challenge resolution with a fixed seed.
- [ ] GameTest: rank stat modifiers replaced (not stacked) on rank change.
- [ ] GameTest: body unload → `AT_LARGE`; stale body discarded; never two bodies.
- [ ] GameTest: ambush eligibility (Peaceful, creative, safe radius, protected land via `LandProtection.swapForTesting`, cooldown, cap) with dimension list overridden for the test server.
- [ ] GameTest: escape and cheat-death chances with fixed RNG; weakness death and third death are permanent.
- [ ] GameTest: rank kill-value multiplier through the kill path; `NemesisDefeatedEvent` once per death.
- [ ] GameTest: world event scheduler respects band, spacing, exclusion radius, protected land, per-player and server caps.
- [ ] GameTest: ticket manager forces at most 9 chunks per event and 54 total, releases on end, on staged-player logout/distance, and drops leftovers on start.
- [ ] GameTest: each event type in `npc_arena` resolves on-screen with a scripted outcome and writes the right renown/deaths; off-screen resolution with a fixed seed.
- [ ] GameTest: `WorldEventsPayload` contents (radius filter, grudge events, max 16).
- [ ] Playtest: die to a Mordor orc until one rises, see it on the menu page, get ambushed, scar it, kill it for good with its weakness.
- [ ] Playtest: as an orc-pledged player, get a Gondor captain risen against you and ambushed by it.
- [ ] Playtest: see an event marker on the map, ride there, swing a warband clash, check rumour, alignment and hierarchy.

## Tickets

Proposed 1-2 day tickets (not opened until this spec is approved; `pod:npc` unless marked; all after G3):

**Nemesis core**
1. Records and persistence: record, SavedData, `nemesis_link`, faction/opt-out tags, `disabled_factions` config, `/lotr nemesis list|info|purge`. (1.5 d)
2. Sides: player side calculation and cache, enemy check, side-switch handling (dormant grudges, decay, wake-up). (1 d)
3. Names, titles and trait data: `nemesis_titles` bank, `nemesis_traits` format and loader, datagen of the starting set, lang. (1 d)
4. Promotion and renown: player-kill and hired-unit-kill triggers, rolls, renown, seats and challengers, risen/rank events, rise speech. (1.5 d)
5. Manifestation and rank stats: modifiers, loadout tiers, bodyguards, mount, boss bar, retreat/unload/stale-body rules, despawn exclusion. (2 d)
6. Trait effects. (2 d)
7. Escape, cheat-death, dying for good: scars, rank alignment multiplier, defeated event. (1.5 d)
8. Politics: challenges, cross-faction feuds, alliances, betrayal, ascension, fading, rumours (off-screen path; staging hooks into world events). (1.5 d)
9. Hierarchy sync and screen: payloads and `NemesisHierarchyScreen`. (2 d)
10. **`pod:progression`:** Nemeses page in the menu hub. (0.5 d)

**World events**
11. Event framework and scheduler: event state machine, participant attachment, per-player rolls, cooldowns, distance band, site checks, territory, caps, outcomes into records, off-screen resolution, rumours, start/end events, `/lotr worldevent`. (2 d)
12. Chunk ticket manager: `TicketController`, per-event and total limits, release rules, startup validation. (1 d)
13. Ambush as an event type (own schedule, rules above). (1.5 d)
14. Event types A: beast hunt, captain hunting, war camp. (2 d)
15. Event types B: warband clash, feud duel (with politics staging). (2 d)
16. Event types C: village raid, rescue. (2 d) Needs a **`pod:world`** settlement lookup ticket.
17. Map sync: `WorldEventsPayload`, client cache, `MapMarkerClient` backend. (1 d) Needs a **`pod:core`** ticket for `core.api.MapMarkerClient`. (0.5 d)
18. **`pod:progression`:** draw world-event markers on the map page (after #88 and the map GUI). (1 d)

**Other pods and later**
19. **`pod:progression`:** `nemesis_hunt` quest kind. (2 d)
20. **`pod:progression`:** nemesis and world-event achievements, "Captain-bane" title. (1 d)
21. Invasion leaders (after the invasion spec). (1 d)
22. Admin and test tooling: `/lotr nemesis spawn|renown|kill`, fixed-RNG GameTest helpers, scripted event runner for `npc_arena`. (1 d)

## Open questions for the maintainer

1. Should Dunland, Near and Far Harad, Easterlings and half-trolls be on by default (they are in the tag but disabled in config)?
2. Event rate: ~1 event per 30 min of eligible play, 12 min cooldown, 64-256 blocks away. Right feel?
3. War camps: entities and one campfire only, or may they place a few temporary blocks (tents, banner) that are removed at the end?
4. Should players see markers for events staged for other players within 1024 blocks (proposed yes), or only their own?
5. Rescue reward: alignment only, or should freed captives sometimes reveal a nemesis's location or weakness (proposed: weakness)?
