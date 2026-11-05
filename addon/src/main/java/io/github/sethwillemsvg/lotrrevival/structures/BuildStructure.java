package io.github.sethwillemsvg.lotrrevival.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

/**
 * {@code lotr_revival:build}: one template through vanilla jigsaw placement, plus the site checks (specs/addon/structures.md,
 * Placement). Every check reads base heights from the chunk generator; no chunk is generated for a check.
 */
public final class BuildStructure extends Structure {
    static final String SYS = StructureStats.SYS;
    private static final Set<String> WARNED_NO_CREDIT = ConcurrentHashMap.newKeySet();

    public static final MapCodec<BuildStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Codec.STRING.fieldOf("build").forGetter(s -> s.build),
            ResourceLocation.CODEC.fieldOf("template").forGetter(s -> s.template),
            Codec.INT.listOf().optionalFieldOf("size", List.of(16, 16, 16)).forGetter(s -> s.size),
            Codec.intRange(0, 64).optionalFieldOf("max_slope", 6).forGetter(s -> s.maxSlope),
            Codec.STRING.optionalFieldOf("site", "land").forGetter(s -> s.site),
            Codec.intRange(0, 64).optionalFieldOf("ground_y", 0).forGetter(s -> s.groundY)
    ).apply(i, BuildStructure::new));

    final Holder<StructureTemplatePool> startPool;
    final String build;
    final ResourceLocation template;
    final List<Integer> size;
    final int maxSlope;
    final String site;
    final int groundY;

    public BuildStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, String build,
                          ResourceLocation template, List<Integer> size, int maxSlope, String site, int groundY) {
        super(settings);
        this.startPool = startPool;
        this.build = build;
        this.template = template;
        this.size = size;
        this.maxSlope = maxSlope;
        this.site = site;
        this.groundY = groundY;
    }

    public String build() {
        return build;
    }

    public ResourceLocation template() {
        return template;
    }

    public int groundY() {
        return groundY;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos chunk = ctx.chunkPos();
        if (StructuresConfig.buildDisabled(build)) return skip("disabled", chunk, "build in disabled_builds");
        Optional<StructureInfo> info = StructureInfo.of(ctx.registryAccess(), LotrRevival.id(build));
        if (info.isEmpty() || !info.get().creditOk()) {
            if (WARNED_NO_CREDIT.add(build))
                LotrLog.warn(SYS, "build {} has no structure_info credit: it never places", build);
            return skip("no_credit", chunk, "");
        }
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        // The template is placed from the start corner in one of 4 random rotations, so sample a 3 x 3 grid that covers
        // every rotation's footprint. TODO(spec §Placement): predict the jigsaw rotation and sample its 4 corners + centre.
        int s = Math.max(Math.max(size.get(0), size.get(2)) - 1, 1);
        SiteRules.Column[] cols = new SiteRules.Column[9];
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                cols[n++] = column(ctx, x + dx * s, z + dz * s);
            }
        }
        if (!"land".equals(site)) {
            // TODO(spec §Placement, ticket 11): river crossings (SiteRules.riverCrossing) and the rotation they pick
            return skip("no_river", chunk, "site " + site + " not implemented yet");
        }
        SiteRules.Verdict v = SiteRules.land(maxSlope, cols);
        if (!v.ok()) return skip(v.reason().startsWith("slope") ? "slope" : v.reason(), chunk, v.reason());
        // Jigsaw adds the first free height at the piece's centre to y; -groundY puts the template's ground layer on the
        // terrain's top solid block. TODO(spec §Fitting): confirm in the playtest that doors are not buried or floating.
        BlockPos start = new BlockPos(x, -groundY, z);
        Optional<GenerationStub> stub = JigsawPlacement.addPieces(ctx, startPool, Optional.empty(), 1, start, false,
                Optional.of(Heightmap.Types.WORLD_SURFACE_WG), 80, PoolAliasLookup.EMPTY, DimensionPadding.ZERO,
                LiquidSettings.APPLY_WATERLOGGING);
        if (stub.isEmpty()) return skip("jigsaw", chunk, "jigsaw placement found no fit");
        StructureStats.count("placed");
        LotrLog.dbg(SYS, "start {} chunk={},{} slope={}/{} water=no -> placed at {},{},{}", LotrRevival.id(build), chunk.x,
                chunk.z, v.slope(), maxSlope, stub.get().position().getX(), stub.get().position().getY(),
                stub.get().position().getZ());
        return stub;
    }

    private static SiteRules.Column column(GenerationContext ctx, int x, int z) {
        int surface = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(),
                ctx.randomState());
        int floor = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(),
                ctx.randomState());
        return new SiteRules.Column(surface, floor);
    }

    private Optional<GenerationStub> skip(String reason, ChunkPos chunk, String detail) {
        StructureStats.count("skip." + reason);
        LotrLog.dbg(SYS, "start {} chunk={},{} -> skipped: {} {}", LotrRevival.id(build), chunk.x, chunk.z, reason, detail);
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return StructuresModule.BUILD.get();
    }
}
