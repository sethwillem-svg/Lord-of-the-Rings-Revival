package io.github.sethwillemsvg.lotrrevival.progression.banner;

import com.mojang.serialization.Codec;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;

/** The eight banner permissions. {@link #FULL} covers everything, including placing and breaking. */
public enum BannerPermission implements StringRepresentable {
    FULL("full"),
    DOORS("doors"),
    TABLES("tables"),
    CONTAINERS("containers"),
    PERSONAL_CONTAINERS("personal_containers"),
    FOOD("food"),
    BEDS("beds"),
    SWITCHES("switches");

    public static final Codec<BannerPermission> CODEC = StringRepresentable.fromEnum(BannerPermission::values);

    /** A set of permissions as a list of names; always decoded into an immutable set. */
    public static final Codec<Set<BannerPermission>> SET_CODEC =
            CODEC.listOf().xmap(BannerPermission::setOf, set -> set.stream().sorted().toList());

    private final String serializedName;

    BannerPermission(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** {@code lotrrevival:banner_permission/<name>}; {@link #FULL} has no tag (it is the fallback). */
    public TagKey<Block> tag() {
        return TagKey.create(Registries.BLOCK, LOTRRevival.id("banner_permission/" + serializedName));
    }

    /** An immutable copy of the given permissions. */
    public static Set<BannerPermission> setOf(Collection<BannerPermission> permissions) {
        EnumSet<BannerPermission> copy = EnumSet.noneOf(BannerPermission.class);
        copy.addAll(permissions);
        return Collections.unmodifiableSet(copy);
    }

    public static Set<BannerPermission> setOf(BannerPermission... permissions) {
        return setOf(java.util.Arrays.asList(permissions));
    }

    /** Whether {@code set} allows {@code permission}: it holds Full or the permission itself. */
    public static boolean grants(Set<BannerPermission> set, BannerPermission permission) {
        return set.contains(FULL) || set.contains(permission);
    }
}
