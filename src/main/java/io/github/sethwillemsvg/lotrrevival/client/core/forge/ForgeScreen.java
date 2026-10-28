package io.github.sethwillemsvg.lotrrevival.client.core.forge;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.forge.ForgeBlockEntity;
import io.github.sethwillemsvg.lotrrevival.core.forge.ForgeBlocks;
import io.github.sethwillemsvg.lotrrevival.core.forge.ForgeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Screen of the forges: four lanes with progress bars and a flame for the shared fuel. */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
    private static final ResourceLocation BACKGROUND = LOTRRevival.id("textures/gui/container/forge.png");
    private static final ResourceLocation FLAME = LOTRRevival.id("container/forge/flame");
    private static final ResourceLocation PROGRESS = LOTRRevival.id("container/forge/progress");

    private static final int FLAME_SIZE = 14;
    private static final int BAR_W = 16;
    private static final int BAR_H = 6;

    public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 166);

        if (menu.isBurning()) {
            int lit = Mth.ceil(menu.burnFraction() * FLAME_SIZE);
            graphics.blitSprite(
                    FLAME,
                    FLAME_SIZE,
                    FLAME_SIZE,
                    0,
                    FLAME_SIZE - lit,
                    leftPos + ForgeMenu.FLAME_X,
                    topPos + ForgeMenu.FLAME_Y + FLAME_SIZE - lit,
                    FLAME_SIZE,
                    lit);
        }
        for (int lane = 0; lane < ForgeBlockEntity.LANES; lane++) {
            int width = Mth.ceil(menu.progress(lane) * BAR_W);
            if (width > 0) {
                graphics.blitSprite(
                        PROGRESS, BAR_W, BAR_H, 0, 0, leftPos + ForgeMenu.laneX(lane), topPos + ForgeMenu.PROGRESS_Y, width, BAR_H);
            }
        }
    }

    /** Registers the screen on the client mod bus. */
    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(ForgeBlocks.MENU.get(), ForgeScreen::new);
        }
    }
}
