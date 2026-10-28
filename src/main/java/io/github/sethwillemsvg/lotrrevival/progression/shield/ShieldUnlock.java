package io.github.sethwillemsvg.lotrrevival.progression.shield;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * How a shield is unlocked, type-dispatched on {@code type}. Only {@link AchievementCount} is evaluated; the
 * other types are reserved in the codec so later content can use them, and they never count as met for now.
 */
public sealed interface ShieldUnlock {
    Codec<ShieldUnlock> CODEC = Type.CODEC.dispatch("type", ShieldUnlock::type, Type::codec);

    Type type();

    /** Whether a player with this many counted achievements may select the shield. */
    boolean isMet(int achievementCount);

    /** The line shown for a locked shield. */
    Component lockReason();

    enum Type implements StringRepresentable {
        ACHIEVEMENT_COUNT("achievement_count", AchievementCount.CODEC),
        ALIGNMENT("alignment", Alignment.CODEC),
        ACHIEVEMENT("achievement", Achievement.CODEC),
        GRANTED("granted", Granted.CODEC);

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String name;
        private final MapCodec<? extends ShieldUnlock> codec;

        Type(String name, MapCodec<? extends ShieldUnlock> codec) {
            this.name = name;
            this.codec = codec;
        }

        public MapCodec<? extends ShieldUnlock> codec() {
            return codec;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Unlocked once the player has at least {@code min} counted achievements. */
    record AchievementCount(int min) implements ShieldUnlock {
        public static final MapCodec<AchievementCount> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("min").forGetter(AchievementCount::min))
                .apply(i, AchievementCount::new));

        @Override
        public Type type() {
            return Type.ACHIEVEMENT_COUNT;
        }

        @Override
        public boolean isMet(int achievementCount) {
            return achievementCount >= min;
        }

        @Override
        public Component lockReason() {
            return Component.translatable("gui.lotrrevival.menu.shields.lock.achievement_count", min);
        }
    }

    /** Reserved: alignment with a faction of at least {@code min}. */
    record Alignment(ResourceLocation faction, float min) implements ShieldUnlock {
        public static final MapCodec<Alignment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("faction").forGetter(Alignment::faction),
                        Codec.FLOAT.fieldOf("min").forGetter(Alignment::min))
                .apply(i, Alignment::new));

        @Override
        public Type type() {
            return Type.ALIGNMENT;
        }

        @Override
        public boolean isMet(int achievementCount) {
            return false;
        }

        @Override
        public Component lockReason() {
            return Component.translatable("gui.lotrrevival.menu.shields.lock.reserved");
        }
    }

    /** Reserved: a single named achievement. */
    record Achievement(ResourceLocation id) implements ShieldUnlock {
        public static final MapCodec<Achievement> CODEC = RecordCodecBuilder.mapCodec(
                i -> i.group(ResourceLocation.CODEC.fieldOf("id").forGetter(Achievement::id)).apply(i, Achievement::new));

        @Override
        public Type type() {
            return Type.ACHIEVEMENT;
        }

        @Override
        public boolean isMet(int achievementCount) {
            return false;
        }

        @Override
        public Component lockReason() {
            return Component.translatable("gui.lotrrevival.menu.shields.lock.reserved");
        }
    }

    /** Reserved: given out by a command or another system. */
    record Granted() implements ShieldUnlock {
        public static final MapCodec<Granted> CODEC = MapCodec.unit(new Granted());

        @Override
        public Type type() {
            return Type.GRANTED;
        }

        @Override
        public boolean isMet(int achievementCount) {
            return false;
        }

        @Override
        public Component lockReason() {
            return Component.translatable("gui.lotrrevival.menu.shields.lock.reserved");
        }
    }
}
