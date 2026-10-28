package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * What a completed quest paid, stored in the {@link QuestRecord}.
 *
 * @param alignment alignment rolled for the reward faction (0 when it was skipped)
 * @param coins coins rolled
 * @param items extra item stacks given
 * @param hired whether the giver joined the player as a hired unit
 */
public record QuestRewardSummary(float alignment, int coins, List<ItemStack> items, boolean hired) {
    public static final QuestRewardSummary NONE = new QuestRewardSummary(0.0f, 0, List.of(), false);

    public static final Codec<QuestRewardSummary> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.FLOAT.optionalFieldOf("alignment", 0.0f).forGetter(QuestRewardSummary::alignment),
                    Codec.INT.optionalFieldOf("coins", 0).forGetter(QuestRewardSummary::coins),
                    ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(QuestRewardSummary::items),
                    Codec.BOOL.optionalFieldOf("hired", false).forGetter(QuestRewardSummary::hired))
            .apply(i, QuestRewardSummary::new));

    public QuestRewardSummary {
        items = List.copyOf(items);
    }
}
