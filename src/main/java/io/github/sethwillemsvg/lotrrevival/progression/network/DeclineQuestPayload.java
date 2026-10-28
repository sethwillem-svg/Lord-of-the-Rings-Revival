package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound: the player declines the giver's current offer. The template and count must match the offer the
 * server holds, so a stale screen cannot act on a different quest. The server validates everything else.
 */
public record DeclineQuestPayload(int entityId, ResourceLocation template, int count) implements CustomPacketPayload {
    public static final Type<DeclineQuestPayload> TYPE = new Type<>(LOTRRevival.id("progression/decline_quest"));

    public static final StreamCodec<ByteBuf, DeclineQuestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            DeclineQuestPayload::entityId,
            CoreNetwork.ID_CODEC,
            DeclineQuestPayload::template,
            ByteBufCodecs.VAR_INT,
            DeclineQuestPayload::count,
            DeclineQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
