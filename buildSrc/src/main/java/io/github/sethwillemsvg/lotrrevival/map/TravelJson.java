package io.github.sethwillemsvg.lotrrevival.map;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the datapack registry JSON for waypoint regions, waypoints and roads
 * (specs/world/waypoints-travel.md, "Generated datapack registries"). Output is deterministic: keys sorted,
 * numbers without trailing zeros, two-space indent, scalar arrays on one line, LF line ends.
 */
final class TravelJson {
    static final String NAMESPACE = "lotrrevival";
    /** Where the registries live under the output directory ({@code data/lotrrevival/lotrrevival/<registry>}). */
    private static final String BASE = "data/" + NAMESPACE + "/" + NAMESPACE + "/";

    private TravelJson() {}

    static void writeAll(TravelSource source, Path outputDir) throws IOException {
        for (TravelSource.Region region : source.regions) {
            write(outputDir, "waypoint_region", region.id(), region(region));
        }
        for (TravelSource.Waypoint waypoint : source.waypoints) {
            write(outputDir, "waypoint", waypoint.id(), waypoint(waypoint));
        }
        for (TravelSource.Road road : source.roads) {
            write(outputDir, "road", road.id(), road(road));
        }
    }

    private static void write(Path outputDir, String registry, String id, String json) throws IOException {
        Path file = outputDir.resolve(BASE + registry + "/" + id + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    static String region(TravelSource.Region region) {
        Map<String, Object> json = new TreeMap<>();
        json.put("name", component("waypoint_region." + NAMESPACE + "." + region.id(), region.name()));
        List<Object> biomes = new ArrayList<>();
        for (String biome : region.biomes()) {
            biomes.add(location(biome));
        }
        json.put("biomes", biomes);
        return render(json);
    }

    static String waypoint(TravelSource.Waypoint waypoint) {
        Map<String, Object> json = new TreeMap<>();
        json.put("name", component("waypoint." + NAMESPACE + "." + waypoint.id(), waypoint.name()));
        json.put("lore", component("waypoint." + NAMESPACE + "." + waypoint.id() + ".lore", waypoint.lore()));
        json.put("pos", List.of(waypoint.u(), waypoint.v()));
        json.put("region", location(waypoint.region()));
        if (waypoint.faction() != null) {
            json.put("faction", location(waypoint.faction()));
        }
        json.put("hidden", waypoint.hidden());
        return render(json);
    }

    static String road(TravelSource.Road road) {
        Map<String, Object> json = new TreeMap<>();
        json.put("name", component("road." + NAMESPACE + "." + road.id(), road.name()));
        json.put("width", road.width());
        json.put("show_on_map", road.showOnMap());
        List<Object> points = new ArrayList<>();
        for (double[] p : road.points()) {
            points.add(List.of(p[0], p[1]));
        }
        json.put("points", points);
        return render(json);
    }

    private static Map<String, Object> component(String key, String fallback) {
        Map<String, Object> component = new TreeMap<>();
        component.put("translate", key);
        component.put("fallback", fallback);
        return component;
    }

    /** Source files use bare ids; registry JSON needs full resource locations. */
    private static String location(String id) {
        return id.indexOf(':') >= 0 || id.startsWith("#") ? id : NAMESPACE + ":" + id;
    }

    // ---- minimal deterministic JSON writer ----

    static String render(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.append('\n').toString();
    }

    private static void write(StringBuilder sb, Object value, int indent) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                pad(sb, indent + 2);
                string(sb, (String) e.getKey());
                sb.append(": ");
                write(sb, e.getValue(), indent + 2);
            }
            sb.append('\n');
            pad(sb, indent);
            sb.append('}');
        } else if (value instanceof List<?> list) {
            boolean scalars = list.stream().noneMatch(x -> x instanceof Map || x instanceof List);
            if (list.isEmpty()) {
                sb.append("[]");
            } else if (scalars) {
                sb.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    write(sb, list.get(i), indent);
                }
                sb.append(']');
            } else {
                sb.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    pad(sb, indent + 2);
                    write(sb, list.get(i), indent + 2);
                    sb.append(i + 1 < list.size() ? ",\n" : "\n");
                }
                pad(sb, indent);
                sb.append(']');
            }
        } else if (value instanceof String s) {
            string(sb, s);
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (value instanceof Number n) {
            sb.append(number(n.doubleValue()));
        } else {
            throw new IllegalArgumentException("Unsupported JSON value " + value);
        }
    }

    static String number(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) {
            sb.append(' ');
        }
    }

    // ---- golden test ----

    /**
     * Generates one region, one waypoint and one road from a fixed fixture and compares them byte for byte
     * with the checked-in expected files in {@code goldenDir} ({@code waypoint_region.json},
     * {@code waypoint.json}, {@code road.json}).
     *
     * @return null when they match, otherwise what differs
     */
    static String goldenTest(Path goldenDir) throws IOException {
        TravelSource.Region region = new TravelSource.Region(
                "bree_land", "Bree-land", List.of("bree_land", "chetwood", "midgewater_marshes"));
        TravelSource.Waypoint waypoint = new TravelSource.Waypoint(
                "bree",
                "Bree",
                "bree_land",
                "bree",
                "The chief village of Bree-land, where the East Road meets the Greenway.",
                false,
                1819.5,
                1466);
        TravelSource.Road road = new TravelSource.Road(
                "greenway",
                "The Greenway",
                4,
                true,
                List.of(new double[] {1815, 1300}, new double[] {1818, 1400}, new double[] {1819.5, 1466}),
                List.of());
        String[][] cases = {
            {"waypoint_region.json", region(region)},
            {"waypoint.json", waypoint(waypoint)},
            {"road.json", road(road)},
        };
        for (String[] c : cases) {
            Path expected = goldenDir.resolve(c[0]);
            if (!Files.isRegularFile(expected)) {
                return "generated-JSON golden test: missing expected file " + expected;
            }
            String text = Files.readString(expected, StandardCharsets.UTF_8);
            if (!text.equals(c[1])) {
                return "generated-JSON golden test: " + c[0]
                        + " does not match its expected file byte for byte.\n--- expected\n" + text
                        + "--- generated\n" + c[1];
            }
        }
        return null;
    }
}
