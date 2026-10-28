package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.StringRepresentable;

/** Clientbound: close the offer screen of a giver, with the reason to show. */
public record QuestOfferClosedPayload(int entityId, Reason reason) implements CustomPacketPayload {
    /** Why the screen closes. {@code taken}: someone else accepted; {@code gone}: expired, died or removed. */
    public enum Reason implements StringRepresentable {
        TAKEN("taken"),
        GONE("gone"),
        BUSY("busy"),
        TOO_FAR("too_far");

        private final String id;

        Reason(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        /** Lang key of the line the client shows. */
        public String langKey() {
            return "quest.lotrrevival.offer.closed." + id;
        }
    }

    public static final Type<QuestOfferClosedPayload> TYPE =
            new Type<>(LOTRRevival.id("progression/quest_offer_closed"));

    public static final StreamCodec<ByteBuf, QuestOfferClosedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            QuestOfferClosedPayload::entityId,
            ByteBufCodecs.idMapper(i -> Reason.values()[Math.floorMod(i, Reason.values().length)], Reason::ordinal),
            QuestOfferClosedPayload::reason,
            QuestOfferClosedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
