# Quest givers, bounties, fellowships, banners and shields

- **Pod:** progression
- **Lead:** Opus 4
- **Status:** approved (maintainer pre-approved: match Legacy behavior, 2026-10-28)
- **Depends on:** `specs/progression/data-model.md` (quest data model, `Quests` API, `quest_giver` attachment, achievement count, title cache, menu), `specs/core/core-apis.md` (alignment read/write, pledge, faction relations, `FactionKillEvent`, `AlignmentCreditor`, coin API `core.coin.Coins`, registers), `specs/npc/npc-base.md` (`NpcEntity`, roles, `NpcInteractEvent`), `specs/npc/hiring-trading.md` (hired units, coins), `specs/world/waypoints-travel.md` (custom waypoints, protected-land check)
- **Sources:** `docs/reference/legacy/mini-quests.md`, `docs/reference/legacy/banners-fellowships-menu.md` (our own words about Legacy 36.15). All names, text, dialogue and art are our own.

## Summary

This spec turns the mini-quest data model into something players meet in the world, and adds the social and territorial systems that sit on top of alignment and achievements. Ordinary faction NPCs now and then hold a quest offer, marked over their head; players accept it, track it in a quest book and a HUD box, and hand it in for alignment and coins dropped at the giver's feet. Players who slaughter a faction's people become **bounty** targets that other players can hunt. **Fellowships** are player groups with roles, a private chat and protection from each other's blades. **Banners** placed on bronze, silver or gold blocks protect a cube of land for a faction or a whitelist, and keep hostile mobs, enemy NPCs and invasions out. **Shields** are cosmetic rewards for achievement milestones. Players care because these are the loops that make a server feel like Middle-earth: errands for the people you serve, a price on the heads of raiders, companies of friends and land they can hold.

## Player-facing behavior

### Quest offers and givers

1. An idle, adult, non-trader, non-hired faction NPC sometimes rolls an offer (about once every 17 minutes on average). A coloured marker floats over its head, in the quest group's colour, visible within 12 blocks to players who could accept it.
2. Right-clicking the NPC opens the **offer screen**: the NPC's name, an opening line, the objective ("Bring me 12 Bread") and **Accept** / **Decline**. While anyone has the screen open the offer does not age.
3. **Accept** gives the player the quest, tracks it in the HUD and removes the offer; the marker disappears for everyone. Another player who had the same offer open sees the screen close with "Someone else has taken this task." **Decline** just closes the screen; the offer stays.
4. If the player already has 5 live quests for the giver's faction (or 20 in total), the NPC says it would not want to burden them further, and no screen opens.
5. Right-clicking a giver the player already holds a quest from: collect quests take matching items (held slot first) and complete when the count is reached; kill and bounty quests complete if the objective is done; otherwise the NPC says a progress line ("I still need 7 more"). The quest becomes the tracked quest.
6. On completion the NPC says a thank-you line, the alignment gain shows through Core's alignment notice, and the coins drop at the giver's feet as the fewest coins. If the group has a first-completion achievement, it is earned (toast and broadcast through the normal award flow).
7. If a giver dies, every player holding a quest from it sees the quest fail ("the giver has died") with a one-time chat notice; the quest stays in the active list until removed.
8. Offers expire after 20 minutes. An NPC holding an offer that becomes busy (a fight starts, it gets hired) keeps the offer but cannot be accepted from until it is idle again.

### Quest book and HUD tracker

1. Every player receives a **quest book** the first time they enter Middle-earth; it can also be crafted (book + silver coin, shapeless). Using it opens the **quest log**.
2. The log has two tabs, **Active (n)** and **Completed (n)**. Selecting a quest shows a diary-style page: "Day N" (the in-game day it was given), the biome name where it was given, the giver's name and opening line, the objective, progress, failure reason (if failed), and for records the outcome and rewards (alignment, coins, items).
3. Buttons: **Track** / **Untrack**, **Abandon** (live quests, with a confirmation; free), **Remove** (failed quests), **Delete** (completed records, with a confirmation).
4. The **HUD tracker** shows the one tracked quest in a small box tinted with the group colour: an icon (the collect item, or a sword for kill and bounty), the short objective, short progress (`3/10`), "Return to <giver>" when ready, or the failure reason. A client option puts it on the left or right. It is hidden with the GUI (F1), with the debug screen open and while any screen is open.
5. The Middle-earth menu hub gains a **Quests** button that opens the log.

### Bounties

