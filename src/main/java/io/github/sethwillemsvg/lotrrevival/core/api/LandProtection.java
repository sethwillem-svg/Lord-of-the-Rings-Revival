package io.github.sethwillemsvg.lotrrevival.core.api;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Holder for the single land-protection implementation. Progression registers it; consumers (World waypoint
 * creation, NPC spawner, invasions, block-breaking AI) call the static methods. Until an implementation is
 * registered everything is allowed.
 */
public final class LandProtection {
    private static final Impl NONE = new Impl() {};
    private static volatile Impl current = NONE;

    private LandProtection() {}

    /** The queries Progression answers. Every default allows the action. */
    public interface Impl {
        /** True if the player may break or place blocks at the position (Full permission). */
        default boolean canEdit(Level level, BlockPos pos, Player player) {
            return true;
        }

        /** True if protection at the position stops the faction's NPCs from spawning there. */
        default boolean blocksFactionSpawn(Level level, BlockPos pos, ResourceKey<Faction> faction) {
            return false;
        }

        /** True if protection at the position keeps the faction's invasions out. */
        default boolean blocksInvasion(Level level, BlockPos pos, ResourceKey<Faction> faction) {
            return false;
        }

        /** True if protection at the position stops the faction's NPCs from breaking or placing blocks. */
        default boolean blocksNpcGriefing(Level level, BlockPos pos, ResourceKey<Faction> faction) {
            return false;
        }
    }

    /** Registers the implementation (Progression only). Throws if one is already registered. */
    public static synchronized void register(Impl impl) {
        Objects.requireNonNull(impl);
        if (current != NONE) {
            throw new IllegalStateException("A LandProtection implementation is already registered");
        }
        current = impl;
    }

    /** Test hook: replaces the implementation (null restores the defaults) and returns the previous one or null. */
    public static synchronized Impl swapForTesting(Impl impl) {
        Impl previous = current;
        current = impl == null ? NONE : impl;
        return previous == NONE ? null : previous;
    }

    public static boolean canEdit(Level level, BlockPos pos, Player player) {
        return current.canEdit(level, pos, player);
    }

    public static boolean blocksFactionSpawn(Level level, BlockPos pos, ResourceKey<Faction> faction) {
        return current.blocksFactionSpawn(level, pos, faction);
    }

    public static boolean blocksInvasion(Level level, BlockPos pos, ResourceKey<Faction> faction) {
        return current.blocksInvasion(level, pos, faction);
    }

    public static boolean blocksNpcGriefing(Level level, BlockPos pos, ResourceKey<Faction> faction) {
        return current.blocksNpcGriefing(level, pos, faction);
    }
}
