package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.client.progression.ClientQuests;
import io.github.sethwillemsvg.lotrrevival.client.progression.AchievementToastHandler;
import io.github.sethwillemsvg.lotrrevival.client.progression.ClientAchievements;
import io.github.sethwillemsvg.lotrrevival.client.progression.ClientTitles;
import io.github.sethwillemsvg.lotrrevival.client.progression.fellowship.ClientFellowships;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipActions;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipPositions;
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
        registrar.playToServer(SelectShieldPayload.TYPE, SelectShieldPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldService.select(player, payload.shield());
            }
        });
        registrar.playToClient(
                ShieldSyncPayload.TYPE,
                ShieldSyncPayload.STREAM_CODEC,
                (payload, ctx) -> io.github.sethwillemsvg.lotrrevival.client.progression.shield.ClientShields.apply(payload));
        registrar.playToClient(
                FellowshipsFullPayload.TYPE,
                FellowshipsFullPayload.STREAM_CODEC,
                (payload, ctx) -> ClientFellowships.applyFull(payload));
        registrar.playToClient(
                FellowshipsDeltaPayload.TYPE,
                FellowshipsDeltaPayload.STREAM_CODEC,
                (payload, ctx) -> ClientFellowships.applyDelta(payload));
        registrar.playToClient(
                FellowshipPositionsPayload.TYPE,
                FellowshipPositionsPayload.STREAM_CODEC,
                (payload, ctx) -> ClientFellowships.applyPositions(payload));
        registrar.playToServer(
                FellowshipActionPayload.TYPE, FellowshipActionPayload.STREAM_CODEC, (payload, ctx) -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        FellowshipActions.perform(player, payload);
                    }
                });
        registrar.playToServer(MapViewingPayload.TYPE, MapViewingPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                FellowshipPositions.setViewing(player, payload.open());
            }
        });
        registrar.playToServer(AbandonQuestPayload.TYPE, AbandonQuestPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                io.github.sethwillemsvg.lotrrevival.progression.quest.QuestLogActions.abandon(player, payload);
            }
        });
        registrar.playToServer(RemoveQuestPayload.TYPE, RemoveQuestPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                io.github.sethwillemsvg.lotrrevival.progression.quest.QuestLogActions.remove(player, payload);
            }
        });
        registrar.playToServer(TrackQuestPayload.TYPE, TrackQuestPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                io.github.sethwillemsvg.lotrrevival.progression.quest.QuestLogActions.track(player, payload);
            }
        });
        registrar.playToServer(
                DeleteQuestRecordPayload.TYPE, DeleteQuestRecordPayload.STREAM_CODEC, (payload, ctx) -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        io.github.sethwillemsvg.lotrrevival.progression.quest.QuestLogActions.deleteRecord(
                                player, payload);
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
