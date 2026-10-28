package io.github.sethwillemsvg.lotrrevival.core.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.resources.ResourceLocation;

/**
 * Where a piece of gear is crafted: the vanilla crafting table, one faction table, or a table group
 * tag. Referenced by id only, so gear can name tables before the table blocks exist.
 *
 * @param id block id (or block tag id when {@code tag})
 * @param tag whether {@code id} names a block tag ({@code #lotrrevival:crafting_tables/<group>})
 */
public record CraftTable(ResourceLocation id, boolean tag) {
    public static final CraftTable VANILLA = new CraftTable(ResourceLocation.withDefaultNamespace("crafting_table"), false);

    /** A culture's table, {@code lotrrevival:<culture>_crafting_table} (for example {@code gondor}, {@code ranger}). */
    public static CraftTable culture(String culture) {
        return new CraftTable(LOTRRevival.id(culture + "_crafting_table"), false);
    }

    /** A table group, {@code #lotrrevival:crafting_tables/<group>}. */
    public static CraftTable group(String group) {
        return new CraftTable(LOTRRevival.id("crafting_tables/" + group), true);
    }

    public boolean isVanilla() {
        return !tag && id.equals(VANILLA.id);
    }

    /** The value of the {@code table} field in faction recipe JSON: a block id or {@code #tag}. */
    public String recipeValue() {
        return tag ? "#" + id : id.toString();
    }
}
