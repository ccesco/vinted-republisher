package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

class ListingInventoryTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");

    @Test
    void joins_each_listing_with_its_latest_reading_backup_and_validation() {
        ListingBackup backup = aBackup(1, SAVED_AT);
        StatisticsReading reading = reading(1, 12, 3);
        BackupValidation validation = BackupValidation.of(backup, SAVED_AT.plusSeconds(60));

        ListingInventory inventory = ListingInventory.of(List.of(present(1, "a", "1"), present(2, "b", "1")),
                List.of(reading), List.of(backup), List.of(validation));

        assertThat(inventory.select(InventoryCriteria.defaults())).containsExactly(
                new InventoryEntry(present(2, "b", "1"), Optional.empty(), Optional.empty(), Optional.empty()),
                new InventoryEntry(present(1, "a", "1"), Optional.of(reading), Optional.of(backup),
                        Optional.of(validation.validatedAt())));
    }

    @Test
    void validation_of_a_replaced_backup_does_not_count() {
        ListingBackup replaced = aBackup(1, SAVED_AT);
        ListingBackup current = aBackup(1, SAVED_AT.plusSeconds(3600));

        ListingInventory inventory = ListingInventory.of(List.of(present(1, "a", "1")), List.of(), List.of(current),
                List.of(BackupValidation.of(replaced, SAVED_AT.plusSeconds(60))));

        assertThat(inventory.select(InventoryCriteria.defaults())).singleElement()
                .satisfies(entry -> assertThat(entry.validatedAt()).isEmpty());
    }

    @Test
    void filters_on_presence() {
        ListingInventory inventory = ListingInventory.of(
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
        ListingInventory inventory = ListingInventory.of(
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
        ListingInventory inventory = ListingInventory.of(
                List.of(present(1, "Pull rayé", "1"), present(2, "ROBE d'été", "1"), present(3, "Jean bleu", "1")),
                List.of(), List.of(), List.of());

        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.ALL, "  rAye "))).containsExactly(1L);
        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.ALL, "ETE"))).containsExactly(2L);
        assertThat(ids(inventory, criteria(PresenceFilter.PRESENT, BackupFilter.ALL, "%"))).isEmpty();
    }

    @Test
    void sorts_on_statistics_with_unread_listings_last_in_both_directions() {
        ListingInventory inventory = ListingInventory.of(
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
        ListingInventory inventory = ListingInventory.of(
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
        ListingInventory inventory = ListingInventory.of(
                List.of(present(1, "same", "1"), present(2, "same", "1")), List.of(), List.of(), List.of());

        assertThat(ids(inventory, sorted(InventorySort.PRICE, SortDirection.ASC))).containsExactly(2L, 1L);
        assertThat(ids(inventory, sorted(InventorySort.PRICE, SortDirection.DESC))).containsExactly(2L, 1L);
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
