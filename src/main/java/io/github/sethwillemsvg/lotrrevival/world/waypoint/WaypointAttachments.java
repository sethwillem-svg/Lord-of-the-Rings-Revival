package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Attachment types owned by the waypoint system. */
public final class WaypointAttachments {
    /** {@code lotrrevival:travel}: players only, saved, copied on death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TravelData>> TRAVEL =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "travel",
                    () -> AttachmentType.builder(() -> TravelData.EMPTY)
                            .serialize(TravelData.CODEC)
                            .copyOnDeath()
                            .build());

    private WaypointAttachments() {}

    /** Touch from init so the class loads before registration. */
    public static void init() {}
}
