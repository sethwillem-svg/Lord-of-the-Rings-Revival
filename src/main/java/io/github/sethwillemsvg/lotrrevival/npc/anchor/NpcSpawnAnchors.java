package io.github.sethwillemsvg.lotrrevival.npc.anchor;

import com.mojang.serialization.Codec;
import java.util.List;

/** Value of the {@code lotrrevival:npc_spawn_anchors} chunk attachment. Immutable: replace it to change it. */
public record NpcSpawnAnchors(List<SpawnAnchor> anchors) {
    public static final NpcSpawnAnchors EMPTY = new NpcSpawnAnchors(List.of());

    public static final Codec<NpcSpawnAnchors> CODEC =
            SpawnAnchor.CODEC.listOf().xmap(NpcSpawnAnchors::new, NpcSpawnAnchors::anchors);

    public NpcSpawnAnchors {
        anchors = List.copyOf(anchors);
    }
}
