# Playtest: wave G5 (and the untested G4 merges)

Branch: `integrate/wave-g5` (draft PR, do not merge). It is `main` plus PRs #169-#180.
Covers what no human has tested yet: merged PRs #152-#165 and open PRs #169-#180.

```sh
git fetch origin && git switch integrate/wave-g5
./gradlew runClient
```

New singleplayer world, Creative, **Allow cheats: ON**. Tick a box when the check passes; note PR number + what you saw when it fails.

## Dev commands (all verified in our code)

| What | Command |
| --- | --- |
| Go to Middle-earth | `/lotrrevival middleearth` |
| Survival / creative | `/gamemode survival` / `/gamemode creative` |
| Alignment | `/lotr alignment set @s lotrrevival:hobbit 100` (also `get`, `add @s <faction> <n>`, `reset @s`) |
| Pledge | `/lotr pledge set @s lotrrevival:hobbit force`, `/lotr pledge clear @s` |
| Factions | `/lotr faction list all` |
| Waypoints | `/lotr waypoint list`, `/lotr waypoint unlock @s all`, `/lotr waypoint clock @s 1000000` |
| Fast travel | `/lotr waypoint travel <Tab>` (`brandywine_bridge` or `fixed:lotrrevival:brandywine_bridge` = mod waypoint, `custom:<id>` = yours) |
| Custom waypoints | `/lotr waypoint custom create <name>`, `... rename <id> <name>`, `... delete <id>` |
| Ring portal | `/lotr portal info`, `/lotr portal reset` |
| Quests | `/lotrquest offer <entity>`, `/lotrquest list`, `/lotrquest clear @s` |
| Bounties | `/lotrbounty list [faction]`, `/lotrbounty addkills <player> <faction> <n>`, `/lotrbounty clear <player>` |
| Banners | `/lotrbanner info <pos>`, `/lotrbanner range <pos> <n>|reset` |
| Fellowships | `/fellowship create "Name"`, `/fellowship list`, `/fellowship info <name>` (admin: `/lotrfellowship list`) |
| NPC settings | `/lotr npc friendlyfire on|off` |
| Middle-earth menu | key **L** |
| Run GameTests in-game | `/test runall` |

Useful ids: NPCs `lotrrevival:hobbit`, `hobbit_bounder`, `hobbit_shirriff`, `bree_guard`, `bree_man`, `bree_hobbit`. Tables `lotrrevival:hobbit_crafting_table`, `bree_crafting_table`, `ranger_crafting_table`, `gondor_crafting_table`, `mordor_crafting_table`. Coins `lotrrevival:silver_coin`, `silver_coin_10`, `silver_coin_100`.

Tip: `/summon lotrrevival:hobbit ~ ~ ~3 {NoAI:1b}` keeps an NPC still for screen tests.

---

# Already merged (#152-#165)

## #152 Ores (copper, tin, silver, Morgul iron, mithril)
Setup: `/lotrrevival middleearth`, `/gamemode spectator`.
1. Fly below y 64 in a few biomes (Shire, Bree-land, Lone-lands).
- [ ] Tin ore (stone and deepslate variants) common between y -64 and 64
- [ ] Copper ore present in the same range
- [ ] Silver ore only below about y -32, rarer
- [ ] No Morgul iron or mithril ore anywhere (no pilot biome has them yet)
- [ ] Tin/silver ore drops raw tin/raw silver with a stone pickaxe or better (survival)

## #153 Quest offer screen and markers
Setup: Middle-earth, survival, `/lotr alignment set @s lotrrevival:hobbit 10`, `/summon lotrrevival:hobbit ~ ~ ~4 {NoAI:1b}`, then `/lotrquest offer @e[type=lotrrevival:hobbit,limit=1,sort=nearest]`.
- [ ] Marker shows over the hobbit's head within 12 blocks, gone at 13+
- [ ] Right-click opens the offer screen: giver name, opening line, objective with count, reward line
- [ ] Decline closes it; marker stays
- [ ] Accept gives the quest (`/lotrquest list`), marker disappears
- [ ] `/lotr alignment set @s lotrrevival:hobbit -100`, offer again: no marker
- [ ] (2 players, optional) both open the screen; one accepts; the other's screen closes with "Someone else has taken this task."

