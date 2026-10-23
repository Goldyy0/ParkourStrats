package me.texyle.startreminders.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "^" in a strategy field means "the same as the field above". It may stand alone or inside a text, where it is
 * replaced in place: "^ 1t" becomes "<above> 1t". A "^" in the middle of a word (x^2) is left alone.
 */
public final class RepeatMarker {

    private static final Pattern MARKER = Pattern.compile("(?<=^|\\s)\\^|\\^(?=$|\\s)");

    private RepeatMarker() {}

    /** While typing: only a "^" that stands alone as a word is replaced, and nothing else is touched. */
    private static final Pattern TYPED = Pattern.compile("(?<=^|\\s)\\^(?=$|\\s)");

    /** Replaces a "^" the player has just typed as a word of its own; the rest of the text, spaces included, stays. */
    public static String resolveTyped(String text, String above) {
        if (text == null || text.indexOf('^') < 0) return text == null ? "" : text;
        return TYPED.matcher(text).replaceAll(Matcher.quoteReplacement(above != null ? above.trim() : ""));
    }

    /** @param above the value of the field above, already resolved; may be empty */
    public static String resolve(String text, String above) {
        if (text == null) return "";
        if (text.indexOf('^') < 0) return text;

        String value = above != null ? above.trim() : "";
        Matcher matcher = MARKER.matcher(text);
        if (!matcher.find()) return text;

        String out = matcher.replaceAll(Matcher.quoteReplacement(value));
        return out.replaceAll("\\s{2,}", " ").trim();
    }
}
