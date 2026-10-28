package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipView;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@code lotrrevival:progression/fellowships_full}: every fellowship the viewer is in, and their pending invites. */
public record FellowshipsFullPayload(List<FellowshipView.Entry> fellowships, List<FellowshipView.Invite> invites)
        implements CustomPacketPayload {
    public static final Type<FellowshipsFullPayload> TYPE = new Type<>(LOTRRevival.id("progression/fellowships_full"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FellowshipsFullPayload> STREAM_CODEC = StreamCodec.composite(
            FellowshipView.ENTRIES,
            FellowshipsFullPayload::fellowships,
            FellowshipView.INVITES,
            FellowshipsFullPayload::invites,
            FellowshipsFullPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
