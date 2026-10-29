# LOTR Revival: models, skeletons and attack motion plan

This branch holds only this file. To get it without the rest of the repo:

```sh
git clone -b motion-plan --single-branch https://github.com/sethwillem-svg/lord-of-the-rings-revival motion-plan
```

## Factions to model

| Group | Factions and units |
|---|---|
| **Men** | **Gondor:** soldiers, archers, Tower Guard, levy. **Gondor fiefs:** Dol Amroth (Swan Knights, men-at-arms), Ithilien rangers, Blackroot Vale, Lamedon, Lebennin, Lossarnach, Pelargir, Pinnath Gelin. **Others:** Rohan (with shieldmaidens), Dale, Bree (with ruffians), Dúnedain, Dunland (berserkers, axe-throwers), Rhudaur hillmen, Lossoth, Rhúdel/Easterlings (with fire-throwers and Golden Easterlings), Near Harad (Coast Southrons, Umbar, Corsairs, Harnennor, Gulfings, Nomads), Morwaith (zebra riders), Taurethrim (blowgunners), Half-trolls, bandits, slaves, Woses |
| **Elves** | Lindon, Rivendell, Lothlórien, Woodland Realm (elk riders), Dorwinion (with Vintner Guard) |
| **Dwarves** | Durin's Folk, Blue Mountains |
| **Hobbits** | Shire, Bree-hobbits |
| **Orc gaps** | Uruk sapper, crossbower, berserker; Gundabad scrounger; spider keeper; slaver; bombardiers |
| **Undead** | Barrow-wights; Marsh, Ruins and Pyramid wraiths |
| **Utumno** | ice spiders, snow-trolls, tormented elves, ice/obsidian/fire wargs, Balrog |
| **Bosses** | Hill-troll Chieftain, Mallorn Ent, Balrog |
| **Creatures** | wargs (riders, bombardiers), spiders, Ents/Huorns, mûmakil, Graugs, woodland elk, zebras. Horses use vanilla. |

## Skeletons

1. Orc: done, adding joints
2. Troll/Olog: done
3. Human: elves reuse it
4. Dwarf
5. Hobbit
6. Half-troll
7. Wraith
8. Ent/Huorn
9. Graug
10. Balrog
11. Quadruped: warg, elk, zebra
12. Spider
13. Mûmak

## Attack sets

- **Men**
  - Base: sword, shield, spear/pike, bow, axe, polearm, banner rally, horn.
  - Extras: lance charge, crossbow, fire-thrower, blowgun, throwing axe, berserker frenzy.
- **Elves:** sword combos, twin knives, rapid bow, glaive, dodge.
- **Dwarves:** axe, mattock/hammer, shield bash, throwing axe, crossbow.
- **Hobbits:** sling/stone throw, short-sword stab, cower.
- **Orc gaps:** sapper bomb placement, crossbow volley, berserker frenzy.
- **Undead:** wight grab, wraith drain.
- **Creatures**
  - Wargs: bite, pounce, maul, howl.
  - Spiders: bite, web shot, leap.
  - Ents/Huorns: stomp, sweep, throw, root grab.
  - Mûmakil: tusk sweep, trample.
  - Graugs: smash, grab-and-throw, charge, roar.
  - Elk and zebras: buck, kick.
- **Bosses**
  - Balrog: whip lash, flame sword.
  - Chieftain: boulder throw, ground slam.
  - Mallorn Ent: sweep, stomp.

## Book references

| Race/unit | Where Tolkien shows them fighting |
|---|---|
| Rohan cavalry, Woses (poisoned darts and arrows) | *RotK*, "The Ride of the Rohirrim"; "The Battle of the Pelennor Fields" |
| Gondor, Swan Knights (Imrahil), Easterling axemen, Haradrim, mûmakil | *RotK*, "The Battle of the Pelennor Fields" |
| Mûmak in Ithilien, Ithilien rangers' ambush | *TTT*, "Of Herbs and Stewed Rabbit" |
| Dúnedain (Grey Company), Corsairs | *RotK*, "The Passing of the Grey Company"; "The Last Debate" |
| Gimli's axe, Legolas's bow, Dunlendings, Uruk assault | *TTT*, "Helm's Deep" |
| Uruk-hai fighting and marching, Boromir's last stand | *TTT*, "The Departure of Boromir"; "The Uruk-hai" |
| Moria orcs, cave troll, orc-chieftain's spear thrust, Balrog's whip and sword | *FotR*, "The Bridge of Khazad-dûm" |
| Ents breaking Isengard, Huorns | *TTT*, "The Road to Isengard"; "Flotsam and Jetsam" |
| Troll-chief at the Black Gate | *RotK*, "The Black Gate Opens" |
| Barrow-wight | *FotR*, "Fog on the Barrow-Downs" |
| Mirkwood spiders, wood-elves | *The Hobbit*, "Flies and Spiders" |
| Wargs and goblins | *The Hobbit*, "Out of the Frying-Pan into the Fire" |
| Iron Hills dwarves (mattocks, shield wall), Bolg and Gundabad, wood-elf and Lake-men archers | *The Hobbit*, "The Clouds Burst" |
| Bard and the Dale/Lake-town archers | *The Hobbit*, "Fire and Water" |
| Hobbit stone-throwing; hobbit archers and militia | *FotR*, Prologue "Concerning Hobbits"; *RotK*, "The Scouring of the Shire" |
| Galadhrim bowmen | *FotR*, "Lothlórien" |
| Angmar, Lossoth, Olog-hai | *RotK*, Appendix A (Angmar, Lossoth); Appendix F (Olog-hai) |
| Half-trolls of Far Harad | *RotK*, "The Battle of the Pelennor Fields" (troll-men from Far Harad) |

