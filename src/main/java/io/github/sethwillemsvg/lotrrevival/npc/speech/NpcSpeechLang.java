package io.github.sethwillemsvg.lotrrevival.npc.speech;

import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Every NPC speech line, written new for this repo. The lines live here (not only in the lang file) because the
 * server picks a random line number and so must know how many lines each category has. Keys are
 * {@code npc.lotrrevival.<race>.<category>.<n>}, numbered from 1.
 */
public final class NpcSpeechLang {
    private static final Map<NpcRace, Map<String, List<String>>> LINES = new EnumMap<>(NpcRace.class);

    static {
        Map<String, List<String>> hobbit = new LinkedHashMap<>();
        hobbit.put("friendly", List.of(
                "Well met! The kettle is always on in this part of the world.",
                "Good to see a friendly face on the lane. Mind the mud by the mill.",
                "You have the look of someone who has walked a long way. Rest your feet a while.",
                "Folk like you are welcome here. Come back at harvest and there will be pie.",
                "If more travellers were as decent as you, we would never bother with latches."));
        hobbit.put("neutral", List.of(
                "Hm? Oh, good day. I was counting turnips.",
                "Weather looks fair enough, I suppose.",
                "I do not think we have met. Mind you do not trample the garden.",
                "Visitors, visitors. There is always somebody passing through.",
                "Keep to the path and we shall get along."));
        hobbit.put("hostile", List.of(
                "Stay back! I know your sort and I want no part of you.",
                "Be off with you, before I fetch the Bounders!",
                "I have nothing to say to the likes of you. Go on, away!",
                "You are not welcome here. Not one bit.",
                "Do not come a step closer, I am warning you."));
        hobbit.put("night", List.of(
                "It is rather late to be wandering about. Bolt your door, that is my advice.",
                "Lamps are lit and supper is long cleared. Whatever brings you out so late?",
                "Mind the dark. Strange things move about once the sun is down.",
                "I was just heading in. Good night to you, and do not dawdle.",
                "Hush, you will wake the whole lane at this hour."));
        hobbit.put("flee", List.of(
                "Help! Somebody help!",
                "Run, run, run!",
                "Not me, please, not me!",
                "I am only a hobbit, leave me be!",
                "Out of my way, out of my way!"));
        hobbit.put("witness", List.of(
                "You murdering wretch! We saw what you did!",
                "That was a neighbour of mine! You shall answer for it!",
                "Shameful! Every one of us saw it!",
                "Wicked, wicked deed. You will not leave this place easily.",
                "Somebody get the Shirriff! Quick!"));
        hobbit.put("trader_none", List.of(
                "The shelves are bare today, I am afraid. Come by another time.",
                "I would gladly sell you something, but the cellar is quite empty.",
                "Nothing to offer at present. The cart has not come in yet.",
                "Sorry, friend. No wares today, only conversation.",
                "My stock has not arrived. Try me again soon."));
        hobbit.put("farmer_friendly", List.of(
                "The barley is coming on beautifully this year. A friend like you deserves a bit of luck.",
                "Rain on Monday, sun by Wednesday. That is how a good crop likes it.",
                "Come and see the pumpkins if you can spare a moment. The biggest are the size of a pony.",
                "Hard work and good soil, that is all a farmer needs. And decent company, naturally.",
                "Mind the furrows. I spent all morning on those rows."));
        hobbit.put("innkeeper_friendly", List.of(
                "Welcome, welcome! The ale is fresh and the fire is warm.",
                "A friend of the Shire drinks well in my house. Make yourself comfortable.",
                "Stew on the hob, bread in the oven. You came at exactly the right time.",
                "Sit anywhere you like. The back bench has the best view of the hearth.",
                "Another traveller with good manners! I could get used to this."));
        hobbit.put("shirriff_friendly", List.of(
                "Keeping the peace is long hours and little thanks, so a kind word means a great deal.",
                "The Bounders are watching the borders day and night. You may rest easy.",
                "Law in the Shire is mostly returning lost sheep. Still, someone has to do it.",
                "You have a good name among us. I shall remember it.",
                "If you ever need honest help, the Shirriff's door is open."));
        LINES.put(NpcRace.HOBBIT, hobbit);

        Map<String, List<String>> man = new LinkedHashMap<>();
        man.put("friendly", List.of(
                "Well met, traveller. Bree is glad of honest folk.",
                "Good to see you again. The road has been quiet, thanks be.",
                "There is a seat by the hearth for you any evening you like.",
                "You have done right by this town. It is not forgotten.",
                "Mind how you go, friend. The road out of Bree is longer than it looks."));
        man.put("neutral", List.of(
                "Hm. Another one passing through.",
                "Fine day, if it holds. What do you want?",
                "Keep your business honest and we shall have no quarrel.",
                "I do not know you, stranger, but I do not know ill of you either.",
                "Speak plain and be quick. There is work to be done."));
        man.put("hostile", List.of(
                "Keep away from me, villain.",
                "Your kind is not welcome in Bree. Move along, or the guard shall hear of this.",
                "I have heard the tales about you. Do not think I have forgotten.",
                "One more step and I will shout for the watch.",
                "Get out of my sight."));
        man.put("night", List.of(
                "The gates are shut for a reason. What are you doing out so late?",
                "Dark times, dark roads. Get inside before something finds you.",
                "Folk who linger after dusk seldom have good reason.",
                "Lock your door tonight, and do not open it for strangers.",
                "Late hour for talk, friend. Be on your way."));
        man.put("flee", List.of(
                "Guards! Guards!",
                "Run for your life!",
                "Stay away from me!",
                "Somebody help, I am being attacked!",
                "Please, no, I have a family!"));
        man.put("witness", List.of(
                "Murderer! I saw you do it!",
                "That man had done nothing to you! Seize them!",
                "You will hang for this, I swear it.",
                "By all that is good, the whole street saw that. Do not let them escape!",
                "Bloody-handed coward. Bree will not forget this."));
        man.put("trader_none", List.of(
                "I have nothing to sell today, friend. The merchants are late.",
                "Stock is thin at the moment. Ask again after the next cart.",
                "Sorry, empty counter. Business is slow when the roads are bad.",
                "Nothing for sale yet. Come back in a few days.",
                "I would trade if I could, but I am clean out."));
        man.put("farmer_friendly", List.of(
                "Good soil here, if you know how to treat it. Harvest will be fine this year.",
                "It is a slow business, farming, but an honest one.",
                "The oxen are stubborn, but I am more stubborn still.",
                "If the frost holds off, we shall have grain enough for the winter and some to spare.",
                "Mind the hedge, there are thorns, and my dog is not fond of strangers."));
        man.put("innkeeper_friendly", List.of(
                "Welcome in! Dinner is hot and the beds are dry.",
                "You look like someone with a tale or two. Sit down and share one.",
                "A pint for the weary? On the house, this once.",
                "Warm yourself by the fire. You are among friends here.",
                "Half the news of the road passes through this room, you know. Ask away."));
        LINES.put(NpcRace.MAN, man);
    }

    private NpcSpeechLang() {}

    /** Number of lines of a category for a race; zero when the race has none. */
    public static int count(NpcRace race, String category) {
        return LINES.getOrDefault(race, Map.of()).getOrDefault(category, List.of()).size();
    }

    /** {@code npc.lotrrevival.<race>.<category>.<n>}, with {@code n} counted from 1. */
    public static String key(String race, String category, int n) {
        return "npc.lotrrevival." + race + "." + category + "." + n;
    }

    /** Called from {@code NpcLang}. */
    public static void addTo(LanguageProvider lang) {
        LINES.forEach((race, categories) -> categories.forEach((category, lines) -> {
            for (int i = 0; i < lines.size(); i++) {
                lang.add(key(race.id(), category, i + 1), lines.get(i));
            }
        }));
    }
}
