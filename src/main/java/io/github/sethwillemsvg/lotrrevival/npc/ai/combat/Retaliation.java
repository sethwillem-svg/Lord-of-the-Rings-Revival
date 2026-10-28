package io.github.sethwillemsvg.lotrrevival.npc.ai.combat;

import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelation;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcTemperament;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Per-NPC retaliation state: the attacker slot of target priority (guards, captains and man civilians fight it) or a
 * flee mark (hobbit civilians run for {@link #FLEE_TICKS}). Transient, never saved. Ends when the attacker dies,
 * logs out, changes dimension, goes creative or spectator, or stays beyond sense range for
 * {@link #OUT_OF_RANGE_TICKS}.
 */
public final class Retaliation {
    public static final int FLEE_TICKS = 200;
    public static final int OUT_OF_RANGE_TICKS = 200;

    private final NpcEntity npc;
    @Nullable
    private LivingEntity target;
    private int outOfRangeTicks;
    @Nullable
    private LivingEntity fleeFrom;
    private long fleeUntil;

    public Retaliation(NpcEntity npc) {
        this.npc = npc;
    }

    /** Guards, captains and man civilians fight back; hobbit civilians flee. */
    public static boolean fights(NpcEntity npc) {
        return NpcCombat.isFighter(npc) || npc.race().temperament() != NpcTemperament.FLEE;
    }

    /** The current retaliation target, or null. */
    @Nullable
    public LivingEntity target() {
        return target;
    }

    /** Whether the NPC is in its flee window from this entity. */
    public boolean isFleeingFrom(Entity other) {
        return fleeFrom != null && fleeFrom == other && npc.level().getGameTime() < fleeUntil;
    }

    /** Whether a hit or a witnessed kill by this attacker makes the NPC react at all. */
    public boolean wouldReactTo(LivingEntity attacker) {
        if (attacker == npc || !NpcCombat.isAttackable(attacker)) {
            return false;
        }
        if (attacker instanceof Player player) {
            return !NpcCombat.isOwner(npc, player);
        }
        // Accidental hits between the same or allied factions never cause retaliation.
        return FactionRelations.factionOf(attacker)
                .map(faction -> FactionRelations.relation(npc.level().registryAccess(), npc.faction(), faction)
                        != FactionRelation.ALLY)
                .orElse(true);
    }

    /** Reacts to an attacker: fight it (home range ignored) or flee from it. Only this NPC reacts. */
    public void provoke(LivingEntity attacker) {
        if (!wouldReactTo(attacker)) {
            return;
        }
        if (fights(npc)) {
            if (attacker instanceof Player && NpcCombat.ignoresPlayers(npc)) {
                return;
            }
            target = attacker;
            outOfRangeTicks = 0;
            fleeFrom = null;
            npc.setTarget(attacker);
        } else {
            fleeFrom = attacker;
            fleeUntil = npc.level().getGameTime() + FLEE_TICKS;
        }
    }

    /** Server tick: drops stale targets. */
    public void tick() {
        if (target != null) {
            if (!valid(target)) {
                clear();
            } else if (NpcCombat.inSenseRange(npc, target)) {
                outOfRangeTicks = 0;
            } else if (++outOfRangeTicks >= OUT_OF_RANGE_TICKS) {
                clear();
            }
        }
        if (fleeFrom != null && (npc.level().getGameTime() >= fleeUntil || !valid(fleeFrom))) {
            fleeFrom = null;
        }
    }

    public void clear() {
        LivingEntity old = target;
        target = null;
        outOfRangeTicks = 0;
        if (old != null && npc.getTarget() == old) {
            npc.setTarget(null);
        }
    }

    private boolean valid(LivingEntity entity) {
        return entity.level() == npc.level() && NpcCombat.isAttackable(entity);
    }
}
