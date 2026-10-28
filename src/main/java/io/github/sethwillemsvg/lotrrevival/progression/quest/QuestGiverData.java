package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * Value of the entity attachment {@code lotrrevival:quest_giver}. Offers are rolled by the later offers ticket; for
 * now only {@code holders} is maintained, as quests start and end.
 *
 * @param offer the current offer, if any
 * @param holders players with an active quest from this giver
 */
public record QuestGiverData(Optional<Offer> offer, Set<UUID> holders) {
    public static final QuestGiverData EMPTY = new QuestGiverData(Optional.empty(), Set.of());

    /** Offer lifetime in ticks. */
    public static final int OFFER_TICKS = 24_000;

    /**
     * A rolled offer: template, count and fixed base values.
     *
     * @param forPlayer set for private (scripted) offers
     * @param bountyTarget the hunted player of a bounty offer (filled by the bounty offers ticket)
     * @param bountyTargetName display name of that player
     */
    public record Offer(
            ResourceLocation template,
            int count,
            float alignmentBase,
            int coinBase,
            int ticksLeft,
            Optional<UUID> forPlayer,
            Optional<UUID> bountyTarget,
            Optional<String> bountyTargetName) {
        public Offer(
                ResourceLocation template,
                int count,
                float alignmentBase,
                int coinBase,
                int ticksLeft,
                Optional<UUID> forPlayer) {
            this(template, count, alignmentBase, coinBase, ticksLeft, forPlayer, Optional.empty(), Optional.empty());
        }

        public Offer withTicksLeft(int ticks) {
            return new Offer(
                    template, count, alignmentBase, coinBase, ticks, forPlayer, bountyTarget, bountyTargetName);
        }

        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("template").forGetter(Offer::template),
                        Codec.INT.fieldOf("count").forGetter(Offer::count),
                        Codec.FLOAT.fieldOf("alignment_base").forGetter(Offer::alignmentBase),
                        Codec.INT.fieldOf("coin_base").forGetter(Offer::coinBase),
                        Codec.INT.optionalFieldOf("ticks_left", OFFER_TICKS).forGetter(Offer::ticksLeft),
                        UUIDUtil.CODEC.optionalFieldOf("for_player").forGetter(Offer::forPlayer),
                        UUIDUtil.CODEC.optionalFieldOf("bounty_target").forGetter(Offer::bountyTarget),
                        Codec.STRING.optionalFieldOf("bounty_target_name").forGetter(Offer::bountyTargetName))
                .apply(i, Offer::new));
    }

    public static final Codec<QuestGiverData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Offer.CODEC.optionalFieldOf("offer").forGetter(QuestGiverData::offer),
                    UUIDUtil.CODEC
                            .listOf()
                            .xmap(list -> (Set<UUID>) new LinkedHashSet<>(list), ArrayList::new)
                            .optionalFieldOf("holders", Set.of())
                            .forGetter(QuestGiverData::holders))
            .apply(i, QuestGiverData::new));

    public QuestGiverData {
        holders = Collections.unmodifiableSet(new LinkedHashSet<>(holders));
    }

    public QuestGiverData withHolder(UUID player, boolean present) {
        Set<UUID> next = new LinkedHashSet<>(holders);
        if (present) {
            next.add(player);
        } else {
            next.remove(player);
        }
        return new QuestGiverData(offer, next);
    }

    /** The same data with another offer (empty clears it). */
    public QuestGiverData withOffer(Optional<Offer> next) {
        return new QuestGiverData(next, holders);
    }
}
