package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Off-screen politics (spec Politics): every 30 minutes, per faction with 2+ AT_LARGE nemeses, 60% chance of a challenge
 * (weight 3) or an ascension (weight 1). Also fading, the over-seat Marked cull and the DEAD purge.
 */
public final class Politics {
    private Politics() {}

    public static void tick(MinecraftServer server) {
        NemesisSavedData data = NemesisSavedData.get(server);
        long tick = server.getTickCount();
        long period = NemesisConfig.i(NemesisConfig.POLITICS_PERIOD_MINUTES) * 1200L;
        if (data.politicsNextTick < 0 || data.politicsNextTick > tick + period) {
            data.politicsNextTick = tick + period;
            data.setDirty();
        }
        if (tick < data.politicsNextTick) return;
        data.politicsNextTick = tick + period;
        data.setDirty();
        round(server);
    }

    public static void round(MinecraftServer server) {
        NemesisSavedData data = NemesisSavedData.get(server);
        NemesisPlayerData.decayDormant(data, server.overworld().getGameTime());
        Set<ResourceLocation> factions = new LinkedHashSet<>();
        for (NemesisRecord r : data.records.values()) factions.add(r.faction);
        LotrLog.dbg("nemesis", "politics round: factions={} tick={}", factions.size(), server.overworld().getGameTime());
        for (ResourceLocation f : factions) {
            try {
                fade(server, f);
                if (!MeRanks.nemesisFaction(Sides.faction(server.overworld(), f))) continue;
                List<NemesisRecord> free = atLarge(data, f);
                if (free.size() < 2) continue;
                double roll = NemesisRandom.roll();
                double chance = NemesisConfig.d(NemesisConfig.POLITICS_CHANCE);
                if (roll >= chance) {
                    LotrLog.dbg("nemesis", "politics: faction={} roll={} >= {} -> nothing", f, String.format("%.2f", roll), chance);
                    continue;
                }
                int cw = NemesisConfig.i(NemesisConfig.CHALLENGE_WEIGHT), aw = NemesisConfig.i(NemesisConfig.ASCENSION_WEIGHT);
                boolean challenge = NemesisRandom.nextInt(Math.max(1, cw + aw)) < cw;
                if (!challenge || !challenge(server, f)) ascension(server, f);
            } catch (RuntimeException e) {
                LotrLog.error("nemesis", "politics failed for " + f, e);
            }
        }
        purge(server);
    }

    private static List<NemesisRecord> atLarge(NemesisSavedData data, ResourceLocation f) {
        List<NemesisRecord> out = new ArrayList<>();
        for (NemesisRecord r : data.living(f)) if (r.status == NemesisRecord.Status.AT_LARGE) out.add(r);
        return out;
    }

    /** A challenger fights the lowest-renown holder of the rank above. Returns false when there is no pairing. */
    public static boolean challenge(MinecraftServer server, ResourceLocation faction) {
        NemesisSavedData data = NemesisSavedData.get(server);
        List<NemesisRecord> free = atLarge(data, faction);
        List<NemesisRecord> challengers = free.stream().filter(r -> r.challenger).toList();
        if (challengers.isEmpty()) {
            LotrLog.dbg("nemesis", "politics: faction={} no challenger -> ascension instead", faction);
            return false;
        }
        NemesisRecord c = NemesisRandom.pick(challengers);
        Rank above = Rank.values()[Math.min(Rank.values().length - 1, c.rank.ordinal() + 1)];
        NemesisRecord holder = free.stream().filter(r -> r != c && r.rank == above).min(Comparator.comparingInt(r -> r.renown)).orElse(null);
        if (holder == null) {
            LotrLog.dbg("nemesis", "politics: faction={} challenger {} finds no AT_LARGE {} to fight", faction, c.shortId(), above);
            return false;
        }
        return fight(server, c, holder);
    }

