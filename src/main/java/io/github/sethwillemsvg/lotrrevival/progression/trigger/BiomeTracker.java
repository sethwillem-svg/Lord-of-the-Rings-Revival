package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressCounters;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Polls each player's biome every 20 ticks and drives {@code enter_biome} and {@code distinct_biomes}. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class BiomeTracker {
    public static final int POLL_INTERVAL_TICKS = 20;

    private BiomeTracker() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % POLL_INTERVAL_TICKS == 0) {
            observe(player, player.level().dimension(), player.level().getBiome(player.blockPosition()));
        }
    }

    /**
     * Records one poll result. Returns true when the biome differed from {@code last_biome} (and the
     * triggers were fired), false when nothing changed.
     */
    public static boolean observe(ServerPlayer player, ResourceKey<Level> dimension, Holder<Biome> biome) {
        ResourceLocation id = biome.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null) {
            return false;
        }
        ProgressCounters counters = player.getData(ProgressionAttachments.PROGRESS_COUNTERS);
        if (counters.lastBiome().filter(id::equals).isPresent()) {
            return false;
        }
        counters = counters.withLastBiome(id);
        boolean newlyVisited =
                dimension.equals(ModDimensions.MIDDLE_EARTH) && !counters.visitedBiomes().contains(id);
        if (newlyVisited) {
            counters = counters.withVisitedBiome(id);
        }
        player.setData(ProgressionAttachments.PROGRESS_COUNTERS, counters);
        ProgressionTriggers.ENTER_BIOME.get().trigger(player, biome, dimension);
        if (newlyVisited) {
            ProgressionTriggers.DISTINCT_BIOMES.get().trigger(player, counters.visitedBiomes().size());
        }
        return true;
    }
}
