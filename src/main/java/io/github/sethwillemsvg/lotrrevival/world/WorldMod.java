package io.github.sethwillemsvg.lotrrevival.world;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Entry point for the World pod; the World pod owns this file. */
public final class WorldMod {
    private WorldMod() {}

    public static void init(IEventBus modBus) {
        WorldConfig.register(net.neoforged.fml.ModLoadingContext.get().getActiveContainer());
        io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointSystem.init();
        io.github.sethwillemsvg.lotrrevival.world.road.RoadMod.init();
        io.github.sethwillemsvg.lotrrevival.world.portal.RingPortals.init();
        modBus.addListener(io.github.sethwillemsvg.lotrrevival.world.network.TravelPayloads::register);
        io.github.sethwillemsvg.lotrrevival.world.network.WaypointPayloads.init(modBus);
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) -> MiddleEarthCommand.register(event.getDispatcher()));
    }
}
