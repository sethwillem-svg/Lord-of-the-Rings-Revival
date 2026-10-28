package io.github.sethwillemsvg.lotrrevival.npc.entity;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** NPC entity types, registered through core's entity type register. */
public final class NpcEntityTypes {
    private NpcEntityTypes() {}

    /** Knockback resistance of guards and captains. */
    private static final double GUARD_KNOCKBACK_RESISTANCE = 0.1;

    public static final DeferredHolder<EntityType<?>, EntityType<HobbitEntity>> HOBBIT =
            ModRegistries.ENTITY_TYPES.register("hobbit", () -> npcType("hobbit", HobbitEntity::new, NpcRace.HOBBIT));

    public static final DeferredHolder<EntityType<?>, EntityType<StandardNpcEntity>> HOBBIT_BOUNDER = standard(
            "hobbit_bounder", NpcRace.HOBBIT, Factions.HOBBIT, NpcClass.GUARD, Set.of(), 2, 4);

    public static final DeferredHolder<EntityType<?>, EntityType<StandardNpcEntity>> HOBBIT_SHIRRIFF = standard(
            "hobbit_shirriff", NpcRace.HOBBIT, Factions.HOBBIT, NpcClass.CAPTAIN, Set.of(), 3, 5);

    /** Bree hobbits never roll traveller; innkeeper is only ever assigned by structures. */
    public static final DeferredHolder<EntityType<?>, EntityType<StandardNpcEntity>> BREE_HOBBIT = standard(
            "bree_hobbit", NpcRace.HOBBIT, Factions.BREE, NpcClass.CIVILIAN, Set.of(NpcRole.TRAVELLER), 1, 3);

    public static final DeferredHolder<EntityType<?>, EntityType<StandardNpcEntity>> BREE_MAN = standard(
            "bree_man", NpcRace.MAN, Factions.BREE, NpcClass.CIVILIAN, Set.of(), 1, 3);

    public static final DeferredHolder<EntityType<?>, EntityType<StandardNpcEntity>> BREE_GUARD = standard(
            "bree_guard", NpcRace.MAN, Factions.BREE, NpcClass.GUARD, Set.of(), 3, 5);

    /** Every type built on {@link StandardNpcEntity}, in registration order. */
    public static List<DeferredHolder<EntityType<?>, EntityType<StandardNpcEntity>>> standardTypes() {
        return List.of(HOBBIT_BOUNDER, HOBBIT_SHIRRIFF, BREE_HOBBIT, BREE_MAN, BREE_GUARD);
    }

    private static DeferredHolder<EntityType<?>, EntityType<StandardNpcEntity>> standard(
            String id,
            NpcRace race,
            ResourceKey<Faction> faction,
            NpcClass npcClass,
            Set<NpcRole> excludedRoles,
            int xpMin,
            int xpMax) {
        return ModRegistries.ENTITY_TYPES.register(
                id,
                () -> npcType(
                        id,
                        (type, level) ->
                                new StandardNpcEntity(type, level, race, faction, npcClass, excludedRoles, xpMin, xpMax),
                        race));
    }

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
        event.put(HOBBIT_BOUNDER.get(), guardAttributes(NpcRace.HOBBIT, 20.0));
        event.put(HOBBIT_SHIRRIFF.get(), guardAttributes(NpcRace.HOBBIT, 20.0));
        event.put(BREE_HOBBIT.get(), NpcEntity.createAttributes(NpcRace.HOBBIT).build());
        event.put(BREE_MAN.get(), NpcEntity.createAttributes(NpcRace.MAN).build());
        event.put(BREE_GUARD.get(), guardAttributes(NpcRace.MAN, 20.0));
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeSupplier guardAttributes(
            NpcRace race, double maxHealth) {
        return NpcEntity.createAttributes(race)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.KNOCKBACK_RESISTANCE, GUARD_KNOCKBACK_RESISTANCE)
                .build();
    }
}
