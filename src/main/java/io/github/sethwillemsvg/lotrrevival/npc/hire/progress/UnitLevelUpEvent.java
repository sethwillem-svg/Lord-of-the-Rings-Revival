package io.github.sethwillemsvg.lotrrevival.npc.hire.progress;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.UUID;
import net.neoforged.bus.api.Event;

/** Posted on the NeoForge bus after a hired unit gained a level. Not cancellable. */
public class UnitLevelUpEvent extends Event {
    private final NpcEntity unit;
    private final UUID owner;
    private final int newLevel;

    public UnitLevelUpEvent(NpcEntity unit, UUID owner, int newLevel) {
        this.unit = unit;
        this.owner = owner;
        this.newLevel = newLevel;
    }

    public NpcEntity getUnit() {
        return unit;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getNewLevel() {
        return newLevel;
    }
}
