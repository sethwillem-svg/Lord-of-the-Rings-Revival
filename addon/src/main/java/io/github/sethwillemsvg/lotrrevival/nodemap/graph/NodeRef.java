package io.github.sethwillemsvg.lotrrevival.nodemap.graph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * A node reference: a fixed node ({@code fixed:<namespace:path>}) or one of the player's camps ({@code camp:<id>}). The
 * string form is used on the wire, in commands and in log lines.
 */
public record NodeRef(@Nullable ResourceLocation fixed, int camp) {
    public static final Codec<NodeRef> CODEC = Codec.STRING.comapFlatMap(s -> {
        NodeRef r = parse(s);
        return r == null ? DataResult.error(() -> "bad node ref " + s) : DataResult.success(r);
    }, NodeRef::toString);

    public static NodeRef fixed(ResourceLocation id) {
        return new NodeRef(id, -1);
    }

    public static NodeRef camp(int id) {
        return new NodeRef(null, id);
    }

    public boolean isCamp() {
        return fixed == null;
    }

    /** {@code fixed:ns:path}, {@code camp:N}, or a bare {@code ns:path} (taken as fixed); null when malformed. */
    @Nullable
    public static NodeRef parse(String s) {
        if (s == null) return null;
        s = s.trim();
        try {
            if (s.startsWith("camp:")) return camp(Integer.parseInt(s.substring(5)));
            if (s.startsWith("fixed:")) s = s.substring(6);
            ResourceLocation id = ResourceLocation.tryParse(s);
            return id == null ? null : fixed(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return isCamp() ? "camp:" + camp : "fixed:" + fixed;
    }

    /** Short form for log lines and chains: the node id path, or {@code camp:N}. */
    public String shortName() {
        return isCamp() ? "camp:" + camp : fixed.getPath();
    }
}
