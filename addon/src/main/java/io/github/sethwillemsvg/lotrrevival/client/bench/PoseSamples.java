package io.github.sethwillemsvg.lotrrevival.client.bench;

import com.arcanc.pulselib.content.animatable.PAnimationController;
import com.arcanc.pulselib.content.model.animation.PAnimationGraph;
import com.arcanc.pulselib.content.model.animation.PAnimationState;
import com.arcanc.pulselib.content.model.animation.PAnimationType;
import com.arcanc.pulselib.content.model.animation.PInterpolationType;
import com.arcanc.pulselib.content.model.baked.PBakedModel;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.client.anim.NpcAnimatable;
import io.github.sethwillemsvg.lotrrevival.client.anim.NpcPose;
import io.github.sethwillemsvg.lotrrevival.client.anim.PulseClientPlugin;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The game's own poses for a few clips at fixed points of each (PulseLib evaluating the race files), written beside the
 * viewer export as pose_samples.json: { race: { clip: [ { phase, bones: { bone: [16 floats, column-major, blocks] } } ] } },
 * each bone's rest-to-posed motion in rig space (client.anim.NpcPose.rigMotion). tools/anim-viewer/test/parity.test.js
 * checks the viewer poses the same clips the same way.
 */
public final class PoseSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static final List<String> CLIPS = List.of("idle", "walk", "run", "ride_move", "guard", "spear_brace", "bow_aim",
            "attack_sword_1", "attack_sword_3", "attack_longsword_1", "attack_spear_1", "attack_dagger_1", "attack_axe_1",
            "attack_sword_1_l", "hurt");
    private static final float[] PHASES = {0f, 0.25f, 0.5f, 0.75f, 1f};

    private PoseSamples() {}

    public static void write(Path dir) {
        JsonObject root = new JsonObject();
        for (var e : PulseClientPlugin.MODELS.entrySet()) {
            PBakedModel model = e.getValue().getModel();
            if (model == null) continue;
            JsonObject race = new JsonObject();
            for (String clip : CLIPS) {
                if (!model.animations().containsKey(clip)) continue;
                JsonArray samples = new JsonArray();
                for (float phase : PHASES) {
                    try {
                        samples.add(sample(model, clip, phase));
                    } catch (RuntimeException ex) {
                        LOG.warn("[lotr_revival export] could not sample {} {} at {}", e.getKey(), clip, phase, ex);
                    }
                }
                race.add(clip, samples);
            }
            root.add(e.getKey(), race);
        }
        try {
            Files.writeString(dir.resolve("pose_samples.json"), new GsonBuilder().create().toJson(root));
            LOG.info("[lotr_revival export] wrote pose_samples.json");
        } catch (IOException ex) {
            LOG.warn("[lotr_revival export] could not write pose_samples.json", ex);
        }
    }

    /** One clip in a one-state graph, put at a phase of its length (PulseLib's syncCycle), then evaluated. */
    private static JsonObject sample(PBakedModel model, String clip, float phase) {
        PAnimationGraph graph = new PAnimationGraph(List.of(new PAnimationState.Clip(clip, clip, PAnimationType.HOLD_LAST_FRAME,
                PInterpolationType.LINEAR, 1f, false)), List.of());
        PAnimationController<NpcAnimatable> c = new PAnimationController<>("probe", graph);
        c.graphRuntime().syncCycle(model, phase);
        Matrix4f[] rig = NpcPose.rigMotion(model, model.evaluate(List.of(c), Map.of(), 0f));
        JsonObject bones = new JsonObject();
        for (int i = 0; i < rig.length; i++) {
            float[] m = rig[i].get(new float[16]);
            JsonArray a = new JsonArray();
            for (float v : m) a.add(v);
            bones.add(model.bone(i).name(), a);
        }
        JsonObject o = new JsonObject();
        o.addProperty("phase", phase);
        o.add("bones", bones);
        return o;
    }
}
