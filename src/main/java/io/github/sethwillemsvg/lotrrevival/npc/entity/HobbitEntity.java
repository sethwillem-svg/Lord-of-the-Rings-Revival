package io.github.sethwillemsvg.lotrrevival.npc.entity;

import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The Shire's civilian hobbit ({@code lotrrevival:hobbit}). */
public final class HobbitEntity extends NpcEntity {
    public HobbitEntity(EntityType<? extends HobbitEntity> type, Level level) {
        super(type, level, NpcRace.HOBBIT, Factions.HOBBIT, NpcClass.CIVILIAN);
    }
}
