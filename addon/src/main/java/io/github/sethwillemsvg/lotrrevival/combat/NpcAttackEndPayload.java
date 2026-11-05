package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: an NPC's held swing is over (clears its marker); PARRIED also plays the attacker's stagger clip. */
public record NpcAttackEndPayload(int entityId, byte reason) implements CustomPacketPayload {
    public enum Reason { HIT, MISSED, PARRIED, CANCELLED }

    public static final Type<NpcAttackEndPayload> TYPE = new Type<>(LotrRevival.id("npc_attack_end"));
    public static final StreamCodec<ByteBuf, NpcAttackEndPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcAttackEndPayload::entityId,
            ByteBufCodecs.BYTE, NpcAttackEndPayload::reason,
            NpcAttackEndPayload::new);

    public static NpcAttackEndPayload of(int entityId, Reason reason) {
        return new NpcAttackEndPayload(entityId, (byte) reason.ordinal());
    }

    public Reason reasonValue() {
        Reason[] all = Reason.values();
        return reason >= 0 && reason < all.length ? all[reason] : Reason.CANCELLED;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
