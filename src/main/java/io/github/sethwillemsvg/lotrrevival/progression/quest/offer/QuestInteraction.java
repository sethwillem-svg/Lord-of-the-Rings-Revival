package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/**
 * The talk interaction chain of a quest giver: (1) reserved pickpocket, (2) the player's own quest from this giver,
 * (3) the offer, (4) reserved bounty hint. The first step that applies takes over; the caller cancels the talk event.
 */
public final class QuestInteraction {
    /** A reserved step of the chain; returns true when it took over. */
    @FunctionalInterface
    public interface Step {
        boolean handle(NpcEntity npc, ServerPlayer player);
    }

    private static final Step NONE = (npc, player) -> false;

    private static volatile Step pickpocket = NONE;
    private static volatile Step bountyHint = NONE;

    private QuestInteraction() {}

    /** Reserved slot 1 (a pickpocket quest from another giver that targets this NPC). */
    public static void setPickpocketStep(Step step) {
        pickpocket = step == null ? NONE : step;
    }

    /** Reserved slot 4 (1 in 3 chance of a bounty hint). */
    public static void setBountyHintStep(Step step) {
        bountyHint = step == null ? NONE : step;
    }

    /**
     * Runs the chain.
     *
     * @return true when a step took over (the NPC's ordinary speech should be suppressed)
     */
    public static boolean handle(NpcEntity npc, ServerPlayer player) {
        if (!ProgressionConfig.get(ProgressionConfig.MINI_QUESTS_ENABLED)) {
            return false;
        }
        if (pickpocket.handle(npc, player)) {
            return true;
        }
        if (ownQuest(npc, player)) {
            return true;
        }
        if (offer(npc, player)) {
            return true;
        }
        return bountyHint.handle(npc, player);
    }

    /** Step 2: turn in, progress line, track. */
    private static boolean ownQuest(NpcEntity npc, ServerPlayer player) {
        Optional<QuestInstance> own = Quests.get(player).active().stream()
                .filter(q -> q.live() && q.giver().equals(npc.getUUID()))
                .findFirst();
        if (own.isEmpty()) {
            return false;
        }
        QuestInstance quest = own.get();
        Quests.TurnIn result = Quests.turnIn(player, quest.uuid(), npc.getRandom());
        if (result.completed()) {
            OfferDialogue.say(npc, player, OfferDialogue.thanksKey(npc.getRandom()));
            return true;
        }
        Quests.track(player, Optional.of(quest.uuid()));
        int remaining = Quests.get(player)
                .find(quest.uuid())
                .map(q -> Math.max(0, q.count() - q.progress()))
                .orElse(0);
        OfferDialogue.say(npc, player, OfferDialogue.progressKey(npc.getRandom()), remaining);
        return true;
    }

    /** Step 3: open the offer screen, or the "too many" line at the caps. */
    private static boolean offer(NpcEntity npc, ServerPlayer player) {
        Optional<QuestGiverData.Offer> found = OfferService.offerOf(npc);
        if (found.isEmpty()) {
            return false;
        }
        QuestGiverData.Offer offer = found.get();
        if (offer.forPlayer().isPresent() && !offer.forPlayer().get().equals(player.getUUID())) {
            return false;
        }
        if (QuestGivers.eligible(npc).isEmpty()) {
            return false;
        }
        Quests.AcceptResult result =
                Quests.canAccept(player, offer.template(), npc.getUUID(), npc.faction().location());
        if (result == Quests.AcceptResult.FACTION_CAP || result == Quests.AcceptResult.TOTAL_CAP) {
            OfferDialogue.say(npc, player, OfferDialogue.TOO_MANY);
            return true;
        }
        if (result != Quests.AcceptResult.OK) {
            return false;
        }
        OfferService.openScreen(player, npc, offer);
        return true;
    }
}
