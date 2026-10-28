package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Why a quest failed. The last three are reserved for bounties and the tutorial. */
public enum FailReason implements StringRepresentable {
    GIVER_DIED("giver_died"),
    INVALID("invalid"),
    CLAIMED("claimed"),
    KILLED_BY_TARGET("killed_by_target"),
    MOVED_ON("moved_on");

    public static final Codec<FailReason> CODEC = StringRepresentable.fromEnum(FailReason::values);

    private final String id;

    FailReason(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Translation key of the player-facing explanation. */
    public String langKey() {
        return "quest.lotrrevival.fail." + id;
    }
}
