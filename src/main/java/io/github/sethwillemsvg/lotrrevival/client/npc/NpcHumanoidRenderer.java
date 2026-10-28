package io.github.sethwillemsvg.lotrrevival.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer for humanoid NPCs: vanilla's humanoid model uniformly scaled to the race's height, with a flat-colour
 * placeholder texture (clearly placeholder; final art is made new later).
 */
public class NpcHumanoidRenderer<T extends NpcEntity> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    /** Height of the unscaled humanoid model in blocks. */
    private static final float MODEL_HEIGHT = 1.8f;

    private final ResourceLocation texture;

    public NpcHumanoidRenderer(EntityRendererProvider.Context context, String textureName) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.35f);
        this.texture = LOTRRevival.id("textures/entity/" + textureName + ".png");
        addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        float scale = entity.race().height() / MODEL_HEIGHT;
        poseStack.scale(scale, scale, scale);
    }

    /** Not always on: only while the local player's crosshair is on the NPC within 8 blocks. */
    @Override
    protected boolean shouldShowName(T entity) {
        return NpcNameplates.shouldShow(entity);
    }

    @Override
    protected void renderNameTag(
            T entity,
            Component displayName,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        int color = NpcNameplates.color(entity);
        super.renderNameTag(
                entity,
                displayName.copy().withStyle(style -> style.withColor(color)),
                poseStack,
                buffer,
                packedLight,
                partialTick);
    }
}
