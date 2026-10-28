package io.github.sethwillemsvg.lotrrevival.core.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Weapon classes (specs/core/combat-items.md, "Weapon classes"). A class sets the damage offset
 * against the material's sword, the swing-speed factor, the reach and the knockback; the
 * {@link CombatMaterial} sets the base damage, durability and repair item.
 *
 * <p>Recipe shapes use {@code M} for the set's metal and {@code S} for a stick.
 */
public enum WeaponClass {
    SWORD("sword", 0.0, 1.0, 0.0, 0.0, ModTags.Items.WEAPONS_SWORDS, List.of("M", "M", "S")),
    DAGGER("dagger", -3.0, 1.5, -0.75, 0.0, ModTags.Items.WEAPONS_DAGGERS, List.of("M", "S")),
    SPEAR("spear", -1.0, 5.0 / 6.0, 1.5, 0.0, ModTags.Items.WEAPONS_SPEARS, List.of("M", "S", "S")),
    /** Reserved for later sets (glaives, halberds). */
    POLEARM("polearm", 0.0, 2.0 / 3.0, 1.5, 0.0, ModTags.Items.WEAPONS_POLEARMS, List.of("  M", " S ", "S  ")),
    /** The long spear: item ids and tags say {@code pike}. */
    PIKE("pike", 0.0, 0.5, 3.0, 0.0, ModTags.Items.WEAPONS_PIKES, List.of("  M", " S ", "S  ")),
    LANCE("lance", 0.0, 0.5, 3.0, 1.0, ModTags.Items.WEAPONS_LANCES, List.of("  M", " M ", "S  ")),
    BATTLEAXE("battleaxe", 2.0, 0.75, 0.0, 0.0, ModTags.Items.WEAPONS_BATTLEAXES, List.of("MMM", "MSM", " S ")),
    HAMMER("hammer", 2.0, 2.0 / 3.0, 0.0, 1.0, ModTags.Items.WEAPONS_HAMMERS, List.of("MSM", "MSM", " S ")),
    /** Melee numbers only; the thrown damage (50% of the material's sword damage) belongs to the throwing ticket. */
    THROWING_AXE("throwing_axe", -2.0, 1.0, 0.0, 0.0, ModTags.Items.WEAPONS_THROWING, List.of(" M", "SM"));

    /** Attack speed of a sword (attacks per second): vanilla's 1.6, anchoring every class factor. */
    public static final double SWORD_ATTACK_SPEED = 1.6;
    /** The player's base entity interaction range in blocks. */
    public static final double BASE_REACH = 3.0;
    /** The player's base attack damage; item modifiers are added on top of it. */
    public static final double PLAYER_BASE_DAMAGE = 1.0;
    /** The player's base attack speed. */
    public static final double PLAYER_BASE_ATTACK_SPEED = 4.0;

    /** Modifier ids. Damage and speed reuse vanilla's base ids so tooltips show the totals, as for vanilla weapons. */
    public static final ResourceLocation DAMAGE_ID = Item.BASE_ATTACK_DAMAGE_ID;

    public static final ResourceLocation SPEED_ID = Item.BASE_ATTACK_SPEED_ID;
    public static final ResourceLocation REACH_ID = LOTRRevival.id("weapon_reach");
    public static final ResourceLocation KNOCKBACK_ID = LOTRRevival.id("weapon_knockback");

    private final String id;
    private final double damageOffset;
    private final double speedFactor;
    private final double reach;
    private final double knockback;
    private final TagKey<Item> tag;
    private final List<String> recipeShape;

    WeaponClass(
            String id,
            double damageOffset,
            double speedFactor,
            double reach,
            double knockback,
            TagKey<Item> tag,
            List<String> recipeShape) {
        this.id = id;
        this.damageOffset = damageOffset;
        this.speedFactor = speedFactor;
        this.reach = reach;
        this.knockback = knockback;
        this.tag = tag;
        this.recipeShape = recipeShape;
    }

    /** Item id suffix and lang word, for example {@code pike}. */
    public String id() {
        return id;
    }

    /** Damage relative to the material's sword. */
    public double damageOffset() {
        return damageOffset;
    }

    /** Swing speed relative to a sword. */
    public double speedFactor() {
        return speedFactor;
    }

    /** Added entity interaction range in blocks. */
    public double reach() {
        return reach;
    }

    /** Reach as a multiplier on the base 3.0 blocks (x0.75 to x2.0), for NPC attack reach. */
    public double reachFactor() {
        return (BASE_REACH + reach) / BASE_REACH;
    }

    /** Extra knockback (attack knockback attribute). */
    public double knockback() {
        return knockback;
    }

    /** The {@code lotrrevival:weapons/<class>} item tag. */
    public TagKey<Item> tag() {
        return tag;
    }

    /** Rows of the crafting shape; {@code M} is the metal, {@code S} a stick. */
    public List<String> recipeShape() {
        return recipeShape;
    }

    /** Attacks per second (sword 1.6). */
    public double attackSpeed() {
        return SWORD_ATTACK_SPEED * speedFactor;
    }

    /** Total damage per full-strength hit in half-hearts (player base 1 included), as the tooltip shows it. */
    public double damage(CombatMaterial material) {
        return material.swordDamage() + damageOffset;
    }

    /** Only swords sweep (specs: "only class that sweeps"). */
    public boolean sweeps() {
        return this == SWORD;
    }

    /** Whether the class can be thrown: spears (charged) and throwing axes (instant). */
    public boolean thrown() {
        return this == SPEAR || this == THROWING_AXE;
    }

    /** English word for lang names, for example {@code Throwing Axe}. */
    public String displayName() {
        StringBuilder out = new StringBuilder();
        for (String part : id.split("_")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    /**
     * The main-hand attribute modifiers of a weapon of this class and material: damage and speed under
     * vanilla's base ids, reach on the entity interaction range, and knockback when non-zero.
     */
    public ItemAttributeModifiers attributes(CombatMaterial material) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                DAMAGE_ID, damage(material) - PLAYER_BASE_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                SPEED_ID, attackSpeed() - PLAYER_BASE_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        if (reach != 0.0) {
            builder.add(
                    Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        if (knockback != 0.0) {
            builder.add(
                    Attributes.ATTACK_KNOCKBACK,
                    new AttributeModifier(KNOCKBACK_ID, knockback, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        return builder.build();
    }
}
