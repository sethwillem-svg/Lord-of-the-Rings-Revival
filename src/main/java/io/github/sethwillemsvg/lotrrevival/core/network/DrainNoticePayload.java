package io.github.sethwillemsvg.lotrrevival.core.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Short client notice after a mortal-enemy drain: how many factions lost alignment. */
public record DrainNoticePayload(int factions) implements CustomPacketPayload {
    public static final Type<DrainNoticePayload> TYPE = new Type<>(LOTRRevival.id("core/drain_notice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DrainNoticePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, DrainNoticePayload::factions, DrainNoticePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
