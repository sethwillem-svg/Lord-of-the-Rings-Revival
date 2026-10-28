package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * A finished quest in the log: completed, failed-and-removed, or abandoned.
 *
 * @param faction giver faction id
 * @param at game time it ended
 */
public record QuestRecord(
        ResourceLocation template,
        Component giverName,
        ResourceLocation faction,
        QuestOutcome outcome,
        Optional<FailReason> failReason,
        long at,
        QuestRewardSummary rewards) {
    public static final Codec<QuestRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.fieldOf("template").forGetter(QuestRecord::template),
                    ComponentSerialization.CODEC.fieldOf("giver_name").forGetter(QuestRecord::giverName),
                    ResourceLocation.CODEC.fieldOf("faction").forGetter(QuestRecord::faction),
                    QuestOutcome.CODEC.fieldOf("outcome").forGetter(QuestRecord::outcome),
                    FailReason.CODEC.optionalFieldOf("fail_reason").forGetter(QuestRecord::failReason),
                    Codec.LONG.fieldOf("at").forGetter(QuestRecord::at),
                    QuestRewardSummary.CODEC
                            .optionalFieldOf("rewards", QuestRewardSummary.NONE)
                            .forGetter(QuestRecord::rewards))
            .apply(i, QuestRecord::new));

    /** Record for an instance that ended without rewards (failed or abandoned). */
    public static QuestRecord ended(QuestInstance instance, QuestOutcome outcome, long gameTime) {
        return new QuestRecord(
                instance.template(),
                instance.giverName(),
                instance.giverFaction(),
                outcome,
                outcome == QuestOutcome.FAILED ? instance.failReason() : Optional.empty(),
                gameTime,
                QuestRewardSummary.NONE);
    }
}
