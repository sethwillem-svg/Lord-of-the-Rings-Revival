# Hiring, trading and coins

- **Pod:** npc
- **Lead:** Opus 3
- **Status:** approved (maintainer decision: match Legacy for coins, traders and hiring)
- **Depends on:** `specs/core/core-apis.md` (alignment read, pledge, faction kinds, `AlignmentCreditor`, `FactionKillEvent`, registers, payload conventions), `specs/npc/npc-base.md` (roles, `trade_min`/`hire_min`, `NpcInteractEvent`, `Hireable`, healing, despawn rules), `specs/progression/data-model.md` (`trade` and `hire_unit` triggers, coin quest rewards), and a new **Core coin API** (requested in this spec, see Data and APIs)
- **Sources:** our own-words notes `docs/reference/legacy/hiring-units.md` and `docs/reference/legacy/combat-trade.md` (sections 4 and 5). Numbers marked (L) are Legacy's and adopted by maintainer decision. Everything else is ours.

## Summary

Coins are Middle-earth's money: silver coins in three denominations that NPCs drop, quests pay and traders take. Traders sell a small, randomly rolled set of goods and buy some goods back; each trader's stock is a little different and runs dry if a player trades it too hard. Unit traders (captains, and the Hobbit Shirriff first) hire out warriors for a one-off coin payment that is cheaper the more a faction trusts the player. A hired unit follows its owner, guards a spot on command, fights the owner's enemies, gains experience and a little toughness for every enemy NPC it kills, and walks away if its owner's standing with its faction falls below what it took to hire it. Players care because coins finally buy something, alignment buys an army, and every unit is a small investment that can die. Trading and hiring never change alignment.

## Player-facing behavior

