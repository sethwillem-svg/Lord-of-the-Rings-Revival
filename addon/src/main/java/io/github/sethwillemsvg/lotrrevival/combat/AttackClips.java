package io.github.sethwillemsvg.lotrrevival.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The attack clips each race has per weapon class (attack_<class>_N) with their length and hit time, from
 * assets/lotr_revival/geckolib/animations/entity/npc_biped_<race>.hits.json (written with the race's animation file by
 * tools/anim-viewer/build_npc.js), read from the mod jar on either side, so server and client use one clip list and one hit.
 */
public final class AttackClips {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String DIR = "/assets/lotr_revival/geckolib/animations/entity/npc_biped_";
    /** A class without clips of its own swings like the next one in this list (then like a sword). */
    private static final Map<String, String> FALLBACK = Map.of("dagger", "sword", "longsword", "sword", "axe", "sword",
            "spear", "sword", "bow", "unarmed", "longbow", "unarmed", "crossbow", "unarmed", "shield", "unarmed", "banner", "sword");

    /** An attack: its length, hit time, and how long it fades in and out over the NPC's stance (seconds). */
    public record Clip(String slot, float length, float hit, float fadeIn, float fadeOut) {}

    private static final Map<String, Map<String, Clip>> HITS = new ConcurrentHashMap<>();
    private static final Map<String, List<Clip>> BY_CLASS = new ConcurrentHashMap<>();

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

    /** An attack by name, including its _l and _shield versions (they share the plain attack's timing), or null. */
    @Nullable
    public static Clip clip(String race, String name) {
        return hits(race).get(name.replaceAll("_(l|shield)$", ""));
    }

    private static Map<String, Clip> hits(String race) {
        return HITS.computeIfAbsent(race, r -> {
            Map<String, Clip> out = new HashMap<>();
            try (InputStream in = AttackClips.class.getResourceAsStream(DIR + r + ".hits.json")) {
                if (in == null) return out;
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.entrySet()) {
                    JsonObject c = e.getValue().getAsJsonObject();
                    out.put(e.getKey(), new Clip(e.getKey(), c.get("length").getAsFloat(), c.get("hit").getAsFloat(),
                            c.has("fadeIn") ? c.get("fadeIn").getAsFloat() : 0.1f, c.has("fadeOut") ? c.get("fadeOut").getAsFloat() : 0.15f));
                }
            } catch (Exception e) {
                LOG.warn("lotr_revival: could not read the {} hit times", r, e);
            }
            return out;
        });
    }
}
