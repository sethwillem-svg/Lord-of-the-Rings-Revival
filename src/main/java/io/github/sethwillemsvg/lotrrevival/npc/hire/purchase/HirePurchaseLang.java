package io.github.sethwillemsvg.lotrrevival.npc.hire.purchase;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** Lang entries of the Units tab and the hire flow, called from {@code HireDataGen}. */
public final class HirePurchaseLang {
    private HirePurchaseLang() {}

    public static void addTo(LanguageProvider lang) {
        lang.add("npc.lotrrevival.trade.units.hire", "Hire");
        lang.add("npc.lotrrevival.trade.units.cost", "%s silver");
        lang.add("npc.lotrrevival.trade.units.required", "Needs %s alignment");
        lang.add("npc.lotrrevival.trade.units.mounted", "%s on a %s");
        lang.add("npc.lotrrevival.trade.units.pledge.faction", "Pledge to their faction");
        lang.add("npc.lotrrevival.trade.units.pledge.any_elf", "Pledge to an elven faction");
        lang.add("npc.lotrrevival.trade.units.pledge.any_dwarf", "Pledge to a dwarven faction");
        lang.add("npc.lotrrevival.trade.units.reason.alignment", "Your alignment is too low");
        lang.add("npc.lotrrevival.trade.units.reason.pledge", "You are not pledged as required");
        lang.add("npc.lotrrevival.trade.units.reason.coins", "You cannot afford this");
        lang.add("npc.lotrrevival.trade.units.squadron", "Squadron");
        lang.add("npc.lotrrevival.trade.units.squadron_hint", "Squadron name (optional)");
        lang.add("npc.lotrrevival.hire.msg.alignment", "You are not trusted enough to hire that unit.");
        lang.add("npc.lotrrevival.hire.msg.pledge", "You must be pledged to hire that unit.");
        lang.add("npc.lotrrevival.hire.msg.coins", "You cannot afford that.");
        lang.add("npc.lotrrevival.hire.msg.no_room", "There is no room for the unit to stand.");
    }
}
