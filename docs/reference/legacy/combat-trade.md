# Legacy reference: combat, armor, mounts, trade, invasions

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Damage values are in half-hearts (1.7.10 scale, same as 1.21). Our ids are proposals.

## 1. Weapon classes

Every faction weapon is "material + class". Base melee damage for the sword class = material damage + 4 (vanilla iron material = 2, so an iron-grade sword deals 6). Other classes adjust that base.

| Class | Damage vs sword | Reach | Speed (attack interval factor) | Extra | Notes |
| --- | --- | --- | --- | --- | --- |
| Sword | 0 | x1.0 (3 blocks for players) | x1.0 | | Can block like 1.7 swords |
| Dagger | -3 | x0.75 | x1.5 faster | | Some variants poison on hit (poison 3-7 s on Normal, scales with difficulty) |
| Spear | -1 | x1.5 | x0.833 | | Throwable: charge up to 20 ticks, thrown damage = 70% of melee, full charge crits; can be picked up |
| Polearm (glaive, halberd) | 0 | x1.5 | x0.667 | | |
| Long polearm (long spear) | 0 | x2.0 | x0.5 | | |
| Pike | 0 | x2.0 | x0.5 | | Same as long polearm |
| Lance | 0 | x2.0 | x0.5 | +1 knockback | Holding it slows the wielder 20% on foot; meant for mounted use |
| Battleaxe | +2 | x1.0 | x0.75 | | Also chops wood fast |
| Hammer / mace | +2 | x1.0 | x0.667 | +1 knockback | |
| Mattock (dwarf) | as tool | | | | Combined pick/axe |
| Trident (Pelargir) | as sword | | | | Can spear fish in water |
| Throwing axe | 50% of (material + 4) when thrown | | | | Stackable ammo-like |
| Command sword | 1 | | | | Orders hired units (see hiring-units.md) |

- Player attack timing: Legacy adds its own cooldown: base 15 ticks for players (20 for mobs) divided by the speed factor. Enchant-like **modifiers** (reforged via templates) can change reach (cap x2.0), speed (cap x1.6) and knockback (cap +2).
- Bane modifiers add damage against specific races; "x-bane" counts can include hired-unit kills (config).

### Melee damage by material (sword base; apply the class deltas above)

| Material group | Sword dmg | Durability | Used by |
| --- | --- | --- | --- |
| Bronze | 5.5 | 230 | Hobbits, Bree, Dunland, Harad commoners, early game |
| Iron (vanilla) | 6 | 250 | Bree guards, Dorwinion |
| Dunland, Harnedor, Morwaith, Lamedon, Blackroot, Pinnath Gelin | 6 | 250-400 | |
| Gondor, Arnor, Rohan, Dale, Angmar, Mordor, Dol Guldur, Morgul, Rhun, Rhun gold, Near Harad, Umbar, Corsair, Gulf, Pelargir, Lossarnach, Half-troll, Taurethrim, Rangers, Black Numenorean | 6.5 | 300-450 | Main mannish and orcish lines |
| Elven (Galadhrim, High, Rivendell, Wood, Dorwinion elf), Dwarven, Blue dwarven, Uruk, Gundabad Uruk, Black Uruk, Dol Amroth, Rohan marshal, Barrow, Bladorthin | 7 | 500-700 | Elite lines |
| Utumno | 7.5 | 400 | Utumno only |
| Mithril, Gondolin (legendary) | 9 | 2400 / 1500 | Rare |

Durability broadly tracks quality: orc steel 350-400, men 300-450, elves 500-700, dwarves 650-700, mithril 2400.

### Ranged weapons

| Weapon | Arrow damage factor | Draw time (ticks) | Notes |
| --- | --- | --- | --- |
| Vanilla bow | x1.0 | 20 | Hillmen, rangers, Gundabad archers |
| Mallorn, Rohan, Ranger bows | x1.0 | 20 | |
| Mirkwood bow | x1.0 | 14 | Fastest |
| Blackroot, Rhun bows | x1.0 | 16 | |
| Near Harad bow | x1.1 | 25 | |
| Orc bow, Gondor bow | x1.125 | 20 | |
| Gundabad Uruk bow, Dorwinion elf bow | x1.2 | 30 / 20 | |
| Elven (Galadhrim), High-elven, Rivendell bows | x1.25 | 16 | |
| Black Uruk bow, Dale bow | x1.25 | 30 | Slow, strong |
| Utumno bow | x1.25 | 20 | |
| Crossbows (bronze, iron, Uruk, mithril) | x(1 + 0.1 x (material dmg - 2)), then x2 | 50 | Fire bolts; poisoned bolts exist |
| Sling | pebbles | | Hobbit bounders |
| Blowgun (Taurethrim) | darts, weak (x0.5 of an arrow) | | Poison darts exist |
| Fire pot (Rhun) | thrown fire | | Easterling fire throwers |

