package io.github.sethwillemsvg.lotrrevival.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRegion;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelation;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /lotr} root command. Core registers the root and the {@code faction} subcommands; the alignment and
 * pledge tickets add their literals under it (Brigadier merges roots with the same name).
 */
public final class LotrCommand {
    private static final DynamicCommandExceptionType UNKNOWN_FACTION = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.lotrrevival.faction.unknown", id));

    private LotrCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> factionList = Commands.literal("list")
                .executes(ctx -> list(ctx.getSource(), false))
                .then(Commands.literal("all").executes(ctx -> list(ctx.getSource(), true)));
        dispatcher.register(Commands.literal("lotr")
                .then(AlignmentCommands.alignment())
                .then(AlignmentCommands.pledge())
                .then(Commands.literal("faction")
                        .then(factionList)
                        .then(Commands.literal("relation")
                                .then(factionArgument("a")
                                        .then(factionArgument("b")
                                                .executes(LotrCommand::relation))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> factionArgument(String name) {
        return Commands.argument(name, ResourceLocationArgument.id())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                        ctx.getSource()
                                .registryAccess()
                                .registryOrThrow(ModDataRegistries.FACTION)
                                .keySet(),
                        builder));
    }

    private static int list(CommandSourceStack source, boolean all) {
        Registry<Faction> registry = source.registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        source.sendSuccess(() -> Component.translatable("commands.lotrrevival.faction.list.header"), false);
        int shown = 0;
        for (FactionRegion region : FactionRegion.values()) {
            List<ResourceKey<Faction>> inRegion = new ArrayList<>();
            for (ResourceKey<Faction> key : registry.registryKeySet()) {
                Faction faction = registry.get(key);
                if (faction != null && faction.region() == region && (all || !faction.hidden())) {
                    inRegion.add(key);
                }
            }
            if (inRegion.isEmpty()) {
                continue;
            }
            inRegion.sort(Comparator.comparingInt((ResourceKey<Faction> k) -> registry.get(k).sortOrder()));
            Component line = Component.translatable(
                    "commands.lotrrevival.faction.list.region",
                    Component.translatable("region." + LOTRRevival.MOD_ID + "." + region.getSerializedName()));
            source.sendSuccess(() -> line, false);
            for (ResourceKey<Faction> key : inRegion) {
                source.sendSuccess(() -> Component.literal("  ").append(Factions.displayName(key)), false);
                shown++;
            }
        }
        return shown;
    }

    private static int relation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Registry<Faction> registry = ctx.getSource().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        ResourceKey<Faction> a = factionArg(ctx, "a", registry);
        ResourceKey<Faction> b = factionArg(ctx, "b", registry);
        FactionRelation relation = Factions.relation(registry, a, b);
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "commands.lotrrevival.faction.relation",
                                Factions.displayName(a),
                                Factions.displayName(b),
                                Component.translatable("relation." + LOTRRevival.MOD_ID + "."
                                        + relation.getSerializedName())),
                        false);
        return relation.ordinal();
    }

    private static ResourceKey<Faction> factionArg(
            CommandContext<CommandSourceStack> ctx, String name, Registry<Faction> registry)
            throws CommandSyntaxException {
        return resolveFaction(ctx.getSource(), ResourceLocationArgument.getId(ctx, name));
    }

    /** Resolves a typed faction id; a bare {@code gondor} (parsed as {@code minecraft:gondor}) means {@code lotrrevival:gondor}. */
    static ResourceKey<Faction> resolveFaction(CommandSourceStack source, ResourceLocation id)
            throws CommandSyntaxException {
        Registry<Faction> registry = source.registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        ResourceKey<Faction> key = ResourceKey.create(ModDataRegistries.FACTION, id);
        if (!registry.containsKey(key) && id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            ResourceKey<Faction> own = ResourceKey.create(ModDataRegistries.FACTION, LOTRRevival.id(id.getPath()));
            if (registry.containsKey(own)) {
                return own;
            }
        }
        if (!registry.containsKey(key)) {
            throw UNKNOWN_FACTION.create(key.location().toString());
        }
        return key;
    }
}
