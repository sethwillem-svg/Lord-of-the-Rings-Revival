package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

/** A member's role in a fellowship. */
public enum FellowshipRole {
    OWNER,
    ADMIN,
    MEMBER;

    /** Owners and admins may invite, remove members, set the icon and toggle the combat options. */
    public boolean canManage() {
        return this != MEMBER;
    }
}