1. **Coins.** Silver coins come as 1, 10 and 100 pieces. Prices are always in total coin value; paying takes coins from the inventory automatically and hands back change. Every trader screen has an exchange tab to break or combine coins.
2. **Opening a trader.** Right-clicking a goods trader (innkeeper, farmer) the player is allowed to trade with opens the trade screen on the **Buy** tab. A unit trader (the Shirriff) opens the **Units** tab. Sneak-right-click still talks. A player below the role's `trade_min` / `hire_min` gets a neutral line (npc-base behavior, unchanged).
3. **Buying.** The Buy tab lists 3-9 offers, each an item, a count and a price. Offers the player cannot afford, or that are sold out, are greyed. Clicking an offer pays and puts the item in the inventory (dropped at the player's feet if full).
4. **Selling.** The Sell tab lists what this trader buys and at what price, plus 9 sell slots. The player puts items in; the screen shows what they are worth; **Sell** pays coins for every full lot of matching items. Items the trader does not want stay in the slots and come back on close.
5. **Running a trader dry.** Trading a lot of one offer greys it out for a while ("sold out"); trading a great deal with one trader makes it re-roll its whole stock and close every offer for 5 minutes.
6. **Hiring.** The Units tab lists the units this trader offers: name, cost for this player, required alignment and any pledge requirement. Units the player cannot hire are greyed with the reason. The player may type a squadron name, then clicks **Hire**: coins are taken and the unit appears at the player's feet facing the way the player looks.
7. **Hired units.** A unit follows its owner, fights what the owner fights and what attacks the owner, never hurts the owner and cannot be hurt by the owner. Right-clicking one's own unit opens its command screen: guard here (with a guard range), auto-teleport toggle, its gear, level and kills, and Dismiss. Command horns and the command sword order whole squadrons.
8. **Leveling.** Each enemy NPC a unit kills gives it 1 XP. On level-up the owner is told, fireworks in the faction's colour go off over the unit and it gains 1 max health.
9. **Desertion and death.** If the owner's alignment with the unit's faction drops below the unit's requirement (or the owner stops meeting its pledge condition) the unit deserts and becomes an ordinary NPC. A unit that dies is gone: no refund, no respawn. The owner gets a chat message.
10. **The Shirriff.** At +50 Shire alignment the Shirriff hires out Bounders for 40 coins (unpledged) or 20 coins (pledged to the Shire), cheaper the higher the player's standing.

## Rules and numbers

Ticks: 20 per second. Numbers marked (L) are Legacy's. Others are ours and are tuning placeholders.

### Coins (Core owns; specified here at Core's request via the `pod:core` ticket)

| Thing | Value | Notes |
| --- | --- | --- |
| Denominations (L) | 1, 10, 100 | Items `lotrrevival:silver_coin` (exists, value 1), `lotrrevival:silver_coin_10`, `lotrrevival:silver_coin_100`. All in `#lotrrevival:coins`. Stack size 64 |
| Display names | "Silver Coin", "Silver Coin (10)", "Silver Coin (100)" | Lang; tooltip shows value. Textures are new art |
| Counted for paying (L) | main inventory, hotbar, off hand | Not armor slots, not containers (bundles, pouches later), not ender chest |
| Paying with change | remove coin stacks lowest value first (1s, then 10s, then 100s, inventory order within a value) until the removed value is at least the price; give back the difference as change | All or nothing: if the player's total is below the price, nothing is removed |
| Giving coins | as the fewest coins (largest denominations first); into the inventory, overflow dropped at the player's feet | Used by selling, change, quest rewards |
| Dropping coins at a position | same split, spawned as item entities at the position | Used by Progression quest rewards ("dropped at the giver") |
| Stolen coins (L) | reserved | Pickpocketing is a later spec; stolen-flagged items are refused by traders and the coin API will skip them for paying |
| Creative players | pay like anyone else | |

### Trader roles at G2

| Entity type | Role | `trade_min` (L) | Pool |
| --- | --- | --- | --- |
| `hobbit` | innkeeper | 0 | `hobbit_innkeeper` |
| `hobbit` | farmer | 0 | `hobbit_farmer` |
| `bree_hobbit` | innkeeper | 0 | `bree_innkeeper` |
| `bree_hobbit` | farmer | 0 | `bree_farmer` |
| `bree_man` | innkeeper | 0 | `bree_innkeeper` |
| `bree_man` | farmer | 0 | `bree_farmer` |
| `hobbit_shirriff` | unit trader | 50 (hire) | units only, no goods |

- **Farmer becomes a trader role** (L): it was a non-trader at G2. Its kill value becomes 2 (trader), it heals like a trader, and right-click opens the trade screen. Farmers hiring out farmhands is later.
- Later trader types (smiths +50, stablemasters +50, Bree market traders 0, elf traders +75, dwarf miners and smiths +100, orc traders +100, travelling merchants) only add pool files and role rows.

### Offer pools and rolling (L)

- Each trader role has two pools: **player buys** (the trader sells) and **player sells** (the trader buys). Each pool entry: item (sell entries may name an item tag instead), count per lot, base price.
- At first spawn (or, for an NPC with no stock yet, the first time the screen opens) the trader rolls each list independently:
  - size = 3 + r + r + r with each r uniform 0..2, so 3-9 entries, capped by the pool size;
  - entries are drawn from the pool without repeats, uniformly;
  - price = max(1, round(base price x uniform(0.75, 1.25))), rounding half up.
- The rolled stock is saved on the trader and survives save/load. It is per trader and shared by all players.
- **No alignment-based item pricing** (L): alignment only gates access. Pledge does not change item prices.
- Not at G2 (needs core items): drink strength (price x strength 1-3), random drink vessels and random gear modifiers on bought gear.

### Stock locking and refresh (L)

| Thing | Value | Notes |
| --- | --- | --- |
| Per-offer recent value | + price for each lot bought or sold | Separate counter per offer, both lists |
| Offer locks | when its recent value reaches 200 | Locked offers are greyed "sold out" and cannot be traded. The trade that crosses 200 still completes |
| Decay | recent value -1 every 60 ticks while the trader is loaded, never below 0 | A locked offer reopens when it reaches 0, so a fully locked offer reopens after about 12,000 ticks (10 min) |
| Refresh | when total value traded since the last refresh reaches 5,000 | Re-roll both lists (new entries, new prices), reset every counter, then lock every new offer for 6,000 ticks (5 min) |
| Timed lock | counts down every tick while loaded | An offer is tradable only when both its recent-value lock and timed lock are clear |

### G2 pools (vanilla placeholders, swapped for core items as they land)

Prices are base prices before the ±25% roll. Count 1 unless shown.

| Pool | Player buys (trader sells) | Player sells (trader buys) |
| --- | --- | --- |
| `hobbit_innkeeper` | bread 5, baked potato 5, cooked chicken 5, cooked porkchop 6, cooked mutton 6, cookie x4 5, mushroom stew 10, rabbit stew 10, pumpkin pie 10, cake 14, `lotrrevival:wooden_mug` 3 | wheat 1, potato 1, carrot 1, beetroot 1, egg 1, sweet berries 1, apple 2, sugar 2, honey bottle 3 |
| `hobbit_farmer` | wheat 2, potato 2, carrot 2, beetroot 2, egg 2, apple 3, pumpkin 3, melon slice x4 2, bread 5, hay bale 15, wheat seeds x4 1 | bone meal 1, bone 1, feather 1, string 1, leather 2, rotten flesh x4 1 |
| `bree_innkeeper` | bread 5, baked potato 5, cooked beef 7, cooked porkchop 6, mushroom stew 10, rabbit stew 10, pumpkin pie 10, cake 14, `lotrrevival:wooden_mug` 3 | wheat 1, potato 1, carrot 1, egg 1, apple 2, sugar 2, honey bottle 3, cod 2, salmon 2 |
| `bree_farmer` | wheat 2, potato 2, carrot 2, beetroot 2, egg 2, apple 3, pumpkin 3, bread 5, hay bale 15, wheat seeds x4 1 | bone meal 1, bone 1, feather 1, string 1, leather 2, raw beef 2, raw porkchop 2 |

Pools are data; the numbers are placeholders within Legacy's price bands (combat-trade.md section 5).

### Trade screen (L where marked)

| Thing | Value |
| --- | --- |
| Tabs | Buy and Sell (traders with a goods pool), Units (unit traders), Exchange (always) |
| Opens on | Buy for `NpcInteractEvent(TRADE)`, Units for `NpcInteractEvent(HIRE)` |
| Closes (L) | player more than 12 blocks from the trader, trader dies or is removed, trader gets any attack target, or the player becomes hostile to its faction |
| Buy | one lot per click; needs the offer tradable and the player able to pay; item to inventory, overflow dropped |
| Sell | 9 sell slots. **Sell** button: for each sell offer, while the offer is tradable and the slots hold at least `count` matching items, consume `count` and pay `price`, adding to the recent value (so a lot can push the offer into its lock). Unmatched and leftover items stay in the slots and return to the inventory on close (dropped if full) |
| Exchange | one slot. **Break** takes one 10 or 100 coin from the slot and gives 10 coins of the next lower value. **Combine** takes every full group of 10 coins of value 1 or 10 from the slot and gives one coin of the next higher value per group. Results go to the inventory, overflow dropped |
| Multiplayer | two players may have the same trader open; stock changes resync to every viewer |
| Sounds | vanilla villager trade sound as a placeholder; our own sounds later |

### Hiring requirements (L)

A unit entry is hirable by a player when all hold:

1. The player can open the trader's unit screen: not hostile, not targeted by the trader, alignment with the trader's faction >= the role's `hire_min` (npc-base).
2. Alignment with the unit's faction >= **required alignment** = list base + entry extra.
3. The entry's pledge condition holds, if it has one:
   - `faction`: pledged to the unit's faction;
   - `any_elf`: pledged to a faction in `#lotrrevival:kind/elf` that is not in `#lotrrevival:kind/men` (so Dorwinion does not count);
   - `any_dwarf`: pledged to a faction in `#lotrrevival:kind/dwarf`.
4. The player can pay the cost (coin API count).

### Hire cost (L)

With base cost C, required alignment R, the player's alignment A with the unit's faction, and "pledged" meaning pledged to the unit's faction (the trader's faction in every list):