**No book source** (mod or game inventions, so design these freely): Taurethrim, Morwaith, Golden Easterlings, fire-throwers, Uruk crossbows, Utumno units, Graugs (from *Shadow of Mordor*), elk and zebra mounts.

## HY-Motion prompts

HY-Motion only does human skeletons. For trolls, Ents, Graugs and the Balrog, generate a human proxy, retarget it, then exaggerate it. Quadrupeds, spiders and the mûmak need hand keyframes or a different tool.

Keep each prompt under 30 words and each clip 2–4 s. For looping attacks, end back in a ready stance. Run each attack with 3–4 seeds and keep the best take.

### Men

| Move | Prompt |
|---|---|
| Sword slash | A person holding a sword in the right hand steps forward and slashes diagonally downward from the right shoulder, then returns to a guarded stance. |
| Sword thrust | A person lunges forward and thrusts a sword straight ahead with the right arm, then pulls back into a ready stance. |
| Shield block | A person raises a shield on the left arm to block in front of the face, braces their legs, then lowers it. |
| Shield bash | A person shoves forward hard with the left shoulder and arm as if bashing with a shield, then steps back. |
| Spear thrust | A person holding a spear in both hands steps forward and stabs it straight ahead, then pulls it back. |
| Pike brace | A person plants their feet wide, crouches slightly, and braces a long spear pointed forward at chest height. |
| Axe chop | A person swings an axe overhead with both hands and chops down hard in front of them, then lifts it back up. |
| Polearm sweep | A person swings a long polearm horizontally from right to left at waist height with both hands. |
| Bow | A person draws a bow with the left arm extended and the right hand pulling back to the cheek, holds, then releases. |
| Crossbow | A person raises a crossbow to the shoulder with both hands, aims briefly, fires, and lowers it. |
| Throwing axe | A person winds the right arm back over the shoulder and throws an object forward hard, following through. |
| Banner rally | A person raises a pole high with both hands and waves it side to side, then thrusts it upward. |
| Horn | A person lifts the right hand to the mouth, leans back slightly, and holds as if blowing a horn. |
| Berserker frenzy | A person wildly swings a heavy weapon in rapid alternating diagonal slashes while advancing aggressively. |
| Fire-thrower | A person holds a tube forward with both hands and sweeps it slowly left to right as if spraying. |
| Blowgun | A person raises a tube to the mouth with both hands, aims, puffs sharply, and lowers it. |
| Mounted slash (seated proxy) | A seated person leans to the right and slashes downward with a sword in the right hand. |
| Lance couch (seated proxy) | A seated person tucks a long lance under the right arm and leans forward, bracing. |

### Elves

| Move | Prompt |
|---|---|
| Sword combo | A person performs a fast, graceful combination of three sword slashes with a spin, ending in a poised stance. |
| Twin knives | A person with a knife in each hand slashes rapidly left and right while stepping forward lightly. |
| Rapid bow | A person quickly draws and fires a bow three times in a row, nocking each arrow smoothly. |
| Glaive | A person spins a long glaive overhead and sweeps it down in a wide arc with both hands. |
| Dodge | A person quickly sidesteps and leans away to the left, then recovers to a ready stance. |

### Dwarves (generate as human, then retarget)

| Move | Prompt |
|---|---|
| Axe | A short, stocky person swings an axe overhead and slams it down with great force, legs planted wide. |
| Mattock smash | A person lifts a heavy hammer with both hands behind the head and smashes it down, bending at the knees. |
| Shield wall | A person crouches behind a raised shield, braces, then shoves forward and steps back. |
| Throwing axe | Same as Men. |
| Crossbow | Same as Men. |

### Hobbits

| Move | Prompt |
|---|---|
| Stone throw | A small person picks up a stone from the ground and throws it overhand quickly. |
| Short-sword stab | A small person darts forward and stabs upward with a short sword, then skips back. |
| Cower | A person crouches down covering their head with both arms, trembling. |

### Orc gaps

| Move | Prompt |
|---|---|
| Sapper | A person runs forward hunched, kneels to place an object on the ground, then turns and sprints away. |
| Crossbow volley | Same as Men's crossbow. |
| Berserker | Same as Men's berserker frenzy. |

### Undead

| Move | Prompt |
|---|---|
| Wight grab | A person slowly reaches out with both arms, grabs forward, and pulls inward while leaning in. |
| Wraith drain | A person floats forward with arms extended, hands clawed, and leans menacingly toward a target. |

### Big proxies (retarget, then exaggerate)

| Move | Prompt |
|---|---|
| Troll smash | A huge heavy person raises a club overhead with both hands and slams it into the ground, staggering forward. |
| Troll sweep | A heavy person swings a club horizontally in a wide arc from right to left, twisting the whole body. |
| Boulder throw | A person lifts a heavy object over the head with both hands and hurls it forward with full body effort. |
| Ground slam | A person raises both fists high and pounds them into the ground in front, crouching. |
| Ent stomp | A tall stiff person raises one leg slowly and stomps down hard, then sways. |
| Ent sweep | A stiff person sweeps one long arm across the body at waist height in a slow, powerful arc. |
| Ent grab-throw | A person reaches down, grabs something with both hands, lifts it overhead, and throws it forward. |
| Graug smash | A hunched heavy person with long arms raises both fists overhead and smashes down, then roars, chest out. |
| Graug charge | A hunched person runs forward heavily on bent legs, arms swinging low, and shoulder-rams forward. |
| Balrog whip | A person swings the right arm back and cracks it forward like a whip, leaning into the motion. |
| Balrog sword | A person raises a sword overhead with the right hand and brings it down in a heavy diagonal strike. |
| Roar | A person spreads both arms wide, leans forward, and throws their head back as if roaring. |
