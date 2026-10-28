package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.api.FellowshipQuery;
import io.github.sethwillemsvg.lotrrevival.core.api.LandProtection;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.NpcUnitData;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitEntry;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitList;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitTargeting;
import io.github.sethwillemsvg.lotrrevival.npc.spawn.NpcSpawnData;
import io.github.sethwillemsvg.lotrrevival.npc.spawn.NpcSpawner;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Banner spawn protection (wild spawner) and fellowship unit protection (hired-unit targeting), ticket #106. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcProtectionTest {
    private NpcProtectionTest() {}

    @GameTest(template = "empty")
    public static void bannerProtectionStopsFactionSpawn(GameTestHelper helper) {
        List<ResourceKey<Faction>> asked = new ArrayList<>();
        LandProtection.Impl previous = LandProtection.swapForTesting(new LandProtection.Impl() {
            @Override
            public boolean blocksFactionSpawn(Level level, BlockPos pos, ResourceKey<Faction> faction) {
                asked.add(faction);
                return faction.equals(Factions.HOBBIT);
            }
        });
        try {
            NpcSpawnData.Entry entry = new NpcSpawnData.Entry(NpcEntityTypes.HOBBIT.get(), 1, 1, 1, Set.of(), 1);
            BlockPos feet = helper.absolutePos(new BlockPos(2, 1, 2));
            for (int i = 0; i < 20; i++) {
                helper.assertTrue(
                        !NpcSpawner.trySpawn(helper.getLevel(), entry, feet, List.of(), helper.getLevel().random),
                        "a protected spot must refuse the spawn");
            }
            helper.assertTrue(asked.contains(Factions.HOBBIT), "the spawner asked about the NPC's faction");
            int npcs = helper.getLevel()
                    .getEntities(EntityTypeTest.forClass(NpcEntity.class), e -> e.blockPosition().distSqr(feet) < 64)
                    .size();
            helper.assertTrue(npcs == 0, "no NPC of the protected faction was placed, found " + npcs);
            helper.succeed();
        } finally {
            LandProtection.swapForTesting(previous);
        }
    }

    @GameTest(template = "empty")
    public static void fellowshipProtectedUnitsAreNotTargets(GameTestHelper helper) {
        ServerPlayer a = mockPlayer(helper, 4, 4);
        ServerPlayer b = mockPlayer(helper, 12, 4);
        FellowshipQuery.Impl previous = null;
        boolean swapped = false;
        try {
            NpcEntity unitA = hiredUnit(helper, a, 4, 6);
            NpcEntity unitB = hiredUnit(helper, b, 12, 6);
            helper.assertTrue(
                    UnitTargeting.judge(unitA, unitB) != UnitTargeting.Verdict.REJECT, "control: other unit is open");
            helper.assertTrue(
                    UnitTargeting.judge(unitA, b) != UnitTargeting.Verdict.REJECT, "control: other player is open");
            previous = FellowshipQuery.swapForTesting(new FellowshipQuery.Impl() {
                @Override
                public boolean protectsUnits(UUID x, UUID y) {
                    return true;
                }
            });
            swapped = true;
            helper.assertTrue(
                    UnitTargeting.judge(unitA, unitB) == UnitTargeting.Verdict.REJECT,
                    "protected owner's unit is never a target");
            helper.assertTrue(
                    UnitTargeting.judge(unitA, b) == UnitTargeting.Verdict.REJECT,
                    "protected owner is never a target");
            FellowshipQuery.swapForTesting(new FellowshipQuery.Impl() {
                @Override
                public boolean preventsPvp(UUID x, UUID y) {
                    return true;
                }
            });
            a.setLastHurtMob(b);
            helper.assertTrue(
                    UnitTargeting.judge(unitA, b) == UnitTargeting.Verdict.REJECT,
                    "PvP-protected player stays off even when the owner hit them");
            unitA.discard();
            unitB.discard();
            helper.succeed();
        } finally {
            if (swapped) {
                FellowshipQuery.swapForTesting(previous);
            }
            helper.getLevel().getServer().getPlayerList().remove(a);
            helper.getLevel().getServer().getPlayerList().remove(b);
        }
    }

    private static ServerPlayer mockPlayer(GameTestHelper helper, int x, int z) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "npc-protection-mock"), false);
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
        Vec3 pos = helper.absoluteVec(new Vec3(x + 0.5, 1, z + 0.5));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static NpcEntity hiredUnit(GameTestHelper helper, ServerPlayer owner, int x, int z) {
        NpcEntity unit = helper.spawn(NpcEntityTypes.HOBBIT_BOUNDER.get(), new BlockPos(x, 1, z));
        unit.setPersistenceRequired();
        unit.setInvulnerable(true);
        UnitList list = NpcUnitData.list(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), NpcRole.UNIT_TRADER)
                .orElseThrow(() -> new AssertionError("shirriff unit list missing"));
        UnitEntry entry = list.entry("bounder").orElseThrow(() -> new AssertionError("bounder entry missing"));
        Alignment.set(owner, Factions.HOBBIT, 500, AlignmentSource.command());
        HiredUnits.hire(unit, owner, list, entry, "");
        return unit;
    }
}
