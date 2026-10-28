package io.github.sethwillemsvg.lotrrevival.npc.friendlyfire;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Friendly-fire protection: a player whose alignment with an NPC's faction is above 0 deals no damage to it (melee,
 * sweep or their own projectile) unless the NPC already targets them or they switched the toggle on. Hits never change
 * alignment; this class only cancels damage.
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class FriendlyFire {
    public static final String HINT_KEY = "npc.lotrrevival.friendlyfire.hint";

    private FriendlyFire() {}

    /** Whether a hit from the player on the NPC is swallowed right now. */
    public static boolean isProtected(NpcEntity npc, ServerPlayer player) {
        return Alignment.get(player, npc.faction()) > 0.0f
                && npc.getTarget() != player
                && !NpcPlayerPrefs.get(player).allowFriendlyFire();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof NpcEntity npc) || npc.level().isClientSide) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player) || !isProtected(npc, player)) {
            return;
        }
        event.setCanceled(true);
        NpcPlayerPrefs prefs = NpcPlayerPrefs.get(player);
        if (!prefs.friendlyFireHintShown()) {
            NpcPlayerPrefs.set(player, prefs.withHintShown());
            player.sendSystemMessage(Component.translatable(HINT_KEY));
        }
    }
}
