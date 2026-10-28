package io.github.sethwillemsvg.lotrrevival.client.world;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.api.TravelStatus;
import io.github.sethwillemsvg.lotrrevival.core.api.WaypointClient;
import io.github.sethwillemsvg.lotrrevival.core.api.WaypointView;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.world.network.CustomWaypointEditPayload;
import io.github.sethwillemsvg.lotrrevival.world.network.FastTravelRequestPayload;
import io.github.sethwillemsvg.lotrrevival.world.network.TravelStatusMirror;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointViews;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The World pod's {@link WaypointClient.Backend}, registered once at client setup. The map page (Progression) never
 * touches this class; it calls the static methods on {@link WaypointClient}.
 *
 * <p><b>Reads</b> are built on demand from what the server synced and cost a few dozen object allocations, so a screen
 * may call them each frame but should keep the list it needs for a whole frame in a local variable:
 * <ul>
 *   <li>{@link #waypoints()}: the fixed waypoints from the synced {@code waypoint} registry that are not hidden-and-locked
 *       (sorted by id), then the player's custom waypoints in creation order. State is LOCKED until the region is
 *       unlocked, BARRED when the owning faction's alignment is below 0 (with the faction), otherwise USABLE.
 *       {@code requiredTicks} is the clock needed to travel there from where the player stands now, so it changes as the
 *       player walks. Empty while the player is not in a world.</li>
 *   <li>{@link #clockTicks()}: the travel clock, advanced locally every client tick between server syncs. A waypoint can
 *       be used when {@code clockTicks() >= requiredTicks}.</li>
 *   <li>{@link #customLimit()}: how many custom waypoints the player may hold (5 + one per 5 achievements).</li>
 *   <li>{@link #status()}: the latest {@code world/travel_status} message, and it stays set after the trip ends. Look at
 *       the state: COUNTDOWN means a trip is running ({@code ticksLeft} is as of the last second), CANCELLED carries the
 *       reason, ARRIVED means the trip finished. It is cleared on disconnect.</li>
 * </ul>
 *
 * <p><b>Requests</b> are fire and forget messages to the server, which re-checks everything and answers in chat and with
 * a status message. They do nothing when not connected. Names longer than the wire limit are cut here and are then
 * refused by the server (a custom name is 1 to 32 characters after trimming).
 */
public final class ClientWaypointBackend implements WaypointClient.Backend {
    @Override
    public List<WaypointView> waypoints() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        return WaypointViews.build(
                player.level().registryAccess(),
                ClientTravel.data(),
                faction -> Alignment.get(player, faction),
                player.getX(),
                player.getZ());
    }

    @Override
    public int clockTicks() {
        return ClientTravel.clock();
    }

    @Override
    public int customLimit() {
        return ClientTravel.limit();
    }

    @Override
    public Optional<TravelStatus> status() {
        return TravelStatusMirror.latest();
    }

    @Override
    public void requestTravel(String ref) {
        if (ref != null && ref.length() <= CoreNetwork.MAX_STRING) {
            send(new FastTravelRequestPayload(ref));
        }
    }

    @Override
    public void createCustom(String name) {
        send(CustomWaypointEditPayload.create(wireName(name)));
    }

    @Override
    public void renameCustom(int id, String name) {
        send(CustomWaypointEditPayload.rename(id, wireName(name)));
    }

    @Override
    public void deleteCustom(int id) {
        send(CustomWaypointEditPayload.delete(id));
    }

    private static String wireName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > CustomWaypointEditPayload.WIRE_NAME_LIMIT
                ? name.substring(0, CustomWaypointEditPayload.WIRE_NAME_LIMIT)
                : name;
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
