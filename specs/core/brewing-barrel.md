# Brewing barrel (pilot system)

- **Pod:** core
- **Lead:** Opus 1
- **Status:** draft
- **Depends on:** nothing beyond vanilla and `core.registry.ModRegistries`. No factions, alignment or networking APIs. Issue #5.

## Summary

A wooden barrel that turns crops and water into drinks over time. The player loads ingredients and a water bucket, waits while it ferments, then lets it age for a stronger brew or taps it early. Each drink is served in a wooden mug, carries a quality tier from Weak to Potent, and gives a buff whose length grows with quality. This is the pilot system that measures how fast one implementer can ship a self-contained feature end to end: block, block entity, menu, custom recipe type, data component, datagen and a GameTest.

## Player-facing behavior

1. **Craft** a Brewing Barrel and some Wooden Mugs (recipes below). Place the barrel; its front faces the player.
2. **Open** it with right-click (any item in hand except an empty mug while drinks are ready, see step 6). The screen has 6 ingredient slots (2 rows of 3), 1 water slot, a progress arrow, 5 quality pips and a status line, above the player inventory.
3. **Load** ingredients (any slots, stacks allowed) and a Water Bucket in the water slot. Shift-click routes a water bucket to the water slot and other items to the ingredient slots.
4. **Brewing starts automatically** on the next tick in which: the barrel is Empty, the water slot holds a water bucket, and the ingredient slots match a brewing recipe. On start the recipe's ingredient counts are removed from the slots (extras stay), and the water bucket becomes an empty bucket in the same slot. The status line reads "Brewing <drink>" and the arrow fills.
5. **Fermentation** runs for the recipe's brew time. While Brewing, Ready or Tapped, the ingredient slots and water slot accept no new items (the player can still take out leftovers and the empty bucket).
6. **Ready.** When fermentation ends the barrel holds the recipe's servings (usually 4) at quality Weak. It keeps **aging**: quality rises one tier per aging interval up to Potent. Pips light up per tier. The status line reads "Ready: <drink>, <quality> (<n> left)" and, below Potent, the time to the next tier as mm:ss.
7. **Tap.** Right-click the barrel holding an empty Wooden Mug to draw one serving: one mug leaves the hand, a filled mug of the drink at the current quality is given (into the hand if the stack was 1, else into the inventory, else dropped at the player). The first draw **taps** the barrel: aging stops and the quality is locked for the rest of the batch.
8. When the last serving is drawn the barrel returns to Empty and can start a new brew.
9. **Drink.** Hold use on a filled mug for 32 ticks (drink animation and sound). The player gains the drink's nutrition and effects, scaled by quality, and gets an empty Wooden Mug back. Drinks can be drunk on a full hunger bar.
10. **Tooltip** on a filled mug: a quality line ("Quality: Strong", colored per tier), then the effect lines in vanilla potion-tooltip style with the scaled duration, then, for alcoholic Strong and Potent drinks, a gray line "May cause nausea".

## Rules and numbers

### Blocks and items

| Thing | Value | Notes |
| --- | --- | --- |
| `brewing_barrel` block | hardness 2.5, blast resistance 2.5, wood sound, mineable with axe, no tool required | Block state: `facing` (4 horizontal values), set to the opposite of the player's horizontal look on placement. Not flammable. |
| Barrel recipe | shaped `PPP / IBI / PPP`: P = `#minecraft:planks`, I = iron ingot, B = `minecraft:barrel` | Yields 1. |
| `wooden_mug` item | stack 16 | Recipe shaped `P P / PNP`: P = `#minecraft:planks`, N = iron nugget. Yields 2. |
| Filled drink items | stack 1 | One item per drink (table below). Return 1 `wooden_mug` when drunk (none in creative). |
| Use time | 32 ticks | Drink animation. |
| Menu reach | 8 blocks | Menu closes beyond this or if the barrel is removed. |

### Brewing

