package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorPieces;
import io.github.sethwillemsvg.lotrrevival.core.combat.LanceSlow;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.GondorGear;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTableRecipes;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTables;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

/** Gondor weapons, armour, table recipes and the real lance's slowdown (ticket #116). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GondorGearTest {
    private static final double EPS = 0.01;

    private GondorGearTest() {}

    /** Player base value plus the item's modifier for the attribute (base alone if the item has none). */
    private static double attribute(ItemStack stack, Holder<Attribute> attribute, double base) {
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return base
                + mods.modifiers().stream()
                        .filter(e -> e.attribute().equals(attribute))
                        .mapToDouble(e -> e.modifier().amount())
                        .findFirst()
                        .orElse(0.0);
    }

    private static void weapon(
            GameTestHelper helper, DeferredItem<Item> item, double damage, double speed, double reach, double knockback) {
        ItemStack stack = new ItemStack(item.get());
        String name = item.getId().getPath();
        double gotDamage = attribute(stack, Attributes.ATTACK_DAMAGE, 1.0);
        double gotSpeed = attribute(stack, Attributes.ATTACK_SPEED, 4.0);
        double gotReach = attribute(stack, Attributes.ENTITY_INTERACTION_RANGE, 0.0);
        double gotKnockback = attribute(stack, Attributes.ATTACK_KNOCKBACK, 0.0);
        helper.assertTrue(Math.abs(gotDamage - damage) < EPS, name + " damage " + gotDamage + " != " + damage);
        helper.assertTrue(Math.abs(gotSpeed - speed) < EPS, name + " speed " + gotSpeed + " != " + speed);
        helper.assertTrue(Math.abs(gotReach - reach) < EPS, name + " reach " + gotReach + " != " + reach);
        helper.assertTrue(
                Math.abs(gotKnockback - knockback) < EPS, name + " knockback " + gotKnockback + " != " + knockback);
        helper.assertTrue(stack.getMaxDamage() == 450, name + " durability " + stack.getMaxDamage());
    }

    @GameTest(template = "empty")
    public static void gondorWeaponsMatchTheSpec(GameTestHelper helper) {
        weapon(helper, GondorGear.DAGGER, 3.5, 2.4, -0.75, 0);
        weapon(helper, GondorGear.SWORD, 6.5, 1.6, 0, 0);
        weapon(helper, GondorGear.SPEAR, 5.5, 1.33, 1.5, 0);
        weapon(helper, GondorGear.PIKE, 6.5, 0.8, 3.0, 0);
        weapon(helper, GondorGear.BATTLEAXE, 8.5, 1.2, 0, 0);
        weapon(helper, GondorGear.HAMMER, 8.5, 1.07, 0, 1);
        weapon(helper, GondorGear.LANCE, 6.5, 0.8, 3.0, 1);
        helper.assertTrue(
                new ItemStack(GondorGear.LANCE.get()).is(ModTags.Items.WEAPONS_LANCES), "lance is in weapons/lances");
        helper.assertTrue(
                new ItemStack(GondorGear.DAGGER.get()).is(ModTags.Items.WEAPONS_DAGGERS), "dagger is in weapons/daggers");
        helper.succeed();
    }

    private static void armour(GameTestHelper helper, ArmorPieces pieces, int[] points, int[] durability, int enchantability) {
        List<DeferredItem<ArmorItem>> all = List.of(pieces.helmet(), pieces.chestplate(), pieces.leggings(), pieces.boots());
        for (int i = 0; i < 4; i++) {
            ArmorItem item = all.get(i).get();
            String name = all.get(i).getId().getPath();
            helper.assertTrue(item.getDefense() == points[i], name + " points " + item.getDefense() + " != " + points[i]);
            int max = new ItemStack(item).getMaxDamage();
            helper.assertTrue(max == durability[i], name + " durability " + max + " != " + durability[i]);
            helper.assertTrue(item.getToughness() == 0.0f, name + " toughness");
            helper.assertTrue(item.getMaterial().value().knockbackResistance() == 0.0f, name + " knockback resistance");
            helper.assertTrue(item.getEnchantmentValue() == enchantability, name + " enchantability");
            helper.assertTrue(new ItemStack(item).is(ModTags.Items.armor(Factions.GONDOR)), name + " in armor/gondor");
        }
    }

    @GameTest(template = "empty")
    public static void gondorArmourMatchesTheSpec(GameTestHelper helper) {
        armour(helper, GondorGear.ARMOR, new int[] {2, 6, 5, 2}, new int[] {297, 432, 405, 351}, 9);
        armour(helper, GondorGear.LEVY, new int[] {1, 4, 3, 1}, new int[] {132, 192, 180, 156}, 15);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gondorRecipesAreFactionScoped(GameTestHelper helper) {
        List<RecipeHolder<CraftingRecipe>> recipes =
                helper.getLevel().getRecipeManager().getAllRecipesFor(FactionTableRecipes.TYPE.get());
        var registries = helper.getLevel().registryAccess();
        ItemStack ingot = new ItemStack(Items.IRON_INGOT);
        ItemStack stick = new ItemStack(Items.STICK);
        CraftingInput sword = CraftingInput.of(1, 3, List.of(ingot, ingot, stick));

        var onGondor = FactionTableRecipes.match(recipes, sword, helper.getLevel(), FactionTables.GONDOR.block().get());
        helper.assertTrue(onGondor.isPresent(), "iron ingot sword shape matches on the Gondor table");
        helper.assertTrue(
                onGondor.get().value().getResultItem(registries).is(GondorGear.SWORD.get()), "result is the Gondor sword");
        helper.assertTrue(
                FactionTableRecipes.match(recipes, sword, helper.getLevel(), Blocks.CRAFTING_TABLE).isEmpty(),
                "no faction match on the vanilla table");
        helper.assertTrue(
                FactionTableRecipes.match(recipes, sword, helper.getLevel(), FactionTables.MORDOR.block().get())
                        .map(h -> !h.value().getResultItem(registries).is(GondorGear.SWORD.get()))
                        .orElse(true),
                "the Gondor sword is not crafted on the Mordor table");

        ItemStack wool = new ItemStack(Items.WHITE_WOOL);
        CraftingInput levyHelmet = CraftingInput.of(3, 2, List.of(wool, wool, wool, wool, ItemStack.EMPTY, wool));
        var levy = FactionTableRecipes.match(recipes, levyHelmet, helper.getLevel(), FactionTables.GONDOR.block().get());
        helper.assertTrue(
                levy.isPresent() && levy.get().value().getResultItem(registries).is(GondorGear.LEVY.helmet().get()),
                "white wool helmet shape makes the levy helmet on the Gondor table");
        helper.succeed();
    }

    /** The real lance, through the real equipment-change and mount events (no direct refresh calls). */
    @GameTest(template = "empty")
    public static void realLanceSlowsOnFootAndClearsOnMountOrSwitch(GameTestHelper helper) {
        // Entities sit outside the 1x1 template's chunk; wait until that space ticks.
        TestArea.whenTicking(helper, 8, () -> realLanceSlowsOnFootAndClearsOnMountOrSwitchBody(helper));
    }

    private static void realLanceSlowsOnFootAndClearsOnMountOrSwitchBody(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        Boat boat = helper.spawn(EntityType.BOAT, 4, 2, 4);
        zombie.setNoAi(true);
        try {
            AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
            double base = speed.getValue();
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GondorGear.LANCE.get()));
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(speed.hasModifier(LanceSlow.MODIFIER_ID), "holding the lance on foot slows");
                    helper.assertTrue(
                            Math.abs(speed.getValue() - base * 0.8) < 1e-6, "speed is 80%, was " + speed.getValue());

                    helper.assertTrue(zombie.startRiding(boat, true), "mounting succeeds");
                    helper.assertTrue(!speed.hasModifier(LanceSlow.MODIFIER_ID), "mounting clears the slowdown");
                    helper.assertTrue(Math.abs(speed.getValue() - base) < 1e-6, "speed restored while mounted");

                    zombie.stopRiding();
                    helper.assertTrue(speed.hasModifier(LanceSlow.MODIFIER_ID), "dismounting with the lance slows again");

                    zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GondorGear.SWORD.get()));
                    helper.runAfterDelay(2, () -> {
                        try {
                            helper.assertTrue(!speed.hasModifier(LanceSlow.MODIFIER_ID), "switching items clears it");
                            helper.assertTrue(Math.abs(speed.getValue() - base) < 1e-6, "speed restored after switching");
                            helper.succeed();
                        } finally {
                            zombie.discard();
                            boat.discard();
                        }
                    });
                } catch (RuntimeException e) {
                    zombie.discard();
                    boat.discard();
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            zombie.discard();
            boat.discard();
            throw e;
        }
    }
}
