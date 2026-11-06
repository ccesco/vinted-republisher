package fr.vinted.republisher.domain.wardrobe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** What one reading of the wardrobe found: the total it announces and the listings read, each once. */
public record WardrobeSnapshot(int displayedTotal, List<ScannedListing> listings) {

    public WardrobeSnapshot {
        listings = eachOnce(listings);
    }

    public WardrobeSnapshot with(List<ScannedListing> further) {
        return new WardrobeSnapshot(displayedTotal, Stream.concat(listings.stream(), further.stream()).toList());
    }

    /** Zero listings is never a success: it would look the same as a page Vinted has changed. */
    public void requireComplete() {
        if (listings.isEmpty()) {
            throw new WardrobeUnreadable("no listing read");
        }
        if (listings.size() != displayedTotal) {
            throw new WardrobeUnreadable(listings.size() + " listings read, " + displayedTotal + " announced");
        }
    }

    /** The whole inventory after this scan: known listings not found active any more stay, absent. */
    public List<Listing> applyTo(List<Listing> known, Instant scannedAt) {
        Map<VintedListingId, Listing> inventory = new LinkedHashMap<>();
        listings.stream().filter(ScannedListing::isActive)
                .forEach(listing -> inventory.put(listing.id(), Listing.seen(listing, scannedAt)));
        known.forEach(listing -> inventory.putIfAbsent(listing.id(), listing.absent()));
        return new ArrayList<>(inventory.values());
    }

    private static List<ScannedListing> eachOnce(List<ScannedListing> listings) {
        Map<VintedListingId, ScannedListing> distinct = new LinkedHashMap<>();
        listings.forEach(listing -> distinct.put(listing.id(), listing));
        return List.copyOf(distinct.values());
    }
}
