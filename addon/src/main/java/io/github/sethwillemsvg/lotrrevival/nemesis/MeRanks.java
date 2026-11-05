package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.api.HiredUnits;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.registries.DynamicRegistriesME;
import net.sevenstars.middleearth.resources.datas.common.NpcRank;
import net.sevenstars.middleearth.resources.datas.factions.Faction;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Middle-earth ranks of NPC types (from the faction's rank pools) and who may rise as a nemesis (spec Who can rise). */
public final class MeRanks {
    public static final TagKey<NpcType> NEVER_NEMESIS = TagKey.create(DynamicRegistriesME.NPC_TYPE, LotrRevival.id("never_nemesis"));
    /** Game tests: type ids treated as tagged {@code never_nemesis}. */
    public static final Set<ResourceLocation> TEST_NEVER = new HashSet<>();
    /** Entity tag on bodyguards and event-spawned NPCs that must never roll a rise. */
    public static final String BODYGUARD_TAG = "lotr_revival.no_rise";

    private MeRanks() {}

    /** The highest rank pool of the faction that lists this type, or null. */
    public static @Nullable NpcRank rankOf(@Nullable Faction faction, @Nullable ResourceLocation type) {
        if (faction == null || type == null) return null;
        NpcRank best = null;
        for (Map.Entry<NpcRank, List<ResourceLocation>> e : faction.getAllNpcDatas().entrySet()) {
            if (e.getValue() != null && e.getValue().contains(type) && (best == null || e.getKey().ordinal() > best.ordinal())) best = e.getKey();
        }
        return best;
    }

    public static @Nullable NpcRank rankOf(NpcEntity npc) {
        return rankOf(Sides.faction(npc.level(), npc.getFactionIdentifier()), npc.getNpcTypeIdentifier());
    }

    public static List<ResourceLocation> pool(@Nullable Faction faction, NpcRank... ranks) {
        List<ResourceLocation> out = new ArrayList<>();
        if (faction == null) return out;
        for (NpcRank r : ranks) {
            List<ResourceLocation> l = faction.getAllNpcDatas().get(r);
            if (l != null) for (ResourceLocation id : l) if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    public static boolean hasLeaders(@Nullable Faction f) {
        return !pool(f, NpcRank.LEADER).isEmpty();
    }

    /** A faction that can have nemeses: GOOD or EVIL, has a LEADER pool, not disabled. */
    public static boolean nemesisFaction(@Nullable Faction f) {
        return f != null && Sides.sideOf(f) != Sides.Side.NONE && hasLeaders(f) && !NemesisConfig.factionDisabled(f.getId());
    }

    public static boolean neverNemesis(Level level, ResourceLocation type) {
        if (TEST_NEVER.contains(type)) return true;
        try {
            return level.registryAccess().registryOrThrow(DynamicRegistriesME.NPC_TYPE)
                    .getHolder(ResourceKey.create(DynamicRegistriesME.NPC_TYPE, type)).map(h -> h.is(NEVER_NEMESIS)).orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Null when this NPC may rise; otherwise the reason it may not (logged by the caller). */
    public static @Nullable String whyNotEligible(NpcEntity npc) {
        if (!NemesisConfig.enabled()) return "disabled";
        if (!NemesisConfig.dimensionAllowed(npc.level())) return "dimension " + npc.level().dimension().location();
        if (NemesisLink.of(npc) != null) return "already a nemesis body";
        if (HiredUnits.isUnit(npc)) return "hired unit";
        if (npc.getStructureManagerHostPos() != null) return "structure-bound";
        if (npc.hasCustomName()) return "named";
        if (npc.getTags().contains(BODYGUARD_TAG)) return "bodyguard";
        Faction f = Sides.faction(npc.level(), npc.getFactionIdentifier());
        if (f == null) return "no faction";
        if (Sides.sideOf(f) == Sides.Side.NONE) return "neutral faction " + f.getId();
        if (!hasLeaders(f)) return "faction has no LEADER pool";
        if (NemesisConfig.factionDisabled(f.getId())) return "faction disabled";
        NpcRank rank = rankOf(f, npc.getNpcTypeIdentifier());
        if (rank == null || rank == NpcRank.CIVILIAN || rank == NpcRank.MILITIA) return "rank " + rank;
        if (neverNemesis(npc.level(), npc.getNpcTypeIdentifier())) return "never_nemesis tag";
        return null;
    }
}
