package io.github.sethwillemsvg.lotrrevival.npc.spawn;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

/**
 * Reload listener for {@code data/<ns>/lotrrevival/npc_spawns/<biome>.json}. The file name is the biome id (so
 * {@code lotrrevival:shire} is {@code data/lotrrevival/lotrrevival/npc_spawns/shire.json}). Content:
 * {@code {"entries": [{type, weight, group_min, group_max, roles?, chance?}]}}. {@code chance} N is an extra 1-in-N
 * roll after the entry is picked. An entry with an unknown entity id is skipped with one warning. Server side only.
 */
public final class NpcSpawnData extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "lotrrevival/npc_spawns";
    public static final String ENTRIES_KEY = "entries";

    /** One spawn table row as written in JSON. */
    public record Raw(
            ResourceLocation type, int weight, int groupMin, int groupMax, Optional<List<NpcRole>> roles, int chance) {
        public static final Codec<Raw> CODEC = RecordCodecBuilder.create(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("type").forGetter(Raw::type),
                        Codec.intRange(1, 100000).fieldOf("weight").forGetter(Raw::weight),
                        Codec.intRange(1, 64).fieldOf("group_min").forGetter(Raw::groupMin),
                        Codec.intRange(1, 64).fieldOf("group_max").forGetter(Raw::groupMax),
                        NpcRole.CODEC.listOf().optionalFieldOf("roles").forGetter(Raw::roles),
                        Codec.intRange(1, 1000000).optionalFieldOf("chance", 1).forGetter(Raw::chance))
                .apply(i, Raw::new));
    }

    /** A resolved spawn entry. {@code roles} empty means any role the type can roll. */
    public record Entry(
            EntityType<?> type, int weight, int groupMin, int groupMax, Set<NpcRole> roles, int chance) {
        public int rollGroupSize(RandomSource random) {
            return groupMin + random.nextInt(groupMax - groupMin + 1);
        }

        /** The extra 1-in-N roll. */
        public boolean rollChance(RandomSource random) {
            return chance <= 1 || random.nextInt(chance) == 0;
        }
    }

    private static volatile Map<ResourceLocation, List<Entry>> tables = Map.of();

    public NpcSpawnData() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        reloadFrom(files, warning -> LOTRRevival.LOGGER.warn("{}", warning));
    }

    /** Replaces all loaded tables with the parsed files. Also the entry point for tests that swap data. */
    public static void reloadFrom(Map<ResourceLocation, JsonElement> files, Consumer<String> warn) {
        Map<ResourceLocation, List<Entry>> loaded = new HashMap<>();
        files.forEach((biome, json) -> {
            List<Entry> entries = parseTable(biome, json, warn);
            if (!entries.isEmpty()) {
                loaded.put(biome, entries);
            }
        });
        tables = Map.copyOf(loaded);
    }

    /** Parses one table. Bad entries and unknown entity ids are skipped, each with one warning. */
    public static List<Entry> parseTable(ResourceLocation biome, JsonElement json, Consumer<String> warn) {
        if (!(json instanceof JsonObject object) || !object.has(ENTRIES_KEY) || !object.get(ENTRIES_KEY).isJsonArray()) {
            warn.accept("Skipping NPC spawn table " + biome + ": expected an object with an \"" + ENTRIES_KEY + "\" list");
            return List.of();
        }
        List<Entry> result = new java.util.ArrayList<>();
        int index = 0;
        for (JsonElement element : object.getAsJsonArray(ENTRIES_KEY)) {
            int at = index++;
            Optional<Raw> raw = Raw.CODEC
                    .parse(JsonOps.INSTANCE, element)
                    .resultOrPartial(error -> warn.accept("Skipping NPC spawn entry " + at + " of " + biome + ": " + error));
            if (raw.isEmpty()) {
                continue;
            }
            Raw r = raw.get();
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(r.type()).map(t -> (EntityType<?>) t);
            if (type.isEmpty()) {
                warn.accept("Skipping NPC spawn entry " + at + " of " + biome + ": unknown entity id " + r.type());
                continue;
            }
            if (r.groupMax() < r.groupMin()) {
                warn.accept("Skipping NPC spawn entry " + at + " of " + biome + ": group_max is below group_min");
                continue;
            }
            Set<NpcRole> roles = EnumSet.noneOf(NpcRole.class);
            r.roles().ifPresent(roles::addAll);
            result.add(new Entry(type.get(), r.weight(), r.groupMin(), r.groupMax(), roles, r.chance()));
        }
        return List.copyOf(result);
    }

    /** The loaded entries for a biome id; empty for a biome without a table. */
    public static List<Entry> forBiome(ResourceLocation biome) {
        return tables.getOrDefault(biome, List.of());
    }

    public static boolean isEmpty() {
        return tables.isEmpty();
    }

    /** The currently loaded tables, so a test can restore them afterwards. */
    public static Map<ResourceLocation, List<Entry>> snapshot() {
        return tables;
    }

    public static void restore(Map<ResourceLocation, List<Entry>> snapshot) {
        tables = snapshot;
    }

    /** Weighted pick; empty for an empty list. */
    public static Optional<Entry> pick(List<Entry> entries, RandomSource random) {
        int total = entries.stream().mapToInt(Entry::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Entry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
