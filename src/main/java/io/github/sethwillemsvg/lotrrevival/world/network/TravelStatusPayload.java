package io.github.sethwillemsvg.lotrrevival.world.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.api.TravelStatus;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Clientbound: countdown start, each second, and the end (cancelled or arrived). */
public record TravelStatusPayload(TravelStatus.State state, String targetRef, int ticksLeft, Optional<Component> reason)
        implements CustomPacketPayload {
    public static final Type<TravelStatusPayload> TYPE = new Type<>(LOTRRevival.id("world/travel_status"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, TravelStatus.State> STATE_CODEC =
            ByteBufCodecs.idMapper(i -> TravelStatus.State.values()[Math.floorMod(i, TravelStatus.State.values().length)],
                    TravelStatus.State::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, TravelStatusPayload> STREAM_CODEC = StreamCodec.composite(
            STATE_CODEC,
            TravelStatusPayload::state,
            ByteBufCodecs.stringUtf8(CoreNetwork.MAX_STRING),
            TravelStatusPayload::targetRef,
            ByteBufCodecs.VAR_INT,
            TravelStatusPayload::ticksLeft,
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC),
            TravelStatusPayload::reason,
            TravelStatusPayload::new);

    public TravelStatus toStatus() {
        return new TravelStatus(state, targetRef, ticksLeft, reason);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
