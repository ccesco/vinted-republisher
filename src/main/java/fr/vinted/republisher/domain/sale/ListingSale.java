package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.ArticleId;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The sale of a known listing: noticed on a "Vendu" card of the wardrobe, dated when the tool first noticed it since
 * Vinted's cards do not tell when it was actually sold, or noted by the member, dated when noted (US-51). One per
 * article, borne by the listing sold (US-53), never changed; only a noted sale can be cancelled.
 */
public record ListingSale(VintedListingId listingId, Instant noticedAt, SaleOrigin origin) {

    public ListingSale {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(noticedAt);
        Objects.requireNonNull(origin);
    }

    public static ListingSale noticed(VintedListingId listingId, Instant noticedAt) {
        return new ListingSale(listingId, noticedAt, SaleOrigin.NOTICED);
    }

    public static ListingSale noted(VintedListingId listingId, Instant notedAt) {
        return new ListingSale(listingId, notedAt, SaleOrigin.NOTED);
    }

    /** A sale noticed on the wardrobe is a fact the tool saw; only the member's own note may be a mistake. */
    public boolean cancellable() {
        return origin == SaleOrigin.NOTED;
    }

    /**
     * Only known listings: the tool never opens a sold one, and would know neither its title nor its price. One sale
     * per article (US-53): none for a listing whose article is already sold, one for the first listing shown sold of
     * each article.
     */
    public static List<ListingSale> newlyNoticed(Collection<VintedListingId> shownSold, Collection<Listing> inventory,
                                                 Collection<ListingSale> recorded, Instant noticedAt) {
        Map<VintedListingId, ArticleId> articleOf = inventory.stream()
                .collect(Collectors.toMap(Listing::id, Listing::articleId));
        Set<ArticleId> sold = recorded.stream().map(sale -> articleOf.get(sale.listingId())).filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        return shownSold.stream().filter(articleOf::containsKey).filter(listing -> sold.add(articleOf.get(listing)))
                .map(listing -> noticed(listing, noticedAt)).toList();
    }
}
