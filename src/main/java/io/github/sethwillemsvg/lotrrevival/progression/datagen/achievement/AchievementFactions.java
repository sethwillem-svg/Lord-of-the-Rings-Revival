package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.ANGMAR;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.BLUE_MOUNTAINS;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.BREE;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DALE;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DOL_GULDUR;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DORWINION;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DUNEDAIN_NORTH;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DUNLAND;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.DURINS_FOLK;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.EASTERLING;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.FANGORN;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.FAR_HARAD;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.GONDOR;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.GUNDABAD;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HALF_TROLL;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HARAD_JUNGLE;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HIGH_ELF;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.HOBBIT;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.ISENGARD;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.LOTHLORIEN;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.MORDOR;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.NEAR_HARAD;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.ROHAN;
import static io.github.sethwillemsvg.lotrrevival.core.faction.Factions.WOOD_ELF;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.Relation;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.SortGroup;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.AlignmentRankTrigger;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.KillFactionNpcTrigger;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;

/**
 * The faction to tab table and the per-faction achievement families (rank entries, first-kill entries). One place,
 * used by every tab provider. {@link #validateCoverage} fails datagen if a visible faction is missing here or a
 * ladder rank has no entry.
 */
public final class AchievementFactions {
    private AchievementFactions() {}

    /**
     * One listed faction.
     *
     * @param name display name, as in the faction lang entry
     * @param noun what the first-kill description calls a member
     * @param ranks size of the Core rank ladder (ids {@code r1..rN}), checked against the registry at datagen
     * @param pledgeRank Core pledge rank id, checked against the registry at datagen
     * @param theme icon of the rank entries
     * @param weapon icon of the first-kill entry
     */
    public record Info(
            ResourceKey<Faction> key,
            AchievementTab tab,
            String name,
            String noun,
            String killTitle,
            int ranks,
            String pledgeRank,
            Item theme,
            Item weapon) {
        public String path() {
            return key.location().getPath();
        }
    }

