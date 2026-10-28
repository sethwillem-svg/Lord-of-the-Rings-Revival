package io.github.sethwillemsvg.lotrrevival.world;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Ore placement for Middle-earth (spec: combat-items, "Ore placement numbers for World").
 *
 * <p>Legacy numbers are for a 0-128 world. Our dimension runs from y -64 and keeps the same surface (sea level
 * 63), so every Legacy height is shifted down by 64: Legacy y 0 is our y -64. Vein sizes and attempts per chunk
 * are unchanged.
 */
public final class MiddleEarthOres {
    private MiddleEarthOres() {}

    /** How many blocks every Legacy ore height is shifted down. */
    public static final int HEIGHT_SHIFT = 64;

    /** One ore: vein size, attempts per chunk (a fraction means one attempt every 1/n chunks), height range. */
    public record Spec(String id, int veinSize, double perChunk, int minY, int maxY) {
        public ResourceKey<ConfiguredFeature<?, ?>> configured() {
            return ResourceKey.create(Registries.CONFIGURED_FEATURE, LOTRRevival.id(id));
        }

        public ResourceKey<PlacedFeature> placed() {
            return ResourceKey.create(Registries.PLACED_FEATURE, LOTRRevival.id(id));
        }
    }

    public static final Spec COPPER = new Spec("ore_copper", 8, 16, legacy(0), legacy(128));
    public static final Spec TIN = new Spec("ore_tin", 8, 16, legacy(0), legacy(128));
    public static final Spec SILVER = new Spec("ore_silver", 8, 3, legacy(0), legacy(32));
    public static final Spec MORGUL_IRON = new Spec("ore_morgul_iron", 8, 20, legacy(0), legacy(64));
    public static final Spec MITHRIL = new Spec("ore_mithril", 6, 0.25, legacy(0), legacy(16));

    public static final List<Spec> ALL = List.of(COPPER, TIN, SILVER, MORGUL_IRON, MITHRIL);

    /**
     * Biomes with Morgul iron (they have no ordinary iron). Ids of biomes that do not exist yet are listed so the
     * ore arrives with the biome; matching is by path. Add new Mordor, Angmar and Dol Guldur biomes here.
     */
    public static final Set<String> MORGUL_IRON_BIOMES = Set.of(
            "mordor", "gorgoroth", "nurn", "mordor_mountains", "morgul_vale", "udun", "nan_ungol",
            "angmar", "angmar_mountains", "mount_gram", "dol_guldur", "mirkwood_mountains");

    /** Biomes with mithril: the Misty Mountains only. */
    public static final Set<String> MITHRIL_BIOMES = Set.of("misty_mountains", "misty_mountains_foothills");

    /** The ores a biome gets, in decoration order. */
    public static List<Spec> oresFor(ResourceKey<Biome> biome) {
        String path = biome.location().getPath();
        List<Spec> ores = new ArrayList<>(List.of(COPPER, TIN, SILVER));
        if (MORGUL_IRON_BIOMES.contains(path)) {
            ores.add(MORGUL_IRON);
        }
        if (MITHRIL_BIOMES.contains(path)) {
            ores.add(MITHRIL);
        }
        return ores;
    }

    private static int legacy(int y) {
        return y - HEIGHT_SHIFT;
    }
}
