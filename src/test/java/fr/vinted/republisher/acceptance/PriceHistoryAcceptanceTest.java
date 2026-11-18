package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.sale.NoticeSales;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.PriceChange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryWardrobeScanRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;

/** US-33: the member follows the prices of a listing on its detail. */
class PriceHistoryAcceptanceTest {

    private static final Instant FIRST = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant SECOND = FIRST.plus(Duration.ofDays(1));
    private static final Instant THIRD = SECOND.plus(Duration.ofDays(1));
    private static final Executor RIGHT_AWAY = Runnable::run;
    private static final VintedListingId PULL = new VintedListingId(101);

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final FakeWardrobeReader reader = new FakeWardrobeReader();
    private final AdjustableClock clock = new AdjustableClock(FIRST);
    private final ScanWardrobe scanWardrobe = new ScanWardrobe(new VintedSession(), new LastScanProblem(),
            new InMemoryWardrobeScanRepository(), reader, listings, statistics,
            new NoticeSales(listings, new InMemoryListingSaleRepository(), clock), noPause(), clock, RIGHT_AWAY,
            new JournalWriter(journal, clock));
    private final ShowListingDetail showListingDetail = new ShowListingDetail(listings, statistics,
            new InMemoryListingBackupRepository(), new InMemoryBackupValidationRepository(),
            new InMemoryListingDeletionRepository(), new InMemoryListingRepublicationRepository(),
            new RepublicationDelay(Duration.ofMinutes(5)), clock, journal, new InMemoryBackupVerificationRepository());

    @Test
    void first_scan_starts_the_price_history() {
        // Étant donné l'annonce 101 « Pull rayé » à 12,50 €, inconnue de l'outil
        // Quand le scan du 1er novembre la lit
        scanAt(FIRST, "12.50");

        // Alors l'historique de prix de sa fiche est : 12,50 € depuis le 1er novembre
        assertThat(showListingDetail.show(PULL).priceHistory()).containsExactly(change("12.50", FIRST));
    }

    @Test
    void each_price_change_is_added_most_recent_first() {
        // Étant donné l'annonce 101 lue à 12,50 € le 1er novembre
        scanAt(FIRST, "12.50");

        // Quand le scan du 2 novembre la lit à 10,00 €, puis celui du 3 novembre à 12,50 €
        scanAt(SECOND, "10.00");
        scanAt(THIRD, "12.50");

        // Alors l'historique de prix de sa fiche est : 12,50 € depuis le 3, 10,00 € depuis le 2, 12,50 € depuis le 1er
        assertThat(showListingDetail.show(PULL).priceHistory())
                .containsExactly(change("12.50", THIRD), change("10.00", SECOND), change("12.50", FIRST));
    }

    @Test
    void unchanged_price_adds_nothing() {
        // Étant donné l'annonce 101 lue à 12,50 € le 1er novembre
        scanAt(FIRST, "12.50");

        // Quand le scan du 2 novembre la lit encore à 12,50 €, puis celui du 3 ne la trouve plus dans le dressing
        scanAt(SECOND, "12.50");
        clock.set(THIRD);
        reader.holding(anActiveListing(102));
        scanWardrobe.scan();

        // Alors l'historique de prix de sa fiche reste : 12,50 € depuis le 1er novembre
        assertThat(listings.find(PULL).orElseThrow().present()).isFalse();
        assertThat(showListingDetail.show(PULL).priceHistory()).containsExactly(change("12.50", FIRST));
    }

    private void scanAt(Instant scannedAt, String price) {
        clock.set(scannedAt);
        reader.holding(aListing(101, "Pull rayé", price, ACTIVE));
        scanWardrobe.scan();
    }

    private static PriceChange change(String amount, Instant changedAt) {
        return new PriceChange(PULL, new Price(new BigDecimal(amount), "EUR"), changedAt);
    }
}

