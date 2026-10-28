package io.github.sethwillemsvg.lotrrevival.npc.ai.combat;

import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Picks the target of a guard or captain every 20 ticks. Priority: retaliation slot, hostile player, enemy NPC,
 * monster (not creepers); nearest wins ties. Man civilians run it too, but only ever take the retaliation slot.
 */
final class NpcTargetGoal extends Goal {
    private static final int RANK_ATTACKER = 0;
    private static final int RANK_PLAYER = 1;
    private static final int RANK_NPC = 2;
    private static final int RANK_MONSTER = 3;

    private final NpcEntity npc;
    private final boolean seeksTargets;
    private long lastCheck = -NpcCombat.RECHECK_TICKS;
    @Nullable
    private LivingEntity pending;

    NpcTargetGoal(NpcEntity npc, boolean seeksTargets) {
        this.npc = npc;
        this.seeksTargets = seeksTargets;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        long now = npc.level().getGameTime();
        if (now - lastCheck < NpcCombat.RECHECK_TICKS) {
            return false;
        }
        lastCheck = now;
        pending = findBest(true);
        return pending != null;
    }

    @Override
    public void start() {
        npc.setTarget(pending);
        pending = null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity current = npc.getTarget();
        if (current == null || !current.isAlive()) {
            return false;
        }
        long now = npc.level().getGameTime();
        if (now - lastCheck < NpcCombat.RECHECK_TICKS) {
            return true;
        }
        lastCheck = now;
        LivingEntity best = findBest(false);
        int currentRank = rank(current, false);
        if (currentRank < 0 && best == null) {
            return false;
        }
        if (best != null && best != current && (currentRank < 0 || rank(best, false) < currentRank)) {
            npc.setTarget(best);
        }
        return true;
    }

    @Override
    public void stop() {
        npc.setTarget(null);
    }

    /** Best target now, or null. {@code roll} makes grey-zone players pass only by chance (new targets only). */
    @Nullable
    private LivingEntity findBest(boolean roll) {
        LivingEntity attacker = npc.retaliationTarget();
        if (attacker != null && NpcCombat.isAttackable(attacker)) {
            return attacker;
        }
        if (!seeksTargets) {
            return null;
        }
        AABB box = npc.getBoundingBox().inflate(NpcCombat.SENSE_RANGE, NpcCombat.SENSE_VERTICAL, NpcCombat.SENSE_RANGE);
        LivingEntity best = null;
        int bestRank = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : npc.level().getEntitiesOfClass(LivingEntity.class, box)) {
            int rank = rank(candidate, roll);
            if (rank < 0 || !npc.hasLineOfSight(candidate)) {
                continue;
            }
            double dist = npc.distanceToSqr(candidate);
            if (rank < bestRank || (rank == bestRank && dist < bestDist)) {
                best = candidate;
                bestRank = rank;
                bestDist = dist;
            }
        }
        return best;
    }

    /** Priority rank of a candidate, or -1 if it is not a valid target. */
    private int rank(LivingEntity candidate, boolean roll) {
        if (candidate == npc || !NpcCombat.isAttackable(candidate)) {
            return -1;
        }
        if (candidate == npc.retaliationTarget()) {
            return RANK_ATTACKER;
        }
        if (!seeksTargets || !NpcCombat.inSenseRange(npc, candidate)) {
            return -1;
        }
        if (candidate instanceof Player player) {
            if (NpcCombat.ignoresPlayers(npc) || NpcCombat.isOwner(npc, player)) {
                return -1;
            }
            float chance = FactionRelations.hostilityChance(npc, player);
            if (chance <= 0.0f || (roll && npc.getRandom().nextFloat() >= chance)) {
                return -1;
            }
            return RANK_PLAYER;
        }
        if (FactionRelations.isHostile(npc, candidate)) {
            return RANK_NPC;
        }
        if (NpcCombat.isMonster(candidate) && !NpcCombat.isCreeper(candidate)) {
            return RANK_MONSTER;
        }
        return -1;
    }
}
