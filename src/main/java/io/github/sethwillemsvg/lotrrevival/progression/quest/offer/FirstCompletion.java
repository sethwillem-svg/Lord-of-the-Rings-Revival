package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGroup;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Awards a quest group's first-completion achievement: grants every remaining criterion of the backing
 * advancement. The normal award flow (toast, broadcast) then runs once, from the advancement progress event.
 */
public final class FirstCompletion {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirstCompletion.class);

    /** Achievement ids already warned about, so a missing advancement is logged once. */
    private static final Set<ResourceLocation> WARNED = new HashSet<>();

    private FirstCompletion() {}

    /**
     * @return whether the advancement was not done before and is done now
     */
    public static boolean award(ServerPlayer player, ResourceLocation groupId) {
        Optional<ResourceLocation> id = Quests.group(player.level().registryAccess(), groupId)
                .flatMap(QuestGroup::firstCompletionAchievement);
        if (id.isEmpty() || player.getServer() == null) {
            return false;
        }
        AdvancementHolder holder = player.getServer().getAdvancements().get(id.get());
        if (holder == null) {
            if (WARNED.add(id.get())) {
                LOGGER.warn("Quest group {} names missing first-completion achievement {}", groupId, id.get());
            }
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return false;
        }
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) {
            player.getAdvancements().award(holder, criterion);
        }
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
