package io.github.sethwillemsvg.lotrrevival.progression.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The wave 1 achievement content (rank, first-kill and general entries) against the real generated data. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AchievementContentTest {
    private static final AlignmentSource COMMAND = AlignmentSource.of(AlignmentSource.COMMAND);

    private static final class Capture implements AchievementService.Sink {
        final List<ResourceLocation> toasts = new ArrayList<>();
        final List<Component> broadcasts = new ArrayList<>();

        @Override
        public void toast(ServerPlayer player, ResourceLocation id) {
            toasts.add(id);
        }

        @Override
        public void broadcast(ServerPlayer player, Component line) {
            broadcasts.add(line);
        }
    }

    private static AdvancementHolder advancement(GameTestHelper helper, String path) {
        AdvancementHolder holder =
                helper.getLevel().getServer().getAdvancements().get(LOTRRevival.id(path));
        helper.assertTrue(holder != null, "advancement " + path + " exists");
        return holder;
    }

    private static boolean done(ServerPlayer player, AdvancementHolder holder) {
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void hobbitRankEntriesFollowAlignment(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        AchievementService.Sink previous = AchievementService.setSink(capture);
        try {
            AdvancementHolder r1 = advancement(helper, "shire/rank_hobbit_r1");
            AdvancementHolder r2 = advancement(helper, "shire/rank_hobbit_r2");
            Alignment.set(player, Factions.HOBBIT, 10.0f, COMMAND);
            helper.assertTrue(done(player, r1), "rank 1 granted at its threshold (10)");
            helper.assertFalse(done(player, r2), "rank 2 not yet granted");
            helper.assertTrue(capture.toasts.contains(r1.id()), "rank 1 toasted: " + capture.toasts);
            Alignment.set(player, Factions.HOBBIT, 100.0f, COMMAND);
            helper.assertTrue(done(player, r2), "rank 2 granted at its threshold (100)");
        } finally {
            AchievementService.setSink(previous);
            AchievementService.forget(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void killHobbitToastsWithoutBroadcast(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        AchievementService.Sink previous = AchievementService.setSink(capture);
        try {
            AdvancementHolder kill = advancement(helper, "shire/kill_hobbit");
            Alignment.set(player, Factions.HOBBIT, -5.0f, COMMAND);
            player.getAdvancements().award(kill, "c");
            helper.assertTrue(capture.toasts.contains(kill.id()), "toast sent: " + capture.toasts);
            helper.assertTrue(capture.broadcasts.isEmpty(), "no chat broadcast for the hobbit kill");
        } finally {
            AchievementService.setSink(previous);
            AchievementService.forget(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void crossFactionEntriesCombineCriteria(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        AchievementService.Sink previous = AchievementService.setSink(capture);
        try {
            AdvancementHolder anyRank = advancement(helper, "general/first_standing");
            AdvancementHolder anyPledge = advancement(helper, "general/first_pledge_rank");
            AdvancementHolder free = advancement(helper, "general/free_peoples_friend");
            Alignment.set(player, Factions.WOOD_ELF, 50.0f, COMMAND);
            helper.assertTrue(done(player, anyRank), "first rank with any faction (any-of)");
            helper.assertFalse(done(player, anyPledge), "wood elf pledge rank (r2) not reached at 50");
            Alignment.set(player, Factions.WOOD_ELF, 100.0f, COMMAND);
            helper.assertTrue(done(player, anyPledge), "pledge rank with any faction (any-of)");

            Alignment.set(player, Factions.HOBBIT, 10.0f, COMMAND);
            Alignment.set(player, Factions.HIGH_ELF, 10.0f, COMMAND);
            Alignment.set(player, Factions.DURINS_FOLK, 10.0f, COMMAND);
            Alignment.set(player, Factions.ROHAN, 10.0f, COMMAND);
            helper.assertFalse(done(player, free), "all-of needs every faction");
            Alignment.set(player, Factions.GONDOR, 10.0f, COMMAND);
            helper.assertTrue(done(player, free), "all-of complete");
        } finally {
            AchievementService.setSink(previous);
            AchievementService.forget(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void contentShape(GameTestHelper helper) {
        var registry = helper.getLevel()
                .registryAccess()
                .registryOrThrow(AchievementEntry.REGISTRY);
        int rank = 0;
        int kill = 0;
        for (ResourceLocation id : registry.keySet()) {
            String path = id.getPath();
            if (path.contains("/rank_")) {
                rank++;
                helper.assertTrue(registry.get(id).rank().isPresent(), id + " carries a rank");
            } else if (path.contains("/kill_")) {
                kill++;
                helper.assertFalse(registry.get(id).requires().isEmpty(), id + " is gated on enemies");
            }
        }
        helper.assertTrue(rank == 176, "176 rank entries, got " + rank);
        helper.assertTrue(kill == 24, "24 first-kill entries, got " + kill);
        helper.assertTrue(registry.containsKey(LOTRRevival.id("shire/kill_hobbit")), "kill_hobbit exists");
        helper.assertTrue(registry.containsKey(LOTRRevival.id("south_east/rank_half_troll_r7")), "last ladder rank");
        helper.succeed();
    }
}
