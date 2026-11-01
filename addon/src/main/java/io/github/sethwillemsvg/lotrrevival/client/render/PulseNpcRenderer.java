package io.github.sethwillemsvg.lotrrevival.client.render;

import com.arcanc.pulselib.content.renderer.PRenderQueue;
import com.arcanc.pulselib.content.renderer.plan.PGeometryData;
import com.arcanc.pulselib.content.renderer.plan.PInstanceHeader;
import com.arcanc.pulselib.util.PRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sethwillemsvg.lotrrevival.client.bench.FrameStats;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Middle-earth NPCs drawn through PulseLib's render backend: static meshes kept on the GPU (PulseLib's geometry arena), and per
 * NPC only one transform per body part. All layers share Middle-earth's character atlas, so they share one render type.
 * Posing is the same as the other renderers (NpcBipedModel + our clips); only the drawing differs.
 *
 * Two ways to cut the skin into meshes:
 *  - merged (default): per NPC look, one mesh per body part holding all its skin layers, so 7 submissions per NPC. The eye layer
 *    stays a separate head mesh, so blinking doesn't need a second copy of everything.
 *  - layered: one mesh per (layer, body part), shared by every NPC with that layer, so about 7 x 9 submissions per NPC but
 *    batched across NPCs.
 * Faces that are fully transparent in a layer's texture are left out of its mesh (an eye layer is one face, not a body).
 * Note: PulseLib keeps every mesh it is given until the world is left or resources reload, so merged meshes add up with the
 * number of different NPC looks seen in a session (a few tens of KB each).
 */
public class PulseNpcRenderer extends VanillaNpcRenderer {
    /** POSITION_TEX_NORMAL: 3 floats, 2 floats, 3 signed bytes + 1 padding. */
    private static final int STRIDE = 24;
    /** Skin layers are stacked by growing each one a little (PulseLib batches draws, so draw order can't layer them). */
    private static final float LAYER_GROW = 0.0025f;   // blocks per layer (0.04 px)
    private static final int MAX_LAYERS = 16;
    /** New merged meshes built per frame at most; NPCs over the budget use the layered meshes until their turn. */
    private static final int BUILDS_PER_FRAME = 24;
    private static final int HEAD = 0, HAT = 1;

    private final boolean merged;
    private final List<List<Quad>> partQuads;
    private final Map<TextureAtlasSprite, PGeometryData[][]> layered = new HashMap<>();
    private final Map<List<TextureAtlasSprite>, PGeometryData[]> mergedMeshes = new HashMap<>();
    private TextureAtlas atlasSeen;
    private long buildFrame = -1;
    private int buildsThisFrame;

    public PulseNpcRenderer(EntityRendererProvider.Context context, boolean merged) {
        super(context);
        this.merged = merged;
        partQuads = bakeQuads(new NpcBipedModel());
    }

