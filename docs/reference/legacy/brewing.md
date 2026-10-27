# Legacy reference: brewing and drinks

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: the keg/barrel block, brewing recipes and timing, drink strength tiers, the drink list with effects, drinking vessels, alcohol and drunkenness, poisoned drinks.

## 1. The barrel block

| Thing | Legacy behavior |
| --- | --- |
| Recipe | Shaped: a row of planks top and bottom, middle row iron ingot, empty bucket, iron ingot. Yields 1 |
| Block | Wood material, hardness 3, blast resistance 5, wood sound; slightly smaller than a full block (about 12/16 wide, 13/16 tall) |
| Facing | Faces the player on placement; the front face is the **tap** |
| Naming | A barrel renamed in an anvil shows that name as its screen title |
| Interaction | Right-click on the tap face with a vessel or a drink does tap/refill actions (section 4). Any other click, or a click on another face, opens the screen |

### 1.1 Screen and slots

| Slots | Count | Accepts |
| --- | --- | --- |
| Ingredients | 6 (2 rows of 3) | Anything, but only while the barrel is empty |
| Water | 3 (the third row) | Water buckets only, only while empty |
| Result | 1 | Shows the drink that will be or is being brewed; players cannot take from or put into it |

Plus a **Brew** button, a progress bar, a fill gauge (servings out of 16) and a status line: empty, "brewing <drink> (<strength>)", or "<drink> (<strength>), <n> left".

### 1.2 Brewing cycle

States: **Empty**, **Brewing**, **Full**.

1. While Empty, whenever slots change, the barrel looks for a recipe. A recipe needs **all three water slots filled with water buckets** and the 6 ingredient slots matching the recipe's 6 ingredients exactly, one item per slot (shapeless; stacks are fine, only one of each is used). An unrelated item in any slot means no match. The preview appears in the result slot.
2. The player presses **Brew**. One item is removed from every filled slot; water buckets become empty buckets in place. The barrel becomes Brewing with 16 servings at the lowest strength. So a full stack of 6 ingredient sets lets the player brew again later without reloading, but each brew needs 3 fresh water buckets.
3. Every **12,000 ticks (10 min)** of brewing, the strength rises one tier. Tier 0 is complete after 12,000 ticks, tier 4 after 60,000 ticks (50 min); at that point the barrel turns Full by itself.
4. Pressing **Brew** again while Brewing (only after at least one tier has finished) stops early: the barrel turns Full at the **last completed** tier (the tier currently in progress is discarded, and progress resets).
5. While Full, players draw servings (section 4). When the last serving is drawn the barrel is Empty again.

Brewing only advances while the chunk is loaded and ticking; no catch-up.

### 1.3 Automation and breaking

| Thing | Legacy behavior |
| --- | --- |
| Hopper from above | Inserts into ingredient slots, filling the emptiest slot first |
| Hopper from sides/below | Inserts water buckets; pulls out only items that are no longer water buckets (empty buckets) |
| Drinks via hopper | Not possible |
| Breaking (survival) | Leftover ingredients and buckets drop. If the barrel was Brewing or Full, the barrel item **keeps the brew** (drink, strength, servings, progress) and shows it in its tooltip; such a barrel item stacks to 1. Placing it restores the brew |
| Breaking (creative) | Drops nothing |
| Middle-click (pick block) | Gives a barrel item carrying the current brew |
| Placing on water | Using the barrel item on still water spawns a floating rideable barrel entity (carries any stored brew) |

## 2. Strength tiers

Every brewed drink carries a strength tier (0..4). The tier name shows in the tooltip and the barrel status.

| Tier | Name | Effect-duration and damage multiplier | Food multiplier | Alcohol multiplier |
| --- | --- | --- | --- | --- |
| 0 | Weak | x0.25 | x0.50 | x0.25 |
| 1 | Light | x0.5 | x0.75 | x0.5 |
| 2 | Moderate | x1.0 | x1.00 | x1.0 |
| 3 | Strong | x2.0 | x1.25 | x2.0 |
| 4 | Potent | x3.0 | x1.50 | x3.0 |

- One multiplier ("strength") scales potion durations, magic damage, heal-on-NPC and alcohol power. A separate gentler multiplier scales hunger and saturation.
- Amplifiers never change. Non-brewed drinks (juices, water, milk) have no tier and count as x1.0.
- Tooltip shows the strength name, an alcohol percentage (alcohol value x strength x 10, colored green under 2%, yellow under 5%, gold under 10%, red under 20%, dark red above), then the effects with scaled durations.
- NPCs that drink in taverns use random tiers 1..3.

