package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.resources.datas.factions.Faction;
import net.sevenstars.middleearth.resources.datas.factions.FactionLookup;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An NPC type's rank, read from its faction's rank pools in Middle-earth's faction data (cached per NPC type id). A type
 * listed under several ranks (a faction reusing one unit for the higher ranks) takes the lowest. Types missing from the
 * pools fall back to words in their id, then to SOLDIER. Shared with the hiring work (spec "Rank lookup").
 */
public final class NpcRanks {
    /** Our copy of Middle-earth's rank names, so the add-on does not break if theirs moves (it is marked "to remove"). */
    public enum Rank { CIVILIAN, MILITIA, SOLDIER, KNIGHT, VETERAN, LEADER }

    private static final Map<ResourceLocation, Rank> CACHE = new ConcurrentHashMap<>();

    private NpcRanks() {}

    public static Rank of(NpcEntity npc) {
        ResourceLocation type;
        try {
            type = npc.getNpcTypeIdentifier();
        } catch (RuntimeException e) {
            type = null;
        }
        if (type == null) return Rank.SOLDIER;
        final ResourceLocation id = type;
        return CACHE.computeIfAbsent(id, k -> lookup(npc, id));
    }

    /** Forgets cached ranks (data reload). */
    public static void clear() {
        CACHE.clear();
    }

    private static Rank lookup(NpcEntity npc, ResourceLocation type) {
        Rank found = null;
        String how = "pool";
        try {
            Level level = npc.level();
            ResourceLocation factionId = npc.getNpcType() != null ? npc.getNpcType().getFactionIdentifier() : null;
            Faction faction = factionId == null ? null : FactionLookup.getFactionById(level, factionId);
            if (faction != null && faction.getAllNpcDatas() != null) {
                for (var e : faction.getAllNpcDatas().entrySet()) {
                    List<ResourceLocation> ids = e.getValue();
                    if (ids == null || !ids.contains(type)) continue;
                    Rank r = Rank.valueOf(e.getKey().name());
                    if (found == null || r.ordinal() < found.ordinal()) found = r;
                }
            }
        } catch (Exception e) {   // FactionIdentifierException, a rank name we do not know, data not loaded
            LotrLog.dbg("combat", "rank lookup failed for {}: {}", type, e.toString());
        }
        if (found == null) {
            how = "id";
            found = fromId(type.getPath());
        }
        LotrLog.dbg("combat", "rank: type={} rank={} from={}", type, found, how);
        return found;
    }

    static Rank fromId(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.matches(".*(leader|captain|chieftain|lord|king(?!s_guard)|commander).*")) return Rank.LEADER;
        if (p.matches(".*(veteran|guard|elite|warden).*")) return Rank.VETERAN;
        if (p.contains("knight")) return Rank.KNIGHT;
        if (p.contains("militia")) return Rank.MILITIA;
        if (p.matches(".*(peasant|civilian|farmer|villager|worker|smith).*")) return Rank.CIVILIAN;
        return Rank.SOLDIER;
    }
}
