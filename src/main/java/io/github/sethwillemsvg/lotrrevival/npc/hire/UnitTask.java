package io.github.sethwillemsvg.lotrrevival.npc.hire;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What a hired unit does for its owner. Only warriors level up; farmers arrive with farmhands (later). */
public enum UnitTask implements StringRepresentable {
    WARRIOR("warrior"),
    FARMER("farmer");

    public static final Codec<UnitTask> CODEC = StringRepresentable.fromEnum(UnitTask::values);

    private final String serializedName;

    UnitTask(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
