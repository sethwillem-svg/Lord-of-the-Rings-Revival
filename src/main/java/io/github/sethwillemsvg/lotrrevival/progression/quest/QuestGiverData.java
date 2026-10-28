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
     */
    public record Offer(
            ResourceLocation template,
            int count,
            float alignmentBase,
            int coinBase,
            int ticksLeft,
            Optional<UUID> forPlayer) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("template").forGetter(Offer::template),
                        Codec.INT.fieldOf("count").forGetter(Offer::count),
                        Codec.FLOAT.fieldOf("alignment_base").forGetter(Offer::alignmentBase),
                        Codec.INT.fieldOf("coin_base").forGetter(Offer::coinBase),
                        Codec.INT.optionalFieldOf("ticks_left", OFFER_TICKS).forGetter(Offer::ticksLeft),
                        UUIDUtil.CODEC.optionalFieldOf("for_player").forGetter(Offer::forPlayer))
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
}
