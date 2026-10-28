# Legacy reference: achievements and titles

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: what achievements exist and how they are earned, what they unlock, and how player titles work. Numbers were counted from the mod's own data, so they are exact for this build; descriptions are summaries, not transcriptions. Achievement names and descriptions are deliberately not reproduced.

## 1. Achievements at a glance

| Fact | Value |
| --- | --- |
| Total achievements (Middle-earth + Utumno) | 668 |
| Hand-authored entries | 492 |
| Auto-generated "reach rank" entries | 176 (one per faction rank, 24 factions) |
| Entries tied to entering a biome | about 95 |
| Entries that also grant a title | 96 |
| Categories | 31 (30 for Middle-earth, 1 for Utumno) |
| Rewards | None directly (no XP, no items). Indirect unlocks, see section 5 |

Achievements are the mod's own system, not vanilla advancements. They have no parent/child tree: each category is a flat list. Every achievement belongs to exactly one category, and every category belongs to one dimension (Middle-earth or Utumno). Counts in the menu and in unlock formulas only use the Middle-earth dimension.

## 2. Categories and counts

Each category has a display name and a tint colour, taken either from a representative faction's colour or from a representative biome's map colour. "General" uses a fixed parchment-gold colour.

| Category (region) | Hand-authored | of which biome entry | Rank entries | Total |
| --- | --- | --- | --- | --- |
| General | 66 | 0 | 0 | 66 |
| The Shire | 18 | 2 | 7 | 25 |
| Blue Mountains | 11 | 1 | 8 | 19 |
| Lindon | 11 | 2 | 8 | 19 |
| Eriador | 41 | 11 | 14 | 55 |
| Bree-land | 16 | 2 | 7 | 23 |
| Angmar | 10 | 1 | 7 | 17 |
| Eregion | 1 | 1 | 0 | 1 |
| Enedwaith | 3 | 3 | 0 | 3 |
| Dunland | 8 | 1 | 7 | 15 |
| Misty Mountains | 3 | 1 | 0 | 3 |
| Forodwaith | 1 | 1 | 0 | 1 |
| Rhovanion | 10 | 10 | 0 | 10 |
| Mirkwood | 19 | 3 | 14 | 33 |
| Dale | 11 | 2 | 7 | 18 |
| Iron Hills | 17 | 1 | 8 | 25 |
| Lothlorien | 12 | 1 | 8 | 20 |
| Fangorn | 7 | 1 | 5 | 12 |
| Rohan | 27 | 4 | 15 | 42 |
| Gondor | 45 | 15 | 8 | 53 |
| Nindalf | 5 | 2 | 0 | 5 |
| Mordor | 28 | 4 | 8 | 36 |
| Dorwinion | 16 | 2 | 8 | 24 |
| Rhun | 18 | 6 | 8 | 26 |
| Orocarni | 1 | 1 | 0 | 1 |
| Near Harad | 42 | 8 | 8 | 50 |
| Far Harad savannah | 11 | 3 | 7 | 18 |
| Far Harad jungle | 13 | 1 | 7 | 20 |
| Pertorogwaith | 8 | 2 | 7 | 15 |
| Ocean | 2 | 2 | 0 | 2 |
| Utumno (own dimension) | 11 | 0 | 0 | 11 |

Observations:

- The distribution is very uneven. Six categories have 1 to 3 entries (Eregion, Enedwaith, Misty Mountains, Forodwaith, Orocarni, Ocean); four have 50 or more.
- Categories with two playable factions (Eriador: Rangers + Gundabad; Mirkwood: Wood-elves + Dol Guldur; Rohan: Rohan + Isengard) get roughly double the rank entries.
- Rank achievements are placed in the category the faction is assigned to, not in a separate "factions" tab.

## 3. What achievements are awarded for (grouped rules)

Counts below are approximate groupings by what the entry checks, across all categories.

| Rule family | Approx. count | Behavior |
| --- | --- | --- |
| Enter a biome or region | ~100 | Granted on the server's periodic player check when the player stands in a biome that has an entry achievement. Each biome has at most one. Entering a biome also unlocks that region's fast-travel waypoints (same check). |
| Reach a faction rank | 176 | One per rank of each of 24 playable factions. Granted when alignment reaches the rank threshold. For ranks above the faction's pledge rank, the player must also be pledged to that faction. |
| Trade with a specific trader type | ~95 | First completed trade with a named kind of trader (captain, smith, innkeeper, market stall, farmer...). |
| Kill a faction's NPC / specific monster | ~70 | First kill of a member of a faction, a specific unit, a boss, or a notable creature. One-shot, not tiered. |
| Wear a full armour set | ~50 | Wearing all four pieces of one faction's armour at once. |
| Use a faction crafting table | ~35 | First use of each faction's crafting table. |
| Complete a mini-quest for a faction group | 32 | First completed mini-quest from that quest group (see mini-quests notes), plus two bounty-hunter milestones. |
| Hire a unit | ~13 | First hire of a specific unit type. |
| Craft / smelt / brew / cook | ~25 | First time making a notable item or alloy. |
| Ride / tame a mount | ~6 | First ride of a specific mount. |
| Marry an NPC | 3 | Hobbit, Blue Mountains dwarf, Durin's Folk dwarf. |
| Misc. one-offs | ~40 | Drinking, eating odd foods, fishing up a ring, pickpocketing, reforging, engraving, getting drunk, killing while drunk, being hit by a thrown weapon, collecting 10 different crafting tables, holding 128 crossbow bolts, having 100 hired units within 64 blocks, defeating an invasion, pledging, conquest. |
| "Explorer" milestones | 5 | Earned 10, 20, 30, 40, 50 biome-entry achievements. These are counts of biome achievements, not distance travelled. |

