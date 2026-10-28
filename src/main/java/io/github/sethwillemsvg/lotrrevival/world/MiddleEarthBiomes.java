package io.github.sethwillemsvg.lotrrevival.world;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Colour table: the single source of truth for biome-map key colours and detail amplitudes.
 *
 * <p>Only the placeholder set is registered so far (ticket #22); the full pilot table from the spec is
 * added by the biome ticket. Datagen writes the map_infos and the relief image from this table.
 */
public final class MiddleEarthBiomes {
    /** One biome-map colour. {@code amplitude} is the procedural detail amplitude in blocks (0-31). */
    public record Entry(ResourceKey<Biome> key, int color, int amplitude) {}

    public static final ResourceKey<Biome> OCEAN = key("ocean");
    public static final ResourceKey<Biome> SHIRE = key("shire");
    public static final ResourceKey<Biome> OLD_FOREST = key("old_forest");

    public static final List<Entry> TABLE = List.of(
            new Entry(OCEAN, 0x1F3A93, 2),
            new Entry(SHIRE, 0x78B048, 7),
            new Entry(OLD_FOREST, 0x1F4A24, 7));

    private MiddleEarthBiomes() {}

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, LOTRRevival.id(path));
    }
}
