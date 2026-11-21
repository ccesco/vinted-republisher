package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.BackupVerification;
import fr.vinted.republisher.domain.backup.BackupVerificationRepository;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.duplicate.ListingEraser;
import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.ListingTagRepository;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
class JpaListingEraserTest extends ApplicationWithDatabaseTest {

    private static final VintedListingId ERASED = new VintedListingId(201);
    private static final VintedListingId KEPT = new VintedListingId(202);
    private static final Instant AT = Instant.parse("2026-11-18T10:00:00Z");
    private static final List<String> TABLES = List.of("listing", "listing_statistics_reading",
            "listing_price_change", "listing_content", "listing_photo", "backup_validation",
            "backup_verification");

    @Autowired
    private ListingEraser eraser;

    @Autowired
    private ListingRepository listings;

    @Autowired
    private ListingStatisticsRepository statistics;

    @Autowired
    private ListingTagRepository tags;

    @Autowired
    private ListingBackupRepository backups;

    @Autowired
    private BackupValidationRepository validations;

    @Autowired
    private BackupVerificationRepository verifications;

    @Autowired
    private ActionJournal journal;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void erases_the_listing_and_its_data_but_not_its_journal_nor_the_other_listings() {
        // AT-47-04 : l'annonce et tout ce qui la concerne sont effacés, le journal garde ses lignes
        everythingAbout(ERASED);
        everythingAbout(KEPT);

        eraser.erase(ERASED);

        assertThat(TABLES).allSatisfy(table -> assertThat(rows(table, ERASED)).as(table).isZero());
        assertThat(TABLES).allSatisfy(table -> assertThat(rows(table, KEPT)).as(table).isPositive());
        assertThat(journal.of(ERASED)).hasSize(1);
    }

    @Test
    void erases_the_article_left_without_listing() {
        // AT-53-09. Étant donné une annonce doublon taguée « été », seule de son article
        everythingAbout(ERASED);
        everythingAbout(KEPT);

        // Quand elle est effacée
        eraser.erase(ERASED);

        // Alors son article et son tag n'existent plus
        assertThat(tags.findAll()).containsExactly(new ListingTag(KEPT, new Tag("été")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM article", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM article_tag", Long.class)).isEqualTo(1);
    }

    @Test
    void keeps_the_article_another_listing_puts_on_sale() {
        everythingAbout(ERASED);
        UUID article = jdbc.queryForObject("SELECT article_id FROM listing WHERE vinted_id = ?", UUID.class,
                ERASED.value());
        jdbc.update("INSERT INTO listing SELECT 203, url, title, price_amount, price_currency, present,"
                + " last_seen_at, first_seen_at, article_id FROM listing WHERE vinted_id = ?",
                ERASED.value());

        eraser.erase(ERASED);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM article WHERE id = ?", Long.class, article)).isOne();
        assertThat(tags.findAll()).containsExactly(new ListingTag(new VintedListingId(203), new Tag("été")));
    }

    @Test
    void erasing_a_listing_without_data_erases_only_it() {
        listings.saveAll(List.of(Listing.seen(aListing(ERASED.value(), "Robe", "12.00", ACTIVE), AT)));

        eraser.erase(ERASED);

        assertThat(listings.find(ERASED)).isEmpty();
    }

    private void everythingAbout(VintedListingId id) {
        listings.saveAll(List.of(Listing.seen(aListing(id.value(), "Robe", "12.00", ACTIVE), AT)));
        statistics.addAll(List.of(new StatisticsReading(id, new ListingStatistics(3, 1), AT)));
        tags.add(new Tag("été"), List.of(id));
        ListingBackup backup = aBackup(id.value(), AT);
        backups.save(backup);
        validations.save(BackupValidation.of(backup, AT));
        verifications.save(VerificationResult.of(backup, new BackupVerification(List.of()), AT));
        journal.write(new ActionRecord(AT, JournaledAction.WARDROBE_BACKUP, ActionOutcome.SUCCEEDED, Optional.of(id),
                Optional.empty()));
    }

    private int rows(String table, VintedListingId id) {
        String column = table.equals("listing") ? "vinted_id" : "listing_id";
        Map<String, Object> count = jdbc.queryForMap("SELECT COUNT(*) AS n FROM " + table + " WHERE " + column
                + " = ?", id.value());
        return ((Number) count.get("n")).intValue();
    }
}
