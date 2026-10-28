package io.github.sethwillemsvg.lotrrevival.core.event;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.Event;

/**
 * Fired (game bus, server side) each time a player takes a result from a faction crafting table. Not cancellable.
 * The result is a copy of the taken stack.
 */
public class FactionTableCraftEvent extends Event {
    private final ServerPlayer player;
    private final Block table;
    private final ResourceKey<Faction> faction;
    private final ItemStack result;

    public FactionTableCraftEvent(ServerPlayer player, Block table, ResourceKey<Faction> faction, ItemStack result) {
        this.player = player;
        this.table = table;
        this.faction = faction;
        this.result = result;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Block getTable() {
        return table;
    }

    public ResourceKey<Faction> getFaction() {
        return faction;
    }

    public ItemStack getResult() {
        return result;
    }
}
