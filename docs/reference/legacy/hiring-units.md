# Legacy reference: hiring units

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Ids are our proposed `snake_case` ids (see npcs.md). Coin amounts are in silver-coin value (1, 10 and 100 denominations; see combat-trade.md).

## 1. Overview

- Players hire NPCs from **unit traders**: captains, lords, warlords, chieftains, commanders, mercenary captains, the Hobbit Shirriff, plus farmers, vinekeepers and slavers who sell farmhands. Each unit trader offers a fixed list of units.
- Two unit **tasks**:
  - **Warrior**: fights, follows, guards, levels up. Almost every unit.
  - **Farmer** (farmhand): tends crops inside a guard radius, uses a small inventory, never levels.
- **Mercenaries** are a third channel: a self-hiring NPC (only the Morwaith/Harad mercenary in Legacy) that the player hires directly by talking to it.
- Hiring is a one-time coin payment. **There is no upkeep, wage or food requirement.**
- A hired NPC never despawns and keeps its generated name. Name tags do not work on hired people; only tamed rideable beasts (wargs, spiders) accept a name tag.

## 2. Requirements to hire

A unit is purchasable when all of these hold:

1. The player can open the trader's unit screen: alignment with the trader's faction at or above the trader's own level (usually the same as the list's base alignment, e.g. Gondor captain +200, Shirriff +50, farmers 0) and the trader is not hostile to them.
2. Alignment >= the unit's **required alignment** = list base + per-unit extra (table below).
3. Pledge condition, if the unit has one:
   - **Faction pledge**: the player must be pledged to that faction (elites: Tower Guard, Black Uruks, Olog-hai, berserkers, Swan Knights, Gundabad Uruks, Easterling gold warriors, Southron champions, Mountain/Mirk trolls).
   - **Any elf**: pledged to any pure-elf faction (Lothlorien, High Elves, Woodland Realm, Dorwinion elves count only if the faction is elf-only). Used by elf warriors, wardens, scouts, banners and mounted elves.
   - **Any dwarf**: pledged to any dwarf faction. Used by dwarf warriors, axe-throwers, boar riders and banners.
   - Pledge is possible from +100 in every faction.
4. Enough coins in the inventory (pouches are not counted).

### Cost formula

- Base cost C from the table.
- If **not pledged** to the trader's faction: cost is doubled, and the discount uses a 2000-point scale.
- Surplus S = max(alignment - required alignment, 0).
- Discount factor f = clamp(S / 1500 if pledged, else S / 2000, 0..1).
- Final cost = round(C x (pledged ? 1 : 2) x (1 - 0.5 f)), minimum 1.
- So pledged players pay C at the threshold and C/2 at +1500 surplus; unpledged pay 2C at the threshold and C at +2000 surplus.

## 3. Unit lists

Format: unit (base cost / extra alignment over the list base). "+mount" means the unit comes riding that mount; mount armor is given always unless a chance is shown. P = faction pledge, E = any-elf pledge, D = any-dwarf pledge.