- surplus S = max(A - R, 0)
- f = clamp(S / 1500, 0, 1) if pledged, else clamp(S / 2000, 0, 1)
- cost = max(1, round(C x (pledged ? 1 : 2) x (1 - 0.5 f))), rounding half up

| Case (Bounder, C 20, R 50) | Cost |
| --- | --- |
| Unpledged at +50 | 40 |
| Unpledged at +1,050 (S 1000, f 0.5) | 30 |
| Unpledged at +2,050 or more | 20 |
| Pledged at +100 (S 50, f 0.0333) | 20 (19.67 rounds to 20) |
| Pledged at +800 (S 750, f 0.5) | 15 |
| Pledged at +1,550 or more | 10 |

Payment is **one-off**. There is **no upkeep, wage or food** (L). Hiring gives **no alignment** (core rule).

### Unit lists

Data per unit trader type. G2 ships one list; the rest are added with their entity types (values from hiring-units.md section 3).

| Unit trader | List base | Entries (unit, base cost / extra alignment) | Ships |
| --- | --- | --- | --- |
| `hobbit_shirriff` | +50 | `hobbit_bounder` (20 / 0) | G2 |
| `hobbit_shirriff` | +50 | `hobbit_bounder` riding a Shire pony (40 / +50) | with the mount spec |
| `bree_captain` | +100 | `bree_guard` (20 / 0); Bree banner bearer (40 / +150) | when `bree_captain` exists |
| farmers (hobbit, Bree) | 0 | farmhand (40 / +50), Farmer task | with farmhands (later) |

