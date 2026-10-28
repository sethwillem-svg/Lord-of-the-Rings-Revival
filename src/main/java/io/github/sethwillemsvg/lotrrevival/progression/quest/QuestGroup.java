package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * A quest group: an entry of the synced datapack registry {@code lotrrevival:quest_group}.
 *
 * @param faction the faction of the givers; caps and statistics use the giver faction snapshot, not this
 * @param rewardFaction faction that receives the alignment; empty means {@code faction}
 * @param skipAlignmentIfEnemy no alignment reward when the player's alignment with the reward faction is negative
 * @param minAlignment alignment with {@code faction} needed to accept
 * @param offerRateMultiplier multiplies the offer roll rate (used by the offers ticket)
 * @param color indicator and tracker tint, packed 0xRRGGBB
 * @param firstCompletionAchievement achievement id granted on the first completion (awarded by the achievement system)
 * @param loreCategories reserved for lore book rewards
 */
public record QuestGroup(
        ResourceKey<Faction> faction,
        Optional<ResourceKey<Faction>> rewardFaction,
        boolean skipAlignmentIfEnemy,
        float minAlignment,
        float offerRateMultiplier,
        int color,
        Optional<ResourceLocation> firstCompletionAchievement,
        List<ResourceLocation> loreCategories) {
    /** {@code lotrrevival:quest_group}, files at {@code data/<ns>/lotrrevival/quest_group/<name>.json}. */
    public static final ResourceKey<Registry<QuestGroup>> REGISTRY = ModDataRegistries.questGroup();

    /** JSON codec; also the network codec of the synced registry. */
    public static final Codec<QuestGroup> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceKey.codec(ModDataRegistries.FACTION).fieldOf("faction").forGetter(QuestGroup::faction),
                    ResourceKey.codec(ModDataRegistries.FACTION)
                            .optionalFieldOf("reward_faction")
                            .forGetter(QuestGroup::rewardFaction),
                    Codec.BOOL
                            .optionalFieldOf("skip_alignment_if_enemy", false)
                            .forGetter(QuestGroup::skipAlignmentIfEnemy),
                    Codec.FLOAT.optionalFieldOf("min_alignment", 0.0f).forGetter(QuestGroup::minAlignment),
                    Codec.floatRange(0.0f, 1000.0f)
                            .optionalFieldOf("offer_rate_multiplier", 1.0f)
                            .forGetter(QuestGroup::offerRateMultiplier),
                    Faction.COLOR_CODEC.fieldOf("color").forGetter(QuestGroup::color),
                    ResourceLocation.CODEC
                            .optionalFieldOf("first_completion_achievement")
                            .forGetter(QuestGroup::firstCompletionAchievement),
                    ResourceLocation.CODEC
                            .listOf()
                            .optionalFieldOf("lore_categories", List.of())
                            .forGetter(QuestGroup::loreCategories))
            .apply(i, QuestGroup::new));

    /** The faction that receives the alignment reward. */
    public ResourceKey<Faction> effectiveRewardFaction() {
        return rewardFaction.orElse(faction);
    }
}
