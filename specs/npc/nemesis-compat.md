# Nemesis System compatibility

- **Pod:** npc
- **Lead:** Opus 3
- **Status:** draft
- **Depends on:** `specs/npc/npc-base.md` (`NpcEntity`, classes, kills, names, despawn), `specs/npc/hiring-trading.md` (hired units, unit levels), `specs/core/core-apis.md` (alignment, `LandProtection`, `FellowshipQuery`), `specs/progression/quests-fellowships-banners.md` (kill quests, banners, achievements). Invasions (S8) and evil-faction NPC types (G3) are not specced yet; the parts that need them are marked **later**.
- **Third-party mod:** *Nemesis System [Neo/Fabric]* by palgaminghd, CurseForge project 1590589, NeoForge file `nemesis_system-neoforge-2.0.0.jar` (file id 8544598, SHA-1 `f00fb3fb4d72a58a24ebfddb50ab88983001924b`), MIT. Mod id `nemesis_system`. Not on Modrinth. Unlike the original LOTR Mod this one is MIT, so its code was read directly to write this spec; class names below are that mod's own public classes.

## Summary

The Nemesis System mod turns a monster that kills you into a named, levelled rival that comes back to ambush you, can cheat death once, scars, feuds and allies with other rivals, and is listed on a hierarchy screen (H). This spec makes it work with our faction NPCs **when the player has installed it**: an orc of an evil faction that kills you can rise as a nemesis with a name from our banks, ambushes respect Middle-earth and banners, killing a nemesis pays alignment scaled by its rank, and other systems (quests, achievements, invasions, hiring) can react to nemesis events. Without the mod installed nothing changes. Players care because it gives Mordor a face: the orc captain who killed you at Cirith Ungol remembers you.

## What the mod does today (2.0.0, NeoForge)

Facts from reading the jar, so implementers do not have to.

