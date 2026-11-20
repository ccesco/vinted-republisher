package fr.vinted.republisher.domain.duplicate;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.Price;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Listings present at the last scan with exactly the same title and price, at least two, by identifier. */
public record DuplicateGroup(String title, Price price, List<Listing> listings) {

    public DuplicateGroup {
        Objects.requireNonNull(title);
        Objects.requireNonNull(price);
        listings = List.copyOf(listings);
    }

    /** When the tool first saw the newest listing of the group. */
    Instant newestFirstSeenAt() {
        return listings.stream().map(Listing::firstSeenAt).max(Instant::compareTo).orElseThrow();
    }
}
