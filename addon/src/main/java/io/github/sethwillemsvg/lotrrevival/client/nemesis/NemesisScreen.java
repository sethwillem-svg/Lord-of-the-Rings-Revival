package io.github.sethwillemsvg.lotrrevival.client.nemesis;

import io.github.sethwillemsvg.lotrrevival.nemesis.network.NemesisRosterPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Nemeses screen (spec Player-facing 2, ticket N12): the war-hosts by faction and rank. Each card shows name and title, rank
 * (in its boss-bar colour) and faction, known strengths and weakness (hidden ones as "?"), scars, the grudge it holds on you and
 * where and when it was last seen. Hosts of your own side are greyed, the dead are dimmed. Paged, with a faction filter; the
 * mouse wheel and the arrow keys turn pages.
 */
public final class NemesisScreen extends Screen {
    private static final int CARD_H = 34, TOP = 40, BOTTOM = 30, HEADER_H = 12;
    private static List<NemesisRosterPayloads.Card> cards = List.of();
    private static boolean received;
    private static int savedPage, savedFilter;

    private int page;
    /** 0 = every faction; i = the i-th faction of {@link #factions()}. */
    private int filter;
    private Button prev, next, filterButton;
    private boolean initialised;

    public NemesisScreen() {
        super(Component.translatable("lotr_revival.nemesis.screen.title"));
    }

    public static void open() {
        received = false;
        PacketDistributor.sendToServer(new NemesisRosterPayloads.Request());
        Minecraft.getInstance().setScreen(new NemesisScreen());
    }

    public static void receive(List<NemesisRosterPayloads.Card> c) {
        cards = new ArrayList<>(c);
        received = true;
    }

    @Override
    protected void init() {
        if (!initialised) {   // init runs again on resize: keep the page then
            page = savedPage;
            filter = savedFilter;
            initialised = true;
        }
        int w = 60, y = height - BOTTOM + 5;
        prev = addRenderableWidget(Button.builder(Component.translatable("lotr_revival.nemesis.screen.prev"), b -> turn(-1))
                .bounds(width / 2 - 100 - w / 2, y, w, 20).build());
        next = addRenderableWidget(Button.builder(Component.translatable("lotr_revival.nemesis.screen.next"), b -> turn(1))
                .bounds(width / 2 + 100 - w / 2, y, w, 20).build());
        filterButton = addRenderableWidget(Button.builder(filterLabel(), b -> cycleFilter())
                .bounds(width / 2 - 80, 18, 160, 18).build());
    }

    @Override
    public void removed() {
        savedPage = page;
        savedFilter = filter;
        super.removed();
    }

    // ---------------------------------------------------------------- data

    private static List<String> factions() {
        List<String> out = new ArrayList<>();
        for (var c : cards) if (!out.contains(c.factionId())) out.add(c.factionId());
        return out;
    }

    private List<NemesisRosterPayloads.Card> shown() {
        List<String> fs = factions();
        if (filter > fs.size()) filter = 0;
        if (filter == 0) return cards;
        String f = fs.get(filter - 1);
        List<NemesisRosterPayloads.Card> out = new ArrayList<>();
        for (var c : cards) if (c.factionId().equals(f)) out.add(c);
        return out;
    }

    private Component filterLabel() {
        List<String> fs = factions();
        if (filter == 0 || filter > fs.size()) return Component.translatable("lotr_revival.nemesis.screen.filter.all");
        String f = fs.get(filter - 1);
        for (var c : cards) if (c.factionId().equals(f)) return c.faction();
        return Component.literal(f);
    }

    private int perPage() {
        // each faction header on a page takes about a third of a card; leave room for one per page
        return Math.max(1, (height - TOP - BOTTOM - HEADER_H) / CARD_H);
    }

    private int pages() {
        return Math.max(1, (shown().size() + perPage() - 1) / perPage());
    }

    private void turn(int by) {
        page = Math.max(0, Math.min(pages() - 1, page + by));
    }

