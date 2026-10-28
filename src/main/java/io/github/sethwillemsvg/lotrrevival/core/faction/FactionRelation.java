package io.github.sethwillemsvg.lotrrevival.core.faction;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The five symmetric relation levels. Pairs that are not listed are {@link #NEUTRAL}. */
public enum FactionRelation implements StringRepresentable {
    ALLY("ally"),
    FRIEND("friend"),
    NEUTRAL("neutral"),
    ENEMY("enemy"),
    MORTAL_ENEMY("mortal_enemy");

    public static final Codec<FactionRelation> CODEC = StringRepresentable.fromEnum(FactionRelation::values);

    private final String serializedName;

    FactionRelation(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Ally or friend. */
    public boolean isGood() {
        return this == ALLY || this == FRIEND;
    }

    /** Enemy or mortal enemy. */
    public boolean isBad() {
        return this == ENEMY || this == MORTAL_ENEMY;
    }
}
