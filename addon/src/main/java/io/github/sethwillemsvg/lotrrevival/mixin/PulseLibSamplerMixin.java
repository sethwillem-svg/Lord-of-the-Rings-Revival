package io.github.sethwillemsvg.lotrrevival.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** PulseLib looks up a sampler's uniform location on every bind: route it through GlStateManager, i.e. UniformLocationCache. */
@Mixin(targets = "com.arcanc.pulselib.content.renderer.legacy.GlStreamStorage", remap = false)
public abstract class PulseLibSamplerMixin {
    @Redirect(method = "bindSampler", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20;glGetUniformLocation(ILjava/lang/CharSequence;)I"),
            remap = false)
    private static int lotrRevival$cachedSamplerLocation(int program, CharSequence name) {
        return GlStateManager._glGetUniformLocation(program, name);
    }
}
