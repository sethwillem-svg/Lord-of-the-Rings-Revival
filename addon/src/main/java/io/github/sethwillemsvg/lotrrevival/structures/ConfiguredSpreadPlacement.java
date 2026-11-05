package io.github.sethwillemsvg.lotrrevival.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * {@code lotr_revival:configured_spread}: a random spread whose spacing and separation come from the config, locked per
 * world ({@link WorldLock}), with a list of exclusions (vanilla allows one). Exclusions against Middle-earth's sets use
 * the generator's own placement check; exclusions between our sets compare grid candidates only, and only against sets
 * whose id sorts before ours, so two sets never exclude each other (and never recurse).
 */
public final class ConfiguredSpreadPlacement extends StructurePlacement {
    static final String SYS = StructureStats.SYS;

    public record Exclusion(ResourceLocation set, int chunks) {
        public static final Codec<Exclusion> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("set").forGetter(Exclusion::set),
                Codec.intRange(0, 32).fieldOf("chunks").forGetter(Exclusion::chunks)
        ).apply(i, Exclusion::new));
    }

    public record Settings(ResourceLocation set, String region, int spacing, int separation, List<Exclusion> exclusions,
                           int ownChunks) {
        public static final MapCodec<Settings> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("set").forGetter(Settings::set),
                Codec.STRING.fieldOf("region").forGetter(Settings::region),
                Codec.intRange(2, 4096).fieldOf("spacing").forGetter(Settings::spacing),
                Codec.intRange(1, 4095).fieldOf("separation").forGetter(Settings::separation),
                Exclusion.CODEC.listOf().optionalFieldOf("exclusions", List.of()).forGetter(Settings::exclusions),
                Codec.intRange(0, 32).optionalFieldOf("own_chunks", 2).forGetter(Settings::ownChunks)
        ).apply(i, Settings::new));
    }

    public static final MapCodec<ConfiguredSpreadPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> placementCodec(i)
            .and(Settings.MAP_CODEC.forGetter((ConfiguredSpreadPlacement p) -> p.settings))
            .apply(i, ConfiguredSpreadPlacement::new));

    private final Settings settings;
    private final int jsonSalt;

    public ConfiguredSpreadPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt,
                                     Optional<ExclusionZone> exclusionZone, Settings settings) {
        super(locateOffset, method, frequency, salt, exclusionZone);
        this.settings = settings;
        this.jsonSalt = salt;
    }

    public Settings settings() {
        return settings;
    }

    public String setId() {
        return settings.set().toString();
    }

    private boolean crossings() {
        return settings.set().getPath().endsWith("_crossings");
    }

    /** Values from the config now (what a new world would lock). */
    public WorldLock.Entry configValues() {
        StructuresConfig.RegionValues v = StructuresConfig.REGIONS.get(settings.region());
        int spacing = settings.spacing(), sep = settings.separation();
        if (v != null) {
            spacing = StructuresConfig.get(crossings() ? v.crossingsSpacing() : v.spacing(), spacing);
            sep = StructuresConfig.get(crossings() ? v.crossingsSeparation() : v.separation(), sep);
        }
        return new WorldLock.Entry(spacing, Math.min(sep, spacing - 1), jsonSalt);
    }

    /** Values in use: the world lock, else the config. */
    public WorldLock.Entry values() {
        return WorldLock.effective(setId(), configValues());
    }

    public ChunkPos candidate(long seed, int x, int z) {
        WorldLock.Entry e = values();
        return SpreadMath.candidate(seed, x, z, e.spacing(), e.separation(), e.salt());
    }

    boolean candidateWithin(long seed, int x, int z, int range) {
        WorldLock.Entry e = values();
        return SpreadMath.candidateWithin(seed, x, z, range, e.spacing(), e.separation(), e.salt());
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        long seed = state.getLevelSeed();
        WorldLock.Entry e = values();
        if (!SpreadMath.isCandidate(seed, x, z, e.spacing(), e.separation(), e.salt())) return false;
        StructureStats.count("candidates");
        if (!StructuresConfig.enabled() || !StructuresConfig.regionEnabled(settings.region())) {
            skip("disabled", x, z, "");
            return false;
        }
        int meChunks = StructuresConfig.get(StructuresConfig.EXCLUSION_MIDDLE_EARTH, 3);
        int ownChunks = StructuresConfig.get(StructuresConfig.EXCLUSION_OWN, settings.ownChunks());
        for (Holder<StructureSet> other : state.possibleStructureSets()) {
            Optional<ResourceLocation> id = other.unwrapKey().map(k -> k.location());
            if (id.isEmpty() || id.get().equals(settings.set())) continue;
            for (Exclusion ex : settings.exclusions()) {
                if (ex.set().equals(id.get()) && state.hasStructureChunkInRange(other, x, z, Math.min(ex.chunks(), meChunks))) {
                    skip("excluded", x, z, id.get() + " within " + Math.min(ex.chunks(), meChunks));
                    return false;
                }
            }
            if (other.value().placement() instanceof ConfiguredSpreadPlacement p
                    && id.get().toString().compareTo(setId()) < 0 && p.candidateWithin(seed, x, z, ownChunks)) {
                skip("excluded_own", x, z, id.get() + " within " + ownChunks);
                return false;
            }
        }
        LotrLog.dbg(SYS, "candidate set={} chunk={},{} spacing={}/{} exclusions clear", setId(), x, z, e.spacing(), e.separation());
        return true;
    }

    private void skip(String reason, int x, int z, String detail) {
        StructureStats.count("skip." + reason);
        LotrLog.dbg(SYS, "skip set={} chunk={},{} {} {}", setId(), x, z, reason, detail);
    }

    @Override
    public StructurePlacementType<?> type() {
        return StructuresModule.CONFIGURED_SPREAD.get();
    }
}
