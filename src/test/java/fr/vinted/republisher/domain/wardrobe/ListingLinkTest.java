package fr.vinted.republisher.domain.wardrobe;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingLinkTest {

    private static final VintedListingId ID = new VintedListingId(1);

    @Test
    void accepts_a_web_address() {
        URI url = URI.create("https://vinted.test/items/1-pull");

        assertThat(new ListingLink(ID, url).url()).isEqualTo(url);
    }

    @Test
    void refuses_a_link_that_is_not_a_web_address() {
        assertThatThrownBy(() -> new ListingLink(ID, URI.create("javascript:alert(1)")))
                .isInstanceOf(InvalidListing.class);
    }

    @Test
    void refuses_a_relative_link() {
        assertThatThrownBy(() -> new ListingLink(ID, URI.create("/items/1"))).isInstanceOf(InvalidListing.class);
    }
}