| Thing | Value | Notes |
| --- | --- | --- |
| Ingredient slots | 6 | Pooled: a recipe is matched on item totals across all 6 slots. |
| Match rule | every non-empty ingredient slot holds an item accepted by some recipe ingredient, and each recipe ingredient's count is met by the total of matching items | An unrelated item in any slot blocks the match. Recipes must not have two ingredients that accept the same item (datagen asserts this). |
| Water | 1 `minecraft:water_bucket` per brew, becomes `minecraft:bucket` | Water bottles and other fluids are out of scope. |
| Start | automatic, server side, checked once per tick while Empty | No button, so no packet. |
| Progress | +1 per server tick while the chunk is loaded and ticking | No catch-up while unloaded (see Open questions). |
| Servings | recipe field, default 4 | |
| Aging interval | half the recipe's brew time | Tier = min(4, floor((elapsed - brewTime) / (brewTime / 2))) while untapped. |
| Interruptions | none other than removal | Ingredients are consumed at start, so nothing in the menu can interrupt a brew. |

Barrel states: **Empty** -> (recipe matched) -> **Brewing** -> (elapsed = brew time) -> **Ready** (aging) -> (first draw) -> **Tapped** -> (servings = 0) -> **Empty**.

### Quality tiers

Stored on every filled drink as the `lotrrevival:drink_quality` data component (int 0-4). A drink item without the component (creative tab, `/give`) behaves as Moderate.

| Tier | Id | Name color | Duration multiplier | Nausea (alcoholic only) |
| --- | --- | --- | --- | --- |
| 0 | `weak` | gray | x0.50 | none |
| 1 | `light` | white | x0.75 | none |
| 2 | `moderate` | green | x1.00 | none |
| 3 | `strong` | aqua | x1.50 | 25% chance, Nausea 200 ticks |
| 4 | `potent` | gold | x2.00 | 50% chance, Nausea 300 ticks |

Amplifiers do not change with quality. Durations are rounded down to whole ticks.

### Drinks (starter set)

Durations are the Moderate (x1.00) base. Nutrition and saturation modifier use vanilla food semantics.

| Item id | Name | Ingredients | Brew time (ticks) | Servings | Effect(s) at Moderate | Nutrition / sat. | Alcoholic |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `ale` | Ale | 3 wheat | 6000 (5 min) | 4 | Strength I, 1200 | 3 / 0.3 | yes |
| `cider` | Cider | 3 apple | 6000 | 4 | Speed I, 1200 | 3 / 0.3 | yes |
| `mead` | Mead | 3 honeycomb | 9000 (7.5 min) | 4 | Regeneration I, 200 | 4 / 0.4 | yes |
| `berry_wine` | Berry Wine | 3 sweet berries, 1 sugar | 12000 (10 min) | 4 | Resistance I, 900 | 2 / 0.2 | yes |
| `miruvor` | Miruvor | 1 golden apple, 2 glow berries, 1 honeycomb | 18000 (15 min) | 2 | Regeneration II, 200; Speed I, 1200 | 4 / 0.8 | no; also removes Nausea |

Worked timings: Ale is Weak at 5:00, Light at 7:30, Moderate at 10:00, Strong at 12:30, Potent at 15:00. Miruvor reaches Potent at 15 + 4 x 7.5 = 45 minutes.

## Edge cases

