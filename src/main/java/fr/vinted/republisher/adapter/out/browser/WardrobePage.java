package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The member's public wardrobe. Vinted loads it by slices: the first comes in the page itself, without
 * the status flags, the following ones from its internal API when the member scrolls down.
 */
final class WardrobePage {

    private static final Pattern MEMBER_PATH = Pattern.compile("/member/(\\d+)(-[^/]*)?/?");
    // A whole element reading exactly that: a member's description may mention articles too.
    private static final Pattern DISPLAYED_TOTAL =
            Pattern.compile("^(\\d[\\d\\s\\u00A0]*?)[\\s\\u00A0]+articles?$");
    private static final String FETCH_FIRST_SLICE = "path => fetch(path, {credentials: 'same-origin'}).then(answer => answer.text()).then(() => null)";
    private static final int WHEEL_STEP_PIXELS = 2500;
    private static final int POINTER_X = 400;
    private static final int POINTER_Y = 400;
    private static final int SAFETY_MARGIN_MILLIS = 30_000;

    private final Page page;
    private final URI url;
    private final String itemsPath;
    // Compared as text: other URLs of the page, trackers among them, may not be valid for java.net.URI.
    private final Pattern sliceUrl;
    private final Clock clock;
    private final List<Response> unreadResponses = new ArrayList<>();

    WardrobePage(Page page, URI url, Clock clock) {
        this.page = page;
        this.url = url;
        this.itemsPath = itemsPath(url);
        this.sliceUrl = Pattern.compile("^https?://[^/]+" + Pattern.quote(itemsPath) + "([?#].*)?$");
        this.clock = clock;
    }

    /** Chrome does not lay out a tab in the background, and the following slices would never load. */
    void open() {
        page.bringToFront();
        // Body reading happens later: the Playwright API is not to be called from one of its handlers.
        page.onResponse(unreadResponses::add);
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

    /** The answer is read among the others: the page's own requests and this one go the same way. */
    void askForFirstSlice() {
        try {
            page.evaluate(FETCH_FIRST_SLICE, itemsPath + "?page=1&per_page=20");
        } catch (PlaywrightException noAnswer) {
            throw new VintedUnreachable(noAnswer.getMessage(), noAnswer);
        }
    }

    /** A real wheel event: the page only reacts to what a member would do. */
    void scrollDown() {
        page.mouse().move(POINTER_X, POINTER_Y);
        page.mouse().wheel(0, WHEEL_STEP_PIXELS);
    }

    /** Waits inside Playwright, which only delivers the page's responses while it is called. */
    void pause(Duration pause) {
        Instant end = clock.instant().plus(pause);
        page.waitForCondition(() -> !clock.instant().isBefore(end),
                new Page.WaitForConditionOptions().setTimeout(pause.toMillis() + SAFETY_MARGIN_MILLIS));
    }

    List<ScannedListing> listingsLoaded() {
        List<ScannedListing> listings = new ArrayList<>();
        List<Response> responses = List.copyOf(unreadResponses);
        unreadResponses.clear();
        for (Response response : responses) {
            listings.addAll(read(response));
        }
        return listings;
    }

    private List<ScannedListing> read(Response response) {
        if (!sliceUrl.matcher(response.url()).matches()) {
            return List.of();
        }
        if (response.status() >= 400) {
            throw new VintedUnreachable("HTTP " + response.status() + " on " + response.url());
        }
        try {
            return WardrobeItems.parse(response.text());
        } catch (PlaywrightException noContent) {
            throw new WardrobeUnreadable("no content in " + response.url(), noContent);
        }
    }

    private static String itemsPath(URI url) {
        Matcher member = MEMBER_PATH.matcher(url.getPath());
        if (!member.matches()) {
            throw new WardrobeUnreadable("no member in " + url);
        }
        return "/api/v2/wardrobe/" + member.group(1) + "/items";
    }
}
