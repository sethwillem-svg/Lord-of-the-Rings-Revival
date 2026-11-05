package io.github.sethwillemsvg.lotrrevival.core.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.Predicate;

/** "Is this player fast-travelling right now" (specs/addon/node-map-travel.md). Until travel lands: never. */
public final class FastTravel {
    private static volatile Predicate<ServerPlayer> travelling = p -> false;

    private FastTravel() {}

    public static boolean isTravelling(ServerPlayer player) {
        return travelling.test(player);
    }

    public static void set(Predicate<ServerPlayer> query) {
        travelling = Objects.requireNonNull(query);
    }
}
