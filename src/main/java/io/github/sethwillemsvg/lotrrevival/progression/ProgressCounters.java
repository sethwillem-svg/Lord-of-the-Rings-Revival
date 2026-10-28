package io.github.sethwillemsvg.lotrrevival.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable value of the {@code lotrrevival:progress_counters} attachment. Ids are kept as plain
 * resource locations so ids from removed datapacks round-trip unchanged.
 *
 * @param visitedBiomes biomes visited in Middle-earth
 * @param lastBiome last biome seen by the poll, in any dimension
 * @param killsByFaction kill counts keyed by faction id
 * @param tradesTotal completed trades
 */
public record ProgressCounters(
        Set<ResourceLocation> visitedBiomes,
        Optional<ResourceLocation> lastBiome,
        Map<ResourceLocation, Integer> killsByFaction,
        int tradesTotal) {
    public static final ProgressCounters EMPTY = new ProgressCounters(Set.of(), Optional.empty(), Map.of(), 0);

    private static final Codec<Set<ResourceLocation>> ID_SET = ResourceLocation.CODEC
            .listOf()
            .xmap(list -> (Set<ResourceLocation>) new LinkedHashSet<>(list), set -> new ArrayList<>(set));

    public static final Codec<ProgressCounters> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ID_SET.optionalFieldOf("visited_biomes", Set.of()).forGetter(ProgressCounters::visitedBiomes),
                    ResourceLocation.CODEC.optionalFieldOf("last_biome").forGetter(ProgressCounters::lastBiome),
                    Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                            .optionalFieldOf("kills_by_faction", Map.of())
                            .forGetter(ProgressCounters::killsByFaction),
                    Codec.INT.optionalFieldOf("trades_total", 0).forGetter(ProgressCounters::tradesTotal))
            .apply(i, ProgressCounters::new));

    public ProgressCounters {
        visitedBiomes = Collections.unmodifiableSet(new LinkedHashSet<>(visitedBiomes));
        killsByFaction = Map.copyOf(killsByFaction);
    }

    public ProgressCounters withVisitedBiome(ResourceLocation biome) {
        Set<ResourceLocation> next = new LinkedHashSet<>(visitedBiomes);
        next.add(biome);
        return new ProgressCounters(next, lastBiome, killsByFaction, tradesTotal);
    }

    public ProgressCounters withLastBiome(ResourceLocation biome) {
        return new ProgressCounters(visitedBiomes, Optional.of(biome), killsByFaction, tradesTotal);
    }

    public ProgressCounters withKill(ResourceLocation faction) {
        Map<ResourceLocation, Integer> next = new HashMap<>(killsByFaction);
        next.merge(faction, 1, Integer::sum);
        return new ProgressCounters(visitedBiomes, lastBiome, next, tradesTotal);
    }

    public ProgressCounters withTrade() {
        return new ProgressCounters(visitedBiomes, lastBiome, killsByFaction, tradesTotal + 1);
    }
}