- No unit limit per player (L).
- Each hire fires `NpcHireEvent` so Progression can count "units hired" per faction and fire `hire_unit`.

### Hire flow

1. Units tab rows show: unit name (entity type name, plus "on a Shire pony" style suffix for mounted entries), cost for this player, required alignment, pledge requirement, and a grey-out reason (`alignment`, `pledge`, `coins`).
2. Squadron field: free text, up to 200 characters (L), trimmed; shared by all rows. Empty means no squadron.
3. **Hire** (server validates everything again): take the cost through the coin API, spawn a fresh NPC of the entry's type at the player's feet with the player's facing (L), with its normal name roll and loadout, persistent, then mark it hired: owner, owner name, required alignment R, pledge condition, squadron, task `warrior`, command state FOLLOW, teleport on.
4. Play the trade sound. Post `NpcHireEvent(player, trader, unit, entry id, cost)`.
5. If spawning fails (no room), refund nothing because nothing was taken: validate the spawn position before taking coins.

### Ownership, desertion, dismissal (L)

- A unit stores owner UUID, owner name, required alignment R, pledge condition, unit faction, squadron, task, command state, guard position and range, teleport flag, halted flag, XP, level, kill count.
- **Desertion check** every 20 ticks while the owner is online (any dimension): if alignment(owner, unit faction) < R, or the pledge condition no longer holds, the unit deserts. The owner gets a chat message naming it. It becomes an ordinary NPC of its faction: no owner, no anchor, normal wild despawn rules, any gear the player gave it is dropped at its feet and its original gear restored.
- **Owner offline:** no desertion check. The unit acts as if guarding its current position with range 8 until the owner is back online and in the same dimension; then it resumes its previous command state. (Ours; Legacy does not define this clearly.)
- **Dismiss** from the command screen (with a confirm click): same result as desertion but with a "dismissed" message. Dismissing a rider later dismisses its hired mount too.
- Hired units never despawn, never target their owner or the owner's mount, never target another unit of the same owner unless their factions are enemies, and are never damaged by their owner (npc-base). Another player's units can be damaged normally (fellowship protection is later).
- Self-defence (L, core `grantsAlignmentOnKill`): a player who kills someone else's unit that was ordered by a command sword to attack them, while holding positive alignment with its faction, loses no alignment.

### Commands (L)

**Unit command screen** (right-click own unit; sneak-right-click talks with `hired` lines):

| Control | Default | Effect |
| --- | --- | --- |
| Teleport automatically | on | See Following |
| Guard mode | off | Takes the unit's current position as its guard point. It stays within guard range, attacks anything it would target within that range, and walks back when idle. Ignores horns and the command sword |
| Guard range | 8 | Slider 1-64 |
| Gear | | Slots for helmet, chest, legs, boots and main-hand weapon. Items the player puts in replace what the unit wears; the original pieces are remembered and come back when the player takes the item out. Ranged and bomb slots arrive with the combat spec |
| Dismiss | | Confirm click, then dismiss |
| Read-only | | Name, faction, level, XP to next level (bar), kill count, status (`following`, `halted`, `guarding`, `in combat`, `owner away`), required alignment and pledge condition |

**Following:**

