package io.github.sethwillemsvg.lotrrevival.core.faction;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

/** Keys for every faction in the {@code lotrrevival:faction} registry, plus lookup helpers. */
public final class Factions {
    private Factions() {}

    // Westlands
    public static final ResourceKey<Faction> HOBBIT = key("hobbit");
    public static final ResourceKey<Faction> BREE = key("bree");
    public static final ResourceKey<Faction> DUNEDAIN_NORTH = key("dunedain_north");
    public static final ResourceKey<Faction> BLUE_MOUNTAINS = key("blue_mountains");
    public static final ResourceKey<Faction> HIGH_ELF = key("high_elf");
    public static final ResourceKey<Faction> GUNDABAD = key("gundabad");
    public static final ResourceKey<Faction> ANGMAR = key("angmar");
    public static final ResourceKey<Faction> WOOD_ELF = key("wood_elf");
    public static final ResourceKey<Faction> DOL_GULDUR = key("dol_guldur");
    public static final ResourceKey<Faction> DALE = key("dale");
    public static final ResourceKey<Faction> DURINS_FOLK = key("durins_folk");
    public static final ResourceKey<Faction> LOTHLORIEN = key("lothlorien");
    public static final ResourceKey<Faction> DUNLAND = key("dunland");
    public static final ResourceKey<Faction> ISENGARD = key("isengard");
    public static final ResourceKey<Faction> FANGORN = key("fangorn");
    public static final ResourceKey<Faction> ROHAN = key("rohan");
    public static final ResourceKey<Faction> GONDOR = key("gondor");
    public static final ResourceKey<Faction> MORDOR = key("mordor");
    // Rhun
    public static final ResourceKey<Faction> DORWINION = key("dorwinion");
    public static final ResourceKey<Faction> EASTERLING = key("easterling");
    // Harad
    public static final ResourceKey<Faction> NEAR_HARAD = key("near_harad");
    public static final ResourceKey<Faction> FAR_HARAD = key("far_harad");
    public static final ResourceKey<Faction> HARAD_JUNGLE = key("harad_jungle");
    public static final ResourceKey<Faction> HALF_TROLL = key("half_troll");
    // Deeps
    public static final ResourceKey<Faction> UTUMNO = key("utumno");

    // Hidden pseudo-factions: no ranks, fixed alignment, never pledgeable.
    /** Generic monsters: mortal enemy of every real faction. */
    public static final ResourceKey<Faction> HOSTILE = key("hostile");
    /** Animals and other non-faction mobs: neutral to everything. */
    public static final ResourceKey<Faction> UNALIGNED = key("unaligned");
    /** Hostile trees: mortal enemy of hobbit, bree and dunedain_north. */
    public static final ResourceKey<Faction> HOSTILE_TREES = key("hostile_trees");
    /** Bree-land troublemakers. */
    public static final ResourceKey<Faction> RUFFIANS = key("ruffians");

    /** The 25 player-facing factions in region order (utumno included, it is hidden). */
    public static final List<ResourceKey<Faction>> REAL = List.of(
            HOBBIT, BREE, DUNEDAIN_NORTH, BLUE_MOUNTAINS, HIGH_ELF, GUNDABAD, ANGMAR, WOOD_ELF, DOL_GULDUR, DALE,
            DURINS_FOLK, LOTHLORIEN, DUNLAND, ISENGARD, FANGORN, ROHAN, GONDOR, MORDOR, DORWINION, EASTERLING,
            NEAR_HARAD, FAR_HARAD, HARAD_JUNGLE, HALF_TROLL, UTUMNO);

    public static final List<ResourceKey<Faction>> PSEUDO = List.of(HOSTILE, UNALIGNED, HOSTILE_TREES, RUFFIANS);

    private static ResourceKey<Faction> key(String path) {
        return ResourceKey.create(ModDataRegistries.FACTION, LOTRRevival.id(path));
    }

    /**
     * Relation between two factions. A faction is an ally of itself; unknown or unlisted pairs are neutral.
     */
    public static FactionRelation relation(
            Registry<Faction> registry, ResourceKey<Faction> a, ResourceKey<Faction> b) {
        if (a.equals(b)) {
            return FactionRelation.ALLY;
        }
        Faction faction = registry.get(a);
        return faction == null ? FactionRelation.NEUTRAL : faction.relationTo(b);
    }

    public static Component displayName(ResourceKey<Faction> key) {
        return Component.translatable(nameKey(key));
    }

    public static String nameKey(ResourceKey<Faction> key) {
        return "faction." + key.location().getNamespace() + "." + key.location().getPath();
    }
}
