package io.github.sethwillemsvg.lotrrevival.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sethwillemsvg.lotrrevival.combat.CombatConfig;
import io.github.sethwillemsvg.lotrrevival.combat.Parry;
import io.github.sethwillemsvg.lotrrevival.combat.ParryPayload;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Blade guard input: the use key with a blade in the main hand (no shield, nothing usable in the off hand, not aimed at a
 * block or entity) or the Parry key (unbound by default) sends a ParryPayload; the server stamps the tick. A local cooldown
 * of one guard length keeps a held key from spamming presses (the server enforces the same).
 * TODO(spec ticket 6): a proper first-person guard pose; the main-hand swing stands in. Lang keys for the key mapping.
 */
public final class ParryInput {
    private ParryInput() {}

    public static final KeyMapping KEY = new KeyMapping("key.lotr_revival.parry", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.lotr_revival");

    private static long lastSent = Long.MIN_VALUE / 2;

    static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem() || e.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) return;   // doors, chests, villagers first
        if (!bladeReady(mc.player)) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        send(mc, "use");
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (mc.player != null && bladeReady(mc.player)) send(mc, "key");
        }
    }

    private static boolean bladeReady(Player p) {
        if (Parry.bladeClass(p) == null) return false;
        if (p.getMainHandItem().getItem() instanceof ShieldItem || p.getOffhandItem().getItem() instanceof ShieldItem) return false;
        ItemStack off = p.getOffhandItem();
        return off.isEmpty() || off.getUseDuration(p) <= 0;
    }

    private static void send(Minecraft mc, String how) {
        if (mc.level == null || mc.player == null) return;
        long now = mc.level.getGameTime();
        if (now - lastSent < CombatConfig.guardTicks()) return;
        lastSent = now;
        PacketDistributor.sendToServer(ParryPayload.INSTANCE);
        mc.player.swing(InteractionHand.MAIN_HAND);
        LotrLog.dbg("combat", "client press: by={} item={} tick={}", how, Parry.bladeClass(mc.player), now);
    }
}
