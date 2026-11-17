package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewListingLinkTest {

    private static final URI VINTED = URI.create("https://www.vinted.fr/member/42-cyril");

    @Test
    void keeps_the_identifier_and_the_page_on_the_vinted_site_without_query_nor_fragment() {
        NewListingLink link = NewListingLink.parse(
                Optional.of("  https://www.vinted.fr/items/10302211455-bonnet-1-3-mois?referrer=catalog#photos "),
                VINTED);

        assertThat(link.id()).isEqualTo(new VintedListingId(10302211455L));
        assertThat(link.url()).isEqualTo(URI.create("https://www.vinted.fr/items/10302211455-bonnet-1-3-mois"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTPS://WWW.VINTED.FR/items/7", "https://www.vinted.fr/items/7/",
            "https://www.vinted.fr/items/7-pull-ray%C3%A9"})
    void accepts_what_a_browser_gives_for_a_listing(String link) {
        assertThat(NewListingLink.parse(Optional.of(link), VINTED).id()).isEqualTo(new VintedListingId(7));
    }

    @Test
    void url_is_rebuilt_on_the_vinted_site_whatever_the_case_of_the_link() {
        assertThat(NewListingLink.parse(Optional.of("HTTPS://WWW.VINTED.FR/items/7"), VINTED).url())
                .isEqualTo(URI.create("https://www.vinted.fr/items/7"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "pas un lien", "https://evil.example/items/7", "http://www.vinted.fr/items/7",
            "https://www.vinted.fr:8443/items/7", "https://user:pw@www.vinted.fr/items/7",
            "https://www.vinted.fr/member/42-cyril", "https://www.vinted.fr/items/7/edit", "https://www.vinted.fr/items/0",
            "https://www.vinted.fr/items/1234567890123456789", "https://www.vinted.fr/items/%31",
            "javascript:alert(1)//www.vinted.fr/items/7"})
    void refuses_anything_but_the_page_of_a_listing_of_the_vinted_site(String link) {
        assertThatThrownBy(() -> NewListingLink.parse(Optional.of(link), VINTED))
                .isInstanceOf(NewListingLinkInvalid.class);
    }

    @Test
    void refuses_a_missing_link() {
        assertThatThrownBy(() -> NewListingLink.parse(Optional.empty(), VINTED))
                .isInstanceOf(NewListingLinkInvalid.class);
    }
}
