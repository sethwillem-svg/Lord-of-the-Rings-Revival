package io.github.sethwillemsvg.lotrrevival.core.combat;

import io.github.sethwillemsvg.lotrrevival.core.combat.item.GearBattleaxe;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.GearSword;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.GearThrowingAxe;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.GearWeapon;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Factory for every combat item (specs/core/combat-items.md, "Core package layout"). Each call
 * registers the item through {@code ModRegistries.ITEMS} and records a {@link GearEntry}, from which
 * the generic providers in {@code datagen/combat/} write models, lang, tags and recipes.
 *
 * <p>Call these from a set holder's {@code init()} (see {@code core.combat.gear.*}); adding a set is
 * a few calls plus textures, with no provider edits.
 */
public final class Gear {
    private Gear() {}

    // ------------------------------------------------------------------ weapons

    /** A weapon of the set's material with the class's standard recipe on the set's table. */
    public static DeferredItem<Item> weapon(GearSet set, WeaponClass weaponClass) {
        return weapon(set, weaponClass, set.recipe(weaponClass));
    }

    /** A weapon with an explicit recipe ({@code null} for none). */
    public static DeferredItem<Item> weapon(GearSet set, WeaponClass weaponClass, GearRecipe recipe) {
        String id = set.id() + "_" + weaponClass.id();
        CombatMaterial material = set.material();
        Supplier<Item> factory =
                switch (weaponClass) {
                    case SWORD -> () -> new GearSword(material);
                    case BATTLEAXE -> () -> new GearBattleaxe(material);
                    case THROWING_AXE -> () -> new GearThrowingAxe(material);
                    default -> () -> new GearWeapon(weaponClass, material);
                };
        DeferredItem<Item> item = ModRegistries.ITEMS.<Item>register(id, factory);
        List<TagKey<Item>> tags = new ArrayList<>();
        tags.add(weaponClass.tag());
        switch (weaponClass) {
            // Swords and axes inherit sweeping/sharpness/durability enchantability from the vanilla tags.
            case SWORD -> tags.add(ItemTags.SWORDS);
            case BATTLEAXE -> tags.add(ItemTags.AXES);
            case THROWING_AXE -> {
                tags.add(ItemTags.WEAPON_ENCHANTABLE);
                tags.add(ItemTags.SHARP_WEAPON_ENCHANTABLE);
            }
            // Not "sword" enchantable, so no Sweeping Edge.
            default -> {
                tags.add(ItemTags.WEAPON_ENCHANTABLE);
                tags.add(ItemTags.SHARP_WEAPON_ENCHANTABLE);
                tags.add(ItemTags.FIRE_ASPECT_ENCHANTABLE);
                tags.add(ItemTags.DURABILITY_ENCHANTABLE);
            }
        }
        if (weaponClass == WeaponClass.DAGGER) {
            tags.add(ModTags.Items.POISONABLE);
        }
        return enter(
                id, item, GearKind.WEAPON, modelFor(weaponClass), set.adjective() + " " + weaponClass.displayName(), tags, recipe);
    }

    /** Several weapon classes of one set with standard recipes. */
    public static void weapons(GearSet set, WeaponClass... classes) {
        for (WeaponClass weaponClass : classes) {
            weapon(set, weaponClass);
        }
    }

    private static ModelKind modelFor(WeaponClass weaponClass) {
        return switch (weaponClass) {
            case PIKE, LANCE -> ModelKind.HANDHELD_WIDE;
            default -> ModelKind.HANDHELD;
        };
    }

    // ------------------------------------------------------------------ tools

    /** A vanilla-shaped tool (pickaxe, axe, shovel, hoe) of the set's material, crafted on the set's table. */
    public static DeferredItem<Item> tool(GearSet set, ToolKind kind) {
        String id = set.id() + "_" + kind.id();
        CombatMaterial material = set.material();
        DeferredItem<Item> item = ModRegistries.ITEMS.<Item>register(id, () -> kind.create(material));
        GearRecipe recipe = GearRecipe.shaped(
                set.table(), kind.shape(), Map.of('M', set.metal(), 'S', RecipeKey.of(Items.STICK)));
        return enter(
                id, item, GearKind.TOOL, ModelKind.HANDHELD, set.adjective() + " " + kind.displayName(),
                List.of(kind.tag()), recipe);
    }

    // ------------------------------------------------------------------ armour

    /**
     * The four pieces of an armour set, with vanilla armour shapes of {@code material}.
     *
     * @param adjective English adjective, for example {@code Gondorian} ("Gondorian Helmet")
     * @param table crafting table; {@code null} recipes are not generated
     * @param material what the pieces are made of
     */
    public static ArmorPieces armor(ArmorSet set, String adjective, CraftTable table, RecipeKey material) {
        return new ArmorPieces(
                piece(set, adjective, table, material, ArmorItem.Type.HELMET),
                piece(set, adjective, table, material, ArmorItem.Type.CHESTPLATE),
                piece(set, adjective, table, material, ArmorItem.Type.LEGGINGS),
                piece(set, adjective, table, material, ArmorItem.Type.BOOTS));
    }

