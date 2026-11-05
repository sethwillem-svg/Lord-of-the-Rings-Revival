package io.github.sethwillemsvg.lotrrevival.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The attack clips each race has per weapon class (attack_<class>_N) with their length and hit time, from
 * assets/lotr_revival/geckolib/animations/entity/npc_biped_<race>.hits.json (written with the race's animation file by
 * tools/anim-viewer/build_npc.js), read from the mod jar on either side, so server and client use one clip list and one hit.
 */
public final class AttackClips {
    private static final String DIR = "/assets/lotr_revival/geckolib/animations/entity/npc_biped_";
    /** A class without clips of its own swings like the next one in this list (then like a sword). */
    private static final Map<String, String> FALLBACK = Map.of("dagger", "sword", "longsword", "sword", "axe", "sword",
            "spear", "sword", "bow", "unarmed", "longbow", "unarmed", "crossbow", "unarmed", "shield", "unarmed", "banner", "sword");

    /** heavy: flagged "heavy": true in hits.json (combat-parry-warning.md: the clip a heavy swing plays). */
    public record Clip(String slot, float length, float hit, boolean heavy) {}

    private static final Map<String, Map<String, Clip>> HITS = new ConcurrentHashMap<>();
    private static final Map<String, List<Clip>> BY_CLASS = new ConcurrentHashMap<>();
    private static final Set<String> WARNED_HEAVY = ConcurrentHashMap.newKeySet();

    private AttackClips() {}

    /** The attack clips a race plays with a weapon class (null: empty hand), falling back to related classes; may be empty. */
    public static List<Clip> of(String race, @Nullable String weaponClass) {
        String cls = weaponClass == null ? "unarmed" : weaponClass;
        return BY_CLASS.computeIfAbsent(race + "/" + cls, k -> {
            Map<String, Clip> hits = hits(race);
            for (String c = cls; c != null; c = FALLBACK.get(c)) {
                List<Clip> found = new ArrayList<>();
                for (int i = 1; hits.containsKey("attack_" + c + "_" + i); i++) found.add(hits.get("attack_" + c + "_" + i));
                if (!found.isEmpty()) return List.copyOf(found);
                if (c.equals("sword") || c.equals("unarmed")) break;
            }
            return List.of();
        });
    }

    /**
     * The clip a heavy swing plays for a race and weapon class: one flagged heavy, else the one with the latest hit frame
     * (WARN once per race and class); null when the class has no clips.
     */
    @Nullable
    public static Clip heavy(String race, @Nullable String weaponClass) {
        List<Clip> clips = of(race, weaponClass);
        if (clips.isEmpty()) return null;
        Clip latest = clips.get(0);
        for (Clip c : clips) {
            if (c.heavy()) return c;
            if (c.hit() > latest.hit()) latest = c;
        }
        String key = race + "/" + (weaponClass == null ? "unarmed" : weaponClass);
        if (WARNED_HEAVY.add(key))
            LotrLog.warn("combat", "no \"heavy\" clip for {}, using the one with the latest hit frame: {} (hit {}s)", key, latest.slot(), latest.hit());
        return latest;
    }

    /** The clip with this slot name for a race, or null (the client looks up the hit time of a clip the server picked). */
    @Nullable
    public static Clip byName(String race, String slot) {
        return hits(race).get(slot);
    }

    private static Map<String, Clip> hits(String race) {
        return HITS.computeIfAbsent(race, r -> {
            Map<String, Clip> out = new HashMap<>();
            try (InputStream in = AttackClips.class.getResourceAsStream(DIR + r + ".hits.json")) {
                if (in == null) return out;
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.entrySet()) {
                    JsonObject c = e.getValue().getAsJsonObject();
                    boolean heavy = c.has("heavy") && c.get("heavy").getAsBoolean();
                    out.put(e.getKey(), new Clip(e.getKey(), c.get("length").getAsFloat(), c.get("hit").getAsFloat(), heavy));
                }
            } catch (Exception e) {
                LotrLog.warn("combat", "could not read the {} hit times: {}", r, e.toString());
            }
            return out;
        });
    }
}
