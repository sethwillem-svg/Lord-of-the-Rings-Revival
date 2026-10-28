package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * One quest a player holds. Ids are plain resource locations so quests from removed datapacks round-trip and fail
 * {@code invalid} instead of breaking the save.
 *
 * @param uuid identity of this instance
 * @param template mini-quest template id
 * @param group quest group id
 * @param giver the giving NPC
 * @param giverName snapshot of the giver's name
 * @param giverFaction faction id of the giver at the time; caps and statistics use it
 * @param giverPos last known giver position
 * @param givenAt game time the quest was accepted
 * @param givenBiome biome where it was accepted
 * @param count rolled target count
 * @param alignmentBase fixed alignment base
 * @param coinBase fixed coin base
 * @param progress progress toward {@code count}
 * @param state active, ready to turn in or failed
 * @param failReason set once failed
 * @param objectiveData reserved data for pickpocket and bounty objectives
 */
public record QuestInstance(
        UUID uuid,
        ResourceLocation template,
        ResourceLocation group,
        UUID giver,
        Component giverName,
        ResourceLocation giverFaction,
        Optional<GlobalPos> giverPos,
        long givenAt,
        Optional<ResourceLocation> givenBiome,
        int count,
        float alignmentBase,
        int coinBase,
        int progress,
        QuestStatus state,
        Optional<FailReason> failReason,
        ObjectiveData objectiveData) {
    public static final Codec<QuestInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    UUIDUtil.CODEC.fieldOf("uuid").forGetter(QuestInstance::uuid),
                    ResourceLocation.CODEC.fieldOf("template").forGetter(QuestInstance::template),
                    ResourceLocation.CODEC.fieldOf("group").forGetter(QuestInstance::group),
                    UUIDUtil.CODEC.fieldOf("giver").forGetter(QuestInstance::giver),
                    ComponentSerialization.CODEC.fieldOf("giver_name").forGetter(QuestInstance::giverName),
                    ResourceLocation.CODEC.fieldOf("giver_faction").forGetter(QuestInstance::giverFaction),
                    GlobalPos.CODEC.optionalFieldOf("giver_pos").forGetter(QuestInstance::giverPos),
                    Codec.LONG.fieldOf("given_at").forGetter(QuestInstance::givenAt),
                    ResourceLocation.CODEC.optionalFieldOf("given_biome").forGetter(QuestInstance::givenBiome),
                    Codec.INT.fieldOf("count").forGetter(QuestInstance::count),
                    Codec.FLOAT.fieldOf("alignment_base").forGetter(QuestInstance::alignmentBase),
                    Codec.INT.fieldOf("coin_base").forGetter(QuestInstance::coinBase),
                    Codec.INT.optionalFieldOf("progress", 0).forGetter(QuestInstance::progress),
                    QuestStatus.CODEC.optionalFieldOf("state", QuestStatus.ACTIVE).forGetter(QuestInstance::state),
                    FailReason.CODEC.optionalFieldOf("fail_reason").forGetter(QuestInstance::failReason),
                    ObjectiveData.CODEC
                            .optionalFieldOf("objective_data", ObjectiveData.EMPTY)
                            .forGetter(QuestInstance::objectiveData))
            .apply(i, QuestInstance::new));

    public boolean failed() {
        return state == QuestStatus.FAILED;
    }

    /** Counts toward the caps: anything not failed. */
    public boolean live() {
        return state != QuestStatus.FAILED;
    }

    public QuestInstance withProgress(int progress, QuestStatus state) {
        return new QuestInstance(
                uuid, template, group, giver, giverName, giverFaction, giverPos, givenAt, givenBiome, count,
                alignmentBase, coinBase, progress, state, failReason, objectiveData);
    }

    public QuestInstance failed(FailReason reason) {
        return new QuestInstance(
                uuid, template, group, giver, giverName, giverFaction, giverPos, givenAt, givenBiome, count,
                alignmentBase, coinBase, progress, QuestStatus.FAILED, Optional.of(reason), objectiveData);
    }

    public QuestInstance withGiverPos(Optional<GlobalPos> pos) {
        return new QuestInstance(
                uuid, template, group, giver, giverName, giverFaction, pos, givenAt, givenBiome, count,
                alignmentBase, coinBase, progress, state, failReason, objectiveData);
    }
}
