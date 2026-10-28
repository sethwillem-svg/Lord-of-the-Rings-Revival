package io.github.sethwillemsvg.lotrrevival.npc.hire.ai;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Goal installer for hired units: halt (3), then follow and guard (5). Melee (4) sits between them so a unit in a
 * fight leaves its post or its owner, and the routine (6) and the idle stroll (7) never run for a hired unit. Every
 * goal does nothing for an NPC that is not hired.
 */
public final class UnitGoals {
    public static final int HALT_PRIORITY = 3;
    public static final int MOVE_PRIORITY = 5;

    private UnitGoals() {}

    public static void install(NpcEntity npc, GoalSelector goals) {
        goals.addGoal(HALT_PRIORITY, new HaltGoal(npc));
        goals.addGoal(MOVE_PRIORITY, new FollowGoal(npc));
        goals.addGoal(MOVE_PRIORITY, new GuardGoal(npc));
    }

    /** Stands still while halted and idle. A target (someone hit it) hands control to melee. */
    static final class HaltGoal extends Goal {
        private final NpcEntity npc;

        HaltGoal(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return npc.isHired() && UnitAi.isHalted(npc) && npc.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            npc.getNavigation().stop();
        }
    }

    /** Walks to the owner when more than 8 blocks away until within 6, through water. */
    static final class FollowGoal extends Goal {
        private static final int REPATH_TICKS = 10;

        private final NpcEntity npc;
        private int repathIn;
        private float oldWaterMalus;

        FollowGoal(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!npc.isHired() || !UnitAi.shouldFollow(npc) || UnitAi.guardArea(npc) != null) {
                return false;
            }
            ServerPlayer owner = UnitAi.presentOwner(npc);
            return owner != null && npc.distanceToSqr(owner) > UnitAi.FOLLOW_START * UnitAi.FOLLOW_START;
        }

        @Override
        public boolean canContinueToUse() {
            if (!UnitAi.shouldFollow(npc) || UnitAi.guardArea(npc) != null) {
                return false;
            }
            ServerPlayer owner = UnitAi.presentOwner(npc);
            return owner != null && npc.distanceToSqr(owner) > UnitAi.FOLLOW_STOP * UnitAi.FOLLOW_STOP;
        }

        @Override
        public void start() {
            repathIn = 0;
            oldWaterMalus = npc.getPathfindingMalus(PathType.WATER);
            npc.setPathfindingMalus(PathType.WATER, 0.0f);
        }

        @Override
        public void tick() {
            ServerPlayer owner = UnitAi.presentOwner(npc);
            if (owner == null) {
                return;
            }
            npc.getLookControl().setLookAt(owner, 10.0f, npc.getMaxHeadXRot());
            if (--repathIn <= 0) {
                repathIn = REPATH_TICKS;
                npc.getNavigation().moveTo(owner, UnitAi.followSpeedMultiplier(npc));
            }
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
            npc.setPathfindingMalus(PathType.WATER, oldWaterMalus);
        }
    }

    /**
     * Holds the guard area: walks back to the guard point when it has drifted, else stands there. Covers the owner
     * being away too (the area is then the unit's own spot with range 8).
     */
    static final class GuardGoal extends Goal {
        private static final int REPATH_TICKS = 10;
        /** A guard walks back to its point when farther than this from it. */
        private static final double HOME_RADIUS = 2.0;

        private final NpcEntity npc;
        private int repathIn;

        GuardGoal(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return npc.isHired() && !UnitAi.isHalted(npc) && UnitAi.guardArea(npc) != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            repathIn = 0;
        }

        @Override
        public void tick() {
            UnitAi.Area area = UnitAi.guardArea(npc);
            if (area == null || --repathIn > 0) {
                return;
            }
            repathIn = REPATH_TICKS;
            double dx = area.center().getX() + 0.5 - npc.getX();
            double dz = area.center().getZ() + 0.5 - npc.getZ();
            if (dx * dx + dz * dz > HOME_RADIUS * HOME_RADIUS) {
                npc.getNavigation()
                        .moveTo(
                                area.center().getX() + 0.5,
                                area.center().getY(),
                                area.center().getZ() + 0.5,
                                UnitAi.followSpeedMultiplier(npc));
            }
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
        }
    }
}
