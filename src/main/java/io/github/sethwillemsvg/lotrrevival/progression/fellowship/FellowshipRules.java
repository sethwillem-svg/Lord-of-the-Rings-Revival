package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The read-only fellowship query surface. The shapes match the planned {@code core.api.FellowshipQuery} facade
 * ({@code preventsPvp(UUID, UUID)} and {@code protectsUnits(UUID, UUID)}); ticket #133 registers these as the
 * implementation once Core's facade lands (#105). Two different players are covered when they share at least one
 * fellowship with the option on; a player is never "protected" from themselves here.
 */
public final class FellowshipRules {
    private FellowshipRules() {}

    /** Members of a shared fellowship with Prevent PvP on cannot hurt each other. */
    public static boolean preventsPvp(UUID a, UUID b) {
        return currentServer().map(server -> preventsPvp(server, a, b)).orElse(false);
    }

    /** Members of a shared fellowship with Prevent hired-unit friendly fire on cannot hurt each other's units. */
    public static boolean protectsUnits(UUID a, UUID b) {
        return currentServer().map(server -> protectsUnits(server, a, b)).orElse(false);
    }

    public static boolean preventsPvp(MinecraftServer server, UUID a, UUID b) {
        return shared(server, a, b, FellowshipOption.PREVENT_PVP);
    }

    public static boolean protectsUnits(MinecraftServer server, UUID a, UUID b) {
        return shared(server, a, b, FellowshipOption.PREVENT_UNIT_FRIENDLY_FIRE);
    }

    private static boolean shared(MinecraftServer server, UUID a, UUID b, FellowshipOption option) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        for (Fellowship fellowship : FellowshipData.get(server).of(a)) {
            if (fellowship.isMember(b) && fellowship.option(option)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<MinecraftServer> currentServer() {
        return Optional.ofNullable(ServerLifecycleHooks.getCurrentServer());
    }
}
