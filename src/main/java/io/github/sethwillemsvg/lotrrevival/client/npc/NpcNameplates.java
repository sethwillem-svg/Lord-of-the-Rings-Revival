package io.github.sethwillemsvg.lotrrevival.client.npc;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcStanding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** When an NPC nameplate is visible to the local player, and in which colour. */
final class NpcNameplates {
    /** Blocks within which the crosshair must be on the NPC. */
    static final double RANGE = 8.0;

    private NpcNameplates() {}

    /** True only while the local player's crosshair is on the NPC within {@link #RANGE} blocks and nothing blocks it. */
    static boolean shouldShow(NpcEntity npc) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || npc.isInvisibleTo(player)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(RANGE));
        return npc.getBoundingBox().inflate(0.1).clip(eye, end).map(hit -> {
            HitResult block = npc.level().clip(new ClipContext(
                    eye, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            return block.getType() == HitResult.Type.MISS;
        }).orElse(false);
    }

    /** Red, white or green from the viewer's synced alignment and the faction's synced fields. */
    static int color(NpcEntity npc) {
        LocalPlayer player = Minecraft.getInstance().player;
        Faction faction = NpcStanding.faction(npc);
        if (player == null || faction == null) {
            return NpcStanding.Tone.NEUTRAL.rgb();
        }
        return NpcStanding.tone(NpcStanding.alignment(npc, player), faction).rgb();
    }
}
