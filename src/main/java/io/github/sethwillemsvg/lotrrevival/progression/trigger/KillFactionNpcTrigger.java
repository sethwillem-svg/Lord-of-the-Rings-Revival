package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootContext;

/**
 * Fires when a player (or a unit credited to them) kills a faction NPC. Conditions: {@code faction} or
 * {@code faction_tag}, {@code entity} predicate on the victim, {@code by_hired} (omitted means any).
 */
public final class KillFactionNpcTrigger extends SimpleCriterionTrigger<KillFactionNpcTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, LivingEntity victim, ResourceKey<Faction> faction, boolean byHired) {
        LootContext context = EntityPredicate.createContext(player, victim);
        Registry<Faction> registry = player.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        trigger(player, instance -> instance.matches(context, registry, faction, byHired));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player,
            Optional<ResourceKey<Faction>> faction,
            Optional<TagKey<Faction>> factionTag,
            Optional<ContextAwarePredicate> entity,
            Optional<Boolean> byHired)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .optionalFieldOf("faction")
                                .forGetter(TriggerInstance::faction),
                        TagKey.codec(ModDataRegistries.FACTION)
                                .optionalFieldOf("faction_tag")
                                .forGetter(TriggerInstance::factionTag),
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("entity")
                                .forGetter(TriggerInstance::entity),
                        Codec.BOOL.optionalFieldOf("by_hired").forGetter(TriggerInstance::byHired))
                .apply(i, TriggerInstance::new));

        public boolean matches(
                LootContext victimContext,
                Registry<Faction> registry,
                ResourceKey<Faction> victimFaction,
                boolean hired) {
            if (faction.isPresent() && !faction.get().equals(victimFaction)) {
                return false;
            }
            if (factionTag.isPresent()
                    && !registry.getHolder(victimFaction)
                            .map(holder -> holder.is(factionTag.get()))
                            .orElse(false)) {
                return false;
            }
            if (byHired.isPresent() && byHired.get() != hired) {
                return false;
            }
            return entity.map(predicate -> predicate.matches(victimContext)).orElse(true);
        }
    }
}
