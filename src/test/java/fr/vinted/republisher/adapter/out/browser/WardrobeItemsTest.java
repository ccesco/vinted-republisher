package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.wardrobe.ListingStatusFlags;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The JSON below repeats the shape Vinted answers with, reduced to what is read and invented in its values. */
class WardrobeItemsTest {

    private static final String RESERVED_ITEM = """
            {"id": 17, "title": "Pull rayé", "price": {"amount": "1.5", "currency_code": "EUR"},
             "url": "https://vinted.test/items/17-pull-raye", "is_draft": false, "is_closed": false,
             "is_reserved": true, "is_hidden": false, "is_processing": false, "favourite_count": 3}""";

    @Test
    void reads_every_listing_of_a_slice() {
        List<ScannedListing> listings = WardrobeItems.parse("{\"items\": [" + RESERVED_ITEM + "]}");

        assertThat(listings).containsExactly(new ScannedListing(new VintedListingId(17),
                URI.create("https://vinted.test/items/17-pull-raye"), "Pull rayé",
                new Price(new BigDecimal("1.50"), "EUR"),
                new ListingStatusFlags(false, false, true, false, false)));
    }

    @Test
    void empty_slice_has_no_listing() {
        assertThat(WardrobeItems.parse("{\"items\": []}")).isEmpty();
    }

    @Test
    void unreadable_when_the_answer_is_not_json() {
        assertThatThrownBy(() -> WardrobeItems.parse("<html>Bloqué</html>")).isInstanceOf(WardrobeUnreadable.class);
    }

    @Test
    void unreadable_without_items() {
        assertThatThrownBy(() -> WardrobeItems.parse("{}")).isInstanceOf(WardrobeUnreadable.class);
    }

    @Test
    void unreadable_when_a_field_is_missing() {
        assertThatThrownBy(() -> parseItemWithout("\"title\": \"Pull rayé\", ")).isInstanceOf(WardrobeUnreadable.class);
    }

    @Test
    void missing_status_flag_never_passes_for_an_active_listing() {
        assertThatThrownBy(() -> parseItemWithout("\"is_reserved\": true,")).isInstanceOf(WardrobeUnreadable.class);
    }

    @Test
    void unreadable_when_a_status_flag_is_not_a_boolean() {
        String item = RESERVED_ITEM.replace("\"is_reserved\": true", "\"is_reserved\": \"non\"");

        assertThatThrownBy(() -> WardrobeItems.parse("{\"items\": [" + item + "]}"))
                .isInstanceOf(WardrobeUnreadable.class);
    }

    @Test
    void unreadable_when_the_price_is_not_a_number() {
        String item = RESERVED_ITEM.replace("\"1.5\"", "\"gratuit\"");

        assertThatThrownBy(() -> WardrobeItems.parse("{\"items\": [" + item + "]}"))
                .isInstanceOf(WardrobeUnreadable.class);
    }

    @Test
    void unreadable_when_the_identifier_is_not_a_number() {
        String item = RESERVED_ITEM.replace("\"id\": 17", "\"id\": \"abc\"");

        assertThatThrownBy(() -> WardrobeItems.parse("{\"items\": [" + item + "]}"))
                .isInstanceOf(WardrobeUnreadable.class);
    }

    private static void parseItemWithout(String field) {
        assertThat(RESERVED_ITEM).contains(field);
        WardrobeItems.parse("{\"items\": [" + RESERVED_ITEM.replace(field, "") + "]}");
    }
}
