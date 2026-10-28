package io.github.sethwillemsvg.lotrrevival.progression.quest;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import net.neoforged.bus.api.IEventBus;

/** Wires the mini-quest system into {@code ProgressionMod.init}. */
public final class QuestSystem {
    private QuestSystem() {}

    public static void init(IEventBus modBus) {
        ModDataRegistries.registerSynced(QuestGroup.REGISTRY, QuestGroup.CODEC);
        ModDataRegistries.registerSynced(QuestTemplate.REGISTRY, QuestTemplate.CODEC);
        QuestItems.init(modBus);
        QuestSync.init();
    }
}
