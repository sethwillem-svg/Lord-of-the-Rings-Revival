package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.quest.PlayerQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code lotrrevival:progression/quests_delta}: sent once at the end of a tick in which the player's quests
 * changed. The quest state is small and bounded, so a delta replaces the client's whole mirror. If the state ever
 * exceeds one chunk, the sender falls back to {@link QuestsFullPayload}.
 */
public record QuestsDeltaPayload(PlayerQuests state) implements CustomPacketPayload {
    public static final Type<QuestsDeltaPayload> TYPE = new Type<>(LOTRRevival.id("progression/quests_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestsDeltaPayload> STREAM_CODEC =
            PlayerQuests.STREAM_CODEC.map(QuestsDeltaPayload::new, QuestsDeltaPayload::state);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
