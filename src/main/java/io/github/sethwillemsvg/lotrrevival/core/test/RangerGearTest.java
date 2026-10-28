package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorPieces;
import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorSet;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.RangerGear;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTableRecipes;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTables;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Ranger weapons, Ranger and Arnor armour: stats, point tables and table-scoped recipes (ticket #114). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RangerGearTest {
    private static final double EPS = 0.01;

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LOTRRevival.id(id));
    }

    private static double modifier(ItemStack stack, Holder<Attribute> attribute) {
        return stack.getAttributeModifiers().modifiers().stream()
                .filter(e -> e.attribute().equals(attribute))
                .map(e -> e.modifier().amount())
                .findFirst()
                .orElse(0.0);
    }

    private static void weapon(
            GameTestHelper helper, String id, double damage, double speed, double reach, double knockback) {
        Item item = item(id);
        helper.assertTrue(item != Items.AIR, id + " is registered");
        ItemStack stack = new ItemStack(item);
        double gotDamage = modifier(stack, Attributes.ATTACK_DAMAGE) + WeaponClass.PLAYER_BASE_DAMAGE;
        double gotSpeed = modifier(stack, Attributes.ATTACK_SPEED) + WeaponClass.PLAYER_BASE_ATTACK_SPEED;
        double gotReach = modifier(stack, Attributes.ENTITY_INTERACTION_RANGE);
        double gotKnockback = modifier(stack, Attributes.ATTACK_KNOCKBACK);
        helper.assertTrue(Math.abs(gotDamage - damage) < EPS, id + " damage " + gotDamage + " != " + damage);
        helper.assertTrue(Math.abs(gotSpeed - speed) < EPS, id + " speed " + gotSpeed + " != " + speed);
        helper.assertTrue(Math.abs(gotReach - reach) < EPS, id + " reach " + gotReach + " != " + reach);
        helper.assertTrue(Math.abs(gotKnockback - knockback) < EPS, id + " knockback " + gotKnockback);
        helper.assertTrue(stack.getMaxDamage() == 400, id + " durability " + stack.getMaxDamage());
    }

    @GameTest(template = "empty")
    public static void rangerWeaponsMatchTheSpec(GameTestHelper helper) {
        // Ranger sword damage 6.5; dagger -3, spear -1, battleaxe +2 (spec "Weapon classes").
        weapon(helper, "ranger_sword", 6.5, 1.6, 0, 0);
        weapon(helper, "ranger_dagger", 3.5, 2.4, -0.75, 0);
        weapon(helper, "ranger_spear", 5.5, 1.33, 1.5, 0);
        weapon(helper, "ranger_battleaxe", 8.5, 1.2, 0, 0);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rangerDaggerIsPoisonable(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(item("ranger_dagger")).is(ModTags.Items.POISONABLE), "dagger is poisonable");
        helper.assertTrue(!new ItemStack(item("ranger_sword")).is(ModTags.Items.POISONABLE), "sword is not");
        helper.succeed();
    }

    private static void armour(
            GameTestHelper helper, ArmorSet set, ArmorPieces pieces, int h, int c, int l, int b, int multiplier) {
        check(helper, set, pieces.helmet().get(), ArmorItem.Type.HELMET, h, multiplier);
        check(helper, set, pieces.chestplate().get(), ArmorItem.Type.CHESTPLATE, c, multiplier);
        check(helper, set, pieces.leggings().get(), ArmorItem.Type.LEGGINGS, l, multiplier);
        check(helper, set, pieces.boots().get(), ArmorItem.Type.BOOTS, b, multiplier);
    }

    private static void check(
            GameTestHelper helper, ArmorSet set, ArmorItem item, ArmorItem.Type type, int points, int multiplier) {
        String name = set.id() + " " + type.getName();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(item.getDefense() == points, name + " points " + item.getDefense() + " != " + points);
        helper.assertTrue(item.getToughness() == 0f, name + " toughness");
        helper.assertTrue(
                stack.getMaxDamage() == type.getDurability(multiplier), name + " durability " + stack.getMaxDamage());
        helper.assertTrue(stack.is(ModTags.Items.armor(Factions.DUNEDAIN_NORTH)), name + " is in armor/dunedain_north");
    }

    @GameTest(template = "empty")
    public static void rangerAndArnorArmourMatchTheSpec(GameTestHelper helper) {
        armour(helper, RangerGear.RANGER_ARMOR, RangerGear.RANGER_PIECES, 2, 5, 4, 2, 21);
        armour(helper, RangerGear.ARNOR_ARMOR, RangerGear.ARNOR_PIECES, 2, 6, 5, 2, 27);
        helper.assertTrue(RangerGear.RANGER_ARMOR.material().value().enchantmentValue() == 15, "leather enchantability");
        helper.assertTrue(RangerGear.ARNOR_ARMOR.material().value().enchantmentValue() == 9, "metal enchantability");
        helper.succeed();
    }

    private static CraftingInput grid(int width, int height, Item item, String shape) {
        List<ItemStack> stacks = new ArrayList<>();
        for (char ch : shape.toCharArray()) {
            stacks.add(ch == 'X' ? new ItemStack(item) : ItemStack.EMPTY);
        }
        return CraftingInput.of(width, height, stacks);
    }

    private static Optional<ItemStack> craft(GameTestHelper helper, CraftingInput input, Block table) {
        List<RecipeHolder<CraftingRecipe>> faction =
                helper.getLevel().getRecipeManager().getAllRecipesFor(FactionTableRecipes.TYPE.get());
        return FactionTableRecipes.match(faction, input, helper.getLevel(), table)
                .map(h -> h.value().assemble(input, helper.getLevel().registryAccess()));
    }

    @GameTest(template = "empty")
    public static void rangerRecipesMatchOnTheRangerTableOnly(GameTestHelper helper) {
        Block rangerTable = FactionTables.RANGER.block().get();

        // Weapon: sword = metal, metal, stick in a column.
        CraftingInput sword = CraftingInput.of(
                1, 3, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STICK)));
        Optional<ItemStack> result = craft(helper, sword, rangerTable);
        helper.assertTrue(result.isPresent() && result.get().is(item("ranger_sword")), "ranger sword on the ranger table");
        // The Gondor table has its own sword with this shape; it must never give the Ranger one.
        Optional<ItemStack> gondor = craft(helper, sword, FactionTables.GONDOR.block().get());
        helper.assertTrue(gondor.isEmpty() || !gondor.get().is(item("ranger_sword")), "no ranger sword on the Gondor table");
        helper.assertTrue(craft(helper, sword, Blocks.CRAFTING_TABLE).isEmpty(), "not on the vanilla table");
        // The vanilla table only ever produces the vanilla iron sword for this grid.
        Optional<RecipeHolder<CraftingRecipe>> vanilla =
                helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, sword, helper.getLevel());
        helper.assertTrue(
                vanilla.isPresent()
                        && vanilla.get().value().assemble(sword, helper.getLevel().registryAccess()).is(Items.IRON_SWORD),
                "the vanilla table crafts an iron sword, not a ranger sword");

        // Armour: the helmet shape gives the Ranger helmet from leather and the Arnor helmet from iron ingots.
        CraftingInput leather = grid(3, 2, Items.LEATHER, "XXXX X");
        Optional<ItemStack> ranger = craft(helper, leather, rangerTable);
        helper.assertTrue(ranger.isPresent() && ranger.get().is(item("ranger_helmet")), "ranger helmet on ranger table");
        helper.assertTrue(craft(helper, leather, Blocks.CRAFTING_TABLE).isEmpty(), "no ranger helmet on vanilla table");

        CraftingInput iron = grid(3, 2, Items.IRON_INGOT, "XXXX X");
        Optional<ItemStack> arnor = craft(helper, iron, rangerTable);
        helper.assertTrue(arnor.isPresent() && arnor.get().is(item("arnor_helmet")), "arnor helmet on ranger table");
        helper.assertTrue(craft(helper, iron, Blocks.CRAFTING_TABLE).isEmpty(), "no arnor helmet on vanilla table");
        helper.succeed();
    }
}