    public static final List<Info> ALL = List.of(
            new Info(HOBBIT, AchievementTab.SHIRE, "Hobbits", "Hobbit", "A Quiet Shire No More", 7, "r2",
                    Items.BREAD, Items.WOODEN_SWORD),
            new Info(BREE, AchievementTab.ERIADOR, "Bree-land", "Bree-lander", "Trouble on the Road to Bree", 7, "r3",
                    Items.LANTERN, Items.IRON_AXE),
            new Info(DUNEDAIN_NORTH, AchievementTab.ERIADOR, "Dunedain of the North", "Ranger of the North",
                    "Bane of the Wandering Watch", 7, "r3", Items.SPYGLASS, Items.IRON_SWORD),
            new Info(BLUE_MOUNTAINS, AchievementTab.ERIADOR, "Blue Mountains", "dwarf of the Blue Mountains",
                    "Delving Deep, Falling Hard", 8, "r3", Items.IRON_PICKAXE, Items.DIAMOND_AXE),
            new Info(HIGH_ELF, AchievementTab.ERIADOR, "High Elves", "High Elf", "Starlight Dimmed", 8, "r3",
                    Items.AMETHYST_SHARD, Items.BOW),
            new Info(ANGMAR, AchievementTab.ERIADOR, "Angmar", "soldier of Angmar", "Cold Iron Shattered", 7, "r3",
                    Items.PACKED_ICE, Items.STONE_SWORD),
            new Info(DUNLAND, AchievementTab.ERIADOR, "Dunland", "Dunlending", "Hillfolk Humbled", 7, "r3",
                    Items.LEATHER, Items.STONE_AXE),
            new Info(GUNDABAD, AchievementTab.MISTY_MOUNTAINS, "Gundabad", "Gundabad orc", "Mountain Fury Silenced",
                    7, "r3", Items.COBBLED_DEEPSLATE, Items.IRON_AXE),
            new Info(WOOD_ELF, AchievementTab.RHOVANION, "Woodland Realm", "Woodland Realm elf",
                    "Hunter in the Canopy", 7, "r2", Items.OAK_LEAVES, Items.CROSSBOW),
            new Info(DOL_GULDUR, AchievementTab.RHOVANION, "Dol Guldur", "servant of Dol Guldur",
                    "A Shadow Leaves the Fortress", 7, "r3", Items.SPIDER_EYE, Items.STONE_SWORD),
            new Info(DALE, AchievementTab.RHOVANION, "Dale", "defender of Dale", "Bells Fall Quiet", 7, "r3",
                    Items.BELL, Items.IRON_SWORD),
            new Info(DURINS_FOLK, AchievementTab.RHOVANION, "Durin's Folk", "dwarf of Durin's Folk",
                    "Stone Meets Stone", 8, "r3", Items.ANVIL, Items.IRON_AXE),
            new Info(DORWINION, AchievementTab.RHOVANION, "Dorwinion", "Dorwinion vintner-guard",
                    "A Vintage Soured", 8, "r3", Items.GLOW_BERRIES, Items.CROSSBOW),
            new Info(LOTHLORIEN, AchievementTab.LOTHLORIEN, "Lothlorien", "Lothlorien elf", "Golden Leaves Fall",
                    8, "r3", Items.GOLDEN_CARROT, Items.BOW),
            new Info(ROHAN, AchievementTab.ROHAN, "Rohan", "Rohirrim rider", "Riders Unhorsed", 7, "r3",
                    Items.SADDLE, Items.TRIDENT),
            new Info(ISENGARD, AchievementTab.ROHAN, "Isengard", "Isengard warrior", "Forge-Fires Dimmed", 8, "r3",
                    Items.BLAST_FURNACE, Items.IRON_AXE),
            new Info(FANGORN, AchievementTab.ROHAN, "Fangorn", "denizen of Fangorn", "Wood for the Fire", 5, "r3",
                    Items.OAK_SAPLING, Items.WOODEN_AXE),
            new Info(GONDOR, AchievementTab.GONDOR, "Gondor", "soldier of Gondor", "Walls Breached", 8, "r3",
                    Items.SHIELD, Items.DIAMOND_SWORD),
            new Info(MORDOR, AchievementTab.MORDOR, "Mordor", "Mordor warrior", "A Spark in the Dark Land", 8, "r3",
                    Items.MAGMA_BLOCK, Items.NETHERITE_SWORD),
            new Info(EASTERLING, AchievementTab.SOUTH_EAST, "Easterlings", "Easterling", "Eastern Banner Torn", 8,
                    "r3", Items.RED_BANNER, Items.IRON_AXE),
            new Info(NEAR_HARAD, AchievementTab.SOUTH_EAST, "Near Harad", "warrior of Near Harad",
                    "Sun-Scorched Spears Broken", 8, "r3", Items.CHISELED_SANDSTONE, Items.GOLDEN_SWORD),
            new Info(FAR_HARAD, AchievementTab.SOUTH_EAST, "Far Harad", "warrior of Far Harad",
                    "Beyond the Burning Sands", 7, "r3", Items.RED_SAND, Items.STONE_AXE),
            new Info(HARAD_JUNGLE, AchievementTab.SOUTH_EAST, "Southern Jungle Tribes", "jungle tribesman",
                    "Lost in the Green Heat", 7, "r3", Items.VINE, Items.BOW),
            new Info(HALF_TROLL, AchievementTab.SOUTH_EAST, "Half-trolls", "half-troll", "Hulking Brute Toppled", 7,
                    "r3", Items.BONE, Items.MACE));

    public static Info of(ResourceKey<Faction> faction) {
        return ALL.stream()
                .filter(info -> info.key().equals(faction))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Faction " + faction.location() + " is not in the achievement table"));
    }

    /** Rank rows of one tab: every listed faction of the tab x every rank of its ladder. */
    public static void addRankRows(AchievementTab tab, Consumer<AchievementRow> out) {
        for (Info info : ALL) {
            if (info.tab() != tab) {
                continue;
            }
            for (int n = 1; n <= info.ranks(); n++) {
                out.accept(rankRow(info, n));
            }
        }
    }

    /** First-kill rows of one tab. */
    public static void addKillRows(AchievementTab tab, Consumer<AchievementRow> out) {
        for (Info info : ALL) {
            if (info.tab() == tab) {
                out.accept(killRow(info));
            }
        }
    }

