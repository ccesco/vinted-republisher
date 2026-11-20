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
import fr.vinted.republisher.domain.republication.NewListingSearchResult;
import fr.vinted.republisher.domain.republication.NoRepublicationToVerify;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.republication.RepublicationReport;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Looks at Vinted, never clicking, to settle a republication in error and to compare a published one with its backup.
 * Nothing is recorded before Vinted has answered, so a failure leaves the republication as it was.
 */
public class VerifyListingRepublication {

    private final VintedSession session;
    private final ListingBackupRepository listingBackups;
    private final ListingRepublicationRepository republications;
    private final ListingDeletionRepository deletions;
    private final ListingRepository listings;
    private final NewListingSearch newListingSearch;
    private final ListingContentReader contents;
    private final BackupPace backupPace;
    private final Clock clock;
    private final ShowListingRepublication showListingRepublication;
    private final JournalWriter journal;

    public VerifyListingRepublication(VintedSession session, ListingBackupRepository listingBackups,
                                      ListingRepublicationRepository republications,
                                      ListingDeletionRepository deletions, ListingRepository listings,
                                      NewListingSearch newListingSearch, ListingContentReader contents,
                                      BackupPace backupPace, Clock clock,
                                      ShowListingRepublication showListingRepublication, JournalWriter journal) {
        this.session = session;
        this.listingBackups = listingBackups;
        this.republications = republications;
        this.deletions = deletions;
        this.listings = listings;
        this.newListingSearch = newListingSearch;
        this.contents = contents;
        this.backupPace = backupPace;
        this.clock = clock;
        this.showListingRepublication = showListingRepublication;
        this.journal = journal;
    }

    /**
     * Throws {@link ListingNotBackedUp}, {@link NoRepublicationToVerify} or {@link BrowserBusy} before opening Chrome.
     * Several listings matching the backup leave the republication in error, or a listing to link as it was.
     */
    public RepublicationReport verify(VintedListingId id) {
        Verification verification = journal.journaledWithReason(JournaledAction.REPUBLICATION_VERIFICATION, id,
                () -> verifyNow(id), Verification::outcome, Verification::reason,
                Optional.of("REPUBLICATION_FAILED"));
        verification.comparisonFailure().ifPresent(failure -> {
            throw failure;
        });
        return verification.report();
    }

    private Verification verifyNow(VintedListingId id) {
        ListingBackup backup = listingBackups.find(id).orElseThrow(() -> new ListingNotBackedUp(id));
        toVerify(id);
        if (!session.start(BrowserAction.REPUBLISHING, id)) {
            throw new BrowserBusy();
        }
        Optional<List<ListingFieldDifference>> comparison;
        try {
            // Read again under the lock: another verification may have settled it meanwhile.
            Optional<ListingRepublication> toVerify = toVerify(id);
            if (toVerify.isEmpty()) {
                return link(id, backup);
            }
            ListingRepublication republication = toVerify.get();
            if (republication.outcome() != RepublicationOutcome.FAILED) {
                comparison = republication.newListingId().map(newListing -> compare(backup, newListing));
            } else {
                ListingRepublication settled = settle(republication, backup);
                try {
                    comparison = settled.newListingId().map(newListing -> compare(backup, newListing));
                } catch (RuntimeException failure) {
                    // The proof is recorded: the verification succeeded, even though the comparison failed.
                    return new Verification(showListingRepublication.verified(id, Optional.empty()),
                            Optional.of(failure));
                }
            }
        } finally {
            session.finish(id);
        }
        return new Verification(showListingRepublication.verified(id, comparison), Optional.empty());
    }

    /** {@code comparisonFailure}: the comparison that failed once the republication was proven, passed on after. */
    private record Verification(RepublicationReport report, Optional<RuntimeException> comparisonFailure) {

        /**
         * Published, whether just proven, linked or only compared; a listing to link without a single new listing
         * stays as it was; otherwise the republication stays in error.
         */
        ActionOutcome outcome() {
            if (report.newListingSearch().isPresent()) {
                return ActionOutcome.NOT_DONE;
            }
            return report.lastRepublication().map(ListingRepublication::outcome)
                    .filter(RepublicationOutcome.PUBLISHED::equals).isPresent()
                    ? ActionOutcome.SUCCEEDED : ActionOutcome.IN_DOUBT;
        }

        Optional<String> reason() {
            return report.newListingSearch().map(NewListingSearchResult::journalReason);
        }
    }

    /** The republication in error or published to verify, or none for a listing to link (US-48). */
    private Optional<ListingRepublication> toVerify(VintedListingId id) {
        Optional<ListingRepublication> latest = republications.findLatest(id);
        if (ListingRepublication.toLink(deletions.findLatest(id), latest)) {
            return Optional.empty();
        }
        ListingRepublication republication = latest.orElseThrow(() -> new NoRepublicationToVerify(id));
        republication.requireToVerify();
        return Optional.of(republication);
    }

    /** Only a single new listing is linked: none or several record nothing, the member then pastes its link. */
    private Verification link(VintedListingId id, ListingBackup backup) {
        List<ScannedListing> matching = newListingSearch.findAgain(backup.content(), republications.findNewListings());
        if (matching.size() != 1) {
            return new Verification(showListingRepublication.searchedWithoutResult(id,
                    NewListingSearchResult.of(matching.size())), Optional.empty());
        }
        ScannedListing found = matching.getFirst();
        Instant linkedAt = clock.instant();
        listings.saveAll(List.of(Listing.seen(found, linkedAt)));
        republications.save(ListingRepublication.linked(backup, found.id(), linkedAt));
        try {
            return new Verification(showListingRepublication.verified(id, Optional.of(compare(backup, found.id()))),
                    Optional.empty());
        } catch (RuntimeException failure) {
            // The link is recorded: the verification succeeded, even though the comparison failed.
            return new Verification(showListingRepublication.verified(id, Optional.empty()), Optional.of(failure));
        }
    }

    private ListingRepublication settle(ListingRepublication failed, ListingBackup backup) {
        Set<VintedListingId> otherNewListings = republications.findNewListings();
        List<ScannedListing> matching = newListingSearch.findAgain(backup.content(), otherNewListings);
        Instant checkedAt = clock.instant();
        // None among the newest listings proves nothing: the member then says what Vinted shows.
        if (matching.size() != 1) {
            return failed;
        }
        ScannedListing found = matching.getFirst();
        listings.saveAll(List.of(Listing.seen(found, checkedAt)));
        ListingRepublication published = failed.published(found.id(), checkedAt);
        republications.save(published);
        return published;
    }

    private List<ListingFieldDifference> compare(ListingBackup backup, VintedListingId newListing) {
        List<ListingReading> read = new ArrayList<>();
        contents.read(List.of(newListing), backupPace, read::add);
        if (read.isEmpty()) {
            throw new ListingUnreadable("nothing read on " + newListing.value());
        }
        return ListingFieldDifference.between(backup.content(), backup.photos().size(), read.getFirst());
    }
}
