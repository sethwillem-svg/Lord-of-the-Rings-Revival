# Hiring, Reputation and Disguise Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Players earn reputation with Middle-earth's factions, hire their soldiers from leaders, and command them as squads in formations; a full enemy armour set disguises them.

**Architecture:** All rules that are plain arithmetic (tiers, kill rule, coins, formation slots and bonuses, levels) live in `army.core`, which imports only `java.*`, so it is unit-tested with plain JUnit. Everything that touches Minecraft or Middle-earth lives in the other `army.*` packages, tested with GameTests. Hired units stay Middle-earth `NpcEntity`s carrying our entity attachment; all per-player army state lives in one world `SavedData` keyed by player UUID, because units can die and earn reputation while their owner is offline.

**Tech Stack:** Java 21, NeoForge 21.1.244 (Minecraft 1.21.1), ModDevGradle 1.0.17, the Middle-earth jar (compileOnly plus runtimeOnly), JUnit 5, NeoForge GameTests.

**Spec:** `specs/addon/hiring-reputation.md`

## Global Constraints

- Clean room: never copy code, data, textures or lang text from Middle-earth, Seven Stars API or the original LOTR mod. Use their public classes, and accessor mixins on their fields, only.
- Mod id `lotr_revival`, base package `io.github.sethwillemsvg.lotrrevival`, ids via `LotrRevival.id("path")`, registry names `snake_case`.
- Middle-earth ids use namespace `middle-earth` (for example `middle-earth:gondor`, `middle-earth:longbeards.erebor`). Factions live in the dynamic registry `DynamicRegistriesME.FACTION`, NPC types in `DynamicRegistriesME.NPC_TYPE`.
- `army.core` imports nothing outside `java.*`; ids there are `String`s.
- Every number in the spec's "Rules and numbers" is a config value in `ArmyConfig` (common config `lotr_revival-common.toml`), except the sworn-enemy table, which is data.
- Networking uses `CustomPacketPayload`. Every C2S handler revalidates on the server: ownership, distance, liveness, cost.
- GameTests go in `army/test/`, use `@GameTest(template = "empty")` with `@GameTestHolder(LotrRevival.MOD_ID)` and `@PrefixGameTestTemplate(false)`, and remove mock players in a `finally`.
- Java 21, 4-space indent, no wildcard imports. Commits look like `feat(addon): ...` and end with the session's attribution lines.
- Build commands need `-PmeJar=<path to the Middle-earth jar>`. In the cloud container that is `/tmp/claude-0/-home-user-lord-of-the-rings-revival/c42c9528-ff28-5f34-8634-a3341f53121d/scratchpad/me/middle-earth/build/libs/Middle-earth-1.0.2-1.21.1-beta-backport.1.jar`, written below as `$ME`. Do not launch the game client in the container; the maintainer playtests on their machine.

## Deviations from the spec (apply to the spec in Task 2)

1. Army state is a world `SavedData` keyed by player UUID, not a player attachment, so a unit dying, or getting a kill, while its owner is offline still updates the owner.
2. Squad number keys work while the command wheel is open (R held). Otherwise 1-9 stay hotbar keys.
3. Movement orders are vanilla `Goal`s, not Seven Stars brain tasks. `NpcEntity` uses goals for movement and its brain only for fighting, so Seven Stars is not needed for this project.
4. The disguise icon is the worn helmet's item icon plus the faction name (no new art).
5. Hostility filtering uses NeoForge's `LivingChangeTargetEvent`, not a mixin on `TargetPlayerDiplomacyGoal`.

## Review Focus

1. **A unit targeting its owner or a fellow unit through any path**: Middle-earth diplomacy, `HurtByTargetGoal` after friendly fire, or charging into a crowd. Expect never. Test in Task 7.
2. **Paying with an awkward mix of coins**, for example only gold for a 30-copper hire, or exactly enough in mixed coins. Expect the exact value taken and the change returned. Test in Task 3.
3. **The server, or the owner, restarting while units are mid-order.** Expect units, squads, orders and fallen records to come back, and units with no online owner to stand still. Test in Tasks 5 and 7.
4. **A hire or revive packet sent from far away, for someone else's unit, or with a forged price.** Expect it refused, with nothing paid or spawned. Test in Task 10.
5. **Mixed squads**: shields and spears with bare hands in shield wall or spear line. Expect capable units in front and the rest in line behind. Test in Tasks 6 and 7.

---

### Task 1: Reputation core (tiers and kill rule) with JUnit

**Files:**
- Modify: `addon/build.gradle` (JUnit 5)
- Create: `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/army/core/Tier.java`
- Create: `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/army/core/FactionRelations.java`
- Create: `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/army/core/KillRule.java`
- Test: `addon/src/test/java/io/github/sethwillemsvg/lotrrevival/army/core/TierTest.java`, `KillRuleTest.java`

**Interfaces:**
- Produces:
  - `enum Tier { HOSTILE, NEUTRAL, TRUSTED, RESPECTED, HONOURED, CHAMPION, HERO }`, with `static Tier of(int score, int[] thresholds)` and `int cap(int[] caps)`.
  - `static int Tier.armyCap(Collection<Integer> scores, int[] thresholds, int[] caps, int max)`.
  - `interface FactionRelations { Set<String> friendsOf(String f); Set<String> hostilesOf(String f); Optional<String> swornEnemyOf(String f); Set<String> all(); }`. Friends are the factions f lists as ALLY or FRIENDLY; hostiles are the ones it lists as HOSTILE.
  - `static Map<String, Integer> KillRule.changes(String victimFaction, FactionRelations r, int multiplier, boolean byUnit)`.

- [ ] **Step 1: Add JUnit to `addon/build.gradle`**: `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'`, `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`, `tasks.named('test') { useJUnitPlatform() }`. Do not enable ModDevGradle's `unitTest`: these tests need no game.

- [ ] **Step 2: Write the failing tests**

```java
// TierTest
static final int[] T = {0, 100, 300, 700, 1500, 3000};   // NEUTRAL..HERO lower bounds
static final int[] CAPS = {0, 0, 4, 8, 14, 22, 30};       // per Tier ordinal
@Test void tiers() {
    assertEquals(Tier.HOSTILE, Tier.of(-1, T));   assertEquals(Tier.NEUTRAL, Tier.of(0, T));
    assertEquals(Tier.TRUSTED, Tier.of(100, T));  assertEquals(Tier.RESPECTED, Tier.of(300, T));
    assertEquals(Tier.HERO, Tier.of(10_000, T));
}
@Test void capIsBestTierClampedToMax() {
    assertEquals(8, Tier.armyCap(List.of(-500, 100, 300), T, CAPS, 30));
    assertEquals(20, Tier.armyCap(List.of(3000), T, CAPS, 20));
    assertEquals(0, Tier.armyCap(List.of(), T, CAPS, 30));
}
// KillRuleTest: fake relations. moria friends {goblin_town}, hostiles {gondor, rohan, longbeards, longbeards.erebor},
// sworn enemy longbeards.erebor; goblin_town friends {moria}
@Test void plainKill()  { assertEquals(Map.of("moria", -2, "goblin_town", -1, "longbeards.erebor", 2, "gondor", 1, "rohan", 1, "longbeards", 1),
                                       KillRule.changes("moria", R, 1, false)); }
@Test void leaderKill() { assertEquals(-20, KillRule.changes("moria", R, 10, false).get("moria"));
                          assertEquals(20, KillRule.changes("moria", R, 10, false).get("longbeards.erebor")); }
@Test void unitKillHalvesTowardZero() { var c = KillRule.changes("moria", R, 1, true);
    assertEquals(-1, c.get("moria")); assertEquals(1, c.get("longbeards.erebor")); assertFalse(c.containsKey("goblin_town")); assertFalse(c.containsKey("gondor")); }
@Test void unknownFaction() { assertEquals(Map.of(), KillRule.changes("nobody", R, 1, false)); }
@Test void victimNeverInOwnFriendList() { /* relations where moria lists itself as ALLY: moria still -2 only */ }
```

