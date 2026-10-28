package io.github.sethwillemsvg.lotrrevival.world.network;

import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.world.travel.FastTravel;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRef;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the World pod's fast travel payloads. Ids follow {@code lotrrevival:world/<name>}. */
public final class TravelPayloads {
    private TravelPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = CoreNetwork.registrar(event);
        registrar.playToServer(
                FastTravelRequestPayload.TYPE, FastTravelRequestPayload.STREAM_CODEC, (payload, ctx) -> {
                    if (!(ctx.player() instanceof ServerPlayer player) || !player.isAlive()) {
                        return;
                    }
                    if (!RequestLimiter.allow(player, FastTravelRequestPayload.TYPE.id())) {
                        return;
                    }
                    WaypointRef.tryParse(payload.ref()).ifPresent(ref -> FastTravel.request(player, ref));
                });
        registrar.playToClient(
                TravelStatusPayload.TYPE,
                TravelStatusPayload.STREAM_CODEC,
                (payload, ctx) -> TravelStatusMirror.apply(payload));
    }
}
