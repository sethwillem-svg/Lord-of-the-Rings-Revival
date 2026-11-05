package io.github.sethwillemsvg.lotrrevival.client.nodemap;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.RouteKind;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.XZ;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.PathResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.sevenstars.middleearth.gui.map.MapScreen;
import org.joml.Vector2d;

/**
 * Our layer on Middle-earth's map screen (spec Client code): routes, node markers, hover tooltip and the node panel,
 * drawn after Middle-earth draws its screen; clicks on our markers or panel are consumed so Middle-earth does not start a
 * drag. Markers are plain shapes until the marker sheet exists (TODO(spec N13)).
 */
@EventBusSubscriber(modid = LotrRevival.MOD_ID, value = Dist.CLIENT)
public final class NodeMapOverlay {
    private static final int HOVER_PX = 5;

    @Nullable static NodeRef selected;
    @Nullable private static Screen loggedFor;
    @Nullable private static NodeRef hovered;
    /** Map frame of the last frame drawn: x0, y0, x1, y1. */
    static int[] frame = new int[4];

    private NodeMapOverlay() {}

    /**
     * The map's frame on screen. MapWidget's bounds are protected, so until the read-only accessor mixin is approved this
     * repeats MapScreen's layout: 198 x 198 centred (normal), or the whole screen less a 5 px margin (fullscreen).
     * TODO(mixin approval): read startX/startY/getWidth()/getHeight() through MapWidgetAccessor instead.
     */
    static int[] bounds(MapScreen ms, int guiW, int guiH) {
        int m = MapScreen.MARGIN;
        if (ms.isFullscreen) return new int[] {m, m, guiW - m, guiH - m};
        int size = 208 - 2 * m;
        int x0 = guiW / 2 - size / 2, y0 = guiH / 2 - 104 + m;
        return new int[] {x0, y0, x0 + size, y0 + size};
    }

