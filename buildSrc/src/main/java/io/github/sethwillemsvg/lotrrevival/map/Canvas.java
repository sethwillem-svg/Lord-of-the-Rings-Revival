package io.github.sethwillemsvg.lotrrevival.map;

/**
 * The fixed Middle-earth canvas (see specs/world/dimension-and-mapgen.md). Mirrors
 * {@code world.MiddleEarthMap}; buildSrc cannot depend on the mod sources, so the validator also checks the
 * committed map_regions.json against these.
 *
 * <p>Block numbers here are Legacy scale. The rasterized images and the travel JSON are in pixels and are the same
 * for every world scale; only the map_infos (datagen) change how many blocks a pixel covers (see
 * {@code world.WorldScale}). Block-based checks run at Legacy, the largest scale, so they also hold at half and
 * quarter scale, where the same pixel distances are fewer blocks.
 */
final class Canvas {
    /** Heightmap size in pixels (16-bit grayscale), 64 blocks per pixel. */
    static final int HEIGHT_WIDTH = 6400;

    static final int HEIGHT_HEIGHT = 8000;
    static final int HEIGHT_BLOCKS_PER_PIXEL = 64;

    /** Biome map size in pixels (8-bit RGB), 128 blocks per pixel. Exactly half the heightmap. */
    static final int BIOME_WIDTH = 3200;

    static final int BIOME_HEIGHT = 4000;

    /** Heightmap encoding: y = value * 0.005. */
    static final double VERTICAL_SCALE = 0.005;

    static final int SEA_LEVEL = 63;

    /** Placeholder (unpainted) ocean: floor y 40, value 8,000. */
    static final int PLACEHOLDER_HEIGHT_VALUE = 8000;

    static final double SEA_FLOOR = 40.0;

    /** Placeholder ocean biome colour. */
    static final int OCEAN_COLOR = 0x1F3A93;

    /** Painted surface must stay under this, including the biome's detail amplitude. */
    static final int MAX_SURFACE = 300;

    /** Land and sources must stay this many heightmap pixels (1,024 blocks) inside the canvas. */
    static final int EDGE_MARGIN_PX = 16;

    static int toValue(double y) {
        return (int) Math.max(0, Math.min(65535, Math.round(y / VERTICAL_SCALE)));
    }

    private Canvas() {}
}
