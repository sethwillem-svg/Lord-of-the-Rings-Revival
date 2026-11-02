package io.github.sethwillemsvg.lotrrevival.client.render;

import com.arcanc.pulselib.content.renderer.plan.PGeometryData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sethwillemsvg.lotrrevival.mixin.CompositeRenderTypeAccessor;
import io.github.sethwillemsvg.lotrrevival.mixin.CompositeStateAccessor;
import io.github.sethwillemsvg.lotrrevival.mixin.TextureStateShardAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turning Minecraft rendering into PulseLib meshes: record what a ModelPart or an item emits once (at rest, in its own space),
 * then pack it as static POSITION_TEX_NORMAL triangles for PulseLib's geometry arena. PulseLib's vertex format has no colour,
 * so colour is per instance: recorded faces are grouped by colour.
 */
final class PulseGeometry {
    /** POSITION_TEX_NORMAL: 3 floats, 2 floats, 3 signed bytes + 1 padding. */
    static final int STRIDE = 24;

    private PulseGeometry() {}

    /** One recorded face: its corners (4 as recorded; any convex polygon after slice), their UVs, the face normal and the
     *  colour Minecraft gave it. While PartCapture is active, part is the body part that drew it (corners and normal in that
     *  part's own space) and draw the model draw it came from. */
    record Quad(Vector3f[] pos, float[] u, float[] v, Vector3f normal, int color, int part, int draw) {
        Quad(Vector3f[] pos, float[] u, float[] v, Vector3f normal, int color) {
            this(pos, u, v, normal, color, -1, -1);
        }
    }

    /** Collects ModelPart / item output, 4 vertices per face. */
    static final class Recorder implements VertexConsumer {
        final List<Quad> quads = new ArrayList<>();
        private final Vector3f[] pos = new Vector3f[4];
        private final float[] u = new float[4], v = new float[4];
        private int n = -1, color = -1, part = -1, draw = -1;
        private Vector3f normal = new Vector3f(0, 1, 0);

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            if (n == 3) flush();
            Vector3f p = new Vector3f(x, y, z);
            if (n == -1) { part = PartCapture.active ? PartCapture.part : -1; draw = PartCapture.active ? PartCapture.draw : -1; }
            if (part >= 0) PartCapture.toLocal.transformPosition(p);
            pos[++n] = p;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { color = a << 24 | r << 16 | g << 8 | b; return this; }
        @Override public VertexConsumer setUv(float uu, float vv) { u[n] = uu; v[n] = vv; return this; }
        @Override public VertexConsumer setUv1(int a, int b) { return this; }
        @Override public VertexConsumer setUv2(int a, int b) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            normal = new Vector3f(x, y, z);
            if (part >= 0) PartCapture.toLocalNormal.transform(normal);
            return this;
        }

