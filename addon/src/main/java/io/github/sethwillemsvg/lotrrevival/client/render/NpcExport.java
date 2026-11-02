package io.github.sethwillemsvg.lotrrevival.client.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import io.github.sethwillemsvg.lotrrevival.client.anim.NpcAnimator;
import io.github.sethwillemsvg.lotrrevival.client.render.PulseGeometry.Quad;
import io.github.sethwillemsvg.lotrrevival.mixin.LivingEntityRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.sevenstars.middleearth.entity.EntityAttributesME;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.renderer.NpcEntityModel;
import net.sevenstars.middleearth.entity.npcs.renderer.NpcEntityRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records Middle-earth NPCs for the NPC clip viewer (tools/anim-viewer/npc.html): everything Middle-earth's own renderer draws
 * (skin layers, 3D hair, ears, nose and feet, armour) at rest, each face in its body part's space, plus the held items in the
 * hands, and the textures they use, cropped to the area used. Written to the game folder's viewer_export/, which is never
 * committed: the textures are Middle-earth's (All Rights Reserved) and only read from the player's own install.
 */
public final class NpcExport {
    private static final Logger LOG = LogUtils.getLogger();
    static final String[] PART_NAMES = {"head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final int RIGHT_ARM = 3, LEFT_ARM = 4;
    /** Vanilla biped boxes in each part's space (px): where a face drawn outside any humanoid part most likely belongs. */
    private static final float[][] BOXES = {
            {-4, -8, -4, 4, 0, 4}, {-4, -8, -4, 4, 0, 4}, {-4, 0, -2, 4, 12, 2},
            {-3, -2, -2, 1, 10, 2}, {-1, -2, -2, 3, 10, 2}, {-2, 0, -2, 2, 12, 2}, {-2, 0, -2, 2, 12, 2}};
    /** Order to try parts in when a face is near several: head first (hair, ears, nose sit on it), body last. */
    private static final int[] PRIORITY = {0, 3, 4, 5, 6, 2};

    /** One mesh: triangles in a body part's space (px, y down), UVs in its texture (0..1). hand: a held item (follows the forearm). */
    record Mesh(int part, ResourceLocation texture, int color, boolean translucent, boolean hand, float[] pos, float[] uv, int[] idx) {}

    public record Npc(String type, String race, String faction, String label, float widthScale, float scale, List<Mesh> meshes) {}

    private NpcExport() {}

    /** Draws one NPC the way Middle-earth's renderer does, at rest, into meshes. Render thread only. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Npc capture(NpcEntity entity, NpcEntityRenderer theirs) {
        NpcEntityModel model = theirs.getModel();
        float age = entity.tickCount;
        List<Mesh> meshes = new ArrayList<>();
        NpcAnimator.restPose = true;
        try {
            model.young = false;
            model.riding = false;
            model.crouching = false;
            model.attackTime = 0f;
            model.setAllVisible(false);   // as Middle-earth's render() does: its skin layer shows the parts it draws
            model.setupAnim(entity, 0f, 0f, age, 0f, 0f);
            ModelPart[] parts = {model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
            Matrix4f[] toPart = new Matrix4f[parts.length];
            for (int i = 0; i < parts.length; i++) {
                PoseStack ps = new PoseStack();
                parts[i].translateAndRotate(ps);
                toPart[i] = new Matrix4f(ps.last().pose()).invert();
            }
            PulseGeometry.RecordingBuffers buffers = new PulseGeometry.RecordingBuffers();
            PartCapture.begin();
            try {
                for (RenderLayer layer : ((LivingEntityRendererAccessor) theirs).lotrRevival$layers()) {
                    if (layer instanceof ItemInHandLayer) continue;   // held items: below, in hand space
                    try {
                        layer.render(new PoseStack(), buffers, 0xF000F0, entity, 0f, 0f, 0f, age, 0f, 0f);
                    } catch (RuntimeException e) {
                        LOG.warn("lotr_revival export: layer {} failed for {}", layer.getClass().getSimpleName(), entity.getNpcTypeIdentifier(), e);
                    }
                }
            } finally {
                PartCapture.end();
            }
            buffers.flush();
            buffers.byType.forEach((rt, rec) -> {
                ResourceLocation texture = texture(rt);
                if (texture == null) return;
                boolean translucent = rt.toString().contains("translucent");
                Map<List<Integer>, List<Quad>> groups = new LinkedHashMap<>();
                for (Quad q : rec.quads) {
                    Quad local = q;
                    int part = q.part();
                    if (part < 0) {   // drawn outside a humanoid part (Middle-earth's hair, ears, nose and feet models): by position
                        part = nearestPart(q, toPart);
                        local = transform(q, toPart[part], part);
                    }
                    groups.computeIfAbsent(List.of(part, q.color()), g -> new ArrayList<>()).add(local);
                }
                groups.forEach((g, quads) -> meshes.add(mesh(g.get(0), texture, g.get(1), translucent, false,
                        PulseGeometry.slice(quads, NpcJoints.cuts(g.get(0))))));
            });
        } finally {
            NpcAnimator.restPose = false;
        }
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        items(entity, rightHanded ? entity.getMainHandItem() : entity.getOffhandItem(), false, meshes);
        items(entity, rightHanded ? entity.getOffhandItem() : entity.getMainHandItem(), true, meshes);
        float width = entity.getAttributes().hasAttribute(EntityAttributesME.WIDTH_SCALE)
                ? (float) entity.getAttributeValue(EntityAttributesME.WIDTH_SCALE) : 1f;
        return new Npc(entity.getNpcTypeIdentifier().toString(), NpcAnimator.raceOf(entity), String.valueOf(entity.getFactionIdentifier()),
                label(entity), width, entity.getScale(), meshes);
    }

    /** A held item, drawn in the hand frame Minecraft's held-item layer uses and moved into the arm's space. */
    private static void items(NpcEntity entity, ItemStack stack, boolean left, List<Mesh> meshes) {
        if (stack.isEmpty()) return;
        ItemInHandRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer();
        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.XP.rotationDegrees(-90f));
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        ps.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
        PulseGeometry.RecordingBuffers buffers = new PulseGeometry.RecordingBuffers();
        renderer.renderItem(entity, stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                left, ps, buffers, 0xF000F0);
        buffers.flush();
        int part = left ? LEFT_ARM : RIGHT_ARM;
        buffers.byType.forEach((rt, rec) -> {
            ResourceLocation texture = texture(rt);
            if (texture == null) return;
            Map<Integer, List<Quad>> byColor = new LinkedHashMap<>();
            for (Quad q : rec.quads) byColor.computeIfAbsent(q.color(), c -> new ArrayList<>()).add(q);
            byColor.forEach((color, quads) -> meshes.add(mesh(part, texture, color, rt.toString().contains("translucent"), true, quads)));
        });
    }

