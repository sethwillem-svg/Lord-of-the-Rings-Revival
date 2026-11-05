package io.github.sethwillemsvg.lotrrevival.nemesis;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read API for other systems (spec Data and APIs): {@code find}, {@code bodyOf}, {@code atLarge}, {@code grudges}, sides. */
public final class NemesisApi {
    private NemesisApi() {}

    public static @Nullable NemesisSavedData data() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : NemesisSavedData.get(s);
    }

    public static @Nullable NemesisRecord find(UUID id) {
        NemesisSavedData d = data();
        return d == null ? null : d.find(id);
    }

    /** The live record of a nemesis body, or null (not a body, a stale body, or a dead record). */
    public static @Nullable NemesisRecord bodyOf(@Nullable Entity e) {
        if (e == null || e.level().isClientSide) return null;
        NemesisLink link = NemesisLink.of(e);
        if (link == null) return null;
        NemesisSavedData d = NemesisSavedData.get(e.getServer());
        NemesisRecord r = d.find(link.id());
        if (r == null || !r.alive() || r.manifestation != link.manifestation()) return null;
        return r;
    }

    public static List<NemesisRecord> atLarge(ResourceLocation faction) {
        List<NemesisRecord> out = new ArrayList<>();
        NemesisSavedData d = data();
        if (d == null) return out;
        for (NemesisRecord r : d.living(faction)) if (r.status == NemesisRecord.Status.AT_LARGE) out.add(r);
        return out;
    }

    /** Nemesis id -> grudge score on this player (dormant grudges included). */
    public static Map<UUID, Integer> grudges(UUID player) {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        NemesisSavedData d = data();
        if (d == null) return out;
        for (NemesisRecord r : d.records.values()) if (r.alive() && r.grudge(player) > 0) out.put(r.id, r.grudge(player));
        return out;
    }

    public static Sides.Side side(ServerPlayer player) {
        return Sides.side(player);
    }

    public static boolean isEnemy(ResourceLocation npcFaction, ServerPlayer player) {
        return Sides.isEnemy(player.level(), npcFaction, player);
    }
}
