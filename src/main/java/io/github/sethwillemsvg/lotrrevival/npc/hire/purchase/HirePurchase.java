package io.github.sethwillemsvg.lotrrevival.npc.hire.purchase;

import io.github.sethwillemsvg.lotrrevival.core.coin.Coins;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HireCost;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.NpcUnitData;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitEntry;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitList;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteractEvent;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcStanding;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeech;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenu;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The hiring purchase flow: the Units tab rows a player sees and the server-side hire (re-validate, find room, take
 * coins, spawn, mark as hired, post {@link NpcHireEvent}). See {@code specs/npc/hiring-trading.md}, Hire flow.
 */
public final class HirePurchase {
    /** Largest squadron text accepted from the screen; the stored text is capped by {@code HiredUnit}. */
    public static final int SQUADRON_FIELD_MAX = 200;

    private static final double[][] SPOTS = {
        {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}
    };

    private HirePurchase() {}

    /** Called once from {@code NpcTrade.init}. */
    public static void init() {
        NeoForge.EVENT_BUS.addListener(HirePurchase::onInteract);
    }

    /** HIRE opens the trade screen on the Units tab and takes over the interaction. */
    private static void onInteract(NpcInteractEvent event) {
        if (event.getKind() == NpcInteractEvent.Kind.HIRE
                && NpcTradeMenus.openTab(event.getPlayer(), event.getNpc(), NpcTradeMenu.Tab.UNITS)) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ rows

    /** The unit list of a trader, if it is a unit trader whose type has one. */
    public static Optional<UnitList> listOf(NpcEntity trader) {
        if (trader.getRole() != NpcRole.UNIT_TRADER) {
            return Optional.empty();
        }
        return NpcUnitData.list(trader.getType(), trader.getRole());
    }

    /** The faction of a unit entry, from a throwaway instance of its entity type. */
    private static Optional<ResourceKey<Faction>> factionOf(ServerLevel level, UnitEntry entry) {
        return BuiltInRegistries.ENTITY_TYPE
                .getOptional(entry.entity())
                .flatMap(type -> HireCost.unitFaction(level, type));
    }

    /** The rows this player sees at this trader, in list order. Empty if the trader has no unit list. */
    public static List<UnitRowView> rows(ServerPlayer player, NpcEntity trader) {
        List<UnitRowView> rows = new ArrayList<>();
        Optional<UnitList> list = listOf(trader);
        if (list.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return rows;
        }
        int coins = Coins.count(player);
        for (UnitEntry entry : list.get().units()) {
            Optional<ResourceKey<Faction>> faction = factionOf(level, entry);
            if (faction.isEmpty()) {
                continue;
            }
            int cost = HireCost.costFor(player, list.get(), entry, faction.get());
            UnitRowView.Blocked blocked = HireCost.checkFor(trader, player, list.get(), entry, faction.get())
                    .map(failure -> failure == HireCost.Failure.PLEDGE ? UnitRowView.Blocked.PLEDGE : UnitRowView.Blocked.ALIGNMENT)
                    .orElse(coins < cost ? UnitRowView.Blocked.COINS : UnitRowView.Blocked.NONE);
            rows.add(new UnitRowView(
                    entry.id(), entry.entity(), entry.mount(), cost, list.get().requiredAlignment(entry), entry.pledge(), blocked));
        }
        return rows;
    }

    // ------------------------------------------------------------------ hire

    private static void say(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("npc.lotrrevival.hire.msg." + key), true);
    }

    /**
     * Hires the entry for the player. Validates everything again (access to the trader, hostility, alignment, pledge,
     * coins), finds a free spot at the player's feet before taking anything, then pays, spawns a fresh unit facing the
     * way the player looks and marks it hired. Mounted entries are not supported yet and are refused.
     *
     * @return the new unit, or empty if nothing happened
     */
    public static Optional<NpcEntity> hire(ServerPlayer player, NpcEntity trader, String entryId, String squadron) {
        Optional<UnitList> maybeList = listOf(trader);
        if (maybeList.isEmpty() || !(player.level() instanceof ServerLevel level) || trader.level() != level) {
            return Optional.empty();
        }
        UnitList list = maybeList.get();
        Optional<UnitEntry> maybeEntry = list.entry(entryId);
        if (maybeEntry.isEmpty() || maybeEntry.get().mount().isPresent()) {
            return Optional.empty();
        }
        UnitEntry entry = maybeEntry.get();

        // The player must be able to open the unit screen at all: calm trader and alignment at least hire_min.
        Optional<Float> access = NpcSpeech.accessMin(trader);
        if (access.isEmpty()
                || NpcStanding.isHostile(trader, player)
                || NpcStanding.alignment(trader, player) < access.get()) {
            say(player, "alignment");
            return Optional.empty();
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.entity()).orElse(null);
        NpcEntity unit = type == null ? null : type.create(level) instanceof NpcEntity npc ? npc : null;
        if (unit == null) {
            return Optional.empty();
        }
        boolean done = false;
        try {
            ResourceKey<Faction> faction = unit.faction();
            Optional<HireCost.Failure> failure = HireCost.checkFor(trader, player, list, entry, faction);
            if (failure.isPresent()) {
                say(player, failure.get() == HireCost.Failure.PLEDGE ? "pledge" : "alignment");
                return Optional.empty();
            }
            int cost = HireCost.costFor(player, list, entry, faction);
            if (Coins.count(player) < cost) {
                say(player, "coins");
                return Optional.empty();
            }
            if (!placeAtFeet(level, unit, player)) {
                say(player, "no_room");
                return Optional.empty();
            }
            if (!Coins.pay(player, cost)) {
                say(player, "coins");
                return Optional.empty();
            }
            unit.finalizeSpawn(level, level.getCurrentDifficultyAt(unit.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            HiredUnits.hire(unit, player, list, entry, squadron);
            level.addFreshEntityWithPassengers(unit);
            done = true;
            trader.playSound(SoundEvents.VILLAGER_TRADE, 1.0f, 1.0f);
            NeoForge.EVENT_BUS.post(new NpcHireEvent(player, trader, unit, entry.id(), cost));
            return Optional.of(unit);
        } finally {
            if (!done) {
                unit.discard();
            }
        }
    }

    /** Puts the unit at the player's feet (or the nearest free spot around them), facing the way the player looks. */
    private static boolean placeAtFeet(ServerLevel level, NpcEntity unit, ServerPlayer player) {
        float yaw = player.getYRot();
        for (double dy = 0; dy <= 1; dy++) {
            for (double[] spot : SPOTS) {
                unit.moveTo(player.getX() + spot[0], player.getY() + dy, player.getZ() + spot[1], yaw, 0.0f);
                if (level.noCollision(unit) && !level.getFluidState(unit.blockPosition()).is(FluidTags.LAVA)) {
                    unit.setYHeadRot(yaw);
                    unit.setYBodyRot(yaw);
                    return true;
                }
            }
        }
        return false;
    }
}
