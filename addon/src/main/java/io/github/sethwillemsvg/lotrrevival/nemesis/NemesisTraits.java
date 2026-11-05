package io.github.sethwillemsvg.lotrrevival.nemesis;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Trait data from {@code data/<ns>/nemesis_traits/<id>.json} (spec Traits): kind, effect (from the fixed set in
 * {@link TraitEffects}), numbers, name lang key, optional side, race and faction filters. Reloadable.
 */
public final class NemesisTraits extends SimpleJsonResourceReloadListener {
    public enum Kind { STRENGTH, WEAKNESS, SCAR }

    public record Trait(ResourceLocation id, Kind kind, String effect, Map<String, Double> numbers, String nameKey,
                        Set<Sides.Side> sides, Set<String> races, Set<ResourceLocation> factions, String title, @Nullable DamageKind damage) {
        public double num(String key, double fallback) {
            return numbers.getOrDefault(key, fallback);
        }

        public boolean allows(Sides.Side side, String race, ResourceLocation faction) {
            return (sides.isEmpty() || sides.contains(side)) && (races.isEmpty() || races.contains(race))
                    && (factions.isEmpty() || factions.contains(faction));
        }
    }

    public static final Set<String> EFFECTS = Set.of("iron_hide", "bloodthirsty", "armour_breaker", "war_horn", "unshakeable",
            "parry_master", "crushing_blow", "grudge_damage", "fears_fire", "arrow_shy", "open_guard", "telegraphs", "sun_sick", "scar");

    private static volatile Map<ResourceLocation, Trait> traits = Map.of();

    public NemesisTraits() {
        super(new Gson(), "nemesis_traits");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Trait> out = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                Trait t = parse(id, json.getAsJsonObject());
                if (!EFFECTS.contains(t.effect())) {
                    LotrLog.warn("nemesis", "trait {} skipped: unknown effect {}", id, t.effect());
                    return;
                }
                out.put(id, t);
            } catch (RuntimeException e) {
                LotrLog.warn("nemesis", "trait {} skipped: {}", id, e.getMessage());
            }
        });
        traits = Map.copyOf(out);
        LotrLog.info("nemesis", "traits loaded: {} ({})", out.size(), out.keySet());
    }

    static Trait parse(ResourceLocation id, JsonObject o) {
        Kind kind = Kind.valueOf(GsonHelper.getAsString(o, "kind").toUpperCase(Locale.ROOT));
        String effect = GsonHelper.getAsString(o, "effect");
        Map<String, Double> nums = new HashMap<>();
        if (o.has("numbers")) for (var e : o.getAsJsonObject("numbers").entrySet()) nums.put(e.getKey(), e.getValue().getAsDouble());
        Set<Sides.Side> sides = new HashSet<>();
        if (o.has("sides")) for (JsonElement s : o.getAsJsonArray("sides")) sides.add(Sides.Side.valueOf(s.getAsString().toUpperCase(Locale.ROOT)));
        Set<String> races = new HashSet<>();
        if (o.has("races")) for (JsonElement s : o.getAsJsonArray("races")) races.add(s.getAsString());
        Set<ResourceLocation> factions = new HashSet<>();
        if (o.has("factions")) for (JsonElement s : o.getAsJsonArray("factions")) factions.add(ResourceLocation.parse(s.getAsString()));
        String nameKey = GsonHelper.getAsString(o, "name", "lotr_revival.nemesis.trait." + id.getPath());
        String title = GsonHelper.getAsString(o, "title", "");
        DamageKind dmg = o.has("damage") ? DamageKind.byKey(GsonHelper.getAsString(o, "damage")) : null;
        return new Trait(id, kind, effect, Map.copyOf(nums), nameKey, Set.copyOf(sides), Set.copyOf(races), Set.copyOf(factions), title, dmg);
    }

    public static @Nullable Trait get(@Nullable ResourceLocation id) {
        return id == null ? null : traits.get(id);
    }

    /** For loading saved records: before any trait file is loaded, keep every id. */
    public static boolean isKnownOrUnloaded(ResourceLocation id) {
        return traits.isEmpty() || traits.containsKey(id);
    }

    public static List<Trait> of(Kind kind, Sides.Side side, String race, ResourceLocation faction) {
        List<Trait> out = new ArrayList<>();
        for (Trait t : traits.values()) if (t.kind() == kind && t.allows(side, race, faction)) out.add(t);
        return out;
    }

    public static @Nullable Trait scarFor(DamageKind kind) {
        for (Trait t : traits.values()) if (t.kind() == Kind.SCAR && t.damage() == kind) return t;
        return null;
    }

    /** Rolls a strength not yet held; null when none is left. */
    public static @Nullable ResourceLocation rollStrength(NemesisRecord r, Sides.Side side) {
        List<Trait> pool = of(Kind.STRENGTH, side, r.race, r.faction);
        pool.removeIf(t -> r.strengths.contains(t.id()));
        if (pool.isEmpty()) return null;
        Trait t = NemesisRandom.pick(pool);
        LotrLog.dbg("nemesis", "trait roll: id={} strength={} from {} candidates", r.shortId(), t.id(), pool.size());
        return t.id();
    }

    public static @Nullable ResourceLocation rollWeakness(NemesisRecord r, Sides.Side side) {
        List<Trait> pool = of(Kind.WEAKNESS, side, r.race, r.faction);
        if (pool.isEmpty()) {
            LotrLog.warn("nemesis", "no weakness allowed for race={} faction={}: record {} has none", r.race, r.faction, r.shortId());
            return null;
        }
        Trait t = NemesisRandom.pick(pool);
        LotrLog.dbg("nemesis", "trait roll: id={} weakness={} from {} candidates", r.shortId(), t.id(), pool.size());
        return t.id();
    }

    /** Game tests: install a trait without a datapack. */
    public static void putForTest(Trait t) {
        Map<ResourceLocation, Trait> m = new LinkedHashMap<>(traits);
        m.put(t.id(), t);
        traits = Map.copyOf(m);
    }

    public static ResourceLocation id(String path) {
        return LotrRevival.id(path);
    }

    public static int count() {
        return traits.size();
    }
}
