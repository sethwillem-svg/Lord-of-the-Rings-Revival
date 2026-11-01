package io.github.sethwillemsvg.lotrrevival.mixin;

import io.github.sethwillemsvg.lotrrevival.client.anim.NpcAnimator;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Middle-earth NPCs play our death clip, so the vanilla sideways tip-over on death is turned off for them. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
    @Inject(method = "getFlipDegrees", at = @At("HEAD"), cancellable = true)
    private void lotrRevival$noDeathFlip(T entity, CallbackInfoReturnable<Float> cir) {
        if (NpcAnimator.isNpc(entity)) cir.setReturnValue(0f);
    }
}
