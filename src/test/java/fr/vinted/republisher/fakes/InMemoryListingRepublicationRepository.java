package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationId;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryListingRepublicationRepository implements ListingRepublicationRepository {

    private final Map<ListingRepublicationId, ListingRepublication> republications = new LinkedHashMap<>();
    private int savesBeforeTheDatabaseGoesDown = Integer.MAX_VALUE;

    public InMemoryListingRepublicationRepository whereDatabaseIsDown() {
        savesBeforeTheDatabaseGoesDown = 0;
        return this;
    }

    public List<ListingRepublication> findAll() {
        return List.copyOf(republications.values());
    }

    @Override
    public List<ListingRepublication> findAllLatest() {
        return republications.values().stream().map(ListingRepublication::listingId).distinct()
                .map(listing -> findLatest(listing).orElseThrow()).toList();
    }

    @Override
    public Optional<ListingRepublication> findLatest(VintedListingId listing) {
        return republications.values().stream().filter(republication -> republication.listingId().equals(listing))
                .max(Comparator.comparing(ListingRepublication::attemptedAt));
    }

    @Override
    public Set<VintedListingId> findNewListings() {
        return republications.values().stream().flatMap(republication -> republication.newListingId().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<Instant> findAttemptedSince(Instant since) {
        return republications.values().stream().map(ListingRepublication::attemptedAt)
                .filter(attempted -> attempted.isAfter(since))
                .toList();
    }

    @Override
    public Optional<ListingRepublication> findPublishedAs(VintedListingId newListing) {
        return republications.values().stream()
                .filter(republication -> republication.newListingId().filter(newListing::equals).isPresent())
                .findFirst();
    }

    @Override
    public void save(ListingRepublication republication) {
        if (savesBeforeTheDatabaseGoesDown == 0) {
            throw new IllegalStateException("database down");
        }
        savesBeforeTheDatabaseGoesDown--;
        republications.put(republication.id(), republication);
    }
}
