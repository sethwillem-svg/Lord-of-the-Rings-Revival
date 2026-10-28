package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired when a quest completes. Conditions: {@code faction}, {@code group} id, {@code quest} (definition id, or
 * {@code #tag} string), {@code min_total} completed quests. Group, quest and tag ids are plain locations so this
 * trigger does not depend on the quest registries.
 */
public final class CompleteMiniQuestTrigger
        extends SimpleCriterionTrigger<CompleteMiniQuestTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(
            ServerPlayer player,
            ResourceKey<Faction> faction,
            ResourceLocation group,
            ResourceLocation quest,
            Collection<ResourceLocation> questTags,
            int totalCompleted) {
        trigger(player, instance -> instance.matches(faction, group, quest, questTags, totalCompleted));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player,
            Optional<ResourceKey<Faction>> faction,
            Optional<ResourceLocation> group,
            Optional<String> quest,
            Optional<Integer> minTotal)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .optionalFieldOf("faction")
                                .forGetter(TriggerInstance::faction),
                        ResourceLocation.CODEC.optionalFieldOf("group").forGetter(TriggerInstance::group),
                        Codec.STRING.optionalFieldOf("quest").forGetter(TriggerInstance::quest),
                        Codec.intRange(1, Integer.MAX_VALUE)
                                .optionalFieldOf("min_total")
                                .forGetter(TriggerInstance::minTotal))
                .apply(i, TriggerInstance::new));

        public boolean matches(
                ResourceKey<Faction> actualFaction,
                ResourceLocation actualGroup,
                ResourceLocation actualQuest,
                Collection<ResourceLocation> questTags,
                int total) {
            if (faction.isPresent() && !faction.get().equals(actualFaction)) {
                return false;
            }
            if (group.isPresent() && !group.get().equals(actualGroup)) {
                return false;
            }
            if (quest.isPresent()) {
                String wanted = quest.get();
                boolean hit = wanted.startsWith("#")
                        ? questTags.stream().anyMatch(tag -> tag.toString().equals(wanted.substring(1)))
                        : actualQuest.toString().equals(wanted);
                if (!hit) {
                    return false;
                }
            }
            return minTotal.map(min -> total >= min).orElse(true);
        }
    }
}
