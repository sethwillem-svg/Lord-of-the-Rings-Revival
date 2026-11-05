package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.resources.datas.common.NpcRank;
import net.sevenstars.middleearth.resources.datas.factions.Faction;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcType;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcTypeLookup;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcUtil;
import net.sevenstars.middleearth.resources.datas.npc_types.data.MountData;
import net.sevenstars.middleearth.resources.datas.npc_types.data.WeightedGearData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Leader gear and mounts (spec Ranks: Gear, Mount). Everything is read from Middle-earth's NPC type data through its public
 * classes ({@code Faction.getAllNpcDatas}, {@code NpcTypeLookup}, {@code NpcType.getGear}/{@code getMountData},
 * {@code NpcUtil.equipAll}, {@code MountData.createEntity}); nothing of it is copied here.
 */
public final class LeaderGear {
    /** Entity tag on a nemesis mount and anything else it carries, so leftovers saved in chunks can be removed on load. */
    public static final String MOUNT_TAG = "lotr_revival.nemesis_mount";
    /** Game tests: the NPC type the last re-roll took its gear from, and its rank pool (null when none was found). */
    public static @Nullable ResourceLocation lastGearType;
    public static @Nullable NpcRank lastGearRank;

    private LeaderGear() {}

    // ---------------------------------------------------------------- gear

    /**
     * From {@code gear_reroll_rank} (Commander) up, once per record: the body's gear is re-rolled from a type of the faction's
     * LEADER rank pool of the record's race (else VETERAN), then saved in the record and kept for every later manifestation.
     * Returns true when the gear changed.
     */
    public static boolean rerollIfDue(ServerLevel level, NpcEntity npc, NemesisRecord r, Faction f) {
        Rank min = NemesisConfig.rank(NemesisConfig.GEAR_REROLL_RANK);
        if (r.gearRerolled || r.rank.ordinal() < min.ordinal()) {
            LotrLog.dbg("nemesis", "gear: id={} rank={} rerolled_before={} -> keeps its gear (re-roll from {})", r.shortId(), r.rank,
                    r.gearRerolled, min);
            return false;
        }
        ResourceLocation race = raceOf(level, r);
        for (NpcRank pool : new NpcRank[]{NpcRank.LEADER, NpcRank.VETERAN}) {
            List<NpcType> types = typesOfRace(level, f, pool, race, r.race);
            List<ResourceLocation> ids = new ArrayList<>();
            for (NpcType t : types) ids.add(t.getId());
            if (types.isEmpty()) {
                LotrLog.dbg("nemesis", "gear: id={} faction={} has no {} type of race {} ({} in pool)", r.shortId(), r.faction, pool,
                        race == null ? r.race : race, MeRanks.pool(f, pool).size());
                continue;
            }
            NpcType pick = NemesisRandom.pick(types);
            WeightedGearData gear = pick.getGear();
            if (gear == null) {
                LotrLog.dbg("nemesis", "gear: id={} type {} ({}) has no gear data", r.shortId(), pick.getId(), pool);
                continue;
            }
            NpcUtil.equipAll(npc, gear);
            npc.updateAttackType();
            snapshot(npc, r);
            r.gearRerolled = true;
            lastGearType = pick.getId();
            lastGearRank = pool;
            LotrLog.dbg("nemesis", "gear re-roll: id={} name=\"{}\" rank={} from {} type {} (race {}, candidates {}) -> {}", r.shortId(),
                    r.displayName(), r.rank, pool, pick.getId(), race == null ? r.race : race, ids, describe(r));
            return true;
        }
        r.gearRerolled = true;   // nothing to roll from: do not try again on every manifestation
        lastGearType = null;
        lastGearRank = null;
        LotrLog.warn("nemesis", "gear re-roll for {} ({} of {}): no LEADER or VETERAN type of race {} with gear; keeps its own gear",
                r.shortId(), r.rank, r.faction, race == null ? r.race : race);
        return false;
    }

