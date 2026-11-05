package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How far a bow is drawn while an NPC plays its bow clips, from the clips' own timing, not from the entity's using-item
 * state: the item model the renderer shows (slack, pulling_0..2) follows the drawing hand as the clip moves it. Each race's
 * timing is in assets/lotr_revival/geckolib/animations/entity/npc_biped_<race>.draw.json (written with the race's
 * animation file by tools/anim-viewer/build_npc.js, measured from the drawing hand: gecko/bow.js drawTiming), in seconds
 * of clip time: drawStart, bow_aim's hand on the string; fullDraw, its full draw; release, bow_release's string leaving
 * the fingers. The same rules are tools/anim-viewer/gecko/bow.js pullAt and stageOf.
 */
public final class BowDraw {
    private BowDraw() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final String DIR = "/assets/lotr_revival/geckolib/animations/entity/npc_biped_";
    private static final Map<String, Optional<Timing>> TIMING = new ConcurrentHashMap<>();

    /** A race's draw timing (seconds of clip time). */
    public record Timing(float drawStart, float fullDraw, float release) {}

    /** The item models by draw: 0 slack (not drawing), then Minecraft's bow predicates: 1 pulling_0 (pulling), 2 pulling_1
     *  (pull 0.65), 3 pulling_2 (pull 0.9). */
    public static final int SLACK = 0, PULLING_0 = 1, PULLING_1 = 2, PULLING_2 = 3;
    public static final float PULL_1 = 0.65f, PULL_2 = 0.9f;

    /** The model shown at draw progress p (0 to 1). */
    public static int stageOf(float p) {
        if (!(p > 0)) return SLACK;
        return p < PULL_1 ? PULLING_0 : p < PULL_2 ? PULLING_1 : PULLING_2;
    }

    /** The predicate values that pick a stage's model: { pulling, pull }, each at its threshold (a stage's own band). */
    public static float[] predicates(int stage) {
        return switch (stage) {
            case PULLING_0 -> new float[] {1f, 0f};
            case PULLING_1 -> new float[] {1f, PULL_1};
            case PULLING_2 -> new float[] {1f, PULL_2};
            default -> new float[] {0f, 0f};
        };
    }

    /** Draw progress in a bow clip at clip time t (s): bow_aim draws from drawStart to fullDraw, straight, and holds;
     *  bow_release holds the full draw until its release, then the string is slack; 0 for anything else. */
    public static float pullAt(@Nullable Timing d, String clip, float t) {
        if (d == null) return 0f;
        if (clip.equals("bow_aim")) return t <= d.drawStart() ? 0f : Math.min(1f, (t - d.drawStart()) / Math.max(1e-6f, d.fullDraw() - d.drawStart()));
        if (clip.equals("bow_release")) return t < d.release() ? 1f : 0f;
        return 0f;
    }

    /** A crossbow's reload thresholds (Minecraft's crossbow predicates: pulling; pull 0.58; pull 1). */
    public static final float CROSSBOW_PULL_1 = 0.58f, CROSSBOW_PULL_2 = 1f;

    /**
     * The model a crossbow shows while it is reloaded (SLACK..PULLING_2), from its real reload: unlike a bow's draw, a
     * crossbow's is timed by the server (its charge duration; the "charged" model comes with the item's loaded projectiles
     * when it ends), so the entity's own using state is the truth here and the stage only keys the renderer's recording
     * (client.render.PulseNpcRenderer) so it does not freeze. pull: ticks used over the charge duration.
     */
    public static int crossbowStage(boolean reloading, boolean charged, float pull) {
        if (!reloading || charged) return SLACK;
        return pull >= CROSSBOW_PULL_2 ? PULLING_2 : pull >= CROSSBOW_PULL_1 ? PULLING_1 : PULLING_0;
    }

    /** A race's draw timing, read once; null if its file is missing or unreadable. */
    @Nullable
    public static Timing of(String race) {
        return TIMING.computeIfAbsent(race, r -> {
            try (InputStream in = BowDraw.class.getResourceAsStream(DIR + r + ".draw.json")) {
                if (in == null) return Optional.empty();
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                return Optional.of(new Timing(o.get("drawStart").getAsFloat(), o.get("fullDraw").getAsFloat(), o.get("release").getAsFloat()));
            } catch (Exception e) {
                LOG.warn("lotr_revival: could not read the draw timing of {}", r, e);
                return Optional.empty();
            }
        }).orElse(null);
    }
}
