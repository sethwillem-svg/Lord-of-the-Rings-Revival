package io.github.sethwillemsvg.lotrrevival.core.combat.gear;

import io.github.sethwillemsvg.lotrrevival.core.combat.CraftTable;
import io.github.sethwillemsvg.lotrrevival.core.combat.Gear;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearRecipe;
import io.github.sethwillemsvg.lotrrevival.core.combat.RecipeKey;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.FactionBow;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.SlingItem;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.PebbleTypes;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Gear holder for ticket #120 and #121: faction bows, the hobbit sling, bronze and iron crossbows
 * (specs/core/combat-items.md, "Ranged weapons"). {@code CombatItems.init} already calls
 * {@link #init()}, so tickets never touch {@code CoreMod}.
 */
public final class RangedGear {
    private RangedGear() {}

    /** Durability of every faction bow. */
    public static final int BOW_DURABILITY = 384;
    /** Durability of the sling. */
    public static final int SLING_DURABILITY = 64;

    /** Vanilla bow shape: stick on the left, string on the right. */
    private static GearRecipe bowRecipe(CraftTable table) {
        return GearRecipe.shaped(
                table,
                List.of(" SX", "S X", " SX"),
                Map.of('S', RecipeKey.of(Items.STICK), 'X', RecipeKey.of(Items.STRING)));
    }

    /** x1.0 arrow damage, 20 ticks to full draw. */
    public static final DeferredItem<Item> RANGER_BOW = Gear.bow(
            "ranger_bow", "Ranger Bow", () -> new FactionBow(BOW_DURABILITY, 20, 1.0), bowRecipe(RangerGear.TABLE));

    /** x1.125 arrow damage, 20 ticks to full draw. */
    public static final DeferredItem<Item> GONDOR_BOW = Gear.bow(
            "gondor_bow", "Gondorian Bow", () -> new FactionBow(BOW_DURABILITY, 20, 1.125),
            bowRecipe(GondorGear.WEAPONS.table()));

    /** x1.125 arrow damage, 20 ticks to full draw. */
    public static final DeferredItem<Item> MORDOR_BOW = Gear.bow(
            "mordor_bow", "Orcish Bow", () -> new FactionBow(BOW_DURABILITY, 20, 1.125),
            bowRecipe(MordorGear.SET.table()));

    /** Sling pebble: 1 gravel makes 4, vanilla table. */
    public static final DeferredItem<Item> PEBBLE = Gear.ammo(
            "pebble", "Pebble", () -> new Item(new Item.Properties()), List.of(ModTags.Items.PEBBLES),
            GearRecipe.shapeless(CraftTable.VANILLA, 4, List.of(RecipeKey.of(Items.GRAVEL))));

    /** Hobbit sling: {@code S S} / {@code  L } / {@code  T } with stick, leather, string. */
    public static final DeferredItem<Item> SLING = Gear.sling(
            "sling", "Sling", () -> new SlingItem(SLING_DURABILITY),
            GearRecipe.shaped(
                    CraftTable.culture("hobbit"),
                    List.of("S S", " L ", " T "),
                    Map.of(
                            'S', RecipeKey.of(Items.STICK),
                            'L', RecipeKey.of(Items.LEATHER),
                            'T', RecipeKey.of(Items.STRING))));

    /** Loads the class so its static gear fields register. Called from {@code CombatItems.init}. */
    public static void init() {
        PebbleTypes.init();
    }
}