Notes on the "General" tab: it holds the first-entry achievement, the explorer milestones, cross-faction one-offs (for example "kill any orc", bane enchantments, high alcohol tolerance), bounty hunter milestones, and banner protection.

## 4. Earning rules and edge cases

- **Alignment gating.** Many entries carry an "ally of" or "enemy of" requirement (one or more factions). "Ally" means alignment >= 0 with at least one listed faction; "enemy" means alignment <= 0 with at least one listed faction. Example: trading with Hobbit traders requires being an ally of the Hobbits; killing a Hobbit requires being an enemy.
- **Earned but not counted.** If the player triggers an entry they are not eligible for, the mod still records it, but it is hidden from the list, is not announced, and does not count toward totals. If alignment later flips to the eligible side, it starts counting and appears as earned. So eligibility is evaluated at display/count time, not only at award time.
- **Rank achievements** use a stricter check: alignment must be non-negative, and ranks above the pledge rank require an active pledge. Their display name is the rank's name (gendered if the rank is gendered); their description says "reach X alignment" or "pledge and reach X".
- **No double award.** An achievement is awarded once per player and stored permanently in player data. Removal is possible by an operator command.
- **Siege lock.** An inter-mod hook can put a player in a "siege" state; while it is active no achievements are awarded and periodic checks are skipped.
- **Periodic checks.** Biome entry, entering the dimension, inventory-based checks (pouch, crafting tables, crossbow bolts) run in the server's per-player tick. The "100 hired units nearby" check runs once per second.
- **Broadcast.** Every earned achievement is announced to the whole server in chat: player name, dimension name, and the achievement name in yellow, wrapped in brackets, with a hover tooltip showing its details. A server config can suppress the broadcast for the "killed a Hobbit" entry only, so new evil players are not hunted for it.
- **Client toast.** The earning player also gets an on-screen notification.

## 5. Rewards and unlocks

Achievements give no XP or items. Their value is indirect:

| Unlock | Formula (Middle-earth achievements counted) |
| --- | --- |
| Custom waypoints available | 5 + floor(achievements / 5) |
| Fellowships the player may lead | 1 + floor(achievements / 20) |
| Achievement shields | 25, 50, 100, 200 achievements for the four tiers (bronze, silver, gold, mithril) |
| Special shields | Three specific achievements unlock a shield each (high alcohol tolerance, defeating the mountain-troll chieftain, defeating the mallorn ent) |
| Titles | 96 achievements unlock a title (section 7) |

The UI text explicitly tells players to "earn more achievements" to unlock more waypoints and fellowships, so achievements function as a progression currency.

## 6. Achievements screen

- Opened from the Middle-earth menu. One category at a time; arrows or a dropdown move between categories. Header shows "dimension Achievements (earned/total)"; each category shows "(earned/total)".
- Each entry is a card with icon, name and description. Earned cards are drawn bright, unearned cards greyed.
- Entries the player is not currently eligible for (wrong side of an alignment gate) are hidden entirely and excluded from the total.
- Sort order inside a category: "special" entries first (rank entries, first entry, explorer milestones) by id; then biome entries alphabetically; then everything else alphabetically.

## 7. Titles

### 7.1 Counts by source

| Source | Count | How earned |
| --- | --- | --- |
| Starter | 31 | Always available to everyone. Generic role words (adventurer, scholar, warrior, merchant...). |
| Exclusive | 12 | Granted from an online player-details service: mod developers, moderators, translators, Patreon patrons, lore contest winners (four years), build contest entrants/winners. Hidden from the list unless owned. |
| Faction alignment | 175 | Tolkien place and people names grouped per faction (for example Shire districts for Hobbits, Bree villages for Bree). Require alignment at the faction's pledge level (usually +100). |
| Multi-faction | 25 | Broad people names (Free Peoples, Elf, Orc, Dwarf, Dunedain, Haradrim...). Require +100 with any one of a listed group of factions. |
| Any faction | 1 | Requires +10,000 with any single faction. |
| Achievement | 96 | Usable once the linked achievement is earned (and counted). Most reuse the achievement's own name; a few have a separate title name. |
| Rank | ~187 | One per faction rank (176), plus feminine variants for 11 gendered ranks. Usable while alignment is at or above the rank threshold. |
| **Total** | **about 527** | |

