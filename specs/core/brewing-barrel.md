# Brewing barrel (pilot system)

- **Pod:** core
- **Lead:** Opus 1
- **Status:** draft
- **Depends on:** nothing beyond vanilla and `core.registry.ModRegistries`. No factions, alignment or custom networking. Issue #5.
- **Sources:** behavior from [`docs/reference/legacy/brewing.md`](../../docs/reference/legacy/brewing.md) (our own notes on Legacy 36.15), adjusted by the maintainer's pilot decisions. Names, ingredients for missing crops, tier words and all assets are our own.

## Summary

A wooden barrel that turns six ingredients and three buckets of water into sixteen servings of a drink. The longer the player lets it brew, the stronger the drink gets, across five strength tiers; the player can stop early to lock a tier. Ordinary alcoholic drinks are food with a chance of nausea that grows with strength; only special draughts give buffs. Breaking the barrel keeps the brew inside the dropped item, like a shulker box. This is the pilot system that measures how fast one implementer can ship a self-contained feature end to end: block, block entity, menu, custom recipe type, data components, datagen and GameTests.

## Player-facing behavior

1. **Craft** a Brewing Barrel and some Wooden Mugs (recipes below). Place the barrel; its front (the **tap**) faces the player.
2. **Open** the screen by right-clicking any face other than the tap, or the tap with anything other than the tap actions in step 7. The screen has 6 ingredient slots (2 rows of 3), 3 water slots (third row), a display-only result slot, a **Brew** button, a progress bar, a fill gauge (servings out of 16) and a status line, above the player inventory. A barrel renamed in an anvil shows its name as the screen title.
3. **Load** while the barrel is Empty: one or more items in each of the 6 ingredient slots and a Water Bucket in each water slot. Water slots accept only water buckets. Shift-click routes water buckets to the water slots and other items to the ingredient slots. When the slots match a recipe, the result slot previews the drink.
4. **Brew.** Pressing Brew while Empty with a matching recipe removes one item from every ingredient slot (extras in a stack stay, so a player can preload several brews), turns each water bucket into an empty bucket in place, and starts brewing 16 servings at strength tier 0. Status: "Brewing <drink> (<tier>)".
5. **Strength rises** one tier every 5 minutes (6,000 ticks; half Legacy's pace, maintainer decision). After 25 minutes (30,000 ticks) tier 4 is complete and the barrel becomes **Full** by itself.
6. **Stop early.** Pressing Brew while Brewing, once at least tier 0 has completed (5 minutes in), ends brewing: the barrel becomes Full at the **last completed** tier and the partial tier is discarded. Before 5 minutes the button is disabled.
7. **Tap.** Right-click the tap face with an empty Wooden Mug while Full: one mug leaves the hand and a filled mug of the drink at the barrel's tier is given (into the hand if the stack was 1, else into the inventory, else dropped at the player); servings -1. Status while Full: "<drink> (<tier>), <n> left". When the last serving is drawn the barrel is Empty again.
8. **Drink.** Hold use on a filled mug for 32 ticks (drink animation and sound). The player gains the drink's food and effects scaled by tier, may get Nausea if it is alcoholic, and gets an empty Wooden Mug back. All pilot drinks can be drunk on a full hunger bar.
9. **Tooltip** on a filled mug: the tier name (colored), the alcohol percentage for alcoholic drinks (colored by band), then effect lines in vanilla potion-tooltip style with scaled durations.
10. **Break** the barrel: leftover ingredients and buckets drop; if it was Brewing or Full, the dropped barrel item carries the brew (drink, tier, servings, progress), shows it in its tooltip, does not stack, and restores the brew when placed. A Brewing barrel resumes from the stored progress.

## Rules and numbers

### Blocks and items

| Thing | Value | Notes |
| --- | --- | --- |
| `brewing_barrel` block | hardness 3, blast resistance 5, wood sound, mineable with axe, no tool required | Full cube in the pilot. Block state `facing` (4 horizontal values), the opposite of the player's horizontal look on placement; the `facing` side is the tap. Not flammable. |
| Barrel recipe | shaped `PPP / IBI / PPP`: P = `#minecraft:planks`, I = iron ingot, B = `minecraft:barrel` | Yields 1. |
| `wooden_mug` item | stack 64 | Recipe shaped `P P / PNP`: P = `#minecraft:planks`, N = iron nugget. Yields 2. |
| Filled drink items | stack 1 | One item per drink (table below). Return 1 `wooden_mug` when drunk (none in creative). |
| Barrel item carrying a brew | stack 1 | Plain barrel items stack to 64. |
| Use time | 32 ticks | Drink animation. |
| Menu reach | 8 blocks | Menu closes beyond this or if the barrel is removed. |

### Brewing

| Thing | Value | Notes |
| --- | --- | --- |
| Slots | 0-5 ingredients, 6-8 water | Ingredient and water slots accept items only while Empty; items can always be taken out. |
| Recipe shape | exactly 6 ingredients, shapeless, one item per slot | Every ingredient slot must be non-empty, and the 6 slots must match the recipe's 6 ingredients one-to-one in any order (as vanilla shapeless crafting does). Ingredients may repeat. An unrelated item in any slot means no match. |
| Water | 3 `minecraft:water_bucket`, one per water slot, all required | Each becomes `minecraft:bucket` on Brew. Other fluids and water bottles are out of scope. |
| Start | Brew button | Sent with the vanilla menu button click (button id 0); no custom payload. Server validates state and match. |
| Servings | 16 per brew, fixed | |
| Tier length | 6,000 ticks | Tier in progress = floor(elapsed / 6,000). At elapsed = 30,000: Full, tier 4. |
| Stop early | allowed when tier in progress >= 1 | Becomes Full at tier in progress - 1; elapsed resets to 0. |
| Progress | +1 per server tick while the chunk is loaded and ticking | No catch-up while unloaded (matches Legacy). |
| Progress bar | Brewing: elapsed / 30,000; otherwise empty | |

Barrel states: **Empty** -> (Brew, recipe matched) -> **Brewing** -> (30,000 ticks, or Brew pressed after tier 0) -> **Full** -> (last serving drawn) -> **Empty**.

### Strength tiers

Stored on every filled drink as the `lotrrevival:drink_strength` data component (int 0-4). A drink item without the component (creative tab, `/give`) behaves as tier 2.

| Tier | Id | Name | Name color | Strength multiplier (effect durations, alcohol) | Food multiplier |
| --- | --- | --- | --- | --- | --- |
| 0 | `thin` | Thin | gray | x0.25 | x0.50 |
| 1 | `fair` | Fair | white | x0.5 | x0.75 |
| 2 | `hearty` | Hearty | green | x1.0 | x1.00 |
| 3 | `stout` | Stout | aqua | x2.0 | x1.25 |
| 4 | `mighty` | Mighty | gold | x3.0 | x1.50 |

- Effect duration = floor(base duration x strength multiplier) ticks. Amplifiers never change.
- Nutrition = max(1, round-half-up(base nutrition x food multiplier)). The saturation modifier is unchanged, so saturation scales with nutrition.
- Alcohol percentage in the tooltip = alcohol value x strength multiplier x 10, one decimal place. Color: green under 2%, yellow under 5%, gold under 10%, red under 20%, dark red at 20% and above.

### Alcohol (pilot)

| Thing | Value |
| --- | --- |
| Power | alcohol value x strength multiplier |
| Nausea chance on finishing a drink | min(1, power); none if power is 0 |
| Nausea duration | floor(uniform random 1,200..1,800 ticks x power), amplifier 0 |
| Stacking | Ordinary vanilla effect rules (a longer existing Nausea is kept) |

Worked examples: Thin Ale power 0.075, 7.5% chance, 90-135 ticks. Mighty Mead power 1.8, always, 2,160-3,240 ticks (1.8-2.7 min).

### Drinks (starter set)

Base values are at tier 2 (Hearty). Nutrition and saturation modifier use vanilla food semantics.

| Item id | Name | Ingredients (6) | Alcohol value | Nutrition / sat. mod. | Effects at Hearty | Kind |
| --- | --- | --- | --- | --- | --- | --- |
| `ale` | Ale | 6 wheat | 0.3 | 3 / 0.3 | none | plain alcohol |
| `cider` | Cider | 6 apple | 0.3 | 4 / 0.4 | none | plain alcohol |
| `mead` | Mead | 6 honeycomb | 0.6 | 4 / 0.4 | none | plain alcohol |
| `dwarven_tonic` | Dwarven Tonic | 3 wheat, 2 glow berries, 1 iron nugget | 0.2 | 4 / 0.4 | Night Vision, 4,800 ticks (240 s) | special draught, mildly alcoholic |
| `miruvor` | Miruvor | 1 golden apple, 2 glow berries, 2 honeycomb, 1 sugar | 0 | 8 / 0.8 | Strength I, 800 ticks; Speed I, 800 ticks | special draught, not alcoholic |

Ingredient notes: Mead uses honeycomb instead of Legacy's sugar. Dwarven Tonic and Miruvor use vanilla stand-ins for crops and metals we do not have yet (dwarfwort, mithril, mallorn nuts, elanor, niphredil); swap them when World adds those items.

Worked examples: Mighty Miruvor gives Strength I and Speed I for 2,400 ticks and 12 nutrition; Thin Miruvor gives 200 ticks and 4 nutrition. Stout Dwarven Tonic gives Night Vision for 9,600 ticks, 5 nutrition, and a 40% chance of Nausea for 480-720 ticks.

## Edge cases

- **Multiplayer:** several players can have the menu open at once; all see the same synced state. Brew presses and taps are resolved on the server in arrival order; a press that no longer applies (state changed) does nothing, and a tap after the last serving opens the menu instead. Clients never decide state.
- **Breaking (survival, including explosions):** the loot table drops the barrel item with no explosion-survival condition. If the barrel was Brewing or Full, the item carries the brew; leftover slot contents drop as items. Creative-mode breaking drops nothing (vanilla behavior).
- **Placing a barrel item with a brew:** restores state, drink, tier, servings and elapsed ticks; slots start empty. A barrel item whose stored drink id no longer exists places as an Empty barrel and logs one warning.
- **Save and reload:** slots, state, drink id, tier, elapsed ticks, servings and custom name persist on the block entity. If the saved drink id no longer exists the barrel resets to Empty, keeps its slot contents, and logs one warning.
- **Recipe removed by a datapack mid-brew:** the brew continues; the barrel stores the result drink, not the recipe.
- **Datapack recipes:** brewing recipes are ordinary datapack recipes, so packs can add, change or remove drinks. A recipe whose result is not a registered drink item, or that does not have exactly 6 ingredients, is rejected at load with an error log.
- **Hoppers and pipes:** none in the pilot (follow-up ticket). The block entity must not implement vanilla `Container` and registers no item-handler capability.
- **Comparators:** no signal in the pilot.
- **Death, logout, dimension change:** not relevant to the block. Drink effects are ordinary mob effects and follow vanilla rules (cleared by milk and death).
- **Drinking in creative:** food and effects apply, Nausea may apply, the filled mug is not consumed, no empty mug is given.
- **Other systems:** none. Later systems (traders, NPC taverns, alignment, achievements) may read the drink items and the strength component; nothing here depends on them.

## Data and APIs

All registered through `core.registry.ModRegistries`. The pilot adds these registers there (shared-file lock, Core owns them going forward): `BLOCK_ENTITY_TYPES`, `MENU_TYPES`, `RECIPE_TYPES`, `RECIPE_SERIALIZERS`, `DATA_COMPONENT_TYPES`.

- **Package:** `core.brewing` for block, block entity, menu, recipe, items and components; `client.core.brewing` for the screen; `core.test` for GameTests; `datagen` for providers.
- **Block:** `lotrrevival:brewing_barrel`, horizontal `facing`, block entity type `lotrrevival:brewing_barrel`, nameable.
- **Block entity:** server ticker only. Fields as listed under Save and reload; slots in an item handler (9 slots: 0-5 ingredients, 6-8 water). Exposes and reads the brew through block-entity component hooks so the loot table can copy it to the item and placement restores it.
- **Menu type:** `lotrrevival:brewing_barrel`. Synced with container data: state, elapsed ticks, tier, servings left. The result slot is display only (cannot be taken from or inserted into) and shows the matched preview while Empty or the brewed drink with its tier otherwise. Brew button via the vanilla menu button click, id 0.
- **Recipe type and serializer:** `lotrrevival:brewing`. JSON shape:
  ```json
  {
    "type": "lotrrevival:brewing",
    "ingredients": [
      { "item": "minecraft:wheat" }, { "item": "minecraft:wheat" }, { "item": "minecraft:wheat" },
      { "item": "minecraft:wheat" }, { "item": "minecraft:wheat" }, { "item": "minecraft:wheat" }
    ],
    "result": "lotrrevival:ale"
  }
  ```
  `ingredients` holds exactly 6 entries. Water, servings (16) and timing (6,000 ticks per tier) are fixed constants, not recipe fields. Codec and stream codec both required.
- **Data components:**
  - `lotrrevival:drink_strength`: int 0-4, persistent and network-synced. Tier ids, names, colors and both multipliers in one shared enum in `core.brewing`.
  - `lotrrevival:barrel_brew`: record of state (brewing or full), drink item id, tier, servings, elapsed ticks; persistent and network-synced. Set on the barrel item by the loot table's copy-components function, together with vanilla `max_stack_size` 1. Tooltip: "<drink> (<tier>), <n> left" or "Brewing <drink> (<tier>)".
- **Drink definition:** alcohol value, nutrition, saturation modifier and effects per drink are Java constants on the drink items for the pilot (not data-driven).
- **Creative tabs:** no new tab. The barrel goes into vanilla Functional Blocks; the mug and the five drinks (no strength component) into Food & Drinks.
- **Datagen providers** (all wired in `DataGenerators`, shared-file lock):
  - block state and block model: orientable cube (front = tap, side, top; bottom uses top);
  - item models: block item parent for the barrel, generated flat models for the mug and drinks;
  - block loot table: drops itself, copies `lotrrevival:barrel_brew` and custom name, no explosion condition;
  - block tags: `minecraft:mineable/axe`;
  - recipes: barrel, mug, and the five brewing recipes;
  - lang `en_us`: block, items, menu title "Brewing Barrel", Brew button, tier names, status lines, tooltip lines.
- **Textures** (placeholders allowed, committed by hand, replaced later by the artist agent):
  - `block/brewing_barrel_front.png`, `block/brewing_barrel_side.png`, `block/brewing_barrel_top.png` (16x16);
  - `item/wooden_mug.png`, `item/ale.png`, `item/cider.png`, `item/mead.png`, `item/dwarven_tonic.png`, `item/miruvor.png` (16x16);
  - `gui/container/brewing_barrel.png` (176x166 background) and sprites under `gui/sprites/container/brewing_barrel/`: `progress.png`, `fill_gauge.png`, and button states.

## Out of scope

Everything in **Later** below, plus JEI/EMI recipe display and catch-up brewing while the chunk is unloaded.

## Later (follow-up tickets, not the pilot)

Each item becomes its own `pod:core` ticket once the pilot ships, unless noted.

1. **Hopper support:** hoppers from above insert into ingredient slots (emptiest first); from the sides or below insert water buckets and pull out only empty buckets; drinks never move by hopper. Comparator signal decided then.
2. **Drunkenness and tolerance:** per-player tolerance attachment (power x 0.99^tolerance; +nausea seconds / 20 on each drunk; -1 per in-game day online), slurred chat while nauseated (up to 40% of words, full effect at 4,800+ ticks remaining, server config toggle, default on).
3. **Refilling:** pouring a brewed drink back into an Empty barrel, or a Full barrel of the same drink and tier under 16 servings, returning the empty vessel.
4. **Barrel extras:** pick-block giving a barrel item with the brew; the smaller block shape (about 12/16 wide, 13/16 tall); placing a barrel item on still water as a floating rideable barrel.
5. **Full drink list:** the remaining Legacy-style drinks (29 in total: perry, wines, liqueurs, rum, vodka, kvass, Athelas Brew, Orc Draught, Morgul-draught and others), most needing World crops first. Termite-style exploding drinks, harmful-effect removal and magic damage come with them.
6. **Vessels:** a `vessel` data component remembering the container, 12 vessel types (mugs, cups, goblets, horns, wine glass, waterskin, bottle), placeable vessels, per-vessel crafted-drink recipes and trade value.
7. **Non-brewed drinks:** water, milk, juices, chocolate, lemonade; juices drinkable only when hungry.
8. **Alignment-gated drinks** (Morgul-draught style), after the alignment API exists.
9. **Poisoned drinks and barrels** (NPC/Progression), **drink achievements** (Progression), **NPC tavern drinking** (NPCs, random tiers 1-3).

## Acceptance tests

All GameTests live in one class, `core.test.BrewingBarrelTests`, and drive the block entity's server tick directly in a loop instead of waiting real ticks. "Press Brew" means calling the menu's button handler (id 0) for a mock player with the menu open.

- [ ] GameTest `ale_starts_on_brew`: ingredient slots hold 2 wheat in slot 0 and 1 wheat in slots 1-5, water slots hold 3 water buckets. Result slot previews Ale. Press Brew: state Brewing, tier 0, servings 16, slot 0 holds 1 wheat, slots 1-5 are empty, water slots hold empty buckets.
- [ ] GameTest `incomplete_or_wrong_load_does_not_brew`: (a) 6 wheat and 2 water buckets; (b) 5 wheat, 1 dirt and 3 water buckets; (c) 5 wheat in 5 slots, one slot empty, and 3 water buckets. In each case pressing Brew leaves the barrel Empty and consumes nothing.
- [ ] GameTest `brew_runs_to_mighty`: start Ale, tick 59,999: Brewing, tier 4. Tick once more: Full, tier 4, servings 16.
- [ ] GameTest `stop_early_locks_last_completed_tier`: start Ale, tick 5,999, press Brew: still Brewing. Tick to 15,000 (tier in progress 2), press Brew: Full, tier 1, servings 16.
- [ ] GameTest `tap_draws_servings`: a Full Ale barrel at tier 3. A mock player holding 1 empty mug uses the tap face: holds `lotrrevival:ale` with `drink_strength` 3, servings 15. Using a side face with an empty mug draws nothing. Drawing 15 more leaves the barrel Empty.
- [ ] GameTest `drink_scales_food_and_effects`: a mock player at 0 hunger finishes a Stout Miruvor: has Strength I and Speed I with duration 1,600 (+-1 tick), food level 10, holds an empty Wooden Mug, no Nausea.
- [ ] GameTest `strong_alcohol_always_nauseates`: a mock player finishes a Mighty Mead: has Nausea with duration between 2,160 and 3,240 ticks inclusive.
- [ ] GameTest `broken_barrel_keeps_brew`: a Full Ale barrel at tier 2 with 10 servings and 1 wheat left in slot 0. Destroy it with drops: exactly one barrel item with `barrel_brew` {full, ale, 2, 10, 0} and max stack size 1, plus 1 wheat. Place the item: the barrel is Full Ale, tier 2, 10 servings. Repeat with a Brewing barrel at elapsed 15,000: after placing it resumes at 15,000 and reaches Full at 30,000.
- [ ] Playtest (use `/tick sprint` for the waits): craft barrel and mugs, brew cider, watch progress, gauge and status; stop one brew early at Fair and let another reach Mighty; tap from the tap face and open the screen from a side with a mug in hand; drink both and check tooltips, food and nausea; rename a barrel in an anvil; break a Full barrel and a Brewing barrel, replace them and confirm the brew is kept; blow one up with TNT; reload the world mid-brew and confirm progress kept.

## Tickets

Filled in by the lead once the spec is approved. Proposed split for Sonnet 1 (`pod:core`, `type:ticket`, `agent:sonnet-1`, milestone G1):

1. **Registers, drinks and strength component** (about 1 day). Add the five new registers to `ModRegistries`; the `drink_strength` component and tier enum (names, colors, both multipliers); `wooden_mug` and the five drink items with drinking, scaled food and effects, nausea chance and duration, mug return and tooltip (tier, alcohol %, effects); creative tab placement; item models, lang and mug recipe via datagen; placeholder item textures; GameTests `drink_scales_food_and_effects`, `strong_alcohol_always_nauseates`. Shared files: `ModRegistries`, `DataGenerators`.
2. **Barrel block, recipe type and brewing logic** (about 2 days, after 1). Block with facing and tap face, nameable block entity with 9 slots, Empty/Brewing/Full state machine with tier timing and stop early, tapping, persistence; `brewing` recipe type, codec, stream codec and one-to-one shapeless matching; `barrel_brew` component, loot copy and restore on placement, barrel item tooltip; menu and screen with Brew button, progress bar, gauge, preview slot and status line; block state, models, loot, tags, lang and all recipes via datagen; placeholder block and GUI textures; GameTests `ale_starts_on_brew`, `incomplete_or_wrong_load_does_not_brew`, `brew_runs_to_mighty`, `stop_early_locks_last_completed_tier`, `tap_draws_servings`, `broken_barrel_keeps_brew`. Shared files: `ModRegistries` (entries only), `DataGenerators`.

Follow-up tickets from **Later** are opened after ticket 2 merges; hopper support and drunkenness/tolerance first.

## Resolved by the maintainer

- Servings: 16 per brew, fixed.
- Breaking keeps the brew in the item (shulker style), including explosions.
- Drink model: plain alcohol gives food and a strength-scaled nausea chance; only special draughts give buffs.
- Five tiers with Legacy's multipliers (x0.25 to x3 strength, x0.5 to x1.5 food), named Thin, Fair, Hearty, Stout, Mighty.
- Timing, water and button: 5 minutes per tier (Legacy uses 10; sped up by maintainer decision), three water buckets, Brew button with stop early. No catch-up while unloaded.
- Hoppers, tolerance and slurred chat, the full drink list and vessels are follow-up tickets.

## Open questions

2. Drink stats are Java constants. Should drinks become a data-driven registry before the full drink list lands?
3. Stand-in ingredients (honeycomb mead, iron-nugget Dwarven Tonic, golden-apple Miruvor): acceptable until World adds the real crops, or hold those two draughts until then?
4. Barrel recipe uses a vanilla barrel (ours) instead of Legacy's planks-iron-bucket recipe. Keep?
5. Creative-mode breaking drops nothing, so a creative player loses the brew (Legacy behavior; vanilla shulker boxes drop in creative). Keep?
