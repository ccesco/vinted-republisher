package fr.vinted.republisher.domain.wardrobe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** What going through the wardrobe found: the total it announces and the links to its listings, each once. */
public record WardrobeListing(int displayedTotal, List<ListingLink> links) {

    public WardrobeListing {
        links = onePerListing(links);
    }

    /** A listing counted twice would make up for a missing one, and the total would no longer prove anything. */
    private static List<ListingLink> onePerListing(List<ListingLink> links) {
        Map<VintedListingId, ListingLink> byListing = new LinkedHashMap<>();
        links.forEach(link -> byListing.putIfAbsent(link.id(), link));
        return List.copyOf(byListing.values());
    }

    public WardrobeListing with(List<ListingLink> further) {
        return new WardrobeListing(displayedTotal, Stream.concat(links.stream(), further.stream()).toList());
    }

    public boolean allLinksFound() {
        return links.size() >= displayedTotal;
    }

    /** Zero links is never a success: it would look the same as a page Vinted has changed. */
    public void requireComplete() {
        if (links.isEmpty()) {
            throw new WardrobeUnreadable("no link found");
        }
        if (links.size() != displayedTotal) {
            throw new WardrobeUnreadable(links.size() + " links found, " + displayedTotal + " announced");
        }
    }
}
