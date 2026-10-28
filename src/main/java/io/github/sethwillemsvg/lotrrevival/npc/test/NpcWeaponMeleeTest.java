package io.github.sethwillemsvg.lotrrevival.npc.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.ai.combat.NpcMelee;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.StandardNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Weapon-class melee timing and reach for NPCs (ticket #128). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcWeaponMeleeTest {
    private NpcWeaponMeleeTest() {}

    private static ItemStack stack(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(LOTRRevival.id(path)));
    }

    @GameTest(template = "empty")
    public static void cooldownFollowsSpeedFactor(GameTestHelper helper) {
        helper.assertTrue(NpcMelee.cooldownTicks(ItemStack.EMPTY) == 20, "bare hands 20");
        helper.assertTrue(NpcMelee.cooldownTicks(new ItemStack(Items.IRON_SWORD)) == 20, "vanilla sword 20");
        helper.assertTrue(NpcMelee.cooldownTicks(stack("bronze_sword")) == 20, "classed sword 20");
        helper.assertTrue(NpcMelee.cooldownTicks(stack("iron_dagger")) == 13, "dagger 20 / 1.5 = 13");
        helper.assertTrue(NpcMelee.cooldownTicks(stack("iron_spear")) == 24, "spear 20 / 0.833 = 24");
        helper.assertTrue(NpcMelee.cooldownTicks(stack("iron_pike")) == 40, "pike 20 / 0.5 = 40");
        helper.assertTrue(NpcMelee.cooldownTicks(stack("iron_battleaxe")) == 27, "battleaxe 20 / 0.75 = 27");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reachFollowsReachFactor(GameTestHelper helper) {
        helper.assertTrue(NpcMelee.reachFactor(ItemStack.EMPTY) == 1.0, "bare hands x1");
        helper.assertTrue(NpcMelee.reachFactor(new ItemStack(Items.IRON_SWORD)) == 1.0, "vanilla sword x1");
        helper.assertTrue(NpcMelee.reachFactor(stack("iron_dagger")) == 0.75, "dagger x0.75");
        helper.assertTrue(NpcMelee.reachFactor(stack("iron_spear")) == 1.5, "spear x1.5");
        helper.assertTrue(NpcMelee.reachFactor(stack("iron_pike")) == 2.0, "pike x2");
        helper.assertTrue(
                NpcMelee.reach(stack("iron_pike")) > NpcMelee.reach(new ItemStack(Items.IRON_SWORD))
                        && NpcMelee.reach(new ItemStack(Items.IRON_SWORD)) > NpcMelee.reach(stack("iron_dagger")),
                "pike reaches past sword past dagger");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pikeHitsFromFurtherThanDagger(GameTestHelper helper) {
        StandardNpcEntity npc = helper.spawn(NpcEntityTypes.BREE_GUARD.get(), new BlockPos(2, 2, 2));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 5));
        Vec3 npcPos = npc.position();
        // Place the cow so its hitbox is between the dagger reach and the pike reach from the NPC's hitbox.
        double gap = (NpcMelee.reach(stack("iron_dagger")) + NpcMelee.reach(stack("iron_pike"))) / 2.0;
        double dist = npc.getBbWidth() / 2.0 + gap + cow.getBbWidth() / 2.0;
        cow.setPos(npcPos.x, npcPos.y, npcPos.z + dist);

        npc.setItemSlot(EquipmentSlot.MAINHAND, stack("iron_dagger"));
        helper.assertTrue(!NpcMelee.inReach(npc, cow), "dagger cannot reach the cow");
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(!NpcMelee.inReach(npc, cow), "sword cannot reach the cow");
        npc.setItemSlot(EquipmentSlot.MAINHAND, stack("iron_pike"));
        helper.assertTrue(NpcMelee.inReach(npc, cow), "pike reaches the cow");
        helper.succeed();
    }
}