| Area | Behavior | Consequence for us |
| --- | --- | --- |
| Dependencies | `neoforge.mods.toml` **requires Architectury API (>= 13.0) and owo-lib** (both sides). The CurseForge page lists only owo-lib. owo-lib for NeoForge 1.21.1 is a beta build. | Installing it means three jars. Another reason not to make it required. |
| Who can be promoted | When a player dies, the killer (`DamageSource.getEntity()`) must be a `Mob` that **implements the vanilla `Enemy` interface**. Chance 0.12, +0.10 if it holds a main-hand item, +0.10 if it wears any armor, +0.20 if it has a custom name, +0.05 per "humiliation" point of the player (2 if wearing any armor whose item id contains `diamond` or `netherite`, 1 if XP level >= 30), capped at 0.85. There is no entity tag, config list or blacklist. | `NpcEntity` extends `PathfinderMob` and does **not** implement `Enemy`, so **none of our NPCs can be promoted by the mod itself.** |
| Eldritch Mobs | On NeoForge the integration is hard-wired off (always the "none" implementation). | Ignore Eldritch Mobs. |
| Promotion | New entry: first name from a fixed list of 62 orc-style names (several are canon characters: Azog, Bolg, Lurtz, Gorbag, Shagrat, Ugluk, Golfimbul), title "the &lt;entity type name&gt;", level 1 + humiliation, status RETREATED. An existing nemesis that wins again gains 1 + humiliation levels. Roster cap 10 living entries; the lowest level is dropped. The killer is then **discarded** (smoke, sound). | We want our names and we must not lose hired units this way. |
| Storage | Per-player data attachment `nemesis_system:army` (copied on death) holding a list of entries; per-entity attachment `nemesis_system:link` (nemesis id + owner player UUID). Entries: uuid, first name, title, title-fixed flag, entity type id, level 1..15, traits, scars, resistances, kills, encounters, status (ACTIVE, RETREATED, DEAD, CHEATED_DEATH), target player, ally id, last death cause, cheated-death flag. | The entry has no faction. Our faction comes from the entity type, which is fixed per type, so that is enough. |
| Ranks | Level 1-2 Lieutenant, 3-4 Captain, 5-6 Commander, 7-9 Warlord, 10+ Overlord. Milestone titles overwrite the title (unless fixed) at 5 "the Undying", 8 "the Dread", 10 "the Overlord" and grant a trait. | Used for our rank scaling below. |
| Ambush | Server-wide: once 15 min have passed since the last ambush, every 5 s a 5% roll; picks one random RETREATED or CHEATED_DEATH entry among all online players; skipped if that player is within 200 blocks of their respawn point in the same dimension. Spawns the entity type with `EntityType.create` (**no `finalizeSpawn`**), 12-20 blocks away, sets name, persistence, link, applies buffs, adds it, targets the player. Level >= 3: 2 bodyguards (4 at level >= 6) of the same type, plain `create`, no link. Level >= 7: rides a vanilla zombie horse. Allied entry may join. The taunt and goat horn play even if the spawn is refused. | Our NPC would arrive with no name, role, gear or faction loadout, and would be wearing vanilla diamond/netherite. Needs fixing on our side. |
| Buffs | Max health x(1 + 0.1 per level above 1, max 2.5), attack x(1 + 0.05, max 1.8), armor + 0.75/level (max 10), knockback resistance + 0.04/level, scale 1 + 0.04/level (max 1.6), vanilla gear (chain helm + iron sword at 3, diamond chest + sword at 6, netherite helm + chest + glowing at 10), resistances from scars, trait passives. **Bug:** movement speed base is *set* to 1.0..1.25 instead of multiplied, so any nemesis runs at roughly 4x normal speed. | We must restore our race speed after their buffs. Report upstream. |
| Combat | Damage from a nemesis to a player who is neither its target nor its current attack target is cancelled. At <= 15% health it may escape (chance 0.6 - 0.045 x level, clamped 0.1..0.6): discarded, status RETREATED. On death: 20% once to cheat death (status CHEATED_DEATH, +1 level, scar title from damage type, resistance); otherwise DEAD. The entity really dies either way (drops, death events). | Death events still reach our alignment code. Escape does not (no death). |
| Politics | Every 30 min, per online player, 50% chance of: feud 40% (one entry deletes another, winner +1 level), alliance 30%, ascension 30% (+1 level). | Works unchanged. |
| Reaper | Every 2 s for online owners: an ACTIVE entry whose entity has not been seen for 30 s goes back to RETREATED. Entities of offline owners are not touched. | A nemesis NPC can be left standing in the world after its owner logs out; we clean that up. |
| Client | H opens an owo-lib UI screen with the roster, synced by one S2C payload. All chat/UI text is hard-coded English (only the key binding is in lang). | Our names show fine; their messages stay English. |
| Commands | `/nemesis list [player]`, `clear`, `remove <name>`, `reset <player>`, `reset all`. | Admin tool for playtests. |
| API | **None.** No events, no tags, no config file, no mixins. All classes are public; useful static entry points exist (`NemesisPlatform.storage()`, `TraitHandler.assign/applyMilestone`, `NemesisBuffs.apply`, `NemesisNetworking.sync`, `NemesisTracker.track`). Static maps (tracker, boss bars, timers) are never cleared on server stop. | Every hook we need is either an attachment we can read, a vanilla/NeoForge event we already get, or a call into their public statics through one adapter class. |

## Player-facing behavior

With the Nemesis System mod installed (and the compat enabled in config):

1. A wild orc (later: any NPC of an eligible evil faction) kills you. It may rise as a nemesis: a red chat line names it with a name from our name banks ("Muzgrat the Gutter-Knife has been promoted to Lieutenant (Level 1)!"), smoke, and it vanishes into the shadows. It appears on the H screen.
2. Later, while you are in Middle-earth and away from your respawn point, it ambushes you with its faction's own gear, its rank's boss bar, bodyguards of its own faction, and a taunt. It never ambushes you in the Overworld or inside a banner-protected area that keeps its faction out.
3. Killing it pays much more alignment than an ordinary orc (scaled by rank), and if you were friendly with its faction, costs much more. If it cheats death it returns scarred and stronger; when it is truly defeated everyone involved is told, and achievements and quests can count it.
4. NPCs of factions at war with it may offer a quest to hunt a nemesis on your roster (**later**, progression).
5. Invasions of its faction may be led by one of your nemeses (**later**, invasions).
6. A nemesis of a faction you serve, beaten to its knees, may swear itself to you as a hired captain (**later**, needs maintainer sign-off).

Without the mod: no change at all, no extra keybind, no log spam (one info line at startup saying the compat is inactive).

## Rules and numbers

### Eligibility (our own promotion; the mod's own check never sees our NPCs)

