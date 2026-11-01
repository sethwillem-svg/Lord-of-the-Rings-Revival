package io.github.sethwillemsvg.lotrrevival.mixin;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Caches HierarchicalModel.getAnyDescendantWithName per model. Keyframe animations look every animated bone up by name each frame,
 * and the lookup streams over the whole part tree; with Middle-earth's warg mounts that was 24% of the render thread in Spark.
 * A model's part tree is built once and never changes, so the answer for a name never changes either.
 */
@Mixin(HierarchicalModel.class)
public abstract class HierarchicalModelMixin {
    @Unique
    private final Map<String, Optional<ModelPart>> lotrRevival$descendants = new HashMap<>();

    @Inject(method = "getAnyDescendantWithName", at = @At("HEAD"), cancellable = true)
    private void lotrRevival$cachedDescendant(String name, CallbackInfoReturnable<Optional<ModelPart>> cir) {
        Optional<ModelPart> cached = lotrRevival$descendants.get(name);
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(method = "getAnyDescendantWithName", at = @At("RETURN"))
    private void lotrRevival$rememberDescendant(String name, CallbackInfoReturnable<Optional<ModelPart>> cir) {
        lotrRevival$descendants.putIfAbsent(name, cir.getReturnValue());
    }
}
