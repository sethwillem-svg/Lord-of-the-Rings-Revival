package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.network.OpenQuestOfferPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferClosedPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.event.QuestOfferEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Server side of quest offers on a giver: setting and clearing the offer, the screen viewers, and the validated
 * accept, decline and screen-open requests. Every client request is checked here; invalid ones are dropped.
 */
public final class OfferService {
    /** The player must be this close (blocks) to accept. */
    public static final double REACH = 8.0;

    /** A viewer further than this from the giver stops pausing the offer. */
    public static final double VIEWER_RANGE = 16.0;

    /** Result of an accept request. */
    public enum AcceptOutcome {
        ACCEPTED,
        /** The entity is gone or is not a quest giver. */
        NO_GIVER,
        TOO_FAR,
        /** No offer any more, or not the one the player saw: someone else took it, or it expired. */
        TAKEN,
        /** A private offer for another player. */
        NOT_FOR_YOU,
        /** The giver is busy (fighting, hired) and cannot be accepted from until idle again. */
        BUSY,
        TOO_MANY,
        /** Refused by the caps' sibling rules (hostile, alignment, one per giver) or an accept check. */
        REFUSED
    }

    /** Extra accept rule (for example the bounty rules); return false to refuse. */
    @FunctionalInterface
    public interface AcceptCheck {
        boolean allows(ServerPlayer player, NpcEntity giver, QuestGiverData.Offer offer);
    }

    private static final List<AcceptCheck> CHECKS = new CopyOnWriteArrayList<>();

    private OfferService() {}

    public static void addAcceptCheck(AcceptCheck check) {
        CHECKS.add(check);
    }

    // ------------------------------------------------------------------ offer state

    public static QuestGiverData data(NpcEntity npc) {
        return npc.getExistingData(ProgressionAttachments.QUEST_GIVER).orElse(QuestGiverData.EMPTY);
    }

    public static Optional<QuestGiverData.Offer> offerOf(NpcEntity npc) {
        return data(npc).offer();
    }

    /** Stores the offer on the giver and tells nearby players. */
    public static void setOffer(NpcEntity npc, QuestGiverData.Offer offer) {
        npc.setData(ProgressionAttachments.QUEST_GIVER, data(npc).withOffer(Optional.of(offer)));
        if (npc.level() instanceof ServerLevel level) {
            OfferMarkers.broadcast(level, npc, Optional.of(offer));
        }
    }

    /**
     * Removes the giver's offer, if any: viewers' screens close with {@code reason} and the marker goes away.
     *
     * @return whether there was an offer
     */
    public static boolean clearOffer(NpcEntity npc, QuestOfferClosedPayload.Reason reason) {
        QuestGiverData data = data(npc);
        if (data.offer().isEmpty()) {
            return false;
        }
        npc.setData(ProgressionAttachments.QUEST_GIVER, data.withOffer(Optional.empty()));
        closeViewers(npc, Optional.empty(), reason);
        if (npc.level() instanceof ServerLevel level) {
            OfferMarkers.broadcast(level, npc, Optional.empty());
        }
        return true;
    }

    /** Closes every viewer's screen except {@code except} and forgets all viewers of the giver. */
    private static void closeViewers(NpcEntity npc, Optional<UUID> except, QuestOfferClosedPayload.Reason reason) {
        if (npc.getServer() != null) {
            for (UUID viewer : OfferViewers.viewers(npc.getUUID())) {
                if (except.isPresent() && except.get().equals(viewer)) {
                    continue;
                }
                ServerPlayer player = npc.getServer().getPlayerList().getPlayer(viewer);
                if (player != null) {
                    close(player, npc.getId(), reason);
                }
            }
        }
        OfferViewers.clear(npc.getUUID());
    }

    public static void close(ServerPlayer player, int entityId, QuestOfferClosedPayload.Reason reason) {
        ProgressionSync.send(player, new QuestOfferClosedPayload(entityId, reason));
    }

    /**
     * Rolls an offer for the giver now (the random per-tick gate has already passed, or an op forces it):
     * generates from the group, lets {@link QuestOfferEvent} cancel it, then stores it.
     */
    public static Optional<QuestGiverData.Offer> rollOffer(
            ServerLevel level, NpcEntity npc, QuestGivers.GroupMatch group, RandomSource random) {
        Optional<QuestGiverData.Offer> offer = OfferRoller.generate(level, npc, group, random);
        if (offer.isEmpty()) {
            return Optional.empty();
        }
        QuestGiverData.Offer rolled = offer.get();
        if (NeoForge.EVENT_BUS
                .post(new QuestOfferEvent(npc, rolled.template(), rolled.count(), rolled.forPlayer().orElse(null)))
                .isCanceled()) {
            return Optional.empty();
        }
        setOffer(npc, rolled);
        return offer;
    }

    // ------------------------------------------------------------------ requests

    /** The quest giver the id names, if it is loaded in the player's level. */
    public static Optional<NpcEntity> giver(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        return entity instanceof NpcEntity npc && QuestGivers.isGiverType(npc) ? Optional.of(npc) : Optional.empty();
    }

    private static boolean sameOffer(QuestGiverData.Offer offer, ResourceLocation template, int count) {
        return offer.template().equals(template) && offer.count() == count;
    }

    /** Opens the offer screen for a player: sends the payload and registers the viewer. */
    public static void openScreen(ServerPlayer player, NpcEntity npc, QuestGiverData.Offer offer) {
        String kind = Quests.template(player.level().registryAccess(), offer.template())
                .map(Holder::value)
                .map(template -> OfferKinds.of(template.objective()))
                .map(OfferKind::id)
                .orElse("collect");
        ProgressionSync.send(
                player,
                new OpenQuestOfferPayload(
                        npc.getId(),
                        offer.template(),
                        offer.count(),
                        offer.alignmentBase(),
                        offer.coinBase(),
                        offer.bountyTargetName(),
                        OfferDialogue.openingKey(kind, npc.getRandom())));
        OfferViewers.add(npc.getUUID(), player.getUUID());
    }

