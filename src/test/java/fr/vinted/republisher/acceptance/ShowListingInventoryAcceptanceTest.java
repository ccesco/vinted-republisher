package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.backup.BackupSummary;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoMissing;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.inventory.BackupFilter;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.InventorySort;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.inventory.PresenceFilter;
import fr.vinted.republisher.domain.inventory.RepublicationTally;
import fr.vinted.republisher.domain.inventory.SortDirection;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-18: the member sees every listing, filtered, sorted and searched; US-44: with its republication tally. */
class ShowListingInventoryAcceptanceTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-11-18T10:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-11-18T10:05:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
    private final InMemoryListingBackupRepository backups = new InMemoryListingBackupRepository();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
    private final InMemoryBackupVerificationRepository verifications = new InMemoryBackupVerificationRepository();
    private final ShowListingInventory showListingInventory =
            new ShowListingInventory(listings, statistics, backups, validations,
                    new InMemoryListingDeletionRepository(), republications,
                    new RepublicationDelay(Duration.ofMinutes(5)), Clock.systemUTC(),
                    new InMemoryListingTagRepository(), verifications);

    /** AT-42-05 */
    @Test
    void inventory_shows_backup_summaries() {
        // Étant donné une annonce sauvegardée avec 5 photos et validée, une annonce sauvegardée dont la dernière
        //   vérification a échoué, et une annonce sans sauvegarde
        listings.saveAll(List.of(present(101, "a", "1"), present(102, "b", "1"), present(103, "c", "1")));
        ListingBackup validated = aBackupWithPhotos(101, 5);
        backups.save(validated);
        Instant validatedAt = SAVED_AT.plusSeconds(60);
        validations.save(BackupValidation.of(validated, validatedAt));
        backups.save(aBackupWithPhotos(102, 1));
        verifications.save(new VerificationResult(new VintedListingId(102), SAVED_AT, SAVED_AT.plusSeconds(30),
                List.of(new PhotoMissing(0))));

        // Quand le membre affiche l'inventaire
        List<InventoryEntry> entries = showListingInventory.show(InventoryCriteria.defaults());

        // Alors la première a sa date de sauvegarde, 5 photos et sa date de validation
        assertThat(entryOf(entries, 101).backup()).contains(new BackupSummary(new VintedListingId(101), SAVED_AT, 5));
        assertThat(entryOf(entries, 101).validatedAt()).contains(validatedAt);
        // Et la deuxième est à l'étape « vérification échouée »
        assertThat(entryOf(entries, 102).stage()).isEqualTo(ListingStage.VERIFICATION_FAILED);
        // Et la troisième n'a pas de sauvegarde
        assertThat(entryOf(entries, 103).backup()).isEmpty();
    }

    @Test
    void present_listings_most_recent_first_by_default() {
        // Étant donné les annonces 101 et 103 présentes et l'annonce 102 absente
        listings.saveAll(List.of(present(101, "a", "1"), present(102, "b", "1").absent(), present(103, "c", "1")));

        // Quand le membre affiche la liste sans critère
        // Alors il voit 103 puis 101
        assertThat(ids(InventoryCriteria.defaults())).containsExactly(103L, 101L);
    }

    @Test
    void filters_on_presence() {
        // Étant donné les annonces 101 et 103 présentes et l'annonce 102 absente
        listings.saveAll(List.of(present(101, "a", "1"), present(102, "b", "1").absent(), present(103, "c", "1")));

        // Quand le membre demande les absentes
        // Alors il voit seulement 102, marquée absente
        assertThat(showListingInventory.show(filtered(PresenceFilter.ABSENT, BackupFilter.ALL, "")))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.listing().id().value()).isEqualTo(102L);
                    assertThat(entry.listing().present()).isFalse();
                });
        // Quand il demande toutes les annonces
        // Alors il voit 103, 102 puis 101
        assertThat(ids(filtered(PresenceFilter.ALL, BackupFilter.ALL, ""))).containsExactly(103L, 102L, 101L);
    }

    @Test
    void filters_on_backup_state() {
        // Étant donné l'annonce 101 jamais sauvegardée, 102 sauvegardée non validée, 103 sauvegardée et validée
        //   et 104 validée puis sauvegardée à nouveau
        listings.saveAll(List.of(present(101, "a", "1"), present(102, "b", "1"), present(103, "c", "1"),
                present(104, "d", "1")));
        backups.save(aBackup(102, SAVED_AT));
        backups.save(aBackup(103, SAVED_AT));
        validations.save(BackupValidation.of(aBackup(103, SAVED_AT), SAVED_AT.plusSeconds(60)));
        validations.save(BackupValidation.of(aBackup(104, SAVED_AT), SAVED_AT.plusSeconds(60)));
        backups.save(aBackup(104, SAVED_AT.plus(Duration.ofDays(1))));

        // Quand le membre demande les non sauvegardées
        // Alors il voit 101
        assertThat(ids(filtered(PresenceFilter.PRESENT, BackupFilter.NOT_BACKED_UP, ""))).containsExactly(101L);
        // Quand il demande les sauvegardées non validées
        // Alors il voit 104 puis 102
        assertThat(ids(filtered(PresenceFilter.PRESENT, BackupFilter.NOT_VALIDATED, ""))).containsExactly(104L, 102L);
        // Quand il demande les validées
        // Alors il voit 103
        assertThat(ids(filtered(PresenceFilter.PRESENT, BackupFilter.VALIDATED, ""))).containsExactly(103L);
    }

    @Test
    void searches_title_ignoring_case_and_accents() {
        // Étant donné les annonces « Pull rayé », « ROBE d'été » et « Jean bleu »
        listings.saveAll(List.of(present(1, "Pull rayé", "1"), present(2, "ROBE d'été", "1"),
                present(3, "Jean bleu", "1")));

        // Quand le membre cherche "  rAye "
        // Alors il voit seulement « Pull rayé »
        assertThat(ids(filtered(PresenceFilter.PRESENT, BackupFilter.ALL, "  rAye "))).containsExactly(1L);
        // Quand il cherche "ETE"
        // Alors il voit seulement « ROBE d'été »
        assertThat(ids(filtered(PresenceFilter.PRESENT, BackupFilter.ALL, "ETE"))).containsExactly(2L);
    }

    @Test
    void sorts_on_statistics_with_unread_listings_last() {
        // Étant donné l'annonce 101 relevée à 5 vues et 2 favoris, 102 à 40 vues et 0 favori, 103 jamais relevée
        listings.saveAll(List.of(present(101, "a", "1"), present(102, "b", "1"), present(103, "c", "1")));
        statistics.addAll(List.of(reading(101, 5, 2), reading(102, 40, 0)));

        // Quand le membre trie par vues décroissantes
        // Alors il voit 102, 101 puis 103
        assertThat(ids(sorted(InventorySort.VIEWS, SortDirection.DESC))).containsExactly(102L, 101L, 103L);
        // Quand il trie par vues croissantes
        // Alors il voit 101, 102 puis 103
        assertThat(ids(sorted(InventorySort.VIEWS, SortDirection.ASC))).containsExactly(101L, 102L, 103L);
        // Quand il trie par favoris décroissants
        // Alors il voit 101, 102 puis 103
        assertThat(ids(sorted(InventorySort.FAVOURITES, SortDirection.DESC))).containsExactly(101L, 102L, 103L);
    }

    @Test
    void sorts_on_title_price_and_backup_date() {
        // Étant donné les annonces 101 « b », 5 €, sauvegardée le 2 ; 102 « A », 12 €, jamais sauvegardée ;
        //   103 « c », 5 €, sauvegardée le 1
        listings.saveAll(List.of(present(101, "b", "5"), present(102, "A", "12"), present(103, "c", "5")));
        backups.save(aBackup(101, SAVED_AT.plus(Duration.ofDays(1))));
        backups.save(aBackup(103, SAVED_AT));

        // Quand le membre trie par titre croissant
        // Alors il voit 102, 101 puis 103
        assertThat(ids(sorted(InventorySort.TITLE, SortDirection.ASC))).containsExactly(102L, 101L, 103L);
        // Quand il trie par prix croissant
        // Alors il voit 103, 101 puis 102
        assertThat(ids(sorted(InventorySort.PRICE, SortDirection.ASC))).containsExactly(103L, 101L, 102L);
        // Quand il trie par date de sauvegarde décroissante
        // Alors il voit 101, 103 puis 102
        assertThat(ids(sorted(InventorySort.SAVED_AT, SortDirection.DESC))).containsExactly(101L, 103L, 102L);
    }

    @Test
    void row_shows_statistics_backup_and_validation() {
        // Étant donné l'annonce 101 présente, relevée à 12 vues et 3 favoris, sauvegardée avec 3 photos et validée
        Listing listing = present(101, "Pull rayé", "1.5");
        listings.saveAll(List.of(listing));
        StatisticsReading reading = reading(101, 12, 3);
        statistics.addAll(List.of(reading));
        ListingBackup backup = aBackupWithPhotos(101, 3);
        backups.save(backup);
        Instant validatedAt = SAVED_AT.plusSeconds(60);
        validations.save(BackupValidation.of(backup, validatedAt));

        // Quand le membre affiche la liste
        // Alors la ligne montre son titre, son prix, 12 vues, 3 favoris, la date du relevé, la date de sauvegarde,
        //   3 photos et la date de validation
        assertThat(showListingInventory.show(InventoryCriteria.defaults())).containsExactly(
                new InventoryEntry(listing, Optional.of(reading), Optional.of(backup.summary()), Optional.of(validatedAt),
                        ListingStage.TO_DELETE, Optional.empty()));
        assertThat(backup.photos()).hasSize(3);
    }

    @Test
    void filters_on_stage() {
        // Étant donné une annonce republiée (absente), une annonce dont la republication est à vérifier (absente)
        //   et une annonce prête à supprimer (présente)
        givenRepublishedToCheckAndToDelete();

        // Quand le membre demande l'étape « Republiée » et toutes les présences
        // Alors seule l'annonce republiée est renvoyée
        assertThat(ids(staged(PresenceFilter.ALL, Optional.of(ListingStage.REPUBLISHED)))).containsExactly(101L);
        // Et sans étape, les trois sont renvoyées
        assertThat(ids(staged(PresenceFilter.ALL, Optional.empty()))).containsExactly(103L, 102L, 101L);
    }

    @Test
    void combines_stage_with_presence() {
        // Étant donné une annonce republiée, donc absente
        givenRepublishedToCheckAndToDelete();

        // Quand le membre demande l'étape « Republiée » avec les présentes
        // Alors la liste est vide
        assertThat(ids(staged(PresenceFilter.PRESENT, Optional.of(ListingStage.REPUBLISHED)))).isEmpty();
    }

    @Test
    void shows_republication_of_republished_and_to_check_listings() {
        // Étant donné une annonce republiée, dont la nouvelle annonce 201 est connue de l'outil,
        //   une annonce à vérifier et une annonce prête à supprimer
        givenRepublishedToCheckAndToDelete();
        Listing newListing = present(201, "a", "1");
        listings.saveAll(List.of(newListing));

        // Quand le membre affiche toutes les annonces
        List<InventoryEntry> entries = showListingInventory.show(staged(PresenceFilter.ALL, Optional.empty()));

        // Alors la republiée porte sa date de publication et sa nouvelle annonce, avec son adresse
        InventoryEntry republished = entryOf(entries, 101);
        assertThat(republished.republication()).hasValueSatisfying(republication -> {
            assertThat(republication.publishedAt()).contains(PUBLISHED_AT);
            assertThat(republication.newListingId()).contains(new VintedListingId(201));
        });
        assertThat(republished.newListing().map(Listing::url)).contains(newListing.url());
        // Et celle à vérifier sa date de tentative, sans date de publication
        assertThat(entryOf(entries, 102).republication()).hasValueSatisfying(republication -> {
            assertThat(republication.attemptedAt()).isEqualTo(ATTEMPTED_AT);
            assertThat(republication.publishedAt()).isEmpty();
        });
        // Et la prête à supprimer aucune
        assertThat(entryOf(entries, 103).republication()).isEmpty();
    }

    /** AT-44-01 */
    @Test
    void a_listing_never_republished_has_an_empty_tally() {
        // Étant donné une annonce prête à supprimer, sans aucune republication
        givenRepublishedToCheckAndToDelete();

        // Quand le membre demande la liste
        List<InventoryEntry> entries = showListingInventory.show(InventoryCriteria.defaults());

        // Alors elle porte 0 republication réussie et 0 en échec
        assertThat(entryOf(entries, 103).republicationTally()).isEqualTo(new RepublicationTally(0, 0));
    }

    /** AT-44-02 */
    @Test
    void the_tally_covers_the_whole_republication_chain() {
        listings.saveAll(List.of(present(101, "a", "1").absent(), present(201, "a", "1").absent(),
                present(301, "a", "1")));
        // Étant donné une annonce A dont une première tentative a été constatée non publiée
        republications.save(notPublished(101, ATTEMPTED_AT));
        // Et A republiée ensuite en B
        republications.save(published(101, 201, ATTEMPTED_AT.plusSeconds(600)));
        // Et B republiée en C
        republications.save(published(201, 301, ATTEMPTED_AT.plusSeconds(1200)));
        // Et une tentative de C constatée non publiée
        republications.save(notPublished(301, ATTEMPTED_AT.plusSeconds(1800)));

        // Quand le membre demande la liste, présence « Toutes »
        List<InventoryEntry> entries = showListingInventory.show(staged(PresenceFilter.ALL, Optional.empty()));

        // Alors A, B et C portent chacune 2 republications réussies et 2 en échec
        assertThat(List.of(101L, 201L, 301L)).allSatisfy(id ->
                assertThat(entryOf(entries, id).republicationTally()).isEqualTo(new RepublicationTally(2, 2)));
    }

    @Test
    void a_listing_of_the_chain_left_out_by_the_filters_still_counts() {
        // Étant donné A (absente), constatée non publiée puis republiée en B (présente)
        listings.saveAll(List.of(present(101, "a", "1").absent(), present(201, "a", "1")));
        republications.save(notPublished(101, ATTEMPTED_AT));
        republications.save(published(101, 201, ATTEMPTED_AT.plusSeconds(600)));

        // Quand le membre demande les annonces présentes
        // Alors B, seule affichée, porte aussi la tentative non publiée de A
        assertThat(showListingInventory.show(InventoryCriteria.defaults())).singleElement()
                .satisfies(entry -> assertThat(entry.republicationTally()).isEqualTo(new RepublicationTally(1, 1)));
    }

    /** AT-44-03 */
    @Test
    void a_republication_to_check_is_not_counted() {
        listings.saveAll(List.of(present(101, "a", "1").absent()));
        // Étant donné une annonce dont une tentative a été constatée non publiée
        republications.save(notPublished(101, ATTEMPTED_AT));
        // Et dont la tentative suivante est en erreur, à vérifier
        ListingRepublication toCheck = ListingRepublication.attempted(aBackup(101, SAVED_AT),
                ATTEMPTED_AT.plusSeconds(600));
        republications.save(toCheck);

        // Quand le membre demande la liste
        InventoryEntry entry = entryOf(showListingInventory.show(staged(PresenceFilter.ALL, Optional.empty())), 101);

        // Alors elle porte 0 republication réussie et 1 en échec
        assertThat(entry.republicationTally()).isEqualTo(new RepublicationTally(0, 1));
        // Et sa colonne « Republication » reste « tentée le …, à vérifier »
        assertThat(entry.stage()).isEqualTo(ListingStage.REPUBLICATION_TO_CHECK);
        assertThat(entry.republication()).contains(toCheck);
    }

    /** AT-44-06 */
    @Test
    void reads_not_published_attempts_once_whatever_the_number_of_listings() {
        for (int count : List.of(3, 30)) {
            // Étant donné 3 annonces, puis 30 annonces, avec des tentatives non publiées
            InMemoryListingRepublicationRepository attempts = new InMemoryListingRepublicationRepository();
            InMemoryListingRepository known = new InMemoryListingRepository();
            IntStream.rangeClosed(1, count).forEach(id -> {
                known.saveAll(List.of(present(id, "a", "1")));
                attempts.save(notPublished(id, ATTEMPTED_AT));
            });

            // Quand le membre demande la liste
            new ShowListingInventory(known, statistics, backups, validations, new InMemoryListingDeletionRepository(),
                    attempts, new RepublicationDelay(Duration.ofMinutes(5)), Clock.systemUTC(),
                    new InMemoryListingTagRepository(), verifications).show(InventoryCriteria.defaults());

            // Alors les tentatives non publiées sont lues une seule fois, dans les deux cas
            assertThat(attempts.notPublishedCountReads()).isEqualTo(1);
        }
    }

    private static ListingRepublication notPublished(long id, Instant attemptedAt) {
        return ListingRepublication.attempted(aBackup(id, SAVED_AT), attemptedAt)
                .notPublished(attemptedAt.plusSeconds(60));
    }

    private static ListingRepublication published(long id, long newListing, Instant attemptedAt) {
        return ListingRepublication.attempted(aBackup(id, SAVED_AT), attemptedAt)
                .published(new VintedListingId(newListing), attemptedAt.plusSeconds(60));
    }

    private static InventoryEntry entryOf(List<InventoryEntry> entries, long id) {
        return entries.stream().filter(entry -> entry.listing().id().value() == id).findFirst().orElseThrow();
    }

    private void givenRepublishedToCheckAndToDelete() {
        listings.saveAll(List.of(present(101, "a", "1").absent(), present(102, "b", "1").absent(),
                present(103, "c", "1")));
        republications.save(ListingRepublication.attempted(aBackup(101, SAVED_AT), ATTEMPTED_AT)
                .published(new VintedListingId(201), PUBLISHED_AT));
        republications.save(ListingRepublication.attempted(aBackup(102, SAVED_AT), ATTEMPTED_AT));
        backups.save(aBackup(103, SAVED_AT));
        validations.save(BackupValidation.of(aBackup(103, SAVED_AT), SAVED_AT.plusSeconds(60)));
    }

    private static InventoryCriteria staged(PresenceFilter presence, Optional<ListingStage> stage) {
        return new InventoryCriteria(presence, BackupFilter.ALL, "", InventorySort.RECENCY, SortDirection.DESC,
                Optional.empty(), stage);
    }

    private static ListingBackup aBackupWithPhotos(long id, int count) {
        return ListingBackup.of(new ListingReading(aContent(id),
                IntStream.range(0, count).mapToObj(rank -> aPhoto(String.valueOf(rank))).toList()), SAVED_AT);
    }

    private static Listing present(long id, String title, String price) {
        return Listing.seen(aListing(id, title, price, ACTIVE), SCANNED_AT);
    }

    private static StatisticsReading reading(long id, int views, int favourites) {
        return new StatisticsReading(new VintedListingId(id), new ListingStatistics(views, favourites), SCANNED_AT);
    }

    private static InventoryCriteria filtered(PresenceFilter presence, BackupFilter backup, String search) {
        return new InventoryCriteria(presence, backup, search, InventorySort.RECENCY, SortDirection.DESC);
    }

    private static InventoryCriteria sorted(InventorySort sort, SortDirection direction) {
        return new InventoryCriteria(PresenceFilter.PRESENT, BackupFilter.ALL, "", sort, direction);
    }

    private List<Long> ids(InventoryCriteria criteria) {
        return showListingInventory.show(criteria).stream().map(entry -> entry.listing().id().value()).toList();
    }
}
