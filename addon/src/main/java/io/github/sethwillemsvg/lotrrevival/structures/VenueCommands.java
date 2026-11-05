package io.github.sethwillemsvg.lotrrevival.structures;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.venue.StructureVenues;
import io.github.sethwillemsvg.lotrrevival.structures.venue.Venue;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueIndex;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueIngest;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueKind;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueQuery;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /lotr_revival venues reindex|list [kind] [radius]} (op 2) and {@code /lotr_revival structures relock} (op 4,
 * run twice within 30 s). Credits commands are ticket 10. Brigadier merges our {@code lotr_revival} literal with other
 * systems' registrations of the same root.
 */
public final class VenueCommands {
    static final String SYS = StructureStats.SYS;

    private VenueCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lotr_revival")
                .then(Commands.literal("venues").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reindex").executes(VenueCommands::reindex))
                        .then(Commands.literal("list").executes(c -> list(c, null, 512))
                                .then(Commands.argument("kind", StringArgumentType.word()).executes(c ->
                                                list(c, StringArgumentType.getString(c, "kind"), 512))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 4096)).executes(c ->
                                                list(c, StringArgumentType.getString(c, "kind"), IntegerArgumentType.getInteger(c, "radius")))))))
                .then(Commands.literal("structures").requires(s -> s.hasPermission(4))
                        .then(Commands.literal("relock").executes(VenueCommands::relock))));
    }

    private static int reindex(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        if (!VenueIngest.indexed(level)) {
            c.getSource().sendFailure(Component.literal("No venue index in " + level.dimension().location()));
            return 0;
        }
        int added = VenueIngest.reindex(level);
        VenueIndex idx = VenueIndex.get(level);
        c.getSource().sendSuccess(() -> Component.literal("Venues: " + added + " new, " + idx.size() + " entries ("
                + idx.completeCount() + " complete)"), true);
        return added;
    }

    private static int list(CommandContext<CommandSourceStack> c, String kind, int radius) {
        ServerLevel level = c.getSource().getLevel();
        VenueQuery q = VenueQuery.of().withIncomplete().withLimit(32);
        if (kind != null && !kind.equals("all")) {
            var k = VenueKind.byId(kind);
            if (k.isEmpty()) {
                c.getSource().sendFailure(Component.literal("Unknown kind " + kind));
                return 0;
            }
            q = VenueQuery.of(k.get()).withIncomplete().withLimit(32);
        }
        BlockPos from = BlockPos.containing(c.getSource().getPosition());
        List<Venue> found = StructureVenues.find(level, from, 0, radius, q);
        c.getSource().sendSuccess(() -> Component.literal(found.size() + " venues within " + radius + " blocks"), false);
        for (Venue v : found) {
            String line = v.structureId() + " " + v.kinds() + " at " + v.centre().toShortString()
                    + v.faction().map(f -> " faction " + f).orElse("") + (v.complete() ? "" : " (incomplete)");
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return found.size();
    }

    private static int relock(CommandContext<CommandSourceStack> c) {
        ServerLevel overworld = c.getSource().getServer().overworld();
        WorldLock lock = WorldLock.get(overworld);
        if (!lock.requestRelock(overworld.getGameTime())) {
            c.getSource().sendSuccess(() -> Component.literal("Relocking applies the current config's spacing to new chunks "
                    + "and can cut structures at old chunk borders. Run it again within 30 seconds to confirm."), false);
            return 0;
        }
        lock.apply(overworld, true);
        LotrLog.warn(SYS, "world lock replaced by {}", c.getSource().getTextName());
        c.getSource().sendSuccess(() -> Component.literal("Structure grid relocked: " + WorldLock.describe()), true);
        return 1;
    }
}
