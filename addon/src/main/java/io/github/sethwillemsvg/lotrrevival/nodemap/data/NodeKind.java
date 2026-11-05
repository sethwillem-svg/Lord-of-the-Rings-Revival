package io.github.sethwillemsvg.lotrrevival.nodemap.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** What a node is; picks its marker icon. {@code CAMP} is for players' own nodes only (never in data). */
public enum NodeKind implements StringRepresentable {
    TOWN, STRONGHOLD, CROSSING, RUIN, LANDMARK, CAMP;

    public static final Codec<NodeKind> CODEC = StringRepresentable.fromEnum(NodeKind::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
