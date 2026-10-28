package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.NpcUnitData;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitEntry;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitList;
import io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitCommandMenu;
import io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitCommandMenus;
import io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitCommandPayloads;
import io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitGear;
import io.github.sethwillemsvg.lotrrevival.npc.interact.Hireable;
import io.github.sethwillemsvg.lotrrevival.npc.trade.PayloadLimiter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The unit command screen (ticket #73): payload rules, gear replacement and restoring, dismissal, opening. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnitCommandScreenTest {
    private UnitCommandScreenTest() {}

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static NpcEntity hiredUnit(GameTestHelper helper, ServerPlayer owner) {
        NpcEntity unit = helper.spawn(NpcEntityTypes.HOBBIT_BOUNDER.get(), new BlockPos(2, 1, 2));
        unit.setNoAi(true);
        unit.setInvulnerable(true);
        unit.setPersistenceRequired();
        UnitList list = NpcUnitData.list(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), NpcRole.UNIT_TRADER)
                .orElseThrow(() -> new AssertionError("shirriff unit list missing"));
        UnitEntry entry = list.entry("bounder").orElseThrow(() -> new AssertionError("bounder entry missing"));
        Alignment.set(owner, Factions.HOBBIT, 500, AlignmentSource.command());
        HiredUnits.hire(unit, owner, list, entry, "");
        owner.setPos(unit.getX() + 1.0, unit.getY(), unit.getZ());
        return unit;
    }

    private static UnitCommandMenu open(ServerPlayer player, NpcEntity unit, int id) {
        UnitCommandMenu menu = new UnitCommandMenu(id, player.getInventory(), unit);
        player.containerMenu = menu;
        return menu;
    }

    // ------------------------------------------------------------------ switches

    @GameTest(template = "empty")
    public static void guardToggleViaPayloadSetsGuardAtUnitPosition(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "unit-cmd-owner");
        NpcEntity unit = hiredUnit(helper, owner);
        open(owner, unit, 7);
        try {
            BlockPos at = unit.blockPosition();
            helper.assertTrue(
                    UnitCommandPayloads.handle(owner, 7, UnitCommandMenu.Action.GUARD_ON.ordinal(), 20), "guard on applied");
            HiredUnit.Command command = HiredUnits.get(unit).command();
            helper.assertTrue(command.state() == Hireable.CommandState.GUARD_POS, "guarding");
            helper.assertTrue(command.guardPos().filter(at::equals).isPresent(), "guard point is the unit's position");
            helper.assertTrue(command.guardRange() == 20, "range 20, got " + command.guardRange());

            helper.assertTrue(
                    UnitCommandPayloads.handle(owner, 7, UnitCommandMenu.Action.SET_RANGE.ordinal(), 3), "range applied");
            helper.assertTrue(HiredUnits.get(unit).command().guardRange() == 3, "range 3");
            helper.assertTrue(
                    !UnitCommandPayloads.handle(owner, 7, UnitCommandMenu.Action.SET_RANGE.ordinal(), 65), "65 refused");
            helper.assertTrue(
                    !UnitCommandPayloads.handle(owner, 7, UnitCommandMenu.Action.SET_RANGE.ordinal(), 0), "0 refused");
            helper.assertTrue(HiredUnits.get(unit).command().guardRange() == 3, "range unchanged by bad values");

            helper.assertTrue(
                    UnitCommandPayloads.handle(owner, 7, UnitCommandMenu.Action.TELEPORT_OFF.ordinal(), 0), "teleport off");
            helper.assertTrue(!HiredUnits.get(unit).command().teleport(), "teleport is off");
            helper.assertTrue(
                    UnitCommandPayloads.handle(owner, 7, UnitCommandMenu.Action.GUARD_OFF.ordinal(), 0), "guard off");
            helper.assertTrue(
                    HiredUnits.get(unit).command().state() == Hireable.CommandState.FOLLOW, "following again");
            helper.assertTrue(!UnitCommandPayloads.handle(owner, 7, 99, 0), "unknown action dropped");
        } finally {
            owner.containerMenu = owner.inventoryMenu;
            unit.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonOwnerPayloadIsIgnored(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "unit-cmd-owner");
        ServerPlayer other = player(helper, "unit-cmd-other");
        NpcEntity unit = hiredUnit(helper, owner);
        other.setPos(unit.getX() + 1.0, unit.getY(), unit.getZ());
        UnitCommandMenu menu = open(other, unit, 8);
        try {
            helper.assertTrue(!menu.stillValid(other), "menu is not valid for a non-owner");
            helper.assertTrue(
                    !UnitCommandPayloads.handle(other, 8, UnitCommandMenu.Action.GUARD_ON.ordinal(), 10), "payload refused");
            helper.assertTrue(
                    HiredUnits.get(unit).command().state() == Hireable.CommandState.FOLLOW, "unit not guarding");
            helper.assertTrue(
                    !UnitCommandPayloads.handle(other, 8, UnitCommandMenu.Action.DISMISS.ordinal(), 0), "dismiss refused");
            helper.assertTrue(unit.isHired(), "still hired");

            // The owner without the menu open, or with a wrong container id, is ignored too.
            owner.containerMenu = owner.inventoryMenu;
            helper.assertTrue(
                    !UnitCommandPayloads.handle(owner, 8, UnitCommandMenu.Action.GUARD_ON.ordinal(), 10), "no menu open");
            open(owner, unit, 9);
            helper.assertTrue(
                    !UnitCommandPayloads.handle(owner, 8, UnitCommandMenu.Action.GUARD_ON.ordinal(), 10), "wrong container id");
            helper.assertTrue(
                    HiredUnits.get(unit).command().state() == Hireable.CommandState.FOLLOW, "still not guarding");
        } finally {
            other.containerMenu = other.inventoryMenu;
            owner.containerMenu = owner.inventoryMenu;
            unit.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void menuClosesWhenOwnerIsTooFarAway(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "unit-cmd-owner");
        NpcEntity unit = hiredUnit(helper, owner);
        UnitCommandMenu menu = open(owner, unit, 10);
        try {
            helper.assertTrue(menu.stillValid(owner), "valid within reach");
            owner.setPos(unit.getX() + 13.0, unit.getY(), unit.getZ());
            helper.assertTrue(!menu.stillValid(owner), "invalid at 13 blocks");
            helper.assertTrue(
                    !UnitCommandPayloads.handle(owner, 10, UnitCommandMenu.Action.TELEPORT_OFF.ordinal(), 0),
                    "payload out of reach refused");
            helper.assertTrue(HiredUnits.get(unit).command().teleport(), "teleport unchanged");
        } finally {
            owner.containerMenu = owner.inventoryMenu;
            unit.discard();
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ gear

    @GameTest(template = "empty")
    public static void givenHelmetReplacesOriginalAndTakingItRestoresIt(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "unit-cmd-owner");
        NpcEntity unit = hiredUnit(helper, owner);
        UnitCommandMenu menu = open(owner, unit, 11);
        try {
            unit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "the unit's own piece is not a given item");

            // The player puts a golden helmet in the helmet slot.
            helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.GOLDEN_HELMET)), "helmet fits the helmet slot");
            helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.GOLDEN_BOOTS)), "boots do not");
            helper.assertTrue(!menu.getSlot(1).mayPlace(new ItemStack(Items.GOLDEN_HELMET)), "helmet not in chest slot");
            menu.getSlot(0).setByPlayer(new ItemStack(Items.GOLDEN_HELMET));
            helper.assertTrue(unit.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "unit wears the given helmet");
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.GOLDEN_HELMET), "slot shows it");
            helper.assertTrue(
                    HiredUnits.get(unit).originalGear().get(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET),
                    "own helmet remembered");

            // Swapping for another helmet keeps the first original.
            menu.getSlot(0).setByPlayer(new ItemStack(Items.IRON_HELMET));
            helper.assertTrue(unit.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "swapped");
            helper.assertTrue(
                    HiredUnits.get(unit).originalGear().get(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET),
                    "original still the leather helmet");

            // The player takes it out.
            ItemStack taken = menu.getSlot(0).remove(1);
            helper.assertTrue(taken.is(Items.IRON_HELMET), "player receives the given helmet");
            helper.assertTrue(unit.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "original restored");
            helper.assertTrue(HiredUnits.get(unit).originalGear().isEmpty(), "nothing remembered any more");
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "slot empty again");

            // Shift-click moves an item from the inventory into its slot and back.
            owner.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            int hotbarSlot = UnitCommandMenu.PLAYER_END - 9;
            menu.quickMoveStack(owner, hotbarSlot);
            helper.assertTrue(unit.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD), "sword in main hand");
            helper.assertTrue(owner.getInventory().getItem(0).isEmpty(), "left the inventory");
            helper.assertTrue(UnitGear.given(unit, EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD), "reported as given");
            menu.quickMoveStack(owner, 4);
            helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.DIAMOND_SWORD)), "back in the inventory");
            helper.assertTrue(HiredUnits.get(unit).originalGear().isEmpty(), "original restored after shift-click");
        } finally {
            owner.getInventory().clearContent();
            owner.containerMenu = owner.inventoryMenu;
            unit.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dismissViaPayloadFreesUnitAndDropsGivenGear(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "unit-cmd-owner");
        NpcEntity unit = hiredUnit(helper, owner);
        UnitCommandMenu menu = open(owner, unit, 12);
        try {
            unit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            menu.getSlot(0).setByPlayer(new ItemStack(Items.GOLDEN_HELMET));

            helper.assertTrue(
                    UnitCommandPayloads.handle(owner, 12, UnitCommandMenu.Action.DISMISS.ordinal(), 0), "dismiss applied");
            helper.assertTrue(!unit.isHired() && unit.owner().isEmpty(), "unit is unowned");
            helper.assertTrue(unit.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "original restored");
            List<ItemEntity> drops = new ArrayList<>(helper.getLevel()
                    .getEntitiesOfClass(
                            ItemEntity.class, unit.getBoundingBox().inflate(3.0), item -> item.getItem().is(Items.GOLDEN_HELMET)));
            helper.assertTrue(drops.size() == 1, "given helmet dropped, got " + drops.size());
            helper.assertTrue(!menu.stillValid(owner), "menu closes once the unit is unowned");
            drops.forEach(ItemEntity::discard);
        } finally {
            owner.containerMenu = owner.inventoryMenu;
            unit.discard();
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ rate limit and opening

    @GameTest(template = "empty")
    public static void payloadsAreRateLimited(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "unit-cmd-owner");
        NpcEntity unit = hiredUnit(helper, owner);
        open(owner, unit, 13);
        try {
            int applied = 0;
            for (int i = 0; i < PayloadLimiter.MAX_PER_SECOND + 10; i++) {
                if (UnitCommandPayloads.handle(owner, 13, UnitCommandMenu.Action.SET_RANGE.ordinal(), 1 + i % 60)) {
                    applied++;
                }
            }
            helper.assertTrue(applied <= PayloadLimiter.MAX_PER_SECOND, "at most 20 per second, got " + applied);
            helper.assertTrue(applied > 0, "some applied");
        } finally {
            owner.containerMenu = owner.inventoryMenu;
            unit.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownerRightClickOpensTheScreenOthersTalk(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "unit-cmd-owner");
        ServerPlayer other = player(helper, "unit-cmd-other");
        NpcEntity unit = hiredUnit(helper, owner);
        other.setPos(unit.getX() + 1.0, unit.getY(), unit.getZ());
        List<ServerPlayer> opened = new ArrayList<>();
        UnitCommandMenus.Opener previous = UnitCommandMenus.setOpener((p, u) -> {
            opened.add(p);
            return true;
        });
        try {
            owner.setShiftKeyDown(true);
            unit.interact(owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(opened.isEmpty(), "sneaking owner talks instead");

            owner.setShiftKeyDown(false);
            unit.interact(owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(opened.size() == 1 && opened.get(0) == owner, "owner opens the screen");

            unit.interact(other, InteractionHand.MAIN_HAND);
            helper.assertTrue(opened.size() == 1, "another player's right-click does not open it");

            // A name tag is taken before vanilla applies it and acts as a plain right-click.
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NAME_TAG));
            PlayerInteractEvent.EntityInteract tagClick =
                    new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, unit);
            NeoForge.EVENT_BUS.post(tagClick);
            helper.assertTrue(tagClick.isCanceled(), "name tag click is cancelled");
            helper.assertTrue(!unit.hasCustomName(), "name tag does nothing");
            helper.assertTrue(opened.size() == 2, "the click opens the screen instead");
        } finally {
            UnitCommandMenus.setOpener(previous);
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            unit.discard();
        }
        helper.succeed();
    }
}
