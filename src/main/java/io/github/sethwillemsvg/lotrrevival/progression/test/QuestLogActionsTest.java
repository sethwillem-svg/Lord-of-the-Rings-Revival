package io.github.sethwillemsvg.lotrrevival.progression.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.network.AbandonQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.DeleteQuestRecordPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.RemoveQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.TrackQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.FailReason;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestBookGiver;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestItems;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestLogActions;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Quest log payload handlers and the first-entry quest book. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuestLogActionsTest {
    private static final ResourceLocation COLLECT = LOTRRevival.id("test/collect_wheat");

    private QuestLogActionsTest() {}

    private static QuestInstance start(ServerPlayer player) {
        return Quests.start(
                        player,
                        COLLECT,
                        10,
                        UUID.randomUUID(),
                        Component.literal("Test Giver"),
                        Factions.GONDOR.location(),
                        Optional.empty())
                .orElseThrow();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void abandonTrackAndRemoveHandlersRejectBadIds(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            QuestInstance live = start(player);
            QuestInstance failing = start(player);
            Quests.fail(player, failing.uuid(), FailReason.GIVER_DIED);

            Optional<UUID> trackedBefore = Quests.get(player).tracked();
            helper.assertFalse(
                    QuestLogActions.abandon(player, new AbandonQuestPayload(UUID.randomUUID())), "unknown id abandons nothing");
            helper.assertFalse(
                    QuestLogActions.abandon(player, new AbandonQuestPayload(failing.uuid())), "a failed quest is removed, not abandoned");
            helper.assertFalse(
                    QuestLogActions.remove(player, new RemoveQuestPayload(live.uuid())), "a live quest cannot be removed");
            helper.assertFalse(
                    QuestLogActions.remove(player, new RemoveQuestPayload(UUID.randomUUID())), "unknown id removes nothing");
            helper.assertFalse(
                    QuestLogActions.track(player, new TrackQuestPayload(Optional.of(UUID.randomUUID()))),
                    "unknown id cannot be tracked");
            helper.assertTrue(Quests.get(player).tracked().equals(trackedBefore), "tracking unchanged");

            helper.assertTrue(
                    QuestLogActions.track(player, new TrackQuestPayload(Optional.of(live.uuid()))), "known quest tracked");
            helper.assertTrue(Quests.get(player).tracked().filter(live.uuid()::equals).isPresent(), "tracked is set");
            helper.assertTrue(QuestLogActions.track(player, new TrackQuestPayload(Optional.empty())), "untrack");
            helper.assertTrue(Quests.get(player).tracked().isEmpty(), "tracking cleared");

            helper.assertTrue(QuestLogActions.remove(player, new RemoveQuestPayload(failing.uuid())), "failed quest removed");
            helper.assertTrue(QuestLogActions.abandon(player, new AbandonQuestPayload(live.uuid())), "live quest abandoned");
            helper.assertTrue(Quests.get(player).active().isEmpty(), "nothing left active");
            helper.assertTrue(Quests.get(player).log().size() == 2, "both ended up in the log");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void deleteHandlerChecksIndexAndRecordTime(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            QuestInstance first = start(player);
            Quests.abandon(player, first.uuid());
            QuestInstance second = start(player);
            Quests.abandon(player, second.uuid());
            long at = Quests.get(player).log().get(0).at();

            helper.assertFalse(QuestLogActions.deleteRecord(player, new DeleteQuestRecordPayload(-1, at)), "negative index");
            helper.assertFalse(QuestLogActions.deleteRecord(player, new DeleteQuestRecordPayload(2, at)), "index past the end");
            helper.assertFalse(
                    QuestLogActions.deleteRecord(player, new DeleteQuestRecordPayload(0, at + 1234)), "wrong record time");
            helper.assertTrue(Quests.get(player).log().size() == 2, "nothing deleted yet");

            helper.assertTrue(QuestLogActions.deleteRecord(player, new DeleteQuestRecordPayload(0, at)), "valid delete");
            helper.assertTrue(Quests.get(player).log().size() == 1, "one record left");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    private static int books(ServerPlayer player) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(QuestItems.QUEST_BOOK.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void questBookIsGivenOnceOnFirstEntryToMiddleEarth(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            helper.assertFalse(QuestBookGiver.giveIfDue(player, Level.OVERWORLD), "not given outside Middle-earth");
            helper.assertTrue(books(player) == 0, "no book yet");
            helper.assertTrue(QuestBookGiver.giveIfDue(player, ModDimensions.MIDDLE_EARTH), "given on entry");
            helper.assertTrue(books(player) == 1, "one book in the inventory");
            helper.assertFalse(QuestBookGiver.giveIfDue(player, ModDimensions.MIDDLE_EARTH), "not given twice");
            helper.assertTrue(books(player) == 1, "still one book");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }
}
