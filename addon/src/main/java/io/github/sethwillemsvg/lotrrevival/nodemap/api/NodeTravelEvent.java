package io.github.sethwillemsvg.lotrrevival.nodemap.api;

import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fast travel events on {@code NeoForge.EVENT_BUS}. {@link Depart} is posted when the charge completes, before anything
 * moves; cancel it (with a reason) to veto the trip. {@link Arrive} is posted after the player and their companions
 * arrived; the hiring system listens to it to bring units on Follow along.
 */
public abstract class NodeTravelEvent extends Event {
    private final ServerPlayer player;
    private final NodeRef from;
    private final NodeRef to;
    private final double length;

    protected NodeTravelEvent(ServerPlayer player, NodeRef from, NodeRef to, double length) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.length = length;
    }

    public ServerPlayer getPlayer() { return player; }
    /** The departure node. */
    public NodeRef getFrom() { return from; }
    /** The destination node. */
    public NodeRef getTo() { return to; }
    /** Journey length L in blocks (route factors applied). */
    public double getLength() { return length; }

    public static final class Depart extends NodeTravelEvent implements ICancellableEvent {
        @Nullable private Component reason;

        public Depart(ServerPlayer player, NodeRef from, NodeRef to, double length) {
            super(player, from, to, length);
        }

        /** Cancels the trip with the message the player sees. */
        public void veto(Component reason) {
            this.reason = reason;
            setCanceled(true);
        }

        @Nullable
        public Component getReason() { return reason; }
    }

    public static final class Arrive extends NodeTravelEvent {
        private final Vec3 fromPos;
        private final ResourceKey<Level> fromDimension;
        private final Vec3 toPos;

        public Arrive(ServerPlayer player, NodeRef from, NodeRef to, double length, Vec3 fromPos, ResourceKey<Level> fromDimension,
                      Vec3 toPos) {
            super(player, from, to, length);
            this.fromPos = fromPos;
            this.fromDimension = fromDimension;
            this.toPos = toPos;
        }

        /** Where the player stood when the charge completed. */
        public Vec3 getFromPos() { return fromPos; }
        public ResourceKey<Level> getFromDimension() { return fromDimension; }
        /** Where the player landed. */
        public Vec3 getToPos() { return toPos; }
    }
}
