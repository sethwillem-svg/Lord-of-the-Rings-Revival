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

    /** One recorded face: 4 corners, their UVs, the face normal and the colour Minecraft gave it. While PartCapture is active,
     *  part is the body part that drew it (corners and normal in that part's own space) and draw the model draw it came from. */
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

    /** True when any texel of the face's texture area is not fully transparent in the sprite. */
    static boolean visible(Quad q, SpriteContents c) {
        float u0 = Math.min(Math.min(q.u[0], q.u[1]), Math.min(q.u[2], q.u[3])), u1 = Math.max(Math.max(q.u[0], q.u[1]), Math.max(q.u[2], q.u[3]));
        float v0 = Math.min(Math.min(q.v[0], q.v[1]), Math.min(q.v[2], q.v[3])), v1 = Math.max(Math.max(q.v[0], q.v[1]), Math.max(q.v[2], q.v[3]));
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
        List<Quad> faces = quads;
        if (sprite != null) {
            SpriteContents c = sprite.contents();
            faces = new ArrayList<>();
            for (Quad q : quads) if (visible(q, c)) faces.add(q);
        }
        if (faces.isEmpty()) return null;
        ByteBuffer vb = ByteBuffer.allocateDirect(faces.size() * 4 * STRIDE).order(ByteOrder.nativeOrder());
        ByteBuffer ib = ByteBuffer.allocateDirect(faces.size() * 6 * 4).order(ByteOrder.nativeOrder());
        int base = 0;
        for (Quad q : faces) {
            for (int k = 0; k < 4; k++) {
                Vector3f p = q.pos[k];
                vb.putFloat(p.x).putFloat(p.y).putFloat(p.z);
                vb.putFloat(sprite != null ? sprite.getU(q.u[k]) : q.u[k]).putFloat(sprite != null ? sprite.getV(q.v[k]) : q.v[k]);
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
