package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The member's public wardrobe, which shows more of its listings as the member scrolls down. */
final class WardrobePage {

    // A whole element reading exactly that: a member's description may mention articles too.
    private static final Pattern DISPLAYED_TOTAL =
            Pattern.compile("^(\\d[\\d\\s\\u00A0]*?)[\\s\\u00A0]+articles?$");
    // Only the listings of the grid: the rest of the page may suggest others. Seen as "grid-item" on Vinted,
    // whose markup is not stable: both a test identifier and a class are accepted.
    private static final String LISTING_LINKS =
            "[data-testid='grid-item'] a[href*='/items/'], .grid-item a[href*='/items/']";
    private static final String HREFS = "links => links.map(link => link.href)";
    private static final int WHEEL_STEP_PIXELS = 2500;
    private static final int POINTER_X = 400;
    private static final int POINTER_Y = 400;

    private final Page page;
    private final URI url;
    private final Pattern listingAddress;

    WardrobePage(Page page, URI url) {
        this.page = page;
        this.url = url;
        // Same site, the page of a listing and none below it, in a form java.net.URI takes: the tool opens these.
        this.listingAddress = Pattern.compile("^(" + Pattern.quote(url.getScheme() + "://" + url.getAuthority())
                + "/items/([1-9]\\d{0,17})(?:-[\\w%.~-]*)?/?)(?:[?#].*)?$");
    }

    /** Chrome does not lay out a tab in the background, and the following listings would never show. */
    void open() {
        page.bringToFront();
        ChromeTabs.open(page, url);
    }

    /** Two different totals could stop the reading too early: nothing can be trusted then. */
    int displayedTotal(Duration timeout) {
        Locator totals = page.getByText(DISPLAYED_TOTAL);
        try {
            totals.first().waitFor(new Locator.WaitForOptions().setTimeout(timeout.toMillis()));
        } catch (TimeoutError notDisplayed) {
            throw new WardrobeUnreadable("no total on the page", notDisplayed);
        }
        Set<Integer> distinct = totals.allTextContents().stream().map(WardrobePage::totalIn).collect(Collectors.toSet());
        if (distinct.size() != 1) {
            throw new WardrobeUnreadable("several totals on the page: " + distinct);
        }
        return distinct.iterator().next();
    }

    private static int totalIn(String text) {
        Matcher matcher = DISPLAYED_TOTAL.matcher(text.strip());
        if (!matcher.matches()) {
            throw new WardrobeUnreadable("no total on the page");
        }
        return Integer.parseInt(matcher.group(1).replaceAll("[\\s\\u00A0]", ""));
    }

    /** A real wheel event: the page only reacts to what a member would do. */
    void scrollDown() {
        page.mouse().move(POINTER_X, POINTER_Y);
        page.mouse().wheel(0, WHEEL_STEP_PIXELS);
    }

    /** Anything else is left out: the count against the total then tells something is wrong. */
    List<ListingLink> listingLinks() {
        List<?> hrefs = (List<?>) page.locator(LISTING_LINKS).evaluateAll(HREFS);
        return hrefs.stream().map(String::valueOf).map(listingAddress::matcher).filter(Matcher::matches)
                .map(WardrobePage::link).toList();
    }

    private static ListingLink link(Matcher address) {
        return new ListingLink(new VintedListingId(Long.parseLong(address.group(2))), URI.create(address.group(1)));
    }
}
