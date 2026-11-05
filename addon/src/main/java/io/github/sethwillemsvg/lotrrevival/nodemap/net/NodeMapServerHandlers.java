package io.github.sethwillemsvg.lotrrevival.nodemap.net;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.Msg;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapStats;
import io.github.sethwillemsvg.lotrrevival.nodemap.camp.CampService;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelService;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the server does with the node map's client packets, apart from the network plumbing (sender alive, rate limit,
 * main thread), so game tests can call it with their own services. Nothing from the client is trusted: refs are parsed
 * again, ops checked, and every rule runs in {@link TravelService} or {@link CampService}.
 */
public final class NodeMapServerHandlers {
    /** Cancel reason for the HUD and panel Cancel buttons (spec Packets, travel_cancel). */
    public static final String CANCEL_REASON = "player";

    private NodeMapServerHandlers() {}

    /** travel_request: returns true when a charge started. A ref that does not parse is dropped with a WARN. */
    public static boolean travelRequest(TravelService travel, ServerPlayer sp, String target) {
        NodeRef ref = NodeRef.parse(target);
        if (ref == null || (ref.isCamp() && ref.camp() < 0)) {
            LotrLog.warn("travel", "travel_request from {} with a bad ref '{}'", LotrLog.id(sp.getUUID()), target);
            return false;
        }
        return travel.request(sp, ref);
    }

    /** travel_cancel: cancels the sender's own charge with reason "player". Returns true when there was one. */
    public static boolean cancel(TravelService travel, ServerPlayer sp) {
        boolean had = travel.cancel(sp, CANCEL_REASON);
        LotrLog.dbg("travel", "travel_cancel: player={} -> {} at={},{},{} tick={}", LotrLog.id(sp.getUUID()),
                had ? "cancelled(" + CANCEL_REASON + ")" : "ignored(no charge)", sp.getBlockX(), sp.getBlockY(), sp.getBlockZ(),
                sp.level().getGameTime());
        if (!had) NodeMapStats.travel("cancel_ignored");
        return had;
    }

    /**
     * camp_edit: create (name), rename (id, name) or delete (id) through {@link CampService}, which checks the dimension,
     * limit, spacing, link range and name again and only ever touches the sender's own camps.
     */
    public static CampService.Result campEdit(CampService camps, ServerPlayer sp, CampEditPayload p) {
        String op = p.op() == null ? "" : p.op();
        LotrLog.dbg("nodemap", "camp_edit: player={} op={} id={} name=\"{}\" tick={}", LotrLog.id(sp.getUUID()), op, p.id(),
                safe(p.name()), sp.level().getGameTime());
        return switch (op) {
            case "create" -> camps.create(sp, p.name());
            case "rename" -> camps.rename(sp, p.id(), p.name());
            case "delete" -> camps.delete(sp, p.id());
            default -> {
                LotrLog.warn("nodemap", "camp_edit from {} with unknown op '{}'", LotrLog.id(sp.getUUID()), safe(op));
                NodeMapStats.nodemap("camp_refused.bad_op");
                yield new CampService.Result(false, null, "bad_op", Msg.t("camp.bad_op", "That cannot be done to a camp."));
            }
        };
    }

    /** For log lines: control characters out, at most 64 characters. */
    static String safe(@Nullable String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 64; i++) {
            char c = s.charAt(i);
            b.append(c < 32 || c == 127 ? '?' : c);
        }
        return b.toString();
    }
}
