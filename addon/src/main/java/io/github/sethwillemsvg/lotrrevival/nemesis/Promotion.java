package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.combat.NpcRaces;
import io.github.sethwillemsvg.lotrrevival.core.api.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.resources.datas.common.NpcRank;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Rises (spec Renown and grudges, kills only): a nemesis-eligible NPC that kills a player whose enemy it is, or a hired unit of
 * such a player, may rise; an existing nemesis only gains renown and grudge.
 */
public final class Promotion {
    private Promotion() {}

    public static void onDeath(LivingDeathEvent e) {
        try {
            LivingEntity victim = e.getEntity();
            if (victim.level().isClientSide || victim.getServer() == null) return;
            Entity killer = e.getSource().getEntity();   // melee attacker or the shooter of a projectile
            if (!(killer instanceof NpcEntity npc)) return;
            if (victim instanceof ServerPlayer p) considerPlayerKill(npc, p);
            else if (HiredUnits.isUnit(victim)) considerUnitKill(npc, victim);
        } catch (RuntimeException ex) {
            LotrLog.error("nemesis", "rise listener failed", ex);
        }
    }

    /** Survival or adventure, not fake, not Peaceful. Uses the real game mode (mock test players report creative). */
    public static @Nullable String whyPlayerExcluded(ServerPlayer p) {
        if (p instanceof FakePlayer) return "fake player";
        GameType gm = p.gameMode.getGameModeForPlayer();
        if (gm != GameType.SURVIVAL && gm != GameType.ADVENTURE) return "game mode " + gm;
        if (p.level().getDifficulty() == Difficulty.PEACEFUL) return "peaceful";
        return null;
    }

    /** Returns the record that rose or gained renown, or null. */
    public static @Nullable NemesisRecord considerPlayerKill(NpcEntity npc, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        long tick = player.level().getGameTime();
        String excluded = whyPlayerExcluded(player);
        if (excluded != null) {
            LotrLog.dbg("nemesis", "rise roll skipped: npc={} victim=player:{} reason={}", LotrLog.id(npc.getUUID()), LotrLog.id(player.getUUID()), excluded);
            return null;
        }
        ResourceLocation faction = npc.getFactionIdentifier();
        boolean enemy = faction != null && Sides.isEnemy(npc.level(), faction, player);
        NemesisRecord existing = NemesisApi.bodyOf(npc);
        if (existing != null) {
            if (!enemy) return null;
            Renown.add(server, existing, NemesisConfig.i(NemesisConfig.RENOWN_PLAYER_KILL), "killed player " + LotrLog.id(player.getUUID()));
            existing.addGrudge(player.getUUID(), 3, tick);
            if (player.getUUID().equals(existing.ambushTarget)) {
                Renown.add(server, existing, NemesisConfig.i(NemesisConfig.RENOWN_AMBUSH_KILL), "killed its ambush target");
                existing.addGrudge(player.getUUID(), 3, tick);
                Ambush.onAmbushEnded(server, existing, player.getUUID(), false);
                Manifestation.scheduleRetreat(existing, tick + 100, "ambush target died");
            }
            existing.knowsStrengths.add(player.getUUID());
            NemesisSavedData.get(server).setDirty();
            return existing;
        }
        NpcRank rank = MeRanks.rankOf(npc);
        double chance = NemesisConfig.d(NemesisConfig.PLAYER_KILL_CHANCE) + (rank == NpcRank.LEADER ? NemesisConfig.d(NemesisConfig.LEADER_BONUS) : 0);
        return roll(npc, "player:" + LotrLog.id(player.getUUID()), enemy, chance, player.getUUID(), 3,
                NemesisConfig.i(NemesisConfig.RENOWN_PLAYER_KILL), player, player);
    }