| Thing | Value | Notes |
| --- | --- | --- |
| Eligible factions | config list, default `lotrrevival:mordor` | Later defaults add `isengard`, `angmar`, `dol_guldur`, `gundabad` as their NPC types land. Server owners may add others (even good factions). |
| Eligible classes | GUARD and CAPTAIN | Civilians never promote. |
| Excluded | hired units (any owner), NPCs with a vanilla name tag set by a player, NPCs already linked to *another* player's nemesis | Hired units must never be discarded by promotion. |
| Killer resolution | `DamageSource.getEntity()` is an eligible `NpcEntity` (covers arrows/thrown weapons it fired) | Kills by a hired unit owned by another player never promote. |
| Victim | any `ServerPlayer` not in creative or spectator, not a FakePlayer | |
| Chance, new nemesis | 0.20 base, +0.15 if CAPTAIN class, +0.10 if holding a weapon, +0.05 per humiliation point, cap 0.85 | Humiliation: +2 if the player's total armor value >= 15, +1 if XP level >= 30 (our gear has no `diamond` in its ids, so we do not reuse their string check). |
| Chance, existing nemesis kills its owner again | 1.0 | Gains 1 + humiliation levels, kills +1, status RETREATED, target = this player. Shadow-of-Mordor rule; theirs rolls the chance again. |
| Starting level | 1 + humiliation, CAPTAIN class +1 | Clamped 1..15 by their data class. |
| Roster cap | 10 living entries; on overflow drop the lowest-level one | Same as theirs. |
| Name | first name rolled from our race name bank (`npc_names/<race>_male_first.json`); title rolled from a new bank `npc_names/nemesis_titles.json` (our own list, e.g. "the Gutter-Knife", "Skull-Splitter") | Their milestone and scar titles still replace ours later; we accept that. Our banks must not reuse canon unique names (no Azog, Gorbag, etc.). |
| Promotion effect | same chat line format, smoke, sound; the NPC is removed exactly the way our despawn removes it (frees home/anchor slots) | |
| Already-linked NPC killed the player but is not eligible any more (config changed) | no promotion, no level | |

### Ambush fix-ups (we cannot stop their roll; we adjust what spawns)

All checks run when a nemesis-linked `NpcEntity` joins a level (`EntityJoinLevelEvent`, the link attachment is set before their `addFreshEntity`).

| Thing | Value | Notes |
| --- | --- | --- |
| Allowed dimensions | config list, default `lotrrevival:middle_earth` | Join refused elsewhere. |
| Banner protection | join refused if `LandProtection.blocksFactionSpawn(level, pos, faction)` at the spawn position | Their taunt and horn already played; accepted until upstream adds a veto event. |
| Refused join | the entry is reset to RETREATED and the owner re-synced on the next tick; bodyguards and mount spawned in the same tick within 6 blocks are refused too | Otherwise their reaper takes 30 s to notice. |
| Initialisation | if the NPC never ran spawn finalisation: gender, role, name seed as `finalizeSpawn` would; **no** name roll (the nemesis name is the custom name) | |
| Gear | replace their vanilla gear with our faction loadout; at Captain rank (level >= 3) and above use the faction's captain loadout | Keep their resistances and trait effects. |
| Movement speed | base = race speed x (1 + min(0.015 x (level - 1), 0.25)) | Undoes their bug; their intended multiplier. |
| Health | keep theirs (already scaled from our race base) | |
| Bodyguards | unlinked `NpcEntity` of the same type joining within 6 blocks in the same tick: run normal finalisation (name, role, loadout) and mark them wild | |
| Mount | a vanilla zombie horse that the nemesis rides on join: dismount and discard it the next tick; **later** swap for the faction mount (warg) once mounts exist | |
| Orphans | every 5 s, a nemesis-linked `NpcEntity` whose owner is offline, or whose entry is not ACTIVE, or is missing, retreats (smoke, removed as by despawn) | Stops duplicates and immortal persistent orcs. |

### Alignment (the "reward/penalty scaled by rank" rule)

Killing a nemesis-linked `NpcEntity` multiplies its type's `kill_value` before core's kill-alignment call. Core then applies gain or loss as usual, so one multiplier covers both reward (enemy faction) and penalty (friendly faction).

