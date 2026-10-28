package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** English (en_us) lang entries. Replaces the hand-written lang file. */
public final class ModLangProvider extends LanguageProvider {
    private static final List<Consumer<LanguageProvider>> CONTRIBUTORS = new ArrayList<>();

    ModLangProvider(PackOutput output) {
        super(output, LOTRRevival.MOD_ID, "en_us");
    }

    /**
     * Lets a pod add en_us keys (only one provider may write en_us.json). Call from the pod's
     * {@code init} or its datagen entry point; contributors run, in registration order, at the end of
     * {@code addTranslations}. Keys that collide with Core's fail the datagen run.
     */
    public static synchronized void contribute(Consumer<LanguageProvider> contributor) {
        CONTRIBUTORS.add(contributor);
    }

    private static final Map<String, String> FACTION_NAMES = new LinkedHashMap<>();

    static {
        FACTION_NAMES.put("hobbit", "Hobbits");
        FACTION_NAMES.put("bree", "Bree-land");
        FACTION_NAMES.put("dunedain_north", "Dunedain of the North");
        FACTION_NAMES.put("blue_mountains", "Blue Mountains");
        FACTION_NAMES.put("high_elf", "High Elves");
        FACTION_NAMES.put("gundabad", "Gundabad");
        FACTION_NAMES.put("angmar", "Angmar");
        FACTION_NAMES.put("wood_elf", "Woodland Realm");
        FACTION_NAMES.put("dol_guldur", "Dol Guldur");
        FACTION_NAMES.put("dale", "Dale");
        FACTION_NAMES.put("durins_folk", "Durin's Folk");
        FACTION_NAMES.put("lothlorien", "Lothlorien");
        FACTION_NAMES.put("dunland", "Dunland");
        FACTION_NAMES.put("isengard", "Isengard");
        FACTION_NAMES.put("fangorn", "Fangorn");
        FACTION_NAMES.put("rohan", "Rohan");
        FACTION_NAMES.put("gondor", "Gondor");
        FACTION_NAMES.put("mordor", "Mordor");
        FACTION_NAMES.put("dorwinion", "Dorwinion");
        FACTION_NAMES.put("easterling", "Easterlings");
        FACTION_NAMES.put("near_harad", "Near Harad");
        FACTION_NAMES.put("far_harad", "Far Harad");
        FACTION_NAMES.put("harad_jungle", "Southern Jungle Tribes");
        FACTION_NAMES.put("half_troll", "Half-trolls");
        FACTION_NAMES.put("utumno", "Utumno");
        FACTION_NAMES.put("hostile", "Hostile");
        FACTION_NAMES.put("unaligned", "Unaligned");
        FACTION_NAMES.put("hostile_trees", "Hostile Trees");
        FACTION_NAMES.put("ruffians", "Ruffians");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.lotrrevival", "The Lord of the Rings: Revival");
        add("key.categories.lotrrevival", "The Lord of the Rings: Revival");
        add("item.lotrrevival.silver_coin", "Silver Coin");

        for (var key : Factions.REAL) {
            check(key.location().getPath());
        }
        for (var key : Factions.PSEUDO) {
            check(key.location().getPath());
        }
        FACTION_NAMES.forEach((id, name) -> add("faction.lotrrevival." + id, name));

        add("region.lotrrevival.westlands", "Westlands");
        add("region.lotrrevival.rhun", "Rhun");
        add("region.lotrrevival.harad", "Harad");
        add("region.lotrrevival.deeps", "The Deeps");

        add("relation.lotrrevival.ally", "Ally");
        add("relation.lotrrevival.friend", "Friend");
        add("relation.lotrrevival.neutral", "Neutral");
        add("relation.lotrrevival.enemy", "Enemy");
        add("relation.lotrrevival.mortal_enemy", "Mortal enemy");

        add("commands.lotrrevival.faction.list.header", "Factions:");
        add("commands.lotrrevival.faction.list.region", "%s:");
        add("commands.lotrrevival.faction.relation", "%s and %s: %s");
        add("commands.lotrrevival.faction.unknown", "Unknown faction: %s");

        add("commands.lotrrevival.alignment.get.header", "Alignment of %s:");
        add("commands.lotrrevival.alignment.get.none", "%s has no alignment with any faction.");
        add("commands.lotrrevival.alignment.value", "%s: %s (%s)");
        add("commands.lotrrevival.alignment.fixed", "%s has a fixed alignment that cannot be changed.");
        add("commands.lotrrevival.alignment.set", "Set alignment with %s to %s for %s player(s).");
        add("commands.lotrrevival.alignment.add", "Changed alignment with %s by %s for %s player(s).");
        add("commands.lotrrevival.alignment.add.spread", "Applied a kill worth %2$s against %1$s to %3$s player(s).");
        add("commands.lotrrevival.alignment.reset.all", "Reset all alignment for %s player(s).");
        add("commands.lotrrevival.alignment.reset.one", "Reset alignment with %s for %s player(s).");
        add("commands.lotrrevival.pledge.set", "%s is now pledged to %s.");
        add("commands.lotrrevival.pledge.set.failed.unknown_faction", "Cannot pledge %s: unknown faction.");
        add("commands.lotrrevival.pledge.set.failed.not_pledgeable", "%s cannot pledge to %s.");
        add("commands.lotrrevival.pledge.set.failed.already_pledged", "%s is already pledged.");
        add("commands.lotrrevival.pledge.set.failed.on_cooldown", "%s is still on pledge cooldown.");
        add("commands.lotrrevival.pledge.set.failed.rank_too_low", "%s has not reached the pledge rank with %s.");
        add("commands.lotrrevival.pledge.set.failed.mortal_enemy_positive",
                "%s holds positive alignment with a mortal enemy of %s.");
        add("commands.lotrrevival.pledge.set.failed.cancelled", "The pledge of %s to %s was cancelled.");
        add("commands.lotrrevival.pledge.clear", "Broke the pledge of %s.");
        add("commands.lotrrevival.pledge.clear.none", "%s is not pledged.");
        add("commands.lotrrevival.pledge.cooldown", "Set the pledge cooldown of %s to %s ticks.");

        add("alignment.lotrrevival.rank.neutral", "Neutral");
        add("alignment.lotrrevival.rank.hostile", "Hostile");
        add("alignment.lotrrevival.rank_changed", "%s: you are now %s.");
        add("alignment.lotrrevival.drain_first",
                "Holding standing with two factions that are mortal enemies slowly drains both.");
        add("alignment.lotrrevival.drain_notice", "Your standing with %s faction(s) faded.");
        add("pledge.lotrrevival.pledged", "You have pledged yourself to %s.");
        add("pledge.lotrrevival.warning", "You have killed one of the people of %s or their allies. Do it again within a day and your pledge is broken.");
        add("pledge.lotrrevival.broken.voluntary", "You have broken your pledge to %s.");
        add("pledge.lotrrevival.broken.killed_ally", "Your pledge to %s is broken: you killed their people again.");
        add("pledge.lotrrevival.broken.below_rank", "Your pledge to %s is broken: you no longer meet its requirements.");
        add("pledge.lotrrevival.broken.command", "Your pledge to %s was ended.");
        add("pledge.lotrrevival.cooldown_ended", "You may pledge yourself to a faction again.");

        addBrewing();
        io.github.sethwillemsvg.lotrrevival.npc.datagen.NpcLang.addTo(this);
        List<Consumer<LanguageProvider>> contributors;
        synchronized (ModLangProvider.class) {
            contributors = new ArrayList<>(CONTRIBUTORS);
        }
        contributors.forEach(contributor -> contributor.accept(this));
    }

