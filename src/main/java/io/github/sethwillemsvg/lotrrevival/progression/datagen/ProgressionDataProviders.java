package io.github.sethwillemsvg.lotrrevival.progression.datagen;

import io.github.sethwillemsvg.lotrrevival.progression.datagen.banner.BannerDatagen;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The single entry point from {@code DataGenerators} into the Progression pod's own data providers (the
 * quest, achievement and title providers predate it and are still called directly). Later progression tickets add
 * their providers here and do not touch {@code DataGenerators} again.
 */
public final class ProgressionDataProviders {
    private ProgressionDataProviders() {}

    public static void gather(GatherDataEvent event) {
        BannerDatagen.gather(event);
    }
}
