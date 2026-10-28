package io.github.sethwillemsvg.lotrrevival.core.combat.ranged;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Entity types of the ranged weapons: the crossbow bolt. */
public final class BoltEntities {
    private BoltEntities() {}

    /** {@code lotrrevival:bolt}: same size and tracking as a vanilla arrow. */
    public static final DeferredHolder<EntityType<?>, EntityType<BoltEntity>> BOLT = ModRegistries.ENTITY_TYPES.register(
            "bolt",
            () -> EntityType.Builder.<BoltEntity>of(BoltEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .eyeHeight(0.13F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("bolt"));

    /** Loads the class so the entity type registers. Called from {@code CrossbowGear.init}. */
    public static void init() {}
}
