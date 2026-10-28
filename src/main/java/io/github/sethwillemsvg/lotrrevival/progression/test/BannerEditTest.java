package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerBlocks;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerDesign;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerEditService;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerIndex;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerPermission;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerProtection;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerSettings;
import io.github.sethwillemsvg.lotrrevival.progression.banner.FactionBannerBlockEntity;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.Fellowship;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipData;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipResult;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.Fellowships;
import io.github.sethwillemsvg.lotrrevival.progression.network.OpenBannerEditorPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.UpdateBannerPayload;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The banner edit screen's server side: opening rules, update validation and fellowship whitelist entries. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BannerEditTest {
    private static final ResourceLocation GONDOR = BannerDesign.idFor(Factions.GONDOR);

    private BannerEditTest() {}

    /** A survival mock player (the helper's mock is always creative). Remove it with {@link #remove}. */
    private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static FactionBannerBlockEntity raise(GameTestHelper helper, BlockPos at, BannerSettings settings) {
        ServerLevel level = helper.getLevel();
        level.setBlock(at.below(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        level.setBlock(at, BannerBlocks.STANDING.get().defaultBlockState(), 3);
        FactionBannerBlockEntity banner = (FactionBannerBlockEntity) level.getBlockEntity(at);
        banner.initialise(GONDOR, settings);
        return banner;
    }

    private static void takeDown(GameTestHelper helper, BlockPos at) {
        ServerLevel level = helper.getLevel();
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(at.below(), Blocks.AIR.defaultBlockState(), 3);
        BannerIndex.get(level).remove(at);
    }

    private static UpdateBannerPayload update(BlockPos pos, BannerSettings from) {
        return new UpdateBannerPayload(
                pos,
                from.mode(),
                from.threshold(),
                List.of(),
                UpdateBannerPayload.mask(from.defaults()),
                from.selfProtection(),
                from.customRange());
    }

    private static UpdateBannerPayload withThreshold(UpdateBannerPayload p, int threshold) {
        return new UpdateBannerPayload(
                p.pos(), p.mode(), threshold, p.entries(), p.defaultsMask(), p.selfProtection(), p.customRange());
    }

    private static void cleanUp(MinecraftServer server, List<UUID> owners) {
        Fellowships.resetHooks();
        BannerEditService.resetRateLimits();
        for (Fellowship fellowship : FellowshipData.get(server).all()) {
            if (owners.contains(fellowship.owner())) {
                Fellowships.disbandNow(server, fellowship);
            }
        }
    }

    @GameTest(template = "empty")
    public static void fellowshipEntryAdmitsMembersOnly(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper, "edit-owner");
        ServerPlayer member = survivalPlayer(helper, "edit-member");
        ServerPlayer stranger = survivalPlayer(helper, "edit-stranger");
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            BannerEditService.resetRateLimits();
            Fellowships.setAchievementCounter(p -> 0);
            FellowshipResult created = Fellowships.create(owner, "Edit Company", false);
            helper.assertTrue(created.ok(), "create: " + created.message().getString());
            Fellowship fellowship = FellowshipData.get(server).of(owner.getUUID()).get(0);
            helper.assertTrue(Fellowships.invite(owner, fellowship.id(), member.getUUID()).ok(), "invite");
            helper.assertTrue(Fellowships.accept(member, fellowship.id()).ok(), "accept");

            BannerSettings fresh = BannerSettings.forOwner(owner);
            FactionBannerBlockEntity banner = raise(helper, at, fresh);
            owner.setPos(Vec3.atCenterOf(at));

            UpdateBannerPayload base = update(at, fresh);
            UpdateBannerPayload payload = new UpdateBannerPayload(
                    at,
                    BannerSettings.Mode.WHITELIST,
                    base.threshold(),
                    List.of(new UpdateBannerPayload.EntryEdit(
                            BannerSettings.Kind.FELLOWSHIP,
                            fellowship.id(),
                            "spoofed name",
                            UpdateBannerPayload.mask(BannerPermission.setOf(BannerPermission.FULL)))),
                    0,
                    true,
                    0);
            helper.assertTrue(
                    BannerEditService.update(owner, payload) == BannerEditService.Result.APPLIED, "owner update applies");
            BannerSettings saved = banner.settings();
            helper.assertTrue(saved.mode() == BannerSettings.Mode.WHITELIST, "mode saved");
            helper.assertTrue(saved.entries().size() == 1, "entry saved");
            helper.assertTrue(saved.entries().get(0).name().equals("Edit Company"), "name comes from the server");
            helper.assertTrue(BannerIndex.get(helper.getLevel()).get(at).settings().equals(saved), "index updated");

            BlockPos inside = at.offset(3, 0, 3);
            helper.assertTrue(
                    BannerProtection.allows(helper.getLevel(), inside, member, BannerPermission.FULL),
                    "a fellowship member is admitted");
            helper.assertFalse(
                    BannerProtection.allows(helper.getLevel(), inside, stranger, BannerPermission.FULL),
                    "a non-member is refused");
            helper.assertTrue(
                    BannerProtection.allows(helper.getLevel(), inside, owner, BannerPermission.FULL), "the owner is admitted");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            cleanUp(server, List.of(owner.getUUID()));
            remove(helper, owner);
            remove(helper, member);
            remove(helper, stranger);
        }
    }

    @GameTest(template = "empty")
    public static void nonOwnerUpdateIsDropped(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper, "edit-owner");
        ServerPlayer stranger = survivalPlayer(helper, "edit-stranger");
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            BannerEditService.resetRateLimits();
            BannerSettings fresh = BannerSettings.forOwner(owner);
            FactionBannerBlockEntity banner = raise(helper, at, fresh);
            stranger.setPos(Vec3.atCenterOf(at));
            owner.setPos(Vec3.atCenterOf(at).add(500, 0, 0));

            UpdateBannerPayload payload = withThreshold(update(at, fresh), 500);
            helper.assertTrue(
                    BannerEditService.update(stranger, payload) == BannerEditService.Result.NOT_ALLOWED,
                    "a non-owner is dropped");
            helper.assertTrue(banner.settings().equals(fresh), "settings unchanged");
            helper.assertFalse(BannerEditService.canEdit(stranger, banner), "a stranger cannot edit");
            helper.assertTrue(BannerEditService.canEdit(owner, banner), "the owner can edit");

            helper.assertTrue(
                    BannerEditService.update(owner, payload) == BannerEditService.Result.TOO_FAR,
                    "an owner out of reach is dropped");
            helper.assertTrue(banner.settings().equals(fresh), "settings still unchanged");

            banner.setSettings(BannerSettings.forStructure());
            helper.assertFalse(BannerEditService.canEdit(owner, banner), "a structure banner cannot be edited");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            BannerEditService.resetRateLimits();
            remove(helper, owner);
            remove(helper, stranger);
        }
    }

    @GameTest(template = "empty")
    public static void thresholdBoundsAndValidation(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper, "edit-owner");
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            BannerEditService.resetRateLimits();
            BannerSettings fresh = BannerSettings.forOwner(owner);
            FactionBannerBlockEntity banner = raise(helper, at, fresh);
            owner.setPos(Vec3.atCenterOf(at));
            UpdateBannerPayload base = update(at, fresh);

            for (int bad : new int[] {0, 10_001, -5}) {
                BannerEditService.resetRateLimits();
                helper.assertTrue(
                        BannerEditService.update(owner, withThreshold(base, bad)) == BannerEditService.Result.INVALID,
                        "threshold " + bad + " rejected");
                helper.assertTrue(banner.settings().equals(fresh), "unchanged after " + bad);
            }
            for (int good : new int[] {1, 10_000}) {
                BannerEditService.resetRateLimits();
                helper.assertTrue(
                        BannerEditService.update(owner, withThreshold(base, good)) == BannerEditService.Result.APPLIED,
                        "threshold " + good + " accepted");
                helper.assertTrue(banner.settings().threshold() == good, "threshold " + good + " saved");
            }

            // A second request in the same tick is rate limited.
            helper.assertTrue(
                    BannerEditService.update(owner, withThreshold(base, 20)) == BannerEditService.Result.RATE_LIMITED,
                    "rate limited");

            // A fellowship the owner is not in, a custom range without op, extra slots and unknown bits are refused.
            BannerEditService.resetRateLimits();
            UpdateBannerPayload foreign = new UpdateBannerPayload(
                    at,
                    BannerSettings.Mode.WHITELIST,
                    5,
                    List.of(new UpdateBannerPayload.EntryEdit(
                            BannerSettings.Kind.FELLOWSHIP, UUID.randomUUID(), "x", 1)),
                    0,
                    true,
                    0);
            helper.assertTrue(
                    BannerEditService.update(owner, foreign) == BannerEditService.Result.INVALID, "unknown fellowship refused");
            BannerEditService.resetRateLimits();
            UpdateBannerPayload range = new UpdateBannerPayload(
                    at, BannerSettings.Mode.FACTION, 5, List.of(), 0, true, 12);
            helper.assertTrue(
                    BannerEditService.update(owner, range) == BannerEditService.Result.INVALID,
                    "custom range needs an operator");
            BannerEditService.resetRateLimits();
            UpdateBannerPayload bits = new UpdateBannerPayload(
                    at, BannerSettings.Mode.FACTION, 5, List.of(), 1 << 20, true, 0);
            helper.assertTrue(
                    BannerEditService.update(owner, bits) == BannerEditService.Result.INVALID, "unknown permission bits refused");
            BannerEditService.resetRateLimits();
            UpdateBannerPayload self = new UpdateBannerPayload(
                    at,
                    BannerSettings.Mode.WHITELIST,
                    5,
                    List.of(new UpdateBannerPayload.EntryEdit(
                            BannerSettings.Kind.PLAYER, owner.getUUID(), owner.getGameProfile().getName(), 1)),
                    0,
                    true,
                    0);
            helper.assertTrue(
                    BannerEditService.update(owner, self) == BannerEditService.Result.INVALID,
                    "the owner cannot be listed a second time");
            helper.assertTrue(banner.settings().threshold() == 10_000, "still the last good value");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            cleanUp(server, List.of(owner.getUUID()));
            remove(helper, owner);
        }
    }

    @GameTest(template = "empty")
    public static void payloadsRoundTrip(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper, "edit-owner");
        try {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            UpdateBannerPayload update = new UpdateBannerPayload(
                    new BlockPos(1, 2, 3),
                    BannerSettings.Mode.WHITELIST,
                    77,
                    List.of(new UpdateBannerPayload.EntryEdit(
                            BannerSettings.Kind.PLAYER, UpdateBannerPayload.NIL, "friend", 0b1010)),
                    0b101,
                    false,
                    9);
            UpdateBannerPayload.STREAM_CODEC.encode(buf, update);
            helper.assertTrue(UpdateBannerPayload.STREAM_CODEC.decode(buf).equals(update), "update_banner round trip");

            BannerSettings settings = BannerSettings.forOwner(owner)
                    .withMode(BannerSettings.Mode.WHITELIST)
                    .withEntries(List.of(BannerSettings.Entry.player(UUID.randomUUID(), "pal")));
            OpenBannerEditorPayload open = new OpenBannerEditorPayload(new BlockPos(4, 5, 6), GONDOR, settings, true);
            OpenBannerEditorPayload.STREAM_CODEC.encode(buf, open);
            helper.assertTrue(OpenBannerEditorPayload.STREAM_CODEC.decode(buf).equals(open), "open_banner_editor round trip");
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }
}
