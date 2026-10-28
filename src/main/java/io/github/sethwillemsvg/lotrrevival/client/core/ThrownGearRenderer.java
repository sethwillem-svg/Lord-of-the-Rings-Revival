package io.github.sethwillemsvg.lotrrevival.client.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrownGear;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws a thrown spear or axe as its item. Spears point along their flight; axes spin end over end while airborne.
 */
public class ThrownGearRenderer extends EntityRenderer<ThrownGear> {
    private final boolean spin;
    private final ItemRenderer itemRenderer;

    public ThrownGearRenderer(EntityRendererProvider.Context context, boolean spin) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.spin = spin;
    }

    @Override
    public void render(
            ThrownGear entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light) {
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() * 0.5, 0.0);
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0f));
        if (spin) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-(entity.tickCount + partialTick) * 45.0f));
        } else {
            // The item sprite points up and to the right; lay it along the flight direction.
            poseStack.mulPose(Axis.ZP.rotationDegrees(pitch - 45.0f));
        }
        poseStack.scale(spin ? 0.9f : 1.3f, spin ? 0.9f : 1.3f, spin ? 0.9f : 1.3f);
        itemRenderer.renderStatic(
                entity.getItem(),
                ItemDisplayContext.GROUND,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffers,
                entity.level(),
                entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownGear entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
