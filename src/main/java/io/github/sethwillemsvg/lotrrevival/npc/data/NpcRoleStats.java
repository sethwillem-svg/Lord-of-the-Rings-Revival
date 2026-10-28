package io.github.sethwillemsvg.lotrrevival.npc.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.Map;
import java.util.Optional;

/**
 * Per-role numbers from {@code npc_roles/<type>.json}.
 *
 * @param tradeMin minimum alignment to trade, absent for roles that do not trade
 * @param hireMin minimum alignment to hire, absent for roles that do not hire
 * @param killValue base alignment value of killing an NPC of this role
 */
public record NpcRoleStats(Optional<Float> tradeMin, Optional<Float> hireMin, float killValue) {
    public static final Codec<NpcRoleStats> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.FLOAT.optionalFieldOf("trade_min").forGetter(NpcRoleStats::tradeMin),
                    Codec.FLOAT.optionalFieldOf("hire_min").forGetter(NpcRoleStats::hireMin),
                    Codec.FLOAT.fieldOf("kill_value").forGetter(NpcRoleStats::killValue))
            .apply(i, NpcRoleStats::new));

    /** One file: role name to stats. */
    public static final Codec<Map<NpcRole, NpcRoleStats>> TABLE_CODEC = Codec.unboundedMap(NpcRole.CODEC, CODEC);

    public static NpcRoleStats of(float killValue) {
        return new NpcRoleStats(Optional.empty(), Optional.empty(), killValue);
    }

    public static NpcRoleStats trader(float tradeMin, float killValue) {
        return new NpcRoleStats(Optional.of(tradeMin), Optional.empty(), killValue);
    }
}
