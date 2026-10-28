package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelation;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.friendlyfire.NpcPlayerPrefs;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.NpcUnitData;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitEntry;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitList;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitReleasedEvent;
import io.github.sethwillemsvg.lotrrevival.npc.hire.progress.UnitDeathEvent;
import io.github.sethwillemsvg.lotrrevival.npc.hire.progress.UnitLevelUpEvent;
import io.github.sethwillemsvg.lotrrevival.npc.hire.progress.UnitLevels;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeech;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeechLang;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Unit XP, levels, kill count, death and speech categories (ticket #71). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcUnitProgressTest {
    private NpcUnitProgressTest() {}

    // ------------------------------------------------------------------ fixtures

    private static ServerPlayer mockPlayer(GameTestHelper helper, int x, int z) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "npc-progress-mock"), false);
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

    private static NpcEntity spawn(GameTestHelper helper, EntityType<? extends NpcEntity> type, int x, int z) {
        NpcEntity entity = helper.spawn(type, new BlockPos(x, 0, z));
        entity.setPersistenceRequired();
        return entity;
    }

    private static NpcEntity hiredBounder(GameTestHelper helper, ServerPlayer owner, int x, int z) {
        NpcEntity unit = spawn(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), x, z);
        UnitList list = NpcUnitData.list(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), NpcRole.UNIT_TRADER)
                .orElseThrow(() -> new AssertionError("shirriff unit list missing"));
        UnitEntry bounder = list.entry("bounder").orElseThrow(() -> new AssertionError("bounder entry missing"));
        HiredUnits.hire(unit, owner, list, bounder, "");
        return unit;
    }

    private static void setFaction(NpcEntity unit, ResourceKey<Faction> faction) {
        HiredUnit s = HiredUnits.get(unit);
        HiredUnits.set(
                unit,
                new HiredUnit(
                        s.owner(),
                        s.ownerName(),
                        s.requiredAlignment(),
                        s.pledge(),
                        faction.location(),
                        s.squadron(),
                        s.task(),
                        s.command(),
                        s.progress(),
                        s.originalGear()));
    }

    private static void kill(GameTestHelper helper, NpcEntity unit, net.minecraft.world.entity.LivingEntity victim) {
        victim.hurt(helper.getLevel().damageSources().mobAttack(unit), 1000.0f);
        helper.assertTrue(!victim.isAlive(), "victim should be dead");
    }

    // ------------------------------------------------------------------ formula

    @GameTest(template = "empty")
    public static void levelFormulaMatchesSpecTable(GameTestHelper helper) {
        int[][] table = {
            {2, 3}, {3, 6}, {4, 10}, {5, 15}, {6, 20}, {8, 33}, {10, 49}, {15, 114}, {20, 227}, {25, 422}, {30, 750},
            {40, 2179}
        };
        for (int[] row : table) {
            helper.assertTrue(
                    UnitLevels.totalXpForLevel(row[0]) == row[1],
                    "L" + row[0] + " expected " + row[1] + " got " + UnitLevels.totalXpForLevel(row[0]));
        }
        helper.assertTrue(UnitLevels.totalXpForLevel(1) == 0, "level 1 needs 0");
        for (int level = 2; level <= 40; level++) {
            int total = UnitLevels.totalXpForLevel(level);
            helper.assertTrue(total > UnitLevels.totalXpForLevel(level - 1), "strictly increasing at L" + level);
            helper.assertTrue(UnitLevels.levelForXp(total) == level, "xp " + total + " is level " + level);
            helper.assertTrue(UnitLevels.levelForXp(total - 1) == level - 1, "xp " + (total - 1) + " is one lower");
        }
        helper.assertTrue(UnitLevels.levelForXp(0) == 1, "0 xp is level 1");
        helper.assertTrue(UnitLevels.levelForXp(100000) > 40, "no cap");
        helper.succeed();
    }

    // ------------------------------------------------------------------ xp and kills

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void enemyKillGivesXpOthersGiveNone(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        try {
            NpcEntity unit = hiredBounder(helper, owner, 8, 6);
            unit.setInvulnerable(true);
            // Make the unit a Mordor unit so a Bree man is its enemy (the world has no enemy-faction NPC type yet).
            setFaction(unit, Factions.MORDOR);
            helper.assertTrue(
                    FactionRelations.relation(helper.getLevel().registryAccess(), Factions.MORDOR, Factions.BREE)
                            .isBad(),
                    "mordor and bree are enemies");

            kill(helper, unit, spawn(helper, NpcEntityTypes.BREE_MAN.get(), 9, 6));
            HiredUnit.Progress progress = HiredUnits.get(unit).progress();
            helper.assertTrue(progress.xp() == 1 && progress.kills() == 1, "enemy NPC: " + progress);

            net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(10, 0, 6));
            kill(helper, unit, zombie);
            progress = HiredUnits.get(unit).progress();
            helper.assertTrue(progress.xp() == 1 && progress.kills() == 2, "zombie counts as kill, no xp: " + progress);

            ResourceKey<Faction> neutral = null;
            for (ResourceKey<Faction> candidate : Factions.REAL) {
                if (FactionRelations.relation(helper.getLevel().registryAccess(), candidate, Factions.BREE)
                        == FactionRelation.NEUTRAL) {
                    neutral = candidate;
                    break;
                }
            }
            helper.assertTrue(neutral != null, "some faction is neutral to Bree");
            setFaction(unit, neutral);
            kill(helper, unit, spawn(helper, NpcEntityTypes.BREE_MAN.get(), 11, 6));
            progress = HiredUnits.get(unit).progress();
            helper.assertTrue(progress.xp() == 1 && progress.kills() == 3, "neutral NPC counts as kill, no xp: " + progress);
            unit.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void threeXpMakesLevelTwoWithMoreHealth(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        List<UnitLevelUpEvent> events = new ArrayList<>();
        Consumer<UnitLevelUpEvent> listener = events::add;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, UnitLevelUpEvent.class, listener);
        try {
            NpcEntity unit = hiredBounder(helper, owner, 8, 6);
            unit.setInvulnerable(true);
            setFaction(unit, Factions.MORDOR);
            double base = unit.getAttributeBaseValue(Attributes.MAX_HEALTH);
            unit.setHealth((float) base - 3.0f);
            float before = unit.getHealth();

            for (int i = 0; i < 2; i++) {
                kill(helper, unit, spawn(helper, NpcEntityTypes.BREE_MAN.get(), 9 + i, 6));
            }
            helper.assertTrue(HiredUnits.get(unit).progress().level() == 1, "level 1 at 2 xp");
            helper.assertTrue(events.isEmpty(), "no level up yet");
            kill(helper, unit, spawn(helper, NpcEntityTypes.BREE_MAN.get(), 12, 6));

            HiredUnit.Progress progress = HiredUnits.get(unit).progress();
            helper.assertTrue(progress.xp() == 3 && progress.level() == 2, "3 xp is level 2: " + progress);
            helper.assertTrue(unit.getMaxHealth() == base + 1.0, "max health " + unit.getMaxHealth() + " base " + base);
            helper.assertTrue(unit.getHealth() == before + 1.0f, "healed 1: " + unit.getHealth() + " from " + before);
            helper.assertTrue(events.size() == 1 && events.get(0).getNewLevel() == 2, "one level-up event");
            helper.assertTrue(events.get(0).getOwner().equals(owner.getUUID()), "event owner");

            // Release removes the bonus.
            HiredUnits.release(unit, UnitReleasedEvent.Reason.DISMISSED);
            helper.assertTrue(unit.getMaxHealth() == base, "bonus removed on release: " + unit.getMaxHealth());
            unit.discard();
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void levelPersistsAcrossSaveAndLoad(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        try {
            NpcEntity unit = hiredBounder(helper, owner, 8, 6);
            unit.setInvulnerable(true);
            double base = unit.getAttributeBaseValue(Attributes.MAX_HEALTH);
            UnitLevels.addXp(unit, 10);
            HiredUnit.Progress progress = HiredUnits.get(unit).progress();
            helper.assertTrue(progress.level() == 4 && progress.xp() == 10, "10 xp is level 4: " + progress);
            helper.assertTrue(unit.getMaxHealth() == base + 3.0, "max health " + unit.getMaxHealth());

            CompoundTag tag = new CompoundTag();
            unit.saveWithoutId(tag);
            NpcEntity loaded = NpcEntityTypes.HOBBIT_BOUNDER.get().create(helper.getLevel());
            helper.assertTrue(loaded != null, "created");
            loaded.load(tag);
            HiredUnit.Progress after = HiredUnits.get(loaded).progress();
            helper.assertTrue(after.level() == 4 && after.xp() == 10, "level survived: " + after);
            helper.assertTrue(loaded.getMaxHealth() == base + 3.0, "max health survived: " + loaded.getMaxHealth());
            unit.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    // ------------------------------------------------------------------ death

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void deadUnitDropsGivenHelmetAndNoCoins(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        List<UnitDeathEvent> events = new ArrayList<>();
        Consumer<UnitDeathEvent> listener = events::add;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, UnitDeathEvent.class, listener);
        try {
            NpcEntity unit = hiredBounder(helper, owner, 8, 6);
            HiredUnits.set(unit, HiredUnits.get(unit).withOriginalGear(EquipmentSlot.HEAD, ItemStack.EMPTY));
            unit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            Vec3 at = unit.position();

            unit.hurt(helper.getLevel().damageSources().generic(), 1000.0f);
            helper.assertTrue(!unit.isAlive(), "unit died");

            AABB area = new AABB(at, at).inflate(3.0);
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area);
            boolean helmet = false;
            for (ItemEntity drop : drops) {
                ItemStack stack = drop.getItem();
                helper.assertTrue(!stack.is(ModTags.Items.COINS), "no coins dropped: " + stack);
                helmet |= stack.is(Items.DIAMOND_HELMET);
            }
            helper.assertTrue(helmet, "player-given helmet dropped");
            helper.assertTrue(events.size() == 1, "one death event, got " + events.size());
            helper.assertTrue(events.get(0).wasOwnerNotified(), "owner told");
            helper.assertTrue(events.get(0).getOwner().equals(owner.getUUID()), "event owner");
            drops.forEach(ItemEntity::discard);
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void deathMessageToggleSilencesOwner(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        List<UnitDeathEvent> events = new ArrayList<>();
        Consumer<UnitDeathEvent> listener = events::add;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, UnitDeathEvent.class, listener);
        try {
            helper.assertTrue(NpcPlayerPrefs.get(owner).unitDeathMessages(), "on by default");
            helper.getLevel()
                    .getServer()
                    .getCommands()
                    .performPrefixedCommand(owner.createCommandSourceStack(), "lotr npc unitdeathmessages off");
            helper.assertTrue(!NpcPlayerPrefs.get(owner).unitDeathMessages(), "command turned it off");

            NpcEntity unit = hiredBounder(helper, owner, 8, 6);
            unit.hurt(helper.getLevel().damageSources().generic(), 1000.0f);
            helper.assertTrue(events.size() == 1, "one death event, got " + events.size());
            helper.assertTrue(!events.get(0).wasOwnerNotified(), "no message with the toggle off");

            helper.getLevel()
                    .getServer()
                    .getCommands()
                    .performPrefixedCommand(owner.createCommandSourceStack(), "lotr npc unitdeathmessages on");
            helper.assertTrue(NpcPlayerPrefs.get(owner).unitDeathMessages(), "command turned it on");
            NpcEntity second = hiredBounder(helper, owner, 9, 6);
            second.hurt(helper.getLevel().damageSources().generic(), 1000.0f);
            helper.assertTrue(events.size() == 2 && events.get(1).wasOwnerNotified(), "message again with it on");
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, owner);
        }
    }

    // ------------------------------------------------------------------ speech

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void ownerSneakTalkUsesHiredCategory(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 10);
        try {
            NpcEntity unit = hiredBounder(helper, owner, 8, 6);
            unit.setInvulnerable(true);
            owner.setShiftKeyDown(true);
            helper.assertTrue(
                    NpcSpeech.HIRED.equals(NpcSpeech.chooseCategory(unit, owner, unit.getRandom())),
                    "sneaking owner gets hired lines");
            owner.setShiftKeyDown(false);
            helper.assertTrue(
                    !NpcSpeech.HIRED.equals(NpcSpeech.chooseCategory(unit, owner, unit.getRandom())),
                    "standing owner does not");
            Alignment.set(owner, Factions.HOBBIT, 100, AlignmentSource.command());
            unit.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "empty")
    public static void everyRaceHasUnitSpeechLines(GameTestHelper helper) {
        for (NpcRace race : new NpcRace[] {NpcRace.HOBBIT, NpcRace.MAN}) {
            for (String category : new String[] {NpcSpeech.HIRED, NpcSpeech.HIRED_KILL, NpcSpeech.HIRED_IDLE}) {
                helper.assertTrue(
                        NpcSpeechLang.count(race, category) >= 4, race.id() + " " + category + " needs 4+ lines");
            }
        }
        helper.succeed();
    }
}
