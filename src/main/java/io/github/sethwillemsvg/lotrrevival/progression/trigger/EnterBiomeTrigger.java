package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** Fires when the player's polled biome changes. Conditions: optional {@code biome} (id or #tag) and {@code dimension}. */
public final class EnterBiomeTrigger extends SimpleCriterionTrigger<EnterBiomeTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Holder<Biome> biome, ResourceKey<Level> dimension) {
        trigger(player, instance -> instance.matches(biome, dimension));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player,
            Optional<HolderSet<Biome>> biome,
            Optional<ResourceKey<Level>> dimension)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        RegistryCodecs.homogeneousList(Registries.BIOME)
                                .optionalFieldOf("biome")
                                .forGetter(TriggerInstance::biome),
                        ResourceKey.codec(Registries.DIMENSION)
                                .optionalFieldOf("dimension")
                                .forGetter(TriggerInstance::dimension))
                .apply(i, TriggerInstance::new));

        public boolean matches(Holder<Biome> current, ResourceKey<Level> currentDimension) {
            return biome.map(set -> set.contains(current)).orElse(true)
                    && dimension.map(currentDimension::equals).orElse(true);
        }
    }
}
