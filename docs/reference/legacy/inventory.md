# Legacy reference: content inventory (everything but items)

Reference notes on the content of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: everything Legacy ships except items: every named block by region and type, then creatures and NPC skins, armour, capes, shields, placed-object models, screens, map, effects, sky, sounds, structure scans and text content. Use it to size our block, art, audio and writing backlog. Items are left for a later note. Names are plain lowercase descriptions (proper names kept), not the original's ids. Counts come from the jar's file listing and block names only; no textures, sounds or text were opened, extracted or copied. Everything we add is **original**: this list says *what* exists, never *how* it looked, sounded or read.

## Summary

| Area | Count |
| --- | --- |
| Named blocks | 1,329 (523 slabs, stairs, walls, fences) |
| Block textures | 1,679 |
| Creature and NPC skins | 577 |
| Worn armour textures | 171 (incl. 20 mount armour) |
| Capes / shields | 26 / 64 |
| Placed-object model textures | 74 |
| Screen, map, effect, sky and weather textures | 41 / 6 / 18 / 7 / 4 |
| Sounds | 190 |
| Structure scans | 75 |
| Speech files / name lists / lore texts | 1,842 / 71 / 114 |
| Item textures (not covered here) | 1,222 |

## Blocks: totals

- **1,329 named blocks.** 523 of them are slabs, stairs, walls, fences and fence gates. They are separate blocks to register but reuse their base block's texture.
- **1,679 block texture files** behind about 808 textured blocks. The difference is mostly connected-texture pieces for gates and pillars, plus top/side/lit faces. We can simplify connected textures.
- Some blocks render with models instead of block textures (chests, banners, weapon racks), so they need model art rather than a 16x16 tile.
- For comparison, Legacy also has 1,222 item textures; see the item backlog separately.

## Blocks: how to read it

- Region is assigned from the culture or material in the block's name. Trees, crops, gems, dyed glass and clay, and plain stone used across the map are under **General**. A few placements are judgement calls: cargon (red rock) and luigon (blue rock) are general, and some plants could belong to a region.
- "Count" includes every colour or wood variant of a type; the list shows each distinct name once.

## Blocks by region