    // Core: brewing
    private void addBrewing() {
        add("item.lotrrevival.wooden_mug", "Wooden Mug");
        add("item.lotrrevival.ale", "Ale");
        add("item.lotrrevival.cider", "Cider");
        add("item.lotrrevival.mead", "Mead");
        add("item.lotrrevival.dwarven_tonic", "Dwarven Tonic");
        add("item.lotrrevival.miruvor", "Miruvor");
        add("drink_strength.lotrrevival.thin", "Thin");
        add("drink_strength.lotrrevival.fair", "Fair");
        add("drink_strength.lotrrevival.hearty", "Hearty");
        add("drink_strength.lotrrevival.stout", "Stout");
        add("drink_strength.lotrrevival.mighty", "Mighty");
        add("tooltip.lotrrevival.alcohol", "Alcohol: %s%%");
        add("block.lotrrevival.brewing_barrel", "Brewing Barrel");
        add("container.lotrrevival.brewing_barrel", "Brewing Barrel");
        add("gui.lotrrevival.brewing_barrel.brew", "Brew");
        add("gui.lotrrevival.brewing_barrel.stop", "Stop");
        add("brewing.lotrrevival.status.empty", "Empty");
        add("brewing.lotrrevival.status.ready", "Ready to brew %s");
        add("brewing.lotrrevival.status.brewing", "Brewing %s (%s)");
        add("brewing.lotrrevival.status.full", "%s (%s), %s left");
    }
    // End Core: brewing

    private static void check(String id) {
        if (!FACTION_NAMES.containsKey(id)) {
            throw new IllegalStateException("Missing faction display name for " + id);
        }
    }
}
