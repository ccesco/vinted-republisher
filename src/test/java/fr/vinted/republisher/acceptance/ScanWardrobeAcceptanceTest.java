package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.session.CheckVintedSession;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatusFlags;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.fakes.FakeVintedAccount;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class ScanWardrobeAcceptanceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final Duration MINIMUM_PAUSE = Duration.ofSeconds(3);
    private static final Duration MAXIMUM_PAUSE = Duration.ofSeconds(6);
    private static final Executor RIGHT_AWAY = Runnable::run;

    private final VintedSession session = new VintedSession();
    private final LastScanProblem lastProblem = new LastScanProblem();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final FakeWardrobeReader reader = new FakeWardrobeReader();
    private final ScanWardrobe scanWardrobe = new ScanWardrobe(session, lastProblem, reader, listings,
            new ScanPace(MINIMUM_PAUSE, MAXIMUM_PAUSE, new Random(42)), Clock.fixed(NOW, ZoneOffset.UTC), RIGHT_AWAY);
    private final ShowWardrobe showWardrobe = new ShowWardrobe(session, lastProblem, listings);

    @Test
    void scan_records_every_active_listing() {
        // Étant donné un dressing de 45 annonces actives réparties sur 3 tranches
        ScannedListing[] wardrobe = LongStream.rangeClosed(1, 45)
                .mapToObj(id -> anActiveListing(id)).toArray(ScannedListing[]::new);
        reader.holding(wardrobe);

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors les 45 annonces sont enregistrées avec leur identifiant, URL, titre et prix
        // Et la date du dernier scan est enregistrée
        assertThat(listings.findAll()).containsExactlyInAnyOrderElementsOf(
                LongStream.rangeClosed(1, 45).mapToObj(id -> Listing.seen(anActiveListing(id), NOW)).toList());
        assertThat(showWardrobe.show().lastScanAt()).contains(NOW);
    }

    @Test
    void only_active_listings_are_kept() {
        // Étant donné un dressing contenant une annonce réservée, une fermée, une masquée, un brouillon et une en cours de traitement
        reader.holding(anActiveListing(1),
                aListing(2, new ListingStatusFlags(false, false, true, false, false)),
                aListing(3, new ListingStatusFlags(false, true, false, false, false)),
                aListing(4, new ListingStatusFlags(false, false, false, true, false)),
                aListing(5, new ListingStatusFlags(true, false, false, false, false)),
                aListing(6, new ListingStatusFlags(false, false, false, false, true)));

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors seules les annonces actives sont enregistrées
        // Et le total lu tient compte de toutes les annonces
        assertThat(listings.findAll()).extracting(Listing::id).containsExactly(new VintedListingId(1));
        assertThat(showWardrobe.show().problem()).isEmpty();
    }

    @Test
    void rescan_updates_known_listings_and_keeps_missing_ones() {
        // Étant donné une annonce connue dont le prix a changé et une annonce connue qui n'est plus dans le dressing
        Listing missing = Listing.seen(anActiveListing(2), NOW.minusSeconds(3600));
        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull rayé", "10.00", ACTIVE), NOW.minusSeconds(3600)), missing));
        ScannedListing cheaper = aListing(1, "Pull rayé", "8.00", ACTIVE);
        reader.holding(cheaper);

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors la première est mise à jour, sans doublon
        // Et la seconde reste en base, marquée absente
        // Et elle n'apparaît plus dans la liste
        assertThat(listings.findAll()).containsExactly(Listing.seen(cheaper, NOW), missing.absent());
        assertThat(showWardrobe.show().listings()).containsExactly(Listing.seen(cheaper, NOW));
    }

    @Test
    void incomplete_scan_leaves_the_inventory_unchanged() {
        // Étant donné un inventaire existant et un dressing dont l'en-tête annonce plus d'annonces que Vinted n'en renvoie
        Listing known = anInventoryOfOneListing();
        reader.announcing(3, anActiveListing(2), anActiveListing(3));

        // Quand je lance le scan
        scanWardrobe.scan();

        // Alors le scan échoue avec le problème "dressing illisible"
        // Et l'inventaire existant est inchangé
        assertThat(showWardrobe.show().problem()).contains(ScanProblem.WARDROBE_UNREADABLE);
        assertThat(listings.findAll()).containsExactly(known);
    }

    @Test
    void scan_without_any_listing_fails() {
        // Étant donné un inventaire existant et aucune annonce renvoyée par Vinted
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
    void http_error_stops_the_scan() {
        // Étant donné une tranche que Vinted refuse par une erreur HTTP
        Listing known = anInventoryOfOneListing();
        reader.whereVintedRefuses();

        // Quand le scan atteint cette tranche
        scanWardrobe.scan();

        // Alors le scan s'arrête sans nouvelle tentative
        // Et le problème indique que Vinted est inaccessible
        // Et l'inventaire existant est inchangé
        assertThat(reader.reads()).isEqualTo(1);
        assertThat(showWardrobe.show().problem()).contains(ScanProblem.VINTED_UNREACHABLE);
        assertThat(listings.findAll()).containsExactly(known);
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
        // Quand l'outil défile plusieurs fois
        reader.holding(anActiveListing(1)).scrolling(100);
        scanWardrobe.scan();

        // Alors chaque pause dure entre 3 et 6 secondes
        assertThat(reader.pauses()).hasSize(100)
                .allSatisfy(pause -> assertThat(pause).isBetween(MINIMUM_PAUSE, MAXIMUM_PAUSE));
    }

    private Listing anInventoryOfOneListing() {
        Listing known = Listing.seen(anActiveListing(1), NOW.minusSeconds(3600));
        listings.saveAll(List.of(known));
        return known;
    }
}
