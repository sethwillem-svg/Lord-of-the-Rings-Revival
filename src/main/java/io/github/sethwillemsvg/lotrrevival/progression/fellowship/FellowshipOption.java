package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import java.util.Locale;
import java.util.Optional;

/** The three per-fellowship switches; all start on. */
public enum FellowshipOption {
    PREVENT_PVP(false),
    PREVENT_UNIT_FRIENDLY_FIRE(false),
    SHOW_MAP(true);

    private final boolean ownerOnly;

    FellowshipOption(boolean ownerOnly) {
        this.ownerOnly = ownerOnly;
    }

    /** True when only the owner may toggle it (admins may toggle the others). */
    public boolean ownerOnly() {
        return ownerOnly;
    }

    /** The command and payload spelling, for example {@code prevent_pvp}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<FellowshipOption> byId(String id) {
        for (FellowshipOption option : values()) {
            if (option.id().equalsIgnoreCase(id)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }
}
