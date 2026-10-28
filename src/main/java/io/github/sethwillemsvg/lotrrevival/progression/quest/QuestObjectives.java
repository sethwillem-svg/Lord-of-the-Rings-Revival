package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * Dispatch table for objective types: a plain immutable map from the {@code type} id to its codec (no registry;
 * see the Core spec). The JSON shape is {@code {"type": "lotrrevival:collect", ...fields}}.
 */
public final class QuestObjectives {
    private static final Map<ResourceLocation, MapCodec<? extends QuestObjective>> TYPES = Map.of(
            QuestObjective.Collect.TYPE, QuestObjective.Collect.MAP_CODEC,
            QuestObjective.KillEntity.TYPE, QuestObjective.KillEntity.MAP_CODEC,
            QuestObjective.KillFaction.TYPE, QuestObjective.KillFaction.MAP_CODEC,
            QuestObjective.Pickpocket.TYPE, QuestObjective.Pickpocket.MAP_CODEC,
            QuestObjective.Bounty.TYPE, QuestObjective.Bounty.MAP_CODEC,
            QuestObjective.Scripted.TYPE, QuestObjective.Scripted.MAP_CODEC);

    private static final Codec<ResourceLocation> TYPE_CODEC = ResourceLocation.CODEC.comapFlatMap(
            id -> TYPES.containsKey(id)
                    ? DataResult.success(id)
                    : DataResult.error(() -> "Unknown quest objective type: " + id),
            Function.identity());

    public static final Codec<QuestObjective> CODEC =
            TYPE_CODEC.dispatch("type", QuestObjective::type, id -> cast(TYPES.get(id)));

    private QuestObjectives() {}

    @SuppressWarnings("unchecked")
    private static MapCodec<QuestObjective> cast(MapCodec<? extends QuestObjective> codec) {
        return (MapCodec<QuestObjective>) codec;
    }
}
