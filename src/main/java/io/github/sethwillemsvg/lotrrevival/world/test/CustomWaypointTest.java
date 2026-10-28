package io.github.sethwillemsvg.lotrrevival.world.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.client.world.ClientTravel;
import io.github.sethwillemsvg.lotrrevival.core.api.WaypointView;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.world.network.CustomWaypointEditPayload;
import io.github.sethwillemsvg.lotrrevival.world.network.TravelSync;
import io.github.sethwillemsvg.lotrrevival.world.network.TravelSyncPayload;
import io.github.sethwillemsvg.lotrrevival.world.network.WaypointPayloads;
import io.github.sethwillemsvg.lotrrevival.world.travel.TravelChecks;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.CustomWaypoints;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelData;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelRules;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRef;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointViews;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Custom waypoints (limit, names, position rules), the travel sync and the client views built from it. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CustomWaypointTest {
    private static final ResourceLocation SHIRE = LOTRRevival.id("shire");

    private CustomWaypointTest() {}

    private static ServerPlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "custom-waypoint-test"));
        Vec3 pos = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static void withMiddleEarth(Runnable body) {
        var previous = TravelChecks.middleEarth;
        TravelChecks.middleEarth = level -> true;
        try {
            body.run();
        } finally {
            TravelChecks.middleEarth = previous;
        }
    }

    @GameTest(template = "empty")
    public static void limitIsSixAtNineAchievementsAndTheSeventhIsRefused(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        int limit = TravelRules.customLimit(9, 5, 5);
        helper.assertTrue(limit == 6, "9 achievements give a limit of 6, got " + limit);
        withMiddleEarth(() -> {
            for (int i = 0; i < 6; i++) {
                CustomWaypoints.Result result = CustomWaypoints.create(player, "Camp " + i, limit);
                helper.assertTrue(result.ok(), "create " + i + " should work: " + result.message().getString());
                helper.assertTrue(result.id().orElse(-1) == i, "ids count up from 0, got " + result.id());
            }
            CustomWaypoints.Result seventh = CustomWaypoints.create(player, "One too many", limit);
            helper.assertTrue(!seventh.ok(), "the 7th create must be refused");
            helper.assertTrue(
                    seventh.message().getString().contains("only hold 6"),
                    "refusal names the limit, got " + seventh.message().getString());
            helper.assertTrue(TravelData.get(player).custom().size() == 6, "still 6 after the refusal");

            // Achievement count dropped to 0: the limit is 5, all 6 stay and stay usable, but nothing new may be made.
            int dropped = TravelRules.customLimit(0, 5, 5);
            helper.assertTrue(
                    !CustomWaypoints.create(player, "Still no", dropped).ok(), "creating is refused over the limit");
            TravelData.set(player, TravelData.get(player).withClock(TravelData.MAX_CLOCK));
            for (int i = 0; i < 6; i++) {
                TravelChecks.Outcome outcome = TravelChecks.check(player, new WaypointRef.Custom(i));
                helper.assertTrue(
                        outcome.allowed(),
                        "custom " + i + " must stay usable, refused with " + outcome.refusal());
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void namesAreTrimmedAndLimitedToThirtyTwoCharacters(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.assertTrue(CustomWaypoints.cleanName("  Camp  ").equals(Optional.of("Camp")), "names are trimmed");
        helper.assertTrue(CustomWaypoints.cleanName("   ").isEmpty(), "a blank name is refused");
        helper.assertTrue(CustomWaypoints.cleanName("").isEmpty(), "an empty name is refused");
        helper.assertTrue(CustomWaypoints.cleanName("x".repeat(32)).isPresent(), "32 characters are fine");
        helper.assertTrue(CustomWaypoints.cleanName("x".repeat(33)).isEmpty(), "33 characters are refused");
        helper.assertTrue(
                CustomWaypoints.cleanName(" " + "x".repeat(32) + " ").isPresent(),
                "the limit applies after trimming");
        withMiddleEarth(() -> {
            CustomWaypoints.Result tooLong = CustomWaypoints.create(player, "y".repeat(33), 5);
            helper.assertTrue(!tooLong.ok(), "a 33-character name must be refused");
            helper.assertTrue(TravelData.get(player).custom().isEmpty(), "nothing was stored");
            helper.assertTrue(CustomWaypoints.create(player, "  Rivendell Road  ", 5).ok(), "padded name accepted");
            helper.assertTrue(
                    TravelData.get(player).custom().get(0).name().equals("Rivendell Road"), "the stored name is trimmed");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creatingOutsideMiddleEarthIsRefused(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        // The GameTest level is not Middle-earth, and nothing swaps the predicate here.
        CustomWaypoints.Result result = CustomWaypoints.create(player, "Nowhere", 5);
        helper.assertTrue(!result.ok(), "creating outside Middle-earth must be refused");
        helper.assertTrue(
                result.message().getString().contains("Middle-earth"),
                "the reason names Middle-earth, got " + result.message().getString());
        helper.assertTrue(TravelData.get(player).custom().isEmpty(), "nothing was stored");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void minimumYAppliesOnlyWhenConfigured(GameTestHelper helper) {
        helper.assertTrue(!CustomWaypoints.belowMinY(-64, CustomWaypoints.NO_MIN_Y), "no limit by default");
        helper.assertTrue(CustomWaypoints.belowMinY(10, 20), "10 is below 20");
        helper.assertTrue(!CustomWaypoints.belowMinY(20, 20), "20 is not below 20");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void renameAndDeleteKeepIdsUnique(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        withMiddleEarth(() -> {
            CustomWaypoints.create(player, "First", 5);
            CustomWaypoints.create(player, "Second", 5);
            helper.assertTrue(CustomWaypoints.rename(player, 1, "  Renamed ").ok(), "rename works");
            helper.assertTrue(
                    TravelData.get(player).custom().get(1).name().equals("Renamed"), "the name changed");
            helper.assertTrue(
                    TravelData.get(player).custom().get(1).pos().equals(player.blockPosition()), "the position is kept");
            helper.assertTrue(!CustomWaypoints.rename(player, 1, "z".repeat(40)).ok(), "a long rename is refused");
            helper.assertTrue(!CustomWaypoints.rename(player, 7, "Nope").ok(), "renaming an unknown id is refused");

            helper.assertTrue(CustomWaypoints.delete(player, 0).ok(), "delete works");
            helper.assertTrue(!CustomWaypoints.delete(player, 0).ok(), "deleting twice is refused");
            CustomWaypoints.Result next = CustomWaypoints.create(player, "Third", 5);
            helper.assertTrue(next.id().orElse(-1) == 2, "a deleted id is never reused, got " + next.id());
            helper.assertTrue(TravelData.get(player).custom().size() == 2, "two waypoints left");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void editPayloadRunsTheSameRules(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        withMiddleEarth(() -> {
            helper.assertTrue(
                    WaypointPayloads.handleEdit(player, CustomWaypointEditPayload.create("Via payload")).ok(),
                    "create by payload");
            helper.assertTrue(
                    WaypointPayloads.handleEdit(player, CustomWaypointEditPayload.rename(0, "Renamed")).ok(),
                    "rename by payload");
            helper.assertTrue(
                    !WaypointPayloads.handleEdit(player, CustomWaypointEditPayload.create("q".repeat(40))).ok(),
                    "a 40-character name is refused, not a protocol error");
            helper.assertTrue(
                    WaypointPayloads.handleEdit(player, CustomWaypointEditPayload.delete(0)).ok(), "delete by payload");
            helper.assertTrue(TravelData.get(player).custom().isEmpty(), "the list is empty again");
        });
        ByteBuf buf = Unpooled.buffer();
        CustomWaypointEditPayload sent = CustomWaypointEditPayload.rename(3, "Wire name");
        CustomWaypointEditPayload.STREAM_CODEC.encode(buf, sent);
        helper.assertTrue(CustomWaypointEditPayload.STREAM_CODEC.decode(buf).equals(sent), "the edit round-trips");
        helper.succeed();
    }

    /** 1,024 custom waypoints with the longest names, in the widest characters, still fit in parts under 32 KiB. */
    @GameTest(template = "empty")
    public static void fullSyncWith1024CustomWaypointsIsSplitUnder32KiB(GameTestHelper helper) {
        TravelData data = TravelData.EMPTY.withRegion(SHIRE).withRegion(LOTRRevival.id("bree_land"));
        data = data.withUse(LOTRRevival.id("bree")).withClock(123_456);
        String wide = "€".repeat(CustomWaypoints.MAX_NAME);
        for (int i = 0; i < TravelData.MAX_CUSTOM; i++) {
            data = data.withNewCustom(wide, new BlockPos(-30_000_000 + i, -64, 29_999_999 - i));
        }
        helper.assertTrue(data.custom().size() == 1024, "1,024 stored");

        List<TravelSyncPayload> parts = TravelSync.parts(data, 6);
        int total = 0;
        int largest = 0;
        List<TravelSyncPayload> received = new ArrayList<>();
        for (TravelSyncPayload part : parts) {
            ByteBuf buf = Unpooled.buffer();
            TravelSyncPayload.STREAM_CODEC.encode(buf, part);
            int size = buf.readableBytes();
            total += size;
            largest = Math.max(largest, size);
            helper.assertTrue(size < 32 * 1024, "a part is " + size + " bytes, must stay under 32 KiB");
            TravelSyncPayload decoded = TravelSyncPayload.STREAM_CODEC.decode(buf);
            helper.assertTrue(decoded.equals(part), "a part must survive the wire unchanged");
            received.add(decoded);
        }
        helper.assertTrue(parts.size() == 8, "1,024 customs at 128 per part make 8 parts, got " + parts.size());
        helper.assertTrue(parts.get(0).first() && !parts.get(0).last(), "first part flags");
        helper.assertTrue(parts.get(7).last() && !parts.get(7).first(), "last part flags");

        try {
            for (TravelSyncPayload part : received) {
                ClientTravel.apply(part);
            }
            TravelData mirrored = ClientTravel.data();
            helper.assertTrue(mirrored.custom().equals(data.custom()), "the mirror holds all 1,024 in order");
            helper.assertTrue(mirrored.regions().equals(data.regions()), "regions mirrored");
            helper.assertTrue(mirrored.usesOf(LOTRRevival.id("bree")) == 1, "uses mirrored");
            helper.assertTrue(mirrored.clock() == 123_456 && ClientTravel.limit() == 6, "clock and limit mirrored");
            helper.assertTrue(mirrored.nextCustomId() == 1024, "next id derived");
            helper.assertTrue(
                    total < 8 * 32 * 1024 && largest < 32 * 1024, "sanity: total " + total + ", largest " + largest);
        } finally {
            ClientTravel.clear();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mirrorSwapsInOnlyWhenTheLastPartLandsAndRunsTheClock(GameTestHelper helper) {
        try {
            TravelData data = TravelData.EMPTY.withClock(100);
            for (int i = 0; i < 130; i++) {
                data = data.withNewCustom("c" + i, new BlockPos(i, 70, i));
            }
            List<TravelSyncPayload> parts = TravelSync.parts(data, 5);
            helper.assertTrue(parts.size() == 2, "130 customs make 2 parts, got " + parts.size());
            ClientTravel.apply(parts.get(0));
            helper.assertTrue(ClientTravel.data().custom().isEmpty(), "a half-received sync is not visible");
            ClientTravel.apply(parts.get(1));
            helper.assertTrue(ClientTravel.data().custom().size() == 130, "complete after the last part");

            ClientTravel.tick();
            ClientTravel.tick();
            helper.assertTrue(ClientTravel.clock() == 102, "the clock runs locally, is " + ClientTravel.clock());

            ClientTravel.apply(TravelSync.parts(TravelData.EMPTY.withClock(5), 5).get(0));
            helper.assertTrue(
                    ClientTravel.clock() == 5 && ClientTravel.data().custom().isEmpty(),
                    "a new sync replaces everything");
        } finally {
            ClientTravel.clear();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void viewsListThePilotWaypointsWithTheRightStates(GameTestHelper helper) {
        // Fast travel waits are off by default; this test checks the Legacy-style wait.
        TravelRules.setCooldownOverride(true);
        try {
            viewsListThePilotWaypointsWithTheRightStatesBody(helper);
        } finally {
            TravelRules.setCooldownOverride(null);
        }
    }

    private static void viewsListThePilotWaypointsWithTheRightStatesBody(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        TravelData data = TravelData.EMPTY
                .withRegion(SHIRE)
                .withUse(LOTRRevival.id("hobbiton"))
                .withNewCustom("Camp", new BlockPos(0, 70, 0));

        // Hobbit alignment -1: Shire towns are known but barred; the unaligned bridge stays usable.
        List<WaypointView> views = WaypointViews.build(
                access, data, faction -> faction.equals(Factions.HOBBIT) ? -1.0 : 0.0, 0, 0);
        helper.assertTrue(views.size() == 29, "28 pilot waypoints and 1 custom, got " + views.size());

        WaypointView hobbiton = find(views, "fixed:lotrrevival:hobbiton");
        helper.assertTrue(hobbiton.state() == WaypointView.State.BARRED, "hobbit -1 bars Hobbiton");
        helper.assertTrue(
                hobbiton.barringFaction().equals(Optional.of(Factions.HOBBIT)), "the barring faction is named");
        helper.assertTrue(hobbiton.kind() == WaypointView.Kind.FIXED, "fixed kind");
        helper.assertTrue(hobbiton.u() == 1620 && hobbiton.v() == 1460, "pixel position");
        helper.assertTrue(hobbiton.uses() == 1, "uses come from the travel data");
        helper.assertTrue(
                hobbiton.requiredTicks() == 32760, "one use at the spot: 27:18, got " + hobbiton.requiredTicks());

        WaypointView bridge = find(views, "fixed:lotrrevival:brandywine_bridge");
        helper.assertTrue(bridge.state() == WaypointView.State.USABLE, "an unaligned waypoint of an open region");
        helper.assertTrue(bridge.barringFaction().isEmpty(), "no barring faction when usable");
        helper.assertTrue(bridge.requiredTicks() == 36000, "no uses: 30:00, got " + bridge.requiredTicks());

        WaypointView bree = find(views, "fixed:lotrrevival:bree");
        helper.assertTrue(bree.state() == WaypointView.State.LOCKED, "Bree-land is not unlocked");
        helper.assertTrue(bree.barringFaction().isEmpty(), "locked is not barred");

        WaypointView camp = find(views, "custom:0");
        helper.assertTrue(camp.kind() == WaypointView.Kind.CUSTOM, "custom kind");
        helper.assertTrue(camp.state() == WaypointView.State.USABLE, "custom waypoints are usable");
        helper.assertTrue(camp.name().getString().equals("Camp"), "custom name is literal");
        helper.assertTrue(camp.u() == 1620 && camp.v() == 1460, "custom pixel position from the block position");

        int usable = 0;
        int barred = 0;
        int locked = 0;
        for (WaypointView view : views) {
            switch (view.state()) {
                case USABLE -> usable++;
                case BARRED -> barred++;
                case LOCKED -> locked++;
            }
        }
        // Shire: 16 hobbit towns barred, the bridge and the ferry usable; custom usable; the other 10 locked.
        helper.assertTrue(barred == 16, "16 hobbit towns barred, got " + barred);
        helper.assertTrue(usable == 3, "bridge, ferry and the custom waypoint usable, got " + usable);
        helper.assertTrue(locked == 10, "the other regions locked, got " + locked);

        // At 0 alignment the same towns open up.
        List<WaypointView> friendly = WaypointViews.build(access, data, faction -> 0.0, 0, 0);
        helper.assertTrue(
                find(friendly, "fixed:lotrrevival:hobbiton").state() == WaypointView.State.USABLE,
                "hobbit 0 opens Hobbiton");
        helper.succeed();
    }

    private static WaypointView find(List<WaypointView> views, String ref) {
        return views.stream().filter(v -> v.ref().equals(ref)).findFirst().orElseThrow();
    }
}
