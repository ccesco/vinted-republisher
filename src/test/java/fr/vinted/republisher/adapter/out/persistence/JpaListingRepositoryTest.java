package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.PriceChange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.seenSince;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
class JpaListingRepositoryTest extends ApplicationWithDatabaseTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant LATER = SCANNED_AT.plusSeconds(3600);

    @Autowired
    private ListingRepository listings;

    @Autowired
    private JdbcTemplate database;

    @Test
    void gives_back_a_listing_as_it_was_saved() {
        Listing listing = Listing.seen(aListing(90000000001L, "Pull rayé", "1.5", ACTIVE), SCANNED_AT);

        listings.saveAll(List.of(listing));

        assertThat(listings.findAll()).containsExactly(listing);
    }

    @Test
    void finds_a_listing_by_its_identifier() {
        Listing listing = Listing.seen(aListing(1, "Pull rayé", "1.5", ACTIVE), SCANNED_AT);
        listings.saveAll(List.of(listing));

        assertThat(listings.find(new VintedListingId(1))).contains(listing);
        assertThat(listings.find(new VintedListingId(2))).isEmpty();
    }

    @Test
    void updates_a_known_listing_without_duplicating_it() {
        listings.saveAll(List.of(Listing.seen(aListing(1, "Ancien titre", "10.00", ACTIVE), SCANNED_AT)));
        Listing updated = Listing.seen(aListing(1, "Nouveau titre", "8.00", ACTIVE), SCANNED_AT.plusSeconds(60));

        listings.saveAll(List.of(updated));

        assertThat(listings.findAll()).containsExactly(
                seenSince(aListing(1, "Nouveau titre", "8.00", ACTIVE), SCANNED_AT, SCANNED_AT.plusSeconds(60)));
    }

    @Test
    void keeps_the_first_seen_date_of_a_known_listing() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), SCANNED_AT)));

        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull rayé", "8.00", ACTIVE), SCANNED_AT.plusSeconds(60))
                .absent()));

        Listing kept = listings.find(new VintedListingId(1)).orElseThrow();
        assertThat(kept.firstSeenAt()).isEqualTo(SCANNED_AT);
        assertThat(kept.lastSeenAt()).isEqualTo(SCANNED_AT.plusSeconds(60));
        assertThat(kept.present()).isFalse();
    }

    @Test
    void keeps_an_absent_listing() {
        Listing absent = Listing.seen(anActiveListing(1), SCANNED_AT).absent();

        listings.saveAll(List.of(absent));

        assertThat(listings.findAll()).containsExactly(absent);
    }

    @Test
    void saves_all_or_nothing() {
        Listing good = Listing.seen(anActiveListing(1), SCANNED_AT);
        Listing refusedByPostgres = Listing.seen(aListing(2, "Pull\u0000rayé", "1.00", ACTIVE), SCANNED_AT);

        Throwable failure = catchThrowable(() -> listings.saveAll(List.of(good, refusedByPostgres)));

        assertThat(failure).isInstanceOf(DataAccessException.class);
        assertThat(listings.findAll()).isEmpty();
    }

    @Test
    void saving_a_new_listing_starts_its_price_history() {
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull", "12.50", ACTIVE), SCANNED_AT)));

        listings.saveAll(List.of(Listing.seen(aListing(202, "Pull", "12.50", ACTIVE), LATER)));

        assertThat(listings.priceHistoryOf(new VintedListingId(202))).containsExactly(change(202, "12.50", LATER));
        assertThat(listings.priceHistoryOf(new VintedListingId(101))).containsExactly(change(101, "12.50", SCANNED_AT));
    }

    @Test
    void adds_each_price_change_and_nothing_for_an_unchanged_price() {
        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull", "12.50", ACTIVE), SCANNED_AT)));
        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull", "10.00", ACTIVE), LATER)));
        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull", "10.00", ACTIVE), LATER.plusSeconds(60)).absent()));

        assertThat(listings.priceHistoryOf(new VintedListingId(1)))
                .containsExactly(change(1, "10.00", LATER), change(1, "12.50", SCANNED_AT));
    }

    @Test
    void listing_without_price_history_starts_it_on_its_next_save() {
        database.update("INSERT INTO listing (vinted_id, url, title, price_amount, price_currency, present,"
                + " last_seen_at, first_seen_at)"
                + " VALUES (1, 'https://vinted.test/items/1', 'Pull', 12.50, 'EUR', true, ?, ?)",
                Timestamp.from(SCANNED_AT), Timestamp.from(SCANNED_AT));
        assertThat(listings.priceHistoryOf(new VintedListingId(1))).isEmpty();

        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull", "12.50", ACTIVE), LATER)));

        assertThat(listings.priceHistoryOf(new VintedListingId(1))).containsExactly(change(1, "12.50", LATER));
    }

    @Test
    void a_failing_price_history_saves_no_listing() {
        database.execute("CREATE FUNCTION refuse_price_change() RETURNS trigger LANGUAGE plpgsql AS"
                + " $$ BEGIN RAISE EXCEPTION 'price history refused'; END $$");
        database.execute("CREATE TRIGGER refuse_price_change BEFORE INSERT ON listing_price_change"
                + " FOR EACH ROW EXECUTE FUNCTION refuse_price_change()");
        try {
            Throwable failure = catchThrowable(() ->
                    listings.saveAll(List.of(Listing.seen(anActiveListing(1), SCANNED_AT))));

            assertThat(failure).isInstanceOf(DataAccessException.class);
            assertThat(listings.findAll()).isEmpty();
        } finally {
            database.execute("DROP TRIGGER refuse_price_change ON listing_price_change");
            database.execute("DROP FUNCTION refuse_price_change()");
        }
    }

    @Test
    void same_listing_twice_in_one_save_adds_its_price_once() {
        Listing first = Listing.seen(aListing(1, "Pull", "12.50", ACTIVE), SCANNED_AT);

        listings.saveAll(List.of(first, first.absent()));

        assertThat(listings.priceHistoryOf(new VintedListingId(1))).containsExactly(change(1, "12.50", SCANNED_AT));
    }

    private static PriceChange change(long listing, String amount, Instant changedAt) {
        return new PriceChange(new VintedListingId(listing), new Price(new BigDecimal(amount), "EUR"), changedAt);
    }
}
