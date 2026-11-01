package io.github.sethwillemsvg.lotrrevival.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.sevenstars.middleearth.client.ModTexturedRenderLayers;
import net.sevenstars.middleearth.config.ModClientConfigs;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.renderer.NpcEntityRenderer;
import org.jetbrains.annotations.Nullable;

/**
 * Baseline for the benchmark: Minecraft's own model path (every vertex of every layer transformed on the CPU each frame),
 * drawing the same skin layers as {@link PulseNpcRenderer}, plus armour and held items through Minecraft's standard layers
 * (shared with PulseNpcRenderer, which only moves the skin onto PulseLib). A MobRenderer like Middle-earth's, so names only show when looked at.
 */
public class VanillaNpcRenderer extends MobRenderer<NpcEntity, NpcBipedModel> {
    public VanillaNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcBipedModel(), 0.5f);
        addLayer(skinLayer(this));
        // armour and held items: Minecraft's own layers, as Middle-earth uses them (same distance cut-off)
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, NpcEntity entity, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (showEquipment(entity))
                    super.render(poseStack, buffers, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, NpcEntity entity, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (showEquipment(entity))
                    super.render(poseStack, buffers, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
    }

    /** Middle-earth's rule: equipment shows on frozen (no-AI) NPCs and within its configured distance. */
    static boolean showEquipment(NpcEntity entity) {
        return entity.isNoAi() || NpcEntityRenderer.getLOD(entity.position()) < ModClientConfigs.LOD_NPC_ARMOR_DISTANCE;
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
                NpcSkin.Layers layers = NpcSkin.layers(entity, atlas);
                for (int i = 0; i < layers.sprites().size(); i++)
                    if (layers.drawn(i)) model.renderToBuffer(poseStack, layers.sprites().get(i).wrap(base), light, overlay);
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
