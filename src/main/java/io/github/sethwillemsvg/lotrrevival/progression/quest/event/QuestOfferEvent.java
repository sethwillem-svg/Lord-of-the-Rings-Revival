package io.github.sethwillemsvg.lotrrevival.progression.quest.event;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Posted on the game bus before a giver takes on a rolled offer. Cancel to drop the offer. Nothing posts it until
 * the offers ticket exists.
 */
public class QuestOfferEvent extends Event implements ICancellableEvent {
    private final LivingEntity giver;
    private final ResourceLocation template;
    private final int count;
    private final UUID forPlayer;

    public QuestOfferEvent(LivingEntity giver, ResourceLocation template, int count, UUID forPlayer) {
        this.giver = giver;
        this.template = template;
        this.count = count;
        this.forPlayer = forPlayer;
    }

    public LivingEntity getGiver() {
        return giver;
    }

    public ResourceLocation getTemplate() {
        return template;
    }

    public int getCount() {
        return count;
    }

    /** The player a private offer is for, or null for a shared offer. */
    public UUID getForPlayer() {
        return forPlayer;
    }
}
