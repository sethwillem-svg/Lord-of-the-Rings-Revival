package io.github.sethwillemsvg.lotrrevival.npc.hire.ai;

import io.github.sethwillemsvg.lotrrevival.npc.ai.combat.NpcCombat;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HireAttachments;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitTask;
import io.github.sethwillemsvg.lotrrevival.npc.interact.Hireable;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared rules of the hired-unit AI: owner presence, guard areas, status and the safe-teleport search. */
public final class UnitAi {
    /** Pathing to the owner starts beyond this many blocks. */
    public static final double FOLLOW_START = 8.0;
    /** Following stops within this many blocks. */
    public static final double FOLLOW_STOP = 6.0;
    /** Effective movement speed of a following unit; the path speed multiplier is this over the speed attribute. */
    public static final double FOLLOW_SPEED = 0.37;
    /** Guard range of a unit whose owner is away. */
    public static final int AWAY_RANGE = 8;
    /** Ticks between teleport checks. */
    public static final int TELEPORT_INTERVAL = 20;
    /** A following unit never teleports from closer than this (the larger of this and the sense range). */
    public static final double TELEPORT_MIN_DISTANCE = 24.0;
    public static final int TELEPORT_MIN_RADIUS = 3;
    public static final int TELEPORT_MAX_RADIUS = 6;
    public static final int TELEPORT_TRIES = 10;

    private UnitAi() {}

    /** A spot and how far from it a unit may roam. */
    public record Area(BlockPos center, int range) {
        public boolean contains(Entity entity) {
            return Vec3.atBottomCenterOf(center).distanceToSqr(entity.position()) <= (double) range * range;
        }
    }

    /** What a unit is doing, for the command screen. */
    public enum Status {
        FOLLOWING,
        HALTED,
        GUARDING,
        IN_COMBAT,
        OWNER_AWAY
    }

    /** The owner when online, alive and in the unit's level; else null (the unit counts the owner as away). */
    @Nullable
    public static ServerPlayer presentOwner(NpcEntity unit) {
        ServerPlayer owner = HiredUnits.onlineOwner(unit);
        if (owner == null || !owner.isAlive() || owner.level() != unit.level()) {
            return null;
        }
        return owner;
    }

