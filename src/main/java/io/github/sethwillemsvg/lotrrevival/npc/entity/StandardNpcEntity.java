package io.github.sethwillemsvg.lotrrevival.npc.entity;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * An NPC whose behavior comes entirely from its race, faction, class and data files: the Bounder, Shirriff and the
 * Bree types. Per-type numbers (XP range, roles it may not roll) are fixed at construction.
 */
public final class StandardNpcEntity extends NpcEntity {
    private final Set<NpcRole> excludedRoles;
    private final int xpMin;
    private final int xpMax;

    public StandardNpcEntity(
            EntityType<? extends StandardNpcEntity> type,
            Level level,
            NpcRace race,
            ResourceKey<Faction> faction,
            NpcClass npcClass,
            Set<NpcRole> excludedRoles,
            int xpMin,
            int xpMax) {
        super(type, level, race, faction, npcClass);
        this.excludedRoles = Set.copyOf(excludedRoles);
        this.xpMin = xpMin;
        this.xpMax = xpMax;
    }

    @Override
    protected boolean canRollRole(NpcRole role) {
        return !excludedRoles.contains(role);
    }

    @Override
    protected int xpMin() {
        return xpMin;
    }

    @Override
    protected int xpMax() {
        return xpMax;
    }
}
