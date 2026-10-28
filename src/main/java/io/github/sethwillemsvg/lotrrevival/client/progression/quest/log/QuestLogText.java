package io.github.sethwillemsvg.lotrrevival.client.progression.quest.log;

import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGroup;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestObjective;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRecord;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRewardSummary;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Builds the player-facing text and look (icon, tint) of quests for the log screen and the HUD tracker. Holds no
 * screen state, so it is shared by both.
 */
public final class QuestLogText {
    public static final int DEFAULT_TINT = 0xC8B070;
    private static final int TICKS_PER_DAY = 24000;

    private QuestLogText() {}

    /** In-game day number (1-based) for a game time. */
    public static long day(long gameTime) {
        return Math.max(0L, gameTime) / TICKS_PER_DAY + 1;
    }

    public static Optional<QuestObjective> objective(RegistryAccess access, ResourceLocation template) {
        return template(access, template).map(QuestTemplate::objective);
    }

    private static Optional<QuestTemplate> template(RegistryAccess access, ResourceLocation id) {
        return access.registry(QuestTemplate.REGISTRY).map(registry -> registry.get(id));
    }

    public static Optional<QuestGroup> group(RegistryAccess access, ResourceLocation id) {
        return access.registry(QuestGroup.REGISTRY).map(registry -> registry.get(id));
    }

    /** Group colour as 0xRRGGBB. */
    public static int tint(RegistryAccess access, ResourceLocation group) {
        return group(access, group).map(QuestGroup::color).orElse(DEFAULT_TINT) & 0xFFFFFF;
    }

