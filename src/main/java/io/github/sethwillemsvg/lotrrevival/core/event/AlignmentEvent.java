package io.github.sethwillemsvg.lotrrevival.core.event;

import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Alignment events on the NeoForge game bus (server side only). */
public abstract class AlignmentEvent extends Event {
    private final ServerPlayer player;
    private final ResourceKey<Faction> faction;

    protected AlignmentEvent(ServerPlayer player, ResourceKey<Faction> faction) {
        this.player = player;
        this.faction = faction;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceKey<Faction> getFaction() {
        return faction;
    }

    /** Before a change is applied (after gain modifiers). Cancel to drop it or mutate the delta. */
    public static class Change extends AlignmentEvent implements ICancellableEvent {
        private float delta;
        private final AlignmentSource source;

        public Change(ServerPlayer player, ResourceKey<Faction> faction, float delta, AlignmentSource source) {
            super(player, faction);
            this.delta = delta;
            this.source = source;
        }

        public float getDelta() {
            return delta;
        }

        public void setDelta(float delta) {
            this.delta = delta;
        }

        public AlignmentSource getSource() {
            return source;
        }
    }

    /** After a change was applied. {@code getNewValue() - getOldValue()} is the actual (clamped, rounded) delta. */
    public static class Changed extends AlignmentEvent {
        private final float oldValue;
        private final float newValue;
        private final AlignmentSource source;

        public Changed(
                ServerPlayer player,
                ResourceKey<Faction> faction,
                float oldValue,
                float newValue,
                AlignmentSource source) {
            super(player, faction);
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.source = source;
        }

        public float getOldValue() {
            return oldValue;
        }

        public float getNewValue() {
            return newValue;
        }

        public AlignmentSource getSource() {
            return source;
        }
    }

    /** After a change moved the player to a different rank of the faction (including neutral and hostile). */
    public static class RankChanged extends AlignmentEvent {
        private final FactionRank oldRank;
        private final FactionRank newRank;

        public RankChanged(
                ServerPlayer player, ResourceKey<Faction> faction, FactionRank oldRank, FactionRank newRank) {
            super(player, faction);
            this.oldRank = oldRank;
            this.newRank = newRank;
        }

        public FactionRank getOldRank() {
            return oldRank;
        }

        public FactionRank getNewRank() {
            return newRank;
        }
    }
}
