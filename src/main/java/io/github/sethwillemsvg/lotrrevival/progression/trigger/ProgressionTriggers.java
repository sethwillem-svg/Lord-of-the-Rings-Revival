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

    private ProgressionTriggers() {}

    /** Touch from {@code ProgressionMod.init} so the class loads before registration. */
    public static void init() {}
}
