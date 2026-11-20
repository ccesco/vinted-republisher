package fr.vinted.republisher.application.republication;

import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingContentReader;
import fr.vinted.republisher.domain.backup.ListingFieldDifference;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.NewListingLink;
import fr.vinted.republisher.domain.republication.NewListingLinkInvalid;
import fr.vinted.republisher.domain.republication.NewListingMismatch;
import fr.vinted.republisher.domain.republication.NoRepublicationToVerify;
import fr.vinted.republisher.domain.republication.RepublicationReport;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The member looked on Vinted and says what a republication in error became: published, with the link of the new
 * listing, whose page alone is read to check it; or not published, without opening Chrome. A listing to link (US-48)
 * is only validated published, its new listing being linked as of the validation. Never clicks on Vinted.
 */
public class ValidateListingRepublication {

    private final VintedSession session;
    private final ListingBackupRepository listingBackups;
    private final ListingRepublicationRepository republications;
    private final ListingDeletionRepository deletions;
    private final ListingRepository listings;
    private final ListingContentReader contents;
    private final BackupPace backupPace;
    private final Clock clock;
    private final ShowListingRepublication showListingRepublication;
    private final URI vintedSite;
    private final JournalWriter journal;

    public ValidateListingRepublication(VintedSession session, ListingBackupRepository listingBackups,
                                        ListingRepublicationRepository republications,
                                        ListingDeletionRepository deletions, ListingRepository listings,
                                        ListingContentReader contents, BackupPace backupPace, Clock clock,
                                        ShowListingRepublication showListingRepublication, URI vintedSite,
                                        JournalWriter journal) {
        this.session = session;
        this.listingBackups = listingBackups;
        this.republications = republications;
        this.deletions = deletions;
        this.listings = listings;
        this.contents = contents;
        this.backupPace = backupPace;
        this.clock = clock;
        this.showListingRepublication = showListingRepublication;
        this.vintedSite = vintedSite;
        this.journal = journal;
    }

    /**
     * Throws {@link ListingNotBackedUp}, {@link NoRepublicationToVerify}, {@link NewListingLinkInvalid} or
     * {@link BrowserBusy} before opening Chrome, {@link NewListingMismatch} when the page has another title or price.
     */
    public RepublicationReport published(VintedListingId id, Optional<String> link) {
        return journal.journaled(JournaledAction.REPUBLICATION_VALIDATION, id, () -> publishedNow(id, link),
                report -> ActionOutcome.SUCCEEDED, Optional.of("REPUBLICATION_FAILED"));
    }

    private RepublicationReport publishedNow(VintedListingId id, Optional<String> link) {
        ListingBackup backup = listingBackups.find(id).orElseThrow(() -> new ListingNotBackedUp(id));
        inErrorOrToLink(id);
        NewListingLink newListing = NewListingLink.parse(link, vintedSite);
        // Checked before Chrome is opened, then again under the lock: another validation may have settled it since.
        requireAnotherListing(id, newListing);
        List<ListingFieldDifference> comparison = underLock(id, () -> {
            Optional<ListingRepublication> failed = inErrorOrToLink(id);
            requireAnotherListing(id, newListing);
            ListingReading read = readPage(newListing.id());
            if (!read.content().title().equals(backup.content().title())
                    || !read.content().price().equals(backup.content().price())) {
                throw new NewListingMismatch(newListing.id());
            }
            Instant validatedAt = clock.instant();
            listings.saveAll(List.of(Listing.seen(new ScannedListing(new ListingLink(newListing.id(),
                    newListing.url()), read.content().title(), read.content().price(), true), validatedAt)));
            republications.save(failed.map(inError -> inError.published(newListing.id(), validatedAt))
                    .orElseGet(() -> ListingRepublication.linked(backup, newListing.id(), validatedAt)));
            return ListingFieldDifference.between(backup.content(), backup.photos().size(), read);
        });
        return showListingRepublication.verified(id, Optional.of(comparison));
    }

    /** Throws {@link ListingNotBackedUp}, {@link NoRepublicationToVerify} or {@link BrowserBusy}. */
    public RepublicationReport notPublished(VintedListingId id) {
        return journal.journaled(JournaledAction.REPUBLICATION_VALIDATION, id, () -> notPublishedNow(id),
                report -> ActionOutcome.NOT_DONE, Optional.of("REPUBLICATION_FAILED"));
    }

    private RepublicationReport notPublishedNow(VintedListingId id) {
        listingBackups.find(id).orElseThrow(() -> new ListingNotBackedUp(id));
        latestInError(id);
        // The lock keeps a verification running meanwhile from settling it the other way.
        underLock(id, () -> {
            ListingRepublication notPublished = latestInError(id).notPublished(clock.instant());
            republications.save(notPublished);
            return notPublished;
        });
        return showListingRepublication.verified(id, Optional.empty());
    }

    /** Neither the old listing nor the new listing of another republication can be this one's new listing. */
    private void requireAnotherListing(VintedListingId id, NewListingLink newListing) {
        if (newListing.id().equals(id) || republications.findNewListings().contains(newListing.id())) {
            throw new NewListingLinkInvalid();
        }
    }

    /** The republication in error, or none for a listing to link (US-48), which is only ever validated published. */
    private Optional<ListingRepublication> inErrorOrToLink(VintedListingId id) {
        Optional<ListingRepublication> latest = republications.findLatest(id);
        if (ListingRepublication.toLink(deletions.findLatest(id), latest)) {
            return Optional.empty();
        }
        return Optional.of(latestInError(id));
    }

    private ListingRepublication latestInError(VintedListingId id) {
        ListingRepublication republication = republications.findLatest(id)
                .orElseThrow(() -> new NoRepublicationToVerify(id));
        republication.requireFailed();
        return republication;
    }

    private ListingReading readPage(VintedListingId newListing) {
        List<ListingReading> read = new ArrayList<>();
        contents.read(List.of(newListing), backupPace, read::add);
        if (read.isEmpty()) {
            throw new ListingUnreadable("nothing read on " + newListing.value());
        }
        return read.getFirst();
    }

    private <T> T underLock(VintedListingId id, Supplier<T> action) {
        if (!session.start(BrowserAction.REPUBLISHING, id)) {
            throw new BrowserBusy();
        }
        try {
            return action.get();
        } finally {
            session.finish(id);
        }
    }
}
