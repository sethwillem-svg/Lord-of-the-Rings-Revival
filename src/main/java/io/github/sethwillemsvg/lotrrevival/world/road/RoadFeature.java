package io.github.sethwillemsvg.lotrrevival.world.road;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Feature {@code lotrrevival:road}: lays road surfaces on the chunk it is placed in. The columns come from
 * {@link RoadPlanner}; this class reads the terrain from the level and writes the blocks.
 */
public final class RoadFeature extends Feature<NoneFeatureConfiguration> {
    /** Layers under the surface that are replaced by the base block. */
    private static final int BASE_DEPTH = 3;

    /** How far up plants are cleared above a road block. */
    private static final int CLEAR_HEIGHT = 3;

    /** Block update flags for worldgen writes: send to clients, no neighbour updates. */
    private static final int FLAGS = 2;

    public RoadFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RoadNetwork network = RoadNetworks.current();
        if (network.isEmpty()) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        // Cheap reject: no road within reach of this chunk (half diagonal plus the neighbour border).
        double edge = network.edgeDistance(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), 16);
        if (edge > 14) {
            return false;
        }
        List<BoundingBox> structures = structureBoxes(level, chunk);
        return !placeRegion(
                        level,
                        network,
                        new LevelTerrain(level, null),
                        chunk.getMinBlockX(),
                        chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(),
                        chunk.getMaxBlockZ(),
                        structures)
                .isEmpty();
    }

    /** Plans and builds the road in a rectangle; returns the columns that were built. */
    public static List<RoadPlanner.Column> placeRegion(
            LevelAccessor level,
            RoadNetwork network,
            RoadPlanner.Terrain terrain,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            List<BoundingBox> structures) {
        List<RoadPlanner.Column> columns = RoadPlanner.plan(network, terrain, minX, minZ, maxX, maxZ, structures);
        for (RoadPlanner.Column column : columns) {
            build(level, column);
        }
        List<RoadPlanner.Bridge> bridges =
                RoadPlanner.planBridges(network, terrain, minX, minZ, maxX, maxZ, structures);
        buildBridges(level, bridges);
        return columns;
    }

    /** Builds bridge columns: piers first, then the deck layer, then the rails (joined to their rail neighbours). */
    public static void buildBridges(LevelAccessor level, List<RoadPlanner.Bridge> bridges) {
        if (bridges.isEmpty()) {
            return;
        }
        Set<Long> rails = new HashSet<>();
        for (RoadPlanner.Bridge b : bridges) {
            if (b.rail()) {
                rails.add(BlockPos.asLong(b.x(), 0, b.z()));
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (RoadPlanner.Bridge b : bridges) {
            BridgeStyle style = b.style();
            if (b.pier()) {
                // From just under the deck to the bed; the water around the pier stays.
                for (int y = b.floor() + 1; y < b.deckY(); y++) {
                    level.setBlock(pos.set(b.x(), y, b.z()), style.pier(), FLAGS);
                }
            }
            level.setBlock(pos.set(b.x(), b.deckY(), b.z()), b.halfStep() ? style.deckSlab() : style.deck(), FLAGS);
            int clearFrom = b.deckY() + 1;
            if (b.rail()) {
                BlockState fence = style.rail();
                fence = connect(level, rails, fence, b.x(), b.deckY() + 1, b.z(), Direction.NORTH);
                fence = connect(level, rails, fence, b.x(), b.deckY() + 1, b.z(), Direction.SOUTH);
                fence = connect(level, rails, fence, b.x(), b.deckY() + 1, b.z(), Direction.EAST);
                fence = connect(level, rails, fence, b.x(), b.deckY() + 1, b.z(), Direction.WEST);
                level.setBlock(pos.set(b.x(), b.deckY() + 1, b.z()), fence, FLAGS);
                clearFrom++;
            }
            clearAbove(level, pos, b.x(), b.z(), clearFrom);
        }
    }

    private static BlockState connect(
            LevelAccessor level, Set<Long> rails, BlockState fence, int x, int y, int z, Direction side) {
        int nx = x + side.getStepX();
        int nz = z + side.getStepZ();
        boolean joined = rails.contains(BlockPos.asLong(nx, 0, nz))
                || level.getBlockState(new BlockPos(nx, y, nz)).is(fence.getBlock());
        BooleanProperty property = PipeBlock.PROPERTY_BY_DIRECTION.get(side);
        return joined ? fence.setValue(property, true) : fence;
    }

    private static void clearAbove(LevelAccessor level, BlockPos.MutableBlockPos pos, int x, int z, int from) {
        for (int y = from; y < from + CLEAR_HEIGHT; y++) {
            pos.set(x, y, z);
            BlockState above = level.getBlockState(pos);
            if (!above.isAir() && isClearable(above)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
    }

    private static void build(LevelAccessor level, RoadPlanner.Column column) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.x(), column.top(), column.z());
        level.setBlock(pos, column.surface().block(), FLAGS);
        BlockState base = column.style().base();
        for (int i = 1; i <= BASE_DEPTH; i++) {
            pos.setY(column.top() - i);
            level.setBlock(pos, base, FLAGS);
        }
        int clearFrom = column.top() + 1;
        if (column.step()) {
            pos.setY(column.top() + 1);
            BlockState slab = column.surface().slab();
            level.setBlock(pos, slab != null ? slab : column.surface().block(), FLAGS);
            clearFrom++;
        }
        // Plants and snow above the road go; solid blocks (a tree trunk, a wall) stay for other rules to judge.
        for (int y = clearFrom; y < clearFrom + CLEAR_HEIGHT; y++) {
            pos.setY(y);
            BlockState above = level.getBlockState(pos);
            if (!above.isAir() && isClearable(above)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
    }

    /** Grass, ferns, flowers, saplings and snow layers: what a road removes above itself. */
    static boolean isClearable(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.SNOW);
    }

    private static List<BoundingBox> structureBoxes(WorldGenLevel level, ChunkPos chunk) {
        List<BoundingBox> boxes = new ArrayList<>();
        StructureManager manager = level.getLevel().structureManager();
        if (level instanceof WorldGenRegion region) {
            manager = manager.forWorldGenRegion(region);
        }
        for (StructureStart start : manager.startsForStructure(chunk, structure -> true)) {
            if (!start.isValid()) {
                continue;
            }
            for (StructurePiece piece : start.getPieces()) {
                boxes.add(piece.getBoundingBox());
            }
        }
        return boxes;
    }

    /**
     * Terrain read from a level. The top solid block is found by scanning down from a start height past plants,
     * snow, leaves, logs and road blocks already placed by a neighbouring chunk, so the result is the same whether
     * or not the neighbour has been decorated yet.
     */
    public static final class LevelTerrain implements RoadPlanner.Terrain {
        private final LevelAccessor level;
        @Nullable
        private final IntUnaryOperator startY;

        /**
         * @param startY the y to start the downward scan from for a column, or null to use the world surface
         *     heightmap of worldgen
         */
        public LevelTerrain(LevelAccessor level, @Nullable IntUnaryOperator startY) {
            this.level = level;
            this.startY = startY;
        }

        @Override
        public int top(int x, int z) {
            int y = startY != null
                    ? startY.applyAsInt(x)
                    : level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
            int min = level.getMinBuildHeight();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
            // A column that already carries a bridge (deck, rail, pier) is a water column, not ground.
            boolean bridge = false;
            while (y >= min) {
                pos.setY(y);
                BlockState state = level.getBlockState(pos);
                if (RoadStyles.isBridgeBlock(state)) {
                    bridge = true;
                } else if (!(bridge && state.is(Blocks.STONE_BRICKS)) && !isSkippable(state, level, pos)) {
                    break;
                }
                y--;
            }
            if (y < min || bridge) {
                return RoadPlanner.NO_GROUND;
            }
            pos.setY(y);
            BlockState ground = level.getBlockState(pos);
            if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.ICE)) {
                return RoadPlanner.NO_GROUND;
            }
            pos.setY(y + 1);
            if (!level.getBlockState(pos).getFluidState().isEmpty()) {
                return RoadPlanner.NO_GROUND;
            }
            return y;
        }

        @Override
        @Nullable
        public RoadPlanner.Water water(int x, int z) {
            int y = startY != null
                    ? startY.applyAsInt(x)
                    : level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            int min = level.getMinBuildHeight();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
            while (y >= min) {
                pos.setY(y);
                BlockState state = level.getBlockState(pos);
                if (!RoadStyles.isBridgeBlock(state) && !isSkippable(state, level, pos)) {
                    break;
                }
                y--;
            }
            if (y < min || !level.getFluidState(pos.setY(y)).is(FluidTags.WATER)) {
                return null;
            }
            int surface = y;
            // Water, kelp and seagrass all carry a water fluid state; the first block without one is the bed.
            while (y >= min && level.getFluidState(pos.setY(y)).is(FluidTags.WATER)) {
                y--;
            }
            if (y < min || level.getBlockState(pos.setY(y)).is(BlockTags.ICE)) {
                return null;
            }
            return new RoadPlanner.Water(surface, y);
        }

        private static boolean isSkippable(BlockState state, LevelAccessor level, BlockPos.MutableBlockPos pos) {
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || isClearable(state)) {
                return true;
            }
            if (RoadStyles.isRoadSlab(state)) {
                return true;
            }
            if (RoadStyles.isRoadSurface(state)) {
                // Only the upper of two stacked road surface blocks is a raised half step.
                pos.setY(pos.getY() - 1);
                boolean stacked = RoadStyles.isRoadSurface(level.getBlockState(pos));
                pos.setY(pos.getY() + 1);
                return stacked;
            }
            return false;
        }

        @Override
        @Nullable
        public ResourceKey<Biome> biome(int x, int z, int top) {
            return level.getBiome(new BlockPos(x, top, z)).unwrapKey().orElse(null);
        }
    }
}
