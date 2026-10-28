package io.github.sethwillemsvg.lotrrevival.core.network;

import io.github.sethwillemsvg.lotrrevival.client.core.ClientAlignment;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers core's clientbound payloads. Handlers only touch the plain-data {@link ClientAlignment} mirror. */
public final class CorePayloads {
    private CorePayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = CoreNetwork.registrar(event);
        registrar.playToClient(
                AlignmentSyncPayload.TYPE,
                AlignmentSyncPayload.STREAM_CODEC,
                (payload, ctx) -> ClientAlignment.applySync(payload));
        registrar.playToClient(
                AlignmentChangePayload.TYPE,
                AlignmentChangePayload.STREAM_CODEC,
                (payload, ctx) -> ClientAlignment.applyChange(payload));
        registrar.playToClient(
                PledgeSyncPayload.TYPE,
                PledgeSyncPayload.STREAM_CODEC,
                (payload, ctx) -> ClientAlignment.applyPledge(payload));
        registrar.playToClient(
                DrainNoticePayload.TYPE,
                DrainNoticePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.player()
                        .displayClientMessage(
                                Component.translatable("alignment.lotrrevival.drain_notice", payload.factions()),
                                true));
    }
}
