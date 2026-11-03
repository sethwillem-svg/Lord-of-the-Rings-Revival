package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: an NPC's held hit has landed on its target; the swing freezes for ticks (combat.Hitstop). */
public record NpcHitPayload(int entityId, int targetId, int ticks) implements CustomPacketPayload {
    public static final Type<NpcHitPayload> TYPE = new Type<>(LotrRevival.id("npc_hit"));
    public static final StreamCodec<ByteBuf, NpcHitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcHitPayload::entityId,
            ByteBufCodecs.VAR_INT, NpcHitPayload::targetId,
            ByteBufCodecs.VAR_INT, NpcHitPayload::ticks,
            NpcHitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