    private static DeferredItem<ArmorItem> piece(
            ArmorSet set, String adjective, CraftTable table, RecipeKey material, ArmorItem.Type type) {
        String id = set.id() + "_" + type.getName();
        DeferredItem<ArmorItem> item = ModRegistries.ITEMS.register(
                id,
                () -> new ArmorItem(set.material(), type, new Item.Properties().durability(set.durability(type))));
        List<TagKey<Item>> tags = new ArrayList<>();
        switch (type) {
            case HELMET -> {
                tags.add(ItemTags.HEAD_ARMOR);
                tags.add(ItemTags.HEAD_ARMOR_ENCHANTABLE);
            }
            case CHESTPLATE -> {
                tags.add(ItemTags.CHEST_ARMOR);
                tags.add(ItemTags.CHEST_ARMOR_ENCHANTABLE);
            }
            case LEGGINGS -> {
                tags.add(ItemTags.LEG_ARMOR);
                tags.add(ItemTags.LEG_ARMOR_ENCHANTABLE);
            }
            default -> {
                tags.add(ItemTags.FOOT_ARMOR);
                tags.add(ItemTags.FOOT_ARMOR_ENCHANTABLE);
            }
        }
        if (set.faction() != null) {
            tags.add(ModTags.Items.armor(set.faction()));
        }
        List<String> shape =
                switch (type) {
                    case HELMET -> List.of("XXX", "X X");
                    case CHESTPLATE -> List.of("X X", "XXX", "XXX");
                    case LEGGINGS -> List.of("XXX", "X X", "X X");
                    default -> List.of("X X", "X X");
                };
        String piece =
                switch (type) {
                    case HELMET -> "Helmet";
                    case CHESTPLATE -> "Chestplate";
                    case LEGGINGS -> "Leggings";
                    default -> "Boots";
                };
        GearRecipe recipe = table == null ? null : GearRecipe.shaped(table, shape, Map.of('X', material));
        enter(id, item, GearKind.ARMOR, ModelKind.GENERATED, adjective + " " + piece, tags, recipe);
        return item;
    }

    // ------------------------------------------------------------------ ranged

    /**
     * A bow. The default item is a plain placeholder {@link BowItem}; the ranged ticket passes a real
     * class through {@link #bow(String, String, Supplier, GearRecipe...)}.
     */
    public static DeferredItem<Item> bow(String id, String name, int durability, GearRecipe... recipes) {
        return bow(id, name, () -> new BowItem(new Item.Properties().durability(durability)), recipes);
    }

    public static DeferredItem<Item> bow(String id, String name, Supplier<? extends Item> factory, GearRecipe... recipes) {
        return ranged(
                id, name, factory, GearKind.BOW, ModelKind.BOW,
                List.of(ModTags.Items.WEAPONS_BOWS, Tags.Items.TOOLS_BOW, ItemTags.BOW_ENCHANTABLE,
                        ItemTags.DURABILITY_ENCHANTABLE),
                recipes);
    }

    /** A crossbow; placeholder {@link CrossbowItem} unless a factory is given. */
    public static DeferredItem<Item> crossbow(String id, String name, int durability, GearRecipe... recipes) {
        return crossbow(id, name, () -> new CrossbowItem(new Item.Properties().durability(durability)), recipes);
    }

    public static DeferredItem<Item> crossbow(
            String id, String name, Supplier<? extends Item> factory, GearRecipe... recipes) {
        return ranged(
                id, name, factory, GearKind.CROSSBOW, ModelKind.CROSSBOW,
                List.of(ModTags.Items.WEAPONS_CROSSBOWS, Tags.Items.TOOLS_CROSSBOW, ItemTags.CROSSBOW_ENCHANTABLE,
                        ItemTags.DURABILITY_ENCHANTABLE),
                recipes);
    }

    /** A sling; placeholder plain item unless a factory is given. */
    public static DeferredItem<Item> sling(String id, String name, int durability, GearRecipe... recipes) {
        return sling(id, name, () -> new Item(new Item.Properties().durability(durability)), recipes);
    }

    public static DeferredItem<Item> sling(String id, String name, Supplier<? extends Item> factory, GearRecipe... recipes) {
        return ranged(
                id, name, factory, GearKind.SLING, ModelKind.GENERATED,
                List.of(ModTags.Items.WEAPONS_SLINGS, ItemTags.DURABILITY_ENCHANTABLE),
                recipes);
    }

    private static DeferredItem<Item> ranged(
            String id,
            String name,
            Supplier<? extends Item> factory,
            GearKind kind,
            ModelKind model,
            List<TagKey<Item>> tags,
            GearRecipe[] recipes) {
        DeferredItem<Item> item = ModRegistries.ITEMS.<Item>register(id, factory);
        GearCatalog.add(new GearEntry(id, item, kind, model, name, List.copyOf(tags), List.of(recipes)));
        return item;
    }

    private static <T extends Item> DeferredItem<T> enter(
            String id,
            DeferredItem<T> item,
            GearKind kind,
            ModelKind model,
            String name,
            List<TagKey<Item>> tags,
            GearRecipe recipe) {
        GearCatalog.add(new GearEntry(
                id, item, kind, model, name, List.copyOf(tags), recipe == null ? List.of() : List.of(recipe)));
        return item;
    }
}
