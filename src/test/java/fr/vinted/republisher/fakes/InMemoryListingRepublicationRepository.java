package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryListingRepublicationRepository implements ListingRepublicationRepository {

    private final Map<VintedListingId, ListingRepublication> republications = new LinkedHashMap<>();
    private int savesBeforeTheDatabaseGoesDown = Integer.MAX_VALUE;

    public InMemoryListingRepublicationRepository whereDatabaseIsDown() {
        savesBeforeTheDatabaseGoesDown = 0;
        return this;
    }

    public List<ListingRepublication> findAll() {
        return List.copyOf(republications.values());
    }

    @Override
    public Optional<ListingRepublication> find(VintedListingId listing) {
        return Optional.ofNullable(republications.get(listing));
    }

    @Override
    public void save(ListingRepublication republication) {
        if (savesBeforeTheDatabaseGoesDown == 0) {
            throw new IllegalStateException("database down");
        }
        savesBeforeTheDatabaseGoesDown--;
        republications.put(republication.listingId(), republication);
    }
}