## 3. Drinks

### 3.1 Barrel drinks (all brew 16 servings)

Each recipe is exactly 6 ingredients plus 3 water buckets. "Alc." is the alcohol value at Moderate (tooltip shows it x10 as a percentage). Food is hunger points / saturation modifier at Moderate. Effect durations are at Moderate.

| Drink | Ingredients (6) | Alc. | Food | Effects at Moderate | Special |
| --- | --- | --- | --- | --- | --- |
| Ale | 6 wheat | 0.3 | 3 / 0.3 | - | |
| Mead | 6 sugar | 0.6 | 4 / 0.4 | - | |
| Cider | 6 apples | 0.3 | 4 / 0.4 | - | |
| Perry | 6 pears | 0.3 | 4 / 0.4 | - | |
| Cherry Liqueur | 6 cherries | 1.0 | 3 / 0.3 | - | |
| Rum | 6 sugar cane | 1.5 | 3 / 0.3 | - | |
| Vodka | 6 potatoes | 1.75 | 3 / 0.3 | - | strongest alcohol |
| Dwarven Ale | 4 wheat, 2 dwarfwort | 0.4 | 3 / 0.3 | - | |
| Dwarven Tonic | 3 wheat, 2 dwarfwort, 1 mithril nugget | 0.2 | 4 / 0.4 | Night Vision 240 s | |
| Maple Beer | 4 wheat, 2 maple syrup | 0.4 | 4 / 0.6 | - | |
| Plum Kvass | 3 wheat, 3 plums | 0.2 | 4 / 0.4 | - | |
| Red Wine | 6 red grapes | 1.0 | 4 / 0.4 | - | |
| White Wine | 6 white grapes | 0.9 | 4 / 0.4 | - | |
| Pomegranate Wine | 6 pomegranates | 0.9 | 4 / 0.4 | - | |
| Carrot Wine | 6 carrots | 0.8 | 3 / 0.4 | - | |
| Arak | 6 dates | 1.4 | 4 / 0.4 | - | |
| Banana Beer | 6 bananas | 0.5 | 4 / 0.6 | - | |
| Melon Liqueur | 6 melon slices | 1.0 | 3 / 0.3 | - | |
| Cactus Liqueur | 6 cactus | 0.8 | 2 / 0.3 | - | |
| Lemon Liqueur | 6 lemons | 1.0 | 3 / 0.3 | - | |
| Lime Liqueur | 6 limes | 1.0 | 3 / 0.3 | - | |
| Corn Liquor | 6 corn | 1.0 | 3 / 0.3 | - | |
| Soured Milk | 6 milk buckets | 0.2 | 5 / 0.5 | - | |
| Termite Tequila | 5 cactus, 1 exploding termite | 1.5 | 3 / 0.3 | - | 1 in 6 chance of a power-3 explosion at the drinker (no block damage) |
| Miruvor | 3 mallorn nuts, 1 elanor, 1 niphredil, 1 sugar | 0 | 8 / 0.8 | Strength 40 s, Speed 40 s | |
| Athelas Brew | 6 athelas | 0 | 6 / 0.6 | Strength 120 s, Regeneration 60 s | Removes all harmful effects |
| Orc Draught | 3 Morgul-shrooms, 3 bones | 0 | 6 / 0.6 | Strength 60 s, Speed 60 s | Deals 2 magic damage (x strength) |
| Morgul-draught | 3 Morgul-flowers, 3 bones | 0 | 4 / 0.4 | Night Vision 300 s | Deals 3 magic damage. Effects apply only if the drinker has positive Mordor alignment; otherwise Poison 5 s instead |
| Torog Draught | 2 sugar cane, 2 rotten flesh, 1 dirt, 1 rhino horn | 0.6 | 6 / 0.6 | Strength 90 s | |

Worked example: Potent Athelas Brew gives Strength 360 s and Regeneration 180 s and 9 hunger; Weak gives 30 s and 15 s and 3 hunger.

### 3.2 Non-brewed drinks (crafted or bought, no tier)

