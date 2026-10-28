package io.github.sethwillemsvg.lotrrevival.core.alignment;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Why a pledge ended. */
public enum PledgeBreakReason implements StringRepresentable {
    VOLUNTARY("voluntary"),
    KILLED_ALLY("killed_ally"),
    BELOW_RANK("below_rank"),
    COMMAND("command");

    public static final Codec<PledgeBreakReason> CODEC = StringRepresentable.fromEnum(PledgeBreakReason::values);

    private final String serializedName;

    PledgeBreakReason(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Command breaks are administrative: no penalty and no cooldown. */
    public boolean hasConsequences() {
        return this != COMMAND;
    }
}
