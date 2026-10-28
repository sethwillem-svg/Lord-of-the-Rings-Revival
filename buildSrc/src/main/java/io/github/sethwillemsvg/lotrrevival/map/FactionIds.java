package io.github.sethwillemsvg.lotrrevival.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The 25 player-facing faction ids a waypoint may name. buildSrc cannot see the mod sources, so this is a copy
 * of Core's table; {@link #compareWithCore} checks it against {@code core/faction/Factions.java} on every build.
 */
final class FactionIds {
    static final Set<String> REAL = new LinkedHashSet<>(List.of(
            "hobbit", "bree", "dunedain_north", "blue_mountains", "high_elf", "gundabad", "angmar", "wood_elf",
            "dol_guldur", "dale", "durins_folk", "lothlorien", "dunland", "isengard", "fangorn", "rohan", "gondor",
            "mordor", "dorwinion", "easterling", "near_harad", "far_harad", "harad_jungle", "half_troll", "utumno"));

    private static final Pattern KEY = Pattern.compile("ResourceKey<Faction>\\s+(\\w+)\\s*=\\s*key\\(\"([a-z0-9_]+)\"\\)");
    private static final Pattern REAL_LIST = Pattern.compile("REAL\\s*=\\s*List\\.of\\(([^;]*)\\)\\s*;");

    private FactionIds() {}

    /** Ids in {@code Factions.REAL} of Core's source file. */
    static List<String> parseCore(Path factionsJava) throws IOException {
        String source = Files.readString(factionsJava, StandardCharsets.UTF_8);
        Map<String, String> idsByConstant = new HashMap<>();
        Matcher keys = KEY.matcher(source);
        while (keys.find()) {
            idsByConstant.put(keys.group(1), keys.group(2));
        }
        Matcher list = REAL_LIST.matcher(source);
        if (!list.find()) {
            throw new IllegalStateException("No REAL = List.of(...) in " + factionsJava);
        }
        List<String> ids = new ArrayList<>();
        for (String constant : list.group(1).split(",")) {
            String name = constant.trim();
            if (name.isEmpty()) {
                continue;
            }
            String id = idsByConstant.get(name);
            if (id == null) {
                throw new IllegalStateException("Factions.REAL names unknown constant " + name);
            }
            ids.add(id);
        }
        return ids;
    }

    /** @return null when the copy matches Core's table, otherwise what differs */
    static String compareWithCore(Path factionsJava) throws IOException {
        Set<String> core = new LinkedHashSet<>(parseCore(factionsJava));
        if (core.equals(REAL)) {
            return null;
        }
        Set<String> missing = new LinkedHashSet<>(core);
        missing.removeAll(REAL);
        Set<String> extra = new LinkedHashSet<>(REAL);
        extra.removeAll(core);
        return "FactionIds.REAL (buildSrc) is out of date with Core's Factions.REAL: missing " + missing + ", extra "
                + extra + ". Update the copy in buildSrc FactionIds.java.";
    }
}
