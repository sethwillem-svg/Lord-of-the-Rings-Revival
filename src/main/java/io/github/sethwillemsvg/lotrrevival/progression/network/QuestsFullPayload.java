package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.quest.PlayerQuests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRecord;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code lotrrevival:progression/quests_full}: the player's whole quest state, split into chunks of at most
 * {@link #MAX_BYTES} bytes. The first chunk carries the tracked quest and counters; every chunk carries a slice of
 * the active list and the log. The client resets on {@code first} and publishes on {@code last}.
 */
public record QuestsFullPayload(boolean first, boolean last, PlayerQuests part) implements CustomPacketPayload {
    public static final Type<QuestsFullPayload> TYPE = new Type<>(LOTRRevival.id("progression/quests_full"));

    /** Spec limit per chunk: 32 KiB. */
    public static final int MAX_BYTES = 32 * 1024;

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestsFullPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            QuestsFullPayload::first,
            ByteBufCodecs.BOOL,
            QuestsFullPayload::last,
            PlayerQuests.STREAM_CODEC,
            QuestsFullPayload::part,
            QuestsFullPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Encoded size of a payload in bytes. */
    public static int encodedSize(PlayerQuests state, RegistryAccess access) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            STREAM_CODEC.encode(buf, new QuestsFullPayload(true, true, state));
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    /** Splits the state into one or more chunks, each encoding to at most {@link #MAX_BYTES}. Never empty. */
    public static List<QuestsFullPayload> split(PlayerQuests state, RegistryAccess access) {
        PlayerQuests header = new PlayerQuests(
                List.of(), state.tracked(), List.of(), state.completedByFaction(),
                state.completedTotal(), state.failedTotal(), state.bountiesCompleted());
        PlayerQuests body = new PlayerQuests(List.of(), Optional.empty(), List.of(), Map.of(), 0, 0, 0);
        int headerBytes = encodedSize(header, access);
        int bodyBytes = encodedSize(body, access);
        // Leave headroom for the NBT list framing of each element.
        final int slack = 64;

        List<PlayerQuests> parts = new ArrayList<>();
        List<QuestInstance> active = new ArrayList<>();
        List<QuestRecord> log = new ArrayList<>();
        int size = headerBytes;
        boolean firstPart = true;

        List<Object> items = new ArrayList<>(state.active());
        items.addAll(state.log());
        for (Object item : items) {
            PlayerQuests single = item instanceof QuestInstance instance
                    ? new PlayerQuests(List.of(instance), Optional.empty(), List.of(), Map.of(), 0, 0, 0)
                    : new PlayerQuests(List.of(), Optional.empty(), List.of((QuestRecord) item), Map.of(), 0, 0, 0);
            int itemBytes = encodedSize(single, access) - bodyBytes + slack;
            if (size + itemBytes > MAX_BYTES && (!active.isEmpty() || !log.isEmpty())) {
                parts.add(chunk(firstPart ? header : body, active, log));
                firstPart = false;
                active = new ArrayList<>();
                log = new ArrayList<>();
                size = bodyBytes;
            }
            if (item instanceof QuestInstance instance) {
                active.add(instance);
            } else {
                log.add((QuestRecord) item);
            }
            size += itemBytes;
        }
        parts.add(chunk(firstPart ? header : body, active, log));

        List<QuestsFullPayload> payloads = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            payloads.add(new QuestsFullPayload(i == 0, i == parts.size() - 1, parts.get(i)));
        }
        return payloads;
    }

    private static PlayerQuests chunk(PlayerQuests base, List<QuestInstance> active, List<QuestRecord> log) {
        return new PlayerQuests(
                active, base.tracked(), log, base.completedByFaction(),
                base.completedTotal(), base.failedTotal(), base.bountiesCompleted());
    }
}
