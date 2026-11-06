package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;

import static fr.vinted.republisher.domain.session.SessionStatus.CONNECTED;
import static fr.vinted.republisher.domain.session.SessionStatus.DISCONNECTED;

/** The tool never types any credential: the member logs in by hand. */
public class PlaywrightVintedAccount implements VintedAccount {

    private static final Logger LOG = LoggerFactory.getLogger(PlaywrightVintedAccount.class);
    // Vinted may redirect a visitor by script once the page is loaded: wait a little before concluding.
    private static final Duration REDIRECT_GRACE = Duration.ofSeconds(5);

    private final URI profileSettingsUrl;
    private final ProfileSettingsPage profileSettings;
    private final ChromeTabs tabs;

    public PlaywrightVintedAccount(VintedSiteProperties site, ChromeTabs tabs) {
        this.profileSettingsUrl = site.profileSettingsUrl();
        this.profileSettings = new ProfileSettingsPage(profileSettingsUrl);
        this.tabs = tabs;
    }

    @Override
    public SessionStatus checkSession() {
        return tabs.inNewTab(page -> {
            ChromeTabs.open(page, profileSettingsUrl);
            return staysOnProfileSettings(page) ? CONNECTED : DISCONNECTED;
        });
    }

    /** Visitors are redirected to the login page; Vinted brings the member back once logged in. */
    @Override
    public SessionStatus awaitManualLogin(Duration timeout) {
        return tabs.inNewTab(page -> {
            ChromeTabs.open(page, profileSettingsUrl);
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
