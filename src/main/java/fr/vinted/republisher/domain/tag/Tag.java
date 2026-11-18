package fr.vinted.republisher.domain.tag;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A label the member puts on listings, kept by the tool only: accents composed (NFC), trimmed, inner spaces reduced
 * to one, in lower case with its accents. Two entries of the same form are the same tag. Sorted in French alphabetical order.
 */
public record Tag(String name) implements Comparable<Tag> {

    public static final int MAXIMUM_LENGTH = 30;

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Collator FRENCH = Collator.getInstance(Locale.FRENCH);

    public Tag {
        if (name == null) {
            throw new InvalidTag("Missing tag");
        }
        String composed = Normalizer.normalize(name, Normalizer.Form.NFC);
        name = SPACES.matcher(composed.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            throw new InvalidTag("Blank tag");
        }
        if (name.length() > MAXIMUM_LENGTH) {
            throw new InvalidTag("Tag longer than " + MAXIMUM_LENGTH + " characters");
        }
    }

    @Override
    public int compareTo(Tag other) {
        int order = FRENCH.compare(name, other.name);
        return order != 0 ? order : name.compareTo(other.name);
    }
}
