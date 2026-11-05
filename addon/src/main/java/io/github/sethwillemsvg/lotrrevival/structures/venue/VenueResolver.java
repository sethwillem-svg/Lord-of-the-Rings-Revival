package io.github.sethwillemsvg.lotrrevival.structures.venue;

import io.github.sethwillemsvg.lotrrevival.structures.StructureInfo;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Venue kinds (from the tags) and structure info (from the data map) per structure id, resolved at lookup time and
 * cached; the cache is cleared on every datapack reload, so tag edits apply to entries indexed before.
 */
public final class VenueResolver {
    public record Resolved(Set<VenueKind> kinds, StructureInfo info) {}

    private static final Resolved UNKNOWN = new Resolved(Set.of(), StructureInfo.NONE);
    private static final Map<ResourceLocation, Resolved> CACHE = new ConcurrentHashMap<>();

    private VenueResolver() {}

    public static Set<VenueKind> kinds(Holder<Structure> holder) {
        EnumSet<VenueKind> out = EnumSet.noneOf(VenueKind.class);
        for (VenueKind k : VenueKind.values()) {
            if (holder.is(k.tag())) out.add(k);
        }
        return out;
    }

    public static Resolved resolve(RegistryAccess access, ResourceLocation id) {
        return CACHE.computeIfAbsent(id, k -> access.registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, k))
                .map(h -> new Resolved(Set.copyOf(kinds(h)), Optional.ofNullable(h.getData(StructureInfo.TYPE)).orElse(StructureInfo.NONE)))
                .orElse(UNKNOWN));
    }

    public static void clear() {
        CACHE.clear();
    }
}
