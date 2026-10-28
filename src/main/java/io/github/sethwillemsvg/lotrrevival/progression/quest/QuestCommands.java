package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lotrquest} (permission level 2): {@code list [player]}, {@code give <player> <template> [count]},
 * {@code complete <player> <quest uuid>}, {@code clear <player>}.
 */
public final class QuestCommands {
    private static final DynamicCommandExceptionType UNKNOWN_TEMPLATE = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.lotrrevival.quest.unknown_template", id));
    private static final DynamicCommandExceptionType UNKNOWN_QUEST = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.lotrrevival.quest.unknown_quest", id));

    private QuestCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal("lotrquest").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("list")
                .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))));
        root.then(Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        ctx.getSource()
                                                .registryAccess()
                                                .registry(QuestTemplate.REGISTRY)
                                                .map(Registry::keySet)
                                                .orElseGet(java.util.Set::of),
                                        builder))
                                .executes(ctx -> give(ctx, Optional.empty()))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100_000))
                                        .executes(ctx -> give(
                                                ctx, Optional.of(IntegerArgumentType.getInteger(ctx, "count"))))))));
        root.then(Commands.literal("complete")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("quest", UuidArgument.uuid())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        playerQuestIds(ctx), builder))
                                .executes(QuestCommands::complete))));
        root.then(Commands.literal("clear")
                .then(Commands.argument("player", EntityArgument.player()).executes(QuestCommands::clear)));
        root.then(io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferCommand.node());
        dispatcher.register(root);
    }

    private static java.util.List<String> playerQuestIds(CommandContext<CommandSourceStack> ctx) {
        try {
            return Quests.get(EntityArgument.getPlayer(ctx, "player")).active().stream()
                    .map(q -> q.uuid().toString())
                    .toList();
        } catch (CommandSyntaxException e) {
            return java.util.List.of();
        }
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        PlayerQuests quests = Quests.get(player);
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.lotrrevival.quest.list.header",
                        player.getDisplayName(),
                        quests.active().size(),
                        quests.completedTotal(),
                        quests.failedTotal()),
                false);
        for (QuestInstance q : quests.active()) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.lotrrevival.quest.list.entry",
                            q.uuid().toString(),
                            q.template().toString(),
                            q.progress(),
                            q.count(),
                            q.state().getSerializedName()),
                    false);
        }
        return quests.active().size();
    }

    private static int give(CommandContext<CommandSourceStack> ctx, Optional<Integer> count)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "template");
        Optional<QuestTemplate> template = Quests.template(player.level().registryAccess(), id)
                .map(net.minecraft.core.Holder::value);
        if (template.isEmpty()) {
            throw UNKNOWN_TEMPLATE.create(id);
        }
        int n = count.orElseGet(() -> template.get().objective().count().roll(player.getRandom()));
        ResourceLocation giverFaction = Quests.group(
                        player.level().registryAccess(), template.get().group().location())
                .map(group -> group.faction().location())
                .orElse(Factions.UNALIGNED.location());
        Quests.start(
                player,
                id,
                n,
                Quests.PLACEHOLDER_GIVER,
                Component.translatable("commands.lotrrevival.quest.giver"),
                giverFaction,
                Optional.empty());
        ctx.getSource().sendSuccess(
                () -> Component.translatable("commands.lotrrevival.quest.give", id.toString(), n, player.getDisplayName()),
                true);
        return 1;
    }

    private static int complete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        UUID id = UuidArgument.getUuid(ctx, "quest");
        if (Quests.get(player).find(id).isEmpty()) {
            throw UNKNOWN_QUEST.create(id.toString());
        }
        Optional<QuestRecord> record = Quests.complete(player, id, true);
        if (record.isEmpty()) {
            throw UNKNOWN_QUEST.create(id.toString());
        }
        ctx.getSource().sendSuccess(
                () -> Component.translatable("commands.lotrrevival.quest.complete", id.toString(), player.getDisplayName()),
                true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Quests.clear(player);
        ctx.getSource().sendSuccess(
                () -> Component.translatable("commands.lotrrevival.quest.clear", player.getDisplayName()), true);
        return 1;
    }
}
