package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.ArticleId;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The articles (US-53), written in the transaction of the caller: a new one for a listing first saved, and the merge
 * of two articles once a published republication proves they are one.
 */
@Component
class Articles {

    /** Run in this order, between the article {@code :kept} and the article {@code :absorbed}. */
    private static final List<String> MERGED_IN_ORDER = List.of(
            "INSERT INTO article_tag (article_id, tag) SELECT :kept, tag FROM article_tag WHERE article_id = :absorbed"
                    + " ON CONFLICT DO NOTHING",
            "DELETE FROM article_tag WHERE article_id = :absorbed",
            // One sale per article: the oldest is kept.
            """
            DELETE FROM article_sale sale WHERE sale.article_id IN (:kept, :absorbed) AND EXISTS (
              SELECT 1 FROM article_sale older WHERE older.article_id IN (:kept, :absorbed)
                AND (older.noticed_at, older.listing_id) < (sale.noticed_at, sale.listing_id))""",
            // Their sale, price changes and republications follow the listings (ON UPDATE CASCADE).
            "UPDATE listing SET article_id = :kept WHERE article_id = :absorbed",
            "DELETE FROM article WHERE id = :absorbed",
            // A price seen again on the other listing is no change.
            """
            DELETE FROM listing_price_change change USING (
              SELECT id, price_amount, price_currency,
                     LAG(price_amount) OVER history AS previous_amount,
                     LAG(price_currency) OVER history AS previous_currency
              FROM listing_price_change WHERE article_id = :kept
              WINDOW history AS (ORDER BY changed_at, id)) repeated
            WHERE change.id = repeated.id AND repeated.previous_amount = repeated.price_amount
              AND repeated.previous_currency = repeated.price_currency""");

    private final NamedParameterJdbcTemplate database;

    Articles(JdbcTemplate database) {
        this.database = new NamedParameterJdbcTemplate(database);
    }

    void add(ArticleId article, Instant createdAt) {
        database.update("INSERT INTO article (id, created_at) VALUES (:id, :createdAt)",
                Map.of("id", article.value(), "createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)));
    }

    /** Empty for an unknown listing. */
    Optional<UUID> of(VintedListingId listing) {
        return database.queryForList("SELECT article_id FROM listing WHERE vinted_id = :listing",
                Map.of("listing", listing.value()), UUID.class).stream().findFirst();
    }

    /**
     * Puts {@code newListing}, and every listing of its article, in the article of {@code listing}: the tags of both,
     * the oldest sale and the price history without repeated prices. Nothing if they already share their article.
     */
    void merge(VintedListingId listing, VintedListingId newListing) {
        Optional<UUID> kept = of(listing);
        Optional<UUID> absorbed = of(newListing);
        if (kept.isEmpty() || absorbed.isEmpty() || Objects.equals(kept, absorbed)) {
            return;
        }
        Map<String, UUID> articles = Map.of("kept", kept.get(), "absorbed", absorbed.get());
        MERGED_IN_ORDER.forEach(statement -> database.update(statement, articles));
    }
}
