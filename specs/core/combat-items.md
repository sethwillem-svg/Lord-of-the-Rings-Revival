# Combat items: weapons, armour, metals and crafting tables

- **Pod:** core
- **Lead:** Opus 1
- **Status:** approved (maintainer pre-approved "match Legacy" for this system)
- **Depends on:** `specs/core/core-apis.md` (registers, faction keys, alignment API, tags),
  `specs/core/brewing-barrel.md` (mithril nugget and tin for later vessels)
- **Sources:** behavior notes `docs/reference/legacy/combat-trade.md` sections 1-2, `biomes.md`
  (Ores), `brewing.md` (poisoned drinks), plus the public wiki's description of crafting-table,
  bronze, forge and poison behavior. Numbers marked (L) follow Legacy; (ours) marks our own design
  where Legacy is unknown or does not fit 1.21. All item names beyond Tolkien proper names are ours,
  and all art is new.

## Summary

Every weapon in Middle-earth is a **material** (bronze, iron, Gondor steel, orc steel, mithril...)
combined with a **weapon class** (dagger, sword, spear, pike, battleaxe, hammer, lance, throwing axe).
The class sets reach, swing speed and a damage offset; the material sets base damage, durability and
repair item. Armour works the same way: a set has one protection fraction that fixes all four
pieces. Faction gear is made on **faction crafting tables** that only open for players with positive
alignment, so a player's standing decides which smiths' work they can make. This spec adds the first
metals (tin, bronze, silver, Morgul iron and orc steel, with mithril registered for later), two
forges, the gear for the Shire, Bree, the Rangers, Gondor and Mordor, faction bows, slings,
crossbows, thrown spears and axes, and poisoned daggers. Because the full game has hundreds of
variants, everything is generated from a small descriptor table rather than written by hand.

## Player-facing behavior

