package io.github.sethwillemsvg.lotrrevival.progression.quest;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** The completion-time reward roll. Takes the random source as a parameter so tests can seed it. */
public final class QuestRewards {
    /** Variance bounds around the base value. */
    public static final float MIN_FACTOR = 0.75f;

    public static final float MAX_FACTOR = 1.25f;
    /** The coin bonus happens 1 in this many rolls. */
    public static final int BONUS_ONE_IN = 12;

    public static final int BONUS_MIN = 2;
    public static final int BONUS_MAX = 5;

    private QuestRewards() {}

    /** Result of a coin roll; {@code bonus} marks the 1-in-12 multiplier. */
    public record CoinRoll(int coins, boolean bonus) {}

    /** Uniform in {@code [0.75, 1.25)}. */
    public static float variance(RandomSource random) {
        return MIN_FACTOR + (MAX_FACTOR - MIN_FACTOR) * random.nextFloat();
    }

    /** Alignment: base x uniform(0.75, 1.25), at least 1. */
    public static float rollAlignment(float base, RandomSource random) {
        return Math.max(1.0f, base * variance(random));
    }

    /** Coins: base x uniform(0.75, 1.25), 1 in 12 times uniform-int(2, 5), at least 1. */
    public static CoinRoll rollCoins(int base, RandomSource random) {
        float value = base * variance(random);
        boolean bonus = random.nextInt(BONUS_ONE_IN) == 0;
        if (bonus) {
            value *= Mth.nextInt(random, BONUS_MIN, BONUS_MAX);
        }
        return new CoinRoll(Math.max(1, Math.round(value)), bonus);
    }
}