    public static @Nullable NemesisRecord considerUnitKill(NpcEntity npc, LivingEntity unit) {
        MinecraftServer server = unit.getServer();
        Optional<UUID> owner = HiredUnits.owner(unit);
        if (owner.isEmpty() || server == null) return null;
        int level = Math.max(1, HiredUnits.level(unit));
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner.get());
        String victim = "unit:" + LotrLog.id(unit.getUUID()) + "(lvl " + level + ", owner " + LotrLog.id(owner.get()) + ")";
        if (ownerPlayer == null) {
            LotrLog.dbg("nemesis", "rise roll skipped: npc={} victim={} reason=owner offline", LotrLog.id(npc.getUUID()), victim);
            return null;
        }
        String excluded = whyPlayerExcluded(ownerPlayer);
        if (excluded != null) {
            LotrLog.dbg("nemesis", "rise roll skipped: npc={} victim={} reason=owner {}", LotrLog.id(npc.getUUID()), victim, excluded);
            return null;
        }
        ResourceLocation faction = npc.getFactionIdentifier();
        boolean enemy = faction != null && Sides.isEnemy(npc.level(), faction, ownerPlayer);
        int renown = NemesisConfig.i(NemesisConfig.RENOWN_UNIT_KILL) + NemesisConfig.i(NemesisConfig.RENOWN_UNIT_PER_LEVEL) * (level - 1);
        NemesisRecord existing = NemesisApi.bodyOf(npc);
        if (existing != null) {
            if (!enemy) return null;
            Renown.add(server, existing, renown, "killed " + victim);
            existing.addGrudge(owner.get(), 1, unit.level().getGameTime());
            return existing;
        }
        double chance = unitKillChance(level);
        return roll(npc, victim, enemy, chance, owner.get(), 1, renown, unit, ownerPlayer);
    }

    public static double unitKillChance(int unitLevel) {
        return NemesisConfig.d(NemesisConfig.UNIT_KILL_CHANCE) + NemesisConfig.d(NemesisConfig.UNIT_KILL_PER_LEVEL) * (Math.max(1, unitLevel) - 1);
    }

    private static @Nullable NemesisRecord roll(NpcEntity npc, String victimDesc, boolean enemy, double chance, UUID grudgePlayer,
                                                int grudge, int renown, Entity victim, ServerPlayer tell) {
        NemesisStats.rolls++;
        String why = MeRanks.whyNotEligible(npc);
        String prefix = "rise roll: npc=" + LotrLog.id(npc.getUUID()) + " type=" + npc.getNpcTypeIdentifier() + " rank=" + MeRanks.rankOf(npc)
                + " victim=" + victimDesc + " enemy=" + enemy;
        if (!enemy || why != null) {
            LotrLog.dbg("nemesis", "{} -> not eligible ({})", prefix, why != null ? why : "not an enemy");
            return null;
        }
        double r = NemesisRandom.roll();
        if (r >= chance) {
            LotrLog.dbg("nemesis", "{} chance={} roll={} -> no rise", prefix, String.format("%.2f", chance), String.format("%.2f", r));
            return null;
        }
        NemesisRecord rec = rise(npc, grudgePlayer, grudge, renown, victim, tell);
        LotrLog.dbg("nemesis", "{} chance={} roll={} -> risen id={} name=\"{}\"", prefix, String.format("%.2f", chance),
                String.format("%.2f", r), rec.shortId(), rec.displayName());
        return rec;
    }

    /** Creates the record, makes this NPC its body (retreats 3 s later), posts {@link NemesisEvents.Risen}. */
    public static NemesisRecord rise(NpcEntity npc, UUID grudgePlayer, int grudge, int renown, @Nullable Entity victim, @Nullable ServerPlayer tell) {
        MinecraftServer server = npc.getServer();
        NemesisSavedData data = NemesisSavedData.get(server);
        long tick = npc.level().getGameTime();
        NemesisRecord r = NemesisFactory.create((net.minecraft.server.level.ServerLevel) npc.level(), npc.getFactionIdentifier(),
                npc.getNpcTypeIdentifier(), raceOf(npc));
        Sides.Side side = Sides.sideOfFaction(npc.level(), r.faction);
        r.addGrudge(grudgePlayer, grudge, tick);
        r.knowsStrengths.add(grudgePlayer);
        Manifestation.adopt(npc, r, data);
        Manifestation.scheduleRetreat(r, tick + 60, "freshly risen");
        NemesisStats.rises++;
        Renown.add(server, r, renown, "rise");
        if (tell != null) {
            Component faction = factionName(npc);
            Component line = side == Sides.Side.GOOD
                    ? Component.translatable("lotr_revival.nemesis.risen.hunt", r.displayName(), faction)
                    : Component.translatable("lotr_revival.nemesis.risen", r.displayName(), faction,
                    Component.translatable("lotr_revival.nemesis.rank." + r.rank.key()));
            tell.sendSystemMessage(line.copy().withStyle(ChatFormatting.RED));
        }
        LotrLog.dbg("nemesis", "risen: id={} name=\"{}\" faction={} type={} race={} strengths={} weakness={} body={} at {},{},{} tick={}",
                r.shortId(), r.displayName(), r.faction, r.npcType, r.race, r.strengths, r.weakness, LotrLog.id(npc.getUUID()),
                npc.getBlockX(), npc.getBlockY(), npc.getBlockZ(), tick);
        NeoForge.EVENT_BUS.post(new NemesisEvents.Risen(r, victim));
        return r;
    }

    public static String raceOf(NpcEntity npc) {
        try {
            if (npc.getNpcType() != null && npc.getNpcType().getRace() != null) return npc.getNpcType().getRace().getPath();
        } catch (RuntimeException ignored) {
        }
        return NpcRaces.of(npc).equals("man") ? "human" : NpcRaces.of(npc);
    }

    public static Component factionName(NpcEntity npc) {
        var f = Sides.faction(npc.level(), npc.getFactionIdentifier());
        return f == null ? Component.literal(String.valueOf(npc.getFactionIdentifier())) : f.tryGetShortName();
    }
}
