package io.github.sethwillemsvg.lotrrevival.mixin;

import io.github.sethwillemsvg.lotrrevival.client.anim.NpcAnimator;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Middle-earth NPC model ends its setupAnim with super.setupAnim, so this TAIL runs after all of its posing. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void lotrRevival$playClips(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                                       float headPitch, CallbackInfo ci) {
        NpcAnimator.apply((HumanoidModel<?>) (Object) this, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