- **Multiplayer:** several players can have the menu open at once; all see the same synced state. Drawing a serving is resolved on the server in click order; a player whose click lands after the last serving gets the menu instead of a drink. Clients never decide state.
- **Breaking mid-brew or with servings left:** the barrel drops itself plus whatever is still in its 7 slots (leftover ingredients, empty or water bucket). The in-progress brew or remaining servings are **lost**. The same applies to explosions and `/setblock`. Creative-mode breaking drops nothing (vanilla behavior).
- **Save and reload:** slots, state, recipe id, elapsed ticks, servings and locked quality persist on the block entity. If the saved recipe id no longer exists (datapack removed it) the barrel resets to Empty, keeps its slot contents, and logs one warning.
- **Datapack recipes:** brewing recipes are ordinary datapack recipes, so packs can add, change or remove drinks. A recipe whose result is not a drink item is rejected at load with an error log.
- **Hoppers and pipes:** no automation in the pilot. The block entity must not implement vanilla `Container` (hoppers would see it) and registers no item-handler capability.
- **Comparators:** no signal in the pilot.
- **Death, logout, dimension change:** not relevant to the block. Drink effects are ordinary mob effects and follow vanilla rules (cleared by milk and death).
- **Drinking in creative:** effects apply, the filled mug is not consumed, no empty mug is given.
- **Miruvor and nausea:** Miruvor removes Nausea before applying its own effects; it never causes Nausea.
- **Other systems:** none. Later systems (traders, NPC taverns, alignment) may read the drink items and the quality component; nothing in this spec depends on them.

## Data and APIs

All registered through `core.registry.ModRegistries`. The pilot adds these registers there (shared-file lock, Core owns them going forward): `BLOCK_ENTITY_TYPES`, `MENU_TYPES`, `RECIPE_TYPES`, `RECIPE_SERIALIZERS`, `DATA_COMPONENT_TYPES`.

