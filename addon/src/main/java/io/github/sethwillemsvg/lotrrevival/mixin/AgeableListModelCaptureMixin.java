package io.github.sethwillemsvg.lotrrevival.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sethwillemsvg.lotrrevival.client.render.PartCapture;
import net.minecraft.client.model.AgeableListModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells PartCapture which humanoid model is drawing while armour is being recorded for PulseLib (does nothing otherwise). */
@Mixin(AgeableListModel.class)
public abstract class AgeableListModelCaptureMixin {
    @Inject(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"))
    private void lotrRevival$modelStart(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        PartCapture.modelStart(this);
    }

    @Inject(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("RETURN"))
    private void lotrRevival$modelEnd(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        PartCapture.modelEnd();
    }
}
