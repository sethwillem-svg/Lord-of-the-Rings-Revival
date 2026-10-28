package io.github.sethwillemsvg.lotrrevival.core.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every {@link GearEntry}, in creation order (which is creative-tab order). Entries are added by
 * {@link Gear} while the mod initialises; {@link #freeze()} then makes the list immutable.
 */
public final class GearCatalog {
    private static final List<GearEntry> ENTRIES = new ArrayList<>();
    private static boolean frozen;

    private GearCatalog() {}

    static synchronized void add(GearEntry entry) {
        if (frozen) {
            throw new IllegalStateException("Gear catalog is frozen; create gear from a holder's init(): " + entry.id());
        }
        ENTRIES.add(entry);
    }

    /** Called once by {@link CombatItems#init} after every holder ran. */
    static synchronized void freeze() {
        frozen = true;
    }

    /** The entries in catalog order (read-only view). */
    public static synchronized List<GearEntry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }
}
