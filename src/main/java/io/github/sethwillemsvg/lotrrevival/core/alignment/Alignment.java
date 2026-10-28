package io.github.sethwillemsvg.lotrrevival.core.alignment;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.client.core.ClientAlignment;
import io.github.sethwillemsvg.lotrrevival.core.config.CoreConfig;
import io.github.sethwillemsvg.lotrrevival.core.event.AlignmentEvent;
import io.github.sethwillemsvg.lotrrevival.core.event.PledgeEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelation;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.network.AlignmentChangePayload;
import io.github.sethwillemsvg.lotrrevival.core.network.AlignmentSyncPayload;
import io.github.sethwillemsvg.lotrrevival.core.network.DrainNoticePayload;
import io.github.sethwillemsvg.lotrrevival.core.network.PledgeSyncPayload;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The alignment API. Reads work on both sides (the client reads the synced mirror); every write is server side.
 * Pods never touch the attachment directly.
 */
public final class Alignment {
    private static final Set<ResourceLocation> WARNED_UNKNOWN = new HashSet<>();
    private static InfluenceProvider influence = InfluenceProvider.DEFAULT;

    private Alignment() {}

    // ---------------------------------------------------------------- reads

    /** Alignment of a player with a faction (fixed factions report their fixed value; unknown factions 0.0). */
    public static float get(Player player, ResourceKey<Faction> faction) {
        Faction f = lookup(player, faction);
        if (f == null) {
            return 0.0f;
        }
        if (f.fixedAlignment().isPresent()) {
            return f.fixedAlignment().get();
        }
        if (player.level().isClientSide) {
            return ClientAlignment.get(player, faction);
        }
        return data(player).value(faction.location());
    }

    public static FactionRank rank(Player player, ResourceKey<Faction> faction) {
        Faction f = lookup(player, faction);
        if (f == null) {
            return AlignmentRules.NEUTRAL;
        }
        return AlignmentRules.rank(f, get(player, faction));
    }

    /** True when the player's alignment reaches the named rank of the faction ("neutral" and "hostile" included). */
    public static boolean meetsRank(Player player, ResourceKey<Faction> faction, String rankId) {
        Faction f = lookup(player, faction);
        if (f == null) {
            return false;
        }
        float value = get(player, faction);
        if (rankId.equals(AlignmentRules.HOSTILE.id())) {
            return true;
        }
        if (rankId.equals(AlignmentRules.NEUTRAL.id())) {
            return value >= 0.0f;
        }
        for (FactionRank rank : f.ranks()) {
            if (rank.id().equals(rankId)) {
                return value >= rank.threshold();
            }
        }
        return false;
    }

    /** The faction the player is pledged to; empty when unpledged or when the faction no longer exists. */
    public static Optional<ResourceKey<Faction>> pledge(Player player) {
        Optional<ResourceLocation> id = player.level().isClientSide
                ? ClientAlignment.pledge(player).map(ResourceKey::location)
                : data(player).pledge();
        if (id.isEmpty()) {
            return Optional.empty();
        }
        ResourceKey<Faction> key = ResourceKey.create(ModDataRegistries.FACTION, id.get());
        return registry(player).containsKey(key) ? Optional.of(key) : Optional.empty();
    }

    /** Remaining pledge cooldown in online ticks. */
    public static int pledgeCooldown(ServerPlayer player) {
        return data(player).pledgeCooldownTicks();
    }

    /** Remaining ticks of the pledge warning counter (0 when no warning is active). */
    public static int pledgeWarning(ServerPlayer player) {
        return data(player).pledgeWarningTicks();
    }

    /** Sets the remaining pledge cooldown (commands); 0 clears it. */
    public static void setPledgeCooldown(ServerPlayer player, int ticks) {
        data(player).setPledgeCooldownTicks(ticks);
        syncAll(player);
    }

    /**
     * The area-of-influence multiplier (0 to 1) for this player and faction, from the registered
     * {@link InfluenceProvider}; 1.0 until the areas-of-influence system registers one.
     */
    public static float influence(ServerPlayer player, ResourceKey<Faction> faction) {
        return influence.multiplier(player, faction);
    }

    public static void setInfluenceProvider(InfluenceProvider provider) {
        influence = provider == null ? InfluenceProvider.DEFAULT : provider;
    }

    // ---------------------------------------------------------------- writes

    /**
     * Adds to the alignment. Positive changes from any source but {@code command} go through the gain modifiers
     * (unpledged halving, mortal-enemy cap). Returns the delta actually applied.
     */
    public static float add(ServerPlayer player, ResourceKey<Faction> faction, float delta, AlignmentSource source) {
        return addInternal(player, faction, delta, source, true);
    }

