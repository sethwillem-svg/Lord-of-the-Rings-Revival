package io.github.sethwillemsvg.lotrrevival.world.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.api.WaypointView;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.RegionUnlocker;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelClock;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelData;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelRules;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.Waypoint;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRef;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRegion;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Waypoint registries, travel attachment, region unlocking and the pure travel rules. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaypointRulesTest {
    private static final ResourceLocation SHIRE = LOTRRevival.id("shire");

    private static ServerPlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "waypoint-test"));
    }

    private static Holder<Biome> biome(GameTestHelper helper, ResourceKey<Biome> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
    }

    @GameTest(template = "empty")
    public static void pilotRegistriesAreLoaded(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var regions = access.registryOrThrow(ModDataRegistries.<WaypointRegion>waypointRegion());
        var waypoints = access.registryOrThrow(ModDataRegistries.<Waypoint>waypoint());
        helper.assertTrue(regions.size() == 7, "Expected 7 regions, got " + regions.size());
        helper.assertTrue(waypoints.size() == 28, "Expected 28 waypoints, got " + waypoints.size());
        for (var entry : waypoints.entrySet()) {
            helper.assertTrue(
                    regions.containsKey(entry.getValue().region()),
                    "Waypoint " + entry.getKey().location() + " names unknown region " + entry.getValue().region());
        }
        Waypoint hobbiton = waypoints.get(LOTRRevival.id("hobbiton"));
        helper.assertTrue(hobbiton != null, "hobbiton missing");
        helper.assertTrue(hobbiton.u() == 1620 && hobbiton.v() == 1460, "hobbiton position");
        helper.assertTrue(hobbiton.faction().equals(java.util.Optional.of(LOTRRevival.id("hobbit"))), "hobbiton faction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void requiredTicksFollowTheFormula(GameTestHelper helper) {
        int[][] cases = {{0, 36000}, {1, 32760}, {10, 14898}, {30, 4974}};
        for (int[] c : cases) {
            int got = TravelRules.requiredTicks(180, 1800, 0.9, 0.000012, c[0], 100);
            helper.assertTrue(got == c[1], "uses " + c[0] + ": expected " + c[1] + " got " + got);
        }
        int far = TravelRules.requiredTicks(180, 1800, 0.9, 0.000012, 0, 102_000);
        helper.assertTrue(far == 44064, "102,000 blocks: expected 44064 got " + far);
        helper.assertTrue(
                TravelRules.requiredTicks(180, 1800, 0.9, 0.000012, 5000, 0)
                        == TravelRules.requiredTicks(180, 1800, 0.9, 0.000012, 1000, 0),
                "uses are capped at 1,000");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woodlandsUnlockShireAndRiverUnlocksNothing(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        helper.assertTrue(RegionUnlocker.unlockAt(p, biome(helper, MiddleEarthBiomes.RIVER)).isEmpty(), "river unlocks");
        helper.assertTrue(TravelData.get(p).regions().isEmpty(), "river must not unlock anything");

        List<ResourceLocation> first = RegionUnlocker.unlockAt(p, biome(helper, MiddleEarthBiomes.SHIRE_WOODLANDS));
        helper.assertTrue(first.equals(List.of(SHIRE)), "shire_woodlands should unlock shire, got " + first);
        helper.assertTrue(TravelData.get(p).hasRegion(SHIRE), "shire not stored");
        helper.assertTrue(
                RegionUnlocker.unlockAt(p, biome(helper, MiddleEarthBiomes.SHIRE)).isEmpty(),
                "a region unlocks once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void factionWaypointBarredBelowZero(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        var waypoints = helper.getLevel().registryAccess().registryOrThrow(ModDataRegistries.<Waypoint>waypoint());
        Waypoint hobbiton = waypoints.get(LOTRRevival.id("hobbiton"));
        Waypoint bridge = waypoints.get(LOTRRevival.id("brandywine_bridge"));

        helper.assertTrue(
                TravelRules.status(hobbiton, p).state() == WaypointView.State.LOCKED, "locked before the region");
        TravelData.set(p, TravelData.get(p).withRegion(SHIRE));

        Alignment.set(p, Factions.HOBBIT, -1.0f, AlignmentSource.command());
        TravelRules.Status barred = TravelRules.status(hobbiton, p);
        helper.assertTrue(barred.state() == WaypointView.State.BARRED, "hobbit -1 should be barred");
        helper.assertTrue(barred.barredBy().equals(java.util.Optional.of(Factions.HOBBIT)), "barring faction");
        helper.assertTrue(
                TravelRules.status(bridge, p).state() == WaypointView.State.USABLE,
                "an unaligned waypoint is usable at any alignment");

        Alignment.set(p, Factions.HOBBIT, 0.0f, AlignmentSource.command());
        helper.assertTrue(TravelRules.status(hobbiton, p).state() == WaypointView.State.USABLE, "hobbit 0 should be usable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hiddenWaypointsAreUnlistedUntilTheirRegionOpens(GameTestHelper helper) {
        Waypoint secret = new Waypoint(
                net.minecraft.network.chat.Component.literal("Secret"),
                net.minecraft.network.chat.Component.literal("A hidden place"),
                10,
                10,
                SHIRE,
                java.util.Optional.empty(),
                true);
        helper.assertTrue(!TravelRules.status(secret, Set.of(), f -> 0).listed(), "hidden and locked must be unlisted");
        TravelRules.Status open = TravelRules.status(secret, Set.of(SHIRE), f -> 0);
        helper.assertTrue(open.listed() && open.state() == WaypointView.State.USABLE, "hidden but unlocked is listed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void customLimitGrowsWithAchievements(GameTestHelper helper) {
        helper.assertTrue(TravelRules.customLimit(0, 5, 5) == 5, "base limit");
        helper.assertTrue(TravelRules.customLimit(4, 5, 5) == 5, "4 achievements");
        helper.assertTrue(TravelRules.customLimit(9, 5, 5) == 6, "9 achievements give 6");

        TravelData data = TravelData.EMPTY;
        int limit = TravelRules.customLimit(9, 5, 5);
        for (int i = 0; i < limit; i++) {
            helper.assertTrue(TravelRules.canCreateCustom(data.custom().size(), limit), "create " + i);
            data = data.withNewCustom("Camp " + i, new BlockPos(i, 70, i));
        }
        helper.assertTrue(!TravelRules.canCreateCustom(data.custom().size(), limit), "7th create must be refused");
        int dropped = TravelRules.customLimit(0, 5, 5);
        helper.assertTrue(!TravelRules.canCreateCustom(data.custom().size(), dropped), "creating refused over the limit");
        helper.assertTrue(data.custom().size() == 6 && data.nextCustomId() == 6, "all 6 remain, ids never reused");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void offlineCreditOnlyOnDedicatedServers(GameTestHelper helper) {
        long hour = 3_600_000L;
        long now = 10 * hour;
        helper.assertTrue(TravelRules.offlineCreditTicks(0.1, now - hour, now, true) == 7200, "10% of an hour");
        helper.assertTrue(TravelRules.offlineCreditTicks(0.1, now - hour, now, false) == 0, "single-player");
        helper.assertTrue(TravelRules.offlineCreditTicks(0.1, 0, now, true) == 0, "unknown logout");
        helper.assertTrue(TravelRules.offlineCreditTicks(0.1, now + hour, now, true) == 0, "future logout");

        ServerPlayer p = player(helper);
        TravelClock.recordLogout(p, now - hour);
        helper.assertTrue(TravelClock.applyOfflineCredit(p, true, now) == 7200, "credit applied");
        helper.assertTrue(TravelData.get(p).clock() == 7200, "clock holds the credit");
        helper.assertTrue(TravelData.get(p).lastLogout() == 0, "logout time cleared");
        helper.assertTrue(TravelClock.applyOfflineCredit(p, true, now) == 0, "no second credit");

        ServerPlayer q = player(helper);
        TravelClock.recordLogout(q, now - hour);
        helper.assertTrue(TravelClock.applyOfflineCredit(q, false, now) == 0, "nothing in single-player");
        helper.assertTrue(TravelData.get(q).clock() == 0, "single-player clock untouched");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clockSaturates(GameTestHelper helper) {
        TravelData data = TravelData.EMPTY.withClock(TravelData.MAX_CLOCK - 1).addClock(5);
        helper.assertTrue(data.clock() == TravelData.MAX_CLOCK, "clock saturates at 1,000,000");
        helper.assertTrue(TravelData.EMPTY.withClock(-5).clock() == 0, "clock never negative");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waypointRefsRoundTrip(GameTestHelper helper) {
        WaypointRef fixed = WaypointRef.tryParse("fixed:lotrrevival:bree").orElse(null);
        helper.assertTrue(fixed instanceof WaypointRef.Fixed f && f.id().equals(LOTRRevival.id("bree")), "fixed ref");
        helper.assertTrue(fixed.wire().equals("fixed:lotrrevival:bree"), "fixed wire");
        WaypointRef custom = WaypointRef.tryParse("custom:12").orElse(null);
        helper.assertTrue(custom instanceof WaypointRef.Custom c && c.id() == 12, "custom ref");
        helper.assertTrue(WaypointRef.tryParse("custom:x").isEmpty(), "bad custom id");
        helper.assertTrue(WaypointRef.tryParse("bree").isEmpty(), "missing prefix");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void travelDataSurvivesCodecAndDeath(GameTestHelper helper) {
        TravelData data = TravelData.EMPTY
                .withRegion(SHIRE)
                .withUse(LOTRRevival.id("bree"))
                .withNewCustom("Home", new BlockPos(1, 2, 3))
                .withClock(1234)
                .withLast(new WaypointRef.Fixed(LOTRRevival.id("bree")))
                .withLastLogout(99L);
        var json = TravelData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        TravelData back = TravelData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        helper.assertTrue(back.equals(data), "codec round trip changed the data");

        ServerPlayer old = player(helper);
        TravelData.set(old, data);
        ServerPlayer reborn = player(helper);
        reborn.restoreFrom(old, false);
        helper.assertTrue(TravelData.get(reborn).equals(data), "travel data must survive death");
        helper.succeed();
    }
}
