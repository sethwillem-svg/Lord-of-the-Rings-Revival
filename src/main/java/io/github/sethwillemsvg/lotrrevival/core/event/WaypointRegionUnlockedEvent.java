package io.github.sethwillemsvg.lotrrevival.core.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * A player unlocked a waypoint region. Not cancelable. Posted on the NeoForge game bus, server side,
 * by the World pod after the fact; listened to by the Progression pod for travel achievements.
 */
public class WaypointRegionUnlockedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation region;

    public WaypointRegionUnlockedEvent(ServerPlayer player, ResourceLocation region) {
        this.player = player;
        this.region = region;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** Id of the region in the {@code lotrrevival:waypoint_region} registry. */
    public ResourceLocation getRegion() {
        return region;
    }
}
