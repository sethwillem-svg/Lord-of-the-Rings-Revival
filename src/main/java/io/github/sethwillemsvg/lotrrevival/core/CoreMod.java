package io.github.sethwillemsvg.lotrrevival.core;

import io.github.sethwillemsvg.lotrrevival.core.command.LotrCommand;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Core pod entry point. Owned by the Core pod. */
public final class CoreMod {
    private CoreMod() {}

    public static void init(IEventBus modBus) {
        ModDataRegistries.register(modBus);
        NeoForge.EVENT_BUS.addListener(LotrCommand::onRegisterCommands);
    }
}
