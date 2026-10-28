package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serverbound: track one active quest, or none (empty untracks). Unknown quests are ignored. */
public record TrackQuestPayload(Optional<UUID> quest) implements CustomPacketPayload {
    public static final Type<TrackQuestPayload> TYPE = new Type<>(LOTRRevival.id("progression/track_quest"));

    public static final StreamCodec<ByteBuf, TrackQuestPayload> STREAM_CODEC =
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).map(TrackQuestPayload::new, TrackQuestPayload::quest);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
