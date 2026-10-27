# Legacy reference: banners, fellowships, shields, capes and the Middle-earth menu

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: banner land protection, fellowships, cosmetic shields and capes, and the Middle-earth menu and map screens with their key bindings. Fast-travel numbers are included because the map screen shows them, although waypoints themselves belong to the World pod.

Caveat: this decompile appears to come from a community-maintained build. One comment in it says banner protection against explosions was disabled for performance. The notes below describe the intended design; the explosion row is marked.

## 1. Banners

### 1.1 Banner items

- 42 banner designs. Each belongs to a faction (several factions have more than one: Gondor has about a dozen fief banners, Mordor four, Near Harad four).
- A banner can be placed on top of a block (free-standing, 1 x 3 blocks tall) or on a wall. Only free-standing banners protect land.
- A free-standing banner breaks and drops if the block under it stops being solid.

### 1.2 Protection range

The block directly under a free-standing banner decides whether it protects and how far:

| Block under banner | Protection radius |
| --- | --- |
| Bronze block | 8 |
| Silver block | 16 |
| Gold block | 32 |
| Anything else | 0 (decorative only) |

- The protected area is a **cube** centred on the banner's base block: radius in all three axes, including up and down. Radius 32 gives a 65 x 65 x 65 box.
- An operator-set custom range (1 to 64) can override the block-based range. 64 is also the maximum search distance used when checking protection.
- Removing the base block removes protection (and drops the banner, see 1.1).
- A server config turns all banner protection off.

### 1.3 Placing a protecting banner

- Requires alignment of at least +1 with the banner's faction (survival only; creative ignores this).
- The new banner's whole protection cube must not overlap any spot where an existing banner would deny the player full permission. Otherwise the player is told the land is already protected and placement fails.
- The placing player becomes the banner's owner (whitelist slot 0). A banner item picked back up keeps its protection settings and owner, so it can be moved.
- Placing a protecting banner awards an achievement.

### 1.4 Protection modes

Each protecting banner is in one of two modes, chosen by the owner:

