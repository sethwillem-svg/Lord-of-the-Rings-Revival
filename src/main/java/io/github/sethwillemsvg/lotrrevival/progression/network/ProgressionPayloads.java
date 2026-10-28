package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.client.progression.ClientQuests;
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
                QuestsFullPayload.TYPE,
                QuestsFullPayload.STREAM_CODEC,
                (payload, ctx) -> ClientQuests.applyFull(payload));
        registrar.playToClient(
                QuestsDeltaPayload.TYPE,
                QuestsDeltaPayload.STREAM_CODEC,
                (payload, ctx) -> ClientQuests.applyDelta(payload));
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
        registrar.playToClient(
                QuestOfferMarkerPayload.TYPE,
                QuestOfferMarkerPayload.STREAM_CODEC,
                (payload, ctx) -> io.github.sethwillemsvg.lotrrevival.client.progression.quest.offer.ClientQuestOffers
                        .onMarker(payload));
        registrar.playToClient(
                OpenQuestOfferPayload.TYPE,
                OpenQuestOfferPayload.STREAM_CODEC,
                (payload, ctx) -> io.github.sethwillemsvg.lotrrevival.client.progression.quest.offer.ClientQuestOffers
                        .onOpen(payload));
        registrar.playToClient(
                QuestOfferClosedPayload.TYPE,
                QuestOfferClosedPayload.STREAM_CODEC,
                (payload, ctx) -> io.github.sethwillemsvg.lotrrevival.client.progression.quest.offer.ClientQuestOffers
                        .onClosed(payload));
        registrar.playToServer(OfferScreenPayload.TYPE, OfferScreenPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferService.screen(
                        player, payload.entityId(), payload.open());
            }
        });
        registrar.playToServer(AcceptQuestPayload.TYPE, AcceptQuestPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferService.accept(
                        player, payload.entityId(), payload.template(), payload.count());
            }
        });
        registrar.playToServer(DeclineQuestPayload.TYPE, DeclineQuestPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferService.decline(
                        player, payload.entityId());
            }
        });
        // Later tickets: registrar.playToClient(...), registrar.playToServer(...)
    }
}