    /** Saves what the body wears now as the record's gear. */
    public static void snapshot(NpcEntity npc, NemesisRecord r) {
        r.gear.clear();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.BODY) continue;
            ItemStack s = npc.getItemBySlot(slot);
            if (!s.isEmpty()) r.gear.put(slot, s.copy());
        }
    }

    public static String describe(NemesisRecord r) {
        StringJoiner j = new StringJoiner(" ", "[", "]");
        r.gear.forEach((slot, s) -> j.add(slot.getName() + "=" + BuiltInRegistries.ITEM.getKey(s.getItem())));
        return j.toString();
    }

    private static @Nullable ResourceLocation raceOf(ServerLevel level, NemesisRecord r) {
        NpcType own = type(level, r.npcType);
        return own == null ? null : own.getRace();
    }

    /** Types of one rank pool of the faction whose race is {@code race} (or whose race path is {@code racePath} when unknown). */
    static List<NpcType> typesOfRace(ServerLevel level, Faction f, NpcRank pool, @Nullable ResourceLocation race, String racePath) {
        List<NpcType> out = new ArrayList<>();
        for (ResourceLocation id : MeRanks.pool(f, pool)) {
            NpcType t = type(level, id);
            if (t == null || t.getRace() == null) continue;
            boolean same = race != null ? race.equals(t.getRace()) : t.getRace().getPath().equals(racePath);
            if (same) out.add(t);
        }
        return out;
    }

    static @Nullable NpcType type(ServerLevel level, @Nullable ResourceLocation id) {
        if (id == null) return null;
        try {
            return NpcTypeLookup.getNpcType(level, id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- mounts

    /**
     * From {@code mount_rank} (Warlord) up the body rides: its own type's mount when the type has mount data (Middle-earth already
     * seats it on that when it builds the NPC), else the mount of a LEADER type of its faction that has one, else none. Below that
     * rank, a mount Middle-earth gave the type is removed. Call before the body is added to the level. Returns the mount or null.
     */
    public static @Nullable Entity mountIfDue(ServerLevel level, NpcEntity npc, NemesisRecord r, Faction f) {
        Rank min = NemesisConfig.rank(NemesisConfig.MOUNT_RANK);
        Entity vehicle = npc.getVehicle();
        if (r.rank.ordinal() < min.ordinal()) {
            if (vehicle != null) {
                LotrLog.dbg("nemesis", "mount: id={} rank={} below {}: type mount {} removed", r.shortId(), r.rank, min,
                        vehicle.getType().toShortString());
                npc.stopRiding();
                discardMount(vehicle, npc);
            }
            return null;
        }
        if (vehicle != null) {
            tag(vehicle, npc);
            LotrLog.dbg("nemesis", "mount: id={} rank={} rides its type's mount {} ({})", r.shortId(), r.rank, vehicle.getType().toShortString(),
                    r.npcType);
            return vehicle;
        }
        String source;
        MountData data = null;
        NpcType own = type(level, r.npcType);
        if (own != null && own.hasMount()) {
            data = own.getMountData();
            source = "own type " + r.npcType;
        } else {
            List<NpcType> leaders = new ArrayList<>();
            for (ResourceLocation id : MeRanks.pool(f, NpcRank.LEADER)) {
                NpcType t = type(level, id);
                if (t != null && t.hasMount()) leaders.add(t);
            }
            if (leaders.isEmpty()) {
                LotrLog.dbg("nemesis", "mount: id={} rank={}: neither type {} nor a LEADER type of {} has mount data -> on foot", r.shortId(),
                        r.rank, r.npcType, r.faction);
                return null;
            }
            NpcType pick = NemesisRandom.pick(leaders);
            data = pick.getMountData();
            source = "LEADER type " + pick.getId();
        }
        try {
            data.createEntity(level, npc);
        } catch (RuntimeException e) {
            LotrLog.error("nemesis", "mount for " + r.shortId() + " from " + source + " failed", e);
            return null;
        }
        Entity mount = npc.getVehicle();
        if (mount == null) {
            LotrLog.warn("nemesis", "mount for {} from {}: Middle-earth made no mount; on foot", r.shortId(), source);
            return null;
        }
        tag(mount, npc);
        LotrLog.dbg("nemesis", "mount: id={} rank={} rides {} from {} at {}", r.shortId(), r.rank, mount.getType().toShortString(), source,
                mount.blockPosition().toShortString());
        return mount;
    }

    /** The mount and its other riders never rise, and are found and removed if they load without their nemesis. */
    private static void tag(Entity mount, NpcEntity rider) {
        mount.addTag(MOUNT_TAG);
        mount.addTag(MeRanks.BODYGUARD_TAG);
        for (Entity p : mount.getPassengers()) {
            if (p == rider || p instanceof Player) continue;
            p.addTag(MOUNT_TAG);
            p.addTag(MeRanks.BODYGUARD_TAG);
        }
    }

    /** Removes a mount and every rider on it except the nemesis and players. */
    public static void discardMount(@Nullable Entity mount, @Nullable Entity rider) {
        if (mount == null || mount.isRemoved()) return;
        for (Entity p : new ArrayList<>(mount.getPassengers())) {
            if (p == rider || p instanceof Player) continue;
            p.discard();
        }
        mount.ejectPassengers();
        mount.discard();
    }
}