- **Package:** `core.brewing` for block, block entity, menu, recipe, items and component; `client.core.brewing` for the screen; `core.test` for the GameTest; `datagen` for providers.
- **Block:** `lotrrevival:brewing_barrel`, horizontal `facing`, with block entity type `lotrrevival:brewing_barrel`.
- **Block entity:** server ticker only. Fields as listed under Save and reload; slots in an item handler (7 slots: 0-5 ingredients, 6 water).
- **Menu type:** `lotrrevival:brewing_barrel`. Synced with a container data of 6 ints: state, elapsed ticks, brew time, current tier, servings left, drink index (or the recipe's result is sent in the open-menu buffer). No custom payloads.
- **Recipe type and serializer:** `lotrrevival:brewing`. JSON shape:
  ```json
  {
    "type": "lotrrevival:brewing",
    "ingredients": [ { "ingredient": { "item": "minecraft:wheat" }, "count": 3 } ],
    "brew_time": 6000,
    "servings": 4,
    "result": "lotrrevival:ale"
  }
  ```
  `ingredients` holds 1 to 6 entries, `count` 1 to 64; `brew_time` at least 20; `servings` 1 to 16, default 4. Codec and stream codec both required.
- **Data component:** `lotrrevival:drink_quality`, int 0-4, persistent and network-synced. Tier names and colors in one shared enum in `core.brewing`.
- **Drink definition:** effects, nutrition, saturation, alcoholic flag per drink are Java constants on the drink items for the pilot (not data-driven).
- **Creative tabs:** no new tab. The barrel goes into vanilla Functional Blocks; the mug and the five drinks (Moderate, no component) into Food & Drinks.
- **Datagen providers** (all wired in `DataGenerators`, shared-file lock):
  - block state and block model: orientable cube (front, side, top; bottom uses top);
  - item models: block item parent for the barrel, generated flat models for the mug and drinks;
  - block loot table: drops itself;
  - block tags: `minecraft:mineable/axe`;
  - recipes: barrel, mug, and the five brewing recipes;
  - lang `en_us`: block, items, menu title "Brewing Barrel", tier names, status lines, tooltip lines.
- **Textures** (placeholders allowed, committed by hand, replaced later by the artist agent):
  - `block/brewing_barrel_front.png`, `block/brewing_barrel_side.png`, `block/brewing_barrel_top.png` (16x16);
  - `item/wooden_mug.png`, `item/ale.png`, `item/cider.png`, `item/mead.png`, `item/berry_wine.png`, `item/miruvor.png` (16x16);
  - `gui/container/brewing_barrel.png` (176x166 background) and sprites `gui/sprites/container/brewing_barrel/progress_arrow.png` (24x16), `quality_pip_off.png` and `quality_pip_on.png` (5x5).

## Out of scope

- Hopper, pipe and comparator interaction.
- Keeping barrel contents in the dropped item when broken.
- Aging or brewing while the chunk is unloaded.
- Other fluids, water bottles, and drinks needing items we do not have yet (perry, Dorwinion wine, Old Winyards, orc draughts).
- Drunkenness beyond vanilla Nausea, and any faction or alignment effects of drinks.
- Placeable mugs, tankards, or other vessels.
- JEI/EMI recipe display.

## Acceptance tests

All GameTests live in one class, `core.test.BrewingBarrelTests`, and drive the block entity's server tick directly in a loop instead of waiting real ticks.

- [ ] GameTest `ale_brews_to_weak`: place a barrel, insert 5 wheat and a water bucket, tick once: state is Brewing, 2 wheat remain, water slot holds an empty bucket. Tick 6000 more: state is Ready, servings 4, tier 0.
- [ ] GameTest `ale_ages_to_potent_and_locks_on_tap`: from Ready, tick 12000 more: tier 4. Draw one serving with a mock player holding 1 empty mug: the result is `lotrrevival:ale` with `drink_quality` 4, servings 3, state Tapped. Tick 6000 more: tier still 4.
- [ ] GameTest `wrong_ingredient_blocks_match`: 3 wheat plus 1 dirt and a water bucket: after 20 ticks the state is still Empty and nothing is consumed.
- [ ] GameTest `drink_applies_scaled_effect`: a mock player finishes drinking an Ale with quality 3: has Strength I with duration 1800 (+-1 tick), and holds an empty Wooden Mug.
- [ ] Playtest: craft barrel and mugs, brew cider, watch progress and pips in the GUI, tap at Light and at Potent, drink both, check tooltips; break a barrel mid-brew and confirm leftovers drop and the brew is lost; reload the world mid-brew and confirm progress kept.

## Tickets

Filled in by the lead once the spec is approved. Proposed split for Sonnet 1 (`pod:core`, `type:ticket`, `agent:sonnet-1`, milestone G1):

1. **Registers, items and quality component** (about 1 day). Add the five new registers to `ModRegistries`; the `drink_quality` component and tier enum; `wooden_mug` and the five drink items with drinking, effects, nutrition, nausea, mug return and tooltip; creative tab placement; item models, lang and mug recipe via datagen; placeholder item textures; GameTest `drink_applies_scaled_effect`. Shared files: `ModRegistries`, `DataGenerators`.
2. **Barrel block, recipe type and brewing logic** (about 1.5 days, after 1). Block with facing, block entity with slots, state machine, aging and tapping, persistence, drops; `brewing` recipe type, codec, stream codec and matching; menu and screen with progress arrow, pips and status line; block state, models, loot, tags, lang and all recipes via datagen; placeholder block and GUI textures; GameTests `ale_brews_to_weak`, `ale_ages_to_potent_and_locks_on_tap`, `wrong_ingredient_blocks_match`. Shared files: `ModRegistries` (entries only), `DataGenerators`.

## Open questions

1. Should brewing and aging catch up while the chunk is unloaded (by stored game time)? Pilot says no; players may expect a barrel to age while they travel.
2. Should a broken barrel keep its brew in the item (shulker-style) instead of losing it? Pilot says lost, to keep scope small.
3. Is losing the brew acceptable when the barrel is destroyed by a creeper, or should leftover servings drop as filled mugs? Needs mugs we do not have, so pilot says lost.
4. Potent alcoholic drinks risk Nausea while giving the longest buff. Is that the trade-off we want, or should top quality be strictly better?
5. Drink stats are Java constants. Should drinks become data-driven (a datapack registry) before more are added by other pods?
6. Miruvor as a brewable is a gameplay choice; later it may become a trader or Elf-only item. Keep it in the starter set?
