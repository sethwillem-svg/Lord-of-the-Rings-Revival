package io.github.sethwillemsvg.lotrrevival.npc.entity;

import net.minecraft.util.StringRepresentable;

/** Picks the name bank and voice lines. One model per race. */
public enum NpcGender implements StringRepresentable {
    MALE("male"),
    FEMALE("female");

    private final String serializedName;

    NpcGender(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static java.util.Optional<NpcGender> byName(String name) {
        for (NpcGender gender : values()) {
            if (gender.serializedName.equals(name)) {
                return java.util.Optional.of(gender);
            }
        }
        return java.util.Optional.empty();
    }

    public static NpcGender byOrdinal(int ordinal) {
        NpcGender[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
