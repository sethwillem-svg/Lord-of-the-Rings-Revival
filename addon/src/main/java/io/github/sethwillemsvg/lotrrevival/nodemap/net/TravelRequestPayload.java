package io.github.sethwillemsvg.lotrrevival.nodemap.net;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the Travel button, with the destination node ref ({@code fixed:ns:path} or {@code camp:N}). */
public record TravelRequestPayload(String target) implements CustomPacketPayload {
    public static final Type<TravelRequestPayload> TYPE = new Type<>(LotrRevival.id("travel_request"));
    public static final StreamCodec<ByteBuf, TravelRequestPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(256).map(TravelRequestPayload::new, TravelRequestPayload::target);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
