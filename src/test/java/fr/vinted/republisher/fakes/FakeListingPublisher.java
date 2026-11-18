package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.republication.FieldNotFillable;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.republication.ListingPublisher;
import fr.vinted.republisher.domain.republication.RepublicationVisit;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** The listing form of a pretend Vinted: what it was filled with, and what shows in the wardrobe once published. */
public class FakeListingPublisher implements ListingPublisher {

    private final FakeWardrobeReader wardrobe;
    private final List<ScannedListing> shownOncePublished = new ArrayList<>();
    private Optional<Supplier<RuntimeException>> failureBeforePublishing = Optional.empty();
    private Optional<Supplier<RuntimeException>> failureAfterPublishing = Optional.empty();
    private Optional<ListingContent> filledContent = Optional.empty();
    private List<DownloadedPhoto> sentPhotos = List.of();
    private int formsOpened;
    private int publishClicks;

    /** Publishing changes what {@code wardrobe} holds. */
    public FakeListingPublisher(FakeWardrobeReader wardrobe) {
        this.wardrobe = wardrobe;
    }

    /** The wardrobe holds these listings once Publish is clicked. */
    public FakeListingPublisher showingOncePublished(ScannedListing... listings) {
        shownOncePublished.clear();
        shownOncePublished.addAll(List.of(listings));
        return this;
    }

    public FakeListingPublisher whereSessionIsDisconnected() {
        return failingBeforePublishing(() -> new SessionDisconnected("redirected to /member/login"));
    }

    public FakeListingPublisher whereBrowserCannotStart() {
        return failingBeforePublishing(() -> new BrowserUnavailable("Chrome not found"));
    }

    public FakeListingPublisher whereVintedRefuses() {
        return failingBeforePublishing(() -> new VintedUnreachable("HTTP 403"));
    }

    public FakeListingPublisher whereTheFormIsUnreadable() {
        return failingBeforePublishing(() -> new ListingFormUnreadable("no title field"));
    }

    public FakeListingPublisher whereBrowserFailsUnexpectedly() {
        return failingBeforePublishing(() -> new IllegalStateException("unexpected browser failure"));
    }

    public FakeListingPublisher whereSizeCannotBeFilled() {
        return failingBeforePublishing(() -> new FieldNotFillable(ListingFormField.SIZE, Optional.of(588L)));
    }

    public FakeListingPublisher whereBrowserStopsAfterPublishing() {
        failureAfterPublishing = Optional.of(() -> new BrowserUnavailable("Chrome closed"));
        return this;
    }

    public int formsOpened() {
        return formsOpened;
    }

    public int publishClicks() {
        return publishClicks;
    }

    public Optional<ListingContent> filledContent() {
        return filledContent;
    }

    public List<DownloadedPhoto> sentPhotos() {
        return sentPhotos;
    }

    @Override
    public void publish(ListingContent content, List<DownloadedPhoto> photos, RepublicationVisit visit) {
        formsOpened++;
        failureBeforePublishing.ifPresent(failure -> {
            throw failure.get();
        });
        sentPhotos = List.copyOf(photos);
        filledContent = Optional.of(content);
        if (!visit.readyToPublish()) {
            return;
        }
        publishClicks++;
        failureAfterPublishing.ifPresent(failure -> {
            throw failure.get();
        });
        wardrobe.holding(shownOncePublished.toArray(ScannedListing[]::new));
    }

    private FakeListingPublisher failingBeforePublishing(Supplier<RuntimeException> failure) {
        failureBeforePublishing = Optional.of(failure);
        return this;
    }
}
