package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.progression.network.FellowshipPositionsPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Member position sync for the map: every 40 ticks, to each player whose client reports the map open, the positions
 * of online co-members in the same dimension, from fellowships with Show map locations on.
 */
public final class FellowshipPositions {
    public static final int INTERVAL_TICKS = 40;

    private static final Set<UUID> VIEWING = new HashSet<>();
    private static final Set<UUID> SENT_NONEMPTY = new HashSet<>();

    private FellowshipPositions() {}

    public static void setViewing(ServerPlayer player, boolean open) {
        if (open) {
            VIEWING.add(player.getUUID());
        } else {
            VIEWING.remove(player.getUUID());
        }
    }

    public static boolean isViewing(UUID player) {
        return VIEWING.contains(player);
    }

    public static void forget(UUID player) {
        VIEWING.remove(player);
        SENT_NONEMPTY.remove(player);
    }

    /** Sends the positions to every viewing player when the tick is on the interval. */
    public static void tick(MinecraftServer server) {
        if (VIEWING.isEmpty() || server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        FellowshipData data = FellowshipData.get(server);
        for (UUID viewerId : new ArrayList<>(VIEWING)) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer == null) {
                forget(viewerId);
                continue;
            }
            List<FellowshipPositionsPayload.Entry> entries = collect(server, data, viewer);
            if (entries.isEmpty() && !SENT_NONEMPTY.contains(viewerId)) {
                continue;
            }
            if (entries.isEmpty()) {
                SENT_NONEMPTY.remove(viewerId);
            } else {
                SENT_NONEMPTY.add(viewerId);
            }
            ProgressionSync.send(viewer, new FellowshipPositionsPayload(entries));
        }
    }

    /** Co-members of the viewer that may be shown on their map. */
    public static List<FellowshipPositionsPayload.Entry> collect(MinecraftServer server, FellowshipData data, ServerPlayer viewer) {
        Map<UUID, ServerPlayer> visible = new LinkedHashMap<>();
        for (Fellowship fellowship : data.of(viewer.getUUID())) {
            if (!fellowship.option(FellowshipOption.SHOW_MAP)) {
                continue;
            }
            for (UUID id : fellowship.allMembers()) {
                if (id.equals(viewer.getUUID()) || visible.containsKey(id)) {
                    continue;
                }
                ServerPlayer other = server.getPlayerList().getPlayer(id);
                if (other != null && other.level().dimension().equals(viewer.level().dimension())) {
                    visible.put(id, other);
                }
            }
        }
        List<FellowshipPositionsPayload.Entry> entries = new ArrayList<>();
        for (ServerPlayer other : visible.values()) {
            entries.add(new FellowshipPositionsPayload.Entry(
                    other.getUUID(),
                    other.getGameProfile().getName(),
                    other.level().dimension().location(),
                    other.getBlockX(),
                    other.getBlockZ()));
        }
        return entries;
    }
}
