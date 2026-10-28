package io.github.sethwillemsvg.lotrrevival.core.combat.gear;

import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorPieces;
import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorSet;
import io.github.sethwillemsvg.lotrrevival.core.combat.CraftTable;
import io.github.sethwillemsvg.lotrrevival.core.combat.Gear;
import io.github.sethwillemsvg.lotrrevival.core.combat.RecipeKey;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.Tags;

/**
 * Ticket #112: Shire and Bree armour (specs/core/combat-items.md, "Armour"). Both sets are our own
 * designs: the light padded Shire (Bounder) kit and the Bree guard's leather-and-iron kit. Neither
 * has weapons in this spec ({@code CombatItems.init} calls {@link #init()}).
 */
public final class ShireBreeGear {
    private ShireBreeGear() {}

    /** Shire: p 0.4 (1/4/3/1), 200 uses, leather, hobbit table. */
    public static final ArmorSet SHIRE_SET =
            ArmorSet.cloth("shire", 0.4, 200, () -> Ingredient.of(Items.LEATHER), Factions.HOBBIT);

    /** Bree: p 0.5 (2/5/4/2), 250 uses, iron ingot, Bree table. */
    public static final ArmorSet BREE_SET =
            ArmorSet.metal("bree", 0.5, 250, () -> Ingredient.of(Tags.Items.INGOTS_IRON), Factions.BREE);

    public static final ArmorPieces SHIRE =
            Gear.armor(SHIRE_SET, "Shire", CraftTable.culture("hobbit"), RecipeKey.of(Items.LEATHER));
    public static final ArmorPieces BREE =
            Gear.armor(BREE_SET, "Bree", CraftTable.culture("bree"), RecipeKey.of(Tags.Items.INGOTS_IRON));

    /** Loads the class so its static gear fields register. Called from {@code CombatItems.init}. */
    public static void init() {}
}
