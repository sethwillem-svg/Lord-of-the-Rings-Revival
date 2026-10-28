package io.github.sethwillemsvg.lotrrevival.core.event;

import io.github.sethwillemsvg.lotrrevival.core.alignment.PledgeBreakReason;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Pledge events on the NeoForge game bus (server side only). */
public abstract class PledgeEvent extends Event {
    private final ServerPlayer player;
    private final ResourceKey<Faction> faction;

    protected PledgeEvent(ServerPlayer player, ResourceKey<Faction> faction) {
        this.player = player;
        this.faction = faction;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceKey<Faction> getFaction() {
        return faction;
    }

    /** Before a pledge is made. Cancel to refuse it. */
    public static class Pledge extends PledgeEvent implements ICancellableEvent {
        public Pledge(ServerPlayer player, ResourceKey<Faction> faction) {
            super(player, faction);
        }
    }

    /** The player killed a member of the pledged faction or one of its allies and was warned. */
    public static class Warned extends PledgeEvent {
        private final LivingEntity victim;

        public Warned(ServerPlayer player, ResourceKey<Faction> faction, LivingEntity victim) {
            super(player, faction);
            this.victim = victim;
        }

        public LivingEntity getVictim() {
            return victim;
        }
    }

    /** After a pledge ended. Penalty is the alignment lost; cooldown is the pledge cooldown started, in ticks. */
    public static class Break extends PledgeEvent {
        private final PledgeBreakReason reason;
        private final float penalty;
        private final int cooldownTicks;

        public Break(
                ServerPlayer player,
                ResourceKey<Faction> faction,
                PledgeBreakReason reason,
                float penalty,
                int cooldownTicks) {
            super(player, faction);
            this.reason = reason;
            this.penalty = penalty;
            this.cooldownTicks = cooldownTicks;
        }

        public PledgeBreakReason getReason() {
            return reason;
        }

        public float getPenalty() {
            return penalty;
        }

        public int getCooldownTicks() {
            return cooldownTicks;
        }
    }
}
