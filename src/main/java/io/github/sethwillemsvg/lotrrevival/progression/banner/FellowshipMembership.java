package io.github.sethwillemsvg.lotrrevival.progression.banner;

import java.util.UUID;

/**
 * How whitelist entries that name a fellowship are resolved. The fellowship package installs the real
 * implementation through {@link BannerProtection#setFellowships}; until then {@link #NONE} matches nobody.
 */
@FunctionalInterface
public interface FellowshipMembership {
    /** Matches nobody. */
    FellowshipMembership NONE = (fellowship, player) -> false;

    /** Whether {@code player} is a current member of the fellowship. */
    boolean isMember(UUID fellowship, UUID player);
}
