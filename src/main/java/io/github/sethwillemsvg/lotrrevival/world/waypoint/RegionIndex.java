package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Biome to regions lookup. Rebuilt when the server starts and on every datapack reload (tags updated); also
 * rebuilt on demand when the region registry instance changes.
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class RegionIndex {
    private static volatile Cached cached;

    private final Map<ResourceLocation, List<ResourceLocation>> byBiome;

    private RegionIndex(Map<ResourceLocation, List<ResourceLocation>> byBiome) {
        this.byBiome = byBiome;
    }

    private record Cached(Registry<WaypointRegion> source, RegionIndex index) {}

    /** Regions whose biome list contains {@code biome}; normally zero or one. */
    public List<ResourceLocation> regionsOf(ResourceLocation biome) {
        return byBiome.getOrDefault(biome, List.of());
    }

    public static RegionIndex build(Registry<WaypointRegion> regions, Registry<Biome> biomes) {
        Map<ResourceLocation, List<ResourceLocation>> map = new HashMap<>();
        for (var entry : regions.entrySet()) {
            ResourceLocation regionId = entry.getKey().location();
            for (String text : entry.getValue().biomes()) {
                if (text.startsWith("#")) {
                    ResourceLocation tagId = ResourceLocation.tryParse(text.substring(1));
                    if (tagId == null) {
                        continue;
                    }
                    biomes.getTag(TagKey.create(Registries.BIOME, tagId))
                            .ifPresent(set -> set.forEach(holder -> holder.unwrapKey()
                                    .ifPresent(key -> add(map, key.location(), regionId))));
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(text);
                    if (id != null) {
                        add(map, id, regionId);
                    }
                }
            }
        }
        return new RegionIndex(map);
    }

    private static void add(Map<ResourceLocation, List<ResourceLocation>> map, ResourceLocation biome, ResourceLocation region) {
        List<ResourceLocation> list = map.computeIfAbsent(biome, k -> new ArrayList<>());
        if (!list.contains(region)) {
            list.add(region);
        }
    }

    /** The index for these registries, built on first use. */
    public static RegionIndex of(RegistryAccess access) {
        Registry<WaypointRegion> regions = access.registryOrThrow(ModDataRegistries.<WaypointRegion>waypointRegion());
        Cached current = cached;
        if (current == null || current.source() != regions) {
            current = rebuild(access);
        }
        return current.index();
    }

    private static Cached rebuild(RegistryAccess access) {
        Registry<WaypointRegion> regions = access.registryOrThrow(ModDataRegistries.<WaypointRegion>waypointRegion());
        Cached next = new Cached(regions, build(regions, access.registryOrThrow(Registries.BIOME)));
        cached = next;
        return next;
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            rebuild(event.getRegistryAccess());
        }
    }
}
