package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import io.github.sethwillemsvg.lotrrevival.client.progression.ClientTitles;
import io.github.sethwillemsvg.lotrrevival.progression.network.SelectTitlePayload;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleDefinition;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleDefinitions;
import io.github.sethwillemsvg.lotrrevival.progression.title.Titles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Titles page: usable titles plus "None", the 16 chat colours, the feminine-ranks toggle, a preview and Apply.
 * Apply only sends the choice; the page shows whatever the server answers through the title mirror.
 */
public final class TitlesScreen extends MenuScreen {
    private static final int ROW_HEIGHT = 14;
    private static final int SENT_TIMEOUT_TICKS = 60;
    private static final List<ChatFormatting> COLOURS =
            Arrays.stream(ChatFormatting.values()).filter(ChatFormatting::isColor).toList();

    /** A selectable line of the list. */
    private record Option(Optional<ResourceLocation> id, TitleDefinition definition) {}

    private enum Status {
        NONE,
        SENT,
        APPLIED,
        REJECTED
    }

    private List<Option> options = List.of();
    private Optional<ResourceLocation> chosen = Optional.empty();
    private ChatFormatting colour = ChatFormatting.WHITE;
    private boolean feminine;
    private Snapshot seen;
    private Status status = Status.NONE;
    private int sentTicks;
    private int ticks;

    private int scroll;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int swatchX;
    private int swatchY;
    private int swatchSize;
    private int previewY;
    private Button apply;
    private Button toggle;

    private record Snapshot(Optional<ResourceLocation> selected, ChatFormatting colour, boolean feminine) {
        static Snapshot ofServer() {
            return new Snapshot(ClientTitles.selected(), ClientTitles.color(), ClientTitles.feminine());
        }
    }

    TitlesScreen() {
        super(MenuPage.TITLES, Component.translatable("gui.lotrrevival.menu.titles"));
    }

