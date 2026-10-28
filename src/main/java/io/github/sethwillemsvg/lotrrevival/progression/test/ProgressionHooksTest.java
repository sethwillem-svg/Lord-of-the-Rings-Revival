package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.api.LandProtection;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.material.MaterialBlocks;
import io.github.sethwillemsvg.lotrrevival.core.material.Metal;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerBases;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerBlocks;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerDesign;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerIndex;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerProtection;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerSettings;
import io.github.sethwillemsvg.lotrrevival.progression.banner.FactionBannerBlockEntity;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.Bounties;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.BountyData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Progression's registrations behind Core's LandProtection facade, bronze/silver bases and influence-gated kills. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressionHooksTest {
    private ProgressionHooksTest() {}

    /** A survival mock player (the helper's mock is always creative). Remove it when done. */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "hooks-mock"), false);
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

    private static void raise(GameTestHelper helper, BlockPos at, Block base, ServerPlayer owner) {
        ServerLevel level = helper.getLevel();
        level.setBlock(at.below(), base.defaultBlockState(), 3);
        level.setBlock(at, BannerBlocks.STANDING.get().defaultBlockState(), 3);
        FactionBannerBlockEntity banner = (FactionBannerBlockEntity) level.getBlockEntity(at);
        banner.initialise(BannerDesign.idFor(Factions.GONDOR), BannerSettings.forOwner(owner));
    }

    private static void takeDown(GameTestHelper helper, BlockPos at) {
        ServerLevel level = helper.getLevel();
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(at.below(), Blocks.AIR.defaultBlockState(), 3);
        BannerIndex.get(level).remove(at);
    }

    @GameTest(template = "empty")
    public static void facadeBlocksEnemySpawnsInsideAGondorBanner(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            raise(helper, at, Blocks.GOLD_BLOCK, owner);
            ServerLevel level = helper.getLevel();
            helper.assertFalse(
                    FactionRelations.relation(level.registryAccess(), Factions.GONDOR, Factions.ROHAN).isBad(),
                    "Rohan is not an enemy of Gondor");
            BlockPos inside = at.offset(3, 0, 3);
            helper.assertTrue(LandProtection.blocksFactionSpawn(level, inside, Factions.MORDOR), "enemy blocked");
            helper.assertFalse(LandProtection.blocksFactionSpawn(level, inside, Factions.ROHAN), "ally allowed");
            helper.assertFalse(
                    LandProtection.blocksFactionSpawn(level, inside, Factions.GONDOR), "own faction allowed");
            helper.assertFalse(
                    LandProtection.blocksFactionSpawn(level, at.offset(40, 0, 0), Factions.MORDOR),
                    "outside allowed");
            helper.assertTrue(LandProtection.blocksInvasion(level, inside, Factions.MORDOR), "invasion blocked");
            helper.assertTrue(LandProtection.blocksNpcGriefing(level, inside, Factions.MORDOR), "griefing blocked");
            helper.assertTrue(LandProtection.canEdit(level, inside, owner), "the owner may edit");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            helper.getLevel().getServer().getPlayerList().remove(owner);
        }
    }

    @GameTest(template = "empty")
    public static void bronzeAndSilverBasesSetTheRange(GameTestHelper helper) {
        helper.assertTrue(
                BannerBases.rangeOf(MaterialBlocks.BLOCK.get(Metal.BRONZE).get().defaultBlockState()) == 8,
                "bronze block is 8");
        helper.assertTrue(
                BannerBases.rangeOf(MaterialBlocks.BLOCK.get(Metal.SILVER).get().defaultBlockState()) == 16,
                "silver block is 16");

        ServerPlayer owner = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            raise(helper, at, MaterialBlocks.BLOCK.get(Metal.SILVER).get(), owner);
            ServerLevel level = helper.getLevel();
            helper.assertTrue(BannerProtection.query(level, at.offset(16, 0, 16)).size() == 1, "silver reaches 16");
            helper.assertTrue(BannerProtection.query(level, at.offset(17, 0, 0)).isEmpty(), "silver stops past 16");
            helper.assertTrue(
                    LandProtection.blocksFactionSpawn(level, at.offset(16, 0, 0), Factions.MORDOR),
                    "a silver banner blocks enemy spawns at 16");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            helper.getLevel().getServer().getPlayerList().remove(owner);
        }
    }

    @GameTest(template = "empty")
    public static void killsOutsideInfluenceLeaveNoRecord(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        var server = helper.getLevel().getServer();
        try {
            LivingEntity victim = (LivingEntity) helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
            Alignment.setInfluenceProvider((p, faction) -> 0.0f);
            NeoForge.EVENT_BUS.post(new FactionKillEvent(player, victim, Factions.GONDOR, 1.0f, false, false));
            helper.assertTrue(
                    Bounties.liveRecords(server, player.getUUID(), Factions.GONDOR) == 0,
                    "no record outside the area of influence");
            Alignment.setInfluenceProvider(null);
            NeoForge.EVENT_BUS.post(new FactionKillEvent(player, victim, Factions.GONDOR, 1.0f, false, false));
            helper.assertTrue(
                    Bounties.liveRecords(server, player.getUUID(), Factions.GONDOR) == 1, "a record inside it");
            helper.succeed();
        } finally {
            Alignment.setInfluenceProvider(null);
            BountyData.get(server).clearKills(player.getUUID(), null);
            server.getPlayerList().remove(player);
        }
    }
}
