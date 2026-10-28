package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The offer roll: how often an eligible giver rolls and how an offer is generated from its group (kind by weight,
 * template uniform within the kind, up to {@link #MAX_ATTEMPTS} tries).
 */
public final class OfferRoller {
    /** One roll in {@code BASE_RATE / multiplier} per server tick per eligible giver (about 17 minutes). */
    public static final int BASE_RATE = 20_000;

    /** A failed generation (for example a bounty without a valid target) retries this many times per roll. */
    public static final int MAX_ATTEMPTS = 8;

    private OfferRoller() {}

    /**
     * The denominator of the per-tick roll: {@code round(20000 / multiplier)}, at least 1. A multiplier of 0 or
     * less never rolls and yields 0.
     */
    public static int rate(float multiplier) {
        if (multiplier <= 0.0f) {
            return 0;
        }
        return Math.max(1, Math.round(BASE_RATE / multiplier));
    }

    /** One tick's roll: true 1 time in {@code rate}. */
    public static boolean shouldRoll(RandomSource random, int rate) {
        return rate > 0 && random.nextInt(rate) == 0;
    }

    /**
     * Generates an offer from the group's templates: picks a kind by weight among the kinds the group has
     * templates for, then a template uniformly within the kind, and retries a kind that cannot generate.
     *
     * @return empty when the group has no template to offer or all attempts failed
     */
    public static Optional<QuestGiverData.Offer> generate(
            ServerLevel level, NpcEntity giver, QuestGivers.GroupMatch group, RandomSource random) {
        List<QuestGivers.TemplateEntry> templates = QuestGivers.templates(level.registryAccess(), group.id());
        Map<OfferKind, List<QuestGivers.TemplateEntry>> byKind = new LinkedHashMap<>();
        for (OfferKind kind : OfferKinds.all()) {
            List<QuestGivers.TemplateEntry> matching = new ArrayList<>();
            for (QuestGivers.TemplateEntry entry : templates) {
                if (kind.matches(entry.template().objective())) {
                    matching.add(entry);
                }
            }
            if (!matching.isEmpty() && kind.weight() > 0) {
                byKind.put(kind, matching);
            }
        }
        if (byKind.isEmpty()) {
            return Optional.empty();
        }
        OfferKind.Context context = new OfferKind.Context(level, giver, group, random);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            OfferKind kind = pickKind(byKind, random);
            List<QuestGivers.TemplateEntry> pool = byKind.get(kind);
            QuestGivers.TemplateEntry template = pool.get(random.nextInt(pool.size()));
            Optional<QuestGiverData.Offer> offer = kind.generate(context, template.id(), template.template());
            if (offer.isPresent()) {
                return offer;
            }
        }
        return Optional.empty();
    }

    private static OfferKind pickKind(Map<OfferKind, List<QuestGivers.TemplateEntry>> byKind, RandomSource random) {
        int total = 0;
        for (OfferKind kind : byKind.keySet()) {
            total += kind.weight();
        }
        int roll = random.nextInt(total);
        for (OfferKind kind : byKind.keySet()) {
            roll -= kind.weight();
            if (roll < 0) {
                return kind;
            }
        }
        throw new IllegalStateException("unreachable weighted pick");
    }
}
