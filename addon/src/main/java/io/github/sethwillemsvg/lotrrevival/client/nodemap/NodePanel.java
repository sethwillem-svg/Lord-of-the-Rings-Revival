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
 * The node panel (spec The map 5 and 6), right of the map or over its left edge in fullscreen, with two tabs. Place: the
 * selected node's name, description, state, route length, the cooldown, hunger and coins the trip would cost, and a Travel
 * button, greyed out with the reason when travel is not possible (Cancel travel while a charge runs). Camps: see
 * {@link CampsTab}. The server checks everything again.
 */
final class NodePanel {
    static final int W = 150, PAD = 5, BTN_H = 14, TAB_H = 13;

    enum Tab { PLACE, CAMPS }

    /** A clickable rectangle of the last frame: x0, y0, x1, y1, and what a click does. */
    record Hit(int[] rect, Runnable action) {}

    static Tab tab = Tab.PLACE;
    /** Panel rectangle of the last frame, or null when none was drawn. */
    @Nullable private static int[] panel;
    static final List<Hit> HITS = new ArrayList<>();

    private NodePanel() {}

    static Component t(String key, Object... args) {
        return Component.translatable("nodemap.lotr_revival." + key, args);
    }

    static void draw(MapScreen ms, GuiGraphics g, Font font, int mx, int my) {
        panel = null;
        HITS.clear();
        int[] f = NodeMapOverlay.frame;
        int x = ms.isFullscreen ? f[0] + 4 : f[2] + 6;
        if (!ms.isFullscreen && x + W > g.guiWidth()) x = Math.max(2, f[0] - W - 6);
        int y = f[1];
        int maxH = Math.max(60, f[3] - f[1]);

        // tabs
        int half = W / 2;
        tabButton(g, font, new int[] {x, y, x + half, y + TAB_H}, t("tab.place"), Tab.PLACE, mx, my);
        tabButton(g, font, new int[] {x + half, y, x + W, y + TAB_H}, t("tab.camps"), Tab.CAMPS, mx, my);
        int top = y + TAB_H;

        if (tab == Tab.CAMPS) {
            int h = CampsTab.draw(ms, g, font, x, top, W, maxH - TAB_H, mx, my);
            panel = new int[] {x, y, x + W, top + h};
            return;
        }
        int h = drawPlace(ms, g, font, x, top, mx, my);
        panel = new int[] {x, y, x + W, top + h};
    }

    private static void tabButton(GuiGraphics g, Font font, int[] r, Component label, Tab which, int mx, int my) {
        boolean on = tab == which;
        g.fill(r[0], r[1], r[2], r[3], on ? 0xE0201810 : in(r, mx, my) ? 0xE0403020 : 0xE0141008);
        g.renderOutline(r[0], r[1], r[2] - r[0], r[3] - r[1], 0xFF8B6A3A);
        g.drawCenteredString(font, label, (r[0] + r[2]) / 2, r[1] + 3, on ? 0xFFFFE8A0 : 0xFFB0A080);
        HITS.add(new Hit(r, () -> {
            if (tab != which) LotrLog.dbg("nodemap", "client panel: tab {}", which);
            tab = which;
            CampsTab.reset();
        }));
    }

