package io.github.sethwillemsvg.lotrrevival.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.sevenstars.middleearth.client.ModTexturedRenderLayers;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Baseline for the benchmark: Minecraft's own model path (every vertex of every layer transformed on the CPU each frame),
 * drawing the same skin layers as {@link PulseNpcRenderer}.
 */
public class VanillaNpcRenderer extends LivingEntityRenderer<NpcEntity, NpcBipedModel> {
    public VanillaNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcBipedModel(), 0.5f);
        addLayer(skinLayer(this));
    }

    protected RenderLayer<NpcEntity, NpcBipedModel> skinLayer(RenderLayerParent<NpcEntity, NpcBipedModel> parent) {
        return new RenderLayer<>(parent) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, NpcEntity entity, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                TextureAtlas atlas = NpcSkin.atlas();
                VertexConsumer base = buffers.getBuffer(ModTexturedRenderLayers.getCharacterTexturesRenderLayer());
                int overlay = getOverlayCoords(entity, 0f);
                NpcBipedModel model = getParentModel();
                for (TextureAtlasSprite sprite : NpcSkin.layers(entity, atlas))
                    model.renderToBuffer(poseStack, sprite.wrap(base), light, overlay);
            }
        };
    }

    @Override
    protected void scale(NpcEntity entity, PoseStack poseStack, float partialTick) {
        float w = entity.getWidthScale();
        poseStack.scale(w, 1f, w);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        return NpcSkin.atlas().location();
    }

    /** No base pass: everything is drawn by the skin layer (as the Middle-earth renderer does). */
    @Nullable
    @Override
    protected RenderType getRenderType(NpcEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return null;
    }
}
