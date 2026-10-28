package io.github.sethwillemsvg.lotrrevival.npc.trade;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** Lang entries of the trade screen, called from {@code NpcLang}. */
public final class NpcTradeLang {
    private NpcTradeLang() {}

    public static void addTo(LanguageProvider lang) {
        lang.add("npc.lotrrevival.trade.tab.buy", "Buy");
        lang.add("npc.lotrrevival.trade.tab.sell", "Sell");
        lang.add("npc.lotrrevival.trade.tab.units", "Units");
        lang.add("npc.lotrrevival.trade.tab.exchange", "Exchange");
        lang.add("npc.lotrrevival.trade.price", "%s");
        lang.add("npc.lotrrevival.trade.price_tooltip", "Price: %s silver");
        lang.add("npc.lotrrevival.trade.sell_tooltip", "Pays: %s silver per lot");
        lang.add("npc.lotrrevival.trade.any", "Any %s");
        lang.add("npc.lotrrevival.trade.sold_out", "Sold out for now");
        lang.add("npc.lotrrevival.trade.cannot_afford", "You cannot afford this");
        lang.add("npc.lotrrevival.trade.sell.header", "The trader buys:");
        lang.add("npc.lotrrevival.trade.sell.none", "Nothing.");
        lang.add("npc.lotrrevival.trade.sell.value", "Worth: %s silver");
        lang.add("npc.lotrrevival.trade.sell.button", "Sell");
        lang.add("npc.lotrrevival.trade.exchange.break", "Break");
        lang.add("npc.lotrrevival.trade.exchange.combine", "Combine");
        lang.add("npc.lotrrevival.trade.exchange.hint", "Put silver coins in the slot.");
        lang.add("npc.lotrrevival.trade.units.placeholder", "No units are on offer here yet.");
        lang.add("npc.lotrrevival.trade.msg.sold_out", "That is sold out for now.");
        lang.add("npc.lotrrevival.trade.msg.cannot_afford", "You cannot afford that.");
        lang.add("npc.lotrrevival.trade.msg.nothing_sold", "The trader does not want any of that.");
    }
}
