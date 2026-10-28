package io.github.sethwillemsvg.lotrrevival.client.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipRole;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipView;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.Fellowships;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Plain-data rules of the Fellowships pages: which actions a role is offered, name hints and the lead count. The
 * server stays the authority; this only decides what the screens show and enable.
 */
public final class FellowshipPageModel {
    private FellowshipPageModel() {}

    /** What the name field currently says about the text typed. */
    public enum NameCheck {
        OK,
        EMPTY,
        TOO_LONG,
        BAD_CHARS,
        TAKEN
    }

    /** Page-level actions offered to a role. */
    public record Actions(
            boolean invite, boolean setIcon, boolean toggleCombat, boolean toggleMap, boolean rename, boolean leave, boolean disband) {}

    /** Actions offered on one selected member row. */
    public record MemberActions(boolean remove, boolean promote, boolean demote, boolean transfer) {
        public boolean any() {
            return remove || promote || demote || transfer;
        }
    }

    public static Actions actions(FellowshipRole viewer) {
        boolean owner = viewer == FellowshipRole.OWNER;
        return new Actions(viewer.canManage(), viewer.canManage(), viewer.canManage(), owner, owner, !owner, owner);
    }

    /** What the viewer may do to another member. Transfer needs the heir online (the server checks it too). */
    public static MemberActions memberActions(FellowshipRole viewer, FellowshipRole target, boolean targetOnline, boolean targetIsViewer) {
        if (targetIsViewer || target == FellowshipRole.OWNER || !viewer.canManage()) {
            return new MemberActions(false, false, false, false);
        }
        boolean owner = viewer == FellowshipRole.OWNER;
        boolean remove = owner || target == FellowshipRole.MEMBER;
        return new MemberActions(
                remove,
                owner && target == FellowshipRole.MEMBER,
                owner && target == FellowshipRole.ADMIN,
                owner && targetOnline);
    }

    /**
     * Checks a fellowship name the way the server does. {@code existing} holds the names of the viewer's own
     * fellowships (the server also checks the other members', which the client cannot see); {@code ignore} is a name
     * that may be kept (the one being renamed).
     */
    public static NameCheck checkName(String raw, Collection<String> existing, String ignore) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            return NameCheck.EMPTY;
        }
        if (name.length() > Fellowships.NAME_MAX) {
            return NameCheck.TOO_LONG;
        }
        if (Fellowships.validName(name).isEmpty()) {
            return NameCheck.BAD_CHARS;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String other : existing) {
            if (other.toLowerCase(Locale.ROOT).equals(lower)
                    && (ignore == null || !other.equalsIgnoreCase(ignore))) {
                return NameCheck.TAKEN;
            }
        }
        return NameCheck.OK;
    }

    /** How many of these fellowships the player owns. */
    public static int leadCount(List<FellowshipView.Entry> fellowships, UUID player) {
        int count = 0;
        for (FellowshipView.Entry entry : fellowships) {
            if (entry.roleOf(player).filter(role -> role == FellowshipRole.OWNER).isPresent()) {
                count++;
            }
        }
        return count;
    }

    /** The owner's name, or an empty string. */
    public static String ownerName(FellowshipView.Entry entry) {
        return entry.members().stream()
                .filter(member -> member.role() == FellowshipRole.OWNER)
                .map(FellowshipView.Member::name)
                .findFirst()
                .orElse("");
    }
}
