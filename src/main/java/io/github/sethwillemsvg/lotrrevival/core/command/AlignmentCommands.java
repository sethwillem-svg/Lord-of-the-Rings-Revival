package io.github.sethwillemsvg.lotrrevival.core.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentRules;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.alignment.PledgeBreakReason;
import io.github.sethwillemsvg.lotrrevival.core.alignment.PledgeResult;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** {@code /lotr alignment ...} and {@code /lotr pledge ...}. Registered under the shared {@code /lotr} root. */
final class AlignmentCommands {
    private static final int MAX_COOLDOWN = 10_000_000;

    private AlignmentCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> alignment() {
        return Commands.literal("alignment")
                .then(Commands.literal("get")
                        .executes(ctx -> getAll(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> getAll(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                                .then(factionArgument("faction")
                                        .executes(ctx -> getOne(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                faction(ctx, "faction"))))))
                .then(Commands.literal("set")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(factionArgument("faction")
                                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                .executes(ctx -> set(ctx))))))
                .then(Commands.literal("add")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(factionArgument("faction")
                                        .then(Commands.argument("delta", FloatArgumentType.floatArg())
                                                .executes(ctx -> add(ctx, false))
                                                .then(Commands.literal("spread").executes(ctx -> add(ctx, true)))))))
                .then(Commands.literal("reset")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> reset(ctx, null))
                                .then(factionArgument("faction")
                                        .executes(ctx -> reset(ctx, faction(ctx, "faction"))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> pledge() {
        return Commands.literal("pledge")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(factionArgument("faction")
                                        .executes(ctx -> pledgeSet(ctx, false))
                                        .then(Commands.literal("force").executes(ctx -> pledgeSet(ctx, true))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(AlignmentCommands::pledgeClear)))
                .then(Commands.literal("cooldown")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0, MAX_COOLDOWN))
                                        .executes(AlignmentCommands::pledgeCooldown))));
    }

    // ------------------------------------------------------------ alignment

    private static int getAll(CommandSourceStack source, ServerPlayer player) {
        Registry<Faction> registry = source.registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        List<ResourceKey<Faction>> keys = new ArrayList<>();
        for (ResourceKey<Faction> key : registry.registryKeySet()) {
            if (!registry.getOrThrow(key).hidden() && Alignment.get(player, key) != 0.0f) {
                keys.add(key);
            }
        }
        keys.sort(Comparator.comparingInt((ResourceKey<Faction> k) -> registry.getOrThrow(k).sortOrder()));
        if (keys.isEmpty()) {
            source.sendSuccess(
                    () -> Component.translatable("commands.lotrrevival.alignment.get.none", player.getDisplayName()),
                    false);
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable("commands.lotrrevival.alignment.get.header", player.getDisplayName()),
                false);
        for (ResourceKey<Faction> key : keys) {
            source.sendSuccess(() -> Component.literal("  ").append(describe(player, key)), false);
        }
        return keys.size();
    }

    private static int getOne(CommandSourceStack source, ServerPlayer player, ResourceKey<Faction> key) {
        source.sendSuccess(() -> describe(player, key), false);
        return Math.round(Alignment.get(player, key));
    }

    private static Component describe(ServerPlayer player, ResourceKey<Faction> key) {
        return Component.translatable(
                "commands.lotrrevival.alignment.value",
                Factions.displayName(key),
                signed(Alignment.get(player, key)),
                AlignmentRules.rankName(key, Alignment.rank(player, key)));
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceKey<Faction> key = faction(ctx, "faction");
        float value = FloatArgumentType.getFloat(ctx, "value");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        if (isFixed(ctx.getSource(), key)) {
            ctx.getSource()
                    .sendFailure(Component.translatable(
                            "commands.lotrrevival.alignment.fixed", Factions.displayName(key)));
            return 0;
        }
        for (ServerPlayer player : players) {
            Alignment.set(player, key, value, AlignmentSource.command());
        }
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "commands.lotrrevival.alignment.set",
                                Factions.displayName(key),
                                signed(AlignmentRules.round(AlignmentRules.clamp(value))),
                                players.size()),
                        true);
        return players.size();
    }

    private static int add(CommandContext<CommandSourceStack> ctx, boolean spread) throws CommandSyntaxException {
        ResourceKey<Faction> key = faction(ctx, "faction");
        float delta = FloatArgumentType.getFloat(ctx, "delta");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        if (!spread && isFixed(ctx.getSource(), key)) {
            ctx.getSource()
                    .sendFailure(Component.translatable(
                            "commands.lotrrevival.alignment.fixed", Factions.displayName(key)));
            return 0;
        }
        for (ServerPlayer player : players) {
            if (spread) {
                Alignment.applyKill(player, key, delta, false, AlignmentSource.command());
            } else {
                Alignment.add(player, key, delta, AlignmentSource.command());
            }
        }
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                spread
                                        ? "commands.lotrrevival.alignment.add.spread"
                                        : "commands.lotrrevival.alignment.add",
                                Factions.displayName(key),
                                signed(delta),
                                players.size()),
                        true);
        return players.size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ResourceKey<Faction> only)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        for (ServerPlayer player : players) {
            Alignment.reset(player, only);
        }
        ctx.getSource()
                .sendSuccess(
                        () -> only == null
                                ? Component.translatable("commands.lotrrevival.alignment.reset.all", players.size())
                                : Component.translatable(
                                        "commands.lotrrevival.alignment.reset.one",
                                        Factions.displayName(only),
                                        players.size()),
                        true);
        return players.size();
    }

    // ------------------------------------------------------------ pledge

    private static int pledgeSet(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceKey<Faction> key = faction(ctx, "faction");
        PledgeResult result = Alignment.tryPledge(player, key, force);
        if (!result.isSuccess()) {
            ctx.getSource()
                    .sendFailure(Component.translatable(
                            "commands.lotrrevival.pledge.set.failed." + result.name().toLowerCase(Locale.ROOT),
                            player.getDisplayName(),
                            Factions.displayName(key)));
            return 0;
        }
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "commands.lotrrevival.pledge.set", player.getDisplayName(), Factions.displayName(key)),
                        true);
        return 1;
    }

    private static int pledgeClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        if (Alignment.pledge(player).isEmpty()) {
            ctx.getSource()
                    .sendFailure(Component.translatable(
                            "commands.lotrrevival.pledge.clear.none", player.getDisplayName()));
            return 0;
        }
        Alignment.breakPledge(player, PledgeBreakReason.COMMAND);
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable("commands.lotrrevival.pledge.clear", player.getDisplayName()),
                        true);
        return 1;
    }

    private static int pledgeCooldown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        Alignment.setPledgeCooldown(player, ticks);
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "commands.lotrrevival.pledge.cooldown", player.getDisplayName(), ticks),
                        true);
        return ticks;
    }

    // ------------------------------------------------------------ helpers

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> factionArgument(String name) {
        return Commands.argument(name, ResourceLocationArgument.id())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                        ctx.getSource()
                                .registryAccess()
                                .registryOrThrow(ModDataRegistries.FACTION)
                                .keySet(),
                        builder));
    }

    private static ResourceKey<Faction> faction(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        return LotrCommand.resolveFaction(ctx.getSource(), ResourceLocationArgument.getId(ctx, name));
    }

    private static boolean isFixed(CommandSourceStack source, ResourceKey<Faction> key) {
        Faction faction = source.registryAccess().registryOrThrow(ModDataRegistries.FACTION).get(key);
        return faction != null && faction.fixedAlignment().isPresent();
    }

    private static String signed(float value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }
}
