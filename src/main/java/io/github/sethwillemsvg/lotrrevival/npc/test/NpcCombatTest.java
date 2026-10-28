package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.ai.combat.NpcCombat;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** GameTests for hostility targeting, faction warfare, fleeing, melee damage and healing (ticket #40). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcCombatTest {
    private static final int Z = 20;

    private NpcCombatTest() {}

    // ------------------------------------------------------------------ fixtures

    /** A mock server player like the helper's, but with a configurable creative flag (the helper's is always creative). */
    private static ServerPlayer mockPlayer(GameTestHelper helper, boolean creative, int x, int z) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "npc-combat-mock"), false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return creative;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        Vec3 pos = helper.absoluteVec(new Vec3(x + 0.5, 0, z + 0.5));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static ServerPlayer survivalPlayer(
            GameTestHelper helper, ResourceKey<Faction> faction, float alignment, int x, int z) {
        ServerPlayer player = mockPlayer(helper, false, x, z);
        Alignment.set(player, faction, alignment, AlignmentSource.command());
        return player;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static <T extends Entity> T spawnNoPersist(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T entity = helper.spawn(type, new BlockPos(x, 0, z));
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        return entity;
    }

    // ------------------------------------------------------------------ hostility

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    public static void bounderTargetsHostilePlayer(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper, Factions.HOBBIT, -5.0f, 10, Z);
        NpcEntity bounder = spawnNoPersist(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 6, Z);
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(bounder.getTarget() == player, "bounder should target the -5 player, has " + bounder.getTarget());
            } finally {
                remove(helper, player);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    public static void bounderIgnoresNeutralPlayer(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper, Factions.HOBBIT, 0.0f, 10, Z);
        NpcEntity bounder = spawnNoPersist(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 6, Z);
        boolean[] targeted = {false};
        helper.onEachTick(() -> targeted[0] |= bounder.getTarget() == player);
        helper.runAfterDelay(100, () -> {
            try {
                helper.assertTrue(!targeted[0], "bounder must never target a player at alignment 0");
            } finally {
                remove(helper, player);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "npc_arena", timeoutTicks = 300)
    public static void guardTargetsOnlyTheHostilePlayer(GameTestHelper helper) {
        ServerPlayer friend = survivalPlayer(helper, Factions.BREE, 20.0f, 12, Z);
        ServerPlayer enemy = survivalPlayer(helper, Factions.BREE, -20.0f, 8, Z);
        NpcEntity guard = spawnNoPersist(helper, NpcEntityTypes.BREE_GUARD.get(), 10, Z);
        boolean[] targetedFriend = {false};
        boolean[] targetedEnemy = {false};
        helper.onEachTick(() -> {
            targetedFriend[0] |= guard.getTarget() == friend;
            targetedEnemy[0] |= guard.getTarget() == enemy;
        });
        helper.runAfterDelay(100, () -> {
            try {
                helper.assertTrue(targetedEnemy[0], "guard should target the -20 player");
                helper.assertTrue(!targetedFriend[0], "guard must never target the +20 player");
            } finally {
                remove(helper, friend);
                remove(helper, enemy);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "npc_arena", timeoutTicks = 300)
    public static void creativePlayerIsNeverTargeted(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, true, 10, Z);
        Alignment.set(player, Factions.HOBBIT, -100.0f, AlignmentSource.command());
        NpcEntity bounder = spawnNoPersist(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 6, Z);
        boolean[] targeted = {false};
        helper.onEachTick(() -> targeted[0] |= bounder.getTarget() == player);
        helper.runAfterDelay(100, () -> {
            try {
                helper.assertTrue(!targeted[0], "creative player must never be targeted");
            } finally {
                remove(helper, player);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "npc_arena", timeoutTicks = 400)
    public static void alliedGuardsDoNotTargetEachOther(GameTestHelper helper) {
        NpcEntity bounder = spawnNoPersist(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 8, Z);
        NpcEntity guard = spawnNoPersist(helper, NpcEntityTypes.BREE_GUARD.get(), 10, Z);
        boolean[] targeted = {false};
        helper.onEachTick(() -> targeted[0] |= bounder.getTarget() == guard || guard.getTarget() == bounder);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(!targeted[0], "allied guards must not target each other");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ fleeing and monsters

    @GameTest(template = "npc_arena", timeoutTicks = 300)
    public static void hobbitFleesHostilePlayer(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper, Factions.HOBBIT, -5.0f, 8, Z - 3);
        NpcEntity hobbit = spawnNoPersist(helper, NpcEntityTypes.HOBBIT.get(), 8, Z);
        double start = hobbit.distanceTo(player);
        helper.runAfterDelay(60, () -> {
            try {
                double now = hobbit.distanceTo(player);
                helper.assertTrue(now >= start + 3.0, "hobbit should flee: distance " + start + " -> " + now + ", difficulty " + hobbit.level().getDifficulty());
            } finally {
                remove(helper, player);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "npc_arena", timeoutTicks = 300)
    public static void guardAttacksZombieAndManFlees(GameTestHelper helper) {
        Zombie zombie = spawnNoPersist(helper, EntityType.ZOMBIE, 13, Z);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        NpcEntity guard = spawnNoPersist(helper, NpcEntityTypes.BREE_GUARD.get(), 6, Z);
        NpcEntity man = spawnNoPersist(helper, NpcEntityTypes.BREE_MAN.get(), 9, Z + 4);
        double start = man.distanceTo(zombie);
        boolean[] targeted = {false};
        helper.onEachTick(() -> targeted[0] |= guard.getTarget() == zombie);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(targeted[0], "guard should target the zombie within 16 blocks");
            double now = man.distanceTo(zombie);
            helper.assertTrue(now >= start + 2.0, "man should flee the zombie: distance " + start + " -> " + now);
            zombie.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ melee

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void meleeDamageIsThreeQuartersOfWeapon(GameTestHelper helper) {
        NpcEntity armed = spawnNoPersist(helper, NpcEntityTypes.BREE_GUARD.get(), 8, Z);
        NpcEntity unarmed = spawnNoPersist(helper, NpcEntityTypes.BREE_MAN.get(), 8, Z + 4);
        armed.setNoAi(true);
        unarmed.setNoAi(true);
        armed.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Cow cowA = spawnNoPersist(helper, EntityType.COW, 12, Z);
        Cow cowB = spawnNoPersist(helper, EntityType.COW, 12, Z + 4);
        cowA.setNoAi(true);
        cowB.setNoAi(true);
        helper.assertTrue(
                Math.abs(NpcCombat.meleeDamage(armed) - 4.5) < 1.0e-4, "iron sword melee " + NpcCombat.meleeDamage(armed));
        float beforeA = cowA.getHealth();
        float beforeB = cowB.getHealth();
        helper.assertTrue(armed.doHurtTarget(cowA), "armed hit lands");
        helper.assertTrue(unarmed.doHurtTarget(cowB), "unarmed hit lands");
        float dealtA = beforeA - cowA.getHealth();
        float dealtB = beforeB - cowB.getHealth();
        helper.assertTrue(Math.abs(dealtA - 4.5f) < 1.0e-3f, "iron sword dealt " + dealtA);
        helper.assertTrue(Math.abs(dealtB - 2.0f) < 1.0e-3f, "unarmed dealt " + dealtB);
        helper.succeed();
    }

    // ------------------------------------------------------------------ healing

    @GameTest(template = "npc_arena", timeoutTicks = 500)
    public static void onlyTradersHeal(GameTestHelper helper) {
        NpcEntity innkeeper = spawnNoPersist(helper, NpcEntityTypes.HOBBIT.get(), 8, Z);
        innkeeper.setRole(NpcRole.INNKEEPER);
        innkeeper.setHealth(8.0f);
        NpcEntity guard = spawnNoPersist(helper, NpcEntityTypes.BREE_GUARD.get(), 12, Z);
        guard.setHealth(10.0f);
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(innkeeper.getHealth() == 9.0f, "innkeeper heals 1 HP in 40 ticks, has " + innkeeper.getHealth());
            helper.assertTrue(guard.getHealth() == 10.0f, "guard must not heal, has " + guard.getHealth());
        });
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(guard.getHealth() == 10.0f, "guard must not heal after 400 ticks, has " + guard.getHealth());
            LivingEntity healed = innkeeper;
            helper.assertTrue(healed.getHealth() > 9.0f, "innkeeper keeps healing, has " + healed.getHealth());
            helper.succeed();
        });
    }
}
