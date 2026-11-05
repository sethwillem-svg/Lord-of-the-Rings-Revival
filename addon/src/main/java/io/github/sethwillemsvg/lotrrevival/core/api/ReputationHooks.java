package io.github.sethwillemsvg.lotrrevival.core.api;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player's reputation with a faction, for systems that only read it (node map, structures, ...). The hiring system owns
 * the scores and installs the real source with {@link #set}; until then every faction reads 0 (neutral).
 */
public final class ReputationHooks {
    @FunctionalInterface
    public interface Delegate {
        int reputation(ServerPlayer player, ResourceLocation faction);
    }

    private static volatile Delegate delegate;
    private static volatile boolean warned;

    private ReputationHooks() {}

    public static int reputation(ServerPlayer player, ResourceLocation faction) {
        Delegate d = delegate;
        if (d == null) {
            if (!warned) {
                warned = true;
                LotrLog.info("core", "reputation: no source installed yet; every faction reads 0");
            }
            return 0;
        }
        return d.reputation(player, faction);
    }

    public static void set(Delegate d) {
        delegate = d;
    }
}
