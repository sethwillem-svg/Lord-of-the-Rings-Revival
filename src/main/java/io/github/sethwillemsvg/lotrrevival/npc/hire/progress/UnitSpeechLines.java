package io.github.sethwillemsvg.lotrrevival.npc.hire.progress;

import java.util.List;
import java.util.Map;

/**
 * Speech lines of hired units, added to the race tables of {@code NpcSpeechLang}. Categories: {@code hired} (owner
 * sneak-right-clicks), {@code hired_kill} (after a kill) and {@code hired_idle} (while following).
 */
public final class UnitSpeechLines {
    private UnitSpeechLines() {}

    public static void addHobbit(Map<String, List<String>> lines) {
        lines.put("hired", List.of(
                "Ready when you are. I have packed a bit of cheese, just in case.",
                "Lead on. I may be short, but I am steady.",
                "The Shire is worth a little trouble, and so are you.",
                "Just say the word and I will stand where you point.",
                "I took the coin, but I would have come for the company alone."));
        lines.put("hired_kill", List.of(
                "That is one less to trouble the lanes!",
                "Not so tough after all, was he?",
                "Stay down, and stay out of the Shire.",
                "Well, that is done. Mind where you step.",
                "Plenty of practice with a sling pays off."));
        lines.put("hired_idle", List.of(
                "Do you suppose there is a decent inn on this road?",
                "My feet are rather sore, but I shall not mention it again.",
                "It is a fine morning for a walk, whatever else may be about.",
                "I wonder what they are cooking at home right now.",
                "Quiet today. I like quiet."));
    }

    public static void addMan(Map<String, List<String>> lines) {
        lines.put("hired", List.of(
                "At your service. Give the order and it is done.",
                "My blade is yours while you keep faith with Bree.",
                "I have stood watch in worse places. Where to?",
                "Speak plainly and I will follow without complaint.",
                "You pay well and fight fair. That is enough for me."));
        lines.put("hired_kill", List.of(
                "One more who will not trouble the road again.",
                "Down he goes. Keep your guard up.",
                "That is how it is done.",
                "He chose the wrong fight.",
                "Steel settles most arguments."));
        lines.put("hired_idle", List.of(
                "Quiet road. I do not trust it.",
                "Keep an eye on the treeline, I will watch the path.",
                "Long march, but I have done longer.",
                "I could use a hot meal and a dry bed.",
                "Something stirs out there. I can feel it."));
    }
}
