package io.github.sethwillemsvg.lotrrevival.nemesis;

import net.minecraft.world.BossEvent;

import java.util.Locale;

/** Nemesis ranks (spec Ranks table). Renown thresholds and seats come from the config; the stat table is fixed here. */
public enum Rank {
    MARKED(1.25, 1.10, 2, 0.1, 1, 0, BossEvent.BossBarColor.WHITE),
    CAPTAIN(1.6, 1.20, 4, 0.2, 1, 2, BossEvent.BossBarColor.GREEN),
    COMMANDER(2.2, 1.35, 6, 0.3, 2, 3, BossEvent.BossBarColor.YELLOW),
    WARLORD(3.0, 1.50, 8, 0.4, 3, 4, BossEvent.BossBarColor.PURPLE),
    OVERLORD(4.0, 1.65, 10, 0.5, 4, 6, BossEvent.BossBarColor.RED);

    public final double health, damage, armour, knockback;
    public final int strengths, bodyguards;
    public final BossEvent.BossBarColor color;

    Rank(double health, double damage, double armour, double knockback, int strengths, int bodyguards, BossEvent.BossBarColor color) {
        this.health = health;
        this.damage = damage;
        this.armour = armour;
        this.knockback = knockback;
        this.strengths = strengths;
        this.bodyguards = bodyguards;
        this.color = color;
    }

    public int threshold() {
        return (int) NemesisConfig.listAt(NemesisConfig.RANK_THRESHOLDS, ordinal(), new int[]{0, 15, 35, 70, 120}[ordinal()]);
    }

    public int seats() {
        return (int) NemesisConfig.listAt(NemesisConfig.RANK_SEATS, ordinal(), new int[]{12, 8, 5, 3, 1}[ordinal()]);
    }

    /** Highest rank whose threshold this renown reaches. */
    public static Rank forRenown(int renown) {
        Rank r = MARKED;
        for (Rank k : values()) if (renown >= k.threshold()) r = k;
        return r;
    }

    public Rank below() {
        return this == MARKED ? MARKED : values()[ordinal() - 1];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Rank byName(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MARKED;
        }
    }
}