| Region | Named blocks | Of which slabs, stairs, walls, fences |
| --- | --- | --- |
| [The Shire](#the-shire) | 18 | 4 |
| [Bree-land](#bree-land) | 1 | 0 |
| [Arnor and the Dúnedain of the North](#arnor-and-the-dnedain-of-the-north) | 18 | 11 |
| [Angmar](#angmar) | 17 | 10 |
| [Gundabad and the Misty Mountains](#gundabad-and-the-misty-mountains) | 1 | 0 |
| [Lindon and Rivendell (High Elves)](#lindon-and-rivendell-high-elves) | 26 | 11 |
| [Lothlórien (Galadhrim)](#lothlrien-galadhrim) | 47 | 15 |
| [Dol Guldur (southern Mirkwood)](#dol-guldur-southern-mirkwood) | 19 | 10 |
| [Mirkwood and the Woodland Realm](#mirkwood-and-the-woodland-realm) | 40 | 16 |
| [Dale and Esgaroth](#dale-and-esgaroth) | 16 | 10 |
| [Dwarves (Durin's folk and Blue Mountains)](#dwarves-durins-folk-and-blue-mountains) | 33 | 11 |
| [Dunland](#dunland) | 1 | 0 |
| [Rohan](#rohan) | 18 | 8 |
| [Fangorn and the Ents](#fangorn-and-the-ents) | 3 | 0 |
| [Gondor](#gondor) | 66 | 34 |
| [Mordor and Minas Morgul](#mordor-and-minas-morgul) | 65 | 22 |
| [Dead Marshes](#dead-marshes) | 2 | 0 |
| [Rhûn and Dorwinion](#rhn-and-dorwinion) | 51 | 31 |
| [Near Harad and Umbar](#near-harad-and-umbar) | 62 | 29 |
| [Far Harad and the jungles](#far-harad-and-the-jungles) | 103 | 43 |
| [Utumno](#utumno) | 34 | 21 |
| [General (shared trees, crops, gems, dyed and plain blocks)](#general-shared-trees-crops-gems-dyed-and-plain-blocks) | 688 | 237 |
| **Total** | **1329** | **523** |

### The Shire

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 1 | shire pine wood slab |
| Stairs | 1 | shire pine wood stairs |
| Fences and fence gates | 2 | shire pine fence, shire pine fence gate |
| Doors, trapdoors and gates | 6 | blue hobbit gate, green hobbit gate, red hobbit gate, shire pine door, shire pine trapdoor, yellow hobbit gate |
| Crafting tables | 1 | hobbit crafting table |
| Planks | 1 | shire pine wood planks |
| Wood, logs and beams | 2 | shire pine wood, shire pine wood beam |
| Leaves and vines | 1 | shire pine leaves |
| Saplings | 1 | shire pine sapling |
| Plants and flowers | 1 | shire heather |
| Utility and furniture | 1 | hobbit oven |

### Bree-land

| Type | Count | Blocks |
| --- | --- | --- |
| Crafting tables | 1 | bree-land crafting table |

### Arnor and the Dúnedain of the North

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 5 | arnor brick slab, arnor pillar slab, cracked arnor brick slab, cracked arnor pillar slab, mossy arnor brick slab |
| Stairs | 3 | arnor brick stairs, cracked arnor brick stairs, mossy arnor brick stairs |
| Walls | 3 | arnor brick wall, cracked arnor brick wall, mossy arnor brick wall |
| Pillars | 2 | arnor pillar, cracked arnor pillar |
| Crafting tables | 1 | ranger crafting table |
| Bricks and stone | 4 | arnor brick, carved arnor brick, cracked arnor brick, mossy arnor brick |

### Angmar

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 4 | angmar brick slab, angmar pillar slab, cracked angmar brick slab, snowy angmar brick slab |
| Stairs | 3 | angmar brick stairs, cracked angmar brick stairs, snowy angmar brick stairs |
| Walls | 3 | angmar brick wall, cracked angmar brick wall, snowy angmar brick wall |
| Pillars | 1 | angmar pillar |
| Crafting tables | 1 | angmar crafting table |
| Bricks and stone | 5 | angmar brick, cracked angmar brick, gulduril angmar brick, gulduril cracked angmar brick, snowy angmar brick |

### Gundabad and the Misty Mountains

| Type | Count | Blocks |
| --- | --- | --- |
| Crafting tables | 1 | gundabad crafting table |

### Lindon and Rivendell (High Elves)

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 5 | cracked high elven brick slab, cracked high elven pillar slab, high elven brick slab, high elven pillar slab, mossy high elven brick slab |
| Stairs | 3 | cracked high elven brick stairs, high elven brick stairs, mossy high elven brick stairs |
| Walls | 3 | cracked high elven brick wall, high elven brick wall, mossy high elven brick wall |
| Doors, trapdoors and gates | 1 | high elven gate |
| Bars and panes | 2 | high elven steel bars, high elven wood-bars |
| Pillars | 2 | cracked high elven pillar, high elven pillar |
| Crafting tables | 2 | lindon crafting table, rivendell crafting table |
| Bricks and stone | 6 | carved high elven brick, cracked high elven brick, gold-trimmed high elven brick, high elven brick, mossy high elven brick, silver-trimmed high elven brick |
| Lighting | 2 | high elven chandelier, high elven torch |

### Lothlórien (Galadhrim)

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 6 | cracked galadhrim brick slab, cracked galadhrim pillar slab, galadhrim brick slab, galadhrim pillar slab, mallorn wood slab, mossy galadhrim brick slab |
| Stairs | 4 | cracked galadhrim brick stairs, galadhrim brick stairs, mallorn wood stairs, mossy galadhrim brick stairs |
| Walls | 3 | cracked galadhrim brick wall, galadhrim brick wall, mossy galadhrim brick wall |
| Fences and fence gates | 2 | mallorn fence, mallorn fence gate |
| Doors, trapdoors and gates | 3 | galadhrim gate, mallorn door, mallorn trapdoor |
| Bars and panes | 2 | galadhrim steel bars, galadhrim wood-bars |
| Pillars | 2 | cracked galadhrim pillar, galadhrim pillar |
| Crafting tables | 1 | galadhrim crafting table |
| Planks | 1 | mallorn wood planks |
| Wood, logs and beams | 2 | mallorn wood, mallorn wood beam |
| Leaves and vines | 1 | mallorn leaves |
| Saplings | 2 | corrupt mallorn sapling, mallorn sapling |
| Bricks and stone | 6 | carved galadhrim brick, cracked galadhrim brick, galadhrim brick, gold-trimmed galadhrim brick, mossy galadhrim brick, silver-trimmed galadhrim brick |
| Lighting | 8 | blue mallorn chandelier, blue mallorn torch, golden mallorn chandelier, golden mallorn torch, green mallorn chandelier, green mallorn torch, silver mallorn chandelier, silver mallorn torch |
| Plants and flowers | 2 | elanor, niphredil |
| Utility and furniture | 1 | mallorn ladder |
| Other | 1 | mallorn box |

### Dol Guldur (southern Mirkwood)

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 4 | cracked dol guldur brick slab, dol guldur brick slab, dol guldur pillar slab, mossy dol guldur brick slab |
| Stairs | 3 | cracked dol guldur brick stairs, dol guldur brick stairs, mossy dol guldur brick stairs |
| Walls | 3 | cracked dol guldur brick wall, dol guldur brick wall, mossy dol guldur brick wall |
| Pillars | 1 | dol guldur pillar |
| Crafting tables | 1 | dol guldur crafting table |
| Bricks and stone | 6 | carved dol guldur brick, cracked dol guldur brick, dol guldur brick, gulduril cracked dol guldur brick, gulduril dol guldur brick, mossy dol guldur brick |
| Other | 1 | khamûl's fire |

### Mirkwood and the Woodland Realm

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 6 | cracked wood-elven brick slab, cracked wood-elven pillar slab, mirk-oak wood slab, mossy wood-elven brick slab, wood-elven brick slab, wood-elven pillar slab |
| Stairs | 4 | cracked wood-elven brick stairs, mirk-oak wood stairs, mossy wood-elven brick stairs, wood-elven brick stairs |
| Walls | 3 | cracked wood-elven brick wall, mossy wood-elven brick wall, wood-elven brick wall |
| Fences and fence gates | 3 | dark oak fence gate, mirk-oak fence, mirk-oak fence gate |
| Doors, trapdoors and gates | 5 | dark oak door, dark oak trapdoor, mirk-oak door, mirk-oak trapdoor, wood-elven gate |
| Bars and panes | 2 | wood-elven steel bars, wood-elven wood-bars |
| Pillars | 2 | cracked wood-elven pillar, wood-elven pillar |
| Crafting tables | 1 | wood-elven crafting table |
| Planks | 1 | mirk-oak wood planks |
| Wood, logs and beams | 11 | carved wood-elven brick, cracked wood-elven brick, dark oak wood beam, gold-trimmed wood-elven brick, mirk-oak wood, mirk-oak wood beam, mossy wood-elven brick, silver-trimmed wood-elven brick, wood-elven brick, wood-elven chandelier, wood-elven torch |
| Leaves and vines | 1 | mirk-oak leaves |
| Saplings | 1 | mirk-oak sapling |

### Dale and Esgaroth

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 4 | cracked dalish brick slab, dalish brick slab, dalish pillar slab, mossy dalish brick slab |
| Stairs | 3 | cracked dalish brick stairs, dalish brick stairs, mossy dalish brick stairs |
| Walls | 3 | cracked dalish brick wall, dalish brick wall, mossy dalish brick wall |
| Pillars | 1 | dalish pillar |
| Crafting tables | 1 | dalish crafting table |
| Bricks and stone | 4 | carved dalish brick, cracked dalish brick, dalish brick, mossy dalish brick |

### Dwarves (Durin's folk and Blue Mountains)

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 5 | cracked dwarven brick slab, cracked dwarven pillar slab, dwarven brick slab, dwarven pillar slab, obsidian dwarven brick slab |
| Stairs | 3 | cracked dwarven brick stairs, dwarven brick stairs, obsidian dwarven brick stairs |
| Walls | 3 | cracked dwarven brick wall, dwarven brick wall, obsidian dwarven brick wall |
| Doors, trapdoors and gates | 3 | dwarven door, dwarven gate, ithildin dwarven door |
| Bars and panes | 2 | blue dwarven steel bars, dwarven steel bars |
| Pillars | 2 | cracked dwarven pillar, dwarven pillar |
| Crafting tables | 1 | dwarven crafting table |
| Storage and gem blocks | 2 | block of blue dwarven steel, block of dwarven steel |
| Bricks and stone | 8 | carved dwarven brick, cracked dwarven brick, dwarven brick, glowing dwarven brick, gold-trimmed dwarven brick, mithril-trimmed dwarven brick, obsidian dwarven brick, silver-trimmed dwarven brick |
| Lighting | 2 | blue dwarven chandelier, dwarven chandelier |
| Plants and flowers | 1 | dwarfwort |
| Utility and furniture | 1 | dwarven forge |

### Dunland

| Type | Count | Blocks |
| --- | --- | --- |
| Crafting tables | 1 | dunlending crafting table |

### Rohan

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 4 | rohan brick slab, rohan pillar slab, rohan rock slab, smooth rohan rock slab |
| Stairs | 2 | rohan brick stairs, rohan rock stairs |
| Walls | 2 | rohan brick wall, rohan rock wall |
| Doors, trapdoors and gates | 1 | rohirric gate |
| Pillars | 1 | rohan pillar |
| Crafting tables | 1 | rohirric crafting table |
| Wood, logs and beams | 2 | gold-trimmed rohan wood beam, rohan wood beam |
| Bricks and stone | 4 | carved rohan brick, rohan brick, rohan rock, smooth rohan rock |
| Plants and flowers | 1 | simbelmynë |

### Fangorn and the Ents

| Type | Count | Blocks |
| --- | --- | --- |
| Lighting | 1 | ent jar |
| Other | 2 | fangorn's beard, huorn leaf |

### Gondor

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 13 | cracked gondor brick slab, cracked gondor cobblebrick slab, dol amroth brick slab, gondor brick slab, gondor cobblebrick slab, gondor pillar slab, gondor rock slab, lebethron wood slab, mossy gondor brick slab, mossy gondor cobblebrick slab, númenórean brick slab, númenórean pillar slab, smooth gondor rock slab |
| Stairs | 10 | cracked gondor brick stairs, cracked gondor cobblebrick stairs, dol amroth brick stairs, gondor brick stairs, gondor cobblebrick stairs, gondor rock stairs, lebethron wood stairs, mossy gondor brick stairs, mossy gondor cobblebrick stairs, númenórean brick stairs |
| Walls | 9 | cracked gondor brick wall, cracked gondor cobblebrick wall, dol amroth brick wall, gondor brick wall, gondor cobblebrick wall, gondor rock wall, mossy gondor brick wall, mossy gondor cobblebrick wall, númenórean brick wall |
| Fences and fence gates | 2 | lebethron fence, lebethron fence gate |
| Doors, trapdoors and gates | 4 | dol amroth gate, gondorian gate, lebethron door, lebethron trapdoor |
| Pillars | 2 | gondor pillar, númenórean pillar |
| Crafting tables | 2 | dol amroth crafting table, gondorian crafting table |
| Planks | 1 | lebethron wood planks |
| Wood, logs and beams | 2 | lebethron wood, lebethron wood beam |
| Leaves and vines | 1 | lebethron leaves |
| Saplings | 1 | lebethron sapling |
| Bricks and stone | 15 | carved black gondor brick, carved gondor brick, cracked gondor brick, cracked gondor cobblebrick, dol amroth brick, gondor brick, gondor rock, gulduril cracked gondor brick, gulduril gondor brick, gulduril mossy gondor brick, gulduril númenórean brick, mossy gondor brick, mossy gondor cobblebrick, númenórean brick, smooth gondor rock |
| Other | 4 | beacon of gondor, blackroot, gondor cobblebrick, lebethron casket |

### Mordor and Minas Morgul

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 12 | cracked mordor brick slab, mordor brick slab, mordor dirt slab, mordor gravel slab, mordor pillar slab, mordor rock slab, obsidian gravel slab, obsidian taurethrim brick slab, obsidian taurethrim pillar slab, smooth mordor rock slab, uruk brick slab, uruk pillar slab |
| Stairs | 5 | cracked mordor brick stairs, mordor brick stairs, mordor rock stairs, obsidian taurethrim brick stairs, uruk brick stairs |
| Walls | 5 | cracked mordor brick wall, mordor brick wall, mordor rock wall, obsidian taurethrim brick wall, uruk brick wall |
| Doors, trapdoors and gates | 2 | orc gate, uruk gate |
| Bars and panes | 2 | orc steel bars, uruk steel bars |
| Pillars | 3 | mordor pillar, obsidian taurethrim pillar, uruk pillar |
| Crafting tables | 2 | morgul crafting table, uruk crafting table |
| Ores and veins | 2 | morgul iron ore |
| Storage and gem blocks | 4 | block of black uruk steel, block of morgul steel, block of orc steel, block of uruk steel |
| Bricks and stone | 9 | carved mordor brick, cracked mordor brick, gulduril cracked mordor brick, gulduril mordor brick, mordor brick, mordor rock, obsidian taurethrim brick, smooth mordor rock, uruk brick |
| Lighting | 4 | morgul chandelier, morgul torch, orc chandelier, uruk chandelier |
| Clay, glass, thatch and earth | 3 | mordor dirt, mordor gravel, obsidian gravel |
| Plants and flowers | 1 | morgul-shroom |
| Utility and furniture | 4 | obsidian taurethrim dart trap, orc bomb, orc chain, orc forge |
| Other | 7 | mordor scrub, morgul-flowers, obsidian stalactite, obsidian stalagmite, orc plating, rusted orc plating, web of ungoliant |

### Dead Marshes

| Type | Count | Blocks |
| --- | --- | --- |
| Other | 2 | dead marsh plant, quagmire |

### Rhûn and Dorwinion

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 13 | cracked dorwinion brick slab, cracked rhûnic brick slab, dorwinion brick slab, dorwinion pillar slab, flowery dorwinion brick slab, flowery rhûnic brick slab, mossy dorwinion brick slab, mossy dorwinion pillar slab, mossy rhûnic brick slab, red rhûnic brick slab, red rhûnic pillar slab, rhûnic brick slab, rhûnic pillar slab |
| Stairs | 9 | cracked dorwinion brick stairs, cracked rhûnic brick stairs, dorwinion brick stairs, flowery dorwinion brick stairs, flowery rhûnic brick stairs, mossy dorwinion brick stairs, mossy rhûnic brick stairs, red rhûnic brick stairs, rhûnic brick stairs |
| Walls | 9 | cracked dorwinion brick wall, cracked rhûnic brick wall, dorwinion brick wall, flowery dorwinion brick wall, flowery rhûnic brick wall, mossy dorwinion brick wall, mossy rhûnic brick wall, red rhûnic brick wall, rhûnic brick wall |
| Doors, trapdoors and gates | 1 | rhûnic gate |
| Pillars | 4 | dorwinion pillar, mossy dorwinion pillar, red rhûnic pillar, rhûnic pillar |
| Crafting tables | 2 | dorwinion crafting table, rhûnic crafting table |
| Bricks and stone | 13 | carved dorwinion brick, carved red rhûnic brick, carved rhûnic brick, cracked dorwinion brick, cracked rhûnic brick, dorwinion brick, flowery dorwinion brick, flowery rhûnic brick, gold-trimmed rhûnic brick, mossy dorwinion brick, mossy rhûnic brick, red rhûnic brick, rhûnic brick |

### Near Harad and Umbar

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 11 | cracked haradric brick slab, cracked red haradric brick slab, cracked umbar brick slab, date palm wood slab, haradric brick slab, haradric pillar slab, palm wood slab, red haradric brick slab, red haradric pillar slab, umbar brick slab, umbar pillar slab |
| Stairs | 8 | cracked haradric brick stairs, cracked red haradric brick stairs, cracked umbar brick stairs, date palm wood stairs, haradric brick stairs, palm wood stairs, red haradric brick stairs, umbar brick stairs |
| Walls | 6 | cracked haradric brick wall, cracked red haradric brick wall, cracked umbar brick wall, haradric brick wall, red haradric brick wall, umbar brick wall |
| Fences and fence gates | 4 | date palm fence, date palm fence gate, palm fence, palm fence gate |
| Doors, trapdoors and gates | 5 | date palm door, date palm trapdoor, haradric gate, palm door, palm trapdoor |
| Pillars | 3 | haradric pillar, red haradric pillar, umbar pillar |
| Crafting tables | 1 | haradric crafting table |
| Planks | 2 | date palm wood planks, palm wood planks |
| Wood, logs and beams | 4 | date palm wood, date palm wood beam, palm wood, palm wood beam |
| Leaves and vines | 2 | date palm leaves, palm leaves |
| Saplings | 2 | date palm sapling, palm sapling |
| Bricks and stone | 11 | carved black umbar brick, carved haradric brick, carved red haradric brick, carved umbar brick, cracked haradric brick, cracked red haradric brick, cracked umbar brick, haradric brick, lapis-trimmed haradric brick, red haradric brick, umbar brick |
| Utility and furniture | 1 | ancient haradric chest |
| Other | 2 | flame of harad, harad daisy |

### Far Harad and the jungles

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 15 | banana wood slab, baobab wood slab, cracked morwaith brick slab, cracked taurethrim brick slab, golden taurethrim brick slab, golden taurethrim pillar slab, jungle mud path slab, jungle mud slab, kanuka wood slab, mahogany wood slab, mango wood slab, morwaith brick slab, mossy taurethrim brick slab, taurethrim brick slab, taurethrim pillar slab |
| Stairs | 11 | banana wood stairs, baobab wood stairs, cracked morwaith brick stairs, cracked taurethrim brick stairs, golden taurethrim brick stairs, kanuka wood stairs, mahogany wood stairs, mango wood stairs, morwaith brick stairs, mossy taurethrim brick stairs, taurethrim brick stairs |
| Walls | 6 | cracked morwaith brick wall, cracked taurethrim brick wall, golden taurethrim brick wall, morwaith brick wall, mossy taurethrim brick wall, taurethrim brick wall |
| Fences and fence gates | 11 | banana fence, banana fence gate, baobab fence, baobab fence gate, jungle fence gate, kanuka fence, kanuka fence gate, mahogany fence, mahogany fence gate, mango fence, mango fence gate |
| Doors, trapdoors and gates | 13 | banana door, banana trapdoor, baobab door, baobab trapdoor, jungle door, jungle trapdoor, kanuka door, kanuka trapdoor, mahogany door, mahogany trapdoor, mango door, mango trapdoor, taurethrim gate |
| Pillars | 2 | golden taurethrim pillar, taurethrim pillar |
| Crafting tables | 3 | half-troll crafting table, morwaith crafting table, taurethrim crafting table |
| Planks | 5 | banana wood planks, baobab wood planks, kanuka wood planks, mahogany wood planks, mango wood planks |
| Wood, logs and beams | 11 | banana wood, banana wood beam, baobab wood, baobab wood beam, jungle wood beam, kanuka wood, kanuka wood beam, mahogany wood, mahogany wood beam, mango wood, mango wood beam |
| Leaves and vines | 5 | banana leaves, baobab leaves, kanuka leaves, mahogany leaves, mango leaves |
| Saplings | 5 | banana sapling, baobab sapling, kanuka sapling, mahogany sapling, mango sapling |
| Bricks and stone | 6 | cracked morwaith brick, cracked taurethrim brick, golden taurethrim brick, morwaith brick, mossy taurethrim brick, taurethrim brick |
| Clay, glass, thatch and earth | 4 | barren jungle mud, jungle mud, jungle mud farmland, jungle mud path |
| Plants and flowers | 1 | jungle grass |
| Utility and furniture | 2 | golden taurethrim dart trap, taurethrim dart trap |
| Other | 3 | infested termite mound, jungle sprout, termite mound |

### Utumno

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 9 | fire utumno brick slab, fire utumno pillar slab, fire utumno tiling slab, ice utumno brick slab, ice utumno pillar slab, ice utumno tiling slab, obsidian utumno brick slab, obsidian utumno pillar slab, obsidian utumno tiling slab |
| Stairs | 6 | fire utumno brick stairs, fire utumno tiling stairs, ice utumno brick stairs, ice utumno tiling stairs, obsidian utumno brick stairs, obsidian utumno tiling stairs |
| Walls | 6 | fire utumno brick wall, fire utumno tiling wall, ice utumno brick wall, ice utumno tiling wall, obsidian utumno brick wall, obsidian utumno tiling wall |
| Pillars | 3 | fire utumno pillar, ice utumno pillar, obsidian utumno pillar |
| Bricks and stone | 7 | burning utumno brick, fiery obsidian utumno brick, fire utumno brick, glowing ice utumno brick, ice utumno brick, obsidian utumno brick, utumno entrance brick |
| Other | 3 | fire utumno tiling, ice utumno tiling, obsidian utumno tiling |

### General (shared trees, crops, gems, dyed and plain blocks)

| Type | Count | Blocks |
| --- | --- | --- |
| Slabs | 78 | almond wood slab, apple wood slab, aspen wood slab, beech wood slab, black clay tiling slab, blue clay tiling slab, bone slab, brick pillar slab, brown clay tiling slab, cargon brick slab, cargon pillar slab, cargon slab, cedar wood slab, chalk brick slab, chalk pillar slab, chalk slab, charred wood slab, cherry wood slab, chestnut wood slab, clay tiling slab, cracked bricks slab, cracked stone brick slab, cyan clay tiling slab, cypress wood slab, dirt path slab, dirt slab, dragonblood wood slab, fir wood slab, gravel slab, gray clay tiling slab, green clay tiling slab, green-oak wood slab, holly wood slab, lairelossë wood slab, larch wood slab, lemon wood slab, light blue clay tiling slab, light gray clay tiling slab, lime clay tiling slab, lime wood slab, luigon brick slab, luigon pillar slab, luigon slab, magenta clay tiling slab, mangrove wood slab, maple wood slab, mossy bricks slab, mossy cobblestone slab, mossy stone brick slab, mud brick slab, olive wood slab, orange clay tiling slab, orange wood slab, pear wood slab, pine wood slab, pink clay tiling slab, plum wood slab, pomegranate wood slab, purple clay tiling slab, red clay tiling slab, red sand slab, red sandstone slab, redwood wood slab, reed slab, rotten wood slab, sand slab, scorched stone slab, smooth cargon slab, smooth chalk slab, smooth luigon slab, stone pillar slab, stone slab, thatch slab, white clay tiling slab, white sand slab, white sandstone slab, willow wood slab, yellow clay tiling slab |
| Stairs | 64 | almond wood stairs, apple wood stairs, aspen wood stairs, beech wood stairs, black clay tiling stairs, blue clay tiling stairs, bone stairs, brown clay tiling stairs, cargon brick stairs, cargon stairs, cedar wood stairs, chalk brick stairs, chalk stairs, charred wood stairs, cherry wood stairs, chestnut wood stairs, clay tiling stairs, cracked brick stairs, cracked stone brick stairs, cyan clay tiling stairs, cypress wood stairs, dragonblood wood stairs, fir wood stairs, gray clay tiling stairs, green clay tiling stairs, green-oak wood stairs, holly wood stairs, lairelossë wood stairs, larch wood stairs, lemon wood stairs, light blue clay tiling stairs, light gray clay tiling stairs, lime clay tiling stairs, lime wood stairs, luigon brick stairs, luigon stairs, magenta clay tiling stairs, mangrove wood stairs, maple wood stairs, mossy brick stairs, mossy cobblestone stairs, mossy stone brick stairs, mud brick stairs, olive wood stairs, orange clay tiling stairs, orange wood stairs, pear wood stairs, pine wood stairs, pink clay tiling stairs, plum wood stairs, pomegranate wood stairs, purple clay tiling stairs, red clay tiling stairs, red sandstone stairs, redwood wood stairs, reed stairs, rotten wood stairs, scorched stone stairs, stone stairs, thatch stairs, white clay tiling stairs, white sandstone stairs, willow wood stairs, yellow clay tiling stairs |
| Walls | 36 | black clay tiling wall, blue clay tiling wall, bone wall, brick wall, brown clay tiling wall, cargon brick wall, cargon wall, chalk brick wall, chalk wall, clay tiling wall, cracked brick wall, cracked stone brick wall, cyan clay tiling wall, gray clay tiling wall, green clay tiling wall, light blue clay tiling wall, light gray clay tiling wall, lime clay tiling wall, luigon brick wall, luigon wall, magenta clay tiling wall, mossy brick wall, mossy stone brick wall, mud brick wall, orange clay tiling wall, pink clay tiling wall, purple clay tiling wall, red clay tiling wall, red sandstone wall, sandstone wall, scorched stone wall, stone brick wall, stone wall, white clay tiling wall, white sandstone wall, yellow clay tiling wall |
| Fences and fence gates | 59 | acacia fence gate, almond fence, almond fence gate, apple fence, apple fence gate, aspen fence, aspen fence gate, beech fence, beech fence gate, birch fence gate, cedar fence, cedar fence gate, charred fence, charred fence gate, cherry fence, cherry fence gate, chestnut fence, chestnut fence gate, cypress fence, cypress fence gate, dragonblood fence, dragonblood fence gate, fir fence, fir fence gate, green-oak fence, green-oak fence gate, holly fence, holly fence gate, lairelossë fence, lairelossë fence gate, larch fence, larch fence gate, lemon fence, lemon fence gate, lime fence, lime fence gate, mangrove fence, mangrove fence gate, maple fence, maple fence gate, olive fence, olive fence gate, orange fence, orange fence gate, pear fence, pear fence gate, pine fence, pine fence gate, plum fence, plum fence gate, pomegranate fence, pomegranate fence gate, redwood fence, redwood fence gate, rotten fence, rotten fence gate, spruce fence gate, willow fence, willow fence gate |
| Doors, trapdoors and gates | 69 | acacia door, acacia trapdoor, almond door, almond trapdoor, apple door, apple trapdoor, aspen door, aspen trapdoor, beech door, beech trapdoor, birch door, birch trapdoor, bronze portcullis, cedar door, cedar trapdoor, charred door, charred trapdoor, cherry door, cherry trapdoor, chestnut door, chestnut trapdoor, cypress door, cypress trapdoor, dragonblood door, dragonblood trapdoor, fir door, fir trapdoor, golden gate, green-oak door, green-oak trapdoor, holly door, holly trapdoor, iron portcullis, lairelossë door, lairelossë trapdoor, larch door, larch trapdoor, lemon door, lemon trapdoor, lime door, lime trapdoor, mangrove door, mangrove trapdoor, maple door, maple trapdoor, mithril gate, olive door, olive trapdoor, orange door, orange trapdoor, pear door, pear trapdoor, pine door, pine trapdoor, plum door, plum trapdoor, pomegranate door, pomegranate trapdoor, redwood door, redwood trapdoor, rotten door, rotten trapdoor, silver gate, spruce door, spruce trapdoor, willow door, willow trapdoor, wooden gate, wooden portcullis |
| Bars and panes | 22 | black fine glass pane, blue fine glass pane, bronze bars, brown fine glass pane, cyan fine glass pane, fine glass pane, gold bars, gray fine glass pane, green fine glass pane, light blue fine glass pane, light gray fine glass pane, lime fine glass pane, magenta fine glass pane, mithril bars, orange fine glass pane, pink fine glass pane, purple fine glass pane, red fine glass pane, reed bars, silver bars, white fine glass pane, yellow fine glass pane |
| Pillars | 5 | brick pillar, cargon pillar, chalk pillar, luigon pillar, stone pillar |
| Crafting tables | 3 | blue mountains crafting table, gulfen crafting table, umbaric crafting table |
| Ores and veins | 19 | amber vein, amethyst vein, copper ore, diamond vein, durnor ore, edhelvir ore, emerald vein, glowstone ore, gulduril ore, mithril ore, niter ore, opal vein, ruby vein, sapphire vein, silver ore, sulfur ore, tin ore, topaz vein |
| Storage and gem blocks | 31 | amber block, amethyst block, block of bronze, block of copper, block of durnor, block of edhelvir, block of elven steel, block of galvorn, block of gilded iron, block of gulduril, block of kebab, block of mithril, block of niter, block of salt, block of silver, block of sulfur, block of tin, bone block, coral block, diamond block, emerald block, hearth block, marchpane block, opal block, pearl block, reed block, ruby block, sapphire block, thatch block, topaz block, waste block |
| Planks | 28 | almond wood planks, apple wood planks, aspen wood planks, beech wood planks, cedar wood planks, charred wood planks, cherry wood planks, chestnut wood planks, cypress wood planks, dragonblood wood planks, fir wood planks, green-oak wood planks, holly wood planks, lairelossë wood planks, larch wood planks, lemon wood planks, lime wood planks, mangrove wood planks, maple wood planks, olive wood planks, orange wood planks, pear wood planks, pine wood planks, plum wood planks, pomegranate wood planks, redwood wood planks, rotten planks, willow wood planks |
| Wood, logs and beams | 60 | acacia wood beam, almond wood, almond wood beam, apple wood, apple wood beam, aspen wood, aspen wood beam, beech wood, beech wood beam, birch wood beam, cedar wood, cedar wood beam, charred wood, charred wood beam, cherry wood, cherry wood beam, chestnut wood, chestnut wood beam, cypress wood, cypress wood beam, dragonblood wood, dragonblood wood beam, fir wood, fir wood beam, green-oak wood, green-oak wood beam, holly wood, holly wood beam, lairelossë wood, lairelossë wood beam, larch wood, larch wood beam, lemon wood, lemon wood beam, lime wood, lime wood beam, mangrove wood, mangrove wood beam, maple wood, maple wood beam, oak wood beam, olive wood, olive wood beam, orange wood, orange wood beam, pear wood, pear wood beam, pine wood, pine wood beam, plum wood, plum wood beam, pomegranate wood, pomegranate wood beam, redwood wood, redwood wood beam, rotten wood, rotten wood beam, spruce wood beam, willow wood, willow wood beam |
| Leaves and vines | 29 | almond leaves, apple leaves, aspen leaves, beech leaves, cedar leaves, cherry leaves, chestnut leaves, cypress leaves, dragonblood leaves, fir leaves, green-oak leaves, holly leaves, lairelossë leaves, larch leaves, lemon leaves, lime leaves, mangrove leaves, maple leaves, mirkwood vines, olive leaves, orange leaves, pear leaves, pine leaves, plum leaves, pomegranate leaves, red-oak leaves, redwood leaves, willow leaves, willow vines |
| Saplings | 27 | almond sapling, apple sapling, aspen sapling, beech sapling, cedar sapling, cherry sapling, chestnut sapling, cypress sapling, dragonblood sapling, fir sapling, green-oak sapling, holly sapling, lairelossë sapling, larch sapling, lemon sapling, lime sapling, mangrove sapling, maple sapling, olive sapling, orange sapling, pear sapling, pine sapling, plum sapling, pomegranate sapling, red-oak sapling, redwood sapling, willow sapling |
| Bricks and stone | 17 | cargon brick, carved cargon brick, carved luigon brick, chalk, chalk brick, cracked bricks, luigon brick, mossy bricks, mud brick, red sandstone, rock salt, sandstone kebab stand, scorched stone, smooth chalk, smooth stone, stone chest, white sandstone |
| Crops, bushes and food | 15 | bare blackberry bush, bare blueberry bush, bare cranberry bush, bare elderberry bush, bare raspberry bush, bare wildberry bush, blackberry bush, blueberry bush, corn stalks, cranberry bush, elderberry bush, flax plant, kebab stand, raspberry bush, wildberry bush |
| Lighting | 6 | bronze chandelier, butterfly jar, gold chandelier, iron chandelier, mithril chandelier, silver chandelier |
| Clay, glass, thatch and earth | 44 | black clay tiling, black fine glass, blue clay tiling, blue fine glass, brown clay tiling, brown fine glass, clay tiling, cyan clay tiling, cyan fine glass, dirt path, dried reeds, fine glass, gray clay tiling, gray fine glass, green clay tiling, green fine glass, light blue clay tiling, light blue fine glass, light gray clay tiling, light gray fine glass, lime clay tiling, lime fine glass, magenta clay tiling, magenta fine glass, orange clay tiling, orange fine glass, pink clay tiling, pink fine glass, purple clay tiling, purple fine glass, red clay, red clay tiling, red fine glass, red sand gem, reed basket, reeds, thatch flooring, wattle and daub, white clay tiling, white fine glass, white sand, yellow clay tiling, yellow fine glass, yellow sand gem |
| Plants and flowers | 31 | arid grass, asphodel, athelas, black iris, blue chrysanthemum, bluebell, clover, coral reef, elfsong, elvengrass, flowery grass, four-leaf clover, hibiscus, lavender, marigold, moonflower, nettles, orange chrysanthemum, pink chrysanthemum, pipe-weed plant, riverweed, short grass, southbells, stabbing thorn, tears of yavanna, thistle, wheatgrass, white chrysanthemum, withered moss, yellow chrysanthemum, yellow iris |
| Utility and furniture | 24 | alloy forge, barrel, bronze bird cage, copper treasure pile, elven forge, elven rope, gold treasure pile, golden bird cage, iron bird cage, millstone, mob spawner, pressure plate, rope, silver bird cage, silver treasure pile, table of command, unsmeltery, weapon rack, wooden bird cage |
| Other | 21 | button, cargon, drystone, grapevine post, ice stalactite, ice stalagmite, luigon, remains, smooth cargon, smooth luigon, stalactite, stalagmite, sunfruit, troll totem base, troll totem body, troll totem head |

## Creatures and NPCs (entity skins)

577 skin textures across 54 groups. Numbers in brackets are how many skin variants exist; NPCs have many face and outfit variants each.

| Group | Skins | Variants |
| --- | --- | --- |
| aurochs | 4 | (base) (4) |
| balrog | 3 | (base) (1), balrog (1), balrog bright (1) |
| bandit | 11 | bandit (6), harad (5) |
| barrow wight | 1 | wight (1) |
| bear | 3 | (base) (3) |
| bird | 23 | common (9), crebain (1), crow (1), far harad (9), gorcrow (1), magpie (1), seagull (1) |
| boar | 2 | (base) (2) |
| bree | 54 | (base) (8), bree female (9), bree male (30), headwear female (1), ruffian (5), ruffian hood (1) |
| butterfly | 12 | common (5), jungle (5), lorien (1), mirkwood (1) |
| camel | 4 | (base) (4) |
| char | 9 | (base) (9) |
| crocodile | 1 | (base) (1) |
| dale | 10 | (base) (2), dale female (2), dale male (3), dale soldier (3) |
| deer | 2 | (base) (2) |
| dikdik | 3 | (base) (3) |
| dorwinion | 12 | dorwinion female (4), dorwinion male (4), outfit (4) |
| dunland | 12 | (base) (1), berserker (1), dunlending female (3), dunlending male (4), outfit (3) |
| dwarf | 18 | (base) (5), blue mountains female (3), blue mountains male (3), dwarf female (3), dwarf male (3), wicked male (1) |
| elf | 71 | (base) (12), dorwinion female (3), dorwinion male (3), galadhrim female (3), galadhrim male (4), high elf female (11), high elf male (18), jazz female (3), jazz male (3), jazz outfit (1), tormented (3), wood elf female (3), wood elf male (4) |
| elk | 4 | (base) (1), elk (3) |
| ent | 5 | (base) (5) |
| fish | 6 | clownfish (1), common (4), salmon (1) |
| flamingo | 2 | (base) (2) |
| gemsbok | 1 | (base) (1) |
| giraffe | 2 | (base) (2) |
| gondor | 47 | (base) (8), gondor female (14), gondor male (10), gondor soldier (6), headwear female (2), outfit (1), ranger (3), swan knight (3) |
| half troll | 6 | (base) (3), half troll (3) |
| hillman | 8 | hillman female (4), hillman male (3), outfit (1) |
| hobbit | 38 | (base) (6), child female (3), child male (3), hobbit female (13), hobbit male (13) |
| huorn | 5 | face (5) |
| kine araw | 2 | (base) (2) |
| lion | 3 | (base) (3) |
| midge | 1 | (base) (1) |
| moredain | 14 | moredain female (4), moredain male (5), outfit (5) |
| near harad | 45 | (base) (3), haradrim female (3), haradrim male (5), harnedor female (3), harnedor male (5), harnedor outfit (3), harnedor warrior (5), nomad female (3), nomad hat (2), nomad male (5), renegade hood (1), slave gondor male (2), slave morwaith male (1), slave near harad male (2), slave taurethrim male (1), warrior (1) |
| nurn | 7 | slave female (3), slave male (4) |
| orc | 14 | black uruk (3), orc (8), uruk hai (3) |
| rabbit | 5 | (base) (5) |
| ranger | 9 | (base) (1), ranger female (3), ranger male (5) |
| rhino | 2 | (base) (2) |
| rhun | 11 | (base) (1), easterling female (5), easterling male (5) |
| rohan | 29 | (base) (7), rohan female (7), rohan male (6), shieldmaiden (3), warrior (6) |
| scorpion | 2 | (base) (2) |
| scrap trader | 2 | (base) (2) |
| spider | 5 | (base) (5) |
| swan | 1 | (base) (1) |
| tauredain | 12 | outfit (4), shaman outfit (1), tauredain female (3), tauredain male (4) |
| termite | 1 | (base) (1) |
| troll | 18 | (base) (6), mirk troll (1), mirk troll armor (1), mountain troll (1), olog hai (1), olog hai armor (2), snow troll (1), troll (1), utumno (4) |
| warg | 8 | (base) (8) |
| white oryx | 3 | (base) (3) |
| wraith | 2 | (base) (2) |
| zebra | 1 | (base) (1) |

## Worn armour

151 worn-armour layer textures covering 68 sets, plus 20 mount armour textures (horse, warg and other mount barding). Sets: angmar, arnor, black numenorean, black uruk, blackroot, blue dwarven, bone, bronze, corsair, dale, dol amroth, dol guldur, dorwinion, dorwinion elf, dunlending, dwarven, dwarven gold, dwarven mithril, dwarven silver, fur, galadhrim, galvorn, gambeson dale, gambeson dol amroth, gambeson dol amroth legs, gambeson gondor, gambeson lebennin, gemsbok, gondolin, gondor, gulf harad, gundabad uruk, half troll, harad nomad, harad robes, harad turban, harnedor, hat, high elven, hithlain, jacket lamedon, kaftan, lamedon, lossarnach, mithril, mordor, moredain, moredain lion armor, morgul, near harad, partyhat, pelargir, pinnath gelin, ranger, ranger ithilien, rhun, rhun gold, rivendell, rohan, rohan marshal, tauredain, tauredain gold, umbar, uruk, uruk helmet, utumno, wood elven, wood elven scout.

## Capes

26 capes: blackroot, dale, dorwinion captain, dorwinion elf captain, dunlending berserker, galadhrim, galadhrim trader, gandalf, gondor, gondor tower guard, gulf, harad champion, high elf, lamedon, lossarnach, near harad, pelargir, pinnath gelin, ranger, ranger ithilien, rivendell, rivendell trader, rohan, santa, taurethrim, wood elf.

## Player shields (cosmetic)

64 shields: 38 faction shields (one per faction), 4 achievement tiers (bronze, silver, gold, mithril), and 22 special or event shields (contests, supporters, team roles).

## Model textures for placed objects

74 textures for things drawn as 3D models rather than block tiles (placed drinking vessels, chests, banners, weapon racks and so on). ale horn, ale horn gold, armor stand, arrow poisoned, banner (43), beacon, boss trophy (2), chest (5), crossbow bolt, glass bottle, goblet copper, goblet gold, goblet silver, goblet wood, kebab (4), mug, mug clay, skull cup, skull staff, troll totem, unsmeltery (2), weapon rack, wine glass. Banners are 43 faction and pattern designs.

## Screens and HUD

41 GUI textures: achievements – icons, achievements – page, alignment, anvil, armor stand, attack meter, banner edit, barrel – barrel, barrel – brewing, beacon, branding iron, coin exchange, dale cracker, effects, factions, factions full, fellowships, forge, horn select, menu icons, menu overlay, message, millstone, mob spawner, npc – gollum, npc – hired, npc – hired farmer, npc – hired warrior, npc – trade, npc – unit trade, npc – unit trade buttons, oven, pouch, pouch with chest, quest – miniquest, quest – red book, quest – red book miniquests, quest – tracker, squadron item, unsmelter, widgets.

## Map

6 map textures: the world map, its overlay, the map screen frame, terrain shading, conquest overlay and one easter-egg map. **Ours must be our own drawing** (see docs/tooling.md).

## Effects and overlays

17 textures: burn overlay, compass, elvenportal 0, elvenportal 1, elvenportal overlay, frost overlay, gulduril glow, mist overlay, morgulportal overlay, particles, particles2, portal, portal overlay, portal writing, utumno portal light circle, water particles, wight.

## Sky and weather

7 sky textures (sun, moon, Eärendil star, clouds, three night skies) and 4 weather textures (ash, rain, sandstorm, snow).

## Sounds

190 sound files: ambient – terrain (3), ambient – weather (9), aurochs (5), bear (5), bird (16), bird – crow (5), bird – seagull (4), block – gate (4), block – plate (1), block – treasure (7), crocodile (3), deer (4), dwarf (4), elf (1), elf – male (3), elk (4), ent (1), ent – mallorn (2), event (7), flamingo (5), gollum (5), half troll (5), item (12), lion (3), midges (3), misc (1), music (1), orc (5), rabbit (5), rhino (3), swan (1), troll (7), warg (18), wight (23), wraith (2), zebra (3). Music is not bundled (players add their own).

## Structure scans

75 hand-built structure pieces stored as scans (most other structures are generated in code; see structures.md): black uruk fort, bree barn, bree garden, bree gatehouse, bree house, bree inn, bree market, bree office, bree ruffian house, bree smithy, bree stable, bree well, corsair camp cage, corsair camp centre, corsair cove, dunland fort, dunland house, dunland tavern, gulf altar, gulf bazaar, gulf farm, gulf house, gulf pasture, gulf pyramid, gulf smithy, gulf tavern, gulf totem, gulf tower, gulf war camp, harad pyramid, harad ruined fort, harnedor farm, harnedor fort, harnedor house, harnedor house ruined, harnedor market, harnedor pasture, harnedor smithy, harnedor stables, harnedor tavern, harnedor tavern ruined, harnedor tower, hobbit burrow, mumak skeleton, nomad bazaar, nomad tent, nomad tent chieftain, nomad tent large, southron barracks, southron bazaar, southron farm, southron fort, southron fortress gate, southron house, southron mansion, southron pasture, southron smithy, southron stable, southron statue base, southron statue bird, southron statue mumak, southron statue snake, southron tavern, southron tower, southron town corner, southron town gate, southron training, southron well, taurethrim farm, taurethrim house, taurethrim smithy, umbar statue pharazon, umbar statue pillar, umbar statue snake, warg pit.

## Text content (counts only; we write our own)

- **NPC speech:** 1842 speech files, 1351 of them for mini-quests. By faction: angmar (14), blue dwarf (13), bree (33), char (15), dale (15), dol guldur (9), dorwinion (27), dunlending (7), dwarf (16), ent (5), galadhrim (15), gondor (40), gundabad (9), half troll (7), high elf (10), hobbit (16), isengard (9), misc (6), mordor (15), moredain (6), near harad (98), ranger north (11), rhun (18), rivendell (14), rohan (23), special (5), tauredain (16), troll (4), utumno (3), wood elf (11).
- **Name lists:** 71 name lists for NPC names by culture.
- **Lore books:** 114 readable lore texts (letters, diaries, poems, recipes, histories) found in structure loot.
- **Languages:** 22 translations, including a Quenya one.
