package io.github.sethwillemsvg.lotrrevival.client.anim;

import io.github.sethwillemsvg.lotrrevival.combat.NpcAttackPayload;
import io.github.sethwillemsvg.lotrrevival.combat.NpcHitPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Client side of NpcAttackPayload: the NPC plays the attack clip the server picked, timed to the server's hit. */
public final class ClientAttack {
    private ClientAttack() {}

    public static void handle(NpcAttackPayload p) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(p.entityId()) instanceof LivingEntity e) NpcAnimator.startAttack(e, p.slot(), p.speed());
    }

    /** Client side of NpcHitPayload: the hitstop freeze. */
    public static void hit(NpcHitPayload p) {
        // Task 7 fills this
    }
}
