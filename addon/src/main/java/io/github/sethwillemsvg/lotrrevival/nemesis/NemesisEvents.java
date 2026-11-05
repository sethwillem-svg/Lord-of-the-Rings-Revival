package io.github.sethwillemsvg.lotrrevival.nemesis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

/** Game events on the NeoForge bus (spec Data and APIs). */
public final class NemesisEvents {
    private NemesisEvents() {}

    public static class Base extends Event {
        private final NemesisRecord record;

        protected Base(NemesisRecord record) {
            this.record = record;
        }

        public NemesisRecord getRecord() {
            return record;
        }
    }

    /** A new nemesis rose from a kill. */
    public static final class Risen extends Base {
        private final @Nullable Entity victim;

        public Risen(NemesisRecord record, @Nullable Entity victim) {
            super(record);
            this.victim = victim;
        }

        public @Nullable Entity getVictim() {
            return victim;
        }
    }

    public static final class RankChanged extends Base {
        private final Rank from, to;
        private final String reason;

        public RankChanged(NemesisRecord record, Rank from, Rank to, String reason) {
            super(record);
            this.from = from;
            this.to = to;
            this.reason = reason;
        }

        public Rank getFrom() {
            return from;
        }

        public Rank getTo() {
            return to;
        }

        public String getReason() {
            return reason;
        }
    }

    public enum Outcome { ESCAPED, CHEATED_DEATH, DEAD }

    public static final class Defeated extends Base {
        private final Outcome outcome;
        private final String cause;
        private final @Nullable Entity killer;

        public Defeated(NemesisRecord record, Outcome outcome, String cause, @Nullable Entity killer) {
            super(record);
            this.outcome = outcome;
            this.cause = cause;
            this.killer = killer;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public String getCause() {
            return cause;
        }

        public @Nullable Entity getKiller() {
            return killer;
        }
    }

    /** About to ambush a player; cancel to stop it (no cooldown starts). */
    public static final class Ambush extends Base implements ICancellableEvent {
        private final ServerPlayer target;

        public Ambush(NemesisRecord record, ServerPlayer target) {
            super(record);
            this.target = target;
        }

        public ServerPlayer getTarget() {
            return target;
        }
    }
}
