package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferClosedPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Per-tick work of a giver: the offer roll, the offer's lifetime and the giver position refresh. */
public final class OfferTicker {
    /** Ticks between giver position refreshes. */
    public static final int POSITION_REFRESH_TICKS = 100;

    private OfferTicker() {}

    /** Called once per server tick for every NPC. Cheap for NPCs that are not quest givers. */
    public static void tick(ServerLevel level, NpcEntity npc) {
        Optional<QuestGiverData> existing = npc.getExistingData(ProgressionAttachments.QUEST_GIVER);
        if (existing.isPresent() && existing.get().offer().isPresent()) {
            ageOffer(npc, existing.get().offer().get());
        } else if (npc.getType().is(OfferTags.QUEST_GIVERS)) {
            maybeRoll(level, npc);
        }
        if (existing.isPresent()
                && !existing.get().holders().isEmpty()
                && npc.tickCount % POSITION_REFRESH_TICKS == 0) {
            refreshPositions(level, npc, existing.get());
        }
    }

    private static void maybeRoll(ServerLevel level, NpcEntity npc) {
        if (!ProgressionConfig.get(ProgressionConfig.MINI_QUESTS_ENABLED) || !QuestGivers.isIdle(npc)) {
            return;
        }
        Optional<QuestGivers.GroupMatch> group = QuestGivers.matchGroup(level.registryAccess(), npc);
        if (group.isEmpty()) {
            return;
        }
        int rate = OfferRoller.rate(group.get().group().offerRateMultiplier());
        if (OfferRoller.shouldRoll(npc.getRandom(), rate)) {
            OfferService.rollOffer(level, npc, group.get(), npc.getRandom());
        }
    }

    /** Counts the offer down by one tick unless a registered viewer pauses it; drops it at zero. */
    static void ageOffer(NpcEntity npc, QuestGiverData.Offer offer) {
        UUID id = npc.getUUID();
        if (OfferViewers.has(id)) {
            pruneViewers(npc);
        }
        if (OfferViewers.has(id)) {
            return;
        }
        int next = offer.ticksLeft() - 1;
        if (next <= 0) {
            OfferService.clearOffer(npc, QuestOfferClosedPayload.Reason.GONE);
        } else {
            npc.setData(
                    ProgressionAttachments.QUEST_GIVER,
                    OfferService.data(npc).withOffer(Optional.of(offer.withTicksLeft(next))));
        }
    }

    /** Drops viewers that left: offline, dead, in another dimension, or further than 16 blocks. */
    static void pruneViewers(NpcEntity npc) {
        if (npc.getServer() == null) {
            return;
        }
        double range = OfferService.VIEWER_RANGE * OfferService.VIEWER_RANGE;
        for (UUID viewer : OfferViewers.viewers(npc.getUUID())) {
            ServerPlayer player = npc.getServer().getPlayerList().getPlayer(viewer);
            if (player == null || !player.isAlive() || player.level() != npc.level()) {
                OfferViewers.remove(npc.getUUID(), viewer);
            } else if (player.distanceToSqr(npc) > range) {
                OfferViewers.remove(npc.getUUID(), viewer);
                OfferService.close(player, npc.getId(), QuestOfferClosedPayload.Reason.TOO_FAR);
            }
        }
    }

    private static void refreshPositions(ServerLevel level, NpcEntity npc, QuestGiverData data) {
        if (npc.getServer() == null) {
            return;
        }
        GlobalPos pos = GlobalPos.of(level.dimension(), npc.blockPosition());
        for (UUID holder : data.holders()) {
            ServerPlayer player = npc.getServer().getPlayerList().getPlayer(holder);
            if (player != null) {
                Quests.updateGiverPos(player, npc.getUUID(), pos);
            }
        }
    }
}
