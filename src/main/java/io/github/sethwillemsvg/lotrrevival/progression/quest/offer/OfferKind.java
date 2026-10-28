package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestObjective;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestTemplate;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * A kind of quest offer the roll can pick: a weight, the objectives it covers and how to turn a chosen template
 * into an offer. The bounty offers ticket registers its own kind in {@link OfferKinds}.
 */
public interface OfferKind {
    /** What a kind needs to build an offer. */
    record Context(ServerLevel level, NpcEntity giver, QuestGivers.GroupMatch group, RandomSource random) {}

    /** Stable id, also the dialogue category (for example {@code collect}). */
    String id();

    /** Relative weight at the roll, among the kinds the group has templates for. */
    int weight();

    /** Whether a template with this objective belongs to the kind. */
    boolean matches(QuestObjective objective);

    /**
     * Builds the offer for a template the roll picked.
     *
     * @return empty when no offer can be made (for example a bounty without a valid target): the roll retries
     */
    default Optional<QuestGiverData.Offer> generate(
            Context context, ResourceLocation templateId, QuestTemplate template) {
        int count = template.objective().count().roll(context.random());
        return Optional.of(new QuestGiverData.Offer(
                templateId,
                count,
                template.alignmentBase(count),
                template.coinBase(count),
                QuestGiverData.OFFER_TICKS,
                Optional.empty()));
    }
}
