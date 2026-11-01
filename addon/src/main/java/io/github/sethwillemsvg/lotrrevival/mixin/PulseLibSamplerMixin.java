package io.github.sethwillemsvg.lotrrevival.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Two driver queries PulseLib makes on every draw group, answered from state Minecraft already tracks (Spark: about 20% of the
 * render thread together): the sampler's uniform location (UniformLocationCache via GlStateManager) and the active texture unit
 * it saves and restores around binding its streams (GlStateManager's tracked value, which Minecraft's own shaders also use).
 */
@Mixin(targets = "com.arcanc.pulselib.content.renderer.legacy.GlStreamStorage", remap = false)
public abstract class PulseLibSamplerMixin {
    @Redirect(method = "bindSampler", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20;glGetUniformLocation(ILjava/lang/CharSequence;)I"),
            remap = false)
    private static int lotrRevival$cachedSamplerLocation(int program, CharSequence name) {
        return GlStateManager._glGetUniformLocation(program, name);
    }

    @Redirect(method = "bind", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetInteger(I)I"), remap = false)
    private static int lotrRevival$trackedActiveTexture(int pname) {
        return pname == GL13.GL_ACTIVE_TEXTURE ? GlStateManager._getActiveTexture() : GL11.glGetInteger(pname);
    }
}
