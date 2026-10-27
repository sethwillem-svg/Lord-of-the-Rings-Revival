# Legacy reference: structures

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: every generated structure type, where it spawns, rough size, what is inside, how often, and the few things placed at fixed map positions. Names are our descriptive names. Biome names refer to `biomes.md`. Sizes are rough footprints estimated from the builds (W x L x H in blocks) and are good to about +/- 30%.

## 1. Placement rules (all structures)

| Rule | Value |
| --- | --- |
| Scattered structures | Each biome lists structures with a "1 in N chunks" chance. Every chunk rolls each entry independently (seeded by chunk position), so "1/100" means about one per 100 chunks of that biome, i.e. one per 160 x 160 blocks. |
| Ground checks | Most structures refuse to place on water, steep slopes or the wrong biome; failed rolls just produce nothing, so real density is lower than the raw chance. |
| No-go zones | Scattered structures and villages skip chunks within about 16 blocks of a road, within 256 blocks of any fixed waypoint or fixed structure, on the fixed mountains, and in vineyard variants. |
| Villages | Placed on a grid: one candidate per cell of 10-16 chunks (160-256 blocks), jittered by 1-2 chunks, accepted with the biome's village chance. A village claims a radius of 3-6 chunks (48-96 blocks), flattens terrain under it and lays its own paths in the culture's road style. |
| Garrisons | Forts, camps, towers and warg pits contain an invisible **NPC respawner** that keeps a garrison topped up (typically up to 12 NPCs within 20-32 blocks, re-checked over time) once the area is loaded. Killing the garrison does not clear the site for good. |
| Spawner chests | Tombs and pyramids use "spawner chests": opening the chest releases a guardian (barrow-wight, pyramid wraith) once. |
| Crafting | Every culture's buildings include that culture's crafting table; forts add a command table (for hired units); smithies add a forge or alloy forge; hobbit holes and farms add a hobbit oven. Traders and smiths standing in buildings are the main trader locations. |
| Loot | Chests use per-structure loot tables of 3-10 rolls: larders (food and drink), houses (tools, food, a little silver), treasure (coins, goblets, rings, gold and silver), military (culture weapons and armour, arrows), ruins (bones, old gear, coins). |

## 2. Fixed placements (same in every world)

| Thing | Where | Notes |
| --- | --- | --- |
| World spawn / ring portal landing | World (0, 0), map pixel (810, 730), a few hundred blocks from Hobbiton | Players arriving through the ring portal land here; first arrival builds a return portal (see world-map-travel.md). |
| Bree, Staddle, Combe, Archet | Offset 2 map pixels (256 blocks) from their waypoints | The only fixed villages. Bree is a large walled village with hedge, gates, gatehouse, inn, market, smithy, office, stables, hobbit burrows and a ruffian house. The others are hamlets or villages. Random Bree-land villages also appear elsewhere in Bree-land (chance 1.0 per grid cell). |
| Isengard ring wall | Centred 448 blocks north of the Isengard waypoint | Circular wall, radius 400 blocks, top at y 100, gate bottom y 80, ladders and torches. |
| Rammas Echor | Centred on Minas Tirith | Circular wall, radius 500 blocks, top y 85, gate y 77-82. |
| Utumno entrance | Map pixel (1139, 394), Forodwaith | Gateway to the Utumno dimension. |
| Mordor cherry tree | Map pixel (1630, 1170), Mordor | A single living cherry tree in the waste (easter egg). |
| Marsh hut | Map pixel (1419, 1134), Dead Marshes area | Lone hut with a troll and a horse (easter egg). |
| Gruk's house | Map pixel (989, 528), far north | Large lone house, about 40 x 40 (easter egg). |
| Ticket booth | Map pixel (1583, 2527), far south | Tiny Easterling-style booth with a caged lion (easter egg). |
| Fixed mountains | About 38 peaks (see biomes.md) | Not structures, but they block structure placement. |
| Roads and bridges | 56 named roads | See world-map-travel.md. |

There are **no** fixed Minas Tirith, Edoras, Helm's Deep, Barad-dûr or Rivendell builds in Legacy; those places are only waypoints over normal biome generation (plus the walls above).

## 3. Villages and towns (grid placed)

