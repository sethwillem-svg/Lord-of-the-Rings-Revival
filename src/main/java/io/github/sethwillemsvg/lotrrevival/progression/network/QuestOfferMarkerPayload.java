package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound: the offer marker over a giver appeared, changed or went away. {@code group} is the quest group (its
 * colour tints the marker), empty when the giver has no offer any more. {@code forPlayer} is set for private offers.
 */
public record QuestOfferMarkerPayload(int entityId, Optional<ResourceLocation> group, Optional<UUID> forPlayer)
        implements CustomPacketPayload {
    public static final Type<QuestOfferMarkerPayload> TYPE =
            new Type<>(LOTRRevival.id("progression/quest_offer_marker"));

    public static final StreamCodec<ByteBuf, QuestOfferMarkerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            QuestOfferMarkerPayload::entityId,
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            QuestOfferMarkerPayload::group,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
            QuestOfferMarkerPayload::forPlayer,
            QuestOfferMarkerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
