package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The sales, most recently noticed first, and the total of their prices in each currency, without any exchange. */
public record SalesOverview(List<Sale> sales, List<Price> totals) {

    // One reading of the wardrobe notices several sales at once: the listing id keeps their order stable.
    private static final Comparator<Sale> MOST_RECENT_FIRST = Comparator.comparing(Sale::noticedAt).reversed()
            .thenComparing(sale -> sale.listingId().value());

    public SalesOverview {
        sales = List.copyOf(sales);
        totals = List.copyOf(totals);
    }

    public static SalesOverview of(Collection<ListingSale> recorded, Collection<Listing> inventory) {
        Map<VintedListingId, Listing> byId =
                inventory.stream().collect(Collectors.toMap(Listing::id, Function.identity()));
        List<Sale> sales = recorded.stream()
                .flatMap(sale -> Optional.ofNullable(byId.get(sale.listingId())).map(listing -> new Sale(
                        listing.id(), listing.title(), listing.price(), sale.noticedAt(), sale.origin())).stream())
                .sorted(MOST_RECENT_FIRST)
                .toList();
        return new SalesOverview(sales, totalsOf(sales));
    }

    private static List<Price> totalsOf(List<Sale> sales) {
        Map<String, BigDecimal> byCurrency = sales.stream().map(Sale::price).collect(Collectors.groupingBy(
                Price::currency, TreeMap::new, Collectors.reducing(BigDecimal.ZERO, Price::amount, BigDecimal::add)));
        return byCurrency.entrySet().stream().map(total -> new Price(total.getValue(), total.getKey())).toList();
    }
}