    /** C2S {@code offer_screen}: the player opened or closed the screen. Opening needs a valid, reachable offer. */
    public static void screen(ServerPlayer player, int entityId, boolean open) {
        Optional<NpcEntity> giver = giver(player, entityId);
        if (giver.isEmpty()) {
            return;
        }
        NpcEntity npc = giver.get();
        if (!open) {
            OfferViewers.remove(npc.getUUID(), player.getUUID());
            return;
        }
        Optional<QuestGiverData.Offer> offer = offerOf(npc);
        if (offer.isPresent()
                && player.distanceToSqr(npc) <= REACH * REACH
                && offer.get().forPlayer().map(player.getUUID()::equals).orElse(true)
                && QuestGivers.eligible(npc).isPresent()) {
            OfferViewers.add(npc.getUUID(), player.getUUID());
        }
    }

    /** C2S {@code decline_quest}: the screen closes, the offer stays. */
    public static void decline(ServerPlayer player, int entityId) {
        giver(player, entityId).ifPresent(npc -> OfferViewers.remove(npc.getUUID(), player.getUUID()));
    }

    /**
     * C2S {@code accept_quest}: full validation (giver, reach, the offer is still this one and for this player,
     * giver idle, caps and alignment through {@link Quests#canAccept}, accept checks), then the quest starts, the
     * offer goes away, other viewers see {@code taken} and the giver stays in the world.
     */
    public static AcceptOutcome accept(ServerPlayer player, int entityId, ResourceLocation template, int count) {
        Optional<NpcEntity> found = giver(player, entityId);
        if (found.isEmpty()) {
            close(player, entityId, QuestOfferClosedPayload.Reason.GONE);
            return AcceptOutcome.NO_GIVER;
        }
        NpcEntity npc = found.get();
        if (player.distanceToSqr(npc) > REACH * REACH) {
            close(player, entityId, QuestOfferClosedPayload.Reason.TOO_FAR);
            OfferViewers.remove(npc.getUUID(), player.getUUID());
            return AcceptOutcome.TOO_FAR;
        }
        Optional<QuestGiverData.Offer> current = offerOf(npc);
        if (current.isEmpty() || !sameOffer(current.get(), template, count)) {
            close(player, entityId, QuestOfferClosedPayload.Reason.TAKEN);
            player.sendSystemMessage(Component.translatable(OfferDialogue.TAKEN));
            OfferViewers.remove(npc.getUUID(), player.getUUID());
            return AcceptOutcome.TAKEN;
        }
        QuestGiverData.Offer offer = current.get();
        if (offer.forPlayer().isPresent() && !offer.forPlayer().get().equals(player.getUUID())) {
            return AcceptOutcome.NOT_FOR_YOU;
        }
        if (QuestGivers.eligible(npc).isEmpty()) {
            close(player, entityId, QuestOfferClosedPayload.Reason.BUSY);
            OfferViewers.remove(npc.getUUID(), player.getUUID());
            return AcceptOutcome.BUSY;
        }
        Quests.AcceptResult check = Quests.canAccept(player, template, npc.getUUID(), npc.faction().location());
        if (check == Quests.AcceptResult.FACTION_CAP || check == Quests.AcceptResult.TOTAL_CAP) {
            OfferDialogue.say(npc, player, OfferDialogue.TOO_MANY);
            close(player, entityId, QuestOfferClosedPayload.Reason.GONE);
            OfferViewers.remove(npc.getUUID(), player.getUUID());
            return AcceptOutcome.TOO_MANY;
        }
        if (check != Quests.AcceptResult.OK || !CHECKS.stream().allMatch(c -> c.allows(player, npc, offer))) {
            OfferViewers.remove(npc.getUUID(), player.getUUID());
            return AcceptOutcome.REFUSED;
        }
        Quests.AcceptResult started = Quests.accept(
                player,
                template,
                offer.count(),
                npc.getUUID(),
                npc.speakerName(),
                npc.faction().location(),
                Optional.of(GlobalPos.of(npc.level().dimension(), npc.blockPosition())));
        if (started != Quests.AcceptResult.OK) {
            OfferViewers.remove(npc.getUUID(), player.getUUID());
            return AcceptOutcome.REFUSED;
        }
        npc.setPersistenceRequired();
        npc.setData(ProgressionAttachments.QUEST_GIVER, data(npc).withOffer(Optional.empty()));
        closeViewers(npc, Optional.of(player.getUUID()), QuestOfferClosedPayload.Reason.TAKEN);
        OfferViewers.remove(npc.getUUID(), player.getUUID());
        if (npc.level() instanceof ServerLevel level) {
            OfferMarkers.broadcast(level, npc, Optional.empty());
        }
        return AcceptOutcome.ACCEPTED;
    }

    // ------------------------------------------------------------------ giver death

    /**
     * The giver died: fails every holder's quest ({@code giver_died}), clears its offer and marker. Call from the
     * death event only for quest givers.
     */
    public static void onGiverDeath(NpcEntity npc) {
        QuestGiverData data = data(npc);
        if (!data.holders().isEmpty() && npc.getServer() != null) {
            Quests.failQuestsFromGiver(npc.getServer(), npc.getUUID());
        }
        clearOffer(npc, QuestOfferClosedPayload.Reason.GONE);
    }
}