    @Override
    protected RenderLayer<NpcEntity, NpcBipedModel> skinLayer(RenderLayerParent<NpcEntity, NpcBipedModel> parent) {
        return new RenderLayer<>(parent) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, NpcEntity entity, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                TextureAtlas atlas = NpcSkin.atlas();
                if (atlas != atlasSeen) {   // resources reloaded: sprites (and their UVs) are new
                    layered.clear();
                    mergedMeshes.clear();
                    atlasSeen = atlas;
                }
                NpcSkin.Layers layers = NpcSkin.layers(entity, atlas);
                int n = Math.min(layers.sprites().size(), MAX_LAYERS);
                if (n == 0) return;
                PGeometryData[] whole = merged ? merged(layers, n) : null;
                RenderType type = PRenderTypes.RenderTypeProvider.trianglesCutout(atlas.location());
                int overlay = getOverlayCoords(entity, 0f);
                ModelPart[] parts = getParentModel().parts;
                for (int i = 0; i < parts.length; i++) {
                    ModelPart part = parts[i];
                    if (!part.visible) continue;
                    poseStack.pushPose();
                    part.translateAndRotate(poseStack);
                    Matrix4f m = poseStack.last().pose();
                    if (whole != null) {
                        submit(type, whole[i], m, light, overlay);
                        if (layers.eye() >= 0 && !layers.blinking() && (i == HEAD || i == HAT))
                            submit(type, layered(layers.sprites().get(layers.eye()), i, layers.eye()), m, light, overlay);
                    } else {
                        for (int k = 0; k < n; k++)
                            if (layers.drawn(k)) submit(type, layered(layers.sprites().get(k), i, k), m, light, overlay);
                    }
                    poseStack.popPose();
                }
            }
        };
    }

    private static void submit(RenderType type, PGeometryData mesh, Matrix4f m, int light, int overlay) {
        if (mesh != null) PRenderQueue.submitEntityMesh(type, mesh, new PInstanceHeader(m, 0xFFFFFFFF, light, overlay));
    }

    /** One layer of one part (null when the layer is transparent there). */
    private PGeometryData layered(TextureAtlasSprite sprite, int part, int layer) {
        PGeometryData[][] byPart = layered.computeIfAbsent(sprite, s -> new PGeometryData[partQuads.size()][MAX_LAYERS]);
        if (byPart[part][layer] == null) byPart[part][layer] = orEmpty(pack(List.of(new Slice(sprite, layer)), partQuads.get(part)));
        return byPart[part][layer] == EMPTY ? null : byPart[part][layer];
    }

    /** All layers but the eyes for every part, cached per look; null when this frame's build budget is used up. */
    private PGeometryData[] merged(NpcSkin.Layers layers, int n) {
        TextureAtlasSprite[] key = new TextureAtlasSprite[n];
        for (int k = 0; k < n; k++) key[k] = k == layers.eye() ? null : layers.sprites().get(k);
        List<TextureAtlasSprite> look = Arrays.asList(key);
        PGeometryData[] meshes = mergedMeshes.get(look);
        if (meshes != null) return meshes;
        long frame = FrameStats.frameIndex();
        if (frame != buildFrame) { buildFrame = frame; buildsThisFrame = 0; }
        if (buildsThisFrame++ >= BUILDS_PER_FRAME) return null;
        List<Slice> slices = new ArrayList<>();
        for (int k = 0; k < n; k++) if (key[k] != null) slices.add(new Slice(key[k], k));
        meshes = new PGeometryData[partQuads.size()];
        for (int i = 0; i < meshes.length; i++) meshes[i] = pack(slices, partQuads.get(i));
        mergedMeshes.put(look, meshes);
        return meshes;
    }

    // ---- geometry ----
    /** One cube face; center is its cube's center (for growing the cube like a CubeDeformation). */
    private record Quad(Vector3f[] pos, float[] u, float[] v, Vector3f normal, Vector3f center) {}

    /** A skin layer drawn into a mesh: its sprite and its place in the stack. */
    private record Slice(TextureAtlasSprite sprite, int layer) {}

    private static final PGeometryData EMPTY = new PGeometryData(ByteBuffer.allocate(0), ByteBuffer.allocate(0), STRIDE, 0,
            PGeometryData.IndexType.UNSIGNED_INT);

    private static PGeometryData orEmpty(PGeometryData g) {
        return g == null ? EMPTY : g;
    }

    /** Every cube face of each drawn part (and its overlay children), in the part's own space (blocks, y down): recorded by
     *  rendering the part once, at rest, into a vertex consumer that keeps what Minecraft emits. */
    private static List<List<Quad>> bakeQuads(NpcBipedModel model) {
        List<List<Quad>> out = new ArrayList<>();
        for (ModelPart part : model.parts) {
            part.setPos(0, 0, 0);
            part.setRotation(0, 0, 0);
            part.visible = true;
            Recorder rec = new Recorder();
            part.render(new PoseStack(), rec, 0xF000F0, 0);
            rec.flush();
            out.add(withCenters(rec.quads));
        }
        return out;
    }

    /** Collects ModelPart output (4 vertices per face). */
    private static final class Recorder implements VertexConsumer {
        final List<Quad> quads = new ArrayList<>();
        private final Vector3f[] pos = new Vector3f[4];
        private final float[] u = new float[4], v = new float[4];
        private int n = -1;
        private Vector3f normal = new Vector3f(0, 1, 0);

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            if (n == 3) flush();
            pos[++n] = new Vector3f(x, y, z);
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float uu, float vv) { u[n] = uu; v[n] = vv; return this; }
        @Override public VertexConsumer setUv1(int a, int b) { return this; }
        @Override public VertexConsumer setUv2(int a, int b) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { normal = new Vector3f(x, y, z); return this; }
        void flush() {
            if (n < 3) return;
            quads.add(new Quad(pos.clone(), u.clone(), v.clone(), new Vector3f(normal).normalize(), null));
            n = -1;
        }
    }

    /** ModelPart emits each cube as 6 consecutive faces: give every face its cube's center. */
    private static List<Quad> withCenters(List<Quad> quads) {
        List<Quad> out = new ArrayList<>(quads.size());
        for (int c = 0; c + 6 <= quads.size(); c += 6) {
            Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
            for (int f = c; f < c + 6; f++) for (Vector3f p : quads.get(f).pos) { min.min(p); max.max(p); }
            Vector3f center = min.add(max).mul(0.5f);
            for (int f = c; f < c + 6; f++) { Quad q = quads.get(f); out.add(new Quad(q.pos, q.u, q.v, q.normal, center)); }
        }
        return out;
    }

    /** True when any texel of the face's texture area is not fully transparent in the sprite. */
    private static boolean visible(Quad q, SpriteContents c) {
        float u0 = Math.min(Math.min(q.u[0], q.u[1]), Math.min(q.u[2], q.u[3])), u1 = Math.max(Math.max(q.u[0], q.u[1]), Math.max(q.u[2], q.u[3]));
        float v0 = Math.min(Math.min(q.v[0], q.v[1]), Math.min(q.v[2], q.v[3])), v1 = Math.max(Math.max(q.v[0], q.v[1]), Math.max(q.v[2], q.v[3]));
        int w = c.width(), h = c.height();
        int x0 = Math.max(0, (int) Math.floor(u0 * w + 1e-3f)), x1 = Math.min(w, (int) Math.ceil(u1 * w - 1e-3f));
        int y0 = Math.max(0, (int) Math.floor(v0 * h + 1e-3f)), y1 = Math.min(h, (int) Math.ceil(v1 * h - 1e-3f));
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if (!c.isTransparent(0, x, y)) return true;
        return false;
    }

    /** The visible faces of each slice, each slice grown to its place in the stack; null when nothing is visible. */
    private static PGeometryData pack(List<Slice> slices, List<Quad> quads) {
        List<Quad> faces = new ArrayList<>();
        List<Slice> owner = new ArrayList<>();
        for (Slice s : slices) {
            SpriteContents c = s.sprite.contents();
            for (Quad q : quads) if (visible(q, c)) { faces.add(q); owner.add(s); }
        }
        if (faces.isEmpty()) return null;
        ByteBuffer vb = ByteBuffer.allocateDirect(faces.size() * 4 * STRIDE).order(ByteOrder.nativeOrder());
        ByteBuffer ib = ByteBuffer.allocateDirect(faces.size() * 6 * 4).order(ByteOrder.nativeOrder());
        int base = 0;
        for (int f = 0; f < faces.size(); f++) {
            Quad q = faces.get(f);
            Slice s = owner.get(f);
            float grow = s.layer * LAYER_GROW;
            for (int k = 0; k < 4; k++) {
                Vector3f p = q.pos[k], c = q.center;   // grow the cube outward on every axis
                vb.putFloat(p.x + Math.signum(p.x - c.x) * grow).putFloat(p.y + Math.signum(p.y - c.y) * grow)
                        .putFloat(p.z + Math.signum(p.z - c.z) * grow);
                vb.putFloat(s.sprite.getU(q.u[k])).putFloat(s.sprite.getV(q.v[k]));
                vb.put(normalByte(q.normal.x)).put(normalByte(q.normal.y)).put(normalByte(q.normal.z)).put((byte) 0);
            }
            ib.putInt(base).putInt(base + 1).putInt(base + 2).putInt(base).putInt(base + 2).putInt(base + 3);
            base += 4;
        }
        vb.flip();
        ib.flip();
        return new PGeometryData(vb, ib, STRIDE, faces.size() * 6, PGeometryData.IndexType.UNSIGNED_INT);
    }

    private static byte normalByte(float f) {
        return (byte) Math.round(Math.max(-1f, Math.min(1f, f)) * 127f);
    }
}
