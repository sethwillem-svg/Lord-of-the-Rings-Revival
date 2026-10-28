package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * One mini-quest template: an entry of the synced datapack registry {@code lotrrevival:mini_quest}. One target
 * with a count range and a reward factor.
 *
 * @param extraRewards added to the base alignment and coins when the quest completes
 */
public record QuestTemplate(
        ResourceKey<QuestGroup> group, QuestObjective objective, float rewardFactor, List<QuestReward> extraRewards) {
    /** {@code lotrrevival:mini_quest}, files at {@code data/<ns>/lotrrevival/mini_quest/<name>.json}. */
    public static final ResourceKey<Registry<QuestTemplate>> REGISTRY = ModDataRegistries.miniQuest();

    /** JSON codec; also the network codec of the synced registry. */
    public static final Codec<QuestTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceKey.codec(QuestGroup.REGISTRY).fieldOf("group").forGetter(QuestTemplate::group),
                    QuestObjectives.CODEC.fieldOf("objective").forGetter(QuestTemplate::objective),
                    Codec.floatRange(0.0f, 10_000.0f).fieldOf("reward_factor").forGetter(QuestTemplate::rewardFactor),
                    QuestRewardTypes.CODEC
                            .listOf()
                            .optionalFieldOf("extra_rewards", List.of())
                            .forGetter(QuestTemplate::extraRewards))
            .apply(i, QuestTemplate::new));

    /** Alignment base for a rolled count. */
    public float alignmentBase(int count) {
        return objective.alignmentBase(count, rewardFactor);
    }

    /** Coin base for a rolled count. */
    public int coinBase(int count) {
        return objective.coinBase(count, rewardFactor, alignmentBase(count));
    }
}
