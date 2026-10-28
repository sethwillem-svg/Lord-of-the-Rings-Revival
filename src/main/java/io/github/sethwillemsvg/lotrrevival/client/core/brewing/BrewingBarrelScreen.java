package io.github.sethwillemsvg.lotrrevival.client.core.brewing;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BarrelState;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingBarrelBlockEntity;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingBarrelMenu;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingBlocks;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Screen of the brewing barrel: slots, progress bar, fill gauge, Brew button and a status line. */
public class BrewingBarrelScreen extends AbstractContainerScreen<BrewingBarrelMenu> {
    private static final ResourceLocation BACKGROUND = LOTRRevival.id("textures/gui/container/brewing_barrel.png");
    private static final ResourceLocation PROGRESS = LOTRRevival.id("container/brewing_barrel/progress");
    private static final ResourceLocation GAUGE = LOTRRevival.id("container/brewing_barrel/fill_gauge");
    private static final WidgetSprites BUTTON_SPRITES = new WidgetSprites(
            LOTRRevival.id("container/brewing_barrel/button"),
            LOTRRevival.id("container/brewing_barrel/button_disabled"),
            LOTRRevival.id("container/brewing_barrel/button_highlighted"));

    private static final int BAR_X = 92;
    private static final int PROGRESS_Y = 18;
    private static final int GAUGE_Y = 32;
    private static final int BAR_W = 52;
    private static final int BAR_H = 8;
    private static final int BUTTON_Y = 48;
    private static final int BUTTON_W = 52;
    private static final int BUTTON_H = 18;

    private ImageButton brewButton;

    public BrewingBarrelScreen(BrewingBarrelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 1000; // replaced by the status line
    }

    @Override
    protected void init() {
        super.init();
        brewButton = addRenderableWidget(new ImageButton(
                leftPos + BAR_X,
                topPos + BUTTON_Y,
                BUTTON_W,
                BUTTON_H,
                BUTTON_SPRITES,
                button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BrewingBarrelMenu.BUTTON_BREW);
                    }
                },
                Component.translatable("gui.lotrrevival.brewing_barrel.brew")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (brewButton != null) {
            brewButton.active = menu.canPressBrew();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component label = Component.translatable(
                menu.state() == BarrelState.BREWING
                        ? "gui.lotrrevival.brewing_barrel.stop"
                        : "gui.lotrrevival.brewing_barrel.brew");
        graphics.drawCenteredString(
                font,
                label,
                leftPos + BAR_X + BUTTON_W / 2,
                topPos + BUTTON_Y + (BUTTON_H - 8) / 2,
                brewButton != null && brewButton.active ? 0xFFFFFF : 0xA0A0A0);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 166);

        if (menu.state() == BarrelState.BREWING) {
            int width = Math.min(BAR_W, menu.elapsed() * BAR_W / BrewingBarrelBlockEntity.TOTAL_TICKS);
            if (width > 0) {
                graphics.blitSprite(PROGRESS, BAR_W, BAR_H, 0, 0, leftPos + BAR_X, topPos + PROGRESS_Y, width, BAR_H);
            }
        }
        int servingWidth = menu.servings() * BAR_W / BrewingBarrelBlockEntity.SERVINGS;
        if (servingWidth > 0) {
            graphics.blitSprite(GAUGE, BAR_W, BAR_H, 0, 0, leftPos + BAR_X, topPos + GAUGE_Y, servingWidth, BAR_H);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, statusLine(), 8, 73, 0x404040, false);
    }

    private Component statusLine() {
        ItemStack drink = menu.resultStack();
        return switch (menu.state()) {
            case EMPTY -> drink.isEmpty() ? BrewingStatus.empty() : BrewingStatus.ready(drink.getItem());
            case BREWING -> BrewingStatus.brewing(drink.getItem(), menu.tier());
            case FULL -> BrewingStatus.full(drink.getItem(), menu.tier(), menu.servings());
        };
    }

    /** Registers the screen on the client mod bus. */
    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(BrewingBlocks.BARREL_MENU.get(), BrewingBarrelScreen::new);
        }
    }
}
