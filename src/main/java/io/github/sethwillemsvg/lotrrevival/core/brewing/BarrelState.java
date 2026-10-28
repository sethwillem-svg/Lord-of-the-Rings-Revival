package io.github.sethwillemsvg.lotrrevival.core.brewing;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** State machine of a brewing barrel: Empty, Brewing, Full. */
public enum BarrelState implements StringRepresentable {
    EMPTY("empty"),
    BREWING("brewing"),
    FULL("full");

    public static final Codec<BarrelState> CODEC = StringRepresentable.fromEnum(BarrelState::values);
    public static final StreamCodec<io.netty.buffer.ByteBuf, BarrelState> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(BarrelState::byOrdinal, BarrelState::ordinal);

    private static final BarrelState[] VALUES = values();

    private final String name;

    BarrelState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static BarrelState byOrdinal(int ordinal) {
        return VALUES[Math.floorMod(ordinal, VALUES.length)];
    }
}
