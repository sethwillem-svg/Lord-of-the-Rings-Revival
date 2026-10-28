package io.github.sethwillemsvg.lotrrevival.core.combat;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * A weapon or tool material (specs/core/combat-items.md, "Materials"). Implements {@link Tier} so
 * the vanilla tool classes accept it. Repair items are referenced by their {@code c:ingots/<metal>}
 * tag, so the metal items themselves do not have to exist yet.
 *
 * @param id material id, for example {@code orc_steel}
 * @param swordDamage total damage of this material's sword in half-hearts (player base 1 included)
 * @param durability uses shared by every class of the material
 * @param miningSpeed tool mining speed
 * @param incorrectForDrops tag of blocks this tier cannot harvest
 * @param enchantability enchantability of items made from it
 * @param repairMetal metal whose {@code c:ingots/<metal>} tag repairs it
 */
public record CombatMaterial(
        String id,
        double swordDamage,
        int durability,
        float miningSpeed,
        TagKey<Block> incorrectForDrops,
        int enchantability,
        String repairMetal)
        implements Tier {
    public static final CombatMaterial BRONZE =
            new CombatMaterial("bronze", 5.5, 230, 6.0f, BlockTags.INCORRECT_FOR_IRON_TOOL, 14, "bronze");
    public static final CombatMaterial IRON =
            new CombatMaterial("iron", 6.0, 250, 6.0f, BlockTags.INCORRECT_FOR_IRON_TOOL, 14, "iron");
    public static final CombatMaterial RANGER =
            new CombatMaterial("ranger", 6.5, 400, 6.0f, BlockTags.INCORRECT_FOR_IRON_TOOL, 14, "iron");
    public static final CombatMaterial GONDOR =
            new CombatMaterial("gondor", 6.5, 450, 6.0f, BlockTags.INCORRECT_FOR_IRON_TOOL, 14, "iron");
    public static final CombatMaterial ORC_STEEL =
            new CombatMaterial("orc_steel", 6.5, 350, 6.0f, BlockTags.INCORRECT_FOR_IRON_TOOL, 14, "orc_steel");
    /** Registered now, gear in a later spec. Diamond tier is ours. */
    public static final CombatMaterial MITHRIL =
            new CombatMaterial("mithril", 9.0, 2400, 8.0f, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 20, "mithril");

    public static final List<CombatMaterial> ALL = List.of(BRONZE, IRON, RANGER, GONDOR, ORC_STEEL, MITHRIL);

    public static Optional<CombatMaterial> byId(String id) {
        return ALL.stream().filter(m -> m.id.equals(id)).findFirst();
    }

    /** Material damage as the crossbow formula uses it: sword damage minus 4 (bronze 1.5, iron 2, mithril 5). */
    public double materialDamage() {
        return swordDamage - 4.0;
    }

    /** {@code c:ingots/<metal>}, the metal that crafts and repairs this material. */
    public TagKey<Item> ingotTag() {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/" + repairMetal));
    }

    @Override
    public int getUses() {
        return durability;
    }

    @Override
    public float getSpeed() {
        return miningSpeed;
    }

    /** Bonus so that a tool's total damage lines up with the vanilla scale: sword damage minus 4. */
    @Override
    public float getAttackDamageBonus() {
        return (float) materialDamage();
    }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return incorrectForDrops;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantability;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(ingotTag());
    }
}
