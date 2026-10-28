package io.github.sethwillemsvg.lotrrevival.progression.quest;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressCounters;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Gives each player one quest book the first time they are seen in Middle-earth. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class QuestBookGiver {
    private static final int POLL_INTERVAL_TICKS = 20;

    private QuestBookGiver() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % POLL_INTERVAL_TICKS == 0) {
            giveIfDue(player, player.level().dimension());
        }
    }

    /**
     * Gives the book when {@code dimension} is Middle-earth and it was never given. Returns true when it was
     * given now. The flag is set even if the inventory was full (the book is dropped), so it is never given twice.
     */
    public static boolean giveIfDue(ServerPlayer player, ResourceKey<Level> dimension) {
        if (!dimension.equals(ModDimensions.MIDDLE_EARTH)) {
            return false;
        }
        ProgressCounters counters = player.getData(ProgressionAttachments.PROGRESS_COUNTERS);
        if (counters.questBookGiven()) {
            return false;
        }
        player.setData(ProgressionAttachments.PROGRESS_COUNTERS, counters.withQuestBookGiven());
        ItemStack book = new ItemStack(QuestItems.QUEST_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        return true;
    }
}
