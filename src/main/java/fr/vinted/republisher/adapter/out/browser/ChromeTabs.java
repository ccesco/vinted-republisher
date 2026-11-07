package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
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

    private final Chrome chrome;

    public ChromeTabs(Chrome chrome) {
        this.chrome = chrome;
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

    /** An HTTP error is expected to be Vinted's anti-bot block page: it must not pass for a displayed page. */
    static void open(Page page, URI url) {
        Response response;
        try {
            response = page.navigate(url.toString());
        } catch (PlaywrightException noAnswer) {
            throw new VintedUnreachable(noAnswer.getMessage(), noAnswer);
        }
        if (response != null && response.status() >= 400) {
            throw new VintedUnreachable("HTTP " + response.status() + " on " + response.url());
        }
    }

    /** Closing Playwright only detaches from Chrome, which stays open. */
    private static <T> T inNewTab(URI endpoint, Function<Page, T> action) {
        try (Playwright playwright = Playwright.create(NO_BROWSER_DOWNLOAD)) {
            Browser browser = playwright.chromium().connectOverCDP(endpoint.toString());
            Page page = browser.contexts().getFirst().newPage();
            try {
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
