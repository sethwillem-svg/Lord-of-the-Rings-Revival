package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.CoreKeys;
import io.github.sethwillemsvg.lotrrevival.core.api.AchievementCounters;
import io.github.sethwillemsvg.lotrrevival.core.item.CoreItems;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import com.mojang.serialization.Codec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Datapack registry keys and hook, achievement counter default, coin item and tags. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreHooksTest {
    @GameTest(template = "empty")
    public static void reservedKeysAndFrozenHook(GameTestHelper helper) {
        helper.assertTrue(
                ModDataRegistries.achievement().location().equals(LOTRRevival.id("achievement")), "achievement key");
        helper.assertTrue(ModDataRegistries.title().location().equals(LOTRRevival.id("title")), "title key");
        helper.assertTrue(
                ModDataRegistries.questGroup().location().equals(LOTRRevival.id("quest_group")), "quest_group key");
        helper.assertTrue(
                ModDataRegistries.miniQuest().location().equals(LOTRRevival.id("mini_quest")), "mini_quest key");
        // The registry event has already fired, so late registration must fail loudly.
        boolean threw = false;
        try {
            ModDataRegistries.registerSynced(ModDataRegistries.<String>achievement(), Codec.STRING);
        } catch (IllegalStateException expected) {
            threw = true;
        }
        helper.assertTrue(threw, "late registerSynced should throw");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void achievementCounterDefaultsToZero(GameTestHelper helper) {
        // The default counter ignores its argument, so no mock player is needed.
        Player player = null;
        helper.assertTrue(AchievementCounters.count(player) == 0, "default count should be 0");
        boolean threw = false;
        try {
            AchievementCounters.register(null);
        } catch (NullPointerException expected) {
            threw = true;
        }
        helper.assertTrue(threw, "null counter rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void silverCoinAndTags(GameTestHelper helper) {
        helper.assertTrue(CoreItems.SILVER_COIN.get().getDefaultInstance().is(ModTags.Items.COINS), "coin not in #coins");
        helper.assertTrue(ModTags.EntityTypes.NPCS.location().equals(LOTRRevival.id("npcs")), "npcs tag key");
        helper.assertTrue(CoreKeys.CATEGORY.equals("key.categories.lotrrevival"), "key category");
        helper.succeed();
    }
}
