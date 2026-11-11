package fr.vinted.republisher.domain.wardrobe;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Views and favourites a listing has gathered, as its card on the wardrobe shows them to the member. */
public record ListingStatistics(int viewCount, int favouriteCount) {

    // A number written in digits, with spaces between thousands as French pages print them.
    private static final String COUNT = "(\\d[\\d\\s\\u00A0\\u202F]*?)[\\s\\u00A0\\u202F]+";
    private static final Pattern VIEWS = Pattern.compile("^" + COUNT + "vues?$");
    private static final Pattern FAVOURITES = Pattern.compile("^" + COUNT + "favoris?$");
    private static final int MAXIMUM_DIGITS = 9;

    public ListingStatistics {
        if (viewCount < 0 || favouriteCount < 0) {
            throw new InvalidListing(viewCount + " views, " + favouriteCount + " favourites");
        }
    }

    /**
     * Both numbers or none: a card shows them only to the member, and a text in another form means Vinted
     * changed its page. Each is recognised by its words, whatever its place on the card.
     */
    public static Optional<ListingStatistics> shownBy(List<String> cardTexts) {
        Optional<Integer> views = countIn(cardTexts, VIEWS);
        Optional<Integer> favourites = countIn(cardTexts, FAVOURITES);
        if (views.isEmpty() || favourites.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ListingStatistics(views.get(), favourites.get()));
    }

    /** Two different counts on one card cannot be told apart: neither is trusted. */
    private static Optional<Integer> countIn(List<String> cardTexts, Pattern form) {
        Set<String> counts = cardTexts.stream().map(String::strip).map(form::matcher).filter(Matcher::matches)
                .map(count -> count.group(1).replaceAll("[\\s\\u00A0\\u202F]", "")).collect(Collectors.toSet());
        if (counts.size() != 1) {
            return Optional.empty();
        }
        String digits = counts.iterator().next();
        return digits.length() > MAXIMUM_DIGITS ? Optional.empty() : Optional.of(Integer.parseInt(digits));
    }
}
