package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/** Fires when the visited-biome set grows. Condition: required {@code min_count} (>= 1), matched as size >= min_count. */
public final class DistinctBiomesTrigger extends SimpleCriterionTrigger<DistinctBiomesTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int visitedCount) {
        trigger(player, instance -> visitedCount >= instance.minCount());
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int minCount)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("min_count").forGetter(TriggerInstance::minCount))
                .apply(i, TriggerInstance::new));
    }
}
