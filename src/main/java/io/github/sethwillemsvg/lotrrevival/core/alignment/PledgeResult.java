package io.github.sethwillemsvg.lotrrevival.core.alignment;

/** Outcome of {@link Alignment#tryPledge}. */
public enum PledgeResult {
    SUCCESS,
    UNKNOWN_FACTION,
    NOT_PLEDGEABLE,
    ALREADY_PLEDGED,
    ON_COOLDOWN,
    RANK_TOO_LOW,
    MORTAL_ENEMY_POSITIVE,
    CANCELLED;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
