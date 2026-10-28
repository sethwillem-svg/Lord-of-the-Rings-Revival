package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.friendlyfire.FriendlyFire;
import io.github.sethwillemsvg.lotrrevival.npc.friendlyfire.NpcPlayerPrefs;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteractEvent;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteraction;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeech;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeechEvent;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** GameTests for friendly fire, retaliation and kill witnesses (ticket #42). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcFriendlyFireTest {
    private static final int Z = 20;

    private NpcFriendlyFireTest() {}

    // ------------------------------------------------------------------ fixtures

    /** A survival mock player that records the translation keys of the system messages it receives. */
    private static final class MockPlayer extends ServerPlayer {
        final List<String> messageKeys = new ArrayList<>();

        MockPlayer(GameTestHelper helper, CommonListenerCookie cookie) {
            super(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public void sendSystemMessage(Component component) {
            if (component.getContents() instanceof TranslatableContents translatable) {
                messageKeys.add(translatable.getKey());
            }
            super.sendSystemMessage(component);
        }

        long count(String key) {
            return messageKeys.stream().filter(key::equals).count();
        }
    }

    private static MockPlayer player(GameTestHelper helper, ResourceKey<Faction> faction, float alignment, int x, int z) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "npc-ff-mock"), false);
        MockPlayer player = new MockPlayer(helper, cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        Vec3 pos = helper.absoluteVec(new Vec3(x + 0.5, 0, z + 0.5));
        player.setPos(pos.x, pos.y, pos.z);
        Alignment.set(player, faction, alignment, AlignmentSource.command());
        return player;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static <T extends NpcEntity> T spawn(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T entity = helper.spawn(type, new BlockPos(x, 0, z));
        ((Mob) entity).setPersistenceRequired();
        return entity;
    }

    private static boolean hit(GameTestHelper helper, NpcEntity npc, ServerPlayer player, float amount) {
        return npc.hurt(helper.getLevel().damageSources().playerAttack(player), amount);
    }

    // ------------------------------------------------------------------ protection

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void friendlyHitDoesNothingAndHintShownOnce(GameTestHelper helper) {
        MockPlayer player = player(helper, Factions.BREE, 20.0f, 4, Z);
        NpcEntity man = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 6, Z);
        try {
            float before = man.getHealth();
            helper.assertTrue(!hit(helper, man, player, 4.0f), "friendly hit must be cancelled");
            helper.assertTrue(man.getHealth() == before, "no damage, health " + man.getHealth());
            helper.assertTrue(man.getTarget() == null, "no target after a cancelled hit");
            helper.assertTrue(player.count(FriendlyFire.HINT_KEY) == 1, "hint sent once: " + player.messageKeys);
            helper.assertTrue(NpcPlayerPrefs.get(player).friendlyFireHintShown(), "hint flag set");
            hit(helper, man, player, 4.0f);
            helper.assertTrue(player.count(FriendlyFire.HINT_KEY) == 1, "second hit sends no second hint");
            helper.assertTrue(Alignment.get(player, Factions.BREE) == 20.0f, "alignment unchanged");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void toggleOnLetsHitsLandAndAngersOnlyTheVictim(GameTestHelper helper) {
        MockPlayer player = player(helper, Factions.BREE, 20.0f, 2, Z);
        NpcEntity man = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 4, Z);
        NpcEntity guard = spawn(helper, NpcEntityTypes.BREE_GUARD.get(), 10, Z);
        try {
            NpcPlayerPrefs.set(player, NpcPlayerPrefs.get(player).withAllowFriendlyFire(true));
            float before = man.getHealth();
            helper.assertTrue(hit(helper, man, player, 4.0f), "hit lands with the toggle on");
            helper.assertTrue(man.getHealth() == before - 4.0f, "damage applied, health " + man.getHealth());
            helper.assertTrue(man.getTarget() == player, "bree_man targets the player");
            helper.runAfterDelay(30, () -> {
                try {
                    helper.assertTrue(guard.getTarget() != player, "a plain hit does not call the guard");
                    helper.assertTrue(Alignment.get(player, Factions.BREE) == 20.0f, "alignment unchanged");
                } finally {
                    remove(helper, player);
                }
                helper.succeed();
            });
        } catch (RuntimeException e) {
            remove(helper, player);
            throw e;
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void playerAtZeroIsNotProtected(GameTestHelper helper) {
        MockPlayer player = player(helper, Factions.BREE, 0.0f, 2, Z);
        NpcEntity man = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 4, Z);
        try {
            float before = man.getHealth();
            helper.assertTrue(hit(helper, man, player, 3.0f), "hit lands at alignment 0");
            helper.assertTrue(man.getHealth() == before - 3.0f, "damage applied");
            helper.assertTrue(man.getTarget() == player, "man targets the player");
            helper.assertTrue(player.count(FriendlyFire.HINT_KEY) == 0, "no hint at 0");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void targetedFriendlyPlayerMayDefendThemselves(GameTestHelper helper) {
        MockPlayer player = player(helper, Factions.BREE, 20.0f, 2, Z);
        NpcEntity man = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 4, Z);
        try {
            man.setTarget(player);
            helper.assertTrue(hit(helper, man, player, 2.0f), "an NPC that targets the player takes the hit");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void commandTogglesFriendlyFire(GameTestHelper helper) {
        MockPlayer player = player(helper, Factions.BREE, 0.0f, 2, Z);
        try {
            var server = helper.getLevel().getServer();
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "lotr npc friendlyfire on");
            helper.assertTrue(NpcPlayerPrefs.get(player).allowFriendlyFire(), "on");
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "lotr npc friendlyfire off");
            helper.assertTrue(!NpcPlayerPrefs.get(player).allowFriendlyFire(), "off");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ retaliation lifetime

    @GameTest(template = "npc_arena", timeoutTicks = 400)
    public static void retaliationClearsAfterTwoHundredTicksBeyondSenseRange(GameTestHelper helper) {
        MockPlayer player = player(helper, Factions.BREE, 0.0f, 2, Z);
        NpcEntity man = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 4, Z);
        helper.assertTrue(hit(helper, man, player, 1.0f), "hit lands");
        helper.assertTrue(man.retaliationTarget() == player, "retaliating");
        player.setPos(player.getX(), player.getY() + 30.0, player.getZ());
        helper.runAfterDelay(150, () -> helper.assertTrue(
                man.retaliationTarget() == player, "still retaliating after 150 ticks out of range"));
        helper.runAfterDelay(230, () -> {
            try {
                helper.assertTrue(man.retaliationTarget() == null, "retaliation cleared");
                helper.assertTrue(man.getTarget() == null, "target cleared");
            } finally {
                remove(helper, player);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void retaliationEndsOnLogout(GameTestHelper helper) {
        MockPlayer player = player(helper, Factions.BREE, 0.0f, 2, Z);
        NpcEntity man = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 4, Z);
        helper.assertTrue(hit(helper, man, player, 1.0f), "hit lands");
        helper.assertTrue(man.retaliationTarget() == player, "retaliating");
        remove(helper, player);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(man.retaliationTarget() == null, "cleared on logout");
            helper.succeed();
        });
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void hobbitCivilianFleesFromAHitInsteadOfFighting(GameTestHelper helper) {
        MockPlayer player = player(helper, Factions.HOBBIT, 0.0f, 2, Z);
        NpcEntity hobbit = spawn(helper, NpcEntityTypes.HOBBIT.get(), 4, Z);
        try {
            helper.assertTrue(hit(helper, hobbit, player, 1.0f), "hit lands");
            helper.assertTrue(hobbit.retaliationTarget() == null && hobbit.getTarget() == null, "hobbit does not fight");
            helper.assertTrue(hobbit.retaliation().isFleeingFrom(player), "hobbit flees the attacker");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ kills

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    public static void killTurnsNearbyAlliesOnTheKiller(GameTestHelper helper) {
        // High alignment (and the toggle on) so the kill's alignment loss cannot make the far guard hostile by itself.
        MockPlayer player = player(helper, Factions.BREE, 100.0f, 1, Z);
        NpcPlayerPrefs.set(player, NpcPlayerPrefs.get(player).withAllowFriendlyFire(true));
        NpcEntity victim = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 1, Z + 2);
        NpcEntity near = spawn(helper, NpcEntityTypes.BREE_GUARD.get(), 7, Z + 2);
        NpcEntity far = spawn(helper, NpcEntityTypes.BREE_GUARD.get(), 13, Z + 2);
        NpcEntity hobbit = spawn(helper, NpcEntityTypes.HOBBIT.get(), 1, Z + 8);
        List<NpcEntity> speakers = new ArrayList<>();
        Consumer<NpcSpeechEvent> listener = e -> {
            if (NpcSpeech.WITNESS.equals(e.getCategory())) {
                speakers.add(e.getNpc());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, NpcSpeechEvent.class, listener);
        try {
            helper.assertTrue(near.getTarget() == null && far.getTarget() == null, "idle before the kill");
            victim.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0f);
            helper.assertTrue(!victim.isAlive(), "victim dead");
            helper.assertTrue(near.getTarget() == player, "guard 6 blocks away targets the killer");
            helper.assertTrue(hobbit.retaliation().isFleeingFrom(player), "hobbit civilian 6 blocks away flees");
            helper.assertTrue(hobbit.getTarget() == null, "hobbit does not fight");
            helper.assertTrue(speakers.contains(near) && speakers.contains(hobbit), "both witnesses speak: " + speakers);
            helper.runAfterDelay(30, () -> {
                try {
                    helper.assertTrue(far.getTarget() != player, "guard 12 blocks away is not a witness");
                    helper.assertTrue(!speakers.contains(far), "far guard says nothing");
                } finally {
                    NeoForge.EVENT_BUS.unregister(listener);
                    remove(helper, player);
                }
                helper.succeed();
            });
        } catch (RuntimeException e) {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, player);
            throw e;
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void atMostFiveWitnessesSpeak(GameTestHelper helper) {
        MockPlayer player = player(helper, Factions.BREE, 0.0f, 1, Z);
        NpcEntity victim = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 1, Z + 2);
        List<NpcEntity> guards = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            guards.add(spawn(helper, NpcEntityTypes.BREE_GUARD.get(), 3 + i, Z + 3));
        }
        List<NpcEntity> speakers = new ArrayList<>();
        Consumer<NpcSpeechEvent> listener = e -> {
            if (NpcSpeech.WITNESS.equals(e.getCategory())) {
                speakers.add(e.getNpc());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, NpcSpeechEvent.class, listener);
        try {
            victim.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0f);
            long inRange = guards.stream().filter(g -> g.getTarget() == player).count();
            helper.assertTrue(inRange >= 6, "most guards within 8 blocks react, got " + inRange);
            helper.assertTrue(speakers.size() == 5, "exactly 5 witness lines, got " + speakers.size());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void killWithNoPlayerTriggersNothing(GameTestHelper helper) {
        NpcEntity victim = spawn(helper, NpcEntityTypes.BREE_MAN.get(), 4, Z);
        NpcEntity guard = spawn(helper, NpcEntityTypes.BREE_GUARD.get(), 7, Z);
        victim.hurt(helper.getLevel().damageSources().generic(), 1000.0f);
        helper.assertTrue(!victim.isAlive(), "victim dead");
        helper.assertTrue(guard.getTarget() == null && guard.retaliationTarget() == null, "guard stays idle");
        helper.succeed();
    }

    // ------------------------------------------------------------------ interaction thresholds

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void shirriffHireNeedsFifty(GameTestHelper helper) {
        NpcEntity shirriff = spawn(helper, NpcEntityTypes.HOBBIT_SHIRRIFF.get(), 4, Z);
        shirriff.setRole(NpcRole.UNIT_TRADER);
        MockPlayer player = player(helper, Factions.HOBBIT, 50.0f, 2, Z);
        List<NpcInteractEvent.Kind> kinds = new ArrayList<>();
        Consumer<NpcInteractEvent> listener = e -> {
            if (e.getNpc() == shirriff) {
                kinds.add(e.getKind());
                e.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, NpcInteractEvent.class, listener);
        try {
            NpcInteraction.onRightClick(shirriff, player);
            helper.assertTrue(kinds.equals(List.of(NpcInteractEvent.Kind.HIRE)), "at +50: " + kinds);
            kinds.clear();
            Alignment.set(player, Factions.HOBBIT, 49.0f, AlignmentSource.command());
            NpcInteraction.onRightClick(shirriff, player);
            helper.assertTrue(kinds.equals(List.of(NpcInteractEvent.Kind.TALK)), "at +49: " + kinds);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, player);
        }
        helper.succeed();
    }
}
