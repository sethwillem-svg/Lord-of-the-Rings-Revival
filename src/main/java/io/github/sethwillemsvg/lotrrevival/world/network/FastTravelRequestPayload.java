package io.github.sethwillemsvg.lotrrevival.world.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serverbound: the player asks to fast travel to a waypoint ({@code fixed:<id>} or {@code custom:<n>}). */
public record FastTravelRequestPayload(String ref) implements CustomPacketPayload {
    public static final Type<FastTravelRequestPayload> TYPE = new Type<>(LOTRRevival.id("world/fast_travel_request"));

    public static final StreamCodec<ByteBuf, FastTravelRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(CoreNetwork.MAX_STRING), FastTravelRequestPayload::ref, FastTravelRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
