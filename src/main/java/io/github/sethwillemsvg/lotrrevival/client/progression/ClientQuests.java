package io.github.sethwillemsvg.lotrrevival.client.progression;

import io.github.sethwillemsvg.lotrrevival.progression.network.QuestsDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestsFullPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.PlayerQuests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only client mirror of the local player's quests. Only the payload handlers write to it. Holds plain data
 * only (no client-only classes) so common code may read it. The quest book and HUD screens read from here.
 */
public final class ClientQuests {
    private static PlayerQuests state = PlayerQuests.EMPTY;

    // Chunks of a split full sync collect here until the last one arrives.
    private static final List<QuestInstance> PENDING_ACTIVE = new ArrayList<>();
    private static final List<QuestRecord> PENDING_LOG = new ArrayList<>();
    private static PlayerQuests pendingHeader = PlayerQuests.EMPTY;

    private ClientQuests() {}

    public static PlayerQuests get() {
        return state;
    }

    public static Optional<QuestInstance> tracked() {
        Optional<UUID> id = state.tracked();
        return id.flatMap(state::find);
    }

    public static void applyFull(QuestsFullPayload payload) {
        if (payload.first()) {
            PENDING_ACTIVE.clear();
            PENDING_LOG.clear();
            pendingHeader = payload.part();
        }
        PENDING_ACTIVE.addAll(payload.part().active());
        PENDING_LOG.addAll(payload.part().log());
        if (payload.last()) {
            state = new PlayerQuests(
                    PENDING_ACTIVE,
                    pendingHeader.tracked(),
                    PENDING_LOG,
                    pendingHeader.completedByFaction(),
                    pendingHeader.completedTotal(),
                    pendingHeader.failedTotal(),
                    pendingHeader.bountiesCompleted());
            PENDING_ACTIVE.clear();
            PENDING_LOG.clear();
        }
    }

    public static void applyDelta(QuestsDeltaPayload payload) {
        state = payload.state();
    }

    /** Forgets everything; called when the client leaves a world. */
    public static void reset() {
        state = PlayerQuests.EMPTY;
        PENDING_ACTIVE.clear();
        PENDING_LOG.clear();
        pendingHeader = PlayerQuests.EMPTY;
    }
}
