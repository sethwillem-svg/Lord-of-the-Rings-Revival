package io.github.sethwillemsvg.lotrrevival.npc.interact;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Suspicion;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

/**
 * How an NPC regards a player, from the player's alignment with the NPC's faction. Pure functions plus two small
 * lookups; safe to call on both sides because it touches no client-only classes.
 */
public final class NpcStanding {
    private NpcStanding() {}

    /** Red, white or green; the nameplate colour is {@link #rgb()}. */
    public enum Tone {
        HOSTILE(0xFF5555),
        NEUTRAL(0xFFFFFF),
        FRIENDLY(0x55FF55);

        private final int rgb;

        Tone(int rgb) {
            this.rgb = rgb;
        }

        public int rgb() {
            return rgb;
        }
    }

    /**
     * The nameplate tone: red if {@code a < hostilityThreshold}; white if {@code a <= 0} or inside the grey zone
     * (below the suspicion ceiling); green otherwise.
     */
    public static Tone tone(float a, float hostilityThreshold, Optional<Suspicion> suspicion) {
        if (a < hostilityThreshold) {
            return Tone.HOSTILE;
        }
        if (a <= 0.0f || (suspicion.isPresent() && a < suspicion.get().ceiling())) {
            return Tone.NEUTRAL;
        }
        return Tone.FRIENDLY;
    }

    public static Tone tone(float a, Faction faction) {
        return tone(a, faction.hostilityThreshold(), faction.suspicion());
    }

    @Nullable
    public static Faction faction(NpcEntity npc) {
        return npc.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION).get(npc.faction());
    }

    /** The player's alignment with the NPC's faction. Works on both sides. */
    public static float alignment(NpcEntity npc, Player player) {
        return Alignment.get(player, npc.faction());
    }

    /**
     * Hostile for talking: alignment below the faction's hostility threshold, or the NPC is targeting the player.
     * Creative and spectator players are never hostile.
     */
    public static boolean isHostile(NpcEntity npc, Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (npc.getTarget() == player) {
            return true;
        }
        Faction faction = faction(npc);
        float threshold = faction == null ? 0.0f : faction.hostilityThreshold();
        return alignment(npc, player) < threshold;
    }
}
