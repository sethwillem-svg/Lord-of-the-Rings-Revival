package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.VarInt;
import net.minecraft.resources.ResourceLocation;

/** Wire-size helpers for the achievement payloads. */
public final class AchievementSync {
    /** Largest encoded size of one {@code achievements_full} payload. */
    public static final int MAX_PAYLOAD_BYTES = 32 * 1024;

    /** Room kept for the list length prefix (a VarInt) and the {@code last} flag. */
    private static final int HEADER_BYTES = VarInt.getByteSize(CoreNetwork.MAX_COLLECTION) + 1;

    /** A delta larger than this many ids is replaced by a full sync. */
    public static final int MAX_DELTA_IDS = 256;

    private AchievementSync() {}

    /** Encoded size of one id: a VarInt length prefix plus the UTF-8 bytes of {@code namespace:path}. */
    public static int encodedSize(ResourceLocation id) {
        int length = id.toString().getBytes(StandardCharsets.UTF_8).length;
        return VarInt.getByteSize(length) + length;
    }

    /**
     * Splits the ids into payloads that each encode within {@link #MAX_PAYLOAD_BYTES}. Only the final payload
     * has {@code last} set; an empty input yields one empty last payload so the client still resets.
     */
    public static List<AchievementsFullPayload> split(Collection<ResourceLocation> ids) {
        List<AchievementsFullPayload> out = new ArrayList<>();
        List<ResourceLocation> current = new ArrayList<>();
        int size = HEADER_BYTES;
        for (ResourceLocation id : ids) {
            int idSize = encodedSize(id);
            if (!current.isEmpty()
                    && (size + idSize > MAX_PAYLOAD_BYTES || current.size() >= CoreNetwork.MAX_COLLECTION)) {
                out.add(new AchievementsFullPayload(List.copyOf(current), false));
                current.clear();
                size = HEADER_BYTES;
            }
            current.add(id);
            size += idSize;
        }
        out.add(new AchievementsFullPayload(List.copyOf(current), true));
        return out;
    }
}
