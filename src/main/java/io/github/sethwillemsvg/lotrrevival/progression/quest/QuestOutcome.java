package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How a quest ended, as stored in the log. */
public enum QuestOutcome implements StringRepresentable {
    COMPLETED("completed"),
    FAILED("failed"),
    ABANDONED("abandoned");

    public static final Codec<QuestOutcome> CODEC = StringRepresentable.fromEnum(QuestOutcome::values);

    private final String id;

    QuestOutcome(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
