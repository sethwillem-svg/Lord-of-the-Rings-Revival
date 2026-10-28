package io.github.sethwillemsvg.lotrrevival.client.progression.quest.log;

import io.github.sethwillemsvg.lotrrevival.client.progression.ClientQuests;
import io.github.sethwillemsvg.lotrrevival.client.progression.menu.MenuPage;
import io.github.sethwillemsvg.lotrrevival.progression.network.AbandonQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.DeleteQuestRecordPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.RemoveQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.TrackQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.PlayerQuests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The quest log: Active and Completed tabs, a list on the left and the selected quest's diary page on the right.
 * Every button only sends a request; the screen redraws from the {@link ClientQuests} mirror when the server's
 * answer arrives.
 */
public final class QuestLogScreen extends Screen {
    private static final int PANEL_BG = 0xF0191612;
    private static final int PANEL_BORDER = 0xFF8A7440;
    private static final int INNER_BG = 0xFF0F0D0A;
    private static final int GOLD = 0xFFE8C860;
    private static final int TEXT = 0xFFE6E0D0;
    private static final int DIM = 0xFF807868;
    private static final int RED = 0xFFE06050;
    private static final int ROW_HEIGHT = 20;

    private final boolean fromMenu;
    private boolean completedTab;
    private Optional<UUID> selectedQuest = Optional.empty();
    private int selectedRecord = -1;

    private PlayerQuests seen = PlayerQuests.EMPTY;
    private int listScroll;
    private int pageScroll;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int pageLeft;
    private int pageRight;
    private int pageTop;
    private int pageBottom;
    private List<FormattedCharSequence> pageLines = List.of();

    public QuestLogScreen(boolean fromMenu) {
        super(Component.translatable("gui.lotrrevival.quests.title"));
        this.fromMenu = fromMenu;
    }

    /** Opens the log from the quest book (Close button). */
    public static void openFromBook() {
        Minecraft.getInstance().setScreen(new QuestLogScreen(false));
    }

    /** Opens the log from the menu hub (Back button). */
    public static void openFromMenu() {
        Minecraft.getInstance().setScreen(new QuestLogScreen(true));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 8, 340);
        panelHeight = Math.min(height - 8, 230);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        seen = ClientQuests.get();
        normaliseSelection();

        int tabY = top + 20;
        int tabWidth = (panelWidth - 24) / 4;
        PlayerQuests quests = seen;
        addRenderableWidget(Button.builder(
                                Component.translatable("gui.lotrrevival.quests.tab.active", quests.active().size()),
                                b -> switchTab(false))
                        .bounds(left + 10, tabY, tabWidth, 16)
                        .build())
                .active = completedTab;
        addRenderableWidget(Button.builder(
                                Component.translatable("gui.lotrrevival.quests.tab.completed", quests.log().size()),
                                b -> switchTab(true))
                        .bounds(left + 12 + tabWidth, tabY, tabWidth, 16)
                        .build())
                .active = !completedTab;

        int footerY = top + panelHeight - 22;
        listLeft = left + 10;
        listRight = left + 10 + (panelWidth - 30) * 2 / 5;
        listTop = tabY + 20;
        listBottom = footerY - 4;
        pageLeft = listRight + 10;
        pageRight = left + panelWidth - 10;
        pageTop = tabY;
        int buttonsY = footerY;
        pageBottom = buttonsY - 4;

