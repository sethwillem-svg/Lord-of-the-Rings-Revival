package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferClosedPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** {@code /lotrquest offer <entity>}: forces an offer on a quest giver, replacing any current one (testing). */
public final class OfferCommand {
    private static final SimpleCommandExceptionType NOT_A_GIVER = new SimpleCommandExceptionType(
            Component.translatable("commands.lotrrevival.quest.offer.not_giver"));
    private static final SimpleCommandExceptionType NO_GROUP = new SimpleCommandExceptionType(
            Component.translatable("commands.lotrrevival.quest.offer.no_group"));
    private static final SimpleCommandExceptionType FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.lotrrevival.quest.offer.failed"));

    private OfferCommand() {}

    /** The {@code offer} literal, to be attached under {@code /lotrquest}. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("offer")
                .then(Commands.argument("entity", EntityArgument.entity()).executes(OfferCommand::offer));
    }

    private static int offer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(ctx, "entity");
        if (!(entity instanceof NpcEntity npc) || !QuestGivers.isGiverType(npc) || !(npc.level() instanceof ServerLevel level)) {
            throw NOT_A_GIVER.create();
        }
        Optional<QuestGivers.GroupMatch> group = QuestGivers.matchGroup(level.registryAccess(), npc);
        if (group.isEmpty()) {
            throw NO_GROUP.create();
        }
        OfferService.clearOffer(npc, QuestOfferClosedPayload.Reason.GONE);
        Optional<QuestGiverData.Offer> offer = OfferService.rollOffer(level, npc, group.get(), npc.getRandom());
        if (offer.isEmpty()) {
            throw FAILED.create();
        }
        QuestGiverData.Offer rolled = offer.get();
        ctx.getSource().sendSuccess(
                () -> Component.translatable(
                        "commands.lotrrevival.quest.offer.success",
                        npc.getDisplayName(),
                        rolled.template().toString(),
                        rolled.count()),
                true);
        return 1;
    }
}
