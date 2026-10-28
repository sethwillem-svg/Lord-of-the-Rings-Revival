package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** State of a quest in the active list. */
public enum QuestStatus implements StringRepresentable {
    ACTIVE("active"),
    READY_TO_TURN_IN("ready_to_turn_in"),
    FAILED("failed");

    public static final Codec<QuestStatus> CODEC = StringRepresentable.fromEnum(QuestStatus::values);

    private final String id;

    QuestStatus(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
