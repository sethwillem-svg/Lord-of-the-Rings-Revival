package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import net.neoforged.bus.api.IEventBus;

/**
 * Wires the banner system into {@code ProgressionMod.init}: the synced {@code banner_design} registry, the blocks
 * and their data components, and (through {@link BannerEvents}) the game-bus listeners.
 */
public final class BannerSystem {
    private BannerSystem() {}

    public static void init(IEventBus modBus) {
        ModDataRegistries.registerSynced(BannerDesign.REGISTRY, BannerDesign.CODEC);
        BannerBlocks.init(modBus);
    }
}