- [ ] **Step 3: Run them and see them fail.** Run `cd addon && ./gradlew test -PmeJar=$ME`. Expected: compilation fails because `Tier` and `KillRule` don't exist.

- [ ] **Step 4: Implement `Tier`, `FactionRelations`, `KillRule`**
  - `changes`: the victim gets -2; each friend gets -1; the sworn enemy gets +2; each other hostile gets +1 (a sworn enemy that is also listed as hostile gets +2 only, once).
  - Multiply by `multiplier`, then integer-divide by 2 when `byUnit` (Java division rounds toward zero).
  - Drop zero entries. A faction not in `r.all()` gives an empty map.

- [ ] **Step 5: Run the tests.** Same command; expected PASS.

- [ ] **Step 6: Commit**: `feat(addon): reputation tiers and kill rule`.

### Task 2: Army store, Middle-earth relations, reputation service and `/reputation`

**Files:**
- Modify: `addon/build.gradle` (`gameTestServer` run)
- Modify: `addon/src/main/resources/lotr_revival.mixins.json` (add a `"mixins"` list)
- Modify: `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/LotrRevival.java`
- Modify: `specs/addon/hiring-reputation.md` (the five deviations above)
- Create `army/` (under `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/`):
  - `ArmyConfig.java`
  - `data/PlayerArmy.java`, `data/ArmyStore.java`
  - `rep/SwornEnemies.java`, `rep/MeRelations.java`, `rep/Reputation.java`, `rep/ReputationCommand.java`
  - `me/MeBridge.java`
- Create mixins: `mixin/FactionAccessor.java`, `mixin/InitialDiplomacyAccessor.java`, `mixin/NpcTypeAccessor.java`
- Create data: `addon/src/main/resources/data/lotr_revival/sworn_enemies/*.json` (one per row of the spec table)
- Create: `addon/src/main/resources/data/lotr_revival/structure/empty.nbt`, restored from our own history with `git show 69c69ea:src/main/resources/data/lotrrevival/structure/empty.nbt`
- Test: `army/test/ArmyTestUtil.java`, `army/test/ReputationTests.java`

