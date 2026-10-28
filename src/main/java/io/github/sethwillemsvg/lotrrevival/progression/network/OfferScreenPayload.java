package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serverbound: the offer screen of a giver was opened or closed (pauses the offer's lifetime while open). */
public record OfferScreenPayload(int entityId, boolean open) implements CustomPacketPayload {
    public static final Type<OfferScreenPayload> TYPE = new Type<>(LOTRRevival.id("progression/offer_screen"));

    public static final StreamCodec<ByteBuf, OfferScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            OfferScreenPayload::entityId,
            ByteBufCodecs.BOOL,
            OfferScreenPayload::open,
            OfferScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
