package io.github.sethwillemsvg.lotrrevival.client.render;

import com.arcanc.pulselib.content.renderer.PRenderQueue;
import com.arcanc.pulselib.content.renderer.plan.PGeometryData;
import com.arcanc.pulselib.content.renderer.plan.PInstanceHeader;
import com.arcanc.pulselib.util.PRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Middle-earth NPCs drawn through PulseLib's render backend: each body part of each skin layer is a static mesh kept on the GPU
 * (PulseLib's geometry arena), and every NPC only sends one transform per part. Identical (part, sprite) meshes from all NPCs
 * are drawn as one instanced batch; all layers share Middle-earth's character atlas, so they share one render type.
 * Posing is the same as the other renderers (NpcBipedModel + our clips); only the drawing differs.
 */
public class PulseNpcRenderer extends VanillaNpcRenderer {
    /** POSITION_TEX_NORMAL: 3 floats, 2 floats, 3 signed bytes + 1 padding. */
    private static final int STRIDE = 24;

    private final Map<TextureAtlasSprite, PGeometryData[]> meshes = new WeakHashMap<>();
    private final List<List<Quad>> partQuads;

    public PulseNpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        partQuads = bakeQuads(new NpcBipedModel());
    }

    @Override
    protected RenderLayer<NpcEntity, NpcBipedModel> skinLayer(RenderLayerParent<NpcEntity, NpcBipedModel> parent) {
        return new RenderLayer<>(parent) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, NpcEntity entity, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                TextureAtlas atlas = NpcSkin.atlas();
                List<TextureAtlasSprite> layers = NpcSkin.layers(entity, atlas);
                if (layers.isEmpty()) return;
                RenderType type = PRenderTypes.RenderTypeProvider.trianglesCutout(atlas.location());
                int overlay = getOverlayCoords(entity, 0f);
                ModelPart[] parts = getParentModel().parts;
                for (int i = 0; i < parts.length; i++) {
                    ModelPart part = parts[i];
                    if (!part.visible) continue;
                    poseStack.pushPose();
                    part.translateAndRotate(poseStack);
                    Matrix4f m = poseStack.last().pose();
                    for (TextureAtlasSprite sprite : layers)
                        PRenderQueue.submitEntityMesh(type, mesh(sprite, i), new PInstanceHeader(m, 0xFFFFFFFF, light, overlay));
                    poseStack.popPose();
                }
            }
        };
    }

    private PGeometryData mesh(TextureAtlasSprite sprite, int part) {
        PGeometryData[] row = meshes.computeIfAbsent(sprite, s -> new PGeometryData[partQuads.size()]);
        if (row[part] == null) row[part] = pack(partQuads.get(part), sprite);
        return row[part];
    }

    // ---- geometry ----
    private record Quad(Vector3f[] pos, float[] u, float[] v, Vector3f normal) {}

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
            out.add(rec.quads);
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
            quads.add(new Quad(pos.clone(), u.clone(), v.clone(), new Vector3f(normal).normalize()));
            n = -1;
        }
    }

    private static PGeometryData pack(List<Quad> quads, TextureAtlasSprite sprite) {
        ByteBuffer vb = ByteBuffer.allocateDirect(quads.size() * 4 * STRIDE).order(ByteOrder.nativeOrder());
        ByteBuffer ib = ByteBuffer.allocateDirect(quads.size() * 6 * 4).order(ByteOrder.nativeOrder());
        int base = 0;
        for (Quad q : quads) {
            for (int k = 0; k < 4; k++) {
                vb.putFloat(q.pos[k].x).putFloat(q.pos[k].y).putFloat(q.pos[k].z);
                vb.putFloat(sprite.getU(q.u[k])).putFloat(sprite.getV(q.v[k]));
                vb.put(normalByte(q.normal.x)).put(normalByte(q.normal.y)).put(normalByte(q.normal.z)).put((byte) 0);
            }
            ib.putInt(base).putInt(base + 1).putInt(base + 2).putInt(base).putInt(base + 2).putInt(base + 3);
            base += 4;
        }
        vb.flip();
        ib.flip();
        return new PGeometryData(vb, ib, STRIDE, quads.size() * 6, PGeometryData.IndexType.UNSIGNED_INT);
    }

    private static byte normalByte(float f) {
        return (byte) Math.round(Math.max(-1f, Math.min(1f, f)) * 127f);
    }
}
