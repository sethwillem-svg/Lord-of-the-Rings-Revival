package io.github.sethwillemsvg.lotrrevival.npc.trade;

import io.github.sethwillemsvg.lotrrevival.core.coin.Coins;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcStanding;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The trade screen's menu: 9 sell slots, one exchange slot and the player inventory, with four tabs. The server holds
 * the trader; the client holds only the synced offer views. The offer lists are re-synced to every viewer from
 * {@link #broadcastChanges()} whenever they change.
 */
public class NpcTradeMenu extends AbstractContainerMenu {
    /** Screen tabs. Units is a placeholder until the hiring purchase flow fills it. */
    public enum Tab {
        BUY,
        SELL,
        UNITS,
        EXCHANGE
    }

    /** Menu button ids {@code BUTTON_TAB_BASE + tab ordinal} switch tab. */
    public static final int BUTTON_TAB_BASE = 100;
    public static final int SELL_SLOTS = 9;
    public static final int SLOT_EXCHANGE = SELL_SLOTS;
    public static final int PLAYER_START = SELL_SLOTS + 1;
    public static final int PLAYER_END = PLAYER_START + 36;

    // Layout shared with the screen (relative to the top-left of the background).
    public static final int SELL_X = 112;
    public static final int SELL_Y = 34;
    public static final int EXCHANGE_X = 80;
    public static final int EXCHANGE_Y = 62;
    public static final int INVENTORY_Y = 142;
    public static final int HOTBAR_Y = 200;

    /** Close distance (L): 12 blocks. */
    public static final double REACH_SQR = 12.0 * 12.0;

    @Nullable private final NpcEntity npc;
    private final Player owner;
    private final int npcId;
    private final boolean hasGoods;
    private final boolean hasUnits;
    private Tab tab;
    private final Container sellSlots = new SimpleContainer(SELL_SLOTS);
    private final Container exchangeSlot = new SimpleContainer(1);
    private List<OfferView> buys = List.of();
    private List<OfferView> sells = List.of();
    private boolean stockSent;

    /** Client constructor, called with the extra data written by {@link NpcTradeMenus#open}. */
    public NpcTradeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, null, buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), Tab.values()[buf.readByte()]);
    }

    /** Server constructor. */
    public NpcTradeMenu(int containerId, Inventory inventory, NpcEntity npc, Tab startTab) {
        this(containerId, inventory, npc, npc.getId(), hasGoods(npc), npc.getRole() == NpcRole.UNIT_TRADER, startTab);
    }

    private NpcTradeMenu(
            int containerId,
            Inventory inventory,
            @Nullable NpcEntity npc,
            int npcId,
            boolean hasGoods,
            boolean hasUnits,
            Tab startTab) {
        super(NpcTradeMenus.MENU.get(), containerId);
        this.npc = npc;
        this.owner = inventory.player;
        this.npcId = npcId;
        this.hasGoods = hasGoods;
        this.hasUnits = hasUnits;
        this.tab = startTab;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new TabSlot(sellSlots, row * 3 + col, SELL_X + 18 * col, SELL_Y + 18 * row, Tab.SELL));
            }
        }
        addSlot(new TabSlot(exchangeSlot, 0, EXCHANGE_X, EXCHANGE_Y, Tab.EXCHANGE));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    private static boolean hasGoods(NpcEntity npc) {
        TraderStock stock = TraderStocks.stock(npc);
        return !stock.playerBuys().isEmpty() || !stock.playerSells().isEmpty();
    }

    /** A slot that exists only on its tab. */
    private final class TabSlot extends Slot {
        private final Tab required;

        TabSlot(Container container, int index, int x, int y, Tab required) {
            super(container, index, x, y);
            this.required = required;
        }

        @Override
        public boolean isActive() {
            return tab == required;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (tab != required) {
                return false;
            }
            return required == Tab.SELL || Coins.unitValue(stack.getItem()) > 0;
        }

        @Override
        public boolean mayPickup(Player player) {
            return tab == required;
        }
    }

    // ------------------------------------------------------------------ state

    public Tab tab() {
        return tab;
    }

    /** Switches tab locally (the client screen also sends the matching menu button). */
    public void setTab(Tab tab) {
        if (hasTab(tab)) {
            this.tab = tab;
        }
    }

    public boolean hasTab(Tab tab) {
        return switch (tab) {
            case BUY, SELL -> hasGoods;
            case UNITS -> hasUnits;
            case EXCHANGE -> true;
        };
    }

    public int npcId() {
        return npcId;
    }

    @Nullable
    public NpcEntity npc() {
        return npc;
    }

    public List<OfferView> buys() {
        return buys;
    }

    public List<OfferView> sells() {
        return sells;
    }

    public Container sellContainer() {
        return sellSlots;
    }

    public Container exchangeContainer() {
        return exchangeSlot;
    }

    /** Client: takes the synced offer lists. */
    public void applyStock(List<OfferView> buys, List<OfferView> sells) {
        this.buys = List.copyOf(buys);
        this.sells = List.copyOf(sells);
    }

    /**
     * What the Sell button would pay for the given slot contents against these offers, lot by lot, capped by each
     * offer's {@code lotsLeft}. Matches what the server pays unless the stock changes meanwhile.
     */
    public static int previewSell(List<ItemStack> slots, List<OfferView> offers) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : slots) {
            left.add(stack.copy());
        }
        int total = 0;
        for (OfferView offer : offers) {
            int lots = 0;
            while (lots < offer.lotsLeft() && offer.count() > 0 && consume(left, offer, offer.count())) {
                lots++;
                total += offer.price();
            }
        }
        return total;
    }

    private static boolean consume(List<ItemStack> left, OfferView offer, int count) {
        int held = 0;
        for (ItemStack stack : left) {
            if (offer.matches(stack)) {
                held += stack.getCount();
            }
        }
        if (held < count) {
            return false;
        }
        int remaining = count;
        for (ItemStack stack : left) {
            if (remaining > 0 && offer.matches(stack)) {
                int n = Math.min(remaining, stack.getCount());
                stack.shrink(n);
                remaining -= n;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ server actions

    public boolean buy(ServerPlayer player, int offer) {
        return npc != null && TradeActions.buy(player, npc, offer);
    }

    public int sell(ServerPlayer player) {
        return npc == null ? 0 : TradeActions.sell(player, npc, sellSlots);
    }

    public boolean exchange(ServerPlayer player, boolean combine) {
        return TradeActions.exchange(player, exchangeSlot, combine);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int index = id - BUTTON_TAB_BASE;
        if (index >= 0 && index < Tab.values().length && hasTab(Tab.values()[index])) {
            tab = Tab.values()[index];
            return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (npc != null && owner instanceof ServerPlayer player) {
            syncStock(player);
        }
    }

    private void syncStock(ServerPlayer player) {
        TraderStock stock = TraderStocks.stock(npc);
        List<OfferView> newBuys = stock.playerBuys().stream().map(OfferView::of).toList();
        List<OfferView> newSells = stock.playerSells().stream().map(OfferView::of).toList();
        if (stockSent && newBuys.equals(buys) && newSells.equals(sells)) {
            return;
        }
        stockSent = true;
        buys = newBuys;
        sells = newSells;
        if (!player.isFakePlayer()) {
            PacketDistributor.sendToPlayer(player, new TradePayloads.Stock(containerId, newBuys, newSells));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = switch (tab) {
                case SELL -> moveItemStackTo(stack, 0, SELL_SLOTS, false);
                case EXCHANGE -> moveItemStackTo(stack, SLOT_EXCHANGE, SLOT_EXCHANGE + 1, false);
                default -> false;
            };
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** Leftovers in the sell and exchange slots go back to the player (dropped if the inventory is full). */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            returnAll(serverPlayer, sellSlots);
            returnAll(serverPlayer, exchangeSlot);
        }
    }

    private static void returnAll(ServerPlayer player, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                ItemReturn.give(player, stack);
            }
        }
    }

    /**
     * Valid while the trader lives, is in the player's level within 12 blocks, has no attack target and the player is
     * alive and not hostile to it. The client side (no trader) is always valid.
     */
    @Override
    public boolean stillValid(Player player) {
        if (npc == null) {
            return true;
        }
        return player.isAlive()
                && npc.isAlive()
                && !npc.isRemoved()
                && npc.level() == player.level()
                && player.distanceToSqr(npc) <= REACH_SQR
                && npc.getTarget() == null
                && !NpcStanding.isHostile(npc, player);
    }
}
