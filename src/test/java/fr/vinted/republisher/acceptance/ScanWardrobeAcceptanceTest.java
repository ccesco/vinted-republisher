package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.session.CheckVintedSession;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeVintedAccount;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryWardrobeScanRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anInactiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class ScanWardrobeAcceptanceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);
    private static final Duration MINIMUM_PAUSE = Duration.ofSeconds(10);
    private static final Duration MAXIMUM_PAUSE = Duration.ofSeconds(15);
    private static final Executor RIGHT_AWAY = Runnable::run;

    private final VintedSession session = new VintedSession();
    private final LastScanProblem lastProblem = new LastScanProblem();
    private final InMemoryWardrobeScanRepository scans = new InMemoryWardrobeScanRepository();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final FakeWardrobeReader reader = new FakeWardrobeReader();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final ScanPace pace = new ScanPace(new PauseRange(Duration.ofSeconds(3), Duration.ofSeconds(6)),
            new PauseRange(MINIMUM_PAUSE, MAXIMUM_PAUSE), new Random(42));
    private final ScanWardrobe scanWardrobe =
            new ScanWardrobe(session, lastProblem, scans, reader, listings, pace, clock, RIGHT_AWAY);
    private final ShowWardrobe showWardrobe = new ShowWardrobe(session, lastProblem, listings, scans);

    @Test
    void scan_records_every_active_listing() {
        // Étant donné un dressing de 45 annonces actives
        reader.holding(LongStream.rangeClosed(1, 45).mapToObj(id -> anActiveListing(id)).toArray(ScannedListing[]::new));

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors les 45 annonces sont enregistrées avec leur identifiant, URL, titre et prix
        // Et la date de fin du scan est enregistrée
        assertThat(listings.findAll()).containsExactlyInAnyOrderElementsOf(
                LongStream.rangeClosed(1, 45).mapToObj(id -> Listing.seen(anActiveListing(id), NOW)).toList());
        assertThat(showWardrobe.show().lastScanAt()).contains(NOW);
    }

    @Test
    void only_active_listings_are_kept() {
        // Étant donné un dressing contenant une annonce dont la disponibilité n'est pas "InStock"
        reader.holding(anActiveListing(1), anInactiveListing(2));

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors seules les annonces actives sont enregistrées
        // Et le scan se termine normalement
        assertThat(listings.findAll()).extracting(Listing::id).containsExactly(new VintedListingId(1));
        assertThat(showWardrobe.show().problem()).isEmpty();
        assertThat(showWardrobe.show().lastScanAt()).contains(NOW);
    }

    @Test
    void rescan_updates_known_listings_and_keeps_missing_ones() {
        // Étant donné une annonce connue dont le prix a changé et une annonce connue qui n'est plus dans le dressing
        Listing missing = Listing.seen(anActiveListing(2), EARLIER);
        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull rayé", "10.00", ACTIVE), EARLIER), missing));
        ScannedListing cheaper = aListing(1, "Pull rayé", "8.00", ACTIVE);
        reader.holding(cheaper);

        // Quand je lance le scan jusqu'à sa fin
        scanWardrobe.scan();

        // Alors la première est mise à jour, sans doublon
        // Et la seconde reste en base, marquée absente
        // Et elle n'apparaît plus dans la liste
        assertThat(listings.findAll()).containsExactly(Listing.seen(cheaper, NOW), missing.absent());
        assertThat(showWardrobe.show().listings()).containsExactly(Listing.seen(cheaper, NOW));
    }

    @Test
    void incomplete_listing_of_the_wardrobe_changes_nothing() {
        // Étant donné un inventaire existant et un dressing dont l'en-tête annonce plus d'annonces que la page n'en montre
        Listing known = anInventoryOfOneListing();
        reader.announcing(3, anActiveListing(2), anActiveListing(3));

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors le scan échoue avec le problème "dressing illisible"
        // Et aucune annonce n'est ouverte
        // Et l'inventaire existant est inchangé
        assertThat(showWardrobe.show().problem()).contains(ScanProblem.WARDROBE_UNREADABLE);
        assertThat(reader.opened()).isEmpty();
        assertThat(listings.findAll()).containsExactly(known);
    }

    @Test
    void scan_without_any_listing_fails() {
        // Étant donné un inventaire existant et aucune annonce sur la page du dressing
        Listing known = anInventoryOfOneListing();
        reader.announcing(0);

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors le scan échoue avec le problème "dressing illisible"
        // Et l'inventaire existant est inchangé
        assertThat(showWardrobe.show().problem()).contains(ScanProblem.WARDROBE_UNREADABLE);
        assertThat(listings.findAll()).containsExactly(known);
    }

    @Test
    void http_error_stops_the_scan_and_keeps_what_was_read() {
        // Étant donné une annonce que Vinted refuse par une erreur HTTP
        Listing known = anInventoryOfOneListing();
        reader.holding(anActiveListing(2), anActiveListing(3), anActiveListing(4)).whereVintedRefuses(3);

        // Quand le scan atteint cette annonce
        scanWardrobe.scan();

        // Alors le scan s'arrête sans nouvelle tentative
        // Et le problème indique que Vinted est inaccessible
        // Et les annonces déjà lues restent enregistrées
        // Et aucune annonce n'est marquée absente
        assertThat(reader.opened()).containsExactly(new VintedListingId(2), new VintedListingId(3));
        assertThat(showWardrobe.show().problem()).contains(ScanProblem.VINTED_UNREACHABLE);
        assertThat(listings.findAll()).containsExactly(known, Listing.seen(anActiveListing(2), NOW));
    }

    @Test
    void interrupted_scan_resumes_with_what_is_left() {
        // Étant donné un scan interrompu après avoir lu 2 annonces sur 3
        Listing gone = anInventoryOfOneListing();
        ScannedListing[] wardrobe = {anActiveListing(2), anActiveListing(3), anActiveListing(4)};
        reader.holding(wardrobe).whereVintedRefuses(4);
        scanWardrobe.scan();
        clock.advance(Duration.ofHours(1));
        reader.holding(wardrobe);

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors seule la troisième annonce est ouverte
        // Et le scan se termine
        // Et les annonces disparues sont alors marquées absentes
        assertThat(reader.opened()).containsExactly(new VintedListingId(4));
        assertThat(showWardrobe.show().lastScanAt()).contains(NOW.plus(Duration.ofHours(1)));
        assertThat(listings.findAll()).contains(gone.absent());
    }

    @Test
    void listing_gone_during_an_interruption_ends_up_absent() {
        // Étant donné un scan interrompu après avoir lu 2 annonces sur 3
        reader.holding(anActiveListing(1), anActiveListing(2), anActiveListing(3)).whereVintedRefuses(3);
        scanWardrobe.scan();
        clock.advance(Duration.ofHours(1));
        // Et la deuxième annonce a quitté le dressing depuis
        reader.holding(anActiveListing(1), anActiveListing(3));

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors seule la troisième annonce est ouverte
        // Et la deuxième annonce est marquée absente
        assertThat(reader.opened()).containsExactly(new VintedListingId(3));
        assertThat(listings.findAll()).contains(Listing.seen(anActiveListing(2), NOW).absent());
    }

    @Test
    void unavailable_browser_fails_the_scan() {
        // Étant donné un navigateur injoignable
        Listing known = anInventoryOfOneListing();
        reader.whereBrowserCannotStart();

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors le problème indique que le navigateur est indisponible
        // Et l'inventaire existant est inchangé
        assertThat(showWardrobe.show().problem()).contains(ScanProblem.BROWSER_UNAVAILABLE);
        assertThat(listings.findAll()).containsExactly(known);
    }

    @Test
    void request_during_a_browser_action_is_ignored() {
        // Étant donné une action navigateur en cours
        session.start(BrowserAction.CHECKING);

        // Quand je demande un scan, ou une vérification pendant un scan
        scanWardrobe.scan();

        // Alors la demande est ignorée
        assertThat(reader.reads()).isZero();
        assertThat(session.overview().currentAction()).contains(BrowserAction.CHECKING);

        session.finish();
        FakeVintedAccount account = FakeVintedAccount.withMemberConnected();
        reader.holding(anActiveListing(1))
                .during(() -> new CheckVintedSession(session, account, RIGHT_AWAY).check());

        scanWardrobe.scan();

        assertThat(account.openedBrowsers()).isZero();
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void pauses_stay_within_the_configured_bounds() {
        // Étant donné un scan en cours
        reader.holding(LongStream.rangeClosed(1, 100).mapToObj(id -> anActiveListing(id)).toArray(ScannedListing[]::new));

        // Quand l'outil passe d'une annonce à la suivante
        scanWardrobe.scan();

        // Alors chaque pause dure entre 10 et 15 secondes
        assertThat(reader.pausesBetweenListings()).hasSize(99)
                .allSatisfy(pause -> assertThat(pause).isBetween(MINIMUM_PAUSE, MAXIMUM_PAUSE));
    }

    private Listing anInventoryOfOneListing() {
        Listing known = Listing.seen(anActiveListing(1), EARLIER);
        listings.saveAll(List.of(known));
        return known;
    }
}
