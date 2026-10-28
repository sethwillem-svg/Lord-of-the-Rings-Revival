package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound: the player takes the giver's current offer. The template and count must match the offer the
 * server holds, so a stale screen cannot act on a different quest. The server validates everything else.
 */
public record AcceptQuestPayload(int entityId, ResourceLocation template, int count) implements CustomPacketPayload {
    public static final Type<AcceptQuestPayload> TYPE = new Type<>(LOTRRevival.id("progression/accept_quest"));

    public static final StreamCodec<ByteBuf, AcceptQuestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            AcceptQuestPayload::entityId,
            CoreNetwork.ID_CODEC,
            AcceptQuestPayload::template,
            ByteBufCodecs.VAR_INT,
            AcceptQuestPayload::count,
            AcceptQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
