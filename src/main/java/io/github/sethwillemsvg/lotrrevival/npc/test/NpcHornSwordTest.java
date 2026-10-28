package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.NpcUnitData;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitEntry;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitList;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitCommands;
import io.github.sethwillemsvg.lotrrevival.npc.hire.horn.HornComponents;
import io.github.sethwillemsvg.lotrrevival.npc.hire.horn.HornItems;
import io.github.sethwillemsvg.lotrrevival.npc.hire.horn.HornMode;
import io.github.sethwillemsvg.lotrrevival.npc.hire.horn.HornPayloads;
import io.github.sethwillemsvg.lotrrevival.npc.hire.horn.Squadrons;
import io.github.sethwillemsvg.lotrrevival.npc.hire.horn.SwordOrders;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Command horn, command sword and squadrons (ticket #74). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcHornSwordTest {
    private NpcHornSwordTest() {}

    // ------------------------------------------------------------------ fixtures

    private static ServerPlayer mockPlayer(GameTestHelper helper, int x, int z) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "npc-horn-mock"), false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        Vec3 pos = helper.absoluteVec(new Vec3(x + 0.5, 0, z + 0.5));
        player.setPos(pos.x, pos.y, pos.z);
        Alignment.set(player, Factions.HOBBIT, 100.0f, AlignmentSource.command());
        return player;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static NpcEntity unit(GameTestHelper helper, ServerPlayer owner, int x, int z, String squadron) {
        NpcEntity entity = helper.spawn(NpcEntityTypes.HOBBIT_BOUNDER.get(), new BlockPos(x, 0, z));
        entity.setPersistenceRequired();
        UnitList list = NpcUnitData.list(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), NpcRole.UNIT_TRADER)
                .orElseThrow(() -> new AssertionError("shirriff unit list missing"));
        UnitEntry entry = list.entry("bounder").orElseThrow(() -> new AssertionError("bounder entry missing"));
        HiredUnits.hire(entity, owner, list, entry, squadron);
        return entity;
    }

    private static Zombie zombie(GameTestHelper helper, int x, int z) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 0, z));
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        return zombie;
    }

    private static ItemStack horn(HornMode mode, String squadron) {
        ItemStack stack = new ItemStack(HornItems.COMMAND_HORN.get());
        if (mode != HornMode.BLANK) {
            stack.set(HornComponents.HORN_MODE.get(), mode);
        }
        stack.set(HornComponents.SQUADRON.get(), squadron);
        return stack;
    }

    private static void blow(GameTestHelper helper, ServerPlayer player, ItemStack horn) {
        horn.getItem().finishUsingItem(horn, helper.getLevel(), player);
    }

    private static boolean halted(NpcEntity unit) {
        return HiredUnits.get(unit).command().halted();
    }

    // ------------------------------------------------------------------ horn

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void hornHaltsAndReadiesMatchingUnits(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 4);
        try {
            NpcEntity alpha = unit(helper, owner, 6, 6, "Alpha");
            NpcEntity beta = unit(helper, owner, 10, 6, "beta");
            NpcEntity guard = unit(helper, owner, 8, 10, "alpha");
            UnitCommands.setGuard(guard, true, 8);
            Zombie zombie = zombie(helper, 6, 14);
            alpha.setTarget(zombie);
            beta.setTarget(zombie);

            ItemStack horn = horn(HornMode.HALT, "  ALPHA ");
            owner.setItemInHand(InteractionHand.MAIN_HAND, horn);
            blow(helper, owner, horn);

            helper.assertTrue(halted(alpha), "matching unit halted (case-insensitive, trimmed)");
            helper.assertTrue(alpha.getTarget() == null, "halted unit dropped its target");
            helper.assertTrue(!halted(beta) && beta.getTarget() == zombie, "other squadron ignored");
            helper.assertTrue(!halted(guard), "guarding unit ignored");
            helper.assertTrue(HornComponents.modeOf(horn) == HornMode.READY, "halt switches the horn to ready");

            blow(helper, owner, horn);
            helper.assertTrue(!halted(alpha), "ready resumes the unit");
            helper.assertTrue(!halted(beta) && !halted(guard), "others still unaffected");
            helper.assertTrue(HornComponents.modeOf(horn) == HornMode.HALT, "ready switches the horn to halt");
            alpha.discard();
            beta.discard();
            guard.discard();
            zombie.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void emptySquadronMatchesOnlyEmpty(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 4);
        try {
            NpcEntity none = unit(helper, owner, 6, 6, "");
            NpcEntity named = unit(helper, owner, 10, 6, "Alpha");
            ItemStack horn = horn(HornMode.HALT, "");
            owner.setItemInHand(InteractionHand.MAIN_HAND, horn);
            blow(helper, owner, horn);
            helper.assertTrue(halted(none), "empty squadron unit halted by the unnamed horn");
            helper.assertTrue(!halted(named), "named unit ignored by the unnamed horn");
            helper.assertTrue(Squadrons.matches(" Alpha ", "aLPHA"), "match ignores case and spaces");
            helper.assertTrue(!Squadrons.matches("", "Alpha"), "empty does not match a name");
            none.discard();
            named.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void hornModeAndRenamePayloadsAreChecked(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, 8, 4);
        try {
            helper.assertTrue(!HornPayloads.applyMode(player, false, HornMode.HALT), "no horn in hand: refused");
            ItemStack blank = new ItemStack(HornItems.COMMAND_HORN.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, blank);
            helper.assertTrue(HornComponents.modeOf(blank) == HornMode.BLANK, "new horn is blank");
            helper.assertTrue(HornPayloads.applyMode(player, false, HornMode.SUMMON), "mode set");
            helper.assertTrue(HornComponents.modeOf(blank) == HornMode.SUMMON, "mode is summon");
            helper.assertTrue(HornPayloads.applyRename(player, false, "x".repeat(300)), "rename accepted");
            helper.assertTrue(HornComponents.squadronOf(blank).length() == 200, "squadron capped at 200");
            helper.assertTrue(HornPayloads.applyRename(player, false, "   "), "blank rename accepted");
            helper.assertTrue(HornComponents.squadronOf(blank).isEmpty(), "blank rename clears the squadron");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STICK));
            helper.assertTrue(!HornPayloads.applyRename(player, false, "Alpha"), "wrong item refused");
            helper.succeed();
        } finally {
            remove(helper, player);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void hornSummonsFarUnitNearOwner(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 1);
        try {
            NpcEntity far = unit(helper, owner, 8, 38, "Alpha");
            helper.assertTrue(far.distanceTo(owner) > 30.0f, "unit starts far away: " + far.distanceTo(owner));
            ItemStack horn = horn(HornMode.SUMMON, "alpha");
            owner.setItemInHand(InteractionHand.MAIN_HAND, horn);
            blow(helper, owner, horn);
            helper.assertTrue(far.distanceTo(owner) <= 6.5f, "unit summoned within 6 blocks: " + far.distanceTo(owner));
            helper.assertTrue(HornComponents.modeOf(horn) == HornMode.SUMMON, "summon horn stays summon");
            far.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    // ------------------------------------------------------------------ sword

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void swordSplitsUnitsAcrossTargetsAndSkyCancels(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 4);
        try {
            NpcEntity a = unit(helper, owner, 7, 6, "");
            NpcEntity b = unit(helper, owner, 9, 6, "");
            Zombie z1 = zombie(helper, 7, 16);
            Zombie z2 = zombie(helper, 9, 16);
            Vec3 point = helper.absoluteVec(new Vec3(8.0, 1.0, 16.5));

            int ordered = SwordOrders.order(owner, "", point);
            helper.assertTrue(ordered == 2, "both units ordered, got " + ordered);
            LivingEntity ta = a.getTarget();
            LivingEntity tb = b.getTarget();
            helper.assertTrue(ta != null && tb != null && ta != tb, "units split across the two zombies");
            helper.assertTrue(
                    (ta == z1 || ta == z2) && (tb == z1 || tb == z2), "targets are the zombies near the aimed point");
            helper.assertTrue(
                    HiredUnits.get(a).command().swordOrder() && HiredUnits.get(b).command().swordOrder(),
                    "sword-order flags set");

            ItemStack sword = new ItemStack(HornItems.COMMAND_SWORD.get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, sword);
            SwordOrders.use(owner, "", java.util.Optional.empty());
            helper.assertTrue(a.getTarget() == null && b.getTarget() == null, "aiming at the sky drops the targets");
            helper.assertTrue(
                    !HiredUnits.get(a).command().swordOrder() && !HiredUnits.get(b).command().swordOrder(),
                    "sword-order flags cleared");
            a.discard();
            b.discard();
            z1.discard();
            z2.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void swordIgnoresOtherSquadronsAndFarUnits(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 1);
        try {
            NpcEntity mine = unit(helper, owner, 8, 3, "Alpha");
            NpcEntity other = unit(helper, owner, 9, 3, "Beta");
            NpcEntity far = unit(helper, owner, 8, 30, "Alpha");
            Zombie zombie = zombie(helper, 8, 14);
            int ordered = SwordOrders.order(owner, "alpha", helper.absoluteVec(new Vec3(8.5, 1.0, 14.5)));
            helper.assertTrue(ordered == 1, "only the near unit of the squadron is ordered, got " + ordered);
            helper.assertTrue(mine.getTarget() == zombie, "matching unit targets the zombie");
            helper.assertTrue(other.getTarget() == null, "other squadron ignored");
            helper.assertTrue(far.getTarget() == null, "unit beyond 12 blocks ignored");
            mine.discard();
            other.discard();
            far.discard();
            zombie.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void swordOrderEndsWhenTargetDies(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 4);
        try {
            NpcEntity a = unit(helper, owner, 8, 6, "");
            Zombie z = zombie(helper, 8, 12);
            SwordOrders.order(owner, "", helper.absoluteVec(new Vec3(8.5, 1.0, 12.5)));
            helper.assertTrue(HiredUnits.get(a).command().swordOrder(), "flag set");
            z.kill();
            helper.runAfterDelay(3, () -> {
                try {
                    helper.assertTrue(!HiredUnits.get(a).command().swordOrder(), "flag cleared after the target died");
                    a.discard();
                    helper.succeed();
                } finally {
                    remove(helper, owner);
                }
            });
        } catch (RuntimeException | Error e) {
            remove(helper, owner);
            throw e;
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void swordHasOneAttackDamage(GameTestHelper helper) {
        ItemStack sword = new ItemStack(HornItems.COMMAND_SWORD.get());
        double bonus = sword.getAttributeModifiers().modifiers().stream()
                .filter(e -> e.attribute().is(Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow())
                        && e.slot() == EquipmentSlotGroup.MAINHAND)
                .mapToDouble(e -> e.modifier().amount())
                .sum();
        helper.assertTrue(bonus == 0.0, "no damage bonus over the bare-hand 1, got " + bonus);
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void killingSwordOrderedUnitCostsNoAlignment(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 1);
        ServerPlayer victim = mockPlayer(helper, 8, 20);
        try {
            NpcEntity unit = unit(helper, owner, 8, 8, "");
            SwordOrders.order(owner, "", victim.position());
            helper.assertTrue(unit.getTarget() == victim, "unit ordered onto the other player");
            float before = Alignment.get(victim, Factions.HOBBIT);
            unit.hurt(helper.getLevel().damageSources().playerAttack(victim), 1000.0f);
            helper.assertTrue(!unit.isAlive(), "unit died");
            helper.assertTrue(
                    Alignment.get(victim, Factions.HOBBIT) == before,
                    "no alignment change: " + before + " -> " + Alignment.get(victim, Factions.HOBBIT));
            helper.succeed();
        } finally {
            remove(helper, victim);
            remove(helper, owner);
        }
    }
}
