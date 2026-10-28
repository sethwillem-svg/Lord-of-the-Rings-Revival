package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serverbound: delete one log record. {@code index} counts oldest first; {@code at} is the record's end time, so a
 * log that shifted since the screen was drawn deletes nothing.
 */
public record DeleteQuestRecordPayload(int index, long at) implements CustomPacketPayload {
    public static final Type<DeleteQuestRecordPayload> TYPE =
            new Type<>(LOTRRevival.id("progression/delete_quest_record"));

    public static final StreamCodec<ByteBuf, DeleteQuestRecordPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            DeleteQuestRecordPayload::index,
            ByteBufCodecs.VAR_LONG,
            DeleteQuestRecordPayload::at,
            DeleteQuestRecordPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
