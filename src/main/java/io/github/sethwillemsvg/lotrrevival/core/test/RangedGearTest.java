package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearCatalog;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearEntry;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.RangedGear;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.SlingItem;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.PebbleProjectile;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

/** Faction bows, the hobbit sling and the pebble (ticket #120). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RangedGearTest {
    /** Total use duration of a bow or sling; the remaining time counts down from here. */
    private static final int USE_DURATION = 72000;

    private RangedGearTest() {}

    private static AABB around(GameTestHelper helper, ServerPlayer player) {
        return new AABB(player.blockPosition()).inflate(6);
    }

    /** Draws {@code bow} for {@code ticks} and returns the base damage of the one arrow it shot. */
    private static double shotBaseDamage(GameTestHelper helper, ServerPlayer player, Item bow, int ticks) {
        ItemStack stack = new ItemStack(bow);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getInventory().add(new ItemStack(Items.ARROW, 4));
        List<AbstractArrow> before = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, around(helper, player));
        bow.releaseUsing(stack, helper.getLevel(), player, USE_DURATION - ticks);
        List<AbstractArrow> arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, around(helper, player));
        arrows.removeAll(before);
        helper.assertTrue(arrows.size() == 1, "one arrow shot by " + bow + " after " + ticks + " ticks, got " + arrows.size());
        double damage = arrows.get(0).getBaseDamage();
        arrows.forEach(AbstractArrow::discard);
        return damage;
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void factionBowsScaleArrowBaseDamage(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.moveTo(helper.absoluteVec(new Vec3(2, 2, 2)));
            double vanilla = shotBaseDamage(helper, player, Items.BOW, 20);
            double ranger = shotBaseDamage(helper, player, RangedGear.RANGER_BOW.get(), 20);
            double gondor = shotBaseDamage(helper, player, RangedGear.GONDOR_BOW.get(), 20);
            double mordor = shotBaseDamage(helper, player, RangedGear.MORDOR_BOW.get(), 20);
            helper.assertTrue(Math.abs(vanilla - 2.0) < 1e-6, "vanilla arrow base damage " + vanilla);
            helper.assertTrue(Math.abs(ranger - vanilla) < 1e-6, "ranger bow equals vanilla, was " + ranger);
            helper.assertTrue(Math.abs(gondor - vanilla * 1.125) < 1e-6, "gondor bow is x1.125, was " + gondor);
            helper.assertTrue(Math.abs(mordor - vanilla * 1.125) < 1e-6, "mordor bow is x1.125, was " + mordor);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void factionBowsMatchTheSpec(GameTestHelper helper) {
        for (DeferredItem<Item> bow : List.of(RangedGear.RANGER_BOW, RangedGear.GONDOR_BOW, RangedGear.MORDOR_BOW)) {
            ItemStack stack = new ItemStack(bow.get());
            helper.assertTrue(stack.getMaxDamage() == 384, bow.getId() + " durability " + stack.getMaxDamage());
            helper.assertTrue(stack.is(ModTags.Items.WEAPONS_BOWS), bow.getId() + " is in weapons/bows");
        }
        helper.assertTrue(new ItemStack(RangedGear.SLING.get()).getMaxDamage() == 64, "sling durability 64");
        helper.assertTrue(new ItemStack(RangedGear.SLING.get()).is(ModTags.Items.WEAPONS_SLINGS), "sling in weapons/slings");
        helper.assertTrue(new ItemStack(RangedGear.PEBBLE.get()).getMaxStackSize() == 64, "pebble stacks to 64");
        helper.assertTrue(new ItemStack(RangedGear.PEBBLE.get()).is(ModTags.Items.PEBBLES), "pebble in pebbles tag");
        long catalogued = GearCatalog.entries().stream()
                .map(GearEntry::id)
                .filter(id -> id.equals("pebble") || id.equals("sling") || id.endsWith("_bow"))
                .count();
        helper.assertTrue(catalogued == 5, "bows, sling and pebble are in the gear catalog, found " + catalogued);
        helper.succeed();
    }

    private static List<PebbleProjectile> pebbles(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(PebbleProjectile.class, around(helper, player));
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void slingThrowsScaledPebblesAndNothingBelowMinimum(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.moveTo(helper.absoluteVec(new Vec3(2, 2, 2)));
            ItemStack sling = new ItemStack(RangedGear.SLING.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, sling);
            player.getInventory().add(new ItemStack(RangedGear.PEBBLE.get(), 8));
            SlingItem item = (SlingItem) sling.getItem();

            item.releaseUsing(sling, helper.getLevel(), player, USE_DURATION - 3);
            helper.assertTrue(pebbles(helper, player).isEmpty(), "under 4 ticks spawns nothing");

            item.releaseUsing(sling, helper.getLevel(), player, USE_DURATION - 10);
            List<PebbleProjectile> full = pebbles(helper, player);
            helper.assertTrue(full.size() == 1, "10 ticks spawns one pebble, got " + full.size());
            PebbleProjectile pebble = full.get(0);
            helper.assertTrue(Math.abs(pebble.damage() - 2.0) < 1e-6, "full wind-up damage " + pebble.damage());
            double speed = pebble.getDeltaMovement().length();
            helper.assertTrue(Math.abs(speed - 1.5) < 0.1, "full wind-up velocity " + speed);
            pebble.discard();

            item.releaseUsing(sling, helper.getLevel(), player, USE_DURATION - 25);
            List<PebbleProjectile> over = pebbles(helper, player);
            helper.assertTrue(over.size() == 1 && Math.abs(over.get(0).damage() - 2.0) < 1e-6, "wind-up is capped at 10 ticks");
            over.forEach(PebbleProjectile::discard);

            item.releaseUsing(sling, helper.getLevel(), player, USE_DURATION - 5);
            List<PebbleProjectile> half = pebbles(helper, player);
            helper.assertTrue(half.size() == 1 && Math.abs(half.get(0).damage() - 1.0) < 1e-6, "5 ticks deals 1.0");
            half.forEach(PebbleProjectile::discard);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullWindUpPebbleDealsTwoAndBreaksOnImpact(GameTestHelper helper) {
        Pig shooter = helper.spawn(EntityType.PIG, 2, 2, 2);
        shooter.setNoAi(true);
        Pig target = helper.spawn(EntityType.PIG, 2, 2, 5);
        target.setNoAi(true);
        PebbleProjectile pebble = new PebbleProjectile(helper.getLevel(), shooter);
        pebble.shootFromRotation(shooter, 0.0F, 0.0F, 0.0F, SlingItem.FULL_VELOCITY, 0.0F);
        helper.getLevel().addFreshEntity(pebble);
        float before = target.getHealth();
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(
                        Math.abs(before - target.getHealth() - 2.0F) < 1e-4,
                        "pebble deals 2.0, health " + before + " -> " + target.getHealth());
                helper.assertTrue(pebble.isRemoved(), "the pebble breaks on impact");
                helper.succeed();
            } finally {
                pebble.discard();
                shooter.discard();
                target.discard();
            }
        });
    }
}
