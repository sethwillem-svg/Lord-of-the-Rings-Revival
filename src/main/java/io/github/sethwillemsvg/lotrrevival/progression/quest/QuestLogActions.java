package io.github.sethwillemsvg.lotrrevival.progression.quest;

import io.github.sethwillemsvg.lotrrevival.progression.network.AbandonQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.DeleteQuestRecordPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.RemoveQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.TrackQuestPayload;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Server handlers of the quest log payloads. Each returns whether anything changed; bad input changes nothing. */
public final class QuestLogActions {
    private QuestLogActions() {}

    public static boolean abandon(ServerPlayer player, AbandonQuestPayload payload) {
        return Quests.abandon(player, payload.quest());
    }

    public static boolean remove(ServerPlayer player, RemoveQuestPayload payload) {
        return Quests.remove(player, payload.quest());
    }

    public static boolean track(ServerPlayer player, TrackQuestPayload payload) {
        return Quests.track(player, payload.quest());
    }

    /** Deletes the record only when the index is valid and the record at it ended at the time the client saw. */
    public static boolean deleteRecord(ServerPlayer player, DeleteQuestRecordPayload payload) {
        List<QuestRecord> log = Quests.get(player).log();
        if (payload.index() < 0 || payload.index() >= log.size() || log.get(payload.index()).at() != payload.at()) {
            return false;
        }
        return Quests.deleteRecord(player, payload.index());
    }
}
