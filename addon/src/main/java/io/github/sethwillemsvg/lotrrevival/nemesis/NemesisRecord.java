package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One nemesis, alive in saved data whether or not a body is loaded (spec Bodies and persistence). Plain mutable class saved to
 * NBT by {@link #save}/{@link #load}; all changes go through {@link NemesisSavedData#setDirty()} by the caller.
 */
public final class NemesisRecord {
    public enum Status { AT_LARGE, MANIFEST, DEAD }

    /** A grudge on one player. */
    public static final class Grudge {
        public int score;
        public boolean dormant;
        /** Game time the grudge last decayed while dormant. */
        public long lastDecay;

        public Grudge(int score, boolean dormant, long lastDecay) {
            this.score = score;
            this.dormant = dormant;
            this.lastDecay = lastDecay;
        }
    }

    public final UUID id;
    public ResourceLocation faction;
    public ResourceLocation npcType;
    public String race = "any";
    public String firstName = "Nameless";
    public String title = "";
    /** Replaces the title after cheat-death; null when unscarred. */
    public @Nullable String scarTitle;
    public int renown;
    public Rank rank = Rank.MARKED;
    /** Has the renown for a full rank above but no seat. */
    public boolean challenger;
    public final List<ResourceLocation> strengths = new ArrayList<>();
    public @Nullable ResourceLocation weakness;
    public final List<DamageKind> scars = new ArrayList<>();
    /** Players who have fought it (strengths revealed). */
    public final Set<UUID> knowsStrengths = new HashSet<>();
    /** Players who hit its weak spot (weakness revealed). */
    public final Set<UUID> knowsWeakness = new HashSet<>();
    public int cheatDeaths;
    public final Map<UUID, Grudge> grudges = new LinkedHashMap<>();
    public final Map<EquipmentSlot, ItemStack> gear = new EnumMap<>(EquipmentSlot.class);
    public boolean gearRerolled;
    public Status status = Status.AT_LARGE;
    public int manifestation;
    public @Nullable UUID body;
    public @Nullable ResourceLocation lastDimension;
    public BlockPos lastPos = BlockPos.ZERO;
    public long lastSeenTick;
    public long createdTick;
    /** Real time (ms) a non-dormant grudge holder was last online (fading). */
    public long lastHolderOnlineMs;
    /** Game time it became DEAD (purge after the keep time). */
    public long deadSinceTick;
    /** Extra bodyguards earned by an undisturbed muster, used at the next manifestation. */
    public int extraBodyguards;

    // transient, per manifestation
    public final Set<UUID> damagedBy = new HashSet<>();
    public boolean escapeTried;
    public boolean warHornUsed;
    public long noPlayerSinceTick = -1;
    /** Event id when the current body belongs to a world event (bodies in events do not retreat on distance). */
    public @Nullable UUID eventId;
    /** The player this manifestation ambushes, if any. */
    public @Nullable UUID ambushTarget;

    public NemesisRecord(UUID id) {
        this.id = id;
    }

    public String shortId() {
        return LotrLog.id(id);
    }

    public String displayName() {
        String t = scarTitle != null ? scarTitle : title;
        return t == null || t.isEmpty() ? firstName : firstName + " " + t;
    }

    public boolean alive() {
        return status != Status.DEAD;
    }

    public int grudge(UUID player) {
        Grudge g = grudges.get(player);
        return g == null ? 0 : g.score;
    }

    public int activeGrudge(UUID player) {
        Grudge g = grudges.get(player);
        return g == null || g.dormant ? 0 : g.score;
    }

    /** Adds to a grudge (capped), keeping only the top holders. Returns the new score. */
    public int addGrudge(UUID player, int amount, long now) {
        if (amount == 0) return grudge(player);
        int cap = NemesisConfig.i(NemesisConfig.GRUDGE_CAP);
        Grudge g = grudges.computeIfAbsent(player, k -> new Grudge(0, false, now));
        int before = g.score;
        g.score = Math.max(0, Math.min(cap, g.score + amount));
        int keep = NemesisConfig.i(NemesisConfig.GRUDGE_HOLDERS);
        if (grudges.size() > keep) {
            UUID lowest = grudges.entrySet().stream().filter(e -> !e.getKey().equals(player))
                    .min(Comparator.comparingInt(e -> e.getValue().score)).map(Map.Entry::getKey).orElse(null);
            if (lowest != null) {
                grudges.remove(lowest);
                LotrLog.dbg("nemesis", "grudge dropped (top {} only): id={} player={}", keep, shortId(), LotrLog.id(lowest));
            }
        }
        LotrLog.dbg("nemesis", "grudge: id={} player={} {} -> {} (+{})", shortId(), LotrLog.id(player), before, g.score, amount);
        return g.score;
    }

    public Optional<UUID> topGrudge() {
        return grudges.entrySet().stream().filter(e -> !e.getValue().dormant)
                .max(Comparator.comparingInt(e -> e.getValue().score)).map(Map.Entry::getKey);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("faction", faction.toString());
        t.putString("type", npcType.toString());
        t.putString("race", race);
        t.putString("first", firstName);
        t.putString("title", title);
        if (scarTitle != null) t.putString("scar_title", scarTitle);
        t.putInt("renown", renown);
        t.putString("rank", rank.key());
        t.putBoolean("challenger", challenger);
        ListTag st = new ListTag();
        for (ResourceLocation s : strengths) st.add(StringTag.valueOf(s.toString()));
        t.put("strengths", st);
        if (weakness != null) t.putString("weakness", weakness.toString());
        ListTag sc = new ListTag();
        for (DamageKind k : scars) sc.add(StringTag.valueOf(k.key()));
        t.put("scars", sc);
        t.put("knows_strengths", uuids(knowsStrengths));
        t.put("knows_weakness", uuids(knowsWeakness));
        t.putInt("cheat_deaths", cheatDeaths);
        ListTag gl = new ListTag();
        grudges.forEach((p, g) -> {
            CompoundTag gt = new CompoundTag();
            gt.putUUID("player", p);
            gt.putInt("score", g.score);
            gt.putBoolean("dormant", g.dormant);
            gt.putLong("last_decay", g.lastDecay);
            gl.add(gt);
        });
        t.put("grudges", gl);
        CompoundTag gearTag = new CompoundTag();
        gear.forEach((slot, stack) -> {
            if (!stack.isEmpty()) gearTag.put(slot.getName(), stack.saveOptional(provider));
        });
        t.put("gear", gearTag);
        t.putBoolean("gear_rerolled", gearRerolled);
        t.putString("status", status.name());
        t.putInt("manifestation", manifestation);
        if (body != null) t.putUUID("body", body);
        if (lastDimension != null) t.putString("last_dim", lastDimension.toString());
        t.put("last_pos", NbtUtils.writeBlockPos(lastPos));
        t.putLong("last_seen", lastSeenTick);
        t.putLong("created", createdTick);
        t.putLong("holder_online_ms", lastHolderOnlineMs);
        t.putLong("dead_since", deadSinceTick);
        t.putInt("extra_guards", extraBodyguards);
        return t;
    }

    /** Loads one record; unknown trait ids are dropped through {@code traitKnown} with one WARN each. */
    public static NemesisRecord load(CompoundTag t, HolderLookup.Provider provider, java.util.function.Predicate<ResourceLocation> traitKnown) {
        NemesisRecord r = new NemesisRecord(t.getUUID("id"));
        r.faction = ResourceLocation.parse(t.getString("faction"));
        r.npcType = ResourceLocation.parse(t.getString("type"));
        r.race = t.getString("race");
        r.firstName = t.getString("first");
        r.title = t.getString("title");
        r.scarTitle = t.contains("scar_title") ? t.getString("scar_title") : null;
        r.renown = t.getInt("renown");
        r.rank = Rank.byName(t.getString("rank"));
        r.challenger = t.getBoolean("challenger");
        for (Tag s : t.getList("strengths", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(s.getAsString());
            if (id != null && traitKnown.test(id)) r.strengths.add(id);
            else LotrLog.warn("nemesis", "unknown trait id {} dropped from record {}", s.getAsString(), r.shortId());
        }
        if (t.contains("weakness")) {
            ResourceLocation w = ResourceLocation.tryParse(t.getString("weakness"));
            if (w != null && traitKnown.test(w)) r.weakness = w;
            else LotrLog.warn("nemesis", "unknown trait id {} dropped from record {}", t.getString("weakness"), r.shortId());
        }
        for (Tag s : t.getList("scars", Tag.TAG_STRING)) r.scars.add(DamageKind.byKey(s.getAsString()));
        readUuids(t.getList("knows_strengths", Tag.TAG_INT_ARRAY), r.knowsStrengths);
        readUuids(t.getList("knows_weakness", Tag.TAG_INT_ARRAY), r.knowsWeakness);
        r.cheatDeaths = t.getInt("cheat_deaths");
        for (Tag g : t.getList("grudges", Tag.TAG_COMPOUND)) {
            CompoundTag gt = (CompoundTag) g;
            r.grudges.put(gt.getUUID("player"), new Grudge(gt.getInt("score"), gt.getBoolean("dormant"), gt.getLong("last_decay")));
        }
        CompoundTag gearTag = t.getCompound("gear");
        for (String key : gearTag.getAllKeys()) {
            try {
                r.gear.put(EquipmentSlot.byName(key), ItemStack.parseOptional(provider, gearTag.getCompound(key)));
            } catch (IllegalArgumentException e) {
                LotrLog.warn("nemesis", "record {}: bad gear slot {} skipped", r.shortId(), key);
            }
        }
        r.gearRerolled = t.getBoolean("gear_rerolled");
        try {
            r.status = Status.valueOf(t.getString("status"));
        } catch (IllegalArgumentException e) {
            r.status = Status.AT_LARGE;
        }
        r.manifestation = t.getInt("manifestation");
        r.body = t.hasUUID("body") ? t.getUUID("body") : null;
        r.lastDimension = t.contains("last_dim") ? ResourceLocation.tryParse(t.getString("last_dim")) : null;
        r.lastPos = NbtUtils.readBlockPos(t, "last_pos").orElse(BlockPos.ZERO);
        r.lastSeenTick = t.getLong("last_seen");
        r.createdTick = t.getLong("created");
        r.lastHolderOnlineMs = t.getLong("holder_online_ms");
        r.deadSinceTick = t.getLong("dead_since");
        r.extraBodyguards = t.getInt("extra_guards");
        return r;
    }

    private static ListTag uuids(Set<UUID> set) {
        ListTag l = new ListTag();
        for (UUID u : set) l.add(NbtUtils.createUUID(u));
        return l;
    }

    private static void readUuids(ListTag list, Set<UUID> into) {
        for (Tag t : list) into.add(NbtUtils.loadUUID(t));
    }
}
