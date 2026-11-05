package io.github.sethwillemsvg.lotrrevival.structures.venue;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** What {@link StructureVenues#find} looks for. */
public record VenueQuery(Set<VenueKind> kinds, Predicate<Optional<ResourceLocation>> faction, boolean includeIncomplete,
                         int limit) {
    /** Any faction, complete venues only, at most 16. No kinds means every kind. */
    public static VenueQuery of(VenueKind... kinds) {
        Set<VenueKind> set = kinds.length == 0 ? EnumSet.allOf(VenueKind.class) : EnumSet.of(kinds[0], kinds);
        return new VenueQuery(set, f -> true, false, 16);
    }

    public VenueQuery withIncomplete() {
        return new VenueQuery(kinds, faction, true, limit);
    }

    public VenueQuery withFaction(Predicate<Optional<ResourceLocation>> f) {
        return new VenueQuery(kinds, f, includeIncomplete, limit);
    }

    public VenueQuery withLimit(int n) {
        return new VenueQuery(kinds, faction, includeIncomplete, n);
    }
}
