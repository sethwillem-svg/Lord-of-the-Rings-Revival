package io.github.sethwillemsvg.lotrrevival.client.core.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.ranged.BoltEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Draws a crossbow bolt with the vanilla arrow model and our own texture. */
public class BoltRenderer extends ArrowRenderer<BoltEntity> {
    private static final ResourceLocation TEXTURE = LOTRRevival.id("textures/entity/projectiles/bolt.png");

    public BoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(BoltEntity entity) {
        return TEXTURE;
    }
}
