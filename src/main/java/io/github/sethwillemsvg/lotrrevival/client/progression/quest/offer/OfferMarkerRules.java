package io.github.sethwillemsvg.lotrrevival.client.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.client.core.ClientAlignment;
import io.github.sethwillemsvg.lotrrevival.client.progression.ClientQuests;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentRules;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferMarkerPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGroup;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Whether the local player gets to see an offer marker. Reads only synced data, so it matches what accept allows. */
final class OfferMarkerRules {
    /** Markers are drawn within this many blocks of the local player (inclusive). */
    static final double RANGE = 12.0;

    private OfferMarkerRules() {}

    /**
     * The quest group to draw the marker for, if the local player could accept the offer: within {@link #RANGE},
     * the offer is public or meant for this player, they are not hostile to the group's faction, reach its
     * {@code min_alignment} and hold no quest from this giver already.
     */
    static Optional<QuestGroup> visibleGroup(
            Player player, Entity giver, QuestOfferMarkerPayload marker, RegistryAccess access) {
        if (marker.group().isEmpty() || !giver.isAlive() || player.distanceToSqr(giver) > RANGE * RANGE) {
            return Optional.empty();
        }
        if (marker.forPlayer().isPresent() && !marker.forPlayer().get().equals(player.getUUID())) {
            return Optional.empty();
        }
        Optional<QuestGroup> group = OfferText.group(access, marker.group().get());
        if (group.isEmpty()) {
            return Optional.empty();
        }
        QuestGroup g = group.get();
        if (ClientAlignment.rank(player, g.faction()).id().equals(AlignmentRules.HOSTILE.id())
                || ClientAlignment.get(player, g.faction()) < g.minAlignment()) {
            return Optional.empty();
        }
        if (ClientQuests.get().hasLiveFrom(giver.getUUID())) {
            return Optional.empty();
        }
        return group;
    }
}
