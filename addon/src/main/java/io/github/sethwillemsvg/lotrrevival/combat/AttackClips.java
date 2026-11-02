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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The attack clips each race has per weapon class (attack_<class>_N in assets/lotr_revival/clips/index.json, written by
 * tools/anim-viewer/bake_vanilla.js) with their length and hit time, read from the mod jar on either side.
 */
public final class AttackClips {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String DIR = "/assets/lotr_revival/clips/";
    /** A class without clips of its own swings like the next one in this list (then like a sword). */
    private static final Map<String, String> FALLBACK = Map.of("dagger", "sword", "longsword", "sword", "axe", "sword",
            "spear", "sword", "bow", "unarmed", "longbow", "unarmed", "crossbow", "unarmed", "shield", "unarmed", "banner", "sword");

    public record Clip(String slot, float length, float hit) {}

    private static JsonObject index;
    private static final Map<String, List<Clip>> BY_CLASS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Clip>> CLIPS = new ConcurrentHashMap<>();

    private AttackClips() {}

    /** The attack clips a race plays with a weapon class (null: empty hand), falling back to related classes; may be empty. */
    public static List<Clip> of(String race, @Nullable String weaponClass) {
        String cls = weaponClass == null ? "unarmed" : weaponClass;
        return BY_CLASS.computeIfAbsent(race + "/" + cls, k -> {
            for (String c = cls; c != null; c = FALLBACK.get(c)) {
                List<Clip> found = new ArrayList<>();
                for (int i = 1; ; i++) {
                    Optional<Clip> clip = clip(race, "attack_" + c + "_" + i);
                    if (clip.isEmpty()) break;
                    found.add(clip.get());
                }
                if (!found.isEmpty()) return found;
                if (c.equals("sword") || c.equals("unarmed")) break;
            }
            return List.of();
        });
    }

    private static Optional<Clip> clip(String race, String slot) {
        return CLIPS.computeIfAbsent(race + "/" + slot, key -> {
            if (!listed(race, slot)) return Optional.empty();
            try (InputStream in = AttackClips.class.getResourceAsStream(DIR + key + ".json")) {
                if (in == null) return Optional.empty();
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                return Optional.of(new Clip(slot, o.get("length").getAsFloat(), o.has("hit") ? o.get("hit").getAsFloat() : 0f));
            } catch (Exception e) {
                LOG.warn("lotr_revival: could not read clip {}", key, e);
                return Optional.empty();
            }
        });
    }

    private static synchronized boolean listed(String race, String slot) {
        if (index == null) {
            try (InputStream in = AttackClips.class.getResourceAsStream(DIR + "index.json")) {
                index = in == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                index = new JsonObject();
            }
        }
        if (!index.has(race)) return false;
        for (var e : index.getAsJsonArray(race)) if (e.getAsString().equals(slot)) return true;
        return false;
    }
}
