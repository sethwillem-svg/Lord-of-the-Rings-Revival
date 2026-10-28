package io.github.sethwillemsvg.lotrrevival.npc.hire.command;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteraction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The {@code lotrrevival:unit_command} menu type, opening it from the right-click of the owner, and the name tag
 * rule (a name tag on a hired unit does nothing but the usual right-click).
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class UnitCommandMenus {
    public static final DeferredHolder<MenuType<?>, MenuType<UnitCommandMenu>> MENU =
            ModRegistries.MENU_TYPES.register("unit_command", () -> IMenuTypeExtension.create(UnitCommandMenu::new));

    private UnitCommandMenus() {}

    /** Opens the screen; swappable because GameTest players have no connection that can receive a menu. */
    @FunctionalInterface
    public interface Opener {
        boolean open(ServerPlayer player, NpcEntity unit);
    }

    private static Opener opener = UnitCommandMenus::open;

    /** Test seam: replaces the opener and returns the previous one so the test can restore it. */
    public static Opener setOpener(Opener replacement) {
        Opener previous = opener;
        opener = replacement;
        return previous;
    }

    /**
     * The right-click of a player on an NPC: opens the command screen when the unit is the player's own and the
     * player is not sneaking. Returns whether the interaction was taken (otherwise the caller talks or trades).
     */
    public static boolean openFor(NpcEntity unit, ServerPlayer player) {
        return !player.isShiftKeyDown() && HiredUnits.isOwnedBy(unit, player.getUUID()) && opener.open(player, unit);
    }

    /** Opens the command screen for the unit's owner. */
    public static boolean open(ServerPlayer player, NpcEntity unit) {
        return player.openMenu(
                        new SimpleMenuProvider(
                                (id, inventory, p) -> new UnitCommandMenu(id, inventory, unit), unit.getDisplayName()),
                        buf -> buf.writeVarInt(unit.getId()))
                .isPresent();
    }

    /**
     * Name tags do nothing on hired units: the click is taken before vanilla applies the tag and handled as an
     * ordinary right-click (the command screen for the owner, talking for anyone else).
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof NpcEntity unit
                && unit.isHired()
                && event.getItemStack().is(Items.NAME_TAG)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer player) {
                NpcInteraction.onRightClick(unit, player);
            }
        }
    }
}
