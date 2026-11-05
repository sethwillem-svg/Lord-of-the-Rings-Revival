package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renown and ranks (spec Ranks): rank follows renown, limited by seats per faction; a nemesis with the renown for a full rank
 * but no seat waits one rank below as a challenger. Incumbents keep their seats; only a challenge (Politics) unseats them.
 */
public final class Renown {
    private Renown() {}

    public static void add(MinecraftServer server, NemesisRecord r, int amount, String reason) {
        if (amount == 0 || !r.alive()) return;
        int before = r.renown;
        r.renown = Math.max(0, Math.min(NemesisConfig.i(NemesisConfig.RENOWN_CAP), r.renown + amount));
        NemesisSavedData data = NemesisSavedData.get(server);
        data.setDirty();
        LotrLog.dbg("nemesis", "renown: id={} name=\"{}\" {} -> {} (+{}) reason={} tick={}", r.shortId(), r.displayName(), before,
                r.renown, amount, reason, server.overworld().getGameTime());
        reseat(server, r.faction, reason);
    }

    /** Recomputes ranks and challenger flags for one faction's war-host. */
    public static void reseat(MinecraftServer server, ResourceLocation faction, String reason) {
        NemesisSavedData data = NemesisSavedData.get(server);
        List<NemesisRecord> live = data.living(faction);
        Map<NemesisRecord, Rank> assigned = new HashMap<>();
        Rank[] ranks = Rank.values();
        for (int i = ranks.length - 1; i >= 1; i--) {
            Rank r = ranks[i];
            final int idx = i;
            List<NemesisRecord> cands = new ArrayList<>();
            for (NemesisRecord n : live) if (!assigned.containsKey(n) && Rank.forRenown(n.renown).ordinal() >= idx) cands.add(n);
            // incumbents (already at or above this rank) first, then by renown
            cands.sort(Comparator.<NemesisRecord>comparingInt(n -> n.rank.ordinal() >= idx ? 0 : 1)
                    .thenComparing(Comparator.<NemesisRecord>comparingInt(n -> n.renown).reversed()));
            int seats = r.seats();
            for (int k = 0; k < Math.min(seats, cands.size()); k++) assigned.put(cands.get(k), r);
        }
        for (NemesisRecord n : live) {
            Rank to = assigned.getOrDefault(n, Rank.MARKED);
            boolean challenger = Rank.forRenown(n.renown).ordinal() > to.ordinal();
            if (challenger != n.challenger) {
                n.challenger = challenger;
                LotrLog.dbg("nemesis", "seat change: id={} challenger={} (deserves {}, holds {}) faction={}", n.shortId(), challenger,
                        Rank.forRenown(n.renown), to, faction);
                data.setDirty();
            }
            if (to != n.rank) setRank(server, n, to, reason);
        }
    }

    public static void setRank(MinecraftServer server, NemesisRecord n, Rank to, String reason) {
        Rank from = n.rank;
        if (from == to) return;
        n.rank = to;
        NemesisStats.rankChanges++;
        NemesisSavedData.get(server).setDirty();
        LotrLog.dbg("nemesis", "rank change: id={} name=\"{}\" faction={} {} -> {} renown={} reason={}", n.shortId(), n.displayName(),
                n.faction, from, to, n.renown, reason);
        if (to.ordinal() > from.ordinal()) {
            Sides.Side side = Sides.sideOfFaction(server.overworld(), n.faction);
            while (n.strengths.size() < to.strengths) {
                var s = NemesisTraits.rollStrength(n, side);
                if (s == null) break;
                n.strengths.add(s);
            }
            if (n.weakness == null) n.weakness = NemesisTraits.rollWeakness(n, side);
        }
        Manifestation.onRankChanged(server, n);
        NeoForge.EVENT_BUS.post(new NemesisEvents.RankChanged(n, from, to, reason));
    }
}
