package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.HobbitEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcClass;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.spawn.NpcDespawn;
import io.github.sethwillemsvg.lotrrevival.npc.spawn.NpcSpawnData;
import io.github.sethwillemsvg.lotrrevival.npc.spawn.NpcSpawnRules;
import io.github.sethwillemsvg.lotrrevival.npc.spawn.NpcSpawner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Wild spawner math, spot checks, spawn tables and despawn rules. Nothing here needs the Middle-earth dimension. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcSpawnTest {
    private NpcSpawnTest() {}

    private static NpcSpawnRules.Spot goodSpot() {
        return new NpcSpawnRules.Spot(true, false, true, 15, 30.0 * 30.0, 100.0 * 100.0, false, true);
    }

    private static NpcSpawnRules.Spot with(
            NpcSpawnRules.Spot s, boolean liquid, int light, double playerDistance, boolean inStructure) {
        return new NpcSpawnRules.Spot(
                s.spawnableSurface(),
                liquid,
                s.skyVisible(),
                light,
                playerDistance * playerDistance,
                s.spawnDistanceSq(),
                inStructure,
                s.roomToStand());
    }

    @GameTest(template = "empty")
    public static void capAndDamping(GameTestHelper helper) {
        helper.assertTrue(NpcSpawnRules.cap(196, 1, 0.1) == 100, "one player, 196 chunks: " + NpcSpawnRules.cap(196, 1, 0.1));
        helper.assertTrue(NpcSpawnRules.cap(196, 6, 0.1) == 66, "six players: " + NpcSpawnRules.cap(196, 6, 0.1));
        helper.assertTrue(NpcSpawnRules.cap(196, 1000, 0.1) == 50, "damping floor: " + NpcSpawnRules.cap(196, 1000, 0.1));
        helper.assertTrue(NpcSpawnRules.cap(392, 1, 0.1) == 200, "two areas of chunks");
        helper.assertTrue(NpcSpawnRules.cap(0, 1, 0.1) == 0, "no chunks, no cap");
        helper.assertTrue(NpcSpawnRules.damping(1, 0.1) == 1.0, "one player is undamped");
        helper.assertTrue(NpcSpawnRules.damping(0, 0.1) == 1.0, "no players is undamped");
        helper.assertTrue(NpcSpawnRules.damping(3, 0.0) == 1.0, "d = 0 never damps");
        for (int players = 1; players < 200; players++) {
            helper.assertTrue(NpcSpawnRules.cap(196, players, 0.1) >= 50, "cap below half at " + players + " players");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spotChecks(GameTestHelper helper) {
        NpcSpawnRules.Spot ok = goodSpot();
        helper.assertTrue(NpcSpawnRules.check(ok) == NpcSpawnRules.Reject.NONE, "a good spot passes");
        helper.assertTrue(
                NpcSpawnRules.check(with(ok, false, 7, 30, false)) == NpcSpawnRules.Reject.DARK, "light 7 is rejected");
        helper.assertTrue(
                NpcSpawnRules.check(with(ok, false, 8, 30, false)) == NpcSpawnRules.Reject.NONE, "light 8 passes");
        helper.assertTrue(
                NpcSpawnRules.check(with(ok, true, 15, 30, false)) == NpcSpawnRules.Reject.LIQUID,
                "water or lava is rejected");
        helper.assertTrue(
                NpcSpawnRules.check(with(ok, false, 15, 23.9, false)) == NpcSpawnRules.Reject.NEAR_PLAYER,
                "under 24 blocks from a player is rejected");
        helper.assertTrue(
                NpcSpawnRules.check(with(ok, false, 15, 24.0, false)) == NpcSpawnRules.Reject.NONE,
                "24 blocks from a player passes");
        helper.assertTrue(
                NpcSpawnRules.check(with(ok, false, 15, 30, true)) == NpcSpawnRules.Reject.IN_STRUCTURE,
                "structure columns are skipped");
        helper.assertTrue(
                NpcSpawnRules.check(new NpcSpawnRules.Spot(true, false, true, 15, 900, 100.0, false, true))
                        == NpcSpawnRules.Reject.NEAR_WORLD_SPAWN,
                "10 blocks from world spawn is rejected");
        helper.assertTrue(
                NpcSpawnRules.check(new NpcSpawnRules.Spot(true, false, false, 15, 900, 1.0e6, false, true))
                        == NpcSpawnRules.Reject.NO_SKY,
                "no sky is rejected");
        helper.assertTrue(
                NpcSpawnRules.check(new NpcSpawnRules.Spot(false, false, true, 15, 900, 1.0e6, false, true))
                        == NpcSpawnRules.Reject.SURFACE,
                "a bad surface is rejected");
        helper.assertTrue(NpcSpawnRules.isSpawnableSurface(Blocks.GRASS_BLOCK), "grass");
        helper.assertTrue(NpcSpawnRules.isSpawnableSurface(Blocks.DIRT_PATH), "path");
        helper.assertTrue(NpcSpawnRules.isSpawnableSurface(Blocks.SAND), "sand");
        helper.assertTrue(!NpcSpawnRules.isSpawnableSurface(Blocks.STONE), "stone");
        helper.assertTrue(!NpcSpawnRules.isSpawnableSurface(Blocks.OAK_LEAVES), "leaves");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void timeHobbitAndRoadRules(GameTestHelper helper) {
        helper.assertTrue(NpcSpawnRules.timeAllowed(NpcClass.CIVILIAN, 0), "civilians at dawn");
        helper.assertTrue(NpcSpawnRules.timeAllowed(NpcClass.CIVILIAN, 11999), "civilians at the last day tick");
        helper.assertTrue(!NpcSpawnRules.timeAllowed(NpcClass.CIVILIAN, 12000), "no civilians at dusk");
        helper.assertTrue(!NpcSpawnRules.timeAllowed(NpcClass.CIVILIAN, 24000L * 3 + 18000), "no civilians at night, later day");
        helper.assertTrue(NpcSpawnRules.timeAllowed(NpcClass.GUARD, 18000), "guards any time");
        helper.assertTrue(NpcSpawnRules.hobbitGroundOk(Blocks.GRASS_BLOCK, 64, 63), "hobbit above sea level on grass");
        helper.assertTrue(!NpcSpawnRules.hobbitGroundOk(Blocks.GRASS_BLOCK, 62, 63), "hobbit below sea level");
        helper.assertTrue(!NpcSpawnRules.hobbitGroundOk(Blocks.SAND, 70, 63), "hobbit on sand");
        helper.assertTrue(NpcSpawnRules.travellerAllowed(false, false), "no road tag members: anywhere");
        helper.assertTrue(NpcSpawnRules.travellerAllowed(true, true), "near a road");
        helper.assertTrue(!NpcSpawnRules.travellerAllowed(true, false), "far from the road");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownIdSkipsOnlyThatEntry(GameTestHelper helper) {
        JsonElement json = JsonParser.parseString("""
                {"entries": [
                  {"type": "lotrrevival:hobbit", "weight": 5, "group_min": 1, "group_max": 2},
                  {"type": "lotrrevival:not_a_real_npc", "weight": 5, "group_min": 1, "group_max": 2},
                  {"type": "lotrrevival:bree_man", "weight": 3, "group_min": 1, "group_max": 3, "chance": 500,
                   "roles": ["villager", "farmer"]}
                ]}""");
        List<String> warnings = new ArrayList<>();
        List<NpcSpawnData.Entry> entries = NpcSpawnData.parseTable(LOTRRevival.id("test"), json, warnings::add);
        helper.assertTrue(entries.size() == 2, "the two valid entries load, got " + entries.size());
        helper.assertTrue(warnings.size() == 1, "exactly one warning, got " + warnings);
        helper.assertTrue(warnings.get(0).contains("not_a_real_npc"), "the warning names the id: " + warnings.get(0));
        helper.assertTrue(entries.get(0).type() == NpcEntityTypes.HOBBIT.get(), "first entry is the hobbit");
        helper.assertTrue(entries.get(0).chance() == 1, "chance defaults to 1");
        helper.assertTrue(entries.get(1).chance() == 500 && entries.get(1).roles().size() == 2, "chance and roles read");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shippedTablesLoaded(GameTestHelper helper) {
        List<NpcSpawnData.Entry> shire = NpcSpawnData.forBiome(LOTRRevival.id("shire"));
        helper.assertTrue(shire.size() == 2, "shire has two entries, got " + shire.size());
        NpcSpawnData.Entry hobbit = shire.stream().filter(e -> e.type() == NpcEntityTypes.HOBBIT.get()).findFirst().orElseThrow();
        helper.assertTrue(hobbit.weight() == 40 && hobbit.groupMin() == 1 && hobbit.groupMax() == 4, "hobbit 40, 1-4");
        NpcSpawnData.Entry bounder = shire.stream().filter(e -> e.type() == NpcEntityTypes.HOBBIT_BOUNDER.get()).findFirst().orElseThrow();
        helper.assertTrue(bounder.weight() == 1 && bounder.groupMax() == 3, "bounder 1, 1-3");
        List<NpcSpawnData.Entry> bree = NpcSpawnData.forBiome(LOTRRevival.id("bree_land"));
        helper.assertTrue(bree.size() == 3 && bree.stream().allMatch(e -> e.chance() == 500), "bree-land entries are 1 in 500");
        helper.assertTrue(NpcSpawnData.forBiome(LOTRRevival.id("no_such_biome")).isEmpty(), "unknown biome has no table");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void despawnParticipation(GameTestHelper helper) {
        helper.assertTrue(NpcDespawn.mayDespawn(true, false, false, false, false), "plain wild NPC");
        helper.assertTrue(!NpcDespawn.mayDespawn(false, false, false, false, false), "non-wild NPC");
        helper.assertTrue(!NpcDespawn.mayDespawn(true, true, false, false, false), "home-bound");
        helper.assertTrue(!NpcDespawn.mayDespawn(true, false, true, false, false), "name-tagged");
        helper.assertTrue(!NpcDespawn.mayDespawn(true, false, false, true, false), "hired");
        helper.assertTrue(!NpcDespawn.mayDespawn(true, false, false, false, true), "targeting a player");

        HobbitEntity wild = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(2, 2, 2));
        wild.setNoAi(true);
        wild.setWild(true);
        HobbitEntity bound = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(3, 2, 2));
        bound.setNoAi(true);
        bound.setWild(true);
        bound.setHome(helper.absolutePos(new BlockPos(3, 2, 2)), 16);
        HobbitEntity tagged = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(4, 2, 2));
        tagged.setNoAi(true);
        tagged.setWild(true);
        tagged.setCustomName(Component.literal("Named"));
        HobbitEntity summoned = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(5, 2, 2));
        summoned.setNoAi(true);

        helper.assertTrue(wild.removeWhenFarAway(1.0e6), "a wild NPC may be removed");
        helper.assertTrue(!bound.removeWhenFarAway(1.0e6), "a home-bound NPC is kept");
        helper.assertTrue(!tagged.removeWhenFarAway(1.0e6), "a name-tagged NPC is kept");
        helper.assertTrue(!summoned.removeWhenFarAway(1.0e6), "a summoned (non-wild) NPC is kept");

        // Vanilla's own check, end to end, when no real player is near enough to interfere.
        // GameTestHelper.spawn makes mobs persistent, which vanilla never despawns, so build these by hand.
        HobbitEntity farWild = new HobbitEntity(NpcEntityTypes.HOBBIT.get(), helper.getLevel());
        farWild.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5)));
        farWild.setWild(true);
        HobbitEntity farBound = new HobbitEntity(NpcEntityTypes.HOBBIT.get(), helper.getLevel());
        farBound.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2.0, 2.5)));
        farBound.setWild(true);
        farBound.setHome(farBound.blockPosition(), 16);
        ServerPlayer far = helper.makeMockServerPlayerInLevel();
        try {
            far.setPos(farWild.getX() + 400.0, farWild.getY(), farWild.getZ());
            if (helper.getLevel().getNearestPlayer(farWild, 128.0) == null) {
                farWild.checkDespawn();
                farBound.checkDespawn();
                helper.assertTrue(farWild.isRemoved(), "a wild NPC with no player within 128 blocks is removed");
                helper.assertTrue(!farBound.isRemoved(), "a home-bound NPC is not removed");
            }
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(far);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wildFlagPersists(GameTestHelper helper) {
        HobbitEntity original = new HobbitEntity(NpcEntityTypes.HOBBIT.get(), helper.getLevel());
        original.setWild(true);
        HobbitEntity loaded = new HobbitEntity(NpcEntityTypes.HOBBIT.get(), helper.getLevel());
        loaded.load(original.saveWithoutId(new CompoundTag()));
        helper.assertTrue(loaded.isWild(), "wild survives a save");
        HobbitEntity plain = new HobbitEntity(NpcEntityTypes.HOBBIT.get(), helper.getLevel());
        HobbitEntity loadedPlain = new HobbitEntity(NpcEntityTypes.HOBBIT.get(), helper.getLevel());
        loadedPlain.load(plain.saveWithoutId(new CompoundTag()));
        helper.assertTrue(!loadedPlain.isWild(), "non-wild stays non-wild");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnerIsInertOutsideMiddleEarth(GameTestHelper helper) {
        int before = helper.getLevel().getEntities(EntityTypeTest.forClass(NpcEntity.class), NpcEntity::isWild).size();
        NpcSpawner.onLevelTick(new LevelTickEvent.Post(() -> true, helper.getLevel()));
        int after = helper.getLevel().getEntities(EntityTypeTest.forClass(NpcEntity.class), NpcEntity::isWild).size();
        helper.assertTrue(before == after, "no wild NPC may appear outside Middle-earth");
        helper.succeed();
    }
}
