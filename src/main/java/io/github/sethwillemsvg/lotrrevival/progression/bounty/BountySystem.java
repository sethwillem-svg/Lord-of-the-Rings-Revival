package io.github.sethwillemsvg.lotrrevival.progression.bounty;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/** Wires the bounty system into {@code ProgressionMod.init} and awards its code-driven achievements. */
public final class BountySystem {
    public static final ResourceLocation FIRST_BOUNTY = LOTRRevival.id("general/first_bounty");
    public static final ResourceLocation BOUNTY_HUNTER = LOTRRevival.id("general/bounty_hunter");
    public static final ResourceLocation TURNED_THE_HUNT = LOTRRevival.id("combat/turned_the_hunt");

    private BountySystem() {}

    public static void init(IEventBus modBus) {
        BountyItems.init(modBus);
    }

    /** Grants the backing advancement of an achievement row (criterion {@code c}); idempotent. */
    public static void award(ServerPlayer player, ResourceLocation achievement) {
        if (player.getServer() == null) {
            return;
        }
        AdvancementHolder holder = player.getServer().getAdvancements().get(achievement);
        if (holder != null) {
            player.getAdvancements().award(holder, "c");
        }
    }
}
