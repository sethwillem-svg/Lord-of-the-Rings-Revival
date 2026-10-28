package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The mode of a command horn. A horn without the mode component is {@link #BLANK}. */
public enum HornMode implements StringRepresentable {
    BLANK,
    HALT,
    READY,
    SUMMON;

    public static final Codec<HornMode> CODEC = StringRepresentable.fromEnum(HornMode::values);
    public static final StreamCodec<ByteBuf, HornMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)], HornMode::ordinal);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Lang key of the mode name. */
    public String langKey() {
        return "item.lotrrevival.command_horn.mode." + getSerializedName();
    }

    /** The mode the horn switches to after it was blown: halt and ready swap, the others stay. */
    public HornMode afterUse() {
        return switch (this) {
            case HALT -> READY;
            case READY -> HALT;
            default -> this;
        };
    }
}
