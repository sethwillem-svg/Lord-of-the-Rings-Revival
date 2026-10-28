package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.core.event.WaypointRegionUnlockedEvent;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Unlocks the regions of the biome a player stands in, every 20 ticks in Middle-earth. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class RegionUnlocker {
    public static final int CHECK_INTERVAL = 20;

    private RegionUnlocker() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % CHECK_INTERVAL != 0
                || !player.level().dimension().equals(ModDimensions.MIDDLE_EARTH)) {
            return;
        }
        unlockAt(player, player.serverLevel().getBiome(player.blockPosition()));
    }

    /**
     * Unlocks every region listing {@code biome} that the player has not unlocked yet: stores it, tells the player and
     * posts {@link WaypointRegionUnlockedEvent}.
     *
     * @return the regions newly unlocked
     */
    public static List<ResourceLocation> unlockAt(ServerPlayer player, Holder<Biome> biome) {
        var access = player.serverLevel().registryAccess();
        List<ResourceLocation> newlyUnlocked = new ArrayList<>();
        var biomeKey = biome.unwrapKey();
        if (biomeKey.isEmpty()) {
            return newlyUnlocked;
        }
        TravelData data = TravelData.get(player);
        Registry<WaypointRegion> regions = access.registryOrThrow(ModDataRegistries.<WaypointRegion>waypointRegion());
        for (ResourceLocation id : RegionIndex.of(access).regionsOf(biomeKey.get().location())) {
            if (!data.hasRegion(id) && regions.containsKey(id)) {
                data = data.withRegion(id);
                newlyUnlocked.add(id);
            }
        }
        if (newlyUnlocked.isEmpty()) {
            return newlyUnlocked;
        }
        TravelData.set(player, data);
        for (ResourceLocation id : newlyUnlocked) {
            Component name = regions.get(id).name();
            player.sendSystemMessage(Component.translatable("message.lotrrevival.region_unlocked", name));
            NeoForge.EVENT_BUS.post(new WaypointRegionUnlockedEvent(player, id));
        }
        return newlyUnlocked;
    }
}
