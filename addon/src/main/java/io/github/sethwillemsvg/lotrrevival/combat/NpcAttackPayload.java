package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (players tracking the NPC): an NPC starts a held melee swing. slot is the attack clip ("" for none: marker
 * only); windupTicks is swing start to hit frame; targetId is the target's entity id (-1 none); flags bit 0 heavy, bit 1
 * mounted. The client time-warps the clip from windupTicks and the clip's own hit time, and shows the warning marker.
 */
public record NpcAttackPayload(int entityId, String slot, int windupTicks, int targetId, byte flags) implements CustomPacketPayload {
    public static final byte HEAVY = 1, MOUNTED = 2;

    public static final Type<NpcAttackPayload> TYPE = new Type<>(LotrRevival.id("npc_attack"));
    public static final StreamCodec<ByteBuf, NpcAttackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcAttackPayload::entityId,
            ByteBufCodecs.STRING_UTF8, NpcAttackPayload::slot,
            ByteBufCodecs.VAR_INT, NpcAttackPayload::windupTicks,
            ByteBufCodecs.VAR_INT, NpcAttackPayload::targetId,
            ByteBufCodecs.BYTE, NpcAttackPayload::flags,
            NpcAttackPayload::new);

    public static byte flags(boolean heavy, boolean mounted) {
        return (byte) ((heavy ? HEAVY : 0) | (mounted ? MOUNTED : 0));
    }

    public boolean heavy() {
        return (flags & HEAVY) != 0;
    }

    public boolean mounted() {
        return (flags & MOUNTED) != 0;
    }

    public boolean hasClip() {
        return !slot.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
