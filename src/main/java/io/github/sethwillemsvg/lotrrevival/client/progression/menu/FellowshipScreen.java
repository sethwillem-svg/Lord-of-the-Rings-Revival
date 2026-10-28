package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import io.github.sethwillemsvg.lotrrevival.client.progression.fellowship.ClientFellowships;
import io.github.sethwillemsvg.lotrrevival.client.progression.fellowship.FellowshipPageModel;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipOption;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipRole;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipView;
import io.github.sethwillemsvg.lotrrevival.progression.network.FellowshipActionPayload;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One fellowship: icon, owner, members (titles in their colour, online markers), the three options and only the
 * actions the viewer's role allows. Remove, transfer, leave and disband ask for confirmation first.
 */
public final class FellowshipScreen extends MenuScreen {
    private static final int ROW_HEIGHT = 12;
    private static final int GREEN = 0xFF60C060;
    private static final int GREY = 0xFF55504A;
    private static final int RED = 0xFFD06050;
    private static final int NAME_FIELD_MAX = 40;

    private final UUID id;
    private UUID selected;
    private int scroll;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int rightX;
    private int rightRight;
    private int optionTop;
    private String keptText = "";

    private EditBox textBox;
    private Button invite;
    private Button rename;
    private Button remove;
    private Button promote;
    private Button demote;
    private Button transfer;

    FellowshipScreen(UUID id) {
        super(MenuPage.FELLOWSHIPS, Component.translatable("gui.lotrrevival.menu.fellowships"));
        this.id = id;
    }

    private Optional<FellowshipView.Entry> entry() {
        return ClientFellowships.find(id);
    }

    private UUID viewer() {
        return Minecraft.getInstance().player.getUUID();
    }

    private FellowshipRole viewerRole() {
        return entry().flatMap(e -> e.roleOf(viewer())).orElse(FellowshipRole.MEMBER);
    }

    @Override
    protected void buildPage() {
        FellowshipView.Entry entry = entry().orElse(null);
        if (entry == null) {
            MenuPage.open(MenuPage.FELLOWSHIPS);
            return;
        }
        FellowshipPageModel.Actions actions = FellowshipPageModel.actions(viewerRole());
        int footerY = top + panelHeight - 22;
        listLeft = left + 10;
        int listWidth = (panelWidth - 30) * 11 / 20;
        listRight = listLeft + listWidth;
        rightX = listRight + 10;
        rightRight = left + panelWidth - 10;

        addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.back"), b -> MenuPage.open(MenuPage.FELLOWSHIPS))
                .bounds(left + panelWidth / 2 - 40, footerY, 80, 18)
                .build());

        listTop = top + 30;
        // Bottom stack, from the footer up: name field row, then the selected-member actions.
        int fieldY = footerY - 20;
        int memberY = fieldY - 18;
        listBottom = memberY - 3;

