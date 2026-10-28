package io.github.sethwillemsvg.lotrrevival.core.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentData;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.alignment.PledgeBreakReason;
import io.github.sethwillemsvg.lotrrevival.core.alignment.PledgeResult;
import io.github.sethwillemsvg.lotrrevival.core.config.CoreConfig;
import io.github.sethwillemsvg.lotrrevival.core.event.AlignmentEvent;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.event.PledgeEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.AlignmentCreditor;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionMember;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Alignment attachment, kill rules, pledge, hostility, drain and persistence. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AlignmentTest {
    private static final AlignmentSource QUEST = AlignmentSource.of(AlignmentSource.QUEST);

    /** A pig that belongs to a faction; the real NPC entities arrive with the NPC pod. */
    private static class TestMember extends Pig implements FactionMember {
        private final ResourceKey<Faction> faction;
        private final float value;
        private final boolean civilian;

        TestMember(Level level, ResourceKey<Faction> faction, float value, boolean civilian) {
            super(EntityType.PIG, level);
            this.faction = faction;
            this.value = value;
            this.civilian = civilian;
        }

        @Override
        public ResourceKey<Faction> faction() {
            return faction;
        }

        @Override
        public float alignmentKillValue() {
            return value;
        }

        @Override
        public boolean isCivilian() {
            return civilian;
        }
    }

    /** A hired unit credited to a player. */
    private static final class TestHired extends TestMember implements AlignmentCreditor {
        private final UUID owner;

        TestHired(Level level, UUID owner) {
            super(level, Factions.GONDOR, 1.0f, false);
            this.owner = owner;
        }

        @Override
        public Optional<UUID> creditedPlayer() {
            return Optional.of(owner);
        }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "alignment-test"));
    }

    private static void kill(GameTestHelper helper, ServerPlayer killer, ResourceKey<Faction> faction, float value,
            boolean civilian) {
        TestMember victim = new TestMember(helper.getLevel(), faction, value, civilian);
        victim.setPos(killer.position());
        victim.die(helper.getLevel().damageSources().playerAttack(killer));
    }

    private static void expect(GameTestHelper helper, ServerPlayer p, ResourceKey<Faction> f, float expected) {
        float actual = Alignment.get(p, f);
        helper.assertTrue(
                Math.abs(actual - expected) < 0.001f,
                f.location().getPath() + " expected " + expected + " but was " + actual);
    }

    private static <T extends net.neoforged.bus.api.Event> Consumer<T> listen(Class<T> type, Consumer<T> handler) {
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL, false, type, handler);
        return handler;
    }

    private static void unlisten(Object handler) {
        NeoForge.EVENT_BUS.unregister(handler);
    }

    // ------------------------------------------------------------------ kills

    @GameTest(template = "empty")
    public static void killSpreadFromGondor(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        kill(helper, p, Factions.GONDOR, 1.0f, false);
        for (var f : List.of(Factions.GONDOR, Factions.DUNEDAIN_NORTH, Factions.ROHAN, Factions.HOBBIT,
                Factions.HIGH_ELF, Factions.DALE)) {
            expect(helper, p, f, -1.0f);
        }
        for (var f : List.of(Factions.DUNLAND, Factions.FAR_HARAD, Factions.GUNDABAD, Factions.ANGMAR,
                Factions.DOL_GULDUR, Factions.ISENGARD, Factions.MORDOR, Factions.EASTERLING, Factions.NEAR_HARAD,
                Factions.HALF_TROLL)) {
            expect(helper, p, f, 1.0f);
        }
        expect(helper, p, Factions.LOTHLORIEN, 0.0f);
        expect(helper, p, Factions.HARAD_JUNGLE, 0.0f);
        expect(helper, p, Factions.UTUMNO, -66666.0f);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void civilianKillGivesNoBonusToDisapprovers(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        kill(helper, p, Factions.GONDOR, 1.0f, true);
        expect(helper, p, Factions.EASTERLING, 0.0f);
        expect(helper, p, Factions.NEAR_HARAD, 0.0f);
        expect(helper, p, Factions.MORDOR, 1.0f);
        expect(helper, p, Factions.GONDOR, -1.0f);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void penaltyScaling(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Alignment.set(p, Factions.GONDOR, 500.0f, AlignmentSource.command());
        kill(helper, p, Factions.GONDOR, 1.0f, false);
        expect(helper, p, Factions.GONDOR, 490.0f);
        ServerPlayer q = player(helper);
        Alignment.set(q, Factions.GONDOR, 1200.0f, AlignmentSource.command());
        kill(helper, q, Factions.GONDOR, 1.0f, false);
        expect(helper, q, Factions.GONDOR, 1180.0f);
        ServerPlayer r = player(helper);
        Alignment.set(r, Factions.ROHAN, 30.0f, AlignmentSource.command());
        kill(helper, r, Factions.GONDOR, 1.0f, false);
        expect(helper, r, Factions.ROHAN, 29.0f);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unpledgedGainIsHalvedAbovePledgeThreshold(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Alignment.set(p, Factions.MORDOR, 150.0f, AlignmentSource.command());
        kill(helper, p, Factions.GONDOR, 2.0f, false);
        expect(helper, p, Factions.MORDOR, 151.0f);
        ServerPlayer q = player(helper);
        Alignment.set(q, Factions.MORDOR, 150.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.tryPledge(q, Factions.MORDOR) == PledgeResult.SUCCESS, "pledge to mordor");
        kill(helper, q, Factions.GONDOR, 2.0f, false);
        expect(helper, q, Factions.MORDOR, 152.0f);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pledgedMortalEnemyGainIsCappedAtZero(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Alignment.set(p, Factions.GONDOR, 100.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.tryPledge(p, Factions.GONDOR).isSuccess(), "pledge to gondor");
        Alignment.set(p, Factions.MORDOR, -0.5f, AlignmentSource.command());
        float applied = Alignment.add(p, Factions.MORDOR, 2.0f, QUEST);
        expect(helper, p, Factions.MORDOR, 0.0f);
        helper.assertTrue(Math.abs(applied - 0.5f) < 0.001f, "applied delta " + applied);
        Alignment.add(p, Factions.MORDOR, 2.0f, QUEST);
        expect(helper, p, Factions.MORDOR, 0.0f);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void hiredKillChangesNothingButFiresEvent(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        List<FactionKillEvent> seen = new ArrayList<>();
        Consumer<FactionKillEvent> handler = listen(FactionKillEvent.class, seen::add);
        try {
            TestHired hired = new TestHired(helper.getLevel(), owner.getUUID());
            hired.setPos(owner.position());
            TestMember victim = new TestMember(helper.getLevel(), Factions.GONDOR, 1.0f, false);
            victim.setPos(owner.position());
            victim.die(helper.getLevel().damageSources().mobAttack(hired));
            helper.assertTrue(seen.size() == 1, "expected one kill event, got " + seen.size());
            helper.assertTrue(seen.get(0).isCreditedViaHire(), "hire flag");
            helper.assertTrue(seen.get(0).getPlayer() == owner, "credited player");
            for (ResourceKey<Faction> f : Factions.REAL) {
                if (f != Factions.UTUMNO) {
                    expect(helper, owner, f, 0.0f);
                }
            }
            // PvP: a player victim never changes alignment.
            ServerPlayer other = player(helper);
            DamageSource pvp = helper.getLevel().damageSources().playerAttack(owner);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(other, pvp));
            expect(helper, owner, Factions.GONDOR, 0.0f);
            helper.assertTrue(seen.size() == 1, "pvp must not fire a kill event");
        } finally {
            unlisten(handler);
            helper.getLevel().getServer().getPlayerList().remove(owner);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledKillChangesNothing(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Consumer<FactionKillEvent> handler = listen(FactionKillEvent.class, e -> e.setCanceled(true));
        try {
            kill(helper, p, Factions.GONDOR, 1.0f, false);
        } finally {
            unlisten(handler);
        }
        expect(helper, p, Factions.GONDOR, 0.0f);
        expect(helper, p, Factions.MORDOR, 0.0f);
        helper.succeed();
    }

    // ------------------------------------------------------------------ ranks and hostility

    @GameTest(template = "empty")
    public static void rankChangedFiresAtThreshold(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        List<String> ranks = new ArrayList<>();
        Consumer<AlignmentEvent.RankChanged> handler = listen(
                AlignmentEvent.RankChanged.class, e -> ranks.add(e.getNewRank().id()));
        try {
            Alignment.add(p, Factions.GONDOR, 10.0f, QUEST);
            Alignment.set(p, Factions.GONDOR, 9.9f, AlignmentSource.command());
            Alignment.add(p, Factions.WOOD_ELF, 10.0f, QUEST);
        } finally {
            unlisten(handler);
        }
        helper.assertTrue(ranks.equals(List.of("r1", "neutral")), "rank events were " + ranks);
        helper.assertTrue(Alignment.meetsRank(p, Factions.WOOD_ELF, "neutral"), "wood elf neutral");
        helper.assertTrue(!Alignment.meetsRank(p, Factions.WOOD_ELF, "r1"), "wood elf first rank is 50");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hostilityAndSuspicion(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        TestMember orc = new TestMember(helper.getLevel(), Factions.MORDOR, 1.0f, false);
        Alignment.set(p, Factions.MORDOR, -1.0f, AlignmentSource.command());
        helper.assertTrue(FactionRelations.hostilityChance(orc, p) == 1.0f, "chance at -1");
        helper.assertTrue(FactionRelations.isHostile(orc, p), "hostile at -1");
        Alignment.set(p, Factions.MORDOR, 2.0f, AlignmentSource.command());
        helper.assertTrue(FactionRelations.hostilityChance(orc, p) == 0.1f, "chance at +2");
        helper.assertTrue(!FactionRelations.isHostile(orc, p), "not hard hostile at +2");
        Alignment.set(p, Factions.MORDOR, 100.0f, AlignmentSource.command());
        helper.assertTrue(FactionRelations.hostilityChance(orc, p) == 0.0f, "chance at +100");
        TestMember elf = new TestMember(helper.getLevel(), Factions.WOOD_ELF, 1.0f, false);
        Alignment.set(p, Factions.WOOD_ELF, 49.9f, AlignmentSource.command());
        helper.assertTrue(FactionRelations.isHostile(elf, p), "wood elf hostile at 49.9");
        Alignment.set(p, Factions.WOOD_ELF, 50.0f, AlignmentSource.command());
        helper.assertTrue(!FactionRelations.isHostile(elf, p), "wood elf not hostile at 50");
        TestMember utumno = new TestMember(helper.getLevel(), Factions.UTUMNO, 1.0f, false);
        helper.assertTrue(FactionRelations.isHostile(utumno, p), "utumno always hostile");
        helper.assertTrue(FactionRelations.isHostile(orc, new TestMember(helper.getLevel(), Factions.GONDOR, 1, false)),
                "mordor vs gondor");
        helper.assertTrue(!FactionRelations.isHostile(orc, new TestMember(helper.getLevel(), Factions.MORDOR, 1, false)),
                "same faction");
        helper.succeed();
    }

    // ------------------------------------------------------------------ change events

    @GameTest(template = "empty")
    public static void changeCancellationAndRounding(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        List<AlignmentEvent.Changed> changed = new ArrayList<>();
        Consumer<AlignmentEvent.Changed> changedHandler = listen(AlignmentEvent.Changed.class, changed::add);
        Consumer<AlignmentEvent.Change> cancel = listen(AlignmentEvent.Change.class, e -> e.setCanceled(true));
        try {
            float applied = Alignment.add(p, Factions.GONDOR, 5.0f, QUEST);
            helper.assertTrue(applied == 0.0f, "cancelled add returned " + applied);
            expect(helper, p, Factions.GONDOR, 0.0f);
        } finally {
            unlisten(cancel);
        }
        helper.assertTrue(changed.isEmpty(), "cancelled change fired Changed");
        try {
            float applied = Alignment.add(p, Factions.GONDOR, 0.04f, QUEST);
            helper.assertTrue(applied == 0.0f, "rounded-away add returned " + applied);
            helper.assertTrue(changed.isEmpty(), "zero delta fired Changed");
            Consumer<AlignmentEvent.Change> halve = listen(AlignmentEvent.Change.class, e -> e.setDelta(e.getDelta() / 2));
            try {
                Alignment.add(p, Factions.GONDOR, 5.0f, QUEST);
            } finally {
                unlisten(halve);
            }
            expect(helper, p, Factions.GONDOR, 2.5f);
            helper.assertTrue(changed.size() == 1, "one Changed expected");
        } finally {
            unlisten(changedHandler);
        }
        // Clamp and fixed alignment.
        Alignment.set(p, Factions.GONDOR, 99999.0f, AlignmentSource.command());
        expect(helper, p, Factions.GONDOR, 10000.0f);
        Alignment.set(p, Factions.UTUMNO, 5.0f, AlignmentSource.command());
        Alignment.add(p, Factions.UTUMNO, 5.0f, QUEST);
        expect(helper, p, Factions.UTUMNO, -66666.0f);
        helper.succeed();
    }

    // ------------------------------------------------------------------ persistence

    @GameTest(template = "empty")
    public static void attachmentSurvivesDeathAndEndClone(GameTestHelper helper) {
        ServerPlayer old = player(helper);
        Alignment.set(old, Factions.GONDOR, 250.0f, AlignmentSource.command());
        Alignment.set(old, Factions.MORDOR, -30.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.tryPledge(old, Factions.GONDOR).isSuccess(), "pledge");
        Alignment.setPledgeCooldown(old, 1234);
        for (boolean death : new boolean[] {true, false}) {
            ServerPlayer copy = player(helper);
            copy.copyAttachmentsFrom(old, death);
            expect(helper, copy, Factions.GONDOR, 250.0f);
            expect(helper, copy, Factions.MORDOR, -30.0f);
            helper.assertTrue(
                    Alignment.pledge(copy).equals(Optional.of(Factions.GONDOR)), "pledge copied (death=" + death + ")");
            helper.assertTrue(Alignment.pledgeCooldown(copy) == 1234, "cooldown copied (death=" + death + ")");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownFactionIdsRoundTrip(GameTestHelper helper) {
        ResourceLocation removed = LOTRRevival.id("removed_faction");
        AlignmentData data = new AlignmentData();
        data.setValue(removed, 12.3f);
        data.setValue(Factions.GONDOR.location(), 5.0f);
        data.setPledge(removed);
        data.setPledgeCooldownTicks(77);
        data.setPledgeCooldownFaction(removed);
        data.setDrainTimer(320);
        data.setDrainNoticeShown(true);
        DataResult<JsonElement> encoded = AlignmentData.CODEC.encodeStart(JsonOps.INSTANCE, data);
        JsonElement json = encoded.getOrThrow(IllegalStateException::new);
        AlignmentData decoded = AlignmentData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);
        helper.assertTrue(decoded.value(removed) == 12.3f, "removed faction value");
        helper.assertTrue(decoded.value(Factions.GONDOR.location()) == 5.0f, "gondor value");
        helper.assertTrue(decoded.pledge().equals(Optional.of(removed)), "pledge to removed faction kept");
        helper.assertTrue(decoded.pledgeCooldownTicks() == 77, "cooldown");
        helper.assertTrue(decoded.pledgeCooldownFaction().equals(Optional.of(removed)), "cooldown faction");
        helper.assertTrue(decoded.drainTimer() == 320, "drain timer");
        helper.assertTrue(decoded.drainNoticeShown(), "drain notice flag");
        JsonElement empty = AlignmentData.CODEC.encodeStart(JsonOps.INSTANCE, new AlignmentData())
                .getOrThrow(IllegalStateException::new);
        helper.assertTrue(empty.getAsJsonObject().size() == 0, "empty data stores nothing: " + empty);
        // A pledge to a faction missing from the registry reads as no pledge but is kept.
        ServerPlayer p = player(helper);
        p.copyAttachmentsFrom(copyHolder(helper, data), false);
        helper.assertTrue(Alignment.pledge(p).isEmpty(), "pledge to missing faction reads as none");
        helper.assertTrue(
                p.getData(io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentAttachments.ALIGNMENT)
                        .pledge()
                        .equals(Optional.of(removed)),
                "pledge to missing faction not erased");
        expect(helper, p, Factions.GONDOR, 5.0f);
        helper.succeed();
    }

    private static ServerPlayer copyHolder(GameTestHelper helper, AlignmentData data) {
        ServerPlayer holder = player(helper);
        AlignmentData target = holder.getData(io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentAttachments.ALIGNMENT);
        data.values().forEach(target::setValue);
        target.setPledge(data.pledge().orElse(null));
        return holder;
    }

    // ------------------------------------------------------------------ pledge

    @GameTest(template = "empty")
    public static void pledgeRequirements(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        helper.assertTrue(Alignment.tryPledge(p, Factions.GONDOR) == PledgeResult.RANK_TOO_LOW, "too low");
        Alignment.set(p, Factions.GONDOR, 100.0f, AlignmentSource.command());
        Alignment.set(p, Factions.MORDOR, 5.0f, AlignmentSource.command());
        helper.assertTrue(
                Alignment.tryPledge(p, Factions.GONDOR) == PledgeResult.MORTAL_ENEMY_POSITIVE, "mortal enemy blocks");
        Alignment.set(p, Factions.MORDOR, 0.0f, AlignmentSource.command());
        Alignment.set(p, Factions.DUNLAND, 50.0f, AlignmentSource.command()); // plain enemy of gondor
        helper.assertTrue(Alignment.tryPledge(p, Factions.GONDOR) == PledgeResult.SUCCESS, "enemy does not block");
        helper.assertTrue(Alignment.tryPledge(p, Factions.GONDOR) == PledgeResult.ALREADY_PLEDGED, "already pledged");
        ServerPlayer q = player(helper);
        helper.assertTrue(Alignment.tryPledge(q, Factions.UTUMNO) == PledgeResult.NOT_PLEDGEABLE, "utumno");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pledgeWarningThenBreak(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Alignment.set(p, Factions.GONDOR, 600.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.tryPledge(p, Factions.GONDOR).isSuccess(), "pledge");
        List<String> log = new ArrayList<>();
        Consumer<PledgeEvent.Warned> warned = listen(PledgeEvent.Warned.class, e -> log.add("warned"));
        Consumer<PledgeEvent.Break> broke = listen(
                PledgeEvent.Break.class, e -> log.add("break:" + e.getReason().getSerializedName()));
        try {
            kill(helper, p, Factions.ROHAN, 1.0f, false);
            helper.assertTrue(log.equals(List.of("warned")), "first kill warns: " + log);
            helper.assertTrue(Alignment.pledge(p).isPresent(), "pledge kept after warning");
            helper.assertTrue(Alignment.pledgeWarning(p) == 24000, "warning counter started");
            for (int n = 0; n < 24000; n++) {
                Alignment.tickTimers(p);
            }
            helper.assertTrue(Alignment.pledgeWarning(p) == 0, "warning expired");
            kill(helper, p, Factions.ROHAN, 1.0f, false);
            helper.assertTrue(log.equals(List.of("warned", "warned")), "expired counter only warns again: " + log);
            helper.assertTrue(Alignment.pledge(p).isPresent(), "pledge still kept");
            kill(helper, p, Factions.ROHAN, 1.0f, false);
            helper.assertTrue(
                    log.equals(List.of("warned", "warned", "break:killed_ally")), "second kill breaks: " + log);
            helper.assertTrue(Alignment.pledge(p).isEmpty(), "pledge broken");
        } finally {
            unlisten(warned);
            unlisten(broke);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakPenaltyAndCooldown(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Alignment.set(p, Factions.GONDOR, 600.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.tryPledge(p, Factions.GONDOR).isSuccess(), "pledge");
        Alignment.breakPledge(p, PledgeBreakReason.VOLUNTARY);
        expect(helper, p, Factions.GONDOR, 100.0f);
        helper.assertTrue(Alignment.pledgeCooldown(p) == 54000, "cooldown " + Alignment.pledgeCooldown(p));
        helper.assertTrue(Alignment.tryPledge(p, Factions.GONDOR) == PledgeResult.ON_COOLDOWN, "cooldown blocks");

        ServerPlayer low = player(helper);
        Alignment.set(low, Factions.GONDOR, 100.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.tryPledge(low, Factions.GONDOR).isSuccess(), "pledge low");
        Alignment.set(low, Factions.GONDOR, 95.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.pledge(low).isEmpty(), "auto-break below rank");
        expect(helper, low, Factions.GONDOR, 50.0f);

        ServerPlayer lower = player(helper);
        Alignment.set(lower, Factions.GONDOR, 100.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.tryPledge(lower, Factions.GONDOR).isSuccess(), "pledge lower");
        Alignment.set(lower, Factions.GONDOR, 40.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.pledge(lower).isEmpty(), "auto-break at 40");
        expect(helper, lower, Factions.GONDOR, 40.0f);

        ServerPlayer cmd = player(helper);
        Alignment.set(cmd, Factions.GONDOR, 600.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.tryPledge(cmd, Factions.GONDOR).isSuccess(), "pledge cmd");
        Alignment.breakPledge(cmd, PledgeBreakReason.COMMAND);
        expect(helper, cmd, Factions.GONDOR, 600.0f);
        helper.assertTrue(Alignment.pledgeCooldown(cmd) == 0, "command break has no cooldown");

        // A kill that both warns and drops below the pledge rank: the auto-break wins.
        ServerPlayer kp = player(helper);
        Alignment.set(kp, Factions.GONDOR, 101.0f, AlignmentSource.command());
        helper.assertTrue(Alignment.tryPledge(kp, Factions.GONDOR).isSuccess(), "pledge kp");
        kill(helper, kp, Factions.ROHAN, 2.0f, false);
        helper.assertTrue(Alignment.pledge(kp).isEmpty(), "kill auto-broke pledge");
        helper.assertTrue(Alignment.pledgeWarning(kp) == 0, "no warning shown after auto-break");
        helper.succeed();
    }

    // ------------------------------------------------------------------ drain

    @GameTest(template = "empty")
    public static void mortalEnemyDrain(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Alignment.set(p, Factions.GONDOR, 50.0f, AlignmentSource.command());
        Alignment.set(p, Factions.MORDOR, 50.0f, AlignmentSource.command());
        Alignment.set(p, Factions.GUNDABAD, 50.0f, AlignmentSource.command());
        Alignment.set(p, Factions.LOTHLORIEN, 50.0f, AlignmentSource.command()); // mortal enemy of the orc factions
        for (int n = 0; n < 999; n++) {
            Alignment.tickTimers(p);
        }
        expect(helper, p, Factions.GONDOR, 50.0f);
        Alignment.tickTimers(p);
        expect(helper, p, Factions.GONDOR, 45.0f);
        expect(helper, p, Factions.MORDOR, 45.0f);
        expect(helper, p, Factions.GUNDABAD, 45.0f);
        expect(helper, p, Factions.LOTHLORIEN, 45.0f);

        ServerPlayer low = player(helper);
        Alignment.set(low, Factions.GONDOR, 3.0f, AlignmentSource.command());
        Alignment.set(low, Factions.MORDOR, 3.0f, AlignmentSource.command());
        for (int n = 0; n < 1000; n++) {
            Alignment.tickTimers(low);
        }
        expect(helper, low, Factions.GONDOR, 0.0f);
        expect(helper, low, Factions.MORDOR, 0.0f);

        CoreConfig.MORTAL_ENEMY_DRAIN.set(false);
        try {
            ServerPlayer off = player(helper);
            Alignment.set(off, Factions.GONDOR, 50.0f, AlignmentSource.command());
            Alignment.set(off, Factions.MORDOR, 50.0f, AlignmentSource.command());
            for (int n = 0; n < 2000; n++) {
                Alignment.tickTimers(off);
            }
            expect(helper, off, Factions.GONDOR, 50.0f);
            expect(helper, off, Factions.MORDOR, 50.0f);
        } finally {
            CoreConfig.MORTAL_ENEMY_DRAIN.set(true);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ commands

    private static void run(GameTestHelper helper, net.minecraft.commands.CommandSourceStack source, String command) {
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            helper.fail("command failed: /" + command + ": " + e.getMessage());
        }
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void commandsWork(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        try {
            var server = helper.getLevel().getServer();
            var source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            String name = p.getGameProfile().getName();
            run(helper, source, "lotr alignment set " + name + " gondor 600");
            expect(helper, p, Factions.GONDOR, 600.0f);
            run(helper, source, "lotr alignment add " + name + " gondor -100.5");
            expect(helper, p, Factions.GONDOR, 499.5f);
            run(helper, source, "lotr pledge set " + name + " gondor");
            helper.assertTrue(Alignment.pledge(p).equals(Optional.of(Factions.GONDOR)), "pledge set by command");
            run(helper, source, "lotr pledge cooldown " + name + " 500");
            helper.assertTrue(Alignment.pledgeCooldown(p) == 500, "cooldown set by command");
            run(helper, source, "lotr pledge clear " + name);
            helper.assertTrue(Alignment.pledge(p).isEmpty(), "pledge cleared");
            expect(helper, p, Factions.GONDOR, 499.5f);
            run(helper, source, "lotr alignment add " + name + " gondor 1 spread");
            expect(helper, p, Factions.MORDOR, 1.0f);
            run(helper, source, "lotr alignment reset " + name + " gondor");
            expect(helper, p, Factions.GONDOR, 0.0f);
            expect(helper, p, Factions.MORDOR, 1.0f);
            run(helper, source, "lotr alignment reset " + name);
            expect(helper, p, Factions.MORDOR, 0.0f);
            run(helper, source, "lotr alignment set " + name + " utumno 5");
            expect(helper, p, Factions.UTUMNO, -66666.0f);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
        helper.succeed();
    }
}
