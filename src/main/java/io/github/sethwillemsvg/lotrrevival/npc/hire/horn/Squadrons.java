package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;

/** Squadron names and finding the units a horn or sword commands. */
public final class Squadrons {
    private Squadrons() {}

    /** Trims and caps a squadron name at {@value HiredUnit#SQUADRON_MAX} characters. */
    public static String clean(String squadron) {
        return HiredUnit.cleanSquadron(squadron);
    }

    /** Case-insensitive match after trimming; empty matches empty. */
    public static boolean matches(String a, String b) {
        return clean(a).toLowerCase(Locale.ROOT).equals(clean(b).toLowerCase(Locale.ROOT));
    }

    /** Whether the unit is the player's, reached by horns and the sword, and in the given squadron. */
    public static boolean commands(ServerPlayer player, NpcEntity unit, String squadron) {
        HiredUnit state = HiredUnits.get(unit);
        return state.isHired()
                && state.owner().filter(player.getUUID()::equals).isPresent()
                && UnitCommands.reachedByHorns(unit)
                && matches(state.squadron(), squadron);
    }

    /** Every loaded unit of the player in this level that the squadron's horn or sword reaches. */
    public static List<NpcEntity> unitsIn(ServerLevel level, ServerPlayer player, String squadron) {
        List<NpcEntity> found = new ArrayList<>();
        for (NpcEntity unit : level.getEntities(EntityTypeTest.forClass(NpcEntity.class), e -> e.isAlive())) {
            if (commands(player, unit, squadron)) {
                found.add(unit);
            }
        }
        return found;
    }
}