| Mode | Who may act inside |
| --- | --- |
| Faction (default) | Any player whose alignment with the banner's faction is at least the banner's threshold. Threshold is set by the owner, from +1 (default) to +10,000. |
| Whitelist | Only listed players and listed fellowships. The list starts with 16 slots (admins can resize from 1 to 4,000). Slot 0 is always the owner. A fellowship entry (typed with a prefix symbol plus the name of one of the owner's fellowships) admits all its current members. |

If a whitelist banner somehow has no owner, it falls back to faction mode.

### 1.5 Permissions

Permissions are fine-grained. In whitelist mode each entry has its own set; there is also a "default permissions" set that applies to everyone (both modes).

| Permission | Covers |
| --- | --- |
| Full | Everything, including placing and breaking blocks |
| Doors | Doors, trapdoors, fence gates, portcullis gates |
| Tables | Crafting tables, faction tables, anvils, command tables |
| Containers | Chests and other inventories, armour stands, weapon racks, bookshelves, jukeboxes |
| Personal containers | Ender chests |
| Food | Plates, cakes, placed food, mugs, barrels and food stands |
| Beds | Beds |
| Switches | Buttons and levers |

- Right-clicking a block picks the matching permission. Sneaking while holding an item always needs Full (the player might place something).
- Old banners without saved permissions grant Full to each whitelisted entry.

### 1.6 What protection blocks

| Actor | Blocked when |
| --- | --- |
| Player breaking/placing blocks, using items on blocks | Player lacks Full permission (creative players and server ops in creative bypass) |
| Player interacting with blocks | Player lacks the matching permission |
| Hostile mob natural spawns | Inside any protecting banner's area |
| Faction NPC natural spawns | Inside a banner of an enemy faction ("bad relation") |
| Invasions | An invasion cannot start inside the area of a banner whose faction is an enemy of the invaders; a conquest horn is refused there |
| NPC block-breaking abilities | Blocked inside banners of enemy factions |
| Thrown projectiles, TNT, TNT carts | Checked against the thrower/placer as above; unknown source counts as protected (marked as disabled in this build) |
| Fire spread from one special fire block | Blocked inside any banner |
| Custom waypoints | Cannot be created inside land the player is not permitted in |

- **Warning message:** "this land is protected by X" in chat, where X is the faction name, the owner name (whitelist mode) or "a structure". Rate-limited per player by a config cooldown (default 20 ticks).
- **Structure banners:** banners spawned by world structures protect against everyone, cannot be edited and do not carry settings when picked up.

### 1.7 Banner self-protection and editing

- Only the owner (or an op in creative, for non-structure banners) can open the edit screen by right-clicking the banner.
- Edit screen: mode toggle (faction/whitelist), alignment threshold, whitelist name fields with per-entry permission toggles, default permissions, and a self-protection toggle.
- **Self-protection on (default):** the banner cannot be broken by anyone who is not the owner. A server config can disable self-protection everywhere.
- Non-player damage never breaks a protecting banner.

## 2. Fellowships

### 2.1 Basics

| Fact | Value |
| --- | --- |
| Size | Unlimited by default; server config sets a max (negative = no limit). Invites past the limit are rejected with a message. |
| Fellowships a player may lead | 1 + floor(Middle-earth achievements / 20) |
| Fellowships a player may join | No limit |
| Name | Must be unique among the fellowships the player belongs to |
| Icon | Any item, set by owner or admin from the held item |
| Creation | Through the menu; a server config can disable player creation (admins can still create by command) |

### 2.2 Roles

| Action | Owner | Admin | Member |
| --- | --- | --- | --- |
| Invite players | yes | yes | no |
| Remove members | yes | yes | no |
| Set icon | yes | yes | no |
| Toggle PvP prevention | yes | yes | no |
| Toggle hired-unit friendly fire | yes | yes | no |
| Toggle map-location sharing | yes | no | no |
| Rename | yes | no | no |
| Promote/demote admins | yes | no | no |
| Transfer ownership | yes | no | no |
| Disband | yes | no | no |
| Leave | no (must transfer or disband) | yes | yes |

- Invites are stored until accepted or rejected, and survive logout. Accepting a disbanded fellowship's invite fails with a message. The inviter gets a notice on acceptance.
- Members receive notifications for joins, leaves, removals, renames, ownership transfers and disbanding.

### 2.3 Fellowship options (all default on)

- **Prevent PvP:** members cannot damage each other.
- **Prevent hired-unit friendly fire:** members' hired units cannot be hurt by other members (and vice versa).
- **Show map locations:** members see each other on the map even if a member has chosen to hide from the map.

### 2.4 Shared features

- **Chat:** a command sends a message only to members of a named fellowship, shown in yellow with the fellowship name as a prefix. Clicking the prefix pre-fills the command to reply. The message still passes the server's normal chat event.
- **Custom waypoints:** a player can share any of their custom waypoints with one or more of their fellowships. Other members can fast travel to it after they have personally visited the nearby area. Members can hide shared waypoints from their own map. If the sharer leaves the fellowship, the share is removed.
- **Banner whitelists:** a fellowship can be a whitelist entry (section 1.4).
- **Member list:** the fellowship screen lists the owner, admins and members with their selected titles (in their chosen colour), and shows who is online.
- Fellowships are saved globally in the world save, separate from player data, so they persist when everyone is offline.

## 3. Shields and capes

### 3.1 Shields (player cosmetic)

- 64 designs in three groups:

| Group | Count | Unlock |
| --- | --- | --- |
| Alignment | 38 | +1000 alignment with the design's faction (no pledge needed). Several factions have multiple designs (Gondor fiefs, Mordor subgroups, Harad regions). |
| Achievable | 7 | Four by achievement count (25, 50, 100, 200) and three by specific achievements |
| Exclusive | 19 | Developers, moderators, translators, patrons, lore and build contest winners; from the online player-details service. Some are hidden unless owned. |

- Selected on the Shields page of the menu (browse with arrows, select or remove). Validity is checked when selecting; it is not re-checked later.
- Purely visual: the shield is drawn on the player's back, or on the arm when holding a sword or tool, and in a blocking pose when blocking. It shifts outward if a chestplate is worn. Invisible players show it faintly to those who can see them.
- The selected shield is synced to all players in the world.

### 3.2 Capes

- In this build capes are **not** a player cosmetic. The 27 cape textures are worn by specific NPC types (captains, guards, traders, the wizard). There is no cape selection screen or unlock track for players.

## 4. The Middle-earth menu

### 4.1 Opening

- One key (default **L**) opens the menu when no other screen is open.
- The menu reopens the last sub-screen the player used, instead of the hub, until the player returns to the hub.
- During the tutorial quest, opening the menu jumps straight to the map or factions page when that stage needs it.

### 4.2 Hub

A centred title "<dimension name> Menu" with a row of icon buttons; each shows its name on hover and has a one-letter in-menu shortcut:

| Button | Shortcut | Screen |
| --- | --- | --- |
| Achievements | A | Section 4 of the achievements notes |
| Map | M | Section 4.3 |
| Factions | F | Section 4.4 |
| "?" | none | Placeholder slot; opens nothing in this build |
| Fellowships | P | Section 2 |
| Titles | T | Title picker with colour row |
| Shields | S | Section 3.1 |
| Options | O | Mod client options (HUD positions, map options, hide own location on map, and similar) |

Mini-quests are **not** in the menu; they live in the quest-log book and the HUD tracker.

### 4.3 Map screen

- **Map:** the whole Middle-earth map as a drawn image, pannable (drag) and zoomable (buttons and wheel), with a fullscreen toggle, a sepia toggle and a labels toggle (region and place names that fade by zoom level).
- **Player:** own position and facing. Cursor coordinates (x, z; with y for ops).
- **Other players:** shown if they have not hidden themselves; hidden players are still shown to fellow members when that fellowship shares map locations. Ops in creative see everyone; an admin command can hide a player from non-ops; a server config can force all locations on or off. Not shown during a siege state.
- **Waypoints:** fixed waypoints (named places) and the player's custom waypoints, each toggleable. Locked waypoints show why: not yet visited (region not entered), enemy (alignment too low), or unlockable through conquest.
- **Custom waypoints:** create at the player's position (not inside protected land the player lacks rights in; config may set a minimum y), rename, delete, share with fellowships. Count limit is 5 + floor(achievements / 5); the screen shows "used N of M" and tells the player achievements raise the limit.
- **Fast travel:** hover a waypoint to see either "press the fast-travel key" or the remaining wait time. Pressing the fast-travel key (default **F**) starts a 10 second countdown (200 ticks) before teleporting.
- **Fast-travel wait time:** per waypoint, time = min + (max - min) x 0.9^(times the player has used that waypoint), multiplied by max(1, distance x 0.000012). Server defaults: min 180 s, max 1800 s (3 and 30 minutes). Adjustable by command. The clock counts up since the last travel.
- **Admin teleport:** for ops, the map-teleport key (default **M**) teleports to the cursor.
- **Areas of influence overlay:** shows the selected faction's full and reduced alignment zones; a message if zones are disabled in the world.
- **Conquest overlay:** for pledged players, a heatmap of conquest for their faction and its relations; viewing neutral/friendly factions' conquest needs a higher rank.

### 4.4 Factions screen

- One faction at a time, grouped by region; arrows switch faction and region.
- Shows: alignment and its state (rank name), pledge limits, the full rank list with thresholds (unknown higher ranks shown as "???"), relations (allies, enemies, mortal enemies), and whether the faction approves of killing civilians.
- Statistics per faction: enemies slain, NPCs slain, trades made, units hired, mini-quests completed, conquest earned.
- Pledge button with requirements, warnings about mortal enemies and pledge-break cooldown; break-pledge confirmation.
- Button to view the faction's areas of influence on the map.
- Ops can view another player's alignments.

### 4.5 Other key bindings

All in a mod key category, rebindable:

| Default key | Action |
| --- | --- |
| L | Open the Middle-earth menu |
| F | Fast travel (on the map) |
| M | Map teleport (ops) |
| Left / Right arrows | Cycle the faction shown on the HUD alignment bar |
| Up / Down arrows | Cycle the faction region on the HUD alignment bar |

The alignment keys only work with no screen open and are rate-limited by a couple of ticks; the chosen faction is saved per region on the server.

## 5. Config surface (server)

- Allow banner protection; allow self-protecting banners; protection warning cooldown (ticks).
- Enable fellowship creation; fellowship max size.
- Force map locations (off / force hide / force show).
- Custom waypoint minimum y.
- Fast-travel min/max wait (also by command).
