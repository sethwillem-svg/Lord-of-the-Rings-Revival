package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import io.github.sethwillemsvg.lotrrevival.client.progression.ClientAchievements;
import io.github.sethwillemsvg.lotrrevival.client.progression.fellowship.ClientFellowships;
import io.github.sethwillemsvg.lotrrevival.client.progression.fellowship.FellowshipPageModel;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipRole;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipView;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.Fellowships;
import io.github.sethwillemsvg.lotrrevival.progression.network.FellowshipActionPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Fellowships page: a Create row, the pending invites (Accept / Reject) and the viewer's fellowships. Clicking a
 * fellowship opens its page. Every change is a {@code fellowship_action}; the lists follow the server's answer.
 */
public final class FellowshipsScreen extends MenuScreen {
    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 44;
    private static final int RED = 0xFFD06050;
    private static final int NAME_FIELD_MAX = 40;

    private EditBox nameBox;
    private Button create;
    private int scroll;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int hintY;
    private String keptName = "";

    FellowshipsScreen() {
        super(MenuPage.FELLOWSHIPS, Component.translatable("gui.lotrrevival.menu.fellowships"));
    }

    @Override
    protected void buildPage() {
        listLeft = left + 10;
        listRight = left + panelWidth - 10;
        int createWidth = 56;
        nameBox = new EditBox(
                font, listLeft, top + 20, listRight - listLeft - createWidth - 4, 16,
                Component.translatable("gui.lotrrevival.menu.fellowships.name"));
        nameBox.setMaxLength(NAME_FIELD_MAX);
        nameBox.setHint(Component.translatable("gui.lotrrevival.menu.fellowships.name_hint"));
        nameBox.setValue(keptName);
        nameBox.setResponder(value -> keptName = value);
        addRenderableWidget(nameBox);
        create = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.create"), b -> create())
                .bounds(listRight - createWidth, top + 19, createWidth, 18)
                .build());
        hintY = top + 40;
        listTop = top + 52;
        int footerY = addFooter(true);
        listBottom = footerY - 4;
        clampScroll();
        updateCreate();
    }

    private List<FellowshipView.Entry> entries() {
        return ClientFellowships.fellowships();
    }

    private UUID viewer() {
        return Minecraft.getInstance().player.getUUID();
    }

    private int leadLimit() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 1 : Fellowships.leadLimit(ClientAchievements.count(mc.player));
    }

    private FellowshipPageModel.NameCheck nameCheck() {
        return FellowshipPageModel.checkName(
                nameBox.getValue(), entries().stream().map(FellowshipView.Entry::name).toList(), null);
    }

    private boolean atLeadLimit() {
        return FellowshipPageModel.leadCount(entries(), viewer()) >= leadLimit();
    }

    private void updateCreate() {
        if (create != null) {
            create.active = nameCheck() == FellowshipPageModel.NameCheck.OK && !atLeadLimit();
        }
    }

    private void create() {
        if (!create.active) {
            return;
        }
        PacketDistributor.sendToServer(new FellowshipActionPayload(
                FellowshipActionPayload.Action.CREATE, new UUID(0L, 0L), nameBox.getValue().trim(), false));
        nameBox.setValue("");
    }

    @Override
    public void tick() {
        updateCreate();
        clampScroll();
    }

    private int rowCount() {
        return ClientFellowships.invites().size() + entries().size();
    }

    private int maxScroll() {
        return Math.max(0, rowCount() * ROW_HEIGHT - (listBottom - listTop - 2));
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + panelWidth / 2, top + 7, GOLD);
        renderHint(graphics);
        renderList(graphics, mouseX, mouseY);
    }

    private void renderHint(GuiGraphics graphics) {
        FellowshipPageModel.NameCheck check = nameCheck();
        int led = FellowshipPageModel.leadCount(entries(), viewer());
        Component text;
        int colour = DIM;
        if (check != FellowshipPageModel.NameCheck.EMPTY && check != FellowshipPageModel.NameCheck.OK) {
            text = Component.translatable("gui.lotrrevival.menu.fellowships.hint." + check.name().toLowerCase(java.util.Locale.ROOT), Fellowships.NAME_MAX);
            colour = RED;
        } else if (atLeadLimit()) {
            text = Component.translatable("gui.lotrrevival.menu.fellowships.lead_full", led, leadLimit());
            colour = RED;
        } else {
            text = Component.translatable("gui.lotrrevival.menu.fellowships.lead", led, leadLimit());
        }
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), listRight - listLeft), listLeft, hintY, colour, false);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(listLeft, listTop, listRight, listBottom, INNER_BG);
        graphics.renderOutline(listLeft, listTop, listRight - listLeft, listBottom - listTop, PANEL_BORDER);
        List<FellowshipView.Invite> invites = ClientFellowships.invites();
        List<FellowshipView.Entry> entries = entries();
        if (invites.isEmpty() && entries.isEmpty()) {
            graphics.drawCenteredString(
                    font, Component.translatable("gui.lotrrevival.menu.fellowships.empty"),
                    (listLeft + listRight) / 2, (listTop + listBottom) / 2 - 4, DIM);
            return;
        }
        graphics.enableScissor(listLeft + 1, listTop + 1, listRight - 1, listBottom - 1);
        int n = 0;
        for (FellowshipView.Invite invite : invites) {
            int y = rowY(n++);
            if (y + ROW_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            Component text = Component.translatable("gui.lotrrevival.menu.fellowships.invite", invite.name(), invite.inviter());
            int textWidth = listRight - listLeft - 2 * BUTTON_WIDTH - 20;
            graphics.drawString(font, font.plainSubstrByWidth(text.getString(), textWidth), listLeft + 4, y + 6, GOLD, false);
            drawSmallButton(graphics, acceptX(), y, "accept", mouseX, mouseY);
            drawSmallButton(graphics, rejectX(), y, "reject", mouseX, mouseY);
        }
        for (FellowshipView.Entry entry : entries) {
            int y = rowY(n++);
            if (y + ROW_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            boolean hover = mouseX >= listLeft && mouseX < listRight - 4 && mouseY >= y && mouseY < y + ROW_HEIGHT
                    && mouseY >= listTop && mouseY < listBottom;
            if (hover) {
                graphics.fill(listLeft + 1, y, listRight - 1, y + ROW_HEIGHT, 0xFF2A2418);
            }
            if (!entry.icon().isEmpty()) {
                graphics.renderItem(entry.icon(), listLeft + 3, y + 2);
            }
            graphics.drawString(font, font.plainSubstrByWidth(entry.name(), 110), listLeft + 23, y + 6, TEXT, false);
            Component role = Component.translatable(
                    "fellowship.lotrrevival.role." + entry.roleOf(viewer()).orElse(FellowshipRole.MEMBER).name().toLowerCase(java.util.Locale.ROOT));
            Component info = Component.translatable("gui.lotrrevival.menu.fellowships.row", entry.members().size(), role);
            String shown = font.plainSubstrByWidth(info.getString(), listRight - listLeft - 140);
            graphics.drawString(font, shown, listRight - 8 - font.width(shown), y + 6, DIM, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, listRight - 4, listTop + 1, listBottom - 1, scroll, maxScroll());
    }

    private int rowY(int index) {
        return listTop + 1 + index * ROW_HEIGHT - scroll;
    }

    private int acceptX() {
        return listRight - 8 - 2 * BUTTON_WIDTH - 2;
    }

    private int rejectX() {
        return listRight - 8 - BUTTON_WIDTH;
    }

    private void drawSmallButton(GuiGraphics graphics, int x, int y, String key, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y + 2 && mouseY < y + ROW_HEIGHT - 2;
        graphics.fill(x, y + 2, x + BUTTON_WIDTH, y + ROW_HEIGHT - 2, hover ? 0xFF4A3E22 : 0xFF2E2818);
        graphics.renderOutline(x, y + 2, BUTTON_WIDTH, ROW_HEIGHT - 4, PANEL_BORDER);
        graphics.drawCenteredString(
                font, Component.translatable("gui.lotrrevival.menu.fellowships." + key), x + BUTTON_WIDTH / 2, y + 6, TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || mouseX < listLeft || mouseX >= listRight - 4 || mouseY < listTop || mouseY >= listBottom) {
            return false;
        }
        int index = ((int) mouseY - listTop - 1 + scroll) / ROW_HEIGHT;
        List<FellowshipView.Invite> invites = ClientFellowships.invites();
        if (index < 0) {
            return false;
        }
        if (index < invites.size()) {
            FellowshipView.Invite invite = invites.get(index);
            if (mouseX >= acceptX() && mouseX < acceptX() + BUTTON_WIDTH) {
                sendInviteAnswer(FellowshipActionPayload.Action.ACCEPT, invite);
                return true;
            }
            if (mouseX >= rejectX() && mouseX < rejectX() + BUTTON_WIDTH) {
                sendInviteAnswer(FellowshipActionPayload.Action.REJECT, invite);
                return true;
            }
            return false;
        }
        int entryIndex = index - invites.size();
        List<FellowshipView.Entry> entries = entries();
        if (entryIndex < entries.size()) {
            Minecraft.getInstance().setScreen(new FellowshipScreen(entries.get(entryIndex).id()));
            return true;
        }
        return false;
    }

    private static void sendInviteAnswer(FellowshipActionPayload.Action action, FellowshipView.Invite invite) {
        PacketDistributor.sendToServer(new FellowshipActionPayload(action, invite.fellowship(), "", false));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * ROW_HEIGHT, 0, maxScroll());
        return true;
    }
}
