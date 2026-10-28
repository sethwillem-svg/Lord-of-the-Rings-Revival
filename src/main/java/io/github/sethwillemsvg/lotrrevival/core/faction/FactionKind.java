package io.github.sethwillemsvg.lotrrevival.core.faction;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Grouping kind of a faction (tag {@code lotrrevival:kind/<kind>}). */
public enum FactionKind implements StringRepresentable {
    FREE("free"),
    MEN("men"),
    ELF("elf"),
    DWARF("dwarf"),
    ORC("orc"),
    TROLL("troll"),
    TREE("tree");

    public static final Codec<FactionKind> CODEC = StringRepresentable.fromEnum(FactionKind::values);

    private final String serializedName;

    FactionKind(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
