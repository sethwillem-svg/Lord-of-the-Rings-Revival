package io.github.sethwillemsvg.lotrrevival.progression.quest;

import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestsDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestsFullPayload;
import net.minecraft.server.level.ServerPlayer;

/** Sends the {@code quests} attachment to its owner: full on login, respawn and dimension change, delta on change. */
public final class QuestSync {
    private QuestSync() {}

    /** Registers the full-sync hook and the delta sender with the sync scheduler. Called once from init. */
    public static void init() {
        ProgressionSync.registerFullSync(QuestSync::sendFull);
        ProgressionSync.registerDeltaSender(ProgressionSync.Type.QUESTS, QuestSync::sendDelta);
    }

    public static void sendFull(ServerPlayer player) {
        for (QuestsFullPayload payload : QuestsFullPayload.split(Quests.get(player), player.level().registryAccess())) {
            ProgressionSync.send(player, payload);
        }
    }

    public static void sendDelta(ServerPlayer player) {
        PlayerQuests state = Quests.get(player);
        if (QuestsFullPayload.encodedSize(state, player.level().registryAccess()) > QuestsFullPayload.MAX_BYTES) {
            sendFull(player);
        } else {
            ProgressionSync.send(player, new QuestsDeltaPayload(state));
        }
    }
}
