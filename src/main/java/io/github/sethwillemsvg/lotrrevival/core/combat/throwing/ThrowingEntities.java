package io.github.sethwillemsvg.lotrrevival.core.combat.throwing;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Entity types of the thrown weapons ({@code lotrrevival:thrown_spear}, {@code lotrrevival:thrown_axe}). */
public final class ThrowingEntities {
    private ThrowingEntities() {}

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownSpear>> THROWN_SPEAR =
            ModRegistries.ENTITY_TYPES.register(
                    "thrown_spear",
                    () -> EntityType.Builder.<ThrownSpear>of(ThrownSpear::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("thrown_spear"));

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownAxe>> THROWN_AXE =
            ModRegistries.ENTITY_TYPES.register(
                    "thrown_axe",
                    () -> EntityType.Builder.<ThrownAxe>of(ThrownAxe::new, MobCategory.MISC)
                            .sized(0.4f, 0.4f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("thrown_axe"));

    /** Touch from init so the class loads before registration. */
    public static void init() {}
}
