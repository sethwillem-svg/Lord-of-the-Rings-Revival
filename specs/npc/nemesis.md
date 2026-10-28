# Nemesis system

- **Pod:** npc
- **Lead:** Opus 3
- **Status:** draft
- **Schedule:** after G3 (needs orc and other evil-faction NPC types, mounts and faction captain loadouts). Invasion leaders and hunt quests land with or after G4.
- **Depends on:** `specs/npc/npc-base.md` (`NpcEntity`, classes, names, speech, kills, despawn, loadouts), `specs/npc/hiring-trading.md` (hired units, unit levels, hire cost), `specs/core/core-apis.md` (alignment and kill path, faction relations, `LandProtection`, `FellowshipQuery`, coins), `specs/progression/quests-fellowships-banners.md` (kill quests, banners, achievements, titles, menu hub). Invasion spec (S8, not written yet).
- **Beyond parity:** Legacy LOTR had no nemesis system. Nothing here comes from it; this is our own design. Numbers are ours and tunable through config.
- **Why not the Nemesis System (PAL) mod:** we read it (MIT) as a feature checklist only; it cannot promote our NPCs, has no API and needs Architectury and owo-lib, so a native system fits our factions better.

## Summary

Evil-faction captains remember you. An orc that kills you, butchers your hired company or walks away from a fight with you can become a **nemesis**: a named NPC with a title, a rank in its faction's war-host, strengths, a weakness, scars and a grudge against the players it has met. Nemeses persist in the world's saved data even when no copy of them is loaded, come back to ambush the players they hold grudges against, sometimes escape or cheat death, feud and ally with each other along faction lines, lead invasions, carry bounties that other factions post on them, and can be broken and hired by a player their faction respects. Players care because Middle-earth stops being a field of interchangeable orcs: the Uruk who scarred you at the Fords now leads the raid on your keep.

## Player-facing behavior

1. **Rise.** A wild captain or soldier of an eligible faction kills you (or your hired units, or survives a hard fight with you). A red chat line announces it: "*Gashnak Skull-Splitter* of Mordor has risen to **Captain**." It roars, smoke bursts, and it retreats (vanishes).
2. **Hierarchy.** The Middle-earth menu (L) gains a **Nemeses** page: the war-hosts of each eligible faction as a pyramid (Overlord on top), each card showing name, title, rank, scars, known strengths and weaknesses, who it holds grudges against, and its rivals and allies. Your own grudges are highlighted.
3. **Ambush.** Later, while you travel Middle-earth away from home and banners, a nemesis that holds a grudge against you finds you: a horn sounds, it speaks a taunt naming you and how you last met, then attacks with its bodyguards, a rank-coloured boss bar and, at high ranks, a mount.
4. **The fight.** Nemeses hit harder and have more health by rank and carry traits. Badly hurt, a nemesis may flee. Killed, it may cheat death and come back scarred. Exploit its weakness or kill it after it has already cheated death and it dies for good.
5. **Rewards.** Killing a nemesis pays (or costs) alignment scaled by rank, counts for achievements and titles, and completes hunt quests that enemy factions post on it.
6. **Politics.** Between ambushes you hear rumours: a Captain challenged a Warlord and took his place; two Commanders swore an alliance; a Mordor Warlord slew an Isengard rival.
7. **War.** Invasions of a faction may be led by one of its nemeses, preferably one that hates a player nearby.
8. **Broken.** If you stand well with its faction, a beaten nemesis may kneel instead of fleeing; pay and it swears itself to you as a hired captain.

## Rules and numbers

### Who can become a nemesis

| Thing | Value | Notes |
| --- | --- | --- |
| Faction flag | faction tag `#lotrrevival:nemesis_factions` (npc pod writes it) | Default at G3: `mordor`, `isengard`, `angmar`, `dol_guldur`, `gundabad`. Datapacks may add any faction, good ones included. A tag on our faction datapack registry, so no core change. |
| Type opt-out | entity type tag `#lotrrevival:never_nemesis` | For civilians-only types, trolls, special NPCs. |
| Classes | GUARD and CAPTAIN | Civilians never. |
| Must be | wild (not hired, not structure-bound quest/trader NPCs), alive, in Middle-earth, not name-tagged | |
| Already a nemesis | gains renown instead of a new record | |