1. **Early metals.** Tin ore and silver ore appear underground alongside vanilla copper (placement is
   World's). Copper plus tin crafts into bronze on any crafting table (1 + 1 = 1), or alloys in a
   forge (1 + 1 = 2). Bronze tools mine like iron; bronze weapons are slightly weaker than iron.
2. **Weapon classes feel different.** A dagger swings fast and short; a spear reaches 1.5 blocks
   further and can be thrown; a pike reaches 3 blocks further but swings slowly; battleaxes and
   hammers hit hard and slow, hammers knock back further; a lance is for horseback and slows a
   walking wielder. Tooltips show the vanilla attribute lines (damage, speed, reach).
3. **Faction crafting tables.** Each culture has its own crafting table. Right-clicking one with
   alignment of at least +1 with its faction opens a 3x3 crafting screen that makes that faction's
   gear (and ordinary recipes). Below +1 the table puffs smoke, plays a short sound and shows
   "The <faction> craftsmen will not share their work with you" on the action bar.
4. **Getting a table.** Tables are found in that culture's buildings (World), bought from its smiths
   and similar traders for about 100 coins (NPC/Progression), or crafted on an existing table of the
   same kind from 2 planks above 2 of the culture's building blocks. There is no recipe on the
   vanilla table (L).
5. **Forges.** The alloy forge (any player, vanilla table) and the orc forge (Mordor table) smelt
   ores four at a time on one fuel supply and make alloys: bronze in either, orc steel from Morgul
   iron only in the orc forge. Forges refuse food.
6. **Armour.** Each faction set has its own look and protection value; Gondor and Mordor sets match
   iron, Ranger sets are a little lighter, Shire and levy sets are light padded gear.
7. **Ranged.** Faction bows draw at different speeds and add a damage multiplier to arrows. Hobbits
   use slings that throw pebbles. Bronze and iron crossbows fire bolts slowly but hard. Spears are
   charged and thrown like tridents; throwing axes are thrown instantly and picked back up.
8. **Poison.** Combining any dagger with a bottle of poison in a crafting grid makes it a poisoned
   dagger (same item, same durability and name, a "Poisoned" tooltip line). Its hits poison the
   target for a few seconds.

## Rules and numbers

### Weapon classes

Damage is total damage per full-strength hit in half-hearts, as shown in the tooltip (player base 1
plus the item's modifier). Speed is the 1.21 attack-speed value (attacks per second). Reach is a
modifier on the player's entity interaction range (base 3.0 blocks); block reach is unchanged.

| Class | Damage vs material's sword | Speed factor | Attack speed | Reach | Knockback | Special |
| --- | --- | --- | --- | --- | --- | --- |
| Sword | 0 | x1.0 | 1.6 | +0 | 0 | Sweeps like a vanilla sword (only class that sweeps) |
| Dagger | -3 (L) | x1.5 (L) | 2.4 | -0.75 (x0.75) (L) | 0 | Poisonable |
| Spear | -1 (L) | x0.833 (L) | 1.33 | +1.5 (x1.5) (L) | 0 | Throwable (below) |
| Polearm | 0 (L) | x0.667 (L) | 1.07 | +1.5 (L) | 0 | Reserved class for later sets (glaives, halberds) |
| Long spear / pike | 0 (L) | x0.5 (L) | 0.8 | +3.0 (x2.0) (L) | 0 | |
| Lance | 0 (L) | x0.5 (L) | 0.8 | +3.0 (L) | +1 (L) | Main hand while not riding: -20% movement speed (L) |
| Battleaxe | +2 (L) | x0.75 (L) | 1.2 | +0 | 0 | Is also an axe: mines `mineable/axe` at the material's tier speed, strips logs, disables shields |
| Hammer | +2 (L) | x0.667 (L) | 1.07 | +0 | +1 (L) | |
| Throwing axe | -2 melee (ours) | x1.0 | 1.6 | +0 | 0 | Thrown damage = 50% of the material's sword damage (L) |

- **Speed anchor (ours).** Legacy's own player cooldown is 15 ticks for a sword. We anchor the sword
  at vanilla's 1.6 per second so our swords match vanilla swords, and apply Legacy's factors to it.
- **NPCs** use the same items. Their melee cooldown is 20 ticks / speed factor and their attack reach
  is multiplied by the reach factor (x0.75 to x2.0), read through `WeaponStats` (Data and APIs).
- **Not ported:** Legacy swords block like 1.7 swords; 1.21 has no sword blocking and we do not add it.

### Materials

| Material id | Sword damage | Durability | Tool tier | Enchantability | Repair item | Used by |
| --- | --- | --- | --- | --- | --- | --- |
| `bronze` | 5.5 (L) | 230 (L) | iron (L) | 14 | bronze ingot | generic, Shire, Bree |
| `iron` | 6 (L) | 250 (L) | iron | 14 | iron ingot | generic, Bree guards |
| `ranger` | 6.5 (L) | 400 | iron | 14 | iron ingot | Rangers (`dunedain_north`) |
| `gondor` | 6.5 (L) | 450 | iron | 14 | iron ingot | Gondor |
| `orc_steel` | 6.5 (L) | 350 (L) | iron | 14 | orc steel ingot | Mordor (later Angmar, Dol Guldur) |
| `mithril` | 9 (L) | 2400 (L) | diamond (ours) | 20 | mithril ingot | registered now, gear in a later spec |

Durabilities inside Legacy's per-tier ranges (300-450 for men and orcs) are our picks. Every class of
a material shares its durability. Later materials (elven 7 / 500-700, dwarven 7 / 650-700, Uruk 7,
Utumno 7.5) are rows added by later specs.

Worked damage examples: bronze dagger 2.5, bronze sword 5.5, iron pike 6, iron battleaxe 8, Gondor
spear 5.5, Gondor hammer 8.5, orc steel dagger 3.5, mithril sword 9.

### Weapon sets in this spec

| Set (item id prefix) | Material | Classes | Crafted on |
| --- | --- | --- | --- |
| `bronze` | bronze | dagger, sword, spear, battleaxe, throwing axe; pickaxe, axe, shovel, hoe | vanilla table |
| `iron` | iron | dagger, spear, pike, battleaxe, hammer, throwing axe (vanilla iron sword is the sword) | vanilla table |
| `ranger` | ranger | dagger, sword, spear, battleaxe | ranger table |
| `gondor` | gondor | dagger, sword, spear, pike, battleaxe, hammer, lance | Gondor table |
| `mordor` | orc_steel | dagger, sword, spear, pike, battleaxe, hammer | Mordor table |

Item ids are `<set>_<class>` (`gondor_pike`, `mordor_dagger`, `iron_throwing_axe`). English names are
"<Set adjective> <Class>" ("Gondorian Pike", "Orcish Dagger", "Bronze Throwing Axe").

### Weapon recipes (ours; one shape per class)

`M` = the set's metal (bronze ingot, iron ingot, or orc steel ingot), `S` = stick. All yield 1.

| Class | Shape (rows) |
| --- | --- |
| Dagger | `M`, `S` |
| Sword | `M`, `M`, `S` |
| Spear | ` M`, ` S`, `S ` (every set; see note) |
| Pike | `  M`, ` S `, `S  ` |
| Lance | `  M`, ` M `, `S  ` |
| Battleaxe | `MMM`, `MSM`, ` S ` |
| Hammer | `MSM`, `MSM`, ` S ` |
| Throwing axe | ` M`, `SM` |
| Bronze tools | vanilla tool shapes |

Spear note (#168): a straight `M`, `S`, `S` column is the vanilla shovel grid, so on the vanilla table
the bronze and iron spears would collide with the bronze and vanilla iron shovels. Legacy angles its
spear recipe (head at the top, shaft slanting down); our behaviour notes do not record the exact
grid, and the full 3x3 diagonal is already the pike. The spear therefore uses the 2-wide slant
` M`, ` S`, `S ` on every table, faction tables included, so one class keeps one shape. Shaped
recipes also match mirrored, so `M `, `S `, ` S` gives the spear too. Vanilla shovel grids are
unchanged and still give shovels.

Ranger and Gondor gear use iron ingots, as most Legacy faction gear does (L).

### Armour

Points per piece from protection fraction `p` (L): helmet = round(25·p·0.14), chest =
round(25·p·0.40), legs = round(25·p·0.32), boots = round(25·p·0.14). Durability multiplier (L) =
round(material uses x 0.06), applied to vanilla's per-slot base (11/16/15/13). Toughness 0 and
knockback resistance 0 for every set (Legacy has neither).

| Set (item id prefix) | Faction tag `armor/<faction>` | p | Points H/C/L/B (total) | Uses | Durability mult. | Material key | Crafted on |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `bronze` | none (generic) | 0.5 (L) | 2/5/4/2 (13) | 230 | 14 | bronze ingot | vanilla table |
| `shire` | hobbit | 0.4 (ours) | 1/4/3/1 (9) | 200 | 12 | leather | hobbit table |
| `bree` | bree | 0.5 (ours) | 2/5/4/2 (13) | 250 | 15 | iron ingot | Bree table |
| `ranger` | dunedain_north | 0.48 (L) | 2/5/4/2 (13) | 350 | 21 | leather | ranger table |
| `arnor` | dunedain_north | 0.6 (L) | 2/6/5/2 (15) | 450 | 27 | iron ingot | ranger table |
| `gondor` | gondor | 0.6 (L) | 2/6/5/2 (15) | 450 | 27 | iron ingot | Gondor table |
| `gondor_levy` | gondor | 0.4 (L, gambeson tier) | 1/4/3/1 (9) | 200 | 12 | white wool | Gondor table |
| `mordor` | mordor | 0.6 (L) | 2/6/5/2 (15) | 350 | 21 | orc steel ingot | Mordor table |
| `mithril` (later) | none | 0.8 (L) | 3/8/6/3 (20) | 2400 | 144 | mithril ingot | later spec |

- Legacy has no Shire or Bree armour; the maintainer asked for both, so these two sets are ours
  (light padded Bounder kit; Bree guard leather-and-iron).
- Pieces use vanilla armour shapes with the material key. Ids `<set>_helmet`, `_chestplate`,
  `_leggings`, `_boots`. Enchantability: 9 for metal sets, 15 for leather and wool sets.
- Weak orcs wearing armour at 75% value and helmet variants (winged, captain) are NPC/later work.
- Robes and other 0-armour clothing are out of scope.

### Ranged weapons

| Weapon | Arrow damage factor | Full draw (ticks) | Durability | Crafted on | Notes |
| --- | --- | --- | --- | --- | --- |
| `ranger_bow` | x1.0 (L) | 20 (L) | 384 | ranger table | vanilla bow shape |
| `gondor_bow` | x1.125 (L) | 20 (L) | 384 | Gondor table | vanilla bow shape |
| `mordor_bow` | x1.125 (L) | 20 (L) | 384 | Mordor table | vanilla bow shape |
| `bronze_crossbow` | x1.95 | 50 (L) | 230 | vanilla table | fires bolts only |
| `iron_crossbow` | x2.0 | 50 (L) | 250 | vanilla table | fires bolts only |
| `sling` | pebble 2.0 at full wind-up (ours) | 10 (ours) | 64 (ours) | hobbit table | fires `pebble` |

- **Bows:** behave like the vanilla bow (arrows, enchantments, infinity) except draw time, and the
  shot arrow's base damage is multiplied by the factor. A shorter draw reaches full power sooner.
- **Crossbows (L):** factor = (1 + 0.1 x (material damage - 2)) x 2, where material damage is sword
  damage - 4 (bronze 1.5, iron 2, mithril 5 gives x2.6 later). They charge like the vanilla crossbow
  but take 50 ticks, fire one `bolt` (base damage 2.0 x factor, no gravity difference from arrows),
  and accept vanilla crossbow enchantments (Quick Charge shortens by 5 ticks per level, ours).
  Bolts: 1 metal ingot (iron or bronze) + 1 stick + 1 feather, shapeless, yields 4, stack 64. Bolts
  are picked up like arrows. Poisoned bolts are later.
- **Sling (ours numbers):** hold to wind up (minimum 4 ticks), release to throw one pebble; damage
  and speed scale with wind-up up to 10 ticks. Pebble: 2.0 damage, velocity 1.5, breaks on impact
  (no pickup). `pebble` from 1 gravel -> 4 pebbles (vanilla table), stack 64. Sling shape: `S S`,
  ` L `, ` T ` with stick, leather, string.

### Thrown weapons

| Thing | Value | Notes |
| --- | --- | --- |
| Spear throw charge | up to 20 ticks (L); release under 5 ticks does nothing | Like a trident |
| Spear thrown damage | 70% of melee damage x (charge / 20) (L) | |
| Full-charge spear | critical (x1.5, crit particles) (L) | |
| Spear after throw | loses 1 durability, lands as a pickup only the thrower can collect for 20 s, then anyone (ours); creative throws keep the item | Loyalty and Riptide do not apply |
| Pikes, lances, polearms | not throwable | |
| Throwing axe | thrown instantly on use, 10-tick cooldown (ours); damage 50% of material sword damage (L): bronze 2.75, iron 3.0 | |
| Throwing axe item | stack 16, no durability (L "ammo-like"); always drops as a pickup (ours) | |

### Metals, ores and smelting

| Item / block | How obtained | Smelts / crafts into |
| --- | --- | --- |
| Copper | vanilla | |
| `tin_ore`, `deepslate_tin_ore` | World places (L: every biome) | drops `raw_tin` (fortune like copper, 1 base) |
| `raw_tin` -> `tin_ingot` | furnace, blast furnace, any forge; 0.7 xp | 9 `tin_nugget` <-> ingot; 9 ingots <-> `tin_block`; 9 raw <-> `raw_tin_block` |
| `bronze_ingot` | 1 copper ingot + 1 tin ingot shapeless = 1 (L); any forge: copper + tin (raw or ingot, either slot) = 2 (L) | nuggets and `bronze_block` |
| `silver_ore`, `deepslate_silver_ore` | World places (L: every biome, y 0-32) | `raw_silver` -> `silver_ingot` (1.0 xp), nuggets, `silver_block`, `raw_silver_block` |
| `morgul_iron_ore`, `deepslate_morgul_iron_ore` | World places (L: Mordor, Angmar, Dol Guldur) | drops `raw_morgul_iron`; smelts **only** in the orc forge to `orc_steel_ingot` (L), 0.7 xp |
| `orc_steel_ingot` | orc forge | nuggets, `orc_steel_block` |
| `mithril_ore`, `deepslate_mithril_ore` | World places (L: Misty Mountains only, rare) | `raw_mithril` -> `mithril_ingot` in furnace, blast furnace or any forge, 2.0 xp; `mithril_nugget`, `mithril_block` |

- Hardness and tool: tin like copper ore (stone pickaxe); Morgul iron like iron ore (stone); silver
  like gold ore (iron); mithril hardness 5, needs diamond (ours).
- Tags: `c:ores/<m>`, `c:raw_materials/<m>`, `c:ingots/<m>`, `c:nuggets/<m>`, `c:storage_blocks/<m>`
  for tin, silver, bronze, orc_steel, mithril, morgul_iron; mirrors `lotrrevival:ores/<m>`; ore
  blocks in `minecraft:mineable/pickaxe` and the right `needs_*_tool` tag. Metal blocks are beacon
  bases.
- Ore placement numbers for World (L, from `biomes.md`): copper (vein 8, 16 per chunk, y 0-128),
  tin (8, 16, y 0-128), silver (8, 3, y 0-32), Morgul iron (vein 8, 20 per chunk in Mordor, Angmar,
  Dol Guldur, which have no ordinary iron), mithril (vein 6, 0.25 per chunk, y 0-16, Misty Mountains
  only). Heights are Legacy's 0-128 world; World rescales them to our dimension's height.

### Forges

| Thing | Value | Notes |
| --- | --- | --- |
| Blocks | `alloy_forge`, `orc_forge` | Dwarven and elven forges are later rows |
| Layout | 4 lanes, each an input slot with an alloy slot above it, 4 output slots, 1 fuel slot | |
| Time | 200 ticks per item per lane (ours); lanes run in parallel | |
| Fuel | burns while at least one lane is progressing; one fuel item serves all four lanes (ours) | This is the "more efficient than a furnace" of Legacy |
| Accepts | any `lotrrevival:forging` recipe for that forge, plus vanilla `smelting` recipes whose input is not edible (L: no food) | |
| Forging recipes | copper + tin (raw or ingot, either order) -> 2 bronze in any forge (L); `raw_morgul_iron` -> 1 orc steel in the orc forge only (L) | later: Uruk steel, dwarven steel... |
| Alignment | none (L) | |
| Recipes | alloy forge: 8 stone bricks in a ring, vanilla table (L); orc forge: 8 polished blackstone bricks in a ring on the Mordor table (stand-in for Mordor bricks) | |
| Automation | hopper above fills input slots, sides fill fuel, below pulls outputs (ours, furnace-like) | |
| Block | facing, lit state with light 13 while burning, drops contents when broken | |

### Faction crafting tables

| Thing | Value | Notes |
| --- | --- | --- |
| Tables in this spec | `hobbit_crafting_table`, `bree_crafting_table`, `ranger_crafting_table`, `gondor_crafting_table`, `mordor_crafting_table` | one per culture; about 30 more later, one per culture family |
| Faction | hobbit, bree, dunedain_north, gondor, mordor | |
| Use requirement | alignment with the table's faction >= +1.0 (L) | creative and spectator players bypass (ours) |
| Refusal | smoke puff particles at the block, a short fizz sound, action-bar message; screen does not open | |
| Losing alignment while open | menu re-checks every 20 ticks and closes if the requirement fails (ours) | |
| Grid | 3x3 plus result, like the vanilla crafting table; no recipe book (ours) | |
| What it crafts | its own `lotrrevival:faction_crafting` recipes first, then any vanilla crafting recipe (ours) | |
| Table recipe (on its own table, L) | 2 planks (any) above 2 culture blocks, yields 1 | hobbit: wool (L); Bree: bricks (ours); ranger: `arnor_brick` (ours); Gondor: chiseled stone bricks (stand-in until Gondor stone exists); Mordor: polished blackstone bricks (stand-in until Mordor bricks exist) |
| Trade price | about 100 coins (L, pool price, NPC variance 75-125) | NPC trader pools |
| Block | wood-like, hardness 2.5, axe, drops itself, member of `lotrrevival:crafting_tables` | |
| First craft | fires `FactionTableCraftEvent` when a player takes a result | Progression's "<culture> crafter" achievement |

Recipes may name one table or a table group tag (`#lotrrevival:crafting_tables/<group>`, for example a
later `orc` group shared by Mordor, Angmar and Dol Guldur tables).

### Poison

| Thing | Value | Notes |
| --- | --- | --- |
| `bottle_of_poison` | stack 1, not drinkable yet (poisoned drinks are a brewing follow-up) | |
| Obtaining (stand-in) | glass bottle + spider eye + poisonous potato, shapeless | Legacy: squeezing a poisonous wild berry (World plant) or milking venomous spiders and scorpions (NPC creatures); add those when they exist |
| Poisoning | any item in `#lotrrevival:poisonable` without the mark + 1 bottle of poison, shapeless, any crafting grid (L) | result keeps durability, name, enchantments and other components; the bottle comes back as an empty glass bottle |
| Mark | data component `lotrrevival:poisoned` (unit) | tooltip line "Poisoned" in dark green; permanent (L) |
| `#lotrrevival:poisonable` | all daggers in this spec | later: bolts, arrows, darts |
| On hit | melee hit by any living attacker holding a poisoned item in main hand that deals damage applies Poison I for a random 60-140 ticks (L, 3-7 s on Normal), x0.5 on Easy, x1.5 on Hard (ours scaling) | entities in `#lotrrevival:poison_immune` (NPC fills, for example elves) are skipped; vanilla undead are immune anyway |

## Edge cases

- **Multiplayer:** the table's alignment check runs on the server; the client mirror is used only
  to avoid opening a screen that would be refused. Forges and tables are normal shared blocks.
- **Death, logout, dimension change:** nothing is stored on the player. Thrown spears in unloaded
  chunks stay until picked up (persistent like tridents).
- **Datapack removes a table's faction:** the table refuses everyone except creative players and
  logs one warning.
- **Faction recipe vs vanilla recipe with the same grid:** the faction recipe wins on a faction
  table; on a vanilla table only vanilla recipes exist.
- **Our vanilla-table recipe vs a vanilla recipe:** never share a grid. A clash makes the result
  depend on recipe load order (the reason the spear is a slant, not a shovel column; #168).
- **Shift-click crafting** on a faction table crafts as many as the inputs allow, re-checking
  alignment once per click.
- **Poisoning an already poisoned dagger:** no recipe match. Poisoned daggers stack only with
  identical poisoned daggers (stack size is 1 anyway).
- **Thrown spear hits a friendly NPC:** the NPC pod's friendly-fire protection applies as for any
  player projectile.
- **Lance slowdown:** applies only to players and NPCs not riding; removed instantly when switching
  items or mounting.
- **Repair:** anvil with the material's repair item; two of the same item combine as vanilla.
- **Interaction with other systems:** NPC loadouts reference these items by id; Progression listens
  to `FactionTableCraftEvent` and checks armour sets through `armor/<faction>` tags; traders sell
  tables and gear (NPC pod pools).

## Data and APIs

### Core package layout (one holder per ticket, so tickets merge independently)

| Package / class | Contents | Ticket |
| --- | --- | --- |
| `core.combat.WeaponClass` | enum: damage offset, speed factor, reach, knockback, tag, recipe shape | foundation |
| `core.combat.CombatMaterial` | record + constants table above (damage, durability, tier, enchantability, repair tag) | foundation |
| `core.combat.ArmorSet` | record: id, p, uses, enchantability, repair tag, faction tag, armour-material holder | foundation |
| `core.combat.Gear` | factory: `weapon(set, material, class, recipe)`, `armor(set, pieces, recipe)`, `bow(...)`, `tool(...)`; registers the item through `ModRegistries.ITEMS` and records a `GearEntry` | foundation |
| `core.combat.GearCatalog` | immutable list of every `GearEntry` (id, item, kind, model kind, English name, tags, recipe spec) | foundation |
| `core.combat.WeaponStats` | `Optional<WeaponProfile> of(ItemStack)`: class, material, damage, speed factor, reach factor, knockback; for NPC AI | foundation |
| `core.material.MaterialItems`, `MaterialBlocks` | ingots, nuggets, raw items, ores, storage blocks | metals |
| `core.forge.*` | forge block, block entity, menu, `forging` recipe type | forges |
| `core.table.*` | `FactionTableBlock`, menu, `faction_crafting` recipe types, `FactionTableCraftEvent` (in `core.event`) | tables |
| `core.combat.gear.GeneralGear`, `ShireBreeGear`, `RangerGear`, `GondorGear`, `MordorGear`, `RangedGear` | the `Gear.*` calls for each set (stubs created by the foundation ticket) | one per set ticket |
| `core.combat.poison.*` | bottle, component, special recipe, hit listener | poison |
| `core.combat.throwing.*` | spear and axe projectiles, sling pebble | throwing / ranged |

### Datagen for hundreds of variants

- Every gear item is created through `Gear`, which records a `GearEntry`. Generic providers in
  `datagen/combat/` iterate `GearCatalog` and write everything: item models (handheld for weapons,
  wide handheld for pikes and lances, generated for armour, pulling models for bows and crossbows),
  English lang (via `ModLangProvider.contribute`), item tags, and recipes (shape from the class or
  armour piece, key from the set's material, recipe type from the table field: vanilla `crafting_shaped`
  or `lotrrevival:faction_shaped`). Adding a new faction set is one holder class with a few `Gear`
  calls and textures; no provider edits.
- `DataGenerators` gets one call, `CombatDatagen.gather(event, lookup)`, added once by the foundation
  ticket. Metals, forges and tables have their own provider classes registered in one marked block
  each.
- Tags per entry: `lotrrevival:weapons/<class>` (adds `swords`, `polearms`, `lances`, `bows`,
  `crossbows`, `slings`, and item tag `bolts`), `lotrrevival:armor/<faction>`, vanilla
  `minecraft:swords` and `minecraft:axes` (battleaxes), `minecraft:{head,chest,leg,foot}_armor`, and
  the matching `minecraft:enchantable/*` tags (daggers, spears, pikes, lances and hammers get
  `weapon`, `sharp_weapon`, `fire_aspect`, `durability`, not `sword` so no Sweeping Edge).
- Textures: placeholder 16x16 sprites generated by a script from our own per-class templates
  recoloured with a per-material palette; armour layers likewise. Final art through the art agents
  (`docs/tooling.md`). Never derived from the original.
- Recipe JSON for faction tables:

```json
{
  "type": "lotrrevival:faction_shaped",
  "table": "lotrrevival:gondor_crafting_table",
  "pattern": ["M", "M", "S"],
  "key": { "M": { "tag": "c:ingots/iron" }, "S": { "item": "minecraft:stick" } },
  "result": { "id": "lotrrevival:gondor_sword", "count": 1 }
}
```

`table` is a block id or `#` block tag. `lotrrevival:faction_shapeless` has `ingredients` instead.

- Forging recipe JSON: `{ "type": "lotrrevival:forging", "forges": "#lotrrevival:forges/any" |
  block id, "input": ingredient, "alloy": optional ingredient, "result": stack, "experience": f,
  "cooking_time": 200 }`. Block tags `lotrrevival:forges/any`.

### Registries and components

- Uses existing registers only: `ITEMS`, `BLOCKS`, `BLOCK_ENTITY_TYPES`, `MENU_TYPES`,
  `ENTITY_TYPES` (thrown spear, thrown axe, pebble, bolt), `ARMOR_MATERIALS`, `RECIPE_TYPES`,
  `RECIPE_SERIALIZERS`, `DATA_COMPONENTS` (`poisoned`), `CREATIVE_TABS`. No `ModRegistries` edits.
- Creative tabs: `lotrrevival:combat` (all gear, catalog order) and `lotrrevival:materials` (metals,
  ores, forges, tables, poison).
- Tags (keys in `core.tag.ModTags`): item `weapons/*` above, `bolts`, `pebbles`; block
  `crafting_tables/<group>`, `forges/any`, `ores/<m>`; entity type `poison_immune`.
- Attribute modifiers use ids `lotrrevival:weapon_damage`, `weapon_speed`, `weapon_reach`,
  `weapon_knockback`, `lance_slow`.
- Event `core.event.FactionTableCraftEvent` (game bus, server): player, table block, faction key,
  result stack. Not cancellable.

## Out of scope

- Weapon modifiers and reforging (Legacy's enchant-like reach/speed/knockback/bane modifiers),
  unsmelting, engraving: a later `specs/core/modifiers.md`.
- Mount armour, the command sword and horn: mount and hiring specs.
- Faction tools (Gondor pickaxes and the like), dwarven mattocks, elven, dwarven, Rohan, Dale,
  Harad and other cultures' gear and tables, mithril gear: later rows and tickets.
- Poisoned drinks, arrows, bolts and darts; drinking a bottle of poison.
- Fire pots, blowguns, orc bombs.
- Sword blocking.
- Ore placement and structure placement of tables and forges (World), NPC loadouts, AI weapon use,
  trader pools selling tables and gear (NPC).

## Acceptance tests

- [ ] GameTest: every catalog weapon's damage, attack speed and reach modifiers equal the class and
      material tables (bronze dagger 2.5 / 2.4 / -0.75; Gondor pike 6.5 / 0.8 / +3.0; iron hammer
      8 / 1.07 / knockback +1).
- [ ] GameTest: `WeaponStats.of(mordor_spear)` reports spear, orc_steel, speed factor 0.833, reach
      factor 1.5; a vanilla stick returns empty.
- [ ] GameTest: armour points for every set match the H/C/L/B table; durability = base x multiplier.
- [ ] GameTest: copper + tin crafts 1 bronze; forge with copper ingot over tin ingot gives 2 bronze;
      raw Morgul iron in the alloy forge does nothing; in the orc forge gives orc steel; a furnace
      rejects raw Morgul iron; a forge rejects bread.
- [ ] GameTest: player with Gondor +0.5 is refused by the Gondor table; at +1.0 the menu opens and
      the Gondor sword recipe matches; the same grid on a vanilla table gives nothing.
- [ ] GameTest: a Mordor-table recipe does not match on the Gondor table; a group-tag recipe matches
      on every table in the group.
- [ ] GameTest: poisoning keeps damage value and custom name and returns a glass bottle; a poisoned
      dagger hit applies Poison 60-140 ticks on Normal; an unpoisoned one does not.
- [ ] GameTest: spear thrown at full charge deals 70% of melee x1.5 (crit) and loses 1 durability;
      a throwing axe lands as a pickup.
- [ ] GameTest: Gondor bow arrow damage = vanilla arrow damage x1.125 at full draw; iron crossbow
      bolt base damage 4.0.
- [ ] GameTest: lance in main hand on foot applies -20% speed; removed when mounted or switched.
- [ ] GameTest (#168): on the vanilla table, `M`, `S`, `S` with an iron ingot gives the vanilla iron
      shovel and with a bronze ingot the bronze shovel; the spear slant (and its mirror) gives the
      iron or bronze spear.
- [ ] Playtest: craft and use each class; reach and speed differences are noticeable; tables refuse
      with smoke at 0 alignment; forges run 4 lanes on one fuel; textures read as their culture.

## Tickets

All `pod:core`, `type:ticket`, `phase:3`, milestone G3. Each gear ticket fills its own pre-created
holder class and adds no provider code, so they merge independently.

| # | Ticket | Agent | Blocked by |
| --- | --- | --- | --- |
| #107 | Combat foundation (weapon classes, materials, gear catalog, generic gear datagen, holder stubs) | sonnet-1 | - |
| #108 | Metals and ores (tin, silver, bronze, Morgul iron, orc steel, mithril) | sonnet-2 | - |
| #109 | Faction crafting tables (5 tables, alignment gate, faction recipe types, craft event) | sonnet-1 | - |
| #110 | Bronze and iron gear (generic weapon sets, bronze tools and armour) | sonnet-2 | #107, #108 |
| #112 | Shire and Bree armour sets | sonnet-1 | #107, #109 |
| #114 | Ranger gear (weapons, Ranger and Arnor armour) | sonnet-2 | #107, #109 |
| #116 | Gondor gear (weapons incl. lance, Gondor and levy armour) | sonnet-1 | #107, #109 |
| #118 | Mordor gear (orc steel weapons and armour) | sonnet-2 | #107, #108, #109 |
| #120 | Faction bows and hobbit sling | sonnet-2 | #107, #109 |
| #121 | Bronze and iron crossbows and bolts | sonnet-1 | #107, #108 |
| #123 | Alloy forge and orc forge | sonnet-2 | #107, #108, #109 |
| #124 | Thrown spears and throwing axes | sonnet-1 | #110 |
| #125 | Bottle of poison and poisoned daggers | sonnet-2 | #110 |

Parallel now: #107, #108, #109. After those: #110, #112, #114, #116, #118, #120, #121, #123 in any
order; then #124 and #125.

Follow-ups in other pods: #126 `pod:world` ore placement (after #108); #127 `pod:world` tables and
forges in structures (after #109, #123); #128 `pod:npc` loadout swap, `WeaponStats` use, trader
pools, `poison_immune` (after #107, #110, #112).

## Open questions

1. Speed anchor: swords at vanilla 1.6/s (ours) rather than Legacy's 15-tick cooldown (1.33/s). Keep?
2. Faction tables also craft vanilla recipes (ours, unknown in Legacy). Keep?
3. First table of a culture only from structures or traders (Legacy). A world without that culture's
   settlements yet has no survival way in; acceptable until World places them (#127)?
