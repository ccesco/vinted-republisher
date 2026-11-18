package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.tag.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryCriteriaTest {

    @Test
    void defaults_show_present_listings_most_recent_first() {
        assertThat(InventoryCriteria.defaults()).isEqualTo(new InventoryCriteria(
                PresenceFilter.PRESENT, BackupFilter.ALL, "", InventorySort.RECENCY, SortDirection.DESC));
    }

    @Test
    void missing_search_means_no_search() {
        assertThat(new InventoryCriteria(PresenceFilter.PRESENT, BackupFilter.ALL, null, InventorySort.RECENCY,
                SortDirection.DESC).search()).isEmpty();
    }

    @Test
    void search_is_kept_without_case_accents_nor_surrounding_spaces() {
        assertThat(new InventoryCriteria(PresenceFilter.PRESENT, BackupFilter.ALL, "  Été ", InventorySort.RECENCY,
                SortDirection.DESC).search()).isEqualTo("ete");
    }

    @Test
    void search_longer_than_100_characters_is_refused() {
        String tooLong = "a".repeat(101);

        assertThatThrownBy(() -> new InventoryCriteria(PresenceFilter.PRESENT, BackupFilter.ALL, tooLong,
                InventorySort.RECENCY, SortDirection.DESC)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void no_tag_filter_by_default() {
        assertThat(InventoryCriteria.defaults().tag()).isEmpty();
    }

    @Test
    void tag_filter_is_kept_as_a_formed_tag() {
        InventoryCriteria criteria = new InventoryCriteria(PresenceFilter.ALL, BackupFilter.ALL, "",
                InventorySort.RECENCY, SortDirection.DESC, Optional.of(new Tag("Été")));

        assertThat(criteria.tag()).contains(new Tag("été"));
    }
}