### What promotes them (renown)

A wild eligible NPC with no record gets one when its first renown gain passes the **promotion roll**; after that every gain is just added.

| Trigger | Renown | Promotion roll (new NPC) | Grudge |
| --- | --- | --- | --- |
| Kills a player (direct, or its projectile) | +10 | 35%, +20% if CAPTAIN class | that player +3 |
| Kills a hired unit of a player | +2, +1 per 5 unit levels (max +6) | 10%, +3% per 5 unit levels (max 30%) | owner +1 |
| **Survives a fight:** took >= 50% of max health from players in one fight, then the fight ended with it alive (all damaging players dead, or > 32 blocks away for 30 s) | +4 | 25% | each damaging player +1 |
| Escapes (see below) | +4 | n/a | attacker +1 |
| Cheats death | +6 | n/a | killer +2 |
| Wins a feud or challenge | +8 | n/a | none |
| Ambush kills its target again | +5 on top of the kill | n/a | +3 (revenge) |

Players in creative or spectator, FakePlayers and players on Peaceful never cause promotions or grudges.

### Ranks

Rank comes from renown, but each faction's host has limited seats. If the seat is full the nemesis waits at the lower rank and becomes a **challenger** (see Politics).

| Rank | Renown | Seats per faction | Health | Melee damage | Armor | Knockback res. | Traits (strengths) | Bodyguards | Extras |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Marked | 0 | 12 | x1.25 | x1.10 | +2 | +0.1 | 1 | 0 | white boss bar |
| Captain | 15 | 8 | x1.6 | x1.20 | +4 | +0.2 | 1 | 2 | captain loadout |
| Commander | 35 | 5 | x2.2 | x1.35 | +6 | +0.3 | 2 | 3 | yellow bar |
| Warlord | 70 | 3 | x3.0 | x1.50 | +8 | +0.4 | 3 | 4 | rides faction mount, purple bar |
| Overlord | 120 | 1 | x4.0 | x1.65 | +10 | +0.5 | 4 | 6 | red bar, war-banner bearer when banners exist |

- Multipliers apply to the NPC's own race base values (attribute modifiers with fixed ids, so they are replaced, never stacked). Movement speed: +3% per rank step above Marked (max +12%).
- Every nemesis has exactly **one weakness** from creation. A Commander or higher may lose it once by challenge win ("overcame his fear of fire") — 25% per won challenge.
- Gear: Marked uses the type's normal loadout; Captain and above use the faction captain loadout; Warlord and above get the loadout's best tier in every slot. All from npc loadout data (`NpcLoadoutData`), never vanilla diamond.
- Renown cap 200. Seats and thresholds are config.

### Traits

Data-driven: datapack `data/<ns>/lotrrevival/nemesis_traits/<id>.json` (reloadable) with kind (`strength`, `weakness`, `scar`), effect id, numbers, lang key, allowed factions/races. Effects are a fixed set in code; data picks and tunes them. Starting set (ours):

| Strength | Effect |
| --- | --- |
| Iron-hide | -40% projectile damage |
| Fire-blooded | immune to fire and lava |
| Bloodthirsty | heals 20% of melee damage dealt |
| Armour-breaker | melee ignores 25% of target armor |
| War-horn | once per fight at <= 50% health, calls 3 soldiers of its type |
| Poisoned blade | melee poisons 4 s |
| Unshakeable | knockback immune, cannot be stunned or slowed |
| Fleet | +15% movement speed, leaps at targets 4-10 blocks away (cooldown 8 s) |
| Hate-fueled | +25% damage against a player it holds a grudge against |

| Weakness | Effect |
| --- | --- |
| Fears fire | x1.5 fire damage; flees for 5 s when set alight |
| Sun-sick | -25% damage in direct daylight (orcs only) |
| Open guard | x1.5 damage taken from behind |
| Arrow-shy | x1.4 projectile damage |
| Beast-fear | flees 5 s from wolves, horses and wargs not its own |
| Slow wits | first 2 s of every fight it does nothing (ambushes included) |

