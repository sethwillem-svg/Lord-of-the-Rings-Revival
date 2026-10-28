package io.github.sethwillemsvg.lotrrevival.npc.entity;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** NPC entity types, registered through core's entity type register. */
public final class NpcEntityTypes {
    private NpcEntityTypes() {}

    public static final DeferredHolder<EntityType<?>, EntityType<HobbitEntity>> HOBBIT =
            ModRegistries.ENTITY_TYPES.register("hobbit", () -> npcType("hobbit", HobbitEntity::new, NpcRace.HOBBIT));

    private static <T extends NpcEntity> EntityType<T> npcType(
            String id, EntityType.EntityFactory<T> factory, NpcRace race) {
        // MISC keeps NPCs out of the vanilla monster and creature caps; the npc spawner handles wild spawns.
        return EntityType.Builder.of(factory, MobCategory.MISC)
                .sized(race.width(), race.height())
                .eyeHeight(race.eyeHeight())
                .clientTrackingRange(8)
                .build(id);
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(NpcEntityTypes::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(HOBBIT.get(), NpcEntity.createAttributes(NpcRace.HOBBIT).build());
    }
}
