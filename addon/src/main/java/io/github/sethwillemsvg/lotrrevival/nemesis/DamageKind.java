package io.github.sethwillemsvg.lotrrevival.nemesis;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Locale;

/** The damage kinds scars and weaknesses talk about. */
public enum DamageKind {
    FIRE, PROJECTILE, EXPLOSION, FALL, DROWNING, MELEE, OTHER;

    public static DamageKind of(DamageSource src) {
        if (src.is(DamageTypeTags.IS_FIRE)) return FIRE;
        if (src.is(DamageTypeTags.IS_PROJECTILE)) return PROJECTILE;
        if (src.is(DamageTypeTags.IS_EXPLOSION)) return EXPLOSION;
        if (src.is(DamageTypeTags.IS_FALL)) return FALL;
        if (src.is(DamageTypeTags.IS_DROWNING)) return DROWNING;
        if (src.getEntity() != null && src.getDirectEntity() == src.getEntity()) return MELEE;
        return OTHER;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DamageKind byKey(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
