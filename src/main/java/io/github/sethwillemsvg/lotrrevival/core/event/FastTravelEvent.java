package io.github.sethwillemsvg.lotrrevival.core.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

/**
 * A player completed a fast travel. Not cancelable. Posted on the NeoForge game bus, server side, by
 * the World pod after the teleport; listened to by the Progression pod for travel achievements.
 */
public class FastTravelEvent extends Event {
    private final ServerPlayer player;
    private final String waypointRef;
    private final Vec3 from;
    private final Vec3 to;
    private final double horizontalDistance;
    private final boolean custom;

    public FastTravelEvent(
            ServerPlayer player, String waypointRef, Vec3 from, Vec3 to, double horizontalDistance, boolean custom) {
        this.player = player;
        this.waypointRef = waypointRef;
        this.from = from;
        this.to = to;
        this.horizontalDistance = horizontalDistance;
        this.custom = custom;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** Wire ref of the destination, {@code fixed:<namespace:path>} or {@code custom:<id>}. */
    public String getWaypointRef() {
        return waypointRef;
    }

    public Vec3 getFrom() {
        return from;
    }

    public Vec3 getTo() {
        return to;
    }

    /** Distance travelled ignoring height, in blocks. */
    public double getHorizontalDistance() {
        return horizontalDistance;
    }

    /** Whether the destination was a custom waypoint. */
    public boolean isCustom() {
        return custom;
    }
}
