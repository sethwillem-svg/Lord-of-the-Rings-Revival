package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** A reference to a waypoint on the wire and in commands: {@code fixed:<namespace:path>} or {@code custom:<id>}. */
public sealed interface WaypointRef {
    Codec<WaypointRef> CODEC = Codec.STRING.comapFlatMap(WaypointRef::parse, WaypointRef::wire);

    String wire();

    record Fixed(ResourceLocation id) implements WaypointRef {
        @Override
        public String wire() {
            return "fixed:" + id;
        }
    }

    record Custom(int id) implements WaypointRef {
        @Override
        public String wire() {
            return "custom:" + id;
        }
    }

    static DataResult<WaypointRef> parse(String text) {
        if (text.startsWith("fixed:")) {
            ResourceLocation id = ResourceLocation.tryParse(text.substring(6));
            return id == null
                    ? DataResult.error(() -> "Bad waypoint id in " + text)
                    : DataResult.success(new Fixed(id));
        }
        if (text.startsWith("custom:")) {
            try {
                int id = Integer.parseInt(text.substring(7));
                return id < 0
                        ? DataResult.error(() -> "Negative custom id in " + text)
                        : DataResult.success(new Custom(id));
            } catch (NumberFormatException e) {
                return DataResult.error(() -> "Bad custom id in " + text);
            }
        }
        return DataResult.error(() -> "Waypoint ref must start with fixed: or custom: (" + text + ")");
    }

    static Optional<WaypointRef> tryParse(String text) {
        return parse(text).result();
    }
}
