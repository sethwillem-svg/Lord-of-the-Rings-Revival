package io.github.sethwillemsvg.lotrrevival.client.progression.quest.offer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferMarkerPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGroup;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Draws the quest offer marker floating over a giver's head, tinted in the quest group's colour. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
public final class OfferMarkerRenderer {
    private static final ResourceLocation TEXTURE = LOTRRevival.id("textures/gui/quest_offer_marker.png");
    private static final float SIZE = 0.6f;

    private OfferMarkerRenderer() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Map<Integer, QuestOfferMarkerPayload> markers = ClientQuestOffers.markers();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (markers.isEmpty() || level == null || player == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutoutNoCull(TEXTURE);
        VertexConsumer consumer = null;
        float time = level.getGameTime() + partial;

        Iterator<Map.Entry<Integer, QuestOfferMarkerPayload>> it = markers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, QuestOfferMarkerPayload> entry = it.next();
            Entity giver = level.getEntity(entry.getKey());
            if (giver == null || giver.isRemoved()) {
                // Out of tracking range or gone; the server re-sends the marker when the giver is tracked again.
                it.remove();
                continue;
            }
            Optional<QuestGroup> group = OfferMarkerRules.visibleGroup(
                    player, giver, entry.getValue(), level.registryAccess());
            if (group.isEmpty()) {
                continue;
            }
            if (consumer == null) {
                consumer = buffers.getBuffer(type);
            }
            Vec3 pos = giver.getPosition(partial);
            float bob = Mth.sin(time * 0.1f) * 0.06f;
            pose.pushPose();
            pose.translate(
                    pos.x - cam.x, pos.y + giver.getBbHeight() + 0.7 + bob - cam.y, pos.z - cam.z);
            pose.mulPose(camera.rotation());
            quad(consumer, pose.last().pose(), pose.last(), 0xFF000000 | (group.get().color() & 0xFFFFFF));
            pose.popPose();
        }
        if (consumer != null) {
            buffers.endBatch(type);
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, int argb) {
        float h = SIZE / 2.0f;
        vertex(consumer, matrix, pose, -h, 0.0f, 0.0f, 1.0f, argb);
        vertex(consumer, matrix, pose, h, 0.0f, 1.0f, 1.0f, argb);
        vertex(consumer, matrix, pose, h, SIZE, 1.0f, 0.0f, argb);
        vertex(consumer, matrix, pose, -h, SIZE, 0.0f, 0.0f, argb);
    }

    private static void vertex(
            VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, float x, float y, float u, float v, int argb) {
        consumer.addVertex(matrix, x, y, 0.0f)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0f, 0.0f, 1.0f);
    }
}
