package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferMarkerPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Sends the offer marker state of a giver to the players who can see it. The client draws within 12 blocks. */
public final class OfferMarkers {
    /** Players within this distance are told; the entity's own tracking range is 128 blocks. */
    private static final double SEND_RANGE_SQR = 128.0 * 128.0;

    private OfferMarkers() {}

    /** The group id of an offer's template, empty if the template is unknown. */
    public static Optional<ResourceLocation> groupOf(ServerLevel level, QuestGiverData.Offer offer) {
        return Quests.template(level.registryAccess(), offer.template())
                .map(Holder::value)
                .map(template -> template.group().location());
    }

    public static QuestOfferMarkerPayload payload(
            ServerLevel level, NpcEntity npc, Optional<QuestGiverData.Offer> offer) {
        Optional<ResourceLocation> group = offer.flatMap(o -> groupOf(level, o));
        return new QuestOfferMarkerPayload(npc.getId(), group, offer.flatMap(QuestGiverData.Offer::forPlayer));
    }

    /** The marker changed (offer appeared or went away): tell everyone near. */
    public static void broadcast(ServerLevel level, NpcEntity npc, Optional<QuestGiverData.Offer> offer) {
        QuestOfferMarkerPayload payload = payload(level, npc, offer);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(npc) <= SEND_RANGE_SQR) {
                ProgressionSync.send(player, payload);
            }
        }
    }

    /** A player starts tracking the giver: tell them the current marker, if any. */
    public static void sendTo(ServerPlayer player, NpcEntity npc) {
        Optional<QuestGiverData.Offer> offer = OfferService.offerOf(npc);
        if (offer.isPresent() && npc.level() instanceof ServerLevel level) {
            ProgressionSync.send(player, payload(level, npc, offer));
        }
    }
}