### 7.2 Requirement rules

- **Rank titles:** alignment >= rank threshold. Ranks above the pledge rank also need a pledge to that faction. A server option ("strict faction titles") additionally requires a pledge for the pledge rank itself.
- **Faction alignment titles:** alignment >= the faction's pledge-rank value. With the strict option on, a pledge is also required.
- **Gendered ranks:** the player data stores a feminine-rank preference; gendered ranks show the feminine name for that player.
- **Exclusive titles:** checked against data fetched from an external web service at login. Titles are only revoked after that data has arrived, so a slow or failed fetch does not strip a title.

### 7.3 Selecting and displaying

- The Titles page lists every title the player can see (exclusive ones only if owned). Usable titles are white (yellow on hover); unusable ones are grey with a description of how to unlock.
- The player picks one title, or none. After picking, a row of the 16 vanilla chat colours appears; the player chooses one. Default colour is white. The pair (title, colour) is sent to the server and validated there.
- **Display:** the selected title is shown as a coloured bracketed prefix before the player's name in chat messages only. It is also shown next to names in the fellowship member list. It is **not** shown on the nameplate above the head or in the tab list.
- A server config disables titles entirely.
- **Losing a title:** every player tick the server checks the selected title. If the player no longer qualifies (alignment dropped, pledge broken, achievement removed), the selection is cleared and the player gets a chat notice naming the lost title.
- Titles are not exclusive between players; any number of players can hold the same title.
- Offline players' selected titles are cached so they can still be shown (for example in fellowship lists).

## 8. Shields (cosmetic, related unlock track)

Covered in more detail in the banners/fellowships/menu notes. In short: 64 shield designs, 38 unlocked by +1000 alignment with a faction, 7 by achievements (four count tiers plus three specific achievements), 19 exclusive. Purely cosmetic.

## 9. Admin and config surface

- Command to grant or remove an achievement for a player (by internal name).
- Config: enable titles; strict faction title requirements; suppress the Hobbit-kill broadcast.

## Differences from our draft spec

Compared with `specs/progression/data-model.md` (draft, 2026-10).

| Topic | Legacy | Our draft | Recommendation |
| --- | --- | --- | --- |
| Tab count | 31 categories, 6 of them with 1 to 3 entries | 14 tabs (10 region + 4 category) | Keep 14; merging tiny regions is an improvement. Make sure rank entries still land in their region tab, not a "factions" tab. |
| Total entries | 668 | ~1,000 by G4 | 1,000 is ambitious; plan for ~650 to 700 at parity and treat the rest as stretch. |
| Kill achievements | First kill only, no tiers | Kill tiers 1/50/250/1000 per faction | Keep tiers, but they are our addition; budget their lang text, since 4 tiers x 24 factions adds ~96 entries. |
| Travel achievements | Count of distinct biomes entered (10/20/30/40/50), not distance | Distance tiers 1k to 500k blocks | Add a "distinct biomes visited" family; it is the original's exploration hook. Distance can stay as an extra. |
| Rewards | No XP/items; achievement count unlocks waypoints (5 + n/5), led fellowships (1 + n/20), shields (25/50/100/200) | Vanilla XP allowed; title grants via datapack map | Add a Core-readable "achievement count" helper; waypoints, fellowships and shields will need it. |
| Alignment gating | Entries carry ally/enemy requirements; ineligible entries are hidden and not counted, re-evaluated live | Not modelled | Add an optional `requires` condition (ally/enemy of faction(s)) evaluated at award time; live re-evaluation is impractical with vanilla advancements, so accept award-time only. |
| Broadcast | Every achievement announced server-wide, one config exception | `announce_to_chat` on most | Matches; add a per-entry or config denylist for griefing-sensitive entries. |
| Title sources | 5 kinds: starter, exclusive, faction-alignment, achievement, rank (+ multi-faction and any-faction variants) | 3 kinds: advancement, alignment_rank, special | Add `starter` (always) and `alignment` (min value with any of N factions) source types; `special` covers exclusive. |
| Rank title pledge rule | Ranks above pledge rank require an active pledge | Rank >= threshold only | Add `requires_pledge` derived from Core's rank model. |
| Gendered ranks | 11 ranks have feminine names chosen by a player preference | Not modelled | Add an optional feminine text per rank title and a player preference; small cost now, painful later. |
| Title colour | Player picks 1 of 16 chat colours | Fixed per title definition | Let the player pick (16 colours), keep the definition colour as the default. |
| Where shown | Chat prefix and fellowship list only | Chat, nameplate and tab list | Keep ours (strictly more visible); make nameplate display a client option. |
| Losing a title | Checked every tick, cleared with chat notice | Checked on login and alignment change | Ours is equivalent and cheaper; keep. |
| Title count | ~527 | Not sized | Expect ~500 title definitions; datagen them from faction/rank tables like the advancements. |
