package io.github.sethwillemsvg.lotrrevival.npc.ai.combat;

import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcClass;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.interact.Hireable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Shared numbers and rules for NPC targeting, fleeing, melee and healing (see {@code specs/npc/npc-base.md}). */
public final class NpcCombat {
    /** Horizontal sense range of guards and captains, in blocks. */
    public static final double SENSE_RANGE = 16.0;
    /** Vertical sense range, in blocks. */
    public static final double SENSE_VERTICAL = 8.0;
    /** Civilians flee from threats within this many blocks. */
    public static final float FLEE_RANGE = 8.0f;
    /** Guards keep this far from creepers. */
    public static final float CREEPER_DISTANCE = 4.0f;
    /** Ticks between target re-evaluations. */
    public static final int RECHECK_TICKS = 20;
    /** Share of the held weapon's attack damage an NPC deals. */
    public static final double WEAPON_DAMAGE_FACTOR = 0.75;
    /** Man civilians flee when below this fraction of their health. */
    public static final float LOW_HEALTH_FRACTION = 0.3f;
    /** Trader roles and hired units heal 1 HP this often while they have no target. */
    public static final int HEAL_INTERVAL_TICKS = 40;
    /** Melee speed multiplier while chasing. */
    public static final double CHASE_SPEED = 1.2;

    private NpcCombat() {}

    /** Whether the NPC fights on its own initiative: guards and captains. */
    public static boolean isFighter(NpcEntity npc) {
        return npc.npcClass() != NpcClass.CIVILIAN;
    }

    /** Within sense range horizontally and vertically (distance only, no line of sight). */
    public static boolean inSenseRange(Entity from, Entity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return dx * dx + dz * dz <= SENSE_RANGE * SENSE_RANGE && Math.abs(to.getY() - from.getY()) <= SENSE_VERTICAL;
    }

    /** A vanilla monster that NPCs fight or flee from. */
    public static boolean isMonster(Entity entity) {
        return entity.getType().getCategory() == MobCategory.MONSTER;
    }

    public static boolean isCreeper(Entity entity) {
        return entity instanceof Creeper;
    }

    /** Player is below the NPC faction's hostility threshold (deterministic part, no grey-zone roll). */
    public static boolean isHostilePlayer(NpcEntity npc, Player player) {
        return !ignoresPlayers(npc) && !isOwner(npc, player) && FactionRelations.isHostile(npc, player);
    }

    /** NPCs never target players on Peaceful. */
    public static boolean ignoresPlayers(NpcEntity npc) {
        return npc.level().getDifficulty() == Difficulty.PEACEFUL;
    }

    /** Hired units never target their owner. */
    public static boolean isOwner(NpcEntity npc, Player player) {
        return npc instanceof Hireable hireable
                && hireable.isHired()
                && hireable.owner().filter(player.getUUID()::equals).isPresent();
    }

    public static boolean isLowHealth(NpcEntity npc) {
        return npc.getHealth() < npc.getMaxHealth() * LOW_HEALTH_FRACTION;
    }

    /** Trader roles and hired units regenerate; everyone else never does. */
    public static boolean canHeal(NpcEntity npc) {
        return npc.getRole().isTrader() || (npc instanceof Hireable hireable && hireable.isHired());
    }

    /**
     * Pre-enchantment melee damage: 75% of the main-hand item's attack damage (1 plus its attack-damage modifiers),
     * or the race's unarmed damage when the held item has no attack-damage modifiers. Enchantment bonus is added by
     * vanilla's attack code afterwards.
     */
    public static double meleeDamage(NpcEntity npc) {
        ItemStack held = npc.getMainHandItem();
        double weapon = 1.0;
        boolean armed = false;
        if (!held.isEmpty()) {
            ItemAttributeModifiers modifiers =
                    held.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
                if (entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND)) {
                    armed = true;
                    double amount = entry.modifier().amount();
                    weapon += switch (entry.modifier().operation()) {
                        case ADD_VALUE, ADD_MULTIPLIED_BASE -> amount;
                        case ADD_MULTIPLIED_TOTAL -> amount * weapon;
                    };
                }
            }
        }
        if (!armed) {
            return npc.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        }
        return weapon * WEAPON_DAMAGE_FACTOR;
    }

    /** Whether a living entity is a valid thing to attack at all (alive, not creative or spectator). */
    public static boolean isAttackable(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        return !(entity instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }
}
