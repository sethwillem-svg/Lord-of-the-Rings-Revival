package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * Fired by trader code when a trade completes. Conditions: {@code faction}, {@code trader} (type or tag),
 * {@code direction} (default any), {@code item}.
 */
public final class TradeTrigger extends SimpleCriterionTrigger<TradeTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(
            ServerPlayer player,
            ResourceKey<Faction> faction,
            EntityType<?> traderType,
            TradeDirection direction,
            ItemStack item) {
        trigger(player, instance -> instance.matches(faction, traderType, direction, item));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player,
            Optional<ResourceKey<Faction>> faction,
            Optional<HolderSet<EntityType<?>>> trader,
            TradeDirection direction,
            Optional<ItemPredicate> item)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .optionalFieldOf("faction")
                                .forGetter(TriggerInstance::faction),
                        RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE)
                                .optionalFieldOf("trader")
                                .forGetter(TriggerInstance::trader),
                        TradeDirection.CODEC
                                .optionalFieldOf("direction", TradeDirection.ANY)
                                .forGetter(TriggerInstance::direction),
                        ItemPredicate.CODEC.optionalFieldOf("item").forGetter(TriggerInstance::item))
                .apply(i, TriggerInstance::new));

        public boolean matches(
                ResourceKey<Faction> actualFaction, EntityType<?> traderType, TradeDirection actual, ItemStack stack) {
            return faction.map(actualFaction::equals).orElse(true)
                    && trader.map(set -> set.contains(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(traderType)))
                            .orElse(true)
                    && (direction == TradeDirection.ANY || direction == actual)
                    && item.map(predicate -> predicate.test(stack)).orElse(true);
        }
    }
}
