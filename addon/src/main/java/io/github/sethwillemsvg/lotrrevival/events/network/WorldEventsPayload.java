package io.github.sethwillemsvg.lotrrevival.events.network;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.client.nemesis.EventMarkerCache;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.events.EventParticipant;
import io.github.sethwillemsvg.lotrrevival.events.WorldEvent;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventManager;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventSavedData;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventType;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRecord;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisSavedData;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sides;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * S2C {@code WorldEventsPayload} (spec Map markers): every event within 1024 blocks in the viewer's dimension plus any event with a
 * nemesis holding a grudge on them, at most 16, nearest first, titles chosen for the viewer's side. Sent on login, dimension
 * change and whenever an event starts, changes or ends.
 */
public record WorldEventsPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<WorldEventsPayload> TYPE = new Type<>(LotrRevival.id("world_events"));

    public record Entry(String id, String type, int x, int z, int radius, String venue, boolean active, String outcome, String titleKey,
                        int color, long start, long end, boolean own) {
        static void write(FriendlyByteBuf b, Entry e) {
            b.writeUtf(e.id);
            b.writeUtf(e.type);
            b.writeVarInt(e.x);
            b.writeVarInt(e.z);
            b.writeVarInt(e.radius);
            b.writeUtf(e.venue);
            b.writeBoolean(e.active);
            b.writeUtf(e.outcome);
            b.writeUtf(e.titleKey);
            b.writeInt(e.color);
            b.writeLong(e.start);
            b.writeLong(e.end);
            b.writeBoolean(e.own);
        }

        static Entry read(FriendlyByteBuf b) {
            return new Entry(b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(), b.readBoolean(), b.readUtf(),
                    b.readUtf(), b.readInt(), b.readLong(), b.readLong(), b.readBoolean());
        }
    }

    public static final StreamCodec<FriendlyByteBuf, WorldEventsPayload> CODEC = StreamCodec.of(
            (b, p) -> b.writeCollection(p.entries, Entry::write), b -> new WorldEventsPayload(b.readList(Entry::read)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(TYPE, CODEC, (p, ctx) -> ctx.enqueueWork(() -> EventMarkerCache.receive(p.entries())));
    }

    public static int sent;

    /** Something changed: every player gets their own list again. */
    public static void changed(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p);
    }

    public static void send(ServerPlayer p) {
        List<Entry> entries = build(p);
        PacketDistributor.sendToPlayer(p, new WorldEventsPayload(entries));
        sent++;
        LotrLog.dbg("events", "markers to {}: {} event(s)", LotrLog.id(p.getUUID()), entries.size());
    }

    public static List<Entry> build(ServerPlayer viewer) {
        ResourceLocation dim = viewer.level().dimension().location();
        Sides.Side mySide = Sides.side(viewer);
        NemesisSavedData nd = NemesisSavedData.get(viewer.getServer());
        record Cand(Entry e, double d2) {}
        List<Cand> out = new ArrayList<>();
        for (WorldEvent ev : WorldEventManager.active()) {
            boolean grudge = false;
            for (UUID id : ev.nemeses.keySet()) {
                NemesisRecord r = nd.find(id);
                if (r != null && r.activeGrudge(viewer.getUUID()) > 0) grudge = true;
            }
            double d2 = sqr(ev.centre.getX() - viewer.getX()) + sqr(ev.centre.getZ() - viewer.getZ());
            if (!grudge && (!ev.dimension.equals(dim) || d2 > 1024.0 * 1024.0)) continue;
            out.add(new Cand(entry(viewer, mySide, ev.summary(), true), d2));
        }
        for (WorldEvent.Summary s : WorldEventSavedData.get(viewer.getServer()).ended) {
            double d2 = sqr(s.x() - viewer.getX()) + sqr(s.z() - viewer.getZ());
            if (!s.dimension().equals(dim) || d2 > 1024.0 * 1024.0) continue;
            out.add(new Cand(entry(viewer, mySide, s, false), d2));
        }
        out.sort(Comparator.comparingDouble(Cand::d2));
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < Math.min(16, out.size()); i++) list.add(out.get(i).e());
        return list;
    }

    private static Entry entry(ServerPlayer viewer, Sides.Side mySide, WorldEvent.Summary s, boolean active) {
        Sides.Side leadSide = s.lead().isEmpty() ? Sides.Side.NONE : Sides.sideOfFaction(viewer.level(), s.lead().get(0));
        String viewerSide = mySide != Sides.Side.NONE && mySide != leadSide ? EventParticipant.OPPOSITION : EventParticipant.LEAD;
        WorldEventType t = WorldEventManager.TYPES.get(s.type());
        String title = t == null ? "lotr_revival.event.title.unknown" : t.titleKey(viewerSide);
        int color = 0xFFAAAAAA;
        if (!s.lead().isEmpty()) {
            var f = Sides.faction(viewer.level(), s.lead().get(0));
            if (f != null && f.getBaseBannerColor() != null) color = 0xFF000000 | f.getBaseBannerColor().getTextureDiffuseColor();
        }
        return new Entry(s.id().toString(), s.type().toString(), s.x(), s.z(), s.radius(), s.venueKind() == null ? "" : s.venueKind(), active,
                s.outcome(), title, color, s.startTick(), s.endTick(), s.owner().equals(viewer.getUUID()));
    }

    private static double sqr(double v) {
        return v * v;
    }
}
