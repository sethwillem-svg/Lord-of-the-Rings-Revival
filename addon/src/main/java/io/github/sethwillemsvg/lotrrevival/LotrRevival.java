package io.github.sethwillemsvg.lotrrevival;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;

/** Add-on for the Middle-earth mod. For now it only re-poses Middle-earth NPCs with our clips (client side, see client.anim). */
@Mod(LotrRevival.MOD_ID)
public final class LotrRevival {
    public static final String MOD_ID = "lotr_revival";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