| Drink | How obtained | Food | Effect |
| --- | --- | --- | --- |
| Water | Empty vessel used on a still water source | 0 | - |
| Milk | Vessel of milk | 0 | Clears all effects like a milk bucket |
| Chocolate | Milk vessel + sugar + cocoa beans | 6 / 0.6 | - |
| Apple, Orange, Mango, Pomegranate Juice | Empty vessel + 2 fruit | 6 / 0.6 | - |
| Blueberry, Blackberry, Raspberry, Cranberry, Elderberry Juice | Empty vessel + 3 berries | 5 / 0.5 | - |
| Red and white grape juice | Empty vessel + 3 grapes | 5 / 0.5 | - |
| Lemonade | Water vessel + lemon + sugar | 5 / 0.3 | - |
| Jungle cocoa (Taurethrim trade good) | Traders | 6 / 0.6 | Strength 40 s, Speed 40 s (carries a strength tier like brewed drinks, but has no barrel recipe) |
| Jungle remedy (Taurethrim trade good) | Traders | 0 | Removes all harmful effects |

Juice-type drinks (food drinks) can only be drunk when hungry; alcoholic and effect drinks can be drunk on a full hunger bar.

## 4. Vessels

Each filled drink remembers the vessel it is in; drinking returns that empty vessel (nothing returned in creative). Crafting recipes for crafted drinks exist once per vessel type. Most vessels can be placed on a surface as a decorative block holding the drink.

| Vessel | Made from | Placeable | Extra trade value (coins) |
| --- | --- | --- | --- |
| Mug | 2 tin ingots + 1 plank (yields 2) | yes | 0 |
| Ceramic mug | Clay mug (2 tin + 1 clay ball, yields 2) smelted | yes | 1 |
| Wooden cup | (craft) | yes | 0 |
| Copper goblet | (craft) | yes | 5 |
| Silver goblet | (craft) | yes | 8 |
| Gold goblet | (craft) | yes | 10 |
| Skull cup | (craft; drinking from it grants an achievement) | yes | 3 |
| Wine glass | (craft) | yes | 3 |
| Glass bottle (vanilla) | vanilla | yes | 2 |
| Waterskin | (craft) | **no** | 0 |
| Drinking horn | (craft) | yes | 5 |
| Gilded drinking horn | (craft) | yes | 8 |

Filled drinks stack to 1; empty vessels to 64.

### 4.1 Tapping and refilling

| Action (on the tap face) | Result |
| --- | --- |
| Empty vessel in hand, barrel Full | One serving moves into that vessel type at the barrel's strength; barrel count -1 |
| Brewed drink in hand, barrel Empty | The drink is poured in; barrel becomes Full with 1 serving of that drink and strength; player gets the empty vessel back |
| Brewed drink in hand, barrel Full with the **same drink and same strength**, under 16 | Poured in, count +1 |
| Anything else | Opens the screen |

So barrels double as storage for up to 16 servings of one drink at one strength.

## 5. Alcohol and drunkenness

| Thing | Legacy behavior |
| --- | --- |
| Alcohol power per drink | Alcohol value x strength multiplier (e.g. Potent Vodka = 5.25; Weak Ale = 0.075) |
| Tolerance reduction | Power x 0.99^tolerance (tolerance 100 -> x0.37; 250 -> x0.08) |
| Chance of getting drunk | Equal to power (capped by certainty at 1.0); so any drink with power >= 1 always makes you drunk |
| Drunk effect | Vanilla Nausea for 60 to 90 s x power (e.g. Potent Vodka: about 5 to 8 min) |
| Tolerance gain | Each time you get drunk, tolerance rises by the nausea seconds / 20 (rounded) |
| Tolerance decay | -1 every 24,000 ticks online (one in-game day) |
| Tolerance achievement | At 250 tolerance |
| Drunk chat | While nauseated, the player's chat messages get slurred: chance per word scales with remaining nausea (full effect at 4,800 ticks or more), up to 40%. Server config toggle, default on |
| NPCs | Tavern NPCs drink; with alcoholic drinks some get drunk for 30 to 1,500 s, slur their speech, and "drunkard" NPCs drink more often |

Drink use time is 32 ticks with the vanilla drinking animation.

## 6. Poisoned drinks

| Thing | Legacy behavior |
| --- | --- |
| Poisoning a drink | Craft a filled drink with a bottle of poison |
| Poisoning a barrel | Use a bottle of poison on a Brewing or Full barrel that is not already poisoned; every serving drawn is poisoned |
| Effect on drinking | A lethal-type poison effect for 15 s |
| Visibility | Only the poisoner (and creative players) can see that a drink is poisoned |

