package io.github.sethwillemsvg.lotrrevival.client.progression;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Our own achievement toast: the entry icon, a heading and the title. Replaces the vanilla advancement toast. */
public final class AchievementToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_MILLIS = 5000L;
    private static final int TEXT_WIDTH = 125;
    private static final int HEADING_COLOR = 0xFFFFFF00;

    private final AchievementEntry entry;

    public AchievementToast(AchievementEntry entry) {
        this.entry = entry;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long timeSinceLastVisible) {
        graphics.blitSprite(BACKGROUND, 0, 0, width(), height());
        Font font = toasts.getMinecraft().font;
        graphics.drawString(font, Component.translatable("toast.lotrrevival.achievement"), 30, 7, HEADING_COLOR, false);
        FormattedCharSequence title =
                Language.getInstance().getVisualOrder(font.substrByWidth(entry.title(), TEXT_WIDTH));
        graphics.drawString(font, title, 30, 18, 0xFFFFFFFF, false);
        graphics.renderFakeItem(entry.icon(), 8, 8);
        return timeSinceLastVisible >= DISPLAY_MILLIS * toasts.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE
                : Visibility.SHOW;
    }
}
