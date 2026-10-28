package io.github.sethwillemsvg.lotrrevival.core.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

/**
 * One armour set (specs/core/combat-items.md, "Armour"): a protection fraction {@code p} fixes all
 * four pieces, and the uses fix the durability multiplier. Toughness and knockback resistance are 0.
 *
 * @param id set id, also the texture name {@code textures/models/armor/<id>_layer_N.png}
 * @param p protection fraction
 * @param uses material uses (durability multiplier = round(uses x 0.06))
 * @param enchantability 9 for metal sets, 15 for leather and wool
 * @param faction faction whose {@code armor/<faction>} tag the pieces join; null for generic sets
 * @param material the registered armour material
 */
public record ArmorSet(
        String id,
        double p,
        int uses,
        int enchantability,
        @Nullable ResourceKey<Faction> faction,
        Holder<ArmorMaterial> material) {
    /** Helmet, chestplate, leggings, boots: the slots a set covers. */
    public static final List<ArmorItem.Type> PIECES =
            List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS);

    /** Protection from {@code p}: helmet 14%, chest 40%, legs 32%, boots 14% of 25 points, rounded. */
    public static int points(double p, ArmorItem.Type type) {
        double share =
                switch (type) {
                    case HELMET, BOOTS -> 0.14;
                    case CHESTPLATE -> 0.40;
                    case LEGGINGS -> 0.32;
                    case BODY -> 0.0;
                };
        return (int) Math.round(25.0 * p * share);
    }

    /** Durability multiplier applied to vanilla's per-slot base (11/16/15/13). */
    public static int durabilityMultiplier(int uses) {
        return (int) Math.round(uses * 0.06);
    }

    public int points(ArmorItem.Type type) {
        return points(p, type);
    }

    public int durabilityMultiplier() {
        return durabilityMultiplier(uses);
    }

    /** Max durability of one piece. */
    public int durability(ArmorItem.Type type) {
        return type.getDurability(durabilityMultiplier());
    }

    /**
     * Registers the armour material and returns the set. Call from a set holder's static
     * initialiser; the material rides on {@code ModRegistries.ARMOR_MATERIALS}.
     *
     * @param repair repair ingredient (an ingot tag, leather, wool...)
     */
    public static ArmorSet register(
            String id,
            double p,
            int uses,
            int enchantability,
            Supplier<Ingredient> repair,
            @Nullable ResourceKey<Faction> faction,
            Holder<SoundEvent> equipSound) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : PIECES) {
            defense.put(type, points(p, type));
        }
        var holder = ModRegistries.ARMOR_MATERIALS.register(
                id,
                () -> new ArmorMaterial(
                        defense,
                        enchantability,
                        equipSound,
                        repair,
                        List.of(new ArmorMaterial.Layer(LOTRRevival.id(id))),
                        0.0f,
                        0.0f));
        return new ArmorSet(id, p, uses, enchantability, faction, holder);
    }

    /** Metal set: enchantability 9, iron equip sound. */
    public static ArmorSet metal(
            String id, double p, int uses, Supplier<Ingredient> repair, @Nullable ResourceKey<Faction> faction) {
        return register(id, p, uses, 9, repair, faction, SoundEvents.ARMOR_EQUIP_IRON);
    }

    /** Leather or wool set: enchantability 15, leather equip sound. */
    public static ArmorSet cloth(
            String id, double p, int uses, Supplier<Ingredient> repair, @Nullable ResourceKey<Faction> faction) {
        return register(id, p, uses, 15, repair, faction, SoundEvents.ARMOR_EQUIP_LEATHER);
    }
}