    private static ResourceLocation texture(RenderType rt) {
        return rt.toString().contains("glint") ? null : PulseGeometry.texture(rt).orElse(null);
    }

    private static String label(NpcEntity entity) {
        String type = entity.getNpcTypeIdentifier().getPath();
        List<String> held = new ArrayList<>();
        for (ItemStack s : new ItemStack[] {entity.getMainHandItem(), entity.getOffhandItem()})
            if (!s.isEmpty()) held.add(s.getHoverName().getString());
        return held.isEmpty() ? type : type + " · " + String.join(" + ", held);
    }

    /** The part whose (vanilla) box is nearest the face's centre, in that part's space. */
    private static int nearestPart(Quad q, Matrix4f[] toPart) {
        Vector3f c = new Vector3f();
        for (Vector3f p : q.pos()) c.add(p);
        c.div(q.pos().length);
        int best = 2;
        float bestD = Float.MAX_VALUE;
        for (int i : PRIORITY) {
            Vector3f l = toPart[i].transformPosition(new Vector3f(c)).mul(16f);
            float[] b = BOXES[i];
            float dx = Math.max(Math.max(b[0] - l.x, 0), l.x - b[3]), dy = Math.max(Math.max(b[1] - l.y, 0), l.y - b[4]);
            float dz = Math.max(Math.max(b[2] - l.z, 0), l.z - b[5]);
            float d = dx * dx + dy * dy + dz * dz;
            if (d < bestD - 1e-4f) { bestD = d; best = i; }
        }
        return best;
    }

    private static Quad transform(Quad q, Matrix4f m, int part) {
        Vector3f[] pos = new Vector3f[q.pos().length];
        for (int i = 0; i < pos.length; i++) pos[i] = m.transformPosition(new Vector3f(q.pos()[i]));
        return new Quad(pos, q.u(), q.v(), q.normal(), q.color(), part, q.draw());
    }

    /** Faces (blocks) as one triangle mesh (px). */
    private static Mesh mesh(int part, ResourceLocation texture, int color, boolean translucent, boolean hand, List<Quad> faces) {
        int nv = 0, ni = 0;
        for (Quad q : faces) { nv += q.pos().length; ni += (q.pos().length - 2) * 3; }
        float[] pos = new float[nv * 3], uv = new float[nv * 2];
        int[] idx = new int[ni];
        int v = 0, i = 0;
        for (Quad q : faces) {
            int n = q.pos().length;
            for (int k = 1; k < n - 1; k++) { idx[i++] = v; idx[i++] = v + k; idx[i++] = v + k + 1; }
            for (int k = 0; k < n; k++, v++) {
                Vector3f p = q.pos()[k];
                pos[v * 3] = p.x * 16f; pos[v * 3 + 1] = p.y * 16f; pos[v * 3 + 2] = p.z * 16f;
                uv[v * 2] = q.u()[k]; uv[v * 2 + 1] = q.v()[k];
            }
        }
        return new Mesh(part, texture, color, translucent, hand, pos, uv, idx);
    }

    // ---- writing ----

