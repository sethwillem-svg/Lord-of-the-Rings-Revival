package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fires after an alignment rank change (and on login). Conditions: required {@code faction}, optional core
 * {@code rank} id (omitted means alignment >= 0). Matches when the player currently meets the rank.
 */
public final class AlignmentRankTrigger extends SimpleCriterionTrigger<AlignmentRankTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceKey<Faction> faction) {
        trigger(player, instance -> instance.matches(player, faction));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player, ResourceKey<Faction> faction, Optional<String> rank)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .fieldOf("faction")
                                .forGetter(TriggerInstance::faction),
                        FactionRank.ID_CODEC.optionalFieldOf("rank").forGetter(TriggerInstance::rank))
                .apply(i, TriggerInstance::new));

        public boolean matches(ServerPlayer player, ResourceKey<Faction> current) {
            return faction.equals(current) && Alignment.meetsRank(player, faction, rank.orElse("neutral"));
        }
    }
}
