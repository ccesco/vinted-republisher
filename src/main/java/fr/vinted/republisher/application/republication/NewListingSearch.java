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
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Looks for the new listing of a republication among the listings the wardrobe shows on opening, the newest, opening
 * as a scan does only the listings that may be it, without waiting.
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

    /** Right after Publish: the only unknown listing with the saved title and price; empty if none or several. */
    Optional<ScannedListing> find(ListingContent saved) {
        Set<VintedListingId> known = listings.findAll().stream().map(Listing::id)
                .collect(Collectors.toUnmodifiableSet());
        return ListingRepublication.newListingAmong(readNewest(link -> !known.contains(link.id())), saved);
    }

    /**
     * Later, to verify a republication: the listings with the saved title and price, among those unknown and those a
     * scan may have recorded since, leaving out the old listing and the new listings of other republications. The
     * newest listings first; the whole wardrobe only if none of them matches, as none means "not published".
     */
    List<ScannedListing> findAgain(ListingContent saved, Set<VintedListingId> otherNewListings) {
        Set<VintedListingId> unlikely = listings.findAll().stream()
                .filter(known -> !known.title().equals(saved.title()) || !known.price().equals(saved.price()))
                .map(Listing::id).collect(Collectors.toSet());
        unlikely.add(saved.id());
        unlikely.addAll(otherNewListings);
        Predicate<ListingLink> candidate = link -> !unlikely.contains(link.id());
        List<ScannedListing> amongNewest = ListingRepublication.matching(readNewest(candidate), saved);
        if (!amongNewest.isEmpty()) {
            return amongNewest;
        }
        Opening opening = new Opening(candidate);
        wardrobe.read(pace, opening);
        return ListingRepublication.matching(opening.read, saved);
    }

    private List<ScannedListing> readNewest(Predicate<ListingLink> toOpen) {
        Opening opening = new Opening(toOpen);
        wardrobe.readNewest(pace, opening);
        return opening.read;
    }

    private static final class Opening implements WardrobeVisit {

        private final Predicate<ListingLink> toOpen;
        private final List<ScannedListing> read = new ArrayList<>();

        private Opening(Predicate<ListingLink> toOpen) {
            this.toOpen = toOpen;
        }

        @Override
        public List<ListingLink> listingsToOpen(WardrobeListing found) {
            return found.links().stream().filter(toOpen).toList();
        }

        @Override
        public void record(ScannedListing listing) {
            read.add(listing);
        }
    }
}
