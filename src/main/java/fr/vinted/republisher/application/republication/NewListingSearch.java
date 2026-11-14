package fr.vinted.republisher.application.republication;

import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.WardrobeVisit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proves a republication: one reading of the wardrobe right after Publish, opening as a scan does only the listings
 * the tool does not know, without waiting or reading again.
 */
public class NewListingSearch {

    private final WardrobeReader wardrobe;
    private final ScanPace pace;
    private final ListingRepository listings;

    public NewListingSearch(WardrobeReader wardrobe, ScanPace pace, ListingRepository listings) {
        this.wardrobe = wardrobe;
        this.pace = pace;
        this.listings = listings;
    }

    /** The only unknown listing with the saved title and price; empty if there is none or more than one. */
    Optional<ScannedListing> find(ListingContent saved) {
        UnknownListings unknown = new UnknownListings(listings.findAll().stream().map(Listing::id)
                .collect(Collectors.toUnmodifiableSet()));
        wardrobe.read(pace, unknown);
        return ListingRepublication.newListingAmong(unknown.read, saved);
    }

    private static final class UnknownListings implements WardrobeVisit {

        private final Set<VintedListingId> known;
        private final List<ScannedListing> read = new ArrayList<>();

        private UnknownListings(Set<VintedListingId> known) {
            this.known = known;
        }

        @Override
        public List<ListingLink> listingsToOpen(WardrobeListing found) {
            return found.links().stream().filter(link -> !known.contains(link.id())).toList();
        }

        @Override
        public void record(ScannedListing listing) {
            read.add(listing);
        }
    }
}
