package io.github.sethwillemsvg.lotrrevival.nemesis.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.api.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.core.api.ReputationHooks;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nemesis.Ambush;
import io.github.sethwillemsvg.lotrrevival.nemesis.DamageKind;
import io.github.sethwillemsvg.lotrrevival.nemesis.Manifestation;
import io.github.sethwillemsvg.lotrrevival.nemesis.MeRanks;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisBossBar;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisFactory;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisLink;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisPlayerData;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRecord;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisSavedData;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisTraits;
import io.github.sethwillemsvg.lotrrevival.nemesis.Politics;
import io.github.sethwillemsvg.lotrrevival.nemesis.Promotion;
import io.github.sethwillemsvg.lotrrevival.nemesis.Rank;
import io.github.sethwillemsvg.lotrrevival.nemesis.Renown;
import io.github.sethwillemsvg.lotrrevival.nemesis.SergeantData;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sergeants;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sides;
import io.github.sethwillemsvg.lotrrevival.nemesis.TraitEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.BRIGAND;
import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.GONDOR;
import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.MORDOR;
import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.me;

/**
 * Nemesis acceptance tests (specs/addon/nemesis-events.md). Each test is its own batch so the shared saved data and the forced
 * random rolls of one test never meet another's.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NemesisTests {
    private static final ResourceLocation WARRIOR = me("mordor.warrior"), CAPTAIN = me("mordor.captain"), SNAGA = me("mordor.snaga"),
            MILITIA = me("mordor.militia"), VETERAN = me("mordor.veteran"), THUG = me("brigand.thug"), SOLDIER = me("gondor.soldier");
    private static final BlockPos P = new BlockPos(1, 1, 1);

    private NemesisTests() {}

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static NemesisRecord record(GameTestHelper h, ResourceLocation faction, ResourceLocation type, int renown) {
        NemesisRecord r = NemesisFactory.create(h.getLevel(), faction, type, NemesisFactory.raceOfType(h.getLevel(), type));
        r.renown = renown;
        Renown.reseat(server(h), faction, "test");
        return r;
    }

    private static NpcEntity body(GameTestHelper h, NemesisRecord r, BlockPos rel) {
        NpcEntity e = Manifestation.spawn(h.getLevel(), r, h.absolutePos(rel), "test");
        h.assertTrue(e != null, "body of " + r.shortId() + " did not spawn");
        e.setNoAi(true);
        return e;
    }

    private static DamageSource source(GameTestHelper h, ResourceKey<DamageType> key) {
        Holder<DamageType> type = h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new DamageSource(type);
    }

    private static void cleanup(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) NemesisTestPlayers.remove(p);
        NemesisTestPlayers.clear(server(h));
    }

    // ------------------------------------------------------------------ sides

    @GameTest(template = "empty", batch = "nemesis_sides")
    public static void sides(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[4];
        NemesisTestPlayers.run(h, "sides", () -> {
            ServerPlayer good = ps[0] = NemesisTestPlayers.player(h, GONDOR, P);
            ServerPlayer evil = ps[1] = NemesisTestPlayers.player(h, MORDOR, P);
            ServerPlayer brig = ps[2] = NemesisTestPlayers.player(h, BRIGAND, P);
            ServerPlayer none = ps[3] = NemesisTestPlayers.player(h, null, P);
            h.assertTrue(Sides.side(good) == Sides.Side.GOOD, "Gondor player not good: " + Sides.side(good));
            h.assertTrue(Sides.side(evil) == Sides.Side.EVIL, "Mordor player not evil: " + Sides.side(evil));
            h.assertTrue(Sides.side(brig) == Sides.Side.NONE, "brigand player not unsided");
            h.assertTrue(Sides.side(none) == Sides.Side.NONE, "no-faction player not unsided");
            h.assertTrue(Sides.isEnemy(h.getLevel(), MORDOR, good), "Mordor is not an enemy of the Gondor player");
            h.assertFalse(Sides.isEnemy(h.getLevel(), MORDOR, evil), "Mordor is an enemy of the Mordor player");
            h.assertFalse(Sides.isEnemy(h.getLevel(), MORDOR, none), "Mordor is an enemy of an unsided player with 0 reputation");
            NemesisTestPlayers.setReputation(none, MORDOR, -1);
            h.assertTrue(Sides.isEnemy(h.getLevel(), MORDOR, none), "negative Mordor reputation did not make Mordor an enemy");
        }, () -> cleanup(h, ps));
    }

    // ------------------------------------------------------------------ eligibility

    @GameTest(template = "empty", batch = "nemesis_eligibility")
    public static void eligibility(GameTestHelper h) {
        NemesisTestPlayers.run(h, "eligibility", () -> {
            h.assertTrue(MeRanks.whyNotEligible(NemesisTestPlayers.npc(h, WARRIOR, P)) == null,
                    "warrior not eligible: " + MeRanks.whyNotEligible(NemesisTestPlayers.npc(h, WARRIOR, P)));
            NpcEntity cap = NemesisTestPlayers.npc(h, CAPTAIN, P);
            h.assertTrue(MeRanks.whyNotEligible(cap) == null, "captain not eligible: " + MeRanks.whyNotEligible(cap));
            expectNot(h, NemesisTestPlayers.npc(h, SNAGA, P), "snaga");
            expectNot(h, NemesisTestPlayers.npc(h, MILITIA, P), "militia");
            expectNot(h, NemesisTestPlayers.npc(h, THUG, P), "brigand");
            NpcEntity unit = NemesisTestPlayers.npc(h, WARRIOR, P);
            HiredUnits.markForTest(unit, UUID.randomUUID(), 1);
            expectNot(h, unit, "hired unit");
            NpcEntity bound = NemesisTestPlayers.npc(h, WARRIOR, P);
            bound.assignStructureManager(h.absolutePos(P));
            expectNot(h, bound, "structure-bound");
            NpcEntity named = NemesisTestPlayers.npc(h, WARRIOR, P);
            named.setCustomName(net.minecraft.network.chat.Component.literal("Bob"));
            expectNot(h, named, "named");
            MeRanks.TEST_NEVER.add(VETERAN);
            try {
                expectNot(h, NemesisTestPlayers.npc(h, VETERAN, P), "never_nemesis");
            } finally {
                MeRanks.TEST_NEVER.remove(VETERAN);
            }
        }, () -> cleanup(h));
    }

    private static void expectNot(GameTestHelper h, NpcEntity npc, String what) {
        String why = MeRanks.whyNotEligible(npc);
        LotrLog.info("test", "eligibility: {} -> {}", what, why);
        h.assertTrue(why != null, what + " was eligible");
    }

    // ------------------------------------------------------------------ rises

    @GameTest(template = "empty", batch = "nemesis_rise_player", timeoutTicks = 200)
    public static void riseOnPlayerKill(GameTestHelper h) {
        String name = "rise_on_player_kill";
        NemesisTestPlayers.begin(h, name);
        ServerPlayer p = null;
        NemesisRecord risen;
        try {
            p = NemesisTestPlayers.player(h, GONDOR, P);
            NpcEntity npc = NemesisTestPlayers.npc(h, WARRIOR, new BlockPos(2, 1, 1));
            NemesisRandom.force(0.0);
            risen = Promotion.considerPlayerKill(npc, p);
            h.assertTrue(risen != null, "no rise with a forced roll");
            h.assertTrue(risen.rank == Rank.MARKED && risen.faction.equals(MORDOR), "not a Marked of Mordor: " + risen.rank + " " + risen.faction);
            h.assertTrue(risen.grudge(p.getUUID()) == 3, "grudge " + risen.grudge(p.getUUID()) + " != 3");
            h.assertTrue(!risen.firstName.isEmpty() && !"Nameless".equals(risen.firstName), "no name from the banks");
            h.assertTrue(risen.strengths.size() == 1, "strengths " + risen.strengths);
            h.assertTrue(risen.weakness != null, "no weakness");
            NemesisRandom.force(0.99);
            h.assertTrue(Promotion.considerPlayerKill(NemesisTestPlayers.npc(h, WARRIOR, new BlockPos(3, 1, 1)), p) == null, "rose with a failing roll");
            p.setGameMode(GameType.CREATIVE);
            NemesisRandom.reset();
            NemesisRandom.force(0.0);
            h.assertTrue(Promotion.considerPlayerKill(NemesisTestPlayers.npc(h, WARRIOR, new BlockPos(3, 1, 2)), p) == null, "rose in creative");
            NemesisRandom.reset();
        } catch (RuntimeException e) {
            NemesisTestPlayers.fail(name, e);
            NemesisTestPlayers.clear(server(h));
            throw e;
        } finally {
            NemesisTestPlayers.remove(p);
        }
        NemesisRecord r = risen;
        h.runAfterDelay(140, () -> {
            try {
                h.assertTrue(r.status == NemesisRecord.Status.AT_LARGE, "freshly risen body did not retreat: " + r.status);
                NemesisTestPlayers.pass(name);
                h.succeed();
            } catch (RuntimeException e) {
                NemesisTestPlayers.fail(name, e);
                throw e;
            } finally {
                NemesisTestPlayers.clear(server(h));
            }
        });
    }

    @GameTest(template = "empty", batch = "nemesis_rise_unit")
    public static void riseOnUnitKill(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[1];
        NemesisTestPlayers.run(h, "rise_on_unit_kill", () -> {
            ServerPlayer owner = ps[0] = NemesisTestPlayers.player(h, GONDOR, P);
            NpcEntity unit = NemesisTestPlayers.npc(h, SOLDIER, new BlockPos(1, 1, 2));
            HiredUnits.markForTest(unit, owner.getUUID(), 3);
            double chance = Promotion.unitKillChance(3);
            h.assertTrue(Math.abs(chance - 0.16) < 1e-6, "level-3 unit chance " + chance + " != 0.16");
            NemesisRandom.force(0.15);
            NemesisRecord r = Promotion.considerUnitKill(NemesisTestPlayers.npc(h, WARRIOR, new BlockPos(2, 1, 1)), unit);
            h.assertTrue(r != null, "no rise at roll 0.15 < 0.16");
            h.assertTrue(r.grudge(owner.getUUID()) == 1, "owner grudge " + r.grudge(owner.getUUID()) + " != 1");
        }, () -> cleanup(h, ps));
    }

    // ------------------------------------------------------------------ ranks

    @GameTest(template = "empty", batch = "nemesis_ranks")
    public static void ranksAndSeats(GameTestHelper h) {
        NemesisTestPlayers.run(h, "ranks_and_seats", () -> {
            h.assertTrue(Rank.forRenown(0) == Rank.MARKED && Rank.forRenown(15) == Rank.CAPTAIN && Rank.forRenown(35) == Rank.COMMANDER
                    && Rank.forRenown(70) == Rank.WARLORD && Rank.forRenown(120) == Rank.OVERLORD && Rank.forRenown(14) == Rank.MARKED,
                    "renown thresholds do not map to ranks");
            for (int i = 0; i < 9; i++) record(h, MORDOR, WARRIOR, 20);
            List<NemesisRecord> host = NemesisSavedData.get(server(h)).living(MORDOR);
            long captains = host.stream().filter(r -> r.rank == Rank.CAPTAIN).count();
            List<NemesisRecord> waiting = host.stream().filter(r -> r.rank == Rank.MARKED).toList();
            h.assertTrue(captains == 8, "captains " + captains + " != 8");
            h.assertTrue(waiting.size() == 1 && waiting.get(0).challenger, "ninth is not a Marked challenger");
            NemesisRecord c = waiting.get(0);
            NemesisRecord holder = host.stream().filter(r -> r.rank == Rank.CAPTAIN).findFirst().orElseThrow();
            int before = c.renown;
            NemesisRandom.force(0.99, 0.0);   // challenger rolls 30, holder rolls 1
            Politics.fight(server(h), c, holder);
            h.assertTrue(holder.status == NemesisRecord.Status.DEAD, "loser not DEAD");
            h.assertTrue(c.rank == Rank.CAPTAIN, "winner rank " + c.rank);
            h.assertTrue(c.renown == before + 8, "winner renown " + c.renown + " != " + (before + 8));
        }, () -> cleanup(h));
    }

    @GameTest(template = "empty", batch = "nemesis_rank_modifiers")
    public static void rankModifiers(GameTestHelper h) {
        NemesisTestPlayers.run(h, "rank_modifiers", () -> {
            NemesisRecord r = record(h, MORDOR, WARRIOR, 35);
            h.assertTrue(r.rank == Rank.COMMANDER, "renown 35 is not Commander: " + r.rank);
            NpcEntity e = body(h, r, P);
            checkRatio(h, e.getAttribute(Attributes.MAX_HEALTH), 2.2, "max health");
            checkRatio(h, e.getAttribute(Attributes.ATTACK_DAMAGE), 1.35, "attack damage");
            Renown.setRank(server(h), r, Rank.WARLORD, "test");
            Renown.setRank(server(h), r, Rank.CAPTAIN, "test");
            for (var attr : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR, Attributes.KNOCKBACK_RESISTANCE)) {
                AttributeInstance inst = e.getAttribute(attr);
                if (inst == null) continue;
                long n = inst.getModifiers().stream().filter(m -> m.id().equals(Manifestation.RANK_MODIFIER)).count();
                h.assertTrue(n == 1, attr.getRegisteredName() + " has " + n + " rank modifiers");
            }
        }, () -> cleanup(h));
    }

    private static void checkRatio(GameTestHelper h, @Nullable AttributeInstance inst, double expected, String what) {
        h.assertTrue(inst != null, "no " + what + " attribute");
        double with = inst.getValue();
        var mod = inst.getModifier(Manifestation.RANK_MODIFIER);
        h.assertTrue(mod != null, "no rank modifier on " + what);
        inst.removeModifier(Manifestation.RANK_MODIFIER);
        double without = inst.getValue();
        inst.addPermanentModifier(mod);
        double ratio = with / without;
        LotrLog.info("test", "rank_modifiers: {} {} -> {} (x{})", what, without, with, ratio);
        h.assertTrue(Math.abs(ratio - expected) < 0.02, what + " ratio " + ratio + " != " + expected);
    }

    @GameTest(template = "empty", batch = "nemesis_body_rules", timeoutTicks = 300)
    public static void bodyRules(GameTestHelper h) {
        String name = "body_rules";
        NemesisTestPlayers.begin(h, name);
        NemesisRecord r;
        try {
            Manifestation.testRetreatDelayTicks = 40;
            r = record(h, MORDOR, WARRIOR, 0);
            NpcEntity e = body(h, r, P);
            h.assertTrue(r.manifestation == 1 && r.status == NemesisRecord.Status.MANIFEST, "first manifestation " + r.manifestation);
            h.assertTrue(Manifestation.spawn(h.getLevel(), r, h.absolutePos(P), "test second") == null, "a second body spawned for one record");
            h.assertTrue(NemesisLink.of(e) != null, "body has no link");
        } catch (RuntimeException ex) {
            NemesisTestPlayers.fail(name, ex);
            NemesisTestPlayers.clear(server(h));
            throw ex;
        }
        NemesisRecord rec = r;
        h.runAfterDelay(200, () -> {
            try {
                h.assertTrue(rec.status == NemesisRecord.Status.AT_LARGE, "no player near but the body did not retreat: " + rec.status);
                NpcEntity second = body(h, rec, P);
                h.assertTrue(rec.manifestation == 2, "manifestation " + rec.manifestation + " != 2");
                NpcEntity old = new net.sevenstars.middleearth.entity.npcs.initializer.NpcEntityBuilder(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 1)))
                        .withNpcType(WARRIOR).build();
                NemesisLink.set(old, new NemesisLink(rec.id, 1));
                h.assertFalse(h.getLevel().addFreshEntity(old), "a body with the old manifestation number was allowed to join");
                h.assertTrue(second.isAlive(), "current body gone");
                NemesisTestPlayers.pass(name);
                h.succeed();
            } catch (RuntimeException ex) {
                NemesisTestPlayers.fail(name, ex);
                throw ex;
            } finally {
                NemesisTestPlayers.clear(server(h));
            }
        });
    }

    // ------------------------------------------------------------------ escape and cheat-death

    @GameTest(template = "empty", batch = "nemesis_escape", timeoutTicks = 100)
    public static void escapeCheatDeath(GameTestHelper h) {
        String name = "escape_cheat_death";
        NemesisTestPlayers.begin(h, name);
        NemesisRecord r;
        try {
            r = record(h, MORDOR, WARRIOR, 0);
            r.weakness = NemesisTraits.id("arrow_shy");
            NpcEntity e = body(h, r, P);
            int renown = r.renown;
            e.setHealth(e.getMaxHealth() * 0.30f);
            NemesisRandom.force(0.0);   // escape roll passes
            e.hurt(e.damageSources().magic(), e.getMaxHealth() * 0.15f);
            h.assertTrue(r.escapeTried, "no escape roll at 15% health");
            h.assertTrue(r.renown == renown + 4, "escape renown " + r.renown + " != " + (renown + 4));
        } catch (RuntimeException ex) {
            NemesisTestPlayers.fail(name, ex);
            NemesisTestPlayers.clear(server(h));
            throw ex;
        }
        NemesisRecord rec = r;
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(rec.status == NemesisRecord.Status.AT_LARGE, "escaped body not AT_LARGE: " + rec.status);
                // cheat-death with a passing roll: AT_LARGE, scar and scar title
                NpcEntity e2 = body(h, rec, P);
                NemesisRandom.force(0.99, 0.0);   // escape fails, cheat-death passes
                e2.hurt(e2.damageSources().inFire(), 10000);
                h.assertTrue(rec.status == NemesisRecord.Status.AT_LARGE, "cheated death but status " + rec.status);
                h.assertTrue(rec.cheatDeaths == 1 && rec.scars.contains(DamageKind.FIRE), "no fire scar: " + rec.scars);
                h.assertTrue(rec.scarTitle != null && !rec.scarTitle.isEmpty(), "no scar title");
                // third death: dead for good without a roll
                rec.cheatDeaths = 2;
                NpcEntity e3 = body(h, rec, P);
                NemesisRandom.force(0.99);   // escape fails
                e3.hurt(e3.damageSources().magic(), 10000);
                h.assertTrue(rec.status == NemesisRecord.Status.DEAD, "third death not DEAD: " + rec.status);
                // weakness death
                NemesisRecord w = record(h, MORDOR, WARRIOR, 0);
                w.weakness = NemesisTraits.id("arrow_shy");
                NpcEntity e4 = body(h, w, new BlockPos(2, 1, 2));
                NemesisRandom.force(0.99, 0.0);   // escape fails; a cheat-death roll, if wrongly made, would pass
                e4.hurt(source(h, DamageTypes.ARROW), 10000);
                h.assertTrue(w.status == NemesisRecord.Status.DEAD, "death by its weakness not DEAD: " + w.status);
                NemesisTestPlayers.pass(name);
                h.succeed();
            } catch (RuntimeException ex) {
                NemesisTestPlayers.fail(name, ex);
                throw ex;
            } finally {
                NemesisTestPlayers.clear(server(h));
            }
        });
    }

    @GameTest(template = "empty", batch = "nemesis_reputation")
    public static void reputationHook(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[1];
        ReputationHooks.Delegate prev = ReputationHooks.get();
        int[] calls = {0, 0};
        NemesisTestPlayers.run(h, "reputation_hook", () -> {
            ReputationHooks.set(new ReputationHooks.Delegate() {
                @Override
                public void onKill(LivingEntity killer, LivingEntity victim, int multiplier) {
                    calls[0]++;
                    calls[1] = multiplier;
                    prev.onKill(killer, victim, multiplier);
                }

                @Override
                public int reputation(ServerPlayer player, ResourceLocation faction) {
                    return prev.reputation(player, faction);
                }

                @Override
                public void add(ServerPlayer player, ResourceLocation faction, int amount, String source) {
                    prev.add(player, faction, amount, source);
                }

                @Override
                public @Nullable ResourceLocation swornEnemy(ResourceLocation faction) {
                    return prev.swornEnemy(faction);
                }
            });
            ServerPlayer p = ps[0] = NemesisTestPlayers.player(h, GONDOR, new BlockPos(1, 1, 3));
            NemesisRecord r = record(h, MORDOR, WARRIOR, 0);
            r.weakness = null;
            NpcEntity e = body(h, r, P);
            // the player hits from the front: without this the combat NPC parry roll can turn the hit into 0 damage
            io.github.sethwillemsvg.lotrrevival.combat.NpcParry.chanceOverride(e, 0.0);
            NemesisRandom.force(0.99, 0.99);   // no escape, no cheat-death
            float before = e.getHealth();
            boolean hurt = e.hurt(e.damageSources().playerAttack(p), 10000);
            h.assertTrue(calls[0] == 1 && calls[1] == 25, "onKill calls=" + calls[0] + " multiplier=" + calls[1] + " delegate="
                    + ReputationHooks.get().getClass().getName() + " hurt()=" + hurt + " health " + before + "->" + e.getHealth()
                    + " dead=" + e.isDeadOrDying() + " status=" + r.status + " escape_tried=" + r.escapeTried + " cheat_deaths=" + r.cheatDeaths
                    + " killer=" + LotrLog.id(p.getUUID()) + " killer_mode=" + p.gameMode.getGameModeForPlayer());
            NemesisRecord r2 = record(h, MORDOR, WARRIOR, 0);
            NpcEntity e2 = body(h, r2, new BlockPos(2, 1, 1));
            io.github.sethwillemsvg.lotrrevival.combat.NpcParry.chanceOverride(e2, 0.0);
            NemesisRandom.force(0.0);   // escape
            e2.hurt(e2.damageSources().playerAttack(p), 10000);
            h.assertTrue(r2.escapeTried, "no escape roll on the second body: health=" + e2.getHealth() + " status=" + r2.status);
            h.assertTrue(calls[0] == 1, "an escape called onKill: calls=" + calls[0] + " status=" + r2.status);
        }, () -> {
            ReputationHooks.set(prev);
            cleanup(h, ps);
        });
    }

    // ------------------------------------------------------------------ traits

    @GameTest(template = "empty", batch = "nemesis_traits")
    public static void traits(GameTestHelper h) {
        NemesisTestPlayers.run(h, "traits", () -> {
            NpcEntity npc = NemesisTestPlayers.npc(h, WARRIOR, new BlockPos(2, 1, 2));
            npc.setYRot(0);
            npc.yBodyRot = 0;
            DamageSource arrow = source(h, DamageTypes.ARROW);
            var iron = NemesisTraits.get(NemesisTraits.id("iron_hide"));
            var shy = NemesisTraits.get(NemesisTraits.id("arrow_shy"));
            var open = NemesisTraits.get(NemesisTraits.id("open_guard"));
            h.assertTrue(iron != null && shy != null && open != null, "trait data not loaded (" + NemesisTraits.count() + " traits)");
            double m1 = TraitEffects.takenMultiplier(npc, List.of(iron), List.of(), arrow);
            double m2 = TraitEffects.takenMultiplier(npc, List.of(shy), List.of(), arrow);
            h.assertTrue(Math.abs(m1 - 0.6) < 1e-6, "Iron-hide x" + m1);
            h.assertTrue(Math.abs(m2 - 1.4) < 1e-6, "Arrow-shy x" + m2);
            Zombie front = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 4));   // yaw 0 faces +Z
            Zombie behind = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 0));
            double mf = TraitEffects.takenMultiplier(npc, List.of(open), List.of(), npc.damageSources().mobAttack(front));
            double mb = TraitEffects.takenMultiplier(npc, List.of(open), List.of(), npc.damageSources().mobAttack(behind));
            h.assertTrue(Math.abs(mf - 1.0) < 1e-6, "Open guard applied from the front: x" + mf);
            h.assertTrue(Math.abs(mb - 1.5) < 1e-6, "Open guard not applied from behind: x" + mb);
            // unknown trait id in a saved record is dropped (with a WARN)
            NemesisRecord r = record(h, MORDOR, WARRIOR, 0);
            CompoundTag t = r.save(h.getLevel().registryAccess());
            ListTag st = new ListTag();
            st.add(StringTag.valueOf("lotr_revival:no_such_trait"));
            st.add(StringTag.valueOf("lotr_revival:iron_hide"));
            t.put("strengths", st);
            NemesisRecord loaded = NemesisRecord.load(t, h.getLevel().registryAccess(), NemesisTraits::isKnownOrUnloaded);
            h.assertTrue(loaded.strengths.equals(List.of(NemesisTraits.id("iron_hide"))), "unknown trait kept: " + loaded.strengths);
        }, () -> cleanup(h));
    }

    // ------------------------------------------------------------------ side switch

    @GameTest(template = "empty", batch = "nemesis_side_switch")
    public static void sideSwitch(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[1];
        NemesisTestPlayers.run(h, "side_switch", () -> {
            ServerPlayer p = ps[0] = NemesisTestPlayers.player(h, GONDOR, P);
            NemesisSavedData data = NemesisSavedData.get(server(h));
            long tick = h.getLevel().getGameTime();
            NemesisRecord orc = record(h, MORDOR, WARRIOR, 0);
            orc.addGrudge(p.getUUID(), 4, tick);
            NemesisPlayerData.checkSideSwitch(p, data);
            h.assertFalse(orc.grudges.get(p.getUUID()).dormant, "Mordor grudge dormant for a Gondor player");
            NemesisTestPlayers.setFaction(p, MORDOR);
            h.assertTrue(NemesisPlayerData.checkSideSwitch(p, data), "switch to Mordor not detected as a side change");
            h.assertTrue(orc.grudges.get(p.getUUID()).dormant, "Mordor grudge awake after joining Mordor");
            orc.grudges.get(p.getUUID()).lastDecay = tick - 2 * 24000L;
            NemesisPlayerData.decayDormant(data, tick);
            h.assertTrue(orc.grudge(p.getUUID()) == 2, "dormant grudge after 2 days " + orc.grudge(p.getUUID()) + " != 2");
            NemesisTestPlayers.setFaction(p, GONDOR);
            NemesisPlayerData.checkSideSwitch(p, data);
            h.assertFalse(orc.grudges.get(p.getUUID()).dormant, "switching back did not wake the grudge");
        }, () -> cleanup(h, ps));
    }

    // ------------------------------------------------------------------ ambush

    @GameTest(template = "empty", batch = "nemesis_ambush", timeoutTicks = 60)
    public static void ambushEligibility(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[1];
        var prevRed = Ambush.governorRed;
        NemesisTestPlayers.run(h, "ambush_eligibility", () -> {
            ServerPlayer p = ps[0] = NemesisTestPlayers.player(h, GONDOR, P);
            NemesisSavedData data = NemesisSavedData.get(server(h));
            data.ambushCooldownUntil.remove(p.getUUID());
            Ambush.testSafeRadius = 0;
            h.assertTrue(Ambush.whyNotEligible(p, data) != null, "eligible with 0 minutes online");
            Ambush.SESSION_START.put(p.getUUID(), (long) server(h).getTickCount() - 20 * 60 * 20);
            String why = Ambush.whyNotEligible(p, data);
            h.assertTrue(why == null, "not eligible: " + why + " (player at " + p.blockPosition().toShortString() + " in "
                    + p.level().dimension().location() + ")");
            p.setGameMode(GameType.CREATIVE);
            h.assertTrue(Ambush.whyNotEligible(p, data) != null, "eligible in creative");
            p.setGameMode(GameType.SURVIVAL);
            // a radius just past the player's real distance to world spawn (the test level's spawn can be anywhere)
            BlockPos spawn = p.serverLevel().getSharedSpawnPos();
            double toSpawn = Math.sqrt(p.blockPosition().distSqr(spawn));
            Ambush.testSafeRadius = (int) Math.ceil(toSpawn) + 16;
            String inside = Ambush.whyNotEligible(p, data);
            h.assertTrue(inside != null, "eligible inside the safe radius: radius=" + Ambush.testSafeRadius + " player at "
                    + p.blockPosition().toShortString() + " in " + p.level().dimension().location() + " spawn " + spawn.toShortString()
                    + " dist=" + String.format("%.1f", toSpawn) + " session_start=" + Ambush.SESSION_START.get(p.getUUID())
                    + " tick=" + server(h).getTickCount() + " game_mode=" + p.gameMode.getGameModeForPlayer());
            LotrLog.info("test", "ambush_eligibility: inside the safe radius -> {}", inside);
            Ambush.testSafeRadius = 0;
            data.ambushCooldownUntil.put(p.getUUID(), data.onlineTicks.getOrDefault(p.getUUID(), 0L) + 1000);
            h.assertTrue(Ambush.whyNotEligible(p, data) != null, "eligible inside the cooldown");
            data.ambushCooldownUntil.remove(p.getUUID());
            Ambush.governorRed = () -> true;
            h.assertTrue(Ambush.whyNotEligible(p, data) != null, "eligible with a Red governor");
            Ambush.governorRed = prevRed;
            // TODO(spec §Acceptance ambush_eligibility): Peaceful and the max_manifest cap are covered by whyNotEligible but not driven here
            NemesisRecord r = record(h, MORDOR, WARRIOR, 15);   // Captain: 2 bodyguards
            r.addGrudge(p.getUUID(), 3, h.getLevel().getGameTime());
            h.assertTrue(Ambush.candidate(p, data) == r, "grudge record not a candidate");
            NpcEntity body = Ambush.start(p, r);
            h.assertTrue(body != null, "forced ambush did not spawn (no spot near " + p.blockPosition().toShortString() + "?)");
            double d = Math.sqrt(body.distanceToSqr(p));
            h.assertTrue(d >= 14 && d <= 31, "ambush spawned " + d + " blocks away");
            var b = Manifestation.body(r.id);
            h.assertTrue(b != null && b.guards.size() == Rank.CAPTAIN.bodyguards, "bodyguards " + (b == null ? -1 : b.guards.size()));
            h.assertTrue(NemesisBossBar.has(body.getUUID()), "no boss bar");
        }, () -> {
            Ambush.governorRed = prevRed;
            cleanup(h, ps);
        });
    }

    // ------------------------------------------------------------------ sergeant

    @GameTest(template = "empty", batch = "nemesis_sergeant")
    public static void sergeant(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[1];
        NemesisTestPlayers.run(h, "sergeant", () -> {
            ServerPlayer owner = ps[0] = NemesisTestPlayers.player(h, GONDOR, new BlockPos(1, 1, 3));
            NemesisRecord r = record(h, MORDOR, WARRIOR, 0);
            NpcEntity body = body(h, r, P);
            NpcEntity five = NemesisTestPlayers.npc(h, SOLDIER, new BlockPos(2, 1, 1));
            NpcEntity four = NemesisTestPlayers.npc(h, SOLDIER, new BlockPos(2, 1, 2));
            HiredUnits.markForTest(five, owner.getUUID(), 5);
            HiredUnits.markForTest(four, owner.getUUID(), 4);
            long now = h.getLevel().getGameTime();
            Sergeants.recordHit(r.id, five.getUUID(), 5, now);
            Sergeants.recordHit(r.id, four.getUUID(), 50, now);
            Sergeants.onNemesisDown(server(h), r, body);
            h.assertTrue(five.hasData(NemesisLink.SERGEANT), "level-5 unit not promoted");
            SergeantData sd = five.getData(NemesisLink.SERGEANT);
            h.assertTrue(!sd.firstName().isEmpty() && NemesisTraits.get(sd.trait()) != null, "sergeant without name or strength: " + sd);
            h.assertFalse(four.hasData(NemesisLink.SERGEANT), "level-4 unit promoted");
            NemesisSavedData data = NemesisSavedData.get(server(h));
            data.sergeants.put(owner.getUUID(), new java.util.HashSet<>(Set.of(five.getUUID(), UUID.randomUUID(), UUID.randomUUID())));
            NpcEntity another = NemesisTestPlayers.npc(h, SOLDIER, new BlockPos(3, 1, 1));
            HiredUnits.markForTest(another, owner.getUUID(), 5);
            h.assertFalse(Sergeants.promote(server(h), another, 10), "owner's cap of 3 did not hold");
            data.sergeants.remove(owner.getUUID());
        }, () -> cleanup(h, ps));
    }

    // ------------------------------------------------------------------ persistence

    @GameTest(template = "empty", batch = "nemesis_persistence")
    public static void persistence(GameTestHelper h) {
        NemesisTestPlayers.run(h, "persistence", () -> {
            NemesisSavedData data = NemesisSavedData.get(server(h));
            NemesisRecord r = record(h, MORDOR, WARRIOR, 40);
            UUID player = UUID.randomUUID();
            r.addGrudge(player, 6, 123);
            r.grudges.get(player).dormant = true;
            r.knowsStrengths.add(player);
            r.knowsWeakness.add(player);
            r.scars.add(DamageKind.FIRE);
            r.scarTitle = "the Burnt";
            r.cheatDeaths = 1;
            r.gear.put(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            data.ambushCooldownUntil.put(player, 777L);
            data.onlineTicks.put(player, 555L);
            CompoundTag tag = data.save(new CompoundTag(), h.getLevel().registryAccess());
            NemesisSavedData back = NemesisSavedData.load(tag, h.getLevel().registryAccess());
            NemesisRecord b = back.find(r.id);
            h.assertTrue(b != null, "record lost");
            h.assertTrue(b.renown == r.renown && b.rank == r.rank && b.displayName().equals(r.displayName()), "name, renown or rank changed");
            h.assertTrue(b.grudge(player) == 6 && b.grudges.get(player).dormant, "grudge lost");
            h.assertTrue(b.knowsStrengths.contains(player) && b.knowsWeakness.contains(player), "intel lost");
            h.assertTrue(b.scars.equals(r.scars) && b.cheatDeaths == 1, "scars lost");
            h.assertTrue(b.strengths.equals(r.strengths) && java.util.Objects.equals(b.weakness, r.weakness), "traits changed");
            h.assertTrue(b.gear.get(EquipmentSlot.MAINHAND) != null && b.gear.get(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "gear lost");
            h.assertTrue(back.ambushCooldownUntil.get(player) == 777L && back.onlineTicks.get(player) == 555L, "cooldowns lost");
            data.ambushCooldownUntil.remove(player);
            data.onlineTicks.remove(player);
            // TODO(spec §Acceptance persistence): the ended-events list round trip lives in events.test.EventTests
        }, () -> cleanup(h));
    }
}