    /** Power = renown + 1d30 each; the winner takes the rank and +8 renown; the loser is DEAD. */
    public static boolean fight(MinecraftServer server, NemesisRecord challenger, NemesisRecord holder) {
        int pc = challenger.renown + NemesisRandom.between(1, 30), ph = holder.renown + NemesisRandom.between(1, 30);
        NemesisRecord win = pc > ph ? challenger : holder, lose = win == challenger ? holder : challenger;
        Rank seat = holder.rank;
        LotrLog.dbg("nemesis", "challenge: faction={} challenger={} ({} renown, power {}) vs holder={} ({} {} renown, power {}) -> winner={}",
                challenger.faction, challenger.shortId(), challenger.renown, pc, holder.shortId(), holder.rank, holder.renown, ph, win.shortId());
        DeathRules.die(server, lose, 3, "lost a challenge to " + win.shortId(), null);
        if (win == challenger) Renown.setRank(server, win, seat, "won challenge");
        if (win.rank.ordinal() >= Rank.COMMANDER.ordinal() && win.weakness != null
                && NemesisRandom.chance(NemesisConfig.d(NemesisConfig.WEAKNESS_LOSS_CHANCE))) {
            LotrLog.dbg("nemesis", "challenge: winner {} lost its weakness {} (new one at next rank)", win.shortId(), win.weakness);
            win.weakness = null;
        }
        Renown.add(server, win, NemesisConfig.i(NemesisConfig.RENOWN_CHALLENGE), "won challenge");
        Component line = Component.translatable("lotr_revival.nemesis.rumour.challenge", challenger.displayName(), holder.displayName(),
                Component.translatable(win == challenger ? "lotr_revival.nemesis.rumour.took_place" : "lotr_revival.nemesis.rumour.was_slain"));
        DeathRules.rumour(server, challenger, line);
        DeathRules.rumour(server, holder, line);
        return true;
    }

    public static void ascension(MinecraftServer server, ResourceLocation faction) {
        List<NemesisRecord> free = atLarge(NemesisSavedData.get(server), faction);
        if (free.isEmpty()) return;
        NemesisRecord r = NemesisRandom.pick(free);
        LotrLog.dbg("nemesis", "ascension: faction={} id={}", faction, r.shortId());
        Renown.add(server, r, NemesisConfig.i(NemesisConfig.RENOWN_ASCENSION), "ascension");
    }

    /** Rule 4: no non-dormant grudge holder online for fade_days real days, or the lowest Marked over the seat count. */
    public static void fade(MinecraftServer server, ResourceLocation faction) {
        NemesisSavedData data = NemesisSavedData.get(server);
        long limit = NemesisConfig.i(NemesisConfig.FADE_DAYS) * 86_400_000L, now = System.currentTimeMillis();
        for (NemesisRecord r : data.living(faction)) {
            if (r.status == NemesisRecord.Status.AT_LARGE && now - r.lastHolderOnlineMs > limit)
                DeathRules.die(server, r, 4, "faded: no grudge holder online for " + (now - r.lastHolderOnlineMs) / 86_400_000L + " days", null);
        }
        List<NemesisRecord> marked = new ArrayList<>(data.living(faction).stream().filter(r -> r.rank == Rank.MARKED).toList());
        int seats = Rank.MARKED.seats();
        marked.sort(Comparator.comparingInt(r -> r.renown));
        while (marked.size() > seats) {
            NemesisRecord low = marked.removeFirst();
            if (low.status == NemesisRecord.Status.MANIFEST) continue;
            DeathRules.die(server, low, 4, "faded: lowest Marked over " + seats + " seats", null);
        }
    }

    /** DEAD records are kept 7 in-game days for the screen, then removed. */
    public static void purge(MinecraftServer server) {
        NemesisSavedData data = NemesisSavedData.get(server);
        long now = server.overworld().getGameTime(), keep = NemesisConfig.i(NemesisConfig.DEAD_KEEP_DAYS) * 24000L;
        int before = data.records.size();
        data.records.values().removeIf(r -> r.status == NemesisRecord.Status.DEAD && now - r.deadSinceTick > keep);
        if (data.records.size() != before) {
            data.setDirty();
            LotrLog.dbg("nemesis", "purged {} DEAD record(s)", before - data.records.size());
        }
    }
}
