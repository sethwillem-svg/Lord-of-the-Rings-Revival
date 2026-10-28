package io.github.sethwillemsvg.lotrrevival.core.combat;

import net.minecraft.world.item.ArmorItem;
import net.neoforged.neoforge.registries.DeferredItem;

/** The four registered pieces of an armour set. */
public record ArmorPieces(
        DeferredItem<ArmorItem> helmet,
        DeferredItem<ArmorItem> chestplate,
        DeferredItem<ArmorItem> leggings,
        DeferredItem<ArmorItem> boots) {}
