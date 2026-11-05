package io.github.sethwillemsvg.lotrrevival.nemesis;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Our own name banks (spec Names): {@code data/lotr_revival/nemesis_names/<race>.json} with {@code {"first": [...]}}, {@code any}
 * as fallback, and {@code titles.json}, a list of {@code {text, side, traits}}. Titles matching one of the traits are preferred.
 */
public final class NemesisNames extends SimpleJsonResourceReloadListener {
    public record Title(String text, String side, Set<String> traits) {}

    private static volatile Map<String, List<String>> firstByRace = Map.of();
    private static volatile List<Title> titles = List.of();
    private static final Set<String> WARNED = new HashSet<>();

    public NemesisNames() {
        super(new Gson(), "nemesis_names");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, List<String>> first = new HashMap<>();
        List<Title> t = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                if (id.getPath().equals("titles")) {
                    for (JsonElement e : json.getAsJsonArray()) {
                        JsonObject o = e.getAsJsonObject();
                        Set<String> traits = new HashSet<>();
                        if (o.has("traits")) for (JsonElement s : o.getAsJsonArray("traits")) traits.add(s.getAsString());
                        t.add(new Title(GsonHelper.getAsString(o, "text"), GsonHelper.getAsString(o, "side", "any"), traits));
                    }
                } else {
                    List<String> names = new ArrayList<>();
                    for (JsonElement e : GsonHelper.getAsJsonArray(json.getAsJsonObject(), "first")) names.add(e.getAsString());
                    first.computeIfAbsent(id.getPath(), k -> new ArrayList<>()).addAll(names);
                }
            } catch (RuntimeException e) {
                LotrLog.warn("nemesis", "name file {} skipped: {}", id, e.getMessage());
            }
        });
        firstByRace = Map.copyOf(first);
        titles = List.copyOf(t);
        WARNED.clear();
        Map<String, Integer> sizes = new HashMap<>();
        first.forEach((k, v) -> sizes.put(k, v.size()));
        LotrLog.info("nemesis", "name banks loaded: races={} titles={}", sizes, t.size());
    }

    public static String firstName(String race) {
        List<String> bank = firstByRace.get(race);
        if (bank == null || bank.isEmpty()) {
            if (WARNED.add(race)) LotrLog.warn("nemesis", "name bank empty for race {}: using 'any'", race);
            bank = firstByRace.getOrDefault("any", List.of());
        }
        return bank.isEmpty() ? "Nameless" : NemesisRandom.pick(bank);
    }

    public static String title(Sides.Side side, List<ResourceLocation> traitIds) {
        String s = side.name().toLowerCase(Locale.ROOT);
        List<Title> ok = new ArrayList<>(), matching = new ArrayList<>();
        for (Title t : titles) {
            if (!t.side().equals("any") && !t.side().equals(s)) continue;
            ok.add(t);
            for (ResourceLocation id : traitIds) if (t.traits().contains(id.getPath())) matching.add(t);
        }
        List<Title> from = matching.isEmpty() ? ok : matching;
        return from.isEmpty() ? "" : NemesisRandom.pick(from).text();
    }

    /** Rolls a unique {first, title} for the faction (10 re-rolls, then a numeral is added). */
    public static String[] uniqueName(NemesisSavedData data, ResourceLocation faction, String race, Sides.Side side,
                                      List<ResourceLocation> traits) {
        Set<String> taken = new HashSet<>();
        for (NemesisRecord r : data.living(faction)) taken.add(r.displayName());
        String first = "Nameless", title = "";
        for (int i = 0; i <= 10; i++) {
            first = firstName(race);
            title = title(side, traits);
            String full = title.isEmpty() ? first : first + " " + title;
            if (!taken.contains(full)) return new String[]{first, title};
        }
        for (int n = 2; n < 100; n++) {
            String numbered = first + " " + roman(n);
            String full = title.isEmpty() ? numbered : numbered + " " + title;
            if (!taken.contains(full)) {
                LotrLog.dbg("nemesis", "name re-rolls exhausted for {}: numeral added -> {}", faction, full);
                return new String[]{numbered, title};
            }
        }
        return new String[]{first, title};
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n < r.length ? r[n] : Integer.toString(n);
    }
}
