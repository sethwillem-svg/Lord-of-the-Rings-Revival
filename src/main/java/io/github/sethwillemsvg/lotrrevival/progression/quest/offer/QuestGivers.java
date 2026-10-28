package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGroup;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Who may give quests, and which quest group an NPC belongs to. Eligibility is checked by the offer roll, the
 * marker and every accept.
 */
public final class QuestGivers {
    /** A quest group with its id. */
    public record GroupMatch(ResourceLocation id, QuestGroup group) {}

    /** A quest template with its id. */
    public record TemplateEntry(ResourceLocation id, QuestTemplate template) {}

    /** Groups per faction, rebuilt when the registry instance changes (datapack reload). */
    private record Index(Registry<QuestGroup> registry, Map<ResourceKey<Faction>, List<GroupMatch>> byFaction) {}

    private static volatile Index index;

    private QuestGivers() {}

    /** The entity is an NPC of a type in {@link OfferTags#QUEST_GIVERS}. */
    public static boolean isGiverType(Entity entity) {
        return entity instanceof NpcEntity && entity.getType().is(OfferTags.QUEST_GIVERS);
    }

    /** Alive, adult, not a trader, not hired and without an attack target: the NPC can give or hand in right now. */
    public static boolean isIdle(NpcEntity npc) {
        return npc.isAlive() && !npc.isBaby() && !npc.getRole().isTrader() && !npc.isHired() && npc.getTarget() == null;
    }

    /** The group an NPC of this faction and type belongs to, regardless of its state. */
    public static Optional<GroupMatch> matchGroup(RegistryAccess access, NpcEntity npc) {
        Optional<Registry<QuestGroup>> registry = access.registry(QuestGroup.REGISTRY);
        if (registry.isEmpty()) {
            return Optional.empty();
        }
        List<GroupMatch> candidates = index(registry.get()).byFaction().get(npc.faction());
        if (candidates == null) {
            return Optional.empty();
        }
        return pick(candidates, npc.getType());
    }

    /**
     * Chooses among groups of the NPC's faction: only groups whose {@code givers} (if any) list the NPC's type
     * qualify; groups with a {@code givers} list win over groups without; ties go to the lowest id.
     */
    public static Optional<GroupMatch> pick(List<GroupMatch> candidates, EntityType<?> type) {
        GroupMatch best = null;
        for (GroupMatch candidate : candidates) {
            if (!candidate.group().allowsGiver(type)) {
                continue;
            }
            if (best == null || better(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean better(GroupMatch candidate, GroupMatch best) {
        boolean candidateRestricted = candidate.group().givers().isPresent();
        boolean bestRestricted = best.group().givers().isPresent();
        if (candidateRestricted != bestRestricted) {
            return candidateRestricted;
        }
        return candidate.id().compareTo(best.id()) < 0;
    }

    /**
     * Full eligibility: mini-quests enabled, a giver type, idle, and a matching group.
     *
     * @return the group the giver offers from, empty if the NPC is not an eligible giver
     */
    public static Optional<GroupMatch> eligible(NpcEntity npc) {
        if (!ProgressionConfig.get(ProgressionConfig.MINI_QUESTS_ENABLED)
                || !isGiverType(npc)
                || !isIdle(npc)) {
            return Optional.empty();
        }
        return matchGroup(npc.level().registryAccess(), npc);
    }

    /** Every template of a group, in id order. */
    public static List<TemplateEntry> templates(RegistryAccess access, ResourceLocation groupId) {
        List<TemplateEntry> out = new ArrayList<>();
        access.registry(QuestTemplate.REGISTRY).ifPresent(registry -> {
            for (Map.Entry<ResourceKey<QuestTemplate>, QuestTemplate> entry : registry.entrySet()) {
                if (entry.getValue().group().location().equals(groupId)) {
                    out.add(new TemplateEntry(entry.getKey().location(), entry.getValue()));
                }
            }
        });
        out.sort(Comparator.comparing(TemplateEntry::id));
        return out;
    }

    private static Index index(Registry<QuestGroup> registry) {
        Index current = index;
        if (current != null && current.registry() == registry) {
            return current;
        }
        Map<ResourceKey<Faction>, List<GroupMatch>> byFaction = new HashMap<>();
        for (Map.Entry<ResourceKey<QuestGroup>, QuestGroup> entry : registry.entrySet()) {
            byFaction
                    .computeIfAbsent(entry.getValue().faction(), key -> new ArrayList<>())
                    .add(new GroupMatch(entry.getKey().location(), entry.getValue()));
        }
        byFaction.values().forEach(list -> list.sort(Comparator.comparing(GroupMatch::id)));
        Index built = new Index(registry, byFaction);
        index = built;
        return built;
    }
}
