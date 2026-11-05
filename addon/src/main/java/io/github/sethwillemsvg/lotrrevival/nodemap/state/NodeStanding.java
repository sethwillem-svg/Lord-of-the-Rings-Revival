package io.github.sethwillemsvg.lotrrevival.nodemap.state;

import io.github.sethwillemsvg.lotrrevival.core.api.ReputationHooks;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player's reputation with a node's faction. The live one reads the hiring system's scores through
 * {@link ReputationHooks}; tests use {@link #fixed} so they do not depend on hiring.
 */
@FunctionalInterface
public interface NodeStanding {
    NodeStanding LIVE = ReputationHooks::reputation;

    int reputation(ServerPlayer player, ResourceLocation faction);

    /** Fixed scores; factions not in the map read {@code otherwise}. */
    static NodeStanding fixed(Map<ResourceLocation, Integer> scores, int otherwise) {
        return (p, f) -> scores.getOrDefault(f, otherwise);
    }
}
