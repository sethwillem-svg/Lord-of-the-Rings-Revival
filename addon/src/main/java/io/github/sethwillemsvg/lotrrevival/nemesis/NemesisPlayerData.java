package io.github.sethwillemsvg.lotrrevival.nemesis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Player attachment {@code lotr_revival:nemesis_player}: the Middle-earth faction last seen (for side-switch detection) and
 * when the side last changed. Side switches make the new side's grudges dormant (spec Sides).
 */
public record NemesisPlayerData(String lastFaction, long switchTick) {
    public static final Codec<NemesisPlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("last_faction").forGetter(NemesisPlayerData::lastFaction),
            Codec.LONG.fieldOf("switch_tick").forGetter(NemesisPlayerData::switchTick)).apply(i, NemesisPlayerData::new));
    public static final NemesisPlayerData EMPTY = new NemesisPlayerData("", 0);

    /**
     * Compares the player's chosen faction with the one last seen (on login and every 30 s); on a change, updates which
     * grudges are dormant. Returns true when the side changed.
     */
    public static boolean checkSideSwitch(ServerPlayer player, NemesisSavedData data) {
        NemesisPlayerData d = player.getData(NemesisLink.PLAYER);
        var now = Sides.playerFaction(player);
        String nowStr = now == null ? "" : now.toString();
        if (nowStr.equals(d.lastFaction())) return false;
        Sides.Side before = d.lastFaction().isEmpty() ? Sides.Side.NONE : Sides.sideOfFaction(player.level(),
                net.minecraft.resources.ResourceLocation.tryParse(d.lastFaction()));
        Sides.Side after = Sides.side(player);
        long tick = player.level().getGameTime();
        player.setData(NemesisLink.PLAYER, new NemesisPlayerData(nowStr, tick));
        applyDormancy(player.getUUID(), after, data, tick);
        LotrLog.dbg("nemesis", "side switch: player={} faction {} -> {} side {} -> {} tick={}", LotrLog.id(player.getUUID()),
                d.lastFaction().isEmpty() ? "-" : d.lastFaction(), nowStr.isEmpty() ? "-" : nowStr, before, after, tick);
        return before != after;
    }

    /** Grudges held by nemeses of the player's own side are dormant; all others awake. */
    public static void applyDormancy(UUID player, Sides.Side playerSide, NemesisSavedData data, long tick) {
        int slept = 0, woke = 0;
        for (NemesisRecord r : data.records.values()) {
            NemesisRecord.Grudge g = r.grudges.get(player);
            if (g == null) continue;
            boolean dormant = playerSide != Sides.Side.NONE && Sides.sideOfFactionCached(r.faction) == playerSide;
            if (dormant != g.dormant) {
                g.dormant = dormant;
                g.lastDecay = tick;
                if (dormant) slept++;
                else woke++;
                LotrLog.dbg("nemesis", "grudge {}: id={} player={} score={}", dormant ? "dormant" : "awake", r.shortId(),
                        LotrLog.id(player), g.score);
            }
        }
        if (slept + woke > 0) data.setDirty();
    }

    /** Dormant grudges lose 1 per in-game day (24000 ticks) and are removed at 0. */
    public static void decayDormant(NemesisSavedData data, long tick) {
        for (NemesisRecord r : data.records.values()) {
            var it = r.grudges.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                NemesisRecord.Grudge g = e.getValue();
                if (!g.dormant) continue;
                long days = (tick - g.lastDecay) / 24000L;
                if (days <= 0) continue;
                int before = g.score;
                g.score -= (int) days;
                g.lastDecay += days * 24000L;
                data.setDirty();
                LotrLog.dbg("nemesis", "dormant grudge decay: id={} player={} {} -> {}", r.shortId(), LotrLog.id(e.getKey()), before,
                        Math.max(0, g.score));
                if (g.score <= 0) it.remove();
            }
        }
    }
}
