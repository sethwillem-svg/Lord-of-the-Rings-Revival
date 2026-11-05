package io.github.sethwillemsvg.lotrrevival.structures;

import java.util.List;
import java.util.Optional;

/**
 * The region table (specs/addon/structures.md, Placement). Keep in step with tools/structure-import/regions.json, which
 * the tool uses to write the sets and biome tags. Middle-earth ids are integration points only.
 */
public final class Regions {
    private Regions() {}

    public record Region(String id, String faction, String middleEarthSet, int spacing, int separation) {
        public Optional<String> factionId() {
            return Optional.ofNullable(faction);
        }
    }

    public static final int CROSSINGS_SPACING = 12;
    public static final int CROSSINGS_SEPARATION = 4;

    public static final List<Region> ALL = List.of(
            new Region("gondor", "middle-earth:gondor", "middle-earth:gondor", 24, 10),
            new Region("rohan", "middle-earth:rohan", "middle-earth:rohan", 24, 10),
            new Region("dale", "middle-earth:dale", "middle-earth:dale", 24, 10),
            new Region("erebor", "middle-earth:longbeards.erebor", "middle-earth:longbeards", 28, 12),
            new Region("lothlorien", "middle-earth:lothlorien", "middle-earth:lothlorien", 20, 8),
            new Region("woodland_realm", "middle-earth:woodland_realm", "middle-earth:woodland_realm", 20, 8),
            new Region("shire", "middle-earth:shire", "middle-earth:shire", 24, 10),
            new Region("mordor", "middle-earth:mordor", "middle-earth:mordor", 24, 10),
            new Region("isengard", "middle-earth:isengard", "middle-earth:isengard", 20, 8),
            new Region("gundabad", "middle-earth:hobgoblin_tribes.gundabad", "middle-earth:gundabad", 24, 10),
            new Region("brigand", "middle-earth:brigand", "middle-earth:anduin_vales", 28, 12),
            new Region("wild", null, "middle-earth:cardolan", 32, 14));

    public static Optional<Region> byId(String id) {
        return ALL.stream().filter(r -> r.id().equals(id)).findFirst();
    }
}
