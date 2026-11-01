package io.github.sethwillemsvg.lotrrevival.client.render;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.sevenstars.middleearth.client.ModTexturedRenderLayers;
import net.sevenstars.middleearth.client.renderer.RenderResourceCache;
import net.sevenstars.middleearth.client.renderer.RenderResourceCache.NpcPrefix;
import net.sevenstars.middleearth.config.ModClientConfigs;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.data.NpcTextureData;
import net.sevenstars.middleearth.entity.npcs.renderer.NpcEntityRenderer;
import net.sevenstars.middleearth.entity.npcs.renderer.NpcRenderedPart;
import net.sevenstars.middleearth.registries.AtlasesME;
import net.sevenstars.middleearth.registries.CharacterClothesRegistryME;

import java.util.ArrayList;
import java.util.List;

/**
 * The skin layers a Middle-earth NPC is drawn with, bottom to top, as sprites of their character atlas: skin, eyes (unless
 * blinking), eyebrows, scar, beard, clothing, hair. Both of our renderers draw exactly these, so they can be compared.
 * Not drawn by either (yet): 3D hair/ears/nose/feet, emissive eyes. Armour and held items are Minecraft layers in both.
 */
public final class NpcSkin {
    private NpcSkin() {}

    public static TextureAtlas atlas() {
        return AtlasesME.getAtlasFromPath(ModTexturedRenderLayers.CHARACTER_ATLAS_TEXTURES);
    }

    public static List<TextureAtlasSprite> layers(NpcEntity entity, TextureAtlas atlas) {
        List<TextureAtlasSprite> out = new ArrayList<>(10);
        if (entity.isInvisible()) return out;
        NpcTextureData data = entity.retrieveNpcTextureData();
        if (data == null) return out;
        if (ModClientConfigs.ENABLE_SIMPLIFIED_CHARACTER_RENDERING && data.getSimplifiedSkin() != null) {
            out.add(atlas.getSprite(data.getSimplifiedSkin()));
            return out;
        }
        if (data.get(NpcRenderedPart.BODY) == null || data.get(NpcRenderedPart.HEAD) == null || data.get(NpcRenderedPart.EYE) == null) return out;
        add(out, atlas, data.get(NpcRenderedPart.BODY), NpcPrefix.SKIN);
        add(out, atlas, data.get(NpcRenderedPart.HEAD), NpcPrefix.SKIN);
        int blink = entity.tickCount % NpcEntityRenderer.BLINKING_INTERVAL;
        if (blink < NpcEntityRenderer.BLINKING_INTERVAL - NpcEntityRenderer.BLINKING_DURATION) add(out, atlas, data.get(NpcRenderedPart.EYE), NpcPrefix.EYE);
        add(out, atlas, data.get(NpcRenderedPart.EYEBROW), NpcPrefix.HAIR);
        add(out, atlas, data.get(NpcRenderedPart.SCAR), NpcPrefix.SKIN);
        add(out, atlas, data.get(NpcRenderedPart.BEARD), NpcPrefix.HAIR);
        ResourceLocation base = data.get(NpcRenderedPart.CLOTHING_BASE), over = data.get(NpcRenderedPart.CLOTHING_OVER),
                extra = data.get(NpcRenderedPart.CLOTHING_EXTRA);
        if (base == null && over == null && extra == null) add(out, atlas, CharacterClothesRegistryME.Base.THONG_BROWN, NpcPrefix.CLOTHES_BASE);
        add(out, atlas, base, NpcPrefix.CLOTHES_BASE);
        add(out, atlas, over, NpcPrefix.CLOTHES_OVER);
        add(out, atlas, extra, NpcPrefix.CLOTHES_EXTRA);
        add(out, atlas, data.get(NpcRenderedPart.HAIR), NpcPrefix.HAIR);
        return out;
    }

    private static void add(List<TextureAtlasSprite> out, TextureAtlas atlas, ResourceLocation id, NpcPrefix prefix) {
        if (id != null) out.add(atlas.getSprite(RenderResourceCache.npcTexture(id, prefix)));
    }
}
