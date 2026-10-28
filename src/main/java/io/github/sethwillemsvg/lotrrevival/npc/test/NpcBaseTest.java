package io.github.sethwillemsvg.lotrrevival.npc.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleData;
import io.github.sethwillemsvg.lotrrevival.npc.entity.HobbitEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcGender;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.item.NpcItems;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** NPC base entity, role data and the hobbit civilian. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcBaseTest {
    private NpcBaseTest() {}

    private static HobbitEntity newHobbit(Level level) {
        return NpcEntityTypes.HOBBIT.get().create(level);
    }

    @GameTest(template = "empty")
    public static void hobbitStats(GameTestHelper helper) {
        HobbitEntity hobbit = newHobbit(helper.getLevel());
        helper.assertTrue(hobbit.getMaxHealth() == 16.0f, "max health " + hobbit.getMaxHealth());
        helper.assertTrue(hobbit.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.24, "speed");
        helper.assertTrue(hobbit.getAttributeValue(Attributes.ATTACK_DAMAGE) == 2.0, "attack damage");
        helper.assertTrue(hobbit.getAttributeValue(Attributes.FOLLOW_RANGE) == 16.0, "follow range");
        helper.assertTrue(hobbit.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0.0, "knockback resistance");
        helper.assertTrue(hobbit.getBbWidth() == 0.45f, "width " + hobbit.getBbWidth());
        helper.assertTrue(hobbit.getBbHeight() == 1.25f, "height " + hobbit.getBbHeight());
        helper.assertTrue(
                Math.abs(hobbit.getEyeHeight() - 1.05f) < 1.0e-4f, "eye height " + hobbit.getEyeHeight());
        helper.assertTrue(!hobbit.wantsToPickUp(new ItemStack(Items.DIAMOND)), "must not pick up items");
        helper.assertTrue(
                NpcEntityTypes.HOBBIT.get().is(ModTags.EntityTypes.HOBBITS), "hobbit is in the hobbits entity tag");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnEggAndFinalizeSpawn(GameTestHelper helper) {
        helper.assertTrue(
                SpawnEggItem.byId(NpcEntityTypes.HOBBIT.get()) == NpcItems.HOBBIT_SPAWN_EGG.get(),
                "spawn egg maps to hobbit");
        Set<NpcRole> seen = EnumSet.noneOf(NpcRole.class);
        Set<NpcGender> genders = EnumSet.noneOf(NpcGender.class);
        for (int i = 0; i < 400; i++) {
            HobbitEntity hobbit = newHobbit(helper.getLevel());
            hobbit.finalizeSpawn(
                    helper.getLevel(),
                    helper.getLevel().getCurrentDifficultyAt(hobbit.blockPosition()),
                    MobSpawnType.SPAWN_EGG,
                    null);
            seen.add(hobbit.getRole());
            genders.add(hobbit.getGender());
        }
        helper.assertTrue(!seen.contains(NpcRole.INNKEEPER), "innkeeper must never be rolled");
        helper.assertTrue(
                seen.equals(EnumSet.of(NpcRole.VILLAGER, NpcRole.FARMER, NpcRole.TRAVELLER)), "roles seen " + seen);
        helper.assertTrue(genders.size() == 2, "both genders appear, saw " + genders);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nbtRoundTrip(GameTestHelper helper) {
        HobbitEntity original = newHobbit(helper.getLevel());
        original.setRole(NpcRole.INNKEEPER);
        original.setGender(NpcGender.FEMALE);
        original.setNpcName("Test Name");
        original.setNameSeed(123456789L);
        original.setHome(new BlockPos(10, 64, -3), 12);

        CompoundTag tag = original.saveWithoutId(new CompoundTag());
        helper.assertTrue(!tag.contains("Faction"), "faction must not be saved");

        HobbitEntity loaded = newHobbit(helper.getLevel());
        loaded.load(tag);
        helper.assertTrue(loaded.getRole() == NpcRole.INNKEEPER, "role " + loaded.getRole());
        helper.assertTrue(loaded.getGender() == NpcGender.FEMALE, "gender " + loaded.getGender());
        helper.assertTrue("Test Name".equals(loaded.getNpcName()), "name " + loaded.getNpcName());
        helper.assertTrue(loaded.getNameSeed() == 123456789L, "seed " + loaded.getNameSeed());
        helper.assertTrue(
                loaded.homePos().filter(new BlockPos(10, 64, -3)::equals).isPresent(),
                "home pos " + loaded.homePos());
        helper.assertTrue(loaded.homeRadius() == 12, "home radius " + loaded.homeRadius());

        HobbitEntity homeless = newHobbit(helper.getLevel());
        HobbitEntity loadedHomeless = newHobbit(helper.getLevel());
        loadedHomeless.load(homeless.saveWithoutId(new CompoundTag()));
        helper.assertTrue(loadedHomeless.homePos().isEmpty(), "no home stays no home");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void factionAndKillValues(GameTestHelper helper) {
        HobbitEntity hobbit = newHobbit(helper.getLevel());
        helper.assertTrue(
                FactionRelations.factionOf(hobbit).filter(Factions.HOBBIT::equals).isPresent(),
                "factionOf " + FactionRelations.factionOf(hobbit));
        helper.assertTrue(hobbit.isCivilian(), "isCivilian");
        hobbit.setRole(NpcRole.VILLAGER);
        helper.assertTrue(hobbit.alignmentKillValue() == 1.0f, "villager " + hobbit.alignmentKillValue());
        hobbit.setRole(NpcRole.FARMER);
        helper.assertTrue(hobbit.alignmentKillValue() == 1.0f, "farmer " + hobbit.alignmentKillValue());
        hobbit.setRole(NpcRole.INNKEEPER);
        helper.assertTrue(hobbit.alignmentKillValue() == 2.0f, "innkeeper " + hobbit.alignmentKillValue());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void roleDataLoaded(GameTestHelper helper) {
        var innkeeper = NpcRoleData.stats(NpcEntityTypes.HOBBIT.get(), NpcRole.INNKEEPER);
        helper.assertTrue(innkeeper.isPresent(), "innkeeper entry loaded from npc_roles/hobbit.json");
        helper.assertTrue(innkeeper.get().tradeMin().filter(v -> v == 0.0f).isPresent(), "trade_min 0");
        helper.assertTrue(innkeeper.get().killValue() == 2.0f, "kill_value 2");
        helper.assertTrue(
                NpcRoleData.stats(NpcEntityTypes.HOBBIT.get(), NpcRole.VILLAGER)
                        .filter(s -> s.tradeMin().isEmpty() && s.killValue() == 1.0f)
                        .isPresent(),
                "villager has no trade_min and kill_value 1");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void playerKillPostsOneFactionKillEvent(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<FactionKillEvent> seen = new ArrayList<>();
        Consumer<FactionKillEvent> listener = seen::add;
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL, false, FactionKillEvent.class, listener);
        try {
            HobbitEntity hobbit = newHobbit(helper.getLevel());
            hobbit.setPos(player.position());
            hobbit.setRole(NpcRole.VILLAGER);
            hobbit.die(helper.getLevel().damageSources().playerAttack(player));
            helper.assertTrue(seen.size() == 1, "expected exactly one kill event, got " + seen.size());
            FactionKillEvent event = seen.get(0);
            helper.assertTrue(event.getBaseValue() == 1.0f, "base value " + event.getBaseValue());
            helper.assertTrue(event.isCivilian(), "civilian");
            helper.assertTrue(Factions.HOBBIT.equals(event.getVictimFaction()), "victim faction");
            helper.assertTrue(event.getPlayer() == player, "killer");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void portalDoesNotChangeDimension(GameTestHelper helper) {
        BlockPos portal = new BlockPos(2, 2, 2);
        helper.setBlock(portal, Blocks.NETHER_PORTAL);
        HobbitEntity hobbit = helper.spawn(NpcEntityTypes.HOBBIT.get(), portal);
        hobbit.setNoAi(true);
        helper.assertTrue(!hobbit.canChangeDimensions(helper.getLevel(), helper.getLevel()), "canChangeDimensions");
        helper.assertTrue(!hobbit.canUsePortal(true), "canUsePortal");
        Level before = hobbit.level();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!hobbit.isRemoved(), "hobbit must still exist");
            helper.assertTrue(hobbit.level() == before, "hobbit must stay in its dimension");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void summonedHobbitKeepsEntityType(GameTestHelper helper) {
        NpcEntity npc = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(2, 2, 2));
        helper.assertTrue(npc.getType() == EntityType.byString("lotrrevival:hobbit").orElseThrow(), "type id");
        helper.assertTrue(npc.race().id().equals("hobbit"), "race");
        helper.succeed();
    }

    // ------------------------------------------------------------------ doors and gates

    private static final BlockPos BARRIER = new BlockPos(8, 0, 20);

    /** A stone wall across the arena at x=8 so the only short route east is through the barrier block(s). */
    private static void buildWall(GameTestHelper helper) {
        for (int z = 0; z < 40; z++) {
            for (int y = 0; y <= 3; y++) {
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
            }
        }
    }

    /** Spawns a hobbit west of the wall and keeps ordering it to a point east of it. Returns it. */
    private static HobbitEntity sendAcross(GameTestHelper helper) {
        HobbitEntity hobbit = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(4, 0, 20));
        net.minecraft.world.phys.Vec3 target = helper.absoluteVec(new net.minecraft.world.phys.Vec3(12.5, 0, 20.5));
        long[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0]++ % 10 == 0) {
                hobbit.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            }
        });
        return hobbit;
    }

    private static boolean eastOfWall(GameTestHelper helper, HobbitEntity hobbit) {
        return helper.relativeVec(hobbit.position()).x > 8.9;
    }

    @GameTest(template = "npc_arena", timeoutTicks = 400)
    public static void hobbitOpensAndClosesWoodenDoor(GameTestHelper helper) {
        buildWall(helper);
        helper.setBlock(
                BARRIER,
                Blocks.OAK_DOOR
                        .defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(
                BARRIER.above(),
                Blocks.OAK_DOOR
                        .defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST)
                        .setValue(
                                net.minecraft.world.level.block.DoorBlock.HALF,
                                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        HobbitEntity hobbit = sendAcross(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(eastOfWall(helper, hobbit), "hobbit should have walked through the wooden door, at " + helper.relativeVec(hobbit.position()));
            helper.assertBlockProperty(BARRIER, net.minecraft.world.level.block.DoorBlock.OPEN, false);
        });
    }

    @GameTest(template = "npc_arena", timeoutTicks = 400)
    public static void hobbitOpensAndClosesFenceGate(GameTestHelper helper) {
        buildWall(helper);
        helper.setBlock(
                BARRIER,
                Blocks.OAK_FENCE_GATE
                        .defaultBlockState()
                        .setValue(
                                net.minecraft.world.level.block.FenceGateBlock.FACING,
                                net.minecraft.core.Direction.EAST));
        // A gate is one block high, so open the cell above it for the hobbit's 1.25 block height.
        helper.setBlock(BARRIER.above(), Blocks.AIR);
        HobbitEntity hobbit = sendAcross(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(eastOfWall(helper, hobbit), "hobbit should have walked through the fence gate, at " + helper.relativeVec(hobbit.position()));
            helper.assertBlockProperty(BARRIER, net.minecraft.world.level.block.FenceGateBlock.OPEN, false);
        });
    }

    @GameTest(template = "npc_arena", timeoutTicks = 400)
    public static void hobbitIgnoresIronDoor(GameTestHelper helper) {
        buildWall(helper);
        helper.setBlock(
                BARRIER,
                Blocks.IRON_DOOR
                        .defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(
                BARRIER.above(),
                Blocks.IRON_DOOR
                        .defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST)
                        .setValue(
                                net.minecraft.world.level.block.DoorBlock.HALF,
                                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        HobbitEntity hobbit = sendAcross(helper);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(!eastOfWall(helper, hobbit), "hobbit must not pass an iron door");
            helper.assertBlockProperty(BARRIER, net.minecraft.world.level.block.DoorBlock.OPEN, false);
            helper.succeed();
        });
    }
}