        void flush() {
            if (n < 3) return;
            quads.add(new Quad(pos.clone(), u.clone(), v.clone(), new Vector3f(normal).normalize(), color, part, draw));
            n = -1;
        }
    }

    /** A part and its children at rest, in the part's own space (blocks, y down). The part's pose is restored afterwards. */
    static List<Quad> capture(ModelPart part) {
        float x = part.x, y = part.y, z = part.z, rx = part.xRot, ry = part.yRot, rz = part.zRot;
        boolean visible = part.visible;
        part.setPos(0, 0, 0);
        part.setRotation(0, 0, 0);
        part.visible = true;
        Recorder rec = new Recorder();
        part.render(new PoseStack(), rec, 0xF000F0, 0);
        rec.flush();
        part.setPos(x, y, z);
        part.setRotation(rx, ry, rz);
        part.visible = visible;
        return rec.quads;
    }

    /** Records whatever is drawn into it, per render type. */
    static final class RecordingBuffers implements MultiBufferSource {
        final Map<RenderType, Recorder> byType = new LinkedHashMap<>();

        @Override public VertexConsumer getBuffer(RenderType type) {
            return byType.computeIfAbsent(type, t -> new Recorder());
        }

        void flush() {
            byType.values().forEach(Recorder::flush);
        }
    }

    /** The texture a render type draws with, if it has one. */
    static Optional<ResourceLocation> texture(RenderType type) {
        if (!(type instanceof CompositeRenderTypeAccessor composite)) return Optional.empty();
        var shard = ((CompositeStateAccessor) (Object) composite.lotrRevival$state()).lotrRevival$textureState();
        return ((TextureStateShardAccessor) shard).lotrRevival$cutoutTexture();
    }

    /**
     * Faces cut at the heights ys (part space, blocks, ascending): every face crossing one becomes two polygons, with positions
     * and UVs interpolated along the cut. Gives a bend deformer vertices to curve (NpcJoints.cuts).
     */
    static List<Quad> slice(List<Quad> quads, float[] ys) {
        if (ys.length == 0) return quads;
        List<Quad> out = new ArrayList<>(quads);
        for (float y : ys) {
            List<Quad> next = new ArrayList<>(out.size());
            for (Quad q : out) {
                Quad below = clip(q, y, true), above = clip(q, y, false);
                if (below != null) next.add(below);
                if (above != null) next.add(above);
            }
            out = next;
        }
        return out;
    }

    /** The part of a convex face with y <= cut (below) or y >= cut; null when nothing (or only an edge) is left. */
    @Nullable
    private static Quad clip(Quad q, float cut, boolean below) {
        int n = q.pos.length;
        List<Vector3f> pos = new ArrayList<>(n + 2);
        List<float[]> uv = new ArrayList<>(n + 2);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Vector3f a = q.pos[i], b = q.pos[j];
            float da = below ? cut - a.y : a.y - cut, db = below ? cut - b.y : b.y - cut;   // >= 0: kept side
            if (da >= 0) { pos.add(a); uv.add(new float[] {q.u[i], q.v[i]}); }
            if ((da > 0 && db < 0) || (da < 0 && db > 0)) {
                float t = da / (da - db);
                pos.add(new Vector3f(a).lerp(b, t));
                uv.add(new float[] {q.u[i] + (q.u[j] - q.u[i]) * t, q.v[i] + (q.v[j] - q.v[i]) * t});
            }
        }
        if (pos.size() < 3) return null;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Vector3f p : pos) { minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); }
        // a face lying in the cut plane goes above only (edge-only contact already left fewer than 3 corners)
        if (below ? minY >= cut : maxY < cut) return null;
        if (pos.size() == n && sameCorners(pos, q.pos)) return q;
        float[] u = new float[pos.size()], v = new float[pos.size()];
        for (int i = 0; i < u.length; i++) { u[i] = uv.get(i)[0]; v[i] = uv.get(i)[1]; }
        return new Quad(pos.toArray(new Vector3f[0]), u, v, q.normal, q.color, q.part, q.draw);
    }

    private static boolean sameCorners(List<Vector3f> a, Vector3f[] b) {
        for (int i = 0; i < b.length; i++) if (a.get(i) != b[i]) return false;
        return true;
    }

    /** True when any texel of the face's texture area is not fully transparent in the sprite. */
    static boolean visible(Quad q, SpriteContents c) {
        float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v0 = Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
        for (int i = 0; i < q.u.length; i++) {
            u0 = Math.min(u0, q.u[i]); u1 = Math.max(u1, q.u[i]);
            v0 = Math.min(v0, q.v[i]); v1 = Math.max(v1, q.v[i]);
        }
        int w = c.width(), h = c.height();
        int x0 = Math.max(0, (int) Math.floor(u0 * w + 1e-3f)), x1 = Math.min(w, (int) Math.ceil(u1 * w - 1e-3f));
        int y0 = Math.max(0, (int) Math.floor(v0 * h + 1e-3f)), y1 = Math.min(h, (int) Math.ceil(v1 * h - 1e-3f));
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if (!c.isTransparent(0, x, y)) return true;
        return false;
    }

    /**
     * Packs faces as one static mesh. sprite != null maps the faces' 0..1 UVs into that atlas sprite (and drops faces that are
     * fully transparent in it). Null when no face is left.
     */
    @Nullable
    static PGeometryData pack(List<Quad> quads, @Nullable TextureAtlasSprite sprite) {
        return sprite == null ? packFaces(quads, null) : packLayers(quads, List.of(sprite));
    }

    /**
     * The same faces once per layer, bottom to top, in one mesh: the GPU draws a mesh's triangles in order, so each layer lands
     * on top of the one below at the same depth (as Minecraft's own draw order does), with no spacing between them. Faces fully
     * transparent in a layer are left out of it. Null when no face is left.
     */
    @Nullable
    static PGeometryData packLayers(List<Quad> quads, List<TextureAtlasSprite> layers) {
        List<Quad> faces = new ArrayList<>();
        List<TextureAtlasSprite> spriteOf = new ArrayList<>();
        for (TextureAtlasSprite sprite : layers) {
            SpriteContents c = sprite.contents();
            for (Quad q : quads) if (visible(q, c)) { faces.add(q); spriteOf.add(sprite); }
        }
        return faces.isEmpty() ? null : packFaces(faces, spriteOf);
    }

    /** faces with their sprite (null list: UVs as recorded), each a triangle fan. */
    private static PGeometryData packFaces(List<Quad> faces, @Nullable List<TextureAtlasSprite> spriteOf) {
        if (faces.isEmpty()) return null;
        int vertices = 0, indices = 0;
        for (Quad q : faces) { vertices += q.pos.length; indices += (q.pos.length - 2) * 3; }
        ByteBuffer vb = ByteBuffer.allocateDirect(vertices * STRIDE).order(ByteOrder.nativeOrder());
        ByteBuffer ib = ByteBuffer.allocateDirect(indices * 4).order(ByteOrder.nativeOrder());
        int base = 0;
        for (int f = 0; f < faces.size(); f++) {
            Quad q = faces.get(f);
            TextureAtlasSprite sprite = spriteOf == null ? null : spriteOf.get(f);
            int n = q.pos.length;
            for (int k = 0; k < n; k++) {
                Vector3f p = q.pos[k];
                vb.putFloat(p.x).putFloat(p.y).putFloat(p.z);
                vb.putFloat(sprite != null ? sprite.getU(q.u[k]) : q.u[k]).putFloat(sprite != null ? sprite.getV(q.v[k]) : q.v[k]);
                vb.put(normalByte(q.normal.x)).put(normalByte(q.normal.y)).put(normalByte(q.normal.z)).put((byte) 0);
            }
            for (int k = 1; k < n - 1; k++) ib.putInt(base).putInt(base + k).putInt(base + k + 1);
            base += n;
        }
        vb.flip();
        ib.flip();
        return new PGeometryData(vb, ib, STRIDE, indices, PGeometryData.IndexType.UNSIGNED_INT);
    }

    private static byte normalByte(float f) {
        return (byte) Math.round(Math.max(-1f, Math.min(1f, f)) * 127f);
    }
}
