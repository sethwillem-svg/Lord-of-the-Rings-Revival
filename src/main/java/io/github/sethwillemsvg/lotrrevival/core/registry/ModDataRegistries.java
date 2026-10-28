package io.github.sethwillemsvg.lotrrevival.core.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Declares the mod's datapack registries (keys and codecs). Owned by the Core pod.
 *
 * <p>No registries yet; the faction registry arrives with its own ticket.
 */
public final class ModDataRegistries {
    private ModDataRegistries() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModDataRegistries::onNewRegistry);
    }

    private static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Intentionally empty in G1.
    }
}
