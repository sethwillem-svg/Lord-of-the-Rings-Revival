package io.github.sethwillemsvg.lotrrevival.core.table;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;

/** The faction table use gate: alignment with the table's faction of at least {@link #REQUIRED_ALIGNMENT}. */
public final class FactionTableAccess {
    public static final float REQUIRED_ALIGNMENT = 1.0f;

    private static final Set<ResourceKey<Faction>> WARNED = new HashSet<>();

    private FactionTableAccess() {}

    /**
     * Creative and spectator players always pass. Everyone else needs alignment >= +1.0 with the faction; if the
     * faction no longer exists (a datapack removed it) nobody else passes and one warning is logged per faction.
     */
    public static boolean canUse(Player player, ResourceKey<Faction> faction) {
        if (player.isCreative() || player.isSpectator()) {
            return true;
        }
        boolean known = player.level()
                .registryAccess()
                .registry(ModDataRegistries.FACTION)
                .map(registry -> registry.containsKey(faction))
                .orElse(false);
        if (!known) {
            synchronized (WARNED) {
                if (WARNED.add(faction)) {
                    LOTRRevival.LOGGER.warn(
                            "Faction crafting table refuses non-creative players: unknown faction {}",
                            faction.location());
                }
            }
            return false;
        }
        return Alignment.get(player, faction) >= REQUIRED_ALIGNMENT;
    }
}
