package io.github.sethwillemsvg.lotrrevival.core.combat;

/** Item model shape generated for a catalog entry. */
public enum ModelKind {
    /** {@code item/handheld} parent. */
    HANDHELD,
    /** Pikes and lances: a {@code handheld} model drawn larger in the hand. */
    HANDHELD_WIDE,
    /** Flat {@code item/generated}: armour pieces and slings. */
    GENERATED,
    /** Bow with pulling_0..2 overrides. */
    BOW,
    /** Crossbow with pulling_0..2, arrow and firework overrides. */
    CROSSBOW
}
