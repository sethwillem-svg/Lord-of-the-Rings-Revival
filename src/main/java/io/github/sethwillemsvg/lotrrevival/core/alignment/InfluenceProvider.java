package io.github.sethwillemsvg.lotrrevival.core.alignment;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/** Reserved for the area-of-influence spec: scales kill bonuses and spread penalties. Default returns 1.0. */
@FunctionalInterface
public interface InfluenceProvider {
    InfluenceProvider DEFAULT = (player, faction) -> 1.0f;

    /** Multiplier between 0 and 1. */
    float multiplier(ServerPlayer player, ResourceKey<Faction> faction);
}
