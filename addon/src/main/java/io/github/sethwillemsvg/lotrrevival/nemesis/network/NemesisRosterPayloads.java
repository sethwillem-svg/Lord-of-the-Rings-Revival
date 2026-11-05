package io.github.sethwillemsvg.lotrrevival.nemesis.network;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.client.nemesis.NemesisScreen;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRecord;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisSavedData;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisTraits;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sides;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** C2S roster request and S2C roster (spec N12): at most one answer per 2 s per player, at most 60 cards. */
public final class NemesisRosterPayloads {
    private static final Map<UUID, Long> LAST_ANSWER = new HashMap<>();
    private static final int MAX_CARDS = 60;

    private NemesisRosterPayloads() {}

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(LotrRevival.id("nemesis_roster_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One card on the Nemeses screen. Hidden traits are sent as "?": strengths once any player has fought it, the weakness only to a
     * viewer who has hit its weak spot (spec Traits: Intel). {@code lastSeenAgo} is in ticks, -1 when it was never seen;
     * {@code lastSeenDim} is empty then.
     */
    public record Card(String id, String name, String rank, boolean challenger, String factionId, Component faction, String side,
                       String status, List<String> scars, List<String> strengths, String weakness, int grudge, boolean ownSide,
                       boolean grudgeDormant, int renown, int lastX, int lastZ, String lastSeenDim, long lastSeenAgo) {
        static void write(RegistryFriendlyByteBuf b, Card c) {
            b.writeUtf(c.id);
            b.writeUtf(c.name);
            b.writeUtf(c.rank);
            b.writeBoolean(c.challenger);
            b.writeUtf(c.factionId);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, c.faction);
            b.writeUtf(c.side);
            b.writeUtf(c.status);
            b.writeCollection(c.scars, FriendlyByteBuf::writeUtf);
            b.writeCollection(c.strengths, FriendlyByteBuf::writeUtf);
            b.writeUtf(c.weakness);
            b.writeVarInt(c.grudge);
            b.writeBoolean(c.ownSide);
            b.writeBoolean(c.grudgeDormant);
            b.writeVarInt(c.renown);
            b.writeVarInt(c.lastX);
            b.writeVarInt(c.lastZ);
            b.writeUtf(c.lastSeenDim);
            b.writeVarLong(c.lastSeenAgo + 1);
        }

        static Card read(RegistryFriendlyByteBuf b) {
            return new Card(b.readUtf(), b.readUtf(), b.readUtf(), b.readBoolean(), b.readUtf(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b),
                    b.readUtf(), b.readUtf(), b.readList(FriendlyByteBuf::readUtf), b.readList(FriendlyByteBuf::readUtf), b.readUtf(), b.readVarInt(),
                    b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(), b.readVarLong() - 1);
        }
    }

    public record Roster(List<Card> cards) implements CustomPacketPayload {
        public static final Type<Roster> TYPE = new Type<>(LotrRevival.id("nemesis_roster"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Roster> CODEC = StreamCodec.of(
                (b, r) -> b.writeCollection(r.cards, (bb, c) -> Card.write((RegistryFriendlyByteBuf) bb, c)),
                b -> new Roster(b.readList(bb -> Card.read((RegistryFriendlyByteBuf) bb))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        var reg = e.registrar("1");
        reg.playToServer(Request.TYPE, Request.CODEC, (p, ctx) -> ctx.enqueueWork(() -> answer(ctx)));
        reg.playToClient(Roster.TYPE, Roster.CODEC, (p, ctx) -> ctx.enqueueWork(() -> NemesisScreen.receive(p.cards())));
    }

    private static void answer(IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        long now = sp.getServer().getTickCount();
        Long last = LAST_ANSWER.get(sp.getUUID());
        if (last != null && now - last < 40) {
            LotrLog.dbg("nemesis", "roster request from {} ignored (rate limit)", LotrLog.id(sp.getUUID()));
            return;
        }
        LAST_ANSWER.put(sp.getUUID(), now);
        List<Card> cards = build(sp);
        ctx.reply(new Roster(cards));
        LotrLog.dbg("nemesis", "roster sent to {}: {} cards", LotrLog.id(sp.getUUID()), cards.size());
    }

    public static List<Card> build(ServerPlayer viewer) {
        NemesisSavedData data = NemesisSavedData.get(viewer.getServer());
        Sides.Side mySide = Sides.side(viewer);
        UUID me = viewer.getUUID();
        long now = viewer.level().getGameTime();
        List<NemesisRecord> recs = new ArrayList<>(data.records.values());
        // over the card limit: the ones hunting the viewer first, then the living, then by rank and renown
        recs.sort(Comparator.comparing((NemesisRecord r) -> -r.grudge(me)).thenComparing(r -> r.alive() ? 0 : 1)
                .thenComparing(r -> -r.rank.ordinal()).thenComparing(r -> -r.renown));
        if (recs.size() > MAX_CARDS) {
            LotrLog.dbg("nemesis", "roster for {}: {} records, {} cards sent", LotrLog.id(me), recs.size(), MAX_CARDS);
            recs = new ArrayList<>(recs.subList(0, MAX_CARDS));
        }
        // shown by faction, then rank (highest first), then renown
        recs.sort(Comparator.comparing((NemesisRecord r) -> r.faction.toString()).thenComparing(r -> -r.rank.ordinal()).thenComparing(r -> -r.renown));
        List<Card> out = new ArrayList<>();
        for (NemesisRecord r : recs) {
            var f = Sides.faction(viewer.level(), r.faction);
            Sides.Side side = Sides.sideOf(f);
            boolean strengthsKnown = !r.knowsStrengths.isEmpty();
            List<String> strengths = new ArrayList<>();
            for (ResourceLocation s : r.strengths) {
                var t = NemesisTraits.get(s);
                strengths.add(strengthsKnown && t != null ? t.nameKey() : "?");
            }
            var w = NemesisTraits.get(r.weakness);
            String weak = r.knowsWeakness.contains(viewer.getUUID()) && w != null ? w.nameKey() : "?";
            List<String> scars = new ArrayList<>();
            r.scars.forEach(k -> scars.add(k.key()));
            var g = r.grudges.get(me);
            boolean seen = r.manifestation > 0 && r.lastDimension != null;
            out.add(new Card(r.shortId(), r.displayName(), r.rank.key(), r.challenger, r.faction.toString(),
                    f == null ? Component.literal(r.faction.toString()) : f.tryGetShortName(), side.name(), r.status.name(), scars, strengths, weak,
                    r.grudge(me), side == mySide && side != Sides.Side.NONE, g != null && g.dormant, r.renown, r.lastPos.getX(),
                    r.lastPos.getZ(), seen ? r.lastDimension.getPath() : "", seen ? Math.max(0, now - r.lastSeenTick) : -1));
        }
        return out;
    }
}
