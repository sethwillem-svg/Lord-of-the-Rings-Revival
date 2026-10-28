package io.github.sethwillemsvg.lotrrevival.progression;

import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionPayloads;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;

/** Entry point for the Progression pod; the Progression pod owns this file. */
public final class ProgressionMod {
    private ProgressionMod() {}

    public static void init(IEventBus modBus) {
        ProgressionConfig.register(ModLoadingContext.get().getActiveContainer());
        ProgressionAttachments.init();
        ProgressionTriggers.init();
        modBus.addListener(ProgressionPayloads::register);
    }
}
