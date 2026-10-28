package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import io.github.sethwillemsvg.lotrrevival.client.progression.shield.ClientShields;
import io.github.sethwillemsvg.lotrrevival.progression.api.ProgressionApi;
import io.github.sethwillemsvg.lotrrevival.progression.network.SelectShieldPayload;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldDefinition;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldDefinitions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Shields page: browse the shields with arrows, preview one, Select or Remove. Locked shields show what
 * unlocks them. Select only sends the choice; the page shows whatever the server answers through the mirror.
 */
public final class ShieldsScreen extends MenuScreen {
    /** The plate texture is 32x32 with the design at (2,2) size 10x16. */
    private static final int TEX_SIZE = 32;

    private record Entry(ResourceLocation id, ShieldDefinition definition) {}

    private List<Entry> entries = List.of();
    private int index;
    private boolean positioned;
    private Button previous;
    private Button next;
    private Button select;
    private Button remove;
    private int previewCenterX;
    private int previewTop;
    private int scale;

    ShieldsScreen() {
        super(MenuPage.SHIELDS, Component.translatable("gui.lotrrevival.menu.shields"));
    }

    @Override
    protected void buildPage() {
        refreshEntries();
        if (!positioned) {
            positioned = true;
            Optional<ResourceLocation> current = current();
            for (int n = 0; n < entries.size(); n++) {
                if (current.isPresent() && entries.get(n).id().equals(current.get())) {
                    index = n;
                }
            }
        }

        int footerY = addFooter(true);
        previewCenterX = left + panelWidth / 2;
        previewTop = top + 44;
        scale = Math.max(2, Math.min(6, (footerY - 74 - previewTop) / 16));

        int arrowY = previewTop + (16 * scale) / 2 - 9;
        int arrowOffset = Math.max(40, 5 * scale + 14);
        previous = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.shields.previous"), b -> browse(-1))
                .bounds(previewCenterX - arrowOffset - 20, arrowY, 20, 18)
                .build());
        next = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.shields.next"), b -> browse(1))
                .bounds(previewCenterX + arrowOffset, arrowY, 20, 18)
                .build());
        int buttonsY = footerY - 22;
        select = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.shields.select"), b -> select())
                .bounds(previewCenterX - 86, buttonsY, 84, 18)
                .build());
        remove = addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.menu.shields.remove"), b -> remove())
                .bounds(previewCenterX + 2, buttonsY, 84, 18)
                .build());
        updateButtons();
    }

    private Optional<ResourceLocation> current() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? Optional.empty() : ClientShields.get(player.getUUID());
    }

    private int achievementCount() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0 : ProgressionApi.achievementCount(player);
    }

    /** The shields the player can browse: unlocked ones, and locked ones unless they are hidden until unlocked. */
    private void refreshEntries() {
        LocalPlayer player = Minecraft.getInstance().player;
        List<Entry> next = new ArrayList<>();
        if (player != null) {
            int count = achievementCount();
            for (Map.Entry<ResourceLocation, ShieldDefinition> e :
                    ShieldDefinitions.all(player.level().registryAccess()).entrySet()) {
                ShieldDefinition definition = e.getValue();
                if (definition.hiddenUntilUnlocked() && !definition.unlock().isMet(count)) {
                    continue;
                }
                next.add(new Entry(e.getKey(), definition));
            }
        }
        entries = next;
        index = entries.isEmpty() ? 0 : Math.max(0, Math.min(index, entries.size() - 1));
    }

    private Entry shown() {
        return entries.isEmpty() ? null : entries.get(index);
    }

    private boolean unlocked(Entry entry) {
        return entry.definition().unlock().isMet(achievementCount());
    }

    private void browse(int step) {
        if (entries.isEmpty()) {
            return;
        }
        index = Math.floorMod(index + step, entries.size());
        updateButtons();
    }

    private void select() {
        Entry entry = shown();
        if (entry != null && unlocked(entry)) {
            PacketDistributor.sendToServer(new SelectShieldPayload(Optional.of(entry.id())));
        }
    }

    private void remove() {
        PacketDistributor.sendToServer(new SelectShieldPayload(Optional.empty()));
    }

    private void updateButtons() {
        if (select == null) {
            return;
        }
        Entry entry = shown();
        previous.active = entries.size() > 1;
        next.active = entries.size() > 1;
        select.active = entry != null && unlocked(entry) && !Optional.of(entry.id()).equals(current());
        remove.active = current().isPresent();
    }

    @Override
    public void tick() {
        refreshEntries();
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(
                font, Component.translatable("gui.lotrrevival.menu.shields.header"), left + panelWidth / 2, top + 7, GOLD);
        Entry entry = shown();
        if (entry == null) {
            graphics.drawCenteredString(
                    font, Component.translatable("gui.lotrrevival.menu.shields.none"), previewCenterX, top + panelHeight / 2 - 4, DIM);
            return;
        }
        graphics.drawCenteredString(
                font,
                Component.translatable(ShieldDefinition.nameKey(entry.id())),
                previewCenterX,
                top + 20,
                GOLD);

        int width = 10 * scale;
        int height = 16 * scale;
        int x = previewCenterX - width / 2;
        graphics.fill(x - 3, previewTop - 3, x + width + 3, previewTop + height + 3, INNER_BG);
        graphics.renderOutline(x - 3, previewTop - 3, width + 6, height + 6, PANEL_BORDER);
        boolean unlocked = unlocked(entry);
        graphics.blit(entry.definition().texture(), x, previewTop, width, height, 2, 2, 10, 16, TEX_SIZE, TEX_SIZE);
        if (!unlocked) {
            graphics.fill(x, previewTop, x + width, previewTop + height, 0xA0000000);
        }

        int infoY = previewTop + height + 8;
        Component state;
        if (Optional.of(entry.id()).equals(current())) {
            state = Component.translatable("gui.lotrrevival.menu.shields.state.selected");
        } else if (unlocked) {
            state = Component.translatable("gui.lotrrevival.menu.shields.state.unlocked");
        } else {
            state = Component.translatable("gui.lotrrevival.menu.shields.state.locked");
        }
        graphics.drawCenteredString(font, state, previewCenterX, infoY, unlocked ? TEXT : DIM);
        if (!unlocked) {
            graphics.drawCenteredString(
                    font, entry.definition().unlock().lockReason(), previewCenterX, infoY + 11, 0xFFD0A050);
        }
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.lotrrevival.menu.shields.counter", index + 1, entries.size()),
                previewCenterX,
                previewTop - 13,
                DIM);
    }
}
