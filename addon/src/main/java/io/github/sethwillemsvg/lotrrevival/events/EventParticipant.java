package io.github.sethwillemsvg.lotrrevival.events;

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

/** Entity attachment {@code lotr_revival:world_event} on NPCs spawned for an event: event id, side (lead/opposition), role. */
public record EventParticipant(UUID eventId, String side, String role) {
    public static final String LEAD = "lead", OPPOSITION = "opposition";
    public static final Codec<EventParticipant> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("event").forGetter(EventParticipant::eventId),
            Codec.STRING.fieldOf("side").forGetter(EventParticipant::side),
            Codec.STRING.fieldOf("role").forGetter(EventParticipant::role)).apply(i, EventParticipant::new));
    public static final EventParticipant NONE = new EventParticipant(new UUID(0, 0), "", "");

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LotrRevival.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EventParticipant>> TYPE = ATTACHMENTS.register("world_event",
            () -> AttachmentType.builder(() -> NONE).serialize(CODEC).build());

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static @Nullable EventParticipant of(Entity e) {
        return e.hasData(TYPE) ? e.getData(TYPE) : null;
    }

    public static String other(String side) {
        return LEAD.equals(side) ? OPPOSITION : LEAD;
    }
}
