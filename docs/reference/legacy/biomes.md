# Legacy reference: biomes

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: every Middle-earth biome (169 surface biomes plus the separate Utumno dimension), the sub-biome and variant system, rivers and lakes, ores, spawning and invasions, then a comparison with our draft `specs/world/dimension-and-mapgen.md`. Ids are **our** proposed `snake_case` ids derived from the place name, not the original's registry names. Display names are Tolkien place names.

## How to read the tables

- **Temp / rain**: 1.7.10 climate values. 0.0 is arctic, 0.8 temperate (vanilla plains), 2.0 is Mordor-hot. Rain 0.0 is bone dry, 1.0 lush. Snow falls below roughly 0.15.
- **Height base / var**: 1.7.10 terrain "root height" and "variation". As a rough guide the mean surface is about y = 63 + 18 x base, and var stretches it up and down (var 0.05 is billiard-table flat, 0.3 is rolling, 1.0+ is craggy). Base 2.0 / var 2.0 mountains reach y 150-200+; the mod gives an achievement for standing above y 192 in the Misty Mountains. These are estimates, not measured.
- **Main trees**: the three most heavily weighted tree types. "(dense)" means at least 6 tree attempts per chunk; most open biomes rely on variants (below) for woods.
- **Surface and variants**: non-grass top blocks, then the most notable variant patches the biome can roll.
- **Factions (normal spawn share)**: faction groups with a non-zero base weight, and their share of NPC spawns. Each biome also lists several **zero-weight** groups (usually the local defenders' enemies) that only spawn once those enemies have conquest strength in the area; they are not shown. Spawn lists are owned by the NPC pod; this column is for placement decisions.
- **Invasions**: invasion factions with their chance tier, R = rare, U = uncommon, C = common (numbers in "Invasions and bandits").
- Shared defaults not repeated per row: vanilla-style passive animals (sheep, pig, chicken, cow, deer; or for wild biomes sheep, wild boar, chicken, deer, aurochs), fish, butterflies, rabbits, birds, bats. No vanilla hostiles spawn anywhere.


### Seas, coasts and water

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ocean | The Sea | 0.8 / 0.8 | -1.0 / 0.3 | - | open water; island sub-biome 2% of sea cells | - | - |
| island | Island | 0.9 / 0.8 | 0.0 / 0.3 | oak, large oak, birch | rare islands in the sea | - | - |
| meneltarma | Meneltarma | 0.9 / 0.8 | 0.1 / 0.2 | cedar, large cedar, large oak | stone; Númenor summit isle | - | - |
| beach | Beach | - | - | - | sand; generated at every land/sea edge | - | - |
| beach_gravel | Gravel Beach | - | - | - | gravel; 1 in 20 beach cells | - | - |
| beach_white | White Beach | - | - | - | white sand; Lindon and white-sand biomes | - | - |
| river | River | - | -0.5 / 0.0 | - | water channel; see rivers | - | - |
| lake | Lake | - | - | - | still water | - | - |

### Lindon and the Blue Mountains

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| lindon | Lindon | 0.9 / 0.9 | 0.15 / 0.2 | birch, tall birch, aspen | flower fields, forest patches, steppe, hills, wooded hills, dense forest | Lindon | Gundabad (R) |
| lindon_woodlands | Lindon Woodlands | 0.9 / 1.0 | 0.2 / 0.5 | birch, tall birch, aspen (dense) | - | Lindon | Gundabad (R) |
| lindon_coast | Lindon Coastline | 0.9 / 0.9 | 0.0 / 0.5 | birch, tall birch, aspen | stone | Lindon | Gundabad (R) |
| tower_hills | Tower Hills | 0.8 / 0.8 | 0.5 / 0.5 | birch, tall birch, aspen | flower fields | Lindon 50%, Rangers (North) 50% | Gundabad (R) |
| blue_mountains | Blue Mountains | 0.22 / 0.8 | 1.0 / 2.5 | spruce, fir, pine | - | Blue Mountains 100%, Gundabad (rare) | Gundabad (R) |
| blue_mountains_foothills | Blue Mountains Foothills | 0.5 / 0.8 | 0.5 / 0.9 | spruce, fir, pine | - | Gundabad | Gundabad (R) |

### The Shire

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| shire | The Shire | 0.8 / 0.9 | 0.15 / 0.3 | oak, large oak, chestnut | flower fields, forest patches, hills, wooded hills, orchards | Hobbits | - |
| shire_woodlands | Shire Woodlands | 0.8 / 0.9 | 0.3 / 0.5 | shire pine, oak, large oak (dense) | flower fields, hills; 14% of Shire cells become this | Hobbits | - |
| shire_marshes | Shire Marshes | 0.8 / 1.2 | 0.0 / 0.1 | swamp oak, oak, large oak | - | Hobbits | - |
| shire_moors | Shire Moors | 0.6 / 1.6 | 0.4 / 0.6 | large oak, large chestnut, oak | - | Hobbits | - |
| white_downs | White Downs | 0.6 / 0.7 | 0.6 / 0.6 | oak, large oak, chestnut | flower fields | Hobbits | - |

### Eriador

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| eriador | Eriador | 0.9 / 0.8 | 0.1 / 0.4 | oak, spruce, large oak | scrub, scrub hills, mountain, wasteland | Gundabad 91%, Rangers (North) 9% | Gundabad (U), Rangers (U), Angmar (R), Angmar hillmen (U) |
| eriador_downs | Eriador Downs | 0.6 / 0.7 | 0.5 / 0.5 | oak, spruce, large oak | - | Gundabad 91%, Rangers (North) 9% | Gundabad (U), Rangers (U), Angmar (R), Angmar hillmen (U) |
| old_forest | Old Forest | 0.5 / 1.0 | 0.2 / 0.3 | tall oak, dark oak, oak (dense) | - | dark huorns | - |
| barrow_downs | Barrow-downs | 0.6 / 0.7 | 0.3 / 0.4 | dead oak, oak, spruce | dead woods | barrow-wights (hostile) | - |
| breeland | Bree-land | 0.8 / 0.7 | 0.1 / 0.2 | oak, large oak, beech | flower fields, steppe, hills, wooded hills, orchards | Bree 95%, Ruffians 5% | Gundabad (R) |
| chetwood | Chetwood | 0.8 / 0.9 | 0.2 / 0.4 | oak, large oak, beech | - | Ruffians | Gundabad (R) |
| midgewater | Midgewater | 0.6 / 1.0 | 0.0 / 0.1 | swamp oak, oak, tall oak | - | Gundabad 63%, Angmar hillmen 32%, Rangers (North) 5% | Gundabad (R), Rangers (R) |
| lone_lands | Lone-lands | 0.6 / 0.5 | 0.15 / 0.4 | oak, large oak, spruce | scrub, scrub hills, wasteland, mountain | Gundabad 63%, Angmar hillmen 32%, Rangers (North) 5% | Rivendell (R), Gundabad (C), Rangers (C), Angmar (U), Angmar hillmen (C) |
| lone_lands_hills | Weather Hills | 0.6 / 0.5 | 0.6 / 0.8 | oak, large oak, spruce | - | Gundabad 63%, Angmar hillmen 32%, Rangers (North) 5% | Rivendell (R), Gundabad (C), Rangers (C), Angmar (U), Angmar hillmen (C) |
| trollshaws | Trollshaws | 0.6 / 0.8 | 0.15 / 1.0 | oak, tall oak, beech | - | Gundabad 55%, Angmar trolls 36%, Rangers (North) 9% | Rangers (U), Gundabad (U), Angmar (R), Angmar hillmen (U) |
| rivendell | Rivendell | 0.9 / 1.0 | 0.15 / 0.3 | oak, beech, large oak | flower fields | Rivendell | - |
| rivendell_hills | Rivendell Hills | 0.7 / 0.8 | 2.0 / 0.5 | pine, shrub pine, fir | flower fields | Rivendell | Rivendell (U), Gundabad (R), Angmar (R), Angmar hillmen (R) |
| eregion | Eregion | 0.6 / 0.7 | 0.2 / 0.3 | holly, oak, larch | forest patches | Gundabad | Rangers (R), Gundabad (R) |
| minhiriath | Minhiriath | 0.7 / 0.4 | 0.1 / 0.2 | oak, dead oak, dead spruce | steppe, barren steppe, hills, dead woods | Gundabad 91%, Rangers (North) 9% | Gundabad (U), Rangers (U), Angmar (R), Angmar hillmen (U) |
| eryn_vorn | Eryn Vorn | 0.8 / 0.9 | 0.1 / 0.4 | oak, pine, spruce (dense) | - | Gundabad 91%, Rangers (North) 9% | Gundabad (U), Rangers (U), Angmar (R), Angmar hillmen (U) |
| swanfleet | Swanfleet | 0.8 / 1.0 | 0.0 / 0.1 | tall oak, swamp oak, large oak | - | Gundabad 91%, Rangers (North) 9% | Gundabad (R) |
| enedwaith | Enedwaith | 0.6 / 0.8 | 0.2 / 0.3 | spruce, chestnut, oak | scrub, scrub hills, mountain, wasteland | Dunland | Rangers (R), Gundabad (R), Dunland (U) |
| dunland | Dunland | 0.4 / 0.7 | 0.3 / 0.5 | spruce, fir, pine | - | Dunland 98%, Ruffians 2% | Rangers (R), Rohan (U), Gundabad (R) |

### The North

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| angle | The Angle | 0.6 / 0.8 | 0.15 / 0.3 | oak, shrub oak, large oak | scrub, scrub hills, forest patches, wooded hills | Rangers (North) 100%, Gundabad (rare) | Rangers (C), Gundabad (R), Angmar hillmen (R) |
| coldfells | Coldfells | 0.25 / 0.8 | 0.4 / 0.8 | fir, pine, spruce | - | Gundabad 50%, Angmar trolls/Angmar hillmen/Angmar 50% | Rangers (U), Gundabad (U), Angmar (U), Angmar hillmen (U) |
| ettenmoors | Ettenmoors | 0.2 / 0.6 | 0.5 / 0.6 | pine, spruce, thin spruce | - | Angmar trolls/Angmar hillmen/Angmar 67%, Gundabad 33% | Rangers (U), Gundabad (C), Angmar (C), Angmar hillmen (C) |
| angmar | Angmar | 0.2 / 0.2 | 0.2 / 0.6 | spruce, pine, dead spruce | forest patches, steppe, barren steppe, hills, wooded hills | Angmar/Angmar trolls/snow trolls/Angmar hillmen 98%, wicked dwarves 2% | Rangers (R), Lindon (R), Rivendell (R) |
| angmar_mountains | Angmar Mountains | 0.25 / 0.1 | 1.6 / 1.5 | spruce, pine, dead spruce | - | snow trolls | Rangers (R), Lindon (R), Rivendell (R) |
| forodwaith | Forodwaith | 0.0 / 0.2 | 0.1 / 0.1 | - | snow | snow trolls | - |
| forodwaith_coast | Forodwaith Coastline | 0.0 / 0.4 | 0.0 / 0.5 | - | stone | snow trolls | - |
| forodwaith_mountains | Forodwaith Mountains | 0.0 / 0.2 | 2.0 / 2.0 | - | snow | snow trolls | - |
| forodwaith_glacier | Forodwaith Glacier | 0.0 / 0.1 | 1.0 / 0.1 | - | ice; 5% of Forodwaith Mountains cells | snow trolls | - |
| tundra | Northlands | 0.1 / 0.3 | 0.1 / 0.2 | thin spruce, dead spruce, pine | steppe, barren steppe, hills, dead woods | Gundabad 91%, Rangers (North) 9% | - |
| taiga | Northern Forests | 0.1 / 0.7 | 0.1 / 0.5 | spruce, fir, pine | forest patches, dead woods | Gundabad 91%, Rangers (North) 9% | - |

### Misty and Grey Mountains

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| misty_mountains | Misty Mountains | 0.2 / 0.5 | 2.0 / 2.0 | fir, pine, spruce | - | Gundabad 83%, snow trolls/Angmar 17% | - |
| misty_mountains_foothills | Misty Mountains Foothills | 0.25 / 0.6 | 0.7 / 0.9 | fir, pine, spruce | - | Gundabad 83%, snow trolls/Angmar 17% | - |
| grey_mountains | Grey Mountains | 0.28 / 0.2 | 1.8 / 2.0 | larch, fir, pine | - | Gundabad 83%, snow trolls/Angmar 17% | Gundabad (R), Durin's Folk (R) |
| grey_mountains_foothills | Grey Mountains Foothills | 0.5 / 0.7 | 0.5 / 0.9 | larch, fir, pine | - | Gundabad 83%, snow trolls/Angmar 17% | Gundabad (R), Durin's Folk (R) |

### Vales of Anduin, Lothlórien and Fangorn

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| anduin_hills | Anduin Hills | 0.7 / 0.7 | 0.6 / 0.4 | oak, spruce, birch | - | Gundabad 95%, Lothlórien 5% | Gundabad (U), Dol Guldur (R) |
| anduin_vale | Vales of Anduin | 0.9 / 1.1 | 0.05 / 0.05 | oak, spruce, birch | - | Gundabad 95%, Lothlórien 5% | Gundabad (U), Dol Guldur (R) |
| gladden_fields | Gladden Fields | 0.6 / 1.2 | 0.0 / 0.1 | oak, swamp oak, spruce | - | Gundabad 95%, Lothlórien 5% | Gundabad (U) |
| celebrant | Field of Celebrant | 1.1 / 1.1 | 0.1 / 0.05 | larch, oak, birch | flower fields, forest patches, hills | Gundabad 97%, Lothlórien 3% | Gundabad (R), Dol Guldur (R), Lothlórien (U), Rohan (U) |
| lothlorien | Lothlórien | 0.9 / 1.0 | 0.1 / 0.3 | boughs mallorn, oak, mallorn | flower fields, hills, wooded hills, clearings | Lothlórien | - |
| lothlorien_edge | Eaves of Lothlórien | 0.9 / 1.0 | 0.1 / 0.2 | oak, larch, beech | - | Lothlórien | Gundabad (R), Dol Guldur (R) |
| fangorn | Fangorn | 0.7 / 0.8 | 0.2 / 0.4 | dark oak, tall oak, taller oak (dense) | flower fields, hills | Fangorn | - |
| fangorn_clearing | Fangorn Clearing | 0.7 / 0.8 | 0.2 / 0.1 | dark oak, tall oak, taller oak | - | Fangorn | - |
| fangorn_wasteland | Fangorn Wasteland | 0.7 / 0.4 | 0.2 / 0.4 | charred, dead oak, dead beech | - | Isengard | Isengard (U), Rohan (U), Fangorn (U) |
| brown_lands | Brown Lands | 1.0 / 0.2 | 0.2 / 0.2 | dead oak | - | - | Mordor (U), Black Uruks (R) |
| long_marshes | Long Marshes | 0.6 / 0.9 | 0.0 / 0.1 | swamp oak, oak, dead oak | - | Gundabad | - |

### Mirkwood and Wilderland

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| woodland_realm | Woodland Realm | 0.8 / 0.9 | 0.2 / 0.3 | green oak, fir, pine | forest patches | Woodland Realm | Dol Guldur (U) |
| woodland_realm_hills | Woodland Realm Hills | 0.8 / 0.6 | 0.9 / 0.7 | green oak, extreme green oak, fir | - | Woodland Realm | Dol Guldur (U) |
| mirkwood_north | Northern Mirkwood | 0.7 / 0.7 | 0.2 / 0.4 | green oak, pine, larch (dense) | - | Dol Guldur | Wood-elves (U), Dol Guldur (U) |
| mirkwood_corrupted | Mirkwood | 0.6 / 0.8 | 0.2 / 0.4 | large mirk oak, pine, large oak (dense) | hills | Dol Guldur | Wood-elves (U) |
| mirkwood_mountains | Mirkwood Mountains | 0.28 / 0.9 | 1.2 / 1.5 | fir, spruce, pine | hills | Dol Guldur 65%, Gundabad 35% | Wood-elves (U) |
| dol_guldur | Dol Guldur | 0.6 / 0.8 | 0.2 / 0.5 | dead mirk oak, mirk oak | hills | Dol Guldur | Wood-elves (U) |
| wilderland | Wilderland | 0.9 / 0.4 | 0.2 / 0.4 | oak, dead oak, spruce | scrub, wasteland | Gundabad 33%, Dol Guldur 33%, Mordor 33% | Dol Guldur (U), Mordor (U), Dale (R), Dorwinion (R), Rhûn (U) |
| wilderland_north | Northern Wilderland | 0.6 / 0.6 | 0.2 / 0.5 | oak, dead oak, spruce | - | Gundabad | Gundabad (C), Dol Guldur (R), Dale (R) |
| east_bight | East Bight | 0.8 / 0.3 | 0.15 / 0.05 | dead oak, dead spruce, dead beech | steppe, barren steppe | Gundabad 33%, Dol Guldur 33%, Mordor 33% | Dol Guldur (R) |
| dale | Dale | 0.8 / 0.7 | 0.1 / 0.2 | oak, spruce, pine | orchards | Dale 91%, Gundabad 9% | Gundabad (U), Dol Guldur (R), Mordor (R), Rhûn (R) |
| erebor | Erebor | 0.6 / 0.7 | 0.4 / 0.6 | dead oak, pine, fir | - | Durin's Folk | Gundabad (R), Dol Guldur (R) |
| iron_hills | Iron Hills | 0.27 / 0.4 | 0.3 / 1.4 | spruce, fir, pine | - | Durin's Folk | Gundabad (R) |

### Rohan, Isengard and Dunland borders

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| rohan | Rohan | 0.8 / 0.8 | 0.2 / 0.15 | large oak, oak, birch | boulders, orchards | Rohan 95%, Isengard 5% | Dunland (U), Isengard (U) |
| rohan_woodlands | Rohan Woodlands | 0.9 / 0.9 | 0.2 / 0.4 | large oak, oak, birch | - | Rohan 95%, Isengard 5% | Dunland (U), Isengard (U) |
| rohan_uruk_highlands | Uruk Highlands | 0.7 / 0.4 | 0.8 / 0.3 | large oak, oak, birch | hills, dead woods | Isengard | Rohan (C) |
| wold | The Wold | 0.9 / 0.1 | 0.4 / 0.3 | large oak, oak, dead oak | flower fields, steppe, barren steppe, hills, dead woods | Rohan 95%, Isengard 5% | Dunland (U), Isengard (U) |
| adornland | Adornland | 0.7 / 0.6 | 0.2 / 0.2 | oak, large oak, spruce | boulders, orchards | Dunland 88%, Isengard 9%, Ruffians 4% | Dunland (U), Isengard (R), Rohan (U), Gundabad (R) |
| nan_curunir | Nan Curunír | 0.6 / 0.4 | 0.2 / 0.1 | charred, dead oak, oak | hills, dead woods | Isengard 65%, Dunland 30%, Ruffians 5% | Rohan (R) |
| entwash_mouth | Mouths of Entwash | 0.5 / 1.0 | 0.0 / 0.1 | swamp oak, large oak, tall oak | - | - | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| emyn_muil | Emyn Muil | 0.5 / 0.9 | 0.2 / 0.8 | - | bare stone | Mordor | Mordor (R) |

### Gondor

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| gondor | Gondor | 0.8 / 0.8 | 0.1 / 0.15 | oak, large oak, birch | orchards | Gondor 95%, Mordor 5% | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| gondor_woodlands | Gondor Woodlands | 0.8 / 0.9 | 0.2 / 0.2 | oak, large oak, birch (dense) | - | Gondor 95%, Mordor 5% | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| pelennor | Pelennor Fields | 0.9 / 0.9 | 0.1 / 0.02 | oak, large oak, birch | - | Gondor | - |
| white_mountains | White Mountains | 0.6 / 0.8 | 1.5 / 2.0 | fir, pine, spruce | forest patches | - | Mordor (R) |
| white_mountains_foothills | White Mountains Foothills | 0.6 / 0.7 | 0.5 / 0.9 | fir, pine, spruce | forest patches | - | Mordor (R) |
| lossarnach | Lossarnach | 1.0 / 1.0 | 0.1 / 0.2 | shrub oak, oak, dark oak | flower fields, forest patches, hills, wooded hills, dense forest, shrubland | Gondor (Lossarnach) | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| imloth_melui | Imloth Melui | 1.0 / 1.2 | 0.1 / 0.2 | shrub oak, oak, maple | - | Gondor (Lossarnach) | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| lebennin | Lebennin | 1.0 / 0.9 | 0.1 / 0.3 | oak, large oak, birch | flower fields, forest patches, steppe, hills, wooded hills, dense forest | Gondor (Lebennin) 95%, Mordor 5% | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| pelargir | Pelargir | 1.0 / 1.0 | 0.08 / 0.2 | oak, cypress, cedar | flower fields, hills, wooded hills, shrubland, orchards | Gondor (Pelargir) | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| anduin_mouth | Mouths of Anduin | 0.9 / 1.0 | 0.0 / 0.1 | swamp oak, large oak, oak | - | - | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| dor_en_ernil | Dor-en-Ernil | 0.9 / 0.9 | 0.07 / 0.2 | birch, tall birch, large birch | shrubland, dense forest, orchards | Gondor (Dol Amroth) 99%, Mordor 1% | Mordor (R), Black Uruks (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (U) |
| dor_en_ernil_hills | Dor-en-Ernil Hills | 0.8 / 0.7 | 0.5 / 0.5 | birch, tall birch, large birch | - | Gondor (Dol Amroth) 99%, Mordor 1% | Mordor (R), Black Uruks (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (U) |
| lamedon | Lamedon | 0.9 / 0.5 | 0.2 / 0.2 | oak, larch, aspen | flower fields, forest patches, hills, wooded hills, dead woods, orchards | Gondor (Lamedon) | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| lamedon_hills | Lamedon Hills | 0.6 / 0.4 | 0.6 / 0.9 | oak, larch, aspen | - | Gondor (Lamedon) | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| blackroot_vale | Blackroot Vale | 0.8 / 0.9 | 0.2 / 0.12 | oak, dark oak, fir | flower fields, forest patches, hills, wooded hills, dense forest, dead woods | Gondor (Blackroot) | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| pinnath_gelin | Pinnath Gelin | 0.8 / 0.8 | 0.5 / 0.5 | oak, aspen, birch | flower fields, forest patches, dead woods, orchards | Gondor (Pinnath Gelin) 95%, Mordor 5% | Mordor (U), Black Uruks (R), Nan Ungol (R), Harnedor (R), Southron (R), Umbar (R), Corsairs (R), Rhûn (R) |
| andrast | Andrast | 0.8 / 0.8 | 0.2 / 0.2 | shrub oak, oak, dark oak | flower fields, forest patches, hills, wooded hills, dead woods, shrubland | - | - |
| pukel | Drúwaith Iaur | 0.7 / 0.7 | 0.2 / 0.4 | dark oak, oak, shrub oak | flower fields, forest patches, hills, wooded hills, dead woods, dense forest | - | - |
| tolfalas | Tolfalas | 0.8 / 0.4 | 0.3 / 1.0 | dead oak | - | Corsairs | - |
| ithilien | Ithilien | 0.9 / 0.9 | 0.15 / 0.5 | oak, large oak, cedar | forest patches, shrubland, scrub, lebethron groves, orchards | Mordor 90%, Gondor (Ithilien rangers) 10% | Gondor (U), Ithilien rangers (C), Dol Amroth (R), Mordor (C), Black Uruks (R), Nan Ungol (R), Harnedor (U), Southron (R), Corsairs (R), Rhûn (U) |
| ithilien_hills | Ithilien Hills | 0.7 / 0.7 | 0.6 / 0.6 | oak, large oak, cedar | - | Mordor 90%, Gondor (Ithilien rangers) 10% | Gondor (U), Ithilien rangers (C), Dol Amroth (R), Mordor (C), Black Uruks (R), Nan Ungol (R), Harnedor (U), Southron (R), Corsairs (R), Rhûn (U) |
| ithilien_wasteland | Ithilien Wasteland | 0.6 / 0.6 | 0.15 / 0.2 | dead oak, oak, large oak | scrub, dead woods | Mordor 90%, Gondor (Ithilien rangers) 10% | Gondor (U), Ithilien rangers (C), Dol Amroth (R), Mordor (C), Black Uruks (R), Nan Ungol (R), Harnedor (U), Southron (R), Corsairs (R), Rhûn (U) |
| harondor | Harondor | 1.0 / 0.6 | 0.2 / 0.3 | desert oak, cedar, lemon | forest patches, steppe, barren steppe, hills, wooded hills, shrubland | Gondor (Ithilien rangers) 50%, Harnedor/Southron coast/Harad nomads 50% | Gondor (U), Ithilien rangers (C), Dol Amroth (R), Mordor (U), Black Uruks (R), Harnedor (C), Southron (U), Umbar (R), Corsairs (U), Gulf (R) |

### Mordor and its borders

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| dead_marshes | Dead Marshes | 0.4 / 1.0 | 0.0 / 0.1 | dead oak | marsh pools, remains in the mud; always marsh | - | Mordor (R) |
| nindalf | Nindalf | 0.4 / 1.0 | 0.0 / 0.1 | oak, spruce, dead oak | - | Mordor | Mordor (R), Black Uruks (R) |
| dagorlad | Dagorlad | 1.0 / 0.2 | 0.1 / 0.05 | charred | Mordor dirt | Mordor | Mordor (C), Black Uruks (U) |
| mordor | Mordor | 2.0 / 0.0 | 0.3 / 0.5 | charred | Mordor rock with Mordor gravel and dirt patches; no grass | Mordor 99%, wicked dwarves 1% | - |
| mordor_mountains | Mordor Mountains | 2.0 / 0.0 | 2.0 / 3.0 | charred | Mordor rock | Mordor 99%, wicked dwarves 1% | - |
| gorgoroth | Gorgoroth | 2.0 / 0.0 | 0.6 / 0.2 | charred | Mordor rock | Mordor 99%, wicked dwarves 1% | - |
| udun | Udûn | 1.5 / 0.0 | 0.2 / 0.7 | charred | Mordor rock | Mordor 99%, wicked dwarves 1% | - |
| nan_ungol | Nan Ungol | 2.0 / 0.0 | 0.1 / 0.4 | charred | Mordor rock | Mordor/Mordor (spiders) | - |
| morgul_vale | Morgul Vale | 1.0 / 0.0 | 0.2 / 0.1 | charred, desert oak, dead oak | grass with Mordor rock, gravel and dirt patches | Mordor | - |
| nurn | Nurn | 0.9 / 0.4 | 0.1 / 0.2 | charred, oak, desert oak | steppe, barren steppe, hills, wooded hills, dead woods | Mordor | - |
| nurn_marshes | Nurn Marshes | 0.9 / 0.4 | 0.0 / 0.1 | charred, oak, desert oak | - | Mordor | - |
| nurnen | Sea of Núrnen | 0.9 / 0.4 | -1.0 / 0.3 | - | inland sea | - | - |
| eastern_desolation | Eastern Desolation | 1.0 / 0.3 | 0.2 / 0.2 | charred | Mordor dirt | Mordor 99%, wicked dwarves 1% | - |

### Rhûn and the East

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| rhun | Rhûn | 0.9 / 0.3 | 0.3 / 0.0 | shrub pine, pine, chestnut | dense forest, scrub, scrub hills | - | Rhûn (R) |
| rhun_forest | Rhûn Forest | 0.8 / 0.9 | 0.3 / 0.5 | shrub pine, large oak, pine (dense) | - | - | Rhûn (R) |
| rhun_land | Rhúdel | 1.0 / 0.8 | 0.1 / 0.3 | shrub oak, cypress, oak | orchards | Rhûn 99%, wicked dwarves 1% | Dale (R) |
| rhun_land_steppe | Rhúdel Steppe | 1.0 / 0.3 | 0.2 / 0.05 | shrub pine, shrub oak, cypress | scrub, scrub hills | Rhûn | Dale (R), Rhûn (R) |
| rhun_land_hills | Rhúdel Hills | 1.0 / 0.5 | 0.6 / 0.8 | shrub oak, cypress, oak | - | - | Dale (R) |
| rhun_red_forest | Eryn Garan | 0.9 / 1.0 | 0.1 / 0.3 | redwood, redwood 2, redwood 3 (dense) | - | Rhûn | Dale (R) |
| rhun_island | Tol Rhúnaer | 1.0 / 0.8 | 0.1 / 0.4 | shrub oak, cypress, oak | - | - | - |
| rhun_island_forest | Eryn Rhúnaer | 0.9 / 1.0 | 0.1 / 0.4 | redwood, redwood 2, redwood 3 (dense) | - | - | - |
| dorwinion | Dorwinion | 0.9 / 0.9 | 0.1 / 0.3 | shrub oak, cypress, oak | vineyards (very common), orchards | Dorwinion/Dorwinion elves | Dol Guldur (R), Mordor (R) |
| dorwinion_hills | Emyn Winion | 0.9 / 0.8 | 0.8 / 0.8 | shrub oak, cypress, oak | - | Dorwinion/Dorwinion elves | Dol Guldur (R), Mordor (R) |
| last_desert | Last Desert | 0.7 / 0.0 | 0.2 / 0.05 | dead oak | sand; steppe, hills | - | - |
| wind_mountains | Mountains of the Wind | 0.28 / 0.2 | 2.0 / 2.0 | larch, fir, pine | - | - | - |
| wind_mountains_foothills | Foothills of the Wind | 0.4 / 0.6 | 0.5 / 0.6 | larch, fir, pine | - | - | - |
| red_mountains | Red Mountains | 0.3 / 0.4 | 1.5 / 2.0 | spruce, fir, pine | - | - | - |
| red_mountains_foothills | Red Mountains Foothills | 0.7 / 0.4 | 0.5 / 0.9 | spruce, fir, pine | - | - | - |

### Near Harad

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| harnedor | Harnennor | 1.0 / 0.3 | 0.1 / 0.3 | desert oak, cedar, lemon | forest patches, steppe, barren steppe, hills, wooded hills, shrubland | Harnedor | Gondor (R), Ithilien rangers (U), Harnedor (U), Southron (R), Corsairs (R) |
| near_harad_fertile | Southron Coasts | 1.2 / 0.7 | 0.2 / 0.1 | cedar, desert oak, cypress | flower fields, forest patches, steppe, barren steppe, hills, wooded hills | Southron coast | - |
| near_harad_fertile_forest | Southron Forests | 1.2 / 1.0 | 0.2 / 0.4 | cedar, large cedar, cedar (dense) | - | Southron coast | - |
| near_harad_riverbank | Harad Desert Riverbank | 1.2 / 0.8 | 0.1 / 0.1 | cedar, desert oak, cypress | flower fields | Harad nomads | - |
| near_harad_oasis | Near Harad Oasis | 1.2 / 0.8 | 0.1 / 0.1 | shrub oak, date palm, cedar | - | Harad nomads | - |
| near_harad | Great Desert | 1.5 / 0.1 | 0.2 / 0.1 | dead oak, desert oak | sand; dunes, steppe, hills, red boulders, dead woods, sandy scrub | Harad nomads | - |
| near_harad_hills | Desert Hills | 1.2 / 0.3 | 0.5 / 0.8 | dead oak, desert oak | sand; dead woods, sandy scrub | Harad nomads | - |
| near_harad_semi_desert | Half-deserts | 1.5 / 0.2 | 0.2 / 0.1 | dead oak, desert oak | sand; steppe, barren steppe, hills, wooded hills, dead woods, shrubland | Harad nomads | - |
| near_harad_red_desert | Red Desert | 1.5 / 0.1 | 0.2 / 0.0 | dead oak, desert oak | sand; dunes, steppe, hills, red boulders, dead woods, sandy scrub | Harad nomads | - |
| lostladen | Lostladen | 1.2 / 0.2 | 0.2 / 0.1 | desert oak, dead oak | steppe, barren steppe, hills, wooded hills, shrubland, scrub | - | - |
| umbar | Umbar | 0.9 / 0.6 | 0.1 / 0.2 | desert oak, cypress, cedar | flower fields, forest patches, hills, wooded hills, shrubland, orchards | Umbar/Corsairs/Umbar (renegades) | - |
| umbar_forest | Umbar Forest | 0.8 / 0.8 | 0.2 / 0.3 | desert oak, cypress, cedar (dense) | - | Umbar/Corsairs/Umbar (renegades) | - |
| umbar_hills | Umbar Hills | 0.8 / 0.5 | 1.2 / 0.8 | desert oak, cypress, cedar | flower fields, forest patches, hills, wooded hills, shrubland, orchards | Umbar/Corsairs/Umbar (renegades) | - |
| gulf_harad | Gulf of Harad | 1.0 / 0.5 | 0.15 / 0.1 | palm, desert oak, acacia | flower fields, forest patches, steppe, barren steppe, hills, wooded hills | Gulf of Harad | - |
| gulf_harad_forest | Gulf Forest | 1.0 / 1.0 | 0.2 / 0.4 | dragonblood, palm, desert oak | - | Gulf of Harad | - |
| harad_mountains | Harad Mountains | 0.9 / 0.5 | 1.8 / 2.0 | acacia, desert oak, baobab | - | - | - |

### Far Harad

| id | Name | Temp / rain | Height base / var | Main trees | Surface and variants | Factions (normal spawn share) | Invasions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| far_harad | Far Harad Grasslands | 1.2 / 0.2 | 0.1 / 0.1 | acacia, desert oak, baobab | flower fields, forest patches, steppe, barren steppe, hills, wooded hills | Morwaith | Morwaith (U), Taurethrim (R) |
| far_harad_forest | Far Harad Forest | 1.0 / 1.0 | 0.3 / 0.4 | acacia, desert oak, baobab (dense) | - ; 20% of grassland cells | Morwaith | Morwaith (U), Taurethrim (R) |
| far_harad_arid | Far Harad Arid Grasslands | 1.5 / 0.3 | 0.2 / 0.15 | acacia, desert oak, baobab | flower fields, forest patches, steppe, barren steppe, hills, wooded hills | Morwaith | Morwaith (U), Taurethrim (R) |
| far_harad_arid_hills | Far Harad Arid Hills | 1.5 / 0.3 | 1.0 / 0.6 | acacia, desert oak, baobab | flower fields, forest patches, steppe, barren steppe, hills, wooded hills | Morwaith | Morwaith (U), Taurethrim (R) |
| far_harad_bushland | Far Harad Bushland | 1.0 / 0.4 | 0.2 / 0.1 | acacia, desert oak, birch | flower fields, forest patches, steppe, barren steppe, hills, wooded hills | - | - |
| far_harad_bushland_hills | Far Harad Bushland Hills | 0.8 / 0.4 | 0.8 / 0.8 | acacia, desert oak, birch | flower fields, forest patches, steppe, barren steppe, hills, wooded hills | - | - |
| far_harad_coast | Corsair Coasts | 1.2 / 0.8 | 0.0 / 0.5 | palm, acacia, desert oak | stone | Corsairs | Corsairs (C) |
| far_harad_mangrove | Far Harad Mangrove | 1.0 / 0.9 | -0.05 / 0.05 | acacia, mangrove, desert oak | sand | - | - |
| far_harad_swamp | Far Harad Swampland | 0.8 / 1.0 | 0.0 / 0.1 | acacia, swamp oak, desert oak | - | - | - |
| far_harad_jungle | Far Harad Jungle | 1.2 / 0.9 | 0.2 / 0.4 | jungle, shrub jungle, large jungle (dense) | mud grass; flower fields, hills, mountain, dense jungle | Taurethrim | Morwaith (R), Taurethrim (R) |
| far_harad_jungle_edge | Far Harad Jungle Edge | 1.2 / 0.8 | 0.2 / 0.2 | shrub jungle, jungle, large jungle | mud grass; flower fields, hills | Taurethrim | Morwaith (U), Taurethrim (U) |
| far_harad_jungle_lake | Far Harad Jungle Lake | 1.2 / 0.9 | -0.5 / 0.2 | jungle, shrub jungle, large jungle (dense) | mud grass | Taurethrim | Morwaith (R), Taurethrim (R) |
| far_harad_jungle_mountains | Harad Jungle Mountains | 1.0 / 1.0 | 1.8 / 1.5 | jungle, shrub jungle, large jungle | mud grass | - | - |
| far_harad_cloud_forest | Far Harad Cloud Forest | 1.2 / 1.2 | 0.7 / 0.4 | cloud jungle, shrub jungle, jungle (dense) | - | - | - |
| far_harad_kanuka | Kanuka Forest | 1.0 / 1.0 | 0.3 / 0.5 | kanuka | flower fields, hills | - | - |
| far_harad_volcano | Far Harad Volcano | 1.5 / 0.0 | 0.6 / 1.2 | charred, dead acacia, dead oak | stone, lava; 15% of Perdorogwaith cells | Half-trolls | - |
| tauredain_clearing | Taurethrim Clearing | 1.2 / 0.8 | 0.2 / 0.2 | jungle, shrub jungle, large jungle | mud grass, flower fields; 15% of jungle cells | Taurethrim | - |
| half_troll_forest | Taur-i-Thorogrim | 0.8 / 0.2 | 0.3 / 0.4 | acacia, dead acacia, desert oak | flower fields, hills, wooded hills, clearings, wasteland | Half-trolls | Half-trolls (U), Taurethrim (R) |
| pertorogwaith | Perdorogwaith | 0.7 / 0.1 | 0.2 / 0.5 | dead acacia, dead oak, acacia | forest patches, steppe, barren steppe, hills, wooded hills, dead woods | Half-trolls | - |

### Separate dimension

| id | Name | Notes |
| --- | --- | --- |
| utumno | Utumno | Its own dimension (not on the map), temp 2.0, rain 0.0, flat height. Entered through a fixed entrance structure in the far north of Forodwaith. Players cannot set a respawn there; dying sends them back to Middle-earth at the entrance. |

## Sub-biomes, variants, rivers and lakes

### Sub-biomes (fixed replacement inside a painted biome)

After the map is read, some painted biomes are randomly swapped for a child biome in small cells (about a quarter of a map pixel before smoothing, so roughly 32-64 block patches that blend):

| Painted biome | Becomes | Share of cells |
| --- | --- | --- |
| The Shire | Shire Woodlands | 14% |
| Far Harad Grasslands | Far Harad Forest | 20% |
| Far Harad Jungle | Taurethrim Clearing | 15% |
| Perdorogwaith | Far Harad Volcano | 15% |
| Forodwaith Mountains | Forodwaith Glacier | 5% |
| The Sea | Island | 2% |

All other "hills", "woodlands", "edge" and "marsh" biomes (Weather Hills, Lindon Woodlands, Eaves of Lothlórien and so on) are painted directly on the map.

### Variants (terrain and vegetation patches inside any biome)

Every biome carries two weighted variant lists, one for **large** patches (cells about 1,000 blocks across) and one for **small** patches (about 250 blocks). A cell rolls a variant with the biome's **variant chance** (default 40%; Shire 25%; Ithilien, Lothlórien, Wilderland 70%; Near Harad desert 80%; Dead Marshes 100%, always marsh). Variants do not change the biome id, only terrain and decoration. They are suppressed near the fixed mountains and fixed settlements, and many are suppressed near roads and landmarks.

| Variant | Scale | Effect |
| --- | --- | --- |
| Flowers | small | 10x flowers |
| Forest / light woods | all | about 3x trees (light woods also 2x grass, slightly wetter) |
| Forest of one species (birch, beech, aspen, maple, larch, pine) | all | heavy tree boost using only that species |
| Steppe / barren steppe | large | almost no trees, raised a little, drier |
| Hills / wooded hills | all | +0.5 height, 1.5x variation (wooded: 3x trees) |
| Mountain | all | +1.2 height, 3x variation |
| Clearing | small | no trees, 3x flowers, a bit higher |
| Dense forest (oak, birch, spruce, dark oak, lebethron) | all | very many trees, large and "party" specimens |
| Dead woods (oak, spruce, mixed) | all | dead trees only |
| Shrubland | all | 6x trees, mostly bushy shrub oaks |
| Scrub / scrub hills / wasteland (stone or sandstone versions) | all | patchy exposed stone or sandstone, sparse plants; scrub hills +0.5 height, 2x variation |
| Marsh (lowland) / upland swamp | small | lowland drops slightly below sea level and floods with reeds; upland is raised and wooded |
| Orchard (apple and pear, plum, olive, almond, orange, lemon, lime, date, pomegranate, Shire mix) | all | rows of fruit trees; Shire orchards can spawn hobbit orcharders |
| Vineyard | small | grape rows, no trees, blocks structures and villages (Dorwinion) |
| Boulders (Rohan rock, red sandstone) | large | scattered boulders |
| Savannah baobab, dense jungle, dunes | large | biome-specific looks (baobab clumps, giant jungle trees, sand dunes) |

### Rivers

- Two sources. **Map rivers** are pixels painted as river on the map image; they are pulled out of the biome layer, thinned and smoothed, then laid back as a river cut at their own resolution, so a one-pixel painted river ends up a few dozen blocks wide instead of 128. **Random rivers** are procedural, very sparse, and only appear in biomes that allow them (mountain biomes, Erebor, Nan Curunír, Mirkwood Mountains and a few others forbid them) and never near landmarks or fixed mountains.
- A river cut keeps the surrounding biome id (it is a variant, not a biome); its floor sits at about base -0.5 (well below sea level, so it fills with sea-level water) and is slightly wetter.
- Roads crossing rivers build bridges (see world-map-travel.md).
- In Near Harad, desert cells next to map rivers become **Harad Desert Riverbank**; oases are seeded inside the Great Desert as **Near Harad Oasis** with a small lake in the centre.

### Lakes

- A separate lake layer drops small round lakes (cells of roughly 100-200 blocks) into any biome that allows rivers, away from landmarks. Lake floors sit at base -0.5 like rivers.
- Far Harad Jungle and Far Harad Mangrove get extra, denser lake passes.
- Larger painted water bodies use the Lake biome or a named water biome (Sea of Núrnen); the Sea of Rhûn is painted as sea. (Not verified for every lake.)

### Beaches and coasts

Any land biome cell touching the sea becomes a beach: Lindon becomes Lindon Coastline (1 in 3) or White Beach; Forodwaith becomes Forodwaith Coastline; biomes with white sand get White Beach; others get Beach, with Gravel Beach 1 time in 20. Corsair Coasts and Far Harad Volcano keep their own biome at the shore.

### Fixed mountains

Independent of the painted biome, about 38 named peaks and hills are added as round height boosts at fixed positions, with a peak height (in the same base units) and radius in blocks: for example Mount Doom 5.0 over 250 blocks with a lava crater 25 blocks wide, Erebor 5.0 / 300, Belegost and Nogrod 5.0 / 300, Caradhras 3.0 / 250, Weathertop 2.5 / 65, Bree-hill 2.0 / 500, Bag End's hill 1.0 / 70, Minas Tirith's hill 3.5 / 170 plus Mindolluin, Dol Amroth 2.2 / 250, the seven beacon hills of Gondor 1.5-2.5. Near these peaks variation is damped so they stay cone-shaped, and no variants, rivers or random structures generate on them.

### Surface details

- Above y 90, mountain and hill tops break into exposed stone and gravel in noisy patches (most biomes).
- Under dense canopy, grass turns to podzol-like forest floor in patches.
- Water colour shifts gradually from north to south across the map.

## Ores

Every biome gets the base set (vein size, attempts per chunk, y range): coal (16, 40, 0-128), copper (8, 16, 0-128), tin (8, 16, 0-128), iron (8, 20, 0-64), sulfur, saltpeter (8, 2, 0-64), salt (12, 2, 0-64), gold (8, 2, 0-32), silver (8, 3, 0-32), and eight gem types in single-block clusters (1-2 attempts, deeper for rarer gems; diamond lowest at y 0-16).

| Biome(s) | Extra ores |
| --- | --- |
| Misty Mountains | mithril (6, 0.25 per chunk, y 0-16, the only mithril source); glowing ore (glowstone) |
| Blue Mountains | more coal and iron, glowstone ore |
| Iron Hills | lots of iron (20 extra), gold, silver, glowstone ore |
| Erebor | iron, gold, silver, glowstone ore |
| Red Mountains | glowstone ore |
| Mordor (all Mordor biomes) | a fire-crystal ore, morgul iron (20) and a dark-crystal ore in Mordor rock; Morgul Vale extra dark crystal |
| Angmar, Dol Guldur | morgul iron (20), dark-crystal ore (8) |
| Lindon, Rivendell, Lothlórien | an elven light-crystal ore |
| Near Harad, Southron Coasts, Harnennor, Harondor, Umbar, Gulf, Lostladen | lapis |
| Far Harad Jungle, Rhúdel Hills | extra gold |
| The Sea | salt in stone and in sand near the shore |
| Dead Marshes | remains (bones) in the mud near the surface |

Angmar, Trollshaws, Coldfells and Ettenmoors also bury **troll hoards** (2-6 treasure piles of copper, silver or gold) underground. Several northern and wild biomes (Eriador, Lone-lands, Angmar, Misty and Grey Mountains, Barrow-downs, Ithilien, Wilderland and others) seed small **orc dungeons** underground.

## Spawning, conquest and invasions

### NPC spawning

- Each biome holds weighted **faction groups**. A group's effective weight is its base weight plus 0.2 x the conquest strength that faction (plus one third of its allies' strength) holds in the 1,024 x 1,024-block conquest zone. Individual entries can also require a minimum conquest strength (commonly 50, 100 or 200), which is how elite units (black uruks, olog-hai, uruk captains) only appear once an area is held.
- A biome's **conquest gain rate** scales how fast kills there shift conquest: 0.2 in elf and hobbit homelands, Lothlórien, Rivendell, Fangorn, Pelennor, Umbar; 0.5 in Angmar, Mordor, Lindon, Dunland, Dor-en-Ernil, Pelargir; 0.75 in Ettenmoors, Dorwinion, Rhúdel; 1.0 default.
- NPCs spawn at least 24 blocks from any player. The global cap is 100 NPCs per standard 196 loaded chunks (scaled to loaded area). Orchards, vineyards and similar variants swap in their own worker spawn list part of the time.

### Invasions and bandits

- Chance tiers are probabilities per hour of an event within 48 blocks of an eligible player: rare 10%, uncommon 30%, common 90%. During a lunar eclipse at night, invasion chances are multiplied by 5.
- An invasion only fires near a player whose alignment with the invading faction is negative. It appears as an invasion marker 3-5 blocks above ground that releases waves of that faction's warriors.
- Bandits (which steal items and cancel fast travel) use the same tiers: rare 10%, uncommon 30%, common 80% per hour. Shire, Lindon, Rivendell, Lothlórien, Fangorn, Old Forest, Woodland Realm, Mirkwood, Barrow-downs, Misty Mountains, Forodwaith, Angmar, Mordor and Dagorlad never have bandits; Dunland, Enedwaith, Lone-lands, Trollshaws, Emyn Muil, Harondor, Tolfalas and the Corsair Coasts have the most.

## Differences from our draft spec

Compared with `specs/world/dimension-and-mapgen.md` (draft, NovoAtlas image generation).

### Scale, size, height

| Topic | Legacy | Our draft | Recommendation |
| --- | --- | --- | --- |
| Horizontal scale | 1 map pixel = 128 blocks (biome resolution); everything finer is noise | heightmap 8 blocks/px, biome map 32 blocks/px | Keep ours: painted 8/32-block resolution is the main upgrade over Legacy. |
| Map canvas | 3,200 x 4,000 px, origin (0, 0) at map pixel (810, 730), right next to Hobbiton | 5,120 x 4,096 heightmap px, origin at image centre | Put the origin near Hobbiton/the landing point too, so early-game coordinates stay small. |
| World size | 409,600 x 512,000 blocks of canvas; used land spans about 290,000 (W-E, Lindon to Rhûn) x 430,000 (N-S, Forodwaith to Far Harad) | 40,960 x 32,768 blocks | Decide scope now: at our Shire scale the whole Legacy map needs about 70,000 x 100,000 blocks, which our canvas cannot hold. Either grow the canvas (and RAM) per region, or drop the deep south (Far Harad) from the canvas. |
| Hobbiton to Bree | about 12,800 blocks straight line | about 3,000 by road | Keep ours (about 4.3x smaller). Legacy distances were a common complaint and made fast travel mandatory; ours keeps walking viable. |
| Other reference distances (Legacy, straight line) | Hobbiton-Rivendell 45,800; Hobbiton-Minas Tirith 102,000; Edoras-Minas Tirith 31,900; Hobbiton-Umbar 133,300 | not set | At 4.3x smaller: about 10,700 / 23,700 / 7,400 / 31,000. Use these as the artist's targets. |
| Sea level | water fills to y 62 (vanilla 1.7.10 sea level) | 63 (1.21 noise setting; water also tops out at y 62) | Same in practice; keep 63. |
| Vertical range | y 0-255; plains about y 64-70, big mountains y 150-200+, fixed peaks higher | y -64 to 319, painted max y 300 | Keep ours; scale Legacy's peaks by about 1.3x (Misty Mountains summits around y 230-280). |
| Biome borders | fuzzy (zoom and smoothing layers) | stepped 32-block cells | Accept stepping, but add a small border jitter later if it looks blocky. |
| Sub-biomes and variants | heavy use: random hills, forests, orchards, clearings, scrub in every biome | out of scope for this spec (biomes-eriador) | Keep the idea: our heightmap fixes big hills, so port only vegetation variants (forest patches, orchards, clearings, dead woods, flowers) as placed-feature noise. |
| Rivers | map rivers plus random rivers, cut to below sea level | carved into heightmap, min 16 blocks wide | Keep ours; skip random rivers (they conflict with a painted map). |
| Roads | flatten terrain within 32 blocks, about 8 blocks wide | 3 wide plus ragged edge, max 1 block cut/fill | Keep ours narrower, but consider a gentle terrain blend (Legacy roads read as much grander). |

### The 17 pilot biomes

| Our id | Legacy match | Notes | Recommendation |
| --- | --- | --- | --- |
| ocean | The Sea | Legacy also seeds 2% Island cells and Númenórean ruins | Keep. |
| shore | Beach / Gravel Beach / White Beach | Legacy generates beaches automatically at coasts, never painted | Keep one painted shore id; pick sand/gravel by surface rule. |
| river | River (variant over land biome) | Legacy rivers keep the land biome id | Keep as a biome (simpler for us); let bank spawns use the neighbouring biome. |
| lake | Lake | Same idea | Keep. |
| shire | The Shire | Hobbits only; Isengard ruffians and orcs only via conquest | Keep; add hobbit hole/house/tavern/farm/windmill structures. |
| shire_woodlands | Shire Woodlands | Legacy: random 14% replacement, Shire pine dominant, no hobbit holes | Keep painted. |
| shire_marshes | Shire Marshes | Swamp oaks, rotten houses, rare bandits | Keep. |
| shire_moors | Shire Moors | Large oaks and chestnuts, farms and windmills; Legacy's moors are very wet (rain 1.6) | Keep. |
| old_forest | Old Forest | Dark huorns only (own hostile tree faction), very dense tall and dark oaks | Keep; set faction to a dark-huorn group, not "none". |
| barrow_downs | Barrow-downs | Barrow-wights belong to an unaligned **hostile** group, not Angmar; barrows are 1 in 10 chunks with a wight spawner chest | Answers our open question 8: make wights unaligned hostile. |
| bree_land | Bree-land | Bree men and guards 95%, ruffians 5%; Bree, Staddle, Combe and Archet are the only fixed villages in Legacy | Keep; Rangers here only by conquest in Legacy, ours "rare" is fine. |
| chetwood | Chetwood | Legacy: ruffians only (bandit woods), dense | Change faction note: ruffians/bandits rather than Rangers and Bree. |
| midgewater_marshes | Midgewater | Gundabad orcs 63%, Angmar hillmen 32%, Rangers 5% | Add orc and hillmen threat; Rangers rare is right. |
| lone_lands | Lone-lands (plus Weather Hills as its own biome) | Very dangerous: common Gundabad, Angmar hillmen and Ranger invasions; hillman villages, Rhudaur castle ruins | Keep, and add `weather_hills` when the pilot reaches Weathertop. |
| far_downs | none (closest: White Downs, a Shire sub-biome with hobbits) | Legacy has no Far Downs biome | Rename to `white_downs` or keep `far_downs` as our own addition; both are Tolkien places. |
| tower_hills | Tower Hills | Lindon elves 50% and Rangers 50%; Legacy's own elf-tower structure | Add Rangers. |
| ered_luin_foothills | Blue Mountains Foothills | In Legacy the foothills spawn mostly **Gundabad orcs**; dwarves live in the Blue Mountains proper (stronghold, smithy, houses inside the rock) | Decide: orc-infested foothills (Legacy) or dwarf-held (our draft). Recommend dwarves in the mountains, orcs as a minority here. |

Legacy biomes between the pilot pieces that our list lacks: Eriador (generic wild land, Gundabad-dominated), Eriador Downs, Lindon, Lindon Woodlands, Lindon Coastline, Blue Mountains. If the pilot canvas shows the Gulf of Lune and Ered Luin, add `lindon` and `blue_mountains` too.
