package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

/** Fired by the NPC pod when a player hires a unit. Conditions: {@code entity} (type or tag), {@code faction}. */
public final class HireUnitTrigger extends SimpleCriterionTrigger<HireUnitTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, EntityType<?> unitType, ResourceKey<Faction> faction) {
        trigger(player, instance -> instance.matches(unitType, faction));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player,
            Optional<HolderSet<EntityType<?>>> entity,
            Optional<ResourceKey<Faction>> faction)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE)
                                .optionalFieldOf("entity")
                                .forGetter(TriggerInstance::entity),
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .optionalFieldOf("faction")
                                .forGetter(TriggerInstance::faction))
                .apply(i, TriggerInstance::new));

        public boolean matches(EntityType<?> unitType, ResourceKey<Faction> actualFaction) {
            return entity.map(set -> set.contains(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(unitType)))
                            .orElse(true)
                    && faction.map(actualFaction::equals).orElse(true);
        }
    }
}
