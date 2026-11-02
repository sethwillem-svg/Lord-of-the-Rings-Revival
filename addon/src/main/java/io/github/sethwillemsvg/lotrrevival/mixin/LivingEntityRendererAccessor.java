package io.github.sethwillemsvg.lotrrevival.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** A living entity renderer's layers (the viewer export draws Middle-earth's NPC layers one by one). */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
    @Accessor("layers")
    List<RenderLayer<?, ?>> lotrRevival$layers();
}
