package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serverbound: remove one failed quest from the active list into the log. Other quests are ignored. */
public record RemoveQuestPayload(UUID quest) implements CustomPacketPayload {
    public static final Type<RemoveQuestPayload> TYPE = new Type<>(LOTRRevival.id("progression/remove_quest"));

    public static final StreamCodec<ByteBuf, RemoveQuestPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(RemoveQuestPayload::new, RemoveQuestPayload::quest);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
