package io.github.sethwillemsvg.lotrrevival.npc.routine;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcClass;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import net.minecraft.core.BlockPos;

/**
 * The day/night routine table from {@code specs/npc/npc-base.md}, as pure functions so it can be tested without
 * ticking an entity. {@link RoutineGoal} follows the {@link Plan} this class computes.
 */
public final class NpcRoutine {
    /** Ticks in a Minecraft day. */
    public static final int DAY_LENGTH = 24000;
    /** First tick of dusk. */
    public static final int DUSK_START = 12000;
    /** First tick of night. */
    public static final int NIGHT_START = 13000;

    /** Home radius used when an NPC has a home but no stored radius, civilians. */
    public static final int DEFAULT_CIVILIAN_RADIUS = 16;
    /** Home radius used when an NPC has a home but no stored radius, guards and captains. */
    public static final int DEFAULT_GUARD_RADIUS = 24;
    /** Innkeepers and captains (the Shirriff) never stray further than this from home. */
    public static final int SENTINEL_RADIUS = 6;
    /** Civilians with a home stay within this distance of it at dusk and night. */
    public static final int NIGHT_HOME_RADIUS = 4;
    /** Wild civilians wander within this distance of where night found them. */
    public static final int WILD_NIGHT_RADIUS = 8;
    /** Ticks of trying to reach home before the NPC gives up until the next dusk. */
    public static final int GIVE_UP_TICKS = 600;
    /** Distance of a traveller's walk targets, in blocks. */
    public static final int TRAVEL_MIN = 32;

    public static final int TRAVEL_MAX = 64;

    private NpcRoutine() {}

    public enum Phase {
        DAY,
        DUSK,
        NIGHT
    }

    public enum Mode {
        /** No routine; the plain idle stroll applies. */
        FREE,
        /** Walk toward random points far away. */
        TRAVEL,
        /** Stay within a radius of the home point. */
        AROUND_HOME,
        /** Stay within a radius of the spot where this mode started. */
        AROUND_SPOT
    }

    /**
     * What an NPC should be doing.
     *
     * @param radius allowed distance from the centre in blocks
     * @param homePhase true while the NPC should be walking home (dusk and night for home-bound civilians)
     */
    public record Plan(Mode mode, int radius, boolean homePhase) {
        public static final Plan FREE = new Plan(Mode.FREE, 0, false);

        public boolean isFree() {
            return mode == Mode.FREE;
        }
    }

    /** The phase for a time of day in ticks (any value; it is wrapped to a day). */
    public static Phase phaseAt(long dayTime) {
        long t = Math.floorMod(dayTime, (long) DAY_LENGTH);
        if (t < DUSK_START) {
            return Phase.DAY;
        }
        return t < NIGHT_START ? Phase.DUSK : Phase.NIGHT;
    }

    /** The first dusk strictly after {@code dayTime}, in the same absolute day-time units. */
    public static long nextDusk(long dayTime) {
        long dusk = Math.floorDiv(dayTime, (long) DAY_LENGTH) * DAY_LENGTH + DUSK_START;
        return dayTime >= dusk ? dusk + DAY_LENGTH : dusk;
    }

    /** The home radius in use: the stored one, else the class default. */
    public static int effectiveHomeRadius(NpcEntity npc) {
        if (npc.homeRadius() > 0) {
            return npc.homeRadius();
        }
        return npc.npcClass() == NpcClass.CIVILIAN ? DEFAULT_CIVILIAN_RADIUS : DEFAULT_GUARD_RADIUS;
    }

    /** Innkeepers and captains (the Shirriff) keep close to home at all times. */
    public static boolean isSentinel(NpcEntity npc) {
        return npc.npcClass() == NpcClass.CAPTAIN
                || (npc.npcClass() == NpcClass.CIVILIAN && npc.getRole() == NpcRole.INNKEEPER);
    }

    /** The routine table. Homeless guards, captains and innkeepers wander freely at all times. */
    public static Plan plan(NpcEntity npc, Phase phase) {
        boolean civilian = npc.npcClass() == NpcClass.CIVILIAN;
        if (npc.homePos().isEmpty()) {
            if (!civilian || isSentinel(npc)) {
                return Plan.FREE;
            }
            if (phase == Phase.NIGHT) {
                return new Plan(Mode.AROUND_SPOT, WILD_NIGHT_RADIUS, false);
            }
            if (npc.getRole() == NpcRole.TRAVELLER) {
                return new Plan(Mode.TRAVEL, 0, false);
            }
            return Plan.FREE;
        }
        if (isSentinel(npc)) {
            return new Plan(Mode.AROUND_HOME, SENTINEL_RADIUS, false);
        }
        if (!civilian) {
            return new Plan(Mode.AROUND_HOME, effectiveHomeRadius(npc), false);
        }
        if (phase == Phase.DAY) {
            return new Plan(Mode.AROUND_HOME, effectiveHomeRadius(npc), false);
        }
        return new Plan(Mode.AROUND_HOME, NIGHT_HOME_RADIUS, true);
    }

    /** Convenience for tests and callers that only know the home. */
    public static boolean isNearHome(NpcEntity npc, double blocks) {
        BlockPos home = npc.homePos().orElse(null);
        if (home == null) {
            return false;
        }
        double dx = npc.getX() - (home.getX() + 0.5);
        double dz = npc.getZ() - (home.getZ() + 0.5);
        return dx * dx + dz * dz <= blocks * blocks;
    }
}
