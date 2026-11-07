package fr.vinted.republisher.domain.wardrobe;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One pass over the wardrobe, which may take several attempts: it stays unfinished until every listing was
 * read. Its start tells what it has read, since a listing is stamped when it is read and saved.
 */
public record WardrobeScan(WardrobeScanId id, Instant startedAt, Optional<Instant> finishedAt) {

    public WardrobeScan {
        Objects.requireNonNull(id);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(finishedAt);
    }

    public static WardrobeScan startedAt(Instant startedAt) {
        return new WardrobeScan(new WardrobeScanId(UUID.randomUUID()), startedAt, Optional.empty());
    }

    public WardrobeScan finished(Instant at) {
        return new WardrobeScan(id, startedAt, Optional.of(at));
    }

    /** An inactive listing is never saved, so a resumed scan opens it again. */
    public List<ListingLink> linksToOpen(List<ListingLink> links, List<Listing> inventory) {
        Set<VintedListingId> read = inventory.stream().filter(this::hasRead).map(Listing::id).collect(Collectors.toSet());
        return links.stream().filter(link -> !read.contains(link.id())).toList();
    }

    /** The listings this scan found gone, marked absent. */
    public List<Listing> missingFrom(List<Listing> inventory) {
        return inventory.stream().filter(Listing::present).filter(listing -> !hasRead(listing))
                .map(Listing::absent).toList();
    }

    private boolean hasRead(Listing listing) {
        return !listing.lastSeenAt().isBefore(startedAt);
    }
}
