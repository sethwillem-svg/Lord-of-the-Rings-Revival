package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponProfile;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponStats;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.MordorGear;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTableRecipes;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Mordor gear (ticket #118): weapon profile, armour tables and the Mordor-table recipes. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MordorGearTest {
    private MordorGearTest() {}

    @GameTest(template = "empty")
    public static void mordor_spear_weapon_stats(GameTestHelper helper) {
        Optional<WeaponProfile> profile = WeaponStats.of(new ItemStack(MordorGear.SPEAR.get()));
        helper.assertTrue(profile.isPresent(), "mordor spear has a weapon profile");
        WeaponProfile p = profile.get();
        helper.assertTrue(p.weaponClass() == WeaponClass.SPEAR, "class is spear");
        helper.assertTrue(p.material() == CombatMaterial.ORC_STEEL, "material is orc steel");
        helper.assertTrue(Math.abs(p.speedFactor() - 0.833) < 0.001, "speed factor 0.833, was " + p.speedFactor());
        helper.assertTrue(Math.abs(p.reachFactor() - 1.5) < 1e-9, "reach factor 1.5, was " + p.reachFactor());
        helper.assertTrue(Math.abs(p.damage() - 5.5) < 1e-9, "orc steel spear damage 5.5, was " + p.damage());
        helper.assertTrue(WeaponStats.of(new ItemStack(Items.STICK)).isEmpty(), "a vanilla stick returns empty");
        for (var item : List.of(
                MordorGear.DAGGER, MordorGear.SWORD, MordorGear.SPEAR, MordorGear.PIKE, MordorGear.BATTLEAXE, MordorGear.HAMMER)) {
            helper.assertTrue(
                    new ItemStack(item.get()).getMaxDamage() == 350, item.getId() + " has 350 durability");
            helper.assertTrue(
                    WeaponStats.of(new ItemStack(item.get())).orElseThrow().material() == CombatMaterial.ORC_STEEL,
                    item.getId() + " is orc steel");
        }
        helper.assertTrue(new ItemStack(MordorGear.SWORD.get()).is(ItemTags.SWORDS), "mordor sword is in #swords");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mordor_armour_matches_the_table(GameTestHelper helper) {
        int[] points = {2, 6, 5, 2};
        ArmorItem[] pieces = {
            MordorGear.ARMOR.helmet().get(),
            MordorGear.ARMOR.chestplate().get(),
            MordorGear.ARMOR.leggings().get(),
            MordorGear.ARMOR.boots().get()
        };
        // Vanilla per-slot base 11/16/15/13 times multiplier round(350 x 0.06) = 21.
        int[] durability = {11 * 21, 16 * 21, 15 * 21, 13 * 21};
        helper.assertTrue(MordorGear.ARMOR_SET.durabilityMultiplier() == 21, "durability multiplier 21");
        for (int i = 0; i < 4; i++) {
            ItemStack stack = new ItemStack(pieces[i]);
            helper.assertTrue(
                    pieces[i].getDefense() == points[i], pieces[i].getType() + " points " + pieces[i].getDefense());
            helper.assertTrue(
                    stack.getMaxDamage() == durability[i], pieces[i].getType() + " durability " + stack.getMaxDamage());
            helper.assertTrue(pieces[i].getMaterial().value().toughness() == 0.0f, "no toughness");
            helper.assertTrue(pieces[i].getMaterial().value().knockbackResistance() == 0.0f, "no knockback resistance");
        }
        helper.assertTrue(
                new ItemStack(pieces[0]).is(io.github.sethwillemsvg.lotrrevival.core.tag.ModTags.Items.armor(
                        io.github.sethwillemsvg.lotrrevival.core.faction.Factions.MORDOR)),
                "armour is tagged armor/mordor");
        helper.succeed();
    }

    /** One stack per grid cell: the first item that satisfies each ingredient of the recipe. */
    private static CraftingInput gridFor(ShapedRecipe recipe) {
        List<ItemStack> cells = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] options = ingredient.getItems();
            cells.add(options.length == 0 ? ItemStack.EMPTY : options[0].copyWithCount(1));
        }
        return CraftingInput.of(recipe.getWidth(), recipe.getHeight(), cells);
    }

    @GameTest(template = "empty")
    public static void mordor_recipes_only_match_on_the_mordor_table(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getServer().getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> faction = manager.getAllRecipesFor(FactionTableRecipes.TYPE.get());
        String[] ids = {"mordor_sword", "mordor_helmet"};
        for (String path : ids) {
            ResourceLocation id = LOTRRevival.id(path);
            RecipeHolder<CraftingRecipe> holder = faction.stream()
                    .filter(h -> h.id().equals(id))
                    .findFirst()
                    .orElse(null);
            helper.assertTrue(holder != null, "faction recipe " + id + " exists");
            helper.assertTrue(holder.value() instanceof ShapedRecipe, id + " is a shaped faction recipe");
            CraftingInput grid = gridFor((ShapedRecipe) holder.value());
            helper.assertTrue(
                    FactionTableRecipes.match(faction, grid, level, FactionTables.MORDOR.block().get())
                            .map(h -> h.id().equals(id))
                            .orElse(false),
                    id + " matches on the Mordor table");
            helper.assertTrue(
                    FactionTableRecipes.match(faction, grid, level, FactionTables.GONDOR.block().get()).isEmpty(),
                    id + " does not match on the Gondor table");
            helper.assertTrue(
                    FactionTableRecipes.match(faction, grid, level, Blocks.CRAFTING_TABLE).isEmpty(),
                    id + " does not match on the vanilla table");
            helper.assertTrue(
                    manager.getRecipeFor(RecipeType.CRAFTING, grid, level).isEmpty(),
                    id + " is not a plain crafting recipe");
        }
        helper.succeed();
    }
}
