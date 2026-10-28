package io.github.sethwillemsvg.lotrrevival.progression;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.BountySystem;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionPayloads;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleDefinition;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleService;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestSystem;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;

/** Entry point for the Progression pod; the Progression pod owns this file. */
public final class ProgressionMod {
    private ProgressionMod() {}

    public static void init(IEventBus modBus) {
        ProgressionConfig.register(ModLoadingContext.get().getActiveContainer());
        ProgressionAttachments.init();
        ProgressionTriggers.init();
        AchievementService.init();
        TitleService.init();
        ModDataRegistries.registerSynced(AchievementEntry.REGISTRY, AchievementEntry.CODEC);
        ModDataRegistries.registerSynced(TitleDefinition.REGISTRY, TitleDefinition.CODEC);
        io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldService.init();
        ModDataRegistries.registerSynced(
                io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldDefinition.REGISTRY,
                io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldDefinition.CODEC);
        QuestSystem.init(modBus);
        BountySystem.init(modBus);
        io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipSync.init();
        io.github.sethwillemsvg.lotrrevival.progression.banner.BannerSystem.init(modBus);
        ProgressionCoreHooks.init();
        modBus.addListener(ProgressionPayloads::register);
    }
}
