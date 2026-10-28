package io.github.sethwillemsvg.lotrrevival.npc.trade;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteractEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The {@code lotrrevival:npc_trade} menu type and the {@link NpcInteractEvent} handler that opens it. */
public final class NpcTradeMenus {
    public static final DeferredHolder<MenuType<?>, MenuType<NpcTradeMenu>> MENU =
            ModRegistries.MENU_TYPES.register("npc_trade", () -> IMenuTypeExtension.create(NpcTradeMenu::new));

    private NpcTradeMenus() {}

    /** Called once from {@code NpcTrade.init}. */
    public static void init() {
        NeoForge.EVENT_BUS.addListener(NpcTradeMenus::onInteract);
        PayloadLimiter.init();
    }

    /** Opens the screen; swappable because GameTest players have no connection that can receive a menu. */
    @FunctionalInterface
    public interface Opener {
        boolean open(ServerPlayer player, NpcEntity npc, NpcTradeMenu.Tab tab);
    }

    private static Opener opener = NpcTradeMenus::open;

    /** Test seam: replaces the opener and returns the previous one so the test can restore it. */
    public static Opener setOpener(Opener replacement) {
        Opener previous = opener;
        opener = replacement;
        return previous;
    }

    /** TRADE opens the screen on Buy and takes over the interaction. HIRE belongs to the hiring purchase flow. */
    private static void onInteract(NpcInteractEvent event) {
        if (event.getKind() == NpcInteractEvent.Kind.TRADE
                && opener.open(event.getPlayer(), event.getNpc(), NpcTradeMenu.Tab.BUY)) {
            event.setCanceled(true);
        }
    }

    /**
     * Opens the trade screen on {@code tab}. Returns false (nothing opened) if the trader has nothing for that tab:
     * no goods for Buy/Sell, no unit list for Units.
     */
    public static boolean open(ServerPlayer player, NpcEntity npc, NpcTradeMenu.Tab tab) {
        TraderStock stock = TraderStocks.stock(npc);
        boolean hasGoods = !stock.playerBuys().isEmpty() || !stock.playerSells().isEmpty();
        boolean hasUnits = npc.getRole() == NpcRole.UNIT_TRADER;
        boolean available = switch (tab) {
            case BUY, SELL -> hasGoods;
            case UNITS -> hasUnits;
            case EXCHANGE -> true;
        };
        if (!available) {
            return false;
        }
        return player.openMenu(
                        new SimpleMenuProvider(
                                (id, inventory, p) -> new NpcTradeMenu(id, inventory, npc, tab), npc.getDisplayName()),
                        buf -> {
                            buf.writeVarInt(npc.getId());
                            buf.writeBoolean(hasGoods);
                            buf.writeBoolean(hasUnits);
                            buf.writeByte(tab.ordinal());
                        })
                .isPresent();
    }
}
