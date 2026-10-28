package io.github.sethwillemsvg.lotrrevival.client.progression.banner;

import io.github.sethwillemsvg.lotrrevival.client.progression.fellowship.ClientFellowships;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerPermission;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerSettings;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipView;
import io.github.sethwillemsvg.lotrrevival.progression.network.OpenBannerEditorPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.UpdateBannerPayload;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The banner edit screen: mode, threshold, self-protection, default permissions and the whitelist (player and
 * fellowship rows, eight permission toggles each; the owner's row is locked). Operators also see the custom range.
 * Save sends {@code update_banner}; the server validates everything again.
 */
public final class BannerEditorScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int ROWS_PER_PAGE = 5;
    private static final int ROW_HEIGHT = 20;
    private static final int TOGGLE = 18;
    private static final String[] SHORT = {"Fu", "Dr", "Ta", "Co", "Pe", "Fo", "Be", "Sw"};

    /** An editable whitelist row. */
    private static final class Row {
        final BannerSettings.Kind kind;
        final UUID id;
        final String name;
        final Set<BannerPermission> permissions;

        Row(BannerSettings.Kind kind, UUID id, String name, Set<BannerPermission> permissions) {
            this.kind = kind;
            this.id = id;
            this.name = name;
            this.permissions = EnumSet.noneOf(BannerPermission.class);
            this.permissions.addAll(permissions);
        }
    }

    private final OpenBannerEditorPayload data;
    private BannerSettings.Mode mode;
    private boolean selfProtection;
    private final Set<BannerPermission> defaults = EnumSet.noneOf(BannerPermission.class);
    private final List<Row> rows = new ArrayList<>();
    private String thresholdText;
    private String rangeText;
    private String nameText = "";
    private int page;
    private int fellowshipIndex;

    private EditBox thresholdBox;
    private EditBox rangeBox;
    private EditBox nameBox;
    private int left;
    private int top;

    BannerEditorScreen(OpenBannerEditorPayload data) {
        super(Component.translatable("gui.lotrrevival.banner.title", data.settings().ownerName()));
        this.data = data;
        BannerSettings settings = data.settings();
        this.mode = settings.mode();
        this.selfProtection = settings.selfProtection();
        this.defaults.addAll(settings.defaults());
        for (BannerSettings.Entry entry : settings.entries()) {
            rows.add(new Row(entry.kind(), entry.id(), entry.name(), entry.permissions()));
        }
        this.thresholdText = Integer.toString(settings.threshold());
        this.rangeText = Integer.toString(settings.customRange());
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = Math.max(4, (height - 262) / 2);

        addRenderableWidget(Button.builder(modeLabel(), button -> {
                    captureText();
                    mode = mode == BannerSettings.Mode.FACTION
                            ? BannerSettings.Mode.WHITELIST
                            : BannerSettings.Mode.FACTION;
                    rebuildWidgets();
                })
                .bounds(left, top + 18, 144, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.lotrrevival.banner.mode.tooltip")))
                .build());
        addRenderableWidget(Button.builder(selfProtectionLabel(), button -> {
                    captureText();
                    selfProtection = !selfProtection;
                    rebuildWidgets();
                })
                .bounds(left + 156, top + 18, 144, 20)
                .build());

        thresholdBox = new EditBox(font, left + 110, top + 44, 56, 18, Component.empty());
        thresholdBox.setMaxLength(5);
        thresholdBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        thresholdBox.setValue(thresholdText);
        addRenderableWidget(thresholdBox);
        if (data.op()) {
            rangeBox = new EditBox(font, left + 244, top + 44, 40, 18, Component.empty());
            rangeBox.setMaxLength(2);
            rangeBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
            rangeBox.setValue(rangeText);
            addRenderableWidget(rangeBox);
        } else {
            rangeBox = null;
        }

        // Default permissions
        addToggles(left + 150, top + 70, defaults);

        // Whitelist rows: the owner first (locked), then the entries, five to a page.
        int total = rows.size() + 1;
        int pages = Math.max(1, (total + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        int first = page * ROWS_PER_PAGE;
        for (int n = 0; n < ROWS_PER_PAGE; n++) {
            int index = first + n;
            if (index == 0 || index >= total) {
                continue;
            }
            Row row = rows.get(index - 1);
            int y = top + 112 + n * ROW_HEIGHT;
            addToggles(left + 106, y, row.permissions);
            addRenderableWidget(Button.builder(Component.literal("x"), button -> {
                        captureText();
                        rows.remove(row);
                        rebuildWidgets();
                    })
                    .bounds(left + 106 + 8 * TOGGLE + 4, y, 18, 18)
                    .tooltip(Tooltip.create(Component.translatable("gui.lotrrevival.banner.remove")))
                    .build());
        }

        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                    captureText();
                    page--;
                    rebuildWidgets();
                })
                .bounds(left, top + 214, 20, 20)
                .build())
                .active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                    captureText();
                    page++;
                    rebuildWidgets();
                })
                .bounds(left + 24, top + 214, 20, 20)
                .build())
                .active = page < pages - 1;

        // Adding entries
        boolean room = rows.size() + 1 < data.settings().whitelistSize();
        nameBox = new EditBox(font, left + 50, top + 215, 90, 18, Component.empty());
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable("gui.lotrrevival.banner.player_name"));
        nameBox.setValue(nameText);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.banner.add_player"), button -> {
                    String typed = nameBox.getValue().trim();
                    if (!typed.isEmpty() && rows.size() + 1 < data.settings().whitelistSize()) {
                        captureText();
                        rows.add(new Row(
                                BannerSettings.Kind.PLAYER,
                                UpdateBannerPayload.NIL,
                                typed,
                                EnumSet.of(BannerPermission.FULL)));
                        nameText = "";
                        page = Integer.MAX_VALUE;
                        rebuildWidgets();
                    }
                })
                .bounds(left + 144, top + 214, 66, 20)
                .build())
                .active = room;
        List<FellowshipView.Entry> mine = availableFellowships();
        if (!mine.isEmpty()) {
            fellowshipIndex = Math.floorMod(fellowshipIndex, mine.size());
            FellowshipView.Entry chosen = mine.get(fellowshipIndex);
            addRenderableWidget(Button.builder(Component.literal(chosen.name()), button -> {
                        fellowshipIndex++;
                        captureText();
                        rebuildWidgets();
                    })
                    .bounds(left + 214, top + 214, 56, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                        if (rows.size() + 1 < data.settings().whitelistSize()) {
                            captureText();
                            rows.add(new Row(
                                    BannerSettings.Kind.FELLOWSHIP,
                                    chosen.id(),
                                    chosen.name(),
                                    EnumSet.of(BannerPermission.FULL)));
                            page = Integer.MAX_VALUE;
                            rebuildWidgets();
                        }
                    })
                    .bounds(left + 274, top + 214, 26, 20)
                    .tooltip(Tooltip.create(Component.translatable("gui.lotrrevival.banner.add_fellowship")))
                    .build())
                    .active = room;
        } else {
            addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.banner.no_fellowship"), b -> {})
                    .bounds(left + 214, top + 214, 86, 20)
                    .build())
                    .active = false;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.banner.done"), button -> save())
                .bounds(left, top + 240, 144, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.banner.cancel"), button -> onClose())
                .bounds(left + 156, top + 240, 144, 20)
                .build());
    }

    /** The fellowships that can still be added: the viewer's, minus those already listed. */
    private List<FellowshipView.Entry> availableFellowships() {
        List<FellowshipView.Entry> available = new ArrayList<>();
        for (FellowshipView.Entry entry : ClientFellowships.fellowships()) {
            boolean listed = rows.stream()
                    .anyMatch(row -> row.kind == BannerSettings.Kind.FELLOWSHIP && row.id.equals(entry.id()));
            if (!listed) {
                available.add(entry);
            }
        }
        return available;
    }

    private void addToggles(int x, int y, Set<BannerPermission> set) {
        BannerPermission[] all = BannerPermission.values();
        for (int n = 0; n < all.length; n++) {
            BannerPermission permission = all[n];
            Button button = Button.builder(toggleLabel(n, set.contains(permission)), b -> {
                        if (!set.remove(permission)) {
                            set.add(permission);
                        }
                        b.setMessage(toggleLabel(permission.ordinal(), set.contains(permission)));
                    })
                    .bounds(x + n * TOGGLE, y, TOGGLE, 18)
                    .tooltip(Tooltip.create(Component.translatable(
                            "gui.lotrrevival.banner.permission." + permission.getSerializedName())))
                    .build();
            addRenderableWidget(button);
        }
    }

    private static Component toggleLabel(int index, boolean on) {
        return Component.literal(SHORT[index]).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    private Component modeLabel() {
        return Component.translatable(
                "gui.lotrrevival.banner.mode",
                Component.translatable("gui.lotrrevival.banner.mode." + mode.getSerializedName()));
    }

    private Component selfProtectionLabel() {
        return Component.translatable(
                "gui.lotrrevival.banner.self_protection",
                Component.translatable(selfProtection ? "gui.lotrrevival.banner.on" : "gui.lotrrevival.banner.off"));
    }

    /** Keeps what was typed across a widget rebuild. */
    private void captureText() {
        if (thresholdBox != null) {
            thresholdText = thresholdBox.getValue();
        }
        if (rangeBox != null) {
            rangeText = rangeBox.getValue();
        }
        if (nameBox != null) {
            nameText = nameBox.getValue();
        }
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save() {
        captureText();
        int threshold = Math.max(
                BannerSettings.MIN_THRESHOLD,
                Math.min(BannerSettings.MAX_THRESHOLD, parse(thresholdText, data.settings().threshold())));
        int range = data.op() ? Math.max(0, Math.min(64, parse(rangeText, 0))) : data.settings().customRange();
        List<UpdateBannerPayload.EntryEdit> edits = new ArrayList<>();
        for (Row row : rows) {
            edits.add(new UpdateBannerPayload.EntryEdit(
                    row.kind, row.id, row.name, UpdateBannerPayload.mask(row.permissions)));
        }
        PacketDistributor.sendToServer(new UpdateBannerPayload(
                data.pos(), mode, threshold, edits, UpdateBannerPayload.mask(defaults), selfProtection, range));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top + 4, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.lotrrevival.banner.threshold"), left, top + 49, 0xE0E0E0);
        if (data.op()) {
            graphics.drawString(font, Component.translatable("gui.lotrrevival.banner.range"), left + 174, top + 49, 0xE0E0E0);
        }
        graphics.drawString(font, Component.translatable("gui.lotrrevival.banner.defaults"), left, top + 75, 0xE0E0E0);

        int total = rows.size() + 1;
        int pages = Math.max(1, (total + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        graphics.drawString(
                font,
                Component.translatable("gui.lotrrevival.banner.whitelist", total, data.settings().whitelistSize()),
                left,
                top + 98,
                0xE0E0E0);
        graphics.drawString(
                font,
                Component.translatable("gui.lotrrevival.banner.page", page + 1, pages),
                left + 190,
                top + 98,
                0xA0A0A0);
        int first = page * ROWS_PER_PAGE;
        for (int n = 0; n < ROWS_PER_PAGE; n++) {
            int index = first + n;
            if (index >= total) {
                break;
            }
            int y = top + 112 + n * ROW_HEIGHT;
            if (index == 0) {
                graphics.drawString(font, font.plainSubstrByWidth(data.settings().ownerName(), 70), left, y + 5, 0xFFD060);
                graphics.drawString(font, Component.translatable("gui.lotrrevival.banner.locked"), left + 106, y + 5, 0x909090);
            } else {
                Row row = rows.get(index - 1);
                int color = row.kind == BannerSettings.Kind.FELLOWSHIP ? 0x55FFFF : 0xFFFFFF;
                graphics.drawString(font, font.plainSubstrByWidth(row.name, 100), left, y + 5, color);
            }
        }
    }
}
