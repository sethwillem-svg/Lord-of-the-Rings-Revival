package io.github.sethwillemsvg.lotrrevival.core.combat.throwing;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * The pickup rule of landed thrown weapons: only the thrower may collect for 20 seconds, then anyone. The marker lives
 * in the item entity's persistent data, so it survives saving and unloaded chunks (the clock is the level's game time).
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class ThrownPickup {
    /** Ticks only the thrower can collect a landed weapon. */
    public static final int OWNER_ONLY_TICKS = 400;

    private static final String OWNER_KEY = LOTRRevival.MOD_ID + ":thrown_owner";
    private static final String UNTIL_KEY = LOTRRevival.MOD_ID + ":thrown_owner_until";

    private ThrownPickup() {}

    static void restrictToThrower(ItemEntity drop, @Nullable Entity thrower, long gameTime) {
        if (thrower == null) {
            return;
        }
        CompoundTag data = drop.getPersistentData();
        data.putUUID(OWNER_KEY, thrower.getUUID());
        data.putLong(UNTIL_KEY, gameTime + OWNER_ONLY_TICKS);
    }

    /** Whether {@code player} may take the item entity right now. */
    public static boolean mayCollect(ItemEntity drop, Entity player) {
        CompoundTag data = drop.getPersistentData();
        if (!data.hasUUID(OWNER_KEY)) {
            return true;
        }
        return data.getUUID(OWNER_KEY).equals(player.getUUID())
                || player.level().getGameTime() >= data.getLong(UNTIL_KEY);
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!mayCollect(event.getItemEntity(), event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }
}
