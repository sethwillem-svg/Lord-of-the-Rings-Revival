package io.github.sethwillemsvg.lotrrevival.progression.bounty;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lotrbounty} (permission level 2): {@code list [faction]}, {@code clear <player> [faction]},
 * {@code addkills <player> <faction> <n>} (for testing).
 */
public final class BountyCommands {
    private static final DynamicCommandExceptionType UNKNOWN_FACTION = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.lotrrevival.bounty.unknown_faction", id));

    private BountyCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal("lotrbounty").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("list")
                .executes(ctx -> list(ctx, null))
                .then(factionArgument().executes(ctx -> list(ctx, faction(ctx)))));
        root.then(Commands.literal("clear")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> clear(ctx, null))
                        .then(factionArgument().executes(ctx -> clear(ctx, faction(ctx))))));
        root.then(Commands.literal("addkills")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(factionArgument()
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 100_000))
                                        .executes(BountyCommands::addKills)))));
        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation>
            factionArgument() {
        return Commands.argument("faction", ResourceLocationArgument.id())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                        ctx.getSource()
                                .registryAccess()
                                .registry(ModDataRegistries.FACTION)
                                .map(Registry::keySet)
                                .orElseGet(java.util.Set::of),
                        builder));
    }

    private static ResourceKey<Faction> faction(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "faction");
        ResourceKey<Faction> key = ResourceKey.create(ModDataRegistries.FACTION, id);
        boolean known = ctx.getSource()
                .registryAccess()
                .registry(ModDataRegistries.FACTION)
                .map(registry -> registry.containsKey(key))
                .orElse(false);
        if (!known) {
            throw UNKNOWN_FACTION.create(id.toString());
        }
        return key;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, @Nullable ResourceKey<Faction> only) {
        MinecraftServer server = ctx.getSource().getServer();
        BountyData data = BountyData.get(server);
        long now = Bounties.now(server);
        data.prune(now);
        List<ResourceLocation> factions = new ArrayList<>(data.recordedFactions());
        factions.sort(ResourceLocation::compareTo);
        int lines = 0;
        for (ResourceLocation faction : factions) {
            if (only != null && !only.location().equals(faction)) {
                continue;
            }
            ResourceKey<Faction> key = ResourceKey.create(ModDataRegistries.FACTION, faction);
            for (UUID player : data.recordedPlayers(faction)) {
                int live = data.liveKills(faction, player, now);
                if (live == 0) {
                    continue;
                }
                String status = Bounties.isValidTarget(server, player, key)
                        ? "target"
                        : data.lastClaimed(faction, player).isPresent() ? "immune" : "below threshold";
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                Component name = online != null ? online.getDisplayName() : Component.literal(player.toString());
                ctx.getSource().sendSuccess(
                        () -> Component.translatable(
                                "commands.lotrrevival.bounty.list.entry", faction.toString(), name, live, status),
                        false);
                lines++;
            }
        }
        if (lines == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.lotrrevival.bounty.list.empty"), false);
        }
        return lines;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx, @Nullable ResourceKey<Faction> only)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        BountyData.get(ctx.getSource().getServer())
                .clearKills(player.getUUID(), Optional.ofNullable(only).map(ResourceKey::location).orElse(null));
        ctx.getSource().sendSuccess(
                () -> Component.translatable(
                        "commands.lotrrevival.bounty.clear",
                        player.getDisplayName(),
                        only == null ? "all" : only.location().toString()),
                true);
        return 1;
    }

    private static int addKills(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceKey<Faction> faction = faction(ctx);
        int n = IntegerArgumentType.getInteger(ctx, "n");
        MinecraftServer server = ctx.getSource().getServer();
        BountyData.get(server).addKills(faction.location(), player.getUUID(), Bounties.now(server), n);
        ctx.getSource().sendSuccess(
                () -> Component.translatable(
                        "commands.lotrrevival.bounty.addkills", n, faction.location().toString(), player.getDisplayName()),
                true);
        return n;
    }
}
