package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serverbound: abandon one live quest. Unknown or failed quests are ignored. */
public record AbandonQuestPayload(UUID quest) implements CustomPacketPayload {
    public static final Type<AbandonQuestPayload> TYPE = new Type<>(LOTRRevival.id("progression/abandon_quest"));

    public static final StreamCodec<ByteBuf, AbandonQuestPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(AbandonQuestPayload::new, AbandonQuestPayload::quest);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
