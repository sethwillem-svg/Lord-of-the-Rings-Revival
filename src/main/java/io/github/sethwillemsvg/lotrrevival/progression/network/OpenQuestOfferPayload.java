package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound: open the offer screen for a giver. Carries what the screen shows: the template and rolled count
 * (the objective text), the base rewards, the target's name for bounties and the lang key of the opening line.
 */
public record OpenQuestOfferPayload(
        int entityId,
        ResourceLocation template,
        int count,
        float alignmentBase,
        int coinBase,
        Optional<String> bountyTargetName,
        String openingKey)
        implements CustomPacketPayload {
    public static final Type<OpenQuestOfferPayload> TYPE = new Type<>(LOTRRevival.id("progression/open_quest_offer"));

    private static final StreamCodec<ByteBuf, Optional<String>> TARGET_NAME =
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(64));

    public static final StreamCodec<ByteBuf, OpenQuestOfferPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.entityId());
                CoreNetwork.ID_CODEC.encode(buf, payload.template());
                ByteBufCodecs.VAR_INT.encode(buf, payload.count());
                ByteBufCodecs.FLOAT.encode(buf, payload.alignmentBase());
                ByteBufCodecs.VAR_INT.encode(buf, payload.coinBase());
                TARGET_NAME.encode(buf, payload.bountyTargetName());
                ByteBufCodecs.stringUtf8(CoreNetwork.MAX_STRING).encode(buf, payload.openingKey());
            },
            buf -> new OpenQuestOfferPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    CoreNetwork.ID_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    TARGET_NAME.decode(buf),
                    ByteBufCodecs.stringUtf8(CoreNetwork.MAX_STRING).decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
