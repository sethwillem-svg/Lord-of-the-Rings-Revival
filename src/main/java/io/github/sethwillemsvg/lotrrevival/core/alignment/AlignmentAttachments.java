package io.github.sethwillemsvg.lotrrevival.core.alignment;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Attachment types owned by the alignment system. */
public final class AlignmentAttachments {
    /** {@code lotrrevival:alignment}: players only, saved, copied on death and End exit. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AlignmentData>> ALIGNMENT =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "alignment",
                    () -> AttachmentType.builder(AlignmentData::new)
                            .serialize(AlignmentData.CODEC)
                            .copyOnDeath()
                            .build());

    private AlignmentAttachments() {}

    /** Touch from {@code CoreMod.init} so the class loads before registration. */
    public static void init() {}
}
