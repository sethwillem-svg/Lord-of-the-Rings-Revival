package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/** The lines a quest giver says. Keys live in the lang contribution of the quest offer datagen. */
public final class OfferDialogue {
    /** Opening lines per kind. */
    public static final int OPENING_LINES = 3;

    public static final int PROGRESS_LINES = 2;
    public static final int THANKS_LINES = 2;

    public static final String TOO_MANY = "quest.lotrrevival.offer.too_many";
    public static final String TAKEN = "quest.lotrrevival.offer.taken";

    private OfferDialogue() {}

    /** Lang key of a random opening line for an offer kind ({@code collect}, {@code kill}, ...). */
    public static String openingKey(String kind, RandomSource random) {
        return "quest.lotrrevival.offer." + kind + ".opening." + (1 + random.nextInt(OPENING_LINES));
    }

    public static String progressKey(RandomSource random) {
        return "quest.lotrrevival.offer.progress." + (1 + random.nextInt(PROGRESS_LINES));
    }

    public static String thanksKey(RandomSource random) {
        return "quest.lotrrevival.offer.thanks." + (1 + random.nextInt(THANKS_LINES));
    }

    /** A line a giver said, for observers (GameTests). */
    public record Said(UUID player, String key, Object[] args) {}

    private static volatile Consumer<Said> observer = said -> {};

    /** Replaces the observer of spoken lines and returns the previous one (tests restore it in a finally). */
    public static Consumer<Said> setObserver(Consumer<Said> replacement) {
        Consumer<Said> previous = observer;
        observer = replacement == null ? said -> {} : replacement;
        return previous;
    }

    /** {@code <Name> line}, as a system message to one player only. */
    public static void say(NpcEntity npc, ServerPlayer player, String key, Object... args) {
        observer.accept(new Said(player.getUUID(), key, args));
        player.sendSystemMessage(Component.literal("<")
                .append(npc.speakerName())
                .append("> ")
                .append(Component.translatable(key, args)));
    }
}
