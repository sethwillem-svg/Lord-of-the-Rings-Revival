package io.github.sethwillemsvg.lotrrevival.npc.hire.progress;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.Event;

/** Posted on the NeoForge bus when a hired unit dies. Not cancellable; the unit is still hired at this point. */
public class UnitDeathEvent extends Event {
    private final NpcEntity unit;
    private final UUID owner;
    private final DamageSource source;
    private final boolean ownerNotified;

    public UnitDeathEvent(NpcEntity unit, UUID owner, DamageSource source, boolean ownerNotified) {
        this.unit = unit;
        this.owner = owner;
        this.source = source;
        this.ownerNotified = ownerNotified;
    }

    public NpcEntity getUnit() {
        return unit;
    }

    public UUID getOwner() {
        return owner;
    }

    public DamageSource getSource() {
        return source;
    }

    /** Whether the owner was online, had death messages on, and got the chat message. */
    public boolean wasOwnerNotified() {
        return ownerNotified;
    }
}
