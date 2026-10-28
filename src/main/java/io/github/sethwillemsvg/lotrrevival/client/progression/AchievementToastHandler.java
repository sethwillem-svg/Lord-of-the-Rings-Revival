package io.github.sethwillemsvg.lotrrevival.client.progression;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntries;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Client handler of the {@code achievement_toast} payload. Only ever called on the client. */
public final class AchievementToastHandler {
    private AchievementToastHandler() {}

    public static void show(ResourceLocation id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        AchievementEntries.find(minecraft.level.registryAccess(), id)
                .ifPresent(entry -> minecraft.getToasts().addToast(new AchievementToast(entry)));
    }
}
