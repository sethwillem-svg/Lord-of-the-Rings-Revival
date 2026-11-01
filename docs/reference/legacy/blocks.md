# Legacy reference: block inventory

Reference notes on the content of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: which blocks Legacy has, grouped by region and type, to size our block and texture backlog. Names are plain descriptions, not the original's ids. Counts come from the jar's file listing only; no textures were opened, extracted or copied. Every block we add gets **original** art: this list says *what* to draw, never *how* it looked.

How to read it:

- **Blocks** counts distinct blocks; **Textures** counts the face and state images behind them (top, side, connected pieces, lit/unlit). Gates and pillars have many textures each because Legacy used connected textures; we can simplify those.
- Region is assigned from the block's culture or material. Trees, crops, gems and dyed variants used across the map are under **General**. A few placements are judgement calls (for example, blue rock is listed with the Dwarves only when the block says so).
- Slabs, stairs and walls reuse their base block's textures, so they are not listed separately.

| Region | Blocks | Textures |
| --- | --- | --- |
| [The Shire](#the-shire) | 16 | 75 |
| [Bree-land](#bree-land) | 2 | 3 |
| Region | Blocks | Textures |
| --- | --- | --- |
| [The Shire](#the-shire) | 16 | 75 |
| [Bree-land](#bree-land) | 2 | 3 |
| [Arnor and the Dúnedain of the North](#arnor-and-the-dnedain-of-the-north) | 7 | 16 |
| [Angmar](#angmar) | 5 | 10 |
| [Gundabad and the Misty Mountain orcs](#gundabad-and-the-misty-mountain-orcs) | 1 | 2 |
| [Lindon and Rivendell (High Elves)](#lindon-and-rivendell-high-elves) | 39 | 114 |
| [Lothlórien (Galadhrim)](#lothlrien-galadhrim) | 30 | 42 |
| [Mirkwood (Wood-elves)](#mirkwood-wood-elves) | 10 | 15 |
| [Dale](#dale) | 6 | 11 |
| [Dwarves (Durin's folk and Blue Mountains)](#dwarves-durins-folk-and-blue-mountains) | 25 | 115 |
| [Dunland](#dunland) | 1 | 2 |
| [Rohan](#rohan) | 10 | 31 |
| [Fangorn and the Ents](#fangorn-and-the-ents) | 8 | 9 |
| [Dol Guldur (southern Mirkwood)](#dol-guldur-southern-mirkwood) | 6 | 11 |
| [Gondor](#gondor) | 26 | 69 |
| [Mordor and Minas Morgul](#mordor-and-minas-morgul) | 50 | 111 |
| [Dead Marshes](#dead-marshes) | 2 | 2 |
| [Rhûn and Dorwinion](#rhn-and-dorwinion) | 32 | 70 |
| [Near Harad and Umbar](#near-harad-and-umbar) | 30 | 64 |
| [Far Harad and the jungles](#far-harad-and-the-jungles) | 39 | 85 |
| [Utumno](#utumno) | 10 | 21 |
| [Elvish (shared)](#elvish-shared) | 1 | 4 |
| [General (trees, plants, ores, building blocks used everywhere)](#general-trees-plants-ores-building-blocks-used-everywhere) | 452 | 797 |
| **Total** | **808** | **1679** |

## The Shire

| Type | Blocks |
| --- | --- |
| Gates and bars | gate hobbit blue, gate hobbit green, gate hobbit red, gate hobbit yellow |
| Doors and trapdoors | door shire pine, trapdoor shire pine |
| Wood and logs | wood beam shire pine, wood shire pine |
| Planks | planks shire pine |
| Leaves and vines | leaves shire pine |
| Saplings | sapling shire pine |
| Crafting tables | hobbit crafting table |
| Crops, bushes and food blocks | pipeweed, pipeweed plant |
| Utility and furniture | hobbit oven |
| Other | shire heather |

## Bree-land

| Type | Blocks |
| --- | --- |
| Crafting tables | bree crafting table, bree crafting table side |

## Arnor and the Dúnedain of the North

| Type | Blocks |
| --- | --- |
| Pillars | pillar arnor, pillar arnor cracked |
| Bricks, stone and carved stone | brick arnor, brick arnor carved, brick arnor cracked, brick arnor mossy |
| Crafting tables | ranger crafting table |

## Angmar

| Type | Blocks |
| --- | --- |
| Pillars | pillar angmar |
| Bricks, stone and carved stone | brick angmar, brick angmar cracked, brick angmar snow |
| Crafting tables | angmar crafting table |

## Gundabad and the Misty Mountain orcs

| Type | Blocks |
| --- | --- |
| Crafting tables | gundabad crafting table |

## Lindon and Rivendell (High Elves)

| Type | Blocks |
| --- | --- |
| Gates and bars | gate elven, gate high elven, gate wood elven, high elf bars, high elf wood bars, wood elf bars, wood elf wood bars |
| Pillars | pillar high elven, pillar high elven cracked, pillar wood elven, pillar wood elven cracked |
| Bricks, stone and carved stone | brick high elven, brick high elven carved, brick high elven cracked, brick high elven gold, brick high elven mossy, brick high elven silver, brick wood elven, brick wood elven carved, brick wood elven cracked, brick wood elven gold, brick wood elven mossy, brick wood elven silver |
| Wood and logs | wood elven bed, wood elven crafting table, wood elven torch |
| Ores, gems and metal blocks | ore quendite, ore storage elf steel, ore storage quendite |
| Crafting tables | elven crafting table, high elven crafting table, rivendell crafting table |
| Beds | elven bed, high elven bed |
| Flowers and plants | quendite grass |
| Lighting | chandelier high elven, chandelier wood elven, high elven torch |
| Utility and furniture | elven forge |

## Lothlórien (Galadhrim)

| Type | Blocks |
| --- | --- |
| Gates and bars | galadhrim bars, galadhrim wood bars |
| Doors and trapdoors | door mallorn, trapdoor mallorn |
| Pillars | pillar galadhrim, pillar galadhrim cracked |
| Bricks, stone and carved stone | brick galadhrim, brick galadhrim carved, brick galadhrim cracked, brick galadhrim gold, brick galadhrim mossy, brick galadhrim silver |
| Wood and logs | wood beam mallorn, wood mallorn |
| Planks | planks mallorn |
| Leaves and vines | leaves mallorn |
| Saplings | sapling mallorn |
| Flowers and plants | elanor, niphredil |
| Lighting | chandelier mallorn blue, chandelier mallorn gold, chandelier mallorn green, chandelier mallorn silver, mallorn torch, mallorn torch blue, mallorn torch gold, mallorn torch green |
| Other | corrupt mallorn, hithlain ladder, mallorn ladder |

## Mirkwood (Wood-elves)

| Type | Blocks |
| --- | --- |
| Doors and trapdoors | door mirk oak, trapdoor mirk oak |
| Wood and logs | wood beam mirk oak, wood mirk oak |
| Planks | planks mirk oak |
| Leaves and vines | leaves mirk oak, leaves mirk oak red, mirk vines |
| Saplings | sapling mirk oak, sapling mirk oak red |

## Dale

| Type | Blocks |
| --- | --- |
| Pillars | pillar dale |
| Bricks, stone and carved stone | brick dale, brick dale carved, brick dale cracked, brick dale mossy |
| Crafting tables | dale crafting table |

## Dwarves (Durin's folk and Blue Mountains)

| Type | Blocks |
| --- | --- |
| Gates and bars | blue dwarf bars, dwarf bars, gate dwarven |
| Pillars | pillar dwarven, pillar dwarven cracked |
| Bricks, stone and carved stone | brick dwarven, brick dwarven carved, brick dwarven cracked, brick dwarven glowing, brick dwarven gold, brick dwarven mithril, brick dwarven obsidian, brick dwarven silver |
| Ores, gems and metal blocks | ore storage blue dwarf steel, ore storage dwarf steel |
| Crafting tables | blue dwarven crafting table, blue dwarven crafting table side, dwarven crafting table, dwarven crafting table side |
| Beds | dwarven bed |
| Lighting | chandelier blue dwarven, chandelier dwarven |
| Utility and furniture | dwarven forge |
| Other | dwarf herb, dwarven door ithildin |

## Dunland

| Type | Blocks |
| --- | --- |
| Crafting tables | dunlending crafting table |

## Rohan

| Type | Blocks |
| --- | --- |
| Gates and bars | gate rohan |
| Pillars | pillar rohan |
| Bricks, stone and carved stone | brick rohan, brick rohan carved, rock rohan, smooth stone rohan |
| Wood and logs | wood beam rohan, wood beam rohan gold |
| Crafting tables | rohirric crafting table |
| Flowers and plants | simbelmyne |

## Fangorn and the Ents

| Type | Blocks |
| --- | --- |
| Flowers and plants | fangorn plant brown, fangorn plant gold, fangorn plant green, fangorn plant red, fangorn plant silver, fangorn plant yellow |
| Lighting | ent jar |
| Other | fangorn riverweed |

## Dol Guldur (southern Mirkwood)

| Type | Blocks |
| --- | --- |
| Pillars | pillar dol guldur |
| Bricks, stone and carved stone | brick dol guldur, brick dol guldur carved, brick dol guldur cracked, brick dol guldur mossy |
| Crafting tables | dol guldur crafting table |

## Gondor

| Type | Blocks |
| --- | --- |
| Gates and bars | gate dol amroth, gate gondor |
| Doors and trapdoors | door lebethron, trapdoor lebethron |
| Pillars | pillar black gondor, pillar gondor |
| Bricks, stone and carved stone | brick black gondor, brick black gondor carved, brick dol amroth, brick gondor, brick gondor carved, brick gondor cracked, brick gondor mossy, brick gondor rustic, brick gondor rustic cracked, brick gondor rustic mossy, rock gondor, smooth stone gondor |
| Wood and logs | wood beam lebethron, wood lebethron |
| Planks | planks lebethron |
| Leaves and vines | leaves lebethron |
| Saplings | sapling lebethron |
| Crafting tables | dol amroth crafting table, gondorian crafting table |
| Flowers and plants | blackroot |

## Mordor and Minas Morgul

| Type | Blocks |
| --- | --- |
| Gates and bars | gate orc, gate uruk, orc steel bars, uruk bars |
| Pillars | pillar mordor, pillar taur obsidian, pillar uruk, utumno pillar obsidian |
| Bricks, stone and carved stone | brick mordor, brick mordor carved, brick mordor cracked, brick tauredain obsidian, brick uruk, rock mordor, rock mordor moss, smooth stone mordor, utumno brick obsidian, utumno brick obsidian fire, utumno brick obsidian tile |
| Ores, gems and metal blocks | ore gulduril mordor, ore morgul iron, ore morgul iron mordor, ore storage black uruk steel, ore storage morgul steel, ore storage orc steel, ore storage uruk steel |
| Crafting tables | morgul crafting table, uruk crafting table |
| Beds | orc bed |
| Flowers and plants | mordor grass, morgul flower |
| Clay, daub, thatch, glass and earth | mordor dirt, mordor gravel, obsidian gravel |
| Lighting | chandelier morgul, chandelier orc, chandelier uruk, morgul torch, orc bomb fire, orc torch |
| Utility and furniture | orc forge |
| Other | mordor moss, mordor thorn, morgul shroom, orc bomb, orc bomb handle, orc chain, orc plating iron, orc plating rust, tauredain dart trap obsidian |

## Dead Marshes

| Type | Blocks |
| --- | --- |
| Flowers and plants | dead marsh plant |
| Clay, daub, thatch, glass and earth | quagmire |

## Rhûn and Dorwinion

| Type | Blocks |
| --- | --- |
| Gates and bars | gate rhun |
| Pillars | pillar dorwinion, pillar dorwinion mossy, pillar rhun, pillar rhun red |
| Bricks, stone and carved stone | brick dorwinion, brick dorwinion carved, brick dorwinion cracked, brick dorwinion flowers, brick dorwinion mossy, brick rhun, brick rhun carved, brick rhun cracked, brick rhun flowers, brick rhun gold, brick rhun mossy, brick rhun red, brick rhun red carved |
| Crafting tables | dorwinion crafting table, dorwinion crafting table side, rhun crafting table |
| Flowers and plants | rhun flower chrys blue, rhun flower chrys orange, rhun flower chrys pink, rhun flower chrys white, rhun flower chrys yellow |
| Lighting | rhun fire jar, rhun fire jar crown, rhun fire jar handle, rhun fire jar lid, rhun fire jar neck, rhun fire layer |

## Near Harad and Umbar

| Type | Blocks |
| --- | --- |
| Gates and bars | gate near harad |
| Doors and trapdoors | door date palm, trapdoor date palm |
| Pillars | pillar near harad, pillar near harad red, pillar umbar |
| Bricks, stone and carved stone | brick black umbar carved, brick near harad, brick near harad carved, brick near harad cracked, brick near harad lapis, brick near harad red, brick near harad red carved, brick near harad red cracked, brick umbar, brick umbar carved, brick umbar cracked |
| Wood and logs | wood beam date palm, wood date palm |
| Planks | planks date palm |
| Leaves and vines | leaves date palm |
| Saplings | sapling date palm |
| Crafting tables | gulf crafting table, near harad crafting table, umbar crafting table |
| Flowers and plants | harad flower daisy, harad flower pink, harad flower red, harad flower yellow |
| Other | date |

## Far Harad and the jungles

| Type | Blocks |
| --- | --- |
| Gates and bars | gate tauredain |
| Doors and trapdoors | door banana, door jungle, door mango, trapdoor banana, trapdoor jungle, trapdoor mango |
| Pillars | pillar taur gold, pillar tauredain |
| Bricks, stone and carved stone | brick moredain, brick morwaith cracked, brick tauredain, brick tauredain cracked, brick tauredain gold, brick tauredain mossy |
| Wood and logs | fruit wood mango, wood banana, wood beam banana, wood beam fruit mango, wood beam jungle |
| Planks | planks banana, planks mango |
| Leaves and vines | fruit leaves mango, leaves banana, leaves jungle |
| Saplings | fruit sapling mango, sapling banana |
| Crafting tables | half troll crafting table, moredain crafting table, tauredain crafting table |
| Beds | lion bed |
| Crops, bushes and food blocks | banana cake |
| Lighting | tauredain double torch |
| Other | banana, goran, goran rock, tauredain dart trap, tauredain dart trap gold, termite mound |

## Utumno

| Type | Blocks |
| --- | --- |
| Pillars | utumno pillar fire, utumno pillar ice |
| Bricks, stone and carved stone | utumno brick burning, utumno brick fire, utumno brick fire tile, utumno brick ice, utumno brick ice glowing, utumno brick ice tile |
| Other | utumno return light, utumno return portal base |

## Elvish (shared)

| Type | Blocks |
| --- | --- |
| Crops, bushes and food blocks | dalish pastry |

## General (trees, plants, ores, building blocks used everywhere)

| Type | Blocks |
| --- | --- |
| Gates and bars | bronze bars, gate bronze bars, gate gold, gate iron bars, gate mithril, gate silver, gate wooden, gate wooden cross, gold bars, mithril bars, reed bars, silver bars |
| Doors and trapdoors | door acacia, door almond, door apple, door aspen, door baobab, door beech, door birch, door cedar, door charred, door cherry, door chestnut, door cypress, door dark oak, door dragon, door fir, door green oak, door holly, door kanuka, door lairelosse, door larch, door lemon, door lime, door mahogany, door mangrove, door maple, door olive, door orange, door palm, door pear, door pine, door plum, door pomegranate, door redwood, door rotten, door spruce, door willow, trapdoor acacia, trapdoor almond, trapdoor apple, trapdoor aspen, trapdoor baobab, trapdoor beech, trapdoor birch, trapdoor cedar, trapdoor charred, trapdoor cherry, trapdoor chestnut, trapdoor cypress, trapdoor dark oak, trapdoor dragon, trapdoor fir, trapdoor green oak, trapdoor holly, trapdoor kanuka, trapdoor lairelosse, trapdoor larch, trapdoor lemon, trapdoor lime, trapdoor mahogany, trapdoor mangrove, trapdoor maple, trapdoor olive, trapdoor orange, trapdoor palm, trapdoor pear, trapdoor pine, trapdoor plum, trapdoor pomegranate, trapdoor redwood, trapdoor rotten, trapdoor spruce, trapdoor willow |
| Pillars | pillar blue rock, pillar brick, pillar chalk, pillar red rock, pillar stone |
| Bricks, stone and carved stone | brick blue carved, brick blue rock, brick chalk, brick mud, brick red carved, brick red rock, cobblebrick cob, millstone, ore glowstone, red brick cracked, red brick mossy, red sandstone, rock blue, rock chalk, rock red, sandstone carved, scorched stone, smooth stone blue, smooth stone chalk, smooth stone red, stone snow, white sandstone |
| Wood and logs | fruit wood apple, fruit wood cherry, fruit wood pear, rotten log rotten, wood almond, wood aspen, wood baobab, wood beam acacia, wood beam almond, wood beam aspen, wood beam baobab, wood beam beech, wood beam birch, wood beam cedar, wood beam charred, wood beam chestnut, wood beam cypress, wood beam dark oak, wood beam dragon, wood beam fir, wood beam fruit apple, wood beam fruit cherry, wood beam fruit pear, wood beam green oak, wood beam holly, wood beam kanuka, wood beam lairelosse, wood beam larch, wood beam lemon, wood beam lime, wood beam mahogany, wood beam mangrove, wood beam maple, wood beam oak, wood beam olive, wood beam orange, wood beam palm, wood beam pine, wood beam plum, wood beam pomegranate, wood beam redwood, wood beam rotten, wood beam spruce, wood beam willow, wood beech, wood cedar, wood charred, wood chestnut, wood cypress, wood dragon, wood fir, wood green oak, wood holly, wood kanuka, wood lairelosse, wood larch, wood lemon, wood lime, wood mahogany, wood mangrove, wood maple, wood olive, wood orange, wood palm, wood pine, wood plate, wood plum, wood pomegranate, wood redwood, wood willow |
| Planks | planks almond, planks apple, planks aspen, planks baobab, planks beech, planks cedar, planks charred, planks cherry, planks chestnut, planks cypress, planks dragon, planks fir, planks green oak, planks holly, planks kanuka, planks lairelosse, planks larch, planks lemon, planks lime, planks mahogany, planks mangrove, planks maple, planks olive, planks orange, planks palm, planks pear, planks pine, planks plum, planks pomegranate, planks redwood, planks rotten, planks willow |
| Leaves and vines | fruit leaves apple, fruit leaves cherry, fruit leaves pear, ivy, ivy red, leaves acacia, leaves almond, leaves aspen, leaves baobab, leaves beech, leaves birch, leaves cedar, leaves chestnut, leaves cypress, leaves dark oak, leaves dragon, leaves fir, leaves green oak, leaves holly, leaves kanuka, leaves lairelosse, leaves larch, leaves lemon, leaves lime, leaves mahogany, leaves mangrove, leaves maple, leaves oak, leaves olive, leaves orange, leaves palm, leaves pine, leaves plum, leaves pomegranate, leaves redwood, leaves spruce, leaves willow, willow vines |
| Saplings | fruit sapling apple, fruit sapling cherry, fruit sapling pear, sapling almond, sapling aspen, sapling baobab, sapling beech, sapling cedar, sapling chestnut, sapling cypress, sapling dragon, sapling fir, sapling green oak, sapling holly, sapling kanuka, sapling lairelosse, sapling larch, sapling lemon, sapling lime, sapling mahogany, sapling mangrove, sapling maple, sapling olive, sapling orange, sapling palm, sapling pine, sapling plum, sapling pomegranate, sapling redwood, sapling willow |
| Ores, gems and metal blocks | block gem, block gem amber, block gem amethyst, block gem coral, block gem diamond, block gem emerald, block gem opal, block gem pearl, block gem ruby, block gem sapphire, ore copper, ore gem, ore gem amber, ore gem amethyst, ore gem diamond, ore gem emerald, ore gem opal, ore gem ruby, ore gem sapphire, ore gulduril, ore mithril, ore naurite, ore salt, ore saltpeter, ore silver, ore storage bronze, ore storage copper, ore storage galvorn, ore storage gilded iron, ore storage gulduril, ore storage mithril, ore storage naurite, ore storage salt, ore storage saltpeter, ore storage silver, ore storage sulfur, ore storage tin, ore sulfur, ore tin |
| Beds | straw bed, warg fur bed |
| Crops, bushes and food blocks | apple crumble, berry bush blackberry, berry bush blueberry, berry bush cranberry, berry bush elderberry, berry bush raspberry, berry bush wildberry, berry pie, cherry pie, corn stalk, corn stalk corn, flax, flax plant, grapevine, grapevine red grapes, grapevine red vine, grapevine white grapes, grapevine white vine, kebab block, leek, lemon cake, lettuce, marzipan block, marzipan chocolate, turnip, yam |
| Flowers and plants | arid grass, asphodel, athelas, bluebell, clover petal, clover stem, coral reef, coral reef blue, coral reef green, coral reef purple, coral reef red, coral reef yellow, double flower black iris, double flower pink, double flower red, double flower yellow iris, lavender, marigold, mud grass, reeds, tall grass fernsprout, tall grass flower, tall grass nettle, tall grass short, tall grass thistle, tall grass wheat |
| Clay, daub, thatch, glass and earth | ceramic plate, clay tile, clay tile dyed black, clay tile dyed blue, clay tile dyed brown, clay tile dyed cyan, clay tile dyed gray, clay tile dyed green, clay tile dyed light blue, clay tile dyed lime, clay tile dyed magenta, clay tile dyed orange, clay tile dyed pink, clay tile dyed purple, clay tile dyed red, clay tile dyed silver, clay tile dyed white, clay tile dyed yellow, daub, dirt path dirt, dirt path mud, dried reeds, glass, glass pane, mud, mud farmland dry, mud farmland wet, red clay, stained glass black, stained glass blue, stained glass brown, stained glass cyan, stained glass gray, stained glass green, stained glass light blue, stained glass lime, stained glass magenta, stained glass orange, stained glass pink, stained glass purple, stained glass red, stained glass silver, stained glass white, stained glass yellow, thatch, thatch floor, thatch reed |
| Lighting | butterfly jar glass, butterfly jar lid, chandelier bronze, chandelier gold, chandelier iron, chandelier mithril, chandelier silver, hearth |
| Utility and furniture | alloy forge, barrel, barrel tap, bird cage bronze, bird cage gold, bird cage iron, bird cage silver, bird cage wood, command table, mechanised rail, plate, rope, treasure copper, treasure gold, treasure silver |
| Other | bone block, pine door, remains, waste block var, water flow, water still, web ungoliant, white sand |