    /** Item shown as the quest icon. */
    public static ItemStack icon(RegistryAccess access, QuestInstance quest) {
        Optional<QuestObjective> objective = objective(access, quest.template());
        if (objective.isPresent()) {
            QuestObjective o = objective.get();
            if (o instanceof QuestObjective.Collect collect) {
                Optional<ItemStack> stack = collect.item().items().flatMap(set -> set.stream()
                        .findFirst()
                        .map(holder -> new ItemStack(holder.value())));
                return stack.orElse(new ItemStack(Items.PAPER));
            }
            if (o instanceof QuestObjective.KillEntity || o instanceof QuestObjective.KillFaction) {
                return new ItemStack(Items.IRON_SWORD);
            }
        }
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    /** "Collect Wheat", "Slay Orc", "Defeat Mordor": the short line of the HUD and list. */
    public static Component shortObjective(RegistryAccess access, QuestInstance quest) {
        Optional<QuestObjective> objective = objective(access, quest.template());
        if (objective.isEmpty()) {
            return Component.translatable("quest.lotrrevival.objective.unknown");
        }
        QuestObjective o = objective.get();
        if (o instanceof QuestObjective.Collect collect) {
            Component name = collect.item()
                    .items()
                    .flatMap(set -> set.stream().findFirst().map(h -> new ItemStack(h.value()).getHoverName()))
                    .orElse(Component.translatable("quest.lotrrevival.objective.items"));
            return Component.translatable("quest.lotrrevival.objective.collect", name);
        }
        if (o instanceof QuestObjective.KillEntity kill) {
            return Component.translatable("quest.lotrrevival.objective.kill_entity", entityName(kill.entities()));
        }
        if (o instanceof QuestObjective.KillFaction kill) {
            Component name = kill.faction()
                    .map(Factions::displayName)
                    .orElse(Component.translatable("quest.lotrrevival.objective.enemies"));
            return Component.translatable("quest.lotrrevival.objective.kill_faction", name);
        }
        return Component.translatable("quest.lotrrevival.objective.unknown");
    }

    private static Component entityName(HolderSet<net.minecraft.world.entity.EntityType<?>> set) {
        if (set.unwrapKey().isPresent()) {
            return Component.translatable("quest.lotrrevival.objective.creatures");
        }
        return set.stream()
                .findFirst()
                .<Component>map(holder -> holder.value().getDescription())
                .orElse(Component.translatable("quest.lotrrevival.objective.creatures"));
    }

    /** "3/10", or the failure reason for a failed quest. */
    public static Component shortStatus(QuestInstance quest) {
        if (quest.failed()) {
            return failure(quest).withStyle(ChatFormatting.RED);
        }
        if (quest.state() == io.github.sethwillemsvg.lotrrevival.progression.quest.QuestStatus.READY_TO_TURN_IN) {
            return Component.translatable("quest.lotrrevival.return_to", quest.giverName());
        }
        return Component.literal(quest.progress() + "/" + quest.count());
    }

    public static MutableComponent failure(QuestInstance quest) {
        return quest.failReason()
                .map(reason -> Component.translatable(reason.langKey()))
                .orElse(Component.translatable("quest.lotrrevival.fail.invalid"));
    }

    /** The opening line: a template-specific string when the lang has one, else a generic line per objective. */
    public static Component openingLine(RegistryAccess access, ResourceLocation template) {
        String key = "quest.lotrrevival.template." + template.getNamespace() + "." + template.getPath().replace('/', '.')
                + ".opening";
        if (I18n.exists(key)) {
            return Component.translatable(key);
        }
        String type = objective(access, template)
                .map(o -> o.type().getPath())
                .orElse("unknown");
        String generic = "quest.lotrrevival.opening." + type;
        return I18n.exists(generic)
                ? Component.translatable(generic)
                : Component.translatable("quest.lotrrevival.opening.unknown");
    }

    public static Component biomeName(ResourceLocation biome) {
        return Component.translatable(Util.makeDescriptionId("biome", biome));
    }

    /** Diary page of an active quest. */
    public static List<Component> activeLines(RegistryAccess access, QuestInstance quest) {
        List<Component> lines = new ArrayList<>();
        Component when = quest.givenBiome()
                .map(biome -> (Component) Component.translatable(
                        "quest.lotrrevival.diary.day_in", day(quest.givenAt()), biomeName(biome)))
                .orElse(Component.translatable("quest.lotrrevival.diary.day", day(quest.givenAt())));
        lines.add(when.copy().withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("quest.lotrrevival.diary.from", quest.giverName()));
        lines.add(Component.empty());
        lines.add(openingLine(access, quest.template()).copy().withStyle(ChatFormatting.ITALIC));
        lines.add(Component.empty());
        lines.add(Component.translatable("quest.lotrrevival.diary.objective", shortObjective(access, quest)));
        if (quest.failed()) {
            lines.add(Component.translatable("quest.lotrrevival.diary.failed", failure(quest))
                    .withStyle(ChatFormatting.RED));
        } else if (quest.state() == io.github.sethwillemsvg.lotrrevival.progression.quest.QuestStatus.READY_TO_TURN_IN) {
            lines.add(Component.translatable("quest.lotrrevival.diary.progress", quest.progress(), quest.count()));
            lines.add(Component.translatable("quest.lotrrevival.return_to", quest.giverName())
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.translatable("quest.lotrrevival.diary.progress", quest.progress(), quest.count()));
        }
        return lines;
    }

    /** Diary page of a finished quest in the log. */
    public static List<Component> recordLines(RegistryAccess access, QuestRecord record) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("quest.lotrrevival.diary.ended_day", day(record.at()))
                .withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("quest.lotrrevival.diary.from", record.giverName()));
        lines.add(Component.empty());
        lines.add(openingLine(access, record.template()).copy().withStyle(ChatFormatting.ITALIC));
        lines.add(Component.empty());
        Component objective = shortObjectiveOfTemplate(access, record.template());
        lines.add(Component.translatable("quest.lotrrevival.diary.objective", objective));
        lines.add(outcome(record));
        record.failReason()
                .ifPresent(reason -> lines.add(Component.translatable("quest.lotrrevival.diary.failed", Component.translatable(reason.langKey()))
                        .withStyle(ChatFormatting.RED)));
        QuestRewardSummary rewards = record.rewards();
        if (record.outcome() == io.github.sethwillemsvg.lotrrevival.progression.quest.QuestOutcome.COMPLETED) {
            lines.add(Component.empty());
            lines.add(Component.translatable("quest.lotrrevival.diary.rewards").withStyle(ChatFormatting.GOLD));
            Component faction = rewardFaction(access, record);
            if (rewards.alignment() != 0.0f) {
                lines.add(Component.translatable(
                        "quest.lotrrevival.diary.reward.alignment",
                        String.format(Locale.ROOT, "%+.1f", rewards.alignment()),
                        faction));
            }
            if (rewards.coins() > 0) {
                lines.add(Component.translatable("quest.lotrrevival.diary.reward.coins", rewards.coins()));
            }
            for (ItemStack stack : rewards.items()) {
                lines.add(Component.translatable(
                        "quest.lotrrevival.diary.reward.item", stack.getCount(), stack.getHoverName()));
            }
            if (rewards.hired()) {
                lines.add(Component.translatable("quest.lotrrevival.diary.reward.hired", record.giverName()));
            }
            if (rewards.alignment() == 0.0f
                    && rewards.coins() <= 0
                    && rewards.items().isEmpty()
                    && !rewards.hired()) {
                lines.add(Component.translatable("quest.lotrrevival.diary.reward.none"));
            }
        }
        return lines;
    }

    public static MutableComponent outcome(QuestRecord record) {
        return switch (record.outcome()) {
            case COMPLETED -> Component.translatable("quest.lotrrevival.outcome.completed")
                    .withStyle(ChatFormatting.GREEN);
            case FAILED -> Component.translatable("quest.lotrrevival.outcome.failed").withStyle(ChatFormatting.RED);
            case ABANDONED -> Component.translatable("quest.lotrrevival.outcome.abandoned")
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    private static Component rewardFaction(RegistryAccess access, QuestRecord record) {
        ResourceKey<io.github.sethwillemsvg.lotrrevival.core.faction.Faction> key = template(access, record.template())
                .flatMap(t -> group(access, t.group().location()))
                .map(QuestGroup::effectiveRewardFaction)
                .orElse(ResourceKey.create(ModDataRegistries.FACTION, record.faction()));
        return Factions.displayName(key);
    }

    private static Component shortObjectiveOfTemplate(RegistryAccess access, ResourceLocation template) {
        // A record keeps no rolled count, so describe the objective without it by borrowing the instance shape.
        QuestInstance probe = new QuestInstance(
                new java.util.UUID(0L, 0L),
                template,
                template,
                new java.util.UUID(0L, 0L),
                Component.empty(),
                template,
                Optional.empty(),
                0L,
                Optional.empty(),
                0,
                0.0f,
                0,
                0,
                io.github.sethwillemsvg.lotrrevival.progression.quest.QuestStatus.ACTIVE,
                Optional.empty(),
                io.github.sethwillemsvg.lotrrevival.progression.quest.ObjectiveData.EMPTY);
        return shortObjective(access, probe);
    }
}
