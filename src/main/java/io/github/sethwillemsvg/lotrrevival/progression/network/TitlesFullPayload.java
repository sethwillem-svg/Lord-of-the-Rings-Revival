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

/**
 * The player's whole title state, clientbound, sent on login, respawn and dimension change. Also carries the
 * server's {@code titlesEnabled} and {@code strictFactionTitles} so the client can hide the page and judge
 * usability the way the server does.
 */
public record TitlesFullPayload(
        boolean enabled,
        boolean strict,
        Optional<ResourceLocation> selected,
        String color,
        boolean feminine,
        List<ResourceLocation> granted)
        implements CustomPacketPayload {
    public static final Type<TitlesFullPayload> TYPE = new Type<>(LOTRRevival.id("progression/titles_full"));

    public static final StreamCodec<ByteBuf, TitlesFullPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            TitlesFullPayload::enabled,
            ByteBufCodecs.BOOL,
            TitlesFullPayload::strict,
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            TitlesFullPayload::selected,
            ByteBufCodecs.stringUtf8(32),
            TitlesFullPayload::color,
            ByteBufCodecs.BOOL,
            TitlesFullPayload::feminine,
            CoreNetwork.ID_CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION)),
            TitlesFullPayload::granted,
            TitlesFullPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
