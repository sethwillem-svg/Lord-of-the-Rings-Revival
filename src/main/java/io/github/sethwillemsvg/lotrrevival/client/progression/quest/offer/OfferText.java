package io.github.sethwillemsvg.lotrrevival.client.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.network.OpenQuestOfferPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGroup;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestObjective;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestTemplate;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/** Builds the lines of the offer screen from an {@link OpenQuestOfferPayload} and the synced quest registries. */
final class OfferText {
    private OfferText() {}

    static Optional<QuestTemplate> template(RegistryAccess access, ResourceLocation id) {
        return access.registry(QuestTemplate.REGISTRY).map(registry -> registry.get(id));
    }

    static Optional<QuestGroup> group(RegistryAccess access, ResourceLocation id) {
        return access.registry(QuestGroup.REGISTRY).map(registry -> registry.get(id));
    }

    /** "Bring me 12 Bread", "Slay 5 Orc", "Hunt down Name": the objective with the rolled count. */
    static Component objective(RegistryAccess access, OpenQuestOfferPayload offer) {
        Optional<QuestObjective> objective = template(access, offer.template()).map(QuestTemplate::objective);
        if (objective.isEmpty()) {
            return Component.translatable("quest.lotrrevival.objective.unknown");
        }
        QuestObjective o = objective.get();
        if (o instanceof QuestObjective.Collect collect) {
            Component name = collect.item()
                    .items()
                    .flatMap(set -> set.stream().findFirst().map(h -> new ItemStack(h.value()).getHoverName()))
                    .orElse(Component.translatable("quest.lotrrevival.objective.items"));
            return Component.translatable("gui.lotrrevival.offer.objective.collect", offer.count(), name);
        }
        if (o instanceof QuestObjective.KillEntity kill) {
            return Component.translatable(
                    "gui.lotrrevival.offer.objective.kill_entity", offer.count(), entityName(kill.entities()));
        }
        if (o instanceof QuestObjective.KillFaction kill) {
            Component name = kill.faction()
                    .map(Factions::displayName)
                    .orElse(Component.translatable("quest.lotrrevival.objective.enemies"));
            return Component.translatable("gui.lotrrevival.offer.objective.kill_faction", offer.count(), name);
        }
        if (o instanceof QuestObjective.Bounty) {
            return Component.translatable(
                    "gui.lotrrevival.offer.objective.bounty", offer.bountyTargetName().orElse("?"));
        }
        return Component.translatable("quest.lotrrevival.objective.unknown");
    }

    /** Bounties show their fixed rewards; other offers keep theirs a surprise. Empty for those. */
    static Optional<Component> rewardHint(RegistryAccess access, OpenQuestOfferPayload offer) {
        Optional<QuestTemplate> template = template(access, offer.template());
        if (template.isEmpty() || !(template.get().objective() instanceof QuestObjective.Bounty)) {
            return Optional.empty();
        }
        Component faction = group(access, template.get().group().location())
                .map(g -> Factions.displayName(g.effectiveRewardFaction()))
                .orElse(Component.translatable("gui.lotrrevival.offer.reward.unknown_faction"));
        return Optional.of(Component.translatable(
                "gui.lotrrevival.offer.reward.bounty",
                String.format(Locale.ROOT, "%+.0f", offer.alignmentBase()),
                faction,
                offer.coinBase()));
    }

    private static Component entityName(HolderSet<EntityType<?>> set) {
        if (set.unwrapKey().isPresent()) {
            return Component.translatable("quest.lotrrevival.objective.creatures");
        }
        return set.stream()
                .findFirst()
                .<Component>map(holder -> holder.value().getDescription())
                .orElse(Component.translatable("quest.lotrrevival.objective.creatures"));
    }
}