    static double[] project(MapScreen ms, double x, double z) {
        Vector2d v = ms.mapWidget.getMapPointFromWorldCoordinate(new Vector2d(x, z));   // mutates its argument: always a new one
        return new double[] {v.x, v.y};
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof MapScreen ms) || ms.mapWidget == null) return;
        try {
            draw(ms, e.getGuiGraphics(), e.getMouseX(), e.getMouseY());
        } catch (RuntimeException ex) {
            LotrLog.error("nodemap", "client: map layer draw failed", ex);
        }
    }

    private static boolean visible(NodeRef r) {
        return ClientNodeState.isKnown(r) || ClientNodeState.isRumoured(r);
    }

    private static void draw(MapScreen ms, GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        NodeGraph graph = ClientNodeState.graph();
        frame = bounds(ms, g.guiWidth(), g.guiHeight());
        int x0 = frame[0], y0 = frame[1], x1 = frame[2], y1 = frame[3];

        List<GraphNode> nodes = new ArrayList<>(graph.nodes());
        nodes.addAll(ClientNodeState.campNodes());
        List<GraphEdge> edges = new ArrayList<>(graph.edges());
        edges.addAll(ClientNodeState.campEdges());

        // highlighted route from where the player stands to the selection
        Set<String> gold = new HashSet<>();
        GraphNode from = ClientNodeState.departure();
        if (selected != null && from != null) {
            PathResult p = ClientNodeState.path(from.ref(), selected);
            if (p.found()) for (int i = 1; i < p.chain().size(); i++) gold.add(key(p.chain().get(i - 1), p.chain().get(i)));
        }

        g.enableScissor(x0, y0, x1, y1);
        for (GraphEdge ed : edges) {
            boolean ka = ClientNodeState.isKnown(ed.a()), kb = ClientNodeState.isKnown(ed.b());
            if (!(ka || kb) || !visible(ed.a()) || !visible(ed.b())) continue;
            boolean faint = !(ka && kb);
            boolean hl = gold.contains(key(ed.a(), ed.b()));
            int color = hl ? 0xFFE8C040 : faint ? (ed.kind().color & 0x00FFFFFF) | 0x60000000 : ed.kind().color;
            int width = hl || ed.kind() == RouteKind.ROAD || ed.kind() == RouteKind.RIVER ? 2 : 1;
            List<XZ> pts = ed.points();
            double[] prev = project(ms, pts.get(0).x(), pts.get(0).z());
            for (int i = 1; i < pts.size(); i++) {
                double[] cur = project(ms, pts.get(i).x(), pts.get(i).z());
                line(g, prev[0], prev[1], cur[0], cur[1], color, width, hl ? RouteKind.ROAD : ed.kind(), x0, y0, x1, y1);
                prev = cur;
            }
        }

        // label culling: names only when markers are about 24 px apart or more at this zoom (zoom level is not public)
        double[] o = project(ms, 0, 0), k = project(ms, 1000, 0);
        double pxPer1000 = Math.abs(k[0] - o[0]);
        boolean labels = pxPer1000 >= 24;

        hovered = null;
        int drawn = 0, culled = 0;
        double best = HOVER_PX * HOVER_PX;
        GraphNode hoverNode = null;
        for (GraphNode n : nodes) {
            NodeRef r = n.ref();
            boolean known = ClientNodeState.isKnown(r), rumoured = ClientNodeState.isRumoured(r);
            if (!known && !rumoured) continue;
            double[] s = project(ms, n.x(), n.z());
            if (s[0] < x0 - 4 || s[0] > x1 + 4 || s[1] < y0 - 4 || s[1] > y1 + 4) {
                culled++;
                continue;
            }
            drawn++;
            marker(g, (int) Math.round(s[0]), (int) Math.round(s[1]), n, known, ClientNodeState.isBarred(r), r.equals(selected));
            double d2 = (s[0] - mx) * (s[0] - mx) + (s[1] - my) * (s[1] - my);
            if (d2 <= best && mx >= x0 && mx <= x1 && my >= y0 && my <= y1) {
                best = d2;
                hoverNode = n;
            }
            if (labels || r.equals(selected)) {
                g.drawString(font, n.name(), (int) s[0] + 6, (int) s[1] - 4, known ? 0xFFFFFFFF : 0xFFA0A0A0, true);
            }
        }
        g.disableScissor();

        if (loggedFor != ms) {
            loggedFor = ms;
            LotrLog.dbg("nodemap", "client overlay: map opened fullscreen={} in_me={} nodes drawn={} culled={} routes={} frame={},{}-{},{}",
                    ms.isFullscreen, ms.playerIsInDimension, drawn, culled, edges.size(), x0, y0, x1, y1);
        }

        if (hoverNode != null) {
            hovered = hoverNode.ref();
            double[] s = project(ms, hoverNode.x(), hoverNode.z());
            g.renderComponentTooltip(font, tooltip(hoverNode), (int) s[0], (int) s[1] - 14);
        }
        NodePanel.draw(ms, g, font, mx, my);
    }

    static List<Component> tooltip(GraphNode n) {
        List<Component> l = new ArrayList<>();
        NodeRef r = n.ref();
        l.add(n.name().copy().withStyle(s -> s.withBold(true)));
        l.add(Component.translatableWithFallback("nodemap.lotr_revival.kind." + n.kind().getSerializedName(), n.kind().getSerializedName()));
        if (n.faction() != null) l.add(Component.literal(n.faction().getPath()));
        if (!n.description().getString().isEmpty()) l.add(n.description().copy().withStyle(s -> s.withItalic(true)));
        if (ClientNodeState.isBarred(r)) l.add(Component.translatableWithFallback("nodemap.lotr_revival.state.barred",
                "%s will not receive you.", n.faction() == null ? "?" : n.faction().getPath()).withStyle(s -> s.withColor(0xFF5050)));
        else if (r.isCamp()) l.add(Component.translatableWithFallback("nodemap.lotr_revival.state.camp", "Your camp"));
        else if (!ClientNodeState.isKnown(r)) l.add(Component.translatableWithFallback("nodemap.lotr_revival.state.rumoured", "Rumoured"));
        return l;
    }

    private static String key(NodeRef a, NodeRef b) {
        String x = a.toString(), y = b.toString();
        return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
    }

    private static void marker(GuiGraphics g, int x, int y, GraphNode n, boolean known, boolean barred, boolean sel) {
        int color = switch (n.kind()) {
            case TOWN -> 0xFFE0D8B0;
            case STRONGHOLD -> 0xFF9A9A9A;
            case CROSSING -> 0xFF7FB0E0;
            case RUIN -> 0xFFB08A6A;
            case LANDMARK -> 0xFFB0D080;
            case CAMP -> 0xFF60C060;
        };
        if (sel) g.fill(x - 4, y - 4, x + 5, y + 5, 0xFFE8C040);
        if (known) {
            g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
            g.fill(x - 2, y - 2, x + 3, y + 3, color);
        } else {
            g.renderOutline(x - 3, y - 3, 7, 7, 0xFFA0A0A0);
        }
        if (barred) {
            for (int i = -2; i <= 2; i++) {
                g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFE03030);
                g.fill(x + i, y - i, x + i + 1, y - i + 1, 0xFFE03030);
            }
        }
    }

    /** A line as small squares: solid (road, river), dashed (path) or dotted (wild); segments fully outside are skipped. */
    private static void line(GuiGraphics g, double ax, double ay, double bx, double by, int color, int w, RouteKind kind, int x0, int y0,
                             int x1, int y1) {
        if (Math.max(ax, bx) < x0 || Math.min(ax, bx) > x1 || Math.max(ay, by) < y0 || Math.min(ay, by) > y1) return;
        double len = Math.hypot(bx - ax, by - ay);
        int steps = (int) Math.min(4000, Math.ceil(len));
        for (int i = 0; i <= steps; i++) {
            boolean on = switch (kind) {
                case PATH -> i % 7 < 4;
                case WILD -> i % 4 == 0;
                default -> true;
            };
            if (!on) continue;
            double t = steps == 0 ? 0 : (double) i / steps;
            int px = (int) Math.round(ax + (bx - ax) * t), py = (int) Math.round(ay + (by - ay) * t);
            if (px < x0 - 2 || px > x1 + 2 || py < y0 - 2 || py > y1 + 2) continue;
            g.fill(px, py, px + w, py + w, color);
        }
    }

    @SubscribeEvent
    public static void click(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!(e.getScreen() instanceof MapScreen ms) || ms.mapWidget == null || e.getButton() != 0) return;
        if (NodePanel.click(ms, e.getMouseX(), e.getMouseY())) {
            e.setCanceled(true);
            return;
        }
        if (hovered != null) {
            selected = hovered;
            LotrLog.dbg("nodemap", "client overlay: click on {}", selected);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void closed(ScreenEvent.Closing e) {
        if (e.getScreen() instanceof MapScreen) {
            selected = null;
            hovered = null;
            loggedFor = null;
        }
    }
}
