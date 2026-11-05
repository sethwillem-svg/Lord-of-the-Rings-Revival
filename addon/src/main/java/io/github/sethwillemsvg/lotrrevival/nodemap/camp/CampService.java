package io.github.sethwillemsvg.lotrrevival.nodemap.camp;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.Msg;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapStats;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeAccess;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapData;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Players' own camps (spec Custom nodes): make, rename, remove, with the limit, spacing, name and link-range rules. Links
 * are not stored; {@link NodeAccess} works them out per query.
 */
public final class CampService {
    public static final int NAME_MAX = 32;

    /** {@code ok} with the camp, or a refusal code and message. */
    public record Result(boolean ok, @Nullable NodeMapData.Camp camp, String code, Component message) {}

    private final NodeMapContext ctx;

    public CampService(NodeMapContext ctx) {
        this.ctx = ctx;
    }

    @Nullable
    public static String cleanName(String raw) {
        if (raw == null) return null;
        String n = raw.trim();
        return n.isEmpty() || n.length() > NAME_MAX ? null : n;
    }

    private Result no(ServerPlayer p, String op, String code, Component msg) {
        LotrLog.dbg("nodemap", "camp {}: player={} refused({}) pos={},{},{}", op, LotrLog.id(p.getUUID()), code, p.getBlockX(),
                p.getBlockY(), p.getBlockZ());
        NodeMapStats.nodemap("camp_refused." + code);
        p.displayClientMessage(msg, false);
        return new Result(false, null, code, msg);
    }

    public Result create(ServerPlayer p, String rawName) {
        NodeMapSettings s = ctx.settings.get();
        if (!ctx.activeIn(p.level())) return no(p, "create", "not_in_middle_earth", Msg.t("refuse.dimension", "Only in Middle-earth."));
        String name = cleanName(rawName);
        if (name == null) return no(p, "create", "bad_name", Msg.t("camp.bad_name", "A camp name is 1 to %s characters.", NAME_MAX));
        NodeMapData data = NodeMapAttachment.get(p);
        if (data.camps().size() >= Math.min(s.customMax(), NodeMapData.MAX_STORED_CAMPS)) {
            return no(p, "create", "limit", Msg.t("camp.limit", "You already have %s camps.", data.camps().size()));
        }
        BlockPos pos = p.blockPosition();
        double sp2 = (double) s.customSpacing() * s.customSpacing();
        for (NodeMapData.Camp c : data.camps()) {
            double dx = c.pos().getX() - pos.getX(), dz = c.pos().getZ() - pos.getZ();
            if (dx * dx + dz * dz < sp2) {
                return no(p, "create", "too_close", Msg.t("camp.too_close", "Your camp %s is too close.", c.name()));
            }
        }
        NodeAccess acc = new NodeAccess(ctx, p);
        boolean anyLink = acc.graph.near(pos.getX() + 0.5, pos.getZ() + 0.5, s.customLinkRange()).stream()
                .anyMatch(n -> acc.isKnown(n.ref()) && !acc.isBarred(n));
        if (!anyLink) return no(p, "create", "no_link", Msg.t("camp.no_link", "Too far from any known place."));
        NodeMapData.Camp camp = data.addCamp(name, pos, p.level().getGameTime());
        List<String> links = new NodeAccess(ctx, p).campLinks().stream()
                .filter(e -> e.a().equals(NodeRef.camp(camp.id())) || e.b().equals(NodeRef.camp(camp.id())))
                .map(e -> e.other(NodeRef.camp(camp.id())).shortName() + "(" + Math.round(e.length()) + ")").toList();
        LotrLog.dbg("nodemap", "camp create: player={} id={} name=\"{}\" pos={},{},{} links={}", LotrLog.id(p.getUUID()), camp.id(), name,
                pos.getX(), pos.getY(), pos.getZ(), links);
        NodeMapStats.nodemap("camps_made");
        Component msg = Msg.t("camp.made", "Camp %s made.", name);
        p.displayClientMessage(msg, false);
        ctx.onChange.accept(p);
        return new Result(true, camp, "ok", msg);
    }

    public Result rename(ServerPlayer p, int id, String rawName) {
        String name = cleanName(rawName);
        if (name == null) return no(p, "rename", "bad_name", Msg.t("camp.bad_name", "A camp name is 1 to %s characters.", NAME_MAX));
        NodeMapData data = NodeMapAttachment.get(p);
        NodeMapData.Camp c = data.camp(id);
        if (c == null || !data.renameCamp(id, name)) return no(p, "rename", "no_camp", Msg.t("camp.none", "You have no camp %s.", id));
        LotrLog.dbg("nodemap", "camp rename: player={} id={} \"{}\" -> \"{}\" pos={},{},{}", LotrLog.id(p.getUUID()), id, c.name(), name,
                c.pos().getX(), c.pos().getY(), c.pos().getZ());
        ctx.onChange.accept(p);
        return new Result(true, data.camp(id), "ok", Msg.t("camp.renamed", "Camp renamed to %s.", name));
    }

    public Result delete(ServerPlayer p, int id) {
        NodeMapData data = NodeMapAttachment.get(p);
        NodeMapData.Camp c = data.camp(id);
        if (c == null || !data.removeCamp(id)) return no(p, "delete", "no_camp", Msg.t("camp.none", "You have no camp %s.", id));
        if (NodeRef.camp(id).equals(data.lastNode())) data.setLastNode(null);
        LotrLog.dbg("nodemap", "camp delete: player={} id={} name=\"{}\" pos={},{},{}", LotrLog.id(p.getUUID()), id, c.name(),
                c.pos().getX(), c.pos().getY(), c.pos().getZ());
        ctx.onChange.accept(p);
        return new Result(true, c, "ok", Msg.t("camp.removed", "Camp %s removed.", c.name()));
    }

    /** Links of one camp for display: other end and length. */
    public static List<GraphEdge> linksOf(NodeAccess acc, int campId) {
        NodeRef r = NodeRef.camp(campId);
        return acc.campLinks().stream().filter(e -> e.a().equals(r) || e.b().equals(r)).toList();
    }
}
