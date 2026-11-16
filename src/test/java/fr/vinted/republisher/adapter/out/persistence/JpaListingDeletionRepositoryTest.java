package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
class JpaListingDeletionRepositoryTest extends ApplicationWithDatabaseTest {

    private static final VintedListingId LISTING = new VintedListingId(10208778166L);
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00.123456Z");
    private static final Instant CLICKED_AT = Instant.parse("2026-11-11T10:00:00.654321Z");

    @Autowired
    private ListingDeletionRepository deletions;

    @Autowired
    private ListingRepository listings;

    @Autowired
    private ListingBackupRepository listingBackups;

    @Autowired
    private ShowListingDeletion showListingDeletion;

    @Autowired
    private JdbcTemplate database;

    private ListingBackup backup;

    @BeforeEach
    void startWithABackupAndNoDeletion() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(LISTING.value()), SAVED_AT)));
        backup = aBackup(LISTING.value(), SAVED_AT);
        listingBackups.save(backup);
    }

    @Test
    void nothing_is_found_before_the_first_attempt() {
        assertThat(deletions.findLatest(LISTING)).isEmpty();
    }

    @Test
    void gives_back_an_unconfirmed_and_then_a_confirmed_deletion_as_they_were_saved() {
        ListingDeletion attempt = ListingDeletion.attempted(backup, CLICKED_AT);
        deletions.save(attempt);
        assertThat(deletions.findLatest(LISTING)).contains(attempt);

        ListingDeletion confirmed = attempt.confirmed(CLICKED_AT.plusSeconds(5));
        deletions.save(confirmed);
        assertThat(deletions.findLatest(LISTING)).contains(confirmed);
        assertThat(database.queryForObject("SELECT count(*) FROM listing_deletion", Long.class)).isEqualTo(1);
    }

    @Test
    void republicationDateSurvivesRestart() {
        // Étant donné une suppression prouvée à 14:32, enregistrée en base
        deletions.save(ListingDeletion.attempted(backup, Instant.parse("2026-11-11T14:31:58Z"))
                .confirmed(Instant.parse("2026-11-11T14:32:00Z")));
        // Quand l'outil redémarre à 14:34 (rien n'est gardé en mémoire : tout est relu en base)
        // Alors l'aperçu indique toujours « republiable à partir de 14:37 »
        assertThat(showListingDeletion.show(LISTING).republishableAt())
                .contains(Instant.parse("2026-11-11T14:37:00Z"));
    }

    @Test
    void finds_the_latest_attempt_of_each_listing() {
        VintedListingId other = new VintedListingId(LISTING.value() + 1);
        listings.saveAll(List.of(Listing.seen(anActiveListing(other.value()), SAVED_AT)));
        ListingBackup otherBackup = aBackup(other.value(), SAVED_AT);
        listingBackups.save(otherBackup);
        deletions.save(ListingDeletion.attempted(backup, CLICKED_AT).notDone(CLICKED_AT.plusSeconds(60)));
        ListingDeletion second = ListingDeletion.attempted(backup, CLICKED_AT.plusSeconds(120));
        deletions.save(second);
        ListingDeletion onOther = ListingDeletion.attempted(otherBackup, CLICKED_AT);
        deletions.save(onOther);

        assertThat(deletions.findAllLatest()).containsExactlyInAnyOrder(second, onOther);
    }

    @Test
    void the_latest_attempt_is_the_last_one_clicked() {
        ListingDeletion notDone = ListingDeletion.attempted(backup, CLICKED_AT).notDone(CLICKED_AT.plusSeconds(60));
        deletions.save(notDone);
        ListingDeletion second = ListingDeletion.attempted(backup, CLICKED_AT.plusSeconds(120));
        deletions.save(second);

        assertThat(deletions.findLatest(LISTING)).contains(second);
    }

    @Test
    void a_listing_cannot_have_two_confirmed_deletions() {
        deletions.save(ListingDeletion.attempted(backup, CLICKED_AT).confirmed(CLICKED_AT));

        assertThatThrownBy(() -> deletions.save(
                ListingDeletion.attempted(backup, CLICKED_AT.plusSeconds(60)).confirmed(CLICKED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void attemptsSinceSurviveRestart() {
        // Étant donné des tentatives de suppression enregistrées, quelle que soit leur issue
        ListingDeletion older = ListingDeletion.attempted(backup, CLICKED_AT.minusSeconds(3600));
        deletions.save(older.notDone(CLICKED_AT));
        deletions.save(ListingDeletion.attempted(backup, CLICKED_AT));
        // Quand l'outil redémarre (tout est relu en base)
        // Alors leurs dates sont relues et comptent toujours, sauf celles d'avant la date demandée
        assertThat(deletions.findAttemptedSince(CLICKED_AT.minusSeconds(3600))).containsExactly(CLICKED_AT);
        assertThat(deletions.findAttemptedSince(CLICKED_AT.minusSeconds(3601)))
                .containsExactlyInAnyOrder(CLICKED_AT.minusSeconds(3600), CLICKED_AT);
    }
}
