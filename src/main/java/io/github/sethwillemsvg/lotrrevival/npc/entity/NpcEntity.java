package io.github.sethwillemsvg.lotrrevival.npc.entity;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionMember;
import io.github.sethwillemsvg.lotrrevival.npc.ai.IdleGoals;
import io.github.sethwillemsvg.lotrrevival.npc.ai.NpcGroundNavigation;
import io.github.sethwillemsvg.lotrrevival.npc.ai.SafetyGoals;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleData;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Base of every NPC (hobbits, men, and later elves, dwarves and orcs). Holds race, faction and class (all fixed per
 * entity type and never saved), plus the per-entity role, gender, name and home.
 *
 * <p>Kill alignment is applied by core from {@link FactionMember}; npc code never calls it.
 */
public abstract class NpcEntity extends PathfinderMob implements FactionMember {
    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GENDER =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_NAME =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> DATA_NAME_SEED =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.LONG);

    static final String TAG_ROLE = "Role";
    static final String TAG_GENDER = "Gender";
    static final String TAG_NAME = "NpcName";
    static final String TAG_NAME_SEED = "NameSeed";
    static final String TAG_HOME_POS = "HomePos";
    static final String TAG_HOME_RADIUS = "HomeRadius";

    private final NpcRace race;
    private final ResourceKey<Faction> faction;
    private final NpcClass npcClass;

    @Nullable
    private BlockPos homePos;

    private int homeRadius;

    protected NpcEntity(
            EntityType<? extends NpcEntity> type,
            Level level,
            NpcRace race,
            ResourceKey<Faction> faction,
            NpcClass npcClass) {
        super(type, level);
        this.race = race;
        this.faction = faction;
        this.npcClass = npcClass;
        setCanPickUpLoot(false);
        setPathfindingMalus(PathType.WATER, 8.0f);
        installGoals();
    }

    /** Attributes shared by every NPC of a race; types with different stats adjust the result. */
    public static AttributeSupplier.Builder createAttributes(NpcRace race) {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, race.maxHealth())
                .add(Attributes.MOVEMENT_SPEED, race.movementSpeed())
                .add(Attributes.ATTACK_DAMAGE, race.unarmedDamage())
                .add(Attributes.FOLLOW_RANGE, race.followRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, race.knockbackResistance());
    }

    // ------------------------------------------------------------------ identity

    public NpcRace race() {
        return race;
    }

    public NpcClass npcClass() {
        return npcClass;
    }

    @Override
    public ResourceKey<Faction> faction() {
        return faction;
    }

    @Override
    public boolean isCivilian() {
        return npcClass == NpcClass.CIVILIAN;
    }

    @Override
    public float alignmentKillValue() {
        Optional<Float> fromData = NpcRoleData.killValue(getType(), getRole());
        if (fromData.isPresent()) {
            return fromData.get();
        }
        // Datapack entry missing: fall back to the class default, trader roles count double a plain civilian.
        float base = npcClass.defaultKillValue();
        return isCivilian() && getRole().isTrader() ? Math.max(base, 2.0f) : base;
    }

    // ------------------------------------------------------------------ synced and saved data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, NpcRole.VILLAGER.ordinal());
        builder.define(DATA_GENDER, NpcGender.MALE.ordinal());
        builder.define(DATA_NAME, "");
        builder.define(DATA_NAME_SEED, 0L);
    }

    public NpcRole getRole() {
        return NpcRole.byOrdinal(entityData.get(DATA_ROLE));
    }

    public void setRole(NpcRole role) {
        entityData.set(DATA_ROLE, role.ordinal());
    }

    public NpcGender getGender() {
        return NpcGender.byOrdinal(entityData.get(DATA_GENDER));
    }

    public void setGender(NpcGender gender) {
        entityData.set(DATA_GENDER, gender.ordinal());
    }

    /** The generated name, or empty until the names ticket rolls one. Display falls back to the race name. */
    public String getNpcName() {
        return entityData.get(DATA_NAME);
    }

    public void setNpcName(String name) {
        entityData.set(DATA_NAME, name);
    }

    /** Seed used to roll the name once; persisted so the roll never changes. */
    public long getNameSeed() {
        return entityData.get(DATA_NAME_SEED);
    }

    public void setNameSeed(long seed) {
        entityData.set(DATA_NAME_SEED, seed);
    }

    public Optional<BlockPos> homePos() {
        return Optional.ofNullable(homePos);
    }

    public int homeRadius() {
        return homeRadius;
    }

    public void setHome(BlockPos pos, int radius) {
        this.homePos = pos.immutable();
        this.homeRadius = radius;
    }

    public void clearHome() {
        this.homePos = null;
        this.homeRadius = 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_ROLE, getRole().getSerializedName());
        tag.putString(TAG_GENDER, getGender().getSerializedName());
        tag.putString(TAG_NAME, getNpcName());
        tag.putLong(TAG_NAME_SEED, getNameSeed());
        if (homePos != null) {
            tag.put(TAG_HOME_POS, NbtUtils.writeBlockPos(homePos));
            tag.putInt(TAG_HOME_RADIUS, homeRadius);
        }
        // Faction, race and class are fixed by the entity type and deliberately not saved.
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_ROLE, Tag.TAG_STRING)) {
            NpcRole.byName(tag.getString(TAG_ROLE)).ifPresent(this::setRole);
        }
        if (tag.contains(TAG_GENDER, Tag.TAG_STRING)) {
            NpcGender.byName(tag.getString(TAG_GENDER)).ifPresent(this::setGender);
        }
        setNpcName(tag.getString(TAG_NAME));
        setNameSeed(tag.getLong(TAG_NAME_SEED));
        if (tag.contains(TAG_HOME_POS)) {
            NbtUtils.readBlockPos(tag, TAG_HOME_POS).ifPresent(pos -> setHome(pos, tag.getInt(TAG_HOME_RADIUS)));
        } else {
            clearHome();
        }
    }

    // ------------------------------------------------------------------ spawning

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        RandomSource random = level.getRandom();
        setGender(random.nextBoolean() ? NpcGender.MALE : NpcGender.FEMALE);
        setRole(rollRole(random));
        setNameSeed(random.nextLong());
        return data;
    }

    /** Weighted role roll: villager 60, farmer 30, traveller 10. Innkeeper and trader roles are never rolled. */
    public static NpcRole rollRole(RandomSource random) {
        int total = 0;
        for (NpcRole role : NpcRole.values()) {
            total += role.spawnWeight();
        }
        int pick = random.nextInt(total);
        for (NpcRole role : NpcRole.values()) {
            pick -= role.spawnWeight();
            if (pick < 0) {
                return role;
            }
        }
        return NpcRole.VILLAGER;
    }

    // ------------------------------------------------------------------ behavior

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new NpcGroundNavigation(this, level);
    }

    /**
     * Vanilla calls this from the {@code Mob} constructor, before this class's fields exist, so goals are installed
     * from the {@code NpcEntity} constructor instead (see {@link #installGoals()}).
     */
    @Override
    protected void registerGoals() {}

    /**
     * Delegates to one small installer class per concern. Later tickets (targeting, retaliation, routine) add their
     * own installer here instead of editing the existing ones.
     */
    protected void installGoals() {
        SafetyGoals.install(this, goalSelector);
        IdleGoals.install(this, goalSelector, race);
    }

    /** NPCs never pick up items. */
    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    /** NPCs never use portals, so faction NPCs cannot leak into other dimensions. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }
}
