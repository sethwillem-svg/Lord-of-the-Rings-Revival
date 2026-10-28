package io.github.sethwillemsvg.lotrrevival.progression;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Attachment types owned by the Progression pod. */
public final class ProgressionAttachments {
    /** {@code lotrrevival:progress_counters}: players only, saved, copied on death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ProgressCounters>> PROGRESS_COUNTERS =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "progress_counters",
                    () -> AttachmentType.builder(() -> ProgressCounters.EMPTY)
                            .serialize(ProgressCounters.CODEC)
                            .copyOnDeath()
                            .build());

    /** {@code lotrrevival:titles}: players only, saved, copied on death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TitleData>> TITLES =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "titles",
                    () -> AttachmentType.builder(() -> TitleData.EMPTY)
                            .serialize(TitleData.CODEC)
                            .copyOnDeath()
                            .build());

    private ProgressionAttachments() {}

    /** Touch from {@link ProgressionMod#init} so the class loads before registration. */
    public static void init() {}
}