    /** The Place tab below the tabs; returns its height. */
    private static int drawPlace(MapScreen ms, GuiGraphics g, Font font, int x, int y, int mx, int my) {
        NodeRef sel = NodeMapOverlay.selected;
        GraphNode n = sel == null ? null : ClientNodeState.node(sel);
        List<FormattedCharSequence> lines = new ArrayList<>();
        boolean charging = TravelHud.charging();
        if (n == null) {
            lines.addAll(font.split(t("panel.pick"), W - 2 * PAD));
            if (!charging) return box(g, font, x, y, lines, null);
        } else {
            lines.add(n.name().copy().withStyle(s -> s.withBold(true)).getVisualOrderText());
            lines.addAll(font.split(n.description(), W - 2 * PAD));
        }
        Component why = null;
        String info = "";
        NodeStatePayload st = ClientNodeState.state();
        GraphNode from = ClientNodeState.departure();
        if (n == null) {
            why = null;   // nothing selected: only the Cancel button below
        } else if (!ms.playerIsInDimension) {
            why = t("refuse.dimension");
        } else if (!ClientNodeState.isKnown(sel)) {
            why = t("panel.rumoured");
        } else if (ClientNodeState.isBarred(sel)) {
            why = t("panel.barred");
        } else if (from == null) {
            why = t("panel.no_departure");
        } else if (from.ref().equals(sel)) {
            why = t("panel.here");
        } else {
            PathResult p = ClientNodeState.path(from.ref(), sel);
            if (!p.found()) {
                why = p.status() == PathResult.Status.BLOCKED ? t("panel.blocked", ClientNodeState.node(p.blockedBy()).name()) : t("refuse.no_route");
            } else {
                TravelCost c = ClientNodeState.cost();
                info = t("panel.cost", Math.round(p.length()), TravelCost.clock(c.cooldown(p.length())),
                        String.format("%.1f", c.exhaustion(p.length()) / 4f)).getString();
                int coins = c.coins(p.length());
                if (coins > 0) info += " " + t("panel.coins", coins).getString();
                if (st != null && st.cooldown() > 0) why = t("panel.cooldown", TravelCost.clock(st.cooldown()));
            }
        }
        if (!info.isEmpty()) lines.addAll(font.split(Component.literal(info), W - 2 * PAD));
        if (why != null) lines.addAll(font.split(why.copy().withStyle(s -> s.withColor(0xFF8080)), W - 2 * PAD));
        boolean canTravel = why == null && n != null;
        Button b = charging
                ? new Button(t("panel.cancel"), true, () -> TravelHud.sendCancel("panel"))
                : new Button(t("panel.travel"), canTravel, () -> {
                    LotrLog.dbg("travel", "client: Travel pressed for {}", sel);
                    PacketDistributor.sendToServer(new TravelRequestPayload(sel.toString()));
                    ms.onClose();
                });
        return box(g, font, x, y, lines, b, mx, my);
    }

    record Button(Component label, boolean enabled, Runnable action) {}

    private static int box(GuiGraphics g, Font font, int x, int y, List<FormattedCharSequence> lines, @Nullable Button b) {
        return box(g, font, x, y, lines, b, -1, -1);
    }

    private static int box(GuiGraphics g, Font font, int x, int y, List<FormattedCharSequence> lines, @Nullable Button b, int mx, int my) {
        int h = PAD * 2 + lines.size() * (font.lineHeight + 1) + (b == null ? 0 : BTN_H + PAD);
        g.fill(x, y, x + W, y + h, 0xE0201810);
        g.renderOutline(x, y, W, h, 0xFF8B6A3A);
        int ty = y + PAD;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x + PAD, ty, 0xFFFFFFFF, false);
            ty += font.lineHeight + 1;
        }
        if (b != null) {
            int by = y + h - PAD - BTN_H;
            button(g, font, new int[] {x + PAD, by, x + W - PAD, by + BTN_H}, b.label(), b.enabled(), false, mx, my, b.action());
        }
        return h;
    }

    /** Draws a button and, when enabled, registers its click. {@code danger} draws it red. */
    static void button(GuiGraphics g, Font font, int[] r, Component label, boolean enabled, boolean danger, int mx, int my, Runnable action) {
        boolean over = enabled && in(r, mx, my);
        int bg = !enabled ? 0xFF404040 : danger ? (over ? 0xFFA04040 : 0xFF7A3030) : over ? 0xFF8B6A3A : 0xFF5A4428;
        g.fill(r[0], r[1], r[2], r[3], bg);
        g.drawCenteredString(font, label, (r[0] + r[2]) / 2, r[1] + (r[3] - r[1] - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF909090);
        if (enabled) HITS.add(new Hit(r, action));
    }

    static boolean in(@Nullable int[] r, double x, double y) {
        return r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    /** True when the click was on the panel (consumed). Runs the button under it, if any. */
    static boolean click(MapScreen ms, double x, double y) {
        if (!in(panel, x, y)) return false;
        for (Hit h : List.copyOf(HITS)) {
            if (in(h.rect(), x, y)) {
                h.action().run();
                break;
            }
        }
        return true;
    }

    /** True when the wheel turned over the panel (consumed). */
    static boolean scroll(double x, double y, double dy) {
        if (!in(panel, x, y)) return false;
        if (tab == Tab.CAMPS) CampsTab.scroll(dy);
        return true;
    }

    /** Called when the map closes. */
    static void closed() {
        CampsTab.reset();
    }
}
