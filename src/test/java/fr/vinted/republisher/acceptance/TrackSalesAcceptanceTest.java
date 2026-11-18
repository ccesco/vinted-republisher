package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.sale.NoticeSales;
import fr.vinted.republisher.application.wardrobe.RefreshStatistics;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.LastStatisticsRefreshProblem;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryWardrobeScanRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;

/** US-36: sales noticed on the "Vendu" cards of the wardrobe, by a scan or a statistics refresh. */
class TrackSalesAcceptanceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);
    private static final Executor RIGHT_AWAY = Runnable::run;

    private final VintedSession session = new VintedSession();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
    private final InMemoryListingSaleRepository sales = new InMemoryListingSaleRepository();
    private final FakeWardrobeReader reader = new FakeWardrobeReader();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final JournalWriter journal = new JournalWriter(new InMemoryActionJournal(), clock);
    private final NoticeSales noticeSales = new NoticeSales(listings, sales, clock);
    private final ScanWardrobe scanWardrobe = new ScanWardrobe(session, new LastScanProblem(),
            new InMemoryWardrobeScanRepository(), reader, listings, statistics, noticeSales, noPause(), clock,
            RIGHT_AWAY, journal);
    private final RefreshStatistics refreshStatistics = new RefreshStatistics(session,
            new LastStatisticsRefreshProblem(), reader, statistics, noticeSales, noPause(), clock, RIGHT_AWAY,
            journal);

    @Test
    void a_scan_notices_the_sold_listings() {
        // AT-36-01. Étant donné les annonces 1 et 2 connues, l'annonce 2 sous un bandeau « Vendu »
        knowListings(1, 2);
        reader.holding(anActiveListing(1)).sold(2);

        // Quand je scanne le dressing
        scanWardrobe.scan();

        // Alors l'annonce 2 est vendue, constatée maintenant, sans être ouverte, et marquée absente comme avant
        assertThat(sales.findAll()).containsExactly(new ListingSale(new VintedListingId(2), NOW));
        assertThat(reader.opened()).doesNotContain(new VintedListingId(2));
        assertThat(listings.find(new VintedListingId(2))).hasValueSatisfying(
                listing -> assertThat(listing.present()).isFalse());
    }

    @Test
    void a_statistics_refresh_notices_the_sold_listings() {
        // AT-36-02
        knowListings(1, 2);
        reader.holding(anActiveListing(1)).showing(1, new ListingStatistics(10, 1)).sold(2);

        refreshStatistics.refresh();

        assertThat(sales.findAll()).containsExactly(new ListingSale(new VintedListingId(2), NOW));
    }

    @Test
    void a_sale_is_noticed_once() {
        // AT-36-03. Étant donné l'annonce 2 enregistrée vendue
        knowListings(1, 2);
        reader.holding(anActiveListing(1)).showing(1, new ListingStatistics(10, 1)).sold(2);
        refreshStatistics.refresh();

        // Quand un relevé suivant la montre encore vendue
        clock.advance(Duration.ofDays(1));
        reader.holding(anActiveListing(1)).showing(1, new ListingStatistics(10, 1)).sold(2);
        refreshStatistics.refresh();

        // Alors elle reste une seule vente, à sa première date
        assertThat(sales.findAll()).containsExactly(new ListingSale(new VintedListingId(2), NOW));
    }

    @Test
    void unknown_listings_and_incomplete_wardrobes_record_no_sale() {
        // AT-36-04. Une carte « Vendu » d'une annonce jamais vue
        knowListings(1);
        reader.holding(anActiveListing(1)).sold(9);
        scanWardrobe.scan();
        assertThat(sales.findAll()).isEmpty();

        // Un dressing incomplet qui montre une annonce connue vendue
        reader.announcing(2, anActiveListing(1)).showing(1, new ListingStatistics(10, 1)).sold(1);
        refreshStatistics.refresh();
        assertThat(sales.findAll()).isEmpty();
    }

    private void knowListings(long... ids) {
        for (long id : ids) {
            listings.saveAll(List.of(Listing.seen(anActiveListing(id), EARLIER)));
        }
    }
}