## 7. Achievements touching drinks

Getting drunk; drinking from a skull cup; drinking Mango Juice; Orc Draught; Athelas Brew; any wine; Dwarven Tonic; reaching 250 alcohol tolerance.

## Differences from our draft spec

Compared with `specs/core/brewing-barrel.md` (core-5 draft).

| # | Topic | Legacy | Our draft | Recommendation |
| --- | --- | --- | --- | --- |
| 1 | Tier names | Weak, Light, Moderate, Strong, Potent | **Identical names** | **Decide.** Plain English words, low risk, but the exact five-word sequence is Legacy's. Consider our own set (e.g. Thin, Fair, Good, Fine, Prime) |
| 2 | Start | Manual Brew button (packet) | Automatic when recipe matches | Keep ours (no packet, simpler) |
| 3 | Recipe shape | Always exactly 6 items, one per slot, plus 3 water buckets | 1..6 counted ingredients pooled across slots, 1 water bucket | Keep ours (more flexible, cheaper water) |
| 4 | Servings | 16 per brew, fixed | Recipe field, default 4 | Decide; 4 feels low against Legacy's 16. Suggest default 8 to 16 |
| 5 | Timing | 10 min per tier; tier 0 at 10 min, tier 4 at 50 min | Brew time per recipe (5 to 15 min), then aging at half brew time per tier (Ale potent at 15 min) | Keep ours (per-recipe times, faster pilot); our curve is fine |
| 6 | Quality lock | Brewing stops at Full; player can stop early to lock the last completed tier | Ages while Ready, locks on first tap | Keep ours (no button, same player choice) |
| 7 | Duration multipliers | x0.25, x0.5, x1, x2, x3 | x0.5, x0.75, x1, x1.5, x2 | Keep ours (narrower spread) or decide; Legacy makes Potent 12x Weak |
| 8 | Food scaling | Separate hunger multiplier x0.5..x1.5 | Not scaled by quality (nutrition fixed) | **Match Legacy** idea: scale nutrition gently with quality |
| 9 | Nausea | Probability = alcohol x strength, any tier; tolerance system; long nausea | Fixed 25%/50% at Strong/Potent only, 200/300 ticks | Keep ours for the pilot; tolerance and slurred chat are a later spec |
| 10 | Drink effects | Most alcohol has **no** buff (food + drunkenness only); buffs on special draughts (Miruvor, Athelas, Orc) | Every drink has a buff | Decide; ours is more rewarding. Keep, but note Legacy's ale/cider give no effect |
| 11 | Miruvor | Strength + Speed 40 s, 8 food, from mallorn nuts and Elven flowers | Regen II + Speed, golden apple recipe, removes Nausea | Keep ours (our own design); fine as is |
| 12 | Breaking | Barrel item keeps the brew; ingredients drop | Brew lost | **Match Legacy**: losing a 50-minute brew is harsh; component on the item is cheap in 1.21 |
| 13 | Refilling | Pour drinks back in (same drink and tier, up to 16) | Not supported | Later; nice storage feature, small scope |
| 14 | Hoppers | Ingredients from top, water and buckets from below/sides, no drink extraction | No automation | Keep ours for pilot; Legacy's rules are a good template later |
| 15 | Vessels | 12 vessel types, placeable, vessel remembered on the drink | One wooden mug, not placeable | Keep ours for pilot; plan a `vessel` data component later |
| 16 | Mug recipe | 2 tin ingots + 1 plank | Planks + iron nugget | Keep ours (no tin yet) |
| 17 | Barrel recipe | Planks, iron ingots, bucket | Planks, iron, vanilla barrel | Keep ours |
| 18 | Tap interaction | Tap face only | Anywhere on the block | Decide; tap-face-only lets players open the screen with a mug in hand |
| 19 | Drinking when full | Juices need hunger; alcohol/effect drinks always drinkable | Always drinkable | Keep ours |
| 20 | Mordor-only drink | Effects depend on alignment (Morgul-draught) | No alignment link | Later, once the alignment API exists |
| 21 | Poisoned drinks | Barrel and drink poisoning, hidden from all but the poisoner | None | Out of scope; NPC/Progression later |
