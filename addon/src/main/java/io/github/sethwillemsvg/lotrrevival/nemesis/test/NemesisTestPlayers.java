package io.github.sethwillemsvg.lotrrevival.nemesis.test;

import io.github.sethwillemsvg.lotrrevival.core.api.ReputationHooks;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nemesis.Ambush;
import io.github.sethwillemsvg.lotrrevival.nemesis.Manifestation;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisConfig;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRecord;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.initializer.NpcEntityBuilder;
import net.sevenstars.middleearth.resources.StateSaverAndLoader;
import net.sevenstars.middleearth.resources.persistent_datas.PlayerData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helpers (spec Acceptance tests): mock players with a Middle-earth faction and add-on reputation, Middle-earth NPCs, the
 * START/PASS/FAIL lines, and cleanup of every record and body a test made. Mock players are removed in a {@code finally}.
 */
public final class NemesisTestPlayers {
    public static final ResourceLocation MORDOR = me("mordor"), GONDOR = me("gondor"), BRIGAND = me("brigand");

    private NemesisTestPlayers() {}

    public static ResourceLocation me(String path) {
        return ResourceLocation.fromNamespaceAndPath("middle-earth", path);
    }

    public interface Body {
        void run() throws Exception;
    }

    /** START, setup, body, PASS (and succeed) or FAIL; the cleanup always runs. For tests that finish synchronously. */
    public static void run(GameTestHelper h, String name, Body body, Runnable cleanup) {
        begin(h, name);
        try {
            body.run();
            pass(name);
            h.succeed();
        } catch (Throwable t) {
            fail(name, t);
            throw t instanceof RuntimeException re ? re : new RuntimeException(t);
        } finally {
            cleanup.run();
        }
    }

    public static void begin(GameTestHelper h, String name) {
        LotrLog.info("test", "START {}", name);
        NemesisConfig.testAnyDimension = true;
        io.github.sethwillemsvg.lotrrevival.events.LoadGovernor.frozen = true;
        io.github.sethwillemsvg.lotrrevival.events.LoadGovernor.reset();
        NemesisRandom.forTest(name.hashCode());
        MinecraftServer s = h.getLevel().getServer();
        if (s.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            s.setDifficulty(Difficulty.NORMAL, true);
            LotrLog.info("test", "{}: difficulty was PEACEFUL, set to NORMAL", name);
        }
        clear(s);
    }

    public static void pass(String name) {
        LotrLog.info("test", "PASS {}: ok", name);
    }

    public static void fail(String name, Throwable t) {
        LotrLog.info("test", "FAIL {}: {}", name, t.getMessage());
    }

    /** Retreats every body and forgets the records of the factions the tests use. */
    public static void clear(MinecraftServer s) {
        NemesisSavedData d = NemesisSavedData.get(s);
        for (NemesisRecord r : new ArrayList<>(d.records.values())) if (Manifestation.body(r.id) != null) Manifestation.retreat(s, r, "test cleanup");
        for (ResourceLocation f : List.of(MORDOR, GONDOR, BRIGAND)) d.clearFactionForTest(f);
        NemesisRandom.reset();
        Manifestation.testRetreatDelayTicks = -1;
        Manifestation.testRetreatDistance = -1;
        Ambush.testSafeRadius = -1;
    }

    /** A mock player in survival with this Middle-earth faction (null: none), standing at the test's relative position. */
    public static ServerPlayer player(GameTestHelper h, @Nullable ResourceLocation faction, BlockPos rel) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        var abs = h.absoluteVec(rel.getCenter());
        p.moveTo(abs.x, abs.y, abs.z);
        setFaction(p, faction);
        return p;
    }

    public static void setFaction(ServerPlayer p, @Nullable ResourceLocation faction) {
        PlayerData d = StateSaverAndLoader.getPlayerState(p);
        if (d != null) d.assignNewFactionInformation(faction, null);
    }

    public static void setReputation(ServerPlayer p, ResourceLocation faction, int value) {
        ReputationHooks.Fallback.setForTest(p.getUUID(), faction, value);
    }

    public static void remove(@Nullable ServerPlayer p) {
        if (p == null) return;
        setFaction(p, null);
        ReputationHooks.Fallback.clearForTest(p.getUUID());
        Ambush.SESSION_START.remove(p.getUUID());
        p.getServer().getPlayerList().remove(p);
    }

    /** A Middle-earth NPC of this type, no AI, added to the level. */
    public static NpcEntity npc(GameTestHelper h, ResourceLocation type, BlockPos rel) {
        NpcEntity npc = new NpcEntityBuilder(h.getLevel(), h.absolutePos(rel)).withNpcType(type).build();
        if (npc == null) throw new IllegalStateException("Middle-earth could not build " + type);
        npc.setNoAi(true);
        h.getLevel().addFreshEntity(npc);
        return npc;
    }
}
