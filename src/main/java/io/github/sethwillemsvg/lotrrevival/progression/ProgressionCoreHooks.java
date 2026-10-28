package io.github.sethwillemsvg.lotrrevival.progression;

import io.github.sethwillemsvg.lotrrevival.core.api.FellowshipQuery;
import io.github.sethwillemsvg.lotrrevival.core.api.LandProtection;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerPermission;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerProtection;
import io.github.sethwillemsvg.lotrrevival.progression.banner.FellowshipMembership;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipData;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipRules;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Registers Progression's answers to Core's cross-pod facades: fellowships ({@link FellowshipQuery}) and banner land
 * protection ({@link LandProtection}), and lets banner whitelists resolve fellowship entries.
 */
public final class ProgressionCoreHooks {
    private ProgressionCoreHooks() {}

    /** Banner whitelist fellowship entries resolve against the live fellowship data. */
    public static final FellowshipMembership MEMBERSHIP = (fellowship, player) -> {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null
                && FellowshipData.get(server).find(fellowship).map(f -> f.isMember(player)).orElse(false);
    };

    static void init() {
        FellowshipQuery.register(new FellowshipQuery.Impl() {
            @Override
            public boolean preventsPvp(UUID a, UUID b) {
                return FellowshipRules.preventsPvp(a, b);
            }

            @Override
            public boolean protectsUnits(UUID a, UUID b) {
                return FellowshipRules.protectsUnits(a, b);
            }
        });
        LandProtection.register(new LandProtection.Impl() {
            @Override
            public boolean canEdit(Level level, BlockPos pos, Player player) {
                return BannerProtection.allows(level, pos, player, BannerPermission.FULL);
            }

            @Override
            public boolean blocksFactionSpawn(Level level, BlockPos pos, ResourceKey<Faction> faction) {
                return BannerProtection.blocksFactionSpawn(level, pos, faction);
            }

            @Override
            public boolean blocksInvasion(Level level, BlockPos pos, ResourceKey<Faction> faction) {
                return BannerProtection.blocksInvasion(level, pos, faction);
            }

            @Override
            public boolean blocksNpcGriefing(Level level, BlockPos pos, ResourceKey<Faction> faction) {
                return BannerProtection.blocksNpcGriefing(level, pos, faction);
            }
        });
        BannerProtection.setFellowships(MEMBERSHIP);
    }
}
