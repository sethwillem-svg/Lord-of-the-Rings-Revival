package io.github.sethwillemsvg.lotrrevival.mixin;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only: lets client.render.PulseGeometry find which texture a render type draws with. */
@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface CompositeRenderTypeAccessor {
    @Invoker("state")
    RenderType.CompositeState lotrRevival$state();
}
