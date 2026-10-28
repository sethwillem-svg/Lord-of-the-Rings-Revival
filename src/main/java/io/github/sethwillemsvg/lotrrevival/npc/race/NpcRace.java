package io.github.sethwillemsvg.lotrrevival.npc.race;

/**
 * Code-defined NPC races (not a registry: each race needs a model and renderer). Values come from the race base
 * stats table in {@code specs/npc/npc-base.md}. Per-type overrides (Bounder, Shirriff, captains) live on the entity.
 */
public enum NpcRace {
    HOBBIT("hobbit", 16.0, 0.45f, 1.25f, 1.05f, 0.24, 0.8, 1.5, 2.0, 16.0, 0.0, NpcTemperament.FLEE, 1, 3),
    MAN("man", 20.0, 0.6f, 1.8f, 1.62f, 0.23, 0.8, 1.3, 2.0, 16.0, 0.0, NpcTemperament.FIGHT_BACK_FLEE_LOW, 1, 3);

    private final String id;
    private final double maxHealth;
    private final float width;
    private final float height;
    private final float eyeHeight;
    private final double movementSpeed;
    private final double wanderSpeedMultiplier;
    private final double fleeSpeedMultiplier;
    private final double unarmedDamage;
    private final double followRange;
    private final double knockbackResistance;
    private final NpcTemperament temperament;
    private final int xpMin;
    private final int xpMax;

    NpcRace(
            String id,
            double maxHealth,
            float width,
            float height,
            float eyeHeight,
            double movementSpeed,
            double wanderSpeedMultiplier,
            double fleeSpeedMultiplier,
            double unarmedDamage,
            double followRange,
            double knockbackResistance,
            NpcTemperament temperament,
            int xpMin,
            int xpMax) {
        this.id = id;
        this.maxHealth = maxHealth;
        this.width = width;
        this.height = height;
        this.eyeHeight = eyeHeight;
        this.movementSpeed = movementSpeed;
        this.wanderSpeedMultiplier = wanderSpeedMultiplier;
        this.fleeSpeedMultiplier = fleeSpeedMultiplier;
        this.unarmedDamage = unarmedDamage;
        this.followRange = followRange;
        this.knockbackResistance = knockbackResistance;
        this.temperament = temperament;
        this.xpMin = xpMin;
        this.xpMax = xpMax;
    }

    /** Lower-case id used in lang keys and data file names. */
    public String id() {
        return id;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float eyeHeight() {
        return eyeHeight;
    }

    public double movementSpeed() {
        return movementSpeed;
    }

    public double wanderSpeedMultiplier() {
        return wanderSpeedMultiplier;
    }

    public double fleeSpeedMultiplier() {
        return fleeSpeedMultiplier;
    }

    public double unarmedDamage() {
        return unarmedDamage;
    }

    public double followRange() {
        return followRange;
    }

    public double knockbackResistance() {
        return knockbackResistance;
    }

    public NpcTemperament temperament() {
        return temperament;
    }

    public int xpMin() {
        return xpMin;
    }

    public int xpMax() {
        return xpMax;
    }
}
