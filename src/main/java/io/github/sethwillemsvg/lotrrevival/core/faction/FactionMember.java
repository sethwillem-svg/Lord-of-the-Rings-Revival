package io.github.sethwillemsvg.lotrrevival.core.faction;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/** Implemented by entities that belong to a faction. Core reads this for kills, hostility and pledge rules. */
public interface FactionMember {
    ResourceKey<Faction> faction();

    /** Base alignment value of killing this entity: civilian 1, guard/trader/smith 2, captain 5. */
    default float alignmentKillValue() {
        return 1.0f;
    }

    default boolean isCivilian() {
        return false;
    }

    /** False for summoned or converted entities, creative spawn eggs and the self-defence rule. */
    default boolean grantsAlignmentOnKill(ServerPlayer killer) {
        return true;
    }

    /** Per-type grey-zone override (for example trolls use factor 10). */
    default Optional<Suspicion> suspicion() {
        return Optional.empty();
    }
}