## 2. Armor sets

Armor points come from a protection fraction p: helmet/chest/legs/boots = round(25 x p x 0.14 / 0.40 / 0.32 / 0.14). Armor durability multiplier = round(material uses x 0.06).

| p | Points (H/C/L/B, total) | Sets |
| --- | --- | --- |
| 0.3 | 1/3/2/1 = 7 | Bone (Dunland berserker), Hithlain (Lorien wardens, light) |
| 0.4 | 1/4/3/1 = 9 | Fur, gambesons (Gondor/Dale/Dol Amroth/Lebennin levies), leather jackets, Gemsbok hide, Nomad robes, Morwaith lion skin, Wood-elf scout |
| 0.48 | 2/5/4/2 = 13 | Rangers of the North, Rangers of Ithilien, Morwaith |
| 0.5 | 2/5/4/2 = 13 | Bronze, Rohan, Dunland, Harnedor, Near Harad, Corsair, Gulf, Lamedon, Lossarnach, Dorwinion, Rhun, Half-troll, Taurethrim |
| 0.6 | 2/6/5/2 = 15 (iron-equivalent) | Gondor, Arnor, Dale, Dol Amroth, Pelargir, Pinnath Gelin, Blackroot, Umbar, Rhun gold, Rohan marshal, Mordor, Angmar, Dol Guldur, Morgul, Black Numenorean, all elven sets, Barrow, Taurethrim gold |
| 0.7 | 2/7/6/2 = 17 | Dwarven, Blue-dwarven, Uruk, Gundabad Uruk, Black Uruk, Utumno, Gondolin |
| 0.8 | 3/8/6/3 = 20 | Mithril |

- Most factions also have distinctive variants (Gondor winged helmet, captain/warlord helmets, Dwarven silver and gold for banner bearers and commanders) that share the base set's numbers.
- Weak orcs (common orcs) only get 75% of their armor value, always.
- Robes, kaftans and similar clothing give 0 armor (cosmetic).
- Mount armor exists for horses (per faction: Gondor, Rohan, Dale, Dol Amroth, Galadhrim, High-elven, Rivendell, Lamedon, Umbar, Near Harad, Rhun gold...), wargs (Mordor, Uruk, Angmar), boars (Dwarven, Blue dwarven), elk (Wood-elven), rhinos (Half-troll).

## 3. Mounts

| Mount | Stats vs vanilla horse | Who rides | Notes |
| --- | --- | --- | --- |
| Horse | 20 HP base with vanilla-style random stats; Rohan-biome horses get up to +50% HP, +30% speed, +0.2 jump; Dor-en-Ernil up to +30% / +20% / +0.1 | Men and elves | Killing a horse near Rohirrim costs -1 Rohan and angers them |
| Shire pony | x0.75 HP, x0.5 jump, x0.8 speed | Hobbit bounders | |
| Camel | x0.5 jump | Nomads | |
| Zebra | horse-like | Morwaith | |
| Elk | +0-50% HP, 3 melee, fights back | Wood elves | |
| Wild boar | max 25 HP, 3 melee | Dwarves | |
| Rhino | x1.5 HP (min 40), x1.2 speed, x0.5 jump, 4 melee | Half-trolls | |
| Warg | 20-32 HP, 3-5 melee, NPC itself | Orcs, hillmen | Players can ride/tame at +50 alignment with its faction; it throws off riders below that |
| Spider | 12+ HP by size, NPC itself | Dol Guldur, Mordor (Nan Ungol) orcs | Same +50 rule |

- NPC riding rules: sense range x1.5 and melee reach x1.5 when mounted; charge speed multiplier while attacking (default 1.7). NPCs do not dismount in water. A banner bearer only spawns mounted if its type allows it.
- Wild spawn mounted chances (warrior types): Rohirrim and bounders 1/3; elves 1/4; Swan Knights 1/4; Gondor, Lamedon, Easterling, Umbar soldiers 1/6; Dale, Harnedor, Pinnath Gelin, Nomads 1/8; Blackroot, Gulf, Morwaith 1/10; half-trolls 1/12; northern rangers 1/20; Southron champions always.
- Horses spawned for NPCs belong to the NPC and follow its navigation; when the rider dies the mount remains as a normal animal.