Scars are added by cheat-death (below): resistance to the damage type that "killed" it (-50%) and a scar title.

**Intel:** strengths are shown on the hierarchy page once any player has fought it; the weakness is shown to a player once they have hit it with its weak spot, completed a hunt quest step that reveals it, or read it from a captured orc letter (later). Hidden ones show "?".

### Names and titles

- First name from the race name bank (`npc_names/<race>_male_first.json`).
- Title from a new bank `npc_names/nemesis_titles.json`: list of entries `{ "text": "Skull-Splitter", "tags": ["brutal"] }`, our own words; tags let trait-themed titles match (a Fire-blooded nemesis prefers `fire` titles). No canon unique names (no Azog, Gorbag...).
- Scar titles replace the title on cheat-death ("the Burnt", "the Arrow-Ridden", "the Half-Drowned", from the scar trait's lang key).
- Display: `<First> <Title>` plus rank line "Captain of Mordor". Names are unique per faction (re-roll up to 10 times). All text is lang keys or bank strings, translatable.

### Persistence

- **Record** (the nemesis) lives in `SavedData` `lotrrevival_nemeses` on the overworld storage, keyed by nemesis id: faction, entity type, name parts, renown, rank, traits, weakness, scars, revealed-to sets, cheat-death count, grudges (player UUID -> score, top 8 kept), kills of players and units, allies, rival, status, manifestation entity UUID, last seen dimension/pos/time, created time.
- **Status:** `AT_LARGE` (no body), `MANIFEST` (one body exists), `SWORN` (hired, see Hiring), `DEAD` (kept 7 in-game days for the menu and quests, then purged).
- **Body:** an ordinary `NpcEntity` of the record's type with data attachment `lotrrevival:nemesis_link` (nemesis id + manifestation number). At most **one** body per record; the manifestation number increments on each spawn, and any body whose number is stale is discarded on load.
- **Unload/despawn:** a body never despawns naturally (excluded like targeting NPCs). When its chunk unloads, or no player is within 128 blocks for 60 s, the body retreats: removed, record goes `AT_LARGE`, last seen updated. A body loaded later from a chunk with a stale number is discarded silently.
- Ordinary NPCs that get promoted turn into the record's first body in place (no retreat) unless the trigger was a player kill or an escape, which retreat them.
- Server stop: bodies are saved with their chunk like any NPC; on load the stale-number rule handles duplicates.

### Ambush scheduling and limits

| Thing | Value | Notes |
| --- | --- | --- |
| Check | every 20 s per online player, staggered by player | |
| Eligible player | survival/adventure, not Peaceful, in Middle-earth, online >= 5 min this session, not in a vehicle other than a mount, > 128 blocks from their respawn point, not inside land where `LandProtection.blocksFactionSpawn(level, pos, faction)` is true for the nemesis's faction, not within 64 blocks of an active invasion | |
| Candidates | `AT_LARGE` records holding a grudge >= 2 against the player | Hunt-quest targets count as grudge 2 for the quest holder. |
| Chance per check | 1.5% x (1 + 0.25 x highest grudge, max 3) x 2 if the player is inside the nemesis faction's region | Expected wait roughly 15-40 min. |
| Per-player cooldown | 25 min of online time after an ambush against them (20 min if they won) | |
| Server cap | at most `max_manifest` nemesis bodies (default 4, ambush, invasion and quest bodies together) | |
| Spawn spot | surface 16-28 blocks away, out of the player's line of sight if one exists, never in water or protected land; give up this check if none in 16 tries | |
| Allies | an allied `AT_LARGE` nemesis with a grudge on the same player joins at 40% | counts toward cap |
| Leave | if no grudge holder is within 64 blocks for 30 s, it retreats (+0 renown) | |
| Fellowships | fellowship members within 32 blocks are valid targets of its bodyguards; the nemesis itself fights its grudge holder first | |

### Escape and cheat-death

- **Escape:** once per manifestation, when a hit would leave it at <= 20% health, chance by rank (Marked 50%, Captain 40%, Commander 30%, Warlord 20%, Overlord 10%), +20% if it has a grudge 5+ against the attacker (it wants another try). Not while kneeling, rooted, or when Slow wits is active. Effect: the damage still applies, smoke, taunt line, body removed, `AT_LARGE`, renown +4.
- **Cheat-death:** when the body dies, chance 30% on the first death, 15% on the second, never after two. Not allowed if it **dies for good** (below). Effect: the body dies normally (loot, alignment, events), record stays `AT_LARGE`, renown +6, gains the scar for the killing damage type (fire, projectile, explosion, magic, fall, drowning, melee "Cleaved") and its title, grudge on the killer +2. Next ambush it greets the killer with a scar line.

### How nemeses die for good

Record goes `DEAD` when any of these happen:

1. Dies and the cheat-death roll fails, or it already cheated death twice.
2. Dies to damage of its weakness's type (fire for Fears fire, projectile for Arrow-shy, from-behind for Open guard...) or while Sun-sick in daylight.
3. Loses a challenge or feud (slain off-screen).
4. Is sworn (hired) and the unit later dies (record `DEAD` then).
5. **Fades:** no grudge holder has logged in for 30 real days (config), or the faction's seat count shrinks and it has the lowest renown of the Marked.
6. Admin command.

### Politics (feuds and alliances)

Every 30 min of server time, for each eligible faction with >= 2 `AT_LARGE` nemeses, 60% chance of one event, weighted:

| Event | Weight | Rule |
| --- | --- | --- |
| Challenge | 4 | A challenger (enough renown, seat full) fights the lowest-renown holder of the rank above. Power = renown + 1d30; higher wins. Winner takes the rank, loser is slain (`DEAD`). Draw: nothing. |
| Cross-faction feud | 3 | A nemesis of a faction whose relation to this one is `ENEMY` or `MORTAL_ENEMY` (`FactionRelations`). Same power roll; loser `DEAD`, winner +8 renown. If both have bodies within 48 blocks of each other, they fight in the world instead. |
| Alliance | 2 | Two of the same faction or `ALLY`/`FRIEND` factions with no rival link become allies (max 1 ally each). |
| Betrayal | 1 | Allied pair: 50% the stronger slays the other (+8), else the alliance breaks. |
| Ascension | 2 | Random nemesis +6 renown. |

- Nemeses never feud with members of their own faction except by challenge.
- Rumour: one grey italic chat line to every online player holding a grudge with either party, and to players pledged to either faction. Nobody else is spammed.

### Rank-scaled alignment

Killing a nemesis body multiplies its type's `kill_value` before core's kill-alignment call, so enemy factions pay more and the nemesis's own (and allied) factions take more: Marked x2, Captain x3, Commander x4, Warlord x6, Overlord x10. Applies to cheat-death deaths too (the body really died). Escape gives nothing. Players pledged to the nemesis's faction who kill it lose alignment at the same multiplier.

### Invasion leaders (with the invasion spec)

- When an invasion of faction F starts, if any `AT_LARGE` F nemesis of rank Captain+ exists and the cap allows, 50% (75% if a player in range has a grudge with it) that the highest-ranked such nemesis (grudge holders preferred) leads it. It spawns with its bodyguards as part of the first wave and its boss bar shows "leading the invasion".
- The leader's death or escape routs the current wave (the invasion spec decides what routing means). Banners that keep F's invasions out keep the leader out too.

### Hiring a defeated nemesis ("Broken")

- Only a player whose alignment with the nemesis's faction is at least that faction's captain-unit hire requirement (`hire_min` of the faction's captain), or who is pledged to it. With the default faction relations this means evil-aligned players, who usually meet friendly orcs as nemeses through retaliation fights.
- When such a player's hit would leave the nemesis at <= 20% health, it **kneels** instead of escaping: stops attacking, takes no damage for 10 s, speaks a yield line.
- Right-clicking it while kneeling opens the hire offer: cost = the faction captain's hire cost x (1 + rank index), rank index Marked 0 .. Overlord 4. Declining or walking away > 16 blocks ends the kneel; it escapes.
- On hire: becomes a hired unit of that player (warrior task), unit level 5 x (rank index + 1), keeps name, title, strengths and scars; loses its weakness's flee effects; boss bar removed; record `SWORN`. It no longer ambushes or feuds. A sworn nemesis that dies is `DEAD` for good (units have no recovery).
- Unit cap and other hiring rules from `hiring-trading.md` apply.

### Hunt quests and bounties (progression)

- New kill-quest kind `nemesis_hunt`, offered by NPCs of factions that are `ENEMY`/`MORTAL_ENEMY` of the nemesis's faction, needs +50 alignment with the giver. Target: a named `AT_LARGE` nemesis of that enemy faction, preferring ones that hold a grudge against the accepting player, then highest rank.
- Accepting gives the quest holder grudge 2 with the target (so it hunts them) and a hint of its region; 1 in 3 giver talks reveal its weakness.
- Complete when the target becomes `DEAD` by the holder, their hired unit, or a fellowship member within 32 blocks; cheat-death does **not** complete it (the quest updates: "he lives yet"). Fails if it dies by others or by politics.
- Reward: template alignment and coins x the rank multiplier above. Weight 3 among kinds.

### Achievements and titles (progression)

Progression listens to our events; suggested set: first nemesis risen against you; slay a nemesis; slay the nemesis that last killed you (Vengeance); slay a Warlord; slay an Overlord; make a nemesis cheat death; break and hire a nemesis; slay 10 nemeses (unlocks title "Captain-bane"). Names and art are progression's.

### Speech

New speech categories in `NpcSpeech`: `nemesis_rise`, `nemesis_ambush`, `nemesis_ambush_revenge` (it killed you before), `nemesis_ambush_scarred` (you scarred it), `nemesis_escape`, `nemesis_cheat_death`, `nemesis_death`, `nemesis_kneel`, `nemesis_sworn`, per race where needed. Placeholders: player name, nemesis name, scar, last meeting place (biome). Delivered like other NPC speech: chat to the relevant player only.

### Config (`lotrrevival-npc-server.toml`, `[nemesis]`)

`enabled` (true), promotion chances, renown values, rank thresholds and seats, `max_manifest` (4), ambush check period, chance, cooldown, safe radius, politics period and weights, cheat-death chances, rank alignment multipliers, fade days, `allowed_dimensions` (middle_earth). Lists via `Arrays.asList`.

### Performance limits

- Records: <= 29 per faction by seats (12+8+5+3+1); five factions ~150 records; one SavedData, saved only when dirty.
- No per-tick scans: ambush checks every 20 s per player, politics every 30 min, body retreat checks every 2 s only on existing bodies (<= `max_manifest`).
- Fight tracking for "survives a fight" lives on the NPC only while a player damaged it in the last 60 s.
- Sync: the menu page asks the server for a snapshot when opened (C2S `NemesisRosterRequest`), server answers S2C `NemesisRosterPayload` with at most 60 cards (the player's grudges first, then their quest targets, then faction tops), rate-limited to 1 request per 2 s per player. Boss bars use vanilla `ServerBossEvent`, shown within 48 blocks.

## Edge cases

- **Multiplayer: shared hierarchy (decision).** Records belong to the world and their faction, not to a player. One orc is the same nemesis to everyone; grudges are per player inside it. Why: our NPCs are shared world entities, invasions, bounties, feuds and fellowships are faction-level and multiplayer, and per-player rosters would let the same orc be five different nemeses or none. Cost: on busy servers grudges spread thinner; the per-faction seat caps and grudge-targeted ambushes keep it personal enough. Single-player behaves identically.
- **Two grudge holders together:** the nemesis targets the one with the higher grudge; both get the boss bar and both count for quests if in the same fellowship.
- **Grudge holder logs out mid-fight:** body retreats after 30 s with no grudge holder within 64 blocks; no renown.
- **Player death in an ambush:** counts as a kill (+10 / +5 revenge); the body retreats 5 s later so it does not camp the death spot.
- **Hired units of the grudge holder:** valid targets; their kills of the nemesis count as the owner's for quests and achievements, not for alignment (normal hired-kill rules).
- **Killed by other NPCs or the environment:** cheat-death and die-for-good rules still apply; no player credit, rumour to grudge holders.
- **Promotion while hired:** never; a hired unit is not wild. A sworn nemesis ignores all nemesis rules except name and traits.
- **Name tag:** cannot be applied to a nemesis body; the interaction is refused and the tag is not used up.
- **Dimension change:** NPCs never change dimension; bodies exist only in `allowed_dimensions`.
- **Datapack removes a faction from the tag:** existing records stay but never manifest or feud; they fade on schedule. Removing a trait id: the trait is dropped from records on load with one warning.
- **Peaceful:** no ambushes, no promotions; existing bodies retreat.
- **Command/creative spawn of a nemesis type:** ordinary NPC, no record.
- **Two bodies after a crash:** stale manifestation number rule discards the old one on load.
- **Interaction with banners:** ambush spawn spots are never in protected land; a body chasing into protected land keeps fighting (protection blocks spawning, not pursuit), same as other enemy NPCs.

## Data and APIs

- **NPC pod (new):** `npc/nemesis/` — `NemesisRecord`, `NemesisData` SavedData `lotrrevival_nemeses`, attachment `lotrrevival:nemesis_link` (in `NpcAttachments`), trait effects, ambush scheduler, politics, manifestation, hiring hook, commands `/lotr nemesis list|info|spawn|renown|kill|purge` (op level 2).
- **Datapack (npc datagen):** faction tag `#lotrrevival:nemesis_factions`, entity type tag `#lotrrevival:never_nemesis`, `nemesis_traits/*.json`, `npc_names/nemesis_titles.json`, speech lines, lang.
- **Events (NeoForge game bus, npc API):** `NemesisRisenEvent` (record, trigger, player or null), `NemesisRankChangedEvent`, `NemesisDefeatedEvent` (record, killer, outcome `CHEATED_DEATH` / `DEAD`, cause, killer is grudge holder), `NemesisSwornEvent`, `NemesisAmbushEvent` (pre-spawn, cancellable, for other pods and later mods).
- **Read API for other pods:** `NemesisApi` in npc: `find(id)`, `atLarge(faction)`, `grudges(player)`; progression uses it for hunt quests. If progression must not depend on npc, a thin `core.api.NemesisQuery` holder like `LandProtection` (needs a `pod:core` ticket).
- **Payloads:** `NemesisRosterRequest` (C2S), `NemesisRosterPayload` (S2C), registered like the existing npc payloads.
- **Client:** `client/npc/nemesis/NemesisHierarchyScreen`. Adding the page to the menu hub (`MenuPage`, `MenuHubScreen`) is a progression-owned edit: `pod:progression` ticket.
- **Core APIs used:** `Alignment` kill path, `FactionRelations`, `LandProtection.blocksFactionSpawn`, `FellowshipQuery`, coins, `ModDimensions.MIDDLE_EARTH`.
- **Small NPC-pod edits:** `NpcEntity.alignmentKillValue()` rank multiplier, `NpcEntity` despawn exclusion, `NpcLoadoutData` rank tiers.

## Out of scope

- Nemeses for good factions by default (possible by datapack, untuned).
- Player-vs-player nemeses.
- Captured orc letters / intel items (hook left in Intel).
- Fortress assaults and owning strongholds (Shadow of War style).
- Nemesis-specific models or textures beyond scale and gear (art can follow).
- Integration with the PAL Nemesis System mod.

## Acceptance tests

- [ ] GameTest: an eligible guard kills a mock player with a forced roll → a record with a bank name and title, Marked rank, grudge 3, body retreated; a hired unit kill by a hired orc → no record.
- [ ] GameTest: renown thresholds and full seats → challenger stays at the lower rank; challenge resolution with a fixed seed promotes and kills the right ones.
- [ ] GameTest: rank stat modifiers applied once, replaced (not stacked) after a rank-up; movement speed formula.
- [ ] GameTest: body unload → `AT_LARGE`; loading a stale body discards it; never two bodies for one record.
- [ ] GameTest: ambush eligibility (Peaceful, creative, safe radius, protected land via `LandProtection.swapForTesting`, cooldown, cap) with the dimension list overridden for the test server.
- [ ] GameTest: escape and cheat-death chances by rank with fixed RNG; weakness-type death is permanent; third death is permanent.
- [ ] GameTest: rank kill-value multiplier through the kill path; `NemesisDefeatedEvent` fires once per death with the right outcome.
- [ ] GameTest: Broken flow: qualifying player brings it under 20% → kneel, hire → hired unit at the right level, record `SWORN`.
- [ ] GameTest: roster payload capped at 60 and ordered (grudges first).
- [ ] Playtest: die to a Mordor orc until one rises, see it on the menu page, get ambushed away from home, watch it escape, scar it, kill it for good with its weakness; check alignment and chat.
- [ ] Playtest: two players, shared hierarchy, only the grudge holder gets ambushed; fellowship partner can help and the hunt quest completes.

## Tickets

Proposed 1-2 day tickets (not opened until this spec is approved; all `pod:npc` unless noted, gate after G3):

1. **Records and persistence:** `NemesisRecord`, SavedData, `nemesis_link` attachment, faction and opt-out tags, config section, `/lotr nemesis list|info|purge`. (1.5 d)
2. **Names, titles and trait data:** `nemesis_titles` bank, `nemesis_traits` datapack format + loader, datagen of the starting set, lang. (1 d)
3. **Promotion and renown:** the three triggers incl. fight tracking, promotion rolls, renown, rank seats/challengers, `NemesisRisenEvent` / `NemesisRankChangedEvent`, rise speech. (2 d)
4. **Manifestation and rank stats:** spawn body from record, stat modifiers, loadout tiers, bodyguards, Warlord mount, boss bar, retreat/unload/stale-body rules, despawn exclusion. (2 d)
5. **Trait effects:** the strength, weakness and scar effects. (2 d)
6. **Ambush scheduler:** eligibility, chances, cooldowns, cap, spawn spot search, allies joining, leave rule, ambush speech, cancellable `NemesisAmbushEvent`. (1.5 d)
7. **Escape, cheat-death, dying for good:** rules, scars and titles, rank alignment multiplier, `NemesisDefeatedEvent`. (1.5 d)
8. **Politics:** challenges, cross-faction feuds via relations, alliances, betrayal, ascension, fading, rumours, in-world feud when bodies meet. (1.5 d)
9. **Hierarchy sync and screen:** request/snapshot payloads, `NemesisHierarchyScreen` (pyramid, cards, intel "?"). (2 d) + **`pod:progression`**: add the Nemeses page to the menu hub (0.5 d).
10. **Broken and sworn:** kneel, hire offer, conversion to hired unit, `NemesisSwornEvent`. (1.5 d)
11. **`pod:progression`:** `nemesis_hunt` quest kind using `NemesisApi` and events. (2 d)
12. **`pod:progression`:** nemesis achievements and the "Captain-bane" title. (1 d)
13. **Invasion leaders:** after the invasion spec. (1 d)
14. **Admin and test tooling:** `/lotr nemesis spawn|renown|kill`, GameTest helpers with fixed RNG. (1 d)

## Open questions for the maintainer

1. Shared per-faction hierarchy (proposed) or per-player rosters?
2. Default eligible factions: only the five orc-led hosts, or also Dunland, Harad, Easterlings, half-trolls?
3. Should "survives a fight" promote at all, or only kills (less frequent nemeses)?
4. Hiring Broken nemeses: only for players in good standing with the nemesis's faction (proposed), or also a costly "dominate" for anyone?
5. Ambush rate: one per ~15-40 min of eligible travel with a 25 min cooldown. Too much or too little?
6. Should ambushes ever happen outside Middle-earth (proposed: never)?
