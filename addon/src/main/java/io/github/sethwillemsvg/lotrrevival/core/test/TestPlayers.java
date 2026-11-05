package io.github.sethwillemsvg.lotrrevival.core.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Mock players for game tests. Use this instead of {@code GameTestHelper.makeMockServerPlayerInLevel()}: on the headless
 * test server, Of Beasts and Wild Things sends {@code wild-things:farm_animal_variants} to every joining player, and the
 * vanilla mock connection refuses it ("may not be sent to the client"), which throws out of the join and fails the test.
 * Here the mock connection is configured like a NeoForge client with every registered channel, plus that payload as an
 * ad-hoc channel. Remove the player with {@link #remove} in a {@code finally}.
 */
public final class TestPlayers {
    /** Payloads other mods send on join that the mock connection must accept. */
    private static final Set<ResourceLocation> EXTRA_CHANNELS = Set.of(
            ResourceLocation.fromNamespaceAndPath("wild-things", "farm_animal_variants"));

    private TestPlayers() {}

    public static ServerPlayer mock(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return this.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        NetworkRegistry.onMinecraftRegister(connection, EXTRA_CHANNELS);
        level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        LotrLog.dbg("test", "mock player {} joined at {}", LotrLog.id(p.getUUID()), p.blockPosition().toShortString());
        return p;
    }

    public static void remove(ServerPlayer p) {
        if (p == null) return;
        try {
            p.getServer().getPlayerList().remove(p);
        } catch (RuntimeException e) {
            LotrLog.warn("test", "could not remove mock player {}: {}", LotrLog.id(p.getUUID()), e.toString());
        }
    }
}
