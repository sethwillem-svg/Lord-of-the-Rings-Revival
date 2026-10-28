package io.github.sethwillemsvg.lotrrevival.core.combat;

/** What a catalog entry is, so datagen picks the right models, tags and recipes. */
public enum GearKind {
    WEAPON,
    ARMOR,
    TOOL,
    BOW,
    CROSSBOW,
    SLING,
    /** Stackable ammunition (pebbles, crossbow bolts): a flat generated model, tags and recipes only. */
    AMMO
}