**Interfaces:**
- Consumes: `Tier`, `FactionRelations`, `KillRule` (Task 1).
- Produces:
  - `ArmyConfig`: `ModConfigSpec` values for every spec number. Names: `startChosen=100`, `startHostile=-100`, `repMin=-10000`, `repMax=10000`, `tierThresholds=[0,100,300,700,1500,3000]`, `tierCaps=[0,0,4,8,14,22,30]`, `maxArmy=30`, `leaderMultiplier=10`, plus the later tasks' values. Helpers `int[] thresholds()`, `int[] caps()`.
  - `PlayerArmy`, mutable, with a `Codec`:
    - fields `Map<String,Integer> reputation`, `boolean repInitialised`, `List<Squad> squads`, `Deque<Fallen> fallen`, `Map<String,Long> disguiseCooldownUntil`;
    - `int rep(String f)` and `void addRep(String f, int delta, int min, int max)`.
    - `Squad` and `Fallen` are records defined in Task 4; until then `squads` and `fallen` are empty lists.
  - `ArmyStore extends SavedData`, stored on the overworld under name `lotr_revival_armies`, with `static ArmyStore get(MinecraftServer)` and `PlayerArmy of(UUID)` (creates it on demand and marks the store dirty).
  - `MeBridge`:
    - `@Nullable String chosenFaction(Player)`, via `PlayerUtil.fetchFaction`;
    - `@Nullable String factionOf(Entity)` (an `NpcEntity`'s `getFactionIdentifier()`);
    - `boolean isLeader(NpcEntity)` (its type id is in its faction's LEADER list from `Faction.getAllNpcDatas()`);
    - `Optional<Faction> faction(Level, String)`.
  - `MeRelations implements FactionRelations`, built from the faction registry through `FactionAccessor.lotrRevival$initialDiplomacies()` and `InitialDiplomacyAccessor` (`factionId`, `affinity`), plus `SwornEnemies`. `static MeRelations get(MinecraftServer)` is cached and rebuilt on datapack reload.
  - `SwornEnemies`: a `SimpleJsonResourceReloadListener` on `sworn_enemies` (`{"faction":..., "sworn_enemy":...}`), registered in `AddReloadListenerEvent`; `Optional<String> of(String faction)`.
  - `Reputation`:
    - `static void onKill(MinecraftServer server, UUID owner, LivingEntity victim, int multiplier, boolean byUnit)` (the entry point the nemesis project reuses);
    - `static int get(MinecraftServer, UUID, String faction)`;
    - `static int armyCap(MinecraftServer, UUID)`;
    - `static void initialiseIfNeeded(ServerPlayer)`.
  - `ArmyTestUtil.withPlayer(GameTestHelper, String faction, Consumer<ServerPlayer>)`: makes a mock player, sets Middle-earth's faction via `StateSaverAndLoader.getPlayerState(p).assignNewFactionInformation(faction, <any valid spawn id of that faction, or null if accepted>)`, runs the body, and removes the player in `finally`.

- [ ] **Step 1: Wire GameTests.**
  - In `build.gradle` add `runs { gameTestServer { type = 'gameTestServer'; gameDirectory = project.file('run-gametest'); systemProperty 'neoforge.enabledGameTestNamespaces', mod_id } }`, and add `run-gametest/` to `.gitignore`.
  - Restore `empty.nbt`.
  - Add the three accessors under a new `"mixins"` list (common). They target Middle-earth classes with `@Mixin(value = ..., remap = false)`.

- [ ] **Step 2: Write the failing GameTests in `ReputationTests`**

```java
@GameTest(template = "empty") public static void startingReputation(GameTestHelper h) {
    ArmyTestUtil.withPlayer(h, "middle-earth:gondor", p -> { Reputation.initialiseIfNeeded(p);
        h.assertTrue(Reputation.get(h.getLevel().getServer(), p.getUUID(), "middle-earth:gondor") == 100, "chosen +100");
        h.assertTrue(Reputation.get(h.getLevel().getServer(), p.getUUID(), "middle-earth:mordor") == -100, "hostile -100");
        Reputation.initialiseIfNeeded(p);   // second call changes nothing
        h.assertTrue(Reputation.get(h.getLevel().getServer(), p.getUUID(), "middle-earth:gondor") == 100, "applied once"); });
    h.succeed(); }
@GameTest(template = "empty") public static void killMoriaSoldier(GameTestHelper h) {
    // spawn a non-leader moria NpcEntity via NpcEntityBuilder; Reputation.onKill(p, npc, 1, false)
    // assert: moria -2, longbeards.erebor +2, gondor +1 (moria lists gondor HOSTILE), any faction moria lists ALLY/FRIENDLY -1
}
@GameTest(template = "empty") public static void leaderKillTimesTen(GameTestHelper h) { /* a moria LEADER-rank type: moria -20 */ }
@GameTest(template = "empty") public static void clampedAtMax(GameTestHelper h) { /* addRep 20_000 → 10_000 */ }
@GameTest(template = "empty") public static void swornEnemiesLoaded(GameTestHelper h) {
    h.assertTrue(MeRelations.get(h.getLevel().getServer()).swornEnemyOf("middle-earth:rohan").equals(Optional.of("middle-earth:isengard")), "rohan→isengard");
    h.assertTrue(MeRelations.get(h.getLevel().getServer()).swornEnemyOf("middle-earth:brigand").isEmpty(), "brigand none"); h.succeed(); }
```

- [ ] **Step 3: Run them and see them fail.** Run `cd addon && ./gradlew runGameTestServer -PmeJar=$ME`. Expected: compilation fails.

- [ ] **Step 4: Implement the files listed above.**
  - Hooks registered in `LotrRevival`:
    - `LivingDeathEvent`: a player or unit killer calls `Reputation.onKill`, with multiplier `leaderMultiplier` when `MeBridge.isLeader(victim)`. Unit attribution is added in Task 4.
    - `PlayerEvent.PlayerLoggedInEvent`, and every 100 ticks in `PlayerTickEvent.Post`: call `initialiseIfNeeded`. This sets the chosen faction to `startChosen`, each of `hostilesOf(chosen)` to `startHostile`, and the flag, but only once `chosenFaction` is non-null.
    - `RegisterCommandsEvent` registers `ReputationCommand`.
  - `/reputation` prints each faction with a non-zero score: name, score, tier. `/reputation set <faction> <value>` needs permission 2 (for playtests).

- [ ] **Step 5: Run them.** Same command; expected `All N required tests passed`.

- [ ] **Step 6: Apply the five deviations to the spec** (Summary, Controls, Data and APIs, Disguise) and commit: `feat(addon): reputation from kills, sworn enemies and /reputation`.

### Task 3: Coins and hire rules (core)

**Files:**
- Create: `army/core/Coins.java`, `army/core/HireRank.java`
- Test: `addon/src/test/java/.../army/core/CoinsTest.java`, `HireRankTest.java`

**Interfaces:**
- Produces:
  - `record Coins.Purse(int copper, int silver, int gold)`.
  - `record Coins.Payment(Purse take, Purse change)`.
  - `static Optional<Payment> Coins.pay(Purse have, int price, int silverValue, int goldValue)`.
  - `static Purse Coins.largestFirst(int value, int silverValue, int goldValue)`.
  - `enum HireRank { MILITIA, SOLDIER, KNIGHT, VETERAN }`, whose price, points and required tier are read by `ordinal()` from the config arrays below (core stays free of config classes), with `static Optional<HireRank> from(String meRankName)` (empty for CIVILIAN, LEADER or unknown).
  - Config added to `ArmyConfig`: `silverValue=10`, `goldValue=100`, `rankPrices=[30,80,200,500]`, `rankPoints=[1,1,2,3]`, `rankTiers=[TRUSTED,RESPECTED,HONOURED,CHAMPION]`.

- [ ] **Step 1: Write the failing tests**

```java
@Test void smallestFirstThenChange() { assertEquals(new Payment(new Purse(5, 3, 0), new Purse(5, 0, 0)), Coins.pay(new Purse(5, 10, 0), 30, 10, 100).orElseThrow()); }
@Test void onlyGold()  { assertEquals(new Payment(new Purse(0, 0, 1), new Purse(0, 7, 0)), Coins.pay(new Purse(0, 0, 1), 30, 10, 100).orElseThrow()); }
@Test void exactMix()  { assertEquals(new Payment(new Purse(10, 2, 0), new Purse(0, 0, 0)), Coins.pay(new Purse(10, 2, 0), 30, 10, 100).orElseThrow()); }
@Test void tooPoor()   { assertTrue(Coins.pay(new Purse(10, 1, 0), 30, 10, 100).isEmpty()); }
@Test void free()      { assertEquals(new Payment(new Purse(0, 0, 0), new Purse(0, 0, 0)), Coins.pay(new Purse(0, 0, 0), 0, 10, 100).orElseThrow()); }
@Test void change()    { assertEquals(new Purse(5, 9, 1), Coins.largestFirst(195, 10, 100)); }
@Test void ranks()     { assertTrue(HireRank.from("CIVILIAN").isEmpty()); assertTrue(HireRank.from("LEADER").isEmpty());
                         assertEquals(HireRank.KNIGHT, HireRank.from("KNIGHT").orElseThrow()); }
```

- [ ] **Step 2: Run them and see them fail.** Run `./gradlew test -PmeJar=$ME`; expected compile failure.
- [ ] **Step 3: Implement.** `pay` refuses when the total value is below the price. Otherwise it takes copper, then silver, then gold, each only as many as still needed (rounding up within that coin), and stops once paid ≥ price. Change is `largestFirst(paid - price)`.
- [ ] **Step 4: Run them.** Expected PASS.
- [ ] **Step 5: Commit**: `feat(addon): coin payment and hire ranks`.

### Task 4: Units, squads, recruiting, death and revival (server)

**Files:**
- Create in `army/` (under `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/`):
  - `unit/UnitData.java`, `unit/Units.java`, `unit/UnitEvents.java`
  - `data/Squad.java`, `data/Fallen.java`, `data/ArmyRegistries.java`
  - `hire/Recruiters.java`, `hire/Recruiting.java`, `hire/HireResult.java`
- Create data: `data/lotr_revival/tags/middle-earth/npc_type/recruiters.json` (`{"replace": false, "values": []}`)
- Modify: `data/PlayerArmy.java` (squads and fallen), `rep/Reputation.java` (unit kill credit)
- Test: `army/test/RecruitTests.java`

**Interfaces:**
- Consumes:
  - `ArmyStore`, `PlayerArmy`, `MeBridge`, `MeRelations`, `Reputation` (Task 2);
  - `Coins`, `HireRank` (Task 3);
  - `Tier` (Task 1).
- Produces:
  - `record UnitData(UUID owner, int squad, int level, int xp, HireRank rank, int hirePrice, int slot)`: an entity attachment `lotr_revival:unit` (registered in `ArmyRegistries`, serialised with a codec), on `NpcEntity` only.
  - `enum Order { FOLLOW, HOLD, CHARGE }` and `enum Formation` (defined in Task 6; until then, store the formation's name as `String formation = "LINE"`).
  - `record Squad(int id, String faction, Order order, String formation, @Nullable BlockPos holdPos, float holdYaw, List<UUID> units, @Nullable Order beforeCharge)`, plus `with...` copy helpers.
  - `record Fallen(UUID id, String npcType, String faction, int level, int xp, HireRank rank, int hirePrice, int squad, Optional<Component> name, List<ItemStack> equipment /* HEAD, CHEST, LEGS, FEET, MAINHAND, OFFHAND */, long diedAt)`.
  - `Units`:
    - `static Optional<UnitData> data(Entity)`;
    - `static boolean isUnitOf(Entity, UUID owner)`;
    - `static List<NpcEntity> loaded(ServerLevel, UUID owner)`;
    - `static int pointsUsed(MinecraftServer, UUID owner)` (living units' rank points, from the squads' UUIDs resolved across all levels);
    - `static @Nullable ServerPlayer owner(Entity)`.
  - `Recruiters`:
    - `static boolean isRecruiter(NpcEntity)`: a LEADER-rank type, or in tag `lotr_revival:recruiters`, and not a unit.
    - `static List<RosterEntry> roster(NpcEntity recruiter)`, with `record RosterEntry(String npcType, HireRank rank, int price, int points, Tier required)`, from the recruiter faction's `getAllNpcDatas()` for MILITIA..VETERAN.
  - `enum HireResult { OK, NOT_RECRUITER, TOO_FAR, HOSTILE, DISGUISED, TIER_TOO_LOW, CAP_FULL, NO_SQUAD_SLOT, NOT_ENOUGH_COINS, NO_SPACE, UNKNOWN_TYPE, NOT_FOUND }`.
  - `Recruiting`:
    - `static HireResult hire(ServerPlayer, NpcEntity recruiter, String npcType, int squadOrNew /* -1 = new */)`;
    - `static HireResult revive(ServerPlayer, NpcEntity recruiter, UUID fallenId)`;
    - `static void dismiss(ServerPlayer, NpcEntity unit)`;
    - `static void discardFallen(ServerPlayer, UUID fallenId)`.
  - The disguise check is a hook: `static BiPredicate<ServerPlayer, String> Recruiting.disguisedAs = (p, f) -> false`, which Task 9 replaces.
  - `static void Recruiting.dismissSquad(ServerPlayer, int squadId)`.
  - Dismissing drops the unit's six equipment items at its feet, then discards it and removes it from its squad.
  - Config: `recruitRange=6`, `spawnRadius=4`, `maxSquads=9`, `maxFallen=30`, `reviveShare=0.5`.

- [ ] **Step 1: Write the failing GameTests in `RecruitTests`**
  - `hireNeedsCoins`: a Gondor player at +150 Gondor, with no coins, at a `gondor.leader` → `NOT_ENOUGH_COINS`, no new NPC, inventory unchanged.
  - `hireNeedsTier`: +150 and a SOLDIER-rank type → `TIER_TOO_LOW`.
  - `hireNeedsPoints`: +150 (cap 4) with four militia hired → fifth is `CAP_FULL`.
  - `hireOk`: +150, 1 gold, a MILITIA type → `OK`. The NPC is within 4 blocks of the recruiter and has `UnitData` (owner = player, level 1, price 30, rank MILITIA). The inventory holds 7 silver and no gold. One squad with that unit, order FOLLOW. `isPersistenceRequired()` is true.
  - `hostileRecruiter`: a Gondor player at a `mordor.captain` with `/reputation set mordor 500` → `HOSTILE`.
  - `deathKeepsFallen`: give the hired unit an iron helmet and a sword, kill it, and check that:
    - it dropped no items (no `ItemEntity` nearby);
    - the fallen record holds both items and level 1;
    - the unit is gone from its squad.
  - `reviveHalfPrice`: then revive at the recruiter with 15 copper → `OK`. The new unit wears the helmet and holds the sword, its level matches, the fallen record is removed and 0 copper is left.
  - `unitKillCreditsOwner`: a unit kills a Moria NPC (set target and `hurt` it with `damageSources().mobAttack(unit)` for lethal damage) → the owner's Moria score is -1 (half of -2).

- [ ] **Step 2: Run them and see them fail.** Run `./gradlew runGameTestServer -PmeJar=$ME`; expected compile failure.

- [ ] **Step 3: Implement.**
  - **Hire validation, in this order**, before anything is paid:
    1. `isRecruiter`, else `NOT_RECRUITER`.
    2. Distance at most `recruitRange`, else `TOO_FAR`.
    3. The recruiter's faction is not hostile to the player's chosen faction (`Faction.isHostileToward`), and the player's tier is not HOSTILE, else `HOSTILE`.
    4. Not disguised as that faction, else `DISGUISED`.
    5. The type is in the roster, else `UNKNOWN_TYPE`.
    6. Tier at least `required`, else `TIER_TOO_LOW`.
    7. `pointsUsed + points` at most `armyCap`, else `CAP_FULL`.
    8. Squad: either an existing squad of the same faction, or a new squad while there are fewer than `maxSquads`, else `NO_SQUAD_SLOT`.
    9. `Coins.pay` succeeds on the counted inventory coins, else `NOT_ENOUGH_COINS`.
    10. A free standing spot within `spawnRadius` (a solid block below, two air blocks above), else `NO_SPACE`.
  - **Spawning** mirrors `BenchCommands`' spawn (`NpcEntityBuilder(level,pos).withNpcType(id).build()`, then `addFreshEntity`), then sets `UnitData`, `setPersistenceRequired()` and the squad entry. Coins come out of inventory stacks; change is added with `Inventory.placeItemBackInInventory`.
  - **Revive** uses the same checks; its price is `floor(hirePrice * reviveShare)` and it also needs free points. It sets all six equipment slots from the record (drop chance 0), plus the name, level and xp. It rejoins its squad if that squad still exists with the same faction, otherwise it starts a new one.
  - **`UnitEvents`**:
    - `LivingDeathEvent` on a unit adds a `Fallen` record (oldest dropped beyond `maxFallen`) and removes the unit from its squad.
    - `LivingDropsEvent` and `LivingExperienceDropEvent` are cancelled for units.
    - `Reputation`'s death hook credits `Units.data(killer).owner()` with `byUnit = true`.

- [ ] **Step 4: Run them.** Expected all pass.
- [ ] **Step 5: Commit**: `feat(addon): recruit, revive and dismiss units`.

### Task 5: Persistence and offline owners

**Files:**
- Modify: `data/ArmyStore.java`, `unit/UnitEvents.java`
- Test: `army/test/PersistenceTests.java`

**Interfaces:**
- Consumes: Task 4's types.
- Produces: `static CompoundTag ArmyStore.saveForTest(HolderLookup.Provider)` and `static ArmyStore loadForTest(CompoundTag, HolderLookup.Provider)` (thin wrappers over `save` and the load factory).

- [ ] **Step 1: Write the failing GameTests**
  - `storeRoundTrip`: fill a `PlayerArmy` with reputation, two squads (one HOLD with a `holdPos` and `holdYaw`), a fallen record with an enchanted sword, and a disguise cooldown. Save, load, and check every field is equal (compare ItemStacks with `ItemStack.matches`).
  - `unitDataRoundTrip`: a unit's `UnitData` survives `saveWithoutId` followed by `load` into a fresh `NpcEntity`.
- [ ] **Step 2: Run them and see them fail.** Expected FAIL until the codecs cover every field.
- [ ] **Step 3: Implement.** Fix whatever fields the codecs miss. `Fallen.equipment` uses `ItemStack.OPTIONAL_CODEC` through the registry-aware `RegistryOps`.
- [ ] **Step 4: Run them.** Expected PASS.
- [ ] **Step 5: Commit**: `test(addon): army data survives save and reload`.

### Task 6: Formations core (slots and bonuses)

**Files:**
- Create: `army/core/Formation.java`, `army/core/FormationSlots.java`, `army/core/Levels.java`
- Test: `addon/src/test/java/.../army/core/FormationSlotsTest.java`, `FormationTest.java`, `LevelsTest.java`

**Interfaces:**
- Produces:
  - `enum Formation { LINE, SHIELD_WALL, SPEAR_LINE, COLUMN, WEDGE, LOOSE }`, each carrying:

    | Formation | melee | frontTaken | arrowTaken | explosionTaken | taken | speed | canCharge | rankWidth | spacing | stance |
    | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
    | LINE | 1.10 | 1 | 1 | 1 | 1 | 1 | yes | 10 | 1.5 | `guard` |
    | SHIELD_WALL | 1 | 0.60 | 0.40 | 1 | 1 | 0.70 | no | 8 | 1.0 | `shield_brace` |
    | SPEAR_LINE | 1 | 1 | 1 | 1 | 1 | 0.80 | no | 8 | 1.0 | `spear_brace` |
    | COLUMN | 1 | 1 | 1 | 1 | 1.15 | 1.20 | yes | 2 | 1.5 | `march` |
    | WEDGE | 1 | 1 | 1 | 1 | 1.20 | 1 | yes | (triangle) | 1.5 | `guard` |
    | LOOSE | 0.85 | 1 | 0.50 | 0.50 | 1 | 1 | yes | 5 | 3.0 | none |

    Shield wall's front and arrow reductions are scaled by shield weight. `static double shieldScale(String weight)` returns `light` 0.5, `medium` 0.75, `heavy` 1.0. `double reduced(double baseMul, double scale)` returns `1 - (1 - baseMul) * scale`. Charge bonus: `chargeFirstHit` is 1.30 in every formation and 1.60 in WEDGE.
  - `enum Gear { MELEE, SHIELD, SPEAR, RANGED }`. `boolean Formation.usableBy(Set<Gear>)`: SHIELD_WALL needs SHIELD, SPEAR_LINE needs SPEAR, WEDGE needs MELEE, the others need nothing.
  - `record Slot(double x, double z)`, local coordinates: x is to the right, z is behind the anchor.
  - `static List<Slot> FormationSlots.local(Formation f, int count)`.
  - `static double[] FormationSlots.toWorld(Slot s, double ax, double az, float yawDeg)`, using Minecraft yaw: forward `(-sin, cos)`, right `(-cos, -sin)`, and world = anchor + right·x − forward·z.
  - `static double FormationSlots.depth(Formation f, int count)`.
  - `static List<Integer> FormationSlots.order(List<Set<Gear>> units, Formation f)`: indices with capable units first, in input order.
  - `Levels`: `static int level(int xp, int[] thresholds)` (thresholds `[0,5,15,30,50]`, giving level 1..5) and `static double bonus(int level, double perLevel)` = `1 + (level-1)*perLevel`.

- [ ] **Step 1: Write the failing tests**

```java
@Test void lineCentredRanks() { var s = FormationSlots.local(Formation.LINE, 12);
    assertEquals(new Slot(-6.75, 0), s.get(0)); assertEquals(new Slot(6.75, 0), s.get(9));      // 10 wide, spacing 1.5
    assertEquals(new Slot(-0.75, 1.5), s.get(10)); assertEquals(new Slot(0.75, 1.5), s.get(11)); }
@Test void wedgeTriangle() { var s = FormationSlots.local(Formation.WEDGE, 6);
    assertEquals(new Slot(0, 0), s.get(0)); assertEquals(new Slot(-0.75, 1.5), s.get(1)); assertEquals(new Slot(1.5, 3.0), s.get(5)); }
@Test void columnTwoWide() { assertEquals(new Slot(0.75, 1.5), FormationSlots.local(Formation.COLUMN, 4).get(3)); }
@Test void rotation() { assertArrayEquals(new double[]{-1, 0}, FormationSlots.toWorld(new Slot(1, 0), 0, 0, 0f), 1e-9);   // yaw 0 faces +Z, right is −X
                        assertArrayEquals(new double[]{0, -2}, FormationSlots.toWorld(new Slot(0, 2), 0, 0, 0f), 1e-9); }
@Test void depth() { assertEquals(3.0, FormationSlots.depth(Formation.LINE, 12)); }   // 2 ranks → (ranks)*spacing
@Test void mixedSquadCapableFirst() {
    assertEquals(List.of(1, 3, 0, 2), FormationSlots.order(List.of(Set.of(Gear.MELEE), Set.of(Gear.SHIELD, Gear.MELEE), Set.of(), Set.of(Gear.SHIELD)), Formation.SHIELD_WALL)); }
@Test void shieldScale() { assertEquals(0.80, Formation.reduced(0.60, Formation.shieldScale("light")), 1e-9); }
@Test void levels() { assertEquals(1, Levels.level(4, new int[]{0,5,15,30,50})); assertEquals(5, Levels.level(999, new int[]{0,5,15,30,50}));
                      assertEquals(1.4, Levels.bonus(5, 0.10), 1e-9); }
```

- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement.**
  - Ranks fill front to back, and each rank is centred.
  - WEDGE row r holds r+1 slots.
  - `depth` is the number of ranks × spacing.
- [ ] **Step 4: Run them.** Expected PASS.
- [ ] **Step 5: Commit**: `feat(addon): formation slots, bonuses and unit levels`.

### Task 7: Unit AI: orders, targeting and travel

**Files:**
- Create in `army/`:
  - `ai/UnitGoals.java`, `ai/FollowFormationGoal.java`, `ai/HoldFormationGoal.java`
  - `ai/UnitTargetGoal.java`, `ai/TargetFilter.java`
  - `squad/Squads.java`, `unit/UnitTravel.java`
- Modify: `data/Squad.java` (typed `Formation`), `hire/Recruiting.java`
- Test: `army/test/OrderTests.java`

**Interfaces:**
- Consumes: `FormationSlots`, `Formation`, `Gear` (Task 6); `Units`, `Squad`, `ArmyStore` (Tasks 2 and 4).
- Produces:
  - `Squads`:
    - `static void order(ServerPlayer, int squadId /* -1 = all */, Order, @Nullable BlockPos target, float yaw, @Nullable Entity targetEntity)`;
    - `static void formation(ServerPlayer, int squadId, Formation)`, which refuses formations no unit can use;
    - `static Set<Gear> gearOf(Mob)`: SHIELD for an off-hand `ShieldItem`; SPEAR for a main hand in `#middle-earth:spears`; RANGED for `ProjectileWeaponItem`; otherwise MELEE when the main hand is not empty;
    - `static @Nullable double[] slotFor(NpcEntity unit)`: its world slot, from its owner's position and yaw when following (anchor 3 blocks behind the owner, plus the depth of earlier following squads plus 2), or from `holdPos` and `holdYaw` when holding.
  - `UnitTravel`:
    - `static void bringFollowers(ServerPlayer, ServerLevel fromLevel, Vec3 from)`, the API the node-map project calls after fast travel;
    - hooked to `EntityTravelToDimensionEvent` (remembers the following units within `travelRange` of the player) and to `PlayerEvent.PlayerChangedDimensionEvent` and `EntityTeleportEvent` (moves them).
  - `TargetFilter`: a `LivingChangeTargetEvent` handler, plus `static boolean allowed(Mob attacker, LivingEntity target)`.
  - Config: `followFight=8`, `holdFight=6`, `chargeRadius=16`, `holdRange=64`, `travelRange=32`, `lostRange=32`, `recentAttackTicks=200`.

- [ ] **Step 1: Write the failing GameTests in `OrderTests`**
  - `neverTargetsOwner`: a hired unit's `setTarget(owner)` is cancelled; so is `setTarget(otherUnitOfSameOwner)`. After the owner hits their own unit (`unit.hurt(owner damage source)`), the unit's target stays null.
  - `noPvpInitiation`: the unit will not target a second player B. After B hurts the owner, it does (within `recentAttackTicks`).
  - `followMovesToSlot`: the owner stands 10 blocks away; after 100 ticks the unit is within 2 blocks of `Squads.slotFor(unit)`.
  - `holdStays`: order HOLD at a block 6 away; after 100 ticks the unit is within 1.5 blocks of its slot. Move the owner 20 blocks away; 100 ticks later the unit is still there.
  - `chargeThenRevert`: HOLD, then CHARGE at a hostile Mordor NPC 8 blocks away → the unit's target is that NPC. Kill the NPC; within 40 ticks the squad order is HOLD again.
  - `ownerOfflineStandsStill`: remove the owner (log out); after 100 ticks the unit has moved less than 1 block.
  - `lostUnitTeleports`: put a following unit 40 blocks away behind a wall → within 40 ticks it is within 3 blocks of its slot.
  - `mixedShieldWall`: two shield units and one bare-handed unit in SHIELD_WALL → the bare-handed unit's slot is in rank 2 (z > spacing/2).

- [ ] **Step 2: Run them and see them fail.**

- [ ] **Step 3: Implement.**
  - **`UnitGoals.install(NpcEntity)`** runs on `EntityJoinLevelEvent` for units:
    - goal priority 2: `FollowFormationGoal` and `HoldFormationGoal`, flags MOVE and LOOK;
    - target priority 0: `UnitTargetGoal`.
  - **Movement goals:**
    - They stand down while the unit has a live target within the fight range of its anchor (`followFight` from the owner or the unit, `holdFight` from the slot).
    - They drop a target beyond those ranges.
    - With no online owner, `FollowFormationGoal` keeps the MOVE flag and stops navigation.
    - Teleport to the slot when farther than `lostRange` and `!owner.hasLineOfSight(unit)`.
    - A blocked or unsafe slot (inside a block, a drop over 3 blocks, lava) uses the nearest safe block within 3 blocks.
  - **`UnitTargetGoal`** picks, in this order:
    1. the charge target;
    2. whatever attacked the owner or the unit within `recentAttackTicks`;
    3. the owner's last melee target (`getLastHurtMob`);
    4. the nearest `NpcEntity` whose faction is hostile to the owner's chosen faction, or a `Enemy` monster, within range.
  - **`TargetFilter.allowed`**:
    - false for the unit's owner, or another unit of the same owner;
    - false for another player unless that player attacked the owner within `recentAttackTicks`;
    - false for another player's unit unless that unit or its owner attacked this owner within the window;
    - Task 9 adds the disguise rule.
  - **Charge** stores `beforeCharge`, and reverts once no enemy is within `chargeRadius` of the unit.

- [ ] **Step 4: Run them.** Expected all pass.
- [ ] **Step 5: Commit**: `feat(addon): squad orders, unit targeting and travel`.

### Task 8: Formation combat, charge, banner aura and levels

**Files:**
- Create: `army/squad/FormationCombat.java`, `army/squad/BannerAura.java`, `army/unit/UnitRuntime.java`
- Modify: `army/unit/UnitEvents.java` (xp), `army/squad/Squads.java` (speed modifier on formation change)
- Test: `army/test/CombatTests.java`

**Interfaces:**
- Consumes: `Formation`, `Levels` (Task 6); `Squads.gearOf`, `slotFor` (Task 7).
- Produces:
  - `UnitRuntime`: transient, `WeakHashMap<Mob, UnitRuntime>`. Fields: `double runSinceOrder`, `boolean firstHitDone`, `boolean charging`, `long auraUntil`. Method: `static UnitRuntime of(Mob)`.
  - Attribute modifiers:
    - `lotr_revival:formation_speed` on MOVEMENT_SPEED (`formation.speed - 1`, plus 0.30 while charging, ADD_MULTIPLIED_TOTAL);
    - `lotr_revival:level` on MAX_HEALTH and ATTACK_DAMAGE (`Levels.bonus - 1`, ADD_MULTIPLIED_TOTAL).
  - `static double FormationCombat.multiplier(LivingEntity target, DamageSource src)`, testable. It is applied in `LivingIncomingDamageEvent`.
  - Config: `chargeMinRun=4.0`, `chargeKnockback=1.0`, `frontAngle=60`, `spearChargeSpeed=0.2`, `spearBonus=1.5`, `bannerRange=8`, `bannerDamage=1.10`, `bannerTaken=0.90`, `xpThresholds=[0,5,15,30,50]`, `xpPerKill=1`, `xpPerLeaderKill=5`, `perLevel=0.10`.

- [ ] **Step 1: Write the failing GameTests in `CombatTests`**
  - `shieldWallFront`: a unit with a heavy shield in SHIELD_WALL, hit from the front by a zombie mob attack of 10 → takes 6.0. From behind → 10.0. An arrow from the front → 4.0. With a light shield, an arrow → 7.0.
  - `lineMelee`: a LINE unit hits a dummy for its base damage d → the dummy takes `1.10·d`.
  - `chargeFirstHitOnlyAfterRun`: a CHARGE order, with the target 6 blocks away. The first hit is `1.30·d` with knockback; the second hit is `d`. With the target 2 blocks away (a run under 4), the first hit is `d`. In WEDGE, the first hit is `1.60·d`, and the unit takes 1.20× damage.
  - `spearLineVsCharger`: a SPEAR_LINE unit hits a target moving toward it at 0.3 blocks per tick → `1.5·d` (×1.0 when the target stands still). A charging enemy unit hit by it loses its charge bonus (its `firstHitDone` becomes true).
  - `bannerAura`: a squad mate holding a `HeldBannerItem` within 8 blocks → the unit deals `1.10×` and takes `0.90×`. At 10 blocks it gets neither.
  - `levelUp`: 5 kills → level 2. Max health is ×1.10 and attack damage ×1.10.

- [ ] **Step 2: Run them and see them fail.**

- [ ] **Step 3: Implement.**
  - **Multipliers, applied in this order:**
    1. Target in formation: `taken`, then `frontTaken` or `arrowTaken` scaled by shield weight when SHIELD_WALL. "Front" means the attacker or projectile origin is within `frontAngle` degrees of the unit's facing. Arrows are `DamageTypeTags.IS_PROJECTILE`; explosions are `IS_EXPLOSION`.
    2. The target's banner aura: `bannerTaken`.
    3. Attacker unit, direct melee only: the formation's `melee`; the charge first hit (`chargeFirstHit`, when `runSinceOrder >= chargeMinRun && !firstHitDone`, which then sets `firstHitDone`, with knockback `chargeKnockback`, ×2 when the unit is a passenger); `spearBonus` when the attacker is in SPEAR_LINE and the target's velocity toward the attacker exceeds `spearChargeSpeed`; and the attacker's banner aura (`bannerDamage`).
  - **Shield weight** comes from `CustomShieldItem.type` (`LIGHT_SHIELD`, `MEDIUM_SHIELD`, `HEAVY_SHIELD` map to `light`, `medium`, `heavy`). A vanilla `ShieldItem` counts as `medium`.
  - **`BannerAura`** ticks every 20 ticks per squad and sets `auraUntil = now + 25`. It does not stack.
  - **XP** comes from `LivingDeathEvent` with a unit killer; level changes refresh the level modifiers.

- [ ] **Step 4: Run them.** Expected all pass.
- [ ] **Step 5: Commit**: `feat(addon): formation bonuses, charge, spear line, banner aura and unit levels`.

### Task 9: Disguise

**Files:**
- Create: `army/disguise/ArmourFactions.java`, `army/disguise/Disguise.java`
- Modify: `army/ai/TargetFilter.java`, `army/hire/Recruiting.java` (sets `disguisedAs`)
- Test: `army/test/DisguiseTests.java`

**Interfaces:**
- Consumes:
  - `NpcTypeAccessor.lotrRevival$gearDatas()` (a `WeightedPool<WeightedGearData>` with public `elements`), `WeightedGearData.getNbt()`, `MeRelations`, `MeBridge` (Task 2);
  - `ArmyStore.disguiseCooldownUntil`;
  - `Units` (Task 4).
- Produces:
  - `ArmourFactions`: `static Set<String> factionsOf(Item)`, built on server start and on datapack reload. For each `NpcType` in `DynamicRegistriesME.NPC_TYPE`, walk every string in each gear entry's NBT; strings that parse as an item id whose item is an `ArmorItem` map to `type.getFactionIdentifier()`.
  - `Disguise`:
    - `static Optional<String> activeFaction(ServerPlayer)`;
    - `static Optional<String> wouldDisguiseAs(ServerPlayer)`: the factions shared by all four worn armour pieces that are hostile to the player's chosen faction, first in id order;
    - `static void reveal(ServerPlayer, String faction)`.
  - Config: `disguiseUnitRange=32`, `leaderSeeRange=6`, `leaderSeeTicks=60`, `revealAggroRange=32`, `disguiseCooldownTicks=6000`.

- [ ] **Step 1: Write the failing GameTests in `DisguiseTests`**
  - `armourMapped`: some Mordor NPC type's chest piece maps to `middle-earth:mordor` (pick the item from that type's gear NBT in the test).
  - `fullSetDisguises`: a Gondor player wearing four Mordor-mapped pieces → `activeFaction` is mordor, and a Mordor soldier's `setTarget(player)` is cancelled. With three pieces → not disguised.
  - `ownFactionNoDisguise`: a Mordor player wearing Mordor armour → not disguised (not hostile).
  - `unitsBreakIt`: a hired unit within 32 blocks → not disguised.
  - `attackBreaksIt`: hurting a Mordor NPC → revealed; Mordor NPCs within 32 blocks now target the player; `wouldDisguiseAs` stays empty for 6000 ticks (cooldown).
  - `leaderSeesThrough`: a `mordor.captain` within 5 blocks for 60 ticks → revealed. At 8 blocks → still disguised.
  - `subFactionCounts`: for a player disguised as `middle-earth:longbeards`, a `longbeards.erebor` NPC is also blocked (the target's faction, or its parent, equals the disguise faction).

- [ ] **Step 2: Run them and see them fail.**

- [ ] **Step 3: Implement.**
  - **`Disguise` ticks every 10 ticks per player.** Disguise is active while all of these hold:
    - `wouldDisguiseAs` is present;
    - the faction has no cooldown;
    - no unit of the player is within `disguiseUnitRange`.
  - **Leader exposure:** a counter per player and faction adds 10 while any leader-rank NPC of that faction is within `leaderSeeRange`, and resets otherwise. At `leaderSeeTicks` the player is revealed.
  - **`reveal`** clears the disguise, sets the cooldown and calls `setTarget(player)` on that faction's NPCs within `revealAggroRange`.
  - **Attacking** (`LivingIncomingDamageEvent` with the player as source, against that faction's NPC) reveals.
  - **Taking off a piece** ends the disguise on the next tick (no cooldown).
  - **`TargetFilter`** cancels an `NpcEntity` of faction F (or a sub-faction whose parent is F) targeting a player disguised as F, unless that NPC was hurt by the player.

- [ ] **Step 4: Run them.** Expected all pass.
- [ ] **Step 5: Commit**: `feat(addon): disguise in a full enemy armour set`.

### Task 10: Network payloads and server handlers

**Files:**
- Create: `army/net/ArmyPayloads.java` (records and registration), `army/net/ServerHandlers.java`, `army/net/Sync.java`
- Modify: `LotrRevival.java` (`RegisterPayloadHandlersEvent`)
- Test: `army/test/PacketTests.java`

**Interfaces:**
- Consumes: `Recruiting`, `Squads`, `Recruiters`, `Disguise`, `Reputation`, `Units`.
- Produces payloads (each with a `StreamCodec` and `TYPE = LotrRevival.id(...)`):
  - C2S:
    - `OrderC2S(int squad, Order order, Optional<BlockPos> pos, float yaw, int entityId /* -1 none */)`
    - `FormationC2S(int squad, Formation f)`
    - `DismissC2S(int entityId)`
    - `DismissSquadC2S(int squad)`
    - `HireC2S(int recruiterId, String npcType, int squad)`
    - `ReviveC2S(int recruiterId, UUID fallenId)`
    - `DiscardFallenC2S(UUID fallenId)`
    - `RenameC2S(int entityId, String name /* ≤ 32 chars */)`
    - `SetUnitSquadC2S(int entityId, int squad)`
  - S2C:
    - `ReputationS2C(Map<String,Integer>)`
    - `SquadsS2C(List<SquadSummary>)`, with `record SquadSummary(int id, String faction, Order order, Formation formation, int alive, int total, Set<Formation> usable)`
    - `RecruitS2C(int recruiterId, String faction, int rep, int pointsUsed, int cap, List<RosterEntry>, List<FallenSummary>)`
    - `HireResultS2C(HireResult)`
    - `UnitInfoS2C(int entityId, UUID owner, int level, String stance)`
    - `DisguiseS2C(Optional<String> faction)`
  - `Sync.dirty(UUID owner)` queues `ReputationS2C` and `SquadsS2C` for the end of the server tick. `UnitInfoS2C` is sent on `PlayerEvent.StartTracking` and when level or formation changes.
  - Opening the Recruit menu: `PlayerInteractEvent.EntityInteract` on a recruiter that is not a unit (main hand, not sneaking) sends `RecruitS2C` and cancels. On one's own unit it opens the Unit menu (Task 12).

- [ ] **Step 1: Write the failing GameTests in `PacketTests`**: call `ServerHandlers` directly with a mock player.
  - `hireFromFarRefused`: 10 blocks away → `TOO_FAR`, nothing paid.
  - `orderOthersSquadIgnored`: player A's `OrderC2S` cannot change player B's squads (squad ids are per player, so A's squad 0 is A's own).
  - `dismissOthersUnitRefused`: `DismissC2S` naming B's unit leaves it hired.
  - `renameClamped`: a 100-character name is stored as 32 characters.
  - `forgedPriceIgnored`: `HireC2S` carries no price, so the server prices it from the roster (assert the roster price is charged).
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement.** Handlers run on the server thread (`context.enqueueWork`) and look up entities by id in the player's level.
- [ ] **Step 4: Run them.** Expected all pass.
- [ ] **Step 5: Commit**: `feat(addon): army network payloads`.

### Task 11: Client: controls, command wheel, HUD, Reputation and Recruit screens

**Files:**
- Create in `client/army/`:
  - `ClientArmy.java` (received state)
  - `ArmyKeys.java`, `CommandWheelScreen.java`, `ArmyHud.java`
  - `ReputationScreen.java`, `RecruitScreen.java`, `UnitStars.java`
- Modify: `client/LotrRevivalClient.java`
- Create: `addon/src/main/resources/assets/lotr_revival/lang/en_us.json` (our own strings)

**Interfaces:**
- Consumes: Task 10's payloads.
- Produces:
  - Key mappings (category `key.categories.lotr_revival`):
    - `command` (R, hold);
    - `reputation` (unbound; the screen is also reachable with `/reputation gui`);
    - `follow`, `hold` and `charge` quick keys (unbound).
  - `CommandWheelScreen` opens while `command` is held and acts on release:
    - Mouse direction picks a segment: Follow, Hold here, Charge, Formation, Dismiss.
    - Formation opens a second ring of the selected squad's `usable` formations.
    - Dismiss asks "Dismiss squad N?" with yes/no, then sends `DismissSquadC2S`.
    - Keys 1-9 select a squad and 0 selects all.
    - Hold and Charge raycast from the camera on release: blocks up to `holdRange` 64, entities up to 16. They send `OrderC2S` with the hit position, the player's yaw and the entity id.
  - `ArmyHud` (GUI layer, top left, hidden with F1): "Squad N · Order · Formation · alive/total", and below it while disguised, the helmet item icon plus "Disguised: <faction name>".
  - `UnitStars`: `RenderNameTagEvent` appends `" " + "★".repeat(level - 1)` for units with level 2 or more.
  - `RecruitScreen`:
    - one roster row each: name, rank, price shown as gold/silver/copper, points, required tier, plus a Hire button disabled with a tooltip reason;
    - a squad selector (new, or an existing squad of this faction);
    - a "Fallen" list with Revive and Discard buttons (a fallen unit whose NPC type no longer exists shows "unavailable", with Revive disabled);
    - `HireResultS2C` shows its reason as a toast.
  - `ReputationScreen`: every faction with score, tier and a progress bar to the next tier, sorted by score.

- [ ] **Step 1: Build.** Run `cd addon && ./gradlew build -PmeJar=$ME`; expected BUILD SUCCESSFUL. Client UI has no automated test; Task 13's playtest covers it.
- [ ] **Step 2: Commit**: `feat(addon): squad controls, HUD and recruit screens`.

### Task 12: Unit menu (equipment) and formation stance clips

**Files:**
- Create:
  - `army/unit/UnitMenu.java` and `army/unit/UnitEquipmentContainer.java` (common; MenuType registered in `ArmyRegistries`)
  - `client/army/UnitScreen.java`
- Modify:
  - `client/anim/NpcAnimator.java` (stance selection)
  - `tools/anim-viewer/bake_vanilla.js` (slots)
- Create: `addon/src/main/resources/assets/lotr_revival/clips/<race>/{guard,shield_brace,march}.json` (generated)
- Test: `army/test/UnitMenuTests.java`

**Interfaces:**
- Consumes: `UnitInfoS2C` and `ClientArmy.stance(entityId)`; `RenameC2S`, `SetUnitSquadC2S`, `DismissC2S`.
- Produces:
  - `UnitMenu(int id, Inventory inv, NpcEntity unit)`: 6 unit slots (HEAD, CHEST, LEGS, FEET with `ArmorItem` slot checks, then MAINHAND and OFFHAND), plus the player inventory. `stillValid` requires the owner, a living unit and distance at most 8. Its client constructor reads the entity id from the extra data.
  - `UnitScreen`: equipment slots, then name (edit box sends `RenameC2S`), level stars, health and attack, a squad dropdown (`SetUnitSquadC2S`), and Dismiss (confirm, then `DismissC2S`).
  - Bake slots in `bake_vanilla.js` `SLOTS`: `guard: 'hold.sword'`, `shield_brace: 'defend.brace.shield'`, `march: 'move.march'`, all three in `LOOPS`.
  - `NpcAnimator`: when `ClientArmy.stance(entity)` is non-empty, `idle` plays the stance clip (`spear_brace` plays `guard` with the right arm overridden to xRot −1.2 rad, yRot 0, zRot 0, after the clip is applied). When `stance == "march"`, `walk` plays `march`. If a clip is missing for that race, fall back to `idle` or `walk`.

- [ ] **Step 1: Write the failing GameTests**
  - `menuMovesItems`: give a unit a sword through the menu's slot 4 (`clicked` from the player's inventory) → `unit.getMainHandItem()` is the sword, and it's gone from the player's inventory.
  - `menuRejectsNonArmourInHead`: the HEAD slot refuses a sword.
  - `menuClosesForStranger`: `stillValid` is false for another player.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement the menu and screen.** The interaction hook from Task 10 opens it with `player.openMenu(provider, buf -> buf.writeVarInt(unit.getId()))`.
- [ ] **Step 4: Run them.** Expected PASS.
- [ ] **Step 5: Bake the clips.** In `tools/anim-viewer`, run `node bake_vanilla.js`. Expected: new `guard`, `shield_brace` and `march` files for every race whose library has those clips, and QA prints no failures for them.
- [ ] **Step 6: Build and commit.** Run `./gradlew build -PmeJar=$ME`, then commit: `feat(addon): unit equipment menu and formation stance clips`.

### Task 13: Hand-off for playtest

**Files:**
- Modify: `specs/addon/hiring-reputation.md` (Tickets section: list Tasks 1-12 with their commits)

- [ ] **Step 1: Run every automated test.** Run `cd addon && ./gradlew test runGameTestServer build -PmeJar=$ME`; expected all JUnit tests and GameTests pass, BUILD SUCCESSFUL.
- [ ] **Step 2: Push** `core/anim-viewer`, and fast-forward or cherry-pick the commits onto `main` so both have identical trees.
- [ ] **Step 3: Give the maintainer the playtest checklist.** It is the spec's Playtest line, expanded:
  - hire from Gondor, Rohan and Mordor;
  - each order and each formation, with its stance visible;
  - travel through a portal with followers;
  - die, then revive;
  - the banner bearer;
  - disguise in Mordor territory, then walk up to a captain;
  - hire a type that spawns mounted and check its mount comes along.

  Include the commands `/reputation set middle-earth:gondor 3000` and `/give @s middle-earth:gold_coin 20`.
