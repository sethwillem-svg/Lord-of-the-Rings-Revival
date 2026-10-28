package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrownAxe;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrownGear;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrownPickup;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrownSpear;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrownWeapons;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrowingEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Thrown spears and throwing axes (ticket #124, specs/core/combat-items.md "Thrown weapons"). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThrownWeaponsTest {
    private static final double EPS = 0.01;
    private static final int X = 8;
    private static final int THROWER_Z = 5;
    private static final int TARGET_Z = 15;
    private static final int USE_DURATION = 72000;

    private ThrownWeaponsTest() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LOTRRevival.id(id));
    }

    /** A villager with no AI facing +z, 10 blocks from an iron golem (100 health, no armour) that also has no AI. */
    private record Range(Villager thrower, Mob target) {}

    private static Range range(GameTestHelper helper) {
        Villager thrower = helper.spawn(EntityType.VILLAGER, new BlockPos(X, 0, THROWER_Z));
        thrower.setNoAi(true);
        thrower.setPersistenceRequired();
        thrower.setYRot(0.0f);
        thrower.setXRot(0.0f);
        Mob target = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(X, 0, TARGET_Z));
        target.setNoAi(true);
        return new Range(thrower, target);
    }

    private static <T extends ThrownGear> List<T> flying(GameTestHelper helper, EntityType<T> type) {
        AABB box = helper.getBounds().inflate(8);
        return helper.getLevel().getEntities(type, box, e -> true);
    }

    private static List<ItemEntity> landed(GameTestHelper helper, Item item) {
        AABB box = helper.getBounds().inflate(8);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(item));
    }

    @GameTest(template = "npc_arena")
    public static void damageNumbersFollowTheSpec(GameTestHelper helper) {
        // Bronze spear 4.5 melee: full charge 4.5 x 0.7 x 1.5, half charge 4.5 x 0.7 x 0.5 without the critical.
        helper.assertTrue(
                Math.abs(ThrownWeapons.spearDamage(CombatMaterial.BRONZE, 20) - 4.5 * 0.7 * 1.5) < EPS, "bronze full");
        helper.assertTrue(
                Math.abs(ThrownWeapons.spearDamage(CombatMaterial.BRONZE, 10) - 4.5 * 0.7 * 0.5) < EPS, "bronze half");
        helper.assertTrue(
                Math.abs(ThrownWeapons.spearDamage(CombatMaterial.BRONZE, 40) - 4.5 * 0.7 * 1.5) < EPS, "charge caps at 20");
        helper.assertTrue(Math.abs(ThrownWeapons.axeDamage(CombatMaterial.BRONZE) - 2.75) < EPS, "bronze axe 2.75");
        helper.assertTrue(Math.abs(ThrownWeapons.axeDamage(CombatMaterial.IRON) - 3.0) < EPS, "iron axe 3.0");
        helper.assertTrue(WeaponClass.SPEAR.thrown() && WeaponClass.THROWING_AXE.thrown(), "spear and axe throw");
        helper.assertTrue(
                !WeaponClass.PIKE.thrown() && !WeaponClass.LANCE.thrown() && !WeaponClass.POLEARM.thrown(),
                "pikes, lances and polearms do not throw");
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    public static void fullChargeBronzeSpearHitsAndLandsAsPickup(GameTestHelper helper) {
        Range range = range(helper);
        ItemStack spear = new ItemStack(item("bronze_spear"));
        range.thrower().setItemSlot(EquipmentSlot.MAINHAND, spear);

        spear.getItem().releaseUsing(spear, helper.getLevel(), range.thrower(), USE_DURATION - 20);

        List<ThrownSpear> thrown = flying(helper, ThrowingEntities.THROWN_SPEAR.get());
        helper.assertTrue(thrown.size() == 1, "one spear in flight, found " + thrown.size());
        ThrownSpear projectile = thrown.get(0);
        helper.assertTrue(projectile.isCrit(), "full charge is a critical");
        helper.assertTrue(
                Math.abs(projectile.damage() - 4.5f * 0.7f * 1.5f) < EPS, "thrown damage " + projectile.damage());
        helper.assertTrue(projectile.getItem().getDamageValue() == 1, "spear loses 1 durability");
        helper.assertTrue(spear.isEmpty(), "the thrown spear leaves the hand");

        float before = range.target().getHealth();
        helper.runAfterDelay(40, () -> {
            float dealt = before - range.target().getHealth();
            helper.assertTrue(Math.abs(dealt - 4.5f * 0.7f * 1.5f) < EPS, "target lost " + dealt);
            helper.assertTrue(projectile.isRemoved(), "the projectile turned into a pickup");
            List<ItemEntity> drops = landed(helper, item("bronze_spear"));
            helper.assertTrue(drops.size() == 1, "one dropped spear, found " + drops.size());
            ItemEntity drop = drops.get(0);
            helper.assertTrue(drop.getItem().getDamageValue() == 1, "the dropped spear keeps its wear");
            helper.assertTrue(ThrownPickup.mayCollect(drop, range.thrower()), "the thrower can collect it");
            helper.assertTrue(!ThrownPickup.mayCollect(drop, range.target()), "others cannot yet");
            helper.succeed();
        });
    }

    @GameTest(template = "npc_arena")
    public static void spearReleasedTooEarlySpawnsNothing(GameTestHelper helper) {
        Range range = range(helper);
        ItemStack spear = new ItemStack(item("bronze_spear"));
        range.thrower().setItemSlot(EquipmentSlot.MAINHAND, spear);

        spear.getItem().releaseUsing(spear, helper.getLevel(), range.thrower(), USE_DURATION - 3);
        spear.getItem().releaseUsing(spear, helper.getLevel(), range.thrower(), USE_DURATION - 4);

        helper.assertTrue(flying(helper, ThrowingEntities.THROWN_SPEAR.get()).isEmpty(), "nothing spawned");
        helper.assertTrue(!spear.isEmpty() && spear.getDamageValue() == 0, "the spear is untouched");

        // Five ticks is the first throw that counts.
        spear.getItem().releaseUsing(spear, helper.getLevel(), range.thrower(), USE_DURATION - 5);
        helper.assertTrue(flying(helper, ThrowingEntities.THROWN_SPEAR.get()).size() == 1, "5 ticks throws");
        flying(helper, ThrowingEntities.THROWN_SPEAR.get()).forEach(ThrownGear::discard);
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    public static void ironThrowingAxeHitsAndLandsAsItem(GameTestHelper helper) {
        Range range = range(helper);
        ItemStack axes = new ItemStack(item("iron_throwing_axe"), 3);
        range.thrower().setItemSlot(EquipmentSlot.MAINHAND, axes);

        ThrownAxe axe = ThrownWeapons.throwAxe(helper.getLevel(), range.thrower(), axes, 0.0f);
        helper.assertTrue(axe != null, "the axe was thrown");
        helper.assertTrue(axes.getCount() == 2, "one axe used up, " + axes.getCount() + " left");
        helper.assertTrue(Math.abs(axe.damage() - 3.0f) < EPS, "thrown damage " + axe.damage());
        helper.assertTrue(!axe.isCrit(), "axes do not crit");

        float before = range.target().getHealth();
        helper.runAfterDelay(40, () -> {
            float dealt = before - range.target().getHealth();
            helper.assertTrue(Math.abs(dealt - 3.0f) < EPS, "target lost " + dealt);
            List<ItemEntity> drops = landed(helper, item("iron_throwing_axe"));
            helper.assertTrue(drops.size() == 1, "one dropped axe, found " + drops.size());
            helper.assertTrue(!ThrownPickup.mayCollect(drops.get(0), range.target()), "only the thrower at first");
            helper.succeed();
        });
    }

    @GameTest(template = "npc_arena")
    public static void throwingAxeUseStartsTheCooldown(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack axes = new ItemStack(item("bronze_throwing_axe"), 5);
            player.setItemInHand(InteractionHand.MAIN_HAND, axes);
            net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(X + 0.5, 0.0, 8.5));
            player.moveTo(at.x, at.y, at.z);
            player.setXRot(90.0f);
            helper.assertTrue(!player.getCooldowns().isOnCooldown(axes.getItem()), "no cooldown yet");
            axes.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getCooldowns().isOnCooldown(axes.getItem()), "10-tick cooldown started");
            helper.assertTrue(flying(helper, ThrowingEntities.THROWN_AXE.get()).size() == 1, "an axe is in flight");
            // Mock players are creative: the stack is kept.
            helper.assertTrue(axes.getCount() == 5, "creative keeps the item");
            flying(helper, ThrowingEntities.THROWN_AXE.get()).forEach(ThrownGear::discard);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "npc_arena")
    public static void pikesDoNotThrow(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack pike = new ItemStack(item("iron_pike"));
            player.setItemInHand(InteractionHand.MAIN_HAND, pike);
            pike.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(!player.isUsingItem(), "using a pike does not start a charge");
            pike.getItem().releaseUsing(pike, helper.getLevel(), player, USE_DURATION - 20);
            helper.assertTrue(
                    ThrownWeapons.throwSpear(
                                    helper.getLevel(), player, pike, EquipmentSlot.MAINHAND, 20, 0.0f)
                            == null,
                    "the throw API refuses a pike");
            helper.assertTrue(flying(helper, ThrowingEntities.THROWN_SPEAR.get()).isEmpty(), "nothing spawned");
            helper.assertTrue(!pike.isEmpty(), "the pike stays in hand");

            ItemStack spear = new ItemStack(item("iron_spear"));
            player.setItemInHand(InteractionHand.MAIN_HAND, spear);
            spear.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.isUsingItem(), "a spear starts its charge");
            player.stopUsingItem();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void landedWeaponOpensToEveryoneAfterTwentySeconds(GameTestHelper helper) {
        Range range = range(helper);
        ServerLevel level = helper.getLevel();
        ItemStack axe = new ItemStack(item("iron_throwing_axe"));
        range.thrower().setItemSlot(EquipmentSlot.MAINHAND, axe);
        ThrownWeapons.throwAxe(level, range.thrower(), axe, 0.0f);
        helper.runAfterDelay(40, () -> {
            List<ItemEntity> drops = landed(helper, item("iron_throwing_axe"));
            helper.assertTrue(drops.size() == 1, "one dropped axe");
            ItemEntity drop = drops.get(0);
            helper.assertTrue(!ThrownPickup.mayCollect(drop, range.target()), "locked to the thrower at first");
            // Move the clock marker back instead of waiting 400 ticks.
            drop.getPersistentData().putLong(LOTRRevival.MOD_ID + ":thrown_owner_until", level.getGameTime() - 1);
            helper.assertTrue(ThrownPickup.mayCollect(drop, range.target()), "anyone after 20 seconds");
            helper.succeed();
        });
    }
}
