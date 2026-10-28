package io.github.sethwillemsvg.lotrrevival.npc.routine;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Follows the {@link NpcRoutine.Plan} of the moment: wanders within a radius of home (or of a spot), walks back when
 * it has strayed, or walks toward far-away points (travellers). It never ends while the plan is not
 * {@link NpcRoutine.Mode#FREE}, so the plain idle stroll below it cannot wander an NPC out of its allowed area.
 *
 * <p>All state is transient: after a reload the NPC simply re-reads the time of day and its home.
 */
public final class RoutineGoal extends Goal {
    /** Candidate positions tried when picking a wander target. */
    private static final int TARGET_TRIES = 10;
    /** Ticks between re-issuing the walk home while walking home. */
    private static final int REPATH_INTERVAL = 20;
    /** A traveller gives up on a target after this long. */
    private static final int TRAVEL_TIMEOUT = 1200;
    /** A traveller counts a target as reached within this distance. */
    private static final double TRAVEL_REACHED = 4.0;
    /** Share of daytime wander targets a farmer tries to place next to farmland. */
    private static final float FARMLAND_PREFERENCE = 0.7f;

    private final NpcEntity npc;
    private final double speed;

    @Nullable
    private BlockPos spot;

    private NpcRoutine.Mode lastMode = NpcRoutine.Mode.FREE;
    private boolean homing;
    private int homingTicks;
    private int repathTicks;
    private int idleTicks;
    /** Day time before which the NPC does not try to walk home, set when it gave up. */
    private long suppressedUntil = Long.MIN_VALUE;

    @Nullable
    private Vec3 travelTarget;

    private int travelTicks;

    public RoutineGoal(NpcEntity npc, double speed) {
        this.npc = npc;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** The plan for right now, including having given up on walking home. */
    public NpcRoutine.Plan currentPlan() {
        long dayTime = npc.level().getDayTime();
        NpcRoutine.Plan plan = NpcRoutine.plan(npc, NpcRoutine.phaseAt(dayTime));
        if (plan.homePhase() && dayTime < suppressedUntil) {
            return new NpcRoutine.Plan(NpcRoutine.Mode.AROUND_SPOT, NpcRoutine.WILD_NIGHT_RADIUS, false);
        }
        return plan;
    }

    @Override
    public boolean canUse() {
        return !currentPlan().isFree();
    }

    @Override
    public boolean canContinueToUse() {
        return !currentPlan().isFree();
    }

    @Override
    public void start() {
        idleTicks = 0;
        homing = false;
        homingTicks = 0;
        travelTarget = null;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        spot = null;
        homing = false;
        travelTarget = null;
        lastMode = NpcRoutine.Mode.FREE;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        NpcRoutine.Plan plan = currentPlan();
        if (plan.mode() != lastMode) {
            lastMode = plan.mode();
            homing = false;
            homingTicks = 0;
            travelTarget = null;
            idleTicks = 0;
            spot = null;
        }
        switch (plan.mode()) {
            case AROUND_HOME -> npc.homePos().ifPresent(home -> tickAround(plan, home));
            case AROUND_SPOT -> {
                if (spot == null) {
                    spot = npc.blockPosition();
                }
                tickAround(plan, spot);
            }
            case TRAVEL -> tickTravel();
            case FREE -> {}
        }
    }

    // ------------------------------------------------------------------ staying within a radius

    private void tickAround(NpcRoutine.Plan plan, BlockPos center) {
        PathNavigation navigation = npc.getNavigation();
        int radius = plan.radius();
        double distance = horizontalDistance(center);
        boolean outside = distance > radius || Math.abs(npc.getY() - center.getY()) > Math.max(6, radius);
        if (!homing && outside) {
            homing = true;
            homingTicks = 0;
            repathTicks = 0;
        }
        if (homing && distance <= Math.max(2.0, radius / 2.0) && Math.abs(npc.getY() - center.getY()) <= 6) {
            homing = false;
            homingTicks = 0;
            navigation.stop();
            idleTicks = 20;
        }
        if (homing) {
            if (--repathTicks <= 0) {
                navigation.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, speed);
                repathTicks = REPATH_INTERVAL;
            }
            if (plan.homePhase() && ++homingTicks > NpcRoutine.GIVE_UP_TICKS) {
                suppressedUntil = NpcRoutine.nextDusk(npc.level().getDayTime());
                homing = false;
                homingTicks = 0;
                navigation.stop();
            }
            return;
        }
        if (navigation.isDone()) {
            if (idleTicks > 0) {
                idleTicks--;
                return;
            }
            // At night the NPC stays well inside the radius so it never drifts out of the allowed circle.
            int targetRadius = plan.homePhase() ? Math.max(1, radius - 1) : radius;
            BlockPos target = pickTarget(center, targetRadius, preferFarmland(plan));
            boolean moving = target != null
                    && navigation.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
            idleTicks = moving ? 40 + npc.getRandom().nextInt(80) : 10;
        }
    }

    private boolean preferFarmland(NpcRoutine.Plan plan) {
        return npc.getRole() == NpcRole.FARMER
                && npc.isCivilian()
                && !plan.homePhase()
                && npc.getRandom().nextFloat() < FARMLAND_PREFERENCE;
    }

    private double horizontalDistance(BlockPos center) {
        double dx = npc.getX() - (center.getX() + 0.5);
        double dz = npc.getZ() - (center.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A random standing position within {@code radius} of {@code center}, preferring farmland when asked. */
    @Nullable
    private BlockPos pickTarget(BlockPos center, int radius, boolean farmland) {
        BlockPos first = null;
        for (int i = 0; i < TARGET_TRIES; i++) {
            double angle = npc.getRandom().nextDouble() * Math.PI * 2;
            double length = Math.sqrt(npc.getRandom().nextDouble()) * radius;
            int x = (int) Math.floor(center.getX() + 0.5 + Math.cos(angle) * length);
            int z = (int) Math.floor(center.getZ() + 0.5 + Math.sin(angle) * length);
            BlockPos stand = standingPos(x, center.getY(), z);
            if (stand == null) {
                continue;
            }
            if (!farmland) {
                return stand;
            }
            if (nearFarmland(stand)) {
                return stand;
            }
            if (first == null) {
                first = stand;
            }
        }
        return first;
    }

    private boolean nearFarmland(BlockPos stand) {
        Level level = npc.level();
        if (level.getBlockState(stand.below()).is(Blocks.FARMLAND)) {
            return true;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(stand.relative(direction).below()).is(Blocks.FARMLAND)) {
                return true;
            }
        }
        return false;
    }

    /** A loaded, dry column position with a solid floor and two blocks of headroom near {@code y}, or null. */
    @Nullable
    private BlockPos standingPos(int x, int y, int z) {
        Level level = npc.level();
        if (!level.hasChunkAt(new BlockPos(x, y, z))) {
            return null;
        }
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            BlockPos below = pos.below();
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    && level.getFluidState(pos).isEmpty()
                    && level.getFluidState(below).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ travellers

    private void tickTravel() {
        PathNavigation navigation = npc.getNavigation();
        if (travelTarget != null) {
            travelTicks++;
            double dx = npc.getX() - travelTarget.x;
            double dz = npc.getZ() - travelTarget.z;
            if (dx * dx + dz * dz <= TRAVEL_REACHED * TRAVEL_REACHED || travelTicks > TRAVEL_TIMEOUT) {
                travelTarget = null;
                idleTicks = 60 + npc.getRandom().nextInt(140);
                navigation.stop();
                return;
            }
            if (navigation.isDone()) {
                navigation.moveTo(travelTarget.x, travelTarget.y, travelTarget.z, speed);
            }
            return;
        }
        if (idleTicks > 0) {
            idleTicks--;
            return;
        }
        for (int i = 0; i < TARGET_TRIES; i++) {
            double angle = npc.getRandom().nextDouble() * Math.PI * 2;
            double length = NpcRoutine.TRAVEL_MIN
                    + npc.getRandom().nextDouble() * (NpcRoutine.TRAVEL_MAX - NpcRoutine.TRAVEL_MIN);
            int x = (int) Math.floor(npc.getX() + Math.cos(angle) * length);
            int z = (int) Math.floor(npc.getZ() + Math.sin(angle) * length);
            BlockPos stand = standingPos(x, npc.blockPosition().getY(), z);
            if (stand == null) {
                // Hills and cliffs: fall back to the surface height of a loaded column.
                BlockPos column = new BlockPos(x, 0, z);
                if (!npc.level().hasChunkAt(column)) {
                    continue;
                }
                int top = npc.level()
                        .getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                stand = new BlockPos(x, top, z);
            }
            travelTarget = Vec3.atBottomCenterOf(stand);
            travelTicks = 0;
            navigation.moveTo(travelTarget.x, travelTarget.y, travelTarget.z, speed);
            return;
        }
        idleTicks = 100;
    }
}
