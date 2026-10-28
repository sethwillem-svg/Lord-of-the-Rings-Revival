package io.github.sethwillemsvg.lotrrevival.core.forge;

import net.minecraft.util.StringRepresentable;

/** The forge blocks that share one block class, block entity and menu. Later rows (dwarven, elven) add values. */
public enum ForgeVariant implements StringRepresentable {
    ALLOY("alloy_forge"),
    ORC("orc_forge");

    private final String id;

    ForgeVariant(String id) {
        this.id = id;
    }

    /** Registry name of the block and its item. */
    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
