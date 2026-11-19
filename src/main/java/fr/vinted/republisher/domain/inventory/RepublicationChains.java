package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The listings of each article, linked by published republications, both ways, read once for the whole inventory. */
final class RepublicationChains {

    private final Map<VintedListingId, VintedListingId> next = new HashMap<>();
    private final Map<VintedListingId, VintedListingId> previous = new HashMap<>();

    /** {@code latest} is the last attempt of every listing: a republished listing is never attempted again. */
    RepublicationChains(List<ListingRepublication> latest) {
        latest.stream().filter(republication -> republication.outcome() == RepublicationOutcome.PUBLISHED)
                .forEach(republication -> {
                    VintedListingId newListing = republication.newListingId().orElseThrow();
                    next.put(republication.listingId(), newListing);
                    previous.put(newListing, republication.listingId());
                });
    }

    /** Over the whole chain of {@code listing}: its published republications and attempts found not published. */
    RepublicationTally tallyOf(VintedListingId listing, Map<VintedListingId, Integer> notPublished) {
        Set<VintedListingId> chain = chainOf(listing);
        return new RepublicationTally((int) chain.stream().filter(next::containsKey).count(),
                chain.stream().mapToInt(member -> notPublished.getOrDefault(member, 0)).sum());
    }

    /** A chain that would loop (an id is never reused on Vinted) stops on a listing already seen. */
    private Set<VintedListingId> chainOf(VintedListingId listing) {
        Set<VintedListingId> chain = new HashSet<>(Set.of(listing));
        walk(listing, previous, chain);
        walk(listing, next, chain);
        return chain;
    }

    private static void walk(VintedListingId from, Map<VintedListingId, VintedListingId> links,
                             Set<VintedListingId> chain) {
        Optional<VintedListingId> step = Optional.ofNullable(links.get(from));
        while (step.isPresent() && chain.add(step.get())) {
            step = Optional.ofNullable(links.get(step.get()));
        }
    }
}
