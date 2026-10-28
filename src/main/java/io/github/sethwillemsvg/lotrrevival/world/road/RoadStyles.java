package io.github.sethwillemsvg.lotrrevival.world.road;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pilot road styles and the biome to style table. Later regions add rows here.
 *
 * <p>The Arnor style uses Core's Arnor brick set ({@code arnor_brick}, {@code mossy_arnor_brick},
 * {@code cracked_arnor_brick} and their slabs) when those blocks are registered, and vanilla stone bricks otherwise.
 * Blocks are looked up on first use, never in a static initializer, because the registries are not filled yet then.
 */
public final class RoadStyles {
    /** A style plus the share of its road columns that get placed in one biome. */
    public record Choice(RoadStyle style, double repair) {}

    private static volatile Resolved resolved;

    private RoadStyles() {}

    /** The style and repair chance for a biome; unlisted and unknown biomes get the path style at repair 1. */
    public static Choice forBiome(@Nullable ResourceKey<Biome> biome) {
        Resolved r = resolve();
        if (MiddleEarthBiomes.LONE_LANDS.equals(biome)) {
            return new Choice(r.arnor, 0.4);
        }
        return new Choice(r.path, 1.0);
    }

    public static RoadStyle path() {
        return resolve().path;
    }

    public static RoadStyle arnor() {
        return resolve().arnor;
    }

    /** The bridge style of a biome: {@code stone_and_wood} for every pilot biome. */
    public static BridgeStyle bridgeFor(@Nullable ResourceKey<Biome> biome) {
        return resolve().bridge;
    }

    /** Whether the block is a deck, half-step or rail block of a bridge style. */
    public static boolean isBridgeBlock(BlockState state) {
        return resolve().bridgeBlocks.contains(state.getBlock());
    }

    /** Whether the block is a slab that some road style puts on a half step. */
    public static boolean isRoadSlab(BlockState state) {
        return resolve().slabs.contains(state.getBlock());
    }

    /** Whether the block is a surface block of some road style. */
    public static boolean isRoadSurface(BlockState state) {
        return resolve().surfaces.contains(state.getBlock());
    }

    private static Resolved resolve() {
        Resolved r = resolved;
        if (r == null) {
            r = build();
            resolved = r;
        }
        return r;
    }

    private static Resolved build() {
        RoadStyle path = new RoadStyle(
                "path",
                List.of(
                        new RoadStyle.Surface(Blocks.DIRT_PATH.defaultBlockState(), null, 70),
                        new RoadStyle.Surface(Blocks.COARSE_DIRT.defaultBlockState(), null, 20),
                        new RoadStyle.Surface(Blocks.GRAVEL.defaultBlockState(), null, 10)),
                Blocks.DIRT.defaultBlockState());
        RoadStyle arnor = new RoadStyle(
                "arnor",
                List.of(
                        arnorSurface("arnor_brick", Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, 50),
                        arnorSurface("mossy_arnor_brick", Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_SLAB, 25),
                        arnorSurface("cracked_arnor_brick", Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_SLAB, 25)),
                Blocks.STONE.defaultBlockState());

        Set<Block> slabs = new HashSet<>();
        Set<Block> surfaces = new HashSet<>();
        for (RoadStyle style : List.of(path, arnor)) {
            for (RoadStyle.Surface s : style.surface()) {
                surfaces.add(s.block().getBlock());
                if (s.slab() != null) {
                    slabs.add(s.slab().getBlock());
                }
            }
        }
        BridgeStyle bridge = new BridgeStyle(
                "stone_and_wood",
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState(),
                Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState());
        Set<Block> bridgeBlocks =
                Set.of(bridge.deck().getBlock(), bridge.deckSlab().getBlock(), bridge.rail().getBlock());
        return new Resolved(path, arnor, slabs, surfaces, bridge, bridgeBlocks);
    }

    private static RoadStyle.Surface arnorSurface(String name, Block fallback, Block fallbackSlab, int weight) {
        BlockState block = BuiltInRegistries.BLOCK
                .getOptional(LOTRRevival.id(name))
                .orElse(fallback)
                .defaultBlockState();
        BlockState slab = BuiltInRegistries.BLOCK
                .getOptional(LOTRRevival.id(name + "_slab"))
                .orElse(fallbackSlab)
                .defaultBlockState();
        if (!(slab.getBlock() instanceof SlabBlock)) {
            slab = fallbackSlab.defaultBlockState();
        }
        return new RoadStyle.Surface(block, slab, weight);
    }

    private record Resolved(
            RoadStyle path,
            RoadStyle arnor,
            Set<Block> slabs,
            Set<Block> surfaces,
            BridgeStyle bridge,
            Set<Block> bridgeBlocks) {}
}
