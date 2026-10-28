package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** What a blown command horn does to the units of its squadron. */
public final class HornCommands {
    private HornCommands() {}

    /**
     * Applies the mode to every loaded matching unit of the player: halt and ready in every level, summon only where
     * the player stands (the unit must share the owner's level).
     *
     * @return the number of units that obeyed
     */
    public static int apply(ServerPlayer player, HornMode mode, String squadron) {
        int obeyed = 0;
        if (mode == HornMode.BLANK || player.getServer() == null) {
            return 0;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            if (mode == HornMode.SUMMON && level != player.level()) {
                continue;
            }
            for (NpcEntity unit : Squadrons.unitsIn(level, player, squadron)) {
                boolean done =
                        switch (mode) {
                            case HALT -> UnitCommands.halt(unit);
                            case READY -> UnitCommands.ready(unit);
                            case SUMMON -> UnitCommands.summon(unit);
                            default -> false;
                        };
                if (done) {
                    obeyed++;
                }
            }
        }
        return obeyed;
    }
}
