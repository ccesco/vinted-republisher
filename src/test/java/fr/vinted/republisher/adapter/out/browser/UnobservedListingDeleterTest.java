package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import org.junit.jupiter.api.Test;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aLink;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/** Until the real page is observed with the member, the tool refuses to delete anything. */
class UnobservedListingDeleterTest {

    private final UnobservedListingDeleter deleter = new UnobservedListingDeleter();

    @Test
    void refuses_to_delete_without_asking_anything() {
        assertThatThrownBy(() -> deleter.delete(aLink(1), read -> fail("nothing may be decided")))
                .isInstanceOf(ListingPageUnreadable.class).hasMessageContaining("AT-09-20");
    }

    @Test
    void refuses_to_recheck() {
        assertThatThrownBy(() -> deleter.show(aLink(1))).isInstanceOf(ListingPageUnreadable.class);
    }
}
