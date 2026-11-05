package io.github.sethwillemsvg.lotrrevival.nodemap.net;

import io.github.sethwillemsvg.lotrrevival.client.nodemap.ClientNodeState;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapModule;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeAccess;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.sevenstars.middleearth.network.packets.S2C.PacketOpenMapScreen;
import net.sevenstars.middleearth.permissions.PermissionsME;

/**
 * The node map's packets (spec Packets): registration, the server handlers (sender alive, at most 10 per second,
 * everything re-checked), and building node_state for a player. Client handlers only run on the client (lambdas).
 */
public final class NodeMapPayloads {
    private static final int MAX_PER_SECOND = 10;
    private static final Map<UUID, long[]> RATE = new ConcurrentHashMap<>();

    private NodeMapPayloads() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("1");
            r.playToClient(NodeStatePayload.TYPE, NodeStatePayload.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> ClientNodeState.onState(p)));
            r.playToClient(TravelStatusPayload.TYPE, TravelStatusPayload.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> ClientNodeState.onStatus(p)));
            r.playToServer(OpenNodeMapPayload.TYPE, OpenNodeMapPayload.STREAM_CODEC, (p, ctx) -> server(ctx, "open_node_map", sp -> {
                PacketDistributor.sendToPlayer(sp, new PacketOpenMapScreen(PermissionsME.checkMapTeleport(sp)));
                sync(sp);
            }));
            r.playToServer(TravelRequestPayload.TYPE, TravelRequestPayload.STREAM_CODEC, (p, ctx) -> server(ctx, "travel_request",
                    sp -> NodeMapServerHandlers.travelRequest(NodeMapModule.TRAVEL, sp, p.target())));
            r.playToServer(TravelCancelPayload.TYPE, TravelCancelPayload.STREAM_CODEC,
                    (p, ctx) -> server(ctx, "travel_cancel", sp -> NodeMapServerHandlers.cancel(NodeMapModule.TRAVEL, sp)));
            r.playToServer(CampEditPayload.TYPE, CampEditPayload.STREAM_CODEC,
                    (p, ctx) -> server(ctx, "camp_edit", sp -> NodeMapServerHandlers.campEdit(NodeMapModule.CAMPS, sp, p)));
        });
    }

    private static void server(IPayloadContext ctx, String what, java.util.function.Consumer<ServerPlayer> body) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp) || !sp.isAlive()) return;
            long now = System.currentTimeMillis() / 1000;
            long[] rate = RATE.computeIfAbsent(sp.getUUID(), k -> new long[2]);
            if (rate[0] != now) {
                rate[0] = now;
                rate[1] = 0;
            }
            if (++rate[1] > MAX_PER_SECOND) {
                if (rate[1] == MAX_PER_SECOND + 1) LotrLog.warn("nodemap", "{} from {}: over {} packets per second, dropped", what,
                        LotrLog.id(sp.getUUID()), MAX_PER_SECOND);
                return;
            }
            try {
                body.accept(sp);
            } catch (RuntimeException ex) {
                LotrLog.error("nodemap", what + " from " + LotrLog.id(sp.getUUID()) + " failed", ex);
            }
        });
    }

    public static void forget(ServerPlayer p) {
        RATE.remove(p.getUUID());
    }

    /** Sends node_state to the player (live context). */
    public static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, buildState(NodeMapModule.LIVE, p));
    }

    public static NodeStatePayload buildState(NodeMapContext ctx, ServerPlayer p) {
        NodeAccess acc = new NodeAccess(ctx, p);
        NodeMapSettings s = ctx.settings.get();
        List<NodeStatePayload.Barred> barred = new ArrayList<>();
        for (GraphNode n : acc.graph.nodes()) {
            if (n.faction() != null && acc.isKnown(n.ref()) && acc.isBarred(n)) barred.add(new NodeStatePayload.Barred(n.ref().fixed(), n.faction()));
        }
        List<NodeStatePayload.CampInfo> camps = new ArrayList<>();
        for (NodeMapData.Camp c : acc.data.camps()) {
            List<String> links = new ArrayList<>();
            for (GraphEdge e : acc.campLinks()) {
                NodeRef me = NodeRef.camp(c.id());
                if (e.a().equals(me) || e.b().equals(me)) links.add(e.other(me).toString());
            }
            camps.add(new NodeStatePayload.CampInfo(c.id(), c.name(), c.pos(), links));
        }
        List<net.minecraft.resources.ResourceLocation> known = new ArrayList<>(acc.data.known());
        for (GraphNode n : acc.graph.nodes()) if (n.startKnown() && !known.contains(n.ref().fixed())) known.add(n.ref().fixed());
        @Nullable NodeRef last = acc.data.lastNode();
        return new NodeStatePayload(known, new ArrayList<>(acc.data.rumoured()), barred, camps, acc.data.cooldown(),
                last == null ? "" : last.toString(), s.customMax(), s.departRadius(), s.cost().cooldownBase(), s.cost().cooldownPerBlock(),
                s.cost().cooldownMax(), s.cost().exhaustionPerBlock(), s.cost().exhaustionCap(), s.wildFactor(), s.cost().coinPer1000());
    }

    public static void sendStatus(ServerPlayer p, String state, @Nullable NodeRef target, int left, int total, Component reason) {
        PacketDistributor.sendToPlayer(p, new TravelStatusPayload(state, target == null ? "" : target.toString(), left, total, reason));
    }
}
