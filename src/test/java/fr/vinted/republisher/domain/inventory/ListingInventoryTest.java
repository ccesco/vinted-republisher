package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.backup.BackupSummary;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

class ListingInventoryTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");

    private static ListingInventory inventoryOf(List<Listing> listings, List<StatisticsReading> readings,
                                                List<ListingBackup> backups, List<BackupValidation> validations) {
        return ListingInventory.of(listings, readings, List.of(), backups.stream().map(ListingBackup::summary).toList(),
                validations, List.of(),
                List.of(), List.of(), List.of(),
                new RepublicationDelay(Duration.ofMinutes(5)), SAVED_AT);
    }

    @Test
    void joins_each_listing_with_its_latest_reading_backup_and_validation() {
        ListingBackup backup = aBackup(1, SAVED_AT);
        StatisticsReading reading = reading(1, 12, 3);
        BackupValidation validation = BackupValidation.of(backup, SAVED_AT.plusSeconds(60));

        ListingInventory inventory = inventoryOf(List.of(present(1, "a", "1"), present(2, "b", "1")),
                List.of(reading), List.of(backup), List.of(validation));

        assertThat(inventory.select(InventoryCriteria.defaults())).containsExactly(
                new InventoryEntry(present(2, "b", "1"), Optional.empty(), Optional.empty(), Optional.empty(),
                        ListingStage.TO_BACK_UP, Optional.empty()),
                new InventoryEntry(present(1, "a", "1"), Optional.of(reading), Optional.of(backup.summary()),
                        Optional.of(validation.validatedAt()), ListingStage.TO_DELETE, Optional.empty()));
    }

    @Test
    void validation_of_a_replaced_backup_does_not_count() {
        ListingBackup replaced = aBackup(1, SAVED_AT);
        ListingBackup current = aBackup(1, SAVED_AT.plusSeconds(3600));

        ListingInventory inventory = inventoryOf(List.of(present(1, "a", "1")), List.of(), List.of(current),
                List.of(BackupValidation.of(replaced, SAVED_AT.plusSeconds(60))));

        assertThat(inventory.select(InventoryCriteria.defaults())).singleElement()
                .satisfies(entry -> assertThat(entry.validatedAt()).isEmpty());
    }

    @Test
    void filters_on_presence() {
        ListingInventory inventory = inventoryOf(
                List.of(present(1, "a", "1"), present(2, "b", "1").absent(), present(3, "c", "1")),
                List.of(), List.of(), List.of());

        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.ALL, ""))).containsExactly(3L, 1L);
        assertThat(ids(inventory, criteria(PresenceFilter.ABSENT, BackupFilter.ALL, ""))).containsExactly(2L);
        assertThat(ids(inventory, criteria(PresenceFilter.ALL, BackupFilter.ALL, ""))).containsExactly(3L, 2L, 1L);
    }

    @Test
    void filters_on_backup_state() {
        ListingBackup validated = aBackup(3, SAVED_AT);
        ListingBackup savedAgain = aBackup(4, SAVED_AT.plusSeconds(3600));
        ListingInventory inventory = inventoryOf(
                List.of(present(1, "a", "1"), present(2, "b", "1"), present(3, "c", "1"), present(4, "d", "1")),
                List.of(), List.of(aBackup(2, SAVED_AT), validated, savedAgain),
                List.of(BackupValidation.of(validated, SAVED_AT), BackupValidation.of(aBackup(4, SAVED_AT), SAVED_AT)));

        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.NOT_BACKED_UP, "")))
                .containsExactly(1L);
        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.NOT_VALIDATED, "")))
                .containsExactly(4L, 2L);
        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.VALIDATED, ""))).containsExactly(3L);
    }

    @Test
    void searches_the_title_ignoring_case_accents_and_surrounding_spaces() {
        ListingInventory inventory = inventoryOf(
                List.of(present(1, "Pull rayé", "1"), present(2, "ROBE d'été", "1"), present(3, "Jean bleu", "1")),
                List.of(), List.of(), List.of());

        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.ALL, "  rAye "))).containsExactly(1L);
        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.ALL, "ETE"))).containsExactly(2L);
        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.ALL, "%"))).isEmpty();
    }

    @Test
    void sorts_on_statistics_with_unread_listings_last_in_both_directions() {
        ListingInventory inventory = inventoryOf(
                List.of(present(101, "a", "1"), present(102, "b", "1"), present(103, "c", "1")),
                List.of(reading(101, 5, 2), reading(102, 40, 0)), List.of(), List.of());

        assertThat(ids(inventory, sorted(InventorySort.VIEWS, SortDirection.DESC))).containsExactly(102L, 101L, 103L);
        assertThat(ids(inventory, sorted(InventorySort.VIEWS, SortDirection.ASC))).containsExactly(101L, 102L, 103L);
        assertThat(ids(inventory, sorted(InventorySort.FAVOURITES, SortDirection.DESC)))
                .containsExactly(101L, 102L, 103L);
        assertThat(ids(inventory, sorted(InventorySort.FAVOURITES, SortDirection.ASC)))
                .containsExactly(102L, 101L, 103L);
    }

    @Test
    void sorts_on_title_price_backup_date_and_recency() {
        ListingInventory inventory = inventoryOf(
                List.of(present(101, "b", "5"), present(102, "A", "12"), present(103, "c", "5")),
                List.of(), List.of(aBackup(101, SAVED_AT.plusSeconds(86_400)), aBackup(103, SAVED_AT)), List.of());

        assertThat(ids(inventory, sorted(InventorySort.TITLE, SortDirection.ASC))).containsExactly(102L, 101L, 103L);
        assertThat(ids(inventory, sorted(InventorySort.TITLE, SortDirection.DESC))).containsExactly(103L, 101L, 102L);
        assertThat(ids(inventory, sorted(InventorySort.PRICE, SortDirection.ASC))).containsExactly(103L, 101L, 102L);
        assertThat(ids(inventory, sorted(InventorySort.SAVED_AT, SortDirection.DESC)))
                .containsExactly(101L, 103L, 102L);
        assertThat(ids(inventory, sorted(InventorySort.SAVED_AT, SortDirection.ASC)))
                .containsExactly(103L, 101L, 102L);
        assertThat(ids(inventory, sorted(InventorySort.RECENCY, SortDirection.ASC))).containsExactly(101L, 102L, 103L);
    }

    @Test
    void ties_put_the_most_recent_first_whatever_the_direction() {
        ListingInventory inventory = inventoryOf(
                List.of(present(1, "same", "1"), present(2, "same", "1")), List.of(), List.of(), List.of());

        assertThat(ids(inventory, sorted(InventorySort.PRICE, SortDirection.ASC))).containsExactly(2L, 1L);
        assertThat(ids(inventory, sorted(InventorySort.PRICE, SortDirection.DESC))).containsExactly(2L, 1L);
    }

    @Test
    void two_deletions_clicked_at_the_same_instant_keep_the_one_not_settled_as_not_done() {
        ListingBackup backup = aBackup(1, SAVED_AT);
        Instant clickedAt = SAVED_AT.plusSeconds(3600);
        ListingDeletion notDone = ListingDeletion.attempted(backup, clickedAt).notDone(clickedAt.plusSeconds(60));
        ListingDeletion unconfirmed = ListingDeletion.attempted(backup, clickedAt);

        ListingInventory inventory = ListingInventory.of(List.of(present(1, "a", "1")), List.of(), List.of(),
                summaries(backup), List.of(), List.of(), List.of(notDone, unconfirmed), List.of(), List.of(),
                new RepublicationDelay(Duration.ofMinutes(5)), clickedAt);

        assertThat(inventory.entries()).singleElement()
                .satisfies(entry -> assertThat(entry.stage()).isEqualTo(ListingStage.DELETION_TO_CHECK));
    }

    @Test
    void republished_listing_bears_its_new_listing_when_known() {
        ListingBackup backup = aBackup(1, SAVED_AT);
        Instant clickedAt = SAVED_AT.plusSeconds(3600);
        ListingRepublication published = ListingRepublication.attempted(backup, clickedAt)
                .published(new VintedListingId(2), clickedAt.plusSeconds(60));
        Listing newListing = present(2, "a", "1");

        ListingInventory inventory = ListingInventory.of(List.of(present(1, "a", "1").absent(), newListing),
                List.of(), List.of(), summaries(backup), List.of(), List.of(), List.of(), List.of(published), List.of(),
                new RepublicationDelay(Duration.ofMinutes(5)), clickedAt.plusSeconds(120));

        assertThat(inventory.entries().getFirst().republication()).contains(published);
        assertThat(inventory.entries().getFirst().newListing()).contains(newListing);
        assertThat(inventory.entries().getLast().newListing()).isEmpty();
    }

    @Test
    void stage_filter_keeps_only_listings_at_that_stage() {
        ListingInventory inventory = ListingInventory.of(List.of(present(1, "a", "1"), present(2, "b", "1")),
                List.of(), List.of(), summaries(aBackup(2, SAVED_AT)), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                new RepublicationDelay(Duration.ofMinutes(5)), SAVED_AT);

        assertThat(ids(inventory, new InventoryCriteria(PresenceFilter.ALL, BackupFilter.ALL, "",
                InventorySort.RECENCY, SortDirection.DESC, Optional.empty(), Optional.of(ListingStage.TO_VALIDATE))))
                .containsExactly(2L);
    }

    @Test
    void republication_to_check_is_borne_without_new_listing() {
        ListingBackup backup = aBackup(1, SAVED_AT);
        Instant clickedAt = SAVED_AT.plusSeconds(3600);
        ListingRepublication toCheck = ListingRepublication.attempted(backup, clickedAt);

        ListingInventory inventory = ListingInventory.of(List.of(present(1, "a", "1").absent()), List.of(),
                List.of(), summaries(backup), List.of(), List.of(), List.of(), List.of(toCheck), List.of(),
                new RepublicationDelay(Duration.ofMinutes(5)), clickedAt.plusSeconds(120));

        assertThat(inventory.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.stage()).isEqualTo(ListingStage.REPUBLICATION_TO_CHECK);
            assertThat(entry.republication()).contains(toCheck);
            assertThat(entry.newListing()).isEmpty();
        });
    }

    @Test
    void republication_checked_not_published_is_not_borne() {
        ListingBackup backup = aBackup(1, SAVED_AT);
        Instant clickedAt = SAVED_AT.plusSeconds(3600);
        ListingRepublication notPublished = ListingRepublication.attempted(backup, clickedAt)
                .notPublished(clickedAt.plusSeconds(60));

        ListingInventory inventory = ListingInventory.of(List.of(present(1, "a", "1").absent()), List.of(),
                List.of(), summaries(backup), List.of(), List.of(), List.of(), List.of(notPublished), List.of(),
                new RepublicationDelay(Duration.ofMinutes(5)), clickedAt.plusSeconds(120));

        assertThat(inventory.entries()).singleElement()
                .satisfies(entry -> assertThat(entry.republication()).isEmpty());
    }

    @Test
    void each_listing_carries_the_advice_from_its_latest_and_baseline_readings() {
        Instant eightDaysAgo = SAVED_AT.minus(Duration.ofDays(8));
        Instant yesterday = SAVED_AT.minus(Duration.ofDays(1));
        StatisticsReading dormantBaseline = new StatisticsReading(new VintedListingId(1), new ListingStatistics(40, 2),
                eightDaysAgo);
        StatisticsReading dormantLatest = new StatisticsReading(new VintedListingId(1), new ListingStatistics(46, 2),
                yesterday);
        StatisticsReading seenBaseline = new StatisticsReading(new VintedListingId(2), new ListingStatistics(0, 0),
                eightDaysAgo);
        StatisticsReading seenLatest = new StatisticsReading(new VintedListingId(2), new ListingStatistics(30, 0),
                yesterday);

        ListingInventory inventory = ListingInventory.of(List.of(present(1, "a", "1"), present(2, "b", "1")),
                List.of(dormantLatest, seenLatest), List.of(seenBaseline, dormantBaseline), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), new RepublicationDelay(Duration.ofMinutes(5)), SAVED_AT);

        assertThat(inventory.entries()).extracting(InventoryEntry::advice)
                .containsExactly(Optional.of(new RepublicationAdvice(6, eightDaysAgo)), Optional.empty());
    }

    @Test
    void each_listing_bears_its_tags_in_alphabetical_order() {
        ListingInventory inventory = inventoryOf(List.of(present(1, "a", "1"), present(2, "b", "1")), List.of(),
                List.of(), List.of()).withTags(List.of(tag(1, "lot"), tag(1, "été")));

        assertThat(inventory.select(InventoryCriteria.defaults())).extracting(InventoryEntry::tags)
                .containsExactly(List.of(), List.of(new Tag("été"), new Tag("lot")));
    }

    @Test
    void tag_filter_keeps_only_listings_bearing_the_tag() {
        ListingInventory inventory = inventoryOf(List.of(present(1, "a", "1"), present(2, "b", "1"),
                present(3, "c", "1")), List.of(), List.of(), List.of())
                .withTags(List.of(tag(1, "lot"), tag(2, "été"), tag(3, "lot")));
        InventoryCriteria lot = new InventoryCriteria(PresenceFilter.PRESENT, BackupFilter.ALL, "",
                InventorySort.RECENCY, SortDirection.DESC, Optional.of(new Tag("LOT")));

        assertThat(ids(inventory, lot)).containsExactly(3L, 1L);
    }

    @Test
    void a_listing_without_republication_bears_an_empty_tally() {
        ListingInventory inventory = inventoryOf(List.of(present(1, "a", "1")), List.of(), List.of(), List.of())
                .withRepublicationTallies(List.of(), Map.of());

        assertThat(inventory.entries()).extracting(InventoryEntry::republicationTally)
                .containsExactly(RepublicationTally.NONE);
    }

    @Test
    void every_listing_of_the_chain_bears_the_tally_of_the_whole_article() {
        ListingInventory inventory = inventoryOf(List.of(present(1, "a", "1"), present(2, "a", "1"),
                present(3, "a", "1"), present(4, "b", "1")), List.of(), List.of(), List.of())
                .withRepublicationTallies(List.of(published(1, 2), published(2, 3)),
                        Map.of(id(1), 2, id(3), 1, id(4), 5));

        assertThat(inventory.entries()).extracting(InventoryEntry::republicationTally).containsExactly(
                new RepublicationTally(2, 3), new RepublicationTally(2, 3), new RepublicationTally(2, 3),
                new RepublicationTally(0, 5));
    }

    @Test
    void a_listing_of_the_chain_unknown_to_the_tool_still_counts() {
        ListingInventory inventory = inventoryOf(List.of(present(3, "a", "1")), List.of(), List.of(), List.of())
                .withRepublicationTallies(List.of(published(1, 2), published(2, 3)), Map.of(id(1), 1));

        assertThat(inventory.entries()).extracting(InventoryEntry::republicationTally)
                .containsExactly(new RepublicationTally(2, 1));
    }

    @Test
    void a_chain_that_loops_stops_on_a_listing_already_seen() {
        ListingInventory inventory = inventoryOf(List.of(present(1, "a", "1"), present(2, "a", "1")), List.of(),
                List.of(), List.of())
                .withRepublicationTallies(List.of(published(1, 2), published(2, 1)), Map.of(id(2), 1));

        assertThat(inventory.entries()).extracting(InventoryEntry::republicationTally)
                .containsExactly(new RepublicationTally(2, 1), new RepublicationTally(2, 1));
    }

    @Test
    void only_published_republications_link_the_chain() {
        ListingRepublication toCheck = ListingRepublication.attempted(aBackup(1, SAVED_AT), SAVED_AT);
        ListingInventory inventory = inventoryOf(List.of(present(1, "a", "1")), List.of(), List.of(), List.of())
                .withRepublicationTallies(List.of(toCheck), Map.of(id(1), 1));

        assertThat(inventory.entries()).extracting(InventoryEntry::republicationTally)
                .containsExactly(new RepublicationTally(0, 1));
    }

    private static ListingRepublication published(long id, long newListing) {
        return ListingRepublication.attempted(aBackup(id, SAVED_AT), SAVED_AT)
                .published(id(newListing), SAVED_AT.plusSeconds(60));
    }

    private static VintedListingId id(long id) {
        return new VintedListingId(id);
    }

    private static ListingTag tag(long id, String tag) {
        return new ListingTag(new VintedListingId(id), new Tag(tag));
    }

    private static List<BackupSummary> summaries(ListingBackup... backups) {
        return Stream.of(backups).map(ListingBackup::summary).toList();
    }

    private static Listing present(long id, String title, String price) {
        return Listing.seen(aListing(id, title, price, ACTIVE), SCANNED_AT);
    }

    private static StatisticsReading reading(long id, int views, int favourites) {
        return new StatisticsReading(new VintedListingId(id), new ListingStatistics(views, favourites), SCANNED_AT);
    }

    private static InventoryCriteria criteria(PresenceFilter presence, BackupFilter backup, String search) {
        return new InventoryCriteria(presence, backup, search, InventorySort.RECENCY, SortDirection.DESC);
    }

    private static InventoryCriteria sorted(InventorySort sort, SortDirection direction) {
        return new InventoryCriteria(PresenceFilter.PRESENT, BackupFilter.ALL, "", sort, direction);
    }

    private static List<Long> ids(ListingInventory inventory, InventoryCriteria criteria) {
        return inventory.select(criteria).stream().map(entry -> entry.listing().id().value()).toList();
    }
}
