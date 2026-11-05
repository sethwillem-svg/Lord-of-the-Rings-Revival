package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Bodyguard AI (spec Ranks: Bodyguards follow their nemesis). Two goals added to each bodyguard NPC when it is spawned:
 * <ul>
 *   <li>{@link Follow} (move, priority 2, above Middle-earth's attack goals at 4): out of a fight, walks back once further than
 *   {@code bodyguard_follow_distance} and stops at half of it; in a fight, breaks off once further than
 *   {@code bodyguard_leash_distance}; further than {@code bodyguard_teleport_distance} (a mounted nemesis rode off) it is moved
 *   next to the nemesis. It re-paths every 10 ticks, and only when the nemesis moved more than 1.5 blocks from the path's end or
 *   the path is done. A guard that gains less than half a block over 30 ticks, or finds no path that reaches the nemesis, is
 *   stuck and is moved next to it as well (out of a fight, or beyond the leash).</li>
 *   <li>{@link Defend} (target, priority 2, after "hurt by" at 1): takes the nemesis's attacker (hit in the last 5 s) or the
 *   nemesis's own target as its target.</li>
 * </ul>
 * Goals are not saved; a bodyguard that loads from disk without its nemesis is removed (see {@link Manifestation#onJoin}).
 */
public final class BodyguardGoals {
    /** Entity tag on bodyguards (with {@link MeRanks#BODYGUARD_TAG}) so leftovers saved in chunks can be found on load. */
    public static final String GUARD_TAG = "lotr_revival.bodyguard";
    private static final int REPATH_TICKS = 10;
    private static final int DEFEND_MEMORY_TICKS = 100;

    /** Counters for the nemesis summary line. */
    public static int repaths, teleports, defends, watchdogs;

    private BodyguardGoals() {}

    /**
     * Adds the follow and defend goals to a bodyguard. {@code Mob.goalSelector} and {@code targetSelector} are public through
     * NeoForge's access transformer.
     */
    public static void attach(NpcEntity guard, NpcEntity leader, UUID recordId) {
        guard.addTag(GUARD_TAG);
        guard.goalSelector.addGoal(2, new Follow(guard, leader, recordId));
        guard.targetSelector.addGoal(2, new Defend(guard, leader, recordId));
    }

    /** Drives an idle follow goal (see {@link Follow#watchdog}); called for each guard from the body check. */
    public static void watchdog(NpcEntity guard) {
        for (WrappedGoal w : guard.goalSelector.getAvailableGoals()) if (w.getGoal() instanceof Follow f) f.watchdog();
        for (WrappedGoal w : guard.targetSelector.getAvailableGoals()) if (w.getGoal() instanceof Defend d) d.watchdog();
    }

    static boolean leaderOk(@Nullable LivingEntity leader, NpcEntity guard) {
        return leader != null && leader.isAlive() && !leader.isRemoved() && leader.level() == guard.level();
    }

    // ---------------------------------------------------------------- follow

    public static final class Follow extends Goal {
        private final NpcEntity guard;
        private final NpcEntity leader;
        private final UUID recordId;
        private int repathIn;
        private @Nullable Vec3 pathTarget;
        private boolean leashed;
        /** Closest it got since the last progress, and how many re-path checks passed without getting 0.5 closer. */
        private double bestDist;
        private int stuck;
        /** Last navigation result, for logs and tests. */
        public String lastNav = "none";
        private boolean running;

        public Follow(NpcEntity guard, NpcEntity leader, UUID recordId) {
            this.guard = guard;
            this.leader = leader;
            this.recordId = recordId;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private static double follow() {
            return NemesisConfig.i(NemesisConfig.GUARD_FOLLOW_DISTANCE);
        }

        private static double leash() {
            return Math.max(follow() + 2, NemesisConfig.i(NemesisConfig.GUARD_LEASH_DISTANCE));
        }

        private boolean fighting() {
            LivingEntity t = guard.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canUse() {
            if (!leaderOk(leader, guard) || guard.isPassenger()) return false;
            double d = guard.distanceTo(leader);
            return d > leash() || (!fighting() && d > follow());
        }

        @Override
        public boolean canContinueToUse() {
            if (!leaderOk(leader, guard) || guard.isPassenger()) return false;
            double d = guard.distanceTo(leader);
            if (leashed) return d > leash() * 0.5;   // pulled back from a fight: come well inside the leash before fighting again
            if (fighting()) return d > leash();
            return d > follow() * 0.5;
        }

        @Override
        public void start() {
            running = true;
            leashed = guard.distanceTo(leader) > leash();
            repathIn = 0;
            pathTarget = null;
            bestDist = guard.distanceTo(leader);
            stuck = 0;
            LotrLog.dbg("nemesis", "bodyguard follow: guard={} nemesis={} dist={} leashed={} target={}", LotrLog.id(guard.getUUID()),
                    LotrLog.id(recordId), String.format("%.1f", guard.distanceTo(leader)), leashed,
                    guard.getTarget() == null ? "-" : LotrLog.id(guard.getTarget().getUUID()));
        }

        @Override
        public void stop() {
            running = false;
            guard.getNavigation().stop();
            pathTarget = null;
            leashed = false;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            guard.getLookControl().setLookAt(leader, 10.0F, (float) guard.getMaxHeadXRot());
            double d = guard.distanceTo(leader);
            if (d > NemesisConfig.i(NemesisConfig.GUARD_TELEPORT_DISTANCE) && teleportNear()) return;
            if (--repathIn > 0) return;
            repathIn = REPATH_TICKS;
            // progress check: no 0.5 blocks gained over 3 checks (30 ticks), or no path that reaches it -> stuck
            if (d < bestDist - 0.5) {
                bestDist = d;
                stuck = 0;
            } else {
                stuck++;
            }
            Vec3 at = leader.position();
            boolean moved = pathTarget == null || pathTarget.distanceToSqr(at) > 1.5 * 1.5;
            if (moved || guard.getNavigation().isDone()) {
                double speed = d > leash() ? 1.3 : 1.1;
                Path path = guard.getNavigation().createPath(leader, 0);
                boolean started = path != null && guard.getNavigation().moveTo(path, speed);
                lastNav = path == null ? "no path" : (started ? "moving" : "refused") + (path.canReach() ? "" : " (cannot reach)")
                        + " end=" + (path.getEndNode() == null ? "-" : path.getEndNode().asBlockPos().toShortString());
                if (started) {
                    pathTarget = at;
                    repaths++;
                }
                if (path == null || !path.canReach() || !started) stuck += 2;
            }
            if (stuck >= 3 && (!fighting() || d > leash())) {
                LotrLog.dbg("nemesis", "bodyguard stuck: guard={} nemesis={} dist={} best={} nav={} -> teleport", LotrLog.id(guard.getUUID()),
                        LotrLog.id(recordId), String.format("%.1f", d), String.format("%.1f", bestDist), lastNav);
                if (teleportNear()) {
                    stuck = 0;
                    bestDist = guard.distanceTo(leader);
                } else {
                    stuck = 1;   // try again in a few checks
                }
            }
        }

        /**
         * Called from the 2 s body check. CI run 221 showed the goal never started in the test world (no goals running at
         * all on the guard), so the body check drives the same path-and-teleport logic when the goal sits idle out of range.
         */
        void watchdog() {
            if (running || !leaderOk(leader, guard) || guard.isPassenger()) return;
            double d = guard.distanceTo(leader);
            if (d <= follow() || (fighting() && d <= leash())) return;
            watchdogs++;
            LotrLog.dbg("nemesis", "bodyguard watchdog: follow goal idle, guard={} nemesis={} dist={} canUse={} nav={}", LotrLog.id(guard.getUUID()),
                    LotrLog.id(recordId), String.format("%.1f", d), canUse(), lastNav);
            repathIn = 0;
            tick();
        }

        /** Like a tamed wolf: a free spot on solid ground within 3 blocks of the nemesis. */
        private boolean teleportNear() {
            if (!(guard.level() instanceof ServerLevel level)) return false;
            lastNav = "teleport";
            BlockPos base = leader.blockPosition();
            for (int i = 0; i < 20; i++) {
                int dx = NemesisRandom.between(-3, 3), dz = NemesisRandom.between(-3, 3);
                if (Math.abs(dx) < 1 && Math.abs(dz) < 1) continue;
                BlockPos p = base.offset(dx, NemesisRandom.between(-2, 2), dz);
                BlockState below = level.getBlockState(p.below());
                if (below.getCollisionShape(level, p.below()).isEmpty() || !level.getFluidState(p).isEmpty()) continue;
                Vec3 to = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                if (!level.noCollision(guard, guard.getBoundingBox().move(to.subtract(guard.position())))) continue;
                double from = guard.distanceTo(leader);
                guard.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, guard.getYRot(), guard.getXRot());
                guard.getNavigation().stop();
                pathTarget = null;
                teleports++;
                LotrLog.dbg("nemesis", "bodyguard teleported: guard={} nemesis={} from {} blocks to {}", LotrLog.id(guard.getUUID()),
                        LotrLog.id(recordId), String.format("%.1f", from), p.toShortString());
                return true;
            }
            return false;
        }
    }

    // ---------------------------------------------------------------- defend

    public static final class Defend extends Goal {
        private final NpcEntity guard;
        private final NpcEntity leader;
        private final UUID recordId;
        private @Nullable LivingEntity pick;

        public Defend(NpcEntity guard, NpcEntity leader, UUID recordId) {
            this.guard = guard;
            this.leader = leader;
            this.recordId = recordId;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        private @Nullable LivingEntity threat() {
            LivingEntity attacker = leader.getLastHurtByMob();
            if (attacker != null && leader.tickCount - leader.getLastHurtByMobTimestamp() <= DEFEND_MEMORY_TICKS && valid(attacker))
                return attacker;
            LivingEntity t = leader.getTarget();
            return t != null && valid(t) ? t : null;
        }

        private boolean valid(LivingEntity e) {
            if (e == guard || e == leader || !e.isAlive()) return false;
            if (e.getTags().contains(GUARD_TAG) || e.getTags().contains(LeaderGear.MOUNT_TAG)) return false;
            return guard.canAttack(e, TargetingConditions.forCombat().ignoreLineOfSight());
        }

        @Override
        public boolean canUse() {
            if (!leaderOk(leader, guard)) return false;
            pick = threat();
            return pick != null && pick != guard.getTarget();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = guard.getTarget();
            return leaderOk(leader, guard) && t != null && t.isAlive() && t == pick;
        }

        @Override
        public void start() {
            guard.setTarget(pick);
            defends++;
            LotrLog.dbg("nemesis", "bodyguard defends: guard={} nemesis={} target={} ({})", LotrLog.id(guard.getUUID()), LotrLog.id(recordId),
                    LotrLog.id(pick.getUUID()), pick == leader.getTarget() ? "nemesis's target" : "attacked the nemesis");
        }

        @Override
        public void stop() {
            pick = null;
        }

        /** Body-check fallback, as for {@link Follow#watchdog}: set the target if the goal did not. */
        void watchdog() {
            if (!canUse()) return;
            watchdogs++;
            LotrLog.dbg("nemesis", "bodyguard watchdog: defend goal idle, guard={} nemesis={}", LotrLog.id(guard.getUUID()), LotrLog.id(recordId));
            start();
        }
    }
}
