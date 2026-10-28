package io.github.sethwillemsvg.lotrrevival.progression.achievement;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The 14 tabs of the Achievements page, in display order. See specs/progression/data-model.md "Tab layout". */
public enum AchievementTab implements StringRepresentable {
    GENERAL("general"),
    SHIRE("shire"),
    ERIADOR("eriador"),
    MISTY_MOUNTAINS("misty_mountains"),
    RHOVANION("rhovanion"),
    LOTHLORIEN("lothlorien"),
    ROHAN("rohan"),
    GONDOR("gondor"),
    MORDOR("mordor"),
    SOUTH_EAST("south_east"),
    COMBAT("combat"),
    CRAFTING("crafting"),
    TRADE("trade"),
    TRAVEL("travel");

    public static final Codec<AchievementTab> CODEC = StringRepresentable.fromEnum(AchievementTab::values);

    private final String id;

    AchievementTab(String id) {
        this.id = id;
    }

    /** The tab id used in achievement ids ({@code <tab>/<name>}) and JSON. */
    public String id() {
        return id;
    }

    /** Lang key of the tab title: {@code achievement.lotrrevival.tab.<id>}. */
    public String langKey() {
        return "achievement.lotrrevival.tab." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
