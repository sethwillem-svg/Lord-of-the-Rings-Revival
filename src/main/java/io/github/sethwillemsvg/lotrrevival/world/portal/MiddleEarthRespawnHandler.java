package io.github.sethwillemsvg.lotrrevival.world.portal;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.world.WorldHeights;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import io.github.sethwillemsvg.lotrrevival.world.WorldConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/** Applies {@link MiddleEarthRespawn} to deaths in Middle-earth through NeoForge's respawn-position event. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class MiddleEarthRespawnHandler {
    private MiddleEarthRespawnHandler() {}

    @SubscribeEvent
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        var player = event.getEntity();
        // The event fires for the old, removed player; its level and position are where it died.
        if (event.isFromEndFight() || !player.level().dimension().equals(ModDimensions.MIDDLE_EARTH)) {
            return;
        }
        var server = player.getServer();
        ServerLevel level = server == null ? null : server.getLevel(ModDimensions.MIDDLE_EARTH);
        if (level == null) {
            return;
        }

        // Vanilla resolved the player's bed. It is a valid Middle-earth bed only when it landed in Middle-earth.
        DimensionTransition original = event.getOriginalDimensionTransition();
        boolean validBed = original.newLevel().dimension().equals(ModDimensions.MIDDLE_EARTH)
                && !original.missingRespawnBlock();
        Optional<MiddleEarthRespawn.Spot> bed = validBed
                ? Optional.of(new MiddleEarthRespawn.Spot(original.pos().x, original.pos().z))
                : Optional.empty();

        BlockPos spawn = MiddleEarthSpawn.arrivalPoint(level);
        var settings = new MiddleEarthRespawn.Settings(
                WorldConfig.get(WorldConfig.RESPAWN_ENABLED),
                WorldConfig.get(WorldConfig.RESPAWN_BED_DISTANCE),
                WorldConfig.get(WorldConfig.RESPAWN_SPAWN_DISTANCE),
                WorldConfig.get(WorldConfig.RESPAWN_MIN_OFFSET),
                WorldConfig.get(WorldConfig.RESPAWN_MAX_OFFSET));
        var choice = MiddleEarthRespawn.choose(
                settings,
                new MiddleEarthRespawn.Spot(player.getX(), player.getZ()),
                bed,
                new MiddleEarthRespawn.Spot(spawn.getX() + 0.5, spawn.getZ() + 0.5),
                level.getRandom(),
                (x, z) -> isDryLand(level, x, z));

        switch (choice.kind()) {
            // Vanilla already picked the Middle-earth bed; leave it alone.
            case BED -> { }
            case SPAWN -> event.setDimensionTransition(transition(level, Vec3.atBottomCenterOf(spawn)));
            case FAR_OFFSET -> {
                int x = (int) Math.floor(choice.spot().x());
                int z = (int) Math.floor(choice.spot().z());
                int y = WorldHeights.surface(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                event.setDimensionTransition(transition(level, new Vec3(x + 0.5, y, z + 0.5)));
            }
        }
    }

    private static DimensionTransition transition(ServerLevel level, Vec3 pos) {
        return new DimensionTransition(level, pos, Vec3.ZERO, 0.0f, 0.0f, DimensionTransition.DO_NOTHING);
    }

    /** Land on the painted map: not an ocean, lake or river biome, and the top block is not a fluid. */
    private static boolean isDryLand(ServerLevel level, int x, int z) {
        int top = WorldHeights.surface(level, Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos surface = new BlockPos(x, top - 1, z);
        if (!level.getFluidState(surface).isEmpty()) {
            return false;
        }
        var biome = level.getBiome(surface);
        return !(biome.is(MiddleEarthBiomes.OCEAN) || biome.is(MiddleEarthBiomes.LAKE) || biome.is(MiddleEarthBiomes.RIVER));
    }
}
