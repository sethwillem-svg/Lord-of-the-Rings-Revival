package io.github.sethwillemsvg.lotrrevival.structures.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.SiteRules;
import io.github.sethwillemsvg.lotrrevival.structures.SiteRules.Column;
import io.github.sethwillemsvg.lotrrevival.structures.SpreadMath;
import io.github.sethwillemsvg.lotrrevival.structures.WorldLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Spread maths, site rules and the world lock (specs/addon/structures.md, Acceptance tests). */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlacementTests {
    private PlacementTests() {}

    @GameTest(template = "empty")
    public static void spreadMaths(GameTestHelper h) {
        TestRun.run(h, "spreadMaths", () -> {
            long seed = 0x5EEDL;
            int spacing = 24, sep = 10, salt = 80127772;
            // reference: vanilla's linear random spread with the same numbers
            RandomSpreadStructurePlacement ref = new RandomSpreadStructurePlacement(spacing, sep, RandomSpreadType.LINEAR, salt);
            List<ChunkPos> candidates = new ArrayList<>();
            for (int cx = -3; cx <= 3; cx++) {
                for (int cz = -3; cz <= 3; cz++) {
                    int x = cx * spacing + 5, z = cz * spacing + 7;
                    ChunkPos ours = SpreadMath.candidate(seed, x, z, spacing, sep, salt);
                    ChunkPos theirs = ref.getPotentialStructureChunk(seed, x, z);
                    TestRun.check(ours.equals(theirs), "candidate " + ours + " differs from vanilla " + theirs + " at " + x + "," + z);
                    candidates.add(ours);
                }
            }
            for (ChunkPos a : candidates) {
                for (ChunkPos b : candidates) {
                    if (a.equals(b)) continue;
                    int d = Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
                    TestRun.check(d >= sep, "candidates " + a + " and " + b + " are " + d + " chunks apart (< " + sep + ")");
                }
            }
            ChunkPos c = candidates.get(0);
            TestRun.check(SpreadMath.candidateWithin(seed, c.x + 2, c.z, 3, spacing, sep, salt), "exclusion missed a start 2 chunks away");
            TestRun.check(!SpreadMath.candidateWithin(seed, c.x + 2, c.z, 1, spacing, sep, salt), "exclusion of 1 chunk caught a start 2 away");
            LotrLog.info("test", "spreadMaths: {} candidates match vanilla, separation >= {}", candidates.size(), sep);
        });
    }

    @GameTest(template = "empty")
    public static void siteRules(GameTestHelper h) {
        TestRun.run(h, "siteRules", () -> {
            Column flat = new Column(70, 70);
            TestRun.check(SiteRules.land(6, flat, new Column(74, 74), new Column(68, 68)).ok(), "6-block slope refused");
            SiteRules.Verdict steep = SiteRules.land(6, flat, new Column(77, 77));
            TestRun.check(!steep.ok() && steep.reason().equals("slope 7/6"), "7-block slope passed: " + steep);
            SiteRules.Verdict wet = SiteRules.land(6, flat, new Column(63, 60));
            TestRun.check(!wet.ok() && wet.reason().equals("water"), "water passed: " + wet);
            Column river = new Column(62, 60);
            Column dry = new Column(64, 64), dryHigh = new Column(66, 66), cliff = new Column(70, 70), wetEnd = new Column(62, 58);
            for (int fit = 0; fit < 4; fit++) {
                Column[][] ends = new Column[4][];
                for (int r = 0; r < 4; r++) ends[r] = r == fit ? new Column[]{dry, dryHigh} : new Column[]{dry, wetEnd};
                TestRun.check(SiteRules.riverCrossing(river, ends) == fit, "river crossing picked the wrong rotation for " + fit);
            }
            Column[][] none = {{dry, cliff}, {wetEnd, dry}, {dry, cliff}, {wetEnd, wetEnd}};
            TestRun.check(SiteRules.riverCrossing(river, none) == -1, "river crossing passed with no fitting rotation");
            TestRun.check(SiteRules.riverCrossing(new Column(66, 60), new Column[][]{{dry, dry}}) == -1, "6-deep river passed");
            TestRun.check(SiteRules.riverCrossing(flat, new Column[][]{{dry, dry}}) == -1, "dry centre passed as a river");
        });
    }

    @GameTest(template = "empty")
    public static void worldLock(GameTestHelper h) {
        TestRun.run(h, "worldLock", () -> {
            Map<String, WorldLock.Entry> first = Map.of("lotr_revival:gondor", new WorldLock.Entry(24, 10, 5));
            List<String> warnings = new ArrayList<>();
            Map<String, WorldLock.Entry> locked = WorldLock.reconcile(Map.of(), first, false, warnings);
            TestRun.check(locked.equals(first) && warnings.isEmpty(), "first lock did not store the config: " + locked);
            Map<String, WorldLock.Entry> changed = Map.of("lotr_revival:gondor", new WorldLock.Entry(30, 10, 5),
                    "lotr_revival:wild", new WorldLock.Entry(32, 14, 9));
            Map<String, WorldLock.Entry> kept = WorldLock.reconcile(locked, changed, false, warnings);
            TestRun.check(kept.get("lotr_revival:gondor").spacing() == 24, "the lock did not keep spacing 24");
            TestRun.check(kept.get("lotr_revival:wild").spacing() == 32, "a new set was not locked");
            TestRun.check(warnings.size() == 1 && warnings.get(0).contains("keeps lotr_revival:gondor spacing 24 (config says 30)"),
                    "expected one WARN, got " + warnings);
            warnings.forEach(w -> LotrLog.warn("structures", w));
            Map<String, WorldLock.Entry> relocked = WorldLock.reconcile(kept, changed, true, new ArrayList<>());
            TestRun.check(relocked.equals(changed), "relock did not replace the values: " + relocked);
            // SavedData round trip
            WorldLock lock = WorldLock.load(new CompoundTag(), h.getLevel().registryAccess());
            CompoundTag saved = new WorldLock().save(new CompoundTag(), h.getLevel().registryAccess());
            TestRun.check(lock.entries().isEmpty() && saved.contains("sets"), "empty lock round trip failed");
            TestRun.check(lock.requestRelock(100) == false && lock.requestRelock(200), "relock did not need two calls");
        });
    }
}
