package io.github.sethwillemsvg.lotrrevival.npc.entity;

/** Combat class of an NPC entity type. Fixed per type. */
public enum NpcClass {
    CIVILIAN(1.0f),
    GUARD(2.0f),
    CAPTAIN(5.0f);

    private final float defaultKillValue;

    NpcClass(float defaultKillValue) {
        this.defaultKillValue = defaultKillValue;
    }

    /** Alignment kill value used when no role data entry exists. */
    public float defaultKillValue() {
        return defaultKillValue;
    }
}
