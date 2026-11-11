package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.backup.BackUpWardrobe;
import fr.vinted.republisher.application.backup.ShowBackup;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.BackupProblem;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingPhoto;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import fr.vinted.republisher.fakes.InMemoryWardrobeBackupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aReading;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(OutputCaptureExtension.class)
class BackUpWardrobeAcceptanceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);
    private static final Executor RIGHT_AWAY = Runnable::run;

    private final VintedSession session = new VintedSession();
    private final LastBackupProblem lastProblem = new LastBackupProblem();
    private final InMemoryWardrobeBackupRepository backups = new InMemoryWardrobeBackupRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final FakeListingContentReader reader = new FakeListingContentReader();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final BackupPace pace = new BackupPace(
            new PauseRange(Duration.ofSeconds(10), Duration.ofSeconds(15)), new Random(42));
    private final BackUpWardrobe backUpWardrobe =
            new BackUpWardrobe(session, lastProblem, backups, reader, listings, listingBackups, photos, pace,
                    clock, RIGHT_AWAY);
    private final ShowBackup showBackup = new ShowBackup(session, lastProblem, backups, listingBackups,
            new InMemoryBackupValidationRepository());

    @Test
    void backup_saves_the_content_of_every_present_listing() {
        // Étant donné un inventaire de 3 annonces présentes
        anInventoryOf(1, 2, 3);
        reader.showing(aContent(1), aContent(2), aContent(3));
        // Quand je lance la sauvegarde
        backUpWardrobe.backUp();
        // Alors le contenu des 3 annonces est sauvegardé avec ses identifiants et ses libellés
        // Et la date de fin de la sauvegarde est enregistrée
        assertThat(listingBackups.findAll()).containsExactly(
                aBackup(1, NOW), aBackup(2, NOW),
                aBackup(3, NOW));
        assertThat(showBackup.show().lastBackupAt()).contains(NOW);
    }

    @Test
    void complete_reading_replaces_the_previous_content() {
        // Étant donné une annonce déjà sauvegardée dont la description a changé sur Vinted
        anInventoryOf(1);
        listingBackups.save(ListingBackup.of(
                new ListingReading(aContent(1, "Pull rayé", "Porté deux fois"), List.of(aPhoto("front"))), EARLIER));
        ListingReading changed = new ListingReading(aContent(1, "Pull rayé", "Porté trois fois"), List.of(aPhoto("front")));
        reader.showing(changed);
        // Quand je lance la sauvegarde
        backUpWardrobe.backUp();
        // Alors sa sauvegarde contient la nouvelle description et une nouvelle date
        // Et elle n'a qu'une sauvegarde
        assertThat(listingBackups.findAll()).containsExactly(ListingBackup.of(changed, NOW));
    }

    @Test
    void backup_keeps_every_photo_of_the_listing_in_order() {
        // Étant donné une annonce présente dont la page publique montre 3 photos
        anInventoryOf(1);
        reader.showing(new ListingReading(aContent(1), List.of(aPhoto("front"), aPhoto("back"), aPhoto("label"))));
        // Quand je lance la sauvegarde
        backUpWardrobe.backUp();
        // Alors l'annonce est sauvegardée avec 3 photos, de rang 0 à 2 dans l'ordre de la page
        // Et chaque fichier est dans photos/<annonce>/<empreinte>.<extension>, identique à l'octet près à ce qui a été servi
        assertThat(listingBackups.find(1).orElseThrow().photos()).containsExactly(ListingPhoto.at(0, aPhoto("front")),
                ListingPhoto.at(1, aPhoto("back")), ListingPhoto.at(2, aPhoto("label")));
        assertThat(photos.files(1)).containsOnlyKeys(
                aPhoto("front").fileName(), aPhoto("back").fileName(), aPhoto("label").fileName());
        assertThat(photos.files(1).get(aPhoto("back").fileName())).isEqualTo(aPhoto("back").content());
    }

    @Test
    void new_photos_replace_the_previous_ones() {
        // Étant donné une annonce sauvegardée avec les photos A et B
        anInventoryOf(1);
        reader.showing(new ListingReading(aContent(1), List.of(aPhoto("A"), aPhoto("B"))));
        backUpWardrobe.backUp();
        clock.advance(Duration.ofHours(1));
        // Et sa page publique montre maintenant les photos B et C
        reader.afresh().showing(new ListingReading(aContent(1), List.of(aPhoto("B"), aPhoto("C"))));
        // Quand je lance la sauvegarde
        backUpWardrobe.backUp();
        // Alors sa sauvegarde a les photos B puis C
        // Et le fichier de B n'est pas réécrit
        // Et le fichier de A est supprimé
        assertThat(listingBackups.find(1).orElseThrow().photos())
                .containsExactly(ListingPhoto.at(0, aPhoto("B")), ListingPhoto.at(1, aPhoto("C")));
        assertThat(photos.written()).containsExactly(
                aPhoto("A").fileName(), aPhoto("B").fileName(), aPhoto("C").fileName());
        assertThat(photos.files(1)).containsOnlyKeys(aPhoto("B").fileName(), aPhoto("C").fileName());
    }

    @Test
    void photo_not_downloaded_stops_and_keeps_the_previous_backup() {
        // Étant donné une annonce sauvegardée avec ses photos
        anInventoryOf(1, 2);
        reader.showing(aReading(1), aReading(2));
        backUpWardrobe.backUp();
        ListingBackup previous = listingBackups.find(1).orElseThrow();
        Map<String, byte[]> previousFiles = photos.files(1);
        clock.advance(Duration.ofHours(1));
        // Et l'une de ses photos ne se télécharge pas
        reader.afresh().whereFormIsUnreadable(1, "photo 2 of 1 answered HTTP 404");
        // Quand la sauvegarde atteint cette annonce
        backUpWardrobe.backUp();
        // Alors la sauvegarde s'arrête et reste non terminée
        // Et le problème indique une annonce illisible
        // Et l'ancienne sauvegarde de l'annonce, contenu et fichiers, est intacte
        assertThat(backups.findUnfinished()).isPresent();
        assertThat(showBackup.show().problem()).contains(BackupProblem.LISTING_UNREADABLE);
        assertThat(listingBackups.find(1)).contains(previous);
        assertThat(photos.files(1)).containsOnlyKeys(previousFiles.keySet());
    }

    @Test
    void failed_write_stops_the_backup_and_keeps_the_previous_one() {
        // Étant donné un dossier des photos où l'écriture est impossible
        anInventoryOf(1);
        ListingBackup previous = aBackup(1, EARLIER);
        listingBackups.save(previous);
        reader.showing(new ListingReading(aContent(1), List.of(aPhoto("new"))));
        photos.whereWritingFails();
        // Quand la sauvegarde atteint une annonce
        catchThrowable(backUpWardrobe::backUp);
        // Alors la sauvegarde s'arrête avec le problème « échec de la sauvegarde »
        // Et l'ancienne sauvegarde de l'annonce est intacte
        assertThat(showBackup.show().problem()).contains(BackupProblem.BACKUP_FAILED);
        assertThat(listingBackups.findAll()).containsExactly(previous);
    }

    @Test
    void incomplete_reading_stops_and_keeps_the_previous_content() {
        // Étant donné une annonce déjà sauvegardée dont le formulaire n'a pas d'état
        anInventoryOf(1, 2);
        ListingBackup previous = aBackup(1, EARLIER);
        listingBackups.save(previous);
        reader.showing(aContent(2)).whereFormIsUnreadable(1, "no condition on 1");
        // Quand la sauvegarde atteint cette annonce
        backUpWardrobe.backUp();
        // Alors la sauvegarde s'arrête et reste non terminée
        // Et le problème indique une annonce illisible
        // Et l'ancienne sauvegarde de l'annonce est intacte
        assertThat(reader.opened()).containsExactly(new VintedListingId(1));
        assertThat(backups.findUnfinished()).isPresent();
        assertThat(showBackup.show().problem()).contains(BackupProblem.LISTING_UNREADABLE);
        assertThat(listingBackups.findAll()).containsExactly(previous);
    }

    @Test
    void unknown_attribute_stops_the_backup(CapturedOutput logs) {
        // Étant donné un formulaire dont un attribut a un code inconnu
        anInventoryOf(1);
        reader.whereFormIsUnreadable(1, "unknown attribute code video_game_rating on 1");
        // Quand la sauvegarde atteint cette annonce
        backUpWardrobe.backUp();
        // Alors la sauvegarde s'arrête avec le problème « annonce illisible »
        // Et le code inconnu est écrit dans les logs
        assertThat(showBackup.show().problem()).contains(BackupProblem.LISTING_UNREADABLE);
        assertThat(logs).contains("unknown attribute code video_game_rating on 1");
    }

    @Test
    void login_redirect_stops_the_backup_and_disconnects_the_session() {
        // Étant donné un formulaire de modification qui redirige vers la connexion
        anInventoryOf(1, 2);
        reader.showing(aContent(1)).whereFormRedirectsToLogin(2);
        // Quand la sauvegarde atteint cette annonce
        backUpWardrobe.backUp();
        // Alors la sauvegarde s'arrête et reste non terminée
        // Et le problème indique une session non connectée
        // Et l'état de session est « non connecté »
        assertThat(backups.findUnfinished()).isPresent();
        assertThat(showBackup.show().problem()).contains(BackupProblem.SESSION_DISCONNECTED);
        assertThat(session.overview().status()).contains(SessionStatus.DISCONNECTED);
        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(listingBackups.findAll()).containsExactly(aBackup(1, NOW));
    }

    @Test
    void http_error_stops_the_backup_and_keeps_what_was_saved() {
        // Étant donné un formulaire que Vinted refuse par une erreur HTTP
        anInventoryOf(1, 2, 3);
        reader.showing(aContent(1), aContent(3)).whereVintedRefuses(2);
        // Quand la sauvegarde atteint cette annonce
        backUpWardrobe.backUp();
        // Alors la sauvegarde s'arrête sans nouvelle tentative
        // Et le problème indique que Vinted est inaccessible
        // Et les annonces déjà sauvegardées le restent
        assertThat(reader.opened()).containsExactly(new VintedListingId(1), new VintedListingId(2));
        assertThat(showBackup.show().problem()).contains(BackupProblem.VINTED_UNREACHABLE);
        assertThat(listingBackups.findAll()).containsExactly(aBackup(1, NOW));
    }

    @Test
    void interrupted_backup_resumes_where_it_stopped() {
        // Étant donné une sauvegarde interrompue après 2 annonces sur 3
        anInventoryOf(1, 2, 3);
        reader.showing(aContent(1), aContent(2), aContent(3)).whereVintedRefuses(3);
        backUpWardrobe.backUp();
        clock.advance(Duration.ofHours(1));
        reader.afresh();
        // Quand je lance la sauvegarde
        backUpWardrobe.backUp();
        // Alors seule la troisième annonce est ouverte
        // Et la sauvegarde se termine
        assertThat(reader.opened()).containsExactly(new VintedListingId(3));
        assertThat(showBackup.show().lastBackupAt()).contains(NOW.plus(Duration.ofHours(1)));
        assertThat(showBackup.show().problem()).isEmpty();
    }

    @Test
    void unavailable_browser_is_reported() {
        // Étant donné un navigateur injoignable
        anInventoryOf(1);
        ListingBackup previous = aBackup(1, EARLIER);
        listingBackups.save(previous);
        reader.whereBrowserCannotStart();
        // Quand je lance la sauvegarde
        backUpWardrobe.backUp();
        // Alors le problème indique que le navigateur est indisponible
        // Et aucune sauvegarde n'est modifiée
        assertThat(showBackup.show().problem()).contains(BackupProblem.BROWSER_UNAVAILABLE);
        assertThat(listingBackups.findAll()).containsExactly(previous);
    }

    @Test
    void request_during_a_browser_action_is_ignored() {
        // Étant donné une action navigateur en cours
        anInventoryOf(1);
        session.start(BrowserAction.SCANNING);
        // Quand je demande une sauvegarde
        backUpWardrobe.backUp();
        // Alors la demande est ignorée
        assertThat(reader.tabs()).isZero();
        assertThat(session.overview().currentAction()).contains(BrowserAction.SCANNING);
    }

    private void anInventoryOf(long... ids) {
        listings.saveAll(LongStream.of(ids).mapToObj(id -> Listing.seen(anActiveListing(id), EARLIER)).toList());
    }
}
