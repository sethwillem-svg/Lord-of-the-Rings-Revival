package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.client.progression.shield.ShieldPlacement;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.network.SelectShieldPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.ShieldSyncPayload;
import io.github.sethwillemsvg.lotrrevival.progression.shield.Cosmetics;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldDefinition;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldDefinitions;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldService;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldUnlock;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Shields: registry content, selection validity, death, sync and placement. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShieldTest {
    private static final ResourceLocation BUCKLER = LOTRRevival.id("wanderers_buckler");
    private static final ResourceLocation ROUNDEL = LOTRRevival.id("lorekeepers_roundel");

    private static void cleanUp(GameTestHelper helper, ServerPlayer player) {
        ShieldService.setCountSource(null);
        ShieldService.setSyncObserver(null);
        ShieldService.forget(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    @GameTest(template = "empty")
    public static void registryHoldsTheFourAchievementShields(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        int[] expected = {25, 50, 100, 200};
        List<ShieldDefinition> found = new ArrayList<>(ShieldDefinitions.all(access).values());
        helper.assertTrue(found.size() == 4, "four shields: " + found.size());
        for (int n = 0; n < 4; n++) {
            ShieldUnlock unlock = found.get(n).unlock();
            helper.assertTrue(
                    unlock instanceof ShieldUnlock.AchievementCount count && count.min() == expected[n],
                    "shield " + n + " unlocks at " + expected[n] + ": " + unlock);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unlockCodecReservesTheOtherTypes(GameTestHelper helper) {
        String json = "{\"unlock\":{\"type\":\"alignment\",\"faction\":\"lotrrevival:rohan\",\"min\":100.0},"
                + "\"texture\":\"lotrrevival:textures/entity/shield/x.png\"}";
        ShieldDefinition parsed = ShieldDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
        helper.assertTrue(parsed.unlock() instanceof ShieldUnlock.Alignment, "alignment parses");
        helper.assertFalse(parsed.unlock().isMet(1000), "reserved types are not met yet");
        helper.assertFalse(parsed.hiddenUntilUnlocked(), "default not hidden");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void selectionIsValidatedByAchievementCount(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<ShieldSyncPayload> sent = new ArrayList<>();
        try {
            ShieldService.setSyncObserver((p, payload) -> sent.add(payload));

            ShieldService.setCountSource(p -> 24);
            helper.assertFalse(ShieldService.select(player, Optional.of(BUCKLER)), "24 achievements: rejected");
            helper.assertTrue(ShieldService.selected(player).isEmpty(), "nothing stored on rejection");
            helper.assertTrue(sent.isEmpty(), "nothing synced on rejection");

            ShieldService.setCountSource(p -> 25);
            helper.assertTrue(ShieldService.select(player, Optional.of(BUCKLER)), "25 achievements: accepted");
            helper.assertTrue(ShieldService.selected(player).equals(Optional.of(BUCKLER)), "stored");
            helper.assertTrue(sent.size() == 1, "synced once: " + sent.size());
            helper.assertTrue(sent.get(0).player().equals(player.getUUID()), "sync names the player");
            helper.assertTrue(sent.get(0).shield().equals(Optional.of(BUCKLER)), "sync carries the shield");

            helper.assertFalse(ShieldService.select(player, Optional.of(ROUNDEL)), "50 shield needs 50");
            helper.assertFalse(
                    ShieldService.select(player, Optional.of(LOTRRevival.id("no_such_shield"))), "unknown id rejected");
            helper.assertTrue(ShieldService.selected(player).equals(Optional.of(BUCKLER)), "failed picks change nothing");

            helper.assertTrue(ShieldService.select(player, Optional.of(BUCKLER)), "re-selecting is accepted");
            helper.assertTrue(sent.size() == 1, "no sync when nothing changed");

            // Validity is checked on selection only: a falling count keeps the shield.
            ShieldService.setCountSource(p -> 0);
            helper.assertTrue(ShieldService.selected(player).equals(Optional.of(BUCKLER)), "kept when the count drops");

            helper.assertTrue(ShieldService.select(player, Optional.empty()), "removing is always allowed");
            helper.assertTrue(ShieldService.selected(player).isEmpty(), "removed");
            helper.assertTrue(sent.size() == 2 && sent.get(1).shield().isEmpty(), "removal synced");
        } finally {
            cleanUp(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void selectionSurvivesDeath(GameTestHelper helper) {
        ServerPlayer old = helper.makeMockServerPlayerInLevel();
        ServerPlayer copy = helper.makeMockServerPlayerInLevel();
        try {
            ShieldService.setCountSource(p -> 25);
            helper.assertTrue(ShieldService.select(old, Optional.of(BUCKLER)), "selected");
            copy.copyAttachmentsFrom(old, true);
            Cosmetics actual = copy.getData(ProgressionAttachments.COSMETICS);
            helper.assertTrue(actual.shield().equals(Optional.of(BUCKLER)), "copied on death: " + actual);
        } finally {
            ShieldService.setCountSource(null);
            ShieldService.forget(old);
            helper.getLevel().getServer().getPlayerList().remove(old);
            helper.getLevel().getServer().getPlayerList().remove(copy);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void payloadsRoundTrip(GameTestHelper helper) {
        ByteBuf buf = Unpooled.buffer();
        UUID id = UUID.randomUUID();
        ShieldSyncPayload.STREAM_CODEC.encode(buf, new ShieldSyncPayload(id, Optional.of(BUCKLER)));
        ShieldSyncPayload sync = ShieldSyncPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(sync.player().equals(id) && sync.shield().equals(Optional.of(BUCKLER)), "sync round trip");

        ByteBuf buf2 = Unpooled.buffer();
        SelectShieldPayload.STREAM_CODEC.encode(buf2, new SelectShieldPayload(Optional.empty()));
        helper.assertTrue(SelectShieldPayload.STREAM_CODEC.decode(buf2).shield().isEmpty(), "select none round trip");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldMovesToTheArmWithAWeaponOrTool(GameTestHelper helper) {
        helper.assertTrue(
                ShieldPlacement.choose(false, ItemStack.EMPTY) == ShieldPlacement.Spot.BACK, "empty hand: back");
        helper.assertTrue(
                ShieldPlacement.choose(false, new ItemStack(Items.APPLE)) == ShieldPlacement.Spot.BACK, "food: back");
        helper.assertTrue(
                ShieldPlacement.choose(false, new ItemStack(Items.IRON_SWORD)) == ShieldPlacement.Spot.ARM, "sword: arm");
        helper.assertTrue(
                ShieldPlacement.choose(false, new ItemStack(Items.DIAMOND_PICKAXE)) == ShieldPlacement.Spot.ARM,
                "pickaxe: arm");
        helper.assertTrue(
                ShieldPlacement.choose(false, new ItemStack(Items.STONE_HOE)) == ShieldPlacement.Spot.ARM, "hoe: arm");
        helper.assertTrue(
                ShieldPlacement.choose(true, new ItemStack(Items.IRON_SWORD)) == ShieldPlacement.Spot.FRONT,
                "blocking: raised in front");
        helper.succeed();
    }
}
