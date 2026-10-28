package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Asks the client to show the achievement toast for one earned, eligible id. */
public record AchievementToastPayload(ResourceLocation id) implements CustomPacketPayload {
    public static final Type<AchievementToastPayload> TYPE =
            new Type<>(LOTRRevival.id("progression/achievement_toast"));

    public static final StreamCodec<ByteBuf, AchievementToastPayload> STREAM_CODEC =
            CoreNetwork.ID_CODEC.map(AchievementToastPayload::new, AchievementToastPayload::id);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
