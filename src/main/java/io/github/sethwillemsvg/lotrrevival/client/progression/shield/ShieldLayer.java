package io.github.sethwillemsvg.lotrrevival.client.progression.shield;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldDefinition;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldDefinitions;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.UseAnim;

/**
 * Draws the selected achievement shield on a player: on the back, on the off-hand arm while the main hand
 * holds a weapon or tool, and in front of the chest while blocking. It sits 1 pixel further out over a
 * chestplate. An invisible player's shield is faint for viewers who can see the outline and absent otherwise.
 */
public final class ShieldLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float PIXEL = 1.0F / 16.0F;
    private static final int FAINT = 0x30FFFFFF;
    private static final int OPAQUE = 0xFFFFFFFF;

    private final ShieldModel model;

    public ShieldLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new ShieldModel(models.bakeLayer(ShieldModel.LAYER));
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        Optional<ResourceLocation> selected = ClientShields.get(player.getUUID());
        Minecraft mc = Minecraft.getInstance();
        if (selected.isEmpty() || mc.level == null) {
            return;
        }
        ShieldDefinition definition =
                ShieldDefinitions.find(mc.level.registryAccess(), selected.get()).orElse(null);
        if (definition == null) {
            return;
        }
        boolean faint = false;
        if (player.isInvisible()) {
            if (mc.player == null || player.isInvisibleTo(mc.player)) {
                return;
            }
            faint = true;
        }

        boolean blocking = player.isBlocking()
                || (player.isUsingItem() && player.getUseItem().getUseAnimation() == UseAnim.BLOCK);
        ShieldPlacement.Spot spot = ShieldPlacement.choose(blocking, player.getMainHandItem());
        float chest = player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() ? 0.0F : 1.0F;

        poseStack.pushPose();
        switch (spot) {
            case BACK -> {
                getParentModel().body.translateAndRotate(poseStack);
                poseStack.translate(0.0F, 6.0F * PIXEL, (2.0F + 1.0F + 0.5F + chest) * PIXEL);
            }
            case FRONT -> {
                getParentModel().body.translateAndRotate(poseStack);
                poseStack.translate(0.0F, 6.0F * PIXEL, -(2.0F + 1.0F + 1.5F + chest) * PIXEL);
            }
            case ARM -> {
                boolean left = player.getMainArm() == HumanoidArm.RIGHT;
                var arm = left ? getParentModel().leftArm : getParentModel().rightArm;
                arm.translateAndRotate(poseStack);
                boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
                float side = left ? 1.0F : -1.0F;
                float outer = slim ? 2.0F : 3.0F;
                poseStack.translate(side * (outer + 1.0F + 0.5F + chest) * PIXEL, 4.0F * PIXEL, 0.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(left ? -90.0F : 90.0F));
            }
        }
        RenderType type = faint
                ? RenderType.entityTranslucent(definition.texture())
                : RenderType.entityCutoutNoCull(definition.texture());
        VertexConsumer consumer = buffer.getBuffer(type);
        model.renderToBuffer(poseStack, consumer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, faint ? FAINT : OPAQUE);
        poseStack.popPose();
    }
}
