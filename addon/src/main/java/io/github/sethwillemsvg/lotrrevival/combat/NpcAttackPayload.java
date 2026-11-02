package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: an NPC starts an attack clip, played at speed so that its hit frame lands when the server deals damage. */
public record NpcAttackPayload(int entityId, String slot, float speed) implements CustomPacketPayload {
    public static final Type<NpcAttackPayload> TYPE = new Type<>(LotrRevival.id("npc_attack"));
    public static final StreamCodec<ByteBuf, NpcAttackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcAttackPayload::entityId,
            ByteBufCodecs.STRING_UTF8, NpcAttackPayload::slot,
            ByteBufCodecs.FLOAT, NpcAttackPayload::speed,
            NpcAttackPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
