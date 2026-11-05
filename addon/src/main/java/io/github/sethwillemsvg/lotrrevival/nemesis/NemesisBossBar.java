package io.github.sethwillemsvg.lotrrevival.nemesis;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Vanilla boss bars in the rank colour, shown to grudge holders and players who fought it within 64 blocks. */
public final class NemesisBossBar {
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private NemesisBossBar() {}

    public static void create(NpcEntity body, NemesisRecord r) {
        remove(body.getUUID());
        ServerBossEvent bar = new ServerBossEvent(title(r), r.rank.color, BossEvent.BossBarOverlay.PROGRESS);
        BARS.put(body.getUUID(), bar);
        update(body, r);
    }

    public static boolean has(UUID body) {
        return BARS.containsKey(body);
    }

    private static Component title(NemesisRecord r) {
        return Component.translatable("lotr_revival.nemesis.bar", r.displayName(),
                Component.translatable("lotr_revival.nemesis.rank." + r.rank.key()));
    }

    public static void refresh(NpcEntity body, NemesisRecord r) {
        ServerBossEvent bar = BARS.get(body.getUUID());
        if (bar == null) return;
        bar.setName(title(r));
        bar.setColor(r.rank.color);
    }

    public static void update(NpcEntity body, NemesisRecord r) {
        ServerBossEvent bar = BARS.get(body.getUUID());
        if (bar == null) return;
        bar.setProgress(Math.max(0f, Math.min(1f, body.getHealth() / body.getMaxHealth())));
        if (!(body.level() instanceof ServerLevel level)) return;
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
            if (p.isRemoved() || p.level() != level || p.distanceToSqr(body) > 64 * 64) bar.removePlayer(p);
        }
        for (ServerPlayer p : level.players()) {
            boolean involved = r.activeGrudge(p.getUUID()) > 0 || r.damagedBy.contains(p.getUUID());
            if (involved && p.distanceToSqr(body) <= 64 * 64 && !bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    public static void remove(UUID body) {
        ServerBossEvent bar = BARS.remove(body);
        if (bar != null) bar.removeAllPlayers();
    }
}
