package io.github.sethwillemsvg.lotrrevival.progression.title;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lotrtitle grant|revoke <player> <title>} for special titles (permission 2) and
 * {@code /lotrtitle list [player]}.
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class TitleCommand {
    private static final DynamicCommandExceptionType UNKNOWN_TITLE = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.lotrrevival.title.unknown", id));
    private static final DynamicCommandExceptionType NOT_SPECIAL = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.lotrrevival.title.not_special", id));

    private TitleCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotrtitle")
                .then(Commands.literal("grant")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(titleArgument().executes(ctx -> grant(ctx, true)))))
                .then(Commands.literal("revoke")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(titleArgument().executes(ctx -> grant(ctx, false)))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> list(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> titleArgument() {
        return Commands.argument("title", ResourceLocationArgument.id()).suggests((ctx, builder) -> {
            List<ResourceLocation> special = TitleDefinitions.all(ctx.getSource().registryAccess()).entrySet().stream()
                    .filter(entry -> entry.getValue().source() instanceof TitleSource.Special)
                    .map(Map.Entry::getKey)
                    .toList();
            return SharedSuggestionProvider.suggestResource(special, builder);
        });
    }

    private static int grant(CommandContext<CommandSourceStack> ctx, boolean grant) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "title");
        TitleDefinition definition = TitleDefinitions.find(source.registryAccess(), id).orElse(null);
        if (grant) {
            if (definition == null) {
                throw UNKNOWN_TITLE.create(id.toString());
            }
            if (!(definition.source() instanceof TitleSource.Special)) {
                throw NOT_SPECIAL.create(id.toString());
            }
            boolean changed = TitleService.grant(target, id);
            source.sendSuccess(
                    () -> Component.translatable(
                            changed ? "commands.lotrrevival.title.grant.success" : "commands.lotrrevival.title.grant.already",
                            target.getDisplayName(),
                            id.toString()),
                    true);
            return changed ? 1 : 0;
        }
        // Revoking works on any granted id, even one whose definition a datapack removed.
        boolean changed = TitleService.revoke(target, id);
        source.sendSuccess(
                () -> Component.translatable(
                        changed ? "commands.lotrrevival.title.revoke.success" : "commands.lotrrevival.title.revoke.missing",
                        target.getDisplayName(),
                        id.toString()),
                changed);
        return changed ? 1 : 0;
    }

    private static int list(CommandSourceStack source, ServerPlayer target) {
        List<ResourceLocation> usable = Titles.usable(target);
        TitleData data = TitleService.data(target);
        source.sendSuccess(
                () -> Component.translatable("commands.lotrrevival.title.list.header", target.getDisplayName(), usable.size()),
                false);
        for (ResourceLocation id : usable) {
            boolean selected = data.selected().filter(id::equals).isPresent();
            source.sendSuccess(
                    () -> Component.translatable(
                            selected ? "commands.lotrrevival.title.list.selected" : "commands.lotrrevival.title.list.entry",
                            id.toString()),
                    false);
        }
        return usable.size();
    }
}
