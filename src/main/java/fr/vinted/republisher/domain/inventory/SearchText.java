package fr.vinted.republisher.domain.inventory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Text as the search compares it: without case, accents nor surrounding spaces. */
final class SearchText {

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");

    private SearchText() {
    }

    static String of(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return ACCENTS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).strip();
    }
}
