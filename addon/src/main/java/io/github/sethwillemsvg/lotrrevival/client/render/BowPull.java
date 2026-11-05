package io.github.sethwillemsvg.lotrrevival.client.render;

import io.github.sethwillemsvg.lotrrevival.client.anim.BowDraw;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Picks a bow's drawn model (slack, pulling_0..2) from a pull of our own instead of the entity's using-item state.
 * Minecraft and Middle-earth pick it with the item's model overrides on the "pulling" and "pull" predicates, whose
 * functions read how long the entity has been using the item; our NPCs draw on the clip's time (client.anim.BowDraw),
 * and the renderer records each model once, so the entity's state would freeze the first look. Here the item's own
 * overrides are resolved with the stage's predicate values: each item's "pulling" and "pull" functions are wrapped once
 * (on first use) by one that returns ours while resolve runs on this thread, and the item's own function otherwise, so
 * nothing else (a player's bow, Middle-earth's own rendering) sees a change. Nothing is set on the entity.
 */
final class BowPull {
    private BowPull() {}

    static final ResourceLocation PULLING = ResourceLocation.withDefaultNamespace("pulling"), PULL = ResourceLocation.withDefaultNamespace("pull");
    /** The predicate values in force while resolve runs: { pulling, pull }, null otherwise. */
    private static final ThreadLocal<float[]> OURS = new ThreadLocal<>();
    private static final Set<Item> WRAPPED = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The model a bow's overrides give at stage (BowDraw SLACK..PULLING_2), with the item's other predicates (a crossbow's
     *  "charged", ...) as its own functions give them. */
    static BakedModel resolve(BakedModel base, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, int stage) {
        wrap(stack);
        float[] before = OURS.get();
        OURS.set(BowDraw.predicates(stage));
        try {
            BakedModel resolved = base.getOverrides().resolve(base, stack, level, entity, seed);
            return resolved != null ? resolved : base;
        } finally {
            OURS.set(before);
        }
    }

    /** Wraps the item's "pulling" and "pull" functions once (ItemProperties keeps them per item). */
    private static synchronized void wrap(ItemStack stack) {
        Item item = stack.getItem();
        if (!WRAPPED.add(item)) return;
        wrap(item, stack, PULLING, 0);
        wrap(item, stack, PULL, 1);
    }

    @SuppressWarnings("deprecation")   // ItemPropertyFunction: NeoForge asks for ClampedItemPropertyFunction, but getProperty returns this
    private static void wrap(Item item, ItemStack stack, ResourceLocation name, int index) {
        @Nullable ItemPropertyFunction own = ItemProperties.getProperty(stack, name);
        ItemProperties.register(item, name, (s, level, entity, seed) -> {
            float[] ours = OURS.get();
            if (ours != null) return ours[index];
            return own != null ? own.call(s, level, entity, seed) : Float.NEGATIVE_INFINITY;
        });
    }
}
