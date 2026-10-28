package io.github.sethwillemsvg.lotrrevival.client.world.portal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.world.portal.RingPortalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws a ring portal as three glowing golden rings that turn at different speeds, facing the camera. The rings
 * scale with the portal's growth. Placeholder art; a later artist ticket can replace it.
 */
public class RingPortalRenderer extends EntityRenderer<RingPortalEntity> {
    private static final ResourceLocation TEXTURE = LOTRRevival.id("textures/entity/ring_portal.png");
    private static final int SEGMENTS = 32;
    private static final int FULL_BRIGHT = 0xF000F0;

    public RingPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            RingPortalEntity entity,
            float yaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light) {
        float fraction = entity.growthFraction(partialTick);
        if (fraction <= 0.0f) {
            return;
        }
        float time = entity.tickCount + partialTick;
        float half = RingPortalEntity.SIZE * 0.5f;
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(TEXTURE));

        poseStack.pushPose();
        poseStack.translate(0.0, half, 0.0);
        // Face the camera around the vertical axis.
        var camera = this.entityRenderDispatcher.camera;
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camera.getYRot()));
        float scale = fraction * (0.92f + 0.04f * (float) Math.sin(time * 0.15f));
        for (int i = 0; i < 3; i++) {
            poseStack.pushPose();
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(time * (2.0f + 3.0f * i) * (i % 2 == 0 ? 1 : -1)));
            float outer = half * scale * (1.0f - 0.16f * i);
            float width = 0.14f * (1.0f - 0.2f * i);
            drawRing(consumer, poseStack.last().pose(), poseStack.last(), outer, outer - width);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void drawRing(
            VertexConsumer consumer, Matrix4f pose, PoseStack.Pose normals, float outer, float inner) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            // Both windings so the ring shows from either side.
            quad(consumer, pose, normals, outer * c0, outer * s0, outer * c1, outer * s1, inner * c1, inner * s1, inner * c0, inner * s0);
            quad(consumer, pose, normals, outer * c0, outer * s0, inner * c0, inner * s0, inner * c1, inner * s1, outer * c1, outer * s1);
        }
    }

    private static void quad(
            VertexConsumer consumer,
            Matrix4f pose,
            PoseStack.Pose normals,
            float x0,
            float y0,
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3) {
        vertex(consumer, pose, normals, x0, y0, 0.0f, 0.0f);
        vertex(consumer, pose, normals, x1, y1, 1.0f, 0.0f);
        vertex(consumer, pose, normals, x2, y2, 1.0f, 1.0f);
        vertex(consumer, pose, normals, x3, y3, 0.0f, 1.0f);
    }

    private static void vertex(
            VertexConsumer consumer, Matrix4f pose, PoseStack.Pose normals, float x, float y, float u, float v) {
        consumer.addVertex(pose, x, y, 0.0f)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(normals, 0.0f, 0.0f, 1.0f);
    }

    @Override
    public ResourceLocation getTextureLocation(RingPortalEntity entity) {
        return TEXTURE;
    }
}
