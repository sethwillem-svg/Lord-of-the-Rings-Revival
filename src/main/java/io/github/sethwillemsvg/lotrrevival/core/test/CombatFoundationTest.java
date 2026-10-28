package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorSet;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.LanceSlow;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponProfile;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponStats;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Weapon class x material numbers, armour formula and the lance slowdown (specs/core/combat-items.md). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatFoundationTest {
    private static final double EPS = 0.01;

    private static Optional<Double> modifier(ItemAttributeModifiers mods, Holder<Attribute> attribute) {
        return mods.modifiers().stream()
                .filter(e -> e.attribute().equals(attribute))
                .map(e -> e.modifier().amount())
                .findFirst();
    }

    private static void check(GameTestHelper helper, WeaponClass cls, CombatMaterial mat, double damage, double speed, double reach, double knockback) {
        ItemAttributeModifiers mods = cls.attributes(mat);
        String name = mat.id() + " " + cls.id();
        double gotDamage = modifier(mods, Attributes.ATTACK_DAMAGE).orElseThrow() + WeaponClass.PLAYER_BASE_DAMAGE;
        double gotSpeed = modifier(mods, Attributes.ATTACK_SPEED).orElseThrow() + WeaponClass.PLAYER_BASE_ATTACK_SPEED;
        double gotReach = modifier(mods, Attributes.ENTITY_INTERACTION_RANGE).orElse(0.0);
        double gotKnockback = modifier(mods, Attributes.ATTACK_KNOCKBACK).orElse(0.0);
        helper.assertTrue(Math.abs(gotDamage - damage) < EPS, name + " damage " + gotDamage + " != " + damage);
        helper.assertTrue(Math.abs(gotSpeed - speed) < EPS, name + " speed " + gotSpeed + " != " + speed);
        helper.assertTrue(Math.abs(gotReach - reach) < EPS, name + " reach " + gotReach + " != " + reach);
        helper.assertTrue(Math.abs(gotKnockback - knockback) < EPS, name + " knockback " + gotKnockback + " != " + knockback);
    }

    @GameTest(template = "empty")
    public static void specExamplesMatch(GameTestHelper helper) {
        check(helper, WeaponClass.DAGGER, CombatMaterial.BRONZE, 2.5, 2.4, -0.75, 0);
        check(helper, WeaponClass.PIKE, CombatMaterial.GONDOR, 6.5, 0.8, 3.0, 0);
        check(helper, WeaponClass.HAMMER, CombatMaterial.IRON, 8.0, 1.07, 0, 1);
        // Worked examples from the spec.
        check(helper, WeaponClass.SWORD, CombatMaterial.BRONZE, 5.5, 1.6, 0, 0);
        check(helper, WeaponClass.PIKE, CombatMaterial.IRON, 6.0, 0.8, 3.0, 0);
        check(helper, WeaponClass.BATTLEAXE, CombatMaterial.IRON, 8.0, 1.2, 0, 0);
        check(helper, WeaponClass.SPEAR, CombatMaterial.GONDOR, 5.5, 1.33, 1.5, 0);
        check(helper, WeaponClass.HAMMER, CombatMaterial.GONDOR, 8.5, 1.07, 0, 1);
        check(helper, WeaponClass.DAGGER, CombatMaterial.ORC_STEEL, 3.5, 2.4, -0.75, 0);
        check(helper, WeaponClass.SWORD, CombatMaterial.MITHRIL, 9.0, 1.6, 0, 0);
        check(helper, WeaponClass.LANCE, CombatMaterial.GONDOR, 6.5, 0.8, 3.0, 1);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyClassAndMaterialFollowsTheTables(GameTestHelper helper) {
        for (WeaponClass cls : WeaponClass.values()) {
            for (CombatMaterial mat : CombatMaterial.ALL) {
                WeaponProfile profile = WeaponProfile.of(cls, mat);
                check(helper, cls, mat, mat.swordDamage() + cls.damageOffset(), 1.6 * cls.speedFactor(), cls.reach(), cls.knockback());
                helper.assertTrue(profile.damage() == cls.damage(mat), "profile damage " + cls + " " + mat.id());
                helper.assertTrue(
                        Math.abs(profile.reachFactor() - (3.0 + cls.reach()) / 3.0) < 1e-9, "reach factor " + cls);
                helper.assertTrue(cls.sweeps() == (cls == WeaponClass.SWORD), "only swords sweep");
            }
        }
        helper.assertTrue(Math.abs(WeaponClass.SPEAR.speedFactor() - 0.833) < 0.001, "spear speed factor");
        helper.assertTrue(Math.abs(WeaponClass.SPEAR.reachFactor() - 1.5) < 1e-9, "spear reach factor");
        helper.assertTrue(Math.abs(WeaponClass.DAGGER.reachFactor() - 0.75) < 1e-9, "dagger reach factor");
        helper.assertTrue(Math.abs(WeaponClass.PIKE.reachFactor() - 2.0) < 1e-9, "pike reach factor");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void materialsMatchTheTable(GameTestHelper helper) {
        helper.assertTrue(CombatMaterial.BRONZE.getUses() == 230, "bronze uses");
        helper.assertTrue(CombatMaterial.GONDOR.getUses() == 450, "gondor uses");
        helper.assertTrue(CombatMaterial.MITHRIL.getUses() == 2400, "mithril uses");
        helper.assertTrue(CombatMaterial.MITHRIL.getEnchantmentValue() == 20, "mithril enchantability");
        helper.assertTrue(CombatMaterial.BRONZE.getAttackDamageBonus() == 1.5f, "bronze tier bonus is sword damage - 4");
        helper.assertTrue(CombatMaterial.IRON.materialDamage() == 2.0, "iron crossbow material damage");
        helper.assertTrue(CombatMaterial.RANGER.ingotTag().location().toString().equals("c:ingots/iron"), "ranger repairs with iron");
        helper.assertTrue(CombatMaterial.ORC_STEEL.ingotTag().location().toString().equals("c:ingots/orc_steel"), "orc steel tag");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void weaponStatsIgnoresNonWeapons(GameTestHelper helper) {
        helper.assertTrue(WeaponStats.of(new ItemStack(Items.STICK)).isEmpty(), "stick has no weapon profile");
        helper.assertTrue(WeaponStats.of(ItemStack.EMPTY).isEmpty(), "empty stack has no weapon profile");
        helper.assertTrue(WeaponStats.of(new ItemStack(Items.IRON_SWORD)).isEmpty(), "vanilla swords are not ours");
        helper.succeed();
    }

    private static void armour(GameTestHelper helper, double p, int h, int c, int l, int b) {
        int[] got = {
            ArmorSet.points(p, ArmorItem.Type.HELMET),
            ArmorSet.points(p, ArmorItem.Type.CHESTPLATE),
            ArmorSet.points(p, ArmorItem.Type.LEGGINGS),
            ArmorSet.points(p, ArmorItem.Type.BOOTS)
        };
        helper.assertTrue(
                got[0] == h && got[1] == c && got[2] == l && got[3] == b,
                "p=" + p + " gave " + got[0] + "/" + got[1] + "/" + got[2] + "/" + got[3]);
    }

    @GameTest(template = "empty")
    public static void armourPointsMatchTheTable(GameTestHelper helper) {
        armour(helper, 0.4, 1, 4, 3, 1);
        armour(helper, 0.48, 2, 5, 4, 2);
        armour(helper, 0.5, 2, 5, 4, 2);
        armour(helper, 0.6, 2, 6, 5, 2);
        armour(helper, 0.8, 3, 8, 6, 3);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armourDurabilityMultiplier(GameTestHelper helper) {
        int[][] cases = {{230, 14}, {200, 12}, {250, 15}, {350, 21}, {450, 27}, {2400, 144}};
        for (int[] c : cases) {
            helper.assertTrue(
                    ArmorSet.durabilityMultiplier(c[0]) == c[1],
                    "uses " + c[0] + " gave multiplier " + ArmorSet.durabilityMultiplier(c[0]) + ", want " + c[1]);
        }
        helper.assertTrue(ArmorItem.Type.HELMET.getDurability(27) == 11 * 27, "gondor helmet durability");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lanceSlowsOnFootAndNotMounted(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        try {
            AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
            double base = speed.getValue();
            helper.assertTrue(!speed.hasModifier(LanceSlow.MODIFIER_ID), "no slowdown before holding a lance");

            LanceSlow.refresh(zombie, true, false);
            AttributeModifier mod = speed.getModifier(LanceSlow.MODIFIER_ID);
            helper.assertTrue(mod != null, "lance on foot applies the slowdown");
            helper.assertTrue(mod.amount() == -0.2 && mod.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "-20% total");
            helper.assertTrue(Math.abs(speed.getValue() - base * 0.8) < 1e-6, "speed is 80% of " + base + ", was " + speed.getValue());

            LanceSlow.refresh(zombie, true, false);
            helper.assertTrue(Math.abs(speed.getValue() - base * 0.8) < 1e-6, "refreshing twice does not stack");

            LanceSlow.refresh(zombie, true, true);
            helper.assertTrue(!speed.hasModifier(LanceSlow.MODIFIER_ID), "mounting removes the slowdown");
            helper.assertTrue(Math.abs(speed.getValue() - base) < 1e-6, "speed restored when mounted");

            LanceSlow.refresh(zombie, true, false);
            LanceSlow.refresh(zombie, false, false);
            helper.assertTrue(!speed.hasModifier(LanceSlow.MODIFIER_ID), "switching items removes the slowdown");

            helper.assertTrue(LanceSlow.shouldSlow(true, false), "lance on foot slows");
            helper.assertTrue(!LanceSlow.shouldSlow(true, true), "lance mounted does not");
            helper.assertTrue(!LanceSlow.shouldSlow(false, false), "no lance does not");
            helper.assertTrue(!LanceSlow.isLance(new ItemStack(Items.STICK)), "a stick is not a lance");
            helper.succeed();
        } finally {
            zombie.discard();
        }
    }
}