| Unit trader | List base | Units |
| --- | --- | --- |
| `hobbit_shirriff` | +50 | bounder (20/0); bounder + Shire pony (40/+50) |
| `hobbit_farmer`, `bree_farmer`, `gondor_farmer`, `rohan_farmer`, `easterling_farmer`, `harnedor_farmer`, `tauredain_farmer`, `dorwinion_vinekeeper` | 0 | farmhand / vinehand (40/+50), Farmer task |
| `bree_captain` | +100 | Bree guard (20/0); banner bearer (40/+150) |
| `ranger_north_captain` | +300 | ranger (50/0); ranger + horse (70/+100); banner (70/+150) |
| `dwarf_commander`, `blue_dwarf_commander` | +200 | dwarf civilian-militia (20/0); warrior D (30/+50); axe-thrower D (50/+100); warrior + boar D (50/+150, boar armor); axe-thrower + boar D (70/+200); banner D (50/+200) |
| `galadhrim_lord` | +300 | Galadhrim elf (30/0); warden E (40/+50); warrior E (50/+100); warrior + horse E (70/+200); banner E (70/+250) |
| `high_elf_lord`, `rivendell_lord` | +300 | elf (30/0); warrior E (50/+100); warrior + horse E (70/+200); banner E (70/+250) |
| `wood_elf_captain` | +250 | wood elf (30/0); scout E (40/+50); warrior E (50/+100); warrior + elk E (70/+200); banner E (70/+250) |
| `dale_captain` | +100 | levyman (20/0); soldier (30/+50); archer (50/+100); soldier + horse (50/+150); Dale banner, Esgaroth banner (50/+200 each) |
| `dorwinion_captain` | +150 | guard (40/0); crossbower (60/+50); banner (60/+150) |
| `dorwinion_elf_captain` | +250 | elf warrior (50/0); elf archer (70/+50); banner (70/+150) |
| `rohirrim_marshal` | +150 | warrior (30/0); archer (50/+50); warrior + horse (50/+100); archer + horse (70/+150); banner (50/+150) |
| `gondor_captain` | +200 | levyman (20/0); soldier (30/+50); archer (50/+100); soldier + horse (50/+150); Tower Guard P (50/+250); banner (50/+200) |
| `dol_amroth_captain` | +200 | soldier (30/0); archer (50/+50); Swan Knight P (50/+100); soldier + horse (50/+100, armor 50%); Swan Knight + horse P (70/+200); banner (50/+150) |
| `lossarnach_captain` | +150 | Gondor levy (20/0); axeman (30/+50); banner (50/+200) |
| `pelargir_captain` | +200 | Lebennin levy (20/0); marine (30/+50); banner (50/+200) |
| `pinnath_gelin_captain` | +200 | levy (20/0); soldier (30/+50); soldier + horse (50/+150); banner (50/+200) |
| `blackroot_captain` | +150 | levy (20/0); soldier (30/+50); archer (50/+100); soldier + horse (50/+150); banner (50/+200) |
| `lebennin_captain` | +150 | levy (20/0); Gondor soldier (30/+50); archer (50/+100); banner (40/+150) |
| `lamedon_captain` | +200 | hillman (15/0); soldier (30/+50); archer (50/+100); soldier + horse (50/+150); banner (50/+200) |
| `ranger_ithilien_captain` | +300 | ranger (50/0); banner (70/+150) |
| `dunlending_warlord` | +100 | Dunlending (15/0); warrior (30/+50); archer, axe-thrower (50/+100); berserker P (50/+200); banner (50/+200) |
| `uruk_hai_mercenary_captain` | +150 | snaga (20/0); snaga archer (40/+50); Uruk (40/0); crossbower (60/+50); sapper (70/+100); berserker P (60/+150); warg (20/0); snaga + warg (40/+100, 50% armor); snaga archer + warg (60/+150); warg bombardier (50/+250); banner (60/+150) |
| `mordor_orc_mercenary_captain` | +150 | orc (20/0); archer (40/+50); bombardier (50/+100); warg (20/0); orc + warg (40/+100, 50% armor); archer + warg (60/+150); warg bombardier (50/+250); Olog-hai P (120/+350); Mordor and Minas Morgul banners (40/+150) |
| `black_uruk_captain` | +400 | all P: Black Uruk (60/+250); archer (80/+300); Olog-hai (120/+350); banner (80/+400) |
| `mordor_orc_spider_keeper` | +250 | spider (30/0); orc (20/0); archer (40/0); orc + spider (50/+50); archer + spider (70/+100); Nan Ungol banner (40/+150) |
| `mordor_orc_slaver` | +200 | Nurn slave (40/0), Farmer task |
| `angmar_orc_mercenary_captain` | +150 | orc (20/0); archer (40/+50); bombardier (50/+100); warg (20/0); orc + warg (40/+100, 50%); archer + warg (60/+150); warg bombardier (50/+250); hill troll (100/+250); mountain troll P (120/+350); banner (40/+150) |
| `angmar_hillman_chieftain` | +100 | hillman (15/0); warrior (30/+50); axe-thrower (50/+100); hillman + warg (35/+100); warrior + warg (50/+150, 30%); axe-thrower + warg (70/+200, 30%); banner (50/+200) |
| `gundabad_orc_mercenary_captain` | +100 | orc (15/0); archer (35/+50); warg (20/+50); orc + warg (35/+100); archer + warg (55/+150); Uruk P (40/+250); Uruk archer P (60/+300); banner (35/+150) |
| `dol_guldur_orc_chieftain` | +150 | orc (20/0); archer (40/+50); Mirkwood spider (20/0); orc + spider (40/+100); archer + spider (60/+150); Mirk troll P (100/+350); banner (40/0) |
| `easterling_warlord` | +150 | levy (20/0); warrior (30/+50); archer (50/+100); gold warrior P (50/+200); warrior + horse (50/+150); archer + horse (70/+200); gold warrior + horse P (70/+300); fire thrower (60/+150); banner (50/+200) |
| `near_haradrim_warlord` | +150 | warrior (30/0); archer (50/+50); Southron champion + horse P (60/+100); banner (50/+150) |
| `harnedor_warlord` | +150 | warrior (20/0); archer (40/+50); warrior + horse (40/+100); archer + horse (60/+150); banner (40/+150) |
| `umbar_captain` | +150 | warrior (30/0); archer (50/+50); warrior + horse (50/+100); banner (50/+150) |
| `corsair_captain` | +150 | corsair (20/0); marked as a special corsair unit |
| `corsair_slaver` | 0 | Harad slave (40/0), Farmer task |
| `nomad_warlord` (`nomad_chieftain`) | +150 | warrior (20/0); archer (40/+50); warrior + camel (40/+100); archer + camel (60/+150); banner (40/+150) |
| `gulf_harad_warlord` | +150 | warrior (30/0); archer (50/+50); warrior + horse (50/+100); archer + horse (70/+150); banner (50/+150) |
| `moredain_chieftain` | +150 | warrior (20/0); warrior + zebra (40/+100); banner (40/+150) |
| `tauredain_chieftain` | +200 | warrior (30/0); blowgunner (50/+50); banner (50/+150) |
| `half_troll_warlord` | +200 | half-troll (30/0); warrior (50/+100); warrior + rhino (70/+200, 50%); banner (70/+150) |

