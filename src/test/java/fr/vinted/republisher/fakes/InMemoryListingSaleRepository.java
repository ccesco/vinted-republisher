package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.sale.ListingAlreadySold;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.sale.SaleOrigin;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryListingSaleRepository implements ListingSaleRepository {

    private final List<ListingSale> sales = new ArrayList<>();

    @Override
    public synchronized List<ListingSale> findAll() {
        return List.copyOf(sales);
    }

    @Override
    public synchronized Optional<ListingSale> find(VintedListingId listing) {
        return sales.stream().filter(sale -> sale.listingId().equals(listing)).findFirst();
    }

    @Override
    public synchronized void addAll(List<ListingSale> added) {
        if (added.stream().anyMatch(sale -> find(sale.listingId()).isPresent())) {
            throw new IllegalStateException("A sale is already recorded for one of these listings");
        }
        sales.addAll(added);
    }

    @Override
    public synchronized void add(ListingSale sale) {
        if (find(sale.listingId()).isPresent()) {
            throw new ListingAlreadySold(sale.listingId());
        }
        sales.add(sale);
    }

    @Override
    public synchronized void removeNoted(VintedListingId listing) {
        sales.removeIf(sale -> sale.listingId().equals(listing) && sale.origin() == SaleOrigin.NOTED);
    }
}
