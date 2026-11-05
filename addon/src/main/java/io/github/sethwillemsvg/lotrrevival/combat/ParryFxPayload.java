package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client (trackers of the defender, and the defender): play the defender's parry clip or the player's guard flash. */
public record ParryFxPayload(int defenderId, byte kind) implements CustomPacketPayload {
    public enum Kind { PERFECT, GLANCE, NPC_PARRY }

    public static final Type<ParryFxPayload> TYPE = new Type<>(LotrRevival.id("parry_fx"));
    public static final StreamCodec<ByteBuf, ParryFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ParryFxPayload::defenderId,
            ByteBufCodecs.BYTE, ParryFxPayload::kind,
            ParryFxPayload::new);

    public static ParryFxPayload of(int defenderId, Kind kind) {
        return new ParryFxPayload(defenderId, (byte) kind.ordinal());
    }

    public Kind kindValue() {
        Kind[] all = Kind.values();
        return kind >= 0 && kind < all.length ? all[kind] : Kind.PERFECT;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