    private void cycleFilter() {
        filter = (filter + 1) % (factions().size() + 1);
        page = 0;
        filterButton.setMessage(filterLabel());
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dy != 0) turn(dy < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            turn(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) {
            turn(-1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        List<NemesisRosterPayloads.Card> list = shown();
        page = Math.max(0, Math.min(pages() - 1, page));
        prev.active = page > 0;
        next.active = page < pages() - 1;
        filterButton.visible = factions().size() > 1;
        filterButton.setMessage(filterLabel());
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        if (list.isEmpty()) {
            Component msg = Component.translatable(received ? "lotr_revival.nemesis.screen.empty" : "lotr_revival.nemesis.screen.loading");
            g.drawCenteredString(font, msg, width / 2, TOP + 20, 0xAAAAAA);
            return;
        }
        g.drawCenteredString(font, Component.translatable("lotr_revival.nemesis.screen.page", page + 1, pages()), width / 2,
                height - BOTTOM + 11, 0xCCCCCC);
        int x = Math.max(10, width / 2 - 170), y = TOP;
        String lastFaction = "";
        int from = page * perPage(), to = Math.min(list.size(), from + perPage());
        for (int i = from; i < to; i++) {
            var c = list.get(i);
            if (!c.factionId().equals(lastFaction)) {
                if (y + HEADER_H + CARD_H > height - BOTTOM) break;
                g.drawString(font, c.faction().copy().withStyle(ChatFormatting.BOLD), x, y, 0xFFD27F);
                y += HEADER_H;
                lastFaction = c.factionId();
            }
            if (y + CARD_H > height - BOTTOM + 4) break;
            drawCard(g, c, x, y);
            y += CARD_H;
        }
    }

    private void drawCard(GuiGraphics g, NemesisRosterPayloads.Card c, int x, int y) {
        boolean dead = "DEAD".equals(c.status());
        int base = c.ownSide() ? 0x888888 : dead ? 0x996666 : 0xFFFFFF;
        int dim = c.ownSide() ? 0x707070 : dead ? 0x806060 : 0xBBBBBB;
        // line 1: name and title, rank of faction, status
        MutableComponent rank = Component.translatable("lotr_revival.nemesis.rank." + c.rank());
        MutableComponent line1 = Component.literal(c.name()).withColor(base).append(Component.literal("  "))
                .append(Component.translatable("lotr_revival.nemesis.screen.of", rank, c.faction()).withColor(c.ownSide() || dead ? dim : rankColour(c.rank())));
        if (c.challenger()) line1.append(Component.literal(" ")).append(Component.translatable("lotr_revival.nemesis.screen.challenger").withColor(dim));
        if (dead) line1.append(Component.literal(" ")).append(Component.translatable("lotr_revival.nemesis.screen.status.dead").withColor(0xAA4444));
        else if ("MANIFEST".equals(c.status()))
            line1.append(Component.literal(" ")).append(Component.translatable("lotr_revival.nemesis.screen.status.manifest").withColor(0xE0A040));
        g.drawString(font, line1, x, y, base);
        // line 2: known traits, weakness, scars
        MutableComponent line2 = Component.translatable("lotr_revival.nemesis.screen.strengths", joinTraits(c.strengths()));
        line2.append(Component.literal("   ")).append(Component.translatable("lotr_revival.nemesis.screen.weakness", trait(c.weakness())));
        if (!c.scars().isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (String s : c.scars()) keys.add("lotr_revival.nemesis.trait.scar_" + s);
            line2.append(Component.literal("   ")).append(Component.translatable("lotr_revival.nemesis.screen.scars", joinTraits(keys)));
        }
        g.drawString(font, line2, x + 8, y + 10, dim);
        // line 3: grudge on you, last seen
        MutableComponent line3 = c.grudge() <= 0 ? Component.translatable("lotr_revival.nemesis.screen.no_grudge")
                : Component.translatable(c.grudgeDormant() ? "lotr_revival.nemesis.screen.grudge.dormant" : "lotr_revival.nemesis.screen.grudge",
                c.grudge()).withColor(c.grudgeDormant() ? dim : 0xFF6655);
        line3.append(Component.literal("   "));
        if (c.lastSeenAgo() < 0) line3.append(Component.translatable("lotr_revival.nemesis.screen.last_seen.unknown"));
        else line3.append(Component.translatable("lotr_revival.nemesis.screen.last_seen", c.lastX(), c.lastZ(), c.lastSeenDim(), ago(c.lastSeenAgo())));
        g.drawString(font, line3, x + 8, y + 20, dim);
    }

    private static Component trait(String key) {
        return "?".equals(key) ? Component.literal("?") : Component.translatable(key);
    }

    private static Component joinTraits(List<String> keys) {
        if (keys.isEmpty()) return Component.literal("-");
        MutableComponent out = Component.empty();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) out.append(Component.literal(", "));
            out.append(trait(keys.get(i)));
        }
        return out;
    }

    /** Game ticks as "5 min", "3 h" or "2 days". */
    private static Component ago(long ticks) {
        long min = ticks / 1200;
        if (min < 60) return Component.translatable("lotr_revival.nemesis.screen.time.minutes", Math.max(1, min));
        long h = min / 60;
        if (h < 48) return Component.translatable("lotr_revival.nemesis.screen.time.hours", h);
        return Component.translatable("lotr_revival.nemesis.screen.time.days", h / 24);
    }

    /** The rank's boss-bar colour (spec Ranks table). */
    private static int rankColour(String rank) {
        return switch (rank) {
            case "captain" -> 0x55FF55;
            case "commander" -> 0xFFFF55;
            case "warlord" -> 0xCC66FF;
            case "overlord" -> 0xFF5555;
            default -> 0xFFFFFF;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
