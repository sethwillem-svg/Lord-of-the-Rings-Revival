package io.github.sethwillemsvg.lotrrevival.nodemap.state;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Player attachment {@code lotr_revival:nodemap}: serialized by codec, copied on death. */
public final class NodeMapAttachment {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LotrRevival.MOD_ID);

    public static final Supplier<AttachmentType<NodeMapData>> NODEMAP = TYPES.register("nodemap",
            () -> AttachmentType.builder(NodeMapData::new).serialize(NodeMapData.CODEC).copyOnDeath().build());

    private NodeMapAttachment() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static NodeMapData get(Player player) {
        return player.getData(NODEMAP);
    }
}
