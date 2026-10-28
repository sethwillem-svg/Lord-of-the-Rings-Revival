package io.github.sethwillemsvg.lotrrevival.core.combat.throwing;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Entity type of the sling pebble. */
public final class PebbleTypes {
    private PebbleTypes() {}

    /** {@code lotrrevival:pebble}: a small thrown projectile. */
    public static final DeferredHolder<EntityType<?>, EntityType<PebbleProjectile>> PEBBLE =
            ModRegistries.ENTITY_TYPES.register(
                    "pebble",
                    () -> EntityType.Builder.<PebbleProjectile>of(PebbleProjectile::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("pebble"));

    /** Touch from init so the class loads before registration. */
    public static void init() {}
}
