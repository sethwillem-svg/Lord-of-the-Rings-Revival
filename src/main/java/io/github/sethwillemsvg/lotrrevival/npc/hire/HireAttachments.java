package io.github.sethwillemsvg.lotrrevival.npc.hire;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Attachment types of the hiring system. */
public final class HireAttachments {
    /** {@code lotrrevival:hired_unit}: serialized with the entity, not synced. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<HiredUnit>> HIRED_UNIT =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "hired_unit",
                    () -> AttachmentType.builder(() -> HiredUnit.NONE)
                            .serialize(HiredUnit.CODEC)
                            .build());

    /** {@code lotrrevival:unit_runtime}: transient AI memory of a hired unit (never saved, never synced). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitRuntime>>
            UNIT_RUNTIME = ModRegistries.ATTACHMENT_TYPES.register(
                    "unit_runtime",
                    () -> AttachmentType.builder(io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitRuntime::new)
                            .build());

    private HireAttachments() {}

    /** Touch from {@code NpcMod.init} so the class loads before registration. */
    public static void init() {}
}