| Rank (level) | Multiplier |
| --- | --- |
| Lieutenant (1-2) | x2 |
| Captain (3-4) | x3 |
| Commander (5-6) | x4 |
| Warlord (7-9) | x6 |
| Overlord (10-15) | x8 |

Applies when the entity dies (including the cheat-death death). Escape gives nothing. Multipliers are config values.

### Events we publish (NeoForge game bus, `npc.compat.nemesis`)

Fired only while the mod is loaded. Other pods listen to these, never to the mod.

| Event | When | Fields |
| --- | --- | --- |
| `NemesisPromotedEvent` | after our promotion or level-up | owner player, nemesis id, display name, level, rank, faction, new or returning |
| `NemesisDefeatedEvent` | one tick after a nemesis-linked NPC dies (status read after their handler ran, so listener order does not matter) | owner, killer (nullable), nemesis id, name, level, rank, faction, outcome `DEAD` or `CHEATED_DEATH`, killer is owner, killer is in owner's fellowship |

### Bounty / hunt quest (**later**, progression)

- A new kill-quest kind `nemesis_hunt`, offered by NPCs of a faction that is an enemy of the nemesis's faction, to a player whose roster holds a RETREATED or CHEATED_DEATH entry of an eligible faction. Weight 3 among kinds.
- Completes on `NemesisDefeatedEvent` with outcome DEAD for that nemesis id, killer = owner or a fellowship member.
- Reward: the template's alignment and coins x the rank multiplier above.
- Optional "draw it out": 10-20 min after accepting, if the player is in Middle-earth and outside safe zones, the compat spawns that nemesis as an ambush itself (same adapter as invasions).
- Rosters are private, so other players cannot take a bounty on your nemesis except as your fellowship.

### Invasions (**later**, invasion spec)

- When an invasion of faction F starts within 64 blocks of an online player whose roster holds a RETREATED entry of an F type, 50% chance one such entry (highest level) leads it: spawned by our adapter (create, link, their buffs, our fix-ups), status ACTIVE, tracked; its bodyguards are the invasion wave.
- The invasion spec owns waves, triggers and banner checks.

### Hiring a defeated nemesis (**later**, needs maintainer sign-off)

- Only a player whose alignment with the nemesis's faction meets that faction's hire threshold (possible when retaliation made a friendly orc kill them).
- When such a nemesis drops to <= 15% health from that player's hit, it kneels for 10 s (no attack, no escape) and can be hired for its type's captain hire cost; it becomes a hired unit at unit level min(nemesis level, unit max level), the roster entry is set to DEAD and the owner re-synced ("X has sworn to serve you").
- Ordering against their escape roll is not under our control (see Edge cases); may need an upstream "suppress escape" flag.

### Achievements and titles (**later**, progression)

Progression listens to our two events. Suggested, names theirs to choose: first nemesis promoted against you; slay your own nemesis; slay a Warlord or Overlord; a nemesis cheats death against you; slay 5 nemeses (unlocks a title).

### Config (`lotrrevival-npc-server.toml`, section `nemesis`)

`enabled` (true), `eligible_factions`, `ambush_dimensions`, `respect_land_protection` (true), `fix_movement_speed` (true), `rank_alignment_multipliers` (2,3,4,6,8), promotion chance values above. Lists built with `Arrays.asList`.

## Dependency recommendation

**Optional (soft) dependency. No jar-in-jar.**

- `neoforge.mods.toml`: `modId="nemesis_system"`, `type="optional"`, `versionRange="[2.0.0,2.1)"`, `ordering="AFTER"`, `side="BOTH"`. NeoForge refuses to start if an out-of-range version is installed, which protects us because we call internal, unversioned classes.
- `build.gradle`: `compileOnly "curse.maven:nemesis-system-pal-1590589:8544598"` (cursemaven repo is already declared). Dev runs only with `-PwithNemesis`: add it plus Architectury and owo-lib to `localRuntime`, so default `runClient`, `runGameTestServer` and CI stay unchanged.
- Code isolation: every reference to `com.palgaminghd.*` lives in one adapter class under `npc/compat/nemesis/`, created only when `ModList.get().isLoaded("nemesis_system")`. The rest of our code talks to an interface with a no-op default. Any `LinkageError` from the adapter disables the compat with one error log instead of crashing.
- Why not required: it would force Architectury and a beta owo-lib on every player, it is a 4-month-old single-author mod (its mods.toml credits "Gemini CLI" as author) with no API and a speed bug, and its ambushes ignore dimensions. Why not jar-in-jar: same transitive deps would need bundling (Architectury is LGPL-3.0, owo-lib MIT), and the jar ships no LICENSE file or copyright line, which MIT redistribution requires us to reproduce. Listing it as an optional dependency redistributes nothing, so MIT imposes no obligations on us.
- Upstream requests (issue/Discord to palgaminghd; MIT also allows a fork if they go quiet): (1) cancellable events: promote (with killer + victim), ambush pre-spawn (with position), spawned (to replace gear/mount/bodyguards), defeated/cheated-death; (2) an entity tag `nemesis_system:promotable` / `nemesis_system:never_promote`; (3) name/title provider hook; (4) fix the movement-speed overwrite; (5) translatable strings; (6) clear static state on server stop; (7) include a LICENSE file. With (1) and (2) most fix-ups in this spec collapse into event listeners.

