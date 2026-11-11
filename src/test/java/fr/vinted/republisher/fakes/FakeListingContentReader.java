package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.backup.BackupFixtures;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingContentReader;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FakeListingContentReader implements ListingContentReader {

    private final Map<VintedListingId, ListingReading> readings = new HashMap<>();
    private final Map<VintedListingId, Supplier<RuntimeException>> failures = new HashMap<>();
    private final List<VintedListingId> opened = new ArrayList<>();
    private final List<Duration> pausesBetweenListings = new ArrayList<>();
    private Optional<RuntimeException> browserFailure = Optional.empty();
    private int tabs;
    private Runnable duringTheReading = () -> { };

    public FakeListingContentReader showing(ListingContent... contents) {
        List.of(contents).forEach(content -> readings.put(content.id(),
                new ListingReading(content, List.of(BackupFixtures.aPhoto("of " + content.id().value())))));
        return this;
    }

    public FakeListingContentReader showing(ListingReading... visits) {
        List.of(visits).forEach(reading -> readings.put(reading.id(), reading));
        return this;
    }

    /** Forms change on Vinted: what was opened or refused so far is forgotten. */
    public FakeListingContentReader afresh() {
        opened.clear();
        failures.clear();
        browserFailure = Optional.empty();
        return this;
    }

    public FakeListingContentReader whereVintedRefuses(long listing) {
        return failingOn(listing, () -> new VintedUnreachable("HTTP 403"));
    }

    public FakeListingContentReader whereFormRedirectsToLogin(long listing) {
        return failingOn(listing, () -> new SessionDisconnected("redirected to /member/login"));
    }

    public FakeListingContentReader whereFormIsUnreadable(long listing, String reason) {
        return failingOn(listing, () -> new ListingUnreadable(reason));
    }

    public FakeListingContentReader whereBrowserCannotStart() {
        browserFailure = Optional.of(new BrowserUnavailable("Chrome not found"));
        return this;
    }

    public FakeListingContentReader whereBrowserFailsUnexpectedly() {
        browserFailure = Optional.of(new IllegalStateException("unexpected browser failure"));
        return this;
    }

    public FakeListingContentReader during(Runnable action) {
        duringTheReading = action;
        return this;
    }

    public List<VintedListingId> opened() {
        return opened;
    }

    public List<Duration> pausesBetweenListings() {
        return pausesBetweenListings;
    }

    public int tabs() {
        return tabs;
    }

    @Override
    public void read(List<VintedListingId> listings, BackupPace pace, Consumer<ListingReading> record) {
        tabs++;
        duringTheReading.run();
        browserFailure.ifPresent(reason -> {
            throw reason;
        });
        Iterator<VintedListingId> toOpen = listings.iterator();
        while (toOpen.hasNext()) {
            record.accept(open(toOpen.next()));
            if (toOpen.hasNext()) {
                pausesBetweenListings.add(pace.nextPauseBetweenPages());
            }
        }
    }

    private ListingReading open(VintedListingId listing) {
        opened.add(listing);
        Supplier<RuntimeException> failure = failures.get(listing);
        if (failure != null) {
            throw failure.get();
        }
        return readings.get(listing);
    }

    private FakeListingContentReader failingOn(long listing, Supplier<RuntimeException> failure) {
        failures.put(new VintedListingId(listing), failure);
        return this;
    }
}
