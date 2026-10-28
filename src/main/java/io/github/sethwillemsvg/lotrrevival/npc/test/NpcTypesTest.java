package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcLoadoutData;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleData;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.entity.StandardNpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.item.NpcItems;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Bounder, Shirriff and Bree types: stats, roles, kill values, loadouts, drops. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcTypesTest {
    private NpcTypesTest() {}

    private static StandardNpcEntity create(GameTestHelper helper, EntityType<StandardNpcEntity> type) {
        return type.create(helper.getLevel());
    }

    private static void finalizeSpawn(GameTestHelper helper, NpcEntity npc) {
        npc.finalizeSpawn(
                helper.getLevel(),
                helper.getLevel().getCurrentDifficultyAt(npc.blockPosition()),
                MobSpawnType.SPAWN_EGG,
                null);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.0e-4f;
    }

    @GameTest(template = "empty")
    public static void typeStatsTagsAndEggs(GameTestHelper helper) {
        record Expect(EntityType<StandardNpcEntity> type, float hp, float width, float height, float eye,
                double speed, double knockback, boolean hobbit) {}
        List<Expect> expected = List.of(
                new Expect(NpcEntityTypes.HOBBIT_BOUNDER.get(), 20, 0.45f, 1.25f, 1.05f, 0.24, 0.1, true),
                new Expect(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), 20, 0.45f, 1.25f, 1.05f, 0.24, 0.1, true),
                new Expect(NpcEntityTypes.BREE_HOBBIT.get(), 16, 0.45f, 1.25f, 1.05f, 0.24, 0.0, true),
                new Expect(NpcEntityTypes.BREE_MAN.get(), 20, 0.6f, 1.8f, 1.62f, 0.23, 0.0, false),
                new Expect(NpcEntityTypes.BREE_GUARD.get(), 20, 0.6f, 1.8f, 1.62f, 0.23, 0.1, false));
        for (Expect e : expected) {
            StandardNpcEntity npc = create(helper, e.type());
            String id = String.valueOf(EntityType.getKey(e.type()));
            helper.assertTrue(npc.getMaxHealth() == e.hp(), id + " max health " + npc.getMaxHealth());
            helper.assertTrue(near(npc.getBbWidth(), e.width()), id + " width " + npc.getBbWidth());
            helper.assertTrue(near(npc.getBbHeight(), e.height()), id + " height " + npc.getBbHeight());
            helper.assertTrue(near(npc.getEyeHeight(), e.eye()), id + " eye height " + npc.getEyeHeight());
            helper.assertTrue(npc.getAttributeValue(Attributes.MOVEMENT_SPEED) == e.speed(), id + " speed");
            helper.assertTrue(
                    Math.abs(npc.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - e.knockback()) < 1.0e-9,
                    id + " knockback resistance " + npc.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            helper.assertTrue(e.type().is(e.hobbit() ? ModTags.EntityTypes.HOBBITS : ModTags.EntityTypes.MEN), id + " race tag");
            helper.assertTrue(!e.type().is(e.hobbit() ? ModTags.EntityTypes.MEN : ModTags.EntityTypes.HOBBITS), id + " wrong race tag");
            helper.assertTrue(
                    SpawnEggItem.byId(e.type()) != null, id + " has a spawn egg");
        }
        helper.assertTrue(NpcItems.spawnEggs().size() == 6, "six npc spawn eggs");
        helper.assertTrue(
                create(helper, NpcEntityTypes.BREE_GUARD.get()).faction().equals(Factions.BREE), "bree guard faction");
        helper.assertTrue(
                create(helper, NpcEntityTypes.HOBBIT_BOUNDER.get()).faction().equals(Factions.HOBBIT),
                "bounder faction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void typesSummonByIdAndTitles(GameTestHelper helper) {
        for (String id : List.of("hobbit_bounder", "hobbit_shirriff", "bree_hobbit", "bree_man", "bree_guard")) {
            EntityType<?> type = EntityType.byString(LOTRRevival.MOD_ID + ":" + id).orElse(null);
            helper.assertTrue(type != null, id + " is registered");
            NpcEntity npc = (NpcEntity) helper.spawn((EntityType<? extends NpcEntity>) type, new BlockPos(2, 2, 2));
            helper.assertTrue(npc.isAlive(), id + " summoned");
            npc.discard();
        }
        StandardNpcEntity bounder = create(helper, NpcEntityTypes.HOBBIT_BOUNDER.get());
        helper.assertTrue(
                bounder.titleKey().filter("npc.lotrrevival.title.hobbit_bounder"::equals).isPresent(), "bounder title");
        StandardNpcEntity man = create(helper, NpcEntityTypes.BREE_MAN.get());
        man.setRole(NpcRole.FARMER);
        helper.assertTrue(
                man.titleKey().filter("npc.lotrrevival.role.farmer"::equals).isPresent(), "civilian reuses role key");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void roleDataLoadedForNewTypes(GameTestHelper helper) {
        var shirriff = NpcRoleData.stats(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), NpcRole.UNIT_TRADER);
        helper.assertTrue(shirriff.isPresent(), "shirriff unit_trader entry");
        helper.assertTrue(shirriff.get().tradeMin().filter(v -> v == 50.0f).isPresent(), "shirriff trade_min 50");
        helper.assertTrue(shirriff.get().hireMin().filter(v -> v == 50.0f).isPresent(), "shirriff hire_min 50");
        helper.assertTrue(shirriff.get().killValue() == 5.0f, "shirriff kill value");
        var innkeeper = NpcRoleData.stats(NpcEntityTypes.BREE_MAN.get(), NpcRole.INNKEEPER);
        helper.assertTrue(
                innkeeper.isPresent() && innkeeper.get().tradeMin().filter(v -> v == 0.0f).isPresent(),
                "bree man innkeeper trade_min 0");
        helper.assertTrue(
                NpcRoleData.stats(NpcEntityTypes.BREE_HOBBIT.get(), NpcRole.INNKEEPER).isPresent(),
                "bree hobbit innkeeper entry");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void killValuesViaFactionKillEvent(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<FactionKillEvent> seen = new ArrayList<>();
        Consumer<FactionKillEvent> listener = seen::add;
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL, false, FactionKillEvent.class, listener);
        try {
            check(helper, player, seen, NpcEntityTypes.BREE_MAN.get(), NpcRole.VILLAGER, 1.0f, "bree_man");
            check(helper, player, seen, NpcEntityTypes.BREE_MAN.get(), NpcRole.INNKEEPER, 2.0f, "bree_man innkeeper");
            check(helper, player, seen, NpcEntityTypes.HOBBIT_BOUNDER.get(), NpcRole.VILLAGER, 2.0f, "hobbit_bounder");
            check(helper, player, seen, NpcEntityTypes.BREE_GUARD.get(), NpcRole.VILLAGER, 2.0f, "bree_guard");
            check(helper, player, seen, NpcEntityTypes.HOBBIT_SHIRRIFF.get(), NpcRole.UNIT_TRADER, 5.0f, "hobbit_shirriff");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void check(
            GameTestHelper helper,
            ServerPlayer player,
            List<FactionKillEvent> seen,
            EntityType<StandardNpcEntity> type,
            NpcRole role,
            float expected,
            String label) {
        seen.clear();
        StandardNpcEntity npc = create(helper, type);
        npc.setPos(player.position());
        npc.setRole(role);
        npc.die(helper.getLevel().damageSources().playerAttack(player));
        helper.assertTrue(seen.size() == 1, label + ": expected one kill event, got " + seen.size());
        helper.assertTrue(seen.get(0).getBaseValue() == expected, label + " base value " + seen.get(0).getBaseValue());
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void breeGuardLoadoutAndNoEquipmentDrops(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            for (int i = 0; i < 20; i++) {
                StandardNpcEntity guard = helper.spawn(NpcEntityTypes.BREE_GUARD.get(), new BlockPos(2, 2, 2));
                finalizeSpawn(helper, guard);
                helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "iron sword");
                helper.assertTrue(guard.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE), "leather chest");
                helper.assertTrue(guard.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "leather helmet");
                helper.assertTrue(
                        guard.getItemBySlot(EquipmentSlot.CHEST).has(net.minecraft.core.component.DataComponents.DYED_COLOR),
                        "chest is dyed");
                guard.setLastHurtByPlayer(player);
                guard.die(helper.getLevel().damageSources().playerAttack(player));
                guard.discard();
            }
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64));
            for (ItemEntity drop : drops) {
                ItemStack stack = drop.getItem();
                helper.assertTrue(stack.is(Items.BREAD), "only bread may drop, got " + stack);
                drop.discard();
            }
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void otherLoadouts(GameTestHelper helper) {
        StandardNpcEntity bounder = helper.spawn(NpcEntityTypes.HOBBIT_BOUNDER.get(), new BlockPos(2, 2, 2));
        finalizeSpawn(helper, bounder);
        helper.assertTrue(bounder.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "bounder sword");
        helper.assertTrue(bounder.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "bounder cap");
        int bounderDye = bounder.getItemBySlot(EquipmentSlot.HEAD)
                .get(net.minecraft.core.component.DataComponents.DYED_COLOR)
                .rgb();
        StandardNpcEntity shirriff = helper.spawn(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), new BlockPos(3, 2, 2));
        finalizeSpawn(helper, shirriff);
        helper.assertTrue(shirriff.getRole() == NpcRole.UNIT_TRADER, "shirriff role");
        helper.assertTrue(shirriff.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "shirriff sword");
        int shirriffDye = shirriff.getItemBySlot(EquipmentSlot.HEAD)
                .get(net.minecraft.core.component.DataComponents.DYED_COLOR)
                .rgb();
        helper.assertTrue(bounderDye != shirriffDye, "shirriff cap has a different dye");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void civilianRoleLoadouts(GameTestHelper helper) {
        boolean farmerHoe = false;
        boolean travellerBoots = false;
        for (int i = 0; i < 300; i++) {
            StandardNpcEntity man = create(helper, NpcEntityTypes.BREE_MAN.get());
            finalizeSpawn(helper, man);
            switch (man.getRole()) {
                case FARMER -> {
                    helper.assertTrue(man.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.WOODEN_HOE), "farmer hoe");
                    farmerHoe = true;
                }
                case TRAVELLER -> {
                    helper.assertTrue(man.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS), "traveller boots");
                    travellerBoots = true;
                }
                default -> helper.assertTrue(man.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "villager empty hand");
            }
        }
        helper.assertTrue(farmerHoe && travellerBoots, "saw both farmer and traveller");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loadoutDataSwapChangesNextSpawn(GameTestHelper helper) {
        var original = NpcLoadoutData.snapshot();
        try {
            String json = "{\"default\":{\"mainhand\":[{\"item\":\"minecraft:diamond_sword\"}]}}";
            JsonElement element = JsonParser.parseString(json);
            NpcLoadoutData.reloadFrom(Map.of(LOTRRevival.id("bree_guard"), element));
            StandardNpcEntity swapped = create(helper, NpcEntityTypes.BREE_GUARD.get());
            finalizeSpawn(helper, swapped);
            helper.assertTrue(swapped.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD), "diamond sword after swap");
            helper.assertTrue(swapped.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "chest empty after swap");
        } finally {
            NpcLoadoutData.restore(original);
        }
        StandardNpcEntity restored = create(helper, NpcEntityTypes.BREE_GUARD.get());
        finalizeSpawn(helper, restored);
        helper.assertTrue(restored.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "sword restored");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breeHobbitNeverTravellerOrInnkeeper(GameTestHelper helper) {
        Set<NpcRole> seen = EnumSet.noneOf(NpcRole.class);
        for (int i = 0; i < 200; i++) {
            StandardNpcEntity hobbit = create(helper, NpcEntityTypes.BREE_HOBBIT.get());
            finalizeSpawn(helper, hobbit);
            seen.add(hobbit.getRole());
        }
        helper.assertTrue(seen.equals(EnumSet.of(NpcRole.VILLAGER, NpcRole.FARMER)), "roles seen " + seen);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breeManRollsTraveller(GameTestHelper helper) {
        Set<NpcRole> seen = EnumSet.noneOf(NpcRole.class);
        for (int i = 0; i < 300; i++) {
            StandardNpcEntity man = create(helper, NpcEntityTypes.BREE_MAN.get());
            finalizeSpawn(helper, man);
            seen.add(man.getRole());
        }
        helper.assertTrue(
                seen.equals(EnumSet.of(NpcRole.VILLAGER, NpcRole.FARMER, NpcRole.TRAVELLER)), "roles seen " + seen);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void experienceRanges(GameTestHelper helper) {
        checkXp(helper, NpcEntityTypes.BREE_MAN.get(), 1, 3);
        checkXp(helper, NpcEntityTypes.BREE_HOBBIT.get(), 1, 3);
        checkXp(helper, NpcEntityTypes.HOBBIT_BOUNDER.get(), 2, 4);
        checkXp(helper, NpcEntityTypes.BREE_GUARD.get(), 3, 5);
        checkXp(helper, NpcEntityTypes.HOBBIT_SHIRRIFF.get(), 3, 5);
        helper.succeed();
    }

    private static void checkXp(GameTestHelper helper, EntityType<StandardNpcEntity> type, int min, int max) {
        StandardNpcEntity npc = create(helper, type);
        finalizeSpawn(helper, npc);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = 0; i < 300; i++) {
            int xp = npc.getExperienceReward(helper.getLevel(), null);
            low = Math.min(low, xp);
            high = Math.max(high, xp);
        }
        String id = String.valueOf(EntityType.getKey(type));
        helper.assertTrue(low == min && high == max, id + " xp range " + low + "-" + high);
    }
}
