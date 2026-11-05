package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.api.ReputationHooks;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.sevenstars.middleearth.exceptions.FactionIdentifierException;
import net.sevenstars.middleearth.resources.StateSaverAndLoader;
import net.sevenstars.middleearth.resources.datas.common.DispositionType;
import net.sevenstars.middleearth.resources.datas.factions.Faction;
import net.sevenstars.middleearth.resources.datas.factions.FactionLookup;
import net.sevenstars.middleearth.resources.persistent_datas.PlayerData;
import org.jetbrains.annotations.Nullable;

/**
 * Sides (spec Sides): a faction's side is its Middle-earth disposition, a player's side is the disposition of their chosen
 * faction, and the enemy check is Middle-earth diplomacy or negative add-on reputation.
 */
public final class Sides {
    public enum Side {
        GOOD, EVIL, NONE;

        public Side opposite() {
            return this == GOOD ? EVIL : this == EVIL ? GOOD : NONE;
        }
    }

    private Sides() {}

    public static @Nullable Faction faction(Level level, @Nullable ResourceLocation id) {
        if (id == null || level == null) return null;
        try {
            return FactionLookup.getFactionById(level, id);
        } catch (FactionIdentifierException | RuntimeException e) {
            return null;
        }
    }

    public static Side sideOf(@Nullable Faction f) {
        if (f == null) return Side.NONE;
        DispositionType d = f.getDisposition();
        return d == DispositionType.GOOD ? Side.GOOD : d == DispositionType.EVIL ? Side.EVIL : Side.NONE;
    }

    public static Side sideOfFaction(Level level, @Nullable ResourceLocation id) {
        return sideOf(faction(level, id));
    }

    /** Side of a faction looked up through the running server (for code without a level at hand). */
    public static Side sideOfFactionCached(ResourceLocation id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Side.NONE : sideOfFaction(server.overworld(), id);
    }

    public static @Nullable ResourceLocation playerFaction(ServerPlayer player) {
        try {
            PlayerData d = StateSaverAndLoader.getPlayerStateReadOnly(player);
            return d == null ? null : d.getFaction();
        } catch (RuntimeException e) {
            LotrLog.error("nemesis", "reading Middle-earth player data failed for " + LotrLog.id(player.getUUID()), e);
            return null;
        }
    }

    public static Side side(ServerPlayer player) {
        return sideOfFaction(player.level(), playerFaction(player));
    }

    /**
     * True when an NPC of {@code npcFaction} is this player's enemy: its diplomacy is HOSTILE to the player's chosen faction,
     * or the player's add-on reputation with it is below 0. Symmetric for good and evil players.
     */
    public static boolean isEnemy(Level level, ResourceLocation npcFaction, ServerPlayer player) {
        ResourceLocation pf = playerFaction(player);
        Faction f = faction(level, npcFaction);
        boolean diplomacy = false;
        // Middle-earth treats a null player faction as hostile to everyone; unsided players only get enemies by reputation
        if (f != null && pf != null && !pf.equals(npcFaction)) diplomacy = f.isHostileToward(pf);
        int rep = ReputationHooks.get().reputation(player, npcFaction);
        return diplomacy || rep < 0;
    }

    /** Two factions are hostile when either one's diplomacy says so. */
    public static boolean hostile(Level level, ResourceLocation a, ResourceLocation b) {
        if (a.equals(b)) return false;
        Faction fa = faction(level, a), fb = faction(level, b);
        return (fa != null && fa.isHostileToward(b)) || (fb != null && fb.isHostileToward(a));
    }
}
