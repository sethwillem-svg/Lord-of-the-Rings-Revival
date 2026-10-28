package io.github.sethwillemsvg.lotrrevival.world;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Colour table: the single source of truth for biome-map key colours and detail amplitudes.
 *
 * <p>The pilot set from the spec (Shire to Bree). Datagen writes the map_infos and the relief image from this table.
 */
public final class MiddleEarthBiomes {
    /** One biome-map colour. {@code amplitude} is the procedural detail amplitude in blocks (0-31). */
    public record Entry(ResourceKey<Biome> key, int color, int amplitude) {}

    public static final ResourceKey<Biome> OCEAN = key("ocean");
    public static final ResourceKey<Biome> SHORE = key("shore");
    public static final ResourceKey<Biome> LAKE = key("lake");
    public static final ResourceKey<Biome> SHIRE = key("shire");
    public static final ResourceKey<Biome> SHIRE_WOODLANDS = key("shire_woodlands");
    public static final ResourceKey<Biome> SHIRE_MARSHES = key("shire_marshes");
    public static final ResourceKey<Biome> SHIRE_MOORS = key("shire_moors");
    public static final ResourceKey<Biome> WHITE_DOWNS = key("white_downs");
    public static final ResourceKey<Biome> OLD_FOREST = key("old_forest");
    public static final ResourceKey<Biome> BARROW_DOWNS = key("barrow_downs");
    public static final ResourceKey<Biome> BREE_LAND = key("bree_land");
    public static final ResourceKey<Biome> CHETWOOD = key("chetwood");
    public static final ResourceKey<Biome> MIDGEWATER_MARSHES = key("midgewater_marshes");
    public static final ResourceKey<Biome> LONE_LANDS = key("lone_lands");
    public static final ResourceKey<Biome> TOWER_HILLS = key("tower_hills");
    public static final ResourceKey<Biome> ERED_LUIN_FOOTHILLS = key("ered_luin_foothills");

    /** Never painted: the jittered biome source returns it inside river polylines (ticket 4). */
    public static final ResourceKey<Biome> RIVER = key("river");

    /**
     * The painted colour table. The map pipeline (buildSrc, {@code src/main/map/}) reads this file to learn the
     * valid biome ids, colours and amplitudes, so keep the {@code key("...")} constants and
     * {@code new Entry(NAME, 0xRRGGBB, amplitude)} lines in exactly this shape.
     */
    public static final List<Entry> TABLE = List.of(
            new Entry(OCEAN, 0x1F3A93, 2),
            new Entry(SHORE, 0xD8CB8E, 1),
            new Entry(LAKE, 0x2E64B5, 0),
            new Entry(SHIRE, 0x78B048, 7),
            new Entry(SHIRE_WOODLANDS, 0x3E7A2C, 12),
            new Entry(SHIRE_MARSHES, 0x5F8F6E, 2),
            new Entry(SHIRE_MOORS, 0x9A9A62, 14),
            new Entry(WHITE_DOWNS, 0xC9D7A0, 14),
            new Entry(OLD_FOREST, 0x1F4A24, 7),
            new Entry(BARROW_DOWNS, 0xA3B56B, 9),
            new Entry(BREE_LAND, 0x9CC25A, 5),
            new Entry(CHETWOOD, 0x2F6B3A, 9),
            new Entry(MIDGEWATER_MARSHES, 0x4F7A63, 2),
            new Entry(LONE_LANDS, 0xB8AE6E, 9),
            new Entry(TOWER_HILLS, 0x6E9A5A, 12),
            new Entry(ERED_LUIN_FOOTHILLS, 0x7A8270, 21));

    /** Every Middle-earth biome: the painted ones plus the river. */
    public static List<ResourceKey<Biome>> allKeys() {
        List<ResourceKey<Biome>> keys = new java.util.ArrayList<>();
        for (Entry entry : TABLE) {
            keys.add(entry.key());
        }
        keys.add(RIVER);
        return keys;
    }

    private MiddleEarthBiomes() {}

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, LOTRRevival.id(path));
    }
}
