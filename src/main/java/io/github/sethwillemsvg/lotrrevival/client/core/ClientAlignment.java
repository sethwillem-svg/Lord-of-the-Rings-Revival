package io.github.sethwillemsvg.lotrrevival.client.core;

import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentRules;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.network.AlignmentChangePayload;
import io.github.sethwillemsvg.lotrrevival.core.network.AlignmentSyncPayload;
import io.github.sethwillemsvg.lotrrevival.core.network.PledgeSyncPayload;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Read-only client mirror of the local player's alignment, plus the pledged faction of players being tracked.
 * Only the payload handlers write to it. Holds plain data only (no client-only classes) so common code may read it.
 */
public final class ClientAlignment {
    /** A floating "+1.0 Gondor" line near the hotbar. */
    public record Popup(ResourceLocation faction, float delta, long createdMillis) {}

    private static final long POPUP_MILLIS = 3000L;

    private static final Map<ResourceLocation, Float> VALUES = new HashMap<>();
    private static final Map<UUID, ResourceLocation> PLEDGES = new HashMap<>();
    private static final List<Popup> POPUPS = new ArrayList<>();

    private static UUID owner;
    private static int cooldownTicks;
    private static ResourceLocation cooldownFaction;

    private ClientAlignment() {}

    /** Alignment of the local player with a faction; 0 for other players or when nothing was synced. */
    public static float get(Player player, ResourceKey<Faction> faction) {
        if (owner == null || !owner.equals(player.getUUID())) {
            return 0.0f;
        }
        return VALUES.getOrDefault(faction.location(), 0.0f);
    }

    public static FactionRank rank(Player player, ResourceKey<Faction> faction) {
        Faction f = player.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION).get(faction);
        if (f == null) {
            return AlignmentRules.NEUTRAL;
        }
        return AlignmentRules.rank(f, get(player, faction));
    }

    /** Pledged faction of the local player or of a tracked player. */
    public static Optional<ResourceKey<Faction>> pledge(UUID player) {
        ResourceLocation id = PLEDGES.get(player);
        return id == null ? Optional.empty() : Optional.of(ResourceKey.create(ModDataRegistries.FACTION, id));
    }

    public static Optional<ResourceKey<Faction>> pledge(Player player) {
        return pledge(player.getUUID());
    }

    /** Remaining pledge cooldown in ticks (as of the last sync). */
    public static int cooldownTicks() {
        return cooldownTicks;
    }

    public static Optional<ResourceKey<Faction>> cooldownFaction() {
        return cooldownFaction == null
                ? Optional.empty()
                : Optional.of(ResourceKey.create(ModDataRegistries.FACTION, cooldownFaction));
    }

    /** Popups that have not expired yet, oldest first. */
    public static synchronized List<Popup> activePopups(long nowMillis) {
        POPUPS.removeIf(p -> nowMillis - p.createdMillis() > POPUP_MILLIS);
        return List.copyOf(POPUPS);
    }

    public static synchronized void applySync(AlignmentSyncPayload payload) {
        owner = payload.owner();
        VALUES.clear();
        VALUES.putAll(payload.values());
        cooldownTicks = payload.cooldownTicks();
        cooldownFaction = payload.cooldownFaction().orElse(null);
        applyPledge(new PledgeSyncPayload(payload.owner(), payload.pledge()));
    }

    public static synchronized void applyChange(AlignmentChangePayload payload) {
        if (payload.newValue() == 0.0f) {
            VALUES.remove(payload.faction());
        } else {
            VALUES.put(payload.faction(), payload.newValue());
        }
        if (payload.delta() != 0.0f) {
            POPUPS.add(new Popup(payload.faction(), payload.delta(), System.currentTimeMillis()));
        }
    }

    public static synchronized void applyPledge(PledgeSyncPayload payload) {
        if (payload.faction().isPresent()) {
            PLEDGES.put(payload.player(), payload.faction().get());
        } else {
            PLEDGES.remove(payload.player());
        }
    }

    /** Forgets everything (on disconnect). */
    public static synchronized void clear() {
        owner = null;
        VALUES.clear();
        PLEDGES.clear();
        POPUPS.clear();
        cooldownTicks = 0;
        cooldownFaction = null;
    }
}