| Culture | Biomes (village chance per grid cell) | Grid / radius | Types | Typical contents |
| --- | --- | --- | --- | --- |
| Bree-land | Bree-land 1.0 (+ 4 fixed) | 12 chunks / 4 | hamlet, village | Houses, hobbit burrows, inn (innkeepers, Bree folk, ruffian spy), market stalls (baker, brewer, butcher, florist, farmer, mason, lumberman, blacksmith; human or hobbit), smithy, stables, barn, garden, wells, lamp posts, hedges and gates, gatehouse with guards, captain's office |
| Dúnedain (Rangers) | The Angle 0.7 | 12 / 4 | village | Palisade, ranger houses, lodge, smithy (blacksmith), stables, well, lights; ranger table |
| Gondor (and each fief) | Gondor 0.75, Pelennor 1.0, Lossarnach 1.0, Lamedon 0.8, Lebennin 0.75, Pelargir 0.75, Dor-en-Ernil 0.75, Blackroot Vale 0.6, Pinnath Gelin 0.5 | 16 / 5 | village (about 56%), town (19%), fort (25%) | Houses, stone houses, cottages, tavern (bartender), smithy, stables, barn, bath, farms, wells, benches, gardens, market stalls (ten trades), town walls and gatehouse, fortress; captain of the local fief; fief-specific banners, colours and soldiers |
| Rohan | Rohan 0.6, The Wold 0.25 | 14 / 5 | village (2/3), fort (1/3) | Palisade, houses, mead hall (meadhost, warriors), barn, stables (stablemaster), smithy, farms, pastures, gardens, well, market stalls (nine trades); fort has marshal, archers, warriors and a blacksmith |
| Harnennor | Harnennor 0.75, Harondor 0.1 (ruined versions) | 12 / 4 | village, fortress (1/4) | Houses, tavern, market (twelve trades), smithy, stables, farms, camel pastures, watch tower, palisade; fortress with warlord, archers, horses |
| Southron coast | Southron Coasts 0.6 | 14 / 5 | village, town, fort | Houses, mansions, tavern, bazaar (thirteen trades), smithy, stables, barracks, training yard, farms with slaves, pastures, statues, town walls; fortress with warlord |
| Umbar | Umbar and Umbar Hills 0.9 | as Southron | village, town, fort | Umbar-styled versions of the Southron set, Umbar captain and soldiers, Black Númenórean motifs |
| Harad nomads | Half-deserts 0.5, Harad Desert Riverbank 0.25, Great Desert 0.05 | 12 / 4 | small, big | Tents, large tents, chieftain tent with camels and guards, bazaar tent (armourer, brewer, mason, miner), well |
| Gulf of Harad | Gulf of Harad 0.75 | 14 / 6 | village, town (about 25%), fort (25%) | Houses, tavern, bazaar (thirteen trades), smithy, farms with slaves, pastures, totems, altar, village lights, town walls, watch tower; war camp with warlord; a stepped pyramid with a sacrificial chest in towns |
| Rhûn (Easterlings) | Rhúdel 0.75, Rhúdel Steppe 0.4 (no towns) | 14 / 6 | village, town, fort | Houses, town houses, large town houses, tavern, market stalls (ten trades), smithy (alloy forge), stables, farms, gardens, statue, lamps, wells, town walls and gatehouse, tower, fortress with warlord and gold warriors |
| Taurethrim | Taurethrim Clearing 0.6 | 10 / 3 | village | Houses (simple, large, on stilts with a shaman), smithy, farms, watchtowers, village trees, chieftain pyramid |
| Morwaith | Far Harad grasslands, forest and arid lands, 1 in 200 chunks (scattered, not grid) | about 40 x 40 | village | Ring of huts: chieftain hut, hunter hut, trader hut, plain huts |
| Dale | Dale, 1 in 400 chunks (scattered) | about 50 x 50 | village | Houses, bakery, smithy (alloy forge), towers, walls |
| Angmar hill-men | Angmar 1/400, Ettenmoors 1/1000, Coldfells 1/2000, Lone-lands 1/4000 (scattered) | about 40 x 40 | village | Hillman houses (about 13 x 13), chieftain house with Angmar table |

## 4. Forts, towers and camps (scattered)

