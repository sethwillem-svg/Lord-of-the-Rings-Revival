package io.github.sethwillemsvg.lotrrevival.world;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Entry point for the World pod; the World pod owns this file. */
public final class WorldMod {
    private WorldMod() {}

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) -> MiddleEarthCommand.register(event.getDispatcher()));
    }
}
