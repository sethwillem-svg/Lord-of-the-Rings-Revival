package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/** Dispatch table for reward types, same scheme as {@link QuestObjectives}. */
public final class QuestRewardTypes {
    private static final Map<ResourceLocation, MapCodec<? extends QuestReward>> TYPES = Map.of(
            QuestReward.Alignment.TYPE, QuestReward.Alignment.MAP_CODEC,
            QuestReward.Coins.TYPE, QuestReward.Coins.MAP_CODEC,
            QuestReward.ItemTable.TYPE, QuestReward.ItemTable.MAP_CODEC,
            QuestReward.HireGiver.TYPE, QuestReward.HireGiver.MAP_CODEC,
            QuestReward.LoreBook.TYPE, QuestReward.LoreBook.MAP_CODEC);

    private static final Codec<ResourceLocation> TYPE_CODEC = ResourceLocation.CODEC.comapFlatMap(
            id -> TYPES.containsKey(id)
                    ? DataResult.success(id)
                    : DataResult.error(() -> "Unknown quest reward type: " + id),
            Function.identity());

    public static final Codec<QuestReward> CODEC =
            TYPE_CODEC.dispatch("type", QuestReward::type, id -> cast(TYPES.get(id)));

    private QuestRewardTypes() {}

    @SuppressWarnings("unchecked")
    private static MapCodec<QuestReward> cast(MapCodec<? extends QuestReward> codec) {
        return (MapCodec<QuestReward>) codec;
    }
}
