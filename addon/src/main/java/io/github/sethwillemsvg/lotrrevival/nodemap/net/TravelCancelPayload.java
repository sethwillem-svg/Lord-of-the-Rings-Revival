package io.github.sethwillemsvg.lotrrevival.nodemap.net;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the Cancel button in the HUD bar or the panel. */
public record TravelCancelPayload() implements CustomPacketPayload {
    public static final TravelCancelPayload INSTANCE = new TravelCancelPayload();
    public static final Type<TravelCancelPayload> TYPE = new Type<>(LotrRevival.id("travel_cancel"));
    public static final StreamCodec<ByteBuf, TravelCancelPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
