package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListingStatusFlagsTest {

    @Test
    void listing_is_active_when_no_flag_is_set() {
        assertThat(new ListingStatusFlags(false, false, false, false, false).isActive()).isTrue();
    }

    @Test
    void draft_is_not_active() {
        assertThat(new ListingStatusFlags(true, false, false, false, false).isActive()).isFalse();
    }

    @Test
    void closed_listing_is_not_active() {
        assertThat(new ListingStatusFlags(false, true, false, false, false).isActive()).isFalse();
    }

    @Test
    void reserved_listing_is_not_active() {
        assertThat(new ListingStatusFlags(false, false, true, false, false).isActive()).isFalse();
    }

    @Test
    void hidden_listing_is_not_active() {
        assertThat(new ListingStatusFlags(false, false, false, true, false).isActive()).isFalse();
    }

    @Test
    void listing_being_processed_is_not_active() {
        assertThat(new ListingStatusFlags(false, false, false, false, true).isActive()).isFalse();
    }
}
