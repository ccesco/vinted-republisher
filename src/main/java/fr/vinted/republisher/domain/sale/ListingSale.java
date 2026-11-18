package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A known listing seen sold in the wardrobe, dated when the tool first noticed it: Vinted's cards do not tell when it
 * was actually sold. Never changed nor removed.
 */
public record ListingSale(VintedListingId listingId, Instant noticedAt) {

    public ListingSale {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(noticedAt);
    }

    /** Only known listings: the tool never opens a sold one, and would know neither its title nor its price. */
    public static List<ListingSale> newlyNoticed(Collection<VintedListingId> shownSold, Collection<Listing> inventory,
                                                 Collection<ListingSale> recorded, Instant noticedAt) {
        Set<VintedListingId> known = inventory.stream().map(Listing::id).collect(Collectors.toSet());
        Set<VintedListingId> alreadySold = recorded.stream().map(ListingSale::listingId).collect(Collectors.toSet());
        return shownSold.stream().distinct().filter(known::contains).filter(listing -> !alreadySold.contains(listing))
                .map(listing -> new ListingSale(listing, noticedAt)).toList();
    }
}