        addRenderableWidget(Button.builder(
                        Component.translatable(fromMenu ? "gui.lotrrevival.menu.back" : "gui.lotrrevival.menu.close"),
                        b -> {
                            if (fromMenu) {
                                MenuPage.open(MenuPage.HUB);
                            } else {
                                onClose();
                            }
                        })
                .bounds(listLeft, footerY, listRight - listLeft, 18)
                .build());
        addActionButtons(buttonsY);
        rebuildPage();
    }

    private void addActionButtons(int y) {
        int width = pageRight - pageLeft;
        if (!completedTab) {
            Optional<QuestInstance> quest = selectedInstance();
            if (quest.isEmpty()) {
                return;
            }
            QuestInstance instance = quest.get();
            int half = (width - 4) / 2;
            if (instance.failed()) {
                addRenderableWidget(Button.builder(
                                Component.translatable("gui.lotrrevival.quests.remove"), b -> sendRemove(instance))
                        .bounds(pageLeft, y, width, 18)
                        .build());
                return;
            }
            boolean tracked = seen.tracked().filter(instance.uuid()::equals).isPresent();
            addRenderableWidget(Button.builder(
                            Component.translatable(tracked ? "gui.lotrrevival.quests.untrack" : "gui.lotrrevival.quests.track"),
                            b -> sendTrack(tracked ? Optional.empty() : Optional.of(instance.uuid())))
                    .bounds(pageLeft, y, half, 18)
                    .build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.lotrrevival.quests.abandon"), b -> confirmAbandon(instance))
                    .bounds(pageLeft + half + 4, y, half, 18)
                    .build());
        } else if (selectedRecord >= 0 && selectedRecord < seen.log().size()) {
            int index = selectedRecord;
            QuestRecord record = seen.log().get(index);
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.lotrrevival.quests.delete"), b -> confirmDelete(index, record))
                    .bounds(pageLeft, y, width, 18)
                    .build());
        }
    }

    private void switchTab(boolean completed) {
        completedTab = completed;
        listScroll = 0;
        pageScroll = 0;
        selectedQuest = Optional.empty();
        selectedRecord = -1;
        rebuildWidgets();
    }

    /** Keeps the selection valid for the current state, picking the first row when nothing is selected. */
    private void normaliseSelection() {
        if (!completedTab) {
            if (selectedQuest.isEmpty() || seen.find(selectedQuest.get()).isEmpty()) {
                selectedQuest = seen.active().isEmpty() ? Optional.empty() : Optional.of(seen.active().get(0).uuid());
            }
        } else if (selectedRecord < 0 || selectedRecord >= seen.log().size()) {
            selectedRecord = seen.log().isEmpty() ? -1 : seen.log().size() - 1;
        }
    }

    private Optional<QuestInstance> selectedInstance() {
        return selectedQuest.flatMap(seen::find);
    }

    private RegistryAccess access() {
        return minecraft.level.registryAccess();
    }

    private void rebuildPage() {
        List<Component> lines = new ArrayList<>();
        if (!completedTab) {
            selectedInstance().ifPresent(q -> lines.addAll(QuestLogText.activeLines(access(), q)));
        } else if (selectedRecord >= 0 && selectedRecord < seen.log().size()) {
            lines.addAll(QuestLogText.recordLines(access(), seen.log().get(selectedRecord)));
        }
        int wrap = Math.max(40, pageRight - pageLeft - 12);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            if (line.getString().isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(font.split(line, wrap));
            }
        }
        pageLines = wrapped;
        pageScroll = Mth.clamp(pageScroll, 0, maxPageScroll());
    }

    private int maxPageScroll() {
        return Math.max(0, pageLines.size() * 10 - (pageBottom - pageTop - 6));
    }

    private int rowCount() {
        return completedTab ? seen.log().size() : seen.active().size();
    }

    private int maxListScroll() {
        return Math.max(0, rowCount() * ROW_HEIGHT - (listBottom - listTop - 2));
    }

    // ------------------------------------------------------------------ requests

    private void sendTrack(Optional<UUID> quest) {
        PacketDistributor.sendToServer(new TrackQuestPayload(quest));
    }

    private void sendRemove(QuestInstance instance) {
        PacketDistributor.sendToServer(new RemoveQuestPayload(instance.uuid()));
    }

    private void confirmAbandon(QuestInstance instance) {
        minecraft.setScreen(new ConfirmScreen(
                yes -> {
                    minecraft.setScreen(this);
                    if (yes) {
                        PacketDistributor.sendToServer(new AbandonQuestPayload(instance.uuid()));
                    }
                },
                Component.translatable("gui.lotrrevival.quests.abandon.title"),
                Component.translatable("gui.lotrrevival.quests.abandon.message", instance.giverName())));
    }

    private void confirmDelete(int index, QuestRecord record) {
        minecraft.setScreen(new ConfirmScreen(
                yes -> {
                    minecraft.setScreen(this);
                    if (yes) {
                        PacketDistributor.sendToServer(new DeleteQuestRecordPayload(index, record.at()));
                    }
                },
                Component.translatable("gui.lotrrevival.quests.delete.title"),
                Component.translatable("gui.lotrrevival.quests.delete.message", record.giverName())));
    }

    // ------------------------------------------------------------------ input

    @Override
    public void tick() {
        if (ClientQuests.get() != seen) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listLeft && mouseX < listRight - 4 && mouseY >= listTop && mouseY < listBottom) {
            int row = (int) ((mouseY - listTop - 1 + listScroll) / ROW_HEIGHT);
            if (row >= 0 && row < rowCount()) {
                if (completedTab) {
                    // Newest first on screen; the payload index counts oldest first.
                    selectedRecord = seen.log().size() - 1 - row;
                } else {
                    selectedQuest = Optional.of(seen.active().get(row).uuid());
                }
                pageScroll = 0;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) -Math.signum(scrollY);
        if (mouseX >= pageLeft) {
            pageScroll = Mth.clamp(pageScroll + step * 10, 0, maxPageScroll());
        } else {
            listScroll = Mth.clamp(listScroll + step * ROW_HEIGHT, 0, maxListScroll());
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, PANEL_BORDER);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL_BG);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + panelWidth / 2, top + 7, GOLD);
        renderList(graphics);
        renderPage(graphics);
    }

    private void renderList(GuiGraphics graphics) {
        graphics.fill(listLeft, listTop, listRight, listBottom, INNER_BG);
        graphics.renderOutline(listLeft, listTop, listRight - listLeft, listBottom - listTop, PANEL_BORDER);
        if (rowCount() == 0) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(completedTab ? "gui.lotrrevival.quests.empty_log" : "gui.lotrrevival.quests.empty"),
                    (listLeft + listRight) / 2,
                    listTop + 8,
                    DIM);
            return;
        }
        graphics.enableScissor(listLeft + 1, listTop + 1, listRight - 1, listBottom - 1);
        for (int row = 0; row < rowCount(); row++) {
            int y = listTop + 1 + row * ROW_HEIGHT - listScroll;
            if (y + ROW_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            if (completedTab) {
                int index = seen.log().size() - 1 - row;
                renderRecordRow(graphics, seen.log().get(index), index == selectedRecord, y);
            } else {
                QuestInstance quest = seen.active().get(row);
                renderQuestRow(graphics, quest, selectedQuest.filter(quest.uuid()::equals).isPresent(), y);
            }
        }
        graphics.disableScissor();
        int barX = listRight - 4;
        int height = listBottom - listTop - 2;
        int max = maxListScroll();
        if (max > 0) {
            int bar = Math.max(8, height * height / (height + max));
            int barY = listTop + 1 + (int) ((long) (height - bar) * listScroll / max);
            graphics.fill(barX, listTop + 1, barX + 3, listBottom - 1, 0xFF2A2620);
            graphics.fill(barX, barY, barX + 3, barY + bar, PANEL_BORDER);
        }
    }

    private void renderQuestRow(GuiGraphics graphics, QuestInstance quest, boolean selected, int y) {
        int x1 = listRight - 5;
        if (selected) {
            graphics.fill(listLeft + 1, y, x1, y + ROW_HEIGHT - 1, 0xFF26211A);
        }
        graphics.fill(listLeft + 1, y, listLeft + 3, y + ROW_HEIGHT - 1, 0xFF000000 | QuestLogText.tint(access(), quest.group()));
        graphics.renderItem(QuestLogText.icon(access(), quest), listLeft + 5, y + 1);
        boolean tracked = seen.tracked().filter(quest.uuid()::equals).isPresent();
        int width = x1 - listLeft - 26;
        String name = font.plainSubstrByWidth(QuestLogText.shortObjective(access(), quest).getString(), width);
        graphics.drawString(font, name, listLeft + 24, y + 2, quest.failed() ? RED : tracked ? GOLD : TEXT, false);
        String status = font.plainSubstrByWidth(QuestLogText.shortStatus(quest).getString(), width);
        graphics.drawString(font, status, listLeft + 24, y + 11, quest.failed() ? RED : DIM, false);
    }

    private void renderRecordRow(GuiGraphics graphics, QuestRecord record, boolean selected, int y) {
        int x1 = listRight - 5;
        if (selected) {
            graphics.fill(listLeft + 1, y, x1, y + ROW_HEIGHT - 1, 0xFF26211A);
        }
        int width = x1 - listLeft - 8;
        String giver = font.plainSubstrByWidth(record.giverName().getString(), width);
        graphics.drawString(font, giver, listLeft + 5, y + 2, TEXT, false);
        Component outcome = QuestLogText.outcome(record);
        String line = font.plainSubstrByWidth(
                Component.translatable("quest.lotrrevival.diary.ended_day", QuestLogText.day(record.at()))
                                .getString()
                        + " - " + outcome.getString(),
                width);
        graphics.drawString(font, line, listLeft + 5, y + 11, DIM, false);
    }

    private void renderPage(GuiGraphics graphics) {
        graphics.fill(pageLeft, pageTop, pageRight, pageBottom, INNER_BG);
        graphics.renderOutline(pageLeft, pageTop, pageRight - pageLeft, pageBottom - pageTop, PANEL_BORDER);
        if (pageLines.isEmpty()) {
            graphics.drawCenteredString(
                    font, Component.translatable("gui.lotrrevival.quests.select"), (pageLeft + pageRight) / 2, pageTop + 8, DIM);
            return;
        }
        graphics.enableScissor(pageLeft + 1, pageTop + 1, pageRight - 1, pageBottom - 1);
        int y = pageTop + 4 - pageScroll;
        for (FormattedCharSequence line : pageLines) {
            if (y + 10 >= pageTop && y <= pageBottom) {
                graphics.drawString(font, line, pageLeft + 5, y, TEXT, false);
            }
            y += 10;
        }
        graphics.disableScissor();
        int max = maxPageScroll();
        if (max > 0) {
            int height = pageBottom - pageTop - 2;
            int bar = Math.max(8, height * height / (height + max));
            int barY = pageTop + 1 + (int) ((long) (height - bar) * pageScroll / max);
            graphics.fill(pageRight - 4, pageTop + 1, pageRight - 1, pageBottom - 1, 0xFF2A2620);
            graphics.fill(pageRight - 4, barY, pageRight - 1, barY + bar, PANEL_BORDER);
        }
    }
}
