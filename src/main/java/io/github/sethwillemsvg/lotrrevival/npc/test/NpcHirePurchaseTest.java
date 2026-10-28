package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.coin.Coins;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.purchase.HirePurchase;
import io.github.sethwillemsvg.lotrrevival.npc.hire.purchase.NpcHireEvent;
import io.github.sethwillemsvg.lotrrevival.npc.hire.purchase.UnitRowView;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteractEvent;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteraction;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenu;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenus;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The hiring purchase flow and the Units tab (ticket #72): hire, refusals, cost, opening and squadron text. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcHirePurchaseTest {
    private NpcHirePurchaseTest() {}

    private static ServerPlayer mockPlayer(GameTestHelper helper, int x, int z) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "npc-purchase-mock"), false);
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

    private static NpcEntity shirriff(GameTestHelper helper) {
        NpcEntity shirriff = helper.spawn(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), new BlockPos(8, 0, 6));
        shirriff.setPersistenceRequired();
        shirriff.setRole(NpcRole.UNIT_TRADER);
        return shirriff;
    }

    private static void setHobbit(ServerPlayer player, float value) {
        Alignment.set(player, Factions.HOBBIT, value, AlignmentSource.command());
    }

    private static List<NpcEntity> bounders(GameTestHelper helper, ServerPlayer near) {
        return helper.getLevel()
                .getEntitiesOfClass(
                        NpcEntity.class,
                        near.getBoundingBox().inflate(2.0),
                        e -> e.getType() == NpcEntityTypes.HOBBIT_BOUNDER.get());
    }

    private static void discardAll(List<NpcEntity> units) {
        units.forEach(NpcEntity::discard);
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void unpledgedPlayerHiresBounderForForty(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, 8, 10);
        List<NpcHireEvent> events = new ArrayList<>();
        Consumer<NpcHireEvent> listener = events::add;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, NpcHireEvent.class, listener);
        NpcEntity trader = shirriff(helper);
        try {
            setHobbit(player, 50);
            Coins.give(player, 40);
            Optional<NpcEntity> hired = HirePurchase.hire(player, trader, "bounder", "");
            helper.assertTrue(hired.isPresent(), "hire should succeed");
            helper.assertTrue(Coins.count(player) == 0, "0 coins left, has " + Coins.count(player));
            List<NpcEntity> found = bounders(helper, player);
            helper.assertTrue(found.size() == 1, "one bounder within 2 blocks, found " + found.size());
            NpcEntity unit = found.get(0);
            HiredUnit state = HiredUnits.get(unit);
            helper.assertTrue(state.owner().filter(player.getUUID()::equals).isPresent(), "owned by the player");
            helper.assertTrue(state.requiredAlignment() == 50.0f, "R " + state.requiredAlignment());
            helper.assertTrue(Alignment.get(player, Factions.HOBBIT) == 50.0f, "alignment unchanged");
            helper.assertTrue(unit.isPersistenceRequired(), "persistent");
            helper.assertTrue(events.size() == 1, "one hire event, got " + events.size());
            helper.assertTrue(events.get(0).getCost() == 40, "event cost " + events.get(0).getCost());
            helper.assertTrue(events.get(0).getUnit() == unit && events.get(0).getTrader() == trader, "event parties");
            helper.assertTrue(events.get(0).getEntryId().equals("bounder"), "event entry");
            discardAll(found);
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            trader.discard();
            remove(helper, player);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void thirtyNineCoinsHiresNothing(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, 8, 10);
        List<NpcHireEvent> events = new ArrayList<>();
        Consumer<NpcHireEvent> listener = events::add;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, NpcHireEvent.class, listener);
        NpcEntity trader = shirriff(helper);
        try {
            setHobbit(player, 50);
            Coins.give(player, 39);
            helper.assertTrue(HirePurchase.hire(player, trader, "bounder", "").isEmpty(), "hire refused");
            helper.assertTrue(Coins.count(player) == 39, "coins untouched, has " + Coins.count(player));
            helper.assertTrue(bounders(helper, player).isEmpty(), "no unit spawned");
            helper.assertTrue(events.isEmpty(), "no event");
            List<UnitRowView> rows = HirePurchase.rows(player, trader);
            helper.assertTrue(rows.size() == 1, "one row");
            helper.assertTrue(rows.get(0).blocked() == UnitRowView.Blocked.COINS, "grey-out reason " + rows.get(0).blocked());
            helper.assertTrue(rows.get(0).cost() == 40, "row cost " + rows.get(0).cost());
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            trader.discard();
            remove(helper, player);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void pledgedPlayerShownAndPaysTwenty(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, 8, 10);
        NpcEntity trader = shirriff(helper);
        try {
            setHobbit(player, 100);
            Alignment.tryPledge(player, Factions.HOBBIT, true);
            Coins.give(player, 25);
            List<UnitRowView> rows = HirePurchase.rows(player, trader);
            helper.assertTrue(rows.size() == 1 && rows.get(0).cost() == 20, "row shows 20, rows " + rows);
            helper.assertTrue(rows.get(0).hirable(), "row hirable");
            helper.assertTrue(HirePurchase.hire(player, trader, "bounder", "").isPresent(), "hire succeeds");
            helper.assertTrue(Coins.count(player) == 5, "paid 20, has " + Coins.count(player));
            discardAll(bounders(helper, player));
            helper.succeed();
        } finally {
            trader.discard();
            remove(helper, player);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void lowAlignmentIsGreyedAndRefused(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, 8, 10);
        NpcEntity trader = shirriff(helper);
        try {
            setHobbit(player, 49);
            Coins.give(player, 100);
            helper.assertTrue(HirePurchase.hire(player, trader, "bounder", "").isEmpty(), "refused at +49");
            helper.assertTrue(Coins.count(player) == 100, "coins untouched");
            helper.assertTrue(
                    HirePurchase.rows(player, trader).get(0).blocked() == UnitRowView.Blocked.ALIGNMENT, "alignment reason");
            helper.assertTrue(HirePurchase.hire(player, trader, "no_such_unit", "").isEmpty(), "unknown entry refused");
            helper.assertTrue(bounders(helper, player).isEmpty(), "no unit");
            helper.succeed();
        } finally {
            trader.discard();
            remove(helper, player);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void unitsTabOpensOnlyFromFifty(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, 8, 10);
        NpcEntity trader = shirriff(helper);
        List<NpcInteractEvent.Kind> kinds = new ArrayList<>();
        Consumer<NpcInteractEvent> listener = event -> kinds.add(event.getKind());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, NpcInteractEvent.class, listener);
        List<NpcTradeMenu.Tab> opened = new ArrayList<>();
        NpcTradeMenus.Opener previous = NpcTradeMenus.setOpener((p, npc, tab) -> {
            opened.add(tab);
            return true;
        });
        try {
            setHobbit(player, 49);
            NpcInteraction.onRightClick(trader, player);
            helper.assertTrue(opened.isEmpty(), "no screen at +49");
            helper.assertTrue(kinds.equals(List.of(NpcInteractEvent.Kind.TALK)), "TALK posted at +49, got " + kinds);

            kinds.clear();
            setHobbit(player, 50);
            NpcInteraction.onRightClick(trader, player);
            helper.assertTrue(opened.equals(List.of(NpcTradeMenu.Tab.UNITS)), "Units tab opened at +50, got " + opened);
            helper.assertTrue(kinds.equals(List.of(NpcInteractEvent.Kind.HIRE)), "HIRE posted at +50, got " + kinds);
            helper.succeed();
        } finally {
            NpcTradeMenus.setOpener(previous);
            NeoForge.EVENT_BUS.unregister(listener);
            trader.discard();
            remove(helper, player);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void squadronTextIsTrimmedAndCapped(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, 8, 10);
        NpcEntity trader = shirriff(helper);
        try {
            setHobbit(player, 50);
            Coins.give(player, 80);
            NpcEntity first = HirePurchase.hire(player, trader, "bounder", "   Gate Watch \t").orElseThrow();
            helper.assertTrue(HiredUnits.get(first).squadron().equals("Gate Watch"), "trimmed: '" + HiredUnits.get(first).squadron() + "'");
            NpcEntity second = HirePurchase.hire(player, trader, "bounder", "  " + "x".repeat(300) + "  ").orElseThrow();
            helper.assertTrue(HiredUnits.get(second).squadron().length() == 200, "capped at 200");
            first.discard();
            second.discard();
            helper.succeed();
        } finally {
            trader.discard();
            remove(helper, player);
        }
    }
}
