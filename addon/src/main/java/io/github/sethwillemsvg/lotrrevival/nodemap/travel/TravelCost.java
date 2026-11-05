package io.github.sethwillemsvg.lotrrevival.nodemap.travel;

/**
 * The cost of a journey of length L (blocks, after route factors), spec Rules and numbers / Cost. Pure: no game state.
 * cooldown = min(max, base + L * perBlock); exhaustion = min(cap, L * exhaustionPerBlock); coins = ceil(L / 1000 * coinPer1000).
 */
public record TravelCost(int cooldownBase, double cooldownPerBlock, int cooldownMax, double exhaustionPerBlock, double exhaustionCap,
                         int coinPer1000) {
    public static final TravelCost DEFAULTS = new TravelCost(600, 0.2, 12000, 0.002, 40.0, 0);

    public int cooldown(double length) {
        return (int) Math.min(cooldownMax, Math.round(cooldownBase + Math.max(0, length) * cooldownPerBlock));
    }

    public float exhaustion(double length) {
        return (float) Math.min(exhaustionCap, Math.max(0, length) * exhaustionPerBlock);
    }

    public int coins(double length) {
        return coinPer1000 <= 0 ? 0 : (int) Math.ceil(Math.max(0, length) * coinPer1000 / 1000.0 - 1e-9);
    }

    /** m:ss for a tick count (rounded up to the second). */
    public static String clock(int ticks) {
        int s = (Math.max(0, ticks) + 19) / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
