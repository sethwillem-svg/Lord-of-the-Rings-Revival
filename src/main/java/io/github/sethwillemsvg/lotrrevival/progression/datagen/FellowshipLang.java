package io.github.sethwillemsvg.lotrrevival.progression.datagen;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** en_us strings of the fellowship system (commands, notices and refusals). Called from {@link MenuLang#add}. */
public final class FellowshipLang {
    private FellowshipLang() {}

    public static void add(LanguageProvider lang) {
        String p = "fellowship.lotrrevival.";
        // Refusals
        lang.add(p + "error.creation_disabled", "Creating fellowships is disabled on this server.");
        lang.add(p + "error.bad_name", "A fellowship name has 1 to %s letters, digits, spaces, ', - or _.");
        lang.add(p + "error.lead_limit", "You already lead as many fellowships as you may (%s). Earn more achievements to lead another.");
        lang.add(p + "error.name_taken", "A fellowship called %s already exists among those involved.");
        lang.add(p + "error.unknown", "That fellowship does not exist.");
        lang.add(p + "error.no_permission", "Your role in this fellowship does not allow that.");
        lang.add(p + "error.owner_only", "Only the owner of the fellowship can do that.");
        lang.add(p + "error.already_member", "%s is already in this fellowship.");
        lang.add(p + "error.already_invited", "%s has already been invited.");
        lang.add(p + "error.full", "%s is full.");
        lang.add(p + "error.too_many_invites", "This fellowship already has %s pending invites.");
        lang.add(p + "error.disbanded", "That fellowship has been disbanded.");
        lang.add(p + "error.no_invite", "You have no invite to %s.");
        lang.add(p + "error.not_member", "You are not in that fellowship.");
        lang.add(p + "error.owner_cannot_leave", "The owner cannot leave. Transfer ownership or disband the fellowship.");
        lang.add(p + "error.not_in_fellowship", "%s is not in this fellowship.");
        lang.add(p + "error.not_in_named", "You are not in a fellowship called %s.");
        lang.add(p + "error.cannot_remove_owner", "The owner cannot be removed.");
        lang.add(p + "error.not_plain_member", "%s is already an admin.");
        lang.add(p + "error.not_admin", "%s is not an admin.");
        lang.add(p + "error.heir_offline", "%s must be online to receive ownership.");
        lang.add(p + "error.heir_lead_limit", "%s already leads as many fellowships as they may (%s).");
        lang.add(p + "error.unknown_option", "Unknown option %s.");
        lang.add(p + "error.unknown_player", "No player called %s is known.");
        // Outcomes
        lang.add(p + "created", "You founded the fellowship %s.");
        lang.add(p + "invited", "%1$s was invited to %2$s.");
        lang.add(p + "joined", "You joined %s.");
        lang.add(p + "rejected", "You turned down the invite to %s.");
        lang.add(p + "left", "You left %s.");
        lang.add(p + "removed", "%s was removed from the fellowship.");
        lang.add(p + "promoted", "%s is now an admin.");
        lang.add(p + "demoted", "%s is no longer an admin.");
        lang.add(p + "transferred", "%s is now the owner.");
        lang.add(p + "renamed", "The fellowship is now called %s.");
        lang.add(p + "disbanded", "%s was disbanded.");
        lang.add(p + "icon_set", "The fellowship icon is now the item in your hand.");
        lang.add(p + "icon_cleared", "The fellowship icon was cleared.");
        lang.add(p + "option.on", "%s is now on.");
        lang.add(p + "option.off", "%s is now off.");
        lang.add(p + "option.prevent_pvp", "Prevent PvP");
        lang.add(p + "option.prevent_unit_friendly_fire", "Prevent hired-unit friendly fire");
        lang.add(p + "option.show_map", "Show map locations");
        // Invites and notices
        lang.add(p + "invite.received", "%1$s invited you to the fellowship %2$s. %3$s %4$s");
        lang.add(p + "invite.accept_button", "[Accept]");
        lang.add(p + "invite.reject_button", "[Reject]");
        lang.add(p + "notice.invite_accepted", "%1$s accepted your invite to %2$s.");
        lang.add(p + "notice.joined", "%1$s joined %2$s.");
        lang.add(p + "notice.left", "%1$s left %2$s.");
        lang.add(p + "notice.you_removed", "You were removed from %s.");
        lang.add(p + "notice.removed", "%1$s was removed from %2$s.");
        lang.add(p + "notice.transferred", "%1$s is the new owner of %2$s.");
        lang.add(p + "notice.renamed", "%1$s is now called %2$s.");
        lang.add(p + "notice.disbanded", "%s was disbanded.");
        lang.add(p + "notice.pending_invites", "You have %s pending fellowship invites. Use /fellowship list.");
        // Listings
        lang.add(p + "list.header", "Your fellowships (%s):");
        lang.add(p + "list.entry", "%1$s - %2$s, %3$s members");
        lang.add(p + "list.invite", "Invite to %1$s from %2$s");
        lang.add(p + "info.header", "%1$s (%2$s members)");
        lang.add(p + "info.member", "%1$s - %2$s, %3$s");
        lang.add(p + "info.option", "%1$s: %2$s");
        lang.add(p + "role.owner", "owner");
        lang.add(p + "role.admin", "admin");
        lang.add(p + "role.member", "member");
        lang.add(p + "online", "online");
        lang.add(p + "offline", "offline");
        lang.add(p + "on", "on");
        lang.add(p + "off", "off");
        lang.add(p + "admin.list.header", "Fellowships (%s):");
        lang.add(p + "admin.list.entry", "%1$s - owner %2$s, %3$s members, id %4$s");
    }
}
