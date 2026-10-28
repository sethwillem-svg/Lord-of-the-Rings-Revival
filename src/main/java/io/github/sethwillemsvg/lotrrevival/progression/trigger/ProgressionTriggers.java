package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.advancements.CriterionTrigger;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Criterion triggers owned by the Progression pod. */
public final class ProgressionTriggers {
    public static final DeferredHolder<CriterionTrigger<?>, EnterBiomeTrigger> ENTER_BIOME =
            ModRegistries.TRIGGER_TYPES.register("enter_biome", EnterBiomeTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, DistinctBiomesTrigger> DISTINCT_BIOMES =
            ModRegistries.TRIGGER_TYPES.register("distinct_biomes", DistinctBiomesTrigger::new);

    // Fired by listeners in ProgressionTriggerEvents.
    public static final DeferredHolder<CriterionTrigger<?>, AlignmentRankTrigger> ALIGNMENT_RANK =
            ModRegistries.TRIGGER_TYPES.register("alignment_rank", AlignmentRankTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, KillFactionNpcTrigger> KILL_FACTION_NPC =
            ModRegistries.TRIGGER_TYPES.register("kill_faction_npc", KillFactionNpcTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, WearArmorSetTrigger> WEAR_ARMOR_SET =
            ModRegistries.TRIGGER_TYPES.register("wear_armor_set", WearArmorSetTrigger::new);

    // Registered now, fired by later tickets or other pods through their public trigger methods.
    public static final DeferredHolder<CriterionTrigger<?>, TradeTrigger> TRADE =
            ModRegistries.TRIGGER_TYPES.register("trade", TradeTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, CraftAtTableTrigger> CRAFT_AT_TABLE =
            ModRegistries.TRIGGER_TYPES.register("craft_at_table", CraftAtTableTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, HireUnitTrigger> HIRE_UNIT =
            ModRegistries.TRIGGER_TYPES.register("hire_unit", HireUnitTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, CompleteMiniQuestTrigger> COMPLETE_MINI_QUEST =
            ModRegistries.TRIGGER_TYPES.register("complete_mini_quest", CompleteMiniQuestTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, EarnTitleTrigger> EARN_TITLE =
            ModRegistries.TRIGGER_TYPES.register("earn_title", EarnTitleTrigger::new);

    private ProgressionTriggers() {}

    /** Touch from {@code ProgressionMod.init} so the class loads before registration. */
    public static void init() {}
}
