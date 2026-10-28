package io.github.sethwillemsvg.lotrrevival.core.table;

/** A crafting recipe that only exists on the faction table(s) named by {@link #table()}. */
public interface FactionTableRecipe {
    TableRef table();
}
