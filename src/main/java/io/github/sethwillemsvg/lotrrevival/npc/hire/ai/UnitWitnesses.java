package io.github.sethwillemsvg.lotrrevival.npc.hire.ai;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import net.minecraft.server.level.ServerPlayer;

/** Witness exclusions for hired units, used by the kill-witness code. */
public final class UnitWitnesses {
    private UnitWitnesses() {}

    /**
     * Whether a hired unit sits out the witnesses of a kill: it never joins against its own owner, and no hired unit
     * joins while the killer holds positive alignment with its faction. Unhired NPCs are never excluded.
     */
    public static boolean excluded(NpcEntity witness, ServerPlayer killer) {
        if (!witness.isHired()) {
            return false;
        }
        return excludes(
                HiredUnits.isOwnedBy(witness, killer.getUUID()),
                Alignment.get(killer, witness.faction()));
    }

    /** The pure rule behind {@link #excluded}. */
    public static boolean excludes(boolean ownedByKiller, float killerAlignment) {
        return ownedByKiller || killerAlignment > 0.0f;
    }
}
