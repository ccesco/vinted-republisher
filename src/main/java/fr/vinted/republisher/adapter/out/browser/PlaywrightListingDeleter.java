package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import fr.vinted.republisher.domain.deletion.DeletionVisit;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Deletes a listing from its page, as the member does: its delete button, then the confirmation of Vinted's dialog,
 * as observed with the member on 2026-11-14 (AT-09-20). Nothing else is clicked.
 */
public class PlaywrightListingDeleter implements ListingDeleter {

    private static final Logger LOG = LoggerFactory.getLogger(PlaywrightListingDeleter.class);
    private static final String DELETE_BUTTON = "[data-testid='item-delete-button']";
    private static final String CONFIRMATION_DIALOG = "[role='dialog'][data-testid='item-delete-modal']";
    private static final String CONFIRMATION_BUTTON =
            CONFIRMATION_DIALOG + " [data-testid='item-delete-confirmation-button']";

    private final Duration controlTimeout;
    private final ChromeTabs tabs;

    /** {@code controlTimeout}: how long the delete button, the dialog, then its closing may take to appear. */
    public PlaywrightListingDeleter(Duration controlTimeout, ChromeTabs tabs) {
        this.controlTimeout = controlTimeout;
        this.tabs = tabs;
    }

    @Override
    public Optional<ScannedListing> delete(ListingLink listing, DeletionVisit visit) {
        return tabs.inNewTab(page -> {
            // The member watches the clicks happen, as in the edit form of the backup.
            page.bringToFront();
            ScannedListing read = readable(() -> ListingPage.openUnlessGone(page, listing))
                    .orElseThrow(() -> new VintedUnreachable("HTTP 404 on " + listing.url()));
            // Observed on 2026-11-14: the button shows at once, but its click does nothing until the page's scripts
            // have run, which they have by the load event (React wired it at 3.9 s, load came at 4.9 s).
            page.waitForLoadState(LoadState.LOAD);
            Locator deleteButton = visible(page.locator(DELETE_BUTTON), "no delete button on " + listing.url());
            if (!visit.agreesToDelete(read)) {
                return Optional.of(read);
            }
            deleteButton.click();
            Locator confirmation = visible(page.locator(CONFIRMATION_BUTTON), "no confirmation of the deletion");
            visit.aboutToConfirm();
            confirmation.click();
            page.locator(CONFIRMATION_DIALOG).waitFor(waitingFor(WaitForSelectorState.HIDDEN));
            letTheDeletionRequestEnd(page);
            return readable(() -> ListingPage.openUnlessGone(page, listing));
        });
    }

    @Override
    public Optional<ScannedListing> show(ListingLink listing) {
        return tabs.inNewTab(page -> readable(() -> ListingPage.openUnlessGone(page, listing)));
    }

    /** A control shown twice is as doubtful as a missing one: which one would be clicked? */
    private Locator visible(Locator control, String missing) {
        try {
            control.first().waitFor(waitingFor(WaitForSelectorState.VISIBLE));
        } catch (TimeoutError notThere) {
            throw new ListingPageUnreadable(missing);
        }
        int shown = control.count();
        if (shown != 1) {
            throw new ListingPageUnreadable(shown + " controls where one was expected: " + missing);
        }
        return control;
    }

    /**
     * Leaving the page cancels what it still has to send: the dialog may close before Vinted answers the deletion.
     * A page that keeps talking to Vinted does not stop the proof, which only ever doubts.
     */
    private void letTheDeletionRequestEnd(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(controlTimeout.toMillis()));
        } catch (TimeoutError stillTalking) {
            LOG.warn("Listing page still busy {} after the confirmation, opening it again anyway", controlTimeout);
        }
    }

    private Locator.WaitForOptions waitingFor(WaitForSelectorState state) {
        return new Locator.WaitForOptions().setState(state).setTimeout(controlTimeout.toMillis());
    }

    private static <T> T readable(Supplier<T> reading) {
        try {
            return reading.get();
        } catch (WardrobeUnreadable unexpected) {
            throw new ListingPageUnreadable(unexpected.getMessage());
        }
    }
}