## 4. Coins and currency

- One coin item, **silver coin**, in three denominations worth **1, 10 and 100**. All prices and hire costs are in this value.
- Paying takes value from the inventory automatically and gives change. Pouches are not searched. Stolen (pickpocketed) coins count for some purposes but not trades.
- Every trader screen has a **coin exchange** tab to break or combine denominations.
- Sources: NPC drops (see npcs.md), selling to traders, mini-quest rewards, chests.

## 5. Traders

### Kinds

- **Fixed (structure) traders**: blacksmiths/smiths, bartenders/innkeepers/mead-hosts, farmers, orcharders, miners, butchers, bakers, brewers, fishmongers, florists, greengrocers, lumbermen, masons, goldsmiths, hunters, stablemasters, shamans, scavengers, vintners, orc traders. They are persistent and respawn 10-30 min after death.
- **Travelling traders**: merchants (Dale, Dorwinion man and elf, Near Harad, Nomad, Lindon dwarf, Iron Hills dwarf), Galadhrim and Rivendell traders, and the scrap trader. They appear every 0.8-10 hours near a player, linger, then leave.
- **Unit traders** sell units only (see hiring-units.md); farmers and vinekeepers are both traders and unit traders.
- About 200 buy/sell lists exist (roughly one pair per trader role per culture).

### Access

- Right-click opens a choice: talk or trade. Trade opens only if the trader allows it: alignment >= 0 for general/market traders, farmers, bartenders; +50 for blacksmiths, stablemasters, Shirriff; +75 for elf travelling traders; +100 for dwarf miners and smiths, elf smiths, orc traders; Black Uruk captain +400 etc. Must not be hostile to the player.
- The screen closes if the player moves more than 12 blocks away or the trader gets a target.

### Buy and sell lists

- Each trader has a **buy list** (what the player can buy) and a **sell list** (what the trader buys from the player), each drawn at spawn from that role's pool.
- Size: 3 + rand(0..2) + rand(0..2) + rand(0..2) entries (3-9), capped by pool size.
- Price per entry = pool price x random 0.75-1.25, rounded, min 1. Brewable drinks roll strength 1-3 and multiply price by strength; drinks get a random vessel that can add to the price; bought gear may roll random modifiers (1 in 3 "skilful") with price scaled by modifier value.
- **No alignment-based item pricing** in Legacy (alignment only gates access; alignment discounts exist only for hiring).
- Selling: the player drops matching items in; each trade unit pays the listed price per stack size; pickpocketed items are refused.

### Example price bands (pool prices before variance)

| Category | Price |
| --- | --- |
| Raw meat, raw crops, string, leather | 1-3 (trader buys) |
| Iron/bronze/copper/tin/orc-steel ingots | 3 (trader buys) |
| Gold ingot, small gems | 12-15 (trader buys) |
| Diamond, pearl | 25 (trader buys) |
| Cooked food, bread | 5-7 (player buys) |
| Stews, pies | 10 |
| Pipe, rings | 14-15 |
| Faction dagger / sword / spear | 9 / 15 / 16 |
| Faction helmet / chest / legs / boots | 20 / 32 / 26 / 17 |
| Horse armor | 25 |
| Crafting table of the culture | 100 |

### Stock locking and refresh

- Each entry tracks recent trade value. Once 200 coin value of that entry has been traded it locks; recent value decays by 1 every 60 ticks, so a fully locked entry reopens in ~10 minutes.
- After 5000 total coin value traded since the last refresh, the trader re-rolls both lists and locks every entry for 6000 ticks (5 minutes).
- Traders heal when idle, never get drunk and hold a coin in the off hand when not fighting.

## 6. Pickpocketing and theft (adjacent systems)

- Bree men and Bree hobbits can be pickpocketed (costs -1 Bree alignment when caught); stolen items are flagged and cannot be sold to traders.
- Bandits and ruffian spies steal items from players (ruffian spies once) and flee; killing them drops the stolen goods.

## 7. Invasions

### Natural invasions

- Each biome lists possible invading factions with a frequency tier: rare (10% per hour), uncommon (30% per hour) or common (90% per hour), expressed per second per chunk over about 16 eligible chunks.
- Checked every second over chunks with a non-creative player within 32 blocks. An invasion needs at least one living non-creative player within 48 blocks whose alignment with the invading faction is **below 0**.
- During a lunar eclipse night the chance is x5. Peaceful difficulty and the config toggle disable invasions.
- The invasion marker appears 3-5 blocks above valid ground within 32 blocks of the chosen point. Banner-protected land and enemy structure respawners block it.
- Example: Bree-land has rare Gundabad and Gundabad-warg invasions.

