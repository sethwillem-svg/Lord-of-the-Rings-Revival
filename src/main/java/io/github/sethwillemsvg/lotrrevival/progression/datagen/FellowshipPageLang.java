package io.github.sethwillemsvg.lotrrevival.progression.datagen;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** en_us strings of the Fellowships pages in the Middle-earth menu. Called from {@link MenuLang#add}. */
public final class FellowshipPageLang {
    private FellowshipPageLang() {}

    public static void add(LanguageProvider lang) {
        String p = "gui.lotrrevival.menu.fellowships.";
        lang.add(p + "name", "Fellowship name");
        lang.add(p + "name_hint", "New fellowship name");
        lang.add(p + "text_hint", "Player or new name");
        lang.add(p + "create", "Create");
        lang.add(p + "empty", "You are in no fellowship yet.");
        lang.add(p + "lead", "You lead %1$s of %2$s fellowships you may lead.");
        lang.add(p + "lead_full", "You lead %1$s of %2$s: no more for now.");
        lang.add(p + "hint.too_long", "Names have at most %s characters.");
        lang.add(p + "hint.bad_chars", "Use letters, digits, spaces, ', - or _ only.");
        lang.add(p + "hint.taken", "You already have a fellowship with that name.");
        lang.add(p + "hint.empty", "Type a name.");
        lang.add(p + "hint.ok", "Name is fine.");
        lang.add(p + "invite", "Invite: %1$s (from %2$s)");
        lang.add(p + "accept", "Accept");
        lang.add(p + "reject", "Reject");
        lang.add(p + "row", "%1$s members, %2$s");
        lang.add(p + "owner", "Owner: %s");
        lang.add(p + "remove", "Remove");
        lang.add(p + "promote", "Promote");
        lang.add(p + "demote", "Demote");
        lang.add(p + "transfer", "Transfer");
        lang.add(p + "invite_button", "Invite");
        lang.add(p + "rename", "Rename");
        lang.add(p + "icon", "Set icon");
        lang.add(p + "icon.tooltip", "Uses the item in your main hand; an empty hand clears the icon.");
        lang.add(p + "leave", "Leave");
        lang.add(p + "disband", "Disband");
        lang.add(p + "option.on", "On");
        lang.add(p + "option.off", "Off");
        lang.add(p + "confirm.remove", "Remove member?");
        lang.add(p + "confirm.remove.text", "Remove %s from the fellowship?");
        lang.add(p + "confirm.transfer", "Transfer ownership?");
        lang.add(p + "confirm.transfer.text", "Hand the fellowship to %s? You become an admin.");
        lang.add(p + "confirm.leave", "Leave fellowship?");
        lang.add(p + "confirm.leave.text", "Leave %s?");
        lang.add(p + "confirm.disband", "Disband fellowship?");
        lang.add(p + "confirm.disband.text", "Disband %s for everyone? This cannot be undone.");
    }
}
