package io.github.sethwillemsvg.lotrrevival.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import io.github.sethwillemsvg.lotrrevival.client.perf.UniformLocationCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uniform location lookups answered from UniformLocationCache; a deleted program's entries are dropped. */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    @Inject(method = "_glGetUniformLocation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void lotrRevival$cachedLocation(int program, CharSequence name, CallbackInfoReturnable<Integer> cir) {
        int cached = UniformLocationCache.get(program, name);
        if (UniformLocationCache.known(cached)) cir.setReturnValue(cached);
    }

    @Inject(method = "_glGetUniformLocation", at = @At("RETURN"), remap = false)
    private static void lotrRevival$rememberLocation(int program, CharSequence name, CallbackInfoReturnable<Integer> cir) {
        UniformLocationCache.put(program, name, cir.getReturnValue());
    }

    @Inject(method = "glDeleteProgram", at = @At("HEAD"), remap = false)
    private static void lotrRevival$forgetProgram(int program, CallbackInfo ci) {
        UniformLocationCache.forget(program);
    }
}
