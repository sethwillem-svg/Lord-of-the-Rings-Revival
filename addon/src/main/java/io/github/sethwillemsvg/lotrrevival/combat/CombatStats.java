package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Counts of what combat decided, logged as one DBG summary line every 600 server ticks while combat debug is on. */
public final class CombatStats {
    private CombatStats() {}

    private static final int PERIOD = 600;

    public static int swings, heavySwings, misses, cancelled, hits, perfect, glances, vanillaBlocks, late, locked, behind,
            heavyBypass, npcParries, staggers, counters, presses, swingsRefusedStaggered;

    static void register() {
        NeoForge.EVENT_BUS.addListener(CombatStats::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % PERIOD != 0 || !LotrLog.debug("combat")) return;
        LotrLog.dbg("combat", "summary {}t: swings={} heavy={} hits={} misses={} cancelled={} presses={} perfect={} glances={} "
                        + "vanilla_blocks={} late={} locked={} behind={} heavy_bypass={} npc_parries={} staggers={} counters={} "
                        + "refused_staggered={} tick={}",
                PERIOD, swings, heavySwings, hits, misses, cancelled, presses, perfect, glances, vanillaBlocks, late, locked, behind,
                heavyBypass, npcParries, staggers, counters, swingsRefusedStaggered, e.getServer().getTickCount());
        reset();
    }

    static void reset() {
        swings = heavySwings = misses = cancelled = hits = perfect = glances = vanillaBlocks = late = locked = behind = 0;
        heavyBypass = npcParries = staggers = counters = presses = swingsRefusedStaggered = 0;
    }
}
