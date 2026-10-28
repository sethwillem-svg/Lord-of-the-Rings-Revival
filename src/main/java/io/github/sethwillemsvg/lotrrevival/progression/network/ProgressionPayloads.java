package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.client.progression.AchievementToastHandler;
import io.github.sethwillemsvg.lotrrevival.client.progression.ClientAchievements;
import io.github.sethwillemsvg.lotrrevival.client.progression.ClientTitles;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleService;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the Progression pod's payloads. Ids follow {@code lotrrevival:progression/<name>}.
 * Empty for now; later tickets add one registration line per payload.
 */
public final class ProgressionPayloads {
    private ProgressionPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = CoreNetwork.registrar(event);
        registrar.playToClient(
                AchievementsFullPayload.TYPE,
                AchievementsFullPayload.STREAM_CODEC,
                (payload, ctx) -> ClientAchievements.applyFull(payload));
        registrar.playToClient(
                AchievementsDeltaPayload.TYPE,
                AchievementsDeltaPayload.STREAM_CODEC,
                (payload, ctx) -> ClientAchievements.applyDelta(payload));
        registrar.playToClient(
                AchievementToastPayload.TYPE,
                AchievementToastPayload.STREAM_CODEC,
                (payload, ctx) -> AchievementToastHandler.show(payload.id()));
        registrar.playToClient(
                TitlesFullPayload.TYPE, TitlesFullPayload.STREAM_CODEC, (payload, ctx) -> ClientTitles.applyFull(payload));
        registrar.playToClient(
                TitlesDeltaPayload.TYPE,
                TitlesDeltaPayload.STREAM_CODEC,
                (payload, ctx) -> ClientTitles.applyDelta(payload));
        registrar.playToServer(SelectTitlePayload.TYPE, SelectTitlePayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                TitleService.select(player, payload.title(), payload.color(), payload.feminine());
            }
        });
        // Later tickets: registrar.playToClient(...), registrar.playToServer(...)
    }
}
