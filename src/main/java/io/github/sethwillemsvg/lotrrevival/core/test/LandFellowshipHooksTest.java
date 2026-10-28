package io.github.sethwillemsvg.lotrrevival.core.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.api.FellowshipQuery;
import io.github.sethwillemsvg.lotrrevival.core.api.LandProtection;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.metal.BannerMetals;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Core #105: LandProtection and FellowshipQuery facades, influence accessor, bounty cause, banner metals. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandFellowshipHooksTest {
    @GameTest(template = "empty")
    public static void landProtectionDefaultsThenRegistered(GameTestHelper helper) {
        LandProtection.Impl previous = LandProtection.swapForTesting(null);
        try {
            Level level = helper.getLevel();
            BlockPos pos = helper.absolutePos(BlockPos.ZERO);
            ResourceKey<Faction> faction = Factions.GONDOR;
            helper.assertTrue(LandProtection.canEdit(level, pos, null), "default canEdit allows");
            helper.assertTrue(!LandProtection.blocksFactionSpawn(level, pos, faction), "default spawn allowed");
            helper.assertTrue(!LandProtection.blocksInvasion(level, pos, faction), "default invasion allowed");
            helper.assertTrue(!LandProtection.blocksNpcGriefing(level, pos, faction), "default griefing allowed");

            LandProtection.register(new LandProtection.Impl() {
                @Override
                public boolean canEdit(Level l, BlockPos p, net.minecraft.world.entity.player.Player player) {
                    return false;
                }

                @Override
                public boolean blocksFactionSpawn(Level l, BlockPos p, ResourceKey<Faction> f) {
                    return f.equals(Factions.GONDOR);
                }

                @Override
                public boolean blocksInvasion(Level l, BlockPos p, ResourceKey<Faction> f) {
                    return true;
                }

                @Override
                public boolean blocksNpcGriefing(Level l, BlockPos p, ResourceKey<Faction> f) {
                    return true;
                }
            });
            helper.assertTrue(!LandProtection.canEdit(level, pos, null), "registered canEdit denies");
            helper.assertTrue(LandProtection.blocksFactionSpawn(level, pos, Factions.GONDOR), "registered spawn");
            helper.assertTrue(!LandProtection.blocksFactionSpawn(level, pos, Factions.HOBBIT), "other faction");
            helper.assertTrue(LandProtection.blocksInvasion(level, pos, faction), "registered invasion");
            helper.assertTrue(LandProtection.blocksNpcGriefing(level, pos, faction), "registered griefing");

            boolean threw = false;
            try {
                LandProtection.register(new LandProtection.Impl() {});
            } catch (IllegalStateException expected) {
                threw = true;
            }
            helper.assertTrue(threw, "double registration throws");
            boolean nullThrew = false;
            try {
                LandProtection.swapForTesting(null);
                LandProtection.register(null);
            } catch (NullPointerException expected) {
                nullThrew = true;
            }
            helper.assertTrue(nullThrew, "null implementation rejected");
        } finally {
            LandProtection.swapForTesting(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fellowshipQueryDefaultsThenRegistered(GameTestHelper helper) {
        FellowshipQuery.Impl previous = FellowshipQuery.swapForTesting(null);
        try {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            helper.assertTrue(!FellowshipQuery.preventsPvp(a, b), "default pvp not prevented");
            helper.assertTrue(!FellowshipQuery.protectsUnits(a, b), "default units not protected");

            FellowshipQuery.register(new FellowshipQuery.Impl() {
                @Override
                public boolean preventsPvp(UUID x, UUID y) {
                    return x.equals(a) && y.equals(b);
                }

                @Override
                public boolean protectsUnits(UUID x, UUID y) {
                    return true;
                }
            });
            helper.assertTrue(FellowshipQuery.preventsPvp(a, b), "registered pvp");
            helper.assertTrue(!FellowshipQuery.preventsPvp(b, a), "registered pvp is as implemented");
            helper.assertTrue(FellowshipQuery.protectsUnits(a, b), "registered units");

            boolean threw = false;
            try {
                FellowshipQuery.register(new FellowshipQuery.Impl() {});
            } catch (IllegalStateException expected) {
                threw = true;
            }
            helper.assertTrue(threw, "double registration throws");
        } finally {
            FellowshipQuery.swapForTesting(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void influenceAccessorAndBountyCause(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "influence-test"));
        try {
            Alignment.setInfluenceProvider(null);
            helper.assertTrue(Alignment.influence(player, Factions.GONDOR) == 1.0f, "default influence is 1");
            Alignment.setInfluenceProvider((p, f) -> f.equals(Factions.GONDOR) ? 0.25f : 0.0f);
            helper.assertTrue(Alignment.influence(player, Factions.GONDOR) == 0.25f, "provider value");
            helper.assertTrue(Alignment.influence(player, Factions.HOBBIT) == 0.0f, "provider per faction");
        } finally {
            Alignment.setInfluenceProvider(null);
        }
        AlignmentSource source = AlignmentSource.bounty(Factions.GONDOR.location());
        helper.assertTrue(source.is(AlignmentSource.BOUNTY), "bounty cause");
        helper.assertTrue(
                AlignmentSource.BOUNTY.equals(LOTRRevival.id("bounty")), "bounty id is lotrrevival:bounty");
        helper.assertTrue(source.detail().isPresent(), "bounty detail present");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bannerMetalsRegistered(GameTestHelper helper) {
        helper.assertTrue(BannerMetals.BRONZE_BLOCK.get().asItem() == BannerMetals.BRONZE_BLOCK_ITEM.get(), "bronze");
        helper.assertTrue(BannerMetals.SILVER_BLOCK.get().asItem() == BannerMetals.SILVER_BLOCK_ITEM.get(), "silver");
        helper.assertTrue(BannerMetals.BRONZE_INGOT.get() != BannerMetals.SILVER_INGOT.get(), "ingots differ");
        helper.succeed();
    }
}
