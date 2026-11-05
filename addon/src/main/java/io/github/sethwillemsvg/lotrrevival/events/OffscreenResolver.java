package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nemesis.DeathRules;
import io.github.sethwillemsvg.lotrrevival.nemesis.Manifestation;
import io.github.sethwillemsvg.lotrrevival.nemesis.MeRanks;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisApi;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisConfig;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRecord;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.resources.datas.common.NpcRank;

/**
 * Off-screen resolution (spec Outcomes): power per side = sum over its NPCs (militia or soldier 1, knight or veteran 2, leader 3,
 * nemesis 3 + rank index) + 1d10. The losing side's nemeses die with the normal cheat-death rules; nobody gets a win bonus.
 */
public final class OffscreenResolver {
    private OffscreenResolver() {}

    public static int npcPower(Entity e) {
        NemesisRecord r = NemesisApi.bodyOf(e);
        if (r != null) return 3 + r.rank.ordinal();
        if (!(e instanceof NpcEntity npc)) return 1;
        NpcRank rank = MeRanks.rankOf(npc);
        if (rank == null) return 1;
        return switch (rank) {
            case KNIGHT, VETERAN -> 2;
            case LEADER -> 3;
            default -> 1;
        };
    }

    public static WorldEvent.Outcome resolve(ServerLevel level, WorldEvent ev) {
        int lead = 0, opp = 0;
        for (var en : ev.npcs.entrySet()) {
            Entity e = level.getEntity(en.getKey());
            int p = e == null ? 1 : npcPower(e);
            if (en.getValue().equals(EventParticipant.LEAD)) lead += p;
            else opp += p;
        }
        int rl = NemesisRandom.between(1, 10), ro = NemesisRandom.between(1, 10);
        WorldEvent.Outcome o = lead + rl >= opp + ro ? WorldEvent.Outcome.LEAD_WON : WorldEvent.Outcome.OPPOSITION_WON;
        LotrLog.dbg("events", "off-screen: event={} lead power {}+{}={} opposition {}+{}={} -> {}", ev.shortId(), lead, rl, lead + rl, opp, ro,
                opp + ro, o);
        String loser = o == WorldEvent.Outcome.LEAD_WON ? EventParticipant.OPPOSITION : EventParticipant.LEAD;
        NemesisSavedData data = NemesisSavedData.get(level.getServer());
        for (var en : ev.nemeses.entrySet()) {
            if (!en.getValue().equals(loser)) continue;
            NemesisRecord r = data.find(en.getKey());
            if (r != null && r.alive()) nemesisFalls(level, r);
        }
        return o;
    }

    /** Normal cheat-death rules for a nemesis that falls off-screen. */
    public static void nemesisFalls(ServerLevel level, NemesisRecord r) {
        if (Manifestation.body(r.id) != null) Manifestation.retreat(level.getServer(), r, "fell off-screen");
        if (r.cheatDeaths >= 2) {
            DeathRules.die(level.getServer(), r, 1, "fell in an off-screen event (cheated death twice)", null);
            return;
        }
        double chance = NemesisConfig.listAt(NemesisConfig.CHEAT_DEATH_CHANCES, r.cheatDeaths, 0);
        double roll = NemesisRandom.roll();
        LotrLog.dbg("events", "off-screen cheat-death: id={} chance={} roll={}", r.shortId(), chance, String.format("%.2f", roll));
        if (roll < chance) {
            r.cheatDeaths++;
            NemesisSavedData.get(level.getServer()).setDirty();
        } else {
            DeathRules.die(level.getServer(), r, 1, "fell in an off-screen event", null);
        }
    }
}
