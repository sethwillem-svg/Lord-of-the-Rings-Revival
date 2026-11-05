package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.server.MinecraftServer;

import java.util.EnumMap;
import java.util.Map;

/** Counters for the 600-tick {@code nemesis} summary line (spec Logging). Server thread only. */
public final class NemesisStats {
    public static int rises, deaths, escapes, cheatDeaths, ambushChecks, ambushes, bodyChecks, retreats, rolls, rankChanges, sergeants;

    private NemesisStats() {}

    public static void summary(MinecraftServer server) {
        if (!LotrLog.debug("nemesis")) return;
        NemesisSavedData d = NemesisSavedData.get(server);
        Map<NemesisRecord.Status, Integer> byStatus = new EnumMap<>(NemesisRecord.Status.class);
        for (NemesisRecord r : d.records.values()) byStatus.merge(r.status, 1, Integer::sum);
        LotrLog.dbg("nemesis", "summary tick={}: records={} bodies={} rise_rolls={} rises={} rank_changes={} escapes={} cheat_deaths={} "
                        + "deaths={} ambush_checks={} ambushes={} body_checks={} retreats={} sergeants={} guard_repaths={} guard_teleports={} "
                        + "guard_defends={}",
                server.getTickCount(), byStatus, d.manifestCount(), rolls, rises, rankChanges, escapes, cheatDeaths, deaths,
                ambushChecks, ambushes, bodyChecks, retreats, sergeants, BodyguardGoals.repaths, BodyguardGoals.teleports, BodyguardGoals.defends);
        rises = deaths = escapes = cheatDeaths = ambushChecks = ambushes = bodyChecks = retreats = rolls = rankChanges = sergeants = 0;
        BodyguardGoals.repaths = BodyguardGoals.teleports = BodyguardGoals.defends = 0;
    }
}
