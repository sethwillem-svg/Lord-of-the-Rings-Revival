package io.github.sethwillemsvg.lotrrevival.events;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.core.api.ReputationHooks;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nemesis.MeRanks;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.sevenstars.middleearth.registries.DynamicRegistriesME;
import net.sevenstars.middleearth.resources.datas.biome_events.BiomeEventData;
import net.sevenstars.middleearth.resources.datas.common.NpcRank;
import net.sevenstars.middleearth.resources.datas.factions.Faction;
import net.sevenstars.middleearth.resources.datas.factions.FactionLookup;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcType;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcTypeLookup;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is present at a site (spec Picking factions and NPC types): the NPC types of Middle-earth's {@code structure_event} entry
 * for the venue structure, else the {@code biome_event} entry of the biome, else the default entry. The tables are read (encoded
 * through their public codec), never changed. Picks the lead and opposing factions and weighted NPC types from rank pools.
 */
public final class SiteRoster {
    private static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath("middle-earth", "default");
    /** Game tests: a stub table (NPC type id -> weight) used instead of Middle-earth's. */
    public static @Nullable Map<ResourceLocation, Integer> testTable;

    /** The factions and NPC types chosen for one event. */
    public record Pick(ResourceLocation lead, ResourceLocation opposition, Sides.Side leadSide, Map<ResourceLocation, Integer> table,
                       List<ResourceLocation> present) {}

    private SiteRoster() {}

    /** NPC type id -> weight at the site. */
    public static Map<ResourceLocation, Integer> table(ServerLevel level, BlockPos pos, @Nullable ResourceLocation structure) {
        if (testTable != null) return testTable;
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        try {
            BiomeEventData data = null;
            if (structure != null) data = level.registryAccess().registryOrThrow(DynamicRegistriesME.STRUCTURE_EVENT).get(structure);
            if (data == null) {
                Holder<Biome> biome = level.getBiome(pos);
                var key = biome.unwrapKey();
                if (key.isPresent()) data = level.registryAccess().registryOrThrow(DynamicRegistriesME.BIOME_EVENT).get(key.get().location());
            }
            if (data == null) data = level.registryAccess().registryOrThrow(DynamicRegistriesME.BIOME_EVENT).get(DEFAULT);
            if (data == null) return out;
            JsonElement json = BiomeEventData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            for (JsonElement e : json.getAsJsonObject().getAsJsonArray(BiomeEventData.Fields.WILD_SPAWNS)) {
                var o = e.getAsJsonObject();
                if (!o.has("npc_type")) continue;
                ResourceLocation type = ResourceLocation.tryParse(o.get("npc_type").getAsString());
                if (type != null) out.merge(type, o.has("weight") ? o.get("weight").getAsInt() : 1, Integer::sum);
            }
        } catch (RuntimeException ex) {
            LotrLog.error("events", "reading Middle-earth's spawn table at " + pos.toShortString() + " failed", ex);
        }
        return out;
    }

    public static @Nullable ResourceLocation factionOfType(ServerLevel level, ResourceLocation type) {
        try {
            NpcType t = NpcTypeLookup.getNpcType(level, type);
            return t == null ? null : t.getFactionIdentifier();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** GOOD and EVIL factions with a LEADER pool among the table's NPC types. */
    public static List<ResourceLocation> present(ServerLevel level, Map<ResourceLocation, Integer> table) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation type : table.keySet()) {
            ResourceLocation f = factionOfType(level, type);
            if (f == null || out.contains(f)) continue;
            Faction fac = Sides.faction(level, f);
            if (Sides.sideOf(fac) != Sides.Side.NONE && MeRanks.hasLeaders(fac)) out.add(f);
        }
        return out;
    }

    /**
     * Lead faction of the wanted side (else the other side), opposing faction hostile to it (present, else sworn enemy, else any
     * hostile faction of the other side with leaders). Null when no GOOD or EVIL faction is present.
     */
    public static @Nullable Pick pick(ServerLevel level, BlockPos pos, @Nullable ResourceLocation structure, Sides.Side wantLead) {
        Map<ResourceLocation, Integer> table = table(level, pos, structure);
        List<ResourceLocation> present = present(level, table);
        if (present.isEmpty()) {
            LotrLog.warn("events", "no GOOD or EVIL faction present at {} (table of {} types): site rejected", pos.toShortString(), table.size());
            return null;
        }
        List<ResourceLocation> ofSide = present.stream().filter(f -> Sides.sideOfFaction(level, f) == wantLead).toList();
        if (ofSide.isEmpty()) ofSide = present;
        ResourceLocation lead = NemesisRandom.pick(ofSide);
        ResourceLocation opp = null;
        String how;
        List<ResourceLocation> hostile = present.stream().filter(f -> Sides.hostile(level, lead, f)).toList();
        if (!hostile.isEmpty()) {
            opp = NemesisRandom.pick(hostile);
            how = "present";
        } else {
            ResourceLocation sworn = ReputationHooks.get().swornEnemy(lead);
            if (sworn != null && MeRanks.hasLeaders(Sides.faction(level, sworn))) {
                opp = sworn;
                how = "sworn enemy";
            } else {
                Sides.Side other = Sides.sideOfFaction(level, lead).opposite();
                List<ResourceLocation> any = new ArrayList<>();
                for (Faction f : FactionLookup.getAllFactions(level))
                    if (Sides.sideOf(f) == other && MeRanks.hasLeaders(f) && Sides.hostile(level, lead, f.getId())) any.add(f.getId());
                if (!any.isEmpty()) opp = NemesisRandom.pick(any);
                how = "any hostile";
            }
        }
        if (opp == null) {
            LotrLog.dbg("events", "site {}: lead {} has no opposing faction -> rejected", pos.toShortString(), lead);
            return null;
        }
        LotrLog.dbg("events", "site factions at {}: present={} want_lead={} lead={} opposition={} ({})", pos.toShortString(), present, wantLead,
                lead, opp, how);
        return new Pick(lead, opp, Sides.sideOfFaction(level, lead), table, present);
    }

    /** One NPC type of the faction for this role (leader from LEADER, others SOLDIER and KNIGHT), weighted by the site table. */
    public static @Nullable ResourceLocation type(ServerLevel level, ResourceLocation faction, boolean leader, Map<ResourceLocation, Integer> table) {
        Faction f = Sides.faction(level, faction);
        List<ResourceLocation> pool = leader ? MeRanks.pool(f, NpcRank.LEADER) : MeRanks.pool(f, NpcRank.SOLDIER, NpcRank.KNIGHT);
        if (pool.isEmpty()) return null;
        int total = 0;
        for (ResourceLocation t : pool) total += table.getOrDefault(t, 0);
        if (total <= 0) return NemesisRandom.pick(pool);
        int r = NemesisRandom.nextInt(total);
        for (ResourceLocation t : pool) {
            r -= table.getOrDefault(t, 0);
            if (r < 0) return t;
        }
        return pool.getLast();
    }
}
