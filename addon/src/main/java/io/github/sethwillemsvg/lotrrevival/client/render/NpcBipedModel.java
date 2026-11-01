package io.github.sethwillemsvg.lotrrevival.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.world.InteractionHand;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.renderer.NpcEntityModel;
import net.sevenstars.middleearth.entity.npcs.renderer.NpcEntityRenderer;

/**
 * The biped our renderers draw: Middle-earth's NPC mesh layout (64x64 player layout with jacket, sleeves and pants), so their
 * skin textures map exactly. NpcAnimator re-poses it with our clips at the end of setupAnim, as it does for their model.
 */
public final class NpcBipedModel extends HumanoidModel<NpcEntity> {
    /** The parts drawn, each with its child overlay parts. hat copies head. */
    public final ModelPart[] parts;

    public NpcBipedModel() {
        this(NpcEntityModel.getTexturedModelData(CubeDeformation.NONE).bakeRoot());
    }

    private NpcBipedModel(ModelPart root) {
        super(root);
        this.parts = new ModelPart[] {head, hat, body, rightArm, leftArm, rightLeg, leftLeg};
    }

    @Override
    public void setupAnim(NpcEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        leftArmPose = NpcEntityRenderer.getArmPose(entity, entity.getOffhandItem(), InteractionHand.OFF_HAND);
        rightArmPose = NpcEntityRenderer.getArmPose(entity, entity.getMainHandItem(), InteractionHand.MAIN_HAND);
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
