package io.github.sethwillemsvg.lotrrevival.core.combat;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * One item created through {@link Gear}. Datagen providers read everything they need from here.
 *
 * @param id registry path
 * @param item the registered item
 * @param kind what it is
 * @param model model shape
 * @param name English name
 * @param tags every item tag the item joins
 * @param recipes crafting recipes (may be empty)
 */
public record GearEntry(
        String id,
        Supplier<? extends Item> item,
        GearKind kind,
        ModelKind model,
        String name,
        List<TagKey<Item>> tags,
        List<GearRecipe> recipes) {}
