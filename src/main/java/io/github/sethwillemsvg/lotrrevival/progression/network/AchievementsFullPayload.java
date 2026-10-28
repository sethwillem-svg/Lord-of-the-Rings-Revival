package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The player's earned achievement ids, clientbound. A large set is split over several payloads (see
 * {@link AchievementSync#split}); the client collects the parts and swaps its set in when {@code last} arrives.
 */
public record AchievementsFullPayload(List<ResourceLocation> ids, boolean last) implements CustomPacketPayload {
    public static final Type<AchievementsFullPayload> TYPE =
            new Type<>(LOTRRevival.id("progression/achievements_full"));

    public static final StreamCodec<ByteBuf, AchievementsFullPayload> STREAM_CODEC = StreamCodec.composite(
            CoreNetwork.ID_CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION)),
            AchievementsFullPayload::ids,
            ByteBufCodecs.BOOL,
            AchievementsFullPayload::last,
            AchievementsFullPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
