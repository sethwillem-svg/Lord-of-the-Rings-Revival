package io.github.sethwillemsvg.lotrrevival.client.progression.banner;

import io.github.sethwillemsvg.lotrrevival.progression.network.OpenBannerEditorPayload;
import net.minecraft.client.Minecraft;

/** Opens the banner edit screen when the server asks for it. */
public final class BannerEditorClient {
    private BannerEditorClient() {}

    public static void open(OpenBannerEditorPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new BannerEditorScreen(payload));
    }
}
