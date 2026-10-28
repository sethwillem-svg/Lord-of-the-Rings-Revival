package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.progression.network.FellowshipsDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.FellowshipsFullPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleCache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends each player the fellowships they belong to and their pending invites: full on login, respawn and dimension
 * change, otherwise one delta per changed tick through the progression sync scheduler.
 */
public final class FellowshipSync {
    /** Per player: fellowship ids the client currently holds, to compute removals. */
    private static final Map<UUID, Set<UUID>> SYNCED = new HashMap<>();

    private FellowshipSync() {}

    /** Registers the full-sync hook and the delta sender. Called once from {@link Fellowships} setup. */
    public static void init() {
        ProgressionSync.registerFullSync(FellowshipSync::sendFull);
        ProgressionSync.registerDeltaSender(ProgressionSync.Type.FELLOWSHIPS, FellowshipSync::sendDelta);
    }

    /** Schedules a delta for every listed player that is online. */
    public static void markDirty(MinecraftServer server, Collection<UUID> players) {
        for (UUID id : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                ProgressionSync.markDirty(player, ProgressionSync.Type.FELLOWSHIPS);
            }
        }
    }

    /** Schedules a delta for everyone who shares a fellowship with the player (their online marker changed). */
    public static void markCoMembersDirty(MinecraftServer server, UUID player) {
        Set<UUID> others = new HashSet<>();
        for (Fellowship fellowship : FellowshipData.get(server).of(player)) {
            others.addAll(fellowship.allMembers());
        }
        others.remove(player);
        markDirty(server, others);
    }

    public static void forget(UUID player) {
        SYNCED.remove(player);
    }

    public static void sendFull(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        FellowshipData data = FellowshipData.get(server);
        List<FellowshipView.Entry> entries = entries(server, data, player.getUUID());
        Set<UUID> ids = new HashSet<>();
        entries.forEach(entry -> ids.add(entry.id()));
        SYNCED.put(player.getUUID(), ids);
        ProgressionSync.send(player, new FellowshipsFullPayload(entries, invites(data, player.getUUID())));
    }

    public static void sendDelta(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        FellowshipData data = FellowshipData.get(server);
        List<FellowshipView.Entry> entries = entries(server, data, player.getUUID());
        Set<UUID> now = new HashSet<>();
        entries.forEach(entry -> now.add(entry.id()));
        Set<UUID> before = SYNCED.getOrDefault(player.getUUID(), Set.of());
        List<UUID> removed = new ArrayList<>();
        for (UUID id : before) {
            if (!now.contains(id)) {
                removed.add(id);
            }
        }
        SYNCED.put(player.getUUID(), now);
        ProgressionSync.send(player, new FellowshipsDeltaPayload(entries, removed, invites(data, player.getUUID())));
    }

    private static List<FellowshipView.Entry> entries(MinecraftServer server, FellowshipData data, UUID viewer) {
        List<FellowshipView.Entry> entries = new ArrayList<>();
        for (Fellowship fellowship : data.of(viewer)) {
            entries.add(entry(server, data, fellowship));
        }
        return entries;
    }

    /** The client view of a fellowship, with the cached title of every member. */
    public static FellowshipView.Entry entry(MinecraftServer server, FellowshipData data, Fellowship fellowship) {
        TitleCache titles = TitleCache.get(server);
        List<FellowshipView.Member> members = new ArrayList<>();
        for (UUID id : fellowship.allMembers()) {
            Optional<TitleCache.Entry> title = titles.get(id);
            members.add(new FellowshipView.Member(
                    id,
                    data.nameOf(id),
                    fellowship.role(id).orElse(FellowshipRole.MEMBER),
                    server.getPlayerList().getPlayer(id) != null,
                    title.map(TitleCache.Entry::title),
                    title.map(entry -> entry.color().getName()).orElse("white"),
                    title.map(TitleCache.Entry::feminine).orElse(false)));
        }
        return new FellowshipView.Entry(
                fellowship.id(),
                fellowship.name(),
                fellowship.icon(),
                fellowship.option(FellowshipOption.PREVENT_PVP),
                fellowship.option(FellowshipOption.PREVENT_UNIT_FRIENDLY_FIRE),
                fellowship.option(FellowshipOption.SHOW_MAP),
                members);
    }

    private static List<FellowshipView.Invite> invites(FellowshipData data, UUID viewer) {
        List<FellowshipView.Invite> invites = new ArrayList<>();
        for (Fellowship fellowship : data.invitesFor(viewer)) {
            invites.add(new FellowshipView.Invite(
                    fellowship.id(), fellowship.name(), data.nameOf(fellowship.invites().get(viewer))));
        }
        return invites;
    }
}