1. A player who kills 25 NPCs of one faction within 48 hours (inside that faction's lands) becomes a valid bounty target for it, unless a bounty on them was claimed for that faction in the last 12 hours.
2. NPCs of that faction may then offer a **bounty** quest naming the target. Accepting needs +100 alignment with the faction; the target cannot accept their own bounty, and a player may hold only one bounty per target. The rewards (alignment and coins) are fixed when the offer appears and shown on the offer screen.
3. When a bounty on a player is accepted, the target gets a yellow chat notice that the faction has put a price on their head (on their next tick, or on next login). The hunter is not named.
4. Talking to an NPC of a faction you hold a bounty for has a 1 in 3 chance that the NPC hints where the target was last seen (biome).
5. **Hunter kills target:** the hunter's quest is done (return to the giver to claim). The target loses alignment (see numbers), cannot be targeted by that faction for 12 hours, and every other hunter's bounty on them for that faction fails as "someone else claimed it". The target gets two chat lines; everyone else sees a server-wide announcement.
6. **Target kills hunter:** the hunter's bounty fails ("the target killed you"), the hunter loses alignment with the giver's faction, the target gains alignment and an achievement, and the server sees an announcement.
7. Completing bounties counts toward a bounty tally: the first gives an achievement; every 5th gives a bounty trophy item, and the 5th also an achievement.

### Fellowships

1. A player creates a fellowship from the Fellowships page of the menu (or `/fellowship create <name>`). They become its **owner**. They may lead (own) 1 + floor(achievements / 20) fellowships and join any number.
2. Owners and **admins** invite players by name. Invites wait (across logouts) until accepted or rejected. Accepting a disbanded fellowship's invite fails with a message; the inviter is told when an invite is accepted.
3. Owners and admins can remove members, set the icon from the held item, and toggle **Prevent PvP** and **Prevent hired-unit friendly fire**. Only the owner can toggle **Show map locations**, rename, promote/demote admins, transfer ownership and disband. Everyone except the owner can leave; the owner must transfer or disband first.
4. All three options start **on**. With Prevent PvP, members cannot damage each other (melee, projectiles, any source they own). With Prevent hired-unit friendly fire, members' hired units cannot be hurt by other members or their units, and cannot hurt other members. With Show map locations, members see each other on the map even if they hide themselves from it.
5. Online members get chat notices for joins, leaves, removals, renames, ownership transfers and disbanding.
6. `/fellowship chat <name> <message>` (alias `/fchat`) sends a message only to the members of that fellowship, in yellow with `[<name>]` as a prefix and the sender's title. Clicking the prefix pre-fills `/fchat <name> ` to reply.
7. The Fellowships page lists the player's fellowships and pending invites. A fellowship's page shows the icon, the owner, admins and members with their selected titles in their chosen colour, an online marker per member, the three options, and the actions the viewer's role allows.

### Banners

1. Banners come in faction designs (one per faction to start). A banner stands on top of a block (free-standing, three blocks tall) or hangs on a wall. Only a free-standing banner can protect land.
2. The block under a free-standing banner sets its protection: **bronze block 8**, **silver block 16**, **gold block 32**, anything else 0 (decorative). The protected area is a **cube** centred on the banner's block: radius r in x, y and z, so gold covers 65 x 65 x 65. An operator can set a custom range of 1 to 64 that overrides the base block.
3. Placing a protecting banner needs +1 alignment with the banner's faction (creative ignores this) and fails with "This land is already protected" if any part of the new cube overlaps land where an existing banner would deny the placer full access. The placer becomes the owner. The first protecting banner earns an achievement.
4. In **faction mode** (default) anyone with at least the threshold alignment (owner sets +1 to +10,000; default +1) with the banner's faction may act. In **whitelist mode** only listed players and listed fellowships may act; the list has 16 slots (slot 0 is always the owner); a fellowship entry admits all its current members. Each entry has its own permission set; a default permission set applies to everyone else in both modes.
5. Eight permissions: Full (everything, including placing and breaking), Doors, Tables, Containers, Personal containers, Food, Beds, Switches. Right-clicking a block needs the matching permission; sneaking with an item in hand needs Full.
6. Inside protected land a player without the right permission cannot break or place blocks, use items on blocks, empty or fill buckets or interact with the block. They get "This land is protected by <faction / owner / a stronghold>" in chat, at most once per second.
7. Hostile mobs never spawn naturally inside any protecting banner. NPCs of factions that are enemies of the banner's faction do not spawn inside it, invasions of enemy factions cannot start inside it, and enemy NPCs cannot break blocks inside it.
8. Right-clicking your own banner opens the edit screen: mode, threshold, whitelist entries and their permissions, default permissions, and **self-protection** (default on: nobody but the owner can break the banner). Explosions and other non-player damage never break a protecting banner.
9. A banner broken by its owner drops as an item that keeps its settings and owner, so it can be moved.
10. Structure banners (placed by world structures) protect against everyone, cannot be edited and drop without settings.

### Shields

1. The menu gains a **Shields** page. It shows the shields the player can wear, with arrows to browse, a preview, **Select** and **Remove**. Locked shields show what unlocks them ("Earn 25 achievements").
2. Four shields unlock at 25, 50, 100 and 200 achievements. They are cosmetic: no stats.
3. The selected shield is drawn on the player's back, on the arm when they hold a sword or tool, and raised in a blocking pose while blocking. It sits further out when a chestplate is worn. Other players see it; an invisible player's shield is drawn faintly to viewers who can see invisible players' outlines (spectators, teammates).

## Rules and numbers

### Quest offers

| Thing | Value | Notes |
| --- | --- | --- |
| Offer roll | 1 in round(20,000 / `offer_rate_multiplier`) per server tick per eligible giver | Eligible giver: see Giver eligibility |
| Offer lifetime | 24,000 ticks (20 min) | Pauses while any player has that giver's offer screen open |
| Offers per giver | 1 | Shared by all eligible players; first to accept takes it |
| Kind weights at roll | collect 10, kill 8 (kill_entity and kill_faction together), bounty 4 | Among the kinds the group has templates for; pickpocket and scripted never roll here |
| Template pick | uniform within the chosen kind | |
| Retries | up to 8 per roll | A failed generation (e.g. bounty with no valid target) retries; after 8 nothing until the next roll |
| Marker range | 12 blocks | Client-side cut-off |
| Interaction reach | 8 blocks | Server rejects accept from further away |
| Caps | 5 live per faction, 20 live in total, 1 per giver per player | Already enforced by `Quests.canAccept` |
| Giver position refresh | every 100 ticks; update holders when moved > 16 blocks | |
| Coin drop point | 0.5 blocks above the giver's feet, as fewest coins | `Coins.drop`; if the giver is not loaded in the player's level within 16 blocks, `Coins.give` to the player |
| Quest book recipe | shapeless: book + silver coin | |
| HUD tracker size | 140 x 32 px box, 4 px from the screen edge, 30 px from the top | Client option side `left` / `right` (existing `progression.questTrackerSide`) |

### Giver eligibility (checked by the roll, the marker and accept)

- The entity is an `NpcEntity` in entity tag `#lotrrevival:quest_givers` (Progression tag; datagen fills it with `#lotrrevival:npcs`).
- Not a baby, role is not a trader (`getRole().isTrader()` false), not hired, has no attack target, and is alive.
- A quest group matches the NPC: its `faction` is the NPC's faction and, if the group lists `givers` (new optional entity HolderSet), the NPC's type is in it. Groups with `givers` win over groups without; ties go to the group with the lowest id. No match, no offers.
- Mini-quests enabled in config.

### Accept rules (per player)

- `Quests.canAccept(...)` is `OK` (caps, hostility, group `min_alignment`).
- The offer has no `for_player`, or it is this player.
- Player within 8 blocks of the giver, giver eligible (idle) now.
- Bounty offers add the bounty rules below.

### Rewards

Unchanged from the data model (alignment base x uniform(0.75, 1.25), min 1; coins base x uniform(0.75, 1.25), 1 in 12 times x uniform-int(2, 5), min 1), except bounties, whose alignment and coins are paid exactly as fixed at offer time.

### Bounties

| Thing | Value | Notes |
| --- | --- | --- |
| Kill record | one per kill of a faction NPC by a player not in creative or spectator | Direct kills only; hired-unit kills do not count. Only inside the faction's area of influence: Core's influence multiplier for the faction at the kill > 0 (always true until the areas-of-influence spec lands) |
| Record lifetime | 3,456,000 ticks (48 h of overworld game time) | |
| Records kept | newest 128 per (faction, player) | Older ones are dropped; nothing needs more than 100 |
| Valid target | >= 25 live records with the faction, and last claimed for that faction >= 864,000 ticks (12 h) ago | Online or offline |
| Bounty value v | clamp(live records, 1, 50) | Alignment base, fixed at offer |
| Bounty coins | clamp(round(records x 10 x uniform(0.75, 1.25)), 1, 1000) | Fixed at offer, no completion variance |
| Accept needs | alignment >= +100 with the giver's faction; not the target; no other live bounty on the same target | On top of the normal accept rules |
| Target pick | uniform among valid targets for the giver's faction | |
| Hint chance | 1 in 3 per right-click on an NPC of a faction the player holds a live bounty for, when no other interaction applies | Says the target's last known biome |
| Last known biome | updated every 1,200 ticks while online and on logout | |
| Hunter kills target: target loss | 2v from the target's pledged faction; if not pledged, from their highest faction above +100; never below +100 (a value at or below +100 is untouched) | Cause `bounty` |
| Target kills hunter: target gain | v with the target's pledged faction, or else their highest faction with alignment > 0; none if no positive faction | |
| Target kills hunter: hunter loss | 2v from the giver's faction if above +100, floored at +100 | |
| Bounty trophy | every 5th completed bounty (5, 10, 15...) | Item `lotrrevival:bounty_trophy` |
| Achievements | first bounty completed; 5th bounty completed; a target killing their hunter | `general/first_bounty`, `general/bounty_hunter`, `combat/turned_the_hunt` |

### Fellowships

| Thing | Value | Notes |
| --- | --- | --- |
| Max size | config `progression.fellowshipMaxSize`, default -1 (no limit), else 2 to 1,000 | Counts owner + admins + members. Invites past the limit are refused; acceptance re-checks |
| Fellowships a player may lead | 1 + floor(achievementCount / 20) | Checked on create and on receiving ownership. Losing achievements later keeps what you already own |
| Fellowships a player may join | no limit | |
| Name | 1 to 24 characters after trimming; letters, digits, spaces, `'`, `-`, `_` | Unique (case-insensitive) among the fellowships of every member; create, rename and accept refuse a clash |
| Icon | any item, count 1, components kept | Set from the held item; empty hand clears it |
| Creation | config `progression.fellowshipCreationEnabled`, default true | Ops can always create by command |
| Options default | Prevent PvP on, Prevent hired-unit friendly fire on, Show map locations on | |
| Invites | kept until accepted, rejected or the fellowship disbands | Max 50 pending per fellowship |
| Member position sync | every 40 ticks, only while the viewer reports the map open | Co-members in the same dimension in fellowships with Show map locations on |
| Chat prefix colour | yellow, prefix clickable (`suggest_command`) | |

Role permissions:

| Action | Owner | Admin | Member |
| --- | --- | --- | --- |
| Invite, remove members (not the owner, admins remove members only) | yes | yes | no |
| Set icon, toggle PvP and hired-unit options | yes | yes | no |
| Toggle Show map locations, rename, promote/demote, transfer ownership, disband | yes | no | no |
| Leave | no | yes | yes |
| Chat, view | yes | yes | yes |

### Banners

| Thing | Value | Notes |
| --- | --- | --- |
| Range by base block | bronze 8, silver 16, gold 32, other 0 | Block tags `#lotrrevival:banner_base/bronze`, `/silver`, `/gold` |
| Custom range (op) | 1 to 64, or reset | Overrides the base block (still 0 if not free-standing) |
| Max range | 64 | Also the search distance for protection queries |
| Shape | cube: \|dx\| <= r, \|dy\| <= r, \|dz\| <= r from the banner block | |
| Place alignment | >= +1 with the design's faction | Survival and adventure only |
| Faction threshold | integer +1 to +10,000, default +1 | |
| Whitelist slots | 16 (op command resizes 1 to 4,000) | Slot 0 = owner, cannot be removed |
| Permission set per entry | subset of the 8 permissions | New entries get Full |
| Default permissions | empty | Apply to everyone not otherwise allowed, in both modes |
| Warning cooldown | config `progression.bannerWarningCooldownTicks`, default 20 | Per player |
| Banner height | 3 blocks (the block itself plus two above, which must be air or replaceable at placement) | Collision is the bottom block only |
| Config | `progression.bannerProtectionEnabled` (true), `progression.bannerSelfProtectionEnabled` (true), `progression.bannerExplosionProtection` (true) | |

Permission classification for right-click (block tags, datagen, first match wins in this order):

| Permission | Tag | Initial members |
| --- | --- | --- |
| Personal containers | `#lotrrevival:banner_permission/personal_containers` | ender chest |
| Doors | `.../doors` | `#minecraft:doors`, `#minecraft:trapdoors`, `#minecraft:fence_gates` |
| Beds | `.../beds` | `#minecraft:beds` |
| Switches | `.../switches` | `#minecraft:buttons`, lever, `#minecraft:pressure_plates` (stepping is not blocked; only right-click) |
| Food | `.../food` | cakes and candle cakes, our barrels and kegs when Core adds them (`lotrrevival:brewing_barrel` now) |
| Tables | `.../tables` | crafting table, `#lotrrevival:crafting_tables`, anvils, smithing, grindstone, stonecutter, loom, cartography and fletching tables, enchanting table |
| Containers | `.../containers`, or any block whose block entity is a container or menu provider | chests, barrels, shulker boxes, furnaces, hoppers, droppers, dispensers, brewing stands, lecterns, jukeboxes, chiseled bookshelves, decorated pots |
| Full | anything else, and sneaking with an item in hand | |

Entity interactions inside protected land: item frames, armour stands, paintings and leash knots need Full to break or change, Containers to take from (armour stand, item frame item). Riding and trading with NPCs are not restricted.

What protection blocks:

| Actor | Blocked when |
| --- | --- |
| Player breaking or placing blocks, using items on blocks, buckets | lacks Full (creative bypasses) |
| Player right-clicking a block | lacks the matching permission (creative bypasses) |
| Natural spawns of `MobCategory.MONSTER` mobs | inside any protecting banner |
| Faction NPC spawns (wild spawner) | inside a banner whose faction's relation with the NPC's faction is enemy or mortal enemy |
| Invasions, conquest horns | the start point is inside a banner whose faction is an enemy of the invaders |
| NPC block breaking | inside a banner whose faction is an enemy of the NPC's faction |
| Explosions | blocks inside protected land are removed from the explosion only if the responsible player (igniter, thrower, shooter) has Full there; explosions with no responsible player remove nothing inside protected land (config `bannerExplosionProtection`) |
| Custom waypoints (World) | cannot be created inside land where the player lacks Full |

Who is "allowed" at a position: the player is allowed an action if, for **every** protecting banner whose cube contains the position, that banner allows it. A banner allows an action if: the player is its owner; or (faction mode) the player's alignment with the banner's faction >= threshold; or (whitelist mode) the player matches an entry (by UUID, or as a current member of a listed fellowship) whose set contains Full or the action's permission; or the default set contains Full or the permission. Structure banners allow nobody (except creative). Whitelist mode with no owner falls back to faction mode.

### Shields

| Thing | Value | Notes |
| --- | --- | --- |
| Achievement shields | 4, at 25, 50, 100, 200 counted achievements | Our own designs and names |
| Validity | checked when selecting only | A shield stays selected if the count later drops, as in Legacy |
| Sync | the selected shield id to every client tracking the player, plus self | On change, on start tracking, on login and respawn |
| Back offset | 1 px further out with a chestplate | |
| Arm position | when the main hand holds a sword, axe, pickaxe, shovel, hoe or `#lotrrevival:weapons/*` | Otherwise on the back |

## Edge cases

- **Multiplayer:**
  - Two players accept the same offer in the same tick: the server handles payloads in order; the second gets "taken". Several players may each hold a quest from the same giver (each needs a fresh offer).
  - An offer screen open on a client while the giver dies, is hired, starts fighting or moves more than 8 blocks away closes with a line.
  - The offer timer pauses while at least one viewer is registered; viewers are removed on screen close, logout, death, dimension change and when they move more than 16 blocks from the giver.
  - A bounty target can be online or offline. Hunters of offline targets just wait; there is no expiry.
  - Fellowship protection applies only when both sides are fellowship members of the same fellowship with the option on. A member in two fellowships with conflicting options is protected if any shared fellowship has the option on.
  - Banner overlap: protection is the intersection of all covering banners' rules; the "already protected" check on placement keeps players from planting banners over others' land.
- **Death, logout, dimension change:**
  - Quests, offers and the quest book are unchanged by death (attachments copy). A quest book in the inventory drops like any item; the player can craft another.
  - A giver in an unloaded chunk keeps its offer frozen; it ages only while loaded.
  - Bounty notices for offline targets queue in `SavedData` and show on login. Claimed bounties for offline hunters fail on login (claimed list kept 7 in-game days).
  - Fellowship invites and membership survive logout and restart; notices are online only.
  - Banner protection does not depend on the banner's chunk being loaded: the per-dimension index answers queries.
  - Shields re-sync on respawn and dimension change.
- **Interaction with other systems:**
  - Quest givers become persistent (`setPersistenceRequired`) when a player accepts a quest from them, so they do not despawn.
  - The quest interaction listens to `NpcInteractEvent` kind `TALK` only and cancels it when it took over; traders and unit traders keep their trade and hire flow (sneak-right-click a trader still talks and can reach quests, but traders never hold offers).
  - Bounty alignment changes go through `Alignment.add` with cause `bounty`; they may cascade to allies like other alignment changes.
  - Fellowship PvP and unit protection only cancel damage; they never change alignment or retaliation state. Hired units of a member never take a co-member as a target (NPC pod, through `core.api.FellowshipQuery`).
  - Banners never block NPC trading, hiring, quest interaction or fast travel arrival.
  - Creative players bypass banner block rules and the place-alignment rule; ops in survival do not.
  - A datapack that removes a banner design leaves placed banners decorative (range 0) until the design returns.

## Data and APIs

### Packages (one owner ticket each, so work runs in parallel)

| Package | Content |
| --- | --- |
| `progression.quest.offer` | offer roll, giver eligibility, offer viewers, interaction chain, giver death and position, coin payout, first-completion award, offer payloads |
| `client.progression.quest.offer` | offer screen, offer markers |
| `client.progression.quest.log` | quest log screen, HUD tracker |
| `progression.bounty` | kill records, targets, resolution, notices, hints, bounty offers, trophy item |
| `progression.fellowship`, `client.progression.fellowship` | fellowship data, service, commands, damage rules, chat, sync; Fellowships page |
| `progression.banner`, `client.progression.banner` | banner blocks, items, block entity, designs, index, protection, events; banner renderer and edit screen |
| `progression.shield`, `client.progression.shield` | shield registry, cosmetics attachment, selection, sync; Shields page and render layer |

### Registries and data

- **Quest group** gains optional `givers` (entity type HolderSet). Backward compatible.
- **Quest giver attachment** `QuestGiverData.Offer` gains optional `bounty_target` (UUID) and `bounty_target_name` (string). Backward compatible.
- Entity tag `#lotrrevival:quest_givers` (Progression), value `#lotrrevival:npcs`.
- **Pilot quest group** `lotrrevival:hobbits` (faction Hobbits, colour 0x6BA539, first-completion achievement `shire/first_hobbit_errand`): collect bread 4 to 15 (factor 1), pumpkin pie 1 to 3 (factor 3), wheat 10 to 40 (factor 0.25), oak logs 10 to 40 (factor 0.25); kill_entity spiders 10 to 20 (factor 0.5). The full catalogue is a later content spec.
- **Bounty template** per pilot group: `lotrrevival:hobbits/bounty` with objective `bounty` (no fields). Bounty quests are only offered by groups that have one.
- Datapack registry `lotrrevival:banner_design` (synced): `faction`, `color` (0xRRGGBB cloth), `emblem_color`, `texture` (optional resource location, default generic cloth). Datagen: one design per faction in Core's faction list, id `lotrrevival:<faction path>`.
- Datapack registry `lotrrevival:shield` (synced): `unlock` (type-dispatched: `achievement_count {min}`; reserved `alignment {faction, min}`, `achievement {id}`, `granted`), `texture`, `hidden_until_unlocked` (bool, default false), `sort` (int). Datagen: the four achievement shields.

### Items and blocks (registered through `ModRegistries`, from our packages)

- `lotrrevival:faction_banner` block (standing) and `lotrrevival:faction_wall_banner` block (wall, decorative), one block entity type `lotrrevival:faction_banner`, one item `lotrrevival:faction_banner` (places the standing or wall form by clicked face). Creative tab lists one stack per design.
- Data components: `lotrrevival:banner_design` (design id), `lotrrevival:banner_protection` (the settings record, carried when an owner breaks a non-structure banner).
- `lotrrevival:bounty_trophy` item (stack 64, cosmetic).
- `QuestItems.QUEST_BOOK` gains its use behavior and recipe.
- Banner recipes are deferred to the faction crafting-table spec; until then banners come from the creative tab and `/give`.

### Attachments and saved data

| Name | Kind | Content |
| --- | --- | --- |
| `lotrrevival:cosmetics` | player attachment, saved, copied on death | `shield` (optional id) |
| `progress_counters` | existing | gains `quest_book_given` (bool) |
| `lotrrevival_faction_bounties` | `SavedData` (overworld) | per faction, per player: kill times (newest 128), last claimed time; pending target notices; claimed list (target, faction, time) for offline hunters; last known biome per player |
| `lotrrevival_fellowships` | `SavedData` (overworld) | fellowships by UUID: name, owner, admins, members, icon, three options, pending invites, created time |
| `lotrrevival_banners` | `SavedData` per dimension | index of protecting banners: position, effective range, design faction, a copy of the protection settings; updated on place, break, edit and base-block change |

Banner block entity holds the authoritative settings: owner (UUID, name), mode, threshold, whitelist entries (player UUID + name, or fellowship UUID + cached name, plus a permission set), default permissions, self-protection, custom range, structure flag.

### Payloads (`lotrrevival:progression/...`, registered in `ProgressionPayloads`)

| Name | Direction | Content |
| --- | --- | --- |
| `quest_offer_marker` | S2C | entity id, group id or none, `for_player` or none |
| `open_quest_offer` | S2C | entity id, template id, count, alignment and coin base (bounties: target name), opening line key |
| `quest_offer_closed` | S2C | entity id, reason (`taken`, `gone`, `busy`, `too_far`) |
| `offer_screen` | C2S | entity id, open or closed |
| `accept_quest` / `decline_quest` | C2S | entity id, template id, count (must match the current offer) |
| `abandon_quest`, `remove_quest`, `track_quest` | C2S | quest uuid (track: optional) |
| `delete_quest_record` | C2S | index and record time (both must match) |
| `fellowships_full` / `fellowships_delta` | S2C | the viewer's fellowships (members with name, role, online, title id and colour), pending invites |
| `fellowship_action` | C2S | action enum + fellowship UUID + argument (name, player name, option, value) |
| `fellowship_positions` | S2C | list of (player UUID, name, dimension, x, z) |
| `map_viewing` | C2S | bool |
| `open_banner_editor` | S2C | banner pos, full settings, design, whitelist size, whether the viewer is op |
| `update_banner` | C2S | banner pos, new settings (validated: owner or creative op, ranges, slot count, fellowship entries must be the owner's) |
| `select_shield` | C2S | shield id or none |
| `shield_sync` | S2C | player id, shield id or none |

All C2S payloads are validated server-side (distance, ownership, ranges); invalid ones are dropped and logged at debug.

### Events and hooks

- Quest interaction: `@SubscribeEvent` on `NpcInteractEvent` (kind `TALK`). Order inside: (1) reserved pickpocket; (2) own quest from this giver; (3) offer; (4) bounty hint (1 in 3). Cancel when one applies.
- Giver death: `LivingDeathEvent` for an `NpcEntity` whose `quest_giver` holders are not empty: `Quests.failQuestsFromGiver(uuid)`; also clears its offer and marker.
- Coin payout: `Quests.setCoinPayout(...)` registered at common setup.
- First completion: listener on `QuestCompletedEvent` awards all criteria of the group's `first_completion_achievement` advancement if present and not done (warn once in the log if the advancement is missing).
- New `Quests` methods (same pod): `start(..., ObjectiveData)` overload, `markObjectiveDone(player, questId)`, `updateGiverPos(player, giver, pos)`, `incrementBountiesCompleted(player)`; `complete` pays bounty rewards without variance.
- Fellowship damage rules: `LivingIncomingDamageEvent` (HIGH). Resolve each side to an owner UUID: a player is themselves; an entity with `AlignmentCreditor.creditedPlayer()` is its owner; a projectile resolves through its owner. Different owners sharing a fellowship: two players use Prevent PvP; if either side is a unit use Prevent hired-unit friendly fire.
- Fellowship chat: system messages to online members; also logged to the server console.
- Banners: `BlockEvent.BreakEvent`, `BlockEvent.EntityPlaceEvent`, `PlayerInteractEvent.RightClickBlock`, `FillBucketEvent` (or the bucket use path), `AttackEntityEvent` and `PlayerInteractEvent.EntityInteract` for frames and stands, `ExplosionEvent.Detonate`, `MobSpawnEvent.PositionCheck` (natural `MONSTER` spawns).
- Shield render: a player render layer added through `EntityRenderersEvent.AddLayers` for both player skins.

### Requested from Core (`pod:core` issue)

1. Bronze and silver storage blocks (with the ingots if they do not exist), for banner bases. Progression puts them in `#lotrrevival:banner_base/bronze` and `/silver`; until they land only gold protects.
2. `core.api.LandProtection` facade (Progression registers the one implementation, like `AchievementCounters`): `canEdit(Level, BlockPos, Player)`, `blocksFactionSpawn(Level, BlockPos, ResourceKey<Faction>)`, `blocksInvasion(Level, BlockPos, ResourceKey<Faction>)`, `blocksNpcGriefing(Level, BlockPos, ResourceKey<Faction>)`. Defaults: allow everything. Used by World (custom waypoints), NPC (spawner, future invasions and block-breaking AI).
3. `core.api.FellowshipQuery` facade: `protectsUnits(UUID a, UUID b)` and `preventsPvp(UUID a, UUID b)`; defaults false. Used by NPC (hired-unit targeting).
4. A read accessor for the influence multiplier, `Alignment.influence(ServerPlayer, ResourceKey<Faction>)`, so bounty kill records can respect areas of influence later.
5. Alignment source cause `bounty` (or confirm `AlignmentSource` accepts a custom cause).

### Requested from NPC (`pod:npc` issue)

1. Wild NPC spawner skips positions where `LandProtection.blocksFactionSpawn` is true.
2. Hired units do not pick a target the owner is fellowship-protected from (`FellowshipQuery.protectsUnits(owner, target owner)`), and the retaliation logic ignores hits that were cancelled.
3. Future invasion and block-breaking AI consult `LandProtection` (note in those specs).

### Requested from World (no ticket yet; noted for Opus 2)

Custom waypoint creation calls `LandProtection.canEdit` (the hook named in `specs/world/waypoints-travel.md`); the map screen draws fellowship members from `fellowship_positions` when it exists.

### Commands

- `/fellowship create|invite|accept|reject|leave|remove|promote|demote|transfer|rename|disband|icon|option|chat|list|info`, alias `/fchat <name> <message>`.
- `/lotrfellowship` (permission 2): `list`, `info <name> <owner>`, `create <owner> <name>`, `disband <id>`.
- `/lotrbanner` (permission 2): `range <pos> <1..64|reset>`, `whitelistsize <pos> <1..4000>`, `info <pos>`.
- `/lotrbounty` (permission 2): `list [faction]`, `clear <player> [faction]`, `addkills <player> <faction> <n>` (testing).
- `/lotrquest offer <entity>` (permission 2): force an offer roll on a giver (testing).

## Out of scope

- The full quest catalogue (about 38 groups, ~1,000 templates), dialogue variety, lore books, modifier templates, pouches, hire-the-giver quests, pickpocketing and the scripted tutorial quest (later `mini-quests` content spec).
- Bounty hints by waypoint region (needs World's region lookup); hints are biome-only for now.
- The map screen itself, drawing fellowship members on it, and shared custom waypoints (map and waypoint tickets, World).
- Banner recipes (faction crafting-table spec), banner bearers' buffs (NPC combat spec), structure banners placement (World), fire-spread protection for special fire blocks.
- Alignment-gated and exclusive shields (38 + 3 + 19 in Legacy); the registry shape allows them. Capes (not a player cosmetic in Legacy).
- Invasions themselves.

## Acceptance tests

- [ ] GameTest: with a seeded RNG, an eligible giver rolls an offer at the configured rate (100,000 ticks, multiplier 1: about 5 offers); an ineligible giver (hired, baby, trader, with a target) never rolls.
- [ ] GameTest: an offer with `ticks_left` 2 expires after 2 ticks, but not while a viewer is registered.
- [ ] GameTest: accept via the interaction path gives the quest, removes the offer, makes the giver persistent and refuses a second player with `taken`.
- [ ] GameTest: right-click with 5 live quests for the faction says the "too many" line and opens nothing.
- [ ] GameTest: completing a collect quest drops the rolled coins as coin item entities at the giver; the group's first-completion advancement is done after the first completion and the award fires once.
- [ ] GameTest: killing the giver fails the holder's quest with `giver_died`.
- [ ] GameTest: 25 kills inside 48 h make a valid target; 24 do not; records older than 3,456,000 ticks drop out; a claim gives 864,000 ticks of immunity.
- [ ] GameTest: hunter kills target: target at +300 pledged with v = 30 drops to +240; at +130 drops to +100; at +90 is untouched; other hunters' bounties fail `claimed`.
- [ ] GameTest: target kills hunter: hunter at +150 with v = 40 drops to +100; target gains 40 with their pledge.
- [ ] GameTest: bounty completion pays exactly the fixed coins; the 5th completion adds a trophy.
- [ ] GameTest: fellowship with Prevent PvP: member-to-member arrow damage is cancelled; toggled off it lands. A member's hired unit takes no damage from another member.
- [ ] GameTest: lead limit: a player with 19 achievements cannot create a second fellowship; with 20 they can.
- [ ] GameTest: a fellowship at max size refuses an invite acceptance.
- [ ] GameTest: banner on a gold block protects (32, 32, 32) offset and not (33, 0, 0); a non-member cannot break a block inside; with +1 alignment in faction mode they can; whitelist mode with a fellowship entry admits its members only.
- [ ] GameTest: a second banner whose cube overlaps a denied area cannot be placed; overlapping your own land can.
- [ ] GameTest: natural zombie spawn inside a protecting banner is denied; outside it is allowed.
- [ ] GameTest: Doors permission lets a non-member open a door but not a chest; sneaking with a block in hand is refused.
- [ ] GameTest: selecting a 25-achievement shield with 24 achievements is rejected; with 25 it is accepted and synced.
- [ ] Playtest: on a dedicated server, two players see the same offer marker; one accepts, the other's screen closes; the quest shows in the HUD and the book; hand-in drops coins at the giver.
- [ ] Playtest: create a fellowship, invite, chat with `/fchat`, check titles and online markers on the page, PvP blocked.
- [ ] Playtest: place a gold-base banner, edit it to whitelist mode, add the fellowship, confirm a non-member gets the warning and members can build; confirm no hostile spawns at night inside.
- [ ] Playtest: shield shows on the back, moves to the arm with a sword, faint while invisible.

## Tickets

In dependency order. Milestone G3, phase 3. Six tickets can start at once; each owns its own package.

| # | Title | Agent | Blocked by |
| --- | --- | --- | --- |
| #105 | core: bronze and silver blocks, LandProtection and FellowshipQuery facades, influence accessor | opus-1 (pod:core) | none |
| #111 | progression: quest offers, giver interaction, giver death, coin payout and first-completion award | sonnet-9 | none |
| #113 | progression: quest book screen, HUD tracker and quest log actions | sonnet-10 | none |
| #115 | progression: bounty kill records, targets, resolution and trophies | sonnet-10 | none |
| #117 | progression: fellowships data, roles, invites, commands, damage rules and chat | sonnet-9 | none |
| #119 | progression: banner blocks, designs, protection rules and enforcement | sonnet-10 | none |
| #122 | progression: achievement shields (registry, selection, sync, page, render) | sonnet-9 | none |
| #129 | progression: quest offer screen and offer markers | sonnet-10 | #111 |
| #130 | progression: bounty offers, accept rules, fixed rewards and hints | sonnet-9 | #111, #115 |
| #131 | progression: Fellowships page in the Middle-earth menu | sonnet-9 | #117 |
| #132 | progression: banner edit screen, whitelist and permissions | sonnet-10 | #119, #117 |
| #133 | progression: LandProtection and FellowshipQuery hooks, bronze/silver banner bases | sonnet-10 | #105, #119, #117 |
| #106 | npc: respect banner spawn protection and fellowship unit protection | NPC pod (pod:npc) | #105 |

Pod-internal shared files: `ProgressionPayloads` (#111, #113, #117, #122, #132), `Quests.java` (#111, #115, #130), `ProgressionConfig` (#117, #119), `MenuPage`/`MenuHubScreen` (#113, #122, #131). Merge in issue order and rebase. `DataGenerators` is touched once, by #119, which adds a single Progression entry point.
