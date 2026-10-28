package io.github.sethwillemsvg.lotrrevival.client.progression.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerDesign;
import io.github.sethwillemsvg.lotrrevival.progression.banner.FactionBannerBlockEntity;
import io.github.sethwillemsvg.lotrrevival.progression.banner.FactionWallBannerBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws a faction banner: a pole three blocks tall with a cross bar and a cloth hanging in front of and behind it
 * (standing form), or a short cloth flat against the wall (wall form). The cloth and emblem textures are grey and
 * are tinted with the design colours.
 */
public class FactionBannerRenderer implements BlockEntityRenderer<FactionBannerBlockEntity> {
    private static final ResourceLocation POLE = ResourceLocation.withDefaultNamespace("textures/block/dark_oak_planks.png");
    private static final float HALF_POLE = 1.0F / 16.0F;
    private static final int WHITE = 0xFFFFFFFF;

    public FactionBannerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
            FactionBannerBlockEntity banner,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        BlockState state = banner.getBlockState();
        BannerDesign design = banner.getLevel() == null
                ? null
                : banner.getLevel()
                        .registryAccess()
                        .registry(BannerDesign.REGISTRY)
                        .map(registry -> registry.get(banner.design()))
                        .orElse(null);
        int cloth = design == null ? WHITE : 0xFF000000 | design.color();
        int emblem = design == null ? WHITE : 0xFF000000 | design.emblemColor();
        ResourceLocation clothTexture = design == null ? BannerDesign.DEFAULT_TEXTURE : design.clothTexture();

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        boolean wall = state.getBlock() instanceof FactionWallBannerBlock;
        if (wall) {
            Direction facing = state.getValue(FactionWallBannerBlock.FACING);
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        }

        if (wall) {
            // Cloth flat against the wall, a little off it.
            float z = -0.5F + 0.04F;
            VertexConsumer clothBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(clothTexture));
            quad(poseStack, clothBuffer, -0.4375F, 0.05F, 0.4375F, 0.95F, z, 1, cloth, light, overlay);
            VertexConsumer emblemBuffer =
                    buffers.getBuffer(RenderType.entityCutoutNoCull(BannerDesign.EMBLEM_TEXTURE));
            quad(poseStack, emblemBuffer, -0.4375F, 0.05F, 0.4375F, 0.95F, z + 0.002F, 1, emblem, light, overlay);
            VertexConsumer bar = buffers.getBuffer(RenderType.entitySolid(POLE));
            box(poseStack, bar, -0.47F, 0.95F, -0.5F + 0.02F, 0.47F, 1.0F, -0.5F + 0.06F, WHITE, light, overlay);
        } else {
            VertexConsumer pole = buffers.getBuffer(RenderType.entitySolid(POLE));
            box(poseStack, pole, -HALF_POLE, 0.0F, -HALF_POLE, HALF_POLE, 3.0F, HALF_POLE, WHITE, light, overlay);
            box(poseStack, pole, -0.47F, 2.9F, -HALF_POLE, 0.47F, 2.97F, HALF_POLE, WHITE, light, overlay);
            VertexConsumer clothBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(clothTexture));
            VertexConsumer emblemBuffer =
                    buffers.getBuffer(RenderType.entityCutoutNoCull(BannerDesign.EMBLEM_TEXTURE));
            float front = HALF_POLE + 0.004F;
            // Front (faces +z) and back (faces -z), both drawn so the banner reads from either side.
            quad(poseStack, clothBuffer, -0.4375F, 1.15F, 0.4375F, 2.9F, front, 1, cloth, light, overlay);
            quad(poseStack, emblemBuffer, -0.4375F, 1.15F, 0.4375F, 2.9F, front + 0.002F, 1, emblem, light, overlay);
            quad(poseStack, clothBuffer, -0.4375F, 1.15F, 0.4375F, 2.9F, -front, -1, cloth, light, overlay);
            quad(poseStack, emblemBuffer, -0.4375F, 1.15F, 0.4375F, 2.9F, -front - 0.002F, -1, emblem, light, overlay);
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(FactionBannerBlockEntity banner) {
        return new AABB(banner.getBlockPos()).expandTowards(0.0, 2.0, 0.0);
    }

    /** A vertical quad in the plane z = {@code z}; {@code dir} 1 faces +z, -1 faces -z. */
    private static void quad(
            PoseStack poseStack,
            VertexConsumer buffer,
            float x0,
            float y0,
            float x1,
            float y1,
            float z,
            int dir,
            int color,
            int light,
            int overlay) {
        PoseStack.Pose pose = poseStack.last();
        // Viewed from +z, x grows to the right; the back face mirrors x so the texture reads correctly.
        float left = dir > 0 ? x0 : x1;
        float right = dir > 0 ? x1 : x0;
        vertex(pose, buffer, left, y1, z, 0.0F, 0.0F, dir, color, light, overlay);
        vertex(pose, buffer, left, y0, z, 0.0F, 1.0F, dir, color, light, overlay);
        vertex(pose, buffer, right, y0, z, 1.0F, 1.0F, dir, color, light, overlay);
        vertex(pose, buffer, right, y1, z, 1.0F, 0.0F, dir, color, light, overlay);
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x,
            float y,
            float z,
            float u,
            float v,
            int normalZ,
            int color,
            int light,
            int overlay) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, normalZ);
    }

    /** An axis-aligned box using the whole pole texture on every face. */
    private static void box(
            PoseStack poseStack,
            VertexConsumer buffer,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int color,
            int light,
            int overlay) {
        PoseStack.Pose pose = poseStack.last();
        // +z / -z
        face(pose, buffer, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1, color, light, overlay);
        face(pose, buffer, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1, color, light, overlay);
        // +x / -x
        face(pose, buffer, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0, color, light, overlay);
        face(pose, buffer, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0, color, light, overlay);
        // +y / -y
        face(pose, buffer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, color, light, overlay);
        face(pose, buffer, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, color, light, overlay);
    }

    private static void face(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float cx,
            float cy,
            float cz,
            float dx,
            float dy,
            float dz,
            float nx,
            float ny,
            float nz,
            int color,
            int light,
            int overlay) {
        corner(pose, buffer, ax, ay, az, 0.0F, 0.0F, nx, ny, nz, color, light, overlay);
        corner(pose, buffer, bx, by, bz, 0.0F, 1.0F, nx, ny, nz, color, light, overlay);
        corner(pose, buffer, cx, cy, cz, 1.0F, 1.0F, nx, ny, nz, color, light, overlay);
        corner(pose, buffer, dx, dy, dz, 1.0F, 0.0F, nx, ny, nz, color, light, overlay);
    }

    private static void corner(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x,
            float y,
            float z,
            float u,
            float v,
            float nx,
            float ny,
            float nz,
            int color,
            int light,
            int overlay) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
