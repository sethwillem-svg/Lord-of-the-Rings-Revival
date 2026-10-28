package io.github.sethwillemsvg.lotrrevival.client.core.table;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTableMenu;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTables;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The faction table screen: the vanilla crafting grid layout, titled with the table's name. */
public class FactionTableScreen extends AbstractContainerScreen<FactionTableMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");

    public FactionTableScreen(FactionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 29;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    /** Registers the screen on the client mod bus. */
    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(FactionTables.MENU.get(), FactionTableScreen::new);
        }
    }
}
