package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;

/** Attaches to a normal Chrome (never launched by Playwright) and works in a tab it closes afterwards. */
public class ChromeTabs {

    private static final Logger LOG = LoggerFactory.getLogger(ChromeTabs.class);
    private static final int SAFETY_MARGIN_MILLIS = 30_000;
    // Playwright only attaches to the installed Chrome: it must not download its own browsers.
    private static final Playwright.CreateOptions NO_BROWSER_DOWNLOAD =
            new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));

    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final int HTTP_NOT_FOUND = 404;
    private static final String SESSION_RENEWAL = "/session-refresh";
    private static final Page.WaitForURLOptions SESSION_RENEWED =
            new Page.WaitForURLOptions().setWaitUntil(WaitUntilState.COMMIT);
    private static final Page.NavigateOptions DOCUMENT_READ =
            new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED);

    private final Chrome chrome;
    private final Duration loadTimeout;
    private final ChromeLivePreviews livePreviews;

    public ChromeTabs(Chrome chrome, ChromeLivePreviews livePreviews) {
        this(chrome, LOAD_TIMEOUT, livePreviews);
    }

    /** For tests only: its tabs go to a registry of its own, shown in no live preview. */
    ChromeTabs(Chrome chrome) {
        this(chrome, LOAD_TIMEOUT);
    }

    /** For tests only: its tabs go to a registry of its own, shown in no live preview. */
    ChromeTabs(Chrome chrome, Duration loadTimeout) {
        this(chrome, loadTimeout, new ChromeLivePreviews(Clock.systemUTC()));
    }

    ChromeTabs(Chrome chrome, Duration loadTimeout, ChromeLivePreviews livePreviews) {
        this.chrome = chrome;
        this.loadTimeout = loadTimeout;
        this.livePreviews = livePreviews;
    }

    /** The page only says the browser is unavailable: the reason goes to the logs. */
    <T> T inNewTab(Function<Page, T> action) {
        try {
            return inNewTab(chrome.ensureRunning(), action);
        } catch (BrowserUnavailable unavailable) {
            LOG.error("Browser unavailable", unavailable);
            throw unavailable;
        }
    }

    void workInNewTab(Consumer<Page> action) {
        inNewTab(page -> {
            action.accept(page);
            return page;
        });
    }

    /** Waits inside Playwright, which keeps serving the page in the meantime. */
    static void pause(Page page, Duration pause, Clock clock) {
        Instant end = clock.instant().plus(pause);
        page.waitForCondition(() -> !clock.instant().isBefore(end),
                new Page.WaitForConditionOptions().setTimeout(pause.toMillis() + SAFETY_MARGIN_MILLIS));
    }

    /**
     * The data read is in the document itself: waiting for every photo or tracker to load only risks a timeout.
     * A page that does not arrive in time is loaded once more, but an HTTP error is expected to be Vinted's
     * anti-bot block page: it is never tried again and must not pass for a displayed page.
     */
    static void open(Page page, URI url) {
        requireDisplayed(navigate(page, url));
    }

    /** Like {@link #open}, except that a page answering 404 is not there: false, rather than an error. */
    static boolean openUnlessGone(Page page, URI url) {
        Response response = navigate(page, url);
        if (response != null && response.status() == HTTP_NOT_FOUND) {
            return false;
        }
        requireDisplayed(response);
        return true;
    }

    private static void requireDisplayed(Response response) {
        if (response != null && response.status() >= 400) {
            throw new VintedUnreachable("HTTP " + response.status() + " on " + response.url());
        }
    }

    /**
     * Vinted renews an aging session on a page of its own, which then goes back to the page asked for (observed on
     * 2026-11-14, about 1.3 s): once back, the page is loaded again, so that its own HTTP status is the one read. A
     * renewal that never ends leaves the tab on it, where every reader sees the session disconnected.
     */
    private static Response navigate(Page page, URI url) {
        Response response = load(page, url);
        if (!isRenewingSession(page.url())) {
            return response;
        }
        try {
            page.waitForURL(address -> !isRenewingSession(address), SESSION_RENEWED);
        } catch (TimeoutError neverBack) {
            LOG.warn("Vinted did not end renewing the session on {}", page.url());
            return response;
        }
        return load(page, url);
    }

    private static boolean isRenewingSession(String address) {
        return SESSION_RENEWAL.equals(URI.create(address).getPath());
    }

    private static Response load(Page page, URI url) {
        Response response;
        try {
            try {
                response = page.navigate(url.toString(), DOCUMENT_READ);
            } catch (TimeoutError slow) {
                LOG.warn("{} did not load in time, loading it once more", url);
                // Playwright stops waiting but Chrome keeps the request: the same URL would queue behind it.
                page.navigate("about:blank");
                response = page.navigate(url.toString(), DOCUMENT_READ);
            }
        } catch (PlaywrightException noAnswer) {
            throw new VintedUnreachable(noAnswer.getMessage(), noAnswer);
        }
        return response;
    }

    /** Closing Playwright only detaches from Chrome, which stays open. */
    private <T> T inNewTab(URI endpoint, Function<Page, T> action) {
        try (Playwright playwright = Playwright.create(NO_BROWSER_DOWNLOAD)) {
            Browser browser = playwright.chromium().connectOverCDP(endpoint.toString());
            Page page = browser.contexts().getFirst().newPage();
            page.setDefaultNavigationTimeout(loadTimeout.toMillis());
            try (ChromeLivePreviews.Watch _ = livePreviews.watch(chrome, page)) {
                return action.apply(page);
            } finally {
                closeQuietly(page);
            }
        } catch (PlaywrightException failure) {
            throw new BrowserUnavailable("Chrome at " + endpoint + ": " + failure.getMessage(), failure);
        }
    }

    private static void closeQuietly(Page page) {
        if (page.isClosed()) {
            return;
        }
        try {
            page.close();
        } catch (PlaywrightException alreadyGone) {
            LOG.debug("Tab already closed with Chrome", alreadyGone);
        }
    }
}
