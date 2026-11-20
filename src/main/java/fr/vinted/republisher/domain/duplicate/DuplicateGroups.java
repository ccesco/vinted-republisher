package fr.vinted.republisher.domain.duplicate;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The duplicates of the wardrobe. Only present listings count: an old listing, republished since, has the title and
 * the price of its new listing without being a duplicate.
 */
public record DuplicateGroups(List<DuplicateGroup> groups) {

    public DuplicateGroups {
        groups = List.copyOf(groups);
    }

    /** The groups among the listings, the one whose newest listing was first seen most recently first. */
    public static DuplicateGroups among(Collection<Listing> listings) {
        Map<Sameness, List<Listing>> bySameness = listings.stream().filter(Listing::present)
                .sorted(Comparator.comparing(listing -> listing.id().value()))
                .collect(Collectors.groupingBy(listing -> new Sameness(listing.title(), listing.price())));
        return new DuplicateGroups(bySameness.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> new DuplicateGroup(entry.getKey().title(), entry.getKey().price(), entry.getValue()))
                .sorted(Comparator.comparing(DuplicateGroup::newestFirstSeenAt).reversed())
                .toList());
    }

    /** The other listings of the group of this one, by identifier: empty if it is in no group. */
    public List<Listing> twinsOf(VintedListingId listing) {
        return groups.stream()
                .filter(group -> group.listings().stream().anyMatch(member -> member.id().equals(listing)))
                .flatMap(group -> group.listings().stream())
                .filter(member -> !member.id().equals(listing))
                .toList();
    }

    /** In a group, and without history to keep (republication, deletion, sale). */
    public boolean deletable(VintedListingId listing, Set<VintedListingId> withHistory) {
        return !twinsOf(listing).isEmpty() && !withHistory.contains(listing);
    }

    /** The twins of a listing that may be deleted as a duplicate; refuses any other. */
    public List<Listing> requireDeletable(VintedListingId listing, Set<VintedListingId> withHistory) {
        List<Listing> twins = twinsOf(listing);
        if (twins.isEmpty()) {
            throw new NotADuplicate(listing);
        }
        if (withHistory.contains(listing)) {
            throw new ListingHasHistory(listing);
        }
        return twins;
    }

    private record Sameness(String title, Price price) {
    }
}
