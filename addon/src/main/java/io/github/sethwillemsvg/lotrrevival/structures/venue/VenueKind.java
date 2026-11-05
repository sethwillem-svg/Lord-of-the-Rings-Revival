package io.github.sethwillemsvg.lotrrevival.structures.venue;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Event venue kinds; each is the structure tag {@code #lotr_revival:event_venues/<lower name>}. */
public enum VenueKind {
    OUTPOST, CASTLE, CAMP, BRIDGE, RUIN, WATCHTOWER, TOWER, HIDEOUT, VILLAGE;

    private final TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, LotrRevival.id("event_venues/" + id()));

    public TagKey<Structure> tag() {
        return tag;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<VenueKind> byId(String id) {
        for (VenueKind k : values()) {
            if (k.id().equals(id)) return Optional.of(k);
        }
        return Optional.empty();
    }
}
