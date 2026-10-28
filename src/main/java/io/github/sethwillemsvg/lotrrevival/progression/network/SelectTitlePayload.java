package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound: choose a title (or none), a chat colour name and the feminine preference. The server validates
 * all of it and drops the request silently when anything is off.
 */
public record SelectTitlePayload(Optional<ResourceLocation> title, String color, boolean feminine)
        implements CustomPacketPayload {
    public static final Type<SelectTitlePayload> TYPE = new Type<>(LOTRRevival.id("progression/select_title"));

    public static final StreamCodec<ByteBuf, SelectTitlePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            SelectTitlePayload::title,
            ByteBufCodecs.stringUtf8(32),
            SelectTitlePayload::color,
            ByteBufCodecs.BOOL,
            SelectTitlePayload::feminine,
            SelectTitlePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
