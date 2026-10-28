package io.github.sethwillemsvg.lotrrevival.npc.speech;

import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleData;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleStats;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcStanding;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.NeoForge;

/** What an NPC says to one player, as a system chat line {@code <Name> line}. Nothing is ever broadcast. */
public final class NpcSpeech {
    /** Ticks before the same NPC answers the same player again. */
    public static final int COOLDOWN_TICKS = 60;

    /** Chance that a talk at night gets a night line. */
    public static final float NIGHT_CHANCE = 0.3f;

    public static final String FRIENDLY = "friendly";
    public static final String NEUTRAL = "neutral";
    public static final String HOSTILE = "hostile";
    public static final String NIGHT = "night";
    public static final String FLEE = "flee";
    public static final String WITNESS = "witness";
    public static final String TRADER_NONE = "trader_none";

    private NpcSpeech() {}

    /**
     * Says one random line of a category to a player, ignoring the cooldown (used for witness lines and by
     * {@link #talk}). A category with no lines for the NPC's race falls back to {@code neutral}. Returns the lang key
     * of the line, empty when nothing could be said.
     */
    public static Optional<String> say(NpcEntity npc, ServerPlayer player, String category) {
        String race = npc.race().id();
        String used = category;
        int count = NpcSpeechLang.count(npc.race(), used);
        if (count == 0 && !NEUTRAL.equals(used)) {
            used = NEUTRAL;
            count = NpcSpeechLang.count(npc.race(), used);
        }
        if (count == 0) {
            return Optional.empty();
        }
        String key = NpcSpeechLang.key(race, used, 1 + npc.getRandom().nextInt(count));
        MutableComponent line = Component.literal("<").append(npc.speakerName()).append("> ").append(Component.translatable(key));
        if (player.connection != null) {
            player.sendSystemMessage(line);
        }
        NeoForge.EVENT_BUS.post(new NpcSpeechEvent(npc, player, used, key));
        return Optional.of(key);
    }

    /** Talks to a player with the category chosen by standing, time and role, unless the cooldown is running. */
    public static Optional<String> talk(NpcEntity npc, ServerPlayer player) {
        if (!npc.tryStartSpeechCooldown(player.getUUID(), npc.level().getGameTime(), COOLDOWN_TICKS)) {
            return Optional.empty();
        }
        return say(npc, player, chooseCategory(npc, player, npc.getRandom()));
    }

    /** Same cooldown as {@link #talk}, for a fixed category (the trader's "nothing to sell"). */
    public static Optional<String> talkWith(NpcEntity npc, ServerPlayer player, String category) {
        if (!npc.tryStartSpeechCooldown(player.getUUID(), npc.level().getGameTime(), COOLDOWN_TICKS)) {
            return Optional.empty();
        }
        return say(npc, player, category);
    }

    /**
     * Selection order: hostile, then night (30%), then role-specific or {@code friendly} when the player's alignment
     * is above 0, else {@code neutral}. A trader whose minimum the player does not meet answers {@code neutral}.
     */
    public static String chooseCategory(NpcEntity npc, ServerPlayer player, RandomSource random) {
        if (NpcStanding.isHostile(npc, player)) {
            return HOSTILE;
        }
        long dayTime = npc.level().getDayTime() % 24000L;
        if (dayTime >= 13000L && random.nextFloat() < NIGHT_CHANCE) {
            return NIGHT;
        }
        float a = NpcStanding.alignment(npc, player);
        if (a <= 0.0f) {
            return NEUTRAL;
        }
        Optional<Float> accessMin = accessMin(npc);
        if (npc.getRole().isTrader() && accessMin.isPresent() && a < accessMin.get()) {
            return NEUTRAL;
        }
        String roleCategory = roleCategory(npc.getRole());
        if (roleCategory != null && NpcSpeechLang.count(npc.race(), roleCategory) > 0) {
            return roleCategory;
        }
        return FRIENDLY;
    }

    /** The alignment needed to trade (goods traders) or hire (unit traders) with this NPC, if its role has one. */
    public static Optional<Float> accessMin(NpcEntity npc) {
        Optional<NpcRoleStats> stats = NpcRoleData.stats(npc.getType(), npc.getRole());
        if (npc.getRole() == NpcRole.UNIT_TRADER) {
            return stats.flatMap(NpcRoleStats::hireMin);
        }
        return stats.flatMap(NpcRoleStats::tradeMin);
    }

    private static String roleCategory(NpcRole role) {
        return switch (role) {
            case FARMER -> "farmer_friendly";
            case INNKEEPER -> "innkeeper_friendly";
            case UNIT_TRADER -> "shirriff_friendly";
            default -> null;
        };
    }
}