    private static AchievementRow rankRow(Info info, int n) {
        String rankId = "r" + n;
        String text;
        if (n == 1) {
            text = "Earn your first rank of standing with " + info.name() + ".";
        } else if (rankId.equals(info.pledgeRank())) {
            text = "Reach rank " + n + " with " + info.name() + ", the standing at which you may pledge yourself to them.";
        } else if (n == info.ranks()) {
            text = "Reach the highest rank of standing with " + info.name() + ".";
        } else {
            text = "Rise to rank " + n + " of " + info.ranks() + " in your standing with " + info.name() + ".";
        }
        return AchievementRow.builder(info.tab(), "rank_" + info.path() + "_" + rankId)
                .icon(info.theme())
                .sortGroup(SortGroup.SPECIAL)
                .criterion(rankCriterion(info.key(), rankId))
                .rank(info.key(), rankId)
                .description(text)
                .build();
    }

    private static AchievementRow killRow(Info info) {
        return AchievementRow.builder(info.tab(), "kill_" + info.path())
                .icon(info.weapon())
                .sortGroup(SortGroup.SPECIAL)
                .criterion(ProgressionTriggers.KILL_FACTION_NPC
                        .get()
                        .createCriterion(new KillFactionNpcTrigger.TriggerInstance(
                                Optional.empty(),
                                Optional.of(info.key()),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty())))
                .requires(Relation.ENEMY, info.key())
                .text(info.killTitle(), "Defeat " + article(info.noun()) + " " + info.noun() + " for the first time.")
                .build();
    }

    private static String article(String noun) {
        return "AEIOU".indexOf(Character.toUpperCase(noun.charAt(0))) >= 0 ? "an" : "a";
    }

    /** Criterion that fires once {@code rankId} with {@code faction} is held. */
    public static net.minecraft.advancements.Criterion<AlignmentRankTrigger.TriggerInstance> rankCriterion(
            ResourceKey<Faction> faction, String rankId) {
        return ProgressionTriggers.ALIGNMENT_RANK
                .get()
                .createCriterion(new AlignmentRankTrigger.TriggerInstance(
                        Optional.empty(), faction, Optional.of(rankId)));
    }

    /**
     * Fails datagen when a visible, non-fixed faction is missing from {@link #ALL}, a listed one is hidden, fixed or
     * unknown, or a rank of a ladder (or the pledge rank) differs from the table.
     */
    public static void validateCoverage(List<AchievementRow> rows, Function<ResourceKey<Faction>, Optional<Faction>> lookup) {
        List<String> errors = new ArrayList<>();
        Set<ResourceKey<Faction>> listed = new HashSet<>();
        Set<String> ids = new HashSet<>();
        rows.forEach(row -> ids.add(row.id().getPath()));
        for (Info info : ALL) {
            listed.add(info.key());
            Optional<Faction> faction = lookup.apply(info.key());
            if (faction.isEmpty()) {
                errors.add(info.path() + ": listed faction is not in the registry");
                continue;
            }
            Faction f = faction.get();
            if (f.hidden() || f.fixedAlignment().isPresent()) {
                errors.add(info.path() + ": hidden or fixed-alignment factions must not be listed");
            }
            List<FactionRank> ladder = f.ranks();
            if (ladder.size() != info.ranks()) {
                errors.add(info.path() + ": table says " + info.ranks() + " ranks, ladder has " + ladder.size());
            }
            if (!f.pledgeRank().equals(Optional.of(info.pledgeRank()))) {
                errors.add(info.path() + ": table says pledge rank " + info.pledgeRank() + ", Core says " + f.pledgeRank());
            }
            for (FactionRank rank : ladder) {
                String rowPath = info.tab().id() + "/rank_" + info.path() + "_" + rank.id();
                if (!ids.contains(rowPath)) {
                    errors.add(rowPath + ": missing rank entry");
                }
            }
            if (!ids.contains(info.tab().id() + "/kill_" + info.path())) {
                errors.add(info.path() + ": missing first-kill entry");
            }
        }
        for (ResourceKey<Faction> key : Factions.REAL) {
            Optional<Faction> faction = lookup.apply(key);
            boolean visible = faction.isPresent() && !faction.get().hidden() && faction.get().fixedAlignment().isEmpty();
            if (visible && !listed.contains(key)) {
                errors.add(key.location().getPath() + ": visible faction is not in the achievement faction table");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Achievement faction coverage failed:\n - " + String.join("\n - ", errors));
        }
    }
}
