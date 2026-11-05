package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.combat.NpcRaces;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcType;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcTypeLookup;

import java.util.UUID;

/** Builds a new record (name, one strength, one weakness) for a rise, a command or a game test. */
public final class NemesisFactory {
    private NemesisFactory() {}

    public static NemesisRecord create(ServerLevel level, ResourceLocation faction, ResourceLocation type, String race) {
        NemesisSavedData data = NemesisSavedData.get(level.getServer());
        NemesisRecord r = new NemesisRecord(UUID.randomUUID());
        r.faction = faction;
        r.npcType = type;
        r.race = race;
        r.createdTick = level.getGameTime();
        r.lastHolderOnlineMs = System.currentTimeMillis();
        r.lastDimension = level.dimension().location();
        Sides.Side side = Sides.sideOfFaction(level, faction);
        var s = NemesisTraits.rollStrength(r, side);
        if (s != null) r.strengths.add(s);
        r.weakness = NemesisTraits.rollWeakness(r, side);
        String[] name = NemesisNames.uniqueName(data, faction, race, side, r.strengths);
        r.firstName = name[0];
        r.title = name[1];
        data.put(r);
        LotrLog.dbg("nemesis", "record created: id={} name=\"{}\" faction={} type={} race={} side={} strengths={} weakness={}", r.shortId(),
                r.displayName(), faction, type, race, side, r.strengths, r.weakness);
        return r;
    }

    /** Race path of an NPC type id (Middle-earth race), else a guess from the id. */
    public static String raceOfType(ServerLevel level, ResourceLocation type) {
        try {
            NpcType t = NpcTypeLookup.getNpcType(level, type);
            if (t != null && t.getRace() != null) return t.getRace().getPath();
        } catch (RuntimeException ignored) {
        }
        String r = NpcRaces.fromKey(type.getPath());
        return r.equals("man") ? "human" : r;
    }
}
