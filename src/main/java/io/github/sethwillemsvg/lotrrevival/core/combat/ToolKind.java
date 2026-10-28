package io.github.sethwillemsvg.lotrrevival.core.combat;

import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;

/**
 * Vanilla tool shapes made from a {@link CombatMaterial} (bronze tools). Damage and speed are
 * iron's vanilla numbers; the material's damage bonus is added by the tier.
 */
public enum ToolKind {
    PICKAXE("pickaxe", 1.0f, -2.8f, ItemTags.PICKAXES, List.of("MMM", " S ", " S "), PickaxeItem::new),
    AXE("axe", 5.0f, -3.1f, ItemTags.AXES, List.of("MM", "MS", " S"), AxeItem::new),
    SHOVEL("shovel", 1.5f, -3.0f, ItemTags.SHOVELS, List.of("M", "S", "S"), ShovelItem::new),
    HOE("hoe", -2.0f, -1.0f, ItemTags.HOES, List.of("MM", " S", " S"), HoeItem::new);

    private final String id;
    private final float baseDamage;
    private final float speed;
    private final TagKey<Item> tag;
    private final List<String> shape;
    private final BiFunction<Tier, Item.Properties, ? extends Item> factory;

    ToolKind(
            String id,
            float baseDamage,
            float speed,
            TagKey<Item> tag,
            List<String> shape,
            BiFunction<Tier, Item.Properties, ? extends Item> factory) {
        this.id = id;
        this.baseDamage = baseDamage;
        this.speed = speed;
        this.tag = tag;
        this.shape = shape;
        this.factory = factory;
    }

    public String id() {
        return id;
    }

    public float baseDamage() {
        return baseDamage;
    }

    public float speed() {
        return speed;
    }

    public TagKey<Item> tag() {
        return tag;
    }

    public List<String> shape() {
        return shape;
    }

    public String displayName() {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    Item create(CombatMaterial material) {
        Item.Properties props = new Item.Properties()
                .attributes(net.minecraft.world.item.DiggerItem.createAttributes(material, baseDamage, speed));
        return factory.apply(material, props);
    }
}
