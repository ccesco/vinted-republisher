package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.deletion.DeletionVisit;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;

import java.util.Optional;
import java.util.function.Supplier;

/** A listing page on a pretend Vinted: what it shows, and what happens once its deletion is confirmed. */
public class FakeListingDeleter implements ListingDeleter {

    private Optional<ScannedListing> shown = Optional.empty();
    private boolean goneOnceConfirmed = true;
    private Optional<Supplier<RuntimeException>> failureBeforeAnyClick = Optional.empty();
    private Optional<Supplier<RuntimeException>> failureAfterTheConfirmation = Optional.empty();
    private int pagesOpened;
    private int confirmationClicks;

    public FakeListingDeleter showing(ScannedListing listing) {
        shown = Optional.of(listing);
        return this;
    }

    public FakeListingDeleter showingNothing() {
        shown = Optional.empty();
        return this;
    }

    public FakeListingDeleter whereTheListingStaysOnceConfirmed() {
        goneOnceConfirmed = false;
        return this;
    }

    public FakeListingDeleter whereTheListingGoesOnceConfirmed() {
        goneOnceConfirmed = true;
        return this;
    }

    public FakeListingDeleter whereSessionIsDisconnected() {
        return failingBeforeAnyClick(() -> new SessionDisconnected("redirected to /member/login"));
    }

    public FakeListingDeleter whereBrowserCannotStart() {
        return failingBeforeAnyClick(() -> new BrowserUnavailable("Chrome not found"));
    }

    public FakeListingDeleter whereVintedRefuses() {
        return failingBeforeAnyClick(() -> new VintedUnreachable("HTTP 403"));
    }

    public FakeListingDeleter whereTheDeleteButtonIsMissing() {
        return failingBeforeAnyClick(() -> new ListingPageUnreadable("no delete button"));
    }

    public FakeListingDeleter whereBrowserStopsAfterTheConfirmation() {
        failureAfterTheConfirmation = Optional.of(() -> new BrowserUnavailable("Chrome closed"));
        return this;
    }

    public int pagesOpened() {
        return pagesOpened;
    }

    public int confirmationClicks() {
        return confirmationClicks;
    }

    @Override
    public Optional<ScannedListing> delete(ListingLink listing, DeletionVisit visit) {
        pagesOpened++;
        failureBeforeAnyClick.ifPresent(failure -> {
            throw failure.get();
        });
        ScannedListing read = shown.orElseThrow(() -> new VintedUnreachable("HTTP 404"));
        if (!visit.agreesToDelete(read)) {
            return shown;
        }
        confirmationClicks++;
        failureAfterTheConfirmation.ifPresent(failure -> {
            throw failure.get();
        });
        if (goneOnceConfirmed) {
            shown = Optional.empty();
        }
        pagesOpened++;
        return shown;
    }

    @Override
    public Optional<ScannedListing> show(ListingLink listing) {
        pagesOpened++;
        failureBeforeAnyClick.ifPresent(failure -> {
            throw failure.get();
        });
        return shown;
    }

    private FakeListingDeleter failingBeforeAnyClick(Supplier<RuntimeException> failure) {
        failureBeforeAnyClick = Optional.of(failure);
        return this;
    }
}
