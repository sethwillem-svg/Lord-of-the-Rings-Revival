package io.github.sethwillemsvg.lotrrevival.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sethwillemsvg.lotrrevival.client.anim.BowDraw;
import io.github.sethwillemsvg.lotrrevival.combat.WeaponClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Draws a held item in hand space the way Minecraft's held-item layer does, with one fix for Middle-earth's melee weapons.
 * Middle-earth resolves every item model as its inventory icon first (its mixin on ItemRenderer.getModel) and swaps in a
 * held model only for items that register one; its big swords and longswords register none, so in hand they are drawn as
 * the small flat inventory icon (also in Middle-earth's own renderer). Here melee weapons use their normal item model
 * (Middle-earth's big held model with its display transforms) directly. Bows and longbows do too, with the drawn model
 * (slack, pulling_0..2) their overrides give for the stage the NPC's bow clip has reached (BowPull, client.anim.BowDraw),
 * not for the entity's using-item state. Everything else goes through Minecraft's path.
 */
final class HeldItems {
    private HeldItems() {}

    private static final Set<String> MELEE = Set.of("dagger", "sword", "longsword", "axe", "spear");

    static void render(ItemInHandRenderer itemInHand, LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                       boolean left, PoseStack poseStack, MultiBufferSource buffers, int light) {
        render(itemInHand, entity, stack, context, left, poseStack, buffers, light, BowDraw.SLACK);
    }

    /** As render, a bow drawn to bowStage (client.anim.BowDraw SLACK..PULLING_2; other items ignore it). */
    static void render(ItemInHandRenderer itemInHand, LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                       boolean left, PoseStack poseStack, MultiBufferSource buffers, int light, int bowStage) {
        BakedModel model = heldModel(entity, stack, bowStage);
        if (model == null) {
            itemInHand.renderItem(entity, stack, context, left, poseStack, buffers, light);
            return;
        }
        Minecraft.getInstance().getItemRenderer().render(stack, context, left, poseStack, buffers, light, OverlayTexture.NO_OVERLAY, model);
    }

    /** The normal item model for a Middle-earth melee weapon (with its overrides, e.g. blocking) or for a bow at bowStage,
     *  or null to use Minecraft's path. */
    private static BakedModel heldModel(LivingEntity entity, ItemStack stack, int bowStage) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String cls = WeaponClass.of(stack);
        boolean bow = WeaponGrip.isBow(cls);
        if (!bow && (!"middle-earth".equals(id.getNamespace()) || !MELEE.contains(String.valueOf(cls)))) return null;
        BakedModel base = Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.inventory(id));
        if (base == Minecraft.getInstance().getModelManager().getMissingModel()) return null;
        ClientLevel level = entity.level() instanceof ClientLevel cl ? cl : null;
        if (bow) return BowPull.resolve(base, stack, level, entity, entity.getId(), bowStage);
        BakedModel resolved = base.getOverrides().resolve(base, stack, level, entity, entity.getId());
        return resolved != null ? resolved : base;
    }
}
