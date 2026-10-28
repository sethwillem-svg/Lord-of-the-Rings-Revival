package io.github.sethwillemsvg.lotrrevival.core.combat;

/** What a catalog entry is, so datagen picks the right models, tags and recipes. */
public enum GearKind {
    WEAPON,
    ARMOR,
    TOOL,
    BOW,
    CROSSBOW,
    SLING,
    /** Ammunition (pebbles): a flat generated model, tags and recipes only. */
    AMMO
}
