package io.github.sethwillemsvg.lotrrevival.progression.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.client.progression.menu.AchievementPageModel;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The page order of the Middle-earth menu's Achievements page (plain data, no screen needed). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MenuModelTest {
    private static AchievementPageModel.Row row(String path, String title, AchievementEntry.SortGroup group) {
        AchievementEntry entry = new AchievementEntry(
                AchievementTab.SHIRE,
                new ItemStack(Items.APPLE),
                Component.literal(title),
                Component.literal("desc"),
                group,
                List.of(),
                Optional.empty(),
                true);
        return new AchievementPageModel.Row(LOTRRevival.id("shire/" + path), entry, false);
    }

    @GameTest(template = "empty")
    public static void achievementPageOrder(GameTestHelper helper) {
        List<AchievementPageModel.Row> rows = new ArrayList<>(List.of(
                row("z_other", "Zebra", AchievementEntry.SortGroup.OTHER),
                row("b_biome", "Bog", AchievementEntry.SortGroup.BIOME),
                row("a_other", "apple", AchievementEntry.SortGroup.OTHER),
                row("y_special", "Aardvark", AchievementEntry.SortGroup.SPECIAL),
                row("a_biome", "Meadow", AchievementEntry.SortGroup.BIOME),
                row("x_special", "Zenith", AchievementEntry.SortGroup.SPECIAL)));
        AchievementPageModel.sort(rows);
        List<String> order = rows.stream().map(row -> row.id().getPath()).toList();
        List<String> expected = List.of(
                "shire/x_special", "shire/y_special", "shire/b_biome", "shire/a_biome", "shire/a_other", "shire/z_other");
        helper.assertTrue(order.equals(expected), "page order was " + order);
        helper.succeed();
    }
}
