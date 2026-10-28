package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionKind;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.Relation;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.SortGroup;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.KillFactionNpcTrigger;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Rows of the combat tab: first kills of a whole kind of creature (any faction carrying the kind tag). */
public final class CombatAchievements extends AchievementTabProvider {
    public CombatAchievements() {
        super(AchievementTab.COMBAT);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        kind(out, "slay_orc", FactionKind.ORC, Items.IRON_SWORD, "Orc-Cleaver",
                "Defeat an orc of any banner for the first time.");
        kind(out, "slay_troll", FactionKind.TROLL, Items.STONE_AXE, "Stone Breaker",
                "Bring down a troll or troll-blooded brute for the first time.",
                Factions.ANGMAR, Factions.HALF_TROLL);
        kind(out, "slay_elf", FactionKind.ELF, Items.BOW, "Fair Folk, Foul Deed",
                "Defeat an elf of any realm for the first time.",
                Factions.HIGH_ELF, Factions.WOOD_ELF, Factions.LOTHLORIEN, Factions.DORWINION);
        kind(out, "slay_dwarf", FactionKind.DWARF, Items.IRON_PICKAXE, "Beard and Bone",
                "Defeat a dwarf of any hold for the first time.",
                Factions.BLUE_MOUNTAINS, Factions.DURINS_FOLK);
        kind(out, "slay_tree", FactionKind.TREE, Items.IRON_AXE, "Timber",
                "Fell one of the walking trees for the first time.");

        // Awarded by code when a bounty target kills the hunter sent after them (progression.bounty).
        out.accept(row("turned_the_hunt")
                .icon(Items.CROSSBOW)
                .sortGroup(SortGroup.SPECIAL)
                .criterion(CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                .text("The Hunted Bites Back", "Slay the bounty hunter who came for your head.")
                .build());
    }

    @SafeVarargs
    private void kind(
            Consumer<AchievementRow> out,
            String name,
            FactionKind kind,
            Item icon,
            String title,
            String description,
            ResourceKey<Faction>... enemyOf) {
        AchievementRow.Builder builder = row(name)
                .icon(icon)
                .sortGroup(SortGroup.SPECIAL)
                .criterion(ProgressionTriggers.KILL_FACTION_NPC
                        .get()
                        .createCriterion(new KillFactionNpcTrigger.TriggerInstance(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(ModTags.Factions.kind(kind)),
                                Optional.empty(),
                                Optional.empty())))
                .text(title, description);
        if (enemyOf.length > 0) {
            builder.requires(Relation.ENEMY, enemyOf);
        }
        out.accept(builder.build());
    }
}
