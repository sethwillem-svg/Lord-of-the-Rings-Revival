package io.github.sethwillemsvg.lotrrevival.nodemap.net;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: our "open travel map" key; the server answers with Middle-earth's own open-map packet and a node_state. */
public record OpenNodeMapPayload() implements CustomPacketPayload {
    public static final OpenNodeMapPayload INSTANCE = new OpenNodeMapPayload();
    public static final Type<OpenNodeMapPayload> TYPE = new Type<>(LotrRevival.id("open_node_map"));
    public static final StreamCodec<ByteBuf, OpenNodeMapPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
