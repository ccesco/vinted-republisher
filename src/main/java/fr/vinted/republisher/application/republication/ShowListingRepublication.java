package fr.vinted.republisher.application.republication;

import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.deletion.DeletionReport;
import fr.vinted.republisher.domain.republication.ListingFieldDifference;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationReport;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Optional;

/** The backup of a listing and its deletion, with its republication and the mode republications run in. */
public class ShowListingRepublication {

    private final ShowListingDeletion showListingDeletion;
    private final ListingRepublicationRepository republications;
    private final ListingRepository listings;
    private final RepublicationMode mode;

    public ShowListingRepublication(ShowListingDeletion showListingDeletion,
                                    ListingRepublicationRepository republications, ListingRepository listings,
                                    RepublicationMode mode) {
        this.showListingDeletion = showListingDeletion;
        this.republications = republications;
        this.listings = listings;
        this.mode = mode;
    }

    /** Throws {@link ListingNotBackedUp} if the listing has no backup. */
    public RepublicationReport show(VintedListingId listing) {
        return around(showListingDeletion.show(listing));
    }

    /** The given report, such as the one a deletion answers with, completed with what concerns its republication. */
    public RepublicationReport around(DeletionReport report) {
        return report(report, false, Optional.empty());
    }

    RepublicationReport report(VintedListingId listing, boolean dryRunPassed) {
        return report(showListingDeletion.show(listing), dryRunPassed, Optional.empty());
    }

    RepublicationReport verified(VintedListingId listing, Optional<List<ListingFieldDifference>> comparison) {
        return report(showListingDeletion.show(listing), false, comparison);
    }

    private RepublicationReport report(DeletionReport report, boolean dryRunPassed,
                                       Optional<List<ListingFieldDifference>> comparison) {
        Optional<ListingRepublication> republication = republications.findLatest(report.backup().backup().id());
        return new RepublicationReport(report, republication,
                republication.flatMap(ListingRepublication::newListingId).flatMap(listings::find), mode, dryRunPassed,
                comparison);
    }
}
