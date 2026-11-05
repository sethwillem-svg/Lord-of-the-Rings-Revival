package io.github.sethwillemsvg.lotrrevival.client.nemesis;

import io.github.sethwillemsvg.lotrrevival.nemesis.network.NemesisRosterPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The Nemeses screen (spec Player-facing 2): each faction's war-host by rank with name, title, rank, scars, known strengths,
 * known weakness and the grudge on you. Hosts of your own side are greyed. A plain scrolling list for now.
 */
public final class NemesisScreen extends Screen {
    private static List<NemesisRosterPayloads.Card> cards = List.of();
    private int scroll;

    public NemesisScreen() {
        super(Component.translatable("lotr_revival.nemesis.screen.title"));
    }

    public static void open() {
        PacketDistributor.sendToServer(new NemesisRosterPayloads.Request());
        Minecraft.getInstance().setScreen(new NemesisScreen());
    }

    public static void receive(List<NemesisRosterPayloads.Card> c) {
        cards = new ArrayList<>(c);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.max(0, scroll - (int) Math.signum(dy));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (cards.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("lotr_revival.nemesis.screen.empty"), width / 2, 40, 0xAAAAAA);
            return;
        }
        int y = 24, lineH = 22;
        String lastFaction = "";
        for (int i = scroll; i < cards.size() && y < height - 20; i++) {
            var c = cards.get(i);
            int colour = c.ownSide() ? 0x777777 : "DEAD".equals(c.status()) ? 0x995555 : 0xFFFFFF;
            if (!c.factionId().equals(lastFaction)) {
                g.drawString(font, c.faction(), 10, y, 0xFFD27F);
                y += 11;
                lastFaction = c.factionId();
            }
            Component rank = Component.translatable("lotr_revival.nemesis.rank." + c.rank());
            String grudge = c.grudge() > 0 ? "  grudge " + c.grudge() : "";
            g.drawString(font, Component.literal(c.name() + " - ").append(rank).append(c.challenger() ? " *" : "").append(grudge), 18, y, colour);
            String traits = c.strengths().stream().map(s -> s.equals("?") ? "?" : Component.translatable(s).getString())
                    .collect(Collectors.joining(", "));
            String weak = c.weakness().equals("?") ? "?" : Component.translatable(c.weakness()).getString();
            String scars = c.scars().isEmpty() ? "" : "  scars: " + String.join(", ", c.scars());
            g.drawString(font, "  + " + traits + "   - " + weak + scars + "  [" + c.status() + "]", 18, y + 10, colour & 0xBBBBBB);
            y += lineH;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
