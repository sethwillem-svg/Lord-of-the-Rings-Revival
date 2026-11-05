package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.api.CombatHooks;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionResult;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.initializer.NpcEntityBuilder;
import net.sevenstars.middleearth.resources.datas.common.NpcRank;
import net.sevenstars.middleearth.resources.datas.factions.Faction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nemesis bodies (spec Bodies and persistence, Ranks): spawning with {@code NpcEntityBuilder}, rank modifiers, gear, name,
 * bodyguards, boss bar, the 2 s body check (retreat rules), stale-body removal and name-tag refusal.
 */
public final class Manifestation {
    public static final ResourceLocation RANK_MODIFIER = LotrRevival.id("nemesis_rank");
    private static final int BODY_CHECK_TICKS = 40;

    /** Live state of one body (transient; rebuilt never: a restart sends every nemesis back to AT_LARGE). */
    public static final class Body {
        public final UUID recordId;
        public NpcEntity entity;
        public final List<NpcEntity> guards = new ArrayList<>();
        public long retreatAtTick = -1;
        public String retreatReason = "";
        public long lastHolderNearTick;

        Body(UUID recordId, NpcEntity entity) {
            this.recordId = recordId;
            this.entity = entity;
        }
    }

    private static final Map<UUID, Body> BODIES = new HashMap<>();
    /** Game tests: override the "no player near" distance and delay (blocks, ticks); -1 uses the config. */
    public static int testRetreatDistance = -1, testRetreatDelayTicks = -1;

    private Manifestation() {}

    public static @Nullable Body body(UUID recordId) {
        return BODIES.get(recordId);
    }

    public static int bodyCount() {
        return BODIES.size();
    }

    // ---------------------------------------------------------------- spawning