    /** Sets the alignment (clamped and rounded). Fires {@code Changed} and {@code RankChanged}, not {@code Change}. */
    public static void set(ServerPlayer player, ResourceKey<Faction> faction, float value, AlignmentSource source) {
        Faction f = lookup(player, faction);
        if (f == null || f.fixedAlignment().isPresent()) {
            return;
        }
        float old = data(player).value(faction.location());
        float target = AlignmentRules.round(AlignmentRules.clamp(value));
        if (target == old) {
            return;
        }
        applyValue(player, faction, f, old, target, source);
        checkPledge(player);
    }

    private static float addInternal(
            ServerPlayer player, ResourceKey<Faction> faction, float delta, AlignmentSource source, boolean checkPledge) {
        Faction f = lookup(player, faction);
        if (f == null || f.fixedAlignment().isPresent()) {
            return 0.0f;
        }
        float old = data(player).value(faction.location());
        float change = delta;
        if (change > 0.0f && !source.is(AlignmentSource.COMMAND)) {
            change = applyGainModifiers(player, faction, old, change);
        }
        change = AlignmentRules.round(change);
        if (change == 0.0f) {
            return 0.0f;
        }
        AlignmentEvent.Change event = new AlignmentEvent.Change(player, faction, change, source);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return 0.0f;
        }
        change = AlignmentRules.round(event.getDelta());
        if (change == 0.0f) {
            return 0.0f;
        }
        float target = AlignmentRules.round(AlignmentRules.clamp(old + change));
        applyValue(player, faction, f, old, target, source);
        if (checkPledge) {
            checkPledge(player);
        }
        return AlignmentRules.round(target - old);
    }

    private static float applyGainModifiers(ServerPlayer player, ResourceKey<Faction> faction, float old, float gain) {
        Faction f = lookup(player, faction);
        Optional<ResourceKey<Faction>> pledged = pledge(player);
        Optional<Float> threshold = f == null ? Optional.empty() : AlignmentRules.pledgeThreshold(f);
        if (threshold.isPresent() && old >= threshold.get() && !pledged.equals(Optional.of(faction))) {
            gain /= 2.0f;
        }
        if (pledged.isPresent()
                && FactionRelations.relation(player.level().registryAccess(), pledged.get(), faction)
                        == FactionRelation.MORTAL_ENEMY) {
            gain = Math.min(gain, Math.max(0.0f, -old));
        }
        return gain;
    }

    /** Writes a value, notifies the client and fires Changed and RankChanged. */
    private static void applyValue(
            ServerPlayer player,
            ResourceKey<Faction> faction,
            Faction f,
            float old,
            float target,
            AlignmentSource source) {
        data(player).setValue(faction.location(), target);
        float actual = AlignmentRules.round(target - old);
        send(
                player,
                new AlignmentChangePayload(
                        faction.location(), target, actual, source.type(), source.pos()));
        NeoForge.EVENT_BUS.post(new AlignmentEvent.Changed(player, faction, old, target, source));
        FactionRank oldRank = AlignmentRules.rank(f, old);
        FactionRank newRank = AlignmentRules.rank(f, target);
        if (!oldRank.equals(newRank)) {
            NeoForge.EVENT_BUS.post(new AlignmentEvent.RankChanged(player, faction, oldRank, newRank));
            tell(
                    player,
                    Component.translatable(
                            "alignment.lotrrevival.rank_changed",
                            Factions.displayName(faction),
                            AlignmentRules.rankName(faction, newRank)));
            if (target > old) {
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
            }
        }
    }

    /**
     * Applies the kill rules for a kill of a victim of faction {@code victim} worth {@code baseValue}: the victim's own
     * faction and its allies and friends lose, its enemies and mortal enemies gain. Returns the applied deltas
     * (non-zero only). Pledge warning rules are not part of this; they follow in the death handler.
     */
    public static Map<ResourceKey<Faction>, Float> applyKill(
            ServerPlayer player,
            ResourceKey<Faction> victim,
            float baseValue,
            boolean civilian,
            AlignmentSource source) {
        Map<ResourceKey<Faction>, Float> applied = new LinkedHashMap<>();
        Registry<Faction> registry = registry(player);
        if (registry.get(victim) == null) {
            warnUnknown(victim);
            return applied;
        }
        float m = CoreConfig.get(CoreConfig.KILL_MULTIPLIER).floatValue();
        List<ResourceKey<Faction>> keys = new ArrayList<>(registry.registryKeySet());
        keys.sort(Comparator.comparingInt((ResourceKey<Faction> k) -> registry.get(k).sortOrder())
                .thenComparing(k -> k.location().toString()));
        for (ResourceKey<Faction> key : keys) {
            Faction x = registry.get(key);
            if (x == null || x.fixedAlignment().isPresent()) {
                continue;
            }
            float current = data(player).value(key.location());
            float change;
            if (key.equals(victim)) {
                change = -baseValue * m * AlignmentRules.penaltyScale(current);
            } else {
                FactionRelation relation = FactionRelations.relation(player.level().registryAccess(), victim, key);
                switch (relation) {
                    case ALLY -> change = -baseValue
                            * m
                            * CoreConfig.get(CoreConfig.ALLY_SHARE).floatValue()
                            * influence.multiplier(player, key)
                            * AlignmentRules.penaltyScale(current);
                    case FRIEND -> change = -baseValue
                            * m
                            * CoreConfig.get(CoreConfig.FRIEND_SHARE).floatValue()
                            * influence.multiplier(player, key)
                            * AlignmentRules.penaltyScale(current);
                    case ENEMY, MORTAL_ENEMY -> {
                        if (civilian && x.disapprovesCivilianKills()) {
                            continue;
                        }
                        float share = relation == FactionRelation.ENEMY
                                ? CoreConfig.get(CoreConfig.ENEMY_SHARE).floatValue()
                                : CoreConfig.get(CoreConfig.MORTAL_ENEMY_SHARE).floatValue();
                        change = baseValue * m * share * influence.multiplier(player, key);
                    }
                    default -> {
                        continue;
                    }
                }
            }
            float result = addInternal(player, key, change, source, false);
            if (result != 0.0f) {
                applied.put(key, result);
            }
        }
        checkPledge(player);
        return applied;
    }

    // ---------------------------------------------------------------- pledge

    public static PledgeResult tryPledge(ServerPlayer player, ResourceKey<Faction> faction) {
        return tryPledge(player, faction, false);
    }

    /** Pledges to a faction. {@code force} skips the requirements and the cooldown (commands). */
    public static PledgeResult tryPledge(ServerPlayer player, ResourceKey<Faction> faction, boolean force) {
        Faction f = lookup(player, faction);
        if (f == null) {
            return PledgeResult.UNKNOWN_FACTION;
        }
        Optional<Float> threshold = AlignmentRules.pledgeThreshold(f);
        if (threshold.isEmpty()) {
            return PledgeResult.NOT_PLEDGEABLE;
        }
        if (!force) {
            if (pledge(player).isPresent()) {
                return PledgeResult.ALREADY_PLEDGED;
            }
            if (data(player).pledgeCooldownTicks() > 0) {
                return PledgeResult.ON_COOLDOWN;
            }
            if (get(player, faction) < threshold.get()) {
                return PledgeResult.RANK_TOO_LOW;
            }
            if (hasPositiveMortalEnemy(player, faction)) {
                return PledgeResult.MORTAL_ENEMY_POSITIVE;
            }
        }
        if (NeoForge.EVENT_BUS.post(new PledgeEvent.Pledge(player, faction)).isCanceled()) {
            return PledgeResult.CANCELLED;
        }
        if (pledge(player).isPresent()) {
            breakPledge(player, PledgeBreakReason.COMMAND);
        }
        AlignmentData d = data(player);
        d.setPledge(faction.location());
        d.setPledgeWarningTicks(0);
        syncPledge(player);
        syncAll(player);
        tell(player, Component.translatable("pledge.lotrrevival.pledged", Factions.displayName(faction)));
        return PledgeResult.SUCCESS;
    }

    /** Ends the pledge (no-op when unpledged). Voluntary, killed_ally and below_rank breaks cost alignment and start a cooldown. */
    public static void breakPledge(ServerPlayer player, PledgeBreakReason reason) {
        Optional<ResourceKey<Faction>> pledged = pledge(player);
        if (pledged.isEmpty()) {
            return;
        }
        ResourceKey<Faction> faction = pledged.get();
        Faction f = lookup(player, faction);
        AlignmentData d = data(player);
        float before = d.value(faction.location());
        float threshold = f == null ? 100.0f : AlignmentRules.pledgeThreshold(f).orElse(100.0f);
        int cooldown = 0;
        float target = before;
        if (reason.hasConsequences() && f != null) {
            target = AlignmentRules.breakTarget(f, before, threshold);
            cooldown = AlignmentRules.cooldownTicks(before, threshold);
        }
        d.setPledge(null);
        d.setPledgeWarningTicks(0);
        if (cooldown > 0) {
            d.setPledgeCooldownTicks(cooldown);
            d.setPledgeCooldownFaction(faction.location());
        }
        float penalty = 0.0f;
        if (target < before) {
            penalty = -addInternal(player, faction, target - before, AlignmentSource.pledgeBreak(), false);
        }
        NeoForge.EVENT_BUS.post(new PledgeEvent.Break(player, faction, reason, penalty, cooldown));
        syncPledge(player);
        syncAll(player);
        tell(
                player,
                Component.translatable(
                        "pledge.lotrrevival.broken." + reason.getSerializedName(), Factions.displayName(faction)));
    }

    /** Breaks the pledge with {@code below_rank} when its requirements are no longer met. */
    static void checkPledge(ServerPlayer player) {
        Optional<ResourceKey<Faction>> pledged = pledge(player);
        if (pledged.isEmpty()) {
            return;
        }
        Faction f = lookup(player, pledged.get());
        Optional<Float> threshold = f == null ? Optional.empty() : AlignmentRules.pledgeThreshold(f);
        if (threshold.isEmpty()
                || get(player, pledged.get()) < threshold.get()
                || hasPositiveMortalEnemy(player, pledged.get())) {
            breakPledge(player, PledgeBreakReason.BELOW_RANK);
        }
    }

    private static boolean hasPositiveMortalEnemy(Player player, ResourceKey<Faction> faction) {
        Registry<Faction> registry = registry(player);
        for (ResourceKey<Faction> other : registry.registryKeySet()) {
            if (!other.equals(faction)
                    && FactionRelations.relation(player.level().registryAccess(), faction, other)
                            == FactionRelation.MORTAL_ENEMY
                    && get(player, other) > 0.0f) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pledge warning rules, run after the kill alignment: the first kill of a member of the pledged faction or one of
     * its allies warns, a second while the warning counter runs breaks the pledge.
     */
    static void pledgeKillCheck(ServerPlayer player, ResourceKey<Faction> victimFaction, LivingEntity victim) {
        Optional<ResourceKey<Faction>> pledged = pledge(player);
        if (pledged.isEmpty()) {
            return;
        }
        ResourceKey<Faction> faction = pledged.get();
        if (FactionRelations.relation(player.level().registryAccess(), faction, victimFaction)
                != FactionRelation.ALLY) {
            return;
        }
        AlignmentData d = data(player);
        if (d.pledgeWarningTicks() > 0) {
            breakPledge(player, PledgeBreakReason.KILLED_ALLY);
        } else {
            d.setPledgeWarningTicks(CoreConfig.get(CoreConfig.PLEDGE_WARNING_TICKS));
            NeoForge.EVENT_BUS.post(new PledgeEvent.Warned(player, faction, victim));
            tell(player, Component.translatable("pledge.lotrrevival.warning", Factions.displayName(faction)));
        }
    }

    // ---------------------------------------------------------------- timers

    /** One tick of online time: pledge warning, pledge cooldown and the mortal-enemy drain timer. */
    public static void tickTimers(ServerPlayer player) {
        AlignmentData d = data(player);
        if (d.pledgeWarningTicks() > 0) {
            d.setPledgeWarningTicks(d.pledgeWarningTicks() - 1);
        }
        if (d.pledgeCooldownTicks() > 0) {
            d.setPledgeCooldownTicks(d.pledgeCooldownTicks() - 1);
            if (d.pledgeCooldownTicks() == 0) {
                d.setPledgeCooldownFaction(null);
                syncAll(player);
                tell(player, Component.translatable("pledge.lotrrevival.cooldown_ended"));
            }
        }
        if (CoreConfig.get(CoreConfig.MORTAL_ENEMY_DRAIN)) {
            int timer = d.drainTimer();
            if (timer <= 0) {
                timer = CoreConfig.get(CoreConfig.DRAIN_INTERVAL_TICKS);
            }
            timer--;
            if (timer <= 0) {
                d.setDrainTimer(0);
                drain(player);
            } else {
                d.setDrainTimer(timer);
            }
        }
    }

    /** Every faction in a mortal-enemy pair the player is positive with on both sides loses the drain amount, once. */
    static int drain(ServerPlayer player) {
        Registry<Faction> registry = registry(player);
        List<ResourceKey<Faction>> positive = new ArrayList<>();
        for (ResourceKey<Faction> key : registry.registryKeySet()) {
            Faction f = registry.get(key);
            if (f != null && f.fixedAlignment().isEmpty() && !f.hidden() && get(player, key) > 0.0f) {
                positive.add(key);
            }
        }
        Set<ResourceKey<Faction>> drained = new HashSet<>();
        for (ResourceKey<Faction> a : positive) {
            for (ResourceKey<Faction> b : positive) {
                if (!a.equals(b)
                        && FactionRelations.relation(player.level().registryAccess(), a, b)
                                == FactionRelation.MORTAL_ENEMY) {
                    drained.add(a);
                    drained.add(b);
                }
            }
        }
        if (drained.isEmpty()) {
            return 0;
        }
        float amount = CoreConfig.get(CoreConfig.DRAIN_AMOUNT).floatValue();
        int count = 0;
        List<ResourceKey<Faction>> ordered = new ArrayList<>(drained);
        ordered.sort(Comparator.comparing(k -> k.location().toString()));
        for (ResourceKey<Faction> key : ordered) {
            float loss = Math.min(amount, get(player, key));
            if (loss > 0.0f && addInternal(player, key, -loss, AlignmentSource.drain(), false) != 0.0f) {
                count++;
            }
        }
        checkPledge(player);
        if (count > 0) {
            AlignmentData d = data(player);
            if (!d.drainNoticeShown()) {
                d.setDrainNoticeShown(true);
                tell(player, Component.translatable("alignment.lotrrevival.drain_first"));
            }
            send(player, new DrainNoticePayload(count));
        }
        return count;
    }

    // ---------------------------------------------------------------- reset / sync

    /** Zeroes one faction or all of them. Resetting everything (or the pledged faction) ends the pledge silently. */
    public static void reset(ServerPlayer player, @Nullable ResourceKey<Faction> only) {
        AlignmentData d = data(player);
        Optional<ResourceKey<Faction>> pledged = pledge(player);
        if (pledged.isPresent() && (only == null || only.equals(pledged.get()))) {
            breakPledge(player, PledgeBreakReason.COMMAND);
        }
        for (ResourceLocation id : new ArrayList<>(d.values().keySet())) {
            if (only != null && !only.location().equals(id)) {
                continue;
            }
            ResourceKey<Faction> key = ResourceKey.create(ModDataRegistries.FACTION, id);
            Faction f = registry(player).get(key);
            if (f == null) {
                continue; // keep entries of removed factions
            }
            float old = d.value(id);
            d.setValue(id, 0.0f);
            NeoForge.EVENT_BUS.post(new AlignmentEvent.Changed(player, key, old, 0.0f, AlignmentSource.command()));
            FactionRank oldRank = AlignmentRules.rank(f, old);
            FactionRank newRank = AlignmentRules.rank(f, 0.0f);
            if (!oldRank.equals(newRank)) {
                NeoForge.EVENT_BUS.post(new AlignmentEvent.RankChanged(player, key, oldRank, newRank));
            }
        }
        syncAll(player);
    }

    /** Sends the full snapshot to the player (login, respawn, dimension change, reset). */
    public static void syncAll(ServerPlayer player) {
        AlignmentData d = data(player);
        Registry<Faction> registry = registry(player);
        Map<ResourceLocation, Float> values = new HashMap<>();
        d.values().forEach((id, v) -> {
            if (registry.containsKey(id)) {
                values.put(id, v);
            }
        });
        Optional<ResourceLocation> pledgeId = pledge(player).map(ResourceKey::location);
        send(
                player,
                new AlignmentSyncPayload(
                        player.getUUID(), values, pledgeId, d.pledgeCooldownTicks(), d.pledgeCooldownFaction()));
    }

    /** Sends the player's pledge to the player and everyone tracking them. */
    public static void syncPledge(ServerPlayer player) {
        if (!canReceive(player, PledgeSyncPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player, new PledgeSyncPayload(player.getUUID(), pledge(player).map(ResourceKey::location)));
    }

    // ---------------------------------------------------------------- helpers

    static AlignmentData data(Player player) {
        return player.getData(AlignmentAttachments.ALIGNMENT);
    }

    private static Registry<Faction> registry(Player player) {
        return player.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
    }

    @Nullable
    private static Faction lookup(Player player, ResourceKey<Faction> faction) {
        Faction f = registry(player).get(faction);
        if (f == null) {
            warnUnknown(faction);
        }
        return f;
    }

    private static void warnUnknown(ResourceKey<Faction> faction) {
        if (WARNED_UNKNOWN.add(faction.location())) {
            LOTRRevival.LOGGER.warn("Alignment API called with unknown faction {}", faction.location());
        }
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (canReceive(player, payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** False for players without a negotiated connection (fake players, embedded test connections). */
    private static boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && !player.isFakePlayer() && player.connection.hasChannel(type);
    }

    private static void tell(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendSystemMessage(message);
        }
    }
}