## Hook feasibility

| Integration | How | Verdict |
| --- | --- | --- |
| Evil-faction NPC eligibility | Our own promotion on `LivingDeathEvent` writing their army attachment through the adapter; NPCs stay non-`Enemy` | Compat layer (no mixin). Couples to their internal classes, so version-pinned. |
| Our names | Set first name and title when we create the entry | Compat layer |
| Ambush in Middle-earth only / banner protection | Refuse `EntityJoinLevelEvent` of linked NPCs | Compat layer; taunt still plays. Clean fix needs upstream event (1). |
| Faction gear, bodyguards, speed fix, mount | `EntityJoinLevelEvent` + next-tick fix-ups | Compat layer; bodyguard detection is a proximity heuristic. Clean fix needs upstream (1). |
| Rank-scaled alignment | Multiplier in `NpcEntity.alignmentKillValue()` via the compat interface | Compat layer; small additive edit to `NpcEntity` (npc pod). |
| Promoted/defeated events for other pods | Our own events, defeat read one tick later | Compat layer |
| Hunt quest | Progression quest kind on our events | Progression ticket; no mod access needed |
| Invasion captains | Adapter spawn using their public statics | Compat layer, after invasion spec |
| Hiring a defeated nemesis | Damage hook + hire flow + set entry DEAD | Compat layer, but escape ordering may need upstream flag or a mixin; **mixin not proposed**, needs lead approval if it comes to that |
| Achievements/titles | Progression listens to our events | Progression ticket |
| Making NPCs `Enemy` so the mod promotes them itself | Rejected: cannot exclude hired units (they would be discarded), names are theirs, and `Enemy` changes vanilla behavior (golems, snow golems, conduits target them) | Not recommended. Future orc tickets must not add `Enemy` to `NpcEntity` types while this compat exists (GameTest guards it). |

## Edge cases

- **Multiplayer:** rosters are per player; one NPC can be the nemesis of only one player (the link holds one owner). Their code already cancels damage a nemesis deals to non-target players it is not fighting; our targeting is unchanged otherwise. Alignment goes to whoever kills it. Ambush rolls are server-wide (one per 15+ min across all players); that is their design.
- **Death, logout, dimension change:** the army attachment survives player death. Owner logout: linked NPCs retreat within 5 s (orphan rule). NPCs never change dimension (our rule), so a nemesis cannot follow a portal.
- **Hired units:** never promoted; a hired unit that is somehow linked (should not happen) is ignored by every fix-up.
- **Name tag:** a player-named NPC is excluded from promotion; a nemesis cannot be renamed by name tag (their name is re-applied on spawn anyway).
- **Their reaper vs our refusal:** refused joins reset the entry ourselves; without that, the entry sits ACTIVE for 30 s and blocks politics for it.
- **Mod removed from a world:** their attachments are dropped by NeoForge on load (unknown attachment type); linked NPCs become ordinary NPCs; no crash. Our code never stores nemesis data of its own.
- **Mod version outside range:** game refuses to start with NeoForge's dependency error naming the range.
- **GameTest server:** has no Middle-earth level; tests pass the dimension list explicitly.
- **Listener order:** we never rely on firing before or after the Architectury handlers; defeat outcome is read one tick later, promotion does not race theirs because their check rejects non-`Enemy` killers.
- **Interaction with retaliation:** a friendly evil-faction NPC that kills a player through retaliation can promote; this is intended (and is the hiring hook).

