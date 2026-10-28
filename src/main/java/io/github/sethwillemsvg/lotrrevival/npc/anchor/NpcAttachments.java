package io.github.sethwillemsvg.lotrrevival.npc.anchor;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Attachment types owned by the NPC pod. */
public final class NpcAttachments {
    /** {@code lotrrevival:npc_spawn_anchors}: chunks only, saved with the chunk. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NpcSpawnAnchors>> NPC_SPAWN_ANCHORS =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "npc_spawn_anchors",
                    () -> AttachmentType.builder(() -> NpcSpawnAnchors.EMPTY)
                            .serialize(NpcSpawnAnchors.CODEC)
                            .build());

    private NpcAttachments() {}

    /** Touch from {@code NpcMod.init} so the class loads before registration. */
    public static void init() {}
}
