package io.github.sethwillemsvg.lotrrevival.world.portal;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Registry holders of the ring portal: its entity type and the level attachment. */
public final class RingPortalTypes {
    /** {@code lotrrevival:ring_portal} entity: not summonable, fire-immune, kept loaded near players. */
    public static final DeferredHolder<EntityType<?>, EntityType<RingPortalEntity>> RING_PORTAL =
            ModRegistries.ENTITY_TYPES.register(
                    "ring_portal",
                    () -> EntityType.Builder.<RingPortalEntity>of(RingPortalEntity::new, MobCategory.MISC)
                            .sized(RingPortalEntity.SIZE, RingPortalEntity.SIZE)
                            .fireImmune()
                            .noSummon()
                            .clientTrackingRange(10)
                            .updateInterval(20)
                            .build("ring_portal"));

    /** {@code lotrrevival:ring_portal} attachment on the overworld. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RingPortalData>> DATA =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "ring_portal",
                    () -> AttachmentType.builder(() -> RingPortalData.EMPTY)
                            .serialize(RingPortalData.CODEC)
                            .build());

    private RingPortalTypes() {}

    /** Touch from init so the class loads before registration. */
    public static void init() {}
}
