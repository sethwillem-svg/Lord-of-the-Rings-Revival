package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipView;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code lotrrevival:progression/fellowships_delta}: fellowships that changed (added or replaced by id), fellowships
 * the viewer is no longer in, and the whole current invite list.
 */
public record FellowshipsDeltaPayload(
        List<FellowshipView.Entry> upserted, List<UUID> removed, List<FellowshipView.Invite> invites)
        implements CustomPacketPayload {
    public static final Type<FellowshipsDeltaPayload> TYPE = new Type<>(LOTRRevival.id("progression/fellowships_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FellowshipsDeltaPayload> STREAM_CODEC = StreamCodec.composite(
            FellowshipView.ENTRIES,
            FellowshipsDeltaPayload::upserted,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION)),
            FellowshipsDeltaPayload::removed,
            FellowshipView.INVITES,
            FellowshipsDeltaPayload::invites,
            FellowshipsDeltaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
