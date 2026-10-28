package io.github.sethwillemsvg.lotrrevival.npc.hire.purchase;

import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.npc.hire.PledgeCondition;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * One row of the Units tab as one particular player sees it: the cost for them and why the row is greyed out, if it
 * is.
 *
 * @param id unit list entry id
 * @param entity entity type of the unit
 * @param mount entity type of the mount, for mounted entries
 * @param cost cost for this player
 * @param required required alignment R
 * @param pledge pledge requirement
 * @param blocked why the player cannot hire it now
 */
public record UnitRowView(
        String id,
        ResourceLocation entity,
        Optional<ResourceLocation> mount,
        int cost,
        float required,
        PledgeCondition pledge,
        Blocked blocked) {
    /** Grey-out reason, in the order the checks run. */
    public enum Blocked {
        NONE,
        ALIGNMENT,
        PLEDGE,
        COINS
    }

    private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, Optional<ResourceLocation>> MOUNT =
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC);

    public static final StreamCodec<ByteBuf, UnitRowView> STREAM_CODEC = StreamCodec.of(
            (buf, row) -> {
                ID.encode(buf, row.id());
                CoreNetwork.ID_CODEC.encode(buf, row.entity());
                MOUNT.encode(buf, row.mount());
                ByteBufCodecs.VAR_INT.encode(buf, row.cost());
                ByteBufCodecs.FLOAT.encode(buf, row.required());
                ByteBufCodecs.VAR_INT.encode(buf, row.pledge().ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, row.blocked().ordinal());
            },
            buf -> new UnitRowView(
                    ID.decode(buf),
                    CoreNetwork.ID_CODEC.decode(buf),
                    MOUNT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    PledgeCondition.values()[Math.floorMod(ByteBufCodecs.VAR_INT.decode(buf), PledgeCondition.values().length)],
                    Blocked.values()[Math.floorMod(ByteBufCodecs.VAR_INT.decode(buf), Blocked.values().length)]));

    public boolean hirable() {
        return blocked == Blocked.NONE;
    }
}
