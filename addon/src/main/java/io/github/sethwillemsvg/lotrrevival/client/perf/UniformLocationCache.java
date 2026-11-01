package io.github.sethwillemsvg.lotrrevival.client.perf;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Caches glGetUniformLocation per (shader program, uniform name). Minecraft asks the driver for every sampler location on every
 * ShaderInstance.apply (and PulseLib on every sampler bind); on some drivers each query is slow, and with hundreds of draws a
 * frame it was 30-40% of the render thread in Spark profiles. A program's locations never change while it exists, so entries
 * are dropped only when the program is deleted (shader reload). Render thread only.
 */
public final class UniformLocationCache {
    private static final int MISSING = Integer.MIN_VALUE;
    private static final Int2ObjectOpenHashMap<Object2IntOpenHashMap<String>> BY_PROGRAM = new Int2ObjectOpenHashMap<>();

    private UniformLocationCache() {}

    /** Cached location, or Integer.MIN_VALUE when not known yet. */
    public static int get(int program, CharSequence name) {
        Object2IntOpenHashMap<String> names = BY_PROGRAM.get(program);
        return names == null ? MISSING : names.getOrDefault(name.toString(), MISSING);
    }

    public static boolean known(int location) {
        return location != MISSING;
    }

    public static void put(int program, CharSequence name, int location) {
        BY_PROGRAM.computeIfAbsent(program, p -> {
            Object2IntOpenHashMap<String> m = new Object2IntOpenHashMap<>();
            m.defaultReturnValue(MISSING);
            return m;
        }).put(name.toString(), location);
    }

    public static void forget(int program) {
        BY_PROGRAM.remove(program);
    }
}
