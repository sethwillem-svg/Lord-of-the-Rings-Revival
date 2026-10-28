package io.github.sethwillemsvg.lotrrevival.client.progression.shield;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * A flat plate, 10 x 16 pixels and 2 thick, centred on its origin. Texture 32x32: the design is the front face
 * at (2,2) and the back face at (14,2).
 */
public final class ShieldModel extends Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(LOTRRevival.id("achievement_shield"), "main");

    private final ModelPart plate;

    public ShieldModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.plate = root.getChild("plate");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot()
                .addOrReplaceChild(
                        "plate", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -8.0F, -1.0F, 10.0F, 16.0F, 2.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        plate.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