### Conquest horns (player-triggered)

- Sold by unit traders as a reward slot: requires +1500 alignment with the trader's faction and 2000 coins.
- Using the horn (2 s) starts that faction's invasion at the player's position. Requires +1500 with the invading faction; blocked in protected land and in Utumno. Horn-started invasions do not revert.

### Size and spawning

- Size: random 30-70 kills (horns use the same range). The marker shows a progress bar of remaining enemies.
- Spawning: while a player is within 80 blocks and fewer than 16 of its mobs are within 80 blocks, 1 in 160 chance per tick to spawn 1-6 mobs (weighted from the faction's invasion roster) within 6 blocks horizontally of the marker. A spawn wave plays a horn.
- Invasion mobs are persistent, have at least 40-block sense range, and carry the invasion id plus "bonus factions" (good factions with an area of influence nearby) so killing them always credits those factions.
- Only kills by players or their hired units reduce the remaining count. Contributors are remembered for 2400 ticks after their last kill.
- Ends in failure after 16 consecutive failed spawn attempts, if a creative player hits the marker, or on Peaceful. Optional config: progress reverts by 1 per minute after 5 minutes without player progress (not for horns).
- Announcement: players within 80 blocks with negative alignment to the invader (and the horn user) get a chat message and a progress bar.

### Rewards

- Completing (remaining reaches 0): contributors within 100 blocks with alignment <= 0 toward the invader get an achievement.
- Contributors pledged to a faction that is an enemy of the invader share 50 conquest points split evenly.
- Each invader kill also gives the normal kill alignment (npcs.md section 4), which is the main reward in practice.

### Rosters (weights)

| Invasion | Mobs (weight) |
| --- | --- |
| Hobbit | bounder 15 |
| Bree | guard 15, banner 2 |
| Rangers | ranger 15, banner 2 |
| Dwarves (both) | warrior 10, axe-thrower 5, banner 2 |
| Elves (Lindon, Rivendell, Lorien) | warrior 15, banner 2 |
| Woodland Realm | warrior 10, scout 5, banner 2 |
| Dale | levy 5, soldier 10, archer 5, two banners 1 each |
| Rohan | warrior 10, archer 5, banner 2 |
| Gondor and each fief | levy 5, soldier/marine/axeman 10-15, archer 5, banner 2 (Dol Amroth adds Swan Knights 5) |
| Fangorn | ent 10, huorn 20 |
| Dunland | civilian 10, warrior 5, archer 3, axe-thrower 3, berserker 2, banner 2 |
| Isengard | snaga 5, snaga archer 5, Uruk 10, crossbower 5, berserker 5, sapper 3, banner 2, warg 10, warg bomber 1 |
| Mordor | orc 10, archer 5, bomber 2, banners 3, warg 10, warg bomber 1, Black Uruks 4, Olog-hai 3 |
| Mordor sub-types | Black Uruk (10/5/2), Nan Ungol (orcs 30, spiders 30, banner 5), warg-only |
| Gundabad | orc 20, archer 10, warg 20, banner 5, Uruk 5, Uruk archer 2; also warg-only |
| Angmar | orcs, bombers, banner, wargs; hillmen variant; warg-only |
| Dol Guldur | spider 15, orc 10, archer 5, banner 2, Mirk troll 3 |
| Rhun | levy 5, warrior 10, archer 5, gold warrior 5, banner 5, fire thrower 3 |
| Near Harad cultures | warrior 8-10, archer 5, banner 2; coast adds champions and mercenaries; Umbar adds mercenaries and renegades; Corsairs are corsair-only |
| Morwaith, Taurethrim, Half-trolls | warrior 10-15, ranged 5 (where any), banner 2 |

## 8. Decision-relevant notes for our specs

- Weapon classes are a small matrix (damage delta, reach, speed, knockback) applied over a material table; we can data-drive both.
- Armor is a single protection fraction per set; 0.6 = iron is the anchor.
- Trade value is flat and alignment-gated only; our progression trade spec can add alignment pricing as an original improvement if wanted.
- Invasions are a self-contained marker entity with a kill counter, a weighted roster per faction, and spawn throttling (16 alive, 1/160 per tick, 1-6 per wave).
