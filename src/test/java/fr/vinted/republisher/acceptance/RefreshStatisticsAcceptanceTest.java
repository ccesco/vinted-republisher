package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.wardrobe.RefreshStatistics;
import fr.vinted.republisher.application.wardrobe.ShowStatisticsRefresh;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastStatisticsRefreshProblem;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.StatisticsRefreshProblem;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryWardrobeScanRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class RefreshStatisticsAcceptanceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);
    private static final Executor RIGHT_AWAY = Runnable::run;

    private final VintedSession session = new VintedSession();
    private final LastStatisticsRefreshProblem lastProblem = new LastStatisticsRefreshProblem();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
    private final InMemoryWardrobeScanRepository scans = new InMemoryWardrobeScanRepository();
    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final FakeWardrobeReader reader = new FakeWardrobeReader();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final RefreshStatistics refreshStatistics = new RefreshStatistics(session, lastProblem, reader, statistics,
            noPause(), clock, RIGHT_AWAY, new JournalWriter(journal, clock));
    private final ShowStatisticsRefresh showStatisticsRefresh =
            new ShowStatisticsRefresh(session, lastProblem, statistics);

    @Test
    void records_a_reading_per_card_without_opening_any_listing() {
        // Étant donné un dressing de trois annonces connues dont les cartes montrent vues et favoris
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), EARLIER), Listing.seen(anActiveListing(2), EARLIER),
                Listing.seen(anActiveListing(3), EARLIER)));
        reader.holding(anActiveListing(1), anActiveListing(2), anActiveListing(3))
                .showing(1, new ListingStatistics(10, 1)).showing(2, new ListingStatistics(20, 2))
                .showing(3, new ListingStatistics(30, 3));

        // Quand je rafraîchis les statistiques
        refreshStatistics.refresh();

        // Alors chaque annonce gagne un relevé à la date du parcours, aucune annonce n'est ouverte
        assertThat(statistics.readings()).containsExactlyInAnyOrder(
                new StatisticsReading(new VintedListingId(1), new ListingStatistics(10, 1), NOW),
                new StatisticsReading(new VintedListingId(2), new ListingStatistics(20, 2), NOW),
                new StatisticsReading(new VintedListingId(3), new ListingStatistics(30, 3), NOW));
        assertThat(reader.opened()).isEmpty();
        // Et la date du dernier relevé est celle du parcours
        assertThat(showStatisticsRefresh.show().lastReadAt()).contains(NOW);
        assertThat(showStatisticsRefresh.show().inProgress()).isFalse();
        assertThat(showStatisticsRefresh.show().problem()).isEmpty();
    }

    @Test
    void changes_neither_listings_nor_scans() {
        // Étant donné une annonce connue absente du dressing, une annonce inconnue présente, et un scan interrompu
        Listing absent = Listing.seen(anActiveListing(1), EARLIER);
        listings.saveAll(List.of(absent));
        WardrobeScan interrupted = WardrobeScan.startedAt(EARLIER);
        scans.save(interrupted);
        reader.holding(anActiveListing(2)).showing(2, new ListingStatistics(5, 0));

        // Quand je rafraîchis les statistiques
        refreshStatistics.refresh();

        // Alors l'annonce absente reste, l'inconnue n'est pas ajoutée mais ses nombres sont relevés
        assertThat(listings.findAll()).containsExactly(absent);
        assertThat(statistics.readings()).extracting(StatisticsReading::listingId)
                .containsExactly(new VintedListingId(2));
        // Et le scan interrompu reste à reprendre
        assertThat(scans.findUnfinished()).contains(interrupted);
    }

    @Test
    void incomplete_wardrobe_records_nothing() {
        // Étant donné un dressing qui annonce quatre annonces et n'en montre que trois
        reader.announcing(4, anActiveListing(1), anActiveListing(2), anActiveListing(3))
                .showing(1, new ListingStatistics(10, 1));

        // Quand je rafraîchis les statistiques
        refreshStatistics.refresh();

        // Alors aucun relevé n'est ajouté et le problème est WARDROBE_UNREADABLE
        assertThat(statistics.readings()).isEmpty();
        assertThat(showStatisticsRefresh.show().problem()).contains(StatisticsRefreshProblem.WARDROBE_UNREADABLE);
    }

    @Test
    void no_statistics_shown_is_a_problem() {
        // Étant donné un dressing dont aucune carte ne montre vues et favoris
        reader.holding(anActiveListing(1), anActiveListing(2));

        // Quand je rafraîchis les statistiques
        refreshStatistics.refresh();

        // Alors aucun relevé n'est ajouté et le problème est NO_STATISTICS_SHOWN
        assertThat(statistics.readings()).isEmpty();
        assertThat(showStatisticsRefresh.show().problem()).contains(StatisticsRefreshProblem.NO_STATISTICS_SHOWN);
    }

    @Test
    void is_ignored_while_the_browser_is_busy() {
        // Étant donné un scan en cours
        session.start(BrowserAction.SCANNING);
        reader.holding(anActiveListing(1)).showing(1, new ListingStatistics(10, 1));

        // Quand je rafraîchis les statistiques
        refreshStatistics.refresh();

        // Alors rien n'est lu ni relevé, et le scan reste l'action en cours
        assertThat(reader.reads()).isZero();
        assertThat(statistics.readings()).isEmpty();
        assertThat(session.overview().currentAction()).contains(BrowserAction.SCANNING);
        assertThat(journal.records()).isEmpty();
    }

    @Test
    void shows_the_refresh_in_progress() {
        reader.holding(anActiveListing(1)).showing(1, new ListingStatistics(10, 1))
                .during(() -> {
                    assertThat(session.overview().currentAction()).contains(BrowserAction.REFRESHING_STATISTICS);
                    assertThat(showStatisticsRefresh.show().inProgress()).isTrue();
                });

        refreshStatistics.refresh();

        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void failures_are_reported_and_free_the_browser() {
        // Étant donné un navigateur qui ne démarre pas
        reader.whereBrowserCannotStart();
        refreshStatistics.refresh();
        assertThat(showStatisticsRefresh.show().problem()).contains(StatisticsRefreshProblem.BROWSER_UNAVAILABLE);
        assertThat(session.overview().currentAction()).isEmpty();

        // Puis Vinted qui refuse le dressing
        reader.whereVintedRefusesTheWardrobe();
        refreshStatistics.refresh();
        assertThat(showStatisticsRefresh.show().problem()).contains(StatisticsRefreshProblem.VINTED_UNREACHABLE);

        // Puis une erreur imprévue : rapportée, puis relancée pour être journalisée par l'exécuteur
        reader.whereBrowserFailsUnexpectedly();
        assertThatThrownBy(refreshStatistics::refresh).isInstanceOf(IllegalStateException.class);
        assertThat(showStatisticsRefresh.show().problem()).contains(StatisticsRefreshProblem.REFRESH_FAILED);
        assertThat(session.overview().currentAction()).isEmpty();

        // Un rafraîchissement réussi efface le problème
        reader.holding(anActiveListing(1)).showing(1, new ListingStatistics(10, 1));
        refreshStatistics.refresh();
        assertThat(showStatisticsRefresh.show().problem()).isEmpty();
    }

    @Test
    void is_journaled() {
        // Quand je rafraîchis les statistiques avec succès, puis sur un dressing incomplet
        reader.holding(anActiveListing(1)).showing(1, new ListingStatistics(10, 1));
        refreshStatistics.refresh();
        reader.announcing(2, anActiveListing(1)).showing(1, new ListingStatistics(10, 1));
        refreshStatistics.refresh();

        // Alors le journal montre lancé puis réussi, puis lancé puis en échec (WARDROBE_UNREADABLE)
        assertThat(journal.records()).extracting(ActionRecord::action, ActionRecord::outcome, ActionRecord::reason)
                .containsExactly(
                        tuple(JournaledAction.STATISTICS_REFRESH, ActionOutcome.STARTED, Optional.empty()),
                        tuple(JournaledAction.STATISTICS_REFRESH, ActionOutcome.SUCCEEDED, Optional.empty()),
                        tuple(JournaledAction.STATISTICS_REFRESH, ActionOutcome.STARTED, Optional.empty()),
                        tuple(JournaledAction.STATISTICS_REFRESH, ActionOutcome.FAILED,
                                Optional.of("WARDROBE_UNREADABLE")));
    }

    @Test
    void last_reading_comes_from_a_scan_or_a_refresh() {
        assertThat(showStatisticsRefresh.show().lastReadAt()).isEmpty();
        statistics.addAll(List.of(
                new StatisticsReading(new VintedListingId(1), new ListingStatistics(1, 0), EARLIER),
                new StatisticsReading(new VintedListingId(2), new ListingStatistics(1, 0), NOW)));

        assertThat(showStatisticsRefresh.show().lastReadAt()).contains(NOW);
    }
}
