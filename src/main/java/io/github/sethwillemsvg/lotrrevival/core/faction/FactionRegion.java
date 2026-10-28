package io.github.sethwillemsvg.lotrrevival.core.faction;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Map region a faction belongs to. */
public enum FactionRegion implements StringRepresentable {
    WESTLANDS("westlands"),
    RHUN("rhun"),
    HARAD("harad"),
    DEEPS("deeps");

    public static final Codec<FactionRegion> CODEC = StringRepresentable.fromEnum(FactionRegion::values);

    private final String serializedName;

    FactionRegion(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