| Thing | Value |
| --- | --- |
| Start pathing (L) | owner more than 8 blocks away |
| Stop (L) | within 6 blocks |
| Speed (L) | path speed multiplier = 0.37 / the unit's movement speed attribute, so every unit moves at effective speed 0.37 |
| Water (L) | walks through water when following |
| Teleport (L) | checked every 20 ticks: if teleport is on, it cannot path to the owner and is farther than max(sense range, 24) blocks, it teleports to a random safe standing spot 3-6 blocks from the owner (10 tries, then the owner's exact position if that is safe, else stay) |
| Dimensions (L) | units never follow through portals. Owner in another dimension counts as owner away |
| Fast travel (L, later) | waypoint travel brings every following unit within 256 blocks; needs World's waypoints |

**Command horn** (one item, modes in a data component):

| Thing | Value |
| --- | --- |
| Modes (L) | blank, halt, ready, summon. Using a blank horn opens a screen to pick a mode |
| Use time (L) | 40 ticks, loud horn sound (vanilla goat horn sound as a placeholder), heard by everyone nearby |
| Halt | every matching unit stands still, drops its target and does not seek new targets; it still fights back when hit (ours). Horn mode then switches to ready (L) |
| Ready | matching units resume following. Horn mode then switches to halt (L) |
| Summon | matching units teleport to a safe spot near the owner (same rule as Following), failsafe the owner's exact position |
| Matching units (L) | every loaded unit owned by the user whose squadron equals the horn's squadron, case-insensitive (empty matches empty), that is not guarding and not a Farmer-task unit. No distance limit within the loaded level |

**Command sword:**

| Thing | Value |
| --- | --- |
| Damage (L) | 1 attack damage (weak sword), normal attack speed |
| Use (right-click) (L) | ray-cast up to 64 blocks. Each matching unit within 12 blocks of the player (squadron match, not guarding) picks a valid target within 6 blocks of the aimed point, spread across targets (a target's score = distance squared / 144 + (units already ordered onto it) squared, lowest wins), and attacks it. Aiming at nothing cancels sword orders |
| Order flag | a unit attacking on sword orders is flagged until the order ends (target dead, cancelled, 600 ticks); the flag drives the self-defence rule |

**Squadrons (L):** free text on units, horns and swords. Sneak-use of a horn or sword opens a rename screen for its squadron (max 200 characters). Matching is case-insensitive and trims whitespace.

Recipes (ours): command horn = shaped, 3 iron ingots and 1 leather; command sword = shapeless, iron sword + gold ingot. Placeholders until core metals land.

### Combat behavior of hired units (L)

- They seek what their faction seeks (npc-base), plus: whatever the owner attacked in the last 100 ticks, whatever attacked the owner in the last 100 ticks, and NPCs of a **neutral** faction (relation to the unit's faction) while the owner is hostile to that faction (alignment below its hostility threshold). Bandits are added with their spec.
- Retaliation against whoever hurt the unit or its owner comes first; then the owner's target; then normal priorities.
- Halted units do not seek targets; guarding units only take targets within guard range.
- Hired civilian-class units still fight.
- Healing: 1 HP every 40 ticks without a target (npc-base, unchanged). Banner bonus later.
- **Kills** (core): no alignment for anyone; core fires `FactionKillEvent` with the hire flag when the owner is within 64 blocks. Hired-unit kills do not make witnesses turn on anyone.
- **Witness exclusions** (L): when a player kills an NPC, that player's own units never join the witnesses, and no hired unit joins while the killer holds positive alignment with the unit's faction.
- Speech: after killing an enemy NPC, 1 in 3 chance, owner within 16 blocks: a `hired_kill` line to the owner (L). While following with the owner within 16 blocks: 1 in 4,000 per tick, a `hired_idle` line to the owner (L). Sneak-right-click by the owner gives `hired` lines.

### Unit XP and levels (L)

| Thing | Value |
| --- | --- |
| Who levels | Warrior-task units only; server config `units.leveling` (default true) |
| XP | **1 per kill of an NPC whose faction is an `enemy` or `mortal_enemy` of the unit's faction**. Players, monsters, animals and neutral or friendly NPCs give 0 |
| Kill count | +1 for every living entity the unit kills (shown on the command screen) |
| Total XP for level L | floor(3 x (L - 1) x 1.08^(L - 2)); level 1 needs 0 |
| Level | highest L whose total is <= XP; no cap |
| Per level gained | +1 max health (one attribute modifier, value = level - 1), heal 1, chat message to the owner, fireworks at the unit in the faction's colour; every 5th level a larger burst |
| Riders and mounts (later) | a rider and its hired mount share XP; +1 max health also for a non-NPC mount it rides |

Thresholds: L2 3, L3 6, L4 10, L5 15, L6 20, L8 33, L10 49, L15 114, L20 227, L25 422, L30 750, L40 2179.

### Death (L)

- Owner gets a chat death message (name, level, cause), unless they turned it off: per-player toggle `unit_death_messages` (default on) in the npc player prefs, set by `/lotr npc unitdeathmessages <on|off>`.
- Drops: the gear the player gave it, then its original gear at the normal NPC equipment drop chance (0% at G2). No coins, no loot-table drops, no XP orbs.
- No refund, no respawn, no recovery.

## Edge cases

- **Multiplayer:** stock is shared per trader and every trade is applied on the server in order; a click on an offer that locked a moment earlier fails with a "sold out" message and takes nothing. Hire costs are per player (their own alignment and pledge). Units obey only their owner's horns, sword and screen; another player right-clicking them just talks.
- **Two players buying the last tradable lot:** the first processed wins; the second gets "sold out".
- **Inventory full:** bought items, change, sold-lot coins and exchange results drop at the player's feet. Coins are never lost.
- **Paying exactly with only 100s:** paying 40 with one 100 coin removes the 100 and gives back 6 x 10.
- **Player death, logout, dimension change while the screen is open:** the screen closes; sell and exchange slots return their items (dropped at the player's last position if they cannot be returned).
- **Trader dies:** its stock is lost. A structure anchor respawns a fresh trader with fresh stock (npc-base anchor rule). Legacy keeps the same stock and name on respawn; that is a later anchor change.
- **Owner death:** units keep their command state. Following units teleport to the owner's respawn point only if it is within the teleport rule's reach in the same dimension; otherwise they count the owner as away.
- **Owner alignment drops while offline** (only commands can do that): checked on the first 20-tick check after login.
- **Pledge switch:** pledging to another faction breaks the old pledge; units with a `faction` pledge condition for the old faction desert at the next check, and costs for unpledged rates apply to new hires.
- **Unit in an unloaded chunk:** keeps its state; horns do not reach it; it does not teleport.
- **Unit reaches the owner's hostility threshold:** cannot happen without deserting first, because R is never below the role's `hire_min`, which is at least 0.
- **Name tags** do not work on hired units (L); the right-click opens the command screen instead.
- **Dismissed or deserted unit:** an ordinary wild NPC; it can be hired again only by spawning a new unit from a trader (wild NPCs are not hireable directly; mercenaries are later).
- **Data:** a unit list or pool naming an unknown entity type or item is skipped with one warning; a trader whose pool is missing shows only the Exchange tab (and its Units tab if any) and says a `trader_none` line on open if both goods lists are empty.
- **Interaction with other systems:** alignment is read only (core); coins move only through the coin API (core); Progression listens to `NpcTradeEvent` and `NpcHireEvent`; quest rewards pay through the coin API, not through traders.

## Data and APIs

### Requested from Core (`pod:core` ticket)

1. Items `lotrrevival:silver_coin_10` and `lotrrevival:silver_coin_100` (new art), in `#lotrrevival:coins`, with lang and value tooltips.
2. Coin API, server side, in core (proposed `core.coin.Coins`):
   - `int value(ItemStack stack)`: 0 for non-coins.
   - `int count(Player player)`: total coin value in main inventory, hotbar and off hand.
   - `boolean pay(ServerPlayer player, int amount)`: all-or-nothing payment with change, as in Rules.
   - `void give(ServerPlayer player, int amount)`: fewest coins, inventory then drop at feet.
   - `void drop(ServerLevel level, Vec3 pos, int amount)`: fewest coins as item entities.
   - `List<ItemStack> split(int amount)`: the fewest-coins split.
   - The denomination list (value, item) in descending value, for the exchange tab.
   Progression's quest rewards use `give` / `drop`; the NPC pod uses all of them.

### NPC pod data (datapack JSON, reloadable)

- `data/<ns>/lotrrevival/npc_trades/<entity type path>.json`: role name to `{ "player_buys": [...], "player_sells": [...] }`; each entry `{ "item": id, "count": n, "price": p }`, sell entries may use `"tag": "#..."` instead of `item`.
- `data/<ns>/lotrrevival/npc_units/<entity type path>.json`: role name to `{ "base_alignment": a, "units": [ { "id": "bounder", "entity": "lotrrevival:hobbit_bounder", "cost": 20, "extra_alignment": 0, "pledge": "none" | "faction" | "any_elf" | "any_dwarf", "task": "warrior" | "farmer", "mount": optional entity id, "mount_armor_chance": optional 0..1 } ] }`. Separate folder from `npc_roles` so trading and hiring tickets do not edit the same files.
- `npc_roles` rows for farmers become `trade_min` 0, kill value 2.

### NPC pod state

- Entity attachment `lotrrevival:trader_stock`: both rolled lists (stack, price, recent value, timed lock), total value since refresh. Serialized; not synced (the menu syncs what a viewer needs).
- Entity attachment `lotrrevival:hired_unit`: the unit fields listed under Ownership, plus original gear for replaced slots and the sword-order flag. Serialized. Synced to the owner's client only for the command screen (through the menu), and a small tracked flag "hired by you" for nameplates.
- The existing `Hireable` interface is implemented by the NPC base on top of the attachment; the base also implements core's `AlignmentCreditor` (owner while hired and online) and returns false from `grantsAlignmentOnKill` per the self-defence rule.
- Player prefs attachment (from #42) gains `unit_death_messages` (default true).
- Server config `lotrrevival-npc-server.toml`: `units.leveling` (true).

### Menus, items, payloads, events

- Menu types (via `ModRegistries.MENU_TYPES`, from an npc holder class): `lotrrevival:npc_trade` (Buy, Sell, Units, Exchange tabs), `lotrrevival:unit_command`.
- Items (via `ModRegistries.ITEMS` from `npc.item.NpcItems`, as spawn eggs already are): `lotrrevival:command_horn`, `lotrrevival:command_sword`. Data components (via `ModRegistries.DATA_COMPONENTS`): `lotrrevival:horn_mode`, `lotrrevival:squadron`.
- Client-to-server payloads (`lotrrevival:npc/<name>`): `trade_buy` (offer index), `trade_sell`, `coin_exchange` (break or combine), `hire_unit` (entry id, squadron), `unit_command` (guard on/off, range, teleport toggle, dismiss), `horn_mode` (mode), `squadron_rename` (hand, text). Each validates that the sender has the matching menu open and the menu is still valid (12 blocks of the NPC), or for item payloads that the item is in the named hand. Rate limit per core conventions.
- Server-to-client: stock and unit-row sync via menu data or a `trade_stock` payload to viewers.
- Events on the NeoForge bus, posted after the fact, not cancellable: `NpcTradeEvent(player, trader, direction BUY/SELL, stack, price)`, `NpcHireEvent(player, trader, unit, entry id, cost)`, `UnitLevelUpEvent(unit, owner, new level)`, `UnitDeathEvent(unit, owner, source)`, `UnitReleasedEvent(unit, owner, reason DISMISSED/DESERTED)`.
- Commands: `/lotr npc unitdeathmessages <on|off>`.
- Lang (npc datagen): tab names, buttons, grey-out reasons, sold-out text, desertion, dismissal, level-up and death messages, unit row suffixes, `hired`, `hired_kill`, `hired_idle` speech categories (4+ lines each per race, written new).

## Out of scope

- Mounted unit entries and unit mounts (Shire pony and later beasts), shared rider/mount XP, mount armor rolls: the mount spec.
- Farmhands and the Farmer task, farmer and slaver unit lists: later ticket after a farming AI spec.
- Mercenaries (self-hiring NPCs), conquest horns and invasions (invasion spec), banner bearers and their buffs (combat spec), ranged and bomb gear slots (combat spec).
- Fast travel with units (needs World waypoints), fellowship protection for units (Progression), unit health bars over heads (client option later).
- Travelling traders, smiths, market traders and their pools; trader adverts; drink strength, vessels and gear modifiers in prices; the 1-in-10,000 joke trader.
- Pickpocketing and stolen-item flags (reserved only).
- Keeping a respawned trader's stock and name (anchor change later).
- Any alignment change from trading or hiring (never, by core rule).

## Acceptance tests

- [ ] GameTest (core): paying 40 from one 100 coin leaves 6 x 10; paying 41 from 40 total removes nothing and returns false; `give(137)` yields 1 x 100, 3 x 10, 7 x 1; full inventory drops the rest.
- [ ] GameTest: with a seeded RNG, 10,000 rolls of a 20-entry pool give list sizes only in 3..9 with the expected distribution (3: 1/27, 9: 1/27), no repeats, prices within [0.75, 1.25] x base and never below 1.
- [ ] GameTest: an offer locks after 200 value traded, stays locked at 199 after one decay step, and is tradable again after its recent value decays to 0 (12,000 ticks from 200).
- [ ] GameTest: reaching 5,000 total value re-rolls both lists and every new offer is untradable for 6,000 ticks, then tradable.
- [ ] GameTest: stock survives save/load (write entity NBT, reload, compare offers and counters).
- [ ] GameTest: a player at 0 `hobbit` right-clicks a hobbit farmer and the trade menu opens on Buy; at -1 it talks with a hostile line.
- [ ] GameTest: buying an offer priced 5 with 7 coins leaves 2 coins and the item; with 4 coins nothing changes. `NpcTradeEvent` fires once per lot.
- [ ] GameTest: selling 10 wheat to an offer of count 1 price 1 pays 10 coins and consumes 10 wheat; a non-matching item stays in the sell slot.
- [ ] GameTest: exchange break turns one 100 into 10 x 10; combine turns 23 x 1 into 2 x 10 and leaves 3 x 1 in the slot.
- [ ] GameTest: the menu closes when the player moves 13 blocks away and when the trader gets a target.
- [ ] GameTest: hire cost table above (all six cases) from the pure cost function.
- [ ] GameTest: an unpledged player at +50 `hobbit` with 40 coins hires a Bounder from the Shirriff: 0 coins left, one `hobbit_bounder` within 2 blocks owned by the player with R 50, alignment unchanged, `NpcHireEvent` fired. With 39 coins: nothing happens.
- [ ] GameTest: a player at +49 cannot open the Shirriff's unit tab (TALK posted instead).
- [ ] GameTest: the owner's `hobbit` alignment set to 49 makes the unit desert within 20 ticks (owner messaged, unit unowned); at 50 it stays.
- [ ] GameTest: a unit with a `faction` pledge condition deserts when the owner's pledge is cleared.
- [ ] GameTest: a following unit 20 blocks from the owner paths back and stops within 6 blocks; with no path and 30 blocks away it teleports to 3-6 blocks from the owner; with teleport off it does not.
- [ ] GameTest: a guarding unit with range 8 does not leave its guard area to chase a zombie 12 blocks away, and does attack one 5 blocks away.
- [ ] GameTest: halt horn stops matching units and drops their targets; ready resumes following; a unit with a different squadron ignores both; a guarding unit ignores both.
- [ ] GameTest: a unit attacks a mob that hurt its owner, and the mob its owner hit; it never targets its owner, even with the owner at hostile alignment for a tick before desertion.
- [ ] GameTest: unit XP from kills: an enemy-faction NPC kill gives 1 XP, a zombie and a neutral NPC give 0; 3 XP makes level 2 with +1 max health; the level formula matches the threshold table for L2..L40.
- [ ] GameTest: a unit kill changes no alignment and fires `FactionKillEvent` with the hire flag (integration with core).
- [ ] GameTest: a dead unit drops the helmet the player gave it and no coins; the owner gets one death message, none with the toggle off.
- [ ] GameTest: player-given gear replaces the original, and taking it back restores the original piece.
- [ ] Playtest: earn coins from NPC drops, buy food from a Shire innkeeper, sell crops to a farmer until an offer sells out; break and combine coins.
- [ ] Playtest: at +50 Shire hire three Bounders from the Shirriff, walk, sprint and ride a boat; they keep up or teleport; set one to guard a gate; halt and ready them with a horn; order them onto a target with the command sword; watch one level up.

## Open questions

1. Payload reach: core's convention validates c2s handlers at 8 blocks; Legacy trade screens stay open to 12. This spec validates menu-bound payloads by the menu's 12-block check. Opus 1 to confirm.
2. Ownership of traders: `docs/plan.md` lists traders under Progression; the maintainer folded trading into this spec. Trade code lives in the npc package and Progression consumes `NpcTradeEvent`. Opus 4 to confirm.
3. Coin denomination ids: `silver_coin_10` / `silver_coin_100` (proposed) or a single item with a value component. Separate items keep stacking, tags and recipes simple.
4. Command horn and sword are npc-registered items like the spawn eggs; Core owns items in general. Opus 1 to confirm or take them into core.

## Tickets

G2 (milestone 2), in dependency order:

1. #66 core: silver coin denominations (10, 100) and coin API (requested by npc; Opus 1 assigns)
2. #67 npc: trader offer pools, stock rolling and restock (sonnet-7)
3. #68 npc: unit lists, hire cost formula, hired-unit state and desertion (sonnet-8)
4. #69 npc: trade screen with buy, sell and coin exchange tabs (sonnet-7; blocked by #66, #67)
5. #70 npc: hired-unit follow, guard, halt and teleport AI with owner-aware targeting (sonnet-6; blocked by #68, #42)
6. #71 npc: unit XP, levels, kill count, unit speech and death (sonnet-8; blocked by #68, #42)
7. #72 npc: hiring purchase flow and Units tab, Shirriff hires Bounders at +50 (sonnet-8; blocked by #66, #68, #69)
8. #73 npc: unit command screen: guard, teleport, gear, dismiss (sonnet-6; blocked by #70, #71)
9. #74 npc: command horn, command sword and squadrons (sonnet-7; blocked by #70)

Parallel waves: #66, #67, #68 now; then #69, #70, #71; then #72, #73, #74. #67 and #68 each add one line to `npc/datagen/NpcDataGen.java` (trivial rebase); no other file is shared within a wave.

Later (not opened): mounted unit entries and Shire ponies (mount spec), farmhands, `bree_captain` unit list, fast travel with units, conquest horn.
