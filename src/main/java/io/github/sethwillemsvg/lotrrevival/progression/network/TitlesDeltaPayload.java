package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** What changed since the last title sync: the choice replaces the old one, granted ids are added and removed. */
public record TitlesDeltaPayload(
        Optional<ResourceLocation> selected,
        String color,
        boolean feminine,
        List<ResourceLocation> grantedAdded,
        List<ResourceLocation> grantedRemoved)
        implements CustomPacketPayload {
    public static final Type<TitlesDeltaPayload> TYPE = new Type<>(LOTRRevival.id("progression/titles_delta"));

    public static final StreamCodec<ByteBuf, TitlesDeltaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            TitlesDeltaPayload::selected,
            ByteBufCodecs.stringUtf8(32),
            TitlesDeltaPayload::color,
            ByteBufCodecs.BOOL,
            TitlesDeltaPayload::feminine,
            CoreNetwork.ID_CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION)),
            TitlesDeltaPayload::grantedAdded,
            CoreNetwork.ID_CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION)),
            TitlesDeltaPayload::grantedRemoved,
            TitlesDeltaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