    /**
     * Spawns a body for an AT_LARGE record at {@code pos}. Null when the record cannot manifest (status, cap, disabled faction,
     * unknown type); the reason is logged.
     */
    public static @Nullable NpcEntity spawn(ServerLevel level, NemesisRecord r, BlockPos pos, String reason) {
        MinecraftServer server = level.getServer();
        NemesisSavedData data = NemesisSavedData.get(server);
        if (r.status != NemesisRecord.Status.AT_LARGE) {
            LotrLog.dbg("nemesis", "manifest refused: id={} status={}", r.shortId(), r.status);
            return null;
        }
        int cap = NemesisConfig.i(NemesisConfig.MAX_MANIFEST);
        if (data.manifestCount() >= cap) {
            LotrLog.warn("nemesis", "max_manifest {} reached: id={} not spawned ({})", cap, r.shortId(), reason);
            return null;
        }
        Faction f = Sides.faction(level, r.faction);
        if (f == null || !MeRanks.nemesisFaction(f)) {
            LotrLog.warn("nemesis", "record {} of unknown or disabled faction {}: not spawned", r.shortId(), r.faction);
            return null;
        }
        NpcEntity npc = new NpcEntityBuilder(level, pos).withNpcType(r.npcType).build();
        if (npc == null || npc.getNpcType() == null) {
            LotrLog.warn("nemesis", "record {}: Middle-earth could not build NPC type {}", r.shortId(), r.npcType);
            return null;
        }
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, NemesisRandom.nextInt(360), 0);
        if (!r.gear.isEmpty()) r.gear.forEach((slot, stack) -> npc.setItemSlot(slot, stack.copy()));
        // TODO(spec §Ranks gear): from Commander up, re-roll once from Faction.getRandomGear(level, LEADER, race) and keep it
        r.manifestation++;
        r.status = NemesisRecord.Status.MANIFEST;
        r.lastDimension = level.dimension().location();
        r.lastPos = pos;
        r.lastSeenTick = level.getGameTime();
        resetManifestationState(r);
        NemesisLink.set(npc, new NemesisLink(r.id, r.manifestation));
        data.bindBody(r, npc.getUUID());   // bound before join so the join check accepts it
        prepareBody(npc, r);
        level.addFreshEntity(npc);
        npc.setHealth(npc.getMaxHealth());
        Body b = new Body(r.id, npc);
        b.lastHolderNearTick = level.getGameTime();
        BODIES.put(r.id, b);
        spawnGuards(level, r, b, f);
        NemesisBossBar.create(npc, r);
        LotrLog.dbg("nemesis", "manifest: id={} name=\"{}\" rank={} body={} at {},{},{} dim={} manifestation={} guards={} reason={} tick={}",
                r.shortId(), r.displayName(), r.rank, LotrLog.id(npc.getUUID()), pos.getX(), pos.getY(), pos.getZ(),
                r.lastDimension, r.manifestation, b.guards.size(), reason, level.getGameTime());
        return npc;
    }

    /** Turns a living NPC into the body of a freshly risen record (it keeps its gear; the record snapshots it). */
    public static void adopt(NpcEntity npc, NemesisRecord r, NemesisSavedData data) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = npc.getItemBySlot(slot);
            if (!s.isEmpty()) r.gear.put(slot, s.copy());
        }
        r.manifestation++;
        r.status = NemesisRecord.Status.MANIFEST;
        r.lastDimension = npc.level().dimension().location();
        r.lastPos = npc.blockPosition();
        r.lastSeenTick = npc.level().getGameTime();
        resetManifestationState(r);
        NemesisLink.set(npc, new NemesisLink(r.id, r.manifestation));
        data.bindBody(r, npc.getUUID());
        prepareBody(npc, r);
        npc.setHealth(npc.getMaxHealth());
        Body b = new Body(r.id, npc);
        b.lastHolderNearTick = npc.level().getGameTime();
        BODIES.put(r.id, b);
        NemesisBossBar.create(npc, r);
    }

    private static void resetManifestationState(NemesisRecord r) {
        r.damagedBy.clear();
        r.escapeTried = false;
        r.warHornUsed = false;
        r.noPlayerSinceTick = -1;
    }

    private static void prepareBody(NpcEntity npc, NemesisRecord r) {
        npc.setPersistenceRequired();
        npc.setCustomName(Component.literal(r.displayName()));
        npc.setCustomNameVisible(true);
        applyRank(npc, r);
        applyCombatHooks(npc, r);
    }

    // ---------------------------------------------------------------- rank modifiers

    /** One modifier per attribute with the fixed id, replaced on every call, never stacked. */
    public static void applyRank(NpcEntity npc, NemesisRecord r) {
        Rank rank = r.rank;
        boolean unshakeable = TraitEffects.has(r, "unshakeable");
        setModifier(npc, Attributes.MAX_HEALTH, rank.health - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(npc, Attributes.ATTACK_DAMAGE, rank.damage - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(npc, Attributes.ARMOR, rank.armour, AttributeModifier.Operation.ADD_VALUE);
        setModifier(npc, Attributes.KNOCKBACK_RESISTANCE, unshakeable ? 1.0 : rank.knockback, AttributeModifier.Operation.ADD_VALUE);
        setModifier(npc, Attributes.MOVEMENT_SPEED, 0.03 * rank.ordinal(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (npc.getHealth() > npc.getMaxHealth()) npc.setHealth(npc.getMaxHealth());
        LotrLog.dbg("nemesis", "rank modifiers: body={} id={} rank={} max_health={} attack={} armour={}", LotrLog.id(npc.getUUID()),
                r.shortId(), rank, npc.getMaxHealth(), value(npc, Attributes.ATTACK_DAMAGE), value(npc, Attributes.ARMOR));
    }

    private static void setModifier(NpcEntity npc, Holder<Attribute> attr, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = npc.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(RANK_MODIFIER);
        if (amount != 0) inst.addPermanentModifier(new AttributeModifier(RANK_MODIFIER, amount, op));
    }

    private static double value(NpcEntity npc, Holder<Attribute> attr) {
        AttributeInstance inst = npc.getAttribute(attr);
        return inst == null ? 0 : inst.getValue();
    }

    private static void applyCombatHooks(NpcEntity npc, NemesisRecord r) {
        float heavy = (float) NemesisConfig.d(NemesisConfig.HEAVY_CHANCE);
        float stagger = 0.5f;
        NemesisTraits.Trait crushing = TraitEffects.find(r, "crushing_blow");
        if (crushing != null) heavy = (float) crushing.num("heavy_chance", 0.5);
        NemesisTraits.Trait tele = TraitEffects.find(r, "telegraphs");
        if (tele != null) {
            heavy = 0f;
            stagger = (float) tele.num("stagger", 2.0);
        }
        CombatHooks.get().heavyChance(npc, heavy);
        CombatHooks.get().staggerMultiplier(npc, stagger);
        NemesisTraits.Trait parry = TraitEffects.find(r, "parry_master");
        if (parry != null) CombatHooks.get().npcParry(npc, (float) parry.num("bonus", 0.2), (float) parry.num("max", 0.6),
                (int) parry.num("cooldown", 20));
        LotrLog.dbg("nemesis", "combat hooks: body={} heavy={} stagger=x{} parry_master={}", LotrLog.id(npc.getUUID()), heavy, stagger,
                parry != null);
    }

    public static void onRankChanged(MinecraftServer server, NemesisRecord r) {
        Body b = BODIES.get(r.id);
        if (b == null || b.entity.isRemoved()) return;
        applyRank(b.entity, r);
        applyCombatHooks(b.entity, r);
        NemesisBossBar.refresh(b.entity, r);
    }

    // ---------------------------------------------------------------- bodyguards

    private static void spawnGuards(ServerLevel level, NemesisRecord r, Body b, Faction f) {
        int n = r.rank.bodyguards + r.extraBodyguards;
        if (n <= 0) return;
        r.extraBodyguards = 0;
        List<ResourceLocation> pool = MeRanks.pool(f, NpcRank.SOLDIER, NpcRank.KNIGHT);
        if (pool.isEmpty()) {
            LotrLog.warn("nemesis", "faction {} has no SOLDIER/KNIGHT pool: no bodyguards for {}", r.faction, r.shortId());
            return;
        }
        for (int i = 0; i < n; i++) {
            NpcEntity g = spawnHelper(level, NemesisRandom.pick(pool), b.entity.blockPosition());
            if (g != null) b.guards.add(g);
        }
    }

    /** One NPC of this type next to {@code near}, tagged so it never rolls a rise (bodyguards, war-horn soldiers). */
    public static @Nullable NpcEntity spawnHelper(ServerLevel level, ResourceLocation type, BlockPos near) {
        BlockPos p = near.offset(NemesisRandom.between(-3, 3), 0, NemesisRandom.between(-3, 3));
        p = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
        NpcEntity g = new NpcEntityBuilder(level, p).withNpcType(type).build();
        if (g == null) return null;
        g.addTag(MeRanks.BODYGUARD_TAG);
        level.addFreshEntity(g);
        return g;
    }

    // ---------------------------------------------------------------- retreat

    public static void scheduleRetreat(NemesisRecord r, long atTick, String reason) {
        Body b = BODIES.get(r.id);
        if (b == null) return;
        b.retreatAtTick = atTick;
        b.retreatReason = reason;
        LotrLog.dbg("nemesis", "retreat scheduled: id={} at tick {} reason={}", r.shortId(), atTick, reason);
    }

    /** Removes the body in a puff of smoke; the record goes back to AT_LARGE. */
    public static void retreat(MinecraftServer server, NemesisRecord r, String reason) {
        Body b = BODIES.remove(r.id);
        NemesisSavedData data = NemesisSavedData.get(server);
        if (b != null) {
            NpcEntity e = b.entity;
            if (!e.isRemoved() && e.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
                r.lastPos = e.blockPosition();
                e.discard();
            }
            for (NpcEntity g : b.guards) if (!g.isRemoved()) g.discard();
            NemesisBossBar.remove(e.getUUID());
        }
        if (r.status == NemesisRecord.Status.MANIFEST) r.status = NemesisRecord.Status.AT_LARGE;
        r.ambushTarget = null;
        r.eventId = null;
        data.bindBody(r, null);
        NemesisStats.retreats++;
        LotrLog.dbg("nemesis", "retreat: id={} name=\"{}\" reason={} pos={},{},{} tick={}", r.shortId(), r.displayName(), reason,
                r.lastPos.getX(), r.lastPos.getY(), r.lastPos.getZ(), server.overworld().getGameTime());
    }

    /** The body died (DeathRules): forget it without smoke; guards stay to fight and are removed by their own rules. */
    public static void bodyDied(MinecraftServer server, NemesisRecord r) {
        Body b = BODIES.remove(r.id);
        if (b != null) {
            NemesisBossBar.remove(b.entity.getUUID());
            for (NpcEntity g : b.guards) if (!g.isRemoved()) g.discard();   // TODO(spec §Bodies): let guards fight on, remove 30 s later
        }
        NemesisSavedData.get(server).bindBody(r, null);
    }

    // ---------------------------------------------------------------- body check (every 2 s)

    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick % BODY_CHECK_TICKS != 0 || BODIES.isEmpty()) return;
        NemesisSavedData data = NemesisSavedData.get(server);
        int dist = testRetreatDistance > 0 ? testRetreatDistance : NemesisConfig.i(NemesisConfig.RETREAT_DISTANCE);
        long delay = testRetreatDelayTicks > 0 ? testRetreatDelayTicks : NemesisConfig.i(NemesisConfig.RETREAT_DELAY_SECONDS) * 20L;
        Iterator<Map.Entry<UUID, Body>> it = new ArrayList<>(BODIES.entrySet()).iterator();
        while (it.hasNext()) {
            Body b = it.next().getValue();
            NemesisRecord r = data.find(b.recordId);
            NemesisStats.bodyChecks++;
            if (r == null || !r.alive()) {
                if (!b.entity.isRemoved()) b.entity.discard();
                BODIES.remove(b.recordId);
                continue;
            }
            NpcEntity e = b.entity;
            if (e.isRemoved()) {
                retreat(server, r, e.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK ? "chunk unloaded" : "body removed");
                continue;
            }
            Level level = e.level();
            long gameTime = level.getGameTime();
            r.lastPos = e.blockPosition();
            r.lastSeenTick = gameTime;
            if (level.getDifficulty() == Difficulty.PEACEFUL) {
                retreat(server, r, "peaceful");
                continue;
            }
            if (b.retreatAtTick >= 0 && gameTime >= b.retreatAtTick) {
                retreat(server, r, b.retreatReason);
                continue;
            }
            if (r.eventId == null) {
                if (r.ambushTarget != null) {
                    // ambush leave rule: no grudge holder within 64 blocks for 30 s
                    boolean near = false;
                    for (ServerPlayer p : ((ServerLevel) level).players())
                        if (r.activeGrudge(p.getUUID()) > 0 && p.distanceToSqr(e) < 64 * 64) near = true;
                    if (near) b.lastHolderNearTick = gameTime;
                    else if (gameTime - b.lastHolderNearTick > 600) {
                        retreat(server, r, "ambush: no grudge holder within 64 for 30 s");
                        continue;
                    }
                } else {
                    boolean anyone = level.getNearestPlayer(e, dist) != null;
                    if (anyone) r.noPlayerSinceTick = -1;
                    else if (r.noPlayerSinceTick < 0) r.noPlayerSinceTick = gameTime;
                    else if (gameTime - r.noPlayerSinceTick >= delay) {
                        retreat(server, r, "no player within " + dist + " for " + delay / 20 + " s");
                        continue;
                    }
                }
            }
            if (TraitEffects.has(r, "unshakeable") && e.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            TraitEffects.bodyTick(e, r);
            for (NpcEntity g : b.guards) {
                if (g.isRemoved()) continue;
                if (e.getTarget() != null && g.getTarget() == null) g.setTarget(e.getTarget());
                if (g.distanceToSqr(e) > 12 * 12) g.getNavigation().moveTo(e, 1.1);   // TODO(spec §Ranks): a real follow goal
            }
            NemesisBossBar.update(e, r);
        }
    }

    // ---------------------------------------------------------------- stale bodies, name tags, server start

    /** A body that loads with an older manifestation number, or of a dead or at-large record, is removed silently. */
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        NemesisLink link = NemesisLink.of(e.getEntity());
        if (link == null || e.getEntity().getServer() == null) return;
        NemesisRecord r = NemesisSavedData.get(e.getEntity().getServer()).find(link.id());
        String why = null;
        if (r == null) why = "unknown record";
        else if (!r.alive()) why = "record DEAD";
        else if (r.manifestation != link.manifestation()) why = "old manifestation " + link.manifestation() + " < " + r.manifestation;
        else if (r.body == null || !r.body.equals(e.getEntity().getUUID())) why = "record has no body or another body";
        if (why != null) {
            e.setCanceled(true);
            LotrLog.dbg("nemesis", "stale body removed: entity={} id={} reason={} loaded_from_disk={}", LotrLog.id(e.getEntity().getUUID()),
                    LotrLog.id(link.id()), why, e.loadedFromDisk());
        }
    }

    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!e.getItemStack().is(Items.NAME_TAG)) return;
        Entity t = e.getTarget();
        if (NemesisLink.of(t) != null || t.hasData(NemesisLink.SERGEANT)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            if (!e.getLevel().isClientSide) e.getEntity().displayClientMessage(
                    Component.translatable("lotr_revival.nemesis.no_rename").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** On server start nothing is manifest: bodies saved in chunks become stale and are removed when they load. */
    public static void onServerStarted(MinecraftServer server) {
        BODIES.clear();
        NemesisSavedData data = NemesisSavedData.get(server);
        int n = 0;
        for (NemesisRecord r : data.records.values()) {
            if (r.status == NemesisRecord.Status.MANIFEST) {
                r.status = NemesisRecord.Status.AT_LARGE;
                data.bindBody(r, null);
                n++;
            }
        }
        if (n > 0) LotrLog.info("nemesis", "server start: {} manifest record(s) set back to AT_LARGE", n);
    }

    public static @Nullable ServerLevel levelOf(MinecraftServer server, NemesisRecord r) {
        if (r.lastDimension == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, r.lastDimension));
    }
}