    /**
     * Where the unit must stay: its guard point and range while guarding, its own spot with range
     * {@value #AWAY_RANGE} while the owner is away, else null. Tracks the away spot as a side effect.
     */
    @Nullable
    public static Area guardArea(NpcEntity unit) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired()) {
            return null;
        }
        UnitRuntime runtime = unit.getData(HireAttachments.UNIT_RUNTIME);
        if (presentOwner(unit) == null) {
            if (runtime.awayAnchor == null) {
                runtime.awayAnchor = unit.blockPosition();
            }
            return new Area(runtime.awayAnchor, AWAY_RANGE);
        }
        runtime.awayAnchor = null;
        if (state.command().state() == Hireable.CommandState.GUARD_POS
                && state.command().guardPos().isPresent()) {
            return new Area(state.command().guardPos().get(), state.command().guardRange());
        }
        return null;
    }

    /** Whether the unit is hired and its owner has told it to halt. */
    public static boolean isHalted(NpcEntity unit) {
        HiredUnit state = HiredUnits.get(unit);
        return state.isHired() && state.command().halted();
    }

    /** Whether the unit should follow its owner right now (hired, set to follow, not halted, owner present). */
    public static boolean shouldFollow(NpcEntity unit) {
        HiredUnit state = HiredUnits.get(unit);
        return state.isHired()
                && state.task() != UnitTask.FARMER
                && state.command().state() == Hireable.CommandState.FOLLOW
                && !state.command().halted()
                && presentOwner(unit) != null;
    }

    public static Status status(NpcEntity unit) {
        HiredUnit state = HiredUnits.get(unit);
        if (presentOwner(unit) == null) {
            return Status.OWNER_AWAY;
        }
        if (unit.getTarget() != null) {
            return Status.IN_COMBAT;
        }
        if (state.command().state() == Hireable.CommandState.GUARD_POS) {
            return Status.GUARDING;
        }
        return state.command().halted() ? Status.HALTED : Status.FOLLOWING;
    }

    /** Core travel hook: this unit is the player's and currently following. */
    public static boolean travelsWith(NpcEntity unit, ServerPlayer player) {
        return HiredUnits.isOwnedBy(unit, player.getUUID()) && shouldFollow(unit) && presentOwner(unit) == player;
    }

    /** Path speed multiplier that gives the unit an effective speed of {@link #FOLLOW_SPEED}. */
    public static double followSpeedMultiplier(NpcEntity unit) {
        double base = unit.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return FOLLOW_SPEED / Math.max(base, 0.01);
    }

    /** Whether the unit can reach the owner by walking (a complete path exists). */
    public static boolean canPathTo(NpcEntity unit, ServerPlayer owner) {
        Path path = unit.getNavigation().createPath(owner, 0);
        return path != null && path.canReach();
    }

    /** Whether the teleport rule applies now: following, teleport on, no path and far enough away. */
    public static boolean shouldTeleport(NpcEntity unit, ServerPlayer owner) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.command().teleport() || !shouldFollow(unit)) {
            return false;
        }
        double min = Math.max(NpcCombat.SENSE_RANGE, TELEPORT_MIN_DISTANCE);
        return unit.distanceToSqr(owner) > min * min && !canPathTo(unit, owner);
    }

    /**
     * Moves the unit to a random safe standing spot 3-6 blocks from the owner (10 tries), else the owner's own spot
     * if that is safe, else leaves it where it is.
     *
     * @return whether it moved
     */
    public static boolean teleportNear(NpcEntity unit, ServerPlayer owner) {
        for (int i = 0; i < TELEPORT_TRIES; i++) {
            double angle = unit.getRandom().nextDouble() * Math.PI * 2;
            double radius = TELEPORT_MIN_RADIUS
                    + unit.getRandom().nextDouble() * (TELEPORT_MAX_RADIUS - TELEPORT_MIN_RADIUS);
            Vec3 spot = safeSpot(
                    unit, owner.getX() + Math.cos(angle) * radius, owner.getY(), owner.getZ() + Math.sin(angle) * radius);
            if (spot != null) {
                moveTo(unit, spot);
                return true;
            }
        }
        Vec3 spot = safeSpot(unit, owner.getX(), owner.getY(), owner.getZ());
        if (spot != null) {
            moveTo(unit, spot);
            return true;
        }
        return false;
    }

    private static void moveTo(NpcEntity unit, Vec3 spot) {
        unit.getNavigation().stop();
        unit.moveTo(spot.x, spot.y, spot.z, unit.getYRot(), unit.getXRot());
    }

    /** A standing position at (x, z) within 3 blocks of {@code refY} where the unit fits and is not in danger. */
    @Nullable
    private static Vec3 safeSpot(NpcEntity unit, double x, double refY, double z) {
        BlockPos column = BlockPos.containing(x, refY, z);
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos feet = column.above(dy);
            BlockPos below = feet.below();
            BlockState floor = unit.level().getBlockState(below);
            if (!floor.isFaceSturdy(unit.level(), below, Direction.UP)
                    || !unit.level().getFluidState(feet).isEmpty()
                    || unit.level().getBlockState(feet).is(BlockTags.FIRE)
                    || floor.is(Blocks.MAGMA_BLOCK)) {
                continue;
            }
            Vec3 pos = new Vec3(Math.floor(x) + 0.5, feet.getY(), Math.floor(z) + 0.5);
            AABB box = unit.getDimensions(unit.getPose()).makeBoundingBox(pos);
            if (unit.level().noCollision(unit, box) && !unit.level().containsAnyLiquid(box)) {
                return pos;
            }
        }
        return null;
    }
}
