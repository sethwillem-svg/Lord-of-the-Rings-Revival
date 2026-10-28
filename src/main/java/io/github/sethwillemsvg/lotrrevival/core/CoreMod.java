package io.github.sethwillemsvg.lotrrevival.core;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import net.neoforged.bus.api.IEventBus;

/** Core pod entry point. Owned by the Core pod. */
public final class CoreMod {
    private CoreMod() {}

    public static void init(IEventBus modBus) {
        ModDataRegistries.register(modBus);
    }
}
