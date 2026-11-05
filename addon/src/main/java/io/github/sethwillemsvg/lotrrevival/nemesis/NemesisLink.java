package io.github.sethwillemsvg.lotrrevival.nemesis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Entity attachment {@code lotr_revival:nemesis_link} on a nemesis body: which record it is and which manifestation. Also
 * holds the nemesis package's other attachments ({@code sergeant}, {@code nemesis_player}).
 */
public record NemesisLink(UUID id, int manifestation) {
    public static final Codec<NemesisLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(NemesisLink::id),
            Codec.INT.fieldOf("manifestation").forGetter(NemesisLink::manifestation)).apply(i, NemesisLink::new));
    public static final NemesisLink NONE = new NemesisLink(new UUID(0, 0), -1);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LotrRevival.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NemesisLink>> TYPE = ATTACHMENTS.register("nemesis_link",
            () -> AttachmentType.builder(() -> NONE).serialize(CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SergeantData>> SERGEANT = ATTACHMENTS.register("sergeant",
            () -> AttachmentType.builder(() -> SergeantData.NONE).serialize(SergeantData.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NemesisPlayerData>> PLAYER = ATTACHMENTS.register("nemesis_player",
            () -> AttachmentType.builder(() -> NemesisPlayerData.EMPTY).serialize(NemesisPlayerData.CODEC).copyOnDeath().build());

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static @Nullable NemesisLink of(Entity e) {
        return e.hasData(TYPE) ? e.getData(TYPE) : null;
    }

    public static void set(Entity e, NemesisLink link) {
        e.setData(TYPE, link);
    }
}
