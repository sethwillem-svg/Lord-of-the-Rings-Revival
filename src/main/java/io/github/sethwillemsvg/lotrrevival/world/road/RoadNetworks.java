package io.github.sethwillemsvg.lotrrevival.world.road;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.world.WorldScales;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Holds the current {@link RoadNetwork}. It is built once when the server is about to start (before any level
 * generates) and published through a volatile field, so chunk generation threads always see a complete index. An
 * empty network means no roads; worldgen works without them.
 */
public final class RoadNetworks {
    private static volatile RoadNetwork current = RoadNetwork.EMPTY;

    private RoadNetworks() {}

    public static RoadNetwork current() {
        return current;
    }

    /** Replaces the published network. */
    public static void publish(RoadNetwork network) {
        current = network;
    }

    /**
     * Builds a network from the {@code lotrrevival:road} registry at the world scale named by the same registry
     * access; empty if the registry has no entries.
     */
    public static RoadNetwork fromRegistry(RegistryAccess access) {
        Map<ResourceLocation, Road> roads = new HashMap<>();
        access.registry(ModDataRegistries.<Road>road())
                .ifPresent(registry -> registry.entrySet()
                        .forEach(e -> roads.put(e.getKey().location(), e.getValue())));
        return RoadNetwork.fromRoads(roads, WorldScales.fromRegistry(access));
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(
                (ServerAboutToStartEvent event) -> publish(fromRegistry(event.getServer().registryAccess())));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> publish(RoadNetwork.EMPTY));
    }
}
