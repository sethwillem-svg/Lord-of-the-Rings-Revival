package io.github.sethwillemsvg.lotrrevival.client.nodemap;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.NodeKind;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.PathResult;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.CampEditPayload;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.NodeStatePayload;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.TravelRequestPayload;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sevenstars.middleearth.gui.map.MapScreen;

/**
 * The Camps tab of the node panel (spec The map 6, ticket N12): the player's camps with distance and links, each with
 * Travel, Rename and Remove (Remove asks once more), and Make camp here at the bottom. Names are typed in
 * {@link CampNameScreen}. Everything goes to the server as camp_edit or travel_request, which checks it all again; the
 * list refreshes from the node_state the server sends after any change.
 */
final class CampsTab {
    private static final int ROW_H = 36, SMALL_W = 44, SMALL_H = 12;
    private static int scroll;
    /** Camp id waiting for a second click on Remove, or -1. */
    private static int confirmDelete = -1;

    private CampsTab() {}

    static void reset() {
        confirmDelete = -1;
    }

    static void scroll(double dy) {
        scroll = Math.max(0, scroll - (int) Math.signum(dy));
    }

    /** Draws the tab at x, y within maxH; returns the height used. */
    static int draw(MapScreen ms, GuiGraphics g, Font font, int x, int y, int w, int maxH, int mx, int my) {
        NodeStatePayload st = ClientNodeState.state();
        List<NodeStatePayload.CampInfo> camps = st == null ? List.of() : st.camps();
        int limit = st == null ? 0 : st.campLimit();
        int pad = NodePanel.PAD;
        int headerH = font.lineHeight + 4, footerH = NodePanel.BTN_H + pad;
        int rowsFit = Math.max(1, (maxH - 2 * pad - headerH - footerH) / ROW_H);
        scroll = Math.min(scroll, Math.max(0, camps.size() - rowsFit));
        int shown = Math.min(rowsFit, camps.size() - scroll);
        int listH = camps.isEmpty() ? font.lineHeight * 2 + 4 : shown * ROW_H;
        int h = 2 * pad + headerH + listH + footerH;

        g.fill(x, y, x + w, y + h, 0xE0201810);
        g.renderOutline(x, y, w, h, 0xFF8B6A3A);
        int ty = y + pad;
        Component header = NodePanel.t("camps.header", camps.size(), limit);
        g.drawString(font, header, x + pad, ty, 0xFFFFE8A0, false);
        if (scroll > 0 || scroll + shown < camps.size()) {
            String more = (scroll > 0 ? "▲" : " ") + (scroll + shown < camps.size() ? "▼" : " ");
            g.drawString(font, more, x + w - pad - font.width(more), ty, 0xFFB0A080, false);
        }
        ty += headerH;

        Minecraft mc = Minecraft.getInstance();
        GraphNode from = ClientNodeState.departure();
        if (camps.isEmpty()) {
            for (var l : font.split(NodePanel.t("camps.none"), w - 2 * pad)) {
                g.drawString(font, l, x + pad, ty, 0xFFA0A0A0, false);
                ty += font.lineHeight + 1;
            }
            ty = y + pad + headerH + listH;
        }
        for (int i = scroll; i < scroll + shown; i++) {
            NodeStatePayload.CampInfo c = camps.get(i);
            NodeRef ref = NodeRef.camp(c.id());
            int ry = ty;
            NodeMapOverlay.icon(g, x + pad, ry, NodeKind.CAMP, ref.equals(NodeMapOverlay.selected) ? 3 : 0);
            String name = font.plainSubstrByWidth(c.name(), w - 2 * pad - 12);
            g.drawString(font, name, x + pad + 12, ry + 1, 0xFFFFFFFF, false);
            // a click on the name selects the camp on the map and shows it on the Place tab
            NodePanel.HITS.add(new NodePanel.Hit(new int[] {x + pad, ry, x + w - pad, ry + font.lineHeight + 1}, () -> {
                NodeMapOverlay.selected = ref;
                NodePanel.tab = NodePanel.Tab.PLACE;
                LotrLog.dbg("nodemap", "client camps: select {}", ref);
            }));

            Component sub;
            int subColour = 0xFFB0B0B0;
            if (c.links().isEmpty()) {
                sub = NodePanel.t("camps.no_road");
                subColour = 0xFFFF8080;
            } else if (mc.player != null && ms.playerIsInDimension) {
                long d = Math.round(Math.hypot(mc.player.getX() - (c.pos().getX() + 0.5), mc.player.getZ() - (c.pos().getZ() + 0.5)));
                sub = NodePanel.t("camps.info", d, c.links().size());
            } else {
                sub = NodePanel.t("camps.links", c.links().size());
            }
            g.drawString(font, font.plainSubstrByWidth(sub.getString(), w - 2 * pad), x + pad, ry + font.lineHeight + 2, subColour, false);

            int by = ry + 2 * font.lineHeight + 4;
            boolean canTravel = canTravel(ms, from, ref);
            int bx = x + pad;
            NodePanel.button(g, font, new int[] {bx, by, bx + SMALL_W, by + SMALL_H}, NodePanel.t("camps.travel"), canTravel, false, mx, my, () -> {
                LotrLog.dbg("travel", "client camps: Travel pressed for {}", ref);
                PacketDistributor.sendToServer(new TravelRequestPayload(ref.toString()));
                ms.onClose();
            });
            bx += SMALL_W + 3;
            NodePanel.button(g, font, new int[] {bx, by, bx + SMALL_W, by + SMALL_H}, NodePanel.t("camps.rename"), true, false, mx, my, () -> {
                confirmDelete = -1;
                Minecraft.getInstance().setScreen(new CampNameScreen(ms, NodePanel.t("camps.rename_title"), c.name(), n -> {
                    LotrLog.dbg("nodemap", "client camps: rename {} to \"{}\"", c.id(), n);
                    PacketDistributor.sendToServer(new CampEditPayload("rename", c.id(), n));
                }));
            });
            bx += SMALL_W + 3;
            boolean confirming = confirmDelete == c.id();
            NodePanel.button(g, font, new int[] {bx, by, x + w - pad, by + SMALL_H}, NodePanel.t(confirming ? "camps.confirm" : "camps.remove"),
                    true, confirming, mx, my, () -> {
                        if (confirmDelete != c.id()) {
                            confirmDelete = c.id();
                            return;
                        }
                        confirmDelete = -1;
                        if (ref.equals(NodeMapOverlay.selected)) NodeMapOverlay.selected = null;
                        LotrLog.dbg("nodemap", "client camps: remove {}", c.id());
                        PacketDistributor.sendToServer(new CampEditPayload("delete", c.id(), ""));
                    });
            ty += ROW_H;
        }

        int fy = y + h - pad - NodePanel.BTN_H;
        boolean canMake = ms.playerIsInDimension && camps.size() < limit;
        NodePanel.button(g, font, new int[] {x + pad, fy, x + w - pad, fy + NodePanel.BTN_H}, NodePanel.t("camps.make"), canMake, false, mx, my, () -> {
            confirmDelete = -1;
            Minecraft.getInstance().setScreen(new CampNameScreen(ms, NodePanel.t("camps.make_title"), "", n -> {
                LotrLog.dbg("nodemap", "client camps: make \"{}\"", n);
                PacketDistributor.sendToServer(new CampEditPayload("create", 0, n));
            }));
        });
        return h;
    }

    /** Same checks as the Place tab, short: in Middle-earth, near a known place that is not this camp, a road there, rested. */
    private static boolean canTravel(MapScreen ms, @Nullable GraphNode from, NodeRef to) {
        if (!ms.playerIsInDimension || from == null || from.ref().equals(to)) return false;
        NodeStatePayload st = ClientNodeState.state();
        var player = Minecraft.getInstance().player;
        boolean exempt = player != null && (player.isCreative() || player.isSpectator());
        if (st != null && st.cooldown() > 0 && !exempt) return false;
        PathResult p = ClientNodeState.path(from.ref(), to);
        return p.found();
    }
}
