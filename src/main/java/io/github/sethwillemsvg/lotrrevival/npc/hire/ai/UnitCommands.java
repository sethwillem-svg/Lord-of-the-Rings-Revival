package io.github.sethwillemsvg.lotrrevival.npc.hire.ai;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitTask;
import io.github.sethwillemsvg.lotrrevival.npc.interact.Hireable;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side commands on a hired unit, used by the command screen, the horns and the command sword. Squadron
 * matching is the caller's job; the horn commands ({@link #halt}, {@link #ready}, {@link #summon}) skip units that
 * horns do not reach (guarding units and Farmer-task units) and say so in their result.
 */
public final class UnitCommands {
    public static final int MIN_GUARD_RANGE = 1;
    public static final int MAX_GUARD_RANGE = 64;

    private UnitCommands() {}

    /** Whether horns and the command sword can order this unit: hired, not guarding, not a Farmer-task unit. */
    public static boolean reachedByHorns(NpcEntity unit) {
        HiredUnit state = HiredUnits.get(unit);
        return state.isHired()
                && state.task() != UnitTask.FARMER
                && state.command().state() != Hireable.CommandState.GUARD_POS;
    }

    /**
     * Guard mode on takes the unit's current position as the guard point with the given range (clamped to 1-64) and
     * clears a halt; off goes back to following.
     *
     * @return false if the unit is not hired
     */
    public static boolean setGuard(NpcEntity unit, boolean on, int range) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired()) {
            return false;
        }
        HiredUnit.Command old = state.command();
        int clamped = Math.max(MIN_GUARD_RANGE, Math.min(MAX_GUARD_RANGE, range));
        HiredUnit.Command command = on
                ? new HiredUnit.Command(
                        Hireable.CommandState.GUARD_POS,
                        Optional.of(unit.blockPosition()),
                        clamped,
                        old.teleport(),
                        false,
                        old.swordOrder())
                : new HiredUnit.Command(
                        Hireable.CommandState.FOLLOW,
                        Optional.empty(),
                        old.guardRange(),
                        old.teleport(),
                        old.halted(),
                        old.swordOrder());
        HiredUnits.set(unit, state.withCommand(command));
        unit.getNavigation().stop();
        return true;
    }

    /** Changes only the guard range of a guarding unit (the point stays). */
    public static boolean setGuardRange(NpcEntity unit, int range) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired()) {
            return false;
        }
        HiredUnit.Command old = state.command();
        int clamped = Math.max(MIN_GUARD_RANGE, Math.min(MAX_GUARD_RANGE, range));
        HiredUnits.set(
                unit,
                state.withCommand(new HiredUnit.Command(
                        old.state(), old.guardPos(), clamped, old.teleport(), old.halted(), old.swordOrder())));
        return true;
    }

    /** Turns automatic teleporting to the owner on or off. */
    public static boolean setTeleport(NpcEntity unit, boolean teleport) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired()) {
            return false;
        }
        HiredUnit.Command old = state.command();
        HiredUnits.set(
                unit,
                state.withCommand(new HiredUnit.Command(
                        old.state(), old.guardPos(), old.guardRange(), teleport, old.halted(), old.swordOrder())));
        return true;
    }

    /**
     * Halt: the unit stands still, drops its target and stops seeking new ones (it still fights back when hit).
     *
     * @return whether the unit was halted (false if horns do not reach it)
     */
    public static boolean halt(NpcEntity unit) {
        if (!reachedByHorns(unit)) {
            return false;
        }
        HiredUnit state = HiredUnits.get(unit);
        HiredUnit.Command old = state.command();
        HiredUnits.set(
                unit,
                state.withCommand(new HiredUnit.Command(
                        old.state(), old.guardPos(), old.guardRange(), old.teleport(), true, false)));
        unit.retaliation().clear();
        unit.setTarget(null);
        unit.getNavigation().stop();
        return true;
    }

    /**
     * Ready: a halted unit resumes following.
     *
     * @return whether the unit was reached (false if horns do not reach it)
     */
    public static boolean ready(NpcEntity unit) {
        if (!reachedByHorns(unit)) {
            return false;
        }
        HiredUnit state = HiredUnits.get(unit);
        HiredUnit.Command old = state.command();
        HiredUnits.set(
                unit,
                state.withCommand(new HiredUnit.Command(
                        old.state(), old.guardPos(), old.guardRange(), old.teleport(), false, old.swordOrder())));
        return true;
    }

    /**
     * Summon: the unit teleports to a safe spot 3-6 blocks from its owner (the owner's own spot as failsafe). Needs
     * the owner online, alive and in the unit's level.
     *
     * @return whether the unit moved
     */
    public static boolean summon(NpcEntity unit) {
        if (!reachedByHorns(unit)) {
            return false;
        }
        ServerPlayer owner = UnitAi.presentOwner(unit);
        return owner != null && UnitAi.teleportNear(unit, owner);
    }
}
