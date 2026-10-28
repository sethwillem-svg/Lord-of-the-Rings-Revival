package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestObjective;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The offer kinds the roll chooses from. Collect (weight 10) and kill (8, {@code kill_entity} and
 * {@code kill_faction} together) are built in; the bounty offers ticket registers its kind (weight 4) here.
 * Pickpocket and scripted objectives never roll.
 */
public final class OfferKinds {
    /** Weight of {@code collect} offers. */
    public static final int COLLECT_WEIGHT = 10;

    /** Weight of {@code kill} offers. */
    public static final int KILL_WEIGHT = 8;

    public static final OfferKind COLLECT = new Collect();

    public static final OfferKind KILL = new Kill();

    private static final List<OfferKind> KINDS = new CopyOnWriteArrayList<>(List.of(COLLECT, KILL));

    private OfferKinds() {}

    /** Adds a kind; a second registration of the same id replaces the first. */
    public static void register(OfferKind kind) {
        KINDS.removeIf(existing -> existing.id().equals(kind.id()));
        KINDS.add(kind);
    }

    public static List<OfferKind> all() {
        return List.copyOf(KINDS);
    }

    /** The kind that owns an objective, or null for objectives that never roll. */
    public static OfferKind of(QuestObjective objective) {
        for (OfferKind kind : KINDS) {
            if (kind.matches(objective)) {
                return kind;
            }
        }
        return null;
    }

    private record Collect() implements OfferKind {
        @Override
        public String id() {
            return "collect";
        }

        @Override
        public int weight() {
            return COLLECT_WEIGHT;
        }

        @Override
        public boolean matches(QuestObjective objective) {
            return objective instanceof QuestObjective.Collect;
        }
    }

    private record Kill() implements OfferKind {
        @Override
        public String id() {
            return "kill";
        }

        @Override
        public int weight() {
            return KILL_WEIGHT;
        }

        @Override
        public boolean matches(QuestObjective objective) {
            return objective instanceof QuestObjective.KillEntity || objective instanceof QuestObjective.KillFaction;
        }
    }
}
