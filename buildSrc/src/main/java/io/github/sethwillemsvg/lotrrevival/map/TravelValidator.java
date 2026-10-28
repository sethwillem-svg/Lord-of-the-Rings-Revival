package io.github.sethwillemsvg.lotrrevival.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The waypoint, region and road rules from specs/world/waypoints-travel.md ("Map validator additions"). Fails
 * when:
 * <ul>
 *   <li>a region lists a biome not in the colour table or a water biome, or a land biome is in no region or in
 *       two;
 *   <li>a waypoint id is duplicated or malformed, its region is unknown, its point is outside a painted window or
 *       within 1,024 blocks of the canvas edge, or the biome map pixel under it is not one of its region's
 *       biomes;
 *   <li>a road has fewer than 2 points, a point on a sea or lake pixel or outside a window, or a {@code through}
 *       waypoint further than {@code width + 16} blocks from the control polyline;
 *   <li>a faction id is not one of Core's 25 (see {@link FactionIds}).
 * </ul>
 */
final class TravelValidator {
    /** Biome ids that belong to no waypoint region. */
    static final Set<String> WATER_BIOMES = Set.of("ocean", "shore", "lake", "river");

    /** Extra reach beyond a road's half-width within which a {@code through} waypoint counts as on the road. */
    static final double THROUGH_SLACK_BLOCKS = 16;

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]*");

    /** The biome id under a heightmap pixel position, or null when unmapped or off the map. */
    interface BiomeLookup {
        String biomeAt(double u, double v);
    }

    /** Reads the rasterized biome map (one pixel = 2 x 2 heightmap pixels). */
    static final class GridLookup implements BiomeLookup {
        private final MapValidator.BiomeGrid grid;
        private final Map<Integer, String> idsByColor = new HashMap<>();
        private final int[] row;
        private int rowIndex = -1;

        GridLookup(MapValidator.BiomeGrid grid, BiomeTable table) {
            this.grid = grid;
            this.row = new int[grid.width()];
            for (BiomeTable.Biome biome : table.biomes) {
                idsByColor.putIfAbsent(biome.color(), biome.id());
            }
        }

        @Override
        public String biomeAt(double u, double v) {
            int bx = (int) Math.floor(u / 2);
            int by = (int) Math.floor(v / 2);
            if (bx < 0 || by < 0 || bx >= grid.width() || by >= grid.height()) {
                return null;
            }
            if (by != rowIndex) {
                grid.row(by, row);
                rowIndex = by;
            }
            return idsByColor.get(row[bx] & 0xFFFFFF);
        }
    }

    private TravelValidator() {}

    static MapValidator.Report validate(
            TravelSource src, BiomeTable table, List<Regions.Window> windows, BiomeLookup lookup) {
        MapValidator.Report report = new MapValidator.Report();

        // ---- regions ----
        Map<String, TravelSource.Region> regions = new HashMap<>();
        Map<String, List<String>> regionsByBiome = new HashMap<>();
        for (TravelSource.Region region : src.regions) {
            if (!ID.matcher(region.id()).matches()) {
                report.add("malformed id", "region '" + region.id() + "' is not snake_case");
            }
            regions.put(region.id(), region);
            for (String biome : region.biomes()) {
                if (biome.startsWith("#")) {
                    continue; // a tag: cannot be checked here
                }
                if (table.byId(biome) == null) {
                    report.add("region lists an unknown biome", region.id() + " lists '" + biome + "'");
                } else if (WATER_BIOMES.contains(biome)) {
                    report.add("water biome in a region", region.id() + " lists '" + biome + "'");
                } else {
                    regionsByBiome.computeIfAbsent(biome, k -> new ArrayList<>()).add(region.id());
                }
            }
        }
        for (BiomeTable.Biome biome : table.biomes) {
            if (WATER_BIOMES.contains(biome.id())) {
                continue;
            }
            List<String> in = regionsByBiome.getOrDefault(biome.id(), List.of());
            if (in.isEmpty()) {
                report.add("land biome in no region", biome.id());
            } else if (in.size() > 1) {
                report.add("land biome in several regions", biome.id() + " is in " + in);
            }
        }

        // ---- waypoints ----
        Map<String, TravelSource.Waypoint> waypointsById = new HashMap<>();
        for (TravelSource.Waypoint waypoint : src.waypoints) {
            if (!ID.matcher(waypoint.id()).matches()) {
                report.add("malformed id", "waypoint '" + waypoint.id() + "' is not snake_case");
            }
            if (waypointsById.put(waypoint.id(), waypoint) != null) {
                report.add("duplicate waypoint id", waypoint.id());
            }
            if (waypoint.faction() != null && !FactionIds.REAL.contains(waypoint.faction())) {
                report.add("unknown faction id", waypoint.id() + " names '" + waypoint.faction() + "'");
            }
            TravelSource.Region region = regions.get(waypoint.region());
            if (region == null) {
                report.add("waypoint in an unknown region", waypoint.id() + " names '" + waypoint.region() + "'");
            }
            boolean placed = MapValidator.checkPoint(
                    report, windows, "waypoint " + waypoint.id(), waypoint.u(), waypoint.v());
            if (placed && region != null) {
                String biome = lookup.biomeAt(waypoint.u(), waypoint.v());
                if (biome == null || !region.biomes().contains(biome)) {
                    report.add(
                            "waypoint in the wrong biome",
                            waypoint.id() + " is in " + (biome == null ? "an unmapped pixel" : biome)
                                    + ", region " + region.id() + " has " + region.biomes());
                }
            }
        }

        // ---- roads ----
        Set<String> roadIds = new HashSet<>();
        for (TravelSource.Road road : src.roads) {
            if (!ID.matcher(road.id()).matches()) {
                report.add("malformed id", "road '" + road.id() + "' is not snake_case");
            }
            if (!roadIds.add(road.id())) {
                report.add("duplicate road id", road.id());
            }
            if (road.width() <= 0) {
                report.add("road width", road.id() + " has width " + road.width());
            }
            if (road.points().size() < 2) {
                report.add("road with fewer than 2 points", road.id());
                continue;
            }
            for (double[] p : road.points()) {
                boolean placed = MapValidator.checkPoint(report, windows, "road " + road.id(), p[0], p[1]);
                if (placed) {
                    String biome = lookup.biomeAt(p[0], p[1]);
                    if ("ocean".equals(biome) || "lake".equals(biome)) {
                        report.add("road point in the sea or a lake", road.id() + " at (" + p[0] + ", " + p[1] + ")");
                    }
                }
            }
            Geometry.Polyline line = new Geometry.Polyline(road.points());
            for (String id : road.through()) {
                TravelSource.Waypoint waypoint = waypointsById.get(id);
                if (waypoint == null) {
                    report.add("road passes through an unknown waypoint", road.id() + " names '" + id + "'");
                    continue;
                }
                double blocks = line.distance(waypoint.u(), waypoint.v(), null) * Canvas.HEIGHT_BLOCKS_PER_PIXEL;
                if (blocks > road.width() + THROUGH_SLACK_BLOCKS) {
                    report.add(
                            "road misses a through waypoint",
                            String.format(
                                    "%s is %.0f blocks from %s (limit %.0f)",
                                    id, blocks, road.id(), road.width() + THROUGH_SLACK_BLOCKS));
                }
            }
        }
        return report;
    }

    // ---- self test with deliberately broken fixtures ----

    private static final List<Regions.Window> FIXTURE_WINDOWS = List.of(new Regions.Window("fixture", 1236, 1268, 2004, 1652));

    /** Shire land west of u 1700, Bree-land east of it, sea south of v 1600. */
    private static final BiomeLookup FIXTURE_LOOKUP = (u, v) -> v > 1600 ? "ocean" : u < 1700 ? "shire" : "bree_land";

    private static TravelSource cleanFixture(BiomeTable table) {
        TravelSource src = new TravelSource();
        for (BiomeTable.Biome biome : table.biomes) {
            if (!WATER_BIOMES.contains(biome.id())) {
                src.regions.add(new TravelSource.Region(biome.id(), biome.id(), new ArrayList<>(List.of(biome.id()))));
            }
        }
        src.waypoints.add(new TravelSource.Waypoint("fixture_town", "Fixture Town", "shire", "hobbit", "Lore.", false, 1620, 1460));
        src.waypoints.add(new TravelSource.Waypoint("fixture_bree", "Fixture Bree", "bree_land", "bree", "Lore.", false, 1819.5, 1466));
        src.roads.add(new TravelSource.Road(
                "fixture_road",
                "Fixture Road",
                4,
                true,
                new ArrayList<>(List.of(new double[] {1620, 1460}, new double[] {1700, 1462}, new double[] {1819.5, 1466})),
                new ArrayList<>(List.of("fixture_town", "fixture_bree"))));
        return src;
    }

    private interface Mutation {
        void apply(TravelSource src);
    }

    /**
     * Proves the validator catches what it should: a clean fixture passes, and each of the spec's broken
     * fixtures fails with its own category.
     *
     * @return null when everything behaves, otherwise what went wrong
     */
    static String selfTest(BiomeTable table) {
        String problem = check("clean fixture", null, cleanFixture(table), table);
        if (problem != null) {
            return problem;
        }
        record Case(String fixture, String category, Mutation mutation) {}
        List<Case> cases = List.of(
                new Case("unknown region", "waypoint in an unknown region", s -> s.waypoints.set(0, withRegion(s.waypoints.get(0), "nowhere"))),
                new Case("wrong biome", "waypoint in the wrong biome", s -> s.waypoints.set(0, at(s.waypoints.get(0), 1850, 1460))),
                new Case("duplicate id", "duplicate waypoint id", s -> s.waypoints.add(s.waypoints.get(0))),
                new Case("land biome in no region", "land biome in no region", s -> s.regions.removeIf(r -> r.id().equals("chetwood"))),
                new Case("land biome in two regions", "land biome in several regions", s -> s.regions.add(new TravelSource.Region("extra", "Extra", new ArrayList<>(List.of("shire"))))),
                new Case("water biome in a region", "water biome in a region", s -> s.regions.add(new TravelSource.Region("wet", "Wet", new ArrayList<>(List.of("ocean"))))),
                new Case("unknown biome in a region", "region lists an unknown biome", s -> s.regions.add(new TravelSource.Region("odd", "Odd", new ArrayList<>(List.of("no_such_biome"))))),
                new Case("road point in the sea", "road point in the sea or a lake", s -> s.roads.get(0).points().add(new double[] {1700, 1620})),
                new Case("road point outside every window", "point outside every painted window", s -> s.roads.get(0).points().add(0, new double[] {900, 1460})),
                new Case("waypoint near the canvas edge", "point near or outside the canvas edge", s -> s.waypoints.set(0, at(s.waypoints.get(0), 10, 10))),
                new Case("road with one point", "road with fewer than 2 points", s -> s.roads.set(0, new TravelSource.Road("short", "Short", 4, true, new ArrayList<>(List.of(new double[] {1620, 1460})), new ArrayList<>()))),
                new Case("through waypoint 200 blocks off", "road misses a through waypoint", s -> {
                    s.waypoints.add(new TravelSource.Waypoint("far_town", "Far Town", "shire", null, "Lore.", false, 1650, 1464));
                    s.roads.get(0).through().add("far_town");
                }),
                new Case("through waypoint unknown", "road passes through an unknown waypoint", s -> s.roads.get(0).through().add("ghost")),
                new Case("unknown faction", "unknown faction id", s -> s.waypoints.set(0, withFaction(s.waypoints.get(0), "elves_of_nowhere"))),
                new Case("duplicate road id", "duplicate road id", s -> s.roads.add(s.roads.get(0))),
                new Case("malformed id", "malformed id", s -> s.waypoints.set(0, withId(s.waypoints.get(0), "Fixture-Town"))));
        for (Case c : cases) {
            TravelSource src = cleanFixture(table);
            c.mutation().apply(src);
            problem = check(c.fixture(), c.category(), src, table);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static String check(String fixture, String category, TravelSource src, BiomeTable table) {
        MapValidator.Report report = validate(src, table, FIXTURE_WINDOWS, FIXTURE_LOOKUP);
        if (category == null) {
            return report.isEmpty() ? null : "travel validator self-test: fixture '" + fixture + "' should be clean but reported " + report.lines();
        }
        return report.has(category)
                ? null
                : "travel validator self-test: fixture '" + fixture + "' did not report '" + category + "'; got " + report.lines();
    }

    private static TravelSource.Waypoint withRegion(TravelSource.Waypoint w, String region) {
        return new TravelSource.Waypoint(w.id(), w.name(), region, w.faction(), w.lore(), w.hidden(), w.u(), w.v());
    }

    private static TravelSource.Waypoint withFaction(TravelSource.Waypoint w, String faction) {
        return new TravelSource.Waypoint(w.id(), w.name(), w.region(), faction, w.lore(), w.hidden(), w.u(), w.v());
    }

    private static TravelSource.Waypoint withId(TravelSource.Waypoint w, String id) {
        return new TravelSource.Waypoint(id, w.name(), w.region(), w.faction(), w.lore(), w.hidden(), w.u(), w.v());
    }

    private static TravelSource.Waypoint at(TravelSource.Waypoint w, double u, double v) {
        return new TravelSource.Waypoint(w.id(), w.name(), w.region(), w.faction(), w.lore(), w.hidden(), u, v);
    }
}
