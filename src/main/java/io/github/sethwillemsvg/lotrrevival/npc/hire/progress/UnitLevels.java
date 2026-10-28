package io.github.sethwillemsvg.lotrrevival.npc.hire.progress;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.NpcConfig;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitTask;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeech;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.neoforged.neoforge.common.NeoForge;

/** Kill count, XP, levels and the per-level max health bonus of hired units. */
public final class UnitLevels {
    /** Distance (blocks) within which the owner hears a unit's kill and idle lines. */
    public static final double SPEECH_RANGE = 16.0;

    /** Id of the max health modifier a unit gets from its level (value = level - 1). */
    public static final ResourceLocation HEALTH_MODIFIER = LOTRRevival.id("unit_level");

    /** Entity tag on the level-up fireworks; their blast damage is cancelled. */
    public static final String FIREWORK_TAG = "lotrrevival_unit_level_fireworks";

    private static final double GROWTH = 1.08;

    private UnitLevels() {}

    /** Total XP needed to be level {@code level}: floor(3 x (L - 1) x 1.08^(L - 2)); level 1 needs 0. */
    public static int totalXpForLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        return (int) Math.floor(3.0 * (level - 1) * Math.pow(GROWTH, level - 2));
    }

    /** The highest level whose total XP is at most {@code xp}. No cap. */
    public static int levelForXp(int xp) {
        int level = 1;
        while (totalXpForLevel(level + 1) <= xp) {
            level++;
        }
        return level;
    }

    /** Whether killing {@code victim} is worth XP to a unit of {@code unitFaction}: an enemy-faction NPC only. */
    public static boolean givesXp(NpcEntity unit, ResourceLocation unitFaction, LivingEntity victim) {
        if (!(victim instanceof NpcEntity npc)) {
            return false;
        }
        ResourceKey<Faction> own = ResourceKey.create(ModDataRegistries.FACTION, unitFaction);
        return FactionRelations.relation(unit.level().registryAccess(), own, npc.faction())
                .isBad();
    }

    /** Called when a hired unit killed something: kill count +1, XP and a speech line for an enemy NPC. */
    public static void onKill(NpcEntity unit, LivingEntity victim) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired()) {
            return;
        }
        boolean enemyNpc = givesXp(unit, state.faction(), victim);
        HiredUnit.Progress progress = state.progress();
        state = state.withProgress(new HiredUnit.Progress(progress.xp(), progress.level(), progress.kills() + 1));
        HiredUnits.set(unit, state);
        if (!enemyNpc) {
            return;
        }
        addXp(unit, 1);
        ServerPlayer owner = HiredUnits.onlineOwner(unit);
        if (owner != null && nearOwner(unit, owner) && unit.getRandom().nextInt(3) == 0) {
            NpcSpeech.say(unit, owner, NpcSpeech.HIRED_KILL);
        }
    }

    static boolean nearOwner(NpcEntity unit, ServerPlayer owner) {
        return owner.level() == unit.level() && owner.distanceToSqr(unit) <= SPEECH_RANGE * SPEECH_RANGE;
    }

    /** Adds XP to a hired Warrior unit and applies every level gained. No-op for other units or with leveling off. */
    public static void addXp(NpcEntity unit, int amount) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired()
                || amount <= 0
                || state.task() != UnitTask.WARRIOR
                || !NpcConfig.get(NpcConfig.UNITS_LEVELING)) {
            return;
        }
        HiredUnit.Progress old = state.progress();
        int xp = old.xp() + amount;
        int level = Math.max(old.level(), levelForXp(xp));
        HiredUnit updated = state.withProgress(new HiredUnit.Progress(xp, level, old.kills()));
        HiredUnits.set(unit, updated);
        if (level > old.level()) {
            applyHealthBonus(unit);
            unit.heal(level - old.level());
            for (int gained = old.level() + 1; gained <= level; gained++) {
                onLevelUp(unit, updated, gained);
            }
        }
    }

    private static void onLevelUp(NpcEntity unit, HiredUnit state, int level) {
        ServerPlayer owner = HiredUnits.onlineOwner(unit);
        if (owner != null && owner.connection != null) {
            owner.sendSystemMessage(Component.translatable("npc.lotrrevival.unit.level_up", unit.speakerName(), level));
        }
        if (unit.level() instanceof ServerLevel serverLevel) {
            launchFireworks(serverLevel, unit, state.faction(), level % 5 == 0);
        }
        NeoForge.EVENT_BUS.post(new UnitLevelUpEvent(unit, state.owner().orElseThrow(), level));
    }

    /** Sets the max health modifier to (level - 1) for a hired unit; idempotent. */
    public static void applyHealthBonus(NpcEntity unit) {
        HiredUnit state = HiredUnits.get(unit);
        AttributeInstance maxHealth = unit.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        int bonus = state.isHired() ? state.progress().level() - 1 : 0;
        if (bonus <= 0) {
            maxHealth.removeModifier(HEALTH_MODIFIER);
            return;
        }
        AttributeModifier existing = maxHealth.getModifier(HEALTH_MODIFIER);
        if (existing == null || existing.amount() != bonus) {
            maxHealth.addOrReplacePermanentModifier(
                    new AttributeModifier(HEALTH_MODIFIER, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Removes the level health bonus (on release) and clamps health to the new maximum. */
    public static void removeHealthBonus(NpcEntity unit) {
        AttributeInstance maxHealth = unit.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(HEALTH_MODIFIER);
        }
        if (unit.getHealth() > unit.getMaxHealth()) {
            unit.setHealth(unit.getMaxHealth());
        }
    }

    /** Fireworks in the faction colour above the unit; every 5th level a larger, two-part burst. */
    private static void launchFireworks(ServerLevel level, NpcEntity unit, ResourceLocation factionId, boolean big) {
        int color = 0xFFFFFF;
        Faction faction = level.registryAccess()
                .registryOrThrow(ModDataRegistries.FACTION)
                .get(factionId);
        if (faction != null) {
            color = faction.color();
        }
        List<FireworkExplosion> explosions = new ArrayList<>();
        explosions.add(new FireworkExplosion(
                big ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.SMALL_BALL,
                IntList.of(color),
                IntList.of(),
                false,
                big));
        if (big) {
            explosions.add(new FireworkExplosion(
                    FireworkExplosion.Shape.STAR, IntList.of(color, 0xFFFFFF), IntList.of(color), true, false));
        }
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(0, explosions));
        FireworkRocketEntity entity =
                new FireworkRocketEntity(level, unit.getX(), unit.getY() + unit.getBbHeight(), unit.getZ(), rocket);
        entity.addTag(FIREWORK_TAG);
        level.addFreshEntity(entity);
    }
}
