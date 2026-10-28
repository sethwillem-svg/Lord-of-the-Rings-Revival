# Nemesis system and world events

- **Pod:** npc
- **Lead:** Opus 3
- **Status:** draft (maintainer answers of 2026-10-28 applied, two rounds)
- **Schedule:** after G3 (needs every faction's NPC types, mounts, faction captain loadouts and the map GUI). Invasion leaders, hunt quests and settlement raids land with or after G4.
- **Depends on:** `specs/npc/npc-base.md` (`NpcEntity`, classes, names, speech, kills, despawn, loadouts), `specs/npc/hiring-trading.md` (hired units, unit levels), `specs/core/core-apis.md` (alignment, pledge, kill path, `FactionRelations`, faction kinds, `LandProtection`, `FellowshipQuery`, influence), `specs/progression/quests-fellowships-banners.md` (kill quests, banners, achievements, titles, menu hub), progression map page (#88) and map GUI, World structure spec (not written yet: venue structures, venue tags, structure locator). Invasion spec (S8, not written yet).
- **Beyond parity:** Legacy LOTR had no nemesis system and no world events of this kind. Nothing here comes from it; this is our own design. All numbers are ours and tunable through config.
- **Why not the Nemesis System (PAL) mod:** we read it (MIT) as a feature checklist only; it cannot promote our NPCs, has no API and needs Architectury and owo-lib, so a native system fits our factions better.

## Summary

Captains remember you. A captain of a faction you are at war with that kills you, or butchers your hired company, can become a **nemesis**: a named NPC with a title, a rank in its faction's war-host, strengths, a weakness, scars and a grudge against the players it has met. Orc captains rise against the Free Peoples' champions; Gondor, Rohan, Elven and Dwarven captains rise against the servants of the Shadow. Nemeses persist in saved data when no copy of them is loaded, feud and ally along faction lines, lead invasions, carry bounties posted by enemy factions, and act in the world through **world events**: things that happen a short ride from the player and show on the map, such as an ambush, a warband clash, a captain hunting beasts, a raid on a village or a prisoner waiting for rescue. Players can ride to an event and interfere; what happens there changes alignment and the hierarchy. Players care because Middle-earth stops being a field of interchangeable soldiers and starts having a war going on without them.

## Player-facing behavior

1. **Rise.** A captain or soldier of a faction you are an enemy of kills you or one of your hired units. A red chat line: "*Gashnak Skull-Splitter* of Mordor has risen to **Marked**." For an evil player: "*Hirgon the Unbending* of Gondor has sworn to hunt you." It retreats (vanishes).
2. **Hierarchy.** The Middle-earth menu (L) gains a **Nemeses** page: each enabled faction's war-host as a pyramid (Overlord on top), each card with name, title, rank, scars, known strengths and weakness, grudges, rivals and allies. Your grudges are highlighted; hosts of your own side are shown greyed.
3. **World events.** Every 8-15 minutes or so something starts 64-256 blocks from you, often at a ruin, watchtower, hideout or village: a chat hint ("Steel rings out to the north-east") and a marker on the map. Other players' events nearby show on your map too. Ride there and join in, or ignore it and hear how it ended.
4. **Ambush** is the rare event aimed at you (roughly once per one and a half hours of travel): a nemesis that holds a grudge finds you, speaks a taunt naming you, and attacks with its bodyguards, a rank-coloured boss bar and, at high ranks, a mount.
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

A world event is a short, bounded scene staged near a player. It runs on force-loaded chunks so it plays out whether or not anyone watches closely, uses a world structure as its stage when one is near, shows on every nearby player's map, and resolves into nemesis records and alignment. Events are frequent; ambushes (the event aimed at you) are rare.

#### Event types

| Type | What happens | Needs | Win / lose for the nemesis side |
| --- | --- | --- | --- |
| **Ambush** | A grudge-holding nemesis and bodyguards attack the player (rules below). | grudge >= 2 | Kills or drives off the player / dies or flees |
| **Beast hunt** | A nemesis and 2-3 followers fight wild beasts (wolves, bears, spiders; wargs for good captains) spawned for the event. | biome with suitable beasts | Kills the beasts / dies |
| **Warband clash** | A nemesis warband fights an enemy faction's warband: another nemesis (relation `ENEMY`/`MORTAL_ENEMY`) or an ordinary captain with 4-6 soldiers. | border land: both factions have influence > 0 nearby, or one does and the other is a mortal enemy | Last side standing |
| **Feud duel** | Two nemeses settle a politics challenge in person, ringed by bodyguards who do not interfere. | a pending challenge between `AT_LARGE` nemeses of one faction | Winner takes the rank; loser `DEAD` (or cheats death) |
| **War camp** | A nemesis musters 4-8 soldiers in a temporary camp of tents and a war standard (see War camp blocks); nobody attacks unless provoked. | open land in its faction's territory (never a structure) | Undisturbed for the duration: renown +3 and +2 bodyguards on its next appearance / camp broken: nemesis flees or fights |
| **Village raid** | A nemesis warband attacks a settlement of an enemy faction; its guards defend. Raiders never break or place blocks. | a `village` venue in the band, not protected against the raiders' faction | Kills half the defenders or survives 5 min / dies or flees |
| **Captain hunting** | A nemesis and 2 trackers chase a lone enemy-faction NPC (a Ranger, a scout, an orc runner), who runs for the nearest friendly outpost or watchtower. | open land | Catches and kills the quarry / quarry reaches safety or nemesis dies |
| **Rescue** | A nemesis's warband holds a captive NPC (bound, cannot fight) of the player's side. | captive's faction is the player's side or one they have > 0 with | Captive executed at the timer end / players kill the guards and right-click the captive; the freer (and their fellowship within 32 blocks) learn the nemesis's weakness |

- Every type except ambush and rescue may involve nemeses of the player's own side; events are about the war, not only about the player.
- Participants carry attachment `lotrrevival:world_event` (event id, side, role). Non-nemesis participants are ordinary NPCs and leave with the event.
- Spawned beasts and soldiers count for normal alignment when killed.

#### Venues (world structures)

Events prefer to happen **in** the world's structures, so the land feels lived in.

- **Lookup:** the World pod tags structures by venue kind with structure tags `#lotrrevival:event_venues/<kind>`, kinds: `camp`, `ruin`, `outpost`, `watchtower`, `tower`, `village`, `hideout`, `bridge`. npc asks a structure-locator API (core holder `core.api.StructureVenues`, backend registered by World) for venues of a kind within the distance band: each answer has structure id, bounds, centre, owning faction (or none), whether it has resident NPCs, and whether banner protection covers it.
- **Only already-generated chunks** are searched (structure references stored in existing chunks or a World-side index); the lookup never generates terrain. Budget: one lookup per roll, max 64 chunk reference checks, results cached per 512-block region for 5 min.
- **Preference per type** (first kind found in the band wins; ties: nearest to the band's middle, 160 blocks):

| Type | Preferred venues | Fallback |
| --- | --- | --- |
| Ambush | none (happens where the player is) | n/a |
| Beast hunt | `ruin`, `camp` | open terrain |
| Warband clash | `ruin`, `bridge`, `watchtower`, `outpost` of either faction | open terrain on a faction border |
| Feud duel | `ruin`, `tower`, `camp` of the duellists' faction | open terrain |
| War camp | never a structure (builds its own) | open terrain only |
| Village raid | `village` of an enemy faction (required) | none: the type is dropped |
| Captain hunting | starts in open terrain; the quarry runs to an `outpost`/`watchtower`/`village` of its faction within 128 blocks | if none, it runs straight away from the hunters |
| Rescue | `hideout`, `ruin`, `tower`, `camp` | open terrain |

- **Using a venue:** the event centre is the structure's centre; participants spawn on open floor inside the bounds (two air blocks over a solid block, not in water). Chunk tickets cover the structure, centre-out, within the per-event chunk limit. Resident NPCs of a venue's faction join on their faction's side (a Gondor watchtower's guards fight the raiders). A venue whose banner protection keeps out a participating faction is never chosen.
- **Venue cooldown:** one event per venue per 30 min; at most one active event per venue.
- **Density target** (World pod): in wild land of every region, on average >= 2 venues within 256 blocks of any point, with at least ruins and one faction outpost/watchtower kind per region. Until the World structure set exists, events use the open-terrain fallbacks and village raids only happen where settlements exist.
- This depends on the **World pod's structure spec, which does not exist yet**; see the World tickets below.

#### War camp blocks

The only event that places blocks.

- **Templates:** structure NBT `data/lotrrevival/structure/event/war_camp/<group>_<n>.nbt`, groups `orc`, `men`, `elf`, `dwarf`, 2+ variants each, footprint <= 11 x 11, height <= 6. **Original art built for this repo.** Contents: tents (decorative blocks), a campfire, and a faction **war standard** (a decorative banner block; it never grants land protection). No containers, no block entities with inventories, no fire blocks, no redstone.
- **Site rules:** open terrain only; not inside or within 16 blocks of any structure's bounds; not in protected land; not within 128 blocks of spawn or respawn points; ground height varies <= 2 over the footprint; no terrain levelling.
- **Replace rule:** a template block is placed only where the current block is air or a replaceable plant (`#minecraft:replaceable_by_trees` / short grass, flowers, ferns, snow layer, dead bush). Other positions are skipped; if more than 5% of template blocks would be skipped, the site is rejected.
- **Journal:** before placing, every changed position and its previous block state is written to the event's restore journal in `SavedData` `lotrrevival_world_events` and saved. Placement and restore happen only in chunks the event holds tickets for.
- **Restore exactly:** on event end (after the participants leave), on server stop (before tickets are released), and on startup recovery (leftover journals: force-load their chunks with our controller, restore, release, delete the journal). A position is restored only if it still holds the block we placed or air; anything a player put there is left alone.
- **No drops:** journaled positions drop nothing when broken (by hand, explosion, fire, or a mob) and give no XP; pistons cannot move them; they cannot be picked with silk touch. Breaking one still counts as "camp broken" for the event.

#### Scheduling

| Thing | Value | Notes |
| --- | --- | --- |
| Roll | every 30 s per online player, staggered | Ambush has its own schedule (below). |
| Eligible player | survival/adventure, not Peaceful, in Middle-earth, online >= 3 min this session, no event currently staged for them, not within 64 blocks of an active invasion | |
| Chance per roll | 6% | With the cooldown: about one event every 8-15 min per player |
| Per-player cooldown | 5 min after the last event staged for that player ended | |
| Distance band | 64-256 blocks from the player | Visible or reachable, not on top of them. |
| Site checks | Middle-earth; surface, not water; > 128 blocks from world spawn, the Middle-earth arrival point and any player's respawn point; not in protected land for any participating faction (except a venue's own faction on its own side); not within 96 blocks of another active event | |
| Type weights | beast hunt 3, warband clash 3, war camp 2, captain hunting 2, village raid 2, rescue 1, feud duel 2 (only if a challenge is pending); filtered by venue/site needs and territory; up to 4 redraws | |
| Territory | each participating faction has influence > 0 at the site (core influence), or the biome is tagged for it | |
| Nemesis choice | `AT_LARGE` records with a grudge on the player first, then records of factions near the site, highest renown first; otherwise an ordinary captain (who never becomes a nemesis from an event alone) | |
| Hint | chat line with direction and rough distance to the player it was staged for; map marker for everyone in range | |

#### Performance governor

The scheduler watches server load and backs off.

| State | Enter when | Effect |
| --- | --- | --- |
| Green | average tick time over the last 100 ticks < 30 ms and chunk budget < 75% used | normal |
| Yellow | 30-40 ms, or chunk budget >= 75% | event chance halved; concurrency cap halved (round down, min 1); new events limited to 4 chunks; no war camps or village raids |
| Red | > 40 ms, or chunk budget full | no new events and no ambushes (skipped rolls do not start cooldowns); after 30 s in Red, active events with no player within 64 blocks resolve off-screen and release their chunks, oldest first, until the state drops or none are left |

- Hysteresis: a state is held at least 10 s; going down a level needs the average to be 5 ms below that level's entry threshold.
- Average tick time is the server's own rolling tick-time average (overall, not per dimension). Thresholds and budgets are config.
- The governor state is shown in `/lotr worldevent list` and logged once per state change at INFO.

#### Chunk loading

Our own NeoForge `TicketController` `lotrrevival:world_events` (registered in `RegisterTicketControllersEvent`), forced ticking tickets owned by the event id.

| Limit | Value |
| --- | --- |
| Max concurrent events, server | 3 + 1 per 3 eligible online players, hard max 12 (config) |
| Max events staged for one player | 1 at a time (plus at most one ambush) |
| Max forced chunks per event | 9 (3x3), 4 in Yellow |
| Chunk budget, total | 72 (config) |
| Max entities per event | 16; total across events 160 |
| Max duration | 8 min (ambush 5, war camp 8, rescue 6, feud duel 4) |
| Auto-release | on end; when the staged-for player logs out, leaves Middle-earth or is > 384 blocks away for 60 s (handover first, else off-screen resolution); Red-state shedding; server stop (after war-camp restore) |
| Load validation | on server start the controller's validation callback drops all our leftover tickets; events never resume (war-camp journals are restored as above) |
| Never | within 128 blocks of spawn/respawn points or in protected land; no event in this spec targets protected land (a future siege type would need its own spec) |

Sizing check: at ~12 min between events and ~6 min average duration, about one player in three has an event running; 20 players gives ~7 events, within the cap of 9 for 20 players, and ~60 chunks worst case, within the budget of 72. Ambushes use no forced chunks (they happen 16-28 blocks from the player).

#### Participation, credit and grudges

- **Owner:** the player the event was staged for. Owner-only parts: the chat hint, release/handover rules, the per-player cooldown, and ambush targeting.
- **Participants:** any player who, within the event radius + 32 blocks while it is active, damages an event NPC or is damaged by one. Each participant is recorded with the side they helped: the side whose enemies they damaged most. Anyone may join any event shown on their map.
- **Kills:** normal kill path for whoever lands the kill (alignment, hired-unit rules, nemesis rank multiplier). Nemesis effects also apply to interferers: a nemesis gains grudge +1 on any player who damages it, and renown/grudge +3 for killing one (only if that player is its enemy).
- **Win bonus:** +5 alignment with each faction of the winning side (+10 with the captive's faction for a rescue) to every participant who helped the winning side and dealt >= 20% of the player damage to the losing side (or freed the captive). Once per player per event.
- **NPC targeting:** event NPCs use their normal targeting: they attack players who are hostile to them and those who attack them; a player on their side who stays out of it is ignored.
- **Owner and interferer:** fellowship members are always allies of each other (fellowship friendly-fire rules apply) and share the rescue reveal. Players on opposite sides may fight each other only under the server's PvP setting and fellowship rules; events never turn players into each other's targets and never grant renown or grudges for player-vs-player kills. An interferer helping the other side does not take over the event; if the owner leaves, handover goes to a participant within 128 blocks (fellowship members first).
- **Achievements:** "turn the tide" counts for every participant on the winning side.

#### Outcomes

- **On-screen:** ends when a side wins, the timer runs out, or one side is all dead or fled. Survivors walk away and are removed 30 s later (immediately if unloaded).
- **Off-screen** (released early): power roll per side = sum of participant ranks (soldier 1, captain 2, nemesis 3 + rank index) + 1d10; losers' nemeses are killed with normal cheat-death rules.
- **Into the records:** win +3 renown (+1 per enemy NPC killed, max +5); deaths by normal rules; feud duels decide challenges; war camp bonus; renown from beasts capped at the win bonus.
- **Rumour:** one grey chat line to players within 384 blocks and to grudge holders of any nemesis involved.
- **Events:** `WorldEventStartedEvent` (cancellable), `WorldEventEndedEvent` (type, outcome, venue, participants with sides).

#### Map markers and sync

- Active events plus the last 20 ended ones (kept one in-game day) are held in memory; the ended list and war-camp journals are saved in `SavedData` `lotrrevival_world_events`.
- S2C `WorldEventsPayload`: every event within 1024 blocks of the player in their dimension (staged for anyone) plus any event involving a nemesis with a grudge on them, max 16, nearest first. Sent on login, dimension change, and when an event in that set starts, changes state or ends. Entry: event id, type id, centre x/z, radius, venue kind (or none), state (`active`, `ended`), outcome, title as lang key + args (nemesis and faction names), faction colours, start/end game time, whether it is staged for this player.
- Client: npc registers a backend for a new core client holder `core.api.MapMarkerClient` (same pattern as `WaypointClient`). **Progression's map page draws the markers** (icon per type, faction colour, tooltip with title and time left; own events highlighted; ended events fade).

### Ambush rules (rare; own schedule)

| Thing | Value |
| --- | --- |
| Check | every 30 s per online player, staggered |
| Eligible player | as for events, plus online >= 15 min this session, > 128 blocks from their respawn point, not in protected land for the nemesis's faction, governor not Red |
| Candidates | `AT_LARGE` records with a non-dormant grudge >= 2 on the player whose faction the player is an enemy of; hunt-quest targets count as grudge 2 for the holder |
| Cooldown | 75 min of online time after an ambush against them (60 min if they won) |
| Chance after cooldown | 1% x (1 + 0.25 x highest grudge, max 3) per check, x2 inside the nemesis faction's region: expected wait after the cooldown ~8-35 min, so roughly one ambush per 85-110 min of eligible play |
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

`[nemesis]`: `enabled`, `disabled_factions` (dunland, near_harad, far_harad, easterling, half_troll), promotion chances, renown values, rank thresholds and seats, `max_manifest` (8), ambush check period (30 s), chance (1%), cooldown (75 / 60 min), safe radius (128), politics period and weights, cheat-death chances, rank alignment multipliers, fade days, `allowed_dimensions` (middle_earth), side threshold (100), dormant grudge decay.
`[world_events]`: `enabled`, roll period (30 s), chance (6%), cooldown (5 min), distance band (64, 256), type weights, `max_events_base` (3), `players_per_extra_event` (3), `max_events` (12), `max_chunks_per_event` (9), `chunk_budget` (72), `max_entities` (16 / 160), `max_duration` per type, release distance (384), spawn/respawn exclusion radius (128), venue cooldown (30 min), venue search radius and budget, war camps enabled (true), alignment bonuses.
`[world_events.governor]`: `yellow_mspt` (30), `red_mspt` (40), `hysteresis_mspt` (5), `min_state_seconds` (10), `yellow_chunk_fraction` (0.75), `red_shed_after_seconds` (30).
Lists built with `Arrays.asList`.

### Performance limits

- Records: seats cap each faction at 29; 14 enabled factions ~400 records; one SavedData written only when dirty.
- No per-tick scans: event and ambush rolls every 30 s per player (staggered), politics every 30 min, body checks every 2 s only on existing bodies (<= `max_manifest`), governor reads the server's tick-time average once per second.
- Hard caps: 12 events, 72 forced chunks, 16 entities per event and 160 total; the governor lowers these under load.
- Venue lookups: one per roll at most, 64 chunk checks, cached 5 min per region; never generates chunks.
- War camps: <= 11 x 11 x 6 blocks placed per camp, journal written before placement.
- Sync: hierarchy snapshot on request (1 per 2 s per player, max 60 cards); world events payload max 16 entries, sent on change only.

## Edge cases

- **Multiplayer: shared hierarchy per faction, per-player grudges (decision).** One captain is the same nemesis to everyone; grudges are per player. Fits shared world NPCs, invasions, bounties, feuds and fellowships. A good player and an evil player on one server see the same hosts; each is hunted by the other side's.
- **Other players' events:** visible on the map within 1024 blocks and open to anyone; see Participation for credit. A player can be the owner of one event and a participant in others at the same time.
- **Opposite-side players at one event:** each fights the NPCs that are hostile to them; PvP only under the server setting and fellowship rules; no renown or grudges from PvP.
- **Two grudge holders together:** the nemesis targets the higher grudge; both get the boss bar.
- **Owner logs out in an event:** handover to a participant within 128 blocks (fellowship first), else off-screen resolution and release.
- **Server stop during an event:** war camps restored, tickets released; participants saved in chunks are discarded on load (event attachment with an unknown event id); nemeses in it count as `AT_LARGE`.
- **Crash during a war camp:** the journal survives (saved before placement); startup recovery restores the blocks.
- **Player builds on a war-camp spot:** restore leaves player-placed blocks alone and only reverts our blocks or air.
- **Venue destroyed or griefed by players:** the locator still returns it; events spawn only on valid open floor, and if fewer than half the participants can be placed the venue is skipped and cooled down.
- **Player death in an ambush:** counts as a kill; the body retreats 5 s later.
- **Hired units:** valid targets of nemeses and events; their kills of a nemesis count as the owner's for quests and achievements, not for alignment (normal hired-kill rules).
- **Killed by other NPCs, beasts or the environment:** cheat-death and die-for-good rules apply; rumour to grudge holders.
- **Side switch mid-ambush:** the grudge goes dormant but the current body fights on until it retreats or dies.
- **Unsided player killed by an orc:** promotes if their Mordor alignment is below 0 (they are its enemy); otherwise no promotion.
- **Name tag:** refused on a nemesis body and on event participants; the tag is not used up.
- **Dimension change:** bodies and events exist only in `allowed_dimensions`; NPCs never change dimension.
- **Datapack removes a faction from the tag or config disables it:** records stay, never manifest, feud or appear in events, and fade on schedule. Unknown trait ids are dropped on load with one warning.
- **Peaceful:** no promotions, ambushes or events; existing bodies retreat, running events end off-screen (war camps restored).
- **Banners placed during an event:** the event continues (protection blocks spawning, not pursuit), but no new participants spawn inside.
- **Governor flaps under load:** the 10 s minimum hold and 5 ms hysteresis stop it; shed events are never restarted.

## Data and APIs

- **NPC pod (new):** `npc/nemesis/` (records, SavedData `lotrrevival_nemeses`, attachment `lotrrevival:nemesis_link`, side calculation, traits, promotion, politics, ambush) and `npc/worldevent/` (scheduler, governor, `TicketController` `lotrrevival:world_events`, venue selection, war-camp placer and journal, event types, attachment `lotrrevival:world_event`, SavedData `lotrrevival_world_events`). Commands `/lotr nemesis list|info|spawn|renown|kill|purge` and `/lotr worldevent list|start <type> [venue]|end <id>|restore` (op level 2).
- **Datapack (npc datagen):** `#lotrrevival:nemesis_factions`, `#lotrrevival:never_nemesis`, `nemesis_traits/*.json`, `npc_names/nemesis_titles.json`, speech lines, lang, biome tags for beast hunts if needed. War camp NBT templates (original art, hand-built; NBT is the AGENTS.md exception to datagen).
- **Events (NeoForge game bus):** `NemesisRisenEvent`, `NemesisRankChangedEvent`, `NemesisDefeatedEvent` (outcome `CHEATED_DEATH` / `DEAD`, cause, killer, killer is grudge holder), `NemesisAmbushEvent` (cancellable), `WorldEventStartedEvent` (cancellable), `WorldEventEndedEvent`.
- **Read API:** `NemesisApi` (`find`, `atLarge(faction)`, `grudges(player)`, `side(player)`). If progression must not import npc, a `core.api.NemesisQuery` holder like `LandProtection` (`pod:core` ticket).
- **Payloads:** `NemesisRosterRequest` (C2S), `NemesisRosterPayload` (S2C), `WorldEventsPayload` (S2C).
- **Client:** `client/npc/nemesis/NemesisHierarchyScreen`; npc registers a `core.api.MapMarkerClient` backend. Menu hub page and map markers are drawn by progression (`pod:progression` tickets).
- **Core (new holders, `pod:core`):** `core.api.MapMarkerClient` (client markers, npc writes, progression reads) and `core.api.StructureVenues` (server structure locator, World registers the backend, npc reads). Both follow the `WaypointClient` / `LandProtection` pattern so pods never import each other.
- **World (new, `pod:world`):** venue structure tags `#lotrrevival:event_venues/<kind>`, venue structures in every region, the locator backend. Depends on the World pod's structure spec, which is not written yet.
- **Core APIs used:** alignment and pledge reads, kill path, faction kinds and `FactionRelations`, `Alignment.influence`, `LandProtection.blocksFactionSpawn`, `FellowshipQuery`, `ModDimensions.MIDDLE_EARTH`.
- **Small NPC-pod edits:** `NpcEntity.alignmentKillValue()` rank multiplier, despawn exclusion for bodies and event participants, `NpcLoadoutData` rank tiers.

## Out of scope

- Hiring or dominating nemeses (maintainer decision).
- Promotion from surviving a fight (maintainer decision).
- Sieges of protected land, events in other dimensions, any block changes other than war-camp templates.
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
- [ ] GameTest: ambush eligibility (Peaceful, creative, online time, safe radius, protected land via `LandProtection.swapForTesting`, 75/60 min cooldown, cap, Red governor) with dimension list overridden for the test server.
- [ ] GameTest: escape and cheat-death chances with fixed RNG; weakness death and third death are permanent.
- [ ] GameTest: rank kill-value multiplier through the kill path; `NemesisDefeatedEvent` once per death.
- [ ] GameTest: scheduler respects band, spacing, exclusion radius, protected land, per-player cooldown and the player-scaled server cap.
- [ ] GameTest: governor states from injected tick times (Green/Yellow/Red, hysteresis, minimum hold, Red shedding releases chunks).
- [ ] GameTest: ticket manager forces at most 9 chunks per event (4 in Yellow) and 72 total, releases on end, owner logout/distance and shedding, drops leftovers on start.
- [ ] GameTest: venue selection with a stub `StructureVenues` backend: preference order per type, fallback to open terrain, village raid dropped without a village, venue cooldown.
- [ ] GameTest: war camp places only into air/plants, rejects > 5% blocked, journals before placing, restores exactly (including after a simulated restart from the saved journal), leaves player-placed blocks, drops nothing when broken or exploded.
- [ ] GameTest: participation and credit: interferer kills give alignment and grudge/renown effects; win bonus only to winning-side participants over 20% damage; no renown from PvP kills; handover to a fellowship member.
- [ ] GameTest: each event type in `npc_arena` resolves on-screen with a scripted outcome and writes the right renown/deaths; off-screen resolution with a fixed seed.
- [ ] GameTest: `WorldEventsPayload` contents (radius filter, other players' events included, grudge events, max 16, own flag).
- [ ] Playtest: die to a Mordor orc until one rises, see it on the menu page, get ambushed (rarely), scar it, kill it for good with its weakness.
- [ ] Playtest: as an orc-pledged player, get a Gondor captain risen against you.
- [ ] Playtest: see events every 8-15 min, ride to one at a ruin, swing a warband clash; ride to a friend's event and help; break a war camp and check the ground is restored after it ends and after a restart.
- [ ] Playtest: with a spark/profiler load test (20 bots), the governor holds tick time near 40 ms or below.

## Tickets

Proposed 1-2 day tickets (not opened until this spec is approved; `pod:npc` unless marked; all after G3).

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
11. Event framework and scheduler: state machine, participant attachment, per-player rolls and cooldowns, band, site checks, territory, player-scaled caps, outcomes into records, off-screen resolution, rumours, start/end events, `/lotr worldevent`. (2 d)
12. Performance governor: tick-time states, hysteresis, Yellow limits, Red shedding, config, logging. (1 d)
13. Chunk ticket manager: `TicketController`, per-event and budget limits, release rules, startup validation. (1 d)
14. Participation, credit and grudges: participant tracking and sides, win bonus, interferer grudge/renown, handover, PvP and fellowship rules. (1.5 d)
15. Venue selection: `StructureVenues` client of the core holder, preference tables, open-terrain fallback, placement on venue floors, resident NPCs joining, venue cooldown, stub backend for tests. (1.5 d)
16. War camp blocks: templates (original NBT, 2+ per group), placer with replace rule, journal, exact restore (end, stop, startup), no-drop/piston/silk-touch guards. (2 d)
17. Ambush as a rare event type (own schedule and cooldowns). (1.5 d)
18. Event types A: beast hunt, captain hunting (run-to-venue), war camp behaviour. (2 d)
19. Event types B: warband clash, feud duel (with politics staging). (2 d)
20. Event types C: village raid, rescue (weakness reveal). (2 d)
21. Map sync: `WorldEventsPayload`, client cache, `MapMarkerClient` backend. (1 d)

**Dependencies in other pods**
22. **`pod:core`:** `core.api.MapMarkerClient` holder. (0.5 d)
23. **`pod:core`:** `core.api.StructureVenues` holder (venue record: id, bounds, centre, kind, faction, residents, protected). (0.5 d)
24. **`pod:world` (needs the World structure spec first):** venue structure set: ruins, small camps, outposts, watchtowers, towers, bridges and bandit hideouts per region, original NBT, density target >= 2 venues per 256 blocks of wild land. Likely several tickets, one per region group, sized by Opus 2.
25. **`pod:world`:** venue tags `#lotrrevival:event_venues/<kind>` on every venue and settlement structure, with owning faction metadata. (1 d)
26. **`pod:world`:** `StructureVenues` backend: locator over already-generated chunks / a World-side structure index, budgeted and cached. (1.5 d)
27. **`pod:progression`:** draw world-event markers on the map page (after #88 and the map GUI). (1 d)
28. **`pod:progression`:** `nemesis_hunt` quest kind. (2 d)
29. **`pod:progression`:** nemesis and world-event achievements, "Captain-bane" title. (1 d)

**Later**
30. Invasion leaders (after the invasion spec). (1 d)
31. Admin and test tooling: `/lotr nemesis spawn|renown|kill`, fixed-RNG GameTest helpers, scripted event runner for `npc_arena`, injectable tick-time source for governor tests. (1 d)

## Open questions for the maintainer

1. Should Dunland, Near and Far Harad, Easterlings and half-trolls be on by default? (Default kept: off.)
2. Governor thresholds: 30 ms (back off) and 40 ms (stop) of average tick time. Fine for your server, or tune per host?
3. Should a war camp left standing the full time leave anything permanent (for example its tents stay as a ruin venue), or always restore? (Default: always restore.)
4. Bridges as venues: worth asking World for them, or keep to ruins, camps, outposts, watchtowers, towers, villages and hideouts?
