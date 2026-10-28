package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Issue #168: on the vanilla table the spear grid never collides with the shovel grid. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpearShapeTest {
    private static final List<String> SHOVEL = List.of("M", "S", "S");
    private static final List<String> SPEAR = List.of(" M", " S", "S ");
    private static final List<String> SPEAR_MIRRORED = List.of("M ", "S ", " S");

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LOTRRevival.id(id));
    }

    private static CraftingInput grid(List<String> rows, Item metal) {
        int width = rows.get(0).length();
        List<ItemStack> stacks = new ArrayList<>();
        for (String row : rows) {
            for (char c : row.toCharArray()) {
                stacks.add(switch (c) {
                    case 'M' -> new ItemStack(metal);
                    case 'S' -> new ItemStack(Items.STICK);
                    default -> ItemStack.EMPTY;
                });
            }
        }
        return CraftingInput.of(width, rows.size(), stacks);
    }

    private static void expect(GameTestHelper helper, List<String> rows, Item metal, Item result, String what) {
        CraftingInput input = grid(rows, metal);
        Optional<RecipeHolder<CraftingRecipe>> found =
                helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(found.isPresent(), what + ": no recipe matched");
        ItemStack out = found.get().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(result), what + ": crafted " + out + ", expected " + result);
        long matches = helper.getLevel().getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, helper.getLevel())
                .size();
        helper.assertTrue(matches == 1, what + ": " + matches + " recipes match the grid");
    }

    @GameTest(template = "empty")
    public static void shovelAndSpearGridsDoNotClash(GameTestHelper helper) {
        ItemStack[] bronze = Ingredient.of(CombatMaterial.BRONZE.ingotTag()).getItems();
        helper.assertTrue(bronze.length > 0, "bronze ingot tag is empty");
        Item bronzeIngot = bronze[0].getItem();

        expect(helper, SHOVEL, Items.IRON_INGOT, Items.IRON_SHOVEL, "iron shovel grid");
        expect(helper, SHOVEL, bronzeIngot, item("bronze_shovel"), "bronze shovel grid");
        expect(helper, SPEAR, Items.IRON_INGOT, item("iron_spear"), "iron spear grid");
        expect(helper, SPEAR_MIRRORED, Items.IRON_INGOT, item("iron_spear"), "mirrored iron spear grid");
        expect(helper, SPEAR, bronzeIngot, item("bronze_spear"), "bronze spear grid");
        helper.succeed();
    }
}
