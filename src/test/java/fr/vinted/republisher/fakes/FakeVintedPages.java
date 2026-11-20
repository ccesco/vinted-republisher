package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.deletion.DeletionVisit;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.session.VintedUnreachable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Several listing pages on a pretend Vinted, each deleted on its own (US-47). */
public class FakeVintedPages implements ListingDeleter {

    private final Map<VintedListingId, ScannedListing> shown = new HashMap<>();
    private final List<VintedListingId> opened = new ArrayList<>();
    private boolean goneOnceConfirmed = true;
    private Optional<Supplier<RuntimeException>> failureBeforeAnyClick = Optional.empty();
    private boolean failsAfterTheConfirmation;
    private int confirmationClicks;

    public synchronized FakeVintedPages showing(ScannedListing listing) {
        shown.put(listing.id(), listing);
        return this;
    }

    public synchronized FakeVintedPages showingNothingFor(VintedListingId listing) {
        shown.remove(listing);
        return this;
    }

    public synchronized FakeVintedPages whereTheListingStaysOnceConfirmed() {
        goneOnceConfirmed = false;
        return this;
    }

    public synchronized FakeVintedPages failingBeforeAnyClick(Supplier<RuntimeException> failure) {
        failureBeforeAnyClick = Optional.of(failure);
        return this;
    }

    public synchronized FakeVintedPages whereBrowserStopsAfterTheConfirmation() {
        failsAfterTheConfirmation = true;
        return this;
    }

    public synchronized List<VintedListingId> opened() {
        return List.copyOf(opened);
    }

    public synchronized int confirmationClicks() {
        return confirmationClicks;
    }

    @Override
    public synchronized Optional<ScannedListing> delete(ListingLink listing, DeletionVisit visit) {
        ScannedListing read = open(listing).orElseThrow(() -> new VintedUnreachable("HTTP 404"));
        if (!visit.agreesToDelete(read)) {
            return Optional.of(read);
        }
        visit.aboutToConfirm();
        confirmationClicks++;
        if (failsAfterTheConfirmation) {
            throw new BrowserUnavailable("Chrome closed");
        }
        if (goneOnceConfirmed) {
            shown.remove(listing.id());
        }
        return open(listing);
    }

    @Override
    public synchronized Optional<ScannedListing> show(ListingLink listing) {
        return open(listing);
    }

    private Optional<ScannedListing> open(ListingLink listing) {
        opened.add(listing.id());
        failureBeforeAnyClick.ifPresent(failure -> {
            throw failure.get();
        });
        return Optional.ofNullable(shown.get(listing.id()));
    }
}
