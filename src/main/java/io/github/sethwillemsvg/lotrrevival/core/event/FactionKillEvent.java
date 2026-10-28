package io.github.sethwillemsvg.lotrrevival.core.event;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired when a player (or a unit credited to a player) kills a faction entity. Cancel to apply no alignment; mutate
 * the base value to change it. For hired-unit kills {@link #isCreditedViaHire()} is true: the event fires but no
 * alignment is applied whatever the listeners do.
 */
public class FactionKillEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final LivingEntity victim;
    private final ResourceKey<Faction> victimFaction;
    private float baseValue;
    private final boolean civilian;
    private final boolean creditedViaHire;

    public FactionKillEvent(
            ServerPlayer player,
            LivingEntity victim,
            ResourceKey<Faction> victimFaction,
            float baseValue,
            boolean civilian,
            boolean creditedViaHire) {
        this.player = player;
        this.victim = victim;
        this.victimFaction = victimFaction;
        this.baseValue = baseValue;
        this.civilian = civilian;
        this.creditedViaHire = creditedViaHire;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public LivingEntity getVictim() {
        return victim;
    }

    public ResourceKey<Faction> getVictimFaction() {
        return victimFaction;
    }

    public float getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(float baseValue) {
        this.baseValue = baseValue;
    }

    public boolean isCivilian() {
        return civilian;
    }

    public boolean isCreditedViaHire() {
        return creditedViaHire;
    }
}