    @Override
    protected void buildPage() {
        if (seen == null) {
            seen = Snapshot.ofServer();
            adopt(seen);
        }
        refreshOptions();

        int footerY = top + panelHeight - 22;
        int inner = panelWidth - 30;
        int listWidth = inner * 9 / 20;
        listLeft = left + 10;
        listRight = listLeft + listWidth;
        int rightX = listRight + 10;
        int rightWidth = left + panelWidth - 10 - rightX;

        addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.back"), b -> MenuPage.open(MenuPage.HUB))
                .bounds(listLeft, footerY, listWidth, 18)
                .build());
        apply = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.titles.apply"), b -> apply())
                .bounds(rightX, footerY, rightWidth, 18)
                .build());
        listTop = top + 20;
        listBottom = footerY - 4;

        swatchSize = Mth.clamp(rightWidth / 8 - 1, 6, 14);
        swatchX = rightX;
        swatchY = top + 32;
        int toggleY = swatchY + 2 * (swatchSize + 1) + 4;
        toggle = addRenderableWidget(Button.builder(toggleLabel(), b -> {
                    feminine = !feminine;
                    b.setMessage(toggleLabel());
                })
                .bounds(rightX, toggleY, rightWidth, 16)
                .build());
        previewY = toggleY + 20;
        clampScroll();
        updateApply();
    }

    private Component toggleLabel() {
        return Component.translatable(
                feminine ? "gui.lotrrevival.menu.titles.feminine.on" : "gui.lotrrevival.menu.titles.feminine.off");
    }

    private void adopt(Snapshot snapshot) {
        chosen = snapshot.selected();
        colour = snapshot.colour();
        feminine = snapshot.feminine();
        if (toggle != null) {
            toggle.setMessage(toggleLabel());
        }
    }

    /** Usable titles by display text, preceded by "None". Rebuilt on tick so alignment changes show live. */
    private void refreshOptions() {
        Minecraft mc = Minecraft.getInstance();
        List<Option> next = new ArrayList<>();
        next.add(new Option(Optional.empty(), null));
        if (mc.player != null) {
            List<Option> titles = new ArrayList<>();
            for (ResourceLocation id : Titles.usable(mc.player)) {
                TitleDefinitions.find(mc.player.level().registryAccess(), id)
                        .ifPresent(definition -> titles.add(new Option(Optional.of(id), definition)));
            }
            titles.sort(Comparator.comparing(
                    (Option option) -> option.definition().displayText(false).getString().toLowerCase(Locale.ROOT)));
            next.addAll(titles);
        }
        options = next;
        if (chosen.isPresent() && options.stream().noneMatch(option -> option.id().equals(chosen))) {
            chosen = Optional.empty();
        }
    }

    private boolean chosenListed() {
        return options.stream().anyMatch(option -> option.id().equals(chosen));
    }

    private void updateApply() {
        if (apply == null) {
            return;
        }
        Snapshot server = Snapshot.ofServer();
        boolean changed = !server.selected().equals(chosen) || server.colour() != colour || server.feminine() != feminine;
        apply.active = status != Status.SENT && changed && chosenListed();
    }

    private void apply() {
        if (!chosenListed()) {
            return;
        }
        PacketDistributor.sendToServer(new SelectTitlePayload(chosen, colour.getName(), feminine));
        status = Status.SENT;
        sentTicks = 0;
        updateApply();
    }

    @Override
    public void tick() {
        ticks++;
        Snapshot server = Snapshot.ofServer();
        if (!server.equals(seen)) {
            // The server changed the selection (our Apply, a revoked title, a command): show its answer.
            seen = server;
            adopt(server);
            status = status == Status.SENT ? Status.APPLIED : Status.NONE;
        } else if (status == Status.SENT && ++sentTicks > SENT_TIMEOUT_TICKS) {
            // Nothing came back: the server dropped the request. Fall back to what it has.
            adopt(server);
            status = Status.REJECTED;
        }
        if (ticks % 5 == 0) {
            refreshOptions();
            clampScroll();
        }
        updateApply();
    }

    private int maxScroll() {
        return Math.max(0, options.size() * ROW_HEIGHT - (listBottom - listTop));
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private MutableComponent titleText(Optional<ResourceLocation> id) {
        Minecraft mc = Minecraft.getInstance();
        if (id.isEmpty() || mc.player == null) {
            return Component.translatable("gui.lotrrevival.menu.titles.none");
        }
        return TitleDefinitions.find(mc.player.level().registryAccess(), id.get())
                .map(definition -> definition.displayText(feminine).copy())
                .orElseGet(() -> Component.literal(id.get().toString()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(
                font, Component.translatable("gui.lotrrevival.menu.titles.header"), left + panelWidth / 2, top + 7, GOLD);
        renderList(graphics);
        renderColours(graphics);
        renderPreview(graphics);
    }

    private void renderList(GuiGraphics graphics) {
        graphics.fill(listLeft, listTop, listRight, listBottom, INNER_BG);
        graphics.renderOutline(listLeft, listTop, listRight - listLeft, listBottom - listTop, PANEL_BORDER);
        Optional<ResourceLocation> serverSelected = ClientTitles.selected();
        graphics.enableScissor(listLeft + 1, listTop + 1, listRight - 1, listBottom - 1);
        for (int n = 0; n < options.size(); n++) {
            int y = listTop + 1 + n * ROW_HEIGHT - scroll;
            if (y + ROW_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            Option option = options.get(n);
            boolean picked = option.id().equals(chosen);
            if (picked) {
                graphics.fill(listLeft + 1, y, listRight - 1, y + ROW_HEIGHT, 0xFF3A3020);
            }
            Component text = option.id().isEmpty()
                    ? Component.translatable("gui.lotrrevival.menu.titles.none")
                    : option.definition().displayText(feminine);
            int colourValue = option.id().equals(serverSelected) ? GOLD : TEXT;
            graphics.drawString(
                    font, font.plainSubstrByWidth(text.getString(), listRight - listLeft - 12), listLeft + 4, y + 3, colourValue, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, listRight - 4, listTop + 1, listBottom - 1, scroll, maxScroll());
    }

    private void renderColours(GuiGraphics graphics) {
        graphics.drawString(
                font, Component.translatable("gui.lotrrevival.menu.titles.colour"), swatchX, swatchY - 11, DIM, false);
        for (int n = 0; n < COLOURS.size(); n++) {
            int x = swatchX + (n % 8) * (swatchSize + 1);
            int y = swatchY + (n / 8) * (swatchSize + 1);
            Integer rgb = COLOURS.get(n).getColor();
            graphics.fill(x, y, x + swatchSize, y + swatchSize, 0xFF000000 | (rgb == null ? 0xFFFFFF : rgb));
            if (COLOURS.get(n) == colour) {
                graphics.renderOutline(x - 1, y - 1, swatchSize + 2, swatchSize + 2, 0xFFFFFFFF);
            }
        }
    }

    private void renderPreview(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int x = swatchX;
        int maxWidth = left + panelWidth - 10 - x;
        MutableComponent line = Component.empty();
        if (chosen.isPresent()) {
            line.append(Component.literal("[").append(titleText(chosen)).append("]").withStyle(colour));
            line.append(" ");
        }
        String name = mc.player == null ? "" : mc.player.getName().getString();
        line.append("<" + name + ">");
        graphics.drawString(
                font,
                font.plainSubstrByWidth(
                        Component.translatable("gui.lotrrevival.menu.titles.preview").getString() + ":", maxWidth),
                x,
                previewY,
                DIM,
                false);
        List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(line, maxWidth);
        if (!wrapped.isEmpty()) {
            graphics.drawString(font, wrapped.get(0), x, previewY + 10, TEXT, false);
        }
        Component state = switch (status) {
            case SENT -> Component.translatable("gui.lotrrevival.menu.titles.sent");
            case APPLIED -> Component.translatable("gui.lotrrevival.menu.titles.applied");
            case REJECTED -> Component.translatable("gui.lotrrevival.menu.titles.rejected");
            case NONE -> Component.translatable("gui.lotrrevival.menu.titles.current", titleText(ClientTitles.selected()));
        };
        graphics.drawString(font, font.plainSubstrByWidth(state.getString(), maxWidth), x, previewY + 22, status == Status.REJECTED ? 0xFFD06050 : DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && mouseX >= listLeft && mouseX < listRight - 4 && mouseY >= listTop && mouseY < listBottom) {
            int index = ((int) mouseY - listTop - 1 + scroll) / ROW_HEIGHT;
            if (index >= 0 && index < options.size()) {
                chosen = options.get(index).id();
                status = Status.NONE;
                return true;
            }
        }
        if (button == 0) {
            for (int n = 0; n < COLOURS.size(); n++) {
                int x = swatchX + (n % 8) * (swatchSize + 1);
                int y = swatchY + (n / 8) * (swatchSize + 1);
                if (mouseX >= x && mouseX < x + swatchSize && mouseY >= y && mouseY < y + swatchSize) {
                    colour = COLOURS.get(n);
                    status = Status.NONE;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * ROW_HEIGHT, 0, maxScroll());
        return true;
    }
}
