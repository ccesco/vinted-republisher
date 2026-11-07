package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScannedListingTest {

    @Test
    void refuses_a_blank_title() {
        assertThatThrownBy(() -> aListing(1, " ", "1.00", ACTIVE)).isInstanceOf(InvalidListing.class);
    }

    @Test
    void refuses_an_identifier_that_is_not_positive() {
        assertThatThrownBy(() -> new VintedListingId(0)).isInstanceOf(InvalidListing.class);
    }
}