Patterns worth copying: civilians-as-militia at 15-20 coins with no extra alignment; basic warriors 20-30; archers 40-50 at +50..+100; mounted +20 coins and +50..+100 alignment over the foot version; banner bearers 40-80 at +150..+250 extra; elites 50-120 and pledge-locked.

### Mercenary (self-hiring)

- `moredain_mercenary`: base cost 20, required alignment 0 with its hiring faction (Near Harad), no pledge. The player talks to the NPC itself and gets the same unit screen with just that one entry. Only possible while it is not already hired.

## 4. Hiring flow

- Right-click the unit trader opens a small menu: talk, or open the unit screen (only if the player meets the trader's level). The unit screen lists every unit with cost, required alignment and pledge requirement; units the player cannot afford are greyed out.
- When hiring, the player may type a **squadron** name (up to 200 characters) that is stored on the unit.
- Purchase deducts coins, plays a trade sound, spawns the unit at the player's feet facing away, and (for mounted entries) spawns and mounts the mount. If the mount is itself an NPC (warg, spider), it is hired too under the same owner and squadron.
- The trader's screen also offers the faction's **conquest horn** for 2000 coins at +1500 alignment (see combat-trade.md).
- Each hire increments the player's per-faction "units hired" statistic.

## 5. Ownership, loyalty and desertion

- A unit stores its owner's UUID, the alignment needed to command it (= its purchase requirement) and its pledge condition.
- Every update, if the owner is online and their alignment with the unit's faction drops below that requirement, or they no longer meet the pledge condition (e.g. switched pledge), the unit **deserts**: the owner is told, it becomes an ordinary NPC of its faction again, a farmhand drops its inventory.
- The owner can **dismiss** a unit from its menu; a mounted rider and its hired mount are dismissed together. Dismissed units become ordinary NPCs and can despawn.
- Hired units never attack their owner or the owner's mount, never attack other units of the same owner unless the factions are enemies, and are never damaged by their owner.
- Fellowships: an option prevents members from damaging each other's units (except during a siege).

## 6. Commands

### Unit menu (right-click own unit)

Opens: talk, or the command screen.

Warrior command screen:

- **Teleport automatically** toggle (default on).
- **Guard mode** toggle: the unit takes its current position as a home point and stays within the **guard range** (default 8, settable 1-64). It fights anything hostile in range and walks back when idle. In guard mode it ignores horns and the command sword.
- **Guard range** slider.
- **Inventory**: the player can give the unit replacement helmet, chest, legs, boots, melee weapon, bomb and ranged weapon. Originals are remembered; the player's items are what drop on death.
- **Dismiss**.
- Shows level, XP progress, kill count, status (ready, halted, guarding, in combat), required alignment and pledge.

Farmhand command screen:

- **Guard mode** = "farming here": the farmhand works the area within the guard range (default 8, max 64) around where it was set.
- Guard range slider, **inventory** (4 slots: seeds, two output slots, bonemeal), dismiss. Not in guard mode it simply follows.

### Following

- A following unit paths to the owner when more than 8 blocks away and stops within 6. It walks through water when following.
- Path speed is normalized so all units keep up (speed multiplier ~0.37 / own speed attribute).
- If it cannot path and is farther than max(sense range, 24) blocks, it **teleports** to a random safe spot 3-6 blocks from the owner (if the toggle is on). Mounted units teleport with their mount.
- Banner bearers prefer to follow the nearest fellow unit of their faction more than 8 blocks away, spreading the banner buff over the group.
- Fast travel (waypoints) brings along every following unit (and mounts) within 256 blocks. Units do not follow through dimension portals.

### Command horn (one item, four modes)

- Modes: blank (opens a screen to choose), **halt**, **ready**, **summon**. Using halt or ready toggles the horn to the other mode after use.
- Effect applies to every loaded unit owned by the player whose squadron matches the horn's squadron (empty matches empty). Takes 2 s to blow; loud sound.
  - Halt: unit stands still, drops its target, and does not seek new targets.
  - Ready: resume following.
  - Summon: teleport to the owner (with a failsafe to the owner's exact position).
- Units in guard mode or with the Farmer task ignore horns.

### Command sword

- A sword (weak damage) that, when used, ray-casts up to 64 blocks. Units owned by the player within 12 blocks (matching squadron, not guarding) each pick a valid target within 6 blocks of the aimed point (using the target-spreading sort) and attack it. Aiming at nothing cancels sword-given orders.
- A unit attacking on command is flagged so its victim's faction does not penalize a defender (see npcs.md section 4).

### Squadrons

- A squadron is a free-text tag on a unit and on horns/swords (renamable via a GUI). Commands only reach units with a matching tag, case-insensitive. Untagged items only command untagged units.

## 7. Combat behavior of hired units

- They seek the same enemies their faction would, plus: anything their owner attacks, anything that attacks the owner, bandits, and members of neutral factions the owner is hostile with.
- Civilian-type units (hired militia like `dunlending`) still fight.
- When halted they don't seek targets.
- They heal 1 HP every 40 ticks out of combat; extra healing near banner bearers.
- Kills by units give the owner no alignment and no kill statistics; they give 25% conquest if the owner is within 64 blocks. Config can let unit kills count toward bane enchantments.
- A unit says a line to its owner after 1 in 3 enemy kills and occasionally while idle (see npcs.md speech).

## 8. Leveling

- Only Warrior-task units level, and only if unit leveling is enabled in config (default on).
- XP: **1 per kill of an NPC from an enemy faction**. Players and non-enemy kills give no XP. A rider and its hired mount share XP.
- Total XP for level L = floor(3 x (L - 1) x 1.08^(L - 2)). Selected thresholds:

| Level | Total XP | Level | Total XP |
| --- | --- | --- | --- |
| 2 | 3 | 10 | 49 |
| 3 | 6 | 15 | 114 |
| 4 | 10 | 20 | 227 |
| 5 | 15 | 25 | 422 |
| 6 | 20 | 30 | 750 |
| 8 | 33 | 40 | 2179 |

- Each level: **+1 max HP** (and +1 for a non-NPC mount it rides), healed by 1, chat message to the owner, fireworks in the unit's faction color (bigger every 5th level). No damage or armor gain. Older saves that gave +2 per level were corrected down.
- There is no level cap.
- Clients can show unit health bars and icons above units (config).

## 9. Death

- The owner gets a chat death message (toggleable per player).
- Drops: the gear the player gave it, then its original gear at the normal NPC drop chance. No coins, rare drops or pouches. A farmhand drops its inventory.
- No refund, no respawn, no corpse recovery.
- If a stone troll unit turns to stone in daylight, the owner is told.

## 10. Farmhands in detail

- Actions in priority loop: hoe dirt/grass near water within range, plant seeds from its seed slot, harvest ripe crops (needs space in output), deposit outputs into a chest when a slot reaches 16, collect seeds/bonemeal from a chest when it has 16 or fewer, bonemeal crops if it has bonemeal.
- It only uses chests within its range (min 24 blocks search), and only works while hired and in "farming" mode.
- Farmhands also chase rabbits that eat crops.
- Farmhand types: hobbit, Bree, Gondor, Rohan, Easterling, Harnedor, Taurethrim farmhands, Dorwinion vinehands (grapes), Nurn and Harad slaves.

## 11. Mounts for units

- Mounted entries spawn the mount with the unit. Horses spawned for NPCs are flagged as belonging to an NPC (not rideable by players while the NPC rides) and take the rider's navigation range.
- When an NPC is mounted: sense range x1.5, melee reach x1.5, it uses its mounted weapon (lances for Rohan, Gondor, Dol Amroth), and mount charge speed is 1.7x by default (2.0 for Rohirrim, bounders, Swan Knights; 1.9 for Southron champions; 1.5 for half-trolls).
- If the rider dies, the mount stays (a hired NPC mount like a warg stays hired). If the mount dies the unit fights on foot.
- Mount armor chance per entry: 100% for most horse entries, 50% for warg/rhino entries and some Dol Amroth horses, 30% for hillman wargs.

## 12. Things Legacy does not have

- No upkeep or wages, no unit limit (an achievement exists for 100 units nearby), no formations, no per-unit stances beyond follow/halt/guard, no ranged/melee preference toggle, no healing items for units, no XP from training.