| Structure | Biomes (1 in N chunks) | Size | Garrison and contents |
| --- | --- | --- | --- |
| Gondor watchfort (one design per fief: Gondor, Lossarnach, Lebennin, Pelargir, Lamedon, Blackroot, Pinnath Gelin, Dol Amroth) | Gondor 800, Lossarnach 800, Lebennin 800, Pelargir 600, Lamedon 700, Blackroot Vale 1000, Pinnath Gelin 1000, Dor-en-Ernil 800 | about 11 x 11 x 12 | Fief soldiers and archers via respawner, captain, command table, Gondor table, supply and drink chests |
| Gondor turret | Gondor 1000, Pelennor 500 | about 3 x 3 x 6 | Soldier and archer, supplies |
| Beacon tower | White Mountains and foothills 100 | about 3 x 3 x 15 | Tower guards via respawner, beacon on top |
| Ruined beacon tower | Ithilien 200 | small | Empty |
| Ithilien hideout | Ithilien (all) 50 | about 7 x 7, dug in | Ithilien rangers and captain, Gondor table, supplies (Henneth Annûn style) |
| Rohan watchtower | Rohan 600, Rohan Woodlands 600, The Wold 1000 | about 9 x 9 x 6 | Rohirrim warriors and archers, watchtower chest |
| Dale watchtower / fortress | Dale 500 / 800 | 5 x 5 x 14 / 25 x 25 | Dale soldiers, levymen, archers, captain (fortress), Dale table |
| Dwarven tower | Iron Hills 300, Erebor 400 | about 11 x 11, tall | Dwarf warriors, axe-throwers, commander; forge, dwarven table, command table |
| Blue Mountains stronghold | Blue Mountains (and foothills) 400 | medium, cut into rock | Blue dwarf warriors, axe-throwers, commander; forge, command table, larder |
| High-elven tower | Lindon 900 | about 13 x 13 | Elf lord and warriors, command table |
| Tower Hills tower | Tower Hills 600 | tall white tower | Lindon-style |
| Wood-elf tower | Woodland Realm 100 | about 3 x 3 trunk tower | Wood-elf scouts, warriors, captain; also places orc and dwarf NPCs (likely prisoners in the cells; not verified) |
| Ranger camp | The Angle 500, Tower Hills 800, Eriador and its sub-biomes 1500, Lone-lands 2000 | about 20 x 20 | Ranger tents, captain, command table, Rangers via respawner |
| Ranger watchtower | The Angle 1500, Eriador family 2000, Lone-lands 2000 | about 9 x 9 x 25 | Rangers via respawner, ranger table |
| Dunland hill-fort | Dunland 150, Adornland 700 | about 25 x 25 | Warlord, warriors, archers, crebain, bear rug; command table |
| Dunlending house / tavern / campfire | Dunland 25 / 100 / 40; Adornland 150 / 250 / 200 | 15 x 15 / 23 x 23 / small | Dunlendings, bartender; campfire chest |
| Uruk camp / warg pit | Uruk Highlands 120 / 300, Nan Curunír 120 / 100, Adornland 2000 / 3000 | about 25 x 25 / 15 x 15 | Uruk mercenary captain, uruk trader, tents (uruk table, forge), wargs and snaga |
| Mordor camp / warg pit / tower | Mordor family 100 / 300 / 500 (Udûn more often: camp 20, pit 100), Mordor Mountains tower 400, Wilderland camp 3000 | camp 25 x 25, tower about 9 x 9 x 30 | Orc trader, orc mercenary captain (tower), warg pit with wargs and orcs, tents with Morgul table and orc forge |
| Black Uruk fort | Mordor family 2000 | about 37 x 37 x 16 | Black uruks, archers, captain, command table |
| Mordor spider pit | Nan Ungol 40 | pit | Spider keeper, Mordor spiders |
| Orc slaver tower / Nurn wheat farm | Nurn 200 / 40 | tower / farm | Orc slavers guarding Nurn slaves working wheat fields |
| Dol Guldur tower / altar / camp / spider pit | Dol Guldur 80 / 160 / 50 / 50, Wilderland camp 2000 | tower medium | Orc chieftain, Dol Guldur table, spiders, orc trader |
| Gundabad camp | Vales of Anduin family 1500, Lone-lands 1500, Eriador family 2000, Northern Wilderland 2000, Celebrant 4000 | about 25 x 25 | Gundabad mercenary captain, orc trader, tents with Gundabad table |
| Angmar camp / tower / shrine / warg pit | Angmar 50 / 300 / 200 / 200 | camp 25 x 25, tower tall | Orc trader, orc mercenary captain (tower), Angmar table (shrine, tents), wargs |
| Half-troll house / warlord house | Perdorogwaith 40 / 200 | 9 x 9 | Half-troll, warlord, half-troll table |
| Corsair camp / cove | Corsair camp: Tolfalas 100, Corsair Coasts 100, Umbar 800; cove: Corsair Coasts 10 | camp 20 x 20; cove about 30 x 30 in a cliff | Corsairs, captain, slaver, caged slaves, corsair chests |
| Morwaith mercenary camp | Gulf 1000, Southron Coasts 1000, Umbar 1500, Harnennor 2000, Half-deserts 2000 | 20 x 20 | Morwaith mercenaries, training dummies, tents |
| Harnennor tower / desert camp | Harnennor 800 / 3000, Harondor 2000 / 3000 | tower 7 x 7 x 16 | Harnennor archers and warriors / camels |
| Dorwinion camp, captain tent | Dorwinion 400 | 30 x 30 | Dorwinion guards, captain, elf captain |
| Dorwinion house, elf house, brewery, garden, bath | Dorwinion 200 / 400 / 500 / 300 / 600 | 20 x 20 typical | Dorwinion folk, elves, vintner (brewery) |

