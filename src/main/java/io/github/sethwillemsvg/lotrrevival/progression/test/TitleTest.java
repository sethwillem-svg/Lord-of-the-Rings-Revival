package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.alignment.PledgeBreakReason;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntries;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.github.sethwillemsvg.lotrrevival.progression.network.SelectTitlePayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.TitlesDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.TitlesFullPayload;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleCache;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleData;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleDefinition;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleDefinitions;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleService;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleSource;
import io.github.sethwillemsvg.lotrrevival.progression.title.Titles;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Titles: usability, selection, re-check, attachment, chat prefix, cache and payloads. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TitleTest {
    private static final AlignmentSource COMMAND = AlignmentSource.of(AlignmentSource.COMMAND);
    private static final ResourceLocation STARTER = LOTRRevival.id("test/title_starter");
    private static final ResourceLocation SPECIAL = LOTRRevival.id("test/title_special");
    private static final ResourceLocation RANK_PLEDGE = LOTRRevival.id("test/title_rank_pledge");
    private static final ResourceLocation RANK_ABOVE = LOTRRevival.id("test/title_rank_above");
    private static final ResourceLocation ALIGNED = LOTRRevival.id("test/title_aligned");
    private static final ResourceLocation ACHIEVEMENT = LOTRRevival.id("test/title_achievement");
    private static final ResourceLocation BACKING = LOTRRevival.id("test/title_backing");

    /** What the service produced, captured instead of sent or fired. */
    private static final class Capture implements TitleService.Sink {
        final List<Component> notices = new ArrayList<>();
        final List<ResourceLocation> earned = new ArrayList<>();

        @Override
        public void lost(ServerPlayer player, Component notice) {
            notices.add(notice);
        }

        @Override
        public void earned(ServerPlayer player, ResourceLocation title, int usableCount) {
            earned.add(title);
        }

        long earnedCount(ResourceLocation id) {
            return earned.stream().filter(id::equals).count();
        }
    }

    private static TitleDefinition def(String text, TitleSource source) {
        return new TitleDefinition(Component.literal(text), Optional.empty(), source, false);
    }

    private static Map<ResourceLocation, TitleDefinition> definitions() {
        return Map.of(
                STARTER, def("Test Starter", TitleSource.Starter.INSTANCE),
                SPECIAL, def("Test Special", TitleSource.Special.INSTANCE),
                // Hobbit ladder: r2 (100) is the pledge rank, r3 (250) is above it.
                RANK_PLEDGE, def("Test Pledge Rank", new TitleSource.AlignmentRank(Factions.HOBBIT, "r2")),
                RANK_ABOVE,
                        new TitleDefinition(
                                Component.literal("Test Above Rank"),
                                Optional.of(Component.literal("Test Above Rank F")),
                                new TitleSource.AlignmentRank(Factions.HOBBIT, "r3"),
                                false),
                ALIGNED, def("Test Aligned", new TitleSource.FactionAlignment(List.of(Factions.HOBBIT, Factions.GONDOR), Optional.of(50.0f))),
                ACHIEVEMENT, def("Test Achievement", new TitleSource.AchievementSource(BACKING)));
    }

    private static AchievementEntry openEntry() {
        return new AchievementEntry(
                AchievementTab.GENERAL,
                new ItemStack(Items.BOW),
                Component.literal("Backing"),
                Component.literal("Backing description"),
                AchievementEntry.SortGroup.OTHER,
                List.of(),
                Optional.empty(),
                true);
    }

    private static AdvancementHolder impossible(GameTestHelper helper, ResourceLocation id) {
        JsonElement json = JsonParser.parseString("{\"criteria\":{\"c\":{\"trigger\":\"minecraft:impossible\"}}}");
        RegistryOps<JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        return new AdvancementHolder(id, Advancement.CODEC.parse(ops, json).getOrThrow());
    }

    private static void flush(ServerPlayer player) {
        ProgressionSync.flush(List.of(player));
    }

    private static TitleData data(ServerPlayer player) {
        return player.getData(ProgressionAttachments.TITLES);
    }

    private static void cleanUp(GameTestHelper helper, ServerPlayer player, TitleService.Sink previous) {
        TitleService.setSink(previous);
        TitleDefinitions.clearOverrides();
        AchievementEntries.clearOverrides();
        TitleService.forget(player);
        AchievementService.forget(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void rankTitlesFollowRankAndPledgeAndClearOnDrop(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        TitleService.Sink previous = TitleService.setSink(capture);
        try {
            TitleDefinitions.withOverrides(definitions());
            helper.assertFalse(Titles.isUsable(player, RANK_PLEDGE), "below the rank");
            Alignment.set(player, Factions.HOBBIT, 300.0f, COMMAND);
            helper.assertTrue(Titles.isUsable(player, RANK_PLEDGE), "crossing the pledge rank makes it usable");
            helper.assertFalse(Titles.isUsable(player, RANK_ABOVE), "above the pledge rank needs a pledge");
            helper.assertTrue(Titles.isUsable(player, ALIGNED), "alignment title: any listed faction reaches the minimum");

            Alignment.tryPledge(player, Factions.HOBBIT, true);
            helper.assertTrue(Titles.isUsable(player, RANK_ABOVE), "usable with the pledge");
            helper.assertTrue(
                    TitleService.select(player, Optional.of(RANK_ABOVE), "gold", true), "selecting the pledged rank title");
            flush(player);
            helper.assertTrue(data(player).selected().equals(Optional.of(RANK_ABOVE)), "selection kept");
            helper.assertTrue(capture.notices.isEmpty(), "no notice while everything holds");

            Alignment.breakPledge(player, PledgeBreakReason.COMMAND);
            flush(player);
            helper.assertTrue(data(player).selected().isEmpty(), "breaking the pledge clears the rank title");
            helper.assertTrue(capture.notices.size() == 1, "one notice: " + capture.notices);
            String notice = capture.notices.get(0).getString();
            helper.assertTrue(notice.contains("Test Above Rank F"), "notice names the lost title: " + notice);

            helper.assertTrue(TitleService.select(player, Optional.of(RANK_PLEDGE), "white", false), "select the pledge rank");
            Alignment.set(player, Factions.HOBBIT, 20.0f, COMMAND);
            flush(player);
            helper.assertTrue(data(player).selected().isEmpty(), "dropping below the rank clears the selection");
            helper.assertTrue(capture.notices.size() == 2, "second notice");
            helper.assertTrue(capture.notices.get(1).getString().contains("Test Pledge Rank"), "names the second title");
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void selectRejectsBadColoursUnusableTitlesAndFlooding(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TitleService.Sink previous = TitleService.setSink(new Capture());
        try {
            TitleDefinitions.withOverrides(definitions());
            helper.assertFalse(TitleService.select(player, Optional.of(STARTER), "bold", false), "a format is not a colour");
            helper.assertFalse(TitleService.select(player, Optional.of(STARTER), "reset", false), "reset is not a colour");
            helper.assertFalse(TitleService.select(player, Optional.of(STARTER), "teal", false), "unknown colour");
            helper.assertFalse(TitleService.select(player, Optional.of(SPECIAL), "red", false), "special title not granted");
            helper.assertFalse(TitleService.select(player, Optional.of(RANK_ABOVE), "red", false), "rank not reached");
            helper.assertFalse(
                    TitleService.select(player, Optional.of(LOTRRevival.id("test/missing")), "red", false), "unknown title");
            TitleData untouched = data(player);
            helper.assertTrue(
                    untouched.selected().isEmpty()
                            && untouched.color() == ChatFormatting.WHITE
                            && !untouched.feminineRanks()
                            && untouched.granted().isEmpty(),
                    "rejected requests change nothing: " + untouched);

            helper.assertTrue(TitleService.select(player, Optional.of(STARTER), "dark_red", true), "valid request");
            TitleData data = data(player);
            helper.assertTrue(data.selected().equals(Optional.of(STARTER)), "title stored");
            helper.assertTrue(data.color() == ChatFormatting.DARK_RED, "colour stored");
            helper.assertTrue(data.feminineRanks(), "feminine flag stored");

            helper.assertTrue(TitleService.select(player, Optional.empty(), "white", false), "choosing none is allowed");
            helper.assertTrue(data(player).selected().isEmpty(), "selection cleared");

            // 3 requests so far in this tick window; the 20 per second limit drops the rest.
            int accepted = 0;
            for (int i = 0; i < 30; i++) {
                if (TitleService.select(player, Optional.of(STARTER), i % 2 == 0 ? "red" : "blue", false)) {
                    accepted++;
                }
            }
            helper.assertTrue(accepted > 0 && accepted < 30, "flooding is cut off: " + accepted);
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void revokingTheBackingAdvancementClearsAnAchievementTitle(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        TitleService.Sink previous = TitleService.setSink(capture);
        try {
            TitleDefinitions.withOverrides(definitions());
            AchievementEntries.withOverrides(Map.of(BACKING, openEntry()));
            AdvancementHolder holder = impossible(helper, BACKING);
            helper.assertFalse(Titles.isUsable(player, ACHIEVEMENT), "not earned yet");
            player.getAdvancements().award(holder, "c");
            helper.assertTrue(Titles.isUsable(player, ACHIEVEMENT), "earned, eligible and counted");
            helper.assertTrue(TitleService.select(player, Optional.of(ACHIEVEMENT), "green", false), "select it");
            flush(player);
            helper.assertTrue(data(player).selected().equals(Optional.of(ACHIEVEMENT)), "kept while earned");

            player.getAdvancements().revoke(holder, "c");
            flush(player);
            helper.assertTrue(data(player).selected().isEmpty(), "revoking clears the selection");
            helper.assertTrue(capture.notices.size() == 1, "with a notice");
            helper.assertTrue(capture.notices.get(0).getString().contains("Test Achievement"), "naming the title");
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void grantedTitlesNeedGrantAndEarnTitleFiresOncePerTitle(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        TitleService.Sink previous = TitleService.setSink(capture);
        try {
            TitleDefinitions.withOverrides(definitions());
            flush(player);
            ProgressionSync.markDirty(player, ProgressionSync.Type.TITLES);
            flush(player);
            helper.assertTrue(capture.earnedCount(STARTER) == 1, "starter fired once across two checks");
            helper.assertTrue(capture.earnedCount(SPECIAL) == 0, "special not usable yet");

            helper.assertTrue(TitleService.grant(player, SPECIAL), "grant");
            helper.assertFalse(TitleService.grant(player, SPECIAL), "granting twice does nothing");
            flush(player);
            helper.assertTrue(Titles.isUsable(player, SPECIAL), "granted special title is usable");
            helper.assertTrue(capture.earnedCount(SPECIAL) == 1, "fires once when granted");

            helper.assertTrue(TitleService.revoke(player, SPECIAL), "revoke");
            flush(player);
            helper.assertFalse(Titles.isUsable(player, SPECIAL), "no longer usable");
            TitleService.grant(player, SPECIAL);
            flush(player);
            helper.assertTrue(capture.earnedCount(SPECIAL) == 1, "granting again does not fire again");
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void titlesSurviveDeathUnchanged(GameTestHelper helper) {
        ServerPlayer old = helper.makeMockServerPlayerInLevel();
        ServerPlayer copy = helper.makeMockServerPlayerInLevel();
        try {
            ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("removed_pack", "lost_title");
            TitleData value = new TitleData(
                    Set.of(SPECIAL, unknown),
                    Optional.of(STARTER),
                    ChatFormatting.AQUA,
                    true,
                    Set.of(STARTER, SPECIAL));
            old.setData(ProgressionAttachments.TITLES, value);
            copy.copyAttachmentsFrom(old, true);
            TitleData actual = copy.getData(ProgressionAttachments.TITLES);
            helper.assertTrue(actual.equals(value), "copied on death: " + actual);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(old);
            helper.getLevel().getServer().getPlayerList().remove(copy);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void chatLineShowsTheColouredPrefixOnlyWhileSelected(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TitleService.Sink previous = TitleService.setSink(new Capture());
        try {
            TitleDefinitions.withOverrides(definitions());
            Component message = Component.literal("hello");
            helper.assertTrue(TitleService.chatLine(player, message).isEmpty(), "no title, vanilla chat");
            TitleService.select(player, Optional.of(STARTER), "gold", false);
            Component line = TitleService.chatLine(player, message).orElseThrow();
            String text = line.getString();
            helper.assertTrue(
                    text.equals("[Test Starter] <" + player.getDisplayName().getString() + "> hello"), "format: " + text);
            helper.assertTrue(
                    line.getSiblings().get(0).getStyle().getColor().getValue()
                            == ChatFormatting.GOLD.getColor().intValue(),
                    "bracketed title uses the chosen colour");
            helper.assertTrue(
                    line.getSiblings().get(2).getStyle().getColor() == null, "the name and message keep their colours");
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void titleCacheFollowsTheSelection(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TitleService.Sink previous = TitleService.setSink(new Capture());
        try {
            TitleDefinitions.withOverrides(definitions());
            TitleCache cache = TitleCache.get(helper.getLevel().getServer());
            TitleService.select(player, Optional.of(STARTER), "light_purple", true);
            TitleCache.Entry entry = cache.get(player.getUUID()).orElseThrow();
            helper.assertTrue(entry.title().equals(STARTER), "cached title");
            helper.assertTrue(entry.color() == ChatFormatting.LIGHT_PURPLE, "cached colour");
            helper.assertTrue(entry.feminine(), "cached flag");
            TitleService.select(player, Optional.empty(), "white", false);
            helper.assertTrue(cache.get(player.getUUID()).isEmpty(), "cleared with the selection");
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void definitionCodecHandlesEverySourceType(GameTestHelper helper) {
        String[] sources = {
            "{\"type\":\"starter\"}",
            "{\"type\":\"special\"}",
            "{\"type\":\"alignment\",\"factions\":[\"lotrrevival:hobbit\",\"lotrrevival:bree\"],\"min\":\"pledge\"}",
            "{\"type\":\"alignment\",\"factions\":[\"lotrrevival:gondor\"],\"min\":100.0}",
            "{\"type\":\"achievement\",\"achievement\":\"lotrrevival:general/enter_middle_earth\"}",
            "{\"type\":\"alignment_rank\",\"faction\":\"lotrrevival:hobbit\",\"rank\":\"r3\"}"
        };
        for (String source : sources) {
            String json = "{\"text\":\"Some Title\",\"text_feminine\":\"Some Titless\",\"source\":" + source + "}";
            TitleDefinition parsed = TitleDefinition.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .getOrThrow();
            helper.assertTrue(parsed.displayText(true).getString().equals("Some Titless"), "feminine text: " + source);
            helper.assertTrue(parsed.displayText(false).getString().equals("Some Title"), "plain text: " + source);
            JsonElement back = TitleDefinition.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow();
            TitleDefinition again =
                    TitleDefinition.CODEC.parse(JsonOps.INSTANCE, back).getOrThrow();
            helper.assertTrue(again.equals(parsed), "round trip: " + source);
        }
        helper.assertTrue(
                TitleDefinition.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(
                                "{\"text\":\"x\",\"source\":{\"type\":\"alignment\",\"factions\":[\"lotrrevival:hobbit\"],\"min\":\"later\"}}"))
                        .result()
                        .isEmpty(),
                "min must be a number or pledge");
        helper.assertTrue(
                TitleData.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"color\":\"bold\"}"))
                        .result()
                        .isEmpty(),
                "attachment rejects a non-colour");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void titlePayloadsRoundTrip(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TitlesFullPayload full = new TitlesFullPayload(true, false, Optional.of(STARTER), "gold", true, List.of(SPECIAL));
        TitlesFullPayload.STREAM_CODEC.encode(buf, full);
        helper.assertTrue(TitlesFullPayload.STREAM_CODEC.decode(buf).equals(full), "titles_full");
        TitlesDeltaPayload delta = new TitlesDeltaPayload(Optional.empty(), "white", false, List.of(SPECIAL), List.of(STARTER));
        TitlesDeltaPayload.STREAM_CODEC.encode(buf, delta);
        helper.assertTrue(TitlesDeltaPayload.STREAM_CODEC.decode(buf).equals(delta), "titles_delta");
        SelectTitlePayload select = new SelectTitlePayload(Optional.of(STARTER), "red", true);
        SelectTitlePayload.STREAM_CODEC.encode(buf, select);
        helper.assertTrue(SelectTitlePayload.STREAM_CODEC.decode(buf).equals(select), "select_title");
        helper.succeed();
    }
}