## #154 Banner editor, whitelist, permissions
Setup: survival in Middle-earth. Creative tab Functional Blocks has one banner per faction (or `/give @s lotrrevival:faction_banner[lotrrevival:banner_design="lotrrevival:hobbit"]`). Place a gold block, banner on top.
1. Right-click the banner.
- [ ] Editor opens for the owner
- [ ] Switch to whitelist mode, set threshold, toggle default permissions, add a player name, Save
- [ ] Reopen: values stuck; `/lotrbanner info <pos>` agrees
- [ ] Threshold outside 1-10,000 is refused (nothing changes)
- [ ] Creative + op: Range box visible; survival: hidden
- [ ] (2 players) non-owner right-click does not open the editor; stranger breaking blocks inside gets the warning; whitelisted fellowship member can build

## #155-#159 Gear sets (bronze/iron, Shire/Bree, Ranger/Arnor, Gondor/levy, Mordor)
Setup: creative; place each faction table. JEI not needed, check the creative tab.
- [ ] All new weapons/armour have textures (placeholders), names, and show in the creative tab
- [ ] Bronze/iron gear crafts on the vanilla table; daggers, spears, pikes, battleaxes, hammers, throwing axes present
- [ ] Shire armour crafts on the hobbit table, Bree on the Bree table, Ranger/Arnor on the ranger table, Gondor/levy on the Gondor table, Mordor on the Mordor table
- [ ] A faction recipe does NOT work on the vanilla table or another faction's table
- [ ] Armour bars look right when worn (e.g. Bree 2/5/4/2, Mordor 2/6/5/2) and armour layers render on the player
- [ ] Battleaxe strips logs and chops fast; iron hammer knocks back harder
- [ ] Gondor lance: holding it on foot slows you (~80% speed); riding a horse/boat clears it; switching item clears it
- [ ] Pike: longer reach in melee (hits a mob a sword can't)

## #160 Ring portal
Setup: overworld, survival, `/lotr portal reset`, `/give @s lotrrevival:gold_ring`. Light a fire on netherrack (or a lit campfire).
1. Stand within 16 blocks, throw (Q) the ring into the fire.
- [ ] Small blast (no block damage by default), ring consumed, portal grows above the fire (rings + particles)
- [ ] While still growing it does nothing
- [ ] Stand inside the full portal: after ~4 s (survival) you arrive in Middle-earth next to a twin portal, facing it
- [ ] Creative: crosses almost at once; a mob or dropped item crosses at once
- [ ] Step back into the twin: return to the overworld portal
- [ ] Right after arrival you are not bounced straight back
- [ ] A second ring thrown in fire does nothing; `/lotr portal info` shows positions; `/lotr portal reset` allows a new portal

## #161 Fast travel
Setup: Middle-earth, survival, `/lotr waypoint unlock @s all`, `/lotr waypoint clock @s 1000000`.
1. `/lotr waypoint travel <Tab>` and pick a waypoint far away.
- [ ] Countdown starts (status shown); about 10 s later you arrive at the waypoint on safe ground
- [ ] Moving / jumping / taking damage during the countdown cancels it
- [ ] `/lotr waypoint clock @s 0` then travel: refused with a "not ready" message
- [ ] Riding a horse: horse arrives with you
- [ ] Hired units following you arrive with you (after #162 below)
- [ ] Hobbit-town waypoints refused at hobbit alignment -1 (`/lotr alignment set @s lotrrevival:hobbit -1`)

## #162 Hiring purchase (Units tab)
Setup: Middle-earth, survival, `/lotr alignment set @s lotrrevival:hobbit 50`, `/give @s lotrrevival:silver_coin_10 12`, `/summon lotrrevival:hobbit_shirriff ~ ~ ~3`.
1. Right-click the Shirriff, Units tab, type a squadron name, hire 3 Bounders.
- [ ] Units tab lists Bounders at 40 coins each; hire works; coins deducted; alignment unchanged
- [ ] Hired Bounders follow you
- [ ] At 39 coins the row is greyed out with a reason
- [ ] At alignment +49 the Shirriff only talks (no Units tab)
- [ ] Pledged (`/lotr pledge set @s lotrrevival:hobbit force`, alignment 100): cost shows 20

## #163 Unit command screen
Setup: hire a Bounder (above).
- [ ] Right-click own unit (not sneaking) opens the command screen: name, faction, level/XP bar, kills, status, required alignment, pledge
- [ ] Guard on + range: walk away, unit stays; guard off: it follows again
- [ ] Put a helmet in the helmet slot: unit wears it; take it out: its own helmet returns
- [ ] Dismiss needs a second (confirm) click; unit leaves service and drops given gear
- [ ] Sneak-right-click talks instead; name tag on a hired unit does not rename it

## #164 Command horn, command sword, squadrons
Setup: hire 3 Bounders with squadron "A". `/give @s lotrrevival:command_horn`, `/give @s lotrrevival:command_sword`.
- [ ] Use a blank horn: mode picker opens (halt/ready/summon)
- [ ] Sneak-use the horn or sword: squadron rename screen; set "A"
- [ ] Halt horn (hold ~2 s): squadron A units stop; horn becomes Ready; Ready: they move again
- [ ] Summon from ~30+ blocks: units come to you
- [ ] Units guarding or in another squadron ignore the horn
- [ ] Sword: aim at a mob within 64 blocks and swing: nearby units (12 blocks) attack it; aim at nothing cancels
- [ ] Sword deals only 1 damage itself

## #165 Fellowships page
Setup: key L, Fellowships.
- [ ] Create a fellowship from the page (name hints, "leads n of m")
- [ ] Open it: icon, owner, members, three option toggles, Invite / Rename / Set icon / Disband
- [ ] Disband / Leave ask for confirmation
- [ ] (2 players) invite shows up for the other player with Accept/Reject; a plain member sees options read-only and only Leave

---

# Open PRs in this branch (#169-#180)

## #169 NPC gear loadouts and weapon-class melee
Setup: Middle-earth, `/summon lotrrevival:hobbit_bounder`, `hobbit_shirriff`, `bree_guard` (a few of each).
- [ ] Bounders hold a bronze or iron dagger and wear the Shire helmet
- [ ] Shirriffs hold an iron dagger and a dyed leather cap
- [ ] Bree guards: mostly iron sword, some iron pike; Bree helmet + chestplate
- [ ] Anger a pike guard (survival, hit it): slower swings than a sword guard but hits from further away
- [ ] Dagger NPCs swing faster than sword NPCs

## #170 Banner spawn protection, fellowship unit protection
Setup: Shire, survival. Place a gold block + a Mordor banner (`/give @s lotrrevival:faction_banner[lotrrevival:banner_design="lotrrevival:mordor"]`). `/kill @e[type=lotrrevival:hobbit,distance=..40]`.
- [ ] Wait a few minutes: no hobbits spawn within 32 blocks of the banner (they still spawn outside)
- [ ] Break the banner: hobbits spawn there again
- [ ] (2 players) both in one fellowship with unit protection on: your hired units do not attack the other player or their units; with the option off they can

## #171 Bounty kills gated by influence
Influence is a fixed 1.0 until the areas-of-influence system exists, so nothing changes in play yet. Sanity only:
- [ ] Survival, kill a hobbit: `/lotrbounty list lotrrevival:hobbit` shows your kill record
- [ ] Bronze block under a banner protects 8 blocks, silver block 16 (`/lotrbanner info <pos>`)

## #172 Faction bows, sling, pebble
Setup: survival, `/give @s lotrrevival:ranger_bow`, `gondor_bow`, `mordor_bow`, `sling`, `pebble 64`, `minecraft:arrow 64`.
- [ ] Bows show the pulling animation (3 stages) in hand and full draw at 1 s
- [ ] Gondor/Mordor arrows hit slightly harder than Ranger/vanilla
- [ ] Sling: tap (under ~0.2 s) fires nothing; full wind-up (0.5 s) throws a pebble; pebble breaks on hit (no drop)
- [ ] Pebble deals 1 heart to a pig at full wind-up
- [ ] 1 gravel crafts 4 pebbles (vanilla table); sling crafts on the hobbit table

## #173 Spear recipe shape
Setup: vanilla crafting table, iron ingots + sticks.
- [ ] Straight column (ingot, stick, stick) gives an iron SHOVEL, not a spear
- [ ] Slant (ingot top-middle, stick middle-middle, stick bottom-left) gives an iron spear; the mirrored slant also works
- [ ] Same slant on faction tables gives the faction spear (Ranger/Gondor/Mordor)

## #174 Alloy forge and orc forge
Setup: survival, `/give @s lotrrevival:alloy_forge`, `lotrrevival:orc_forge`, `minecraft:raw_copper 8`, `lotrrevival:raw_tin 8`, `lotrrevival:raw_morgul_iron 4`, `minecraft:coal 8`.
- [ ] Alloy forge GUI: 4 inputs, 4 alloy slots, 4 outputs, 1 fuel
- [ ] Copper in input + tin in alloy slot (or swapped) gives 2 bronze ingots; forge lights up (light level 13)
- [ ] Four lanes run in parallel on one coal
- [ ] Raw Morgul iron is refused in the alloy forge; in the orc forge it gives orc steel
- [ ] Food is refused in every slot
- [ ] Taking outputs gives XP
- [ ] Hopper on top feeds inputs, hopper on the side feeds fuel, hopper below pulls outputs
- [ ] Breaking the forge drops its contents
- [ ] Recipes: alloy forge = 8 stone bricks ring (vanilla table); orc forge = 8 polished blackstone bricks ring on the Mordor table

## #175 Thrown spears and throwing axes
Setup: SURVIVAL (creative keeps the item), `/give @s lotrrevival:bronze_spear`, `lotrrevival:iron_throwing_axe 16`, a few pigs/zombies.
- [ ] Spear: hold use to charge like a trident; release under ~0.25 s does nothing
- [ ] Full charge throw: crit particles, deals solid damage, spear loses 1 durability, lands and can be picked up
- [ ] Pikes/lances cannot be thrown
- [ ] Throwing axe: right-click throws at once, spins in flight, short cooldown (~0.5 s), stacks to 16
- [ ] (2 players) a landed spear/axe can only be picked up by the thrower for 20 s
- [ ] Thrown weapons render in flight (not invisible)

## #176 Crossbows and bolts
Setup: survival, `/give @s lotrrevival:bronze_crossbow`, `lotrrevival:iron_crossbow`, `lotrrevival:bolt 32`, `minecraft:arrow 16`, `minecraft:firework_rocket 4`.
- [ ] Only bolts load (arrows and fireworks do not)
- [ ] Charge takes 2.5 s; item shows pull/charged states; Quick Charge shortens it
- [ ] Bolt flies with its own model and hits hard (iron > bronze slightly); can be picked back up
- [ ] Bolt recipe: iron or bronze ingot + stick + feather (shapeless) gives 4
- [ ] Crossbow recipes on the vanilla table do not clash with the vanilla crossbow
- [ ] (cosmetic, known) third-person arm pose finishes early

## #177 Custom waypoints and travel sync
Setup: Middle-earth, survival, `/lotr waypoint clock @s 1000000`.
- [ ] `/lotr waypoint custom create Home`: created at your feet; `/lotr waypoint list` shows it with an id
- [ ] Rename and delete by id; ids are not reused after delete
- [ ] Name longer than 32 chars refused (chat message, no kick)
- [ ] Create in the overworld: refused
- [ ] Walk away, `/lotr waypoint travel custom:<id>`: travel works
- [ ] Limit: 6th waypoint (with few uses) refused with a message
- [ ] Relog: custom waypoints and clock still there

## #178 Bounty offers
Setup: Middle-earth, `/lotr alignment set @s lotrrevival:hobbit 100`, summon a few `lotrrevival:hobbit` (NoAI).
Single player:
- [ ] `/lotrbounty addkills @s lotrrevival:hobbit 25`, then `/lotrquest offer` a hobbit until a bounty rolls: screen names YOU as the target with a fixed reward; Accept is refused (you are the target)
- [ ] Right-click a giver a few times: about 1 in 3 gives a hint about the target's last known biome
(2 players):
- [ ] Bounty on player B: player A at +99 cannot accept, at +100 can; a second bounty on the same target is refused
- [ ] Killing B completes the bounty and pays exactly the shown coins

## #179 Bottle of poison and poisoned daggers
Setup: survival, `/give @s lotrrevival:bottle_of_poison 2`, `lotrrevival:bronze_dagger`.
- [ ] Bottle cannot be drunk; stacks to 1
- [ ] Dagger + bottle in any crafting grid: poisoned dagger (dark green "Poisoned" tooltip), keeps name/damage; glass bottle returned
- [ ] Poisoned dagger + bottle again: no recipe
- [ ] Hit a zombie/pig: Poison I for about 3-7 s (longer on Hard, shorter on Easy)
- [ ] Arrows or non-dagger items do not poison
- [ ] Stand-in recipe: glass bottle + spider eye + poisonous potato

## #180 Road bridges
Setup: new world (bridges only appear in new chunks), Middle-earth, `/lotr waypoint unlock @s all`, `/lotr waypoint clock @s 1000000`, `/lotr waypoint travel brandywine_bridge`.
- [ ] Where a road meets water there is an oak plank/slab deck, raised above the water (higher over deeper water, max 5)
- [ ] Oak fence rails on both edges, stone-brick piers about every 8 blocks
- [ ] Water and river bed under the bridge are intact; boats pass
- [ ] Small streams in the Shire get short bridges; no floating or chunk-cut decks at chunk borders
- [ ] Lone-lands bridges look worn (gaps), Shire bridges complete
- [ ] Screenshot for the PR: one Shire stream bridge and the Brandywine

---

## Notes for the reviewer
- Already GameTested in CI: numbers, recipes, codecs, server validation. This list is for rendering, feel, GUIs, multiplayer and worldgen.
- Failing check: comment on the PR with the box text, what happened, and the log (`run/logs/latest.log`) if it crashed.