    /** Writes the NPCs and their textures (cropped to the area the meshes use) to dir: index.json, npc_N.json, tex_N.png. */
    public static void write(Path dir, List<Npc> npcs) throws IOException {
        Files.createDirectories(dir);
        try (var old = Files.list(dir)) {
            for (Path p : old.toList()) if (p.getFileName().toString().matches("(npc|tex)_\\d+\\.(json|png)")) Files.delete(p);
        }
        // texture -> used UV area
        Map<ResourceLocation, float[]> used = new LinkedHashMap<>();
        for (Npc n : npcs)
            for (Mesh m : n.meshes()) {
                float[] b = used.computeIfAbsent(m.texture(), t -> new float[] {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE});
                for (int k = 0; k < m.uv().length; k += 2) {
                    b[0] = Math.min(b[0], m.uv()[k]); b[1] = Math.min(b[1], m.uv()[k + 1]);
                    b[2] = Math.max(b[2], m.uv()[k]); b[3] = Math.max(b[3], m.uv()[k + 1]);
                }
            }
        // crop and save each texture; remember how UVs map into the crop
        Map<ResourceLocation, float[]> remap = new LinkedHashMap<>();   // file index, u0, v0, du, dv (texture UV -> crop UV)
        JsonArray textures = new JsonArray();
        int t = 0;
        for (var e : used.entrySet()) {
            float[] b = e.getValue();
            try (NativeImage img = download(e.getKey())) {
                if (img == null) continue;
                int w = img.getWidth(), h = img.getHeight();
                int x0 = clamp((int) Math.floor(b[0] * w + 1e-3f), 0, w - 1), y0 = clamp((int) Math.floor(b[1] * h + 1e-3f), 0, h - 1);
                int x1 = clamp((int) Math.ceil(b[2] * w - 1e-3f), x0 + 1, w), y1 = clamp((int) Math.ceil(b[3] * h - 1e-3f), y0 + 1, h);
                try (NativeImage crop = new NativeImage(x1 - x0, y1 - y0, false)) {
                    img.copyRect(crop, x0, y0, 0, 0, x1 - x0, y1 - y0, false, false);
                    crop.writeToFile(dir.resolve("tex_" + t + ".png"));
                }
                remap.put(e.getKey(), new float[] {t, (float) x0 / w, (float) y0 / h, (float) w / (x1 - x0), (float) h / (y1 - y0)});
                JsonObject o = new JsonObject();
                o.addProperty("file", "tex_" + t + ".png");
                o.addProperty("source", e.getKey().toString());
                textures.add(o);
                t++;
            } catch (RuntimeException ex) {
                LOG.warn("lotr_revival export: could not save texture {}", e.getKey(), ex);
            }
        }
        Gson gson = new GsonBuilder().create();
        JsonArray index = new JsonArray();
        for (int i = 0; i < npcs.size(); i++) {
            Npc n = npcs.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("type", n.type());
            o.addProperty("race", n.race());
            o.addProperty("faction", n.faction());
            o.addProperty("label", n.label());
            o.addProperty("widthScale", n.widthScale());
            o.addProperty("scale", n.scale());
            JsonArray ms = new JsonArray();
            for (Mesh m : n.meshes()) {
                float[] r = remap.get(m.texture());
                if (r == null) continue;
                JsonObject mo = new JsonObject();
                mo.addProperty("part", PART_NAMES[m.part()]);
                mo.addProperty("tex", (int) r[0]);
                mo.addProperty("color", String.format("%08x", m.color()));
                if (m.translucent()) mo.addProperty("translucent", true);
                if (m.hand()) mo.addProperty("hand", true);
                mo.add("pos", numbers(m.pos(), 1000f));
                float[] uv = m.uv().clone();
                for (int k = 0; k < uv.length; k += 2) { uv[k] = (uv[k] - r[1]) * r[3]; uv[k + 1] = (uv[k + 1] - r[2]) * r[4]; }
                mo.add("uv", numbers(uv, 100000f));
                JsonArray idx = new JsonArray();
                for (int k : m.idx()) idx.add(k);
                mo.add("idx", idx);
                ms.add(mo);
            }
            o.add("meshes", ms);
            Files.writeString(dir.resolve("npc_" + i + ".json"), gson.toJson(o));
            JsonObject entry = new JsonObject();
            entry.addProperty("file", "npc_" + i + ".json");
            entry.addProperty("label", n.label());
            entry.addProperty("race", n.race());
            entry.addProperty("type", n.type());
            index.add(entry);
        }
        JsonObject root = new JsonObject();
        root.add("npcs", index);
        root.add("textures", textures);
        Files.writeString(dir.resolve("index.json"), new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    private static JsonArray numbers(float[] values, float precision) {
        JsonArray a = new JsonArray();
        for (float f : values) a.add(Math.round(f * precision) / precision);
        return a;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** A texture as the GPU has it (level 0), or null if it is not loaded. */
    private static NativeImage download(ResourceLocation id) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id);
        RenderSystem.bindTexture(tex.getId());
        int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w <= 0 || h <= 0) return null;
        NativeImage img = new NativeImage(w, h, false);
        img.downloadTexture(0, false);
        return img;
    }
}
