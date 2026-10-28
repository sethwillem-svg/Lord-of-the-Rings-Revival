package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@code lotrrevival:progression/map_viewing}, serverbound: the client reports whether the map screen is open. */
public record MapViewingPayload(boolean open) implements CustomPacketPayload {
    public static final Type<MapViewingPayload> TYPE = new Type<>(LOTRRevival.id("progression/map_viewing"));

    public static final StreamCodec<ByteBuf, MapViewingPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(MapViewingPayload::new, MapViewingPayload::open);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
