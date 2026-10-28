package io.github.sethwillemsvg.lotrrevival.client.progression.quest.log;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestBookItem;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The HUD tracker of the tracked quest (140 x 32), and the client hookup of the quest book. Hidden with F1, F3 or
 * any open screen.
 */
public final class QuestTrackerHud {
    public static final int WIDTH = 140;
    public static final int HEIGHT = 32;
    private static final int MARGIN = 4;
    private static final int TOP = 30;
    private static final ResourceLocation LAYER = LOTRRevival.id("quest_tracker");

    private QuestTrackerHud() {}

    /** True when the tracker may be drawn right now. */
    static boolean visible(Minecraft mc) {
        return mc.player != null
                && mc.level != null
                && mc.screen == null
                && !mc.options.hideGui
                && !mc.getDebugOverlay().showDebugScreen();
    }

    /** Draws the tracker for {@code quest} at {@code x, y}. */
    static void draw(GuiGraphics graphics, Minecraft mc, QuestInstance quest, int x, int y) {
        Font font = mc.font;
        var access = mc.level.registryAccess();
        int tint = QuestLogText.tint(access, quest.group());
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0x90000000);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0x30000000 | tint);
        graphics.fill(x, y, x + 2, y + HEIGHT, 0xFF000000 | tint);
        graphics.renderOutline(x, y, WIDTH, HEIGHT, 0x80000000 | tint);
        graphics.renderItem(QuestLogText.icon(access, quest), x + 5, y + 8);
        int textX = x + 25;
        int textWidth = WIDTH - 25 - 4;
        Component objective = QuestLogText.shortObjective(access, quest);
        graphics.drawString(font, font.plainSubstrByWidth(objective.getString(), textWidth), textX, y + 6, 0xFFFFFFFF, true);
        Component status = QuestLogText.shortStatus(quest);
        int color = quest.failed() ? 0xFFFF6050 : 0xFFE0D8B8;
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), textWidth), textX, y + 18, color, true);
    }

    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, (graphics, deltaTracker) -> {
                Minecraft mc = Minecraft.getInstance();
                if (!visible(mc)) {
                    return;
                }
                io.github.sethwillemsvg.lotrrevival.client.progression.ClientQuests.tracked().ifPresent(quest -> {
                    boolean right = "right".equals(ProgressionConfig.get(ProgressionConfig.QUEST_TRACKER_SIDE));
                    int x = right ? graphics.guiWidth() - MARGIN - WIDTH : MARGIN;
                    int y = TOP;
                    draw(graphics, mc, quest, x, y);
                });
            });
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> QuestBookItem.setClientOpener(QuestLogScreen::openFromBook));
        }
    }
}
