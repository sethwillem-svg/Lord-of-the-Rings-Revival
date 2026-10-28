package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired when a title becomes usable for the first time. Conditions: {@code title} id and {@code count}, the
 * minimum number of distinct usable titles.
 */
public final class EarnTitleTrigger extends SimpleCriterionTrigger<EarnTitleTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceLocation title, int usableCount) {
        trigger(player, instance -> instance.matches(title, usableCount));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player, Optional<ResourceLocation> title, Optional<Integer> count)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        ResourceLocation.CODEC.optionalFieldOf("title").forGetter(TriggerInstance::title),
                        Codec.intRange(1, Integer.MAX_VALUE)
                                .optionalFieldOf("count")
                                .forGetter(TriggerInstance::count))
                .apply(i, TriggerInstance::new));

        public boolean matches(ResourceLocation earned, int usableCount) {
            return title.map(earned::equals).orElse(true) && count.map(min -> usableCount >= min).orElse(true);
        }
    }
}
