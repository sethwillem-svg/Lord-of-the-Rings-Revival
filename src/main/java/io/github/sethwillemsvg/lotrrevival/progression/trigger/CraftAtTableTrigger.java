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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Fired when a player takes a result from a faction crafting table. Conditions: {@code table} (block or tag),
 * {@code faction}, {@code item}.
 */
public final class CraftAtTableTrigger extends SimpleCriterionTrigger<CraftAtTableTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Block table, ResourceKey<Faction> faction, ItemStack result) {
        trigger(player, instance -> instance.matches(table, faction, result));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player,
            Optional<HolderSet<Block>> table,
            Optional<ResourceKey<Faction>> faction,
            Optional<ItemPredicate> item)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        RegistryCodecs.homogeneousList(Registries.BLOCK)
                                .optionalFieldOf("table")
                                .forGetter(TriggerInstance::table),
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .optionalFieldOf("faction")
                                .forGetter(TriggerInstance::faction),
                        ItemPredicate.CODEC.optionalFieldOf("item").forGetter(TriggerInstance::item))
                .apply(i, TriggerInstance::new));

        public boolean matches(Block actualTable, ResourceKey<Faction> actualFaction, ItemStack result) {
            return table.map(set -> set.contains(BuiltInRegistries.BLOCK.wrapAsHolder(actualTable)))
                            .orElse(true)
                    && faction.map(actualFaction::equals).orElse(true)
                    && item.map(predicate -> predicate.test(result)).orElse(true);
        }
    }
}
