package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedAccount;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import static fr.vinted.republisher.domain.session.SessionStatus.CONNECTED;
import static fr.vinted.republisher.domain.session.SessionStatus.DISCONNECTED;

/**
 * Attaches to a normal Chrome (never launched by Playwright) and works in a tab it closes afterwards.
 * The tool never types any credential: the member logs in by hand.
 */
public class PlaywrightVintedAccount implements VintedAccount {

    private static final Logger LOG = LoggerFactory.getLogger(PlaywrightVintedAccount.class);
    // Vinted may redirect a visitor by script once the page is loaded: wait a little before concluding.
    private static final Duration REDIRECT_GRACE = Duration.ofSeconds(5);
    // Playwright only attaches to the installed Chrome: it must not download its own browsers.
    private static final Playwright.CreateOptions NO_BROWSER_DOWNLOAD =
            new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));

    private final VintedSiteProperties site;
    private final ProfileSettingsPage profileSettings;
    private final Chrome chrome;

    public PlaywrightVintedAccount(VintedSiteProperties site, Chrome chrome) {
        this.site = site;
        this.profileSettings = new ProfileSettingsPage(site.profileSettingsUrl());
        this.chrome = chrome;
    }

    @Override
    public SessionStatus checkSession() {
        return inNewTab(page -> staysOnProfileSettings(page) ? CONNECTED : DISCONNECTED);
    }

    /** Visitors are redirected to the login page; Vinted brings the member back once logged in. */
    @Override
    public SessionStatus awaitManualLogin(Duration timeout) {
        return inNewTab(page -> {
            try {
                page.bringToFront();
                if (!staysOnProfileSettings(page)) {
                    page.waitForURL(this::isProfileSettings, new Page.WaitForURLOptions().setTimeout(timeout.toMillis()));
                }
                return CONNECTED;
            } catch (PlaywrightException notLoggedIn) {
                // Timeout expired, tab or Chrome closed by the member: the session stays closed.
                LOG.info("Manual login not completed: {}", notLoggedIn.getMessage());
                return DISCONNECTED;
            }
        });
    }

    /** The page only says the browser is unavailable: the reason goes to the logs. */
    private SessionStatus inNewTab(Function<Page, SessionStatus> action) {
        try {
            return inNewTab(chrome.ensureRunning(), action);
        } catch (BrowserUnavailable unavailable) {
            LOG.error("Browser unavailable", unavailable);
            throw unavailable;
        }
    }

    /** Closing Playwright only detaches from Chrome, which stays open. */
    private SessionStatus inNewTab(URI endpoint, Function<Page, SessionStatus> action) {
        try (Playwright playwright = Playwright.create(NO_BROWSER_DOWNLOAD)) {
            Browser browser = playwright.chromium().connectOverCDP(endpoint.toString());
            Page page = browser.contexts().getFirst().newPage();
            try {
                openProfileSettings(page);
                return action.apply(page);
            } finally {
                closeQuietly(page);
            }
        } catch (PlaywrightException failure) {
            throw new BrowserUnavailable("Chrome at " + endpoint + ": " + failure.getMessage(), failure);
        }
    }

    /** An HTTP error is expected to be Vinted's anti-bot block page: it must not pass for a displayed page. */
    private void openProfileSettings(Page page) {
        Response response;
        try {
            response = page.navigate(site.profileSettingsUrl().toString());
        } catch (PlaywrightException noAnswer) {
            throw new VintedUnreachable(noAnswer.getMessage(), noAnswer);
        }
        if (response != null && response.status() >= 400) {
            throw new VintedUnreachable("HTTP " + response.status() + " on " + response.url());
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

    private boolean staysOnProfileSettings(Page page) {
        try {
            page.waitForURL(url -> !isProfileSettings(url),
                    new Page.WaitForURLOptions().setTimeout(REDIRECT_GRACE.toMillis()));
            return false;
        } catch (TimeoutError noRedirect) {
            return isProfileSettings(page.url());
        }
    }

    private boolean isProfileSettings(String url) {
        return profileSettings.isShownAt(url);
    }
}
