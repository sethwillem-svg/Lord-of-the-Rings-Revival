package io.github.sethwillemsvg.lotrrevival.nodemap.net;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: charge progress for the HUD. {@code state} is charging, cancelled, arrived or refused. */
public record TravelStatusPayload(String state, String target, int ticksLeft, int total, Component reason) implements CustomPacketPayload {
    public static final Type<TravelStatusPayload> TYPE = new Type<>(LotrRevival.id("travel_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TravelStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TravelStatusPayload::state,
            ByteBufCodecs.STRING_UTF8, TravelStatusPayload::target,
            ByteBufCodecs.VAR_INT, TravelStatusPayload::ticksLeft,
            ByteBufCodecs.VAR_INT, TravelStatusPayload::total,
            ComponentSerialization.STREAM_CODEC, TravelStatusPayload::reason,
            TravelStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
