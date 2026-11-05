package io.github.sethwillemsvg.lotrrevival.client.nodemap;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.PathResult;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.NodeStatePayload;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.TravelRequestPayload;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelCost;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sevenstars.middleearth.gui.map.MapScreen;

/**
 * The node panel (spec The map 5): name, description, state, route length, the cooldown and hunger the trip would cost,
 * and a Travel button, greyed out with the reason when travel is not possible. Right of the map, or over the map's left
 * edge in fullscreen. The server checks everything again. TODO(spec N12): Camps tab (camps work through /lotr_revival node camp).
 */
final class NodePanel {
    private static final int W = 150, PAD = 5, BTN_H = 14;
    /** Panel and button rectangles of the last frame: x0, y0, x1, y1. */
    private static int[] panel = null, button = null;
    private static boolean canTravel;

    private NodePanel() {}

    static void draw(MapScreen ms, GuiGraphics g, Font font, int mx, int my) {
        panel = null;
        button = null;
        NodeRef sel = NodeMapOverlay.selected;
        if (sel == null) return;
        GraphNode n = ClientNodeState.node(sel);
        if (n == null) return;
        int[] f = NodeMapOverlay.frame;
        int x = ms.isFullscreen ? f[0] + 4 : f[2] + 6;
        if (!ms.isFullscreen && x + W > g.guiWidth()) x = Math.max(2, f[0] - W - 6);
        int y = f[1];

        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(n.name().copy().withStyle(s -> s.withBold(true)).getVisualOrderText());
        lines.addAll(font.split(n.description(), W - 2 * PAD));
        Component why = null;
        String info = "";
        NodeStatePayload st = ClientNodeState.state();
        GraphNode from = ClientNodeState.departure();
        if (!ms.playerIsInDimension) why = Component.translatableWithFallback("nodemap.lotr_revival.refuse.dimension", "Only in Middle-earth.");
        else if (!ClientNodeState.isKnown(sel)) why = Component.translatableWithFallback("nodemap.lotr_revival.panel.rumoured", "Only rumoured");
        else if (ClientNodeState.isBarred(sel)) why = Component.translatableWithFallback("nodemap.lotr_revival.panel.barred", "Barred to you");
        else if (from == null) why = Component.translatableWithFallback("nodemap.lotr_revival.panel.no_departure", "Not near a known place");
        else if (from.ref().equals(sel)) why = Component.translatableWithFallback("nodemap.lotr_revival.panel.here", "You are here");
        else {
            PathResult p = ClientNodeState.path(from.ref(), sel);
            if (!p.found()) {
                why = p.status() == PathResult.Status.BLOCKED
                        ? Component.translatableWithFallback("nodemap.lotr_revival.panel.blocked", "Road barred at %s", ClientNodeState.node(p.blockedBy()).name())
                        : Component.translatableWithFallback("nodemap.lotr_revival.refuse.no_route", "No known road leads there.");
            } else {
                TravelCost c = ClientNodeState.cost();
                info = Math.round(p.length()) + " blocks, rest " + TravelCost.clock(c.cooldown(p.length())) + ", hunger "
                        + String.format("%.1f", c.exhaustion(p.length()) / 4f);
                if (st != null && st.cooldown() > 0) {
                    why = Component.translatableWithFallback("nodemap.lotr_revival.panel.cooldown", "Rest %s more", TravelCost.clock(st.cooldown()));
                }
            }
        }
        if (!info.isEmpty()) lines.add(Component.literal(info).getVisualOrderText());
        if (why != null) lines.add(why.copy().withStyle(s -> s.withColor(0xFF8080)).getVisualOrderText());
        canTravel = why == null;

        int h = PAD * 3 + lines.size() * (font.lineHeight + 1) + BTN_H;
        panel = new int[] {x, y, x + W, y + h};
        g.fill(x, y, x + W, y + h, 0xE0201810);
        g.renderOutline(x, y, W, h, 0xFF8B6A3A);
        int ty = y + PAD;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x + PAD, ty, 0xFFFFFFFF, false);
            ty += font.lineHeight + 1;
        }
        int bx = x + PAD, by = y + h - PAD - BTN_H;
        button = new int[] {bx, by, x + W - PAD, by + BTN_H};
        boolean over = in(button, mx, my);
        g.fill(button[0], button[1], button[2], button[3], !canTravel ? 0xFF404040 : over ? 0xFF8B6A3A : 0xFF5A4428);
        Component label = Component.translatableWithFallback("nodemap.lotr_revival.panel.travel", "Travel");
        g.drawCenteredString(font, label, (button[0] + button[2]) / 2, by + 3, canTravel ? 0xFFFFFFFF : 0xFF909090);
    }

    private static boolean in(@Nullable int[] r, double x, double y) {
        return r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    /** True when the click was on the panel (consumed). */
    static boolean click(MapScreen ms, double x, double y) {
        if (!in(panel, x, y)) return false;
        if (in(button, x, y) && canTravel && NodeMapOverlay.selected != null) {
            LotrLog.dbg("travel", "client: Travel pressed for {}", NodeMapOverlay.selected);
            PacketDistributor.sendToServer(new TravelRequestPayload(NodeMapOverlay.selected.toString()));
            ms.onClose();
        }
        return true;
    }
}
