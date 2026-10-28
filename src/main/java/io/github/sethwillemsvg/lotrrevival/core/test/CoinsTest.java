package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.coin.CoinItems;
import io.github.sethwillemsvg.lotrrevival.core.coin.Coins;
import io.github.sethwillemsvg.lotrrevival.core.item.CoreItems;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Coin denominations and the server-side coin API. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoinsTest {
    private static int tally(Inventory inv, Item item) {
        int n = 0;
        for (ItemStack s : inv.items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int droppedValue(ServerPlayer p) {
        int total = 0;
        for (ItemEntity e : p.level().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(2.5))) {
            total += Coins.value(e.getItem());
        }
        return total;
    }

    private static void clearDropped(ServerPlayer p) {
        for (ItemEntity e : p.level().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(2.5))) {
            e.discard();
        }
    }

    @GameTest(template = "empty")
    public static void denominationsAndSplit(GameTestHelper helper) {
        helper.assertTrue(CoinItems.SILVER_COIN_10.get().getDefaultInstance().is(ModTags.Items.COINS), "10 not in #coins");
        helper.assertTrue(CoinItems.SILVER_COIN_100.get().getDefaultInstance().is(ModTags.Items.COINS), "100 not in #coins");
        helper.assertTrue(CoinItems.SILVER_COIN_100.get().getDefaultMaxStackSize() == 64, "stack size 64");
        helper.assertTrue(Coins.value(new ItemStack(CoinItems.SILVER_COIN_10.get(), 3)) == 30, "3 x 10 is 30");
        helper.assertTrue(Coins.value(new ItemStack(Items.DIRT, 5)) == 0, "dirt is worth 0");
        List<ItemStack> split = Coins.split(137);
        helper.assertTrue(split.size() == 3, "137 splits into 3 stacks, was " + split.size());
        helper.assertTrue(split.get(0).is(CoinItems.SILVER_COIN_100.get()) && split.get(0).getCount() == 1, "1 x 100");
        helper.assertTrue(split.get(1).is(CoinItems.SILVER_COIN_10.get()) && split.get(1).getCount() == 3, "3 x 10");
        helper.assertTrue(split.get(2).is(CoreItems.SILVER_COIN.get()) && split.get(2).getCount() == 7, "7 x 1");
        int total = 0;
        for (ItemStack s : Coins.split(100 * 70)) {
            helper.assertTrue(s.getCount() <= 64, "stack over 64");
            total += Coins.value(s);
        }
        helper.assertTrue(total == 7000, "big split keeps the value, was " + total);
        helper.assertTrue(Coins.split(0).isEmpty(), "0 splits into nothing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void payGivesChange(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        try {
            p.getInventory().add(new ItemStack(CoinItems.SILVER_COIN_100.get()));
            helper.assertTrue(Coins.count(p) == 100, "count 100");
            helper.assertTrue(Coins.pay(p, 40), "pay 40 should succeed");
            helper.assertTrue(tally(p.getInventory(), CoinItems.SILVER_COIN_100.get()) == 0, "100 coin consumed");
            helper.assertTrue(tally(p.getInventory(), CoinItems.SILVER_COIN_10.get()) == 6, "6 x 10 change");
            helper.assertTrue(Coins.count(p) == 60, "60 left");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void payIsAllOrNothingAndLowestFirst(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        try {
            p.getInventory().add(new ItemStack(CoinItems.SILVER_COIN_10.get(), 4));
            helper.assertTrue(!Coins.pay(p, 41), "pay 41 with 40 must fail");
            helper.assertTrue(Coins.count(p) == 40, "nothing removed");
            helper.assertTrue(tally(p.getInventory(), CoinItems.SILVER_COIN_10.get()) == 4, "tens untouched");
            helper.assertTrue(Coins.pay(p, 0), "paying 0 succeeds");

            // 1s go first, then 10s: paying 12 from 5 x 1 and 2 x 10 uses all 1s and one 10, giving 3 back as 3 x 1.
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(CoreItems.SILVER_COIN.get(), 5));
            p.getInventory().add(new ItemStack(CoinItems.SILVER_COIN_10.get(), 2));
            helper.assertTrue(Coins.pay(p, 12), "pay 12");
            helper.assertTrue(tally(p.getInventory(), CoinItems.SILVER_COIN_10.get()) == 1, "one 10 left");
            helper.assertTrue(tally(p.getInventory(), CoreItems.SILVER_COIN.get()) == 3, "3 x 1 left");
            helper.assertTrue(Coins.count(p) == 13, "13 left");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void giveSplitsAndDropsOverflow(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        try {
            clearDropped(p);
            Coins.give(p, 137);
            helper.assertTrue(tally(p.getInventory(), CoinItems.SILVER_COIN_100.get()) == 1, "1 x 100");
            helper.assertTrue(tally(p.getInventory(), CoinItems.SILVER_COIN_10.get()) == 3, "3 x 10");
            helper.assertTrue(tally(p.getInventory(), CoreItems.SILVER_COIN.get()) == 7, "7 x 1");
            helper.assertTrue(droppedValue(p) == 0, "nothing dropped with room");

            // Fill every main slot but one: the first stack (the 100) fits, the rest drop at the feet.
            p.getInventory().clearContent();
            for (int i = 1; i < p.getInventory().items.size(); i++) {
                p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
            Coins.give(p, 137);
            helper.assertTrue(tally(p.getInventory(), CoinItems.SILVER_COIN_100.get()) == 1, "100 fits in the free slot");
            helper.assertTrue(droppedValue(p) == 37, "37 dropped, was " + droppedValue(p));
            clearDropped(p);

            Coins.drop(p.serverLevel(), p.position(), 215);
            helper.assertTrue(droppedValue(p) == 215, "drop spawns the full amount, was " + droppedValue(p));
        } finally {
            clearDropped(p);
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void countIgnoresArmorAndBundles(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        try {
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(CoinItems.SILVER_COIN_100.get(), 5));
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(CoinItems.SILVER_COIN_100.get(), 3))));
            p.getInventory().add(bundle);
            helper.assertTrue(Coins.count(p) == 0, "armor and bundle coins must not count, was " + Coins.count(p));
            helper.assertTrue(!Coins.pay(p, 1), "cannot pay from armor or bundle");

            p.getInventory().add(new ItemStack(CoreItems.SILVER_COIN.get(), 7));
            p.getInventory().offhand.set(0, new ItemStack(CoinItems.SILVER_COIN_10.get(), 2));
            helper.assertTrue(Coins.count(p) == 27, "inventory plus off hand counts, was " + Coins.count(p));
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
        helper.succeed();
    }
}
