package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server, empty: a blade guard press. The server stamps the tick; the client never claims a time. */
public record ParryPayload() implements CustomPacketPayload {
    public static final ParryPayload INSTANCE = new ParryPayload();
    public static final Type<ParryPayload> TYPE = new Type<>(LotrRevival.id("parry"));
    public static final StreamCodec<ByteBuf, ParryPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