## Data and APIs

- **No new registries, attachments, payloads or items of our own.** Nemesis state lives only in the mod's attachments.
- Reads/writes through the adapter: `nemesis_system:army` (player), `nemesis_system:link` (entity), `NemesisData`, `TraitHandler.assign/applyMilestone`, `NemesisBuffs.apply`, `NemesisNetworking.sync`, `NemesisTracker.track/untrack`.
- Our code: `npc/compat/nemesis/` with `NemesisCompat` (entry, no-op default), the adapter, fix-up event handlers, the two events, config section. Small additive edit to `NpcEntity.alignmentKillValue()`.
- New datapack name bank `data/lotrrevival/lotrrevival/npc_names/nemesis_titles.json` (npc datagen).
- Core APIs used: `Alignment` kill path (unchanged), `LandProtection.blocksFactionSpawn`, `FellowshipQuery`, `ModDimensions.MIDDLE_EARTH`.
- Shared files (Opus 1): `build.gradle`, `gradle.properties` (file id), `src/main/templates/META-INF/neoforge.mods.toml`.

## Out of scope

- Making the mod a required dependency or bundling it.
- Forking or patching the mod; mixins into it.
- Eldritch Mobs.
- Translating the mod's own messages and screen.
- Nemesis duels, intel screen (their roadmap).
- Promoting vanilla monsters differently in Middle-earth (they promote by the mod's own rules).

## Acceptance tests

- [ ] GameTest (no mod on classpath): compat reports inactive, `alignmentKillValue` unchanged, no `NpcEntity` type creates an instance of `Enemy`.
- [ ] GameTest (pure logic, no mod): promotion chance, starting level, rank multiplier and movement-speed formulas match the tables.
- [ ] GameTest (`-PwithNemesis` job): an eligible guard kills a mock player → an army entry with our bank name, linked owner, status RETREATED, NPC removed; a hired unit kill → no entry, unit alive.
- [ ] GameTest (`-PwithNemesis`): a linked NPC joining outside the allowed dimensions or in a protected area is refused and its entry is RETREATED next tick.
- [ ] GameTest (`-PwithNemesis`): killing a linked level-7 NPC applies x6 kill value; `NemesisDefeatedEvent` fires once with the right outcome.
- [ ] Playtest: with the three jars installed, die to a Mordor orc until it promotes, see it on H, get ambushed in Middle-earth with Mordor gear and normal speed, kill it, check alignment and chat.
- [ ] Playtest: without the mod, nothing nemesis-related appears; no H key binding.

## Tickets

Proposed (not opened until this spec is approved):

1. **Optional dependency wiring** (core-shared files, needs Opus 1): optional entry in `neoforge.mods.toml` with range `[2.0.0,2.1)`, `compileOnly` cursemaven coordinate, `-PwithNemesis` dev-runtime flag with Architectury + owo-lib, `nemesis_file_id` in `gradle.properties`. ~0.5 day.
2. **Compat skeleton**: `NemesisCompat` interface + no-op default, guarded adapter loader with `LinkageError` fallback, config section, two events, startup log, no-`Enemy` GameTest. ~1 day.
3. **Promotion of faction NPCs**: eligibility, chances, levels, roster cap, our names + `nemesis_titles` bank (datagen), removal like despawn, chat/particles, `NemesisPromotedEvent`. ~1.5 days. Blocked on G3 Mordor NPC types for real use; testable with a test-only eligible faction.
4. **Ambush fix-ups**: dimension and banner refusal with entry reset, finalisation, faction/captain gear, speed fix, bodyguard and mount handling, orphan retreat. ~2 days.
5. **Rank-scaled alignment + `NemesisDefeatedEvent`**: `alignmentKillValue` multiplier, next-tick defeat read. ~1 day.
6. **CI job `nemesis-compat`** running GameTests with `-PwithNemesis` (optional, non-blocking at first). ~0.5 day, needs Opus 1.
7. **(progression)** `nemesis_hunt` quest kind and achievements/title from our events. Progression lead to spec.
8. **(later, invasions)** Nemesis captain leading invasions via the adapter spawn. After the invasion spec.
9. **(later, sign-off)** Hiring a defeated nemesis.
10. **Upstream requests** filed with palgaminghd (events, tag, speed bug, LICENSE). Not code.
