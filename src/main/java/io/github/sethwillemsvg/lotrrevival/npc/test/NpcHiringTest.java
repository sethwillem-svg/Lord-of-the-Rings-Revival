package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.alignment.PledgeBreakReason;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionKind;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HireCost;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.NpcUnitData;
import io.github.sethwillemsvg.lotrrevival.npc.hire.PledgeCondition;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitEntry;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitList;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitReleasedEvent;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitTask;
import io.github.sethwillemsvg.lotrrevival.npc.interact.Hireable;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Hiring state and rules (ticket #68): cost formula, requirements, hire state, desertion and kill crediting. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcHiringTest {
    private NpcHiringTest() {}

    // ------------------------------------------------------------------ fixtures

    private static ServerPlayer mockPlayer(GameTestHelper helper, int x, int z) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "npc-hiring-mock"), false);
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
        return player;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static NpcEntity spawn(GameTestHelper helper, EntityType<? extends NpcEntity> type, int x, int z) {
        NpcEntity entity = helper.spawn(type, new BlockPos(x, 0, z));
        entity.setPersistenceRequired();
        return entity;
    }

    private static UnitList shirriffList() {
        return NpcUnitData.list(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), NpcRole.UNIT_TRADER)
                .orElseThrow(() -> new AssertionError("shirriff unit list missing"));
    }

    private static UnitEntry bounder(UnitList list) {
        return list.entry("bounder").orElseThrow(() -> new AssertionError("bounder entry missing"));
    }

    private static void setHobbit(ServerPlayer player, float value) {
        Alignment.set(player, Factions.HOBBIT, value, AlignmentSource.command());
    }

    // ------------------------------------------------------------------ data

    @GameTest(template = "empty")
    public static void shirriffListLoaded(GameTestHelper helper) {
        UnitList list = shirriffList();
        helper.assertTrue(list.baseAlignment() == 50.0f, "base " + list.baseAlignment());
        helper.assertTrue(list.units().size() == 1, "one unit shipped, has " + list.units().size());
        UnitEntry entry = bounder(list);
        helper.assertTrue(entry.cost() == 20, "cost " + entry.cost());
        helper.assertTrue(entry.extraAlignment() == 0.0f, "extra " + entry.extraAlignment());
        helper.assertTrue(entry.pledge() == PledgeCondition.NONE, "pledge " + entry.pledge());
        helper.assertTrue(entry.task() == UnitTask.WARRIOR, "task " + entry.task());
        helper.assertTrue(
                entry.entity().equals(LOTRRevival.id("hobbit_bounder")), "entity " + entry.entity());
        helper.assertTrue(list.requiredAlignment(entry) == 50.0f, "R " + list.requiredAlignment(entry));
        helper.succeed();
    }

    // ------------------------------------------------------------------ cost and requirements

    @GameTest(template = "empty")
    public static void hireCostTable(GameTestHelper helper) {
        expectCost(helper, "unpledged +50", HireCost.cost(20, 50, 50, false), 40);
        expectCost(helper, "unpledged +1050", HireCost.cost(20, 50, 1050, false), 30);
        expectCost(helper, "unpledged +2050", HireCost.cost(20, 50, 2050, false), 20);
        expectCost(helper, "unpledged +9000", HireCost.cost(20, 50, 9000, false), 20);
        expectCost(helper, "pledged +100", HireCost.cost(20, 50, 100, true), 20);
        expectCost(helper, "pledged +800", HireCost.cost(20, 50, 800, true), 15);
        expectCost(helper, "pledged +1550", HireCost.cost(20, 50, 1550, true), 10);
        expectCost(helper, "pledged +5000", HireCost.cost(20, 50, 5000, true), 10);
        expectCost(helper, "minimum 1", HireCost.cost(1, 0, 5000, true), 1);
        // Round half up: C 3 pledged with f 1/3 gives 2.5, which rounds to 3.
        expectCost(helper, "half up", HireCost.cost(3, 0, 500, true), 3);
        helper.succeed();
    }

    private static void expectCost(GameTestHelper helper, String name, int actual, int expected) {
        helper.assertTrue(actual == expected, name + ": expected " + expected + " got " + actual);
    }

    @GameTest(template = "empty")
    public static void pureRequirementCheck(GameTestHelper helper) {
        helper.assertTrue(
                HireCost.check(false, 49, 50, true).equals(Optional.of(HireCost.Failure.ALIGNMENT)),
                "+49 fails alignment for a Bounder");
        helper.assertTrue(HireCost.check(false, 50, 50, true).isEmpty(), "+50 passes");
        helper.assertTrue(
                HireCost.check(true, 500, 50, true).equals(Optional.of(HireCost.Failure.HOSTILE)), "hostile first");
        helper.assertTrue(
                HireCost.check(false, 500, 50, false).equals(Optional.of(HireCost.Failure.PLEDGE)), "pledge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pledgeConditionKinds(GameTestHelper helper) {
        var hobbit = Factions.HOBBIT;
        var pledgeHobbit = Optional.of(hobbit);
        helper.assertTrue(PledgeCondition.NONE.holds(hobbit, Optional.empty(), kind -> false), "none always holds");
        helper.assertTrue(!PledgeCondition.FACTION.holds(hobbit, Optional.empty(), kind -> false), "unpledged");
        helper.assertTrue(PledgeCondition.FACTION.holds(hobbit, pledgeHobbit, kind -> false), "own faction");
        helper.assertTrue(
                !PledgeCondition.FACTION.holds(hobbit, Optional.of(Factions.BREE), kind -> false), "other faction");
        helper.assertTrue(
                PledgeCondition.ANY_ELF.holds(hobbit, Optional.of(Factions.HIGH_ELF), kind -> kind == FactionKind.ELF),
                "elf pledge");
        helper.assertTrue(
                !PledgeCondition.ANY_ELF.holds(
                        hobbit,
                        Optional.of(Factions.DORWINION),
                        kind -> kind == FactionKind.ELF || kind == FactionKind.MEN),
                "elf and men does not count");
        helper.assertTrue(
                PledgeCondition.ANY_DWARF.holds(
                        hobbit, Optional.of(Factions.DURINS_FOLK), kind -> kind == FactionKind.DWARF),
                "dwarf pledge");
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void requirementCheckForPlayer(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, 8, 10);
        try {
            NpcEntity shirriff = spawn(helper, NpcEntityTypes.HOBBIT_SHIRRIFF.get(), 8, 6);
            UnitList list = shirriffList();
            UnitEntry entry = bounder(list);
            UnitEntry pledged = new UnitEntry(
                    "pledged",
                    entry.entity(),
                    20,
                    0.0f,
                    PledgeCondition.FACTION,
                    UnitTask.WARRIOR,
                    Optional.empty(),
                    Optional.empty());

            setHobbit(player, 49);
            helper.assertTrue(
                    HireCost.checkFor(shirriff, player, list, entry, Factions.HOBBIT)
                            .equals(Optional.of(HireCost.Failure.ALIGNMENT)),
                    "+49 fails alignment");
            setHobbit(player, 50);
            helper.assertTrue(
                    HireCost.checkFor(shirriff, player, list, entry, Factions.HOBBIT).isEmpty(), "+50 passes");
            helper.assertTrue(
                    HireCost.checkFor(shirriff, player, list, pledged, Factions.HOBBIT)
                            .equals(Optional.of(HireCost.Failure.PLEDGE)),
                    "faction entry fails pledge when unpledged");
            Alignment.tryPledge(player, Factions.HOBBIT, true);
            helper.assertTrue(
                    HireCost.checkFor(shirriff, player, list, pledged, Factions.HOBBIT).isEmpty(),
                    "faction entry passes when pledged to the faction");
            helper.assertTrue(HireCost.pledgedTo(player, Factions.HOBBIT), "pledged to hobbits");
            helper.assertTrue(
                    HireCost.costFor(player, list, entry, Factions.HOBBIT) == 20,
                    "pledged at +50 pays 20, got " + HireCost.costFor(player, list, entry, Factions.HOBBIT));
            helper.assertTrue(
                    HireCost.unitFaction(helper.getLevel(), NpcEntityTypes.HOBBIT_BOUNDER.get())
                            .equals(Optional.of(Factions.HOBBIT)),
                    "bounder faction");
            shirriff.discard();
            helper.succeed();
        } finally {
            remove(helper, player);
        }
    }

    // ------------------------------------------------------------------ state

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void hireStateSurvivesSaveLoad(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        try {
            NpcEntity unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 8, 6);
            UnitList list = shirriffList();
            helper.assertTrue(!unit.isHired(), "wild unit is not hired");
            helper.assertTrue(unit.owner().isEmpty(), "no owner yet");
            helper.assertTrue(unit.commandState() == Hireable.CommandState.NONE, "no command yet");

            HiredUnits.hire(unit, owner, list, bounder(list), "  Rangers  ");
            HiredUnit state = HiredUnits.get(unit);
            state = state.withProgress(new HiredUnit.Progress(7, 3, 5))
                    .withOriginalGear(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET))
                    .withCommand(new HiredUnit.Command(
                            Hireable.CommandState.GUARD_POS, Optional.of(new BlockPos(1, 2, 3)), 12, false, true, true));
            HiredUnits.set(unit, state);

            helper.assertTrue(unit.isHired(), "hired");
            helper.assertTrue(unit.owner().equals(Optional.of(owner.getUUID())), "owner");
            helper.assertTrue(unit.hireMin() == 50.0f, "hireMin is R, got " + unit.hireMin());
            helper.assertTrue(unit.creditedPlayer().equals(Optional.of(owner.getUUID())), "credited player is online owner");
            helper.assertTrue(HiredUnits.get(unit).squadron().equals("Rangers"), "squadron trimmed");
            helper.assertTrue(HiredUnits.get(unit).command().guardRange() == 12, "range");

            CompoundTag tag = new CompoundTag();
            unit.saveWithoutId(tag);
            NpcEntity copy = NpcEntityTypes.HOBBIT_BOUNDER.get().create(helper.getLevel());
            copy.load(tag);
            HiredUnit loaded = HiredUnits.get(copy);
            helper.assertTrue(loaded.owner().equals(Optional.of(owner.getUUID())), "loaded owner");
            helper.assertTrue(loaded.ownerName().equals(owner.getGameProfile().getName()), "loaded owner name");
            helper.assertTrue(loaded.requiredAlignment() == 50.0f, "loaded R");
            helper.assertTrue(loaded.pledge() == PledgeCondition.NONE, "loaded pledge");
            helper.assertTrue(loaded.faction().equals(Factions.HOBBIT.location()), "loaded faction");
            helper.assertTrue(loaded.squadron().equals("Rangers"), "loaded squadron");
            helper.assertTrue(loaded.task() == UnitTask.WARRIOR, "loaded task");
            helper.assertTrue(loaded.progress().equals(new HiredUnit.Progress(7, 3, 5)), "loaded progress");
            helper.assertTrue(loaded.command().equals(state.command()), "loaded command " + loaded.command());
            helper.assertTrue(
                    loaded.originalGear().get(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "loaded original gear");
            helper.assertTrue(copy.isHired(), "copy is hired");
            copy.discard();
            unit.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void hiredUnitNeverDespawns(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        try {
            NpcEntity control = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 4, 6);
            NpcEntity unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 12, 6);
            control.setWild(true);
            unit.setWild(true);
            helper.assertTrue(control.removeWhenFarAway(1.0e9), "an unhired wild unit may despawn");
            UnitList list = shirriffList();
            HiredUnits.hire(unit, owner, list, bounder(list), "");
            helper.assertTrue(!unit.removeWhenFarAway(1.0e9), "a hired unit never despawns");
            helper.assertTrue(unit.requiresCustomPersistence(), "a hired unit is persistent");
            control.discard();
            unit.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void releaseRestoresOriginalGear(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        List<UnitReleasedEvent> events = new ArrayList<>();
        Consumer<UnitReleasedEvent> listener = events::add;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, UnitReleasedEvent.class, listener);
        try {
            NpcEntity unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 8, 6);
            UnitList list = shirriffList();
            HiredUnits.hire(unit, owner, list, bounder(list), "");
            ItemStack own = new ItemStack(Items.LEATHER_HELMET);
            unit.setItemSlot(EquipmentSlot.HEAD, own.copy());
            // The player swaps in a golden helmet; the unit's own is remembered.
            HiredUnits.set(unit, HiredUnits.get(unit).withOriginalGear(EquipmentSlot.HEAD, own));
            unit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));

            HiredUnits.release(unit, UnitReleasedEvent.Reason.DISMISSED);

            helper.assertTrue(!unit.isHired(), "no longer hired");
            helper.assertTrue(unit.owner().isEmpty(), "no owner");
            helper.assertTrue(unit.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "original restored");
            helper.assertTrue(
                    !helper.getLevel()
                            .getEntitiesOfClass(
                                    net.minecraft.world.entity.item.ItemEntity.class,
                                    unit.getBoundingBox().inflate(3.0),
                                    item -> item.getItem().is(Items.GOLDEN_HELMET))
                            .isEmpty(),
                    "given gear dropped");
            helper.assertTrue(events.size() == 1, "one release event, got " + events.size());
            helper.assertTrue(events.get(0).getReason() == UnitReleasedEvent.Reason.DISMISSED, "reason");
            helper.assertTrue(events.get(0).getOwner().equals(owner.getUUID()), "event owner");
            helper.assertTrue(events.get(0).wasOwnerNotified(), "owner messaged");
            helper.assertTrue(HiredUnits.get(unit).equals(HiredUnit.NONE), "state cleared");
            unit.discard();
            helper.getLevel()
                    .getEntitiesOfClass(
                            net.minecraft.world.entity.item.ItemEntity.class,
                            owner.getBoundingBox().inflate(40.0),
                            item -> item.getItem().is(Items.GOLDEN_HELMET))
                    .forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, owner);
        }
    }

    // ------------------------------------------------------------------ desertion

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void unitDesertsBelowRequiredAlignment(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        List<UnitReleasedEvent> events = new ArrayList<>();
        Consumer<UnitReleasedEvent> listener = events::add;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, UnitReleasedEvent.class, listener);
        try {
            setHobbit(owner, 100);
            NpcEntity unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 8, 6);
            UnitList list = shirriffList();
            HiredUnits.hire(unit, owner, list, bounder(list), "");
            setHobbit(owner, 49);
            helper.runAfterDelay(25, () -> {
                try {
                    helper.assertTrue(!unit.isHired(), "unit should have deserted at +49");
                    helper.assertTrue(unit.owner().isEmpty(), "no owner after desertion");
                    helper.assertTrue(events.size() == 1, "one release event, got " + events.size());
                    helper.assertTrue(events.get(0).getReason() == UnitReleasedEvent.Reason.DESERTED, "deserted");
                    helper.assertTrue(events.get(0).wasOwnerNotified(), "owner was messaged");
                    unit.discard();
                    helper.succeed();
                } finally {
                    NeoForge.EVENT_BUS.unregister(listener);
                    remove(helper, owner);
                }
            });
        } catch (RuntimeException | Error e) {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, owner);
            throw e;
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void unitStaysAtRequiredAlignment(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        try {
            setHobbit(owner, 100);
            NpcEntity unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 8, 6);
            UnitList list = shirriffList();
            HiredUnits.hire(unit, owner, list, bounder(list), "");
            setHobbit(owner, 50);
            helper.runAfterDelay(45, () -> {
                try {
                    helper.assertTrue(unit.isHired(), "unit stays at exactly R");
                    unit.discard();
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
    public static void unitDesertsWhenPledgeCleared(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        try {
            setHobbit(owner, 300);
            Alignment.tryPledge(owner, Factions.HOBBIT, true);
            helper.assertTrue(HireCost.pledgedTo(owner, Factions.HOBBIT), "pledged");
            NpcEntity unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 8, 6);
            UnitList list = shirriffList();
            UnitEntry base = bounder(list);
            UnitEntry pledged = new UnitEntry(
                    "pledged",
                    base.entity(),
                    20,
                    0.0f,
                    PledgeCondition.FACTION,
                    UnitTask.WARRIOR,
                    Optional.empty(),
                    Optional.empty());
            HiredUnits.hire(unit, owner, list, pledged, "");
            helper.assertTrue(HiredUnits.get(unit).pledge() == PledgeCondition.FACTION, "pledge condition stored");
            Alignment.breakPledge(owner, PledgeBreakReason.COMMAND);
            setHobbit(owner, 300);
            helper.runAfterDelay(25, () -> {
                try {
                    helper.assertTrue(!unit.isHired(), "unit should desert once the pledge is cleared");
                    unit.discard();
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
    public static void offlineOwnerNeverTriggersDesertion(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        NpcEntity unit = null;
        try {
            setHobbit(owner, 100);
            unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 8, 6);
            UnitList list = shirriffList();
            HiredUnits.hire(unit, owner, list, bounder(list), "");
            setHobbit(owner, 0);
        } finally {
            remove(helper, owner);
        }
        NpcEntity hired = unit;
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(hired.isHired(), "no desertion check while the owner is offline");
            hired.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ kills

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void hiredKillChangesNoAlignmentAndFiresHireEvent(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        List<FactionKillEvent> events = new ArrayList<>();
        Consumer<FactionKillEvent> listener = events::add;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FactionKillEvent.class, listener);
        try {
            setHobbit(owner, 100);
            NpcEntity unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 8, 6);
            UnitList list = shirriffList();
            HiredUnits.hire(unit, owner, list, bounder(list), "");
            NpcEntity victim = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 9, 6);
            float hobbitBefore = Alignment.get(owner, Factions.HOBBIT);
            float breeBefore = Alignment.get(owner, Factions.BREE);

            victim.hurt(helper.getLevel().damageSources().mobAttack(unit), 1000.0f);

            helper.assertTrue(!victim.isAlive(), "victim should be dead");
            helper.assertTrue(events.size() == 1, "one kill event, got " + events.size());
            FactionKillEvent event = events.get(0);
            helper.assertTrue(event.isCreditedViaHire(), "hire flag set");
            helper.assertTrue(event.getPlayer() == owner, "credited to the owner");
            helper.assertTrue(
                    Alignment.get(owner, Factions.HOBBIT) == hobbitBefore, "hobbit alignment unchanged");
            helper.assertTrue(Alignment.get(owner, Factions.BREE) == breeBefore, "bree alignment unchanged");
            unit.discard();
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void swordOrderedUnitGivesNoAlignmentToItsTarget(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        ServerPlayer victimPlayer = mockPlayer(helper, 4, 10);
        try {
            setHobbit(owner, 100);
            setHobbit(victimPlayer, 100);
            NpcEntity unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 8, 6);
            UnitList list = shirriffList();
            HiredUnits.hire(unit, owner, list, bounder(list), "");
            helper.assertTrue(unit.grantsAlignmentOnKill(victimPlayer), "normal kill grants alignment");
            HiredUnits.set(
                    unit,
                    HiredUnits.get(unit)
                            .withCommand(new HiredUnit.Command(
                                    Hireable.CommandState.FOLLOW, Optional.empty(), 8, true, false, true)));
            unit.setTarget(victimPlayer);
            helper.assertTrue(
                    !unit.grantsAlignmentOnKill(victimPlayer), "self-defence: no alignment loss at positive standing");
            setHobbit(victimPlayer, -10);
            helper.assertTrue(unit.grantsAlignmentOnKill(victimPlayer), "no protection at non-positive standing");
            unit.discard();
            helper.succeed();
        } finally {
            remove(helper, victimPlayer);
            remove(helper, owner);
        }
    }
}
