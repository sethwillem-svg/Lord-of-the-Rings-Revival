package io.github.sethwillemsvg.lotrrevival.npc.hire;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.UUID;
import net.neoforged.bus.api.Event;

/** Posted on the NeoForge bus after a unit stops being hired. Not cancellable; the unit is already unowned. */
public class UnitReleasedEvent extends Event {
    /** Why the unit left. */
    public enum Reason {
        DISMISSED,
        DESERTED
    }

    private final NpcEntity unit;
    private final UUID owner;
    private final Reason reason;
    private final boolean ownerNotified;

    public UnitReleasedEvent(NpcEntity unit, UUID owner, Reason reason, boolean ownerNotified) {
        this.unit = unit;
        this.owner = owner;
        this.reason = reason;
        this.ownerNotified = ownerNotified;
    }

    public NpcEntity getUnit() {
        return unit;
    }

    public UUID getOwner() {
        return owner;
    }

    public Reason getReason() {
        return reason;
    }

    /** Whether the owner was online and got the chat message. */
    public boolean wasOwnerNotified() {
        return ownerNotified;
    }
}
