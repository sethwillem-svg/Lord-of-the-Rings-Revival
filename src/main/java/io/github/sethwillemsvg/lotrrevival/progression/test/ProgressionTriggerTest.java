package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionCounters;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.TradeDirection;
import java.util.List;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Alignment, kill and armour triggers plus the registered-only triggers. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressionTriggerTest {
    private static AdvancementHolder advancement(GameTestHelper helper, String id, String trigger, String conditions) {
        String json = "{\"criteria\":{\"c\":{\"trigger\":\"" + trigger + "\",\"conditions\":" + conditions + "}}}";
        RegistryOps<JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        Advancement advancement =
                Advancement.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
        return new AdvancementHolder(LOTRRevival.id(id), advancement);
    }

    private static <T extends CriterionTriggerInstance> void listen(
            PlayerAdvancements advancements, Criterion<T> criterion, AdvancementHolder holder) {
        criterion
                .trigger()
                .addPlayerListener(
                        advancements, new CriterionTrigger.Listener<>(criterion.triggerInstance(), holder, "c"));
    }

    /** Parses and attaches a one-criterion advancement to the player. */
    private static AdvancementHolder attach(
            GameTestHelper helper, ServerPlayer player, String id, String trigger, String conditions) {
        AdvancementHolder holder = advancement(helper, id, trigger, conditions);
        listen(player.getAdvancements(), holder.value().criteria().get("c"), holder);
        return holder;
    }

    private static boolean done(ServerPlayer player, AdvancementHolder holder) {
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static void post(ServerPlayer player, LivingEntity victim, boolean hired) {
        NeoForge.EVENT_BUS.post(new FactionKillEvent(player, victim, Factions.GONDOR, 1.0f, false, hired));
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void alignmentRankCompletesAtThresholdOnly(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            Faction gondor = helper.getLevel()
                    .registryAccess()
                    .registryOrThrow(ModDataRegistries.FACTION)
                    .get(Factions.GONDOR);
            float threshold = gondor.ranks().get(0).threshold();
            AdvancementHolder ranked = attach(
                    helper,
                    player,
                    "test/rank",
                    "lotrrevival:alignment_rank",
                    "{\"faction\":\"lotrrevival:gondor\",\"rank\":\"r1\"}");
            AdvancementHolder friendly = attach(
                    helper,
                    player,
                    "test/rank_none",
                    "lotrrevival:alignment_rank",
                    "{\"faction\":\"lotrrevival:gondor\"}");
            AdvancementHolder otherFaction = attach(
                    helper,
                    player,
                    "test/rank_other",
                    "lotrrevival:alignment_rank",
                    "{\"faction\":\"lotrrevival:mordor\",\"rank\":\"r1\"}");
            AlignmentSource source = AlignmentSource.of(AlignmentSource.COMMAND);
            Alignment.set(player, Factions.GONDOR, -5.0f, source);
            helper.assertFalse(done(player, friendly), "negative alignment does not satisfy a rankless condition");
            Alignment.set(player, Factions.GONDOR, threshold - 1.0f, source);
            helper.assertFalse(done(player, ranked), "below the threshold");
            helper.assertTrue(done(player, friendly), "no rank only needs alignment >= 0");
            Alignment.set(player, Factions.GONDOR, threshold, source);
            helper.assertTrue(done(player, ranked), "at the threshold");
            helper.assertFalse(done(player, otherFaction), "other faction unaffected");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void killFactionNpcMatchesFactionHiredAndCounts(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            AdvancementHolder gondor = attach(
                    helper, player, "test/kill_gondor", "lotrrevival:kill_faction_npc", "{\"faction\":\"lotrrevival:gondor\"}");
            AdvancementHolder mordor = attach(
                    helper, player, "test/kill_mordor", "lotrrevival:kill_faction_npc", "{\"faction\":\"lotrrevival:mordor\"}");
            AdvancementHolder direct = attach(
                    helper,
                    player,
                    "test/kill_direct",
                    "lotrrevival:kill_faction_npc",
                    "{\"faction\":\"lotrrevival:gondor\",\"by_hired\":false}");
            AdvancementHolder hired = attach(
                    helper,
                    player,
                    "test/kill_hired",
                    "lotrrevival:kill_faction_npc",
                    "{\"faction\":\"lotrrevival:gondor\",\"by_hired\":true}");
            AdvancementHolder pigOnly = attach(
                    helper,
                    player,
                    "test/kill_pig",
                    "lotrrevival:kill_faction_npc",
                    "{\"entity\":{\"type\":\"minecraft:cow\"}}");
            LivingEntity victim = EntityType.PIG.create(helper.getLevel());
            post(player, victim, true);
            helper.assertTrue(done(player, gondor), "faction matches");
            helper.assertFalse(done(player, mordor), "other faction rejected");
            helper.assertFalse(done(player, direct), "by_hired false rejects a hired-credit kill");
            helper.assertTrue(done(player, hired), "by_hired true accepts it");
            helper.assertFalse(done(player, pigOnly), "entity predicate rejects a pig");
            helper.assertTrue(ProgressionCounters.killsByFaction(player, Factions.GONDOR.location()) == 1, "counter 1");
            post(player, victim, false);
            helper.assertTrue(done(player, direct), "direct kill accepted");
            helper.assertTrue(ProgressionCounters.killsByFaction(player, Factions.GONDOR.location()) == 2, "counter 2");
            helper.assertTrue(ProgressionCounters.killsByFaction(player, Factions.MORDOR.location()) == 0, "mordor 0");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void killCountsEvenWhenAlignmentCancelled(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        java.util.function.Consumer<FactionKillEvent> canceller = e -> e.setCanceled(true);
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.HIGH, false, FactionKillEvent.class, canceller);
        try {
            AdvancementHolder gondor = attach(
                    helper, player, "test/kill_cancelled", "lotrrevival:kill_faction_npc", "{\"faction\":\"lotrrevival:gondor\"}");
            post(player, EntityType.PIG.create(helper.getLevel()), false);
            helper.assertTrue(done(player, gondor), "cancelled kill still triggers");
            helper.assertTrue(ProgressionCounters.killsByFaction(player, Factions.GONDOR.location()) == 1, "counted");
        } finally {
            NeoForge.EVENT_BUS.unregister(canceller);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void equip(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        ItemStack old = player.getItemBySlot(slot);
        player.setItemSlot(slot, stack);
        NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, slot, old, stack));
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void wearArmorSetNeedsAllFourPieces(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            AdvancementHolder holder = attach(
                    helper, player, "test/wear", "lotrrevival:wear_armor_set", "{\"set\":\"minecraft:trimmable_armor\"}");
            equip(player, EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            equip(player, EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            equip(player, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            helper.assertFalse(done(player, holder), "three pieces do not complete");
            equip(player, EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            helper.assertTrue(done(player, holder), "four pieces complete");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Parses conditions, re-encodes them, and checks the encoded form parses to the same JSON again. */
    private static <T extends CriterionTriggerInstance> void roundTrip(
            GameTestHelper helper, Codec<T> codec, String conditions) {
        RegistryOps<JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        T parsed = codec.parse(ops, JsonParser.parseString(conditions)).getOrThrow();
        JsonElement encoded = codec.encodeStart(ops, parsed).getOrThrow();
        T again = codec.parse(ops, encoded).getOrThrow();
        JsonElement reencoded = codec.encodeStart(ops, again).getOrThrow();
        helper.assertTrue(encoded.equals(reencoded), "round trip of " + conditions + ": " + encoded + " vs " + reencoded);
    }

    @GameTest(template = "empty")
    public static void reservedTriggerCodecsRoundTrip(GameTestHelper helper) {
        roundTrip(
                helper,
                ProgressionTriggers.TRADE.get().codec(),
                "{\"faction\":\"lotrrevival:gondor\",\"trader\":\"#minecraft:skeletons\",\"direction\":\"sell\","
                        + "\"item\":{\"items\":\"minecraft:bread\"}}");
        roundTrip(
                helper,
                ProgressionTriggers.CRAFT_AT_TABLE.get().codec(),
                "{\"table\":\"minecraft:crafting_table\",\"faction\":\"lotrrevival:rohan\",\"item\":{\"items\":\"minecraft:bread\"}}");
        roundTrip(
                helper,
                ProgressionTriggers.HIRE_UNIT.get().codec(),
                "{\"entity\":\"minecraft:pig\",\"faction\":\"lotrrevival:hobbit\"}");
        roundTrip(
                helper,
                ProgressionTriggers.COMPLETE_MINI_QUEST.get().codec(),
                "{\"faction\":\"lotrrevival:bree\",\"group\":\"lotrrevival:bree/errands\","
                        + "\"quest\":\"#lotrrevival:escort\",\"min_total\":5}");
        roundTrip(
                helper,
                ProgressionTriggers.EARN_TITLE.get().codec(),
                "{\"title\":\"lotrrevival:starter/wanderer\",\"count\":3}");
        roundTrip(helper, ProgressionTriggers.TRADE.get().codec(), "{}");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void reservedTriggersAcceptAndReject(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ResourceLocation errands = LOTRRevival.id("bree/errands");
            ResourceLocation courier = LOTRRevival.id("bree/courier");

            AdvancementHolder trade = attach(
                    helper,
                    player,
                    "test/trade",
                    "lotrrevival:trade",
                    "{\"faction\":\"lotrrevival:gondor\",\"trader\":\"minecraft:pig\",\"direction\":\"sell\","
                            + "\"item\":{\"items\":\"minecraft:bread\"}}");
            var tradeTrigger = ProgressionTriggers.TRADE.get();
            tradeTrigger.trigger(player, Factions.GONDOR, EntityType.PIG, TradeDirection.BUY, new ItemStack(Items.BREAD));
            tradeTrigger.trigger(player, Factions.GONDOR, EntityType.COW, TradeDirection.SELL, new ItemStack(Items.BREAD));
            tradeTrigger.trigger(player, Factions.GONDOR, EntityType.PIG, TradeDirection.SELL, new ItemStack(Items.APPLE));
            tradeTrigger.trigger(player, Factions.MORDOR, EntityType.PIG, TradeDirection.SELL, new ItemStack(Items.BREAD));
            helper.assertFalse(done(player, trade), "trade rejects wrong direction, trader, item, faction");
            tradeTrigger.trigger(player, Factions.GONDOR, EntityType.PIG, TradeDirection.SELL, new ItemStack(Items.BREAD));
            helper.assertTrue(done(player, trade), "trade accepts a match");

            AdvancementHolder craft = attach(
                    helper,
                    player,
                    "test/craft",
                    "lotrrevival:craft_at_table",
                    "{\"table\":\"minecraft:crafting_table\",\"faction\":\"lotrrevival:rohan\"}");
            ProgressionTriggers.CRAFT_AT_TABLE.get().trigger(player, Blocks.FURNACE, Factions.ROHAN, new ItemStack(Items.BREAD));
            helper.assertFalse(done(player, craft), "craft rejects wrong table");
            ProgressionTriggers.CRAFT_AT_TABLE
                    .get()
                    .trigger(player, Blocks.CRAFTING_TABLE, Factions.ROHAN, new ItemStack(Items.BREAD));
            helper.assertTrue(done(player, craft), "craft accepts a match");

            AdvancementHolder hire = attach(
                    helper,
                    player,
                    "test/hire",
                    "lotrrevival:hire_unit",
                    "{\"entity\":\"minecraft:pig\",\"faction\":\"lotrrevival:hobbit\"}");
            ProgressionTriggers.HIRE_UNIT.get().trigger(player, EntityType.COW, Factions.HOBBIT);
            helper.assertFalse(done(player, hire), "hire rejects wrong entity");
            ProgressionTriggers.HIRE_UNIT.get().trigger(player, EntityType.PIG, Factions.HOBBIT);
            helper.assertTrue(done(player, hire), "hire accepts a match");

            AdvancementHolder quest = attach(
                    helper,
                    player,
                    "test/quest",
                    "lotrrevival:complete_mini_quest",
                    "{\"faction\":\"lotrrevival:bree\",\"group\":\"lotrrevival:bree/errands\","
                            + "\"quest\":\"#lotrrevival:escort\",\"min_total\":5}");
            var questTrigger = ProgressionTriggers.COMPLETE_MINI_QUEST.get();
            ResourceLocation escortTag = LOTRRevival.id("escort");
            questTrigger.trigger(player, Factions.BREE, errands, courier, List.of(escortTag), 4);
            questTrigger.trigger(player, Factions.BREE, errands, courier, List.of(), 9);
            questTrigger.trigger(player, Factions.BREE, courier, courier, List.of(escortTag), 9);
            helper.assertFalse(done(player, quest), "quest rejects low total, missing tag, wrong group");
            questTrigger.trigger(player, Factions.BREE, errands, courier, List.of(escortTag), 5);
            helper.assertTrue(done(player, quest), "quest accepts a match");

            AdvancementHolder title = attach(
                    helper,
                    player,
                    "test/title",
                    "lotrrevival:earn_title",
                    "{\"title\":\"lotrrevival:starter/wanderer\",\"count\":3}");
            var titleTrigger = ProgressionTriggers.EARN_TITLE.get();
            titleTrigger.trigger(player, LOTRRevival.id("starter/wanderer"), 2);
            titleTrigger.trigger(player, LOTRRevival.id("starter/other"), 9);
            helper.assertFalse(done(player, title), "title rejects low count and wrong title");
            titleTrigger.trigger(player, LOTRRevival.id("starter/wanderer"), 3);
            helper.assertTrue(done(player, title), "title accepts a match");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