        int third = (listWidth - 4) / 3;
        remove = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.remove"), b -> confirmRemove())
                .bounds(listLeft, memberY, third, 16).build());
        promote = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.promote"), b -> sendToSelected(FellowshipActionPayload.Action.PROMOTE))
                .bounds(listLeft + third + 2, memberY, third, 16).build());
        demote = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.demote"), b -> sendToSelected(FellowshipActionPayload.Action.DEMOTE))
                .bounds(listLeft + third + 2, memberY, third, 16).build());
        transfer = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.transfer"), b -> confirmTransfer())
                .bounds(listLeft + 2 * (third + 2), memberY, third, 16).build());

        int fieldRight = left + panelWidth - 10;
        int buttonWidth = 48;
        int buttons = (actions.invite() ? 1 : 0) + (actions.rename() ? 1 : 0);
        int boxWidth = fieldRight - listLeft - buttons * (buttonWidth + 2);
        textBox = new EditBox(font, listLeft, fieldY, boxWidth, 16, Component.translatable("gui.lotrrevival.menu.fellowships.name"));
        textBox.setMaxLength(NAME_FIELD_MAX);
        textBox.setHint(Component.translatable("gui.lotrrevival.menu.fellowships.text_hint"));
        textBox.setValue(keptText);
        textBox.setResponder(value -> keptText = value);
        int x = listLeft + boxWidth + 2;
        if (buttons > 0) {
            addRenderableWidget(textBox);
        }
        if (actions.invite()) {
            invite = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.invite_button"), b -> invitePlayer())
                    .bounds(x, fieldY - 1, buttonWidth, 18).build());
            x += buttonWidth + 2;
        }
        if (actions.rename()) {
            rename = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.rename"), b -> renameTo())
                    .bounds(x, fieldY - 1, buttonWidth, 18).build());
        }

        // Right column: option toggles, then icon and leave/disband.
        optionTop = top + 30;
        int rightWidth = rightRight - rightX;
        int y = optionTop + 3 * 22 + 2;
        if (actions.setIcon()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.icon"), b -> send(FellowshipActionPayload.Action.ICON, "", false))
                    .bounds(rightX, y, rightWidth, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.lotrrevival.menu.fellowships.icon.tooltip")))
                    .build());
            y += 18;
        }
        FellowshipOption[] options = FellowshipOption.values();
        for (int n = 0; n < options.length; n++) {
            FellowshipOption option = options[n];
            boolean allowed = option.ownerOnly() ? actions.toggleMap() : actions.toggleCombat();
            if (allowed) {
                int rowY = optionTop + n * 22;
                addRenderableWidget(Button.builder(Component.empty(), b -> {
                            boolean now = optionValue(entry().orElse(null), option);
                            send(FellowshipActionPayload.Action.OPTION, option.id(), !now);
                        })
                        .bounds(rightRight - 32, rowY + 2, 32, 16)
                        .createNarration(supplier -> Component.translatable("fellowship.lotrrevival.option." + option.id()))
                        .build());
            }
        }
        if (actions.leave()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.leave"), b -> confirmLeave())
                    .bounds(rightX, y, rightWidth, 16).build());
        }
        if (actions.disband()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.fellowships.disband"), b -> confirmDisband())
                    .bounds(rightX, y, rightWidth, 16).build());
        }
        clampScroll();
        updateButtons();
    }

    private static boolean optionValue(FellowshipView.Entry entry, FellowshipOption option) {
        if (entry == null) {
            return false;
        }
        return switch (option) {
            case PREVENT_PVP -> entry.preventPvp();
            case PREVENT_UNIT_FRIENDLY_FIRE -> entry.preventUnitFriendlyFire();
            case SHOW_MAP -> entry.showMap();
        };
    }

    private Optional<FellowshipView.Member> selectedMember() {
        if (selected == null) {
            return Optional.empty();
        }
        return entry().flatMap(e -> e.members().stream().filter(m -> m.id().equals(selected)).findFirst());
    }

    private FellowshipPageModel.MemberActions memberActions() {
        return selectedMember()
                .map(m -> FellowshipPageModel.memberActions(viewerRole(), m.role(), m.online(), m.id().equals(viewer())))
                .orElse(new FellowshipPageModel.MemberActions(false, false, false, false));
    }

    private void updateButtons() {
        if (remove == null) {
            return;
        }
        FellowshipPageModel.MemberActions actions = memberActions();
        boolean canManage = viewerRole().canManage();
        remove.visible = canManage;
        remove.active = actions.remove();
        boolean owner = viewerRole() == FellowshipRole.OWNER;
        boolean showDemote = actions.demote();
        promote.visible = owner && !showDemote;
        promote.active = actions.promote();
        demote.visible = owner && showDemote;
        demote.active = true;
        transfer.visible = owner;
        transfer.active = actions.transfer();
        if (invite != null) {
            invite.active = !textBox.getValue().isBlank();
        }
        if (rename != null) {
            rename.active = FellowshipPageModel.checkName(
                            textBox.getValue(), ClientFellowships.fellowships().stream().map(FellowshipView.Entry::name).toList(),
                            entry().map(FellowshipView.Entry::name).orElse(null))
                    == FellowshipPageModel.NameCheck.OK;
        }
    }

    private void send(FellowshipActionPayload.Action action, String argument, boolean value) {
        PacketDistributor.sendToServer(new FellowshipActionPayload(action, id, argument, value));
    }

    private void sendToSelected(FellowshipActionPayload.Action action) {
        selectedMember().ifPresent(member -> send(action, member.name(), false));
    }

    private void invitePlayer() {
        String name = textBox.getValue().trim();
        if (!name.isEmpty()) {
            send(FellowshipActionPayload.Action.INVITE, name, false);
            textBox.setValue("");
        }
    }

    private void renameTo() {
        if (rename.active) {
            send(FellowshipActionPayload.Action.RENAME, textBox.getValue().trim(), false);
            textBox.setValue("");
        }
    }

    private void confirm(String key, Object arg, Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(
                yes -> {
                    if (yes) {
                        action.run();
                    }
                    mc.setScreen(new FellowshipScreen(id));
                },
                Component.translatable("gui.lotrrevival.menu.fellowships.confirm." + key),
                Component.translatable("gui.lotrrevival.menu.fellowships.confirm." + key + ".text", arg)));
    }

    private void confirmRemove() {
        selectedMember().ifPresent(member -> confirm("remove", member.name(), () -> send(FellowshipActionPayload.Action.REMOVE, member.name(), false)));
    }

    private void confirmTransfer() {
        selectedMember().ifPresent(member -> confirm("transfer", member.name(), () -> send(FellowshipActionPayload.Action.TRANSFER, member.name(), false)));
    }

    private void confirmLeave() {
        confirm("leave", entry().map(FellowshipView.Entry::name).orElse(""), () -> send(FellowshipActionPayload.Action.LEAVE, "", false));
    }

    private void confirmDisband() {
        confirm("disband", entry().map(FellowshipView.Entry::name).orElse(""), () -> send(FellowshipActionPayload.Action.DISBAND, "", false));
    }

    @Override
    public void tick() {
        if (entry().isEmpty()) {
            // Left, removed or disbanded: the fellowship is gone from the mirror.
            MenuPage.open(MenuPage.FELLOWSHIPS);
            return;
        }
        if (selected != null && selectedMember().isEmpty()) {
            selected = null;
        }
        clampScroll();
        updateButtons();
    }

    private int maxScroll() {
        int count = entry().map(e -> e.members().size()).orElse(0);
        return Math.max(0, count * ROW_HEIGHT - (listBottom - listTop - 2));
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        FellowshipView.Entry entry = entry().orElse(null);
        if (entry == null) {
            return;
        }
        renderHeader(graphics, entry);
        renderMembers(graphics, entry);
        renderOptions(graphics, entry);
    }

    private void renderHeader(GuiGraphics graphics, FellowshipView.Entry entry) {
        int x = listLeft;
        if (!entry.icon().isEmpty()) {
            graphics.renderItem(entry.icon(), x, top + 5);
            x += 20;
        }
        graphics.drawString(font, font.plainSubstrByWidth(entry.name(), rightRight - x), x, top + 6, GOLD, false);
        Component owner = Component.translatable("gui.lotrrevival.menu.fellowships.owner", FellowshipPageModel.ownerName(entry));
        graphics.drawString(font, font.plainSubstrByWidth(owner.getString(), rightRight - x), x, top + 17, DIM, false);
    }

    private void renderMembers(GuiGraphics graphics, FellowshipView.Entry entry) {
        graphics.fill(listLeft, listTop, listRight, listBottom, INNER_BG);
        graphics.renderOutline(listLeft, listTop, listRight - listLeft, listBottom - listTop, PANEL_BORDER);
        graphics.enableScissor(listLeft + 1, listTop + 1, listRight - 1, listBottom - 1);
        List<FellowshipView.Member> members = entry.members();
        for (int n = 0; n < members.size(); n++) {
            int y = listTop + 1 + n * ROW_HEIGHT - scroll;
            if (y + ROW_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            FellowshipView.Member member = members.get(n);
            if (member.id().equals(selected)) {
                graphics.fill(listLeft + 1, y, listRight - 1, y + ROW_HEIGHT, 0xFF3A3020);
            }
            graphics.fill(listLeft + 4, y + 4, listLeft + 8, y + 8, member.online() ? GREEN : GREY);
            MutableComponent built = Component.empty();
            Optional<Component> title = titleOf(member);
            if (title.isPresent()) {
                built.append(title.get()).append(" ");
            }
            int roleColour = member.role() == FellowshipRole.OWNER ? GOLD : member.role() == FellowshipRole.ADMIN ? 0xFF90C0E0 : TEXT;
            built.append(Component.literal(member.name()).withStyle(style -> style.withColor(roleColour & 0xFFFFFF)));
            int maxWidth = listRight - listLeft - 18 - (member.role() == FellowshipRole.MEMBER ? 0 : 34);
            Component line = font.width(built) > maxWidth
                    ? Component.literal(font.plainSubstrByWidth(built.getString(), maxWidth))
                    : built;
            graphics.drawString(font, line, listLeft + 12, y + 2, TEXT, false);
            if (member.role() != FellowshipRole.MEMBER) {
                String role = Component.translatable("fellowship.lotrrevival.role." + member.role().name().toLowerCase(Locale.ROOT)).getString();
                graphics.drawString(font, role, listRight - 8 - font.width(role), y + 2, DIM, false);
            }
        }
        graphics.disableScissor();
        drawScrollbar(graphics, listRight - 4, listTop + 1, listBottom - 1, scroll, maxScroll());
    }

    private static Optional<Component> titleOf(FellowshipView.Member member) {
        Minecraft mc = Minecraft.getInstance();
        if (member.title().isEmpty() || mc.level == null) {
            return Optional.empty();
        }
        ChatFormatting colour = ChatFormatting.getByName(member.color());
        if (colour == null || !colour.isColor()) {
            colour = ChatFormatting.WHITE;
        }
        return TitleService.prefix(mc.level.registryAccess(), member.title().get(), colour, member.feminine());
    }

    private void renderOptions(GuiGraphics graphics, FellowshipView.Entry entry) {
        FellowshipPageModel.Actions actions = FellowshipPageModel.actions(viewerRole());
        FellowshipOption[] options = FellowshipOption.values();
        int labelWidth = rightRight - rightX - 36;
        for (int n = 0; n < options.length; n++) {
            FellowshipOption option = options[n];
            int y = optionTop + n * 22;
            Component label = Component.translatable("fellowship.lotrrevival.option." + option.id());
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(label, labelWidth);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                graphics.drawString(font, lines.get(l), rightX, y + 3 + l * 9, TEXT, false);
            }
            boolean on = optionValue(entry, option);
            boolean allowed = option.ownerOnly() ? actions.toggleMap() : actions.toggleCombat();
            Component state = Component.translatable("gui.lotrrevival.menu.fellowships.option." + (on ? "on" : "off"));
            if (allowed) {
                // The toggle button sits at the right edge; draw its state over it.
                graphics.drawCenteredString(font, state, rightRight - 16, y + 6, on ? GREEN : RED);
            } else {
                graphics.drawString(font, state, rightRight - font.width(state), y + 6, on ? GREEN : RED, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && mouseX >= listLeft && mouseX < listRight - 4 && mouseY >= listTop && mouseY < listBottom) {
            int index = ((int) mouseY - listTop - 1 + scroll) / ROW_HEIGHT;
            List<FellowshipView.Member> members = new ArrayList<>(entry().map(FellowshipView.Entry::members).orElse(List.of()));
            if (index >= 0 && index < members.size()) {
                selected = members.get(index).id();
                updateButtons();
                return true;
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
