package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.sale.ListingAlreadySold;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.sale.SaleOrigin;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One sale per article (US-53), borne by the listing sold; inserted, never merged over one already recorded. */
@Repository
class JpaListingSaleRepository implements ListingSaleRepository {

    private static final RowMapper<ListingSale> SALE = (row, _) -> new ListingSale(
            new VintedListingId(row.getLong("listing_id")),
            row.getObject("noticed_at", OffsetDateTime.class).toInstant(), SaleOrigin.valueOf(row.getString("origin")));

    /** An unknown listing has no article: the database refuses the row. */
    private static final String INSERT = "INSERT INTO article_sale (article_id, listing_id, noticed_at, origin) "
            + "VALUES ((SELECT article_id FROM listing WHERE vinted_id = :listing), :listing, :noticedAt, :origin)";

    private final NamedParameterJdbcTemplate database;

    JpaListingSaleRepository(JdbcTemplate database) {
        this.database = new NamedParameterJdbcTemplate(database);
    }

    @Override
    public List<ListingSale> findAll() {
        return database.query("SELECT listing_id, noticed_at, origin FROM article_sale ORDER BY noticed_at, listing_id",
                SALE);
    }

    /** The sale borne by this very listing: none for another listing of a sold article. */
    @Override
    public Optional<ListingSale> find(VintedListingId listing) {
        return database.query("SELECT listing_id, noticed_at, origin FROM article_sale WHERE listing_id = :listing",
                Map.of("listing", listing.value()), SALE).stream().findFirst();
    }

    @Override
    @Transactional
    public void addAll(List<ListingSale> sales) {
        sales.forEach(sale -> database.update(INSERT, parameters(sale)));
    }

    /** One statement, so two concurrent notes cannot both pass. */
    @Override
    @Transactional
    public void add(ListingSale sale) {
        if (database.update(INSERT + " ON CONFLICT DO NOTHING", parameters(sale)) == 0) {
            throw new ListingAlreadySold(sale.listingId());
        }
    }

    @Override
    @Transactional
    public void removeNoted(VintedListingId listing) {
        database.update("DELETE FROM article_sale WHERE listing_id = :listing AND origin = :origin",
                Map.of("listing", listing.value(), "origin", SaleOrigin.NOTED.name()));
    }

    private static Map<String, Object> parameters(ListingSale sale) {
        return Map.of("listing", sale.listingId().value(),
                "noticedAt", OffsetDateTime.ofInstant(sale.noticedAt(), ZoneOffset.UTC),
                "origin", sale.origin().name());
    }
}
