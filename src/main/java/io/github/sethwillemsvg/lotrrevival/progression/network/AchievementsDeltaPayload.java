package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Earned ids added and removed since the last sync, clientbound. At most one per player per tick. */
public record AchievementsDeltaPayload(List<ResourceLocation> added, List<ResourceLocation> removed)
        implements CustomPacketPayload {
    public static final Type<AchievementsDeltaPayload> TYPE =
            new Type<>(LOTRRevival.id("progression/achievements_delta"));

    private static final StreamCodec<ByteBuf, List<ResourceLocation>> IDS =
            CoreNetwork.ID_CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION));

    public static final StreamCodec<ByteBuf, AchievementsDeltaPayload> STREAM_CODEC = StreamCodec.composite(
            IDS,
            AchievementsDeltaPayload::added,
            IDS,
            AchievementsDeltaPayload::removed,
            AchievementsDeltaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
