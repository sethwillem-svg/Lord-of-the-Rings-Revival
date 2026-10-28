package io.github.sethwillemsvg.lotrrevival.world.network;

import io.github.sethwillemsvg.lotrrevival.client.world.ClientTravel;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.world.travel.FastTravel;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.CustomWaypoints;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registrar for the travel sync and custom waypoint payloads ({@code world/travel_sync},
 * {@code world/custom_waypoint_edit}). The fast travel request and status payloads stay in {@link TravelPayloads}.
 */
public final class WaypointPayloads {
    private WaypointPayloads() {}

    /** Wires the registrar and the post-trip resync hook; called once from {@code WorldMod.init}. */
    public static void init(IEventBus modBus) {
        modBus.addListener(WaypointPayloads::register);
        FastTravel.addResyncListener(TravelSync::send);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = CoreNetwork.registrar(event);
        registrar.playToClient(
                TravelSyncPayload.TYPE, TravelSyncPayload.STREAM_CODEC, (payload, ctx) -> ClientTravel.apply(payload));
        registrar.playToServer(
                CustomWaypointEditPayload.TYPE, CustomWaypointEditPayload.STREAM_CODEC, (payload, ctx) -> {
                    // Core's rules for client-to-server payloads: the sender is alive and under 20 per second.
                    if (!(ctx.player() instanceof ServerPlayer player) || !player.isAlive()) {
                        return;
                    }
                    if (!RequestLimiter.allow(player, CustomWaypointEditPayload.TYPE.id())) {
                        return;
                    }
                    handleEdit(player, payload);
                });
    }

    /** Applies an edit and tells the player what happened (success or the reason it was refused). */
    public static CustomWaypoints.Result handleEdit(ServerPlayer player, CustomWaypointEditPayload payload) {
        CustomWaypoints.Result result = switch (payload.op()) {
            case CREATE -> CustomWaypoints.create(player, payload.name());
            case RENAME -> CustomWaypoints.rename(player, payload.id(), payload.name());
            case DELETE -> CustomWaypoints.delete(player, payload.id());
        };
        if (!player.isFakePlayer()) {
            player.sendSystemMessage(result.message());
        }
        return result;
    }
}