## 5. Homes and civilian buildings (scattered)

| Structure | Biomes (1 in N chunks) | Contents |
| --- | --- | --- |
| Hobbit hole, burrow, house | Shire 90 / 45 / 45 (only in the base Shire), White Downs 300 / 150 / 150 | Hobbit family; larder, study and treasure chests; hobbit table and oven |
| Hobbit tavern | Shire 70, Shire Moors 200, White Downs 300 | Bartender, hobbits, shirriff; about 37 x 20 |
| Hobbit farm / windmill | Shire 500 / 600, Shire Moors 1000 / 500, White Downs 2000 / 600 | Farmer and farmhands / hobbit; windmill about 7 x 7 x 13 |
| Hobbit picnic bench | Shire family 40, White Downs 200 | A hobbit having a picnic |
| High-elf house, hall, forge | Lindon 300 / 600 / 1000 | High elves, smith, high-elven table; hall about 15 x 15 |
| Rivendell house, hall, forge | Rivendell 100 / 200 / 200 | Rivendell elves, lord, smith, Rivendell table |
| Galadhrim forge | Lothlórien 120 | Galadhrim smith, elven forge |
| Galadhrim flets (elf house, lord's house) | Built into the crowns of the largest mallorn trees | Elf or elf lord; elven table, command table |
| Wood-elf house, forge | Woodland Realm 16 / 80 | Wood elves, smith; platforms also appear in large mirk-oaks |
| Dwarf house, smithy | Iron Hills: 4 tries per chunk to carve a house into rock between y 70-130; smithy Iron Hills 150, Erebor 400 | Dwarf family, smith, larder |
| Blue Mountains house, smithy | Blue Mountains: 4 tries per chunk into rock, y 70-150; smithy 150 | Blue dwarves, smith |

## 6. Ruins and dungeons

| Structure | Biomes (1 in N chunks) | Contents |
| --- | --- | --- |
| Barrow (Barrow-downs) | Barrow-downs 10 | Stone-ringed mound with a burial chamber; spawner chest releases a barrow-wight; loot: Arnor and bronze armour, mithril tools rarely, goblets, gold, a gold ring |
| Rohan barrow | Rohan 600, Adornland 4000 | Grave mound; Rohan weapons, coins, goblets, saddle |
| Ruined Dúnedain tower | Ettenmoors, Coldfells, Barrow-downs 500; Angle, Lone-lands 800; Eriador family 1000 | Arnor weapons and armour, coins |
| Rhudaur castle | Trollshaws 1000, Lone-lands 1000, Coldfells and Ettenmoors 3000 | Large ruined castle with tower, dungeon and house chests |
| Ruined Gondor tower, Gondor ruins, obelisk, ruin | Throughout Gondor and Ithilien, 500-2000; also Tolfalas, Andrast, Enedwaith, Harondor | Gondor supplies; ruins have a bones chest and a treasure chest (Gondor weapons, silver, goblets); obelisk about 3 x 3 x 15 |
| Ruined Rohan watchtower | Uruk Highlands 300, Adornland 500 | Empty |
| Ruined high-elven turret, ruined Eregion forge | Eregion 500 / 100 | High-elven stonework |
| Ruined wood-elf tower | Mirkwood family 500 | Mirkwood spiders; loot of lost travellers |
| Ruined dwarven tower | Misty and Grey Mountains 1000 | Gundabad orc mercenary captain; dwarven and Gundabad gear |
| Dwarven mine | Iron Hills, Blue Mountains, Erebor: 1 in 150 chunks; ruined versions in the Grey Mountains 1 in 500 | Underground branching corridors, crossings and stairs from a surface entrance with a forge; corridor chests with iron, gold, silver, coal, glowstone dust and rare mithril |
| Harad pyramid | Great Desert 3000, Desert Hills 3000, Half-deserts 4000 | Sandstone pyramid with scorpion spawners and spawner chests (pyramid wraiths); Harad and Black Númenórean gear, gems, gold rings |
| Harad ruined fort, obelisk, mûmak skeleton | Near Harad deserts 1500-6000 | Ruined fort uses pyramid loot; skeleton is decoration |
| Taurethrim pyramid | Far Harad Jungle (not clearings), one candidate per 24-chunk cell, 1 in 10 accepted | Large stepped temple with traps and four spawner chests (pyramid wraiths); Taurethrim gold armour and weapons, gems |
| Númenórean ruin | The Sea, beaches, islands 500 | Coastal ruin |
| Underwater elven ruin | Sea west of Lindon (drowned Beleriand), 1 in 200 chunks | High-elven weapons, mithril items, rings |
| Orc dungeon | Underground in 15 wild northern biomes (6 tries per chunk) | Small room with an orc gear chest (Angmar and orc armour, poisoned daggers, orc bombs) |
| Troll hoard | Underground in Angmar, Trollshaws, Coldfells, Ettenmoors | 2-6 treasure piles (copper, silver, gold) |
| Stone ruins (stone, Arnor, Angmar, dwarven, high-elven, Galadhrim, Dol Guldur, Mordor, Near Harad, Númenor, Umbar, Rhûn, Dorwinion, Taurethrim styles) | Almost every biome, 5-5000 depending on biome | Broken walls and pillars in the local stone; occasional chest |
| Small stone ruin | Nearly every wild biome, 200-1000 | Short wall stub, ruined house chest |
| Ruined, burnt and rotten houses | Wild Eriador, Wilderland, Vales of Anduin, marshes, Forodwaith, 100-4000 | Abandoned cottages; ruined-house chest |
| Blasted land | Angmar 40, Uruk Highlands 40, Nan Curunír 100, Adornland 400 | A scorched crater patch, not a building |

## 7. Loot table examples

Rolls per chest in brackets. Items are named generically; our item set is up to core.

| Loot table | Typical contents |
| --- | --- |
| Barrow-downs tomb (4-8) | Arnor and bronze armour pieces, iron armour, bronze, iron and rarely mithril daggers, a mithril battleaxe or hammer (rare), goblets, gold ingots and nuggets, a gold ring, bones, arrows, books |
| Rohan barrow (6-8) | Rohan sword, spear and dagger, silver coins and nuggets, goblets, a saddle, bones and a skull |
| Gondor ruins treasure (6) | Gondor sword, spear, dagger, hammer, silver coins, goblets, gold and iron ingots |
| Ruined Dúnedain tower (5-7) | Arnor sword, spear, dagger and armour, silver, gold nuggets, bones and a skull |
| Hobbit hole treasure (3-6) | Silver coins, silver and gold rings, gold, rarely a mithril nugget |
| Near Harad pyramid (8-10) | Harad, Gulf and Black Númenórean armour and weapons, gems (amber, emerald, diamond), gold rings, a mithril nugget (rare) |
| Taurethrim pyramid (8-10) | Taurethrim and gold Taurethrim armour, weapons and tools, poisoned daggers, gems, silver, obsidian shards |
| Underwater elven ruin (6-10) | High-elven swords and polearms, mithril sword or dagger (rare), elf steel, mithril and silver rings, coins |
| Dwarven mine corridor (3-6) | Iron, gold, silver, coal, glowstone dust, bread, rarely mithril or a mithril book |
| Orc dungeon (6-8) | Orc and Angmar armour and weapons, poisoned daggers, orc bombs, orc bows, maggoty bread, orc draught, dark crystal |
| Corsair (3-5) | Corsair and Harad daggers and axes, branding iron, rum and araq, fish, bread, coins |
| Mirkwood loot (4-6) | Gear of lost travellers: bronze and leather armour, compass, books, goblets, rings, elf and dwarf bones |

## 8. Notes for our design

- Legacy density is high: in Shire chunks about 1 in 25 chunks rolls some hobbit building. Our smaller scale (about 4x) argues for lower per-chunk chances or bigger spacing for military sites, or the map gets crowded.
- The respawner garrison makes forts permanent faction strongholds; it is a key behavior for alignment farming and conquest.
- Legacy deliberately avoids fixed city builds; our landmark placement type (Bree, Brandywine Bridge) goes beyond it. Keep Legacy's no-go radius idea (256 blocks around landmarks) so scattered structures do not crowd landmarks.
