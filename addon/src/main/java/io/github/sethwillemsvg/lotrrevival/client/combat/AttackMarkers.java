package io.github.sethwillemsvg.lotrrevival.client.combat;

import io.github.sethwillemsvg.lotrrevival.combat.NpcAttackPayload;
import io.github.sethwillemsvg.lotrrevival.combat.Parry;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The client's live attack warnings, keyed by attacker entity id: added by NpcAttackPayload, removed by NpcAttackEndPayload
 * or after the hit tick plus 2. Client thread only. Ticks are the client level's game time.
 */
public final class AttackMarkers {
    private AttackMarkers() {}

    /**
     * One swing. closeTick is when the ring meets the diamond: the ideal press, windup - W/2 - rtt/2 ticks after arrival
     * (W the local player's perfect window for what it holds at arrival).
     */
    public record Marker(int entityId, int targetId, boolean heavy, long arrived, int windup, float closeTick, long expires) {}

    private static final Map<Integer, Marker> MARKERS = new HashMap<>();
    private static final int SUMMARY_PERIOD = 600;
    static int received, ended, expired, capFrames, maxShown;
    private static int ticks;

    public static void add(NpcAttackPayload p, long now) {
        Minecraft mc = Minecraft.getInstance();
        int window = mc.player == null ? 0 : Parry.windowFor(mc.player);
        float rttTicks = rttMs() / 50f;
        float close = now + p.windupTicks() - window / 2f - rttTicks / 2f;
        MARKERS.put(p.entityId(), new Marker(p.entityId(), p.targetId(), p.heavy(), now, p.windupTicks(), Math.max(now, close),
                now + p.windupTicks() + 2));
        received++;
    }

    public static void remove(int entityId) {
        if (MARKERS.remove(entityId) != null) ended++;
    }

    static Collection<Marker> all() {
        return MARKERS.values();
    }

    static void clear() {
        MARKERS.clear();
    }

    /** Ring progress 0 (wide) to 1 (closed on the diamond) at a partial game time. */
    static float progress(Marker m, double time) {
        double span = m.closeTick() - m.arrived();
        if (span <= 0) return 1f;
        return (float) Math.max(0, Math.min(1, (time - m.arrived()) / span));
    }

    /** This client's round trip in milliseconds, from the player list. */
    static int rttMs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    /** Client tick: drop expired markers; the DBG summary every 600 ticks. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            MARKERS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        MARKERS.values().removeIf(m -> {
            boolean old = now > m.expires();
            if (old) expired++;
            return old;
        });
        if (++ticks % SUMMARY_PERIOD == 0 && LotrLog.debug("combat")) {
            LotrLog.dbg("combat", "client summary {}t: markers received={} ended={} expired={} cap_frames={} max_shown={} live={} rtt={}ms",
                    SUMMARY_PERIOD, received, ended, expired, capFrames, maxShown, MARKERS.size(), rttMs());
            received = ended = expired = capFrames = maxShown = 0;
        }
    }
}
